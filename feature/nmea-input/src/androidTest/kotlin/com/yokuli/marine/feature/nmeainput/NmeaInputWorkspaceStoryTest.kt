package com.yokuli.marine.feature.nmeainput

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.nmea.ChecksumPolicy
import com.yokuli.marine.data.runtime.NmeaConnectionDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Feature-owned UI stories; no socket, adapter, or production Shell registration is involved. */
class NmeaInputWorkspaceStoryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyOverviewExposesARealAddAction() {
        val actions = mutableListOf<NmeaInputUiAction>()
        show(NmeaInputUiState(), actions::add)

        compose.onNodeWithTag(NmeaInputTestTags.ROOT).assertIsDisplayed()
        compose.onNodeWithTag(NmeaInputTestTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithTag(NmeaInputTestTags.ADD).performClick()

        assertEquals(listOf(NmeaInputUiAction.AddConnection), actions)
    }

    @Test
    fun tcpTransportWithoutBytesRendersItsDedicatedTruthAndOpensDetails() {
        val id = ConnectionId("tcp-waiting-story")
        val actions = mutableListOf<NmeaInputUiAction>()
        val row = NmeaConnectionRowUi(
            id = id,
            name = "Bridge",
            endpoint = NmeaEndpointUi.TcpClient("bridge.local", 10_111),
            enabled = true,
            transport = ConnectionTransportUi.TCP_CONNECTED,
            input = ConnectionInputUi.NO_BYTES,
            headline = ConnectionHeadlineUi.TCP_CONNECTED_WAITING_FOR_DATA,
            metrics = NmeaConnectionDiagnostics.EMPTY,
            validFramesPerSecond5s = 0.0,
            lastValidFrameAgeMillis = null,
            failure = null,
        )
        show(
            NmeaInputUiState(
                summary = NmeaInputSummaryUi(enabledCount = 1, receivingCount = 0),
                page = NmeaInputPageUi.Overview(listOf(row)),
            ),
            actions::add,
        )

        compose.onNodeWithTag(
            NmeaInputTestTags.status(id.value),
            useUnmergedTree = true,
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(NmeaInputTestTags.connection(id.value)).performScrollTo().performClick()

        assertEquals(listOf(NmeaInputUiAction.OpenConnection(id)), actions)
    }

    @Test
    fun editorKeepsSaveAndSaveEnableAsSeparateActions() {
        val actions = mutableListOf<NmeaInputUiAction>()
        val draft = NmeaConnectionDraft(
            id = ConnectionId("editor-story"),
            name = "Listener",
            transport = NmeaInputTransportKind.UDP_LISTENER,
            tcpHost = "",
            portText = "10112",
            restrictUdpSender = false,
            udpSenderAddress = "",
            checksumPolicy = ChecksumPolicy.STRICT,
        )
        show(NmeaInputUiState(page = NmeaInputPageUi.Editor(draft)), actions::add)

        compose.onNodeWithTag(NmeaInputTestTags.PORT).assertIsDisplayed()
        compose.onNodeWithTag(NmeaInputTestTags.SAVE).performClick()
        compose.onNodeWithTag(NmeaInputTestTags.SAVE_AND_ENABLE).performClick()

        assertEquals(
            listOf(NmeaInputUiAction.Save, NmeaInputUiAction.SaveAndEnable),
            actions,
        )
    }

    private fun show(state: NmeaInputUiState, onAction: (NmeaInputUiAction) -> Unit) {
        compose.setContent {
            YokuliTheme(WpThemeSpec()) {
                NmeaInputWorkspace(state, onAction)
            }
        }
    }
}
