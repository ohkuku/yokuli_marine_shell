package com.yokuli.marine.shell.rebuild.scene.navigation

import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.marine.chart.terrain.ChartTerrainBlockCodec
import com.yokuli.runtime.marine.chart.terrain.ChartTerrainTile
import kotlinx.coroutines.*
import kotlin.math.*

/** 页面只订阅准备状态和读取现成块；JTS、事实查询与网格生成均在 Core 的可恢复作业内。 */
class NavigationTerrainLoader(private val service:ChartDataService) {
    private val products=NavigationTerrainProducts()
    fun trimMemory()=products.trim()
    fun clearSource(datasetId:String?=null){if(datasetId==null)products.trim()}
    suspend fun load(selectedIds:List<String>,origin:GeoPoint,radiusMeters:Double=2_000.0):NavigationChartScene=
        loadRegion(selectedIds,origin,radiusMeters){}

    suspend fun loadRegion(selectedIds:List<String>,origin:GeoPoint,radiusMeters:Double,
        publish:suspend(NavigationChartScene)->Unit):NavigationChartScene {
        require(origin.valid()&&abs(origin.lat)<=89.8){"CHART_TERRAIN_POSITION_INVALID"}
        require(selectedIds.size==1){"CHART_SELECTED_DATA_MISSING"}
        val dataset=requireNotNull(service.state.value.datasets.firstOrNull{it.id==selectedIds.single()&&it.offlineReadable}){"CHART_SELECTED_DATA_MISSING"}
        val radius=radiusMeters.coerceIn(500.0,32_000.0)
        val requests=terrainPreparationRequests(dataset.id,dataset.revision,ChartPoint(origin.lat,origin.lon),radius)
        // 一次提交保证用户离页后 Core 仍拥有完整区域，而不是一半的 Compose 循环。
        val initial=service.prepareTerrainRegion(requests)
        val waiting=requests.zip(initial).toMap(LinkedHashMap())
        val finished=linkedMapOf<ChartBounds,NavigationChartScene>()
        val expected=requests.filter{it.lod==0}.mapTo(linkedSetOf()){it.bounds}
        var lastError:String?=null;var sourceKey=initial.first().manifestId
        var changed=false
        suspend fun current():NavigationChartScene=withContext(Dispatchers.Default) {
            val leaves=finished.values.filter{candidate->finished.values.none{parent->
                parent.bounds!=candidate.bounds&&parent.bounds?.let{a->candidate.bounds?.let{b->a.west<=b.west&&a.east>=b.east&&a.south<=b.south&&a.north>=b.north}}==true
            }}
            val warnings=leaves.flatMap{it.warnings}.toMutableSet()
            if(leaves.any{it.hasGeometry})warnings.remove(NavigationChartWarning.NO_DATA)
            if(expected.any{it !in finished}||waiting.isNotEmpty())warnings+=NavigationChartWarning.PARTIAL_CONTENT
            val markers=leaves.flatMap{it.markers}.distinctBy{it.id}.map{it.copy(
                eastMeters=wrappedLongitude(it.point.lon-origin.lon)*111_320*cos(Math.toRadians(origin.lat)),
                southMeters=-(it.point.lat-origin.lat)*111_320)}
            NavigationChartScene(terrainHash("region:$sourceKey:$origin:$radius:"+leaves.joinToString{it.sceneKey}),origin,radius,
                null,null,leaves.flatMap{it.sources}.distinctBy{it.id},markers,warnings,leaves.sumOf{it.triangleCount},
                leaves.minOfOrNull{it.minElevationMeters}?:0.0,leaves.maxOfOrNull{it.maxElevationMeters}?:0.0,dataset.revision,
                NavigationTerrainCoverage(leaves.sumOf{it.coverage.rasterSampleCount},leaves.sumOf{it.coverage.missingRasterSamples},
                    markers.count{it.kind==NavigationChartMarkerKind.SOUNDING},markers.count{it.kind!=NavigationChartMarkerKind.SOUNDING}),
                sourceKey,terrainBounds(origin,radius),leaves,expected.size)
        }
        var first=true
        // 等待的是 Core 状态，不是 UI 编译。超时保留已准备块，后续完成事件继续读取。
        withTimeoutOrNull(35_000) {
            while(waiting.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val latest=service.state.value.datasets.firstOrNull{it.id==dataset.id}
                require(latest?.offlineReadable==true&&latest.revision==dataset.revision){"CHART_SOURCE_CHANGED"}
                for((request,cachedStatus) in waiting.toMap()) {
                    currentCoroutineContext().ensureActive()
                    val status=if(first)cachedStatus else service.terrainStatus(request)
                    require(status.manifestId==sourceKey){"CHART_SOURCE_CHANGED"}
                    when(status.phase) {
                        ChartTerrainPhase.READY-> {
                            try {
                                val scene=read(request,status,dataset)
                                if((finished[request.bounds]?.lod?:-1)<request.lod) {finished[request.bounds]=scene;changed=true}
                                waiting.remove(request)
                            }catch(cancel:CancellationException){throw cancel}
                            catch(failure:Exception){lastError=failure.message;waiting.remove(request)}
                        }
                        ChartTerrainPhase.FAILED,ChartTerrainPhase.SUBDIVIDED-> {
                            waiting.remove(request);lastError=status.reason
                            val size=minOf(request.bounds.north-request.bounds.south,request.bounds.east-request.bounds.west)*111_320
                            if(status.reason in setOf("CHART_TERRAIN_SUBDIVIDE_REQUIRED","CHART_TERRAIN_MODEL_LIMIT")&&size>400&&expected.size+3<=24) {
                                val children=request.splitTerrainRequest()
                                val replies=service.prepareTerrainRegion(children)
                                children.zip(replies).forEach{(child,reply)->waiting[child]=reply}
                                expected.remove(request.bounds);expected.addAll(children.map{it.bounds})
                                // 不把一块已有基础层撤下；子块全部可显示后才交换覆盖范围。
                            }
                        }
                        ChartTerrainPhase.CANCELLED->{waiting.remove(request);lastError="CHART_TERRAIN_CANCELLED"}
                        ChartTerrainPhase.STALE->{waiting.remove(request);lastError=status.reason?:"CHART_SOURCE_CHANGED"}
                        ChartTerrainPhase.QUEUED,ChartTerrainPhase.PREPARING->Unit
                    }
                }
                // 完整子覆盖就绪才撤销父块，避免重叠与缺口。
                for(parent in finished.keys.toList()) {
                    val children=ChartTerrainRequest(dataset.id,dataset.revision,parent).splitTerrainRequest().map{it.bounds}
                    if(children.all{it in finished}){finished.remove(parent);changed=true}
                }
                if(changed){publish(current());changed=false}
                first=false
                if(waiting.isNotEmpty())delay(if(finished.isEmpty())180 else 500)
            }
        }
        if(finished.isEmpty())error(lastError?:"CHART_TERRAIN_PREPARING")
        return current()
    }

