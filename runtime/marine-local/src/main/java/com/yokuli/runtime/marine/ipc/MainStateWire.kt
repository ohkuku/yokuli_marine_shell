package com.yokuli.runtime.marine.ipc

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.yokuli.anchorwatch.MainUiState
import java.lang.reflect.Modifier

/**
 * 旧大投影的私有增量桥。字段表只从已编译 MainUiState 取得，握手锁定同一 APK；
 * 首帧完整，后续只编码改变的顶层值。轨迹/历史没有变化时既不 JSON 编码也不复制重解码。
 * 每次构造新的不可变快照，禁止在旧 StateFlow 值上修改字段。
 */
internal object MainStateWire {
    private val fields = MainUiState::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
        .onEach { it.isAccessible = true }
    private val byName = fields.associateBy { it.name }
    private val handledFields = setOf(
        "fix",
        "nmeaFix",
        "nmeaConnectionStartedElapsed",
        "systemFix",
        "connection",
        "connectionAttempt",
        "diagnostics",
        "nmeaTransportDiagnostics",
        "nmeaInstruments",
        "settings",
        "settingsReady",
        "sessions",
        "active",
        "points",
        "activeLearningPointCount",
        "follow",
        "page",
        "anchorSection",
        "sailSection",
        "dataSection",
        "mockGps",
        "proxyFeedback",
        "demoGps",
        "alarmSnapshot",
        "rangeEditorRequested",
        "eventsBySession",
        "positionHealth",
        "nmeaSharing",
        "localNmeaServerSettings",
        "localNmeaServerRuntime",
        "acceptedPosition",
        "sonarSurveys",
        "selectedSonarSurveyId",
        "activeSonarSurvey",
        "sonarSamples",
        "sonarGrid",
        "sonarGridVersion",
        "sonarGridChangedCells",
        "sonarRecorder",
        "linzDepth",
        "linzDepthDiagnostics",
        "depthUi",
        "backup",
        "runtimeDiagnostics",
        "dismissedRuntimeFeedbackId",
        "watchSafety",
        "storageHealth",
        "incidents",
        "supportBundle",
        "offlineMap",
        "phoneHeading",
        "trustedNmeaCourse",
        "approachHeadingMode",
        "vesselApproachHeadingAvailable",
        "liveDepth",
        "liveWind",
        "conditions",
        "savedAnchorages",
        "anchorageClusters",
        "anchorageApproach",
        "nearbyAnchoragePrompt",
        "approachDisclaimerTargetId",
        "anchorageDuplicateExisting",
        "anchorageOperationError",
        "centreRecalculation",
        "vesselData",
        "nmeaFields",
        "vesselSettings",
        "outputSettings",
        "phonePositionOutputStatus",
        "tripSessions",
        "activeTrip",
        "tripTrack",
        "tripMapDestination",
        "tripDashboards",
        "phoneSensorCapabilities",
        "vesselMountCalibration",
        "phoneVesselMountState",
        "vesselCalibrationFeedback",
        "runtimeResources",
        "anchorSetupDraft",
        "anchorDraftSaveError",
    )
    init { require(handledFields == byName.keys) { "MainUiState compatibility codec must include every field" } }

    fun difference(previous: MainUiState?, current: MainUiState): JsonObject = JsonObject().apply {
        fields.forEach { field ->
            val value = field.get(current)
            val before = previous?.let(field::get)
            val mutableGridChanged = field.name == "sonarGrid" && previous?.sonarGridVersion != current.sonarGridVersion
            if (previous == null || mutableGridChanged || (before !== value && before != value)) {
                add(field.name, MarineCoreCodec.gson.toJsonTree(value, field.genericType))
            }
        }
    }

