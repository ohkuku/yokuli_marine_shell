@file:Suppress("unused")

import kotlin.js.Promise

/** SDK 2 公共桥，兼容 SDK 1。所有控制进入唯一 Core；只在前台由用户明确触发。 */
@JsName("Yokuli")
external object Yokuli {
    val version: Int
    val system: YokuliSystem
    val marine: YokuliMarine
    val hardware: YokuliHardware
    val devices: YokuliDevices
    val sources: YokuliSources
    val sharing: YokuliSharing
    val voyage: YokuliVoyage
    val nmea: YokuliNmea
    val storage: YokuliStorage
    val navigation: YokuliNavigation
    val ui: YokuliUi
    fun dispose()
}
external interface YokuliSystem {
    fun info(): Promise<YokuliInfo>
    fun services(): Promise<SystemServices>
    fun apps(): Promise<PackageCatalog>
}
/** 以宿主实际目录为准；声明、授权和当前可用性是三个不同条件。 */
external interface SystemServices {
    val sdk: Int
    val appSdk: Int
    val packageFormat: String
    val connection: CoreConnection
    val methods: Array<SystemMethod>
    val runtime: RuntimeCapabilities
}
external interface SystemMethod {
    val name: String
    val since: Int
    val permission: String?
    val declared: Boolean
    val granted: Boolean
    val available: Boolean
    val foregroundOnly: Boolean
}
external interface RuntimeCapabilities {
    val deviceBackends: Array<String>
    val virtualClock: Boolean
    val systemReplay: Boolean
    val scenarioEngine: Boolean
    val backgroundScripts: Boolean
    val nativeApk: Boolean
}
external interface YokuliDevices {
    fun snapshot(): Promise<DeviceSnapshot>
    fun watch(onSnapshot: (DeviceSnapshot) -> Unit, onError: (YokuliError) -> Unit): () -> Unit
}
/** 时间为 Android 单调时钟，不将其当作 UTC；measurementAgeMillis 为测量年龄。 */
external interface DeviceSnapshot {
    val connection: CoreConnection
    val ready: Boolean
    val runtimeId: String?
    val revision: Double
    val capturedElapsedMillis: Double?
    val ageMillis: Double?
    val devices: Array<VirtualDevice>
    val error: String?
}
external interface VirtualDevice {
    val id: String
    val name: String
    val kind: String
    val backend: String
    val availability: String
    val health: String
    val requested: Boolean
    val active: Boolean
    val generation: Double?
    val generationOrigin: String?
    val capabilities: Array<String>
    val lastMeasuredElapsedMillis: Double?
    val lastReceivedElapsedMillis: Double?
    val lastOutputElapsedMillis: Double?
    val measurementAgeMillis: Double?
    val provenance: DeviceProvenance
    val reason: String?
}
external interface DeviceProvenance { val driver: String?; val sourceId: String?; val connectionId: String? }

