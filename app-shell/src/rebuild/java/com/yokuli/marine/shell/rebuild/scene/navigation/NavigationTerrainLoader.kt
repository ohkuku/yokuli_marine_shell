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
    private var factsGeneration=service.state.value.preparedFactsRevision
    private fun refreshFacts(){
        val generation=service.state.value.preparedFactsRevision
        if(factsGeneration!=generation){products.trim();factsGeneration=generation}
    }
    /** 同一修订的已解码块可同步交回画面；不先等待一次 Binder 准备命令。 */
    fun peek(selectedIds:List<String>,origin:GeoPoint,radiusMeters:Double):NavigationChartScene? {
        refreshFacts()
        if(service.state.value.loading)return null
        if(!origin.valid()||abs(origin.lat)>89.8)return null
        val dataset=service.state.value.datasets.firstOrNull{it.id==selectedIds.singleOrNull()&&it.offlineReadable}?:return null
        val radius=radiusMeters.coerceIn(500.0,32_000.0)
        val requests=terrainPreparationRequests(dataset.id,dataset.revision,ChartPoint(origin.lat,origin.lon),radius)
        val tiles=products.nearby(dataset.id,dataset.revision,requests)
        if(tiles.isEmpty())return null
        return composeTerrainRegion(tiles,requests.filter{it.lod==0}.mapTo(linkedSetOf()){it.bounds},
            tiles.first().sourceKey,dataset.revision,origin,radius,tiles.any{it.lod==0})
    }
    fun trimMemory()=products.trim()
    fun clearSource(datasetId:String?=null){if(datasetId==null)products.trim()else products.remove(datasetId)}
    suspend fun load(selectedIds:List<String>,origin:GeoPoint,radiusMeters:Double=2_000.0):NavigationChartScene=
        loadRegion(selectedIds,origin,radiusMeters){}

    suspend fun loadRegion(selectedIds:List<String>,origin:GeoPoint,radiusMeters:Double,retryFailed:Boolean=false,
        publish:suspend(NavigationChartScene)->Unit):NavigationChartScene {
        require(origin.valid()&&abs(origin.lat)<=89.8){"CHART_TERRAIN_POSITION_INVALID"}
        require(selectedIds.size==1){"CHART_SELECTED_DATA_MISSING"}
        val dataset=requireNotNull(service.state.value.datasets.firstOrNull{it.id==selectedIds.single()&&it.offlineReadable}){"CHART_SELECTED_DATA_MISSING"}
        val radius=radiusMeters.coerceIn(500.0,32_000.0)
        val requests=terrainPreparationRequests(dataset.id,dataset.revision,ChartPoint(origin.lat,origin.lon),radius)
        refreshFacts()
        val waiting=LinkedHashMap<ChartTerrainRequest,ChartTerrainStatus>()
        val finished=linkedMapOf<ChartBounds,NavigationChartScene>()
        products.nearby(dataset.id,dataset.revision,requests).forEach{tile->tile.bounds?.let{finished[it]=tile}}
        val expected=requests.filter{it.lod==0}.mapTo(linkedSetOf()){it.bounds}
        var sourceKey=finished.values.firstOrNull()?.sourceKey.orEmpty()
        var lastError:String?=null
        var changed=false
        suspend fun current():NavigationChartScene=withContext(Dispatchers.Default) {
            val latest=service.state.value.datasets.firstOrNull{it.id==dataset.id}
            require(latest?.offlineReadable==true&&latest.revision==dataset.revision){"CHART_SOURCE_CHANGED"}
            composeTerrainRegion(finished.values.toList(),expected,sourceKey,dataset.revision,origin,radius,waiting.isNotEmpty())
        }
        if(finished.isNotEmpty())publish(current())
        if(expected.any{it !in finished}){
            // 全国预制基础层先出现；它只负责观察，不参与可航行证据或船位判断。
            for(status in service.terrainOverview(requests)){
                currentCoroutineContext().ensureActive()
                if(status.phase!=ChartTerrainPhase.READY)continue
                if(sourceKey.isNotEmpty()&&sourceKey!=status.manifestId)finished.clear()
                sourceKey=status.manifestId
                val request=ChartTerrainRequest(dataset.id,dataset.revision,status.bounds,status.lod)
                val scene=read(request,status,dataset)
                if((finished[status.bounds]?.lod?:-1)<scene.lod){finished[status.bounds]=scene;publish(current())}
            }
        }
        // 一次提交保证用户离页后 Core 仍拥有完整区域，而不是一半的 Compose 循环。
        val statuses=service.terrainStatuses(requests)
        val missing=requests.zip(statuses).filter { (_,status)->
            status.phase==ChartTerrainPhase.STALE&&status.reason=="CHART_TERRAIN_NOT_PREPARED" ||
                retryFailed&&status.phase in setOf(ChartTerrainPhase.FAILED,ChartTerrainPhase.CANCELLED)
        }.map{it.first}
        val prepared=if(missing.isEmpty())emptyMap()else missing.zip(service.prepareTerrainRegion(missing)).toMap()
        val initial=requests.zip(statuses).map{(request,status)->prepared[request]?:status}
        if(sourceKey.isNotEmpty()&&sourceKey!=initial.first().manifestId){finished.clear();changed=true}
        sourceKey=initial.first().manifestId
        waiting.putAll(requests.zip(initial))
        var first=true
        // 等待的是 Core 状态，不是 UI 编译。超时保留已准备块，后续完成事件继续读取。
        withTimeoutOrNull(35_000) {
            while(waiting.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val latest=service.state.value.datasets.firstOrNull{it.id==dataset.id}
                require(latest?.offlineReadable==true&&latest.revision==dataset.revision){"CHART_SOURCE_CHANGED"}
                val pending=waiting.keys.toList()
                val replies=if(first)pending.map{waiting.getValue(it)}else service.terrainStatuses(pending)
                for((request,status) in pending.zip(replies)) {
                    currentCoroutineContext().ensureActive()
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
                markers=cached.markers.map{it.copy(sourceId=ids[it.sourceId]?:error("CHART_TERRAIN_PRODUCT_SOURCE"))}).also{products.write(it,dataset.id,dataset.revision)}
        }
        val product=service.readTerrainBlock(request)
        return withContext(Dispatchers.Default) {
            require(product.key==status.key&&product.schema==ChartTerrainBlockCodec.SCHEMA&&ChartTerrainBlockCodec.hash(product.bytes)==product.sha256){"CHART_TERRAIN_PRODUCT_CORRUPT"}
            val tile=ChartTerrainBlockCodec.decode(product.bytes,status.key)
            require(tile.sourceKey==status.manifestId&&tile.bounds==request.bounds&&tile.lod==request.lod){"CHART_TERRAIN_PRODUCT_IDENTITY"}
            tile.toScene(dataset).also{products.write(it,dataset.id,dataset.revision)}
        }
    }

    /** 可见二维海图先提交附近基础层；离页只取消等待，不取消已经接受的 Core 工作。 */
    suspend fun prewarm(selectedIds:List<String>,origin:GeoPoint) {
        delay(800)
        try {
            val dataset=service.state.value.datasets.firstOrNull{it.id==selectedIds.singleOrNull()&&it.offlineReadable}?:return
            val nearest=terrainPreparationRequests(dataset.id,dataset.revision,ChartPoint(origin.lat,origin.lon),4_000.0).filter{it.lod==0}.take(3)
            refreshFacts()
            for(status in service.terrainOverview(nearest).take(2)){
                if(status.phase==ChartTerrainPhase.READY)read(ChartTerrainRequest(dataset.id,dataset.revision,status.bounds,status.lod),status,dataset)
            }
            val statuses=service.terrainStatuses(nearest)
            for((request,status) in nearest.zip(statuses))if(status.phase==ChartTerrainPhase.READY)read(request,status,dataset)
            // 只排最近一个尚未准备的基础块，二维浏览不会无界启动详细三维编译。
            nearest.zip(statuses).firstOrNull{it.second.phase==ChartTerrainPhase.STALE&&it.second.reason=="CHART_TERRAIN_NOT_PREPARED"}
                ?.first?.let{service.prepareTerrain(it)}
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

/** 同一个区域装配器供立即缓存命中与异步分块更新使用，不重新生成任何地形。 */
private fun composeTerrainRegion(
    tiles:List<NavigationChartScene>,expected:Set<ChartBounds>,sourceKey:String,revision:Long,
    origin:GeoPoint,radius:Double,waiting:Boolean,
):NavigationChartScene {
    val requested=terrainBounds(origin,radius)
    val active=tiles.filter{tile->
        val bounds=tile.bounds?:return@filter true
        val children=tiles.filter{other->other.bounds?.let{child->child!=bounds&&containsBounds(bounds,child)}==true}
        children.isEmpty()||!coversVisibleIntersection(bounds,requested,children.mapNotNull{it.bounds})
    }
    val leaves=active.filter{candidate->active.none{parent->parent.bounds!=candidate.bounds&&
        parent.bounds?.let{a->candidate.bounds?.let{b->containsBounds(a,b)}}==true}}
    val warnings=leaves.flatMap{it.warnings}.toMutableSet()
    if(leaves.any{it.hasGeometry})warnings.remove(NavigationChartWarning.NO_DATA)
    if(expected.any{bound->leaves.none{it.bounds==bound}}||waiting)warnings+=NavigationChartWarning.PARTIAL_CONTENT
    val markers=leaves.flatMap{it.markers}.distinctBy{it.id}.map{it.copy(
        eastMeters=wrappedLongitude(it.point.lon-origin.lon)*111_320*cos(Math.toRadians(origin.lat)),
        southMeters=-(it.point.lat-origin.lat)*111_320)}
    return NavigationChartScene(terrainHash("region:$sourceKey:$origin:$radius:"+leaves.joinToString{it.sceneKey}),origin,radius,
        null,null,leaves.flatMap{it.sources}.distinctBy{it.id},markers,warnings,leaves.sumOf{it.triangleCount},
        leaves.minOfOrNull{it.minElevationMeters}?:0.0,leaves.maxOfOrNull{it.maxElevationMeters}?:0.0,revision,
        NavigationTerrainCoverage(leaves.sumOf{it.coverage.rasterSampleCount},leaves.sumOf{it.coverage.missingRasterSamples},
            markers.count{it.kind==NavigationChartMarkerKind.SOUNDING},markers.count{it.kind!=NavigationChartMarkerKind.SOUNDING}),
        sourceKey,terrainBounds(origin,radius),leaves,expected.size)
}

private fun containsBounds(a:ChartBounds,b:ChartBounds)=a.west<=b.west&&a.east>=b.east&&a.south<=b.south&&a.north>=b.north

/** 详细块完整覆盖当前可见的祖先交集才换层，不能以相加重复覆盖面积误判完成。 */
private fun coversVisibleIntersection(parent:ChartBounds,view:ChartBounds,children:List<ChartBounds>):Boolean {
    val regions=view.split().mapNotNull{part->
        val w=max(parent.west,part.west);val e=min(parent.east,part.east)
        val s=max(parent.south,part.south);val n=min(parent.north,part.north)
        if(w<e&&s<n)ChartBounds(w,s,e,n)else null
    }
    return regions.isNotEmpty()&&regions.all{region->
        val clipped=children.mapNotNull{part->
            val w=max(region.west,part.west);val e=min(region.east,part.east)
            val s=max(region.south,part.south);val n=min(region.north,part.north)
            if(w<e&&s<n)ChartBounds(w,s,e,n)else null
        }
        val xs=(listOf(region.west,region.east)+clipped.flatMap{listOf(it.west,it.east)}).distinct().sorted()
        (1 until xs.size).all{index->
            val x=(xs[index-1]+xs[index])/2
            val intervals=clipped.filter{it.west<=x&&it.east>=x}.sortedBy{it.south}
            var north=region.south
            for(part in intervals){if(part.south>north+1e-9)break;north=max(north,part.north)}
            north>=region.north-1e-9
        }
    }
}
