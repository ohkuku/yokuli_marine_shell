package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.ChartPackage
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.LocalChartTileIndex
import com.yokuli.marine.map.domain.OfflineCoverageArea
import com.yokuli.marine.map.domain.SavedRoute
import com.yokuli.marine.map.domain.SlippyTileKey
import com.yokuli.marine.map.domain.TileAvailability
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCoverageCoordinatorTest {
    @Test
    fun `check publishes separated facts only after exact index work completes`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val chart = chart('a')
        val coordinator = OfflineCoverageCoordinator(
            tileIndex = LocalChartTileIndex { _, keys -> keys },
            scope = scope,
        )

        coordinator.start(route(), listOf(chart), targetZoom = 8, halfWidthNauticalMiles = 2.0)
        val ready = withTimeout(2_000L) { coordinator.state.first { it is OfflineCoverageUiState.Ready } }
            as OfflineCoverageUiState.Ready

        assertEquals(TileAvailability.AVAILABLE, ready.result.tileAvailability)
        assertTrue(ready.result.requiredKeyCount > 0)
        assertTrue(ready.result.missingKeys.isEmpty())
        scope.cancel()
    }

    @Test
    fun `cancel is explicit and a non cooperative late index result is ignored`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val release = CompletableDeferred<Unit>()
        val coordinator = OfflineCoverageCoordinator(
            tileIndex = LocalChartTileIndex { _, keys ->
                withContext(NonCancellable) { release.await() }
                keys
            },
            scope = scope,
        )
        coordinator.start(route(), listOf(chart('a')), 8, 2.0)
        coordinator.cancel()
        release.complete(Unit)
        delay(50L)

        assertEquals(OfflineCoverageUiState.Cancelled, coordinator.state.value)
        scope.cancel()
    }

    @Test
    fun `route revision or package version invalidates immediately and rejects old completion`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val release = CompletableDeferred<Unit>()
        val coordinator = OfflineCoverageCoordinator(
            tileIndex = LocalChartTileIndex { _, keys ->
                withContext(NonCancellable) { release.await() }
                keys
            },
            scope = scope,
        )
        val original = route()
        coordinator.start(original, listOf(chart('a')), 8, 2.0)

        coordinator.invalidateIfInputsChanged(listOf(original.copy(revision = 2)), listOf(chart('b')))
        assertTrue(coordinator.state.value is OfflineCoverageUiState.Stale)
        release.complete(Unit)
        delay(50L)

        assertTrue(coordinator.state.value is OfflineCoverageUiState.Stale)
        scope.cancel()
    }

    @Test
    fun `over budget is a typed result and never a partial ready result`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = OfflineCoverageCoordinator(
            tileIndex = LocalChartTileIndex { _, keys -> keys },
            scope = scope,
            maximumKeys = 20,
        )
        val huge = route().copy(waypoints = listOf(GeoPoint(-80.0, -170.0), GeoPoint(80.0, 170.0)))

        coordinator.start(huge, listOf(chart('a')), 18, 2.0, listOf(OfflineCoverageArea(GeoPoint(0.0, 0.0), 2.0)))
        withTimeout(2_000L) { coordinator.state.first { it is OfflineCoverageUiState.TooLarge } }

        assertEquals(OfflineCoverageUiState.TooLarge(20), coordinator.state.value)
        scope.cancel()
    }

    private fun route() = SavedRoute(
        id = "route",
        name = "route",
        waypoints = listOf(GeoPoint(-36.85, 174.75), GeoPoint(-36.80, 174.85)),
    )

    private fun chart(seed: Char) = ChartPackage(
        id = ChartPackageId(seed.toString().repeat(64)),
        displayName = "chart-$seed",
        source = "fixture",
        license = "fixture",
        attribution = "fixture",
        sha256 = seed.toString().repeat(64),
        localUri = "mbtiles:///fixture/$seed",
        coverage = GeoBounds(-90.0, -180.0, 90.0, 180.0),
        minZoom = 0,
        maxZoom = 18,
        version = "1",
    )
}
