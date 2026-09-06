package com.yokuli.marine.data.catalog

import com.yokuli.marine.data.model.MarineObservation
import com.yokuli.marine.data.model.ObservationValidity
import com.yokuli.marine.data.time.MonotonicClock

/** Current usability of an observation. HELD always means an aged value, never a new sample. */
enum class Freshness {
    LIVE,
    HELD,
    STALE,
    INVALID,
    UNAVAILABLE,
}

data class FreshnessThresholds(
    val heldAtMillis: Long = DEFAULT_HELD_AT_MILLIS,
    val staleAtMillis: Long = DEFAULT_STALE_AT_MILLIS,
) {
    init {
        require(heldAtMillis >= 0L) { "The HELD threshold must be non-negative" }
        require(staleAtMillis > heldAtMillis) { "STALE must begin after HELD" }
    }

    companion object {
        const val DEFAULT_HELD_AT_MILLIS = 3_000L
        const val DEFAULT_STALE_AT_MILLIS = 10_000L
    }
}

data class FreshnessEvaluation(
    val state: Freshness,
    val ageMillis: Long?,
    val evaluatedAtMillis: Long,
)

/**
 * Evaluates age against an injected monotonic clock. A timestamp from a later/different clock
 * epoch is rejected as unavailable instead of being mistaken for fresh data.
 */
class FreshnessPolicy(
    private val clock: MonotonicClock,
    private val thresholds: FreshnessThresholds = FreshnessThresholds(),
) {
    fun evaluate(
        observation: MarineObservation?,
        sourceAvailable: Boolean = true,
    ): FreshnessEvaluation = evaluateAt(observation, sourceAvailable, clock.nowMillis())

    internal fun evaluateAt(
        observation: MarineObservation?,
        sourceAvailable: Boolean,
        nowMillis: Long,
    ): FreshnessEvaluation {
        require(nowMillis >= 0L) { "Monotonic time must be non-negative" }
        if (observation == null) {
            return FreshnessEvaluation(Freshness.UNAVAILABLE, null, nowMillis)
        }

        val ageMillis = nowMillis - observation.measuredAtMillis
        if (ageMillis < 0L) {
            return FreshnessEvaluation(Freshness.UNAVAILABLE, null, nowMillis)
        }
        if (!sourceAvailable) {
            return FreshnessEvaluation(Freshness.UNAVAILABLE, ageMillis, nowMillis)
        }
        if (observation.validity == ObservationValidity.EXPLICIT_INVALID) {
            return FreshnessEvaluation(Freshness.INVALID, ageMillis, nowMillis)
        }

        val state = when {
            ageMillis < thresholds.heldAtMillis -> Freshness.LIVE
            ageMillis < thresholds.staleAtMillis -> Freshness.HELD
            else -> Freshness.STALE
        }
        return FreshnessEvaluation(state, ageMillis, nowMillis)
    }
}
