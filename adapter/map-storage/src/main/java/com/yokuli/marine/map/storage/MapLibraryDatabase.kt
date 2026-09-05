package com.yokuli.marine.map.storage

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "library_metadata")
internal data class LibraryMetadataEntity(
    @androidx.room.PrimaryKey val key: Int = 0,
    val revision: Long,
)

@Entity(tableName = "places")
internal data class PlaceEntity(
    @androidx.room.PrimaryKey val id: String,
    val revision: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

@Entity(tableName = "route_drafts")
internal data class RouteDraftEntity(
    @androidx.room.PrimaryKey val id: String,
    val revision: Long,
    val name: String,
    val plannedSpeedKnots: Double,
)

@Entity(tableName = "route_draft_points", primaryKeys = ["draftId", "position"])
internal data class RouteDraftPointEntity(
    val draftId: String,
    val position: Int,
    val latitude: Double,
    val longitude: Double,
)

@Entity(tableName = "saved_routes")
internal data class SavedRouteEntity(
    @androidx.room.PrimaryKey val id: String,
    val revision: Long,
    val name: String,
    val plannedSpeedKnots: Double,
    val sourceDraftId: String?,
    val sourceDraftRevision: Long?,
)

@Entity(tableName = "saved_route_points", primaryKeys = ["routeId", "position"])
internal data class SavedRoutePointEntity(
    val routeId: String,
    val position: Int,
    val latitude: Double,
    val longitude: Double,
)

internal data class MapLibraryRecords(
    val revision: Long,
    val places: List<PlaceEntity>,
    val drafts: List<RouteDraftEntity>,
    val draftPoints: List<RouteDraftPointEntity>,
    val routes: List<SavedRouteEntity>,
    val routePoints: List<SavedRoutePointEntity>,
)

@Dao
internal abstract class MapLibraryDao {
    @Query("SELECT revision FROM library_metadata WHERE `key` = 0")
    abstract suspend fun revision(): Long?

    @Query("SELECT * FROM places ORDER BY id")
    abstract suspend fun places(): List<PlaceEntity>

    @Query("SELECT * FROM route_drafts ORDER BY id")
    abstract suspend fun drafts(): List<RouteDraftEntity>

    @Query("SELECT * FROM route_draft_points ORDER BY draftId, position")
    abstract suspend fun draftPoints(): List<RouteDraftPointEntity>

    @Query("SELECT * FROM saved_routes ORDER BY id")
    abstract suspend fun routes(): List<SavedRouteEntity>

    @Query("SELECT * FROM saved_route_points ORDER BY routeId, position")
    abstract suspend fun routePoints(): List<SavedRoutePointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putMetadata(value: LibraryMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putPlaces(values: List<PlaceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putDrafts(values: List<RouteDraftEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putDraftPoints(values: List<RouteDraftPointEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putRoutes(values: List<SavedRouteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putRoutePoints(values: List<SavedRoutePointEntity>)

    @Query("DELETE FROM route_draft_points")
    protected abstract suspend fun clearDraftPoints()

    @Query("DELETE FROM route_drafts")
    protected abstract suspend fun clearDrafts()

    @Query("DELETE FROM saved_route_points")
    protected abstract suspend fun clearRoutePoints()

    @Query("DELETE FROM saved_routes")
    protected abstract suspend fun clearRoutes()

    @Query("DELETE FROM places")
    protected abstract suspend fun clearPlaces()

    @Transaction
    open suspend fun readAll(): MapLibraryRecords = MapLibraryRecords(
        revision = revision() ?: 0L,
        places = places(),
        drafts = drafts(),
        draftPoints = draftPoints(),
        routes = routes(),
        routePoints = routePoints(),
    )

    @Transaction
    open suspend fun replaceAll(records: MapLibraryRecords) {
        clearDraftPoints()
        clearDrafts()
        clearRoutePoints()
        clearRoutes()
        clearPlaces()
        putMetadata(LibraryMetadataEntity(revision = records.revision))
        if (records.places.isNotEmpty()) putPlaces(records.places)
        if (records.drafts.isNotEmpty()) putDrafts(records.drafts)
        if (records.draftPoints.isNotEmpty()) putDraftPoints(records.draftPoints)
        if (records.routes.isNotEmpty()) putRoutes(records.routes)
        if (records.routePoints.isNotEmpty()) putRoutePoints(records.routePoints)
    }
}

@Database(
    entities = [
        LibraryMetadataEntity::class,
        PlaceEntity::class,
        RouteDraftEntity::class,
        RouteDraftPointEntity::class,
        SavedRouteEntity::class,
        SavedRoutePointEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
internal abstract class MapLibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): MapLibraryDao
}
