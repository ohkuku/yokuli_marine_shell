package com.yokuli.marine.feature.datasources

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.PresentationCadence
import com.yokuli.marine.core.design.WpAppBarAction
import com.yokuli.marine.core.design.WpApplicationBar
import com.yokuli.marine.core.design.WpLiveConsole
import com.yokuli.marine.core.design.WpLiveField
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliMetrics
import com.yokuli.marine.core.design.wpEntrance
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import java.util.Locale

@Composable
fun DataSourcesWorkspace(
    state: DataSourcesUiState,
    onAction: (DataSourcesUiAction) -> Unit,
) {
    val colors = LocalWpTheme.current
    val currentState by rememberUpdatedState(state)
    val currentAction by rememberUpdatedState(onAction)
    BindInternalAppInputHandler { input ->
        if (input != ShellInput.BACK) return@BindInternalAppInputHandler false
        DataSourcesBackPolicy.actionFor(currentState.page)?.let {
            currentAction(it)
            true
        } ?: false
    }

    Column(Modifier.fillMaxSize().background(colors.background).testTag(DataSourcesTestTags.ROOT)) {
        WpPageHeader(
            appKey = "data-sources",
            appName = stringResource(R.string.data_sources_title),
            contextLine = stringResource(R.string.attention_summary, state.attentionCount),
        )
        state.notice?.let { NoticeLine(it) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val page = state.page) {
                is DataSourcesPageUi.Overview -> Overview(state, page, onAction)
                is DataSourcesPageUi.DataDetail -> DataDetail(page.row, onAction)
                is DataSourcesPageUi.SentenceDetail -> SentenceDetail(page.row, onAction)
            }
        }
        if (state.page !is DataSourcesPageUi.Overview) {
            WpApplicationBar(
                listOf(
                    WpAppBarAction(
                        symbol = "←",
                        label = stringResource(R.string.action_back),
                        testTag = "data-sources-back",
                        onClick = { onAction(DataSourcesUiAction.BackToOverview) },
                    ),
                ),
            )
        }
    }
}

@Composable
private fun Overview(
    state: DataSourcesUiState,
    page: DataSourcesPageUi.Overview,
    onAction: (DataSourcesUiAction) -> Unit,
) {
    ScrollBody {
        WpText(stringResource(R.string.catalog_explanation), 13, color = LocalWpTheme.current.muted)
        Spacer(Modifier.height(12.dp))
        PhoneSourceRow(state.phone, onAction)
        Spacer(Modifier.height(14.dp))
        ViewSwitch(state.viewMode, onAction)
        Spacer(Modifier.height(10.dp))
        SearchField(state.query, onAction)
        Spacer(Modifier.height(10.dp))
        FilterRow(state.filter, onAction)
        Spacer(Modifier.height(10.dp))
        val empty = when (state.viewMode) {
            DataSourcesViewMode.DATA -> page.dataRows.isEmpty()
            DataSourcesViewMode.SENTENCES -> page.sentenceRows.isEmpty()
        }
        if (empty) {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp).testTag(DataSourcesTestTags.EMPTY)) {
                WpText(stringResource(R.string.empty_title), 27, weight = FontWeight.Light)
                WpText(stringResource(R.string.empty_body), 13, color = LocalWpTheme.current.muted)
            }
        } else if (state.viewMode == DataSourcesViewMode.DATA) {
            page.dataRows.forEachIndexed { index, row -> DataRow(row, index, onAction) }
        } else {
            page.sentenceRows.forEachIndexed { index, row -> SentenceRow(row, index, onAction) }
        }
    }
}

@Composable
private fun PhoneSourceRow(phone: PhoneSourceSummaryUi, onAction: (DataSourcesUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            WpText(stringResource(R.string.phone_source_title), 21, weight = FontWeight.Light)
            WpText(phoneStateLabel(phone.state), 12, color = colors.muted)
        }
        when {
            !phone.enabledByUser -> TextCommand(
                stringResource(R.string.action_enable),
                DataSourcesTestTags.ENABLE_PHONE,
            ) { onAction(DataSourcesUiAction.EnablePhoneLocation) }
            phone.state == PhoneSourceUi.PERMISSION_REQUIRED -> TextCommand(
                if (phone.permission == com.yokuli.marine.data.phone.PhoneLocationPermission.PERMANENTLY_DENIED) {
                    stringResource(R.string.action_permission_settings)
                } else {
                    stringResource(R.string.action_grant_permission)
                },
                DataSourcesTestTags.ENABLE_PHONE,
            ) {
                onAction(
                    if (phone.permission == com.yokuli.marine.data.phone.PhoneLocationPermission.PERMANENTLY_DENIED) {
                        DataSourcesUiAction.ResolvePhoneLocation
                    } else {
                        DataSourcesUiAction.EnablePhoneLocation
                    },
                )
            }
            phone.state == PhoneSourceUi.SYSTEM_LOCATION_DISABLED -> TextCommand(
                stringResource(R.string.action_location_settings),
                DataSourcesTestTags.ENABLE_PHONE,
            ) { onAction(DataSourcesUiAction.ResolvePhoneLocation) }
            else -> TextCommand(
                stringResource(R.string.action_disable),
                DataSourcesTestTags.DISABLE_PHONE,
            ) { onAction(DataSourcesUiAction.DisablePhoneLocation) }
        }
    }
}

