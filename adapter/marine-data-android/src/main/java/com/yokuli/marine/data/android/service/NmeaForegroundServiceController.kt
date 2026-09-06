package com.yokuli.marine.data.android.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

fun interface ForegroundRuntimeController {
    /** Returns false when Android rejects foreground-service startup. */
    fun reconcile(enabledConnectionCount: Int): Boolean

    companion object {
        val NO_OP = ForegroundRuntimeController { true }
    }
}

class NmeaForegroundServiceController(context: Context) : ForegroundRuntimeController {
    private val applicationContext = context.applicationContext

    override fun reconcile(enabledConnectionCount: Int): Boolean {
        val intent = Intent(applicationContext, NmeaInputForegroundService::class.java)
        return if (enabledConnectionCount > 0) {
            runCatching {
                ContextCompat.startForegroundService(applicationContext, intent)
            }.isSuccess
        } else {
            applicationContext.stopService(intent)
            true
        }
    }
}
