package com.yokuli.anchorwatch.runtime.sensor

import com.yokuli.anchorwatch.location.PhoneHeadingRepository
import com.yokuli.anchorwatch.location.PhoneMotionRepository
import com.yokuli.anchorwatch.location.vessel.PhonePressureRepository
import com.yokuli.anchorwatch.location.vessel.PhoneVesselAttitudeRepository
import javax.inject.Inject
import javax.inject.Singleton

data class SensorRuntimeState(
    val phoneMotionActive: Boolean = false,
    val phoneHeadingActive: Boolean = false,
    val phonePressureActive: Boolean = false,
    val deviceViewOrientationActive: Boolean = false,
)

/**
 * Sole lifecycle gateway for optional phone motion and heading sensors.
 *
 * Runtime owners express requirements through RuntimeResourceManager; they do
 * not start or stop Android sensors directly. Existing heading evidence is
 * persisted with the anchor session, so stopping this runtime only prevents
 * new sensor samples from being collected.
 */
@Singleton
class SensorRuntime @Inject constructor(
    private val phoneMotion: PhoneMotionRepository,
    private val phoneHeading: PhoneHeadingRepository,
    private val vesselAttitude:PhoneVesselAttitudeRepository,
    private val phonePressure:PhonePressureRepository,
) {
    @Synchronized
    fun reconcile(needsPhoneMotion: Boolean, needsPhoneHeading: Boolean, needsPhonePressure: Boolean, needsDeviceViewOrientation: Boolean = false): SensorRuntimeState {
        // 同一个旋转矢量监听器供安装姿态与临时视线共享；视线不会启用Heading、定位或发布。
        val attitudeActive = if (needsPhoneMotion || needsDeviceViewOrientation) vesselAttitude.start()
            else { vesselAttitude.stop(); false }
        val integrityActive = if (needsPhoneMotion) phoneMotion.start() else { phoneMotion.stop(); false }
        val motionActive = needsPhoneMotion && (integrityActive || attitudeActive)
        val headingActive = if (needsPhoneHeading) {
            phoneHeading.start()
        } else {
            phoneHeading.stop()
            false
        }
        val pressureActive=if(needsPhonePressure)phonePressure.start() else{phonePressure.stop();false}
        return SensorRuntimeState(motionActive, headingActive, pressureActive, needsDeviceViewOrientation && attitudeActive)
    }

    @Synchronized
    fun stop(): SensorRuntimeState = reconcile(false, false, false)
}
