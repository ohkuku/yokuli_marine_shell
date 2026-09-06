package com.yokuli.marine.feature.nmeainput

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpAppBarAction
import com.yokuli.marine.core.design.WpApplicationBar
import com.yokuli.marine.core.design.PresentationCadence
import com.yokuli.marine.core.design.WpLiveConsole
import com.yokuli.marine.core.design.WpLiveField
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliMetrics
import com.yokuli.marine.core.design.wpEntrance
import com.yokuli.marine.core.design.wpTilt
import com.yokuli.marine.data.nmea.ChecksumPolicy
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput

/** State-only WP8 workspace. Runtime and sockets stay outside the composable boundary. */
@Composable
fun NmeaInputWorkspace(
    state: NmeaInputUiState,
    onAction: (NmeaInputUiAction) -> Unit,
) {
    val colors = LocalWpTheme.current
    val currentState by rememberUpdatedState(state)
    val currentAction by rememberUpdatedState(onAction)
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    BindInternalAppInputHandler { input ->
        when {
            input != ShellInput.BACK -> false
            imeVisible -> {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                true
            }
            else -> NmeaInputBackPolicy.actionFor(currentState.page)?.let { action ->
                currentAction(action)
                true
            } ?: false
        }
    }

    Column(
        Modifier.fillMaxSize().background(colors.background).testTag(NmeaInputTestTags.ROOT),
    ) {
        WpPageHeader(
            appKey = "nmea-input",
            appName = stringResource(R.string.nmea_input_title),
            contextLine = pageContext(state),
        )
        state.notice?.let { NoticeBar(it, onAction) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val page = state.page) {
                is NmeaInputPageUi.Overview -> OverviewPage(state.summary, page, onAction)
                is NmeaInputPageUi.Detail -> DetailPage(page.connection)
                is NmeaInputPageUi.Editor -> EditorPage(page.draft, onAction)
                is NmeaInputPageUi.ReplacementConfirmation -> ReplacementPage(page)
                is NmeaInputPageUi.DuplicateTcpConfirmation -> DuplicateTcpPage(page)
                is NmeaInputPageUi.DeleteConfirmation -> DeletePage(page.connection)
            }
        }
        WorkspaceApplicationBar(state.page, onAction)
    }
}

@Composable
private fun pageContext(state: NmeaInputUiState): String = when (val page = state.page) {
    is NmeaInputPageUi.Overview -> stringResource(
        R.string.nmea_summary,
        state.summary.enabledCount,
        state.summary.receivingCount,
    )
    is NmeaInputPageUi.Detail -> stringResource(R.string.context_connection_detail)
    is NmeaInputPageUi.Editor -> if (page.draft.expectedRevision == null) {
        stringResource(R.string.context_new_connection)
    } else {
        stringResource(R.string.context_edit_connection)
    }
    is NmeaInputPageUi.ReplacementConfirmation -> stringResource(R.string.context_confirm_change)
    is NmeaInputPageUi.DuplicateTcpConfirmation -> stringResource(R.string.context_confirm_duplicate)
    is NmeaInputPageUi.DeleteConfirmation -> stringResource(R.string.context_confirm_delete)
}

