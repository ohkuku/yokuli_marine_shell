package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.WpAccent
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.shell.engine.LauncherPersistedState
import java.util.Locale

private const val SELECTED_COLOR = "preferences.display.custom_accent"
private const val PALETTE_PREFIX = "preferences.palette."
private const val MAX_CUSTOM_COLORS = 24

fun customAccent(preferences: LauncherPersistedState): Long? = preferences.appPreferenceValues[SELECTED_COLOR]
    ?.takeIf { it.matches(Regex("[a-fA-F0-9]{6}")) }?.toLongOrNull(16)?.or(0xff000000L)
private fun colorHex(argb: Long): String = String.format(Locale.ROOT, "%06X", argb and 0xffffff)

/** 色板条目与当前颜色独立保存。删除收藏色不会悄悄改掉正在使用的颜色。 */
@Composable fun AccentPaletteSettings(os: OsStore) {
    val state by os.shell.persistence.state.collectAsState()
    val custom = state?.appPreferenceValues.orEmpty().keys.filter { it.startsWith(PALETTE_PREFIX) }
        .mapNotNull { it.removePrefix(PALETTE_PREFIX).takeIf { hex -> hex.matches(Regex("[a-f0-9]{6}")) }?.toLongOrNull(16)?.or(0xff000000L) }
        .distinct().sorted()
    var picker by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Long?>(null) }
    val c = LocalMetro.current
    AppSection(os.t("磁贴与强调色", "Tile & accent colour"))
    WpAccent.entries.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { accent ->
                ColorSwatch(accent.argb, os.accent == accent.argb, presetName(os, accent)) {
                    os.shell.updateSystemPreferences { it.copy(accentName = accent.name,
                        appPreferenceValues = it.appPreferenceValues - SELECTED_COLOR) }
                }
            }
        }
    }
    if (custom.isNotEmpty()) {
        Label(os.t("我的颜色", "My colours"), 14, c.muted)
        custom.chunked(4).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { color -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ColorSwatch(color, os.accent == color, "#${colorHex(color)}") {
                    os.shell.updateSystemPreferences { it.copy(appPreferenceValues = it.appPreferenceValues + (SELECTED_COLOR to colorHex(color))) }
                }
                MetroButton("×", { removing = color }, Modifier.width(56.dp))
            } }
        } }
    }
    val preset = WpAccent.entries.firstOrNull { it.argb == os.accent }
    Label(os.t("当前：", "Current: ") + (preset?.let { presetName(os, it) } ?: "#${colorHex(os.accent)}"), 13, c.muted)
    MetroButton(os.t("添加颜色", "Add colour"), { picker = true }, enabled = custom.size < MAX_CUSTOM_COLORS)
    if (custom.size >= MAX_CUSTOM_COLORS) Label(os.t("已保存 24 种颜色，移除一种后可继续添加。", "24 colours saved. Remove one to add another."), 13, c.muted)
    removing?.let { color -> AppDialog(onDismissRequest = { removing = null }) { AppDialogSurface {
        AppDialogTitle(os.t("从色板移除？", "Remove from palette?"))
        Label(os.t("只移除收藏的 #${colorHex(color)}，正在使用的颜色不会改变。", "Remove saved #${colorHex(color)}. The colour currently in use stays unchanged."), 14)
        MetroButton(os.t("移除", "Remove"), {
            os.shell.updateSystemPreferences { it.copy(appPreferenceValues = it.appPreferenceValues - (PALETTE_PREFIX + colorHex(color).lowercase(Locale.ROOT))) }
            removing = null
        }, primary = true)
        MetroButton(os.t("取消", "Cancel"), { removing = null })
    } } }
    if (picker) ColorPickerDialog(os, os.accent, { picker = false }) { color ->
        os.shell.updateSystemPreferences { preferences ->
            val key = PALETTE_PREFIX + colorHex(color).lowercase(Locale.ROOT)
            val count = preferences.appPreferenceValues.keys.count { it.startsWith(PALETTE_PREFIX) }
            val saved = if (WpAccent.entries.any { it.argb == color } || (count >= MAX_CUSTOM_COLORS && key !in preferences.appPreferenceValues))
                preferences.appPreferenceValues else preferences.appPreferenceValues + (key to "b:1")
            preferences.copy(appPreferenceValues = saved + (SELECTED_COLOR to colorHex(color)))
        }
        picker = false
    }
}

