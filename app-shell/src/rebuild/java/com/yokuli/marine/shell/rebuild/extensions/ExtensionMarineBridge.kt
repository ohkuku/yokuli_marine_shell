package com.yokuli.marine.shell.rebuild.extensions

import android.os.SystemClock
import com.yokuli.anchorwatch.domain.vessel.VesselDataFreshness
import com.yokuli.anchorwatch.domain.vessel.VesselDataSnapshot
import com.yokuli.anchorwatch.domain.vessel.VesselDataSource
import com.yokuli.anchorwatch.domain.vessel.VesselMetricId
import com.yokuli.anchorwatch.domain.vessel.VesselObservation
import com.yokuli.anchorwatch.domain.vessel.VesselReference
import com.yokuli.anchorwatch.domain.vessel.source.MetricSourceEligibility
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.ui.displayMetricUnit
import com.yokuli.marine.shell.rebuild.ui.displayMetricValue
import com.yokuli.marine.shell.rebuild.ui.formatCoordinates
import com.yokuli.runtime.contract.RuntimeConnection
import com.yokuli.runtime.contract.RuntimeReadiness
import com.yokuli.shell.engine.ShellVisualSurface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 可对开发者展示的稳定错误码，不把宿主异常堆栈暴露给扩展页面。 */
class ExtensionBridgeException(
    val code: String,
    override val message: String,
    val retryAfterMillis: Long? = null,
) : IllegalStateException(message)

/**
 * 应用通过公开调用表使用获准的系统能力；不取得 MarineSystem 对象、连接控制器或任意反射入口。
 * 一次宿主访问绑定安装版本及授权快照；升级、卸载重装或改权后必须重新建立访问。
 */
class ExtensionMarineBridge(private val os: OsStore, private val installed: ExtensionInstalled) {
    private val requests = RateLimit(20)
    private val writes = RateLimit(5)
    private val snapshots = RateLimit(1)
    private val systemServices = ExtensionSystemServices(os, installed.manifest.id) {
        // 查询账本等挂起操作之后，写入之前再次验证，撤权不能因一次旧检查而失效。
        requireSession(); requireForeground(); requireCoreReady()
    }

