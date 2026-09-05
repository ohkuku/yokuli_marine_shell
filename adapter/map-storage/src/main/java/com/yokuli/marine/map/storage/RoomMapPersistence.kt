package com.yokuli.marine.map.storage

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.room.Room
import com.yokuli.marine.map.domain.GeoPoint
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
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

/**
 * Room is the transactional owner of user-created places/routes/drafts. DataStore owns only
 * lightweight session facts. Neither path has a destructive corruption fallback.
 */
class RoomMapPersistence private constructor(
    private val sessionStore: DataStore<MapStateProto>,
    private val database: MapLibraryDatabase,
) : MapPersistencePort {
    override suspend fun load(): MapLoadResult = try {
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
        MapLoadResult.ReadFailed(
            if (error.message.orEmpty().contains("migration", ignoreCase = true)) {
                MapReadFailure.FUTURE_SCHEMA
            } else {
                MapReadFailure.CORRUPT
            },
        )
    } catch (_: IOException) {
        MapLoadResult.ReadFailed(MapReadFailure.IO)
    } catch (_: Throwable) {
        MapLoadResult.ReadFailed(MapReadFailure.UNKNOWN)
    }

    override suspend fun saveSession(snapshot: MapSessionSnapshot) {
        sessionStore.updateData { MapProtoMapper.encodeSession(snapshot) }
    }

    override suspend fun saveLibrary(snapshot: MapLibrarySnapshot): MapPersistenceAck {
        database.libraryDao().replaceAll(encode(snapshot))
        return MapPersistenceAck(snapshot.revision)
    }

    companion object {
        private const val SESSION_FILE_NAME = "map_session.pb"
        private const val DATABASE_FILE_NAME = "map_library.db"

        fun create(context: Context, scope: CoroutineScope): RoomMapPersistence {
            val database = Room.databaseBuilder(context, MapLibraryDatabase::class.java, DATABASE_FILE_NAME)
                .addMigrations(MIGRATION_1_2)
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
        RouteDraftEntity(draft.id, draft.revision, draft.name, draft.plannedSpeedKnots)
    },
    draftPoints = snapshot.routeDrafts.flatMap { draft ->
        draft.waypoints.mapIndexed { index, point ->
            RouteDraftPointEntity(draft.id, index, point.latitude, point.longitude)
        }
    },
    routes = snapshot.savedRoutes.map { route ->
        SavedRouteEntity(
            route.id,
            route.revision,
            route.name,
            route.plannedSpeedKnots,
            route.sourceDraftId,
            route.sourceDraftRevision,
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
            )
        }
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
            ManualRouteDraft(
                id = entity.id,
                revision = entity.revision,
                name = entity.name,
                plannedSpeedKnots = entity.plannedSpeedKnots,
                waypoints = points.map { GeoPoint(it.latitude, it.longitude) },
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
                plannedSpeedKnots = entity.plannedSpeedKnots,
                revision = entity.revision,
                sourceDraftId = entity.sourceDraftId,
                sourceDraftRevision = entity.sourceDraftRevision,
                waypointPlaceReferences = references,
            )
        }.getOrElse {
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
        ),
        quarantinedRecordCount = quarantined,
    )
}
