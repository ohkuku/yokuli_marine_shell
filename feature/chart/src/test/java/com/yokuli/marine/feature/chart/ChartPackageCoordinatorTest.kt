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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    fun `display name gates install while unknown legal facts remain truthful`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val mapStore = ImmediateMapStore()
        val repository = FakeRepository()
        val coordinator = ChartPackageCoordinator(repository, mapStore, scope)

        coordinator.inspectDocument("content://fixture")
        withTimeout(2_000L) { coordinator.state.first { it is ChartImportUiState.ReadyToInstall } }
        coordinator.dispatch(ChartImportUiAction.Install)
        assertTrue((coordinator.state.value as ChartImportUiState.ReadyToInstall).validationFailure != null)

        coordinator.dispatch(ChartImportUiAction.UpdateField(ChartImportField.DISPLAY_NAME, "Harbour"))
        coordinator.dispatch(ChartImportUiAction.Install)
        withTimeout(2_000L) { coordinator.state.first { it == ChartImportUiState.Idle } }

        assertEquals(repository.installed.single().id, mapStore.state.value.activeChartPackageId)
        assertEquals("Unknown", repository.installed.single().source)
        assertEquals("Unknown", repository.installed.single().license)
        assertEquals("Unknown", repository.installed.single().attribution)
        assertEquals("Unknown", repository.installed.single().version)
        scope.cancel()
    }

    @Test
    fun lateInspectCompletionCannotReplaceNewerSelection() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val mapStore = ImmediateMapStore()
        val repository = RacingRepository()
        val coordinator = ChartPackageCoordinator(repository, mapStore, scope)

        coordinator.inspectDocument("content://old")
        delay(20)
        coordinator.inspectDocument("content://new")
        withTimeout(2_000L) {
            coordinator.state.first {
                it is ChartImportUiState.ReadyToInstall && it.candidate.suggestedDisplayName == "new"
            }
        }
        delay(250)

        val current = coordinator.state.value as ChartImportUiState.ReadyToInstall
        assertEquals("new", current.candidate.suggestedDisplayName)
        scope.cancel()
    }

    @Test
    fun cancelIsNotReportedAsFailure() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = ChartPackageCoordinator(SlowRepository(), ImmediateMapStore(), scope)

        coordinator.inspectDocument("content://slow")
        withTimeout(2_000L) { coordinator.state.first { it is ChartImportUiState.Copying } }
        coordinator.dispatch(ChartImportUiAction.Cancel)
        withTimeout(2_000L) { coordinator.state.first { it is ChartImportUiState.Cancelled } }
        assertTrue(coordinator.state.value !is ChartImportUiState.Failed)
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

    private open class FakeRepository : ChartPackageRepository {
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

    private open class SlowRepository : FakeRepository() {
        override suspend fun inspect(
            sourceUri: String,
            operationId: com.yokuli.marine.map.domain.ChartPackageOperationId,
            onProgress: (com.yokuli.marine.map.domain.ChartPackageInspectProgress) -> Unit,
        ): ChartPackageCandidate {
            onProgress(com.yokuli.marine.map.domain.ChartPackageInspectProgress.Copying(1L, null))
            delay(5_000L)
            return super.inspect(sourceUri)
        }
    }

    private class RacingRepository : SlowRepository() {
        override suspend fun inspect(
            sourceUri: String,
            operationId: com.yokuli.marine.map.domain.ChartPackageOperationId,
            onProgress: (com.yokuli.marine.map.domain.ChartPackageInspectProgress) -> Unit,
        ): ChartPackageCandidate {
            onProgress(com.yokuli.marine.map.domain.ChartPackageInspectProgress.Copying(1L, 1L))
            if (sourceUri.endsWith("old")) {
                kotlinx.coroutines.withContext(NonCancellable) { delay(180L) }
            } else {
                delay(20L)
            }
            return super.inspect(sourceUri).copy(suggestedDisplayName = sourceUri.substringAfterLast('/'))
        }
    }
}
