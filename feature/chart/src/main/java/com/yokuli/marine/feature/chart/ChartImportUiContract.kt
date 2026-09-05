package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.ChartPackageCandidate
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageImportFailure
import com.yokuli.marine.map.domain.ChartPackageOperationId

enum class ChartImportField { DISPLAY_NAME, SOURCE, LICENSE, ATTRIBUTION, VERSION }

sealed interface ChartImportUiState {
    data object Idle : ChartImportUiState
    data class Copying(
        val operationId: ChartPackageOperationId,
        val generation: Long,
        val completedBytes: Long,
        val totalBytes: Long?,
    ) : ChartImportUiState
    data class Inspecting(
        val operationId: ChartPackageOperationId,
        val generation: Long,
        val completedTiles: Long,
        val totalTiles: Long?,
    ) : ChartImportUiState
    data class ReadyToInstall(
        val operationId: ChartPackageOperationId,
        val generation: Long,
        val candidate: ChartPackageCandidate,
        val displayName: String = candidate.suggestedDisplayName,
        val source: String = candidate.suggestedSource,
        val license: String = candidate.suggestedLicense,
        val attribution: String = candidate.suggestedAttribution,
        val version: String = candidate.suggestedVersion,
        val validationFailure: ChartPackageImportFailure? = null,
    ) : ChartImportUiState
    data class Installing(
        val operationId: ChartPackageOperationId,
        val generation: Long,
    ) : ChartImportUiState
    data class Cancelled(
        val operationId: ChartPackageOperationId,
        val generation: Long,
    ) : ChartImportUiState
    data class Failed(
        val reason: ChartPackageImportFailure,
        val operationId: ChartPackageOperationId? = null,
        val generation: Long = 0L,
    ) : ChartImportUiState
}

sealed interface ChartImportUiAction {
    data object ChooseDocument : ChartImportUiAction
    data class UpdateField(val field: ChartImportField, val value: String) : ChartImportUiAction
    data object Install : ChartImportUiAction
    data object Cancel : ChartImportUiAction
    data class Activate(val packageId: ChartPackageId) : ChartImportUiAction
    data class Delete(val packageId: ChartPackageId) : ChartImportUiAction
}
