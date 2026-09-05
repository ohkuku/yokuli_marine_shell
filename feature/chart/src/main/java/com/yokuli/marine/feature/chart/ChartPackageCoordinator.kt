package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageImportException
import com.yokuli.marine.map.domain.ChartPackageImportFailure
import com.yokuli.marine.map.domain.ChartPackageImportRequest
import com.yokuli.marine.map.domain.ChartPackageInspectProgress
import com.yokuli.marine.map.domain.ChartPackageOperationId
import com.yokuli.marine.map.domain.ChartPackageRepository
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapStore
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Map-owned package workflow. The Shell only supplies a document URI when requested. */
class ChartPackageCoordinator(
    private val repository: ChartPackageRepository,
    private val mapStore: MapStore,
    private val scope: CoroutineScope,
    private val incidentLogger: (Throwable) -> Unit = {},
) {
    private val mutableState = MutableStateFlow<ChartImportUiState>(ChartImportUiState.Idle)
    val state: StateFlow<ChartImportUiState> = mutableState.asStateFlow()
    private var operationGeneration = 0L
    private var activeJob: Job? = null

    init { refreshPackages() }

    fun inspectDocument(sourceUri: String) {
        activeJob?.cancel()
        val generation = ++operationGeneration
        val operationId = ChartPackageOperationId(UUID.randomUUID().toString())
        mutableState.value = ChartImportUiState.Copying(operationId, generation, 0L, null)
        activeJob = scope.launch {
            try {
                val candidate = repository.inspect(sourceUri, operationId) { progress ->
                    if (generation == operationGeneration) {
                        mutableState.value = when (progress) {
                            is ChartPackageInspectProgress.Copying -> ChartImportUiState.Copying(
                                operationId, generation, progress.completedBytes, progress.totalBytes,
                            )
                            is ChartPackageInspectProgress.Inspecting -> ChartImportUiState.Inspecting(
                                operationId, generation, progress.completedTiles, progress.totalTiles,
                            )
                        }
                    }
                }
                if (generation == operationGeneration) {
                    mutableState.value = ChartImportUiState.ReadyToInstall(operationId, generation, candidate)
                } else {
                    repository.discard(candidate.stagedImportId)
                }
            } catch (_: CancellationException) {
                // Cancel is an intentional state transition; it must never be reported as a failure.
            } catch (error: Throwable) {
                if (generation == operationGeneration) mutableState.value = failure(error, operationId, generation)
            }
        }
    }

    fun dispatch(action: ChartImportUiAction) {
        when (action) {
            ChartImportUiAction.ChooseDocument -> Unit
            is ChartImportUiAction.UpdateField -> updateField(action)
            ChartImportUiAction.Install -> install()
            ChartImportUiAction.Cancel -> cancel()
            is ChartImportUiAction.Activate -> mapStore.dispatch(MapAction.SelectChartPackage(action.packageId))
            is ChartImportUiAction.Delete -> delete(action.packageId)
        }
    }

    private fun updateField(action: ChartImportUiAction.UpdateField) {
        val current = mutableState.value as? ChartImportUiState.ReadyToInstall ?: return
        mutableState.value = when (action.field) {
            ChartImportField.DISPLAY_NAME -> current.copy(displayName = action.value, validationFailure = null)
            ChartImportField.SOURCE -> current.copy(source = action.value, validationFailure = null)
            ChartImportField.LICENSE -> current.copy(license = action.value, validationFailure = null)
            ChartImportField.ATTRIBUTION -> current.copy(attribution = action.value, validationFailure = null)
            ChartImportField.VERSION -> current.copy(version = action.value, validationFailure = null)
        }
    }

    private fun install() {
        val current = mutableState.value as? ChartImportUiState.ReadyToInstall ?: return
        if (current.displayName.isBlank()) {
            mutableState.value = current.copy(validationFailure = ChartPackageImportFailure.REQUIRED_FIELD_MISSING)
            return
        }
        activeJob?.cancel()
        mutableState.value = ChartImportUiState.Installing(current.operationId, current.generation)
        activeJob = scope.launch {
            try {
                val installed = repository.commit(
                    ChartPackageImportRequest(
                        current.candidate.stagedImportId,
                        current.displayName,
                        current.source.ifBlank { UNKNOWN_FACT },
                        current.license.ifBlank { UNKNOWN_FACT },
                        current.attribution.ifBlank { UNKNOWN_FACT },
                        current.version.ifBlank { UNKNOWN_FACT },
                    ),
                )
                mapStore.dispatch(MapAction.ChartPackagesChanged(repository.listInstalled()))
                mapStore.dispatch(MapAction.SelectChartPackage(installed.id))
                if (current.generation == operationGeneration) mutableState.value = ChartImportUiState.Idle
            } catch (_: CancellationException) {
                // Superseded or cancelled work is not an incident.
            } catch (error: Throwable) {
                if (current.generation == operationGeneration) {
                    mutableState.value = failure(error, current.operationId, current.generation)
                }
            }
        }
    }

    private fun cancel() {
        val previous = mutableState.value
        val operationId = when (previous) {
            is ChartImportUiState.Copying -> previous.operationId
            is ChartImportUiState.Inspecting -> previous.operationId
            is ChartImportUiState.ReadyToInstall -> previous.operationId
            is ChartImportUiState.Installing -> previous.operationId
            is ChartImportUiState.Cancelled -> previous.operationId
            is ChartImportUiState.Failed -> previous.operationId
            ChartImportUiState.Idle -> null
        } ?: return
        activeJob?.cancel()
        val generation = ++operationGeneration
        mutableState.value = ChartImportUiState.Cancelled(operationId, generation)
        if (previous is ChartImportUiState.ReadyToInstall) {
            scope.launch { repository.discard(previous.candidate.stagedImportId) }
        }
    }

    private fun delete(packageId: ChartPackageId) {
        scope.launch {
            try {
                repository.delete(packageId)
                mapStore.dispatch(MapAction.ChartPackagesChanged(repository.listInstalled()))
            } catch (error: Throwable) {
                mutableState.value = failure(error)
            }
        }
    }

    private fun refreshPackages() {
        scope.launch {
            try {
                repository.reconcile()
                mapStore.dispatch(MapAction.ChartPackagesChanged(repository.listInstalled()))
            } catch (error: Throwable) {
                incidentLogger(error)
            }
        }
    }

    private fun failure(
        error: Throwable,
        operationId: ChartPackageOperationId? = null,
        generation: Long = operationGeneration,
    ): ChartImportUiState.Failed {
        incidentLogger(error)
        val reason = (error as? ChartPackageImportException)?.reason ?: ChartPackageImportFailure.IO_FAILURE
        return ChartImportUiState.Failed(reason, operationId, generation)
    }

    private companion object { const val UNKNOWN_FACT = "Unknown" }
}
