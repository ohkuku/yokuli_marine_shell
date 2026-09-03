package com.yokuli.marine.feature.system

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.*
import com.yokuli.marine.core.model.AppLanguage
import com.yokuli.marine.core.model.SystemSection

private data class SystemDestination(
    val section: SystemSection,
    val title: String,
    val value: String,
    val tone: SystemTone,
)

private enum class SystemTone { ACCENT, SAFE, STALE, ALARM }

@Composable
fun SystemWorkspace(state: SystemUiState, onAction: (SystemUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        WpPageHeader(
            appKey = "system",
            appName = stringResource(R.string.app_system),
            contextLine = sectionLabel(state.section),
        )
        Box(Modifier.weight(1f)) {
            if (state.section == SystemSection.DISPLAY) {
                WpDisplaySettings(state = state, onAction = onAction)
            } else {
                SystemDestinationList(state = state, onOpen = { onAction(SystemUiAction.OpenSection(it)) })
            }
        }
        WpApplicationBar(
            buildList {
                add(
                    WpAppBarAction(
                        "⌂",
                        stringResource(R.string.action_home),
                        testTag = "system-home",
                        onClick = { onAction(SystemUiAction.Home) },
                    ),
                )
                if (state.section != SystemSection.OVERVIEW) {
                    add(
                        WpAppBarAction(
                            "≡",
                            stringResource(R.string.action_system),
                            testTag = "system-overview",
                            onClick = { onAction(SystemUiAction.OpenSection(SystemSection.OVERVIEW)) },
                        ),
                    )
                }
            },
        )
    }
}

@Composable
private fun SystemDestinationList(state: SystemUiState, onOpen: (SystemSection) -> Unit) {
    val colors = LocalWpTheme.current
    val items = listOf(
        SystemDestination(
            SystemSection.CONNECTIONS,
            sectionLabel(SystemSection.CONNECTIONS),
            stringResource(if (state.nmeaConnected) R.string.status_nmea_on else R.string.status_nmea_off),
            if (state.nmeaConnected) SystemTone.SAFE else SystemTone.STALE,
        ),
        SystemDestination(
            SystemSection.DATA_SOURCES,
            sectionLabel(SystemSection.DATA_SOURCES),
            stringResource(if (state.positionSourcePhone) R.string.status_position_phone else R.string.status_position_unavailable),
            if (state.positionSourcePhone) SystemTone.ACCENT else SystemTone.STALE,
        ),
        SystemDestination(
            SystemSection.DEVICES,
            sectionLabel(SystemSection.DEVICES),
            stringResource(R.string.status_devices_available, state.availableDeviceCount),
            SystemTone.ACCENT,
        ),
        SystemDestination(
            SystemSection.DISPLAY,
            sectionLabel(SystemSection.DISPLAY),
            stringResource(R.string.status_display, themeModeLabel(state.theme.mode), accentLabel(state.theme.accent)),
            SystemTone.ACCENT,
        ),
        SystemDestination(
            SystemSection.SAFETY,
            sectionLabel(SystemSection.SAFETY),
            stringResource(if (state.safetyReady) R.string.status_ready else R.string.status_not_ready),
            if (state.safetyReady) SystemTone.SAFE else SystemTone.ALARM,
        ),
        SystemDestination(
            SystemSection.STORAGE_DIAGNOSTICS,
            sectionLabel(SystemSection.STORAGE_DIAGNOSTICS),
            stringResource(R.string.status_critical_count, state.criticalIssueCount),
            if (state.criticalIssueCount == 0) SystemTone.ACCENT else SystemTone.ALARM,
        ),
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
        SystemTone.ALARM -> colors.alarm
    }
    Row(
        Modifier.fillMaxWidth().height(64.dp)
            .testTag("system-section-${item.section.name.lowercase()}")
            .wpEntrance(item.section, order)
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
private fun WpDisplaySettings(state: SystemUiState, onAction: (SystemUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = YokuliMetrics.PageMargin),
    ) {
        WpText(stringResource(R.string.setting_background), 20, weight = FontWeight.Light, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
        WpThemeMode.entries.forEachIndexed { index, mode ->
            SelectionRow(
                label = themeModeLabel(mode),
                selected = state.theme.mode == mode,
                testTag = "theme-mode-${mode.name.lowercase()}",
                order = index,
                onClick = { onAction(SystemUiAction.ChangeTheme(state.theme.copy(mode = mode))) },
            )
        }
        WpText(stringResource(R.string.setting_accent), 20, weight = FontWeight.Light, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
        WpAccent.entries.chunked(4).forEachIndexed { rowIndex, row ->
            Row(
                Modifier.fillMaxWidth().wpEntrance("accent-$rowIndex", rowIndex + 2),
                horizontalArrangement = Arrangement.spacedBy(YokuliMetrics.TileGap),
            ) {
                row.forEach { accent ->
                    AccentChoice(
                        accent = accent,
                        selected = accent == state.theme.accent,
                        onClick = { onAction(SystemUiAction.ChangeTheme(state.theme.copy(accent = accent))) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(YokuliMetrics.TileGap))
        }
        WpText(accentLabel(state.theme.accent), 14, color = colors.muted, modifier = Modifier.padding(top = 4.dp))
        WpText(stringResource(R.string.setting_language), 20, weight = FontWeight.Light, modifier = Modifier.padding(top = 24.dp, bottom = 6.dp))
        AppLanguage.entries.forEachIndexed { index, language ->
            SelectionRow(
                label = stringResource(if (language == AppLanguage.CHINESE) R.string.language_chinese else R.string.language_english),
                selected = state.language == language,
                testTag = "language-${if (language == AppLanguage.CHINESE) "zh" else "en"}",
                order = index + 5,
                onClick = { onAction(SystemUiAction.ChangeLanguage(language)) },
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectionRow(label: String, selected: Boolean, testTag: String, order: Int, onClick: () -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().height(YokuliMetrics.MinTouch)
            .testTag(testTag)
            .semantics { this.selected = selected; role = Role.RadioButton }
            .wpEntrance(testTag, order)
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
        WpText(label, 18, modifier = Modifier.padding(start = 12.dp))
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

@Composable
private fun sectionLabel(section: SystemSection): String = stringResource(
    when (section) {
        SystemSection.OVERVIEW -> R.string.section_settings
        SystemSection.CONNECTIONS -> R.string.section_connections
        SystemSection.DATA_SOURCES -> R.string.section_data_sources
        SystemSection.DEVICES -> R.string.section_devices
        SystemSection.DISPLAY -> R.string.section_display
        SystemSection.SAFETY -> R.string.section_safety
        SystemSection.STORAGE_DIAGNOSTICS -> R.string.section_storage_diagnostics
    },
)

@Composable
private fun themeModeLabel(mode: WpThemeMode): String = stringResource(
    if (mode == WpThemeMode.DARK) R.string.theme_dark else R.string.theme_light,
)

@Composable
private fun accentLabel(accent: WpAccent): String = stringResource(
    when (accent) {
        WpAccent.COBALT -> R.string.accent_cobalt
        WpAccent.CYAN -> R.string.accent_cyan
        WpAccent.EMERALD -> R.string.accent_emerald
        WpAccent.MAGENTA -> R.string.accent_magenta
        WpAccent.VIOLET -> R.string.accent_violet
        WpAccent.CRIMSON -> R.string.accent_crimson
        WpAccent.AMBER -> R.string.accent_amber
    },
)
