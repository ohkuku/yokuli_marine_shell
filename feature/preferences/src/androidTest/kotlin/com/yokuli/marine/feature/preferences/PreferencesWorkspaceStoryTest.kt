package com.yokuli.marine.feature.preferences

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.core.model.AppLanguage
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.MeasurementUnitSystem
import com.yokuli.shell.contract.MotionPreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PreferencesWorkspaceStoryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `appearance language units motion Start tiles and About are real settings sections`() {
        val actions = mutableListOf<PreferencesUiAction>()
        compose.setContent { YokuliTheme(WpThemeSpec()) { PreferencesWorkspace(state(), actions::add) } }

        PreferencesSection.entries.filterNot { it == PreferencesSection.OVERVIEW }.forEach { section ->
            compose.onNodeWithTag("preferences-section-${section.name.lowercase()}").assertIsDisplayed()
        }
        compose.onNodeWithTag("preferences-section-units").performClick()
        assertEquals(PreferencesUiAction.OpenSection(PreferencesSection.UNITS), actions.last())
    }

    @Test
    fun `units and motion actions are typed and App Tiles uses installed app declarations`() {
        val actions = mutableListOf<PreferencesUiAction>()
        var section by mutableStateOf(PreferencesSection.UNITS)
        compose.setContent {
            YokuliTheme(WpThemeSpec()) {
                PreferencesWorkspace(state().copy(section = section), actions::add)
            }
        }
        compose.onNodeWithTag("preferences-units-metric").performClick()
        assertEquals(PreferencesUiAction.ChangeUnits(MeasurementUnitSystem.METRIC), actions.last())

        compose.runOnIdle { section = PreferencesSection.MOTION }
        compose.onNodeWithTag("preferences-motion-reduced").performClick()
        assertEquals(PreferencesUiAction.ChangeMotion(MotionPreference.REDUCED), actions.last())

        compose.runOnIdle { section = PreferencesSection.APP_TILES }
        compose.onNodeWithTag("preferences-app-tile-chart").assertIsDisplayed()
    }

    private fun state() = PreferencesUiState(
        theme = WpThemeSpec(),
        language = AppLanguage.CHINESE,
        measurementUnits = MeasurementUnitSystem.NAUTICAL,
        motionPreference = MotionPreference.FOLLOW_SYSTEM,
        pinnedTileCount = 2,
        startDocumentVersion = 2,
        appTiles = listOf(AppTilePreferenceUi(LauncherAppId("chart"), MarineTileSize.entries.toSet())),
        versionName = "test",
        buildVariant = "standalone/debug",
        gitSha = "abc",
    )
}
