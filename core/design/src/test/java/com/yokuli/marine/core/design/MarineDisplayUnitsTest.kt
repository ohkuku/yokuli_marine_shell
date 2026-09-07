package com.yokuli.marine.core.design

import com.yokuli.shell.contract.MeasurementUnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

class MarineDisplayUnitsTest {
    @Test fun `unit preference converts presentation without mutating nautical truth`() {
        assertEquals(2.0, MarineDisplayUnits.distanceFromNauticalMiles(2.0, MeasurementUnitSystem.NAUTICAL), 0.0)
        assertEquals(3.704, MarineDisplayUnits.distanceFromNauticalMiles(2.0, MeasurementUnitSystem.METRIC), 0.0)
    }

    @Test fun `speed preference converts presentation without mutating knots truth`() {
        assertEquals(10.0, MarineDisplayUnits.speedFromKnots(10.0, MeasurementUnitSystem.NAUTICAL), 0.0)
        assertEquals(18.52, MarineDisplayUnits.speedFromKnots(10.0, MeasurementUnitSystem.METRIC), 0.0)
    }

    @Test fun `meter distance follows the same OS unit preference`() {
        assertEquals(2.0, MarineDisplayUnits.distanceFromMeters(3_704.0, MeasurementUnitSystem.NAUTICAL), 0.0)
        assertEquals(3.704, MarineDisplayUnits.distanceFromMeters(3_704.0, MeasurementUnitSystem.METRIC), 0.0)
    }
}