@Composable
private fun OverviewPage(
    summary: NmeaInputSummaryUi,
    page: NmeaInputPageUi.Overview,
    onAction: (NmeaInputUiAction) -> Unit,
) {
    val colors = LocalWpTheme.current
    ScrollBody {
        WpText(
            stringResource(R.string.nmea_summary, summary.enabledCount, summary.receivingCount),
            19,
            color = colors.accent,
            modifier = Modifier.testTag(NmeaInputTestTags.SUMMARY),
        )
        if (page.connections.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 30.dp).testTag(NmeaInputTestTags.EMPTY),
            ) {
                WpText(stringResource(R.string.empty_title), 27, weight = FontWeight.Light)
                WpText(
                    stringResource(R.string.empty_body),
                    13,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            Spacer(Modifier.height(12.dp))
            page.connections.forEachIndexed { index, row ->
                ConnectionRow(row, index, onAction)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConnectionRow(
    row: NmeaConnectionRowUi,
    order: Int,
    onAction: (NmeaInputUiAction) -> Unit,
) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 104.dp)
            .wpEntrance(row.id, order),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).heightIn(min = 88.dp)
                .combinedClickable(
                    interactionSource = interactions,
                    indication = null,
                    onClick = { onAction(NmeaInputUiAction.OpenConnection(row.id)) },
                ).padding(vertical = 8.dp, horizontal = 2.dp)
                .testTag(NmeaInputTestTags.connection(row.id.value)),
        ) {
            WpText(row.name, 23, weight = FontWeight.Light, maxLines = 2)
            WpText(
                endpointLabel(row.endpoint),
                12,
                color = colors.muted,
                modifier = Modifier.padding(top = 3.dp)
                    .testTag(NmeaInputTestTags.endpoint(row.id.value)),
            )
            WpText(
                headlineLabel(row.headline),
                14,
                color = headlineColor(row.headline),
                modifier = Modifier.padding(top = 7.dp)
                    .testTag(NmeaInputTestTags.status(row.id.value)),
            )
            Row(Modifier.padding(top = 3.dp)) {
                val liveFacts = buildList {
                    if (row.validFramesPerSecond5s > 0.0) {
                        add(stringResource(R.string.rate_five_seconds, row.validFramesPerSecond5s))
                    }
                    row.lastValidFrameAgeMillis?.let { add(ageLabel(it)) }
                }.joinToString(" · ")
                if (liveFacts.isNotEmpty()) WpLiveField(
                    value = liveFacts,
                    structuralKey = Triple(row.enabled, row.transport, row.input),
                    cadence = PresentationCadence.NmeaStatus,
                    size = 11,
                    color = colors.muted,
                    minValueWidth = 148.dp,
                    modifier = Modifier.testTag(NmeaInputTestTags.rate(row.id.value)),
                )
            }
        }
        InlineRunAction(row, onAction)
    }
}

@Composable
private fun InlineRunAction(row: NmeaConnectionRowUi, onAction: (NmeaInputUiAction) -> Unit) {
    if (row.enabled) {
        WpCommand(
            label = stringResource(R.string.action_stop),
            tag = NmeaInputTestTags.stop(row.id.value),
        ) { onAction(NmeaInputUiAction.Stop(row.id)) }
    } else {
        WpCommand(
            label = stringResource(R.string.action_start),
            tag = NmeaInputTestTags.start(row.id.value),
        ) { onAction(NmeaInputUiAction.Start(row.id)) }
    }
}