@Composable private fun ColorSwatch(argb: Long, selected: Boolean, name: String, onClick: () -> Unit) {
    val color = Color(argb)
    val ink = if (color.luminance() > .45f) Color.Black else Color.White
    Box(Modifier.size(56.dp).background(color).border(if(selected) 2.dp else 1.dp, if(selected) LocalMetro.current.fg else LocalMetro.current.controlStroke)
        .semantics { contentDescription = name }.selectable(selected, role = Role.RadioButton, onClick = onClick), contentAlignment = Alignment.Center) {
        if(selected) Glyph("check", Modifier.size(24.dp), ink)
    }
}

private fun presetName(os: OsStore, accent: WpAccent) = os.t(when(accent) {
    WpAccent.COBALT -> "钴蓝"; WpAccent.CYAN -> "青色"; WpAccent.EMERALD -> "翡翠绿"; WpAccent.MAGENTA -> "品红"
    WpAccent.VIOLET -> "紫色"; WpAccent.CRIMSON -> "深红"; WpAccent.AMBER -> "琥珀"
}, accent.displayName)

@Composable private fun ColorPickerDialog(os: OsStore, initial: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val initialHsv = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)).toLong() and 0xffffffffL
    var hex by remember { mutableStateOf(colorHex(initial)) }
    fun updateHex() { hex = colorHex(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)).toLong()) }
    AppDialog(onDismissRequest = onDismiss) { AppDialogSurface {
        AppDialogTitle(os.t("选择颜色", "Choose colour"))
        Canvas(Modifier.fillMaxWidth().height(172.dp).pointerInput(Unit) {
            detectTapGestures { point -> saturation = (point.x / size.width).coerceIn(0f,1f); value = 1f-(point.y/size.height).coerceIn(0f,1f); updateHex() }
        }.pointerInput(Unit) {
            detectDragGestures { change, _ -> change.consume(); saturation=(change.position.x/size.width).coerceIn(0f,1f); value=1f-(change.position.y/size.height).coerceIn(0f,1f); updateHex() }
        }) {
            val tint = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue,1f,1f)))
            drawRect(Brush.horizontalGradient(listOf(Color.White,tint)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black)))
            val center = Offset(saturation*size.width,(1-value)*size.height)
            drawCircle(Color.Black,7.dp.toPx(),center,style=Stroke(3.dp.toPx()))
            drawCircle(Color.White,7.dp.toPx(),center,style=Stroke(1.5.dp.toPx()))
        }
        Canvas(Modifier.fillMaxWidth().height(40.dp).pointerInput(Unit) {
            detectTapGestures { point -> hue = 359.99f*(point.x/size.width).coerceIn(0f,1f); updateHex() }
        }.pointerInput(Unit) {
            detectDragGestures { change, _ -> change.consume(); hue=359.99f*(change.position.x/size.width).coerceIn(0f,1f); updateHex() }
        }) {
            drawRect(Brush.horizontalGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)))
            val x = hue/360f*size.width
            drawLine(Color.White,Offset(x,0f),Offset(x,size.height),4.dp.toPx())
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(Color(argb)).border(1.dp,LocalMetro.current.controlStroke))
            Box(Modifier.weight(1f)) { Field(os.t("颜色代码", "Hex colour"), hex, { entered ->
                hex=entered.removePrefix("#").take(6).uppercase(Locale.ROOT)
                if(hex.matches(Regex("[A-F0-9]{6}"))) {
                    val hsv=FloatArray(3);android.graphics.Color.colorToHSV((hex.toLong(16) or 0xff000000L).toInt(),hsv)
                    hue=hsv[0];saturation=hsv[1];value=hsv[2]
                }
            }) }
        }
        MetroButton(os.t("保存并使用", "Save & use"), { onSave(argb) }, primary=true,enabled=hex.matches(Regex("[A-F0-9]{6}")))
        MetroButton(os.t("取消", "Cancel"), onDismiss)
    } }
}
