package com.yokuli.anchorwatch.api

import android.net.Uri
import androidx.room.withTransaction
import com.yokuli.anchorwatch.data.anchorage.AnchorageLibraryRepository
import com.yokuli.anchorwatch.data.anchorage.AnchoragePhotoRepository
import com.yokuli.anchorwatch.data.anchorage.AnchoragePlaceRepository
import com.yokuli.anchorwatch.data.anchorage.AnchorageSaveDraftFactory
import com.yokuli.anchorwatch.data.anchorage.AnchorageSavePlaceInput
import com.yokuli.anchorwatch.data.anchorage.AnchorageSaveRepository
import com.yokuli.anchorwatch.data.anchorage.AnchorageSaveRequest
import com.yokuli.anchorwatch.data.anchorage.AnchorageSaveSpotInput
import com.yokuli.anchorwatch.data.anchorage.AnchorageSpotRepository
import com.yokuli.anchorwatch.data.database.AppDatabase
import com.yokuli.anchorwatch.data.database.entity.AnchorageCollectionEntity
import com.yokuli.anchorwatch.data.database.entity.AnchorageCollectionPlaceCrossRef
import com.yokuli.anchorwatch.data.database.entity.AnchoragePhotoEntity
import com.yokuli.anchorwatch.data.database.entity.AnchoragePlaceEntity
import com.yokuli.anchorwatch.data.database.entity.AnchorageSpotEntity
import com.yokuli.anchorwatch.domain.anchorage.AnchoragePlaceType
import com.yokuli.anchorwatch.domain.anchorage.AnchoragePlanningStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** 本地实现只组合原有单例数据库和仓库；不自行创建数据库、复制记录或维护另一份缓存。 */
@Singleton
class LocalMarineContentService @Inject constructor(
    private val database: AppDatabase,
    private val libraryRepository: AnchorageLibraryRepository,
    private val placeRepository: AnchoragePlaceRepository,
    private val spotRepository: AnchorageSpotRepository,
    private val photoRepository: AnchoragePhotoRepository,
    private val saver: AnchorageSaveRepository,
) : MarineContentService {
    private val saveMutex = Mutex()

    override val library = database.invalidationTracker.createFlow(
        "anchorage_places", "anchorage_spots", "anchorage_collections", "anchorage_collection_places",
        emitInitialState = true,
    ).map {
        database.withTransaction {
            val places = database.anchoragePlaceDao().allNow()
            MarineLibrarySnapshot(
                places = places.filterNot { it.archived }.sortedByDescending { it.lastVisitedAt ?: it.updatedAt },
                spots = database.anchorageSpotDao().allNow().sortedByDescending { it.lastVisitedAt ?: it.updatedAt },
                collections = database.anchorageCollectionDao().allNow().sortedWith(compareBy<AnchorageCollectionEntity> { it.sortOrder }.thenBy { it.name }),
                archivedPlaces = places.filter { it.archived },
                memberships = database.anchorageCollectionDao().membershipsNow(),
            )
        }
    }.distinctUntilChanged()

    override val photos: MarinePhotoService = object : MarinePhotoService {
        override suspend fun import(placeId: Long, source: Uri, caption: String) = photoRepository.import(placeId, source, caption)
        override suspend fun delete(value: AnchoragePhotoEntity) { photoRepository.delete(value) }
        override fun file(value: AnchoragePhotoEntity, thumbnail: Boolean) = photoRepository.file(value, thumbnail)
    }

    override fun observeRecentAlarmEvents(limit: Int) = database.anchorDao().observeRecentAlarmEvents(limit.coerceIn(1, 1000))

    override fun observeCollectionMembers(collectionId: Long) = database.invalidationTracker.createFlow(
        "anchorage_collection_places", emitInitialState = true,
    ).map {
        database.anchorageCollectionDao().membershipsNow().filter { it.collectionId == collectionId }.map { it.placeId }
    }.distinctUntilChanged()

    override suspend fun anchorTrackPage(sessionId: Long, afterTimestamp: Long, afterId: Long, limit: Int) =
        database.anchorDao().pointsPage(sessionId, afterTimestamp, afterId, limit.coerceIn(1, 5000))

    override suspend fun bundle(placeId: Long) = libraryRepository.bundle(placeId)
    override suspend fun updatePlace(value: AnchoragePlaceEntity) = placeRepository.save(value.copy(updatedAt = System.currentTimeMillis()))
    override suspend fun updateSpot(value: AnchorageSpotEntity) = spotRepository.save(value.copy(updatedAt = System.currentTimeMillis()))

    override suspend fun createSpot(placeId: Long, name: String, latitude: Double, longitude: Double): Long {
        val now = System.currentTimeMillis()
        return spotRepository.save(AnchorageSpotEntity(
            placeId = placeId, name = name, spotType = "PLANNED_REFERENCE", latitude = latitude, longitude = longitude,
            coordinateSource = "MAP_SELECTED", verificationStatus = "PLANNED", createdAt = now, updatedAt = now,
        ))
    }

    override suspend fun archivePlace(placeId: Long) {
        database.withTransaction {
            val current = placeRepository.get(placeId) ?: return@withTransaction
            check(database.anchorDao().active()?.anchoragePlaceId != placeId) { "Active watch" }
            updatePlace(current.copy(archived = true))
        }
    }

    override suspend fun restorePlace(placeId: Long) {
        database.withTransaction { placeRepository.get(placeId)?.let { updatePlace(it.copy(archived = false)) } }
    }

    override suspend fun createCollection(name: String): Long = database.withTransaction {
        require(name.isNotBlank()) { "A collection needs a name" }
        val now = System.currentTimeMillis()
        database.anchorageCollectionDao().insert(AnchorageCollectionEntity(
            name = name.trim(), sortOrder = database.anchorageCollectionDao().allNow().size, createdAt = now, updatedAt = now,
        ))
    }

    override suspend fun toggleCollection(collectionId: Long, placeId: Long) {
        database.withTransaction {
            val dao = database.anchorageCollectionDao()
            if (dao.forPlace(placeId).any { it.id == collectionId }) dao.removeMembership(collectionId, placeId)
            else dao.setMembership(AnchorageCollectionPlaceCrossRef(collectionId, placeId, System.currentTimeMillis()))
        }
    }

    override suspend fun saveAnchorage(sessionId: Long, name: String, defaultSpotName: String): Long = saveMutex.withLock {
        database.withTransaction {
            database.anchorageVisitDao().bySession(sessionId)?.let { return@withTransaction it.placeId }
            val session = requireNotNull(database.anchorDao().session(sessionId)) { "Anchor session no longer exists" }
            val existing = session.anchoragePlaceId?.let { placeRepository.get(it) }
            val spot = session.anchorageSpotId?.let { spotRepository.get(it) }?.takeIf { it.placeId == existing?.id }
            val place = existing?.let {
                AnchorageSavePlaceInput(
                    existingPlaceId = it.id, displayName = it.displayName,
                    placeType = runCatching { AnchoragePlaceType.valueOf(it.placeType) }.getOrDefault(AnchoragePlaceType.UNKNOWN),
                    primaryRegionId = it.primaryRegionId, description = it.description, personalNotes = it.personalNotes,
                    favorite = it.favorite, contextRegionIds = database.anchorageMetadataDao().regionsForPlace(it.id).map { ref -> ref.regionId },
                    planningStatus = runCatching { AnchoragePlanningStatus.valueOf(it.planningStatus) }.getOrDefault(AnchoragePlanningStatus.NONE),
                )
            } ?: AnchorageSavePlaceInput(displayName = name, favorite = true)
            saver.save(AnchorageSaveRequest(
                AnchorageSaveDraftFactory.fromSession(session), place,
                AnchorageSaveSpotInput(
                    existingSpotId = spot?.id, name = spot?.name ?: defaultSpotName,
                    approachNotes = spot?.approachNotes.orEmpty(), personalNotes = spot?.personalNotes.orEmpty(),
                ),
            )).placeId
        }
    }
}
