package com.yokuli.marine.shell.rebuild

import androidx.compose.runtime.*
import com.yokuli.anchorwatch.api.MarineContentService
import com.yokuli.anchorwatch.data.database.AnchorSessionEntity
import com.yokuli.anchorwatch.data.database.entity.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 应用显示投影：持久化操作只调用内容端口，沿用原记录 ID 和关系。 */
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
    init {
        os.scope.launch {
            runCatching {
                content.library.collect { catalog ->
                    locations = catalog.places
                    spots = catalog.spots
                    collections = catalog.collections
                    archivedLocations = catalog.archivedPlaces
                    loaded = true
                    error = false
                }
            }.onFailure { if (it is CancellationException) throw it; error = true; loaded = true }
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
    fun put(place:Place) { require(place.point.valid() && place.name.isNotBlank());os.places=os.places.filterNot { it.id==place.id }+place;os.save() }
    fun remove(id:String) { os.places=os.places.filterNot { it.id==id };os.save() }
    fun import(content:Gpx.Contents,skipDuplicates:Boolean):Pair<Int,Int> {
        val newPlaces=mutableListOf<Place>(); val newRoutes=mutableListOf<Route>()
        content.places.forEach { candidate ->
            if(!skipDuplicates || (os.allPlaces+newPlaces).none { it.name==candidate.name && distance(it.point,candidate.point)<1.0 }) newPlaces+=candidate
        }
        content.routes.forEach { candidate ->
            if(!skipDuplicates || (os.routes+newRoutes).none { it.name==candidate.name && it.points==candidate.points }) newRoutes+=candidate
        }
        os.places=os.places+newPlaces;os.routes=os.routes+newRoutes;os.save()
        return newPlaces.size to newRoutes.size
    }
}
