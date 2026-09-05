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
        PlaceEntity(place.id, place.revision, place.name, place.point.latitude, place.point.longitude)
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
            SavedRoutePointEntity(route.id, index, point.latitude, point.longitude)
        }
    },
)

private fun decode(records: MapLibraryRecords): DecodedLibrary {
    var quarantined = 0

    val places = records.places.mapNotNull { entity ->
        runCatching {
            SavedPlace(entity.id, entity.name, GeoPoint(entity.latitude, entity.longitude), entity.revision)
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
            SavedRoute(
                id = entity.id,
                name = entity.name,
                waypoints = points.map { GeoPoint(it.latitude, it.longitude) },
                plannedSpeedKnots = entity.plannedSpeedKnots,
                revision = entity.revision,
                sourceDraftId = entity.sourceDraftId,
                sourceDraftRevision = entity.sourceDraftRevision,
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
