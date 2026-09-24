package com.yokuli.marine.shell.rebuild.scene

import com.yokuli.anchorwatch.data.nmea.NmeaConnectionSnapshot
import com.yokuli.anchorwatch.data.vessel.VesselDataSettings
import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.anchorwatch.domain.vessel.source.MetricSourceEligibility
import com.yokuli.anchorwatch.location.PhoneLocationPhase
import com.yokuli.anchorwatch.location.PhoneLocationStatus
import com.yokuli.anchorwatch.location.vessel.PhoneSensorCapabilities
import com.yokuli.anchorwatch.location.vessel.VesselMountCalibration

/**
 * 单一运行时快照 → 场景读模型。这里不订阅硬件、不保存历史、不仲裁、不执行命令。
 * 当前阶段使用旧运行时的不可变值类型作为输入；所有渲染器共用这一只读适配层。
 */
object VesselScenePresenter {
    val windMetrics: List<VesselMetricId> = listOf(
        VesselMetricId.APPARENT_WIND_SPEED,
        VesselMetricId.APPARENT_WIND_ANGLE,
        VesselMetricId.TRUE_WIND_SPEED,
        VesselMetricId.TRUE_WIND_ANGLE,
        VesselMetricId.TRUE_WIND_DIRECTION,
    )

    fun present(
        snapshot: VesselDataSnapshot,
        settings: VesselDataSettings,
        connections: List<NmeaConnectionSnapshot>,
        phoneStatus: PhoneLocationStatus?,
        mount: VesselMountCalibration,
        capabilities: PhoneSensorCapabilities,
        nowElapsed: Long,
        positionSource: GpsDataSource? = null,
    ): VesselSceneModel {
        val connectionModels = connections.associate { connection ->
            val last = listOfNotNull(
                connection.transport.lastSentenceReceivedElapsedRealtime,
                connection.diagnostics.lastPacketElapsed,
            ).maxOrNull()
            connection.spec.id to VesselSceneConnection(
                id = connection.spec.id,
                name = connection.spec.name,
                state = connection.state,
                requested = connection.requested,
                generation = connection.transport.connectionGeneration,
                lastMessageElapsedRealtime = last,
                messageAgeMillis = age(nowElapsed, last),
                error = connection.error,
            )
        }
        val observations: Map<VesselMetricId, VesselObservation<*>> = linkedMapOf(
            VesselMetricId.POSITION to snapshot.position,
            VesselMetricId.SOG to snapshot.sogKnots,
            VesselMetricId.COG to snapshot.cogTrueDegrees,
            VesselMetricId.HEADING_TRUE to snapshot.headingTrueDegrees,
            VesselMetricId.HEADING_MAGNETIC to snapshot.headingMagneticDegrees,
            VesselMetricId.APPARENT_WIND_SPEED to snapshot.apparentWind.speedKnots,
            VesselMetricId.APPARENT_WIND_ANGLE to snapshot.apparentWind.angleDegrees,
            VesselMetricId.TRUE_WIND_SPEED to snapshot.trueWind.speedKnots,
            VesselMetricId.TRUE_WIND_ANGLE to snapshot.trueWind.angleDegrees,
            VesselMetricId.TRUE_WIND_DIRECTION to snapshot.trueWind.directionDegrees,
            VesselMetricId.DEPTH to snapshot.depthMeters,
            VesselMetricId.UKC to snapshot.derived.underKeelClearanceMeters,
        )
        val metrics = observations.mapValues { (id, observation) ->
            projectMetric(id, observation, snapshot, settings, connectionModels, phoneStatus,
                mount, capabilities, nowElapsed, positionSource)
        }
        // 摘要选择只影响标签；真风和视风、真北和磁北仍各自有独立的完整读数。
        val windPrimary = listOf(VesselMetricId.APPARENT_WIND_SPEED, VesselMetricId.TRUE_WIND_SPEED,
            VesselMetricId.APPARENT_WIND_ANGLE, VesselMetricId.TRUE_WIND_ANGLE, VesselMetricId.TRUE_WIND_DIRECTION)
            .firstOrNull { metrics.getValue(it).hasValue } ?: VesselMetricId.APPARENT_WIND_SPEED
        val headingPrimary = preferredHeadingMetric(snapshot, settings)
        fun hotspot(id: VesselHotspot, primary: VesselMetricId, fields: List<VesselMetricId>) =
            VesselSceneHotspot(id, primary, fields, metrics.getValue(primary).status)
        return VesselSceneModel(
            metrics = metrics,
            hotspots = listOf(
                hotspot(VesselHotspot.WIND, windPrimary, windMetrics),
                hotspot(VesselHotspot.POSITION, VesselMetricId.POSITION, listOf(VesselMetricId.POSITION, VesselMetricId.SOG, VesselMetricId.COG)),
                hotspot(VesselHotspot.HEADING, headingPrimary, listOf(VesselMetricId.HEADING_TRUE, VesselMetricId.HEADING_MAGNETIC)),
                hotspot(VesselHotspot.DEPTH, VesselMetricId.DEPTH, listOf(VesselMetricId.DEPTH, VesselMetricId.UKC)),
            ),
            mount = VesselSceneMount(mount.bowAxis, mount.mountState, mount.mountConfirmed, mount.headingAligned),
            generatedElapsedRealtime = nowElapsed,
        )
    }

