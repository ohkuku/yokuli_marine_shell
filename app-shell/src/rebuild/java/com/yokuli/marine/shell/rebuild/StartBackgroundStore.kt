package com.yokuli.marine.shell.rebuild

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.AtomicFile
import com.yokuli.marine.core.design.StartBackdropMode
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val START_IMAGE = "preferences.start.image"
internal const val START_MODE = "preferences.start.mode"
internal const val START_OPACITY = "preferences.start.opacity"
internal const val START_CROP_SCALE = "preferences.start.crop.scale"
internal const val START_FOCUS_X = "preferences.start.crop.x"
internal const val START_FOCUS_Y = "preferences.start.crop.y"
enum class StartBackgroundFailure { PHOTO_ACCESS, PHOTO_MISSING, PHOTO_FORMAT, STORAGE_FULL, STORAGE_WRITE, PREFERENCE_WRITE, PHOTO_CHANGED, PICKER_UNAVAILABLE }
data class StartBackgroundWrite(val busy: Boolean = false, val failed: Boolean = false, val reason:StartBackgroundFailure?=null)
private class StartBackgroundOperationFailure(val reason:StartBackgroundFailure,cause:Throwable):Exception(cause)
internal fun startBackgroundFailure(error:Throwable,fallback:StartBackgroundFailure):StartBackgroundFailure {
    val causes=generateSequence(error) {it.cause}.take(8).toList()
    return when {
        causes.any {it is StartBackgroundOperationFailure}->(causes.first {it is StartBackgroundOperationFailure} as StartBackgroundOperationFailure).reason
        causes.any {it is android.system.ErrnoException&&it.errno==android.system.OsConstants.ENOSPC}->StartBackgroundFailure.STORAGE_FULL
        causes.any {it is SecurityException}->StartBackgroundFailure.PHOTO_ACCESS
        causes.any {it is java.io.FileNotFoundException}->StartBackgroundFailure.PHOTO_MISSING
        causes.any {it is ImageDecoder.DecodeException}->StartBackgroundFailure.PHOTO_FORMAT
        causes.any {it.message=="BACKGROUND_CHANGED"}->StartBackgroundFailure.PHOTO_CHANGED
        else->fallback
    }
}

/** 背景是系统偏好：照片复制进私有存储，再提交同一 Launcher DataStore，离开设置不会取消写入。 */
class StartBackgroundStore(private val os: OsStore) {
    private val mutex = Mutex()
    private val mutableWrite = MutableStateFlow(StartBackgroundWrite())
    val write = mutableWrite.asStateFlow()
    private val directory get() = File(os.context.filesDir, "start-backgrounds")
    internal fun imageFile(name: String?): File? = name?.takeIf { it.matches(Regex("[a-f0-9-]{36}\\.jpg")) }?.let { File(directory, it) }