    private suspend fun read(request:ChartTerrainRequest,status:ChartTerrainStatus,dataset:ChartDataset):NavigationChartScene {
        products.read(status.key,status.manifestId)?.let{cached->
            val ids=cached.sources.associate{it.id to "${dataset.id}/${it.cellId}/${it.kind.name}/${it.verticalReference.orEmpty()}"}
            return cached.copy(datasetRevision=dataset.revision,sources=cached.sources.map{it.copy(id=requireNotNull(ids[it.id]),datasetId=dataset.id)},
                markers=cached.markers.map{it.copy(sourceId=ids[it.sourceId]?:error("CHART_TERRAIN_PRODUCT_SOURCE"))})
        }
        val product=service.readTerrainBlock(request)
        return withContext(Dispatchers.Default) {
            require(product.key==status.key&&product.schema==ChartTerrainBlockCodec.SCHEMA&&ChartTerrainBlockCodec.hash(product.bytes)==product.sha256){"CHART_TERRAIN_PRODUCT_CORRUPT"}
            val tile=ChartTerrainBlockCodec.decode(product.bytes,status.key)
            require(tile.sourceKey==status.manifestId&&tile.bounds==request.bounds&&tile.lod==request.lod){"CHART_TERRAIN_PRODUCT_IDENTITY"}
            tile.toScene(dataset).also(products::write)
        }
    }

