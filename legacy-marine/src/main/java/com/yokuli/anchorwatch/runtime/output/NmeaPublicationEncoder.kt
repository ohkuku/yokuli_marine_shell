package com.yokuli.anchorwatch.runtime.output

import com.yokuli.anchorwatch.data.NavigationRepository
import com.yokuli.anchorwatch.data.nmea.*
import com.yokuli.anchorwatch.data.sharing.NmeaOutputMux
import com.yokuli.anchorwatch.data.vessel.*
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.location.vessel.PhoneVesselAttitudeRepository
import com.yokuli.anchorwatch.location.vessel.VesselMountCalibration
import com.yokuli.anchorwatch.location.vessel.VesselMountCalibrationRepository
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/** 本机服务与各条发送连接共享的编码边界：能力选择、来源真实性、时效和同 IP 防回送全部在此生效。 */
@Singleton class NmeaPublicationEncoder @Inject constructor(
    private val navigation: NavigationRepository,
    private val hub: VesselDataHub,
    private val positions: VesselPositionRepository,
    private val mux: NmeaOutputMux,
    private val phoneEncoder: AnchorWatchNmeaFeedEncoder,
    private val peers: NmeaPeerGuard,
    mount: VesselMountCalibrationRepository,
    private val attitude: PhoneVesselAttitudeRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var calibration = VesselMountCalibration()
    init { scope.launch { mount.calibration.collect { calibration = it } } }

    /** 原始报文保持来源连接和实际发件人；相同 IP 即使换端口也不允许转发。 */
    fun forward(spec: NmeaConnectionSpec, frame: NmeaRawFrame): String? {
        if (frame.connectionId == spec.id || frame.connectionId !in spec.forwardFrom) return null
        val origin = navigation.connections.value.firstOrNull { it.spec.id == frame.connectionId } ?: return null
        if (!origin.requested || navigation.connectionEpoch(frame.connectionId) != frame.generation) return null
        if (returnsTo(spec,frame.peer) || origin.spec.host.takeIf { it.isNotBlank() }?.let { returnsTo(spec,it) } == true) return null
        return NmeaPublicationPolicy.filter(spec, frame.sentence)
    }

    private fun returnsTo(spec:NmeaConnectionSpec,source:String?)=peers.sameHost(source,navigation.connectionPeer(spec.id)?:spec.host)

    private fun ancestry(observation: VesselObservation<*>): List<VesselSourceIdentity> {
        val candidates = hub.snapshot.value.candidates.values.flatten()
        fun leaves(identity: VesselSourceIdentity, seen: Set<String>): List<VesselSourceIdentity> {
            if (identity.persistentKey in seen) return emptyList()
            if (identity.sourceType != VesselSourceType.APP_DERIVED) return listOf(identity)
            val parent = candidates.firstOrNull { it.source.persistentKey == identity.persistentKey } ?: return emptyList()
            val origin = parent.provenance as? VesselProvenance.Derived ?: return emptyList()
            val parts = origin.inputs.map { leaves(it, seen + identity.persistentKey) }
            return if (parts.any { it.isEmpty() }) emptyList() else parts.flatten()
        }
        val origins = when (val origin = observation.provenanceDetail) {
            is VesselProvenance.Derived -> origin.inputs
            is VesselProvenance.Nmea -> listOf(origin.source)
            else -> listOfNotNull(observation.sourceIdentity)
        }
        val parts = origins.map { leaves(it, emptySet()) }
        return if (parts.any { it.isEmpty() }) emptyList() else parts.flatten()
    }

    private fun allowed(value: VesselObservation<*>, spec: NmeaConnectionSpec, now: Long): Boolean {
        if (value.value == null || value.source in setOf(VesselDataSource.DEMO, VesselDataSource.NONE) || value.receivedElapsedRealtime?.let { now - it < 0 } != false) return false
        if (value.freshness !in setOf(VesselDataFreshness.FRESH, VesselDataFreshness.HELD)) return false
        val origins = ancestry(value)
        if (origins.isEmpty()) return false
        return origins.none { origin ->
            origin.transportProfileId == spec.id || returnsTo(spec,origin.transportPeer) ||
                (origin.sourceType == VesselSourceType.NMEA_INPUT && (origin.transportProfileId == null || navigation.connections.value.none { it.spec.id == origin.transportProfileId && it.requested } || navigation.connectionEpoch(origin.transportProfileId) != origin.connectionGeneration)) ||
                origin.transportProfileId?.let { id -> navigation.connections.value.firstOrNull { it.spec.id == id }?.spec?.host?.let { returnsTo(spec,it) } } == true ||
                origin.sourceType in setOf(VesselSourceType.DEMO, VesselSourceType.PHONE_TX_ECHO)
        }
    }

    /** 系统模式只编码 App 当前实际采用的观测；不会为了输出完整而编造缺失量。 */
    fun encode(spec: NmeaConnectionSpec, now: Long): List<String> {
        val snapshot = hub.snapshot.value
        val selected = NmeaPublicationPolicy.selected(spec)
        if (spec.feed == NmeaFeed.PHONE) {
            val config = NmeaDeviceOutputSettings(phonePositionEnabled = "position" in selected, phoneHeadingEnabled = "heading" in selected, phoneRateOfTurnEnabled = "rotation" in selected, phoneAttitudeEnabled = "attitude" in selected, phonePressureEnabled = "pressure" in selected)
            return AnchorWatchNmeaStream.entries.flatMap { stream ->
                phoneEncoder.encode(stream, snapshot, config, now, positions.acceptedPhoneFix.value, inputProfileId = spec.id, mountCalibration = calibration, runtimeMountState = attitude.mountState.value).sentences
            }.mapNotNull { NmeaPublicationPolicy.filter(spec, it) }
        }
        if (spec.feed == NmeaFeed.RAW) return emptyList()
        fun number(value: VesselObservation<Double>) = value.value?.takeIf { it.isFinite() && allowed(value, spec, now) }
        val lines = buildList {
            val position = snapshot.position
            if ("position" in selected && allowed(position, spec, now) && position.freshness == VesselDataFreshness.FRESH) {
                // 必须使用已选观测的原子位置，不能从另一条 NMEA 连接借来位置。
                val current = position.value
                val candidate = if (position.source == VesselDataSource.PHONE_GNSS) positions.acceptedPhoneFix.value else navigation.fix.value
                candidate?.takeIf { current != null && kotlin.math.abs(it.latitude - current.latitude) < 0.000001 && kotlin.math.abs(it.longitude - current.longitude) < 0.000001 }?.let {
                    // 速度和航向也取全局已选观测；其来源若是接收设备自身，就留空而不回送。
                    val fix=it.copy(sogKnots=number(snapshot.sogKnots),cogTrueDegrees=number(snapshot.cogTrueDegrees),sogReceivedElapsedRealtime=snapshot.sogKnots.receivedElapsedRealtime,cogReceivedElapsedRealtime=snapshot.cogTrueDegrees.receivedElapsedRealtime)
                    addAll(mux.acceptedPosition(fix, now))
                }
            }
            if ("heading" in selected) {
                number(snapshot.headingTrueDegrees)?.let { add(mux.phoneHeading(it)) }
                number(snapshot.headingMagneticDegrees)?.let { add(mux.phoneMagneticHeading(it, null)) }
            }
            if ("water_speed" in selected) number(snapshot.speedThroughWaterKnots)?.let { add(mux.canonicalSpeedThroughWater(it)) }
            if ("depth" in selected) number(snapshot.depthMeters)?.let { depth ->
                if ((snapshot.depthMeters.reference as? VesselReference.Depth)?.reference == com.yokuli.anchorwatch.domain.sonar.DepthReference.BELOW_TRANSDUCER) add(mux.canonicalDepth(depth))
            }
            if ("apparent_wind" in selected) {
                val speed = number(snapshot.apparentWind.speedKnots); val angle = number(snapshot.apparentWind.angleDegrees)
                if (speed != null && angle != null) add(NmeaChecksum.append("WIMWV,${format((angle + 360) % 360)},R,${format(speed)},N,A") + "\r\n")
            }
            if ("true_wind" in selected) {
                val speed = number(snapshot.trueWind.speedKnots); val angle = number(snapshot.trueWind.angleDegrees); val direction = number(snapshot.trueWind.directionDegrees)
                if (speed != null && angle != null && direction != null) addAll(mux.derivedTrueWind(speed, direction, angle))
            }
            if ("rotation" in selected) number(snapshot.rateOfTurnDegreesPerMinute)?.let { add(mux.phoneRateOfTurn(it)) }
            // 姿态和气压分别编码，关闭任一项后另一项仍可正常发出。
            if ("pressure" in selected) number(snapshot.pressureHpa)?.let { mux.selectedXdr(null, it)?.let(::add) }
            if ("attitude" in selected) snapshot.attitude.value?.takeIf { allowed(snapshot.attitude, spec, now) }?.let { mux.selectedXdr(it, null)?.let(::add) }
            if ("temperature" in selected) number(snapshot.waterTemperatureCelsius)?.let { add(NmeaChecksum.append("IIMTW,${format(it)},C") + "\r\n") }
        }
        return lines.mapNotNull { NmeaPublicationPolicy.filter(spec, it) }
    }
    private fun format(value: Double) = String.format(java.util.Locale.US, "%.2f", value)
}
