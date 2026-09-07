package com.yokuli.marine.feature.data

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.YokuliMetrics
import com.yokuli.marine.core.design.wpEntrance
import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.phone.PhoneLocationState
import com.yokuli.marine.data.runtime.ConnectionTransportState
import com.yokuli.marine.data.runtime.NmeaRuntimeFailure
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.shell.compose.BindInternalAppInputHandler
import com.yokuli.shell.contract.ShellInput
import java.util.Locale

@Composable
fun DataWorkspace(state: DataUiState, onAction: (DataUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    val currentState by rememberUpdatedState(state)
    val currentAction by rememberUpdatedState(onAction)
    BindInternalAppInputHandler { input ->
        if (input != ShellInput.BACK) return@BindInternalAppInputHandler false
        DataBackPolicy.parent(currentState.surface)?.let { parent ->
            currentAction(parent.asAction())
            true
        } ?: false
    }
    Column(Modifier.fillMaxSize().background(colors.background).testTag(DataTestTags.ROOT)) {
        WpPageHeader("data", stringResource(R.string.data_title), surfaceContext(state))
        state.notice?.let { notice ->
            WpText(
                noticeLabel(notice), 12, color = colors.accent,
                modifier = Modifier.padding(horizontal = YokuliMetrics.PageMargin, vertical = 2.dp)
                    .clickable { onAction(DataUiAction.DismissNotice) },
            )
        }
        when (val surface = state.surface) {
            is DataSurface.Primary -> {
                PrimaryStrip(surface.area, onAction)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (surface.area) {
                        PrimaryDataArea.BOAT -> BoatOverview(state, onAction)
                        PrimaryDataArea.FLOW -> NervousSystemFlow(state, onAction)
                        PrimaryDataArea.CONNECTIONS -> Connections(state, onAction)
                    }
                }
            }
            is DataSurface.Sensor -> SensorDetail(state, surface.sensor, onAction)
            is DataSurface.Trust -> TrustDetail(state, surface.group, onAction)
            is DataSurface.Consumer -> ConsumerDetail(state, surface.consumerId, onAction)
            is DataSurface.Connection -> ConnectionDetail(state, surface.connectionId.value, onAction)
            is DataSurface.Diagnostics -> Diagnostics(state, surface.connectionId?.value)
            DataSurface.AddSource -> AddSource(onAction)
            is DataSurface.ConnectionWizard -> ConnectionWizard(state, surface.type, onAction)
        }
    }
}

