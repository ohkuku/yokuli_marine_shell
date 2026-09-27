package com.yokuli.anchorwatch.runtime.storage

import android.database.Cursor
import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.yokuli.runtime.contract.hardware.StorageFault
import com.yokuli.runtime.contract.hardware.VirtualHostServices
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.IdentityHashMap

/**
 * 中文：只代理 Room 实际使用的三个 SQLite 接口，不伪造 DAO 结果。
 * 故障在真正 query/execute/commit 之前失败；不会破坏数据库文件，关闭/回滚永远可执行。
 * 真实世界直接返回原 factory，运行中打开的实验数据库也在每次操作时读取故障状态。
 */
class FaultAwareSQLiteFactory(
    private val delegate:SupportSQLiteOpenHelper.Factory=FrameworkSQLiteOpenHelperFactory(),
):SupportSQLiteOpenHelper.Factory {
    override fun create(configuration:SupportSQLiteOpenHelper.Configuration):SupportSQLiteOpenHelper {
        val helper=delegate.create(configuration)
        if(!VirtualHostServices.virtual)return helper
        val databases=IdentityHashMap<SupportSQLiteDatabase,SupportSQLiteDatabase>()
        return wrap(SupportSQLiteOpenHelper::class.java,helper){method,args->
            when(method.name){
                "getReadableDatabase","getWritableDatabase"->{
                    read()
                    // 首次创建数据库本身就是写入，不能在 FULL/READ_ONLY 时偷偷建一个空库。
                    val name=configuration.name
                    if(name==null||!configuration.context.getDatabasePath(name).exists())write()
                    val opened=call(helper,method,args) as SupportSQLiteDatabase
                    synchronized(databases){databases.getOrPut(opened){database(opened)}}
                }
                // 回滚和关闭必须能在故障状态下完成，避免占住事务或文件锁。
                "close"->try{call(helper,method,args)}finally{synchronized(databases){databases.clear()}}
                else->call(helper,method,args)
            }
        }
    }
    private fun database(delegate:SupportSQLiteDatabase):SupportSQLiteDatabase=wrap(SupportSQLiteDatabase::class.java,delegate){method,args->
        when(method.name){
            "close","endTransaction","inTransaction","isDbLockedByCurrentThread","isOpen","getPath","isReadOnly","getAttachedDbs"->call(delegate,method,args)
            "compileStatement"->{
                read()
                val compiled=call(delegate,method,args) as SupportSQLiteStatement
                statement(compiled,args?.firstOrNull() as? String ?: "")
            }
            "query"->{read();val result=call(delegate,method,args);if(result is Cursor)cursor(result)else result}
            "execSQL"->{
                val sql=args?.firstOrNull() as? String ?: ""
                if(!cleanupSql(sql)){if(readSql(sql))read()else write()}
                call(delegate,method,args)
            }
            "insert","delete","update","setVersion","setMaximumSize","setPageSize","setLocale","setForeignKeyConstraintsEnabled","enableWriteAheadLogging","disableWriteAheadLogging","beginTransaction","beginTransactionNonExclusive","beginTransactionWithListener","beginTransactionWithListenerNonExclusive","setTransactionSuccessful"->{write();call(delegate,method,args)}
            // 版本/页面/完整性/pragma 查询仍是真实磁盘读；权限撤销或损坏不能返回缓存成功。
            else->{read();call(delegate,method,args)}
        }
    }
    private fun statement(delegate:SupportSQLiteStatement,sql:String):SupportSQLiteStatement=wrap(SupportSQLiteStatement::class.java,delegate){method,args->
        when(method.name){
            "simpleQueryForLong","simpleQueryForString"->read()
            "execute","executeInsert","executeUpdateDelete"->if(!cleanupSql(sql)){if(readSql(sql))read()else write()}
            // bind/clear/close 只操作句柄，允许清理已经编译的语句。
        }
        call(delegate,method,args)
    }
    private fun cursor(delegate:Cursor):Cursor=wrap(Cursor::class.java,delegate){method,args->
        if(method.name !in setOf("close","isClosed","unregisterContentObserver","unregisterDataSetObserver","deactivate"))read()
        call(delegate,method,args)
    }
    private fun read(){
        if(!VirtualHostServices.virtual)return
        when(VirtualHostServices.storage.value){
            StorageFault.CORRUPT->throw SQLiteDatabaseCorruptException("VIRTUAL_STORAGE_CORRUPT")
            StorageFault.PERMISSION_REVOKED->throw SQLiteAccessPermException("VIRTUAL_STORAGE_PERMISSION_REVOKED")
            else->Unit
        }
    }
    private fun write(){
        read()
        if(!VirtualHostServices.virtual)return
        when(VirtualHostServices.storage.value){
            StorageFault.FULL->throw SQLiteFullException("VIRTUAL_STORAGE_FULL")
            StorageFault.READ_ONLY->throw SQLiteReadOnlyDatabaseException("VIRTUAL_STORAGE_READ_ONLY")
            else->Unit
        }
    }
    private fun leadingSql(sql:String):String=sql.trimStart().uppercase(java.util.Locale.ROOT)
    private fun cleanupSql(sql:String):Boolean=leadingSql(sql).let{it.startsWith("ROLLBACK")||it.startsWith("RELEASE ")}
    private fun readSql(sql:String):Boolean=leadingSql(sql).let{it.startsWith("SELECT ")||it.startsWith("EXPLAIN ")||it in setOf("PRAGMA USER_VERSION","PRAGMA JOURNAL_MODE","PRAGMA INTEGRITY_CHECK","PRAGMA QUICK_CHECK")}
    private fun call(target:Any,method:Method,args:Array<out Any?>?):Any?=try{method.invoke(target,*(args?:emptyArray()))}catch(error:InvocationTargetException){throw error.targetException}
    @Suppress("UNCHECKED_CAST")
    private fun <T:Any> wrap(type:Class<T>,delegate:T,invoke:(Method,Array<out Any?>?)->Any?):T = Proxy.newProxyInstance(type.classLoader,arrayOf(type)){proxy,method,args->
        when{method.declaringClass!=Any::class.java->invoke(method,args);method.name=="equals"->proxy===args?.firstOrNull();method.name=="hashCode"->System.identityHashCode(proxy);else->"FaultAware(${delegate.javaClass.simpleName})"}
    } as T
}
