package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.ChartPackageCandidate
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageImportFailure

enum class ChartImportField { DISPLAY_NAME, SOURCE, LICENSE, ATTRIBUTION, VERSION }

sealed interface ChartImportUiState {
    data object Idle : ChartImportUiState
    data object Inspecting : ChartImportUiState
    data class Editing(
        val candidate: ChartPackageCandidate,
        val displayName: String = candidate.suggestedDisplayName,
        val source: String = candidate.suggestedSource,
        val license: String = candidate.suggestedLicense,
        val attribution: String = candidate.suggestedAttribution,
        val version: String = candidate.suggestedVersion,
        val validationFailure: ChartPackageImportFailure? = null,
    ) : ChartImportUiState
    data object Installing : ChartImportUiState
    data class Failed(val reason: ChartPackageImportFailure) : ChartImportUiState
}

sealed interface ChartImportUiAction {
    data object ChooseDocument : ChartImportUiAction
    data class UpdateField(val field: ChartImportField, val value: String) : ChartImportUiAction
    data object Install : ChartImportUiAction
    data object Cancel : ChartImportUiAction
    data class Activate(val packageId: ChartPackageId) : ChartImportUiAction
    data class Delete(val packageId: ChartPackageId) : ChartImportUiAction
}
