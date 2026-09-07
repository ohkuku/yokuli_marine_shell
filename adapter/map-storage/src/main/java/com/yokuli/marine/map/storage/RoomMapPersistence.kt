package com.yokuli.marine.map.storage

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.room.Room
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.GpxImportRecord
import com.yokuli.marine.map.domain.ImportedTrack
import com.yokuli.marine.map.domain.ImportedTrackEditability
import com.yokuli.marine.map.domain.ImportedTrackPoint
import com.yokuli.marine.map.domain.ImportedTrackSegment
import com.yokuli.marine.map.domain.ManualRouteDraft
import com.yokuli.marine.map.domain.MapLibrarySnapshot
import com.yokuli.marine.map.domain.MapLoadResult
import com.yokuli.marine.map.domain.MapPersistenceAck
import com.yokuli.marine.map.domain.MapPersistencePort
import com.yokuli.marine.map.domain.MapReadFailure
import com.yokuli.marine.map.domain.MapSessionSnapshot
import com.yokuli.marine.map.domain.PlaceCategory
import com.yokuli.marine.map.domain.PlaceRevisionReference
import com.yokuli.marine.map.domain.SavedPlace
import com.yokuli.marine.map.domain.SavedRoute
import com.yokuli.marine.map.storage.proto.MapStateProto
import com.yokuli.marine.navigation.domain.NavigationChangeResult
import com.yokuli.marine.navigation.domain.NavigationLibraryChange
import com.yokuli.marine.navigation.domain.NavigationLibraryCommitResult
import com.yokuli.marine.navigation.domain.NavigationLibraryEditor
import com.yokuli.marine.navigation.domain.NavigationLibraryFailure
import com.yokuli.marine.navigation.domain.NavigationLibraryLoadResult
import com.yokuli.marine.navigation.domain.NavigationLibraryPort
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room remains the single transactional compatibility store while Navigation takes semantic
 * ownership from Chart. DataStore owns only lightweight map-session facts. Neither path has a
 * destructive corruption fallback.
 */
