package com.yokuli.runtime.marine.ais

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

/** 恢复明确保存的监控意图；唯一运行时仍尊重连接自己的开机租约和手动关闭。 */
class AisRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!=Intent.ACTION_BOOT_COMPLETED)return
        val pending=goAsync()
        val app=context.applicationContext
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                val file=AtomicFile(File(app.filesDir,"ais/preferences-v1.json"))
                if(file.baseFile.length()>256*1024)return@launch
                val text=runCatching { file.openRead().use { stream ->
                    val output=java.io.ByteArrayOutputStream()
                    val buffer=ByteArray(4096)
                    while(output.size()<=256*1024) {
                        val count=stream.read(buffer,0,minOf(buffer.size,256*1024+1-output.size()))
                        if(count<0)break
                        output.write(buffer,0,count)
                    }
                    check(output.size()<=256*1024)
                    String(output.toByteArray(),Charsets.UTF_8)
                } }.getOrNull()?:return@launch
                val preferences=runCatching { JSONObject(text).optJSONObject("preferences") }.getOrNull()?:return@launch
                if(!preferences.optBoolean("cpaEnabled")&&!preferences.optBoolean("proximityEnabled")&&!preferences.optBoolean("anchorProximityEnabled"))return@launch
                // Do not construct the marine runtime at every boot when the
                // user has never enabled AIS background monitoring.
                runCatching { ContextCompat.startForegroundService(app,Intent(app,AisMonitoringService::class.java)) }
                    .onFailure { error ->
                        runCatching { File(app.filesDir,"ais/background-start-error.txt").writeText("Android could not restore AIS at boot: ${error.javaClass.simpleName}") }
                    }
            } finally { pending.finish() }
        }
    }
}