external interface YokuliInfo {
    val sdk: Int
    val appSdk: Int
    val packageFormat: String
    val language: String
    val theme: String
    val clock: MarineClock
    val units: YokuliUnits
}
external interface YokuliUnits { val distance: String; val speed: String; val depth: String; val pressure: String; val temperature: String; val coordinates: String }
external interface YokuliMarine {
    fun snapshot(): Promise<MarineSnapshot>
    /** 返回释放函数；SDK 隐藏时停止轮询，显示时恢复。不能用于后台安全任务。 */
    fun watch(onSnapshot: (MarineSnapshot) -> Unit, onError: (YokuliError) -> Unit): () -> Unit
}
external interface MarineSnapshot {
    val snapshotAgeMillis: Double?
    val snapshotCurrent: Boolean
    val capturedAt: Double
    val connection: CoreConnection
    val readings: MarineReadings
    val vessel: VesselSnapshot
    val positionDisplay: String?
}
external interface MarineReading {
    val value: Double?
    val unit: String
    val source: MarineSource?
    val measuredAt: Double?
    val quality: String
    val freshness: String
    val sourceFreshness: String
    val measuredElapsedMillis: Double?
    val rawValue: Double?
    val rawUnit: String
    val ageMillis: Double?
    val reference: String?
}
external interface VesselSnapshot {
    val latitude: Double?
    val longitude: Double?
    val measuredAt: Double?
    val quality: String?
    val freshness: String?
    val source: MarineSource?
    val ageMillis: Double?
    val positionDisplay: String?
}
external interface CoreConnection { val state: String; val transport: String? }
external interface MarineSource { val id: String?; val name: String; val type: String?; val `class`: String; val connectionId: String?; val generation: Double? }
external interface MarineReadings {
    val sog: MarineReading?
    val cog: MarineReading?
    val heading: MarineReading?
    val depth: MarineReading?
    val tws: MarineReading?
    val twd: MarineReading?
    val aws: MarineReading?
    val awa: MarineReading?
    val pressure: MarineReading?
    val air: MarineReading?
    val water: MarineReading?
    val heel: MarineReading?
    val pitch: MarineReading?
}
external interface YokuliNmea {
    fun connections(): Promise<NmeaConnections>
    fun watch(onSnapshot: (NmeaConnections) -> Unit, onError: (YokuliError) -> Unit): () -> Unit
    fun setConnectionEnabled(id: String, enabled: Boolean): Promise<ConnectionChange>
}
external interface ConnectionChange { val requested: Boolean; val id: String; val enabled: Boolean; val state: ConnectionState }
external interface ConnectionState { val id: String; val name: String; val requested: Boolean; val state: String; val receiveEnabled: Boolean; val sendEnabled: Boolean; val error: String? }
external interface YokuliSources {
    fun snapshot(): Promise<SourcesSnapshot>
    fun watch(onSnapshot: (SourcesSnapshot) -> Unit, onError: (YokuliError) -> Unit): () -> Unit
    fun select(metric: String, sourceId: String?): Promise<SourceChange>
}
external interface SourcesSnapshot { val current: Boolean; val generatedElapsedMillis: Double; val position: PositionSources; val metrics: Array<MetricSources> }
external interface PositionSources {
    val mode: String
    val locked: Boolean
    val phoneStatus: String
    val selectionPending: Boolean
    val selectedConnectionId: String?
    val options: Array<PositionOption>
}
external interface PositionOption { val sourceId: String?; val name: String; val available: Boolean }
external interface MetricSources {
    val metric: String
    val pinnedSourceId: String?
    val selectedSourceId: String?
    val selectable: Boolean
    val conflict: Boolean
    val candidatesTruncated: Boolean
    val candidates: Array<SourceCandidate>
}
external interface SourceCandidate {
    val sourceId: String
    val name: String
    val type: String
    val `class`: String
    val connectionId: String?
    val validity: String
    val quality: String
    val measuredAt: Double?
    val ageMillis: Double?
}
external interface SourceChange { val requested: Boolean; val metric: String; val sourceId: String?; val state: SourcesSnapshot }
external interface YokuliSharing {
    fun snapshot(): Promise<SharingSnapshot>
    fun watch(onSnapshot: (SharingSnapshot) -> Unit, onError: (YokuliError) -> Unit): () -> Unit
    fun setEnabled(enabled: Boolean): Promise<SharingChange>
}
external interface SharingSnapshot {
    val configured: Boolean
    val requested: Boolean
    val state: String
    val port: Int
    val clientCount: Int
    val feed: String
    val capabilities: Array<String>
    val forwardFrom: Array<String>
    val generatedSentences: Double
    val queuedSentences: Double
    val sentSentences: Double
    val lastOutputAgeMillis: Double?
    val message: String?
}
external interface SharingChange { val requested: Boolean; val enabled: Boolean; val state: SharingSnapshot }
external interface YokuliVoyage {
    fun snapshot(): Promise<VoyageSnapshot>
    fun watch(onSnapshot: (VoyageSnapshot) -> Unit, onError: (YokuliError) -> Unit): () -> Unit
    fun command(command: VoyageCommand): Promise<VoyageCommandResult>
    fun receipt(requestId: String, recheck: Boolean = definedExternally): Promise<VoyageReceiptResult>
}
external interface VoyageSnapshot {
    val sessionId: String?
    val phase: String
    val name: String?
    val distanceMeters: Double
    val startedAt: Double?
    val pausedAt: Double?
    val elapsedMillis: Double
    val momentCount: Int
    val commandPending: Boolean
}
/** requestId 由调用方持久保存，同一动作重试必须复用；只有 start 不要求 sessionId。 */
external interface VoyageCommand { var action: String; var requestId: String; var sessionId: String?; var name: String?; var motion: Boolean? }
external interface VoyageReceipt { val action: String; val status: String; val reason: String?; val sessionId: String?; val terminal: Boolean }
external interface VoyageCommandResult { val requestId: String; val receipt: VoyageReceipt?; val state: VoyageSnapshot; val requested: Boolean }
external interface VoyageReceiptResult { val requestId: String; val receipt: VoyageReceipt?; val state: VoyageSnapshot; val found: Boolean }

