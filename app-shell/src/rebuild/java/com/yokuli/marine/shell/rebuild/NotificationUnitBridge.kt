package com.yokuli.marine.shell.rebuild

import com.yokuli.anchorwatch.runtime.notification.NotificationCoordinator
import com.yokuli.anchorwatch.runtime.notification.NotificationUnitFormats
import com.yokuli.marine.core.design.MarineUnitFormats
import com.yokuli.shell.contract.DepthUnit
import com.yokuli.shell.contract.LengthUnit
import com.yokuli.shell.contract.MarineUnitPreferences
import com.yokuli.shell.contract.MeasurementUnitSystem
import com.yokuli.shell.contract.PressureUnit
import com.yokuli.shell.contract.TemperatureUnit
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Application 启动即订阅同一份持久化系统偏好，后台冷启动也不等待 Activity。
 * 只传递不可变快照的纯格式化函数；不把 OsStore 或传感器所有权传入后台通知。
 */
internal fun OsStore.observeNotificationUnits(notifications: NotificationCoordinator) = scope.launch {
    shell.persistence.state.filterNotNull().map { saved ->
        MarineUnitPreferences.fromStored(saved.measurementUnitSystemName,saved.appPreferenceValues)
    }.distinctUntilChanged().collect { snapshot ->
        val formats = MarineUnitFormats(snapshot)
        notifications.installUnitFormats(NotificationUnitFormats(formats::length, formats::depth, formats::speed))
    }
}
