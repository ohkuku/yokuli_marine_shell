package com.yokuli.marine.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationCameraContractTest {
    private val reducer = DefaultMapReducer()

    @Test
    fun `user pan enters free browse and one recenter restores the prior navigation camera`() {
        val generation = MapRendererGeneration(1)
        val initial = underwayState().copy(
            navigationCamera = NavigationCameraState(
                mode = NavigationCameraMode.LOOK_AHEAD,
                resumeMode = NavigationCameraMode.LOOK_AHEAD,
                orientation = MapOrientationMode.COURSE_UP,
            ),
            renderer = MapRendererState(
                generation = generation,
                readiness = MapRendererReadiness.RENDERER_READY,
                cameraInputEnabled = true,
            ),
        )

        val browsed = reducer.reduce(
            initial,
            MapAction.RendererCameraIdle(generation, initial.camera.copy(center = GeoPoint(-36.9, 174.7))),
        ).state

        assertEquals(NavigationCameraMode.FREE_BROWSE, browsed.navigationCamera.mode)
        assertEquals(NavigationCameraMode.LOOK_AHEAD, browsed.navigationCamera.resumeMode)
        assertEquals(initial.activeNavigationRoute, browsed.activeNavigationRoute)

        val recentered = reducer.reduce(browsed, MapAction.RecenterNavigationCamera).state
        assertEquals(NavigationCameraMode.LOOK_AHEAD, recentered.navigationCamera.mode)
        assertEquals(MapCameraIntent.NAVIGATION_LOOK_AHEAD, recentered.renderer.pendingCameraCommand?.intent)
    }

    @Test
    fun `look ahead falls back to vessel follow when motion direction is not reliable`() {
        val state = underwayState().copy(
            position = underwayState().position.copy(courseSpeed = null, courseSpeedAvailability = PositionAvailability.UNAVAILABLE),
        )

        val changed = reducer.reduce(
            state,
            MapAction.SetNavigationCameraMode(NavigationCameraMode.LOOK_AHEAD),
        ).state

        assertEquals(NavigationCameraMode.VESSEL_FOLLOW, changed.navigationCamera.mode)
        assertEquals(MapCameraIntent.NAVIGATION_FOLLOW, changed.renderer.pendingCameraCommand?.intent)
    }

    @Test
    fun `camera mode orientation and map content remain independent truths`() {
        val initial = underwayState()
        val next = reducer.reduce(
            reducer.reduce(
                initial,
                MapAction.SetNavigationCameraMode(NavigationCameraMode.NEXT_WAYPOINT),
            ).state,
            MapAction.SetMapOrientationMode(MapOrientationMode.HEADING_UP),
        ).state
        val switchedContent = reducer.reduce(next, MapAction.SetMapViewMode(MapViewMode.MARINE)).state

        assertEquals(NavigationCameraMode.NEXT_WAYPOINT, switchedContent.navigationCamera.mode)
        assertEquals(MapOrientationMode.HEADING_UP, switchedContent.navigationCamera.orientation)
        assertEquals(MapViewMode.MARINE, switchedContent.mapViewMode)
        assertTrue(switchedContent.renderer.pendingCameraCommand?.target is MapCameraTarget.Bounds)
        assertEquals(initial.activeNavigationRoute, switchedContent.activeNavigationRoute)
    }

    private fun underwayState(): MapState {
        val source = ObservationSource("selected-position", "session-1")
        val time = MonotonicTime("boot", 1_000)
        val vessel = GeoPoint(-36.85, 174.76)
        return MapState(
            camera = MapCamera(center = vessel, zoom = 13.0),
            position = PositionState(
                sourceStatus = PositionSourceStatus.Connected(source),
                observation = PositionObservation(
                    identity = ObservationIdentity(source, "position-1", receivedAt = time),
                    point = vessel,
                    validity = ObservationValidity.VALID,
                ),
                availability = PositionAvailability.FRESH,
                courseSpeed = CourseSpeedObservation(
                    identity = ObservationIdentity(source, "motion-1", receivedAt = time),
                    courseOverGroundTrueDegrees = 70.0,
                    speedOverGroundKnots = 6.0,
                    quality = CourseQuality.USABLE,
                    validity = ObservationValidity.VALID,
                ),
                courseSpeedAvailability = PositionAvailability.FRESH,
                viewIntent = PositionViewIntent.FOLLOW_POSITION,
                evaluatedAt = time,
            ),
            navigationActive = true,
            activeNavigationRoute = listOf(
                vessel,
                GeoPoint(-36.80, 174.86),
                GeoPoint(-36.70, 174.95),
            ),
            activeNavigationLeg = listOf(vessel, GeoPoint(-36.80, 174.86)),
            activeNavigationRemainingRoute = listOf(
                vessel,
                GeoPoint(-36.80, 174.86),
                GeoPoint(-36.70, 174.95),
            ),
        )
    }
}
