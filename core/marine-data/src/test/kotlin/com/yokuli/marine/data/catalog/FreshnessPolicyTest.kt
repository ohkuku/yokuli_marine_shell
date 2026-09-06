package com.yokuli.marine.data.catalog

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.ChecksumTrust
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineObservation
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationOrigin
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.ObservationValidity
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.time.MonotonicClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreshnessPolicyTest {
    private val clock = FakeMonotonicClock(10_000L)
    private val evaluator = FreshnessPolicy(clock)

    @Test
    fun exactAgeBoundariesAreLiveHeldAndStale() {
        assertEquals(Freshness.LIVE, evaluator.evaluate(valid(at = 7_001L)).state)
        assertEquals(Freshness.HELD, evaluator.evaluate(valid(at = 7_000L)).state)
        assertEquals(Freshness.HELD, evaluator.evaluate(valid(at = 1L)).state)
        assertEquals(Freshness.STALE, evaluator.evaluate(valid(at = 0L)).state)
    }

    @Test
    fun explicitInvalidityTakesEffectImmediatelyWithoutPretendingTheValueIsZero() {
        val result = evaluator.evaluate(invalid(at = 9_999L))

        assertEquals(Freshness.INVALID, result.state)
        assertEquals(1L, result.ageMillis)
        assertNull(invalid(at = 9_999L).value)
    }

    @Test
    fun unavailableSourceAndImpossibleFutureTimestampNeverLookLive() {
        assertEquals(
            Freshness.UNAVAILABLE,
            evaluator.evaluate(valid(at = 9_999L), sourceAvailable = false).state,
        )
        val future = evaluator.evaluate(valid(at = 10_001L))
        assertEquals(Freshness.UNAVAILABLE, future.state)
        assertNull(future.ageMillis)
    }

    @Test
    fun freshnessAgesWithoutNewPackets() {
        val observation = valid(at = 1_000L)
        clock.now = 3_999L
        assertEquals(Freshness.LIVE, evaluator.evaluate(observation).state)

        clock.now = 4_000L
        assertEquals(Freshness.HELD, evaluator.evaluate(observation).state)

        clock.now = 11_000L
        assertEquals(Freshness.STALE, evaluator.evaluate(observation).state)
    }

    private fun valid(at: Long) = MarineObservation(
        key = DataKey.SpeedOverGround,
        value = MarineValue.Decimal(5.0, MarineUnit.KNOTS),
        validity = ObservationValidity.VALID,
        origin = origin(),
        measuredAtMillis = at,
        groupId = ObservationGroupId(at),
        checksumTrust = ChecksumTrust.VERIFIED,
    )

    private fun invalid(at: Long) = MarineObservation(
        key = DataKey.SpeedOverGround,
        value = null,
        validity = ObservationValidity.EXPLICIT_INVALID,
        origin = origin(),
        measuredAtMillis = at,
        groupId = ObservationGroupId(at),
        checksumTrust = ChecksumTrust.VERIFIED,
    )

    private fun origin() = ObservationOrigin(
        source = SourceIdentity(ConnectionId("gateway")),
        sessionGeneration = SessionGeneration(1),
        talker = "GP",
        formatter = "RMC",
    )
}

private class FakeMonotonicClock(var now: Long) : MonotonicClock {
    override fun nowMillis(): Long = now
}
