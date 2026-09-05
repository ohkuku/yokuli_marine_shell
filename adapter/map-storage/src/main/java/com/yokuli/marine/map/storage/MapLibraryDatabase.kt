package com.yokuli.marine.map.storage

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    @androidx.room.ColumnInfo(defaultValue = "''") val notes: String,
    @androidx.room.ColumnInfo(defaultValue = "'personal'") val category: String,
    @androidx.room.ColumnInfo(defaultValue = "0") val createdAtMillis: Long,
    @androidx.room.ColumnInfo(defaultValue = "0") val updatedAtMillis: Long,
)

@Entity(tableName = "place_tags", primaryKeys = ["placeId", "tag"])
internal data class PlaceTagEntity(
    val placeId: String,
    val tag: String,
)

@Entity(tableName = "route_drafts")
internal data class RouteDraftEntity(
    @androidx.room.PrimaryKey val id: String,
    val revision: Long,
    val name: String,
    val plannedSpeedKnots: Double,
    @androidx.room.ColumnInfo(defaultValue = "''") val notes: String,
    val basePlanId: String?,
    val basePlanRevision: Long?,
    @androidx.room.ColumnInfo(defaultValue = "1") val nextWaypointOrdinal: Int,
)

@Entity(tableName = "route_draft_points", primaryKeys = ["draftId", "position"])
internal data class RouteDraftPointEntity(
    val draftId: String,
    val position: Int,
    val latitude: Double,
    val longitude: Double,
    @androidx.room.ColumnInfo(defaultValue = "''") val waypointId: String,
    val sourcePlaceId: String?,
    val sourcePlaceRevision: Long?,
)

@Entity(tableName = "saved_routes")
internal data class SavedRouteEntity(
    @androidx.room.PrimaryKey val id: String,
    val revision: Long,
    val name: String,
    val plannedSpeedKnots: Double,
    val sourceDraftId: String?,
    val sourceDraftRevision: Long?,
    @androidx.room.ColumnInfo(defaultValue = "''") val notes: String,
)

@Entity(tableName = "saved_route_points", primaryKeys = ["routeId", "position"])
internal data class SavedRoutePointEntity(
    val routeId: String,
    val position: Int,
    val latitude: Double,
    val longitude: Double,
    val sourcePlaceId: String? = null,
    val sourcePlaceRevision: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "''") val waypointId: String,
)

internal data class MapLibraryRecords(
    val revision: Long,
    val places: List<PlaceEntity>,
    val placeTags: List<PlaceTagEntity>,
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

    @Query("SELECT * FROM place_tags ORDER BY placeId, tag")
    abstract suspend fun placeTags(): List<PlaceTagEntity>

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
    protected abstract suspend fun putPlaceTags(values: List<PlaceTagEntity>)

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

    @Query("DELETE FROM place_tags")
    protected abstract suspend fun clearPlaceTags()

    @Transaction
    open suspend fun readAll(): MapLibraryRecords = MapLibraryRecords(
        revision = revision() ?: 0L,
        places = places(),
        placeTags = placeTags(),
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
        clearPlaceTags()
        clearPlaces()
        putMetadata(LibraryMetadataEntity(revision = records.revision))
        if (records.places.isNotEmpty()) putPlaces(records.places)
        if (records.placeTags.isNotEmpty()) putPlaceTags(records.placeTags)
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
        PlaceTagEntity::class,
        RouteDraftEntity::class,
        RouteDraftPointEntity::class,
        SavedRouteEntity::class,
        SavedRoutePointEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
internal abstract class MapLibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): MapLibraryDao
}

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `places` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE `places` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'personal'")
        database.execSQL("ALTER TABLE `places` ADD COLUMN `createdAtMillis` INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE `places` ADD COLUMN `updatedAtMillis` INTEGER NOT NULL DEFAULT 0")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `place_tags` (
                `placeId` TEXT NOT NULL,
                `tag` TEXT NOT NULL,
                PRIMARY KEY(`placeId`, `tag`)
            )
            """.trimIndent(),
        )
        database.execSQL("ALTER TABLE `saved_route_points` ADD COLUMN `sourcePlaceId` TEXT")
        database.execSQL("ALTER TABLE `saved_route_points` ADD COLUMN `sourcePlaceRevision` INTEGER")
    }
}

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `route_drafts` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE `route_drafts` ADD COLUMN `basePlanId` TEXT")
        database.execSQL("ALTER TABLE `route_drafts` ADD COLUMN `basePlanRevision` INTEGER")
        database.execSQL("ALTER TABLE `route_drafts` ADD COLUMN `nextWaypointOrdinal` INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE `route_draft_points` ADD COLUMN `waypointId` TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE `route_draft_points` ADD COLUMN `sourcePlaceId` TEXT")
        database.execSQL("ALTER TABLE `route_draft_points` ADD COLUMN `sourcePlaceRevision` INTEGER")
        database.execSQL("ALTER TABLE `saved_routes` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE `saved_route_points` ADD COLUMN `waypointId` TEXT NOT NULL DEFAULT ''")
        database.execSQL(
            "UPDATE `route_draft_points` SET `waypointId` = `draftId` || '-waypoint-' || (`position` + 1)",
        )
        database.execSQL(
            "UPDATE `saved_route_points` SET `waypointId` = `routeId` || '-waypoint-' || (`position` + 1)",
        )
        database.execSQL(
            "UPDATE `route_drafts` SET `nextWaypointOrdinal` = (SELECT COUNT(*) + 1 FROM `route_draft_points` WHERE `draftId` = `route_drafts`.`id`)",
        )
    }
}
