package com.yokuli.runtime.marine.chart.terrain

import android.database.sqlite.SQLiteDatabase
import com.yokuli.runtime.contract.hardware.VirtualHostServices
import kotlinx.coroutines.*
import java.io.File

/** 原生包只承载已完成的产品表。禁止导入数据库执行触发器、视图或任意扩展 schema。 */
suspend fun validateChartTerrainProducts(file:File)=withContext(Dispatchers.IO) {
    require(file.isFile){"CHART_TERRAIN_PRODUCT_MISSING"}
    SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use {db->
        validateTerrainSchema(db,allowJobs=false)
        var after=""
        while(true) {
            currentCoroutineContext().ensureActive()
            val keys=ArrayList<String>(32)
            db.rawQuery("SELECT key FROM products WHERE key>? ORDER BY key LIMIT 32",arrayOf(after)).use{rows->while(rows.moveToNext())keys+=rows.getString(0)}
            if(keys.isEmpty())break
            for(key in keys) {
                currentCoroutineContext().ensureActive()
                val product=readTerrainProduct(db,key)
                val tile=ChartTerrainBlockCodec.decode(product.bytes,key)
                require(tile.sourceKey==product.sourceKey){"CHART_TERRAIN_PRODUCT_IDENTITY"}
                after=key
            }
        }
    }
}

internal fun validateTerrainSchema(db:SQLiteDatabase,allowJobs:Boolean=true) {
    require(db.version==1){"CHART_TERRAIN_PRODUCT_SCHEMA"}
    val tables=mutableSetOf<String>()
    db.rawQuery("SELECT type,name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'",null).use{rows->
        while(rows.moveToNext()) {
            val type=rows.getString(0);val name=rows.getString(1)
            require(type=="table"&&(name=="products"||allowJobs&&name=="jobs")){"CHART_TERRAIN_PRODUCT_SCHEMA"}
            tables+=name
        }
    }
    require("products" in tables){"CHART_TERRAIN_PRODUCT_SCHEMA"}
    fun columns(table:String,expected:List<Pair<String,String>>) {
        val actual=ArrayList<Pair<String,String>>()
        db.rawQuery("PRAGMA table_info($table)",null).use{rows->while(rows.moveToNext()){
            val name=rows.getString(1)
            require(rows.getInt(5)==(if(name=="key")1 else 0)&&
                (name=="key"||name=="reason"||rows.getInt(3)==1)&&rows.isNull(4)){"CHART_TERRAIN_PRODUCT_SCHEMA"}
            actual+=name to rows.getString(2).uppercase()
        }}
        require(actual==expected){"CHART_TERRAIN_PRODUCT_SCHEMA"}
    }
    columns("products",listOf("key" to "TEXT","source_key" to "TEXT","schema" to "INTEGER","sha256" to "TEXT","payload" to "BLOB","created_at" to "INTEGER"))
    if("jobs" in tables)columns("jobs",listOf("key" to "TEXT","source_key" to "TEXT","request" to "TEXT","phase" to "TEXT","reason" to "TEXT","updated_at" to "INTEGER"))
    db.rawQuery("SELECT 1 FROM products WHERE key IS NULL OR length(key)!=64 OR key GLOB '*[^0-9a-f]*' OR source_key IS NULL OR length(source_key)!=64 OR source_key GLOB '*[^0-9a-f]*' OR schema!=1 OR sha256 IS NULL OR length(sha256)!=64 OR sha256 GLOB '*[^0-9a-f]*' OR typeof(payload)!='blob' OR length(payload)<53 OR length(payload)>${ChartTerrainBlockCodec.MAX_BYTES} LIMIT 1",null).use{rows->
        require(!rows.moveToFirst()){"CHART_TERRAIN_PRODUCT_INVALID"}
    }
    if("jobs" in tables)db.rawQuery("SELECT 1 FROM jobs WHERE length(request)>16384 OR length(key)!=64 OR phase NOT IN ('QUEUED','PREPARING','READY','FAILED','STALE','CANCELLED','SUBDIVIDED') LIMIT 1",null).use{rows->
        require(!rows.moveToFirst()){"CHART_TERRAIN_JOB_INVALID"}
    }
}

internal data class StoredTerrainProduct(val sourceKey:String,val schema:Int,val sha256:String,val bytes:ByteArray)
internal suspend fun readTerrainProduct(db:SQLiteDatabase,key:String):StoredTerrainProduct {
    data class Header(val length:Int,val hash:String,val schema:Int,val source:String)
    val head=db.rawQuery("SELECT length(payload),sha256,schema,source_key FROM products WHERE key=?",arrayOf(key)).use {row->
        require(row.moveToFirst()){"CHART_TERRAIN_NOT_PREPARED"}
        Header(row.getInt(0),row.getString(1),row.getInt(2),row.getString(3))
    }
    require(head.length in 53..ChartTerrainBlockCodec.MAX_BYTES&&head.schema==ChartTerrainBlockCodec.SCHEMA){"CHART_TERRAIN_PRODUCT_INVALID"}
    val bytes=ByteArray(head.length);var offset=0
    while(offset<bytes.size) {
        currentCoroutineContext().ensureActive();VirtualHostServices.beforeRead()
        db.rawQuery("SELECT substr(payload,?,?) FROM products WHERE key=?",arrayOf((offset+1).toString(),"131072",key)).use {row->
            require(row.moveToFirst()){"CHART_TERRAIN_NOT_PREPARED"}
            val chunk=row.getBlob(0);require(chunk.size==minOf(131072,bytes.size-offset)){"CHART_TERRAIN_PRODUCT_CORRUPT"}
            chunk.copyInto(bytes,offset);offset+=chunk.size
        }
    }
    require(ChartTerrainBlockCodec.hash(bytes)==head.hash){"CHART_TERRAIN_PRODUCT_CORRUPT"}
    return StoredTerrainProduct(head.source,head.schema,head.hash,bytes)
}

internal fun createTerrainProducts(db:SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS products(key TEXT PRIMARY KEY,source_key TEXT NOT NULL,schema INTEGER NOT NULL,sha256 TEXT NOT NULL,payload BLOB NOT NULL,created_at INTEGER NOT NULL)")
    db.execSQL("PRAGMA user_version=1")
}
internal fun createTerrainJobs(db:SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS jobs(key TEXT PRIMARY KEY,source_key TEXT NOT NULL,request TEXT NOT NULL,phase TEXT NOT NULL,reason TEXT,updated_at INTEGER NOT NULL)")
}
