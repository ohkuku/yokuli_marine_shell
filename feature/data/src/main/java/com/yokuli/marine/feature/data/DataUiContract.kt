package com.yokuli.marine.feature.data

import com.yokuli.marine.data.model.SourceIdentity

sealed interface DataUiAction {
    data class Navigate(val section: DataSection) : DataUiAction
    data class UseSource(val group: SourceGroup, val source: SourceIdentity) : DataUiAction
    data class UsePhone(val group: SourceGroup) : DataUiAction
    data class DisableGroup(val group: SourceGroup) : DataUiAction
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
}
