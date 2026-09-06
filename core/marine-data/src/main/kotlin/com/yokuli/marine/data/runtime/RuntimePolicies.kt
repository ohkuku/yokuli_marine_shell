package com.yokuli.marine.data.runtime

fun interface RetryJitter {
    fun apply(attempt: Int, baseDelayMillis: Long): Long

    companion object {
        val NONE = RetryJitter { _, baseDelayMillis -> baseDelayMillis }
    }
}

/** One-based bounded exponential reconnect policy; jitter is injected only after base selection. */
class RetryPolicy(
    private val jitter: RetryJitter = RetryJitter.NONE,
    private val maxJitterMillis: Long = 3_000L,
) {
    init {
        require(maxJitterMillis >= 0L) { "Maximum retry jitter must be non-negative" }
    }

    fun delayMillis(attempt: Int): Long {
        require(attempt >= 1) { "Retry attempts are one-based" }
        val base = BASE_DELAYS.getOrElse(attempt - 1) { MAX_BASE_DELAY_MILLIS }
        return jitter.apply(attempt, base).coerceIn(
            minimumValue = 0L,
            maximumValue = Math.addExact(MAX_BASE_DELAY_MILLIS, maxJitterMillis),
        )
    }

    companion object {
        private val BASE_DELAYS = longArrayOf(
            1_000L,
            2_000L,
            4_000L,
            8_000L,
            16_000L,
            30_000L,
        )
        const val MAX_BASE_DELAY_MILLIS: Long = 30_000L
    }
}

/** Pure input-health policy. Transport state and durable run intent never participate. */
class InputHealthPolicy(
    val interruptionTimeoutMillis: Long = 10_000L,
) {
    init {
        require(interruptionTimeoutMillis > 0L) { "Input interruption timeout must be positive" }
    }

    fun evaluate(
        nowMillis: Long,
        lastByteAtMillis: Long?,
        lastValidFrameAtMillis: Long?,
        overloaded: Boolean = false,
    ): ConnectionInputState {
        require(nowMillis >= 0L) { "Monotonic time must be non-negative" }
        require(listOfNotNull(lastByteAtMillis, lastValidFrameAtMillis).all { it >= 0L }) {
            "Input evidence times must be non-negative"
        }
        if (overloaded) return ConnectionInputState.OVERLOADED
        if (lastValidFrameAtMillis != null) {
            val elapsed = nonNegativeElapsed(nowMillis, lastValidFrameAtMillis)
            return if (elapsed >= interruptionTimeoutMillis) {
                ConnectionInputState.INTERRUPTED
            } else {
                ConnectionInputState.RECEIVING_VALID_FRAMES
            }
        }
        return if (lastByteAtMillis == null) {
            ConnectionInputState.NO_BYTES
        } else {
            ConnectionInputState.BYTES_WITHOUT_VALID_FRAME
        }
    }

    private fun nonNegativeElapsed(nowMillis: Long, evidenceMillis: Long): Long =
        if (nowMillis >= evidenceMillis) nowMillis - evidenceMillis else 0L
}

/** Internal bounded accumulator backing the public immutable [FiveSecondFrameRate]. */
internal class FiveSecondFrameRateWindow {
    private val buckets = sortedMapOf<Long, Long>()
    private var latestRecordedAtMillis: Long? = null

    fun record(atMillis: Long) {
        require(atMillis >= 0L) { "Frame time must be monotonic and non-negative" }
        val latest = maxOf(latestRecordedAtMillis ?: atMillis, atMillis)
        latestRecordedAtMillis = latest
        prune(latest)
        val bucket = bucketStart(atMillis)
        if (bucket > cutoff(latest)) {
            buckets[bucket] = saturatingAdd(buckets[bucket] ?: 0L, 1L)
        }
        prune(latest)
        check(buckets.size <= FiveSecondFrameRate.MAX_BUCKETS) {
            "Five-second frame-rate window exceeded its bucket bound"
        }
    }

    fun snapshot(nowMillis: Long): FiveSecondFrameRate {
        require(nowMillis >= 0L) { "Monotonic time must be non-negative" }
        val reference = maxOf(latestRecordedAtMillis ?: nowMillis, nowMillis)
        prune(reference)
        val nowBucket = bucketStart(nowMillis)
        val retained = buckets.filterKeys { bucket -> bucket > cutoff(nowMillis) && bucket <= nowBucket }
        val count = retained.values.fold(0L, ::saturatingAdd)
        return FiveSecondFrameRate(
            framesPerSecond = count.toDouble() / FiveSecondFrameRate.MAX_BUCKETS.toDouble(),
            retainedBucketCount = retained.size,
        )
    }

    fun clear() {
        buckets.clear()
        latestRecordedAtMillis = null
    }

    private fun prune(referenceMillis: Long) {
        val expired = buckets.keys.takeWhile { it <= cutoff(referenceMillis) }
        expired.forEach(buckets::remove)
    }

    private fun cutoff(referenceMillis: Long): Long =
        referenceMillis - FiveSecondFrameRate.WINDOW_MILLIS

    private fun bucketStart(atMillis: Long): Long =
        atMillis - (atMillis % FiveSecondFrameRate.BUCKET_MILLIS)
}

internal fun saturatingAdd(value: Long, increment: Long): Long {
    require(value >= 0L && increment >= 0L)
    return if (Long.MAX_VALUE - value < increment) Long.MAX_VALUE else value + increment
}
