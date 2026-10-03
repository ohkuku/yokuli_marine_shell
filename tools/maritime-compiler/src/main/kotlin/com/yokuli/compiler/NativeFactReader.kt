package com.yokuli.compiler

import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.marine.chart.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import java.io.File

/** 制包只读本次已编译快照；不加载 Android Core，也不另造数据所有者。 */
internal class NativeFactReader(directory:File,val snapshot:ChartDataSnapshot):ChartFactReader,Closeable {
    private val db=JdbcChartSql.openReadOnly(File(directory,"features.sqlite"))
    private val gson=Gson()
    private data class Entry(val feature:NauticalFeature,val bytes:Long)
    private val cache=LinkedHashMap<Long,Entry>(256,.75f,true)
    private val geometryIndex=ChartGeometryQueryIndex()
    private var cacheBytes=0L
    val sourceIdentity:String=db.rawQuery("SELECT identity,content_hash FROM native_content").use {
        require(it.moveToFirst());chartPreparedIdentity(it.getString(0)+":"+it.getString(1),snapshot.datasets.single())
    }
    private fun feature(row:Long,check:()->Unit):NauticalFeature {
        cache[row]?.let{return it.feature}
        val metadata=db.rawQuery("SELECT payload FROM features WHERE rowid=?",arrayOf(row.toString())).use{
            require(it.moveToFirst());ChartFeatureEncoder.decodeMetadata(it.getBlob(0),gson,check)
        }
        val parts=ArrayList<ChartGeometryPart>()
        db.rawQuery("SELECT part_no,hole,point_count FROM geometry_part WHERE feature_row=? ORDER BY part_no",arrayOf(row.toString())).use {rows->
            while(rows.moveToNext()) {
                check();val part=rows.getInt(0);val hole=rows.getInt(1)!=0;val count=rows.getInt(2)
                val points=ArrayList<ChartPoint>(count)
                db.rawQuery("SELECT point_start,point_count,raw_size,crc,payload FROM geometry_span WHERE feature_row=? AND part_no=? ORDER BY span_no",arrayOf(row.toString(),part.toString())).use {spans->
                    while(spans.moveToNext()) {
                        check();val start=spans.getInt(0)
                        val decoded=ChartGeometryBinary.decode(spans.getInt(1),spans.getInt(2),spans.getLong(3),spans.getBlob(4),check)
                        for(i in (if(start==0)0 else 1) until decoded.size)if(start+i<count)points+=decoded[i]
                    }
                }
                require(points.size==count){"CHART_GEOMETRY_SPAN_TRUNCATED"};parts+=ChartGeometryPart(points,hole)
            }
        }
        val feature=metadata.copy(geometry=metadata.geometry.copy(parts=parts))
        val bytes=1024L+parts.sumOf{it.points.size*48L}+feature.attributes.entries.sumOf{(it.key.length+it.value.length)*2L}
        if(bytes<=CACHE_BYTES) {
            while(cache.isNotEmpty()&&cacheBytes+bytes>CACHE_BYTES){val first=cache.entries.iterator();cacheBytes-=first.next().value.bytes;first.remove()}
            cache[row]=Entry(feature,bytes);cacheBytes+=bytes
        }
        return feature
    }
    override suspend fun query(snapshotId:String,bounds:ChartBounds,limit:Int,afterId:String?)=
        querySpatial(snapshotId,bounds,ChartSpatialFilter(),limit,afterId)
    override suspend fun querySpatial(snapshotId:String,bounds:ChartBounds,filter:ChartSpatialFilter,limit:Int,afterId:String?):ChartFeaturePage {
        return page(snapshotId,bounds,filter,limit,afterId,display=false)
    }