@Composable
private fun PrimaryStrip(area: PrimaryDataArea, onAction: (DataUiAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = YokuliMetrics.PageMargin),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        PrimaryDataArea.entries.forEach { item ->
            val interaction = remember { MutableInteractionSource() }
            WpText(
                primaryLabel(item), 16,
                color = if (item == area) LocalWpTheme.current.accent else LocalWpTheme.current.muted,
                weight = if (item == area) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.heightIn(min = YokuliMetrics.MinTouch)
                    .clickable(interaction, indication = null, role = Role.Tab) { onAction(DataUiAction.NavigatePrimary(item)) }
                    .semantics { selected = item == area }.testTag(DataTestTags.primary(item)).padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun BoatOverview(state: DataUiState, onAction: (DataUiAction) -> Unit) {
    ScrollBody(Modifier.testTag(DataTestTags.BOAT)) {
        BoatScene(state.boat, onAction)
        WpText(stringResource(R.string.boat_senses), 12, color = LocalWpTheme.current.muted)
        state.boat.sensors.forEachIndexed { index, sensor ->
            SensorCard(sensor, index) { onAction(DataUiAction.OpenSensor(sensor.sensor)) }
        }
        val impacts = state.consumerImpact.filter { it.active && it.severity != ConsumerImpactSeverity.NONE }
        if (impacts.isNotEmpty()) {
            WpText(stringResource(R.string.consumer_impact_title), 22, weight = FontWeight.Light, modifier = Modifier.padding(top = 12.dp))
            impacts.forEach { impact ->
                WpText(
                    stringResource(
                        R.string.consumer_impact_line,
                        consumerLabel(impact.consumerId),
                        sensorListLabel(impact.affectedSensors),
                    ), 13,
                    color = if (impact.severity == ConsumerImpactSeverity.BLOCKED) LocalWpTheme.current.alarm else LocalWpTheme.current.warning,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            WpCommand(stringResource(R.string.action_add_source)) { onAction(DataUiAction.OpenAddSource) }
            WpCommand(stringResource(R.string.action_view_flow)) { onAction(DataUiAction.NavigatePrimary(PrimaryDataArea.FLOW)) }
        }
    }
}

@Composable
private fun BoatScene(boat: DataBoatOverview, onAction: (DataUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    val live = boat.sensors.any { it.health == SensorHealth.LIVE }
    val pulse by rememberInfiniteTransition(label = "boat-pulse").animateFloat(
        .18f, if (live) .52f else .18f,
        infiniteRepeatable(tween(1_800), RepeatMode.Reverse), label = "boat-pulse-alpha",
    )
    Box(
        Modifier.fillMaxWidth().height(276.dp).padding(vertical = 6.dp)
            .semantics { contentDescription = "marine vessel sensor overview" }.testTag(DataTestTags.VESSEL_SCENE),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width * .5f
            val cy = size.height * .52f
            drawCircle(colors.accent.copy(alpha = pulse * .24f), size.minDimension * .34f, Offset(cx, cy))
            drawCircle(colors.foreground.copy(alpha = .10f), size.minDimension * .31f, Offset(cx, cy), style = Stroke(1f))
            for (ring in 1..3) {
                drawOval(
                    colors.foreground.copy(alpha = .07f), Offset(size.width * .15f, cy + ring * 19f),
                    Size(size.width * .7f, 26f), style = Stroke(1.2f),
                )
            }
            val hull = Path().apply {
                moveTo(cx, cy - 82f); lineTo(cx + 54f, cy + 64f); lineTo(cx, cy + 92f)
                lineTo(cx - 54f, cy + 64f); close()
            }
            drawPath(hull, colors.accent.copy(alpha = .24f))
            drawPath(hull, colors.foreground.copy(alpha = .88f), style = Stroke(3f))
            drawLine(colors.foreground.copy(alpha = .75f), Offset(cx, cy - 58f), Offset(cx, cy + 68f), 2f)
            drawLine(colors.accent, Offset(cx, cy - 80f), Offset(cx, cy - 108f), 4f, StrokeCap.Round)
            drawLine(colors.accent.copy(alpha = .65f), Offset(cx, cy), Offset(cx + 70f, cy - 32f), 2f)
        }
        SceneHotspot(boat, BoatSensor.WIND, Modifier.align(Alignment.TopStart), onAction)
        SceneHotspot(boat, BoatSensor.HEADING, Modifier.align(Alignment.TopEnd), onAction)
        SceneHotspot(boat, BoatSensor.POSITION, Modifier.align(Alignment.BottomStart), onAction)
        SceneHotspot(boat, BoatSensor.DEPTH, Modifier.align(Alignment.BottomEnd), onAction)
    }
}

@Composable
private fun SceneHotspot(boat: DataBoatOverview, sensor: BoatSensor, modifier: Modifier, onAction: (DataUiAction) -> Unit) {
    val value = boat.sensors.single { it.sensor == sensor }
    val endAligned = sensor == BoatSensor.HEADING || sensor == BoatSensor.DEPTH
    Column(
        modifier.width(126.dp).heightIn(min = 64.dp).clickable(role = Role.Button) { onAction(DataUiAction.OpenSensor(sensor)) }.padding(7.dp),
        horizontalAlignment = if (endAligned) Alignment.End else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(8.dp).background(healthColor(value.health), CircleShape))
            WpText(sensorLabel(sensor), 11, color = LocalWpTheme.current.muted, weight = FontWeight.SemiBold)
        }
        WpText(sensorHeroValue(value), 18, weight = FontWeight.Light, maxLines = 1)
    }
}

@Composable
private fun SensorCard(sensor: BoatSensorState, index: Int, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).wpEntrance(sensor.sensor, index)
            .background(LocalWpTheme.current.foreground.copy(alpha = .045f)).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(healthColor(sensor.health), CircleShape))
            WpText(sensorLabel(sensor.sensor), 19, modifier = Modifier.padding(start = 9.dp).weight(1f), weight = FontWeight.Light)
            WpText(healthLabel(sensor.health), 11, color = healthColor(sensor.health), weight = FontWeight.SemiBold)
        }
        WpText(sensorHeroValue(sensor), 27, weight = FontWeight.Light, modifier = Modifier.padding(top = 7.dp))
        WpText(sensorTruthLine(sensor), 11, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun SensorDetail(state: DataUiState, sensor: BoatSensor, onAction: (DataUiAction) -> Unit) {
    val item = state.boat.sensors.single { it.sensor == sensor }
    ScrollBody(Modifier.testTag(DataTestTags.sensor(sensor))) {
        WpText(sensorLabel(sensor), 38, weight = FontWeight.Light)
        WpText(healthLabel(item.health), 13, color = healthColor(item.health), weight = FontWeight.SemiBold)
        WpText(sensorHeroValue(item), 34, weight = FontWeight.Light, modifier = Modifier.padding(top = 18.dp))
        WpText(sensorTruthLine(item), 13, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 5.dp))
        item.resolvedValues.drop(1).forEach { WpText("${keyLabel(it.key)}   ${it.value?.formatted().orEmpty()}", 15, modifier = Modifier.padding(top = 8.dp)) }
        WpText(stringResource(R.string.trust_title), 22, weight = FontWeight.Light, modifier = Modifier.padding(top = 24.dp))
        item.groups.forEach { group ->
            val groupState = state.groups.single { it.group == group }
            WpText("${groupLabel(group)} · ${groupStatusLabel(groupState.status)}", 13, color = statusColor(groupState.status))
            WpCommand(stringResource(R.string.action_review_trust)) { onAction(DataUiAction.OpenTrust(group)) }
        }
        WpCommand(stringResource(R.string.action_diagnostics)) { onAction(DataUiAction.OpenDiagnostics()) }
    }
}

@Composable
private fun TrustDetail(state: DataUiState, group: SourceGroup, onAction: (DataUiAction) -> Unit) {
    val item = state.groups.single { it.group == group }
    ScrollBody(Modifier.testTag(DataTestTags.group(group))) {
        WpText(stringResource(R.string.trust_title), 38, weight = FontWeight.Light)
        WpText(groupLabel(group), 22, weight = FontWeight.Light)
        WpText(stringResource(R.string.trust_explanation), 13, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 8.dp))
        WpText(groupStatusLabel(item.status), 13, color = statusColor(item.status), modifier = Modifier.padding(top = 14.dp))
        if (item.candidates.isEmpty()) {
            WpText(stringResource(R.string.trust_no_candidates), 24, weight = FontWeight.Light, modifier = Modifier.padding(top = 28.dp))
            WpCommand(stringResource(R.string.action_add_source)) { onAction(DataUiAction.OpenAddSource) }
        }
        item.candidates.forEachIndexed { index, candidate ->
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp).wpEntrance(candidate.source, index)
                    .background(LocalWpTheme.current.foreground.copy(alpha = .045f)).padding(14.dp),
            ) {
                WpText(candidate.displayName, 20, weight = FontWeight.Light)
                WpText(availabilityListLabel(candidate.evidence.availabilityByKey.values.distinct()), 11, color = LocalWpTheme.current.muted)
                if (candidate.source == item.selectedSource) {
                    WpText(stringResource(R.string.current_source), 12, color = LocalWpTheme.current.accent, weight = FontWeight.SemiBold)
                } else WpCommand(stringResource(R.string.use_source)) { onAction(DataUiAction.UseSource(group, candidate.source)) }
            }
        }
        if (group == SourceGroup.POSITION_AND_MOTION) WpCommand(stringResource(R.string.use_phone_source)) { onAction(DataUiAction.ChoosePhoneSource) }
        WpCommand(stringResource(R.string.disable_group)) { onAction(DataUiAction.DisableGroup(group)) }
    }
}

