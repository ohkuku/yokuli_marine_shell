package com.yokuli.runtime.marine.ipc

import android.app.Application
import android.content.Context
import android.os.Parcel
import android.os.ParcelFileDescriptor
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.yokuli.anchorwatch.api.*
import com.yokuli.runtime.contract.*
import com.yokuli.runtime.contract.ais.AisTrafficService
import com.yokuli.runtime.contract.chart.ChartDataService
import com.yokuli.runtime.contract.chart.ChartProductBlock
import com.yokuli.runtime.contract.device.DeviceRuntimeService
import com.yokuli.runtime.contract.navigation.NavigationSessionService
import com.yokuli.runtime.contract.planning.RouteAnalysisService
import com.yokuli.runtime.contract.planning.RoutePlanningService
import com.yokuli.runtime.marine.MarineSystem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.security.MessageDigest
import kotlin.coroutines.Continuation

/** APK 内唯一领域所有者在主进程；Shell 只持有 Binder 客户端。不是跨 UID 安全沙箱。 */
object MarineCoreProcess {
    fun isShell(): Boolean = Application.getProcessName().endsWith(":shell")
    fun isCore(context: Context): Boolean = Application.getProcessName() == context.packageName
}

internal data class CoreCall(val id: String, val port: String, val method: String, val arguments: JsonArray = JsonArray())
internal data class CorePacket(val id: String, val sequence: Long = 0, val epoch: String = "", val session: String = "", val delta: Boolean = false, val value: JsonElement = JsonNull.INSTANCE, val error: String? = null, val callbackIndex: Int? = null,
    /** 编译块走独立二进制只读 FD；Gson 不得将每个字节展开成 JSON 数组。 */
    @Transient val block:ChartProductBlock?=null)
internal data class CoreHello(val protocol: Int, val apkVersion: Long, val schema: String, val epoch: String)

