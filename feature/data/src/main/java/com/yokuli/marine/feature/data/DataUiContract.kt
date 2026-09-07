package com.yokuli.marine.feature.data

import com.yokuli.marine.data.model.SourceIdentity

sealed interface DataUiAction {
    data class NavigatePrimary(val area: PrimaryDataArea) : DataUiAction
    data class Navigate(val section: DataSection) : DataUiAction
    data class OpenSensor(val sensor: BoatSensor) : DataUiAction
    data class OpenTrust(val group: SourceGroup) : DataUiAction
    data object OpenAddSource : DataUiAction
    data class ChooseConnectionType(val type: DataConnectionType) : DataUiAction
    data class ChangeConnectionType(val type: DataConnectionType) : DataUiAction
    data object ChoosePhoneSource : DataUiAction
    data class ChangeConnectionHost(val value: String) : DataUiAction
    data class ChangeConnectionPort(val value: String) : DataUiAction
    data class ChangeConnectionName(val value: String) : DataUiAction
    data object TestAndSaveConnection : DataUiAction
    data object FinishConnectionSetup : DataUiAction
    data object CancelConnectionSetup : DataUiAction
    data class OpenConnection(val id: com.yokuli.marine.data.model.ConnectionId) : DataUiAction
    data class EditConnection(val id: com.yokuli.marine.data.model.ConnectionId) : DataUiAction
    data class StartConnection(val id: com.yokuli.marine.data.model.ConnectionId) : DataUiAction
    data class StopConnection(val id: com.yokuli.marine.data.model.ConnectionId) : DataUiAction
    data class RetryConnection(val id: com.yokuli.marine.data.model.ConnectionId) : DataUiAction
    data class DeleteConnection(val id: com.yokuli.marine.data.model.ConnectionId) : DataUiAction
    data class OpenDiagnostics(val connectionId: com.yokuli.marine.data.model.ConnectionId? = null) : DataUiAction
    data object ToggleFlowExpert : DataUiAction
    data class UseSource(val group: SourceGroup, val source: SourceIdentity) : DataUiAction
    data class UsePhone(val group: SourceGroup) : DataUiAction
    data class DisableGroup(val group: SourceGroup) : DataUiAction
    data class InspectFlowSource(val source: SourceIdentity) : DataUiAction
    data class InspectFlowGroup(val group: SourceGroup) : DataUiAction
    data class PhonePermissionResult(val permanentlyDenied: Boolean) : DataUiAction
    data object ResolvePhoneDemand : DataUiAction
    data object ClearSourceFocus : DataUiAction
    data object DismissNotice : DataUiAction
}

sealed interface DataEffect {
    data object RequestPhoneLocationPermission : DataEffect
    data object OpenSystemLocationSettings : DataEffect
    data object OpenAppPermissionSettings : DataEffect
}

object DataBackPolicy {
    fun actionFor(section: DataSection): DataUiAction? = when (section) {
        DataSection.OVERVIEW -> null
        else -> DataUiAction.Navigate(DataSection.OVERVIEW)
    }

    fun parent(surface: DataSurface): DataSurface? = when (surface) {
        is DataSurface.Primary -> if (surface.area == PrimaryDataArea.BOAT) {
            null
        } else {
            DataSurface.Primary(PrimaryDataArea.BOAT)
        }
        is DataSurface.Sensor,
        is DataSurface.Trust,
        -> DataSurface.Primary(PrimaryDataArea.BOAT)
        is DataSurface.Connection -> DataSurface.Primary(PrimaryDataArea.CONNECTIONS)
        is DataSurface.Diagnostics -> surface.connectionId?.let(DataSurface::Connection)
            ?: DataSurface.Primary(PrimaryDataArea.BOAT)
        DataSurface.AddSource -> DataSurface.Primary(PrimaryDataArea.CONNECTIONS)
        is DataSurface.ConnectionWizard -> DataSurface.AddSource
    }
}