@Composable
private fun ViewSwitch(mode: DataSourcesViewMode, onAction: (DataSourcesUiAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        ModeText(
            stringResource(R.string.view_data),
            mode == DataSourcesViewMode.DATA,
            DataSourcesTestTags.DATA_VIEW,
        ) { onAction(DataSourcesUiAction.ChangeView(DataSourcesViewMode.DATA)) }
        ModeText(
            stringResource(R.string.view_sentences),
            mode == DataSourcesViewMode.SENTENCES,
            DataSourcesTestTags.SENTENCE_VIEW,
        ) { onAction(DataSourcesUiAction.ChangeView(DataSourcesViewMode.SENTENCES)) }
    }
}

@Composable
private fun ModeText(label: String, isSelected: Boolean, tag: String, onClick: () -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    WpText(
        label,
        17,
        color = if (isSelected) colors.accent else colors.muted,
        weight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.heightIn(min = YokuliMetrics.MinTouch)
            .clickable(interactions, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { selected = isSelected }
            .testTag(tag)
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun SearchField(query: String, onAction: (DataSourcesUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    val searchDescription = stringResource(R.string.search_content_description)
    BasicTextField(
        value = query,
        onValueChange = { onAction(DataSourcesUiAction.ChangeQuery(it.take(80))) },
        singleLine = true,
        textStyle = TextStyle(color = colors.foreground, fontSize = 17.sp, fontFamily = FontFamily.SansSerif),
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier.fillMaxWidth().heightIn(min = YokuliMetrics.MinTouch)
            .background(colors.foreground.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 11.dp)
            .semantics { contentDescription = searchDescription }
            .testTag(DataSourcesTestTags.SEARCH),
        decorationBox = { inner ->
            if (query.isEmpty()) WpText(stringResource(R.string.search_hint), 16, color = colors.muted)
            inner()
        },
    )
}

@Composable
private fun FilterRow(filter: DataSourcesFilter, onAction: (DataSourcesUiAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        FilterText(stringResource(R.string.filter_all), filter == DataSourcesFilter.All) {
            onAction(DataSourcesUiAction.ChangeFilter(DataSourcesFilter.All))
        }
        FilterText(stringResource(R.string.filter_attention), filter == DataSourcesFilter.NeedsAttention) {
            onAction(DataSourcesUiAction.ChangeFilter(DataSourcesFilter.NeedsAttention))
        }
        FilterText(stringResource(R.string.filter_multiple), filter == DataSourcesFilter.MultipleSources) {
            onAction(DataSourcesUiAction.ChangeFilter(DataSourcesFilter.MultipleSources))
        }
        FilterText(stringResource(R.string.filter_phone), filter == DataSourcesFilter.Phone) {
            onAction(DataSourcesUiAction.ChangeFilter(DataSourcesFilter.Phone))
        }
    }
}

@Composable
private fun FilterText(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    WpText(
        label,
        12,
        color = if (selected) colors.accent else colors.muted,
        modifier = Modifier.heightIn(min = 44.dp)
            .clickable(interactions, indication = null, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun DataRow(row: DataSourceRowUi, index: Int, onAction: (DataSourcesUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 86.dp).wpEntrance(row.key, index)
            .clickable(interactions, indication = null) { onAction(DataSourcesUiAction.OpenData(row.key)) }
            .padding(vertical = 10.dp)
            .testTag(DataSourcesTestTags.data(row.key.stableLabel())),
    ) {
        WpText(dataKeyLabel(row.key), 23, weight = FontWeight.Light)
        WpText(decisionLabel(row.decision.status), 13, color = if (row.needsAttention) colors.alarm else colors.accent)
        val sources = row.candidates.joinToString(" · ") { candidate -> candidate.sourceName }
        WpText(sources, 11, color = colors.muted, maxLines = 2)
        row.resolvedValue?.let {
            WpLiveField(
                value = formatValue(it),
                structuralKey = row.decision.status to row.needsAttention,
                cadence = PresentationCadence.DataOverview,
                size = 16,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun SentenceRow(row: SentenceRowUi, index: Int, onAction: (DataSourcesUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 82.dp).wpEntrance(row.key, index)
            .clickable(interactions, indication = null) { onAction(DataSourcesUiAction.OpenSentence(row.key)) }
            .padding(vertical = 10.dp)
            .testTag(DataSourcesTestTags.sentence(row.sentenceId)),
    ) {
        WpText(row.sentenceId, 23, weight = FontWeight.Light)
        WpText(sentenceMeaningLabel(row.meaning), 13, color = if (row.current) colors.accent else colors.alarm)
        WpLiveField(
            value = stringResource(R.string.sentence_source_count, row.sourceName, row.receivedCount),
            structuralKey = row.current to row.meaning,
            cadence = PresentationCadence.NmeaStatus,
            size = 11,
            color = colors.muted,
            minValueWidth = 180.dp,
        )
    }
}

@Composable
private fun DataDetail(row: DataSourceRowUi, onAction: (DataSourcesUiAction) -> Unit) {
    ScrollBody(Modifier.testTag(DataSourcesTestTags.DETAIL)) {
        WpText(dataKeyLabel(row.key), 32, weight = FontWeight.Light)
        WpText(decisionLabel(row.decision.status), 15, color = LocalWpTheme.current.accent)
        Spacer(Modifier.height(16.dp))
        row.candidates.forEachIndexed { index, candidate ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 10.dp).wpEntrance(candidate.source, index)
                    .testTag(DataSourcesTestTags.candidate(candidate.source.connectionId.value)),
            ) {
                WpText(
                    if (candidate.kind == com.yokuli.marine.data.source.SourceKind.PHONE_SYSTEM_LOCATION) {
                        stringResource(R.string.phone_source_title)
                    } else candidate.sourceName,
                    22,
                    weight = FontWeight.Light,
                )
                WpText(candidate.detail, 12, color = LocalWpTheme.current.muted)
                WpText(availabilityLabel(candidate.availability), 13, color = LocalWpTheme.current.muted)
                candidate.value?.let {
                    WpLiveField(
                        value = formatValue(it),
                        structuralKey = candidate.availability to candidate.selected,
                        cadence = PresentationCadence.DataOverview,
                        size = 18,
                    )
                }
                if (candidate.selected) WpText(stringResource(R.string.currently_used), 12, color = LocalWpTheme.current.accent)
                TextCommand(stringResource(R.string.action_use_source), "data-sources-use-${candidate.source.connectionId.value}") {
                    onAction(DataSourcesUiAction.UseSource(row.key, candidate.source))
                }
                if (candidate.kind == com.yokuli.marine.data.source.SourceKind.NMEA) {
                    TextCommand(stringResource(R.string.action_open_nmea), "data-sources-open-nmea-${candidate.source.connectionId.value}") {
                        onAction(DataSourcesUiAction.OpenNmeaInput(candidate.source.connectionId))
                    }
                }
            }
        }
        TextCommand(stringResource(R.string.action_disable_data), "data-sources-disable-data") {
            onAction(DataSourcesUiAction.DisableData(row.key))
        }
    }
}

@Composable
private fun SentenceDetail(row: SentenceRowUi, onAction: (DataSourcesUiAction) -> Unit) {
    ScrollBody(Modifier.testTag(DataSourcesTestTags.DETAIL)) {
        WpText(row.sentenceId, 32, weight = FontWeight.Light)
        WpText(row.sourceName, 15, color = LocalWpTheme.current.muted)
        WpText(sentenceMeaningLabel(row.meaning), 14, color = LocalWpTheme.current.accent)
        Spacer(Modifier.height(14.dp))
        WpLiveConsole(
            newestFirstLines = row.rawEvidence.map { it.raw },
            structuralKey = row.current to row.meaning,
            cadence = PresentationCadence.RawPreview,
            maxEntries = 20,
            modifier = Modifier.fillMaxWidth(),
        ) {
            WpText(stringResource(R.string.raw_empty), 13, color = LocalWpTheme.current.muted)
        }
        TextCommand(stringResource(R.string.action_open_nmea), "data-sources-open-nmea") {
            onAction(DataSourcesUiAction.OpenNmeaInput(row.key.source.connectionId))
        }
    }
}

@Composable
private fun TextCommand(label: String, tag: String, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    WpText(
        label,
        13,
        color = LocalWpTheme.current.accent,
        modifier = Modifier.heightIn(min = 44.dp)
            .clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .testTag(tag),
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

@Composable
private fun NoticeLine(notice: DataSourcesNotice) {
    WpText(noticeLabel(notice), 12, color = LocalWpTheme.current.accent, modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun dataKeyLabel(key: DataKey): String = when (key) {
    DataKey.Position -> stringResource(R.string.data_position)
    DataKey.SpeedOverGround -> stringResource(R.string.data_sog)
    DataKey.CourseOverGround -> stringResource(R.string.data_cog)
    is DataKey.Heading -> stringResource(R.string.data_heading, key.reference.name)
    is DataKey.Depth -> stringResource(R.string.data_depth, key.reference.name)
    is DataKey.WindAngle -> stringResource(R.string.data_wind_angle, key.reference.name)
    is DataKey.WindSpeed -> stringResource(R.string.data_wind_speed, key.reference.name)
    DataKey.MagneticVariation -> stringResource(R.string.data_magnetic_variation)
    DataKey.SourceTime -> stringResource(R.string.data_source_time)
    DataKey.FixQuality -> stringResource(R.string.data_fix_quality)
    DataKey.Satellites -> stringResource(R.string.data_satellites)
    DataKey.HorizontalDilution -> stringResource(R.string.data_hdop)
    DataKey.Altitude -> stringResource(R.string.data_altitude)
    DataKey.PositionAccuracy -> stringResource(R.string.data_accuracy)
}

@Composable
private fun decisionLabel(status: com.yokuli.marine.data.source.SourceDecisionStatus): String = when (status) {
    com.yokuli.marine.data.source.SourceDecisionStatus.NO_CANDIDATE -> stringResource(R.string.status_no_candidate)
    com.yokuli.marine.data.source.SourceDecisionStatus.DISCOVERING -> stringResource(R.string.status_discovering)
    com.yokuli.marine.data.source.SourceDecisionStatus.NEEDS_SELECTION -> stringResource(R.string.status_needs_selection)
    com.yokuli.marine.data.source.SourceDecisionStatus.USING -> stringResource(R.string.status_using)
    com.yokuli.marine.data.source.SourceDecisionStatus.SELECTED_UNAVAILABLE -> stringResource(R.string.status_interrupted)
    com.yokuli.marine.data.source.SourceDecisionStatus.SELECTED_MISSING -> stringResource(R.string.status_missing)
    com.yokuli.marine.data.source.SourceDecisionStatus.DISABLED -> stringResource(R.string.status_disabled)
}

@Composable
private fun phoneStateLabel(state: PhoneSourceUi): String = when (state) {
    PhoneSourceUi.DISABLED -> stringResource(R.string.phone_disabled)
    PhoneSourceUi.PERMISSION_REQUIRED -> stringResource(R.string.phone_permission)
    PhoneSourceUi.SYSTEM_LOCATION_DISABLED -> stringResource(R.string.phone_system_off)
    PhoneSourceUi.STARTING -> stringResource(R.string.phone_starting)
    PhoneSourceUi.RECEIVING -> stringResource(R.string.phone_receiving)
    PhoneSourceUi.INTERRUPTED -> stringResource(R.string.phone_interrupted)
    PhoneSourceUi.PLATFORM_RESTRICTED -> stringResource(R.string.phone_restricted)
}

@Composable
private fun sentenceMeaningLabel(meaning: SentenceMeaningUi): String = when (meaning) {
    SentenceMeaningUi.PARSED -> stringResource(R.string.sentence_parsed)
    SentenceMeaningUi.EXPLICIT_INVALID -> stringResource(R.string.sentence_invalid)
    SentenceMeaningUi.UNSUPPORTED -> stringResource(R.string.sentence_unsupported)
}

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
private fun noticeLabel(notice: DataSourcesNotice): String = when (notice) {
    DataSourcesNotice.SELECTION_SAVED -> stringResource(R.string.notice_selection_saved)
    DataSourcesNotice.SELECTION_DISABLED -> stringResource(R.string.notice_selection_disabled)
    DataSourcesNotice.SELECTION_SAVE_FAILED -> stringResource(R.string.notice_selection_failed)
    DataSourcesNotice.CANDIDATE_UNAVAILABLE -> stringResource(R.string.notice_candidate_unavailable)
    DataSourcesNotice.PHONE_LOCATION_ENABLED -> stringResource(R.string.notice_phone_enabled)
    DataSourcesNotice.PHONE_LOCATION_DISABLED -> stringResource(R.string.notice_phone_disabled)
    DataSourcesNotice.PHONE_LOCATION_UNAVAILABLE -> stringResource(R.string.notice_phone_unavailable)
    DataSourcesNotice.ACTION_QUEUE_FULL -> stringResource(R.string.notice_busy)
}

private fun formatValue(value: MarineValue): String = when (value) {
    is MarineValue.Position -> String.format(Locale.ROOT, "%.5f, %.5f", value.latitudeDegrees, value.longitudeDegrees)
    is MarineValue.Decimal -> String.format(Locale.ROOT, "%.2f %s", value.value, unit(value.unit))
    is MarineValue.Count -> value.value.toString()
    is MarineValue.UtcEpochMillis -> value.value.toString()
}

private fun unit(unit: MarineUnit): String = when (unit) {
    MarineUnit.DEGREES -> "°"
    MarineUnit.KNOTS -> "kn"
    MarineUnit.METERS -> "m"
    MarineUnit.DIMENSIONLESS -> ""
}
