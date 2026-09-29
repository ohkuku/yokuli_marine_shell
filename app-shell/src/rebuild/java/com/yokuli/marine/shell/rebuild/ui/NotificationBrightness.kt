package com.yokuli.marine.shell.rebuild.ui

import android.provider.Settings
import android.view.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.SystemPreferenceStatus

private const val BRIGHTNESS_KEY = "preferences.display.brightness"
/** 仅拖动时的前台预览；保存真值仍由 Shell 偏好仓库拥有。 */
private object BrightnessPreview { var value by mutableStateOf<Float?>(null) }
private fun decodeBrightness(raw: String?): Float? = raw?.removePrefix("f:")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(.05f, 1f)

/** 普通 APK 只设置自己的 Window，退出前台交还 Android；无需修改系统亮度权限。 */
@Composable fun ApplicationBrightness(os: OsStore, window: Window) {
    val saved by os.shell.persistence.state.collectAsState()
    val requested = BrightnessPreview.value ?: decodeBrightness(saved?.appPreferenceValues?.get(BRIGHTNESS_KEY)) ?: -1f
    SideEffect { if (window.attributes.screenBrightness != requested) window.attributes = window.attributes.apply { screenBrightness = requested } }
    DisposableEffect(window) { onDispose { window.attributes = window.attributes.apply { screenBrightness = -1f } } }
}

@Composable internal fun NotificationBrightnessControl(os: OsStore) {
    val saved by os.shell.persistence.state.collectAsState()
    val writes by os.shell.preferenceCommands.collectAsState()
    val result = writes.lastOrNull { it.key == "notification.brightness" }
    val value = decodeBrightness(saved?.appPreferenceValues?.get(BRIGHTNESS_KEY))
    val systemValue = remember { (runCatching { Settings.System.getInt(os.context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) }.getOrDefault(128) / 255f).coerceIn(.05f, 1f) }
    var draft by remember(value) { mutableFloatStateOf(value ?: systemValue) }
    var width by remember { mutableIntStateOf(1) }
    val enabled = result?.status != SystemPreferenceStatus.PENDING
    val c = LocalMetro.current
    fun change(next: Float) { draft = next.coerceIn(.05f, 1f); BrightnessPreview.value = draft }
    fun save() { os.shell.requestSystemPreferences("notification.brightness") { it.copy(appPreferenceValues = it.appPreferenceValues + (BRIGHTNESS_KEY to "f:$draft")) } }
    LaunchedEffect(result?.status, value) { if (result?.status != SystemPreferenceStatus.PENDING) BrightnessPreview.value = null }
    DisposableEffect(Unit) { onDispose { BrightnessPreview.value = null } }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(os.t("屏幕亮度", "brightness"), 15, modifier = Modifier.weight(1f))
            Label(if (value == null) os.t("跟随系统", "system") else "${(draft * 100).toInt()}%", 13, c.muted)
        }
        val progressLabel = os.t("Yokuli 屏幕亮度", "Yokuli screen brightness")
        Canvas(Modifier.fillMaxWidth().height(48.dp).onSizeChanged { width = it.width.coerceAtLeast(1) }
            .semantics {
                contentDescription = progressLabel
                progressBarRangeInfo = ProgressBarRangeInfo(draft, .05f..1f)
                if (!enabled) disabled()
                setProgress { requested -> if(enabled) { change(requested); save(); true } else false }
            }
            .pointerInput(enabled, width) { if(enabled) detectTapGestures { change(it.x / width); save() } }
            .pointerInput(enabled, width) { if(enabled) detectHorizontalDragGestures(
                onDragStart = { change(it.x / width) }, onDragEnd = { save() },
                onDragCancel = { draft = value ?: systemValue; BrightnessPreview.value = null },
            ) { point, delta -> point.consume(); change(draft + delta / width) } }) {
            val x = 4.dp.toPx() + (size.width - 8.dp.toPx()) * draft
            drawLine(c.controlStroke, Offset(0f, center.y), Offset(size.width, center.y), 2.dp.toPx())
            drawLine(c.accentText, Offset(0f, center.y), Offset(x, center.y), 2.dp.toPx())
            drawRect(if(enabled)c.accentText else c.disabled, Offset(x - 4.dp.toPx(), center.y - 10.dp.toPx()), Size(8.dp.toPx(), 20.dp.toPx()))
        }
        if(value != null) Label(os.t("跟随手机亮度", "use phone brightness"), 13, c.accentText,
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(enabled = enabled) {
                BrightnessPreview.value = null
                os.shell.requestSystemPreferences("notification.brightness") { it.copy(appPreferenceValues = it.appPreferenceValues - BRIGHTNESS_KEY) }
            }.padding(vertical = 12.dp))
        if(result?.status == SystemPreferenceStatus.FAILED) Label(os.t("亮度未保存，请重试", "Brightness was not saved. Try again."), 13, c.accentText)
    }
}
