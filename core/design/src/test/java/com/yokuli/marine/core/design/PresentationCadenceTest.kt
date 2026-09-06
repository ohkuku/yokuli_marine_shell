package com.yokuli.marine.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationCadenceTest {
    @Test
    fun twentyHertzInputHasAtMostFourVisibleLiveUpdatesPerSecond() {
        val clock = TestClock()
        val presentation = CadencedPresentation(PresentationCadence.DataOverview, clock, 0)

        repeat(20) { index ->
            clock.now += 50L
            presentation.submit(index + 1)
            presentation.flush()
        }

        assertEquals(20, presentation.visibleValue)
        assertEquals(5L, presentation.presentationCount) // initial + 4 visible updates
    }

    @Test
    fun disconnectAndOtherSafetyTruthBypassCadenceImmediately() {
        val clock = TestClock()
        val presentation = CadencedPresentation(PresentationCadence.NmeaStatus, clock, "receiving")
        clock.now = 20L
        assertTrue(presentation.submit("42 msg/s") is PresentationDecision.Deferred)

        clock.now = 21L
        assertEquals(
            PresentationDecision.Presented("disconnected"),
            presentation.submit("disconnected", PresentationChange.SAFETY),
        )
        assertEquals("disconnected", presentation.visibleValue)
    }

    @Test
    fun staleBoundaryIsAnImmediateDefinedStructuralTransition() {
        val clock = TestClock()
        val presentation = CadencedPresentation(PresentationCadence.ChartPosition, clock, "live · 0 s")
        clock.now = 10L
        presentation.submit("live · 1 s")

        clock.now = 11L
        presentation.submit("historical · 30 s", PresentationChange.STRUCTURAL)

        assertEquals("historical · 30 s", presentation.visibleValue)
    }

    @Test
    fun rawPreviewIsBoundedAndLatestSnapshotWins() {
        val clock = TestClock()
        val buffer = LatestWinsBatchBuffer<Int>(3, PresentationCadence.RawPreview, clock)
        repeat(20) { index ->
            clock.now += 50L
            buffer.submit(listOf(index, index - 1, index - 2, index - 3))
            buffer.flush()
        }

        assertEquals(listOf(19, 18, 17), buffer.visibleItems)
        assertEquals(3, buffer.capacity)
        assertTrue(buffer.presentationCount <= 5L)
    }

    private class TestClock(var now: Long = 0L) : PresentationClock {
        override fun nowMillis(): Long = now
    }
}
