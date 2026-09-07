package com.yokuli.marine.feature.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
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
import com.yokuli.marine.core.model.AppLanguage
import com.yokuli.shell.contract.MeasurementUnitSystem
import com.yokuli.shell.contract.MotionPreference
import com.yokuli.shell.contract.AppPreferenceDefinition
import com.yokuli.shell.contract.AppPreferenceLabel
import com.yokuli.shell.contract.AppPreferenceValue

@Composable
fun PreferencesWorkspace(state: PreferencesUiState, onAction: (PreferencesUiAction) -> Unit) {
    // Settings sections are direct destinations, not a second nested navigation stack. The
    // Shell owns Back and returns straight to Start from every Settings section.
    Column(Modifier.fillMaxSize().background(LocalWpTheme.current.background).testTag("preferences-workspace")) {
        WpPageHeader("preferences", stringResource(R.string.preferences_title), sectionLabel(state.section))
        when (state.section) {
            PreferencesSection.OVERVIEW -> Overview(state, onAction)
            PreferencesSection.APPEARANCE -> Appearance(state, onAction)
            PreferencesSection.LANGUAGE -> Language(state, onAction)
            PreferencesSection.UNITS -> Units(state, onAction)
            PreferencesSection.MOTION -> Motion(state, onAction)
            PreferencesSection.START -> Start(state, onAction)
            PreferencesSection.APP_TILES -> AppTiles(state, onAction)
            PreferencesSection.ABOUT -> About(state)
        }
    }
}

@Composable
private fun Overview(state: PreferencesUiState, onAction: (PreferencesUiAction) -> Unit) = Body("preferences-overview") {
    val rows = listOf(
        PreferencesSection.APPEARANCE to stringResource(R.string.preferences_summary_appearance, themeModeLabel(state.theme.mode), accentLabel(state.theme.accent)),
        PreferencesSection.LANGUAGE to languageLabel(state.language),
        PreferencesSection.UNITS to unitsLabel(state.measurementUnits),
        PreferencesSection.MOTION to motionLabel(state.motionPreference),
        PreferencesSection.START to stringResource(R.string.preferences_summary_start, state.pinnedTileCount),
        PreferencesSection.APP_TILES to stringResource(R.string.preferences_summary_app_tiles, state.appTiles.size),
        PreferencesSection.ABOUT to stringResource(R.string.preferences_summary_about, state.versionName),
    )
    rows.forEach { (section, summary) ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable { onAction(PreferencesUiAction.OpenSection(section)) }
                .padding(vertical = 7.dp).testTag("preferences-section-${section.name.lowercase()}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                WpText(sectionLabel(section), 21, weight = FontWeight.Light)
                WpText(summary, 11, color = LocalWpTheme.current.muted)
            }
            WpText("›", 24, color = LocalWpTheme.current.muted)
        }
    }
}

