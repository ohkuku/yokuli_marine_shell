package com.yokuli.marine.feature.data

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.yokuli.marine.core.design.PresentationCadence
import com.yokuli.marine.core.design.WpLiveConsole
import com.yokuli.marine.core.design.WpLiveField
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliMetrics
import com.yokuli.marine.core.design.wpEntrance
import com.yokuli.marine.data.catalog.SentenceParseStatus
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.DepthReference
import com.yokuli.marine.data.model.HeadingReference
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.WindReference
import com.yokuli.marine.data.model.WindSpeedReference
import com.yokuli.marine.data.phone.PhoneLocationPermission
import com.yokuli.marine.data.phone.PhoneLocationState
import com.yokuli.marine.data.source.ResolvedDatum
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import java.util.Locale

/**
 * One Data workspace. The connection editor is injected by the composition root so W04 can reuse
 * its proven workflow without making the new Feature depend on the retired product App.
 */
@Composable
fun DataWorkspace(
    state: DataUiState,
    onAction: (DataUiAction) -> Unit,
    inputsContent: @Composable () -> Unit,
) {
    val colors = LocalWpTheme.current
    val currentState by rememberUpdatedState(state)
    val currentAction by rememberUpdatedState(onAction)
    if (state.section != DataSection.INPUTS) {
        BindInternalAppInputHandler { input ->
            if (input != ShellInput.BACK) return@BindInternalAppInputHandler false
            DataBackPolicy.actionFor(currentState.section)?.let {
                currentAction(it)
                true
            } ?: false
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background).testTag(DataTestTags.ROOT)) {
        WpPageHeader(
            appKey = "data",
            appName = stringResource(R.string.data_title),
            contextLine = sectionContext(state),
        )
        SectionStrip(state.section, onAction)
        state.notice?.let { notice ->
            WpText(
                noticeLabel(notice),
                12,
                color = colors.accent,
                modifier = Modifier.padding(horizontal = YokuliMetrics.PageMargin, vertical = 4.dp)
                    .clickable { onAction(DataUiAction.DismissNotice) },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.section) {
                DataSection.OVERVIEW -> Overview(state)
                DataSection.INPUTS -> Box(Modifier.fillMaxSize().testTag(DataTestTags.INPUTS)) { inputsContent() }
                DataSection.SOURCES -> Sources(state, onAction)
                DataSection.FLOW -> Flow(state)
                DataSection.DIAGNOSTICS -> Diagnostics(state)
            }
        }
    }
}