@Composable
private fun Connections(state: DataUiState, onAction: (DataUiAction) -> Unit) {
    ScrollBody(Modifier.testTag(DataTestTags.CONNECTIONS)) {
        WpText(stringResource(R.string.connections_intro), 13, color = LocalWpTheme.current.muted)
        WpCommand(stringResource(R.string.action_add_source)) { onAction(DataUiAction.OpenAddSource) }
        PhoneCapability(state, onAction)
        if (state.inputs.isEmpty()) {
            WpText(stringResource(R.string.connections_empty), 28, weight = FontWeight.Light, modifier = Modifier.padding(top = 30.dp))
            WpText(stringResource(R.string.connections_empty_detail), 13, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 8.dp))
        }
        state.inputs.forEachIndexed { index, input ->
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp).wpEntrance(input.id, index)
                    .background(LocalWpTheme.current.foreground.copy(alpha = .045f))
                    .clickable(role = Role.Button) { onAction(DataUiAction.OpenConnection(input.id)) }.padding(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(inputHealthColor(input.health), CircleShape))
                    WpText(input.displayName, 20, modifier = Modifier.padding(start = 9.dp).weight(1f), weight = FontWeight.Light)
                    WpText(inputHealthLabel(input.health), 11, color = inputHealthColor(input.health), weight = FontWeight.SemiBold)
                }
                WpText(connectionPlainStatus(input), 13, modifier = Modifier.padding(top = 7.dp))
                val provided = sensorListLabel(input.providedSensors)
                WpText(if (provided.isBlank()) stringResource(R.string.provides_waiting) else provided, 11, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
private fun PhoneCapability(state: DataUiState, onAction: (DataUiAction) -> Unit) {
    val color = when (state.phone.state) {
        PhoneLocationState.RECEIVING -> LocalWpTheme.current.safe
        PhoneLocationState.STARTING -> LocalWpTheme.current.warning
        PhoneLocationState.DISABLED_BY_USER -> LocalWpTheme.current.muted
        PhoneLocationState.PERMISSION_REQUIRED,
        PhoneLocationState.SYSTEM_LOCATION_DISABLED,
        PhoneLocationState.INTERRUPTED,
        PhoneLocationState.PLATFORM_RESTRICTED,
        -> LocalWpTheme.current.alarm
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp)
            .background(LocalWpTheme.current.foreground.copy(alpha = .045f))
            .clickable(role = Role.Button) { onAction(DataUiAction.OpenSensor(BoatSensor.POSITION)) }
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            WpText(stringResource(R.string.source_phone), 20, modifier = Modifier.padding(start = 9.dp).weight(1f), weight = FontWeight.Light)
            WpText(phoneStatusLabel(state), 11, color = color, weight = FontWeight.SemiBold)
        }
        WpText(stringResource(R.string.phone_is_builtin), 13, modifier = Modifier.padding(top = 7.dp))
        if (!state.phoneDemand.required) {
            WpCommand(stringResource(R.string.use_phone_source)) { onAction(DataUiAction.ChoosePhoneSource) }
        }
    }
}

@Composable
private fun AddSource(onAction: (DataUiAction) -> Unit) {
    ScrollBody(Modifier.testTag(DataTestTags.ADD_SOURCE)) {
        WpText(stringResource(R.string.add_source_title), 38, weight = FontWeight.Light)
        WpText(stringResource(R.string.add_source_intro), 13, color = LocalWpTheme.current.muted)
        SourceChoice("⌁", R.string.source_boat_gateway, R.string.source_boat_gateway_detail) { onAction(DataUiAction.ChooseConnectionType(DataConnectionType.BOAT_GATEWAY)) }
        SourceChoice("◉", R.string.source_udp, R.string.source_udp_detail) { onAction(DataUiAction.ChooseConnectionType(DataConnectionType.UDP_BROADCAST)) }
        SourceChoice("▯", R.string.source_phone, R.string.source_phone_detail) { onAction(DataUiAction.ChoosePhoneSource) }
        SourceChoice("…", R.string.source_advanced, R.string.source_advanced_detail) { onAction(DataUiAction.ChooseConnectionType(DataConnectionType.ADVANCED_TCP)) }
    }
}

@Composable
private fun SourceChoice(symbol: String, title: Int, detail: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 76.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).border(2.dp, LocalWpTheme.current.foreground, CircleShape), contentAlignment = Alignment.Center) { WpText(symbol, 22) }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            WpText(stringResource(title), 20, weight = FontWeight.Light)
            WpText(stringResource(detail), 12, color = LocalWpTheme.current.muted)
        }
    }
}

@Composable
private fun ConnectionWizard(state: DataUiState, type: DataConnectionType, onAction: (DataUiAction) -> Unit) {
    val draft = state.connectionDraft ?: return
    ScrollBody(Modifier.testTag(DataTestTags.CONNECTION_WIZARD)) {
        WpText(connectionTypeLabel(type), 38, weight = FontWeight.Light)
        WpText(stringResource(R.string.connection_wizard_intro), 13, color = LocalWpTheme.current.muted)
        if (type == DataConnectionType.ADVANCED_TCP || type == DataConnectionType.ADVANCED_UDP) {
            WpText(stringResource(R.string.field_name_optional), 11, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 18.dp))
            WpField(draft.customName, stringResource(R.string.name_auto_hint)) { onAction(DataUiAction.ChangeConnectionName(it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 10.dp)) {
                WpCommand(stringResource(R.string.transport_tcp)) { onAction(DataUiAction.ChangeConnectionType(DataConnectionType.ADVANCED_TCP)) }
                WpCommand(stringResource(R.string.transport_udp)) { onAction(DataUiAction.ChangeConnectionType(DataConnectionType.ADVANCED_UDP)) }
            }
        }
        if (type == DataConnectionType.BOAT_GATEWAY || type == DataConnectionType.ADVANCED_TCP) {
            WpText(stringResource(R.string.field_host), 11, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 18.dp))
            WpField(draft.host, "192.168.4.1") { onAction(DataUiAction.ChangeConnectionHost(it)) }
        }
        WpText(stringResource(R.string.field_port), 11, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 16.dp))
        WpField(draft.portText, "10110") { onAction(DataUiAction.ChangeConnectionPort(it)) }
        WpText(connectionTestTitle(state.connectionTest), 22, weight = FontWeight.Light, modifier = Modifier.padding(top = 24.dp))
        WpText(connectionTestDetail(state.connectionTest), 13, color = connectionTestColor(state.connectionTest))
        state.connectionTest.detectedSensors.forEach { WpText("✓  ${sensorLabel(it)}", 16, color = LocalWpTheme.current.safe, modifier = Modifier.padding(top = 5.dp)) }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            when (state.connectionTest.phase) {
                ConnectionTestPhase.DETECTED -> WpCommand(stringResource(R.string.action_use_source)) { onAction(DataUiAction.UseTestedConnection) }
                ConnectionTestPhase.PROBING, ConnectionTestPhase.SAVING -> Unit
                else -> WpCommand(stringResource(R.string.action_test_source)) { onAction(DataUiAction.TestConnection) }
            }
            WpCommand(stringResource(R.string.action_cancel)) { onAction(DataUiAction.CancelConnectionSetup) }
        }
    }
}