    /** 只选择热点摘要及详情入口，不改动任一指标的采用来源；所有调用者使用同一结果。 */
    fun preferredHeadingMetric(snapshot: VesselDataSnapshot, settings: VesselDataSettings): VesselMetricId {
        val order = listOf(VesselMetricId.HEADING_TRUE, VesselMetricId.HEADING_MAGNETIC)
        fun validHeading(value: Double?) = value != null && value.isFinite() && value in 0.0..360.0
        if (validHeading(snapshot.headingTrueDegrees.value)) return VesselMetricId.HEADING_TRUE
        if (validHeading(snapshot.headingMagneticDegrees.value)) return VesselMetricId.HEADING_MAGNETIC
        // 无采用值时仍能找到并修复用户指定的指标，不把候选读数当成当前船首向。
        order.firstOrNull { !settings.metricSourcePins[it.name].isNullOrBlank() }?.let { return it }
        settings.boatHeadingSourceId?.takeIf(String::isNotBlank)?.let { key ->
            order.firstOrNull { metric -> snapshot.candidates[metric].orEmpty().any {
                VesselSourcePinPolicy.matches(it.source, key)
            } }?.let { return it }
        }
        order.firstOrNull { metric -> snapshot.candidates[metric].orEmpty().any { candidate ->
            candidate.validity in setOf(CandidateValidity.ELIGIBLE, CandidateValidity.LOW_QUALITY) &&
                validHeading((candidate.value as? Number)?.toDouble())
        } }?.let { return it }
        return order.firstOrNull { snapshot.candidates[it].orEmpty().isNotEmpty() } ?: order.first()
    }

