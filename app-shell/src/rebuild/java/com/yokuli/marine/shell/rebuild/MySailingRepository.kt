package com.yokuli.marine.shell.rebuild

import androidx.compose.runtime.*
import com.yokuli.anchorwatch.api.MarineContentService
import com.yokuli.anchorwatch.data.database.AnchorSessionEntity
import com.yokuli.anchorwatch.data.database.entity.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/** 我的航行读写门面：旧锚地走内容端口，Shell 坐标与计划航线走文件回执；沿用原 ID 和关系。 */
class MySailingRepository(private val os: OsStore) {
    private val content: MarineContentService = os.content
    val photos = content.photos
    var locations by mutableStateOf<List<AnchoragePlaceEntity>>(emptyList())
        private set
    var spots by mutableStateOf<List<AnchorageSpotEntity>>(emptyList())
        private set
    var collections by mutableStateOf<List<AnchorageCollectionEntity>>(emptyList())
        private set
    var archivedLocations by mutableStateOf<List<AnchoragePlaceEntity>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var error by mutableStateOf(false)
        private set
    val coordinates:List<Place> get() = spots.mapNotNull { spot ->
        val parent=locations.firstOrNull { it.id==spot.placeId } ?: return@mapNotNull null
        Place("spot:${spot.id}",if(spot.name in listOf("Main spot","Chart reference")) parent.displayName else "${parent.displayName} · ${spot.name}",
            GeoPoint(spot.latitude,spot.longitude),listOf(parent.personalNotes,spot.approachNotes,spot.personalNotes).filter(String::isNotBlank).distinct().joinToString("\n"),PlaceKind.ANCHORAGE)
    }
    private var catalogSubscription: Job? = null
    init { observeCatalog() }

    /** 只恢复内容订阅，不重建仓库、不清空已读资料，也不重新提交任何写入。 */
    fun retryLoading() {
        if (error) observeCatalog()
    }

    private fun observeCatalog() {
        catalogSubscription?.cancel()
        catalogSubscription = os.scope.launch {
            content.library.retryWhen { cause, attempt ->
                if (cause is CancellationException) throw cause
                error = true
                loaded = true
                // 暂时的存储故障不会永久终止进程级目录订阅；同时避免紧密重试占用资源。
                delay((1_000L shl attempt.coerceAtMost(5).toInt()).coerceAtMost(30_000L))
                true
            }.collect { catalog ->
                locations = catalog.places
                spots = catalog.spots
                collections = catalog.collections
                archivedLocations = catalog.archivedPlaces
                loaded = true
                error = false
            }
        }
    }
    suspend fun bundle(id: Long) = content.bundle(id)
    suspend fun updatePlace(place: AnchoragePlaceEntity) = content.updatePlace(place)
    suspend fun updateSpot(spot: AnchorageSpotEntity) = content.updateSpot(spot)
    suspend fun createSpot(placeId: Long, name: String, point: GeoPoint) = content.createSpot(placeId, name, point.lat, point.lon)
    suspend fun archivePlace(id: Long) = content.archivePlace(id)
    suspend fun restorePlace(id: Long) = content.restorePlace(id)
    suspend fun createCollection(name: String) = content.createCollection(name)
    suspend fun toggleCollection(collectionId: Long, placeId: Long) = content.toggleCollection(collectionId, placeId)
    fun observeCollectionMembers(collectionId: Long) = content.observeCollectionMembers(collectionId)
    suspend fun anchorTrackPage(sessionId: Long, afterTimestamp: Long, afterId: Long, limit: Int = 1000) =
        content.anchorTrackPage(sessionId, afterTimestamp, afterId, limit)
    suspend fun saveAnchorage(session: AnchorSessionEntity, name: String) =
        content.saveAnchorage(session.id, name, os.t("实际锚位", "anchor position"))
    /** 地点和计划航线的写入口；读模型可先显示，只有 DurableCommit 成功才提示已保存。 */
    fun put(place:Place):DurableCommit {
        require(place.point.valid() && place.name.isNotBlank())
        os.places=os.places.filterNot { it.id==place.id }+place
        return os.saveWithFeedback("地点已保存", "Place saved", "place:${place.id}")
    }
    fun remove(id:String):DurableCommit {
        os.places=os.places.filterNot { it.id==id }
        return os.saveWithFeedback("地点已删除", "Place deleted")
    }
    fun putRoute(route:Route):DurableCommit {
        require(route.name.isNotBlank() && route.points.isNotEmpty() && route.points.all(GeoPoint::valid))
        os.routes=os.routes.filterNot { it.id==route.id }+route.copy(points=route.points.toList())
        return os.saveWithFeedback("航线已保存", "Route saved", "route:${route.id}")
    }
    fun removeRoute(id:String):DurableCommit {
        os.routes=os.routes.filterNot { it.id==id }
        return os.saveWithFeedback("航线已删除", "Route deleted")
    }
    fun import(content:Gpx.Contents,skipDuplicates:Boolean):DurableCommit {
        val newPlaces=mutableListOf<Place>(); val newRoutes=mutableListOf<Route>()
        content.places.forEach { candidate ->
            if(!skipDuplicates || (os.allPlaces+newPlaces).none { it.name==candidate.name && distance(it.point,candidate.point)<1.0 }) newPlaces+=candidate
        }
        content.routes.forEach { candidate ->
            if(!skipDuplicates || (os.routes+newRoutes).none { it.name==candidate.name && it.points==candidate.points }) newRoutes+=candidate
        }
        os.places=os.places+newPlaces;os.routes=os.routes+newRoutes
        return os.saveWithFeedback("已导入 ${newPlaces.size} 个坐标、${newRoutes.size} 条路线",
            "Imported ${newPlaces.size} places and ${newRoutes.size} routes", "places")
    }
}
