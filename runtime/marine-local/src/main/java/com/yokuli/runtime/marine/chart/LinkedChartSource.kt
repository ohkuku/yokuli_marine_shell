package com.yokuli.runtime.marine.chart

import android.content.Context
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/** 原件由用户文件夹持有；本地只存来源身份、索引版本指纹与派生索引。 */
internal data class ChartSourceLink(val uri:String,val size:Long,val modified:Long,val sha256:String)
internal class ChartRandomAccessUnavailable(cause:Throwable?=null):Exception("CHART_SOURCE_RANDOM_ACCESS_UNSUPPORTED",cause)

/** fd 必须覆盖整个解析/栅格窗口读取生命周期，不能将 /proc 路径持久化。 */
internal class ChartSourceHandle(val file:File,private val descriptor:ParcelFileDescriptor?=null):Closeable {
    override fun close(){descriptor?.close()}
}

internal object LinkedChartSource {
    fun input(context:Context,uri:Uri):InputStream = try {
        if(uri.scheme=="file")File(requireNotNull(uri.path)).inputStream()
        else context.contentResolver.openInputStream(uri)?:error("CHART_SOURCE_PERMISSION_LOST")
    }catch(error:SecurityException){throw IllegalStateException("CHART_SOURCE_PERMISSION_LOST",error)}
    catch(error:java.io.FileNotFoundException){throw IllegalStateException("CHART_SOURCE_MISSING",error)}

    fun random(context:Context,uri:Uri,sqlite:Boolean=false):ChartSourceHandle {
        val handle=if(uri.scheme=="file")ChartSourceHandle(File(requireNotNull(uri.path)))else {
            val descriptor=try {context.contentResolver.openFileDescriptor(uri,"r")?:error("CHART_SOURCE_PERMISSION_LOST")}
                catch(error:SecurityException){throw IllegalStateException("CHART_SOURCE_PERMISSION_LOST",error)}
                catch(error:java.io.FileNotFoundException){throw IllegalStateException("CHART_SOURCE_MISSING",error)}
            try {
                Os.lseek(descriptor.fileDescriptor,0,OsConstants.SEEK_SET)
                val path=File("/proc/self/fd/${descriptor.fd}")
                require(path.isFile&&path.length()>=0)
                RandomAccessFile(path,"r").use {it.seek(0)}
                ChartSourceHandle(path,descriptor)
            }catch(error:Exception){descriptor.close();throw ChartRandomAccessUnavailable(error)}
        }
        try {
            if(sqlite)try {
                SQLiteDatabase.openDatabase(handle.file.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { }
            }catch(error:SQLiteCantOpenDatabaseException){throw ChartRandomAccessUnavailable(error)}
            return handle
        }catch(error:Exception){handle.close();throw error}
    }

    private fun stamp(context:Context,uri:Uri):Pair<Long,Long> {
        if(uri.scheme=="file")return File(requireNotNull(uri.path)).let {
            require(it.isFile){"CHART_SOURCE_MISSING"};it.length() to it.lastModified()
        }
        // fd 属性不依赖某些 DocumentsProvider 缓存的 size/date 列；管道则退回文档元数据。
        try {random(context,uri).use {return it.file.length() to it.file.lastModified()}}
        catch(_:ChartRandomAccessUnavailable){ }
        try {
            return context.contentResolver.query(uri,arrayOf(OpenableColumns.SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED),null,null,null)?.use {
                require(it.moveToFirst()){"CHART_SOURCE_MISSING"}
                (if(it.isNull(0))-1L else it.getLong(0)) to (if(it.isNull(1))0L else it.getLong(1))
            }?:error("CHART_SOURCE_PERMISSION_LOST")
        }catch(error:SecurityException){throw IllegalStateException("CHART_SOURCE_PERMISSION_LOST",error)}
    }

    fun capture(context:Context,uri:Uri,check:()->Unit):ChartSourceLink {
        val before=stamp(context,uri)
        val (size,hash)=digest(context,uri,check)
        val after=stamp(context,uri)
        require(before==after&&(after.first<0||after.first==size)){"CHART_SOURCE_CHANGED"}
        return ChartSourceLink(uri.toString(),size,after.second,hash)
    }

    fun verify(context:Context,link:ChartSourceLink,check:()->Unit={},full:Boolean=false) {
        check();val uri=Uri.parse(link.uri);val now=stamp(context,uri)
        require((now.first<0||now.first==link.size)&&(link.modified<=0||now.second==link.modified)){"CHART_SOURCE_CHANGED"}
        // 无可靠修改时间的提供方不能用“文件大小没变”冒充版本一致。
        if(full||link.modified<=0||now.second<=0) {
            val (size,hash)=digest(context,uri,check)
            require(size==link.size&&hash==link.sha256){"CHART_SOURCE_CHANGED"}
        }
    }

    private fun digest(context:Context,uri:Uri,check:()->Unit):Pair<Long,String> {
        val hash=MessageDigest.getInstance("SHA-256");var size=0L
        input(context,uri).use {input->
            val buffer=ByteArray(256*1024)
            while(true){check();val count=input.read(buffer);if(count<0)break;size+=count
                require(size<=32_000_000_000L){"CHART_PACKAGE_SIZE_LIMIT"};hash.update(buffer,0,count)}
        }
        return size to hash.digest().joinToString(""){"%02x".format(it)}
    }
}
