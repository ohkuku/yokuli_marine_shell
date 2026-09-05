package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.ChartPackage
import com.yokuli.marine.map.domain.ChartPackageCandidate
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageImportRequest
import com.yokuli.marine.map.domain.ChartPackageRepository
import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapDispatchResult
import com.yokuli.marine.map.domain.MapReducer
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartPackageCoordinatorTest {
    @Test
    fun `required legal facts gate install then installed package becomes active`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val mapStore = ImmediateMapStore()
        val repository = FakeRepository()
        val coordinator = ChartPackageCoordinator(repository, mapStore, scope)

        coordinator.inspectDocument("content://fixture")
        withTimeout(2_000L) { coordinator.state.first { it is ChartImportUiState.Editing } }
        coordinator.dispatch(ChartImportUiAction.Install)
        assertTrue((coordinator.state.value as ChartImportUiState.Editing).validationFailure != null)

        mapOf(
            ChartImportField.DISPLAY_NAME to "Harbour",
            ChartImportField.SOURCE to "Survey office",
            ChartImportField.LICENSE to "Test license",
            ChartImportField.ATTRIBUTION to "Survey office",
            ChartImportField.VERSION to "1",
        ).forEach { (field, value) -> coordinator.dispatch(ChartImportUiAction.UpdateField(field, value)) }
        coordinator.dispatch(ChartImportUiAction.Install)
        withTimeout(2_000L) { coordinator.state.first { it == ChartImportUiState.Idle } }

        assertEquals(repository.installed.single().id, mapStore.state.value.activeChartPackageId)
        scope.cancel()
    }

    private class ImmediateMapStore : MapStore {
        private val mutable = MutableStateFlow(MapState())
        override val state: StateFlow<MapState> = mutable
        override fun dispatch(action: MapAction): MapDispatchResult {
            mutable.value = MapReducer.reduce(mutable.value, action).state
            return MapDispatchResult.ACCEPTED
        }
        override fun close() = Unit
    }

    private class FakeRepository : ChartPackageRepository {
        val installed = mutableListOf<ChartPackage>()
        override suspend fun inspect(sourceUri: String) = ChartPackageCandidate(
            "staged", "", "", "", "", "", "a".repeat(64),
            GeoBounds(-37.0, 174.0, -36.0, 176.0), 1, 14, "png",
        )
        override suspend fun commit(request: ChartPackageImportRequest): ChartPackage = ChartPackage(
            ChartPackageId("a".repeat(64)), request.displayName, request.source, request.license,
            request.attribution, "a".repeat(64), "mbtiles:///fixture", GeoBounds(-37.0, 174.0, -36.0, 176.0),
            1, 14, request.version,
        ).also(installed::add)
        override suspend fun discard(stagedImportId: String) = Unit
        override suspend fun listInstalled(): List<ChartPackage> = installed.toList()
        override suspend fun delete(packageId: ChartPackageId) { installed.removeAll { it.id == packageId } }
    }
}
