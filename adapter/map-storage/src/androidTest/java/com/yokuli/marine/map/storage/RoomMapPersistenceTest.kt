package com.yokuli.marine.map.storage

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.ManualRouteDraft
import com.yokuli.marine.map.domain.MapLibrarySnapshot
import com.yokuli.marine.map.domain.MapLoadResult
import com.yokuli.marine.map.domain.MapReadFailure
import com.yokuli.marine.map.domain.MapSessionSnapshot
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
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMapPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

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
            places = listOf(SavedPlace("place-stable", "码头", first, revision = 3L)),
            routeDrafts = listOf(
                ManualRouteDraft("draft-stable", 4L, "草稿", listOf(first, second)),
            ),
            savedRoutes = listOf(SavedRoute("route-stable", "计划", listOf(first, second), 5.0, revision = 6L)),
        )
        val session = MapSessionSnapshot(activeRouteDraftId = "draft-stable")

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
            arrayOf("bad-place", 1L, "bad", 200.0, 174.0),
        )

        val loaded = persistence.load() as MapLoadResult.Ready

        assertEquals(listOf(valid), loaded.library.places)
        assertEquals(1, loaded.quarantinedRecordCount)
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
}
