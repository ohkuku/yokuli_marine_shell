package com.yokuli.runtime.marine.chart

import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
import android.os.SystemClock
import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.NauticalFeature
import kotlinx.coroutines.sync.Mutex
import java.io.Closeable

/**
 * 准星与规划采样共用的有界只读工作集。key 包含不可变目录、用户修订及数据库 inode；
 * 更换资料、调整优先级、后台压实都不会把新请求接到旧的属性/坐标缓存。
 * 缓存不拥有资料：调用者仍须持有 Core 读租约。正在使用的连接只在最后归还时关闭。
 */
internal class ChartPositionSessions : Closeable {
    internal data class Key(val directory:String,val revision:Long,val device:Long,val inode:Long)
    internal class Session(val database:SQLiteDatabase,val rasters:RasterBathymetryStore?) : Closeable {
        val queries=Mutex()
        val version=database.version
        var preparedSourceIdentity:String?=null
        val geometry=ChartGeometrySpanIndex.ReadCache()
        val identity=ChartFeaturePayload.readIdentity(database)
        val rtree=database.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='spatial'",null).use {
            it.moveToFirst()&&it.getString(0).orEmpty().contains("USING rtree",ignoreCase=true)
        }
        private data class Metadata(val feature:NauticalFeature,val bytes:Long)
        private val metadata=LinkedHashMap<Long,Metadata>(128,.75f,true)
        private var metadataBytes=0L
        private val rows=LinkedHashMap<String,Long>(128,.75f,true)
        fun rowId(id:String,signal:CancellationSignal):Long {
            signal.throwIfCanceled()
            return rows[id]?:database.rawQuery("SELECT rowid FROM features WHERE feature_id=?",arrayOf(id),signal).use {
                require(it.moveToFirst()){"CHART_NATIVE_COVERAGE_MISSING"};it.getLong(0)
            }.also {value->
                rows[id]=value
                while(rows.size>2048)rows.remove(rows.entries.iterator().next().key)
            }
        }
        /** 在 queries 锁中访问；仅缓存 v8 无坐标属性，不持有任意大小的全国对象。 */
        fun feature(row:Long,length:Int,gson:Gson,signal:CancellationSignal,check:()->Unit):NauticalFeature {
            check();signal.throwIfCanceled()
            metadata[row]?.let{return it.feature}
            val feature=ChartFeaturePayload.read(database,row,length,gson,signal,check,metadataOnly=true,identity=identity,indexVersion=version)
            val bytes=4096L+feature.attributes.entries.sumOf{(it.key.length+it.value.length).toLong()*2}+
                feature.issues.sumOf{it.length.toLong()*2}+feature.id.length*2L
            if(bytes<=512*1024) {
                metadata[row]=Metadata(feature,bytes);metadataBytes+=bytes
                while(metadata.size>2048||metadataBytes>4*1024*1024) {
                    val first=metadata.entries.iterator().next();metadataBytes-=first.value.bytes;metadata.remove(first.key)
                }
            }
            return feature
        }
        override fun close(){try{rasters?.close()}finally{try{database.close()}finally{metadata.clear();rows.clear();geometry.clear()}}}
    }
    private data class Entry(val key:Key,val session:Session,var readers:Int=0,var retired:Boolean=false,var touched:Long=SystemClock.elapsedRealtime())
    private val lock=Any()
    private val entries=LinkedHashMap<Key,Entry>(4,.75f,true)
    private var closed=false
    internal inner class Borrowed internal constructor(val session:Session,private val release:()->Unit) : Closeable {
        private var returned=false
        override fun close(){synchronized(this){if(returned)return;returned=true};release()}
    }
    fun acquire(key:Key,open:()->Session):Borrowed=synchronized(lock) {
        check(!closed){"CHART_QUERY_SESSIONS_CLOSED"}
        val entry=entries[key]?:run {
            // 不让并发规划请求无限打开数据库。活动连接被读租约保护，不可为了 LRU 强关。
            evictIdle(SystemClock.elapsedRealtime(),all=false,reserve=true)
            require(entries.size<MAX_SESSIONS){"CHART_QUERY_BUSY"}
            Entry(key,open()).also{entries[key]=it}
        }
        entry.readers++;entry.touched=SystemClock.elapsedRealtime()
        Borrowed(entry.session) {synchronized(lock){
            entry.readers--;entry.touched=SystemClock.elapsedRealtime()
            if(entry.readers==0&&entry.retired){entries.remove(entry.key,entry);entry.session.close()}
        }}
    }
    /** 所有者在目录事务后退休已移除版本；绝不从准星请求做磁盘清理。 */
    fun retain(directories:Set<String>)=synchronized(lock) {
        entries.values.toList().filter{it.key.directory !in directories}.forEach{entry->
            entry.retired=true
            if(entry.readers==0){entries.remove(entry.key);entry.session.close()}
        }
    }
    fun trimIdle()=synchronized(lock){evictIdle(SystemClock.elapsedRealtime(),all=false,reserve=false)}
    private fun evictIdle(now:Long,all:Boolean,reserve:Boolean) {
        entries.values.toList().forEach {entry->
            if(entry.readers==0&&(all||entry.retired||now-entry.touched>=60_000||(reserve&&entries.size>=MAX_SESSIONS))) {
                entries.remove(entry.key);entry.session.close()
            }
        }
    }
    override fun close()=synchronized(lock){
        closed=true
        entries.values.toList().forEach{it.retired=true}
        evictIdle(SystemClock.elapsedRealtime(),all=true,reserve=false)
    }
    private companion object {const val MAX_SESSIONS=3}
}