@Composable
private fun SectionStrip(section: DataSection, onAction: (DataUiAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = YokuliMetrics.PageMargin),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        DataSection.entries.forEach { item ->
            val interactions = remember { MutableInteractionSource() }
            WpText(
                sectionLabel(item),
                14,
                color = if (item == section) LocalWpTheme.current.accent else LocalWpTheme.current.muted,
                weight = if (item == section) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.heightIn(min = YokuliMetrics.MinTouch)
                    .clickable(interactions, indication = null, role = Role.Tab) {
                        onAction(DataUiAction.Navigate(item))
                    }
                    .semantics { selected = item == section }
                    .testTag(DataTestTags.section(item))
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun Overview(state: DataUiState) {
    ScrollBody(Modifier.testTag(DataTestTags.OVERVIEW)) {
        WpText(stringResource(R.string.overview_explanation), 13, color = LocalWpTheme.current.muted)
        Spacer(Modifier.height(16.dp))
        OverviewField(
            stringResource(R.string.value_position),
            state.resolvedValues[DataKey.Position],
        )
        OverviewPair(
            stringResource(R.string.value_sog_cog),
            state.resolvedValues[DataKey.SpeedOverGround],
            state.resolvedValues[DataKey.CourseOverGround],
        )
        OverviewField(
            stringResource(R.string.value_heading),
            state.firstResolved(
                DataKey.Heading(HeadingReference.TRUE),
                DataKey.Heading(HeadingReference.MAGNETIC),
            ),
        )
        OverviewField(
            stringResource(R.string.value_depth),
            state.firstResolved(
                DataKey.Depth(DepthReference.BELOW_SURFACE),
                DataKey.Depth(DepthReference.BELOW_TRANSDUCER),
                DataKey.Depth(DepthReference.BELOW_KEEL),
            ),
        )
        OverviewPair(
            stringResource(R.string.value_wind),
            state.firstResolved(
                DataKey.WindAngle(WindReference.APPARENT),
                DataKey.WindAngle(WindReference.TRUE_RELATIVE),
                DataKey.WindAngle(WindReference.TRUE_NORTH),
                DataKey.WindAngle(WindReference.MAGNETIC_NORTH),
            ),
            state.firstResolved(
                DataKey.WindSpeed(WindSpeedReference.APPARENT),
                DataKey.WindSpeed(WindSpeedReference.TRUE),
            ),
        )
    }
}

@Composable
private fun OverviewField(label: String, datum: ResolvedDatum?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        WpLiveField(
            value = datum?.value.formatted(),
            label = label,
            structuralKey = datum?.source to datum?.availability,
            cadence = PresentationCadence.DataOverview,
            size = 27,
            minValueWidth = 190.dp,
        )
        WpText(datum.sourceLine(), 11, color = LocalWpTheme.current.muted)
    }
}

@Composable
private fun OverviewPair(label: String, first: ResolvedDatum?, second: ResolvedDatum?) {
    val value = listOf(first?.value.formatted(), second?.value.formatted()).joinToString("  ·  ")
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        WpLiveField(
            value = value,
            label = label,
            structuralKey = listOf(first?.source, first?.availability, second?.source, second?.availability),
            cadence = PresentationCadence.DataOverview,
            size = 24,
            minValueWidth = 240.dp,
        )
        WpText(
            listOfNotNull(first?.sourceLine(), second?.sourceLine()).distinct().joinToString(" · ")
                .ifBlank { stringResource(R.string.no_selected_source) },
            11,
            color = LocalWpTheme.current.muted,
        )
    }
}

@Composable
private fun Sources(state: DataUiState, onAction: (DataUiAction) -> Unit) {
    val focus = state.focusedSourceConnectionId
    val groups = if (focus == null) state.groups else state.groups.filter { group ->
        group.candidates.any { it.source.connectionId == focus }
    }
    ScrollBody(Modifier.testTag(DataTestTags.SOURCES)) {
        WpText(stringResource(R.string.sources_explanation), 13, color = LocalWpTheme.current.muted)
        if (focus != null) {
            WpCommand(stringResource(R.string.clear_source_focus, focus.value)) {
                onAction(DataUiAction.ClearSourceFocus)
            }
        }
        if (groups.isEmpty()) {
            WpText(stringResource(R.string.sources_empty), 27, weight = FontWeight.Light, modifier = Modifier.padding(top = 24.dp))
        }
        groups.forEachIndexed { index, group ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 12.dp).wpEntrance(group.group, index)
                    .testTag(DataTestTags.group(group.group)),
            ) {
                WpText(groupLabel(group.group), 25, weight = FontWeight.Light)
                WpText(groupStatusLabel(group.status), 13, color = statusColor(group.status))
                group.candidates.forEach { candidate ->
                    Column(
                        Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp)
                            .testTag(DataTestTags.candidate(group.group, candidate.source.connectionId.value)),
                    ) {
                        WpText(candidate.displayName, 18, weight = FontWeight.Light)
                        WpText(
                            candidate.evidence.formatters.sorted().joinToString(" · ")
                                .ifBlank { candidate.evidence.phoneProviders.sorted().joinToString(" · ") },
                            11,
                            color = LocalWpTheme.current.muted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            candidate.evidence.availabilityByKey.values.distinct().forEach { availability ->
                                WpText(availabilityLabel(availability), 11, color = LocalWpTheme.current.muted)
                            }
                        }
                        if (candidate.source == group.selectedSource) {
                            WpText(stringResource(R.string.current_source), 11, color = LocalWpTheme.current.accent)
                        } else {
                            WpCommand(stringResource(R.string.use_source)) {
                                onAction(DataUiAction.UseSource(group.group, candidate.source))
                            }
                        }
                    }
                }
                if (group.group == SourceGroup.POSITION_AND_MOTION &&
                    group.candidates.none { it.source == com.yokuli.marine.data.phone.PHONE_SYSTEM_LOCATION_SOURCE }
                ) {
                    WpCommand(phoneActionLabel(state)) { onAction(DataUiAction.UsePhone(group.group)) }
                }
                WpCommand(stringResource(R.string.disable_group)) {
                    onAction(DataUiAction.DisableGroup(group.group))
                }
            }
        }
    }
}

@Composable
private fun Flow(state: DataUiState) {
    ScrollBody(Modifier.testTag(DataTestTags.FLOW)) {
        WpText(stringResource(R.string.flow_explanation), 13, color = LocalWpTheme.current.muted)
        if (state.flow.isEmpty()) {
            WpText(stringResource(R.string.flow_empty), 27, weight = FontWeight.Light, modifier = Modifier.padding(top = 24.dp))
        }
        state.flow.forEachIndexed { index, link ->
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).wpEntrance(link.source to link.group, index)) {
                WpText(link.source.connectionId.value, 20, weight = FontWeight.Light)
                WpText(
                    "${link.sentenceFamilies.sorted().joinToString(" + ").ifBlank { stringResource(R.string.phone_evidence) }}  →  ${groupLabel(link.group)}",
                    13,
                    color = if (link.selectedForOutput) LocalWpTheme.current.accent else LocalWpTheme.current.muted,
                )
                if (link.selectedForOutput) WpText(stringResource(R.string.resolved_output), 11, color = LocalWpTheme.current.accent)
            }
        }
    }
}

