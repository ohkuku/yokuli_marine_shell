package com.yokuli.runtime.marine.chart

import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import com.yokuli.chartpackage.ChartPackageManifest
import com.yokuli.chartpackage.YokuliChartPackage
import com.yokuli.runtime.contract.chart.RasterBathymetryGrid
import java.io.File

/** 自动读取的原文件说明只供呈现；任何字段都不授予深度、覆盖或航行资格。 */
internal object ChartSourceMetadata {
    fun folder(manifest:ChartPackageManifest):Map<String,String> = runCatching {YokuliChartPackage.validateMetadata(buildMap {
        putAll(manifest.metadata)
        if(manifest.provider.isNotBlank())putIfAbsent("provider",manifest.provider)
        if(manifest.license.isNotBlank())putIfAbsent("license",manifest.license)
        if(manifest.attribution.isNotBlank())putIfAbsent("attribution",manifest.attribution)
    })}.getOrElse {manifest.metadata}

    fun merge(automatic:Map<String,String>,annotations:Map<String,String>):Map<String,String> {
        val result=YokuliChartPackage.validateMetadata(annotations).toMutableMap()
        for((key,value) in bounded(automatic))if(key !in result) {
            val next=result+(key to value)
            if(runCatching {YokuliChartPackage.validateMetadata(next)}.isSuccess)result[key]=value
        }
        return result
    }

    /** 原始文件完整保留；无法装入轻量说明的内容明确标注，并可在完整导出文件查看。 */
    fun bounded(values:Map<String,String>):Map<String,String> {
        val result=linkedMapOf<String,String>();var bytes=0;var omitted=false
        for((key,value) in values) {
            val validKey=key.isNotBlank()&&key==key.trim()&&key.length<=80&&key.none {it.isISOControl()}
            val validValue=value.length<=8192&&value.none {it=='\u0000'||(it.isISOControl()&&it !in "\n\r\t")}
            val size=key.toByteArray().size+value.toByteArray().size
            if(!validKey||!validValue||result.size>=63||bytes+size>126000){omitted=true;continue}
            if(runCatching{YokuliChartPackage.validateMetadata(mapOf(key to value))}.isFailure){omitted=true;continue}
            result[key]=value;bytes+=size
        }
        if(omitted)result["metadata.notice"]="Some source metadata exceeds display limits; the original file is retained in the complete export."
        return YokuliChartPackage.validateMetadata(result)
    }

    fun s57(header:S57Reader.Header,parameters:S57Reader.Parameters?=null):Map<String,String> = buildMap {
        put("S57.cell",header.cell);put("S57.edition",header.edition.toString());put("S57.update",header.update.toString())
        put("S57.usage",header.usage.toString());put("S57.agency",header.agency.toString());put("S57.purpose",header.purpose.toString())
        put("S57.issueDate",header.issue);put("S57.applyDate",header.applyDate)
        parameters?.let {put("S57.compilationScale",it.scale.toString());put("S57.horizontalDatum",it.horizontal.toString())
            put("S57.verticalDatum",it.vertical.toString());put("S57.soundingDatum",it.sounding.toString());put("S57.depthUnit",it.depthUnit.toString())}
    }

    fun geopackage(file:File,check:()->Unit):Map<String,String> {
        val values=linkedMapOf<String,String>();val gson=Gson()
        SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use {db->
            fun tableExists(name:String)=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",arrayOf(name)).use {it.moveToFirst()}
            if(tableExists("gpkg_contents"))db.rawQuery("SELECT table_name,data_type,substr(identifier,1,8192),substr(description,1,8192),last_change,srs_id,length(identifier),length(description) FROM gpkg_contents ORDER BY table_name LIMIT 33",null).use {cursor->
                var index=0
                while(cursor.moveToNext()) {
                    check();if(index>=32){values["metadata.notice"]="Additional metadata remains available in the original GeoPackage.";break}
                    val row=linkedMapOf<String,String>()
                    listOf("table","type","identifier","description","last_change","srs_id").forEachIndexed {column,key->if(!cursor.isNull(column))row[key]=cursor.getString(column)}
                    if(cursor.getInt(6)>8192||cursor.getInt(7)>8192)row["notice"]="Text exceeds display limits; see original file."
                    values["gpkg.contents.${++index}"]=gson.toJson(row)
                }
            }
            if(tableExists("gpkg_metadata"))db.rawQuery("SELECT id,md_scope,md_standard_uri,mime_type,substr(metadata,1,8192),length(metadata) FROM gpkg_metadata ORDER BY id LIMIT 25",null).use {cursor->
                var index=0
                while(cursor.moveToNext()) {
                    check();if(index++>=24){values["metadata.notice"]="Additional metadata remains available in the original GeoPackage.";break}
                    val row=linkedMapOf<String,String>()
                    listOf("id","scope","standard","mime_type","metadata").forEachIndexed {column,key->if(!cursor.isNull(column))row[key]=cursor.getString(column)}
                    if(cursor.getInt(5)>8192)row["notice"]="Text exceeds display limits; see original file."
                    values["gpkg.metadata.${cursor.getLong(0)}"]=gson.toJson(row)
                }
            }
        }
        return bounded(values)
    }

    fun raster(file:File,grid:RasterBathymetryGrid?):Map<String,String> = buildMap {
        put("source.name",file.name);put("source.bytes",file.length().toString());put("source.format",file.extension.lowercase())
        grid?.let {
            put("raster.product",it.product);put("raster.width",it.width.toString());put("raster.height",it.height.toString())
            put("raster.westEdge",it.westEdge.toString());put("raster.northEdge",it.northEdge.toString())
            put("raster.pixelWidthDegrees",it.pixelWidthDegrees.toString());put("raster.pixelHeightDegrees",it.pixelHeightDegrees.toString())
            put("raster.registration",it.registration);put("raster.verticalReference",it.verticalReference)
            it.noData?.let {value->put("raster.noData",value.toString())}
        }
    }
}
