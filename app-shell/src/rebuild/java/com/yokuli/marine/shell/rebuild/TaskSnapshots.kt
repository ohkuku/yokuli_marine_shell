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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class TaskSnapshot(val bitmap:Bitmap,val capturedAt:Long,val pageInstanceKey:String?=null)

/** One real composited image per open task, in memory only. Includes native map textures. */
class TaskSnapshotStore {
    val images=mutableStateMapOf<InternalAppTaskId,TaskSnapshot>()
    private data class Binding(val owner:Any,val taskId:InternalAppTaskId,val pageInstanceKey:String,val window:Window,val rect:Rect)
    private var binding:Binding?=null
    private val captureMutex=Mutex()
    private val callbackHandler=Handler(Looper.getMainLooper())

    fun bind(owner:Any,taskId:InternalAppTaskId,pageInstanceKey:String,window:Window,rect:Rect) {
        if(rect.width()<=0 || rect.height()<=0)return
        val next=Binding(owner,taskId,pageInstanceKey,window,Rect(rect))
        if(binding!=next)binding=next
    }
    fun unbind(owner:Any) { if(binding?.owner===owner)binding=null }
    fun retain(ids:Set<InternalAppTaskId>) { images.keys.toList().filterNot {it in ids}.forEach(images::remove) }

    suspend fun captureCurrent(expectedTaskId:InternalAppTaskId?=null,expectedPageKey:String?=null,contentVisible:()->Boolean={true}):Boolean = captureMutex.withLock {
        if(!contentVisible())return@withLock false
        val chosen=binding ?: return@withLock false
        if(expectedTaskId!=null && chosen.taskId!=expectedTaskId)return@withLock false
        if(expectedPageKey!=null && chosen.pageInstanceKey!=expectedPageKey)return@withLock false
        val rect=Rect(chosen.rect)
        if(!rect.intersect(0,0,chosen.window.decorView.width,chosen.window.decorView.height))return@withLock false
        // 任务卡显示小字和原生地图，720px 长边会把整页文字缩成不可辨识的像素。
        val scale=minOf(1.0,1920.0/maxOf(rect.width(),rect.height()))
        // 分配/清零整页高分辨率图像不占用 UI 帧；不降低任务卡文字和原生地图的清晰度。
        var allocated:Bitmap?=null
        val bitmap=try {
            withContext(Dispatchers.Default) {
                try { Bitmap.createBitmap((rect.width()*scale).roundToInt().coerceAtLeast(1),(rect.height()*scale).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888).also {allocated=it} }
                catch(_:OutOfMemoryError) { null }
            }
        } catch(cancelled:CancellationException) {allocated?.recycle();throw cancelled} ?: return@withLock false
        if(binding!==chosen || !contentVisible()) {bitmap.recycle();return@withLock false}
        var submitted=false
        try {
            withTimeoutOrNull(120) {
                suspendCancellableCoroutine { continuation ->
                    try {
                        PixelCopy.request(chosen.window,rect,bitmap,{ result ->
                            val accepted=result==PixelCopy.SUCCESS && binding===chosen && continuation.isActive && contentVisible()
                            if(accepted)images[chosen.taskId]=TaskSnapshot(bitmap,System.currentTimeMillis(),chosen.pageInstanceKey) else bitmap.recycle()
                            if(continuation.isActive)continuation.resume(accepted)
                        },callbackHandler)
                        submitted=true
                    } catch(_:IllegalArgumentException) {
                        bitmap.recycle();if(continuation.isActive)continuation.resume(false)
                    }
                }
            } ?: false
        } finally {
            // 分配返回后、PixelCopy 接管前也可能取消。接管之后只能由回调回收，不能回收正在写入的位图。
            if(!submitted && !bitmap.isRecycled)bitmap.recycle()
        }
    }
}

internal fun Context.activity():Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> if(baseContext===this)null else baseContext.activity()
    else -> null
}