class RoomMapPersistence private constructor(
    private val sessionStore: DataStore<MapStateProto>,
    private val database: MapLibraryDatabase,
) : MapPersistencePort, NavigationLibraryPort {
    private val libraryMutex = Mutex()

    override suspend fun load(): MapLoadResult = libraryMutex.withLock {
        try {
            val session = MapProtoMapper.decodeSession(sessionStore.data.first())
            val decoded = decode(database.libraryDao().readAll())
            MapLoadResult.Ready(session, decoded.snapshot, decoded.quarantinedRecordCount)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: CorruptionException) {
            if (error.cause?.message.orEmpty().contains("Unsupported map schema", ignoreCase = true)) {
                MapLoadResult.ReadFailed(MapReadFailure.FUTURE_SCHEMA)
            } else {
                MapLoadResult.Corrupt()
            }
        } catch (error: SQLiteException) {
            MapLoadResult.ReadFailed(error.toMapReadFailure())
        } catch (_: IOException) {
            MapLoadResult.ReadFailed(MapReadFailure.IO)
        } catch (_: Throwable) {
            MapLoadResult.ReadFailed(MapReadFailure.UNKNOWN)
        }
    }

    override suspend fun saveSession(snapshot: MapSessionSnapshot) {
        sessionStore.updateData { MapProtoMapper.encodeSession(snapshot) }
    }

    override suspend fun saveLibrary(snapshot: MapLibrarySnapshot): MapPersistenceAck = libraryMutex.withLock {
        database.libraryDao().replaceAll(encode(snapshot))
        MapPersistenceAck(snapshot.revision)
    }

    override suspend fun loadNavigationLibrary(): NavigationLibraryLoadResult = libraryMutex.withLock {
        try {
            val decoded = decode(database.libraryDao().readAll())
            NavigationLibraryLoadResult.Ready(
                NavigationLegacyMapper.toNavigation(decoded.snapshot),
                decoded.quarantinedRecordCount,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: SQLiteException) {
            NavigationLibraryLoadResult.Failed(error.toMapReadFailure().toNavigationFailure())
        } catch (_: IOException) {
            NavigationLibraryLoadResult.Failed(NavigationLibraryFailure.IO)
        } catch (_: Throwable) {
            NavigationLibraryLoadResult.Failed(NavigationLibraryFailure.UNKNOWN)
        }
    }

    override suspend fun commitNavigationChange(
        expectedLibraryRevision: Long,
        change: NavigationLibraryChange,
    ): NavigationLibraryCommitResult = libraryMutex.withLock {
        try {
            val current = NavigationLegacyMapper.toNavigation(decode(database.libraryDao().readAll()).snapshot)
            if (current.revision != expectedLibraryRevision) {
                return@withLock NavigationLibraryCommitResult.Conflict(current.revision)
            }
            when (val result = NavigationLibraryEditor.apply(current, change)) {
                is NavigationChangeResult.Rejected -> NavigationLibraryCommitResult.Rejected(result.reason)
                is NavigationChangeResult.Applied -> {
                    database.libraryDao().replaceAll(encode(NavigationLegacyMapper.toLegacy(result.library)))
                    NavigationLibraryCommitResult.Committed(result.library.revision)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: SQLiteException) {
            NavigationLibraryCommitResult.Failed(error.toMapReadFailure().toNavigationFailure())
        } catch (_: IOException) {
            NavigationLibraryCommitResult.Failed(NavigationLibraryFailure.IO)
        } catch (_: Throwable) {
            NavigationLibraryCommitResult.Failed(NavigationLibraryFailure.UNKNOWN)
        }
    }

    companion object {
        private const val SESSION_FILE_NAME = "map_session.pb"
        private const val DATABASE_FILE_NAME = "map_library.db"

        fun create(context: Context, scope: CoroutineScope): RoomMapPersistence {
            val database = Room.databaseBuilder(context, MapLibraryDatabase::class.java, DATABASE_FILE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
            return create(context.dataStoreFile(SESSION_FILE_NAME), scope, database)
        }

        internal fun create(
            sessionFile: File,
            scope: CoroutineScope,
            database: MapLibraryDatabase,
        ): RoomMapPersistence = RoomMapPersistence(
            sessionStore = DataStoreFactory.create(
                serializer = MapStateSerializer(MapProtoMapper.encodeSession(MapSessionSnapshot())),
                scope = scope,
                produceFile = { sessionFile },
            ),
            database = database,
        )
    }
}

private fun SQLiteException.toMapReadFailure(): MapReadFailure =
    if (message.orEmpty().contains("migration", ignoreCase = true)) MapReadFailure.FUTURE_SCHEMA else MapReadFailure.CORRUPT

private fun MapReadFailure.toNavigationFailure(): NavigationLibraryFailure = when (this) {
    MapReadFailure.IO -> NavigationLibraryFailure.IO
    MapReadFailure.CORRUPT -> NavigationLibraryFailure.CORRUPT
    MapReadFailure.FUTURE_SCHEMA -> NavigationLibraryFailure.FUTURE_SCHEMA
    MapReadFailure.UNKNOWN -> NavigationLibraryFailure.UNKNOWN
}

private data class DecodedLibrary(
    val snapshot: MapLibrarySnapshot,
    val quarantinedRecordCount: Int,
)

private fun encode(snapshot: MapLibrarySnapshot): MapLibraryRecords = MapLibraryRecords(
    revision = snapshot.revision,
    places = snapshot.places.map { place ->
        PlaceEntity(
            id = place.id,
            revision = place.revision,
            name = place.name,
            latitude = place.point.latitude,
            longitude = place.point.longitude,
            notes = place.notes,
            category = place.category.wireValue,
            createdAtMillis = place.createdAtMillis,
            updatedAtMillis = place.updatedAtMillis,
        )
    },
    placeTags = snapshot.places.flatMap { place ->
        place.tags.sorted().map { tag -> PlaceTagEntity(place.id, tag) }
    },
    drafts = snapshot.routeDrafts.map { draft ->
        RouteDraftEntity(
            id = draft.id,
            revision = draft.revision,
            name = draft.name,
            plannedSpeedKnots = draft.plannedSpeedKnots ?: 0.0,
            notes = draft.notes,
            basePlanId = draft.basePlanId,
            basePlanRevision = draft.basePlanRevision,
            nextWaypointOrdinal = draft.nextWaypointOrdinal,
        )
    },
    draftPoints = snapshot.routeDrafts.flatMap { draft ->
        draft.waypoints.mapIndexed { index, point ->
            val source = draft.waypointPlaceReferences[index]
            RouteDraftPointEntity(
                draftId = draft.id,
                position = index,
                latitude = point.latitude,
                longitude = point.longitude,
                waypointId = draft.waypointIds[index],
                sourcePlaceId = source?.placeId,
                sourcePlaceRevision = source?.revision,
            )
        }
    },
    routes = snapshot.savedRoutes.map { route ->
        SavedRouteEntity(
            route.id,
            route.revision,
            route.name,
            route.plannedSpeedKnots ?: 0.0,
            route.sourceDraftId,
            route.sourceDraftRevision,
            route.notes,
        )
    },
    routePoints = snapshot.savedRoutes.flatMap { route ->
        route.waypoints.mapIndexed { index, point ->
            val source = route.waypointPlaceReferences[index]
            SavedRoutePointEntity(
                routeId = route.id,
                position = index,
                latitude = point.latitude,
                longitude = point.longitude,
                sourcePlaceId = source?.placeId,
                sourcePlaceRevision = source?.revision,
                waypointId = route.waypointIds[index],
            )
        }
    },
    importedTracks = snapshot.importedTracks.map { track ->
        ImportedTrackEntity(
            id = track.id,
            revision = track.revision,
            name = track.name,
            description = track.description,
            sourceDigest = track.sourceDigest,
            importedAtMillis = track.importedAtMillis,
            editability = track.editability.name,
            origin = track.origin.name,
            startedAtEpochMillis = track.startedAtEpochMillis,
            endedAtEpochMillis = track.endedAtEpochMillis,
            durationMillis = track.durationMillis,
            distanceNauticalMiles = track.distanceNauticalMiles,
            navigationSessionId = track.navigationSessionId,
            routeId = track.routeId,
            routeRevision = track.routeRevision,
        )
    },
    importedTrackSegments = snapshot.importedTracks.flatMap { track ->
        track.segments.mapIndexed { segmentIndex, _ -> ImportedTrackSegmentEntity(track.id, segmentIndex) }
    },
    importedTrackPoints = snapshot.importedTracks.flatMap { track ->
        track.segments.flatMapIndexed { segmentIndex, segment ->
            segment.points.mapIndexed { pointIndex, point ->
                ImportedTrackPointEntity(
                    trackId = track.id,
                    segmentPosition = segmentIndex,
                    pointPosition = pointIndex,
                    latitude = point.point.latitude,
                    longitude = point.point.longitude,
                    elevationMeters = point.elevationMeters,
                    time = point.time,
                    recordedAtEpochMillis = point.recordedAtEpochMillis,
                    sourceId = point.sourceId,
                    speedOverGroundKnots = point.speedOverGroundKnots,
                    courseOverGroundTrueDegrees = point.courseOverGroundTrueDegrees,
                )
            }
        }
    },
    gpxImportRecords = snapshot.gpxImportRecords.map { record ->
        GpxImportRecordEntity(record.id, record.sha256, record.importedAtMillis)
    },
)

private fun decode(records: MapLibraryRecords): DecodedLibrary {
    var quarantined = 0

    val rawPlaceIds = records.places.map { it.id }.toSet()
    quarantined += records.placeTags.count { it.placeId !in rawPlaceIds }
    val places = records.places.mapNotNull { entity ->
        runCatching {
            val tags = records.placeTags.filter { it.placeId == entity.id }.map { it.tag }
            SavedPlace(
                id = entity.id,
                name = entity.name,
                point = GeoPoint(entity.latitude, entity.longitude),
                revision = entity.revision,
                notes = entity.notes,
                category = requireNotNull(PlaceCategory.fromWireValue(entity.category)) { "Unknown place category" },
                tags = tags,
                createdAtMillis = entity.createdAtMillis,
                updatedAtMillis = entity.updatedAtMillis,
            )
        }.getOrElse {
            quarantined += 1
            null
        }
    }

    val draftIds = records.drafts.map { it.id }.toSet()
    quarantined += records.draftPoints.count { it.draftId !in draftIds }
    val drafts = records.drafts.mapNotNull { entity ->
        val points = records.draftPoints.filter { it.draftId == entity.id }
        runCatching {
            val references = points.mapIndexedNotNull { index, point ->
                when {
                    point.sourcePlaceId == null && point.sourcePlaceRevision == null -> null
                    point.sourcePlaceId != null && point.sourcePlaceRevision != null -> {
                        index to PlaceRevisionReference(point.sourcePlaceId, point.sourcePlaceRevision)
                    }
                    else -> error("Incomplete draft place reference")
                }
            }.toMap()
            ManualRouteDraft(
                id = entity.id,
                revision = entity.revision,
                name = entity.name,
                plannedSpeedKnots = entity.plannedSpeedKnots.takeIf { it > 0.0 },
                waypoints = points.map { GeoPoint(it.latitude, it.longitude) },
                notes = entity.notes,
                waypointIds = points.mapIndexed { index, point ->
                    point.waypointId.ifBlank { "${entity.id}-waypoint-${index + 1}" }
                },
                waypointPlaceReferences = references,
                basePlanId = entity.basePlanId,
                basePlanRevision = entity.basePlanRevision,
                nextWaypointOrdinal = entity.nextWaypointOrdinal.coerceAtLeast(points.size + 1),
            )
        }.getOrElse {
            quarantined += 1
            null
        }
    }

    val routeIds = records.routes.map { it.id }.toSet()
    quarantined += records.routePoints.count { it.routeId !in routeIds }
    val routes = records.routes.mapNotNull { entity ->
        val points = records.routePoints.filter { it.routeId == entity.id }
        runCatching {
            val references = points.mapIndexedNotNull { index, point ->
                when {
                    point.sourcePlaceId == null && point.sourcePlaceRevision == null -> null
                    point.sourcePlaceId != null && point.sourcePlaceRevision != null -> {
                        index to PlaceRevisionReference(point.sourcePlaceId, point.sourcePlaceRevision)
                    }
                    else -> error("Incomplete route place reference")
                }
            }.toMap()
            SavedRoute(
                id = entity.id,
                name = entity.name,
                waypoints = points.map { GeoPoint(it.latitude, it.longitude) },
                plannedSpeedKnots = entity.plannedSpeedKnots.takeIf { it > 0.0 },
                revision = entity.revision,
                sourceDraftId = entity.sourceDraftId,
                sourceDraftRevision = entity.sourceDraftRevision,
                waypointPlaceReferences = references,
                notes = entity.notes,
                waypointIds = points.mapIndexed { index, point ->
                    point.waypointId.ifBlank { "${entity.id}-waypoint-${index + 1}" }
                },
            )
        }.getOrElse {
            quarantined += 1
            null
        }
    }

    val rawTrackIds = records.importedTracks.map { it.id }.toSet()
    quarantined += records.importedTrackSegments.count { it.trackId !in rawTrackIds }
    quarantined += records.importedTrackPoints.count { point ->
        point.trackId !in rawTrackIds || records.importedTrackSegments.none {
            it.trackId == point.trackId && it.position == point.segmentPosition
        }
    }
    val importedTracks = records.importedTracks.mapNotNull { entity ->
        runCatching {
            val segments = records.importedTrackSegments
                .filter { it.trackId == entity.id }
                .sortedBy { it.position }
                .map { segment ->
                    val points = records.importedTrackPoints
                        .filter { it.trackId == entity.id && it.segmentPosition == segment.position }
                        .sortedBy { it.pointPosition }
                        .map { point ->
                            ImportedTrackPoint(
                                point = GeoPoint(point.latitude, point.longitude),
                                elevationMeters = point.elevationMeters,
                                time = point.time,
                                recordedAtEpochMillis = point.recordedAtEpochMillis,
                                sourceId = point.sourceId,
                                speedOverGroundKnots = point.speedOverGroundKnots,
                                courseOverGroundTrueDegrees = point.courseOverGroundTrueDegrees,
                            )
                        }
                    ImportedTrackSegment(points)
                }
            ImportedTrack(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                segments = segments,
                sourceDigest = entity.sourceDigest,
                importedAtMillis = entity.importedAtMillis,
                revision = entity.revision,
                editability = ImportedTrackEditability.valueOf(entity.editability),
                origin = com.yokuli.marine.map.domain.TrackOrigin.valueOf(entity.origin),
                startedAtEpochMillis = entity.startedAtEpochMillis,
                endedAtEpochMillis = entity.endedAtEpochMillis,
                durationMillis = entity.durationMillis,
                distanceNauticalMiles = entity.distanceNauticalMiles,
                navigationSessionId = entity.navigationSessionId,
                routeId = entity.routeId,
                routeRevision = entity.routeRevision,
            )
        }.getOrElse {
            quarantined += 1
            null
        }
    }
    val gpxImportRecords = records.gpxImportRecords.mapNotNull { entity ->
        runCatching { GpxImportRecord(entity.id, entity.sha256, entity.importedAtMillis) }
            .getOrElse {
                quarantined += 1
                null
            }
    }

    return DecodedLibrary(
        snapshot = MapLibrarySnapshot(
            revision = records.revision,
            places = places,
            routeDrafts = drafts,
            savedRoutes = routes,
            importedTracks = importedTracks,
            gpxImportRecords = gpxImportRecords,
        ),
        quarantinedRecordCount = quarantined,
    )
}
