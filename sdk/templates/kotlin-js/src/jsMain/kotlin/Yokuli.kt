@file:Suppress("unused")

import kotlin.js.Promise

/** SDK 1 公共桥：只读规范数据，不创建第二套采集或 NMEA 连接。 */
@JsName("Yokuli")
external object Yokuli {
    val version: Int
    val system: YokuliSystem
    val marine: YokuliMarine
    val nmea: YokuliNmea
    val storage: YokuliStorage
    val navigation: YokuliNavigation
    val ui: YokuliUi
    fun dispose()
}
external interface YokuliSystem { fun info(): Promise<YokuliInfo> }
external interface YokuliInfo {
    val sdk: Int
    val language: String
    val theme: String
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
external interface CoreConnection { val state: String }
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
external interface YokuliNmea { fun connections(): Promise<NmeaConnections> }
external interface NmeaConnections { val connections: Array<NmeaConnection>; val readOnly: Boolean; val connection: CoreConnection }
external interface NmeaConnection { val id: String; val name: String; val transport: String; val state: String; val lastReceivedAt: Double?; val lastReceivedAgeMillis: Double?; val lastReceivedElapsedMillis: Double?; val received: Double; val sent: Double }
external interface YokuliStorage { fun get(): Promise<StoredValue>; fun set(value: dynamic): Promise<SaveResult> }
external interface StoredValue { val value: dynamic }
external interface SaveResult { val saved: Boolean }
external interface YokuliNavigation { fun open(destination: String): Promise<dynamic> }
external interface YokuliError { val code: String?; val message: String; val retryAfterMillis: Double? }
/** UI 库返回真实 DOM，文字通过 textContent 插入；不需要 HTML 字符串或内联脚本。 */
external interface YokuliUi {
    fun node(tag: String, className: String = definedExternally, text: String = definedExternally): dynamic
    fun metric(label: String, reading: MarineReading?, digits: Int = definedExternally): dynamic
    fun status(message: String, isError: Boolean = definedExternally): dynamic
    fun button(text: String, action: () -> dynamic, options: dynamic = definedExternally): dynamic
    fun age(timestamp: Double?, ageMillis: Double? = definedExternally): String
    fun quality(value: String?): String
    fun errorMessage(error: dynamic): String
}