@Composable
private fun Diagnostics(state: DataUiState) {
    ScrollBody(Modifier.testTag(DataTestTags.DIAGNOSTICS)) {
        WpText(stringResource(R.string.diagnostics_explanation), 13, color = LocalWpTheme.current.muted)
        Spacer(Modifier.height(14.dp))
        Counter(stringResource(R.string.counter_sentence_types), state.diagnostics.sentenceTypeCount.toLong())
        Counter(stringResource(R.string.counter_raw_lines), state.diagnostics.rawPreviewCount.toLong())
        Counter(stringResource(R.string.counter_checksum_failures), state.diagnostics.checksumFailureCount)
        Counter(stringResource(R.string.counter_ingress_drops), state.diagnostics.ingressDropCount)
        Spacer(Modifier.height(18.dp))
        WpText(stringResource(R.string.sentence_inventory), 22, weight = FontWeight.Light)
        state.sentences.forEach { sentence ->
            WpText(
                "${sentence.sentenceId} · ${sentence.sourceName} · ${sentence.receivedCount} · ${sentenceStatusLabel(sentence.status)}",
                12,
                color = if (sentence.current) LocalWpTheme.current.foreground else LocalWpTheme.current.muted,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        WpText(stringResource(R.string.raw_input), 22, weight = FontWeight.Light)
        WpLiveConsole(
            newestFirstLines = state.rawLines.map { line ->
                "${line.sourceName}${line.sender?.let { " [$it]" }.orEmpty()}\n${line.raw}"
            },
            structuralKey = state.rawLines.firstOrNull()?.current,
            cadence = PresentationCadence.RawPreview,
            maxEntries = 20,
            modifier = Modifier.fillMaxWidth().testTag(DataTestTags.RAW),
        ) {
            WpText(stringResource(R.string.raw_empty), 13, color = LocalWpTheme.current.muted)
        }
    }
}

@Composable
private fun Counter(label: String, value: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        WpText(label, 12, color = LocalWpTheme.current.muted)
        WpLiveField(value.toString(), structuralKey = label, cadence = PresentationCadence.NmeaStatus, size = 12, minValueWidth = 60.dp)
    }
}

@Composable
private fun WpCommand(label: String, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    WpText(
        label,
        13,
        color = LocalWpTheme.current.accent,
        modifier = Modifier.heightIn(min = 44.dp)
            .clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

@Composable
private fun ScrollBody(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = YokuliMetrics.PageMargin, vertical = 8.dp),
        content = content,
    )
}

private fun DataUiState.firstResolved(vararg keys: DataKey): ResolvedDatum? =
    keys.firstNotNullOfOrNull(resolvedValues::get)

@Composable
private fun ResolvedDatum?.sourceLine(): String = this?.let {
    stringResource(R.string.source_line, source.connectionId.value, availabilityLabel(availability))
} ?: stringResource(R.string.no_selected_source)

private fun MarineValue?.formatted(): String = when (this) {
    null -> "—"
    is MarineValue.Position -> String.format(Locale.US, "%.5f°, %.5f°", latitudeDegrees, longitudeDegrees)
    is MarineValue.Decimal -> when (unit) {
        MarineUnit.DEGREES -> String.format(Locale.US, "%.1f°", value)
        MarineUnit.KNOTS -> String.format(Locale.US, "%.1f kn", value)
        MarineUnit.METERS -> String.format(Locale.US, "%.1f m", value)
        MarineUnit.DIMENSIONLESS -> String.format(Locale.US, "%.2f", value)
    }
    is MarineValue.Count -> value.toString()
    is MarineValue.UtcEpochMillis -> value.toString()
}

@Composable
private fun sectionContext(state: DataUiState): String = when (state.section) {
    DataSection.OVERVIEW -> stringResource(R.string.context_overview)
    DataSection.INPUTS -> stringResource(R.string.context_inputs, state.inputs.count { it.runIntent == com.yokuli.marine.data.connection.ConnectionRunIntent.ENABLED })
    DataSection.SOURCES -> stringResource(R.string.context_sources, state.groups.count { it.status == SourceGroupStatus.USING })
    DataSection.FLOW -> stringResource(R.string.context_flow, state.flow.size)
    DataSection.DIAGNOSTICS -> stringResource(R.string.context_diagnostics, state.diagnostics.sentenceTypeCount)
}

@Composable
private fun sectionLabel(section: DataSection): String = when (section) {
    DataSection.OVERVIEW -> stringResource(R.string.section_overview)
    DataSection.INPUTS -> stringResource(R.string.section_inputs)
    DataSection.SOURCES -> stringResource(R.string.section_sources)
    DataSection.FLOW -> stringResource(R.string.section_flow)
    DataSection.DIAGNOSTICS -> stringResource(R.string.section_diagnostics)
}

@Composable
private fun groupLabel(group: SourceGroup): String = when (group) {
    SourceGroup.POSITION_AND_MOTION -> stringResource(R.string.group_position_motion)
    SourceGroup.HEADING -> stringResource(R.string.group_heading)
    SourceGroup.DEPTH -> stringResource(R.string.group_depth)
    SourceGroup.WIND -> stringResource(R.string.group_wind)
}

@Composable
private fun groupStatusLabel(status: SourceGroupStatus): String = when (status) {
    SourceGroupStatus.NO_DATA -> stringResource(R.string.group_no_data)
    SourceGroupStatus.DISCOVERING -> stringResource(R.string.group_discovering)
    SourceGroupStatus.NEEDS_SELECTION -> stringResource(R.string.group_needs_selection)
    SourceGroupStatus.USING -> stringResource(R.string.group_using)
    SourceGroupStatus.SELECTED_UNAVAILABLE -> stringResource(R.string.group_unavailable)
    SourceGroupStatus.MIXED_LEGACY_SELECTION -> stringResource(R.string.group_mixed)
    SourceGroupStatus.DISABLED -> stringResource(R.string.group_disabled)
}

@Composable
private fun statusColor(status: SourceGroupStatus) = if (
    status in setOf(SourceGroupStatus.NEEDS_SELECTION, SourceGroupStatus.SELECTED_UNAVAILABLE, SourceGroupStatus.MIXED_LEGACY_SELECTION)
) LocalWpTheme.current.alarm else LocalWpTheme.current.accent

@Composable
private fun availabilityLabel(value: SourceCandidateAvailability): String = when (value) {
    SourceCandidateAvailability.LIVE -> stringResource(R.string.availability_live)
    SourceCandidateAvailability.HELD -> stringResource(R.string.availability_held)
    SourceCandidateAvailability.STALE -> stringResource(R.string.availability_stale)
    SourceCandidateAvailability.INVALID -> stringResource(R.string.availability_invalid)
    SourceCandidateAvailability.UNAVAILABLE -> stringResource(R.string.availability_unavailable)
    SourceCandidateAvailability.MISSING -> stringResource(R.string.availability_missing)
}

@Composable
private fun phoneActionLabel(state: DataUiState): String = when (state.phone.state) {
    PhoneLocationState.PERMISSION_REQUIRED -> if (state.phone.permission == PhoneLocationPermission.PERMANENTLY_DENIED) {
        stringResource(R.string.open_permission_settings)
    } else stringResource(R.string.grant_phone_permission)
    PhoneLocationState.SYSTEM_LOCATION_DISABLED -> stringResource(R.string.open_location_settings)
    PhoneLocationState.PLATFORM_RESTRICTED -> stringResource(R.string.retry_phone_location)
    else -> if (state.phoneDemand.required || state.phone.enabledByUser) {
        stringResource(R.string.waiting_for_phone)
    } else stringResource(R.string.use_phone_source)
}

@Composable
private fun noticeLabel(notice: DataNotice): String = when (notice) {
    DataNotice.SAVED -> stringResource(R.string.notice_saved)
    DataNotice.DISABLED -> stringResource(R.string.notice_disabled)
    DataNotice.CANDIDATE_UNAVAILABLE -> stringResource(R.string.notice_candidate_unavailable)
    DataNotice.PERSISTENCE_FAILED -> stringResource(R.string.notice_persistence_failed)
    DataNotice.PHONE_PERMISSION_REQUIRED -> stringResource(R.string.notice_phone_permission)
    DataNotice.PHONE_LOCATION_DISABLED -> stringResource(R.string.notice_phone_location_disabled)
    DataNotice.PHONE_PLATFORM_RESTRICTED -> stringResource(R.string.notice_phone_restricted)
    DataNotice.ACTION_QUEUE_FULL -> stringResource(R.string.notice_busy)
}

@Composable
private fun sentenceStatusLabel(status: SentenceParseStatus): String = when (status) {
    SentenceParseStatus.PARSED -> stringResource(R.string.sentence_parsed)
    SentenceParseStatus.EXPLICIT_INVALID -> stringResource(R.string.sentence_invalid)
    SentenceParseStatus.UNSUPPORTED -> stringResource(R.string.sentence_unsupported)
}