    /** 可见二维海图先提交附近基础层；离页只取消等待，不取消已经接受的 Core 工作。 */
    suspend fun prewarm(selectedIds:List<String>,origin:GeoPoint) {
        delay(800)
        try {
            val dataset=service.state.value.datasets.firstOrNull{it.id==selectedIds.singleOrNull()&&it.offlineReadable}?:return
            val nearest=terrainPreparationRequests(dataset.id,dataset.revision,ChartPoint(origin.lat,origin.lon),4_000.0).first()
            service.prepareTerrain(nearest)
        }catch(cancel:CancellationException){throw cancel}catch(_:Exception){}
    }
    suspend fun prefetch(selectedIds:List<String>,origin:GeoPoint,radiusMeters:Double=2_000.0) {
        delay(400)
        try {
            val dataset=service.state.value.datasets.firstOrNull{it.id==selectedIds.singleOrNull()&&it.offlineReadable}?:return
            service.prepareTerrainRegion(terrainPreparationRequests(dataset.id,dataset.revision,ChartPoint(origin.lat,origin.lon),radiusMeters))
        }catch(cancel:CancellationException){throw cancel}catch(_:Exception){}
    }
}

/** 来源身份重新绑定本次安装；产品内的旧 datasetId/revision 不进入当前导航选择。 */
private fun ChartTerrainTile.toScene(dataset:ChartDataset):NavigationChartScene {
    val sourceIds=sources.associate{it.id to "${dataset.id}/${it.cellId}/${it.kind.name}/${it.verticalReference.orEmpty()}"}
    return NavigationChartScene(key,GeoPoint(origin.latitude,origin.longitude),radiusMeters,surfaceGlb,seabedGlb,
        sources.map{NavigationChartSource(requireNotNull(sourceIds[it.id]),dataset.id,it.cellId,it.name,NavigationChartSourceKind.valueOf(it.kind.name),it.resolutionMeters,it.verticalReference,it.referenceOnly)},
        markers.map{NavigationChartMarker(it.id,it.title,NavigationChartMarkerKind.valueOf(it.kind.name),GeoPoint(it.point.latitude,it.point.longitude),it.eastMeters,it.elevationMeters,it.southMeters,
            sourceIds[it.sourceId]?:error("CHART_TERRAIN_PRODUCT_SOURCE"),it.symbolic,it.depthLowerMeters,it.depthUpperMeters)},
        warnings.mapTo(linkedSetOf()){NavigationChartWarning.valueOf(it.name)},triangleCount,minElevationMeters,maxElevationMeters,dataset.revision,
        NavigationTerrainCoverage(coverage.rasterSampleCount,coverage.missingRasterSamples,coverage.displayedSoundings,coverage.displayedFacilities),sourceKey,bounds,lod=lod)
}
internal fun terrainSourceKey(dataset:ChartDataset)=terrainHash("terrain-5:${dataset.id}:${dataset.revision}:${dataset.preparing}:${dataset.preparationIssue}")
private fun wrappedLongitude(value:Double)=((value+180.0)%360.0+360.0)%360.0-180.0
suspend fun loadNavigationTerrain(service:ChartDataService,selectedIds:List<String>,origin:GeoPoint,radiusMeters:Double=2_000.0):NavigationChartScene=
    NavigationTerrainLoader(service).loadRegion(selectedIds,origin,radiusMeters){}
fun navigationTerrainRadius(dataset:ChartDataset?,point:GeoPoint,requestedRadiusMeters:Double=4_000.0):Double=
    requestedRadiusMeters.takeIf(Double::isFinite)?.coerceIn(500.0,32_000.0)?:4_000.0
internal fun terrainBounds(origin:GeoPoint,radius:Double):ChartBounds {
    val dy=radius/111_320.0;val dx=dy/cos(Math.toRadians(origin.lat)).coerceAtLeast(.003)
    fun wrap(value:Double)=((value+180.0)%360.0+360.0)%360.0-180.0
    return ChartBounds(wrap(origin.lon-dx),(origin.lat-dy).coerceAtLeast(-90.0),wrap(origin.lon+dx),(origin.lat+dy).coerceAtMost(90.0))
}