@Composable
private fun WpField(value: String, hint: String, onValueChange: (String) -> Unit) {
    val colors = LocalWpTheme.current
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, colors.muted).padding(horizontal = 12.dp, vertical = 11.dp)) {
        if (value.isEmpty()) WpText(hint, 16, color = colors.muted)
        BasicTextField(value, onValueChange, Modifier.fillMaxWidth(), textStyle = TextStyle(colors.foreground, fontSize = 17.sp), singleLine = true)
    }
}

@Composable
private fun ConnectionDetail(state: DataUiState, id: String, onAction: (DataUiAction) -> Unit) {
    val input = state.inputs.firstOrNull { it.id.value == id }
    if (input == null) { ScrollBody { WpText(stringResource(R.string.connection_missing), 28, weight = FontWeight.Light) }; return }
    ScrollBody(Modifier.testTag(DataTestTags.connection(id))) {
        WpText(input.displayName, 38, weight = FontWeight.Light)
        WpText(inputHealthLabel(input.health), 13, color = inputHealthColor(input.health), weight = FontWeight.SemiBold)
        WpText(connectionPlainStatus(input), 20, weight = FontWeight.Light, modifier = Modifier.padding(top = 16.dp))
        WpText(stringResource(R.string.provides_title), 11, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 18.dp))
        val provided = sensorListLabel(input.providedSensors)
        WpText(if (provided.isBlank()) stringResource(R.string.provides_waiting) else provided, 17)
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (input.runIntent == ConnectionRunIntent.ENABLED) WpCommand(stringResource(R.string.action_stop)) { onAction(DataUiAction.StopConnection(input.id)) }
            else WpCommand(stringResource(R.string.action_start)) { onAction(DataUiAction.StartConnection(input.id)) }
            if (input.health in setOf(DataInputHealth.ATTENTION, DataInputHealth.INTERRUPTED)) WpCommand(stringResource(R.string.action_retest)) { onAction(DataUiAction.RetryConnection(input.id)) }
            WpCommand(stringResource(R.string.action_edit)) { onAction(DataUiAction.EditConnection(input.id)) }
        }
        WpText(stringResource(R.string.technical_details), 22, weight = FontWeight.Light, modifier = Modifier.padding(top = 28.dp))
        WpText(endpointLabel(input.endpoint), 13, color = LocalWpTheme.current.muted)
        WpText(stringResource(R.string.rate_line, input.framesPerSecond), 13, color = LocalWpTheme.current.muted)
        WpCommand(stringResource(R.string.action_diagnostics)) { onAction(DataUiAction.OpenDiagnostics(input.id)) }
        WpCommand(stringResource(R.string.action_remove_source), alarm = true) { onAction(DataUiAction.DeleteConnection(input.id)) }
    }
}

@Composable
private fun NervousSystemFlow(state: DataUiState, onAction: (DataUiAction) -> Unit) {
    ScrollBody(Modifier.testTag(DataTestTags.FLOW)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WpText(stringResource(R.string.flow_scene_title), 28, weight = FontWeight.Light, modifier = Modifier.weight(1f))
            WpCommand(if (state.flowExpert) stringResource(R.string.flow_simple) else stringResource(R.string.flow_expert)) { onAction(DataUiAction.ToggleFlowExpert) }
        }
        WpText(stringResource(R.string.flow_scene_intro), 13, color = LocalWpTheme.current.muted)
        FlowScene(state, onAction)
        if (state.flowExpert) {
            WpText(stringResource(R.string.expert_evidence), 22, weight = FontWeight.Light, modifier = Modifier.padding(top = 22.dp))
            state.inputs.forEach { WpText("${it.displayName} · ${endpointLabel(it.endpoint)} · ${String.format(Locale.US, "%.1f/s", it.framesPerSecond)}", 13) }
            WpCommand(stringResource(R.string.action_diagnostics)) { onAction(DataUiAction.OpenDiagnostics()) }
        }
    }
}

