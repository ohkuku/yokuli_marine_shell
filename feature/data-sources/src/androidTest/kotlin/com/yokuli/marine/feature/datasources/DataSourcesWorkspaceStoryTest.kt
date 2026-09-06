package com.yokuli.marine.feature.datasources

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.phone.PhoneLocationSnapshot
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.source.MarineSourceSnapshot
import org.junit.Rule
import org.junit.Test

class DataSourcesWorkspaceStoryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyCatalogShowsTruthfulPhoneActionAndNoFakeMeasurements() {
        val state = DataSourcesProjector.project(
            MarineSourceSnapshot.EMPTY,
            NmeaRuntimeSnapshot.EMPTY,
            PhoneLocationSnapshot.EMPTY,
            DataSourcesLocalState(),
            0L,
        )
        compose.setContent { YokuliTheme(WpThemeSpec()) { DataSourcesWorkspace(state, {}) } }

        compose.onNodeWithTag(DataSourcesTestTags.ROOT).assertIsDisplayed()
        compose.onNodeWithTag(DataSourcesTestTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithTag(DataSourcesTestTags.ENABLE_PHONE).assertIsDisplayed()
    }

    @Test
    fun dataAndSentenceViewsAreRealSwitchesOnOneWorkspace() {
        var action: DataSourcesUiAction? = null
        val state = DataSourcesProjector.project(
            MarineSourceSnapshot.EMPTY,
            NmeaRuntimeSnapshot.EMPTY,
            PhoneLocationSnapshot.EMPTY,
            DataSourcesLocalState(),
            0L,
        )
        compose.setContent { YokuliTheme(WpThemeSpec()) { DataSourcesWorkspace(state) { action = it } } }

        compose.onNodeWithTag(DataSourcesTestTags.SENTENCE_VIEW).performClick()
        assert(action == DataSourcesUiAction.ChangeView(DataSourcesViewMode.SENTENCES))
    }

    @Test
    fun rootBackIsOwnedByShellAndNeverExitsFromTheFeature() {
        val state = DataSourcesProjector.project(
            MarineSourceSnapshot.EMPTY,
            NmeaRuntimeSnapshot.EMPTY,
            PhoneLocationSnapshot.EMPTY,
            DataSourcesLocalState(),
            0L,
        )
        assert(DataSourcesBackPolicy.actionFor(state.page) == null)
    }
}
