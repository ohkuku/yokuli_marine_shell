package com.yokuli.marine.feature.system

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.*
import com.yokuli.marine.core.model.SystemSection

private data class SystemDestination(
    val section: SystemSection,
    val title: String,
    val value: String,
    val tone: SystemTone,
)

private enum class SystemTone { ACCENT, SAFE, STALE }

@Composable
fun SystemWorkspace(
    initialSection: SystemSection,
    theme: WpThemeSpec,
    onThemeChange: (WpThemeSpec) -> Unit,
    onHome: () -> Unit,
) {
    val colors = LocalWpTheme.current
    var section by remember(initialSection) { mutableStateOf(initialSection) }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        WpPageHeader(appName = "system", contextLine = section.label())
        Box(Modifier.weight(1f)) {
            if (section == SystemSection.DISPLAY) {
                WpDisplaySettings(theme = theme, onThemeChange = onThemeChange)
            } else {
                SystemDestinationList(onOpen = { section = it })
            }
        }
        WpApplicationBar(
            buildList {
                add(WpAppBarAction("⌂", "home", testTag = "system-home", onClick = onHome))
                if (section != SystemSection.OVERVIEW) {
                    add(WpAppBarAction("≡", "system", testTag = "system-overview", onClick = { section = SystemSection.OVERVIEW }))
                }
            },
        )
    }
}

@Composable
private fun SystemDestinationList(onOpen: (SystemSection) -> Unit) {
    val colors = LocalWpTheme.current
    val items = listOf(
        SystemDestination(SystemSection.CONNECTIONS, "connections", "NMEA OFF", SystemTone.STALE),
        SystemDestination(SystemSection.DATA_SOURCES, "data sources", "POSITION · PHONE", SystemTone.ACCENT),
        SystemDestination(SystemSection.DEVICES, "devices", "2 AVAILABLE", SystemTone.ACCENT),
        SystemDestination(SystemSection.DISPLAY, "display", "${colors.spec.mode.name} · ${colors.spec.accent.displayName.uppercase()}", SystemTone.ACCENT),
        SystemDestination(SystemSection.SAFETY, "safety", "READY", SystemTone.SAFE),
        SystemDestination(SystemSection.STORAGE_DIAGNOSTICS, "storage & diagnostics", "0 CRITICAL", SystemTone.ACCENT),
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = YokuliMetrics.PageMargin),
    ) {
        items.forEachIndexed { index, item ->
            SystemDestinationRow(item = item, order = index + 1, onClick = { onOpen(item.section) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SystemDestinationRow(item: SystemDestination, order: Int, onClick: () -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    val tone = when (item.tone) {
        SystemTone.ACCENT -> colors.accent
        SystemTone.SAFE -> colors.safe
        SystemTone.STALE -> colors.stale
    }
    Row(
        Modifier.fillMaxWidth().height(64.dp).wpEntrance(item.section, order)
            .wpTilt(interactions)
            .combinedClickable(interactionSource = interactions, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(11.dp).background(tone))
        Column(Modifier.padding(start = 13.dp)) {
            WpText(item.title, 20, weight = FontWeight.Light)
            WpText(item.value, 10, color = colors.muted)
        }
    }
}

@Composable
private fun WpDisplaySettings(theme: WpThemeSpec, onThemeChange: (WpThemeSpec) -> Unit) {
    val colors = LocalWpTheme.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = YokuliMetrics.PageMargin),
    ) {
        WpText("background", 20, weight = FontWeight.Light, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
        WpThemeMode.entries.forEachIndexed { index, mode ->
            ThemeModeRow(
                mode = mode,
                selected = theme.mode == mode,
                order = index,
                onClick = { onThemeChange(theme.copy(mode = mode)) },
            )
        }
        WpText("accent color", 20, weight = FontWeight.Light, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
        WpAccent.entries.chunked(4).forEachIndexed { rowIndex, row ->
            Row(
                Modifier.fillMaxWidth().wpEntrance("accent-$rowIndex", rowIndex + 2),
                horizontalArrangement = Arrangement.spacedBy(YokuliMetrics.TileGap),
            ) {
                row.forEach { accent ->
                    AccentChoice(
                        accent = accent,
                        selected = accent == theme.accent,
                        onClick = { onThemeChange(theme.copy(accent = accent)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(YokuliMetrics.TileGap))
        }
        WpText(theme.accent.displayName, 14, color = colors.muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThemeModeRow(mode: WpThemeMode, selected: Boolean, order: Int, onClick: () -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().height(YokuliMetrics.MinTouch)
            .testTag("theme-mode-${mode.name.lowercase()}")
            .semantics { this.selected = selected; role = Role.RadioButton }
            .wpEntrance(mode, order)
            .wpTilt(interactions)
            .combinedClickable(interactionSource = interactions, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).border(2.dp, if (selected) colors.accent else colors.muted),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).background(colors.accent))
        }
        WpText(mode.name.lowercase(), 18, modifier = Modifier.padding(start = 12.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccentChoice(
    accent: WpAccent,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = WpThemePolicy.resolve(WpThemeSpec(accent = accent))
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier.height(58.dp)
            .testTag("theme-accent-${accent.displayName}")
            .semantics { this.selected = selected; role = Role.RadioButton }
            .wpTilt(interactions)
            .combinedClickable(interactionSource = interactions, indication = null, onClick = onClick)
            .background(scheme.accent)
            .then(if (selected) Modifier.border(3.dp, scheme.onAccent) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) WpText("✓", 22, color = scheme.onAccent)
    }
}

private fun SystemSection.label(): String = when (this) {
    SystemSection.OVERVIEW -> "SETTINGS"
    SystemSection.CONNECTIONS -> "CONNECTIONS"
    SystemSection.DATA_SOURCES -> "DATA SOURCES"
    SystemSection.DEVICES -> "DEVICES"
    SystemSection.DISPLAY -> "DISPLAY"
    SystemSection.SAFETY -> "SAFETY"
    SystemSection.STORAGE_DIAGNOSTICS -> "STORAGE & DIAGNOSTICS"
}