@Composable
private fun FlowScene(state: DataUiState, onAction: (DataUiAction) -> Unit) {
    val colors = LocalWpTheme.current
    val sourceNames = linkedMapOf<SourceIdentity, String>()
    state.inputs.forEach { sourceNames[SourceIdentity(it.id)] = it.displayName }
    state.flow.forEach { sourceNames[it.source] = it.sourceDisplayName }
    val sources = sourceNames.entries.toList()
    val sensors = state.boat.sensors
    val consumers = state.consumerImpact.filter { it.active }
    Box(Modifier.fillMaxWidth().height(410.dp).padding(top = 16.dp).testTag(DataTestTags.FLOW_SCENE)) {
        Canvas(Modifier.fillMaxSize()) {
            fun nodeY(index: Int, count: Int): Float = if (count <= 1) {
                size.height * .5f
            } else {
                val usable = size.height - 96f
                48f + usable * index / (count - 1).toFloat()
            }
            val left = size.width * .14f
            val channel = size.width * .48f
            val resolver = size.width * .71f
            val right = size.width * .90f
            state.flow.groupBy { it.source to sensorFor(it.group) }.forEach { (identity, links) ->
                val sourceIndex = sources.indexOfFirst { it.key == identity.first }
                val sensorIndex = sensors.indexOfFirst { it.sensor == identity.second }
                if (sourceIndex >= 0 && sensorIndex >= 0) {
                    val selected = links.any { it.selectedForOutput }
                    val available = links.any {
                        it.health in setOf(SourceCandidateAvailability.LIVE, SourceCandidateAvailability.HELD)
                    }
                    drawLine(
                        color = if (selected) colors.accent.copy(alpha = .9f) else colors.muted.copy(alpha = .24f),
                        start = Offset(left, nodeY(sourceIndex, sources.size)),
                        end = Offset(channel, nodeY(sensorIndex, sensors.size)),
                        strokeWidth = if (selected) 4f else 2f,
                        pathEffect = if (available) null else PathEffect.dashPathEffect(floatArrayOf(9f, 7f)),
                    )
                }
            }
            sensors.forEachIndexed { sensorIndex, sensor ->
                val active = sensor.selectedSource != null &&
                    sensor.health !in setOf(SensorHealth.UNAVAILABLE, SensorHealth.NEEDS_ATTENTION)
                drawLine(
                    if (active) colors.accent.copy(alpha = .72f) else colors.muted.copy(alpha = .18f),
                    Offset(channel, nodeY(sensorIndex, sensors.size)),
                    Offset(resolver, size.height * .5f),
                    if (active) 3.5f else 1.5f,
                )
            }
            consumers.forEachIndexed { index, consumer ->
                drawLine(
                    if (consumer.severity == ConsumerImpactSeverity.BLOCKED) colors.alarm else colors.accent.copy(alpha = .6f),
                    Offset(resolver, size.height * .5f),
                    Offset(right, nodeY(index, consumers.size)),
                    if (consumer.severity == ConsumerImpactSeverity.BLOCKED) 4f else 2.5f,
                )
            }
            drawCircle(colors.background, 20f, Offset(resolver, size.height * .5f))
            drawCircle(colors.accent, 18f, Offset(resolver, size.height * .5f), style = Stroke(3f))
        }
        FlowColumn(stringResource(R.string.flow_inputs), Modifier.align(Alignment.CenterStart).width(112.dp)) {
            sources.take(6).forEach { source ->
                val links = state.flow.filter { it.source == source.key }
                FlowNode(
                    source.value,
                    links.any { it.selectedForOutput },
                    if (links.any { it.selectedForOutput }) stringResource(R.string.current_source)
                    else stringResource(R.string.flow_standby),
                ) { onAction(DataUiAction.InspectFlowSource(source.key)) }
            }
            if (sources.isEmpty()) WpText(stringResource(R.string.flow_no_input), 13, color = colors.muted)
        }
        FlowColumn(stringResource(R.string.flow_channels), Modifier.align(Alignment.Center).width(126.dp)) {
            sensors.forEach { sensor -> FlowNode(sensorLabel(sensor.sensor), sensor.health in setOf(SensorHealth.LIVE, SensorHealth.HELD)) { onAction(DataUiAction.OpenSensor(sensor.sensor)) } }
        }
        FlowColumn(stringResource(R.string.flow_consumers), Modifier.align(Alignment.CenterEnd).width(118.dp), Alignment.End) {
            consumers.forEach { consumer ->
                FlowNode(consumerLabel(consumer.consumerId), consumer.severity != ConsumerImpactSeverity.BLOCKED) {
                    onAction(DataUiAction.OpenConsumer(consumer.consumerId))
                }
            }
            if (consumers.isEmpty()) WpText(stringResource(R.string.flow_no_consumer), 12, color = colors.muted)
        }
        WpText(stringResource(R.string.flow_os_truth), 10, color = colors.accent, weight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center).offset(x = 78.dp, y = (-36).dp))
    }
}

@Composable
private fun FlowColumn(label: String, modifier: Modifier, alignment: Alignment.Horizontal = Alignment.Start, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxHeight(), horizontalAlignment = alignment) {
        WpText(label, 10, color = LocalWpTheme.current.muted, weight = FontWeight.SemiBold)
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = alignment,
            content = content,
        )
    }
}

@Composable
private fun FlowNode(label: String, active: Boolean, detail: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(if (active) LocalWpTheme.current.accent else LocalWpTheme.current.stale, CircleShape))
        Column(Modifier.padding(start = 7.dp).weight(1f)) {
            WpText(label, 12, maxLines = 2)
            detail?.let { WpText(it, 9, color = LocalWpTheme.current.muted, maxLines = 1) }
        }
    }
}