    private fun projectMetric(
        id: VesselMetricId,
        observation: VesselObservation<*>,
        snapshot: VesselDataSnapshot,
        settings: VesselDataSettings,
        connections: Map<String, VesselSceneConnection>,
        phoneStatus: PhoneLocationStatus?,
        mount: VesselMountCalibration,
        capabilities: PhoneSensorCapabilities,
        now: Long,
        positionSource: GpsDataSource?,
    ): VesselSceneMetric {
        val candidates = snapshot.candidates[id].orEmpty()
        val metricPin = settings.metricSourcePins[id.name]
        val requestedKey = when (id) {
            VesselMetricId.POSITION -> if (positionSource == GpsDataSource.SYSTEM) null
                else metricPin ?: settings.pinnedPositionSourceId
            VesselMetricId.HEADING_TRUE, VesselMetricId.HEADING_MAGNETIC -> metricPin ?: settings.boatHeadingSourceId
            else -> metricPin
        }
        val preference = when (id) {
            VesselMetricId.POSITION -> when (positionSource) {
                GpsDataSource.SYSTEM -> VesselSourcePreference.PHONE
                GpsDataSource.NMEA -> VesselSourcePreference.BOAT
                else -> settings.positionPreference
            }
            VesselMetricId.HEADING_TRUE, VesselMetricId.HEADING_MAGNETIC ->
                if (metricPin != null) VesselSourcePreference.AUTO else settings.headingPreference
            VesselMetricId.SOG, VesselMetricId.COG -> if (metricPin != null) VesselSourcePreference.AUTO else when (positionSource) {
                GpsDataSource.SYSTEM -> VesselSourcePreference.PHONE
                GpsDataSource.NMEA -> VesselSourcePreference.BOAT
                else -> VesselSourcePreference.AUTO
            }
            else -> VesselSourcePreference.AUTO
        }
        val number = (observation.value as? Number)?.toDouble()?.takeIf { it.isFinite() }
        val position = (observation.value as? VesselPosition)?.takeIf(::validPosition)
        val hasValue = number != null || position != null
        val invalidValue = observation.value != null && !hasValue
        val requestedCandidate = requestedKey?.let { key -> candidates.firstOrNull { VesselSourcePinPolicy.matches(it.source, key) } }
        val freshness = displayFreshness(id, observation, hasValue, now)
        val adopted = observation.sourceIdentity.takeIf {
            hasValue && freshness in setOf(VesselDataFreshness.FRESH, VesselDataFreshness.HELD)
        }
        val conflict = (observation.conflict ?: snapshot.conflicts[id])?.takeIf { it.active }
        val selectedUnavailable = requestedKey != null && (requestedCandidate == null ||
            MetricSourceEligibility.evaluate(id, requestedCandidate, now) != CandidateValidity.ELIGIBLE)
        val phoneRelevant = preference == VesselSourcePreference.PHONE ||
            requestedCandidate?.source?.sourceType == VesselSourceType.PHONE_SENSOR ||
            observation.sourceIdentity?.sourceType == VesselSourceType.PHONE_SENSOR
        val issues = buildSet {
            if (invalidValue) add(VesselSceneStatus.INVALID)
            if (id == VesselMetricId.POSITION && positionSource == GpsDataSource.NONE) add(VesselSceneStatus.SOURCE_DISABLED)
            if (id == VesselMetricId.POSITION && phoneRelevant) when (phoneStatus?.phase) {
                PhoneLocationPhase.PERMISSION_REQUIRED -> add(VesselSceneStatus.PERMISSION_REQUIRED)
                PhoneLocationPhase.PROVIDER_DISABLED -> add(VesselSceneStatus.PROVIDER_DISABLED)
                PhoneLocationPhase.OFF -> add(VesselSceneStatus.SOURCE_DISABLED)
                else -> Unit
            }
            if (id in headingMetrics && phoneRelevant && !mount.headingAligned) add(VesselSceneStatus.CALIBRATION_REQUIRED)
            if (id in headingMetrics && phoneRelevant && !capabilities.magnetometerAvailable) add(VesselSceneStatus.SENSOR_MISSING)
            if (conflict != null) add(VesselSceneStatus.CONFLICT)
            if (hasValue && observation.quality != VesselDataQuality.GOOD) add(VesselSceneStatus.LOW_QUALITY)
            if (hasValue && freshness != VesselDataFreshness.FRESH) add(VesselSceneStatus.LAST_READING)
            if (!hasValue && !invalidValue) add(if (candidates.isNotEmpty())
                VesselSceneStatus.NO_ADOPTED_READING else VesselSceneStatus.NEVER_RECEIVED)
        }
        val status = issuePriority.firstOrNull { it in issues } ?: VesselSceneStatus.CURRENT
        val sourceConnection = observation.sourceIdentity?.transportProfileId?.let(connections::get)
        val ownMeasurement = observation.receivedElapsedRealtime
        val candidateModels = candidates.map { candidate ->
            val validity = MetricSourceEligibility.evaluate(id, candidate, now)
            VesselSceneCandidate(
                source = candidate.source,
                sourceClass = candidate.sourceClass,
                number = (candidate.value as? Number)?.toDouble()?.takeIf { it.isFinite() },
                position = (candidate.value as? VesselPosition)?.takeIf(::validPosition),
                reference = candidate.reference,
                quality = candidate.quality,
                validity = validity,
                freshness = when {
                    validity in setOf(CandidateValidity.INVALID, CandidateValidity.DISABLED) -> VesselDataFreshness.UNAVAILABLE
                    validity == CandidateValidity.STALE -> VesselDataFreshness.STALE
                    candidate.sourceHeartbeatElapsedRealtime > candidate.receivedElapsedRealtime -> VesselDataFreshness.HELD
                    else -> VesselDataFreshness.FRESH
                },
                observedAtUtcMillis = candidate.observedAtUtcMillis,
                receivedElapsedRealtime = candidate.receivedElapsedRealtime,
                sourceHeartbeatElapsedRealtime = candidate.sourceHeartbeatElapsedRealtime,
                ageMillis = age(now, candidate.receivedElapsedRealtime),
                provenance = candidate.provenance,
                isRequested = requestedKey?.let { VesselSourcePinPolicy.matches(candidate.source, it) } == true,
                isAdopted = adopted?.id == candidate.source.id,
                connection = candidate.source.transportProfileId?.let(connections::get),
            )
        }
        return VesselSceneMetric(
            id = id, number = number, position = position,
            source = observation.sourceIdentity, adoptedSource = adopted,
            sourceClass = observation.sourceClass, preference = preference,
            requestedSourceKey = requestedKey, requestedSource = requestedCandidate?.source,
            selectedSourceUnavailable = selectedUnavailable,
            freshness = freshness, quality = observation.quality, status = status, issues = issues,
            observedAtUtcMillis = observation.observedAtUtcMillis,
            receivedElapsedRealtime = ownMeasurement,
            sourceHeartbeatElapsedRealtime = observation.sourceHeartbeatElapsedRealtime,
            ageMillis = age(now, ownMeasurement), reference = observation.reference,
            provenance = observation.provenance, provenanceDetail = observation.provenanceDetail,
            conflict = conflict, selectionReason = observation.selectionReason,
            candidates = candidateModels, connection = sourceConnection,
            connectionContinuesWithoutMeasurement = hasValue && freshness != VesselDataFreshness.FRESH &&
                ownMeasurement != null && sourceConnection?.lastMessageElapsedRealtime?.let { it > ownMeasurement &&
                    sourceConnection.messageAgeMillis?.let { messageAge -> messageAge <= 15_000L } == true } == true,
        )
    }