    fun choose(uri: Uri,preferredMode:StartBackdropMode?=null) = submit(StartBackgroundFailure.STORAGE_WRITE) {
        val created = withContext(Dispatchers.IO) {
            check(directory.isDirectory || directory.mkdirs())
            val destination = File(directory, "${UUID.randomUUID()}.jpg")
            val bitmap = decode(ImageDecoder.createSource(os.context.contentResolver, uri))
            try {
                val file = AtomicFile(destination)
                val stream = file.startWrite()
                try {
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream))
                    val writtenBytes=stream.channel.position()
                    check(writtenBytes>0L) {"BACKGROUND_IMAGE_EMPTY"}
                    // AtomicFile 的内部同步/改名可能只记日志；先显式同步，再核对实际提交的字节数。
                    stream.fd.sync()
                    file.finishWrite(stream)
                    check(destination.isFile&&destination.length()==writtenBytes) {"BACKGROUND_IMAGE_NOT_COMMITTED"}
                } catch (failure: Throwable) { file.failWrite(stream);file.delete();throw failure }
                destination
            } finally { bitmap.recycle() }
        }
        try {
            os.shell.persistence.updatePreferences { current -> current.copy(appPreferenceValues = current.appPreferenceValues + mapOf(
                START_IMAGE to created.name,
                START_CROP_SCALE to "1.0", START_FOCUS_X to "0.5", START_FOCUS_Y to "0.5",
                START_MODE to (preferredMode?.takeUnless {it==StartBackdropMode.NONE}?.name ?: current.appPreferenceValues[START_MODE]?.takeIf {it in setOf(StartBackdropMode.FULL.name,StartBackdropMode.TILES.name)} ?: StartBackdropMode.FULL.name),
            )) }
        } catch (cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
        catch (failure: Exception) {
            withContext(Dispatchers.IO) {created.delete()}
            throw StartBackgroundOperationFailure(startBackgroundFailure(failure,StartBackgroundFailure.PREFERENCE_WRITE),failure)
        }
        // 提交后只清理由本功能拥有的旧图片；绝不删除仍被偏好指向的图片。
        withContext(Dispatchers.IO) { runCatching {directory.listFiles()?.filter { it != created && imageFile(it.name) != null }?.forEach { it.delete() }} }
    }

    fun mode(value: StartBackdropMode) = option(START_MODE, value.name)
    fun opacity(value: Float) = option(START_OPACITY, value.coerceIn(0f, 1f).toString())
    /** 一次确认写入三个取景字段；取消预览不调用这个入口。 */
    fun crop(expectedImage:String,scale:Float,x:Float,y:Float) = submit {
        require(scale.isFinite()&&x.isFinite()&&y.isFinite())
        os.shell.persistence.updatePreferences {
            check(it.appPreferenceValues[START_IMAGE]==expectedImage) {"BACKGROUND_CHANGED"}
            it.copy(appPreferenceValues=it.appPreferenceValues+mapOf(
                START_CROP_SCALE to scale.coerceIn(1f,3f).toString(), START_FOCUS_X to x.coerceIn(0f,1f).toString(), START_FOCUS_Y to y.coerceIn(0f,1f).toString()))
        }
    }
    fun remove() = submit {
        os.shell.persistence.updatePreferences { it.copy(appPreferenceValues = it.appPreferenceValues - setOf(START_IMAGE,START_CROP_SCALE,START_FOCUS_X,START_FOCUS_Y) + (START_MODE to StartBackdropMode.NONE.name)) }
        withContext(Dispatchers.IO) { runCatching {directory.listFiles()?.filter { imageFile(it.name) != null }?.forEach { it.delete() }} }
    }
    private fun option(key: String, value: String) = submit {
        os.shell.persistence.updatePreferences { it.copy(appPreferenceValues = it.appPreferenceValues + (key to value)) }
    }
    fun pickerFailed(error:Exception) {if(!mutableWrite.value.busy)mutableWrite.value=StartBackgroundWrite(failed=true,reason=startBackgroundFailure(error,StartBackgroundFailure.PICKER_UNAVAILABLE))}
    fun clearFailure() {if(!mutableWrite.value.busy)mutableWrite.value=StartBackgroundWrite()}
    private fun submit(fallback:StartBackgroundFailure=StartBackgroundFailure.PREFERENCE_WRITE,action: suspend () -> Unit) = os.scope.launch {
            mutex.withLock {
                mutableWrite.value = StartBackgroundWrite(busy = true)
                try { action(); mutableWrite.value = StartBackgroundWrite() }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) { mutableWrite.value = StartBackgroundWrite(failed = true,reason=startBackgroundFailure(error,fallback)) }
            }
    }
    internal suspend fun load(name: String): Bitmap? = withContext(Dispatchers.IO) {
        imageFile(name)?.takeIf { it.isFile }?.let { decode(ImageDecoder.createSource(it)) }
    }
    private fun decode(source: ImageDecoder.Source): Bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val largest = maxOf(info.size.width, info.size.height)
        require(largest > 0)
        if (largest > 1920) decoder.setTargetSize((info.size.width * 1920L / largest).toInt().coerceAtLeast(1), (info.size.height * 1920L / largest).toInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
    }
}
