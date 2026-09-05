package com.yokuli.marine.data.time

/** Injected monotonic time. Platform adapters own the actual boot-clock implementation. */
fun interface MonotonicClock {
    fun nowMillis(): Long
}
