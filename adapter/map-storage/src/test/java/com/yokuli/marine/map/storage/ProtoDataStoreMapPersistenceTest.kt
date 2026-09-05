package com.yokuli.marine.map.storage

import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.ManualRouteDraft
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProtoDataStoreMapPersistenceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `round trip restores planning data but never runtime position or navigation`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(temporaryFolder.root, "map.pb")
        val persistence = ProtoDataStoreMapPersistence.create(file, scope)
        val snapshot = MapState(
            camera = MapCamera(GeoPoint(-36.8, 174.8), 12.0),
            routeDraft = ManualRouteDraft(waypoints = listOf(GeoPoint(-36.8, 174.8), GeoPoint(-36.7, 174.9))),
        ).persisted()

        persistence.save(snapshot)
        val restored = persistence.load()

        assertEquals(snapshot.camera, restored.camera)
        assertEquals(snapshot.routeDraft, restored.routeDraft)
        assertFalse(restored.navigationActive)
        assertNull(restored.positionObservation)
        scope.cancel()
    }
}
