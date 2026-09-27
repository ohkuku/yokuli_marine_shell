package com.yokuli.marine.shell.rebuild

import com.yokuli.runtime.contract.RuntimeReadiness
import com.yokuli.runtime.contract.RuntimeUnitPreferences
import com.yokuli.runtime.marine.MarineSystem
import com.yokuli.shell.contract.MarineUnitPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Shell 是设置唯一所有者；后台收到不可变单位投影，重连后重新同步，冷启动沿用上次成功投影。 */
internal fun OsStore.observeNotificationUnits(system: MarineSystem) = scope.launch {
    combine(shell.persistence.state.filterNotNull().map { saved ->
        MarineUnitPreferences.fromStored(saved.measurementUnitSystemName, saved.appPreferenceValues)
    }.distinctUntilChanged(), system.connection) { units, connection -> units to connection }
        .collectLatest { (units, connection) ->
            if (connection.readiness != RuntimeReadiness.READY) return@collectLatest
            val snapshot = RuntimeUnitPreferences(units.navigation.name, units.length.name, units.depth.name,
                units.temperature.name, units.pressure.name, units.distance.name, units.speed.name, units.scaleDistance?.name)
            while (true) {
                try { system.presentation.updateUnits(snapshot); break }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { delay(5_000) }
            }
        }
}
