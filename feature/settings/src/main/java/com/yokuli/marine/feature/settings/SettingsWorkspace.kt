package com.yokuli.marine.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpAccent
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.WpThemeMode
import com.yokuli.marine.core.design.WpThemePolicy
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliMetrics
import com.yokuli.marine.core.design.wpEntrance
import com.yokuli.marine.core.design.wpTilt
import com.yokuli.marine.core.model.AppLanguage

private data class SettingsDestination(
    val section: SettingsSection,
    val title: String,
    val value: String,
)

@Composable
fun SettingsWorkspace(state: SettingsUiState, onAction: (SettingsUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    Column(Modifier.fillMaxSize().background(colors.background).testTag("settings-workspace")) {
        WpPageHeader(
            appKey = "settings",
            appName = stringResource(R.string.app_settings),
            contextLine = sectionLabel(state.section),
        )
        when (state.section) {
            SettingsSection.OVERVIEW -> SettingsOverview(state) { onAction(SettingsUiAction.OpenSection(it)) }
            SettingsSection.APPEARANCE -> AppearanceSettings(state, onAction)
            SettingsSection.START_SCREEN -> StartScreenSettings(state, onAction)
            SettingsSection.MAP -> MapSettings(state)
            SettingsSection.LANGUAGE -> LanguageSettings(state, onAction)
            SettingsSection.ABOUT -> AboutSettings(state)
        }
    }
}

@Composable
private fun SettingsOverview(state: SettingsUiState, onOpen: (SettingsSection) -> Unit) {
    val destinations = listOf(
        SettingsDestination(SettingsSection.APPEARANCE, sectionLabel(SettingsSection.APPEARANCE), themeSummary(state)),
        SettingsDestination(
            SettingsSection.START_SCREEN,
            sectionLabel(SettingsSection.START_SCREEN),
            stringResource(R.string.start_summary, state.pinnedTileCount),
        ),
        SettingsDestination(
            SettingsSection.MAP,
            sectionLabel(SettingsSection.MAP),
            stringResource(if (state.mapConfigured) R.string.map_configured else R.string.map_not_configured),
        ),
        SettingsDestination(
            SettingsSection.LANGUAGE,
            sectionLabel(SettingsSection.LANGUAGE),
            languageLabel(state.language),
        ),
        SettingsDestination(
            SettingsSection.ABOUT,
            sectionLabel(SettingsSection.ABOUT),
            stringResource(R.string.about_summary, state.versionName),
        ),
    )
    SettingsBody {
        destinations.forEachIndexed { index, destination ->
            SettingsDestinationRow(destination, index) { onOpen(destination.section) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsDestinationRow(item: SettingsDestination, order: Int, onClick: () -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().height(66.dp)
            .testTag("settings-section-${item.section.name.lowercase()}")
            .wpEntrance(item.section, order)
            .wpTilt(interactions)
            .combinedClickable(interactionSource = interactions, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(11.dp).background(colors.accent))
        Column(Modifier.padding(start = 14.dp)) {
            WpText(item.title, 21, weight = FontWeight.Light)
            WpText(item.value, 11, color = colors.muted)
        }
    }
}

@Composable
private fun AppearanceSettings(state: SettingsUiState, onAction: (SettingsUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    SettingsBody {
        SettingsLabel(stringResource(R.string.setting_background))
        WpThemeMode.entries.forEachIndexed { index, mode ->
            SelectionRow(
                label = themeModeLabel(mode),
                selected = state.theme.mode == mode,
                testTag = "theme-mode-${mode.name.lowercase()}",
                order = index,
            ) { onAction(SettingsUiAction.ChangeTheme(state.theme.copy(mode = mode))) }
        }
        SettingsLabel(stringResource(R.string.setting_accent), top = 24)
        WpAccent.entries.chunked(4).forEachIndexed { rowIndex, row ->
            Row(
                Modifier.fillMaxWidth().wpEntrance("accent-$rowIndex", rowIndex + 2),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { accent ->
                    AccentChoice(
                        accent = accent,
                        selected = accent == state.theme.accent,
                        onClick = { onAction(SettingsUiAction.ChangeTheme(state.theme.copy(accent = accent))) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
        }
        WpText(accentLabel(state.theme.accent), 14, color = colors.muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun StartScreenSettings(state: SettingsUiState, onAction: (SettingsUiAction) -> Unit) {
    SettingsBody {
        WpText(stringResource(R.string.start_document, state.startDocumentVersion, state.pinnedTileCount), 18)
        WpText(stringResource(R.string.start_reset_explanation), 12, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 8.dp))
        SettingsCommand(stringResource(R.string.start_reset), "settings-reset-start") {
            onAction(SettingsUiAction.ResetStartScreen)
        }
        SettingsCommand(stringResource(R.string.open_android_settings), "settings-open-android-settings") {
            onAction(SettingsUiAction.OpenAndroidSettings)
        }
        if (state.debugShellLabAvailable) {
            SettingsLabel(stringResource(R.string.debug_tools), top = 28)
            WpText(stringResource(R.string.shell_lab_explanation), 12, color = LocalWpTheme.current.muted)
            SettingsCommand(stringResource(R.string.open_shell_lab), "settings-open-shell-lab") {
                onAction(SettingsUiAction.OpenShellLab)
            }
        }
    }
}

@Composable
private fun MapSettings(state: SettingsUiState) {
    SettingsBody {
        SettingsLabel(stringResource(R.string.map_provider))
        WpText(stringResource(R.string.map_google_normal), 21, weight = FontWeight.Light)
        WpText(
            stringResource(if (state.mapConfigured) R.string.map_configured_detail else R.string.map_not_configured_detail),
            12,
            color = LocalWpTheme.current.muted,
            modifier = Modifier.padding(top = 6.dp),
        )
        WpText(stringResource(R.string.map_attribution), 11, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 18.dp))
    }
}

@Composable
private fun LanguageSettings(state: SettingsUiState, onAction: (SettingsUiAction) -> Unit) {
    SettingsBody {
        SettingsLabel(stringResource(R.string.setting_language))
        AppLanguage.entries.forEachIndexed { index, language ->
            SelectionRow(
                label = languageLabel(language),
                selected = language == state.language,
                testTag = "language-${language.languageTag}",
                order = index,
            ) { onAction(SettingsUiAction.ChangeLanguage(language)) }
        }
        WpText(stringResource(R.string.language_restart_note), 11, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun AboutSettings(state: SettingsUiState) {
    SettingsBody {
        SettingsLabel(stringResource(R.string.about_product))
        WpText(stringResource(R.string.product_name), 27, weight = FontWeight.Light)
        AboutRow(stringResource(R.string.about_version), state.versionName)
        AboutRow(stringResource(R.string.about_variant), state.buildVariant)
        AboutRow(stringResource(R.string.about_revision), state.gitSha.take(12))
        AboutRow(stringResource(R.string.about_map), stringResource(if (state.mapConfigured) R.string.map_configured else R.string.map_not_configured))
        AboutRow(stringResource(R.string.about_shell_document), "v${state.startDocumentVersion}")
        WpText(stringResource(R.string.about_scope), 11, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 22.dp))
    }
}

@Composable
private fun SettingsBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = YokuliMetrics.PageMargin, vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun SettingsLabel(text: String, top: Int = 4) {
    WpText(text, 20, weight = FontWeight.Light, modifier = Modifier.padding(top = top.dp, bottom = 8.dp))
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
        Box(Modifier.size(22.dp).border(2.dp, if (selected) colors.accent else colors.muted), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).background(colors.accent))
        }
        WpText(label, 18, modifier = Modifier.padding(start = 12.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccentChoice(accent: WpAccent, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        if (selected) Box(Modifier.size(13.dp).background(scheme.onAccent))
    }
}

@Composable
private fun SettingsCommand(label: String, tag: String, onClick: () -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    Box(
        Modifier.padding(top = 16.dp).height(YokuliMetrics.MinTouch).fillMaxWidth()
            .testTag(tag).wpTilt(interactions)
            .combinedClickable(interactionSource = interactions, indication = null, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) { WpText(label, 18, color = colors.accent) }
}

@Composable
private fun AboutRow(label: String, value: String) {
    val colors = LocalWpTheme.current
    Row(Modifier.fillMaxWidth().padding(top = 15.dp), verticalAlignment = Alignment.Bottom) {
        WpText(label, 13, color = colors.muted)
        Spacer(Modifier.weight(1f))
        WpText(value, 14)
    }
}

@Composable
private fun sectionLabel(section: SettingsSection): String = stringResource(
    when (section) {
        SettingsSection.OVERVIEW -> R.string.section_overview
        SettingsSection.APPEARANCE -> R.string.section_appearance
        SettingsSection.START_SCREEN -> R.string.section_start_screen
        SettingsSection.MAP -> R.string.section_map
        SettingsSection.LANGUAGE -> R.string.section_language
        SettingsSection.ABOUT -> R.string.section_about
    },
)

@Composable
private fun themeSummary(state: SettingsUiState) = stringResource(
    R.string.appearance_summary,
    themeModeLabel(state.theme.mode),
    accentLabel(state.theme.accent),
)

@Composable
private fun themeModeLabel(mode: WpThemeMode): String =
    stringResource(if (mode == WpThemeMode.DARK) R.string.theme_dark else R.string.theme_light)

@Composable
private fun languageLabel(language: AppLanguage): String =
    stringResource(if (language == AppLanguage.CHINESE) R.string.language_chinese else R.string.language_english)

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
