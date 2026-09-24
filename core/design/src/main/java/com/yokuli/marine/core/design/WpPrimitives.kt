package com.yokuli.marine.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WpText(
    text: String, size: Int, modifier: Modifier = Modifier, color: Color? = null,
    weight: FontWeight = WpTypeScale.weight(size), maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(text, modifier, style = TextStyle(
        color = color ?: LocalWpTheme.current.foreground,
        fontSize = (size * LocalWpTextScale.current).sp, fontWeight = weight, fontFamily = WpFontFamily,
        lineHeight = (WpTypeScale.lineHeight(size) * LocalWpTextScale.current).sp,
        letterSpacing = 0.sp, platformStyle = PlatformTextStyle(includeFontPadding = false), textMotion = TextMotion.Static,
    ), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** 兼容旧调用签名的紧凑 W10M 页标题；应用身份和上下文不使用强制大写。 */
@Composable
fun WpPageHeader(appKey: String, appName: String, contextLine: String? = null, trailing: String? = null, modifier: Modifier = Modifier) {
    val colors = LocalWpTheme.current
    Row(modifier.fillMaxWidth().padding(horizontal = W10MobileMetrics.PageInset, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            WpText(appName, WpTypeScale.PageTitle, maxLines = 2, modifier = Modifier.testTag("wp-page-title-$appKey"))
            if (!contextLine.isNullOrBlank()) WpText(contextLine, WpTypeScale.Caption, color = colors.muted,
                modifier = Modifier.padding(top = 4.dp), maxLines = 2)
        }
        if (!trailing.isNullOrBlank()) WpText(trailing, WpTypeScale.Caption, color = colors.muted, maxLines = 1)
    }
}

data class WpAppBarAction(
    val symbol: String, val label: String, val description: String = label,
    val selected: Boolean = false, val testTag: String? = null, val onClick: () -> Unit,
)

/** Mobile CommandBar：等宽命令、可见文字、单一点击区域；不沿用 WP8 的白色圆环。 */
@Composable
fun WpApplicationBar(actions: List<WpAppBarAction>, modifier: Modifier = Modifier, onOverflow: (() -> Unit)? = null) {
    val colors = LocalWpTheme.current
    Row(modifier.fillMaxWidth().heightIn(min = W10MobileMetrics.CommandBar).background(colors.chrome).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        actions.forEach { action ->
            W10CommandAction(action, Modifier.weight(1f).then(action.testTag?.let { Modifier.testTag(it) } ?: Modifier))
        }
        if (onOverflow != null) WpCircleButton("…", stringResource(R.string.action_more), onOverflow)
    }
}

@Composable
private fun W10CommandAction(action: WpAppBarAction, modifier: Modifier = Modifier) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    Column(modifier.heightIn(min = W10MobileMetrics.TouchTarget)
        .background(if (pressed) colors.pressed else if (action.selected) colors.subtle else Color.Transparent)
        .border(if (focused) 2.dp else 0.dp, if (focused) colors.accentText else Color.Transparent)
        .semantics(mergeDescendants = true) { contentDescription = action.description }
        .wpTilt(interactions, maximumDegrees = 0f)
        .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = action.onClick)
        .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        W10CommandSymbol(action.symbol, if (action.selected) colors.accentText else colors.foreground)
        WpText(action.label, WpTypeScale.Caption, maxLines = 1,
            color = if (action.selected) colors.accentText else colors.foreground,
            modifier = Modifier.padding(top = 2.dp).clearAndSetSemantics {})
    }
}

/** 保留历史命名，呈现为 MDL2 的无外圈命令键；只有选中态和按下态有填色。 */
@Composable
fun WpCircleButton(symbol: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    Box(modifier.size(W10MobileMetrics.TouchTarget)
        .background(if (pressed) colors.pressed else if (selected) colors.subtle else Color.Transparent)
        .border(if (focused) 2.dp else 0.dp, if (focused) colors.accentText else Color.Transparent)
        .semantics { contentDescription = description }
        .wpTilt(interactions, maximumDegrees = 0f)
        .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        W10CommandSymbol(symbol, if (selected) colors.accentText else colors.foreground)
    }
}

/** 常用命令是几何图形而非字体字符，避免 CJK/缺字回退把省略号、箭头绘成不同形态。 */
@Composable
private fun W10CommandSymbol(symbol: String, color: Color) {
    val known = symbol in setOf("…", "+", "−", "-", "×", "✕", "‹", "›", "←", "→", "▶", "Ⅱ", "■")
    if (!known) {
        Box(Modifier.size(W10MobileMetrics.Icon).clearAndSetSemantics {}, contentAlignment = Alignment.Center) { WpText(symbol, 20, color = color, maxLines = 1) }
        return
    }
    Canvas(Modifier.size(W10MobileMetrics.Icon).clearAndSetSemantics {}) {
        val unit = size.minDimension / 24f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), 1.5f * unit)
        when (symbol) {
            "…" -> listOf(5f, 12f, 19f).forEach { drawCircle(color, 1.4f * unit, Offset(it * unit, 12f * unit)) }
            "+" -> { line(4f, 12f, 20f, 12f); line(12f, 4f, 12f, 20f) }
            "−", "-" -> line(4f, 12f, 20f, 12f)
            "×", "✕" -> { line(5f, 5f, 19f, 19f); line(5f, 19f, 19f, 5f) }
            "‹", "←" -> { line(4f, 12f, 12f, 4f); line(4f, 12f, 12f, 20f); if (symbol == "←") line(4f, 12f, 21f, 12f) }
            "›", "→" -> { line(20f, 12f, 12f, 4f); line(20f, 12f, 12f, 20f); if (symbol == "→") line(3f, 12f, 20f, 12f) }
            "Ⅱ" -> { line(8f, 5f, 8f, 19f); line(16f, 5f, 16f, 19f) }
            "■" -> drawRect(color, Offset(6f * unit, 6f * unit), androidx.compose.ui.geometry.Size(12f * unit, 12f * unit))
            "▶" -> drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(7f * unit, 4f * unit); lineTo(20f * unit, 12f * unit); lineTo(7f * unit, 20f * unit); close() }, color)
        }
    }
}
