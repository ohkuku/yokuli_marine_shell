package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.anchorwatch.domain.vessel.InstrumentTileId
import com.yokuli.anchorwatch.domain.vessel.VesselDataFreshness
import com.yokuli.marine.shell.rebuild.data.Reading
import org.junit.Assert.*
import org.junit.Test

class InstrumentReadingPolicyTest {
    @Test fun lastDepthAndSpeedRemainReadableWhenUpdatesPause() {
        for (state in listOf(VesselDataFreshness.HELD, VesselDataFreshness.STALE)) {
            assertTrue(InstrumentReadingPolicy.displayable(InstrumentTileId.DEPTH, state))
            assertTrue(InstrumentReadingPolicy.displayable(InstrumentTileId.SOG, state))
            assertFalse(InstrumentReadingPolicy.displayable(InstrumentTileId.HEADING, state))
            assertFalse(InstrumentReadingPolicy.displayable(InstrumentTileId.HEEL, state))
        }
    }

    @Test fun signedWindAnglesKeepTheirPortAndStarboardPositions() {
        for (angle in listOf(20.0, 90.0, 170.0, 180.0)) {
            val port = InstrumentReadingPolicy.signedWindFraction(-angle)
            val starboard = InstrumentReadingPolicy.signedWindFraction(angle)
            assertTrue(port < 0f)
            assertEquals(-starboard, port, .0001f)
        }
        assertEquals(0f, InstrumentReadingPolicy.signedWindFraction(0.0), .0001f)
    }

    @Test fun headingsKeepTheAuthoritativeFifteenSecondLease() {
        val heading = Reading(120.0, "°T", "compass", 1000, validForMillis = 15_000)
        assertTrue(heading.fresh(13_000))
        assertFalse(heading.fresh(17_000))
        assertFalse(heading.copy(freshness = VesselDataFreshness.HELD).fresh(2000))
    }

    @Test fun thirtySecondPressureUpdatesAreAContinuousHistory() {
        val previous = Reading(1013.0, "hPa", "barometer", 1000, sourceKey = "phone:pressure", validForMillis = 60_000)
        assertTrue(readingsAreContinuous(previous, previous.copy(value = 1012.9, elapsed = 31_000)))
        assertFalse(readingsAreContinuous(previous, previous.copy(elapsed = 61_001)))
    }

    @Test fun twoDevicesWithTheSameNameHaveSeparateTraces() {
        val first = Reading(5.0, "kn", "Boat", 1000, sourceKey = "connection-a:1")
        assertFalse(readingsAreContinuous(first, first.copy(elapsed = 2000, sourceKey = "connection-b:1")))
        assertFalse(readingsAreContinuous(first, first.copy(elapsed = 2000, sourceKey = "connection-a:2")))
    }

    @Test fun northCrossingDoesNotDrawALineAcrossTheTrend() {
        val first = Reading(359.0, "°T", "compass", 1000)
        assertFalse(readingsAreContinuous(first, first.copy(value = 1.0, elapsed = 2000)))
    }

    @Test fun everyNumericInstrumentHasItsOwnHistoryKey() {
        val keys = InstrumentTileId.entries.filter { it != InstrumentTileId.POSITION }.map { instrumentTrendKey(it) }
        assertFalse(keys.contains(null))
        assertEquals(keys.size, keys.toSet().size)
        assertEquals("pressure", instrumentTrendKey(InstrumentTileId.PRESSURE))
        assertEquals("depth", instrumentTrendKey(InstrumentTileId.DEPTH))
        assertEquals("twa", instrumentTrendKey(InstrumentTileId.TRUE_WIND_ANGLE))
    }
}