@Composable
private fun DetailPage(detail: NmeaConnectionDetailUi) {
    val row = detail.row
    val colors = LocalWpTheme.current
    ScrollBody(Modifier.testTag(NmeaInputTestTags.DETAIL)) {
        WpText(row.name, 30, weight = FontWeight.Light)
        WpText(endpointLabel(row.endpoint), 13, color = colors.muted, modifier = Modifier.padding(top = 4.dp))
        WpText(
            headlineLabel(row.headline),
            20,
            color = headlineColor(row.headline),
            modifier = Modifier.padding(top = 18.dp),
        )
        row.failure?.let {
            WpText(
                failureLabel(it),
                13,
                color = colors.alarm,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        SectionTitle(stringResource(R.string.section_runtime_facts))
        FactRow(stringResource(R.string.fact_user_intent), if (row.enabled) {
            stringResource(R.string.intent_enabled)
        } else {
            stringResource(R.string.intent_stopped)
        })
        FactRow(stringResource(R.string.fact_transport), transportLabel(row.transport))
        FactRow(stringResource(R.string.fact_input), inputLabel(row.input))
        LiveFactRow(
            stringResource(R.string.fact_last_valid),
            row.lastValidFrameAgeMillis?.let { ageLabel(it) } ?: stringResource(R.string.last_never),
            Triple(row.enabled, row.transport, row.input),
        )
        LiveFactRow(
            stringResource(R.string.fact_rate),
            stringResource(R.string.rate_five_seconds, row.validFramesPerSecond5s),
            Triple(row.enabled, row.transport, row.input),
        )

        Column(Modifier.testTag(NmeaInputTestTags.DIAGNOSTICS)) {
            SectionTitle(stringResource(R.string.section_diagnostics))
            DiagnosticRow(R.string.diagnostic_bytes, row.metrics.byteCount)
            DiagnosticRow(R.string.diagnostic_datagrams, row.metrics.datagramCount)
            DiagnosticRow(R.string.diagnostic_frames, row.metrics.frameCount)
            DiagnosticRow(R.string.diagnostic_legal_frames, row.metrics.legalFrameCount)
            DiagnosticRow(R.string.diagnostic_parsed_frames, row.metrics.parsedFrameCount)
            DiagnosticRow(R.string.diagnostic_observations, row.metrics.parsedObservationCount)
            DiagnosticRow(R.string.diagnostic_explicit_invalid, row.metrics.explicitInvalidFrameCount)
            DiagnosticRow(R.string.diagnostic_unsupported, row.metrics.unsupportedFrameCount)
            DiagnosticRow(R.string.diagnostic_checksum_failures, row.metrics.checksumFailureCount)
            DiagnosticRow(R.string.diagnostic_malformed, row.metrics.malformedFrameCount)
            DiagnosticRow(R.string.diagnostic_stale_session_drops, row.metrics.staleGenerationDropCount)
            DiagnosticRow(R.string.diagnostic_ingress_drops, row.metrics.ingressDropCount)
            DiagnosticRow(R.string.diagnostic_preview_drops, detail.rawPreviewDropCount)
            WpText(
                stringResource(R.string.input_timeout_note),
                11,
                color = colors.muted,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Column(Modifier.testTag(NmeaInputTestTags.RAW_PREVIEW)) {
            SectionTitle(stringResource(R.string.section_recent_input))
            val rawLines = detail.rawPreview.map { entry ->
                val provenance = when {
                    !entry.isCurrentSession -> stringResource(R.string.raw_previous_session)
                    entry.sender != null -> stringResource(R.string.raw_sender, entry.sender)
                    else -> stringResource(R.string.raw_current_session)
                }
                "$provenance\n${entry.raw}"
            }
            WpLiveConsole(
                newestFirstLines = rawLines,
                structuralKey = detail.row.id to detail.rawPreview.map { it.isCurrentSession },
                maxEntries = 20,
                modifier = Modifier.fillMaxWidth(),
            ) {
                WpText(stringResource(R.string.raw_empty), 13, color = colors.muted)
            }
        }
    }
}

@Composable
private fun EditorPage(draft: NmeaConnectionDraft, onAction: (NmeaInputUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    ScrollBody(Modifier.testTag(NmeaInputTestTags.EDITOR)) {
        WpText(
            if (draft.expectedRevision == null) stringResource(R.string.editor_new_title)
            else stringResource(R.string.editor_edit_title),
            28,
            weight = FontWeight.Light,
        )
        WpField(
            label = stringResource(R.string.field_name),
            value = draft.name,
            tag = NmeaInputTestTags.NAME,
            error = draft.errors[NmeaInputField.NAME],
            onValueChange = { onAction(NmeaInputUiAction.ChangeName(it)) },
        )

        FieldLabel(stringResource(R.string.field_protocol))
        SelectionRow(
            label = stringResource(R.string.protocol_tcp_client),
            selected = draft.transport == NmeaInputTransportKind.TCP_CLIENT,
            tag = "nmea-input-editor-protocol-tcp",
        ) { onAction(NmeaInputUiAction.ChangeTransport(NmeaInputTransportKind.TCP_CLIENT)) }
        SelectionRow(
            label = stringResource(R.string.protocol_udp_listener),
            selected = draft.transport == NmeaInputTransportKind.UDP_LISTENER,
            tag = "nmea-input-editor-protocol-udp",
        ) { onAction(NmeaInputUiAction.ChangeTransport(NmeaInputTransportKind.UDP_LISTENER)) }

        if (draft.transport == NmeaInputTransportKind.TCP_CLIENT) {
            WpField(
                label = stringResource(R.string.field_tcp_host),
                value = draft.tcpHost,
                tag = NmeaInputTestTags.TCP_HOST,
                error = draft.errors[NmeaInputField.TCP_HOST],
                keyboardType = KeyboardType.Uri,
                onValueChange = { onAction(NmeaInputUiAction.ChangeTcpHost(it)) },
            )
        } else {
            WpText(
                stringResource(R.string.udp_listener_explanation),
                12,
                color = colors.muted,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        WpField(
            label = if (draft.transport == NmeaInputTransportKind.TCP_CLIENT) {
                stringResource(R.string.field_remote_port)
            } else {
                stringResource(R.string.field_local_port)
            },
            value = draft.portText,
            tag = NmeaInputTestTags.PORT,
            error = draft.errors[NmeaInputField.PORT],
            keyboardType = KeyboardType.Number,
            onValueChange = { onAction(NmeaInputUiAction.ChangePort(it)) },
        )

        if (draft.transport == NmeaInputTransportKind.UDP_LISTENER) {
            SelectionRow(
                label = stringResource(R.string.restrict_udp_sender),
                selected = draft.restrictUdpSender,
                tag = NmeaInputTestTags.UDP_SENDER_RESTRICTION,
            ) {
                onAction(NmeaInputUiAction.ChangeUdpSenderRestriction(!draft.restrictUdpSender))
            }
            if (draft.restrictUdpSender) {
                WpField(
                    label = stringResource(R.string.field_udp_sender),
                    value = draft.udpSenderAddress,
                    tag = NmeaInputTestTags.UDP_SENDER,
                    error = draft.errors[NmeaInputField.UDP_SENDER_ADDRESS],
                    keyboardType = KeyboardType.Uri,
                    onValueChange = { onAction(NmeaInputUiAction.ChangeUdpSenderAddress(it)) },
                )
            }
        }

        FieldLabel(stringResource(R.string.field_checksum_policy))
        SelectionRow(
            label = stringResource(R.string.checksum_strict),
            selected = draft.checksumPolicy == ChecksumPolicy.STRICT,
            tag = NmeaInputTestTags.CHECKSUM_STRICT,
        ) { onAction(NmeaInputUiAction.ChangeChecksumPolicy(ChecksumPolicy.STRICT)) }
        SelectionRow(
            label = stringResource(R.string.checksum_allow_missing),
            selected = draft.checksumPolicy == ChecksumPolicy.ALLOW_MISSING,
            tag = NmeaInputTestTags.CHECKSUM_ALLOW_MISSING,
        ) { onAction(NmeaInputUiAction.ChangeChecksumPolicy(ChecksumPolicy.ALLOW_MISSING)) }
        WpText(
            stringResource(R.string.checksum_explanation),
            11,
            color = colors.muted,
            modifier = Modifier.padding(top = 5.dp, bottom = 16.dp),
        )
        if (draft.submitting) {
            WpText(stringResource(R.string.operation_in_progress), 13, color = colors.accent)
        }
    }
}

@Composable
private fun ReplacementPage(page: NmeaInputPageUi.ReplacementConfirmation) {
    val colors = LocalWpTheme.current
    ScrollBody(Modifier.testTag(NmeaInputTestTags.REPLACEMENT)) {
        WpText(stringResource(R.string.replace_title), 30, weight = FontWeight.Light)
        WpText(
            stringResource(R.string.replace_body, page.draft.name),
            15,
            modifier = Modifier.padding(top = 14.dp),
        )
        WpText(
            if (page.startAfterSave) stringResource(R.string.replace_restart_fact)
            else stringResource(R.string.replace_save_fact),
            12,
            color = colors.muted,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun DeletePage(row: NmeaConnectionRowUi) {
    val colors = LocalWpTheme.current
    ScrollBody(Modifier.testTag(NmeaInputTestTags.DELETE_CONFIRMATION)) {
        WpText(stringResource(R.string.delete_title), 30, weight = FontWeight.Light)
        WpText(
            stringResource(R.string.delete_body, row.name),
            15,
            modifier = Modifier.padding(top = 14.dp),
        )
        WpText(
            stringResource(R.string.delete_fact),
            12,
            color = colors.muted,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun DuplicateTcpPage(page: NmeaInputPageUi.DuplicateTcpConfirmation) {
    val colors = LocalWpTheme.current
    ScrollBody(Modifier.testTag(NmeaInputTestTags.DUPLICATE_TCP)) {
        WpText(stringResource(R.string.duplicate_tcp_title), 30, weight = FontWeight.Light)
        WpText(
            stringResource(R.string.duplicate_tcp_body, page.draft.name),
            15,
            modifier = Modifier.padding(top = 14.dp),
        )
        WpText(
            stringResource(R.string.duplicate_tcp_fact),
            12,
            color = colors.muted,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun WorkspaceApplicationBar(
    page: NmeaInputPageUi,
    onAction: (NmeaInputUiAction) -> Unit,
) {
    val actions = when (page) {
        is NmeaInputPageUi.Overview -> listOf(
            appBarAction("+", R.string.action_add, NmeaInputTestTags.ADD) {
                onAction(NmeaInputUiAction.AddConnection)
            },
        )
        is NmeaInputPageUi.Detail -> buildList {
            add(appBarAction("≡", R.string.action_view_data, "nmea-input-detail-view-data") {
                onAction(NmeaInputUiAction.ViewReceivedData(page.connection.row.id))
            })
            add(appBarAction("✎", R.string.action_edit, "nmea-input-detail-edit") {
                onAction(NmeaInputUiAction.EditConnection(page.connection.row.id))
            })
            if (page.connection.row.enabled) {
                add(appBarAction("■", R.string.action_stop, NmeaInputTestTags.stop(page.connection.row.id.value)) {
                    onAction(NmeaInputUiAction.Stop(page.connection.row.id))
                })
            } else {
                add(appBarAction("▶", R.string.action_start, NmeaInputTestTags.start(page.connection.row.id.value)) {
                    onAction(NmeaInputUiAction.Start(page.connection.row.id))
                })
            }
            if (page.connection.row.headline == ConnectionHeadlineUi.FAILED ||
                page.connection.row.headline == ConnectionHeadlineUi.INPUT_INTERRUPTED
            ) {
                add(appBarAction("↻", R.string.action_retry, NmeaInputTestTags.retry(page.connection.row.id.value)) {
                    onAction(NmeaInputUiAction.Retry(page.connection.row.id))
                })
            }
            add(appBarAction("×", R.string.action_delete, "nmea-input-detail-delete") {
                onAction(NmeaInputUiAction.RequestDelete(page.connection.row.id))
            })
        }
        is NmeaInputPageUi.Editor -> if (page.draft.submitting) {
            listOf(appBarAction("←", R.string.action_cancel, "nmea-input-editor-cancel") {
                onAction(NmeaInputUiAction.BackToOverview)
            })
        } else {
            listOf(
                appBarAction("✓", R.string.action_save, NmeaInputTestTags.SAVE) {
                    onAction(NmeaInputUiAction.Save)
                },
                appBarAction("▶", R.string.action_save_enable, NmeaInputTestTags.SAVE_AND_ENABLE) {
                    onAction(NmeaInputUiAction.SaveAndEnable)
                },
                appBarAction("←", R.string.action_cancel, "nmea-input-editor-cancel") {
                    onAction(NmeaInputUiAction.BackToOverview)
                },
            )
        }
        is NmeaInputPageUi.ReplacementConfirmation -> listOf(
            appBarAction("✓", R.string.action_confirm, NmeaInputTestTags.REPLACEMENT_CONFIRM) {
                onAction(NmeaInputUiAction.ConfirmRunningReplacement)
            },
            appBarAction("←", R.string.action_cancel, "nmea-input-replacement-cancel") {
                onAction(NmeaInputUiAction.BackToOverview)
            },
        )
        is NmeaInputPageUi.DuplicateTcpConfirmation -> listOf(
            appBarAction("✓", R.string.action_confirm, NmeaInputTestTags.DUPLICATE_TCP_CONFIRM) {
                onAction(NmeaInputUiAction.ConfirmDuplicateTcp)
            },
            appBarAction("←", R.string.action_cancel, "nmea-input-duplicate-tcp-cancel") {
                onAction(NmeaInputUiAction.BackToOverview)
            },
        )
        is NmeaInputPageUi.DeleteConfirmation -> listOf(
            appBarAction("×", R.string.action_delete, NmeaInputTestTags.DELETE_CONFIRM) {
                onAction(NmeaInputUiAction.ConfirmDelete)
            },
            appBarAction("←", R.string.action_cancel, "nmea-input-delete-cancel") {
                onAction(NmeaInputUiAction.BackToOverview)
            },
        )
    }
    WpApplicationBar(actions)
}

@Composable
private fun appBarAction(
    symbol: String,
    labelResource: Int,
    tag: String,
    action: () -> Unit,
) = WpAppBarAction(
    symbol = symbol,
    label = stringResource(labelResource),
    testTag = tag,
    onClick = action,
)

@Composable
private fun NoticeBar(notice: NmeaInputNoticeUi, onAction: (NmeaInputUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    val dismissDescription = stringResource(R.string.action_dismiss)
    Row(
        Modifier.fillMaxWidth().heightIn(min = YokuliMetrics.MinTouch)
            .background(if (notice is NmeaInputNoticeUi.OperationFailed) colors.alarm else colors.accent)
            .semantics { role = Role.Button; contentDescription = dismissDescription }
            .clickable(interactionSource = interactions, indication = null) {
                onAction(NmeaInputUiAction.DismissNotice)
            }.padding(horizontal = YokuliMetrics.PageMargin, vertical = 8.dp)
            .testTag(NmeaInputTestTags.NOTICE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WpText(noticeLabel(notice), 13, color = Color.White, modifier = Modifier.weight(1f))
        WpText("×", 19, color = Color.White)
    }
}

@Composable
private fun WpField(
    label: String,
    value: String,
    tag: String,
    error: NmeaInputValidationError?,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    val colors = LocalWpTheme.current
    FieldLabel(label)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            color = colors.foreground,
            fontSize = 18.sp,
            fontFamily = FontFamily.SansSerif,
        ),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().heightIn(min = YokuliMetrics.MinTouch)
            .border(1.dp, if (error == null) colors.muted else colors.alarm)
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .semantics { contentDescription = label }
            .testTag(tag),
    )
    error?.let {
        WpText(
            validationLabel(it),
            11,
            color = colors.alarm,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    WpText(text, 17, weight = FontWeight.Light, modifier = Modifier.padding(top = 17.dp, bottom = 7.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectionRow(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = YokuliMetrics.MinTouch)
            .semantics { this.selected = selected; role = Role.RadioButton }
            .combinedClickable(interactionSource = interactions, indication = null, onClick = onClick)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).border(2.dp, if (selected) colors.accent else colors.muted),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).background(colors.accent))
        }
        WpText(label, 16, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun WpCommand(label: String, tag: String, onClick: () -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    Box(
        Modifier.heightIn(min = YokuliMetrics.MinTouch).padding(start = 8.dp)
            .semantics { role = Role.Button; contentDescription = label }
            .wpTilt(interactions, maximumDegrees = 4f)
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        WpText(label, 13, color = colors.accent)
    }
}

@Composable
private fun ScrollBody(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = YokuliMetrics.PageMargin, vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(text: String) {
    WpText(text, 22, weight = FontWeight.Light, modifier = Modifier.padding(top = 24.dp, bottom = 7.dp))
}

@Composable
private fun FactRow(label: String, value: String) {
    val colors = LocalWpTheme.current
    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.Bottom) {
        WpText(label, 12, color = colors.muted)
        Spacer(Modifier.weight(1f))
        WpText(value, 13, modifier = Modifier.padding(start = 12.dp), maxLines = 2)
    }
}

@Composable
private fun LiveFactRow(label: String, value: String, structuralKey: Any?) {
    val colors = LocalWpTheme.current
    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.Bottom) {
        WpText(label, 12, color = colors.muted)
        Spacer(Modifier.weight(1f))
        WpLiveField(
            value = value,
            structuralKey = structuralKey,
            cadence = PresentationCadence.NmeaStatus,
            size = 13,
            minValueWidth = 148.dp,
            modifier = Modifier.padding(start = 12.dp),
            maxLines = 2,
        )
    }
}

@Composable
private fun DiagnosticRow(labelResource: Int, value: Long) {
    FactRow(stringResource(labelResource), value.toString())
}

@Composable
private fun endpointLabel(endpoint: NmeaEndpointUi): String = when (endpoint) {
    is NmeaEndpointUi.TcpClient -> stringResource(R.string.endpoint_tcp, endpoint.host, endpoint.port)
    is NmeaEndpointUi.UdpListener -> endpoint.senderAddress?.let {
        stringResource(R.string.endpoint_udp_restricted, endpoint.localPort, it)
    } ?: stringResource(R.string.endpoint_udp, endpoint.localPort)
}

@Composable
private fun headlineLabel(headline: ConnectionHeadlineUi): String = stringResource(
    when (headline) {
        ConnectionHeadlineUi.STOPPED -> R.string.headline_stopped
        ConnectionHeadlineUi.STARTING -> R.string.headline_starting
        ConnectionHeadlineUi.TCP_CONNECTED_WAITING_FOR_DATA -> R.string.headline_tcp_waiting
        ConnectionHeadlineUi.UDP_LISTENING_WAITING_FOR_DATAGRAM -> R.string.headline_udp_waiting
        ConnectionHeadlineUi.INPUT_WITHOUT_VALID_NMEA -> R.string.headline_invalid_input
        ConnectionHeadlineUi.RECEIVING_VALID_NMEA -> R.string.headline_receiving
        ConnectionHeadlineUi.INPUT_INTERRUPTED -> R.string.headline_interrupted
        ConnectionHeadlineUi.INPUT_OVERLOADED -> R.string.headline_overloaded
        ConnectionHeadlineUi.WAITING_FOR_NETWORK -> R.string.headline_waiting_network
        ConnectionHeadlineUi.RECONNECT_WAITING -> R.string.headline_reconnect
        ConnectionHeadlineUi.PLATFORM_START_REQUIRED -> R.string.headline_platform_start
        ConnectionHeadlineUi.FAILED -> R.string.headline_failed
    },
)

@Composable
private fun headlineColor(headline: ConnectionHeadlineUi) = when (headline) {
    ConnectionHeadlineUi.RECEIVING_VALID_NMEA -> LocalWpTheme.current.safe
    ConnectionHeadlineUi.FAILED,
    ConnectionHeadlineUi.INPUT_OVERLOADED,
    -> LocalWpTheme.current.alarm
    ConnectionHeadlineUi.INPUT_INTERRUPTED,
    ConnectionHeadlineUi.INPUT_WITHOUT_VALID_NMEA,
    ConnectionHeadlineUi.RECONNECT_WAITING,
    ConnectionHeadlineUi.WAITING_FOR_NETWORK,
    ConnectionHeadlineUi.PLATFORM_START_REQUIRED,
    -> LocalWpTheme.current.warning
    else -> LocalWpTheme.current.muted
}

@Composable
private fun transportLabel(transport: ConnectionTransportUi): String = stringResource(
    when (transport) {
        ConnectionTransportUi.STOPPED -> R.string.transport_stopped
        ConnectionTransportUi.STARTING -> R.string.transport_starting
        ConnectionTransportUi.TCP_CONNECTED -> R.string.transport_tcp_connected
        ConnectionTransportUi.UDP_LISTENING -> R.string.transport_udp_listening
        ConnectionTransportUi.WAITING_FOR_NETWORK -> R.string.transport_waiting_network
        ConnectionTransportUi.RECONNECT_WAITING -> R.string.transport_reconnect
        ConnectionTransportUi.PLATFORM_START_REQUIRED -> R.string.transport_platform_start
        ConnectionTransportUi.FAILED -> R.string.transport_failed
    },
)

@Composable
private fun inputLabel(input: ConnectionInputUi): String = stringResource(
    when (input) {
        ConnectionInputUi.NO_BYTES -> R.string.input_no_bytes
        ConnectionInputUi.BYTES_WITHOUT_VALID_FRAME -> R.string.input_no_valid_frame
        ConnectionInputUi.RECEIVING_VALID_FRAMES -> R.string.input_receiving
        ConnectionInputUi.INTERRUPTED -> R.string.input_interrupted
        ConnectionInputUi.OVERLOADED -> R.string.input_overloaded
    },
)

@Composable
private fun failureLabel(failure: NmeaInputFailureUi): String = when (failure) {
    is NmeaInputFailureUi.UdpAddressInUse -> stringResource(R.string.failure_udp_port, failure.port)
    NmeaInputFailureUi.PersistenceFailed -> stringResource(R.string.failure_persistence)
    NmeaInputFailureUi.InvalidConfiguration -> stringResource(R.string.failure_invalid_configuration)
    NmeaInputFailureUi.DuplicateTcpEndpoint -> stringResource(R.string.failure_duplicate_tcp)
    NmeaInputFailureUi.ConnectionLimitReached -> stringResource(R.string.failure_connection_limit)
    NmeaInputFailureUi.ConnectionNotFound -> stringResource(R.string.failure_connection_not_found)
    NmeaInputFailureUi.StaleSession -> stringResource(R.string.failure_stale_session)
    NmeaInputFailureUi.PlatformRestricted -> stringResource(R.string.failure_platform_restricted)
    NmeaInputFailureUi.IngressOverloaded -> stringResource(R.string.failure_ingress_overloaded)
    NmeaInputFailureUi.TransportFailed -> stringResource(R.string.failure_transport)
    NmeaInputFailureUi.InternalError -> stringResource(R.string.failure_internal)
}

@Composable
private fun noticeLabel(notice: NmeaInputNoticeUi): String = when (notice) {
    is NmeaInputNoticeUi.OperationFailed -> failureLabel(notice.failure)
    NmeaInputNoticeUi.Saved -> stringResource(R.string.notice_saved)
    NmeaInputNoticeUi.Started -> stringResource(R.string.notice_start_requested)
    NmeaInputNoticeUi.Stopped -> stringResource(R.string.notice_stop_requested)
    NmeaInputNoticeUi.Deleted -> stringResource(R.string.notice_deleted)
    NmeaInputNoticeUi.ActionQueueFull -> stringResource(R.string.notice_queue_full)
}

@Composable
private fun validationLabel(error: NmeaInputValidationError): String = stringResource(
    when (error) {
        NmeaInputValidationError.REQUIRED -> R.string.validation_required
        NmeaInputValidationError.ASCII_DIGITS_ONLY -> R.string.validation_ascii_digits
        NmeaInputValidationError.PORT_OUT_OF_RANGE -> R.string.validation_port_range
    },
)

@Composable
private fun ageLabel(ageMillis: Long): String = when {
    ageMillis < 1_000L -> stringResource(R.string.last_just_now)
    ageMillis < 60_000L -> stringResource(R.string.last_seconds_ago, ageMillis / 1_000L)
    else -> stringResource(R.string.last_minutes_ago, ageMillis / 60_000L)
}
