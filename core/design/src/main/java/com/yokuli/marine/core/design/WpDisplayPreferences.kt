package com.yokuli.marine.core.design

import androidx.compose.runtime.staticCompositionLocalOf
import com.yokuli.shell.contract.MeasurementUnitSystem

/**
 * Presentation-only OS preference. Domain values stay in their canonical marine units; consumers
 * opt in at their rendering edge so changing this value cannot rewrite persisted or live data.
 */
val LocalMeasurementUnitSystem = staticCompositionLocalOf { MeasurementUnitSystem.NAUTICAL }

object MarineDisplayUnits {
    const val NAUTICAL_MILES_TO_KILOMETRES = 1.852

    fun distanceFromNauticalMiles(value: Double, units: MeasurementUnitSystem): Double = when (units) {
        MeasurementUnitSystem.NAUTICAL -> value
        MeasurementUnitSystem.METRIC -> value * NAUTICAL_MILES_TO_KILOMETRES
    }
}
