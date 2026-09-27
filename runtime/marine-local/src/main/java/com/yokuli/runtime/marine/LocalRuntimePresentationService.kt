package com.yokuli.runtime.marine

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.yokuli.anchorwatch.runtime.notification.NotificationCoordinator
import com.yokuli.anchorwatch.runtime.notification.NotificationUnitFormats
import com.yokuli.runtime.contract.RuntimePresentationService
import com.yokuli.runtime.contract.RuntimeUnitPreferences
import com.yokuli.shell.contract.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 只在 Marine Core 构造；这份投影不反写 Shell 设置，也不拥有传感器。 */
@Singleton
class LocalRuntimePresentationService @Inject constructor(
    @ApplicationContext context: Context,
    private val notifications: NotificationCoordinator,
) : RuntimePresentationService {
    private val file = AtomicFile(File(context.filesDir, "runtime-presentation-v1.json"))
    private val gson = Gson()
    private val mutex = Mutex()
    init {
        val restored = runCatching {
            file.openRead().bufferedReader().use { gson.fromJson(it, RuntimeUnitPreferences::class.java) }
        }.getOrNull() ?: RuntimeUnitPreferences()
        install(restored)
    }
    override suspend fun updateUnits(preferences: RuntimeUnitPreferences) = withContext(Dispatchers.IO) {
        mutex.withLock {
            // 先验证并构造同一套格式化器；未知枚举不能写入投影。
            val formats = formats(preferences)
            val stream = file.startWrite()
            try {
                val bytes = gson.toJson(preferences).toByteArray(Charsets.UTF_8)
                stream.write(bytes)
                stream.fd.sync()
                file.finishWrite(stream)
                check(file.openRead().use { it.readBytes() }.contentEquals(bytes)) { "UNIT_PROJECTION_WRITE_NOT_CONFIRMED" }
            } catch (error: Exception) { file.failWrite(stream); throw error }
            notifications.installUnitFormats(NotificationUnitFormats(formats::length, formats::depth, formats::speed))
        }
    }
    private fun install(value: RuntimeUnitPreferences) {
        val formats = runCatching { formats(value) }.getOrElse { MarineUnitFormats(MarineUnitPreferences()) }
        notifications.installUnitFormats(NotificationUnitFormats(formats::length, formats::depth, formats::speed))
    }
    private fun formats(value: RuntimeUnitPreferences) = MarineUnitFormats(MarineUnitPreferences(
        navigation = MeasurementUnitSystem.valueOf(value.navigation), length = LengthUnit.valueOf(value.length),
        depth = DepthUnit.valueOf(value.depth), temperature = TemperatureUnit.valueOf(value.temperature),
        pressure = PressureUnit.valueOf(value.pressure), distance = DistanceUnit.valueOf(value.distance),
        speed = SpeedUnit.valueOf(value.speed), scaleDistance = value.scaleDistance?.let(DistanceUnit::valueOf),
    ))
}