/** 固定端口表是完整的调用白名单；远端不能指定任意 class、constructor 或内部 controller。 */
internal object MarineCorePorts {
    val interfaces: Map<String, Class<*>> = linkedMapOf(
        "core" to MarineSystem::class.java,
        "hardwareLab" to com.yokuli.runtime.contract.hardware.HardwareLabService::class.java,
        "devices" to DeviceRuntimeService::class.java,
        "residency" to RuntimeResidencyService::class.java,
        "charts" to ChartDataService::class.java,
        "chartStore" to com.yokuli.runtime.contract.chart.OfficialChartStore::class.java,
        "navigation" to NavigationSessionService::class.java,
        "analysis" to RouteAnalysisService::class.java,
        "planning" to RoutePlanningService::class.java,
        "voyage" to VoyageSessionService::class.java,
        "anchorCommands" to AnchorCommandMonitor::class.java,
        "ais" to AisTrafficService::class.java,
        "presentation" to RuntimePresentationService::class.java,
        "readingHistory" to ReadingHistoryService::class.java,
        "services" to MarineServices::class.java,
        "sources" to DataSourceService::class.java,
        "voyages" to VoyageService::class.java,
        "anchor" to AnchorService::class.java,
        "network" to NetworkService::class.java,
        "sharing" to SharingService::class.java,
        "preferences" to VesselPreferencesService::class.java,
        "feedback" to MarineFeedbackService::class.java,
        "display" to DisplayDemandService::class.java,
        "content" to MarineContentService::class.java,
        "photos" to MarinePhotoService::class.java,
    )
    fun key(method: Method) = method.name + "(" + method.genericParameterTypes.joinToString(",") { it.typeName } + ")"
    private val methods = interfaces.mapValues { (_, type) -> type.methods.filter { it.declaringClass != Any::class.java }.associateBy(::key) }
    fun method(port: String, key: String): Method = methods[port]?.get(key) ?: throw SecurityException("Unknown marine port operation")
    fun nested(type: Class<*>): String? = interfaces.entries.firstOrNull { it.value == type }?.key
    fun target(system: MarineSystem, port: String): Any = when (port) {
        "core" -> system
        "hardwareLab" -> system.hardwareLab
        "devices" -> system.devices
        "residency" -> system.residency; "charts" -> system.charts; "navigation" -> system.navigation
        "chartStore" -> system.chartStore
        "analysis" -> system.analysis; "planning" -> system.planning; "voyage" -> system.voyage
        "anchorCommands" -> system.anchorCommands; "ais" -> system.ais; "presentation" -> system.presentation
        "readingHistory" -> system.readingHistory
        "services" -> system.services; "sources" -> system.services.sources; "voyages" -> system.services.voyages
        "anchor" -> system.services.anchor; "network" -> system.services.network; "sharing" -> system.services.sharing
        "preferences" -> system.services.preferences; "feedback" -> system.services.feedback
        "display" -> system.services.display; "content" -> system.services.content; "photos" -> system.services.content.photos
        else -> throw SecurityException("Unknown marine port")
    }
    val schema: String by lazy {
        val descriptor = methods.entries.sortedBy { it.key }.joinToString("\n") { (port, ops) -> "$port:${ops.values.map { it.toGenericString() }.sorted()}" }
        MessageDigest.getInstance("SHA-256").digest(descriptor.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun isSuspend(method: Method) = method.parameterTypes.lastOrNull() == Continuation::class.java
    fun valueType(method: Method): Type {
        if (isSuspend(method)) {
            val continuation = method.genericParameterTypes.last() as ParameterizedType
            return concrete(continuation.actualTypeArguments[0])
        }
        val type = method.genericReturnType
        if (Flow::class.java.isAssignableFrom(method.returnType)) return concrete((type as ParameterizedType).actualTypeArguments[0])
        return if (Job::class.java.isAssignableFrom(method.returnType)) Unit::class.java else type
    }
    private fun concrete(type: Type): Type = if (type is WildcardType) type.lowerBounds.firstOrNull() ?: type.upperBounds.first() else type
    fun argumentTypes(method: Method): List<Type> = method.genericParameterTypes.toList().let { if (isSuspend(method)) it.dropLast(1) else it }
    /** 只有读操作可被客户端取消；已接受的写命令归 Core，UI 死亡不取消写入。 */
    fun cancellableRead(port: String, method: Method) = when (port) {
        "hardwareLab" -> method.name == "readRecording"
        "chartStore" -> method.name == "browse"
        "charts" -> method.name in setOf("acquireSnapshot", "acquireDisplaySnapshot", "validateDisplayProduct", "query", "querySpatial", "inspectPosition", "browse", "readFeature", "readMetadata", "readStorageUsage", "rasterWindows", "drawing", "terrainStatus", "terrainStatuses", "terrainOverview", "readTerrainBlock")
        "voyage" -> method.name in setOf("receipt", "snapshot")
        "voyages" -> method.name in setOf("tripReport", "tripReplay", "tripMapData", "commandReceipt")
        "content" -> method.name in setOf("anchorTrackPage", "bundle")
        "readingHistory" -> method.name in setOf("slice", "slices")
        else -> false
    }
}

internal object CoreWire {
    const val DESCRIPTOR = "com.yokuli.marine.private.Core.v1"
    const val CALLBACK = "com.yokuli.marine.private.Client.v1"
    const val VERSION = 1
    const val HELLO = 1
    const val ATTACH = 2
    const val CALL = 3
    const val SUBSCRIBE = 4
    const val RELEASE = 5
    const val DETACH = 6
    const val ABANDON_READ = 7
    const val RESULT = 101
    const val VALUE = 102
    const val ARGUMENT_CALLBACK = 103
    const val MAX_BYTES = 64L * 1024 * 1024
    const val MAX_IN_FLIGHT = 96
    const val MAX_SUBSCRIPTIONS = 64
    const val MAX_CLIENTS = 4

    fun version(context: Context): Long = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /** 小增量直接进 Parcel；大图幅、轨迹和历史走 FD，不反复把传感器帧写入磁盘。 */
    fun writePayload(context: Context, parcel: Parcel, value: Any) {
        if(value is CorePacket && value.block!=null) {
            val block=value.block
            require(block.bytes.size in 1..32*1024*1024 && block.key.length<=512 && block.sha256.matches(Regex("[0-9a-f]{64}"))) { "MARINE_PRODUCT_INVALID" }
            val folder=File(context.cacheDir,"core-wire").apply{mkdirs()}
            val file=File.createTempFile("product-",".bin",folder)
            try {
                file.outputStream().use{it.write(block.bytes)}
                parcel.writeInt(2);parcel.writeLong(block.bytes.size.toLong())
                parcel.writeString(MarineCoreCodec.gson.toJson(value.copy(block=null)))
                parcel.writeString(block.key);parcel.writeInt(block.schema);parcel.writeString(block.sha256)
                ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{parcel.writeParcelable(it,0)}
            }finally{file.delete()}
            return
        }
        val sink = PayloadSink(context)
        try {
            sink.writer(Charsets.UTF_8).use { MarineCoreCodec.gson.toJson(value, it) }
            val file = sink.file
            parcel.writeInt(if (file == null) 0 else 1)
            parcel.writeLong(sink.count)
            if (file == null) parcel.writeByteArray(sink.small.toByteArray())
            else ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { parcel.writeParcelable(it, 0) }
        } finally { sink.close(); sink.file?.delete() }
    }

    private class PayloadSink(private val context: Context) : java.io.OutputStream() {
        val small = java.io.ByteArrayOutputStream(8192)
        var count = 0L; private set
        var file: File? = null; private set
        private var disk: java.io.OutputStream? = null
        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            require(count + length <= MAX_BYTES) { "MARINE_PAYLOAD_TOO_LARGE" }
            if (disk == null && count + length > 48 * 1024) {
                val folder = File(context.cacheDir, "core-wire").apply { mkdirs() }
                file = File.createTempFile("wire-", ".json", folder)
                disk = requireNotNull(file).outputStream().buffered().also { small.writeTo(it); small.reset() }
            }
            (disk ?: small).write(bytes, offset, length)
            count += length
        }
        override fun flush() { disk?.flush() }
        override fun close() { disk?.close() }
    }

    fun <T> readPayload(parcel: Parcel, type: Class<T>): T {
        val mode = parcel.readInt()
        val size = parcel.readLong()
        require(size in 0..MAX_BYTES) { "MARINE_PAYLOAD_TOO_LARGE" }
        if(mode==2) {
            require(type==CorePacket::class.java && size in 1..32L*1024*1024) { "MARINE_PRODUCT_INVALID" }
            val header=requireNotNull(parcel.readString());require(header.length<=16_384){"MARINE_PRODUCT_INVALID"}
            val packet=MarineCoreCodec.gson.fromJson(header,CorePacket::class.java)
            val key=requireNotNull(parcel.readString());val schema=parcel.readInt();val hash=requireNotNull(parcel.readString())
            @Suppress("DEPRECATION") val fd=requireNotNull(parcel.readParcelable<ParcelFileDescriptor>(ParcelFileDescriptor::class.java.classLoader))
            val bytes=ParcelFileDescriptor.AutoCloseInputStream(fd).use {input->
                require(key.length<=512 && schema>0 && hash.matches(Regex("[0-9a-f]{64}"))){"MARINE_PRODUCT_INVALID"}
                ByteArray(size.toInt()).also {result->
                    var offset=0
                    while(offset<result.size){val count=input.read(result,offset,result.size-offset);require(count>0){"MARINE_PRODUCT_TRUNCATED"};offset+=count}
                    require(input.read()==-1){"MARINE_PRODUCT_LENGTH_MISMATCH"}
                }
            }
            val actual=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
            require(actual==hash){"MARINE_PRODUCT_CHECKSUM"}
            return type.cast(packet.copy(block=ChartProductBlock(key,schema,bytes,hash)))
        }
        if (mode == 0) {
            require(size <= 48 * 1024)
            val bytes = requireNotNull(parcel.createByteArray())
            require(bytes.size.toLong() == size)
            return MarineCoreCodec.gson.fromJson(bytes.toString(Charsets.UTF_8), type)
        }
        require(mode == 1) { "UNSUPPORTED_MARINE_PAYLOAD" }
        @Suppress("DEPRECATION") val fd = requireNotNull(parcel.readParcelable<ParcelFileDescriptor>(ParcelFileDescriptor::class.java.classLoader))
        return ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
            val bytes = java.io.ByteArrayOutputStream(minOf(size, 64 * 1024L).toInt())
            val chunk = ByteArray(32 * 1024)
            var count = 0L
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                count += read
                require(count <= size && count <= MAX_BYTES) { "MARINE_PAYLOAD_LENGTH_MISMATCH" }
                bytes.write(chunk, 0, read)
            }
            require(count == size) { "MARINE_PAYLOAD_TRUNCATED" }
            MarineCoreCodec.gson.fromJson(bytes.toString(Charsets.UTF_8.name()), type)
        }
    }
}
