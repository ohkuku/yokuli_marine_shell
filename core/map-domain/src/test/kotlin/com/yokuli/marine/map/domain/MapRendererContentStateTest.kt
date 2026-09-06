package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MapRendererContentStateTest {
    @Test
    fun `base and overlays advance independently and stale renderer callbacks are ignored`() {
        val generation = MapRendererGeneration(8)
        var state = reduce(MapState(), MapAction.RendererHostReady(generation))

        state = reduce(
            state,
            MapAction.RendererContentChanged(generation, base = MapBaseRenderStatus.BASE_LOADING),
        )
        state = reduce(
            state,
            MapAction.RendererContentChanged(generation, overlay = MapOverlayRenderStatus.OVERLAY_READY),
        )
        assertEquals(MapBaseRenderStatus.BASE_LOADING, state.renderer.baseStatus)
        assertEquals(MapOverlayRenderStatus.OVERLAY_READY, state.renderer.overlayStatus)

        state = reduce(
            state,
            MapAction.RendererContentChanged(
                MapRendererGeneration(7),
                base = MapBaseRenderStatus.BASE_READY,
                overlay = MapOverlayRenderStatus.OVERLAY_DEGRADED,
            ),
        )
        assertEquals(MapBaseRenderStatus.BASE_LOADING, state.renderer.baseStatus)
        assertEquals(MapOverlayRenderStatus.OVERLAY_READY, state.renderer.overlayStatus)
    }

    @Test
    fun `detach clears native content claims`() {
        val generation = MapRendererGeneration(2)
        var state = reduce(MapState(), MapAction.RendererHostReady(generation))
        state = reduce(
            state,
            MapAction.RendererContentChanged(
                generation,
                MapBaseRenderStatus.BASE_READY,
                MapOverlayRenderStatus.OVERLAY_READY,
            ),
        )

        state = reduce(state, MapAction.RendererDetached(generation))

        assertEquals(MapBaseRenderStatus.BASE_UNAVAILABLE, state.renderer.baseStatus)
        assertEquals(MapOverlayRenderStatus.OVERLAY_NONE, state.renderer.overlayStatus)
    }

    private fun reduce(state: MapState, action: MapAction): MapState =
        MapReducer.reduce(state, action).state
}