@Composable
private fun ConsumerDetail(state: DataUiState, consumerId: MarineConsumerId, onAction: (DataUiAction) -> Unit) {
    val registration = MarineConsumerRegistry.registrations.single { it.id == consumerId }
    val impact = state.consumerImpact.single { it.consumerId == consumerId }
    ScrollBody {
        WpText(consumerLabel(consumerId), 38, weight = FontWeight.Light)
        WpText(
            stringResource(if (impact.active) R.string.consumer_active else R.string.consumer_inactive),
            13,
            color = if (impact.active) LocalWpTheme.current.accent else LocalWpTheme.current.muted,
        )
        if (registration.requiredSensors.isNotEmpty()) {
            WpText(stringResource(R.string.consumer_required), 11, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 22.dp))
            registration.requiredSensors.forEach { sensor ->
                WpCommand(sensorLabel(sensor)) { onAction(DataUiAction.OpenSensor(sensor)) }
            }
        }
        if (registration.optionalSensors.isNotEmpty()) {
            WpText(stringResource(R.string.consumer_optional), 11, color = LocalWpTheme.current.muted, modifier = Modifier.padding(top = 22.dp))
            registration.optionalSensors.forEach { sensor ->
                WpCommand(sensorLabel(sensor)) { onAction(DataUiAction.OpenSensor(sensor)) }
            }
        }
        WpText(
            if (impact.affectedSensors.isEmpty()) stringResource(R.string.consumer_no_impact)
            else stringResource(R.string.consumer_affected, sensorListLabel(impact.affectedSensors)),
            15,
            color = when (impact.severity) {
                ConsumerImpactSeverity.NONE -> LocalWpTheme.current.safe
                ConsumerImpactSeverity.DEGRADED -> LocalWpTheme.current.warning
                ConsumerImpactSeverity.BLOCKED -> LocalWpTheme.current.alarm
            },
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

@Composable
private fun Diagnostics(state: DataUiState, connectionId: String?) {
    val names = state.inputs.filter { connectionId == null || it.id.value == connectionId }.map { it.displayName }.toSet()
    val sentences = state.sentences.filter { connectionId == null || it.sourceName in names }
    val raw = state.rawLines.filter { connectionId == null || it.sourceName in names }
    ScrollBody(Modifier.testTag(DataTestTags.DIAGNOSTICS)) {
        WpText(stringResource(R.string.diagnostics_title), 38, weight = FontWeight.Light)
        WpText(stringResource(R.string.diagnostics_explanation), 13, color = LocalWpTheme.current.muted)
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            DiagnosticCounter(R.string.counter_sentence_types, state.diagnostics.sentenceTypeCount)
            DiagnosticCounter(R.string.counter_checksum_failures, state.diagnostics.checksumFailureCount)
            DiagnosticCounter(R.string.counter_ingress_drops, state.diagnostics.ingressDropCount)
        }
        WpText(stringResource(R.string.sentence_inventory), 22, weight = FontWeight.Light, modifier = Modifier.padding(top = 24.dp))
        sentences.take(40).forEach { WpText("${it.sentenceId}  ${it.formatter}  ${it.status}", 12, color = LocalWpTheme.current.muted) }
        WpText(stringResource(R.string.raw_input), 22, weight = FontWeight.Light, modifier = Modifier.padding(top = 22.dp))
        if (raw.isEmpty()) WpText(stringResource(R.string.raw_empty), 13, color = LocalWpTheme.current.muted)
        raw.takeLast(80).forEach { WpText(it.raw, 11, color = LocalWpTheme.current.muted, maxLines = 2) }
    }
}

@Composable
private fun DiagnosticCounter(label: Int, value: Number) { Column { WpText(value.toString(), 24, weight = FontWeight.Light); WpText(stringResource(label), 9, color = LocalWpTheme.current.muted) } }

@Composable
private fun ScrollBody(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = YokuliMetrics.PageMargin, vertical = 8.dp), content = content)
}

@Composable
private fun WpCommand(label: String, alarm: Boolean = false, onClick: () -> Unit) {
    WpText(
        label.uppercase(), 12, color = if (alarm) LocalWpTheme.current.alarm else LocalWpTheme.current.accent, weight = FontWeight.SemiBold,
        modifier = Modifier.heightIn(min = YokuliMetrics.MinTouch).clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
    )
}

private fun DataSurface.asAction(): DataUiAction = when (this) {
    is DataSurface.Primary -> DataUiAction.NavigatePrimary(area)
    is DataSurface.Sensor -> DataUiAction.OpenSensor(sensor)
    is DataSurface.Trust -> DataUiAction.OpenTrust(group)
    is DataSurface.Consumer -> DataUiAction.OpenConsumer(consumerId)
    is DataSurface.Connection -> DataUiAction.OpenConnection(connectionId)
    is DataSurface.Diagnostics -> DataUiAction.OpenDiagnostics(connectionId)
    DataSurface.AddSource -> DataUiAction.OpenAddSource
    is DataSurface.ConnectionWizard -> DataUiAction.ChooseConnectionType(type)
}

@Composable private fun surfaceContext(state: DataUiState): String = when (val s = state.surface) {
    is DataSurface.Primary -> when (s.area) {
        PrimaryDataArea.BOAT -> stringResource(R.string.context_boat)
        PrimaryDataArea.FLOW -> stringResource(R.string.context_nervous_system)
        PrimaryDataArea.CONNECTIONS -> stringResource(R.string.context_connections, state.inputs.count { it.runIntent == ConnectionRunIntent.ENABLED })
    }
    is DataSurface.Sensor -> sensorLabel(s.sensor)
    is DataSurface.Trust -> stringResource(R.string.context_trust)
    is DataSurface.Consumer -> consumerLabel(s.consumerId)
    is DataSurface.Connection -> stringResource(R.string.context_connection)
    is DataSurface.Diagnostics -> stringResource(R.string.context_diagnostics, state.diagnostics.sentenceTypeCount)
    DataSurface.AddSource -> stringResource(R.string.context_add_source)
    is DataSurface.ConnectionWizard -> connectionTypeLabel(s.type)
}

