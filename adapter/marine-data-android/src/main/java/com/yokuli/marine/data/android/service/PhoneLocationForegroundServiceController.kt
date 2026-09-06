package com.yokuli.marine.data.android.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.yokuli.marine.data.android.location.PhoneLocationForegroundController

class PhoneLocationForegroundServiceController(context: Context) : PhoneLocationForegroundController {
    private val applicationContext = context.applicationContext

    override fun reconcile(enabled: Boolean): Boolean {
        val intent = Intent(applicationContext, PhoneLocationForegroundService::class.java)
        return if (enabled) {
            runCatching { ContextCompat.startForegroundService(applicationContext, intent) }.isSuccess
        } else {
            applicationContext.stopService(intent)
            true
        }
    }
}
