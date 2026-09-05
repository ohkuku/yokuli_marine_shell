package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRendererContractTest {
    private val restoredCamera = MapCamera(GeoPoint(-36.81, 174.83), 12.5, 27.0)

    @Test
    fun rendererReadyBeforeRestoreStillAppliesTheRestoredCameraWithoutPersistingDefaultCamera() {
        val generation = MapRendererGeneration(1)
        var state = reduce(MapState(), MapAction.RendererHostReady(generation)).state
        state = reduce(state, MapAction.RendererReady(generation)).state

        val ignoredDefault = reduce(
            state,
            MapAction.RendererCameraIdle(generation, MapCamera(GeoPoint(0.0, 0.0), 0.0)),
        )
        assertEquals(state, ignoredDefault.state)
        assertFalse(ignoredDefault.effects.any { it is MapEffect.PersistSession })

        val restored = restore(state)
        assertNotNull(restored.renderer.pendingCameraCommand)
        val command = requireNotNull(restored.renderer.pendingCameraCommand)
        assertEquals(MapCameraTarget.Exact(restoredCamera), command.target)
        assertEquals(MapCameraIntent.RESTORE, command.intent)
    }

    @Test
    fun restoreBeforeRendererReadyKeepsOneCommandAndConsumesOnlyItsEcho() {
        val generation = MapRendererGeneration(4)
        var state = restore(MapState())
        assertNotNull(state.renderer.pendingCameraCommand)
        val command = requireNotNull(state.renderer.pendingCameraCommand)
        state = reduce(state, MapAction.RendererHostReady(generation)).state
        state = reduce(state, MapAction.RendererReady(generation)).state

        val echo = reduce(
            state,
            MapAction.RendererCameraIdle(generation, restoredCamera, command.id),
        )
        assertNull(echo.state.renderer.pendingCameraCommand)
        assertEquals(command.id, echo.state.renderer.lastAcknowledgedCameraCommandId)
        assertEquals(restoredCamera, echo.state.camera)
        assertFalse(echo.effects.any { it is MapEffect.PersistSession })
    }

    @Test
    fun staleGenerationAndStaleCommandCannotOverwriteCurrentSession() {
        val first = MapRendererGeneration(7)
        val second = MapRendererGeneration(8)
        var state = restore(MapState())
        state = reduce(state, MapAction.RendererHostReady(first)).state
        state = reduce(state, MapAction.RendererHostReady(second)).state
        state = reduce(state, MapAction.RendererReady(second)).state
        assertNotNull(state.renderer.pendingCameraCommand)
        val command = requireNotNull(state.renderer.pendingCameraCommand)
        val staleCamera = MapCamera(GeoPoint(12.0, 13.0), 3.0)

        assertEquals(state, reduce(state, MapAction.RendererCameraIdle(first, staleCamera, command.id)).state)
        assertEquals(
            state,
            reduce(
                state,
                MapAction.RendererCameraIdle(second, staleCamera, MapCameraCommandId(command.id.value + 1)),
            ).state,
        )
    }

    @Test
    fun userCameraIdlePersistsOnlyAfterRendererReadyAndRestoreAck() {
        val generation = MapRendererGeneration(2)
        var state = restore(MapState())
        state = reduce(state, MapAction.RendererHostReady(generation)).state
        state = reduce(state, MapAction.RendererReady(generation)).state
        assertNotNull(state.renderer.pendingCameraCommand)
        val restoreCommand = requireNotNull(state.renderer.pendingCameraCommand)
        state = reduce(
            state,
            MapAction.RendererCameraIdle(generation, restoredCamera, restoreCommand.id),
        ).state
        val userCamera = restoredCamera.copy(zoom = 13.0)

        val moved = reduce(state, MapAction.RendererCameraIdle(generation, userCamera))
        assertEquals(userCamera, moved.state.camera)
        assertEquals(1, moved.effects.count { it is MapEffect.PersistSession })
    }

    @Test
    fun packageAttachmentIsNotNamedTileReadyAndLateFailureIsDiscarded() {
        val current = MapRendererGeneration(12)
        var state = reduce(MapState(), MapAction.RendererHostReady(current)).state
        state = reduce(state, MapAction.RendererReady(current)).state
        state = reduce(
            state,
            MapAction.RendererCoverageChanged(current, MapTileCoverageStatus.PACKAGE_ATTACHED),
        ).state
        assertEquals(MapTileCoverageStatus.PACKAGE_ATTACHED, state.renderer.tileCoverage)

        val late = reduce(
            state,
            MapAction.RendererFailed(MapRendererGeneration(11), MapRendererFailure.STYLE),
        )
        assertEquals(state, late.state)
    }

    @Test
    fun explicitCameraTargetsUseMonotonicIdsAndPreserveViewportInsets() {
        val firstInsets = MapViewportInsets(leftPx = 8, topPx = 64, rightPx = 12, bottomPx = 220)
        val packageBounds = GeoBounds(-37.0, 174.0, -36.0, 176.0)

        val place = reduce(
            MapState(),
            MapAction.RequestCamera(
                MapCameraTarget.Exact(restoredCamera.copy(center = GeoPoint(-36.7, 174.9))),
                MapCameraIntent.VIEW_PLACE,
                firstInsets,
            ),
        ).state
        val placeCommand = requireNotNull(place.renderer.pendingCameraCommand)
        assertEquals(MapCameraCommandId(1), placeCommand.id)
        assertEquals(firstInsets, placeCommand.viewportInsets)

        val packageView = reduce(
            place,
            MapAction.RequestCamera(
                MapCameraTarget.Bounds(packageBounds),
                MapCameraIntent.VIEW_PACKAGE,
                MapViewportInsets(bottomPx = 96),
            ),
        ).state
        val packageCommand = requireNotNull(packageView.renderer.pendingCameraCommand)
        assertEquals(MapCameraCommandId(2), packageCommand.id)
        assertEquals(MapCameraTarget.Bounds(packageBounds), packageCommand.target)
        assertEquals(96, packageCommand.viewportInsets.bottomPx)
    }

    @Test
    fun selectingAChartPackageNeverImplicitlyFitsAllContent() {
        fun chartPackage(id: String) = ChartPackage(
            ChartPackageId(id), id, "source", "license", "attribution", id.first().toString().repeat(64),
            "mbtiles:///charts/$id.mbtiles", GeoBounds(-37.0, 174.0, -36.0, 176.0), 4, 14, "1",
        )
        val first = chartPackage("aaaa")
        val second = chartPackage("bbbb")
        val loaded = reduce(MapState(), MapAction.ChartPackagesChanged(listOf(first, second))).state

        val selected = reduce(loaded, MapAction.SelectChartPackage(first.id)).state

        assertEquals(first.id, selected.activeChartPackageId)
        assertNull(selected.renderer.pendingCameraCommand)
        assertTrue(selected.camera == loaded.camera)
    }

    private fun restore(state: MapState): MapState = reduce(
        state,
        MapAction.Restore(
            MapLoadResult.Ready(
                session = MapSessionSnapshot(camera = restoredCamera),
                library = MapLibrarySnapshot(),
            ),
        ),
    ).state

    private fun reduce(state: MapState, action: MapAction): MapReduction = DefaultMapReducer().reduce(state, action)
}
