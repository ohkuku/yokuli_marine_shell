package com.yokuli.marine.core.design

import com.yokuli.shell.contract.MeasurementUnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

class MarineDisplayUnitsTest {
    @Test fun `unit preference converts presentation without mutating nautical truth`() {
        assertEquals(2.0, MarineDisplayUnits.distanceFromNauticalMiles(2.0, MeasurementUnitSystem.NAUTICAL), 0.0)
        assertEquals(3.704, MarineDisplayUnits.distanceFromNauticalMiles(2.0, MeasurementUnitSystem.METRIC), 0.0)
    }
}
