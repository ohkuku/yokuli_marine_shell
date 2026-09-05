package com.yokuli.marine.map.storage

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.GpxImportRecord
import com.yokuli.marine.map.domain.ImportedTrack
import com.yokuli.marine.map.domain.ImportedTrackPoint
import com.yokuli.marine.map.domain.ImportedTrackSegment
import com.yokuli.marine.map.domain.ManualRouteDraft
import com.yokuli.marine.map.domain.MapLibrarySnapshot
import com.yokuli.marine.map.domain.MapLoadResult
import com.yokuli.marine.map.domain.MapReadFailure
import com.yokuli.marine.map.domain.MapSessionSnapshot
import com.yokuli.marine.map.domain.PlaceCategory
import com.yokuli.marine.map.domain.PlaceRevisionReference
import com.yokuli.marine.map.domain.SavedPlace
import com.yokuli.marine.map.domain.SavedRoute
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMapPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MapLibraryDatabase::class.java,
    )

    @Test
    fun transactionalLibraryAndSessionRoundTripWithStableRevisions() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, MapLibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sessionFile = File(context.cacheDir, "map-session-${System.nanoTime()}.pb")
        val persistence = RoomMapPersistence.create(sessionFile, scope, database)
        val first = GeoPoint(-36.8, 174.7)
        val second = GeoPoint(-36.7, 174.8)
        val library = MapLibrarySnapshot(
            revision = 7L,
            places = listOf(
                SavedPlace(
                    "place-stable",
                    "码头",
                    first,
                    revision = 3L,
                    notes = "夜间入口",
                    category = PlaceCategory.MARINA,
                    tags = listOf("fuel", "补水"),
                    createdAtMillis = 100L,
                    updatedAtMillis = 200L,
                ),
            ),
            routeDrafts = listOf(
                ManualRouteDraft(
                    id = "draft-stable",
                    revision = 4L,
                    name = "草稿",
                    waypoints = listOf(first, second),
                    plannedSpeedKnots = null,
                    notes = "draft notes",
                    waypointIds = listOf("draft-point-a", "draft-point-b"),
                    waypointPlaceReferences = mapOf(0 to PlaceRevisionReference("place-stable", 3L)),
                    basePlanId = "route-stable",
                    basePlanRevision = 6L,
                    nextWaypointOrdinal = 9,
                ),
            ),
            savedRoutes = listOf(
                SavedRoute(
                    id = "route-stable",
                    name = "计划",
                    waypoints = listOf(first, second),
                    plannedSpeedKnots = null,
                    revision = 6L,
                    waypointPlaceReferences = mapOf(0 to PlaceRevisionReference("place-stable", 3L)),
                    notes = "plan notes",
                    waypointIds = listOf("plan-point-a", "plan-point-b"),
                ),
            ),
            importedTracks = listOf(
                ImportedTrack(
                    id = "track-stable",
                    name = "晨航",
                    description = "two independent legs",
                    segments = listOf(
                        ImportedTrackSegment(
                            listOf(ImportedTrackPoint(first, 2.5, "2026-01-02T03:04:05Z")),
                        ),
                        ImportedTrackSegment(listOf(ImportedTrackPoint(second))),
                    ),
                    sourceDigest = "a".repeat(64),
                    importedAtMillis = 300L,
                ),
            ),
            gpxImportRecords = listOf(GpxImportRecord("import-stable", "a".repeat(64), 300L)),
        )
        val session = MapSessionSnapshot(activeRouteDraftId = "draft-stable", activeRoutePlanId = "route-stable")

        assertEquals(7L, persistence.saveLibrary(library).revision)
        persistence.saveSession(session)
        val loaded = persistence.load() as MapLoadResult.Ready

        assertEquals(library, loaded.library)
        assertEquals(session, loaded.session)
        assertEquals(0, loaded.quarantinedRecordCount)
        database.close()
        scope.cancel()
        assertTrue(sessionFile.exists())
        sessionFile.delete()
        Unit
    }

    @Test
    fun oneMalformedRecordIsQuarantinedWithoutClearingValidRows() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, MapLibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sessionFile = File(context.cacheDir, "map-session-bad-row-${System.nanoTime()}.pb")
        val persistence = RoomMapPersistence.create(sessionFile, scope, database)
        val valid = SavedPlace("valid-place", "valid", GeoPoint(-36.8, 174.7), revision = 1L)
        persistence.saveLibrary(MapLibrarySnapshot(revision = 2L, places = listOf(valid)))
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO places(id, revision, name, latitude, longitude) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any?>("bad-place", 1L, "bad", 200.0, 174.0),
        )

        val loaded = persistence.load() as MapLoadResult.Ready

        assertEquals(listOf(valid), loaded.library.places)
        assertTrue(loaded.quarantinedRecordCount == 1)
        assertEquals(2L, loaded.library.revision)
        database.close()
        scope.cancel()
        sessionFile.delete()
        Unit
    }

    @Test
    fun corruptAndFutureSessionFilesAreReportedWithoutReplacement() = runBlocking {
        suspend fun load(bytes: ByteArray): Pair<MapLoadResult, ByteArray> {
            val database = Room.inMemoryDatabaseBuilder(context, MapLibraryDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val sessionFile = File(context.cacheDir, "map-session-corrupt-${System.nanoTime()}.pb")
            sessionFile.writeBytes(bytes)
            val persistence = RoomMapPersistence.create(sessionFile, scope, database)
            val result = persistence.load()
            val retained = sessionFile.readBytes()
            database.close()
            scope.cancel()
            sessionFile.delete()
            return result to retained
        }

        val corruptBytes = byteArrayOf(0x80.toByte())
        val corrupt = load(corruptBytes)
        assertTrue(corrupt.first is MapLoadResult.Corrupt)
        assertTrue(corruptBytes.contentEquals(corrupt.second))

        val futureBytes = com.yokuli.marine.map.storage.proto.MapStateProto.newBuilder()
            .setSchemaVersion(MapProtoMapper.SCHEMA_VERSION + 1)
            .build()
            .toByteArray()
        val future = load(futureBytes)
        assertEquals(
            MapReadFailure.FUTURE_SCHEMA,
            (future.first as MapLoadResult.ReadFailed).failure,
        )
        assertTrue(futureBytes.contentEquals(future.second))
    }

    @Test
    fun durableLibraryReopensWithTheSameIdsAndRevisions() = runBlocking {
        val databaseName = "map-library-reopen-${System.nanoTime()}.db"
        val firstSessionFile = File(context.cacheDir, "map-session-reopen-a-${System.nanoTime()}.pb")
        val secondSessionFile = File(context.cacheDir, "map-session-reopen-b-${System.nanoTime()}.pb")
        val point = GeoPoint(-41.2866, 174.7756)
        val expected = MapLibrarySnapshot(
            revision = 19L,
            places = listOf(
                SavedPlace(
                    "place-after-kill",
                    "Wellington",
                    point,
                    revision = 8L,
                    notes = "夜间入口",
                    category = PlaceCategory.MARINA,
                    tags = listOf("fuel", "补水"),
                    createdAtMillis = 100L,
                    updatedAtMillis = 200L,
                ),
            ),
            routeDrafts = listOf(ManualRouteDraft("draft-after-kill", 11L, "draft", listOf(point))),
            savedRoutes = listOf(
                SavedRoute(
                    "route-after-kill",
                    "route",
                    listOf(point),
                    5.0,
                    waypointPlaceReferences = mapOf(0 to PlaceRevisionReference("place-after-kill", 8L)),
                ),
            ),
        )

        val firstDatabase = Room.databaseBuilder(context, MapLibraryDatabase::class.java, databaseName).build()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstPersistence = RoomMapPersistence.create(firstSessionFile, firstScope, firstDatabase)
        assertEquals(19L, firstPersistence.saveLibrary(expected).revision)
        firstDatabase.close()
        firstScope.cancel()

        val reopenedDatabase = Room.databaseBuilder(context, MapLibraryDatabase::class.java, databaseName).build()
        val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val reopened = RoomMapPersistence.create(secondSessionFile, reopenedScope, reopenedDatabase)
        val loaded = reopened.load() as MapLoadResult.Ready

        assertEquals(expected, loaded.library)
        reopenedDatabase.close()
        reopenedScope.cancel()
        context.deleteDatabase(databaseName)
        firstSessionFile.delete()
        secondSessionFile.delete()
        Unit
    }

    @Test
    fun versionOnePlacesMigrateWithoutDestructiveFallback() {
        val databaseName = "map-library-migration-${System.nanoTime()}.db"
        migrationHelper.createDatabase(databaseName, 1).apply {
            execSQL("INSERT INTO library_metadata(`key`, revision) VALUES (0, 9)")
            execSQL(
                "INSERT INTO places(id, revision, name, latitude, longitude) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>("legacy-place", 4L, "Legacy", -36.8, 174.7),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(databaseName, 2, true, MIGRATION_1_2).use { migrated ->
            migrated.query(
                "SELECT notes, category, createdAtMillis, updatedAtMillis FROM places WHERE id = 'legacy-place'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertEquals("personal", cursor.getString(1))
                assertEquals(0L, cursor.getLong(2))
                assertEquals(0L, cursor.getLong(3))
            }
        }
    }

    @Test
    fun versionTwoRoutesMigrateWithoutInventingSpeedOrLosingIdentity() {
        val databaseName = "map-library-route-migration-${System.nanoTime()}.db"
        migrationHelper.createDatabase(databaseName, 2).apply {
            execSQL("INSERT INTO library_metadata(`key`, revision) VALUES (0, 12)")
            execSQL(
                "INSERT INTO route_drafts(id, revision, name, plannedSpeedKnots) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>("legacy-draft", 4L, "旧草稿", 0.0),
            )
            execSQL(
                "INSERT INTO route_draft_points(draftId, position, latitude, longitude) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>("legacy-draft", 0, -36.8, 174.7),
            )
            execSQL(
                "INSERT INTO saved_routes(id, revision, name, plannedSpeedKnots, sourceDraftId, sourceDraftRevision) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("legacy-route", 6L, "旧计划", 0.0, "legacy-draft", 4L),
            )
            execSQL(
                "INSERT INTO saved_route_points(routeId, position, latitude, longitude, sourcePlaceId, sourcePlaceRevision) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("legacy-route", 0, -36.8, 174.7, null, null),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(databaseName, 3, true, MIGRATION_2_3).use { migrated ->
            migrated.query(
                "SELECT notes, basePlanId, basePlanRevision, nextWaypointOrdinal, plannedSpeedKnots FROM route_drafts WHERE id = 'legacy-draft'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
                assertEquals(2, cursor.getInt(3))
                assertEquals(0.0, cursor.getDouble(4), 0.0)
            }
            migrated.query(
                "SELECT waypointId FROM route_draft_points WHERE draftId = 'legacy-draft' AND position = 0",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("legacy-draft-waypoint-1", cursor.getString(0))
            }
            migrated.query(
                "SELECT notes, plannedSpeedKnots FROM saved_routes WHERE id = 'legacy-route'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertEquals(0.0, cursor.getDouble(1), 0.0)
            }
            migrated.query(
                "SELECT waypointId FROM saved_route_points WHERE routeId = 'legacy-route' AND position = 0",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("legacy-route-waypoint-1", cursor.getString(0))
            }
        }
    }

    @Test
    fun versionThreeAddsEmptySegmentedTrackAndImportRecordTablesWithoutTouchingExistingRows() {
        val databaseName = "map-library-gpx-migration-${System.nanoTime()}.db"
        migrationHelper.createDatabase(databaseName, 3).apply {
            execSQL("INSERT INTO library_metadata(`key`, revision) VALUES (0, 21)")
            execSQL(
                "INSERT INTO places(id, revision, name, latitude, longitude, notes, category, createdAtMillis, updatedAtMillis) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("existing", 1L, "Existing", -36.8, 174.7, "", "personal", 0L, 0L),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(databaseName, 4, true, MIGRATION_3_4).use { migrated ->
            migrated.query("SELECT COUNT(*) FROM places WHERE id = 'existing'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            listOf(
                "imported_tracks",
                "imported_track_segments",
                "imported_track_points",
                "gpx_import_records",
            ).forEach { table ->
                migrated.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
        }
    }
}