@Composable
private fun Appearance(state: PreferencesUiState, onAction: (PreferencesUiAction) -> Unit) = Body("preferences-appearance") {
    Label(stringResource(R.string.preferences_background))
    WpThemeMode.entries.forEach { mode -> Select(themeModeLabel(mode), state.theme.mode == mode, "preferences-theme-${mode.name.lowercase()}") {
        onAction(PreferencesUiAction.ChangeTheme(state.theme.copy(mode = mode)))
    } }
    Label(stringResource(R.string.preferences_accent))
    WpAccent.entries.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { accent ->
                val scheme = WpThemePolicy.resolve(WpThemeSpec(accent = accent))
                Box(
                    Modifier.size(YokuliMetrics.MinTouch).clickable { onAction(PreferencesUiAction.ChangeTheme(state.theme.copy(accent = accent))) }
                        .semantics {
                            selected = state.theme.accent == accent
                            role = Role.RadioButton
                        }
                        .testTag("preferences-accent-${accent.name.lowercase()}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(30.dp).background(scheme.accent), contentAlignment = Alignment.Center) {
                        if (state.theme.accent == accent) WpText("✓", 18, color = scheme.onAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun Language(state: PreferencesUiState, onAction: (PreferencesUiAction) -> Unit) = Body("preferences-language") {
    AppLanguage.entries.forEach { language -> Select(languageLabel(language), state.language == language, "preferences-language-${language.languageTag}") {
        onAction(PreferencesUiAction.ChangeLanguage(language))
    } }
    WpText(stringResource(R.string.preferences_language_truth), 11, color = LocalWpTheme.current.muted)
}

@Composable
private fun Units(state: PreferencesUiState, onAction: (PreferencesUiAction) -> Unit) = Body("preferences-units") {
    WpText(stringResource(R.string.preferences_units_truth), 12, color = LocalWpTheme.current.muted)
    MeasurementUnitSystem.entries.forEach { units -> Select(unitsLabel(units), state.measurementUnits == units, "preferences-units-${units.name.lowercase()}") {
        onAction(PreferencesUiAction.ChangeUnits(units))
    } }
}

@Composable
private fun Motion(state: PreferencesUiState, onAction: (PreferencesUiAction) -> Unit) = Body("preferences-motion") {
    WpText(stringResource(R.string.preferences_motion_truth), 12, color = LocalWpTheme.current.muted)
    MotionPreference.entries.forEach { preference -> Select(motionLabel(preference), state.motionPreference == preference, "preferences-motion-${preference.name.lowercase()}") {
        onAction(PreferencesUiAction.ChangeMotion(preference))
    } }
}

@Composable
private fun Start(state: PreferencesUiState, onAction: (PreferencesUiAction) -> Unit) = Body("preferences-start") {
    WpText(stringResource(R.string.preferences_start_document, state.startDocumentVersion, state.pinnedTileCount), 18)
    WpText(stringResource(R.string.preferences_start_truth), 12, color = LocalWpTheme.current.muted)
    Command(stringResource(R.string.preferences_reset_start), "preferences-reset-start") { onAction(PreferencesUiAction.ResetStartScreen) }
}

@Composable
private fun AppTiles(state: PreferencesUiState, onAction: (PreferencesUiAction) -> Unit) = Body("preferences-app-tiles") {
    WpText(stringResource(R.string.preferences_app_tiles_truth), 12, color = LocalWpTheme.current.muted)
    state.appTiles.forEach { app ->
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("preferences-app-tile-${app.appId.value}")) {
            WpText(app.appId.value.replace('_', ' '), 19, weight = FontWeight.Light)
            WpText(app.supportedSizes.sortedBy { it.columns * it.rows }.joinToString(" · ") { "${it.columns}×${it.rows}" }, 11, color = LocalWpTheme.current.muted)
            if (app.preferences.isEmpty()) {
                WpText(stringResource(R.string.preferences_no_app_options), 10, color = LocalWpTheme.current.muted)
            } else app.preferences.forEach { item ->
                WpText(item.definition.label.forLanguage(state.language), 14, color = LocalWpTheme.current.muted)
                when (val definition = item.definition) {
                    is AppPreferenceDefinition.Toggle -> {
                        val value = item.value as AppPreferenceValue.Toggle
                        Select(
                            stringResource(if (value.enabled) R.string.preferences_enabled else R.string.preferences_disabled),
                            true,
                            "preferences-app-option-${item.key.value}",
                        ) {
                            onAction(
                                PreferencesUiAction.ChangeAppPreference(
                                    item.key,
                                    AppPreferenceValue.Toggle(!value.enabled),
                                ),
                            )
                        }
                    }
                    is AppPreferenceDefinition.Choice -> definition.options.forEach { option ->
                        Select(
                            definition.optionLabels[option]?.forLanguage(state.language) ?: option,
                            (item.value as? AppPreferenceValue.Choice)?.option == option,
                            "preferences-app-option-${item.key.value}-$option",
                        ) {
                            onAction(PreferencesUiAction.ChangeAppPreference(item.key, AppPreferenceValue.Choice(option)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun About(state: PreferencesUiState) = Body("preferences-about") {
    WpText(stringResource(R.string.preferences_product_name), 28, weight = FontWeight.Light)
    AboutRow(stringResource(R.string.preferences_version), state.versionName)
    AboutRow(stringResource(R.string.preferences_variant), state.buildVariant)
    AboutRow(stringResource(R.string.preferences_revision), state.gitSha.take(12))
    AboutRow(stringResource(R.string.preferences_document), "v${state.startDocumentVersion}")
    WpText(stringResource(R.string.preferences_about_truth), 11, color = LocalWpTheme.current.muted)
}

@Composable
private fun Body(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = YokuliMetrics.PageMargin, vertical = 4.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}

@Composable private fun Label(value: String) { WpText(value, 19, weight = FontWeight.Light) }

@Composable
private fun Select(label: String, isSelected: Boolean, tag: String, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = YokuliMetrics.MinTouch)
            .clickable(interactions, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { selected = isSelected; role = Role.RadioButton }.testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WpText(if (isSelected) "■" else "□", 19, color = if (isSelected) LocalWpTheme.current.accent else LocalWpTheme.current.muted)
        WpText(label, 17, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Command(label: String, tag: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = YokuliMetrics.MinTouch).clickable(onClick = onClick).testTag(tag), contentAlignment = Alignment.CenterStart) {
        WpText(label, 17, color = LocalWpTheme.current.accent)
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        WpText(label, 12, color = LocalWpTheme.current.muted)
        Spacer(Modifier.weight(1f))
        WpText(value, 13)
    }
}

@Composable private fun sectionLabel(section: PreferencesSection) = stringResource(when (section) {
    PreferencesSection.OVERVIEW -> R.string.preferences_section_overview
    PreferencesSection.APPEARANCE -> R.string.preferences_section_appearance
    PreferencesSection.LANGUAGE -> R.string.preferences_section_language
    PreferencesSection.UNITS -> R.string.preferences_section_units
    PreferencesSection.MOTION -> R.string.preferences_section_motion
    PreferencesSection.START -> R.string.preferences_section_start
    PreferencesSection.APP_TILES -> R.string.preferences_section_app_tiles
    PreferencesSection.ABOUT -> R.string.preferences_section_about
})

@Composable private fun themeModeLabel(mode: WpThemeMode) = stringResource(if (mode == WpThemeMode.DARK) R.string.preferences_dark else R.string.preferences_light)
@Composable private fun languageLabel(language: AppLanguage) = stringResource(if (language == AppLanguage.CHINESE) R.string.preferences_chinese else R.string.preferences_english)
@Composable private fun unitsLabel(units: MeasurementUnitSystem) = stringResource(if (units == MeasurementUnitSystem.NAUTICAL) R.string.preferences_units_nautical else R.string.preferences_units_metric)
@Composable private fun motionLabel(value: MotionPreference) = stringResource(if (value == MotionPreference.FOLLOW_SYSTEM) R.string.preferences_motion_system else R.string.preferences_motion_reduced)
@Composable private fun accentLabel(accent: WpAccent) = accent.displayName
private fun AppPreferenceLabel.forLanguage(language: AppLanguage): String =
    if (language == AppLanguage.CHINESE) chinese else english
