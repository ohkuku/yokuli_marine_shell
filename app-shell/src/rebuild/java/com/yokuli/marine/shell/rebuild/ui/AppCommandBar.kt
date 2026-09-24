package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.yokuli.marine.core.design.LocalWpTextScale
import com.yokuli.marine.core.design.W10MobileMotion
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.shell.compose.LocalInternalAppInputEnabled

/** 展示命令只引用已有操作，不拥有业务状态。id 必须在当前栏内稳定唯一。 */
data class AppCommand(val id: String, val icon: String, val label: String, val onClick: () -> Unit,
    val enabled: Boolean = true, val active: Boolean = false)

/**
 * W10M 底部 CommandBar：保留固定地图视口，最多四个主命令，其余进真正的弹出菜单。
 * 紧凑栏的文字说明在“更多”中完整呈现；Popup 吃掉外部点击并拥有 Back，关闭不点穿地图。
 */
@Composable fun AppCommandBar(os: OsStore, actions: List<AppCommand>, secondaryActions: List<AppCommand> = emptyList(), modifier: Modifier = Modifier) {
    val c = LocalMetro.current
    val insets = LocalShellHorizontalInsets.current
    val inputEnabled = LocalInternalAppInputEnabled.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(inputEnabled) { if (!inputEnabled) expanded = false }
    val menuVisibility = remember { MutableTransitionState(false) }
    menuVisibility.targetState = expanded && inputEnabled
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(modifier.fillMaxWidth().background(c.panel).padding(start = insets.pageStart, end = insets.pageEnd)) {
        val commandWidth = if (density.fontScale * LocalWpTextScale.current > 1.5f) 64.dp else 56.dp
        val count = ((maxWidth - 48.dp) / commandWidth).toInt().coerceIn(1, 4).coerceAtMost(actions.size)
        val visible = actions.take(count)
        val menu = (actions + secondaryActions).distinctBy { it.id }
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            visible.forEach { action ->
                CommandIcon(action.icon, action.label, action.enabled && inputEnabled, action.active,
                    Modifier.weight(1f)) { action.onClick() }
            }
            if (menu.isNotEmpty()) CommandIcon("more", os.t("更多命令", "more commands"), inputEnabled, expanded, Modifier.width(48.dp)) { expanded = !expanded }
        }
        if (menuVisibility.currentState || menuVisibility.targetState) {
            val margin = with(density) { 8.dp.roundToPx() }
            val position = remember(margin) { object : PopupPositionProvider {
                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                    val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
                    return IntOffset(x.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
                        (anchorBounds.top - popupContentSize.height).coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)))
                }
            } }
            Popup(popupPositionProvider = position, onDismissRequest = { expanded = false }, properties = PopupProperties(focusable = true)) {
                // 弹出菜单只移动绘制层；底栏和原生海图的测量尺寸始终保持不变。
                AnimatedVisibility(menuVisibility,
                    enter = fadeIn(tween(W10MobileMotion.OverlayMillis)) + slideInVertically(tween(W10MobileMotion.OverlayMillis,easing=W10MobileMotion.EntranceEasing)) { with(density) {16.dp.roundToPx()} },
                    exit = fadeOut(tween(W10MobileMotion.ReleaseMillis)) + slideOutVertically(tween(W10MobileMotion.ReleaseMillis)) { with(density) {8.dp.roundToPx()} }) {
                Column(Modifier.width(maxWidth.coerceAtMost(420.dp)).heightIn(max = screenHeight * .7f)
                    .background(c.panel).border(1.dp, c.controlStroke).verticalScroll(rememberScrollState())
                    .semantics { paneTitle = os.t("命令", "commands") }.padding(vertical = 4.dp)) {
                    menu.forEachIndexed { index, action ->
                        if (index == actions.size && secondaryActions.isNotEmpty()) Box(Modifier.fillMaxWidth().height(1.dp).background(c.controlStroke))
                        val interactions = remember(action.id) { MutableInteractionSource() }
                        val pressed by interactions.collectIsPressedAsState()
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .background(if (pressed) c.pressed else c.panel)
                            .semantics { selected = action.active }
                            .clickable(interactionSource = interactions, indication = null, enabled = expanded && inputEnabled && action.enabled, role = Role.Button) {
                                expanded = false
                                action.onClick()
                            }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val color = if (!action.enabled) c.disabled else if (action.active) c.accentText else c.fg
                            Glyph(action.icon, Modifier.size(24.dp), color)
                            Label(action.label, 15, color, Modifier.weight(1f).padding(start = 16.dp))
                            if (action.active) Glyph("check", Modifier.size(16.dp), color)
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable private fun CommandIcon(icon: String, label: String, enabled: Boolean, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalMetro.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(modifier.height(48.dp).background(if (pressed) c.pressed else c.panel)
        .semantics { contentDescription = label; selected = active }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(icon, Modifier.size(24.dp), if (!enabled) c.disabled else if (active) c.accentText else c.fg)
    }
}
