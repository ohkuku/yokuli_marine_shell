package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageImportException
import com.yokuli.marine.map.domain.ChartPackageImportFailure
import com.yokuli.marine.map.domain.ChartPackageImportRequest
import com.yokuli.marine.map.domain.ChartPackageRepository
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapStore
import kotlinx.coroutines.CoroutineScope
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

    init { refreshPackages() }

    fun inspectDocument(sourceUri: String) {
        mutableState.value = ChartImportUiState.Inspecting
        scope.launch {
            mutableState.value = try {
                ChartImportUiState.Editing(repository.inspect(sourceUri))
            } catch (error: Throwable) {
                failure(error)
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
        val current = mutableState.value as? ChartImportUiState.Editing ?: return
        mutableState.value = when (action.field) {
            ChartImportField.DISPLAY_NAME -> current.copy(displayName = action.value, validationFailure = null)
            ChartImportField.SOURCE -> current.copy(source = action.value, validationFailure = null)
            ChartImportField.LICENSE -> current.copy(license = action.value, validationFailure = null)
            ChartImportField.ATTRIBUTION -> current.copy(attribution = action.value, validationFailure = null)
            ChartImportField.VERSION -> current.copy(version = action.value, validationFailure = null)
        }
    }

    private fun install() {
        val current = mutableState.value as? ChartImportUiState.Editing ?: return
        if (listOf(current.displayName, current.source, current.license, current.attribution, current.version).any { it.isBlank() }) {
            mutableState.value = current.copy(validationFailure = ChartPackageImportFailure.REQUIRED_FIELD_MISSING)
            return
        }
        mutableState.value = ChartImportUiState.Installing
        scope.launch {
            try {
                val installed = repository.commit(
                    ChartPackageImportRequest(
                        current.candidate.stagedImportId,
                        current.displayName,
                        current.source,
                        current.license,
                        current.attribution,
                        current.version,
                    ),
                )
                mapStore.dispatch(MapAction.ChartPackagesChanged(repository.listInstalled()))
                mapStore.dispatch(MapAction.SelectChartPackage(installed.id))
                mutableState.value = ChartImportUiState.Idle
            } catch (error: Throwable) {
                mutableState.value = failure(error)
            }
        }
    }

    private fun cancel() {
        val current = mutableState.value as? ChartImportUiState.Editing
        mutableState.value = ChartImportUiState.Idle
        if (current != null) scope.launch { repository.discard(current.candidate.stagedImportId) }
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
                mapStore.dispatch(MapAction.ChartPackagesChanged(repository.listInstalled()))
            } catch (error: Throwable) {
                incidentLogger(error)
            }
        }
    }

    private fun failure(error: Throwable): ChartImportUiState.Failed {
        incidentLogger(error)
        val reason = (error as? ChartPackageImportException)?.reason ?: ChartPackageImportFailure.IO_FAILURE
        return ChartImportUiState.Failed(reason)
    }
}
