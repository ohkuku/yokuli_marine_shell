package com.yokuli.marine.data.android.runtime

import com.yokuli.marine.data.runtime.NmeaInputRuntimePort

/** Implemented only by the process Application composition root. */
interface MarineDataRuntimeOwner {
    val nmeaInputRuntime: NmeaInputRuntimePort
}