@Composable private fun primaryLabel(v: PrimaryDataArea) = stringResource(when (v) { PrimaryDataArea.BOAT -> R.string.primary_boat; PrimaryDataArea.FLOW -> R.string.primary_flow; PrimaryDataArea.CONNECTIONS -> R.string.primary_connections })
@Composable private fun sensorLabel(v: BoatSensor) = stringResource(when (v) { BoatSensor.POSITION -> R.string.sensor_position; BoatSensor.HEADING -> R.string.sensor_heading; BoatSensor.DEPTH -> R.string.sensor_depth; BoatSensor.WIND -> R.string.sensor_wind })
@Composable private fun sensorListLabel(values: Collection<BoatSensor>): String {
    val labels = mutableListOf<String>()
    values.forEach { labels += sensorLabel(it) }
    return labels.joinToString(" · ")
}
@Composable private fun consumerLabel(v: MarineConsumerId) = stringResource(when (v) { MarineConsumerId.CHART -> R.string.consumer_chart; MarineConsumerId.NAVIGATION -> R.string.consumer_navigation; MarineConsumerId.START_TILE -> R.string.consumer_start_tile })
@Composable private fun healthLabel(v: SensorHealth) = stringResource(when (v) { SensorHealth.LIVE -> R.string.health_live; SensorHealth.HELD -> R.string.health_last_reliable; SensorHealth.STALE -> R.string.health_stale; SensorHealth.UNAVAILABLE -> R.string.health_unavailable; SensorHealth.NEEDS_ATTENTION -> R.string.health_attention })
@Composable private fun groupLabel(v: SourceGroup) = stringResource(when (v) { SourceGroup.POSITION_AND_MOTION -> R.string.group_position_motion; SourceGroup.HEADING -> R.string.group_heading; SourceGroup.DEPTH -> R.string.group_depth; SourceGroup.APPARENT_WIND -> R.string.group_apparent_wind; SourceGroup.TRUE_WIND -> R.string.group_true_wind })
@Composable private fun groupStatusLabel(v: SourceGroupStatus) = stringResource(when (v) { SourceGroupStatus.NO_DATA -> R.string.group_no_data; SourceGroupStatus.DISCOVERING -> R.string.group_discovering; SourceGroupStatus.NEEDS_SELECTION -> R.string.group_needs_selection; SourceGroupStatus.USING -> R.string.group_using; SourceGroupStatus.SELECTED_UNAVAILABLE -> R.string.group_unavailable; SourceGroupStatus.MIXED_LEGACY_SELECTION -> R.string.group_mixed; SourceGroupStatus.DISABLED -> R.string.group_disabled })
@Composable private fun availabilityLabel(v: SourceCandidateAvailability) = stringResource(when (v) { SourceCandidateAvailability.LIVE -> R.string.availability_live; SourceCandidateAvailability.HELD -> R.string.availability_held; SourceCandidateAvailability.STALE -> R.string.availability_stale; SourceCandidateAvailability.INVALID -> R.string.availability_invalid; SourceCandidateAvailability.UNAVAILABLE -> R.string.availability_unavailable; SourceCandidateAvailability.MISSING -> R.string.availability_missing })
@Composable private fun availabilityListLabel(values: Collection<SourceCandidateAvailability>): String {
    val labels = mutableListOf<String>()
    values.forEach { labels += availabilityLabel(it) }
    return labels.joinToString(" · ")
}
@Composable private fun inputHealthLabel(v: DataInputHealth) = stringResource(when (v) { DataInputHealth.STOPPED -> R.string.connection_stopped; DataInputHealth.WAITING -> R.string.connection_starting; DataInputHealth.LISTENING -> R.string.connection_listening; DataInputHealth.RECEIVING -> R.string.connection_receiving; DataInputHealth.INTERRUPTED -> R.string.connection_interrupted; DataInputHealth.ATTENTION -> R.string.connection_attention })

@Composable private fun phoneStatusLabel(state: DataUiState): String = stringResource(when {
    state.phoneDemand.required && state.phone.state == PhoneLocationState.RECEIVING -> R.string.phone_status_in_use
    state.phone.state == PhoneLocationState.STARTING -> R.string.phone_status_starting
    state.phone.state == PhoneLocationState.PERMISSION_REQUIRED -> R.string.phone_status_permission
    state.phone.state == PhoneLocationState.SYSTEM_LOCATION_DISABLED -> R.string.phone_status_location_off
    state.phone.state == PhoneLocationState.INTERRUPTED -> R.string.phone_status_interrupted
    state.phone.state == PhoneLocationState.PLATFORM_RESTRICTED -> R.string.phone_status_restricted
    else -> R.string.phone_status_available
})

@Composable
private fun sensorHeroValue(sensor: BoatSensorState): String {
    val value = when (sensor.sensor) {
        BoatSensor.POSITION -> sensor.resolvedValues.firstOrNull { it.key == DataKey.Position }?.value
        BoatSensor.HEADING -> sensor.resolvedValues.firstOrNull { it.key is DataKey.Heading }?.value
        BoatSensor.DEPTH -> sensor.resolvedValues.firstOrNull { it.key is DataKey.Depth }?.value
        BoatSensor.WIND -> sensor.resolvedValues.firstOrNull { it.key is DataKey.WindSpeed }?.value ?: sensor.resolvedValues.firstOrNull { it.key is DataKey.WindAngle }?.value
    }
    return value?.formatted() ?: stringResource(R.string.value_unavailable)
}

@Composable
private fun sensorTruthLine(sensor: BoatSensorState): String {
    val source = sensor.sourceDisplayName ?: stringResource(R.string.no_selected_source)
    val age = sensor.youngestAgeMillis?.let { if (it < 1_000L) stringResource(R.string.freshness_now) else stringResource(R.string.freshness_seconds, it / 1_000L) }
    return listOfNotNull(source, age).joinToString(" · ")
}

@Composable
private fun connectionPlainStatus(input: DataInputState): String = when {
    input.failure != null -> runtimeFailureLabel(input.failure)
    input.health == DataInputHealth.RECEIVING -> stringResource(R.string.connection_plain_receiving)
    input.transport == ConnectionTransportState.TcpConnected -> stringResource(R.string.connection_plain_tcp_no_data)
    input.transport == ConnectionTransportState.UdpListening -> stringResource(R.string.connection_plain_udp_no_data)
    input.transport == ConnectionTransportState.WaitingForNetwork -> stringResource(R.string.connection_plain_no_network)
    input.health == DataInputHealth.STOPPED -> stringResource(R.string.connection_plain_stopped)
    else -> stringResource(R.string.connection_plain_starting)
}

