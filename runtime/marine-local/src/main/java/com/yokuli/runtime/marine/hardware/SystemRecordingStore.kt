package com.yokuli.runtime.marine.hardware

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.yokuli.runtime.contract.hardware.*
import com.yokuli.runtime.contract.time.MarineTime
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

/** 原始输入日志；保留测量时间与接收时间，不把重放时刻伪装成新的测量。 */
internal data class RecordedEvent(
    val atMillis: Long, val kind: String,
    val frame: HardwareFrame? = null, val devices: List<HardwareDeviceSpec>? = null,
    val command: HardwareLabCommand? = null, val power: HardwarePower? = null,
    val storage: StorageFault? = null,
)
internal data class RecordingHeader(val version: Int = 1, val elapsed: Long, val utc: Long,
    val devices: List<HardwareDeviceSpec>, val scenario: HardwareScenario,
    val power: HardwarePower = HardwarePower(), val storage: StorageFault = StorageFault.NONE)

internal class SystemRecordingStore(context: Context, private val scope: CoroutineScope, private val onError: (String) -> Unit) {
    private val root = File(HardwareLabBoot.directory(context), "recordings").apply { mkdirs() }
    private val gson = Gson()
    private val index = java.util.concurrent.ConcurrentHashMap<String, HardwareRecording>()
    private var job: Job? = null
    private var channels: Channel<RecordedEvent>? = null
    private var subscription: AutoCloseable? = null
    @Volatile var active: HardwareRecording? = null; private set
    private var started = 0L
    init {
        // 上次进程死亡留下的日志仍可导出；完整行可以回放，绝不假称完整录制。
        root.listFiles()?.filter { it.extension == "meta" }?.forEach { meta ->
            runCatching { readMeta(meta) }.getOrNull()?.also { index[it.id] = it }?.takeIf { !it.complete && it.error == null }?.let {
                runCatching { writeMeta(it.copy(bytes = data(it.id).length(), error = "RECORDING_INTERRUPTED")) }
            }
        }
    }
    private fun valid(id: String) = require(id.matches(Regex("[a-f0-9-]{36}"))) { "INVALID_RECORDING_ID" }
    private fun data(id: String): File { valid(id); return File(root, "$id.jsonl") }
    private fun readMeta(file: File): HardwareRecording {
        require(file.length() <= 16_384)
        return AtomicFile(file).openRead().bufferedReader().use { gson.fromJson(it, HardwareRecording::class.java) }
    }
    private fun writeMeta(meta: HardwareRecording) {
        val file = AtomicFile(File(root, "${meta.id}.meta")); val out = file.startWrite()
        try { out.write(gson.toJson(meta).toByteArray()); out.fd.sync(); file.finishWrite(out) }
        catch (e: Throwable) { file.failWrite(out); throw e }
        index[meta.id] = meta
    }
    fun list(): List<HardwareRecording> = index.values.sortedByDescending { it.startedAtUtc }.take(200).map { if (it.id == active?.id) active!! else it }
    fun header(id: String): RecordingHeader = data(id).inputStream().buffered().use { stream ->
        val line = boundedLine(stream, 524_288) ?: error("RECORDING_EMPTY")
        gson.fromJson(line, RecordingHeader::class.java).also {
            require(it.version == 1 && it.elapsed >= 0 && it.devices.size <= 128) { "INVALID_RECORDING_HEADER" }
            ScenarioValidation.requireValid(it.scenario)
        }
    }
    fun get(id: String): HardwareRecording = list().firstOrNull { it.id == id } ?: error("RECORDING_NOT_FOUND")
    suspend fun start(name: String, scenario: HardwareScenario, initialPower: HardwarePower): String {
        check(active == null) { "ALREADY_RECORDING" }
        require(root.listFiles().orEmpty().sumOf { it.length() } < 1_073_741_824L) { "RECORDING_STORAGE_LIMIT" }
        require(list().size < 200) { "RECORDING_COUNT_LIMIT" }
        val id = UUID.randomUUID().toString(); started = MarineTime.nowElapsedMillis()
        val meta = HardwareRecording(id, name.trim().take(100).ifBlank { "System recording" }, MarineTime.nowUtcMillis())
        val out = data(id).outputStream(); val writer = out.bufferedWriter()
        try {
            val header = gson.toJson(RecordingHeader(elapsed = started, utc = meta.startedAtUtc,
                devices = MarineDeviceBus.state.value.devices.filter { it.attached }.map { it.spec }, scenario = scenario,
                power = initialPower, storage = VirtualHostServices.storage.value))
            require(header.toByteArray().size <= 524_288) { "RECORDING_HEADER_TOO_LARGE" }
            writer.appendLine(header)
            writer.flush(); out.fd.sync(); writeMeta(meta)
        } catch (e: Throwable) { writer.close(); data(id).delete(); throw e }
        active = meta
        val queue = Channel<RecordedEvent>(8192); channels = queue
        subscription = MarineDeviceBus.subscribeRecorder(onFrame = { frame ->
            offer(RecordedEvent((frame.receivedElapsedMillis - started).coerceAtLeast(0), "frame", frame = frame))
        }, onCatalog = { snapshot ->
            offer(RecordedEvent((MarineTime.nowElapsedMillis() - started).coerceAtLeast(0), "devices",
                devices = snapshot.devices.filter { it.attached }.map { it.spec }))
        })
        job = scope.launch(Dispatchers.IO) {
            var frames = 0L; var bytes = data(id).length(); var lastFlush = MarineTime.hostElapsedMillis()
            var lastAt = 0L
            var complete = false; var issue: String? = null
            try {
                for (incoming in queue) {
                    val event = incoming.copy(atMillis = maxOf(lastAt, incoming.atMillis)); lastAt = event.atMillis
                    val line = gson.toJson(event); require(line.toByteArray().size <= 60_000) { "RECORDING_EVENT_TOO_LARGE" }
                    bytes += line.toByteArray().size + 1
                    check(bytes <= 268_435_456) { "RECORDING_SIZE_LIMIT" }
                    writer.appendLine(line); if (event.frame != null) frames++
                    active = meta.copy(durationMillis = event.atMillis, frames = frames, bytes = bytes)
                    if (MarineTime.hostElapsedMillis() - lastFlush >= 2000) {
                        writer.flush(); out.fd.sync(); active?.let(::writeMeta); lastFlush = MarineTime.hostElapsedMillis()
                    }
                }
                complete = true
            } catch (e: Exception) { issue = e.message ?: "RECORDING_WRITE_FAILED"; onError(issue) }
            finally {
                subscription?.close(); subscription = null
                runCatching { writer.flush(); out.fd.sync() }; runCatching { writer.close() }
                runCatching { writeMeta((active ?: meta).copy(complete = complete, error = issue, bytes = data(id).length())) }.onFailure { onError("RECORDING_INDEX_WRITE_FAILED") }
                channels = null; active = null
            }
        }
        return id
    }
    private fun offer(event: RecordedEvent) {
        channels?.let { if (!it.trySend(event).isSuccess) it.close(IllegalStateException("RECORDING_BACKPRESSURE")) }
    }
    fun command(value: HardwareLabCommand) = offer(RecordedEvent((MarineTime.nowElapsedMillis() - started).coerceAtLeast(0), "command", command = value.copy(scenario = null, scenarioJson = null)))
    fun power(value: HardwarePower) = offer(RecordedEvent((MarineTime.nowElapsedMillis() - started).coerceAtLeast(0), "power", power = value))
    fun storage(value: StorageFault) = offer(RecordedEvent((MarineTime.nowElapsedMillis() - started).coerceAtLeast(0), "storage", storage = value))
    suspend fun stop() { subscription?.close(); subscription = null; channels?.close(); job?.join(); job = null }
    fun delete(id: String) { check(active?.id != id) { "RECORDING_ACTIVE" }; get(id); check(data(id).delete() || !data(id).exists()); AtomicFile(File(root,"$id.meta")).delete(); index.remove(id) }
    fun chunk(id: String, offset: Long, limit: Int): RecordingChunk {
        get(id); require(offset >= 0 && limit in 4..48_000)
        return RandomAccessFile(data(id), "r").use { file ->
            require(offset <= file.length()); file.seek(offset)
            val bytes = ByteArray(minOf(limit.toLong(), file.length() - offset).toInt()); file.readFully(bytes)
            var count = bytes.size
            if (offset + count < file.length()) {
                // 不截断 UTF-8 字符；偏移始终以已返回的完整字节为准。
                while (count > 0 && (bytes[count - 1].toInt() and 0xc0) == 0x80) count--
                if (count > 0 && (bytes[count - 1].toInt() and 0x80) != 0) count--
            }
            RecordingChunk(String(bytes, 0, count, Charsets.UTF_8), offset + count, offset + count >= file.length())
        }
    }
    suspend fun replay(id: String, from: Long, progress: (Long) -> Unit) {
        val header = header(id); val origin = MarineTime.nowElapsedMillis() - from
        val specs = header.devices.associateBy { it.id }.toMutableMap()
        fun attach(spec: HardwareDeviceSpec) = MarineDeviceBus.attach(spec.copy(backend = com.yokuli.runtime.contract.device.DeviceBackend.REPLAY))
        header.devices.forEach(::attach)
        VirtualHostServices.power(header.power.copy(percent = header.power.percent.coerceIn(0,100))); VirtualHostServices.storage(header.storage)
        data(id).inputStream().buffered().use { stream ->
            boundedLine(stream, 524_288)
            var prior = 0L
            while (true) {
                val line = boundedLine(stream) ?: break
                val event = gson.fromJson(line, RecordedEvent::class.java)
                require(event.atMillis >= prior && event.atMillis <= 604_800_000L) { "RECORDING_TIME_INVALID" }; prior = event.atMillis
                if (event.atMillis >= from) MarineTime.sleepUntil(origin + event.atMillis)
                currentCoroutineContext().ensureActive()
                when (event.kind) {
                    "devices" -> {
                        val devices = requireNotNull(event.devices); require(devices.size <= 128)
                        val ids = devices.map { it.id }.toSet()
                        MarineDeviceBus.state.value.devices.filter { it.attached && it.spec.id !in ids }.forEach { MarineDeviceBus.detach(it.spec.id, "recorded detach") }
                        devices.forEach { spec -> specs[spec.id] = spec; val existing = MarineDeviceBus.state.value.devices.firstOrNull { it.spec.id == spec.id && it.attached }; if (existing == null || existing.spec != spec.copy(backend = com.yokuli.runtime.contract.device.DeviceBackend.REPLAY)) attach(spec) }
                    }
                    "frame" -> if (event.atMillis >= from) {
                        val frame = requireNotNull(event.frame); require(frame.payload.isValid())
                        val spec = specs[frame.deviceId] ?: error("RECORDING_UNKNOWN_DEVICE")
                        if (MarineDeviceBus.state.value.devices.none { it.spec.id == spec.id && it.attached }) attach(spec)
                        MarineDeviceBus.publish(frame.deviceId, frame.payload,
                            measuredElapsedMillis = origin + (frame.measuredElapsedMillis - header.elapsed), utcMillis = frame.utcMillis)
                    }
                    "power" -> event.power?.let { VirtualHostServices.power(it) }
                    "storage" -> event.storage?.let { VirtualHostServices.storage(it) }
                    "command" -> Unit // 审计记录不会重新执行开航、守锚或外部输出命令。
                    else -> error("RECORDING_EVENT_UNKNOWN")
                }
                if (event.atMillis >= from) progress(event.atMillis)
            }
        }
    }
    /** 截断尾行不执行，避免进程死亡产生半个输入帧；长度在分配之前设限。 */
    private fun boundedLine(stream: java.io.InputStream, maximum: Int = 64_000): String? {
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val byte = stream.read(); if (byte < 0) return null
            if (byte == 10) return out.toString(Charsets.UTF_8.name())
            require(out.size() < maximum) { "RECORDING_LINE_TOO_LARGE" }; out.write(byte)
        }
    }
}
