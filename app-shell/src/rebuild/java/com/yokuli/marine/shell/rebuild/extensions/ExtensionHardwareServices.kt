package com.yokuli.marine.shell.rebuild.extensions

import com.google.gson.Gson
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.runtime.contract.hardware.*
import com.yokuli.runtime.contract.time.MarineTime
import org.json.JSONObject
import org.json.JSONArray
import java.security.MessageDigest

/** 公开硬件接口只传输合同 DTO；不暴露私有 Binder、反射服务、文件路径或驱动实例。 */
internal class ExtensionHardwareServices(private val os: OsStore, private val appId: String) {
    private val gson = Gson()
    private val service get() = os.marine?.system?.hardwareLab
        ?: throw ExtensionBridgeException("CORE_UNAVAILABLE", "Marine Core is not connected")

    fun clockJson(): JSONObject {
        val clock = MarineTime.snapshot()
        return JSONObject().put("virtual", clock.virtual).put("paused", clock.paused).put("rate", clock.rate)
            .put("epoch", clock.timeEpoch).put("utcMillis", clock.utcMillis).put("elapsedMillis", clock.elapsedMillis)
            .put("hostElapsedMillis", clock.hostElapsedMillis)
    }

    suspend fun request(method: String, params: JSONObject): JSONObject = when (method) {
        "hardware.snapshot" -> JSONObject(gson.toJson(service.state.value)).put("clock", clockJson()).put("faultCapabilities", JSONObject().apply {
            service.state.value.devices.forEach { device -> put(device.id, JSONArray().apply {
                HardwareFaultPolicy.allowed(device.id).forEach { fault ->
                    val range = HardwareFaultPolicy.magnitudeRange(fault)
                    put(JSONObject().put("type", fault.name).put("unit", HardwareFaultPolicy.magnitudeUnit(fault))
                        .put("minimum", range?.start ?: JSONObject.NULL).put("maximum", range?.endInclusive ?: JSONObject.NULL)
                        .put("defaultMagnitude", HardwareFaultPolicy.defaultMagnitude(fault)))
                }
            }) }
        })
        "hardware.readRecording" -> {
            val id = string(params, "id", 120)
            val offset = integer(params, "offset", 0, 0..Long.MAX_VALUE)
            val limit = integer(params, "limit", 48_000, 4L..48_000L).toInt()
            JSONObject(gson.toJson(service.readRecording(id, offset, limit)))
        }
        "hardware.control" -> {
            val action = enum<LabAction>(string(params, "action", 40))
            val clientId = string(params, "requestId", 80)
            if (!clientId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) invalid("requestId requires 1–80 ASCII letters, digits, underscores or hyphens")
            val requestId = "ext:" + MessageDigest.getInstance("SHA-256").digest((appId + '\u0000' + clientId).toByteArray()).joinToString("") { "%02x".format(it) }
            val deviceId = if (action in setOf(LabAction.ATTACH, LabAction.DETACH, LabAction.SET_FAULT, LabAction.CLEAR_FAULT)) string(params, "deviceId", 120) else ""
            val fault = if (action == LabAction.SET_FAULT) {
                val value = params.optJSONObject("fault") ?: invalid("fault must be an object")
                HardwareFault(deviceId, enum<DeviceFault>(string(value, "type", 40)), number(value, "magnitude", 0.0))
            } else null
            val power = if (action == LabAction.SET_POWER) {
                val value = params.optJSONObject("power") ?: invalid("power must be an object")
                HardwarePower(integer(value, "percent", 100, 0L..100L).toInt(), boolean(value, "external", true),
                    boolean(value, "charging", true), integer(value, "thermal", 0, 0L..6L).toInt(), boolean(value, "screenOn", true), true)
            } else null
            // 保存场景走 Core 的同一 JSON 校验入口。缺失的数值不会由网页自行补成有效数据。
            val scenarioJson = if (action in setOf(LabAction.IMPORT_SCENARIO, LabAction.SAVE_SCENARIO)) {
                val value = params.optJSONObject("scenario") ?: invalid("scenario must be an object")
                value.toString().also { if (it.length > 64_000) invalid("SDK scenario exceeds 64 KiB") }
            } else null
            val command = HardwareLabCommand(
                action = if (action == LabAction.SAVE_SCENARIO) LabAction.IMPORT_SCENARIO else action,
                requestId = requestId, deviceId = deviceId,
                recordingId = if (action in setOf(LabAction.ENTER_REPLAY, LabAction.DELETE_RECORDING)) string(params, "recordingId", 120) else "",
                rate = if (action == LabAction.SET_RATE) number(params, "rate", 1.0).also { if (it !in 0.01..600.0) invalid("rate must be 0.01–600") } else 1.0,
                stepMillis = if (action == LabAction.STEP) integer(params, "stepMillis", 1000, 1L..60_000L) else 1000,
                name = if (params.has("name")) string(params, "name", 80, allowBlank = true) else "",
                scenarioJson = scenarioJson, fault = fault, power = power,
                storageFault = if (action == LabAction.SET_STORAGE) enum<StorageFault>(string(params, "storageFault", 40)) else StorageFault.NONE,
            )
            val result = service.execute(command)
            JSONObject(gson.toJson(result)).put("requestId", clientId)
        }
        else -> throw ExtensionBridgeException("METHOD_NOT_FOUND", "Unsupported hardware method")
    }
    private fun string(params: JSONObject, key: String, limit: Int, allowBlank: Boolean = false): String {
        val value = params.opt(key) as? String ?: invalid("$key must be a string")
        if ((!allowBlank && value.isBlank()) || value.length > limit || value.any { it.code < 32 }) invalid("Invalid $key")
        return value
    }
    private fun number(params: JSONObject, key: String, fallback: Double): Double {
        if (!params.has(key)) return fallback
        val value = (params.opt(key) as? Number)?.toDouble() ?: invalid("$key must be a number")
        if (!value.isFinite()) invalid("$key must be finite")
        return value
    }
    private fun integer(params: JSONObject, key: String, fallback: Long, range: LongRange): Long {
        if (!params.has(key)) return fallback
        val value = number(params, key, fallback.toDouble())
        if (value % 1.0 != 0.0 || value > 9_007_199_254_740_991.0 || value < 0.0 || value.toLong() !in range) invalid("Invalid $key")
        return value.toLong()
    }
    private fun boolean(params: JSONObject, key: String, fallback: Boolean): Boolean = if (!params.has(key)) fallback
        else params.opt(key) as? Boolean ?: invalid("$key must be a boolean")
    private inline fun <reified T : Enum<T>> enum(value: String): T = enumValues<T>().firstOrNull { it.name == value } ?: invalid("Unsupported ${T::class.simpleName}: $value")
    private fun invalid(message: String): Nothing = throw ExtensionBridgeException("INVALID_ARGUMENT", message)
}