external interface NmeaConnections { val connections: Array<NmeaConnection>; val readOnly: Boolean; val connection: CoreConnection }
external interface NmeaConnection { val requested: Boolean; val receiveEnabled: Boolean; val sendEnabled: Boolean; val current: Boolean; val id: String; val name: String; val transport: String; val state: String; val lastReceivedAt: Double?; val lastReceivedAgeMillis: Double?; val lastReceivedElapsedMillis: Double?; val received: Double; val sent: Double }
external interface YokuliStorage { fun get(): Promise<StoredValue>; fun set(value: dynamic): Promise<SaveResult> }
external interface StoredValue { val value: dynamic }
external interface SaveResult { val saved: Boolean }
external interface YokuliNavigation { fun open(destination: String): Promise<dynamic> }
external interface YokuliError { val code: String?; val message: String; val retryAfterMillis: Double? }
/** UI 库返回真实 DOM，文字通过 textContent 插入；不需要 HTML 字符串或内联脚本。 */
external interface YokuliUi {
    fun page(title: String, subtitle: String = definedExternally): dynamic
    fun section(title: String, subtitle: String = definedExternally): dynamic
    fun field(label: String, value: String, onChange: (String) -> Unit, options: dynamic = definedExternally): dynamic
    fun toggle(label: String, checked: Boolean, onChange: (Boolean) -> dynamic, options: dynamic = definedExternally): dynamic
    fun choiceGroup(label: String, items: dynamic, selected: dynamic, onChange: (dynamic) -> dynamic): dynamic
    fun pivot(items: dynamic, options: dynamic = definedExternally): dynamic
    fun node(tag: String, className: String = definedExternally, text: String = definedExternally): dynamic
    fun metric(label: String, reading: MarineReading?, digits: Int = definedExternally): dynamic
    fun status(message: String, isError: Boolean = definedExternally): dynamic
    fun button(text: String, action: () -> dynamic, options: dynamic = definedExternally): dynamic
    fun age(timestamp: Double?, ageMillis: Double? = definedExternally): String
    fun quality(value: String?): String
    fun errorMessage(error: dynamic): String
}


/** 时间由 Core 推进；不得使用 Date.now() 驱动虚拟航行的超时。 */
external interface MarineClock {
    val virtual: Boolean; val paused: Boolean; val rate: Double; val epoch: Double
    val utcMillis: Double; val elapsedMillis: Double; val hostElapsedMillis: Double
}
external interface YokuliHardware {
    fun snapshot(): Promise<HardwareSnapshot>
    fun watch(onSnapshot: (HardwareSnapshot) -> Unit, onError: (YokuliError) -> Unit): () -> Unit
    fun control(command: HardwareCommand): Promise<HardwareResult>
    fun readRecording(id: String, offset: Double = definedExternally, limit: Int = definedExternally): Promise<RecordingChunk>
}
external interface HardwareSnapshot {
    val ready: Boolean; val mode: String; val epoch: Double; val clock: MarineClock
    val paused: Boolean; val rate: Double; val utcMillis: Double; val elapsedMillis: Double
    val scenario: HardwareScenario; val devices: Array<LabDevice>; val faults: Array<HardwareFault>
    val faultCapabilities: dynamic
    val recordings: Array<HardwareRecording>; val recordingId: String?; val replayId: String?
    val replayPositionMillis: Double; val replayDurationMillis: Double; val eventCursor: Int
    val power: HardwarePower; val storage: HardwareStorage; val error: String?
}
external interface HardwareCommand {
    var action: String; var requestId: String; var deviceId: String?; var recordingId: String?
    var rate: Double?; var stepMillis: Double?; var name: String?; var scenario: HardwareScenario?
    var fault: HardwareFault?; var power: HardwarePower?; var storageFault: String?
}
external interface HardwareResult { val accepted: Boolean; val code: String; val message: String?; val requestId: String }
external interface LabDevice { val id: String; val name: String; val kind: String; val attached: Boolean; val frames: Double }
external interface HardwareScenario {
    var version: Int; var name: String; var latitude: Double; var longitude: Double
    var speedKnots: Double; var courseDegrees: Double; var headingDegrees: Double; var accuracyMeters: Double
    var heelDegrees: Double; var pitchDegrees: Double; var rollPeriodSeconds: Double; var depthMeters: Double
    var windSpeedKnots: Double; var windDirectionDegrees: Double; var pressureHpa: Double; var waterTemperatureC: Double
    var waypoints: Array<ScenarioPoint>; var targets: Array<ScenarioAisTarget>; var events: Array<ScenarioEvent>
}
external interface ScenarioPoint { var latitude: Double; var longitude: Double }
external interface ScenarioAisTarget {
    var mmsi: Int; var name: String; var latitude: Double; var longitude: Double
    var speedKnots: Double; var courseDegrees: Double; var headingDegrees: Double
}
external interface ScenarioEvent {
    var atMillis: Double; var action: String; var deviceId: String; var value: Double
    var fault: HardwareFault?; var target: ScenarioAisTarget?; var power: HardwarePower?; var storageFault: String?
}
external interface HardwareFault { var deviceId: String; var type: String; var magnitude: Double }
external interface HardwarePower { var percent: Int; var external: Boolean; var charging: Boolean; var thermal: Int; var screenOn: Boolean; var simulated: Boolean }
external interface HardwareStorage { val freeBytes: Double; val totalBytes: Double; val fault: String }
external interface HardwareRecording {
    val id: String; val name: String; val startedAtUtc: Double; val durationMillis: Double
    val frames: Double; val bytes: Double; val complete: Boolean; val error: String?
}
external interface RecordingChunk { val text: String; val nextOffset: Double; val end: Boolean }

external interface PackageCatalog { val apps: Array<YklPackage> }
external interface YklPackage { val id: String; val name: String; val version: Int; val runtime: String; val system: Boolean; val removable: Boolean; val available: Boolean; val openTarget: String }