@Composable private fun runtimeFailureLabel(v: NmeaRuntimeFailure) = stringResource(when (v) { is NmeaRuntimeFailure.UdpAddressInUse -> R.string.failure_udp_port; NmeaRuntimeFailure.PersistenceFailed -> R.string.failure_persistence; is NmeaRuntimeFailure.InvalidConfiguration -> R.string.failure_invalid; is NmeaRuntimeFailure.DuplicateTcpEndpoint -> R.string.failure_duplicate; NmeaRuntimeFailure.ActiveConnectionCapacityExceeded -> R.string.failure_capacity; NmeaRuntimeFailure.ConnectionNotFound -> R.string.failure_missing; NmeaRuntimeFailure.StaleSession -> R.string.failure_stale_session; NmeaRuntimeFailure.PlatformRestricted -> R.string.failure_platform; NmeaRuntimeFailure.IngressOverloaded -> R.string.failure_overload; is NmeaRuntimeFailure.TransportFailed -> R.string.failure_transport; NmeaRuntimeFailure.InternalError -> R.string.failure_internal })
@Composable private fun connectionTypeLabel(v: DataConnectionType) = stringResource(when (v) { DataConnectionType.BOAT_GATEWAY -> R.string.source_boat_gateway; DataConnectionType.UDP_BROADCAST -> R.string.source_udp; DataConnectionType.ADVANCED_TCP, DataConnectionType.ADVANCED_UDP -> R.string.source_advanced })
@Composable private fun connectionTestTitle(v: DataConnectionTestState) = stringResource(when (v.phase) { ConnectionTestPhase.NOT_STARTED -> R.string.test_ready; ConnectionTestPhase.PROBING -> R.string.test_starting; ConnectionTestPhase.NO_SEMANTIC_DATA -> R.string.test_no_semantic_data; ConnectionTestPhase.DETECTED -> R.string.test_detected; ConnectionTestPhase.SAVING -> R.string.test_saving; ConnectionTestPhase.FAILED -> R.string.test_failed; ConnectionTestPhase.INVALID_CONFIGURATION -> R.string.test_invalid })
@Composable private fun connectionTestDetail(v: DataConnectionTestState) = when (v.phase) { ConnectionTestPhase.NOT_STARTED -> stringResource(R.string.test_ready_detail); ConnectionTestPhase.PROBING -> stringResource(R.string.test_starting_detail); ConnectionTestPhase.NO_SEMANTIC_DATA -> if (v.transportReady) stringResource(R.string.test_transport_only_detail, v.legalFrameCount) else stringResource(R.string.test_no_transport_detail); ConnectionTestPhase.DETECTED -> stringResource(R.string.test_detected_detail); ConnectionTestPhase.SAVING -> stringResource(R.string.test_saving_detail); ConnectionTestPhase.FAILED -> v.failure?.let { runtimeFailureLabel(it) } ?: stringResource(R.string.test_failed_detail); ConnectionTestPhase.INVALID_CONFIGURATION -> stringResource(R.string.test_invalid_detail) }
@Composable private fun endpointLabel(v: NmeaEndpoint) = when (v) { is NmeaEndpoint.TcpClient -> stringResource(R.string.endpoint_tcp, v.host, v.port); is NmeaEndpoint.UdpListener -> stringResource(R.string.endpoint_udp, v.localPort) }
@Composable private fun noticeLabel(v: DataNotice) = stringResource(when (v) { DataNotice.SAVED -> R.string.notice_saved; DataNotice.DISABLED -> R.string.notice_disabled; DataNotice.CANDIDATE_UNAVAILABLE -> R.string.notice_candidate_unavailable; DataNotice.PERSISTENCE_FAILED -> R.string.notice_persistence_failed; DataNotice.PHONE_PERMISSION_REQUIRED -> R.string.notice_phone_permission; DataNotice.PHONE_LOCATION_DISABLED -> R.string.notice_phone_location_disabled; DataNotice.PHONE_PLATFORM_RESTRICTED -> R.string.notice_phone_restricted; DataNotice.ACTION_QUEUE_FULL -> R.string.notice_busy })

@Composable private fun keyLabel(key: DataKey): String = when (key) { DataKey.Position -> sensorLabel(BoatSensor.POSITION); DataKey.SpeedOverGround -> stringResource(R.string.key_speed); DataKey.CourseOverGround -> stringResource(R.string.key_course); is DataKey.Heading -> sensorLabel(BoatSensor.HEADING); is DataKey.Depth -> sensorLabel(BoatSensor.DEPTH); is DataKey.WindAngle -> stringResource(R.string.key_wind_angle); is DataKey.WindSpeed -> stringResource(R.string.key_wind_speed); else -> key.toString() }
private fun MarineValue.formatted(): String = when (this) { is MarineValue.Position -> String.format(Locale.US, "%.5f°, %.5f°", latitudeDegrees, longitudeDegrees); is MarineValue.Decimal -> when (unit) { MarineUnit.DEGREES -> String.format(Locale.US, "%.1f°", value); MarineUnit.KNOTS -> String.format(Locale.US, "%.1f kn", value); MarineUnit.METERS -> String.format(Locale.US, "%.1f m", value); MarineUnit.DIMENSIONLESS -> String.format(Locale.US, "%.2f", value) }; is MarineValue.Count -> value.toString(); is MarineValue.UtcEpochMillis -> value.toString() }

@Composable private fun healthColor(v: SensorHealth) = when (v) { SensorHealth.LIVE -> LocalWpTheme.current.safe; SensorHealth.HELD -> LocalWpTheme.current.warning; SensorHealth.STALE -> LocalWpTheme.current.stale; SensorHealth.UNAVAILABLE, SensorHealth.NEEDS_ATTENTION -> LocalWpTheme.current.alarm }
@Composable private fun inputHealthColor(v: DataInputHealth) = when (v) { DataInputHealth.RECEIVING -> LocalWpTheme.current.safe; DataInputHealth.WAITING, DataInputHealth.LISTENING -> LocalWpTheme.current.warning; DataInputHealth.STOPPED -> LocalWpTheme.current.muted; DataInputHealth.INTERRUPTED, DataInputHealth.ATTENTION -> LocalWpTheme.current.alarm }
@Composable private fun statusColor(v: SourceGroupStatus) = when (v) { SourceGroupStatus.USING -> LocalWpTheme.current.safe; SourceGroupStatus.DISCOVERING -> LocalWpTheme.current.warning; SourceGroupStatus.NO_DATA, SourceGroupStatus.DISABLED -> LocalWpTheme.current.muted; else -> LocalWpTheme.current.alarm }
@Composable private fun connectionTestColor(v: DataConnectionTestState) = when (v.phase) { ConnectionTestPhase.DETECTED -> LocalWpTheme.current.safe; ConnectionTestPhase.FAILED, ConnectionTestPhase.INVALID_CONFIGURATION -> LocalWpTheme.current.alarm; ConnectionTestPhase.NO_SEMANTIC_DATA, ConnectionTestPhase.PROBING, ConnectionTestPhase.SAVING -> LocalWpTheme.current.warning; ConnectionTestPhase.NOT_STARTED -> LocalWpTheme.current.muted }
