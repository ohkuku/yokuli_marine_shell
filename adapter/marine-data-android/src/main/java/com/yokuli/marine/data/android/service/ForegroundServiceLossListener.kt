package com.yokuli.marine.data.android.service

/** Android-only lifecycle signal; Feature and core runtime contracts cannot manufacture it. */
fun interface ForegroundServiceLossListener {
    /**
     * The visibility service disappeared while durable inputs remain enabled. Implementations must
     * release background resources without rewriting the user's enabled intent.
     */
    fun onForegroundServiceLost()
}
