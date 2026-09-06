package com.yokuli.marine.data.android.location

import com.yokuli.marine.data.phone.PhoneLocationPermission
import com.yokuli.marine.data.phone.PlatformLocationFix

interface PhoneLocationPlatform {
    fun permission(): PhoneLocationPermission
    fun isSystemLocationEnabled(): Boolean
    fun start(onFix: (PlatformLocationFix) -> Unit, onProviderChanged: () -> Unit)
    fun stop()
}

fun interface PhoneLocationForegroundController {
    /** False means Android rejected foreground execution; no location listener may start. */
    fun reconcile(enabled: Boolean): Boolean

    companion object {
        val NO_OP = PhoneLocationForegroundController { true }
    }
}
