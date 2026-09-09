package com.yokuli.marine.shell.rebuild

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.runtime.*
import com.yokuli.shell.engine.InternalAppTaskId
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class TaskSnapshot(val bitmap:Bitmap,val capturedAt:Long)

/** One real composited image per open task, in memory only. Includes native map textures. */
class TaskSnapshotStore {
    val images=mutableStateMapOf<InternalAppTaskId,TaskSnapshot>()
    private data class Binding(val owner:Any,val taskId:InternalAppTaskId,val window:Window,val rect:Rect)
    private var binding:Binding?=null
    private val captureMutex=Mutex()

    fun bind(owner:Any,taskId:InternalAppTaskId,window:Window,rect:Rect) {
        if(rect.width()<=0 || rect.height()<=0)return
        val next=Binding(owner,taskId,window,Rect(rect))
        if(binding!=next)binding=next
    }
    fun unbind(owner:Any) { if(binding?.owner===owner)binding=null }
    fun retain(ids:Set<InternalAppTaskId>) { images.keys.toList().filterNot {it in ids}.forEach(images::remove) }

    suspend fun captureCurrent(expectedTaskId:InternalAppTaskId?=null):Boolean = captureMutex.withLock {
        val chosen=binding ?: return@withLock false
        if(expectedTaskId!=null && chosen.taskId!=expectedTaskId)return@withLock false
        val rect=Rect(chosen.rect)
        if(!rect.intersect(0,0,chosen.window.decorView.width,chosen.window.decorView.height))return@withLock false
        val scale=minOf(1.0,720.0/maxOf(rect.width(),rect.height()))
        val bitmap=Bitmap.createBitmap((rect.width()*scale).roundToInt().coerceAtLeast(1),(rect.height()*scale).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        withTimeoutOrNull(180) {
            suspendCancellableCoroutine { continuation ->
                try {
                    PixelCopy.request(chosen.window,rect,bitmap,{ result ->
                        val accepted=result==PixelCopy.SUCCESS && binding===chosen && continuation.isActive
                        if(accepted)images[chosen.taskId]=TaskSnapshot(bitmap,System.currentTimeMillis()) else bitmap.recycle()
                        if(continuation.isActive)continuation.resume(accepted)
                    },Handler(Looper.getMainLooper()))
                } catch(_:IllegalArgumentException) {
                    bitmap.recycle();if(continuation.isActive)continuation.resume(false)
                }
            }
        } ?: false
    }
}

internal fun Context.activity():Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> if(baseContext===this)null else baseContext.activity()
    else -> null
}
