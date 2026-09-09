package com.yokuli.marine.shell.rebuild

import androidx.compose.runtime.*
import com.yokuli.anchorwatch.data.anchorage.*
import com.yokuli.anchorwatch.data.database.AppDatabase
import com.yokuli.anchorwatch.data.database.entity.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SailingDataAccess {
    fun database(): AppDatabase
    fun library(): AnchorageLibraryRepository
    fun places(): AnchoragePlaceRepository
    fun spots(): AnchorageSpotRepository
    fun photos(): AnchoragePhotoRepository
    fun saver(): AnchorageSaveRepository
}

/** One logical catalog over existing user records. Room IDs and visit/media relationships survive. */
class MySailingRepository(private val os: OsStore) {
    private val saveMutex=Mutex()
    private val access=EntryPointAccessors.fromApplication(os.context.applicationContext,SailingDataAccess::class.java)
    val database=access.database()
    val photos=access.photos()
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
                combine(access.library().places,database.anchorageSpotDao().observeAll(),access.library().collections) { places,points,groups -> Triple(places,points,groups) }
                    .collect { (places,points,groups) ->
                        locations=places;spots=points;collections=groups
                        archivedLocations=database.anchoragePlaceDao().allNow().filter {it.archived}
                        loaded=true;error=false
                    }
            }.onFailure { if(it is CancellationException) throw it;error=true;loaded=true }
        }
    }
    suspend fun bundle(id:Long)=access.library().bundle(id)
    suspend fun updatePlace(place:AnchoragePlaceEntity) = access.places().save(place.copy(updatedAt=System.currentTimeMillis()))
    suspend fun updateSpot(spot:AnchorageSpotEntity) = access.spots().save(spot.copy(updatedAt=System.currentTimeMillis()))
    suspend fun createSpot(placeId:Long,name:String,point:GeoPoint):Long = access.spots().save(AnchorageSpotEntity(
        placeId=placeId,name=name,spotType="PLANNED_REFERENCE",latitude=point.lat,longitude=point.lon,
        coordinateSource="MAP_SELECTED",verificationStatus="PLANNED",createdAt=System.currentTimeMillis(),updatedAt=System.currentTimeMillis()))
    suspend fun archivePlace(id:Long) {
        val current=access.places().get(id) ?: return
        check(database.anchorDao().active()?.anchoragePlaceId!=id) { "Active watch" }
        updatePlace(current.copy(archived=true))
    }
    suspend fun restorePlace(id:Long) { access.places().get(id)?.let {updatePlace(it.copy(archived=false))} }
    suspend fun createCollection(name:String) {
        val now=System.currentTimeMillis()
        database.anchorageCollectionDao().insert(AnchorageCollectionEntity(name=name.trim(),sortOrder=collections.size,createdAt=now,updatedAt=now))
    }
    suspend fun toggleCollection(collectionId:Long,placeId:Long) {
        val dao=database.anchorageCollectionDao()
        if(dao.forPlace(placeId).any { it.id==collectionId }) dao.removeMembership(collectionId,placeId)
        else dao.setMembership(AnchorageCollectionPlaceCrossRef(collectionId,placeId,System.currentTimeMillis()))
    }
    suspend fun saveAnchorage(session:com.yokuli.anchorwatch.data.database.AnchorSessionEntity,name:String):Long = saveMutex.withLock {
        database.anchorageVisitDao().bySession(session.id)?.let { return@withLock it.placeId }
        val existing=session.anchoragePlaceId?.let {access.places().get(it)}
        val spot=session.anchorageSpotId?.let {access.spots().get(it)}?.takeIf {it.placeId==existing?.id}
        val place=existing?.let {AnchorageSavePlaceInput(existingPlaceId=it.id,displayName=it.displayName,
            placeType=runCatching {com.yokuli.anchorwatch.domain.anchorage.AnchoragePlaceType.valueOf(it.placeType)}.getOrDefault(com.yokuli.anchorwatch.domain.anchorage.AnchoragePlaceType.UNKNOWN),
            primaryRegionId=it.primaryRegionId,description=it.description,personalNotes=it.personalNotes,favorite=it.favorite,
            contextRegionIds=database.anchorageMetadataDao().regionsForPlace(it.id).map{ref->ref.regionId},
            planningStatus=runCatching {com.yokuli.anchorwatch.domain.anchorage.AnchoragePlanningStatus.valueOf(it.planningStatus)}.getOrDefault(com.yokuli.anchorwatch.domain.anchorage.AnchoragePlanningStatus.NONE))}
            ?: AnchorageSavePlaceInput(displayName=name,favorite=true)
        access.saver().save(AnchorageSaveRequest(AnchorageSaveDraftFactory.fromSession(session),place,
            AnchorageSaveSpotInput(existingSpotId=spot?.id,name=spot?.name?:os.t("实际锚位","anchor position"),
                approachNotes=spot?.approachNotes.orEmpty(),personalNotes=spot?.personalNotes.orEmpty()))).placeId
    }
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