    /**
     * 只有三维展示编译使用精确显示窗口；规划仍走自己的生产米制裁剪器。
     * 同一事实/属性/ID 不变，预算只计算真实窗口交集，不能被远处全国海岸顶点占满。
     */
    fun displayReader():ChartFactReader=object:ChartFactReader {
        override suspend fun query(snapshotId:String,bounds:ChartBounds,limit:Int,afterId:String?)=
            page(snapshotId,bounds,ChartSpatialFilter(),limit,afterId,display=true)
        override suspend fun querySpatial(snapshotId:String,bounds:ChartBounds,filter:ChartSpatialFilter,limit:Int,afterId:String?)=
            page(snapshotId,bounds,filter,limit,afterId,display=true)
        override suspend fun rasterWindows(snapshotId:String,bounds:ChartBounds,maxCells:Int)=
            this@NativeFactReader.rasterWindows(snapshotId,bounds,maxCells)
    }

    private suspend fun page(snapshotId:String,bounds:ChartBounds,filter:ChartSpatialFilter,limit:Int,afterId:String?,display:Boolean):ChartFeaturePage {
        require(snapshotId==snapshot.id&&bounds.valid&&limit in 1..8192){"CHART_SNAPSHOT_EXPIRED"}
        val work=currentCoroutineContext();val clauses=ArrayList<String>();val args=ArrayList<String>()
        clauses+=bounds.split().joinToString(" OR ","(",")") {part->
            args+=listOf(part.east,part.west,part.north,part.south).map(Double::toString)
            "(s.min_x<=? AND s.max_x>=? AND s.min_y<=? AND s.max_y>=?)"
        }
        fun values(column:String,values:Collection<String>,includeUnscaled:Boolean=false){
            if(values.isNotEmpty()) {
                val matches="$column IN (${values.joinToString(","){"?"}})"
                clauses+=if(includeUnscaled)"($column IS NULL OR $matches)" else matches
                args+=values
            }
        }
        values("f.cell",filter.cellIds);values("f.kind",filter.kinds.map{it.name})
        values("f.detail_tier",filter.detailTiers.map(Int::toString),includeUnscaled=true)
        values("f.detail_scale",filter.detailScales.map(Int::toString),includeUnscaled=true)
        afterId?.let{clauses+="f.feature_id>?";args+=it}
        val rows=ArrayList<Pair<Long,String>>()
        // 与 Android 生产入口一致，固定 RTree → rowid；不得由 kind/cell 索引扫描全国后再判空间。
        db.rawQuery("SELECT DISTINCT f.rowid,f.feature_id FROM spatial s CROSS JOIN spatial_feature m ON m.id=s.id CROSS JOIN features f ON f.rowid=m.feature_row WHERE ${clauses.joinToString(" AND ")} ORDER BY f.feature_id LIMIT ${limit+1}",args.toTypedArray()).use {
            while(it.moveToNext()){work.ensureActive();rows+=it.getLong(0) to it.getString(1)}
        }
        val selected=rows.take(limit)
        val clipper=if(display)ChartDisplayWindow(bounds,geometryIndex){work.ensureActive()}else null
        val features=selected.mapNotNull{(row,id)->
            work.ensureActive()
            val loaded=feature(row){work.ensureActive()}
            require(loaded.id==id){"CHART_FEATURE_ID_MISMATCH"}
            val local=clipper?.clip(loaded.geometry)?:geometryIndex.window(loaded.geometry,bounds){work.ensureActive()}
            if(display&&local.parts.isEmpty())null else loaded.copy(geometry=local)
        }
        // 空间 bbox 候选可能在精确裁剪后全空；游标必须前进到最后处理的原始对象，不能停页。
        val more=rows.size>limit
        return ChartFeaturePage(features,selected.lastOrNull()?.second.takeIf{more},more)
    }
    override suspend fun rasterWindows(snapshotId:String,bounds:ChartBounds,maxCells:Int):List<ChartRasterWindow> {
        require(snapshotId==snapshot.id&&snapshot.datasets.all{it.rasters.isNullOrEmpty()}){"Desktop raster source is not configured"}
        return emptyList()
    }
    override fun close(){cache.clear();cacheBytes=0;geometryIndex.clear();db.close()}
    companion object {private const val CACHE_BYTES=512L*1024*1024}
}
