package com.yokuli.marine.data.android.runtime

import android.os.SystemClock
import com.yokuli.marine.data.time.MonotonicClock

object AndroidElapsedRealtimeClock : MonotonicClock {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}