    /** 重用运行时每指标的时效策略，只允许显示状态降级，绝不把过期/缺失读数升级。 */
    private fun displayFreshness(id: VesselMetricId, observation: VesselObservation<*>, hasValue: Boolean, now: Long): VesselDataFreshness {
        if (!hasValue) return VesselDataFreshness.UNAVAILABLE
        if (observation.freshness !in setOf(VesselDataFreshness.FRESH, VesselDataFreshness.HELD)) return observation.freshness
        val measured = observation.receivedElapsedRealtime ?: return VesselDataFreshness.STALE
        val identity = observation.sourceIdentity ?: return if (now - measured in 0..MetricSourceEligibility.measurementLeaseMillis(id))
            observation.freshness else VesselDataFreshness.STALE
        val candidate = VesselSourceCandidate(
            metric = id, value = observation.value ?: return VesselDataFreshness.UNAVAILABLE,
            source = identity, sourceClass = observation.sourceClass, reference = observation.reference,
            receivedElapsedRealtime = measured, observedAtUtcMillis = observation.observedAtUtcMillis,
            quality = observation.quality, provenance = observation.provenanceDetail,
            sourceHeartbeatElapsedRealtime = observation.sourceHeartbeatElapsedRealtime ?: measured,
        )
        return when {
            MetricSourceEligibility.evaluate(id, candidate, now) == CandidateValidity.STALE -> VesselDataFreshness.STALE
            // 同一生产者的心跳可保留值，但不能把旧测量称作正在更新。
            now - measured > MetricSourceEligibility.measurementLeaseMillis(id) -> VesselDataFreshness.HELD
            else -> observation.freshness
        }
    }

    private fun validPosition(value: VesselPosition): Boolean =
        value.latitude.isFinite() && value.longitude.isFinite() &&
            value.latitude in -90.0..90.0 && value.longitude in -180.0..180.0

    /** 跨开机或未来时刻不显示负数年龄，也不能被误读成刚刚更新。 */
    private fun age(now: Long, elapsed: Long?): Long? = elapsed?.let { (now - it).takeIf { value -> value >= 0L } }

    private val headingMetrics = setOf(VesselMetricId.HEADING_TRUE, VesselMetricId.HEADING_MAGNETIC)
    private val issuePriority = listOf(
        VesselSceneStatus.PERMISSION_REQUIRED, VesselSceneStatus.PROVIDER_DISABLED,
        VesselSceneStatus.SOURCE_DISABLED, VesselSceneStatus.CALIBRATION_REQUIRED,
        VesselSceneStatus.SENSOR_MISSING, VesselSceneStatus.INVALID, VesselSceneStatus.CONFLICT,
        VesselSceneStatus.LAST_READING, VesselSceneStatus.LOW_QUALITY,
        VesselSceneStatus.NO_ADOPTED_READING, VesselSceneStatus.NEVER_RECEIVED,
    )
}
