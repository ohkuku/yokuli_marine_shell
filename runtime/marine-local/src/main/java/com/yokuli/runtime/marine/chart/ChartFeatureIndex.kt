package com.yokuli.runtime.marine.chart

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.os.CancellationSignal
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import java.text.Normalizer
import java.util.Locale
import kotlin.math.floor

/** S-57 与 GeoPackage 共用安装索引；仅向尚未发布的版本写入，事务由导入所有者控制。 */
internal object ChartFeatureIndex {
    fun create(db:SQLiteDatabase)=ChartNativeIndexWriter.create(AndroidChartSql(db))
    fun insert(db:SQLiteDatabase,rowId:Long,feature:NauticalFeature,gson:Gson=Gson(),check:()->Unit={}):List<ChartBounds> =
        ChartNativeIndexWriter.insert(AndroidChartSql(db),rowId,feature,gson,check)

    /**
     * 已安装的 JSON 索引在旁路重建，最后才由所有者持锁原子替换。不能 UPDATE 后留下数 GB 空闲页，
     * 也不能持查询锁重建全国资料。失败、取消、空间不足保留旧索引；原始资料从不修改。
     * publish 必须在确认版本仍有效后用原子 rename 发布；旧只读 SQLite 连接可自然读完旧 inode。
     */
    fun compact(file:File,gson:Gson,check:()->Unit,publish:(File)->Unit):Boolean {
        if(!file.isFile)return false
        val temporary=File(file.parentFile,".${file.name}.${UUID.randomUUID()}.compact")
        try {
            check()
            SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use {source->
                if(source.version>=8)return false
                require(source.version in 6..7){"CHART_FEATURE_INDEX_VERSION"}
                // 只需要紧凑副本和事务页的空间；不再额外复制整个旧数据库作备份。
                require(file.parentFile!!.usableSpace>32L*1024*1024){"CHART_STORAGE_FULL"}
                SQLiteDatabase.openOrCreateDatabase(temporary,null).use {target->
                    target.rawQuery("PRAGMA journal_mode=DELETE",null).use {rows->while(rows.moveToNext())Unit}
                    create(target)
                    val signal=CancellationSignal()
                    target.execSQL("ATTACH DATABASE ? AS compact_source",arrayOf<Any>(file.path))
                    // 附加的旧库仍需供前台读取，禁止 EXCLUSIVE 事务封锁旧库。
                    target.beginTransactionNonExclusive()
                    try {
                        var previous=0L
                        while(true) {
                            check()
                            val rows=ArrayList<Pair<Long,Int>>(128)
                            source.rawQuery("SELECT rowid,length(payload) FROM features WHERE rowid>? ORDER BY rowid LIMIT 128",arrayOf(previous.toString()),signal).use {cursor->
                                while(cursor.moveToNext())rows+=cursor.getLong(0) to cursor.getInt(1)
                            }
                            if(rows.isEmpty())break
                            for((row,length) in rows) {
                                check()
                                require(row in 1..2_000_000){"CHART_FEATURE_LIMIT"}
                                val feature=ChartFeaturePayload.read(source,row,length,gson,signal,check)
                                val encoded=ChartFeaturePayload.encode(feature.copy(geometry=feature.geometry.copy(parts=emptyList())),gson,check)
                                // 目录字段已经由原导入器判定；只替换几何编码，不重新推断来源/语义层级。
                                target.execSQL("INSERT INTO features(rowid,feature_id,cell,kind,detail_scale,detail_tier,name,search,payload) SELECT rowid,feature_id,cell,kind,detail_scale,detail_tier,name,search,? FROM compact_source.features WHERE rowid=?",arrayOf<Any>(encoded,row))
                                val geometryHash=ChartGeometrySpanIndex.insert(target,row,feature.geometry,check)
                                ChartGeometrySpanIndex.appendIdentity(target,row,encoded,geometryHash)
                                previous=row
                            }
                            require(file.parentFile!!.usableSpace>8L*1024*1024){"CHART_STORAGE_FULL"}
                        }
                        // 空间索引沿用已经确认的范围；按主键分批复制，不再排序全国每个多边形的顶点。
                        var first=1L
                        while(first<=previous*2+1) {
                            check();val end=minOf(first+4095,previous*2+1)
                            target.execSQL("INSERT INTO spatial SELECT id,min_x,max_x,min_y,max_y FROM compact_source.spatial WHERE id BETWEEN ? AND ?",arrayOf<Any>(first,end))
                            target.execSQL("INSERT INTO spatial_feature SELECT id,feature_row FROM compact_source.spatial_feature WHERE id BETWEEN ? AND ?",arrayOf<Any>(first,end))
                            target.execSQL("INSERT INTO spatial_bucket SELECT spatial_id,bucket FROM compact_source.spatial_bucket WHERE spatial_id BETWEEN ? AND ?",arrayOf<Any>(first,end))
                            first=end+1
                        }
                        check();target.setTransactionSuccessful()
                    }finally {
                        try{target.endTransaction()}finally{target.execSQL("DETACH DATABASE compact_source")}
                    }
                    ChartGeometrySpanIndex.validate(target)
                    target.rawQuery("PRAGMA quick_check(1)",null).use {rows->require(rows.moveToFirst()&&rows.getString(0)=="ok"){"CHART_FEATURE_INDEX_INVALID"}}
                }
            }
            check()
            // SQLite FULL 同步后仍显式同步新文件；发布前文件完整且没有 WAL/未提交事务。
            FileOutputStream(temporary,true).use{it.fd.sync()}
            check();publish(temporary)
            return true
        }finally {
            temporary.delete()
            File(temporary.path+"-journal").delete()
            File(temporary.path+"-wal").delete()
            File(temporary.path+"-shm").delete()
        }
    }

    fun queryBuckets(bounds:ChartBounds):List<Int>?=ChartNativeIndexWriter.queryBuckets(bounds)
    fun normalized(text:String):String=ChartNativeIndexWriter.normalized(text)
    fun matches(feature:NauticalFeature,filter:ChartFeatureFilter,normalizedQuery:String):Boolean =
        ChartNativeIndexWriter.matches(feature,filter,normalizedQuery)
}