    fun apply(previous: MainUiState, json: JsonElement, delta: Boolean): MainUiState {
        val values = json.asJsonObject
        require(values.keySet().all { it in byName }) { "UNKNOWN_MARINE_STATE_FIELD" }
        if (!delta) require(values.keySet() == byName.keys) { "INCOMPLETE_MARINE_INITIAL_SNAPSHOT" }
        fun <T> changed(name: String, current: T): T {
            if (!values.has(name)) return current
            return MarineCoreCodec.gson.fromJson(values[name], byName.getValue(name).genericType)
        }
        // Named Kotlin copy arguments remain correct on ART/Dex regardless of reflection field order.
        return previous.copy(
            fix = changed("fix", previous.fix),
            nmeaFix = changed("nmeaFix", previous.nmeaFix),
            nmeaConnectionStartedElapsed = changed("nmeaConnectionStartedElapsed", previous.nmeaConnectionStartedElapsed),
            systemFix = changed("systemFix", previous.systemFix),
            connection = changed("connection", previous.connection),
            connectionAttempt = changed("connectionAttempt", previous.connectionAttempt),
            diagnostics = changed("diagnostics", previous.diagnostics),
            nmeaTransportDiagnostics = changed("nmeaTransportDiagnostics", previous.nmeaTransportDiagnostics),
            nmeaInstruments = changed("nmeaInstruments", previous.nmeaInstruments),
            settings = changed("settings", previous.settings),
            settingsReady = changed("settingsReady", previous.settingsReady),
            sessions = changed("sessions", previous.sessions),
            active = changed("active", previous.active),
            points = changed("points", previous.points),
            activeLearningPointCount = changed("activeLearningPointCount", previous.activeLearningPointCount),
            follow = changed("follow", previous.follow),
            page = changed("page", previous.page),
            anchorSection = changed("anchorSection", previous.anchorSection),
            sailSection = changed("sailSection", previous.sailSection),
            dataSection = changed("dataSection", previous.dataSection),
            mockGps = changed("mockGps", previous.mockGps),
            proxyFeedback = changed("proxyFeedback", previous.proxyFeedback),
            demoGps = changed("demoGps", previous.demoGps),
            alarmSnapshot = changed("alarmSnapshot", previous.alarmSnapshot),
            rangeEditorRequested = changed("rangeEditorRequested", previous.rangeEditorRequested),
            eventsBySession = changed("eventsBySession", previous.eventsBySession),
            positionHealth = changed("positionHealth", previous.positionHealth),
            nmeaSharing = changed("nmeaSharing", previous.nmeaSharing),
            localNmeaServerSettings = changed("localNmeaServerSettings", previous.localNmeaServerSettings),
            localNmeaServerRuntime = changed("localNmeaServerRuntime", previous.localNmeaServerRuntime),
            acceptedPosition = changed("acceptedPosition", previous.acceptedPosition),
            sonarSurveys = changed("sonarSurveys", previous.sonarSurveys),
            selectedSonarSurveyId = changed("selectedSonarSurveyId", previous.selectedSonarSurveyId),
            activeSonarSurvey = changed("activeSonarSurvey", previous.activeSonarSurvey),
            sonarSamples = changed("sonarSamples", previous.sonarSamples),
            sonarGrid = changed("sonarGrid", previous.sonarGrid),
            sonarGridVersion = changed("sonarGridVersion", previous.sonarGridVersion),
            sonarGridChangedCells = changed("sonarGridChangedCells", previous.sonarGridChangedCells),
            sonarRecorder = changed("sonarRecorder", previous.sonarRecorder),
            linzDepth = changed("linzDepth", previous.linzDepth),
            linzDepthDiagnostics = changed("linzDepthDiagnostics", previous.linzDepthDiagnostics),
            depthUi = changed("depthUi", previous.depthUi),
            backup = changed("backup", previous.backup),
            runtimeDiagnostics = changed("runtimeDiagnostics", previous.runtimeDiagnostics),
            dismissedRuntimeFeedbackId = changed("dismissedRuntimeFeedbackId", previous.dismissedRuntimeFeedbackId),
            watchSafety = changed("watchSafety", previous.watchSafety),
            storageHealth = changed("storageHealth", previous.storageHealth),
            incidents = changed("incidents", previous.incidents),
            supportBundle = changed("supportBundle", previous.supportBundle),
            offlineMap = changed("offlineMap", previous.offlineMap),
            phoneHeading = changed("phoneHeading", previous.phoneHeading),
            trustedNmeaCourse = changed("trustedNmeaCourse", previous.trustedNmeaCourse),
            approachHeadingMode = changed("approachHeadingMode", previous.approachHeadingMode),
            vesselApproachHeadingAvailable = changed("vesselApproachHeadingAvailable", previous.vesselApproachHeadingAvailable),
            liveDepth = changed("liveDepth", previous.liveDepth),
            liveWind = changed("liveWind", previous.liveWind),
            conditions = changed("conditions", previous.conditions),
            savedAnchorages = changed("savedAnchorages", previous.savedAnchorages),
            anchorageClusters = changed("anchorageClusters", previous.anchorageClusters),
            anchorageApproach = changed("anchorageApproach", previous.anchorageApproach),
            nearbyAnchoragePrompt = changed("nearbyAnchoragePrompt", previous.nearbyAnchoragePrompt),
            approachDisclaimerTargetId = changed("approachDisclaimerTargetId", previous.approachDisclaimerTargetId),
            anchorageDuplicateExisting = changed("anchorageDuplicateExisting", previous.anchorageDuplicateExisting),
            anchorageOperationError = changed("anchorageOperationError", previous.anchorageOperationError),
            centreRecalculation = changed("centreRecalculation", previous.centreRecalculation),
            vesselData = changed("vesselData", previous.vesselData),
            nmeaFields = changed("nmeaFields", previous.nmeaFields),
            vesselSettings = changed("vesselSettings", previous.vesselSettings),
            outputSettings = changed("outputSettings", previous.outputSettings),
            phonePositionOutputStatus = changed("phonePositionOutputStatus", previous.phonePositionOutputStatus),
            tripSessions = changed("tripSessions", previous.tripSessions),
            activeTrip = changed("activeTrip", previous.activeTrip),
            tripTrack = changed("tripTrack", previous.tripTrack),
            tripMapDestination = changed("tripMapDestination", previous.tripMapDestination),
            tripDashboards = changed("tripDashboards", previous.tripDashboards),
            phoneSensorCapabilities = changed("phoneSensorCapabilities", previous.phoneSensorCapabilities),
            vesselMountCalibration = changed("vesselMountCalibration", previous.vesselMountCalibration),
            phoneVesselMountState = changed("phoneVesselMountState", previous.phoneVesselMountState),
            vesselCalibrationFeedback = changed("vesselCalibrationFeedback", previous.vesselCalibrationFeedback),
            runtimeResources = changed("runtimeResources", previous.runtimeResources),
            anchorSetupDraft = changed("anchorSetupDraft", previous.anchorSetupDraft),
            anchorDraftSaveError = changed("anchorDraftSaveError", previous.anchorDraftSaveError),
        )
    }
}
