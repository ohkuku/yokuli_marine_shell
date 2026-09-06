package com.yokuli.marine.data.android.runtime

import com.yokuli.marine.data.runtime.NmeaInputRuntimePort
import com.yokuli.marine.data.phone.PhoneLocationRuntimePort
import com.yokuli.marine.data.source.MarineSourceRuntimePort

/** Implemented only by the process Application composition root. */
interface MarineDataRuntimeOwner {
    val nmeaInputRuntime: NmeaInputRuntimePort
    val phoneLocationRuntime: PhoneLocationRuntimePort
    val marineSourceRuntime: MarineSourceRuntimePort
}