    suspend fun request(method: String, params: JSONObject): JSONObject = withContext(Dispatchers.Main.immediate) {
        requireSession()
        val now = SystemClock.elapsedRealtime()
        requests.accept(now)
        val contract = ExtensionSdkContract.methods[method]
            ?: throw ExtensionBridgeException("METHOD_NOT_FOUND", "Unsupported SDK method")
        if (contract.since > installed.manifest.sdk) {
            throw ExtensionBridgeException("SDK_VERSION_REQUIRED", "This method requires manifest sdk=${contract.since}")
        }
        contract.permission?.let(::requirePermission)
        if (contract.foregroundOnly) requireForeground()
        if (contract.needsCore) requireCoreReady()
        if (contract.mutation) writes.accept(now)
        val result = try { when (method) {
            "system.info" -> systemInfo()
            "system.services" -> systemCatalog()
            "devices.snapshot" -> deviceCatalog()
            "storage.get" -> storageOperation {
                JSONObject().put("value", os.extensions.readStorage(installed.manifest.id,
                    expectedDigest = installed.digest, expectedInstalledAt = installed.installedAt,
                    expectedGrants = installed.grants, expectedAuthorizationEpoch = installed.authorizationEpoch))
            }
            "storage.set" -> {
                val value = params.optJSONObject("value") ?: invalid("value must be a JSON object")
                storageOperation {
                    os.extensions.writeStorage(installed.manifest.id, value,
                        expectedDigest = installed.digest, expectedInstalledAt = installed.installedAt,
                        expectedGrants = installed.grants, expectedAuthorizationEpoch = installed.authorizationEpoch)
                    JSONObject().put("saved", true)
                }
            }
            "marine.snapshot" -> {
                requirePermission("marine.read")
                val keys = requestedReadings(params)
                snapshots.accept(now)
                marineSnapshot(keys)
            }
            "nmea.connections" -> {
                requirePermission("nmea.read")
                nmeaConnections()
            }
            "navigation.open" -> {
                requirePermission("navigation.open")
                openDestination(params)
            }
            else -> systemServices.request(method, params)
        } } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ExtensionBridgeException) {
            throw failure
        } catch (failure: com.yokuli.runtime.marine.ipc.MarineCoreUnavailableException) {
            throw ExtensionBridgeException(if (failure.outcomeUnknown) "OUTCOME_UNKNOWN" else "CORE_UNAVAILABLE",
                if (failure.outcomeUnknown) "The operation result is unknown; check system state or the original receipt before retrying"
                else "Marine Core is reconnecting; wait before trying again")
        }
        // 安装注册表在 IO 上原子更新；计算期间撤权的结果也不能交付给旧页面。
        requireSession()
        result
    }

    private fun requireSession(): ExtensionInstalled {
        val current = os.extensions.installed.value.firstOrNull { it.manifest.id == installed.manifest.id }
        if (current == null || current.digest != installed.digest || current.installedAt != installed.installedAt ||
            current.grants != installed.grants || current.authorizationEpoch != installed.authorizationEpoch) {
            throw ExtensionBridgeException("SESSION_EXPIRED", "The app installation or its permissions changed; reopen the app")
        }
        return current
    }

    private fun requirePermission(permission: String) {
        if (permission !in requireSession().grants) {
            throw ExtensionBridgeException("PERMISSION_DENIED", "The app has not been granted $permission")
        }
    }

    private fun isForeground(): Boolean {
        val state = os.shell.engine.state.value
        val task = (state.surface as? ShellVisualSurface.Module)?.let { state.tasks.task(it.taskId) }
        return task != null && os.shell.visibleRouteForTask(task) == "extension:${installed.manifest.id}" &&
            !os.notificationShade.blocksInput && !os.shell.tileWorkshop.blocksInput
    }

    private fun requireForeground() {
        if (!isForeground()) throw ExtensionBridgeException("NOT_FOREGROUND", "Return to this application before changing system state")
    }

    private fun requireCoreReady() {
        if (!coreIsReady()) {
            throw ExtensionBridgeException("CORE_UNAVAILABLE", "Marine Core is initializing or reconnecting")
        }
    }

    private fun coreIsReady(): Boolean {
        val system = os.marine?.system ?: return false
        val state = system.services.state.value
        val generated = state.vesselData.generatedElapsedRealtime
        // Binder 握手就绪与初始读模型到达是两个时刻，不能拿默认/上次进程的设置下命令。
        return system.connection.value.readiness == RuntimeReadiness.READY && state.settingsReady &&
            generated > 0L && SystemClock.elapsedRealtime() - generated in 0L..2_000L
    }

    private suspend fun storageOperation(block: suspend () -> JSONObject): JSONObject = try {
        block().also { requireSession() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: ExtensionBridgeException) {
        throw failure
    } catch (_: Exception) {
        requireSession()
        throw ExtensionBridgeException("STORAGE_FAILURE", "App storage could not be read or saved")
    }

    private fun systemInfo() = JSONObject()
        .put("sdk", ExtensionSdkContract.VERSION)
        .put("appSdk", installed.manifest.sdk)
        .put("packageFormat", ExtensionSdkContract.PACKAGE_FORMAT)
        .put("language", if (os.chinese) "zh-CN" else "en")
        .put("theme", if (os.light) "light" else "dark")
        .put("units", JSONObject()
            .put("distance", os.unitFormats.distanceUnit)
            .put("speed", os.unitFormats.speedUnit)
            .put("depth", os.unitFormats.depthUnit)
            .put("pressure", os.unitFormats.pressureUnit)
            .put("temperature", os.unitFormats.temperatureUnit)
            .put("coordinates", os.coordinateFormat))

    /** 能力发现只列真实接线的方法；未来时钟/回放不能因存在设计文档而报告为可用。 */
    private fun systemCatalog(): JSONObject {
        val connection = os.marine?.system?.connection?.value
        val coreReady = coreIsReady()
        val rows = JSONArray()
        ExtensionSdkContract.methods.values.forEach { method ->
            val declared = method.permission == null || method.permission in installed.manifest.permissions
            val granted = method.permission == null || method.permission in installed.grants
            val sdkCompatible = installed.manifest.sdk >= method.since
            rows.put(JSONObject().put("name", method.name).put("since", method.since)
                .put("permission", nullable(method.permission)).put("declared", declared).put("granted", granted)
                .put("available", sdkCompatible && granted &&
                    (!method.needsCore || coreReady) &&
                    (!method.foregroundOnly || isForeground()))
                .put("foregroundOnly", method.foregroundOnly))
        }
        return JSONObject().put("sdk", ExtensionSdkContract.VERSION).put("appSdk", installed.manifest.sdk)
            .put("packageFormat", ExtensionSdkContract.PACKAGE_FORMAT).put("connection", connectionJson(connection))
            .put("methods", rows).put("runtime", JSONObject()
                .put("deviceBackends", JSONArray(listOf("REAL"))).put("virtualClock", false)
                .put("systemReplay", false).put("scenarioEngine", false)
                .put("backgroundScripts", false).put("nativeApk", false))
    }

    private fun deviceCatalog(): JSONObject {
        val system = os.marine?.system
        val connection = system?.connection?.value
        val snapshot = system?.devices?.state?.value
        val now = SystemClock.elapsedRealtime()
        val snapshotAge = snapshot?.capturedElapsedRealtime?.takeIf { it > 0L }?.let { now - it }
        val ready = snapshot?.ready == true && connection?.readiness == RuntimeReadiness.READY &&
            snapshotAge != null && snapshotAge in 0L..2_000L
        val rows = JSONArray()
        // 断线时可以保留设备身份帮助用户理解，但旧健康值绝不能变成当前运行证据。
        snapshot?.devices?.forEach { device ->
            rows.put(JSONObject().put("id", device.id).put("name", device.name)
                .put("kind", device.kind.name).put("backend", device.backend.name)
                .put("availability", device.availability.name).put("health", if (ready) device.health.name else "UNKNOWN")
                .put("requested", device.requested).put("active", ready && device.active)
                .put("generation", device.generation).put("generationOrigin", device.generationOrigin)
                .put("capabilities", JSONArray(device.capabilities))
                .put("lastMeasuredElapsedMillis", nullable(device.lastMeasuredElapsedRealtime))
                .put("lastReceivedElapsedMillis", nullable(device.lastReceivedElapsedRealtime))
                .put("lastOutputElapsedMillis", nullable(device.lastOutputElapsedRealtime))
                .put("measurementAgeMillis", nullable(device.lastMeasuredElapsedRealtime?.let { now - it }?.takeIf { it >= 0L }))
                .put("provenance", JSONObject().put("driver", device.provenance.driver)
                    .put("sourceId", device.provenance.sourceId).put("connectionId", nullable(device.provenance.connectionId)))
                .put("reason", nullable(device.reason)))
        }
        return JSONObject().put("connection", connectionJson(connection)).put("ready", ready)
            .put("runtimeId", snapshot?.runtimeId.orEmpty()).put("revision", snapshot?.revision ?: 0L)
            .put("capturedElapsedMillis", nullable(snapshot?.capturedElapsedRealtime?.takeIf { it > 0L }))
            .put("ageMillis", nullable(snapshotAge?.takeIf { it >= 0L }))
            .put("devices", rows).put("error", nullable(when {
                ready -> null
                snapshot?.error != null -> snapshot.error
                connection?.readiness != RuntimeReadiness.READY -> connection?.reason ?: "CORE_UNAVAILABLE"
                else -> "DEVICE_CATALOG_STALE"
            }))
    }

    private fun requestedReadings(params: JSONObject): List<String> {
        if (!params.has("readings")) return readingDefinitions.keys.toList()
        val array = params.optJSONArray("readings") ?: invalid("readings must be an array")
        if (array.length() > readingDefinitions.size) invalid("Too many reading keys")
        return List(array.length()) { index ->
            val key = array.opt(index) as? String ?: invalid("Reading keys must be strings")
            if (key !in readingDefinitions) invalid("Unsupported reading key: $key")
            key
        }.distinct()
    }

    private fun marineSnapshot(keys: List<String>): JSONObject {
        val system = os.marine?.system
        val data = system?.services?.state?.value?.vesselData ?: VesselDataSnapshot()
        val connection = system?.connection?.value
        val now = SystemClock.elapsedRealtime()
        // Core 250ms 发布唯一读模型；重连前遗留的 Flow 值不能因为连接恢复就重新算作实时。
        val snapshotAge = data.generatedElapsedRealtime.takeIf { it > 0L }
            ?.let { now - it }?.takeIf { it >= 0L }
        val snapshotCurrent = connection?.readiness == RuntimeReadiness.READY &&
            snapshotAge != null && snapshotAge <= 2_000L
        val position = data.position
        val point = position.value?.takeIf {
            it.latitude.isFinite() && it.longitude.isFinite() &&
                it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
        }
        val vessel = observationMetadata(position, VesselMetricId.POSITION, now, snapshotCurrent, point != null)
            .put("latitude", nullable(point?.latitude))
            .put("longitude", nullable(point?.longitude))
            .put("positionDisplay", nullable(point?.let { os.formatCoordinates(GeoPoint(it.latitude, it.longitude)) }))
        val readings = JSONObject()
        keys.forEach { key ->
            val definition = readingDefinitions.getValue(key)
            val observation = definition.read(data)
            val raw = observation.value?.takeIf(Double::isFinite)
            val display = raw?.let { os.displayMetricValue(key, it) }?.takeIf(Double::isFinite)
            readings.put(key, observationMetadata(observation, definition.metric, now, snapshotCurrent, raw != null)
                .put("value", nullable(display))
                .put("unit", os.displayMetricUnit(key))
                .put("rawValue", nullable(raw))
                .put("rawUnit", definition.rawUnit))
        }
        return JSONObject().put("connection", connectionJson(connection))
            .put("snapshotAgeMillis", nullable(snapshotAge)).put("snapshotCurrent", snapshotCurrent)
            // capturedAt 只是快照组装时刻；不能作为任一观测的测量时刻。
            .put("capturedAt", System.currentTimeMillis()).put("vessel", vessel).put("readings", readings)
    }

    private fun observationMetadata(
        value: VesselObservation<*>, metric: VesselMetricId, now: Long, ready: Boolean, hasValue: Boolean,
    ): JSONObject {
        val age = value.receivedElapsedRealtime?.let { now - it }?.takeIf { it >= 0L }
        val freshness = when {
            !hasValue -> VesselDataFreshness.UNAVAILABLE
            !ready || age == null -> VesselDataFreshness.STALE
            // HELD 由 Core 判定；对缺字段报文允许保留值，但绝不刷新测量年龄。
            value.freshness == VesselDataFreshness.FRESH && age > MetricSourceEligibility.measurementLeaseMillis(metric) -> VesselDataFreshness.STALE
            else -> value.freshness
        }
        val source = value.sourceIdentity?.let {
            JSONObject().put("id", it.id).put("name", it.displayName).put("type", it.sourceType.name)
                .put("class", value.sourceClass.name).put("connectionId", nullable(it.transportProfileId))
                .put("generation", nullable(it.connectionGeneration))
        } ?: value.source.takeUnless { it == VesselDataSource.NONE }?.let {
            // 兼容来源没有可审计 ID 时保留类别，不能伪造一个设备身份。
            JSONObject().put("id", JSONObject.NULL).put("name", it.name).put("type", JSONObject.NULL)
                .put("class", value.sourceClass.name).put("connectionId", JSONObject.NULL).put("generation", JSONObject.NULL)
        }
        val reference = when (val reference = value.reference) {
            VesselReference.TrueNorth -> "TRUE_NORTH"
            VesselReference.MagneticNorth -> "MAGNETIC_NORTH"
            VesselReference.WaterReferenced -> "WATER_REFERENCED"
            VesselReference.GroundReferenced -> "GROUND_REFERENCED"
            VesselReference.VesselRelative -> "VESSEL_RELATIVE"
            is VesselReference.Depth -> "DEPTH_${reference.reference.name}"
            null -> null
        }
        return JSONObject().put("measuredAt", nullable(value.observedAtUtcMillis))
            .put("measuredElapsedMillis", nullable(value.receivedElapsedRealtime))
            .put("ageMillis", nullable(age)).put("quality", value.quality.name)
            .put("freshness", freshness.name).put("sourceFreshness", value.freshness.name)
            .put("source", nullable(source)).put("reference", nullable(reference))
    }

    private fun nmeaConnections(): JSONObject {
        val system = os.marine?.system
        val connections = system?.services?.network?.connections?.value.orEmpty()
        val connection = system?.connection?.value
        val now = SystemClock.elapsedRealtime()
        val devices = system?.devices?.state?.value
        val currentDevices = connection?.readiness == RuntimeReadiness.READY && devices?.ready == true &&
            now - devices.capturedElapsedRealtime in 0L..2_000L
        val rows = JSONArray()
        connections.forEach { item ->
            val receivedAt = item.lastLegalSentenceElapsed
            rows.put(JSONObject().put("id", item.spec.id).put("name", item.spec.name)
                .put("transport", item.spec.protocol.name).put("state", item.state.name)
                .put("current", currentDevices && devices?.devices?.any { it.id == "nmea:${item.spec.id}" &&
                    it.generation == item.transport.connectionGeneration && it.requested == item.requested } == true)
                .put("receiveEnabled", item.spec.receive).put("sendEnabled", item.spec.send)
                .put("requested", item.requested)
                .put("received", item.diagnostics.validSentences).put("sent", item.writtenSentences)
                // Core 只记录单调接收时间，SDK 不用当前墙钟补造过去的 UTC。
                .put("lastReceivedAt", JSONObject.NULL).put("lastReceivedElapsedMillis", nullable(receivedAt))
                .put("lastReceivedAgeMillis", nullable(receivedAt?.let { now - it }?.takeIf { it >= 0L })))
        }
        return JSONObject().put("connection", connectionJson(connection)).put("connections", rows).put("readOnly", true)
    }

    private fun openDestination(params: JSONObject): JSONObject {
        val target = params.opt("target") as? String ?: invalid("target must be an application name")
        if (target !in navigationTargets) invalid("This navigation target is not available to extensions")
        val state = os.shell.engine.state.value
        val task = (state.surface as? ShellVisualSurface.Module)?.let { state.tasks.task(it.taskId) }
        val same = task != null && os.shell.visibleRouteForTask(task) == target
        if (!same) {
            // 退出动画期间 WebView 可能仍挂在视图树上，后台脚本不能借此劫持新访问。
            if (task == null || os.shell.visibleRouteForTask(task) != "extension:${installed.manifest.id}") {
                throw ExtensionBridgeException("NOT_FOREGROUND", "Only the visible app can open another application")
            }
            os.shell.openLinked(target)
        }
        return JSONObject().put("opened", !same).put("target", target)
    }

    private fun connectionJson(connection: RuntimeConnection?) = JSONObject()
        .put("state", (connection?.readiness ?: RuntimeReadiness.INITIALIZING).name)
        .put("transport", nullable(connection?.transport?.name))

    /** 宿主主线程串行访问；滚动窗口不产生额外协程、计时器或后台数据订阅。 */
    private class RateLimit(private val maximum: Int) {
        private val accepted = ArrayDeque<Long>()
        fun accept(now: Long) {
            while (accepted.isNotEmpty() && now - accepted.first() >= 1_000L) accepted.removeFirst()
            if (accepted.size >= maximum) throw ExtensionBridgeException("RATE_LIMITED",
                "Too many SDK requests; retry later", (1_000L - (now - accepted.first())).coerceAtLeast(1L))
            accepted.addLast(now)
        }
    }

    private data class ReadingDefinition(
        val metric: VesselMetricId, val rawUnit: String, val read: (VesselDataSnapshot) -> VesselObservation<Double>,
    )

    companion object {
        private val navigationTargets = setOf("chart", "library", "data_center", "nmea", "ais", "voyages", "anchor",
            "places", "instruments", "local_nmea", "settings", "app_center")
        private val readingDefinitions = linkedMapOf(
            "sog" to ReadingDefinition(VesselMetricId.SOG, "kn") { it.sogKnots },
            "cog" to ReadingDefinition(VesselMetricId.COG, "degree") { it.cogTrueDegrees },
            "heading" to ReadingDefinition(VesselMetricId.HEADING_TRUE, "degree") { it.headingTrueDegrees },
            "depth" to ReadingDefinition(VesselMetricId.DEPTH, "m") { it.depthMeters },
            "tws" to ReadingDefinition(VesselMetricId.TRUE_WIND_SPEED, "kn") { it.trueWind.speedKnots },
            "twd" to ReadingDefinition(VesselMetricId.TRUE_WIND_DIRECTION, "degree") { it.trueWind.directionDegrees },
            "aws" to ReadingDefinition(VesselMetricId.APPARENT_WIND_SPEED, "kn") { it.apparentWind.speedKnots },
            "awa" to ReadingDefinition(VesselMetricId.APPARENT_WIND_ANGLE, "degree") { it.apparentWind.angleDegrees },
            "pressure" to ReadingDefinition(VesselMetricId.PRESSURE, "hPa") { it.pressureHpa },
            "air" to ReadingDefinition(VesselMetricId.AIR_TEMPERATURE, "degC") { it.airTemperatureCelsius },
            "water" to ReadingDefinition(VesselMetricId.WATER_TEMPERATURE, "degC") { it.waterTemperatureCelsius },
            "heel" to ReadingDefinition(VesselMetricId.HEEL, "degree") { it.heelDegrees },
            "pitch" to ReadingDefinition(VesselMetricId.PITCH, "degree") { it.pitchDegrees },
        )
        private fun nullable(value: Any?): Any = value ?: JSONObject.NULL
        private fun invalid(message: String): Nothing = throw ExtensionBridgeException("INVALID_ARGUMENT", message)
    }
}
