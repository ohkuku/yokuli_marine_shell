package com.yokuli.runtime.marine.chart

import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.hardware.VirtualHostServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.CheckedOutputStream

/**
 * Core 独占的派生几何块。来源版本目录不可变，键同时绑定行身份、原始长度和编码版本。
 * 属性保留为小型 JSON，坐标以原始 double 保存；不简化、不插值，也不改变证据含义。
 * 损坏或缺失块回退到 canonical SQLite，原件/索引始终是唯一事实来源。
 */
internal class ChartFeatureBlockStore(private val root:File,private val gson:Gson,scope:CoroutineScope) {
    private val lock=Any()
    private var diskBytes:Long?=null
    private val emptyParts=emptyList<ChartGeometryPart>()
    private data class Pending(val directory:String,val row:Long,val length:Int,val feature:NauticalFeature,val bytes:Long)
    private val queue=Channel<Pending>(16)
    private val queued=HashSet<String>()
    private var queuedBytes=0L

    init {
        scope.launch(Dispatchers.IO) {
            try {
                for(item in queue)try {
                    val work=currentCoroutineContext()
                    write(item.directory,item.row,item.length,item.feature){work.ensureActive()}
                }finally {
                    synchronized(lock){queued.remove("${item.directory}:${item.row}");queuedBytes-=item.bytes}
                }
            }finally {
                // Core 所有者关闭时释放排队几何；取消后 prepare 不能重新保留对象。
                queue.cancel()
                synchronized(lock){queued.clear();queuedBytes=0L}
            }
        }
    }

    /** 有界后台编译；前台读取不等 fsync，也不为了缓存累积无界几何对象。 */
    fun prepare(directory:String,row:Long,length:Int,feature:NauticalFeature) {
        val bytes=length.toLong()*2+feature.geometry.parts.sumOf{it.points.size.toLong()*56+64}+512
        val key="$directory:$row"
        synchronized(lock) {
            if(key in queued||queuedBytes+bytes>12L*1024*1024)return
            if(queue.trySend(Pending(directory,row,length,feature,bytes)).isSuccess){queued+=key;queuedBytes+=bytes}
        }
    }

    private fun target(directory:String,row:Long,length:Int):File {
        val identity="geometry-v1:$directory:$row:$length"
        val hash=MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return File(root,"$hash.ykb")
    }

    fun read(directory:String,row:Long,length:Int,check:()->Unit):NauticalFeature? {
        val file=target(directory,row,length)
        if(!file.isFile)return null
        try {
            check();VirtualHostServices.beforeRead()
            require(file.length() in 24..MAX_BLOCK_BYTES)
            FileInputStream(file).buffered().use {stream->
                val checked=CheckedInputStream(stream,CRC32())
                val input=DataInputStream(checked)
                require(input.readInt()==MAGIC&&input.readInt()==1)
                val metadataLength=input.readInt()
                require(metadataLength in 1..MAX_METADATA_BYTES)
                val metadata=ByteArray(metadataLength);input.readFully(metadata)
                val feature=requireNotNull(gson.fromJson(String(metadata,Charsets.UTF_8),NauticalFeature::class.java))
                val count=input.readInt();require(count in 0..250_000)
                var vertices=0
                val parts=ArrayList<ChartGeometryPart>(count.coerceAtMost(4096))
                repeat(count) {
                    check()
                    val hole=input.readBoolean();val size=input.readInt()
                    require(size>=0&&size<=MAX_VERTICES-vertices);vertices+=size
                    val points=ArrayList<ChartPoint>(size)
                    repeat(size) {index->
                        if(index%256==0)check()
                        val latitude=input.readDouble();val longitude=input.readDouble()
                        require(latitude.isFinite()&&longitude.isFinite())
                        val depth=if(input.readBoolean())input.readDouble().also{require(it.isFinite())}else null
                        points+=ChartPoint(latitude,longitude,depth)
                    }
                    parts+=ChartGeometryPart(points,hole)
                }
                val checksum=checked.checksum.value
                require(input.readLong()==checksum&&input.read()==-1)
                // LRU 时间只属于可丢弃产物，不改来源的测量时间或资料版本。
                if(System.currentTimeMillis()-file.lastModified()>60_000)file.setLastModified(System.currentTimeMillis())
                return feature.copy(geometry=feature.geometry.copy(parts=parts))
            }
        }catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){synchronized(lock){if(file.delete())diskBytes=null};return null}
    }

    private fun write(directory:String,row:Long,length:Int,feature:NauticalFeature,check:()->Unit) {
        val target=target(directory,row,length)
        var temporary:File?=null
        try {
            check();VirtualHostServices.beforeWrite()
            val vertexCount=feature.geometry.parts.sumOf{it.points.size.toLong()}
            if(vertexCount>MAX_VERTICES||feature.geometry.parts.size>250_000)return
            val metadata=gson.toJson(feature.copy(geometry=feature.geometry.copy(parts=emptyParts))).toByteArray(Charsets.UTF_8)
            if(metadata.size>MAX_METADATA_BYTES||metadata.size+vertexCount*25>MAX_BLOCK_BYTES)return
            root.mkdirs()
            val stage=File(root,".${UUID.randomUUID()}.pending");temporary=stage
            FileOutputStream(stage).use {file->
                val buffer=file.buffered()
                val checked=CheckedOutputStream(buffer,CRC32())
                val output=DataOutputStream(checked)
                output.writeInt(MAGIC);output.writeInt(1);output.writeInt(metadata.size);output.write(metadata)
                output.writeInt(feature.geometry.parts.size)
                for(part in feature.geometry.parts) {
                    check();output.writeBoolean(part.hole);output.writeInt(part.points.size)
                    part.points.forEachIndexed {index,point->
                        if(index%256==0)check()
                        output.writeDouble(point.latitude);output.writeDouble(point.longitude)
                        output.writeBoolean(point.depthMeters!=null);point.depthMeters?.let(output::writeDouble)
                    }
                }
                val checksum=checked.checksum.value;output.writeLong(checksum)
                output.flush();file.fd.sync()
            }
            check()
            require(stage.length()<=MAX_BLOCK_BYTES)
            synchronized(lock) {
                val added=if(target.exists()){stage.delete();0L}else {
                    val bytes=stage.length();require(stage.renameTo(target));bytes
                }
                diskBytes=diskBytes?.plus(added)
                // 每次发布都按字节触发回收，不能等固定写入次数后才清理几十个大块。
                if(diskBytes==null||requireNotNull(diskBytes)>MAX_DISK_BYTES)trim()
            }
        }catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){/* 磁盘缓存失败不使有效资料查询失败。 */}
        finally{temporary?.delete()}
    }

    private fun trim() {
        val files=root.listFiles().orEmpty()
        files.filter{it.extension=="pending"&&System.currentTimeMillis()-it.lastModified()>60_000}.forEach{it.delete()}
        val blocks=files.filter{it.extension=="ykb"}.sortedBy{it.lastModified()}
        var total=blocks.sumOf{it.length()}
        for(file in blocks) {
            if(total<=MAX_DISK_BYTES)break
            val bytes=file.length();if(file.delete())total-=bytes
        }
        diskBytes=total
    }

    private companion object {
        const val MAGIC=0x594b4231
        const val MAX_BLOCK_BYTES=16L*1024*1024
        const val MAX_METADATA_BYTES=1_000_000
        const val MAX_VERTICES=500_000
        const val MAX_DISK_BYTES=128L*1024*1024
    }
}
