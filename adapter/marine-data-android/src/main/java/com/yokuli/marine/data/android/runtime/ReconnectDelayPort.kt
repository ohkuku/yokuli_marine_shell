package com.yokuli.marine.data.android.runtime

import kotlinx.coroutines.delay

fun interface ReconnectDelayPort {
    suspend fun await(delayMillis: Long)

    companion object {
        val SYSTEM = ReconnectDelayPort { delayMillis -> delay(delayMillis) }
    }
}

/** Injectable cadence for projecting time-based health even while no packets arrive. */
fun interface HealthTickPort {
    suspend fun awaitNextTick()

    companion object {
        private const val TICK_MILLIS = 1_000L
        val SYSTEM = HealthTickPort { delay(TICK_MILLIS) }
    }
}
