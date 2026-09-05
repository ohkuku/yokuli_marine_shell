package com.yokuli.marine.shell

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapLibraryLoadState
import com.yokuli.marine.map.domain.MapSaveState
import com.yokuli.marine.map.domain.PlaceCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ChartC12ProcessRestartProbeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ShellActivity>()

    @Test
    fun seedDurableStateForExternalProcessRestart() {
        val viewModel = viewModel()
        awaitMapReady(viewModel)
        val camera = MapCamera(GeoPoint(-36.8123, 174.7456), 14.0, 37.0)
        viewModel.mapStore.dispatch(MapAction.CameraChanged(camera))
        viewModel.mapStore.dispatch(
            MapAction.CreatePlace(
                point = camera.center,
                name = PROCESS_PLACE_NAME,
                notes = "must survive external force-stop",
                category = PlaceCategory.ANCHORAGE,
                tags = listOf("c12-process"),
            ),
        )
        compose.waitUntil(15_000) {
            val state = viewModel.mapStore.state.value
            state.places.any { it.name == PROCESS_PLACE_NAME } && state.saveState == MapSaveState.SAVED
        }
        val disk = runBlocking {
            (compose.activityRule.activity.application as ShellApplication).mapPersistence.load()
        }
        assertTrue(disk is com.yokuli.marine.map.domain.MapLoadResult.Ready)
        disk as com.yokuli.marine.map.domain.MapLoadResult.Ready
        assertEquals(camera, disk.session.camera)
        assertTrue(disk.library.places.any { it.name == PROCESS_PLACE_NAME })
    }

    @Test
    fun verifyDurableStateAfterExternalProcessRestart() {
        val viewModel = viewModel()
        awaitMapReady(viewModel)
        compose.waitUntil(15_000) {
            viewModel.mapStore.state.value.places.any { it.name == PROCESS_PLACE_NAME }
        }
        val state = viewModel.mapStore.state.value
        assertEquals(MapCamera(GeoPoint(-36.8123, 174.7456), 14.0, 37.0), state.camera)
        assertEquals("must survive external force-stop", state.places.single { it.name == PROCESS_PLACE_NAME }.notes)
        assertFalse(state.navigationActive)
    }

    private fun viewModel(): ShellViewModel {
        lateinit var value: ShellViewModel
        compose.activityRule.scenario.onActivity { value = ViewModelProvider(it)[ShellViewModel::class.java] }
        return value
    }

    private fun awaitMapReady(viewModel: ShellViewModel) {
        compose.waitUntil(15_000) {
            viewModel.mapStore.state.value.libraryLoadState in
                setOf(MapLibraryLoadState.READY, MapLibraryLoadState.READY_EMPTY)
        }
        SystemClock.sleep(100L)
    }

    private companion object {
        const val PROCESS_PLACE_NAME = "C12 process anchor"
    }
}
