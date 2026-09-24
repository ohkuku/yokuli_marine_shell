package com.yokuli.runtime.contract.ais

import java.util.UUID
import kotlin.math.*

/** 唯一后台运行时串行调用本引擎。界面只读快照，不持有可变目标、分片或风险状态。 */
class AisEngine {
    private data class Record(
        val mmsi: Int,
        var kind: AisEntityKind = AisEntityKind.UNKNOWN,
        var static: AisStaticData = AisStaticData(),
        val latest: LinkedHashMap<String, AisDynamicReport> = linkedMapOf(),
        val valid: LinkedHashMap<String, AisDynamicReport> = linkedMapOf(),
        var selected: AisDynamicReport? = null,
        var lastMessage: Long = 0,
        var distress: AisDistressState = AisDistressState.NONE,
        val track: ArrayDeque<AisTrackPoint> = ArrayDeque(),
        val safety: ArrayDeque<AisSafetyMessage> = ArrayDeque(),
        val raw: ArrayDeque<AisRawMessage> = ArrayDeque(),
        var segment: Long = 0,
        var cached: Boolean = false,
        var conflict: String? = null,
        var wasLost: Boolean = false,
        var hadRisk: Boolean = false,
        var lastRiskElapsed: Long = 0,
        val distances: ArrayDeque<Pair<Long, Double>> = ArrayDeque(),
    )
    private data class InputCounter(var valid: Long = 0, var rejected: Long = 0, var last: Long? = null, var error: String? = null)
    private data class RiskMemory(var event: AisRiskEvent? = null, var enteringAt: Long? = null, var leavingAt: Long? = null)
    private val decoder = AisDecoder()
    private val records = LinkedHashMap<Int, Record>()
    private val counts = LinkedHashMap<String, InputCounter>()
    private val ownReports = LinkedHashMap<String, AisOwnReport>()
    private val dedup = LinkedHashMap<String, Long>()
    private val retained = LinkedHashMap<Int, MutableSet<String>>()
    private val risks = LinkedHashMap<Pair<Int, AisRiskKind>, RiskMemory>()
    private var prefs = AisPreferences()
    private var capacityLimited = false
    private var capacityAt = 0L
    private val runId = UUID.randomUUID().toString().take(8)
    private var sequence = 0L

    fun accept(frame: AisFrame) {
        if (frame.receivedElapsed < 0 || frame.connectionId.isBlank() || frame.connectionId.length > 256 || (frame.peer?.length ?: 0) > 256) return
        if (counts.size >= 128 && frame.connectionId !in counts) counts.remove(counts.keys.first())
        val counter = counts.getOrPut(frame.connectionId) { InputCounter() }
        when (val result = decoder.accept(frame)) {
            AisDecodeResult.NotAis, AisDecodeResult.Pending -> Unit
            is AisDecodeResult.Rejected -> { counter.rejected++; counter.error = result.reason }
            is AisDecodeResult.Message -> {
                val message = result.value
                counter.valid++; counter.last = frame.receivedElapsed
                if (message.own) {
                    if (ownReports.size >= 64 && message.raw.source.key !in ownReports) ownReports.remove(ownReports.keys.first())
                    ownReports[message.raw.source.key] = AisOwnReport(message.mmsi, message.raw.source, frame.receivedElapsed)
                    return
                }
                if (message.mmsi !in records && records.size >= 1024 && !evictOne(frame.receivedElapsed)) {
                    capacityLimited = true; capacityAt = frame.receivedElapsed; return
                }
                val record = records.getOrPut(message.mmsi) { Record(message.mmsi) }
                record.lastMessage = max(record.lastMessage, frame.receivedElapsed)
                if (message.kind != AisEntityKind.UNKNOWN) {
                    // 24A 的名字和远程报告不能把已经确认的基站/助航/SAR 或 Class A 改成另一船型。
                    if (record.kind == AisEntityKind.UNKNOWN || message.dynamic != null || message.kind in distressKinds) {
                        if (!(message.kind == AisEntityKind.LONG_RANGE && record.kind != AisEntityKind.UNKNOWN)) record.kind = message.kind
                    }
                }
                record.static = merge(record.static, message.staticData)
                message.distress?.let { record.distress = it }
                if (record.kind in distressKinds && record.distress == AisDistressState.NONE) record.distress = AisDistressState.UNKNOWN
                message.safety?.let { text ->
                    if (record.safety.lastOrNull()?.let { it.text == text.text && text.receivedElapsed - it.receivedElapsed < 2_000 } != true) {
                        record.safety.addLast(text)
                        while (record.safety.size > 8) record.safety.removeFirst()
                    }
                }
                record.raw.addLast(message.raw)
                while (record.raw.size > 6) record.raw.removeFirst()
                val dynamic = message.dynamic ?: return
                // 仅短窗去重，不永久记 payload；静止船发送的新位置仍会刷新观测时间。
                dedup.entries.removeAll { frame.receivedElapsed - it.value > 1_500 || frame.receivedElapsed < it.value }
                val fingerprint = "${message.raw.type}:${message.raw.payload}:${message.raw.fillBits}"
                val observedAt = dedup[fingerprint] ?: frame.receivedElapsed.also {
                    if (dedup.size >= 4096) dedup.remove(dedup.keys.first())
                    dedup[fingerprint] = it
                }
                val report = dynamic.copy(receivedElapsed = observedAt)
                val key = report.source.key
                if (record.latest[key]?.receivedElapsed?.let { it > report.receivedElapsed } == true) return
                if (record.latest.size >= 8 && key !in record.latest) {
                    val remove = record.latest.keys.firstOrNull { it != record.selected?.source?.key } ?: record.latest.keys.first()
                    record.latest.remove(remove); record.valid.remove(remove)
                }
                record.latest[key] = report
                if (report.position != null && "position_system_inoperative" !in report.invalidFields) {
                    record.valid[key] = report
                    record.cached = false
                }
            }
        }
    }

    fun tick(now: Long, ownship: AisOwnship?, settings: AisPreferences, inputs: List<AisInputHealth>): TrafficSnapshot {
        prefs = settings
        decoder.expire(now)
        ownReports.entries.removeAll { now - it.value.receivedElapsed > 86_400_000 || now < it.value.receivedElapsed }
        if (capacityLimited && now - capacityAt > 60_000 && records.size < 900) capacityLimited = false
        val inferredOwn = ownReports.values.map { it.mmsi }.toSet()
        val ownIds = settings.ownMmsi?.let { setOf(it) } ?: inferredOwn
        risks.keys.removeAll { it.first in ownIds }
        val snapshots = mutableListOf<AisTarget>()
        val remove = mutableListOf<Int>()
        records.values.forEach { record ->
            if (record.mmsi in ownIds) return@forEach
            val previous = record.selected
            val freshCandidates = record.valid.values.filter { now - it.receivedElapsed in 0..AisAgePolicy.lostMillis(record.kind, it) }
            val currentSource = previous?.source?.key
            val preferred = currentSource?.let { record.valid[it] }?.takeIf { now - it.receivedElapsed <= AisAgePolicy.currentMillis(record.kind, it) }
            val newest = freshCandidates.maxByOrNull { if (it.lowResolution) it.receivedElapsed - 120_000 else it.receivedElapsed }
            var chosen = preferred ?: newest ?: previous ?: record.valid.values.maxByOrNull { it.receivedElapsed }
            var conflict: String? = null
            if (chosen != null) {
                val reference = chosen!!
                val point = reference.position
                if (point != null) {
                    val contradictory = freshCandidates.any { other ->
                        val otherPoint = other.position ?: return@any false
                        if (other.source.key == reference.source.key) return@any false
                        val dt = abs(other.receivedElapsed - reference.receivedElapsed) / 1000.0
                        val movementAllowance = max(other.sogMetersPerSecond ?: 0.0, reference.sogMetersPerSecond ?: 0.0).coerceAtLeast(5.0) * dt
                        val precisionAllowance = if (other.lowResolution || reference.lowResolution) 1000.0 else 250.0
                        AisGeometry.distanceMeters(point, otherPoint) > precisionAllowance + movementAllowance
                    }
                    if (contradictory) conflict = "same_mmsi_different_positions"
                    val oldPoint = previous?.position
                    if (previous != null && oldPoint != null && reference.receivedElapsed > previous.receivedElapsed && now - previous.receivedElapsed < AisAgePolicy.lostMillis(record.kind, previous)) {
                        val dt = (reference.receivedElapsed - previous.receivedElapsed) / 1000.0
                        val limit = max(60.0, max(previous.sogMetersPerSecond ?: 0.0, reference.sogMetersPerSecond ?: 0.0) * 2.0) * dt + if (reference.lowResolution || previous.lowResolution) 1000.0 else 250.0
                        if (AisGeometry.distanceMeters(oldPoint, point) > limit) {
                            conflict = "implausible_position_jump"
                            // 不让一次互相矛盾的位置把符号瞬移；原位置仍保留原来的时间。
                            chosen = previous
                        }
                    }
                }
            }
            val age = chosen?.let { (now - it.receivedElapsed).coerceAtLeast(0) }
            val latestChosen = chosen?.source?.key?.let { record.latest[it] }
            val invalidated = latestChosen != null && latestChosen.receivedElapsed >= (chosen?.receivedElapsed ?: 0) && (latestChosen.position == null || "position_system_inoperative" in latestChosen.invalidFields)
            val state = when {
                record.cached -> AisTargetState.LOST
                chosen?.position == null -> if (record.latest.values.any { it.position == null }) AisTargetState.INVALID else AisTargetState.WAITING_POSITION
                age!! > AisAgePolicy.lostMillis(record.kind, chosen!!) -> AisTargetState.LOST
                conflict != null -> AisTargetState.CONFLICT
                invalidated -> AisTargetState.INVALID
                age > AisAgePolicy.currentMillis(record.kind, chosen!!) -> AisTargetState.AGING
                else -> AisTargetState.CURRENT
            }
            record.conflict = conflict
            if (state == AisTargetState.CURRENT && chosen != null && chosen!!.position != null && chosen!!.receivedElapsed > (record.track.lastOrNull()?.receivedElapsed ?: -1)) {
                val last = record.track.lastOrNull()
                if (record.wasLost || (previous != null && chosen!!.source.key != previous.source.key) || (last != null && chosen!!.receivedElapsed - last.receivedElapsed > AisAgePolicy.lostMillis(record.kind, chosen!!))) record.segment++
                val position = chosen!!.position!!
                if (last == null || AisGeometry.distanceMeters(last.position, position) >= 1.0 || chosen!!.receivedElapsed - last.receivedElapsed >= 10_000) record.track.addLast(AisTrackPoint(position, chosen!!.receivedElapsed, record.segment))
                while (record.track.size > 180 || record.track.firstOrNull()?.let { now - it.receivedElapsed > 1_800_000 } == true) record.track.removeFirst()
            }
            if (state in setOf(AisTargetState.LOST, AisTargetState.CONFLICT, AisTargetState.INVALID)) record.wasLost = true else if (state == AisTargetState.CURRENT) record.wasLost = false
            record.selected = chosen
            var target = AisTarget(record.mmsi, record.kind, state, record.static, chosen, record.latest.values.toList(), record.lastMessage, if (record.cached) null else age, invalidated, conflict, record.distress, record.track.toList(), record.safety.toList(), record.raw.toList(), watched = record.mmsi in settings.watchedMmsis, alias = settings.aliases[record.mmsi], cached = record.cached)
            var relative = AisRelativeMotion.calculate(target, ownship, now)
            val distance = relative.distanceMeters
            if (state == AisTargetState.CURRENT && distance != null && chosen != null && (relative.state !in setOf(AisCpaState.OWN_POSITION_MISSING, AisCpaState.CONFLICT, AisCpaState.TARGET_POSITION_MISSING)) && chosen!!.receivedElapsed > (record.distances.lastOrNull()?.first ?: -1)) {
                record.distances.addLast(chosen!!.receivedElapsed to distance)
                while (record.distances.size > 12) record.distances.removeFirst()
            }
            while (record.distances.firstOrNull()?.let { now - it.first > 180_000 } == true) record.distances.removeFirst()
            if (record.distances.size >= 3 && state == AisTargetState.CURRENT) {
                val first = record.distances.first(); val last = record.distances.last()
                val dt = last.first - first.first
                if (dt >= 15_000 && now - last.first <= 60_000) {
                    val change = last.second - first.second
                    // 至少三份观测、足够时间间隔与噪声余量才描述接近，不把单点漂移叫趋势。
                    val threshold = max(20.0, first.second * 0.02)
                    val trend = when { change < -threshold -> AisApproachTrend.APPROACHING; change > threshold -> AisApproachTrend.RECEDING; else -> AisApproachTrend.STEADY }
                    relative = relative.copy(approachTrend = trend)
                }
            }
            target = target.copy(relative = relative)
            evaluateRisk(target, record, ownship, settings, now)
            val activeEvents = AisRiskKind.entries.mapNotNull { risks[target.mmsi to it]?.event }.filter { it.active }
            target = target.copy(riskLevel = activeEvents.maxByOrNull { it.level.ordinal }?.level ?: AisRiskLevel.NONE, riskEventIds = activeEvents.map { it.id })
            if (activeEvents.isNotEmpty()) { record.hadRisk = true; record.lastRiskElapsed = now }
            else if (record.hadRisk && now - record.lastRiskElapsed > 86_400_000) record.hadRisk = false
            val keepTarget = record.mmsi in settings.watchedMmsis || retained[record.mmsi]?.isNotEmpty() == true || record.hadRisk || record.distress == AisDistressState.ACTIVE
            if (!keepTarget && ((state == AisTargetState.LOST && now - record.lastMessage > 1_800_000) || (chosen == null && now - record.lastMessage > 3_600_000))) remove += record.mmsi else snapshots += target
        }
        remove.forEach { records.remove(it); risks.keys.removeAll { key -> key.first == it } }
        risks.entries.removeAll { (key, memory) -> key.first !in records || memory.event?.let { !it.active && now - it.updatedElapsed > 3_600_000 } == true }
        val enriched = inputs.map { input ->
            val counter = counts[input.connectionId]
            input.copy(lastAisElapsed = counter?.last, legalAisMessages = counter?.valid ?: 0, rejectedMessages = counter?.rejected ?: 0, lastError = input.lastError ?: counter?.error,
                dynamicTargets = snapshots.count { t -> t.state == AisTargetState.CURRENT && t.candidates.any { it.source.connectionId == input.connectionId && it.position != null } })
        }
        val events = risks.values.mapNotNull { it.event }.sortedWith(compareByDescending<AisRiskEvent> { it.active }.thenByDescending { it.level.ordinal }.thenByDescending { it.startedElapsed })
        return TrafficSnapshot(generatedElapsed = now, targets = snapshots.sortedBy { it.mmsi }, inputs = enriched, ownship = ownship, preferences = settings,
            events = events.take(512), ownReports = ownReports.values.toList(),
            ownIdentityConflict = inferredOwn.size > 1 || (settings.ownMmsi != null && inferredOwn.any { it != settings.ownMmsi }), capacityLimited = capacityLimited || events.count { it.active } > 512)
    }

    fun retain(mmsi: Int, retain: Boolean, ownerId: String = "default") {
        if (mmsi !in 1..999_999_999) return
        if (retain) {
            if (retained.size < 128 || mmsi in retained) retained.getOrPut(mmsi) { linkedSetOf() }.apply { if (size < 32) add(ownerId.take(128)) }
        } else retained[mmsi]?.let { it.remove(ownerId.take(128)); if (it.isEmpty()) retained.remove(mmsi) }
    }
    fun acknowledge(eventId: String): Boolean {
        val risk = risks.values.firstOrNull { it.event?.id == eventId } ?: return false
        risk.event = risk.event?.copy(acknowledged = true)
        return true
    }
    fun snooze(eventId: String, untilElapsed: Long): Boolean {
        val risk = risks.values.firstOrNull { it.event?.id == eventId } ?: return false
        risk.event = risk.event?.copy(snoozedUntilElapsed = untilElapsed)
        return true
    }
    fun restoreAcknowledgements(acknowledged: Set<String>, snoozed: Map<String, Long>) {
        risks.values.forEach { memory -> memory.event?.let { event -> memory.event = event.copy(acknowledged = event.id in acknowledged || event.acknowledged, snoozedUntilElapsed = snoozed[event.id] ?: event.snoozedUntilElapsed) } }
    }
    fun exportCache(): List<AisCachedTarget> = records.values.sortedWith(compareByDescending<Record> { it.mmsi in prefs.watchedMmsis || it.hadRisk }.thenByDescending { it.lastMessage }).take(256).map { AisCachedTarget(it.mmsi, it.kind, it.static, it.selected?.position, it.selected?.headingDegrees, it.selected?.source) }
    fun restoreCache(cache: List<AisCachedTarget>) {
        cache.take(256).filter { it.mmsi in 1..999_999_999 }.forEach { cached ->
            if (cached.mmsi in records) return@forEach
            fun <T> old(field: AisStaticValue<T>?): AisStaticValue<T>? = field?.copy(receivedElapsed = 0)
            val static = cached.staticData.let { s -> s.copy(name = old(s.name), callSign = old(s.callSign), shipType = old(s.shipType), imo = old(s.imo), dimensions = old(s.dimensions), destination = old(s.destination), eta = old(s.eta), draughtMeters = old(s.draughtMeters), motherShipMmsi = old(s.motherShipMmsi), vendor = old(s.vendor), aidType = old(s.aidType)) }
            val record = Record(cached.mmsi, cached.kind, static, cached = true, wasLost = true)
            cached.lastPosition?.takeIf(AisGeometry::valid)?.let { position ->
                record.selected = AisDynamicReport(cached.lastSource ?: AisSource("cache", 0, null, "CACHE", ""), 0, 0, position, headingDegrees = cached.lastHeadingDegrees)
            }
            records[cached.mmsi] = record
        }
    }

    private fun evictOne(now: Long): Boolean {
        val victim = records.values.filter { it.mmsi !in prefs.watchedMmsis && retained[it.mmsi]?.isNotEmpty() != true && !it.hadRisk && it.distress != AisDistressState.ACTIVE }
            .minWithOrNull(compareBy<Record> { if (now - it.lastMessage > 900_000) 0 else 1 }.thenBy { it.lastMessage }) ?: return false
        records.remove(victim.mmsi)
        risks.keys.removeAll { it.first == victim.mmsi }
        capacityLimited = true; capacityAt = now
        return true
    }

    private fun evaluateRisk(target: AisTarget, record: Record, own: AisOwnship?, settings: AisPreferences, now: Long) {
        val relative = target.relative
        val distance = relative.distanceMeters
        val current = target.state == AisTargetState.CURRENT && !target.cached && !target.positionInvalidated
        val distanceUsable = current && distance != null && relative.state !in setOf(AisCpaState.OWN_POSITION_MISSING, AisCpaState.TARGET_POSITION_MISSING, AisCpaState.CONFLICT) && (target.positionAgeMillis ?: Long.MAX_VALUE) <= if ((target.dynamic?.sogMetersPerSecond ?: 1.0) < 0.25) 180_000 else 60_000
        val surface = target.kind !in setOf(AisEntityKind.BASE_STATION, AisEntityKind.SAR_AIRCRAFT, AisEntityKind.AID_TO_NAVIGATION, AisEntityKind.VIRTUAL_AID, AisEntityKind.UNKNOWN) && target.distress != AisDistressState.TEST
        val cpaUsable = relative.state in setOf(AisCpaState.CALCULATED, AisCpaState.ESTIMATED, AisCpaState.PAST, AisCpaState.PARALLEL)
        fun active(kind: AisRiskKind) = risks[target.mmsi to kind]?.event?.active == true
        val cpaLimit = settings.cpaDistanceMeters * if (active(AisRiskKind.CPA)) 1.2 else 1.0
        val cpa = cpaUsable && relative.cpaMeters != null && relative.tcpaSeconds != null && relative.tcpaSeconds in 0.0..settings.cpaTimeSeconds && relative.cpaMeters < cpaLimit
        updateRisk(target, AisRiskKind.CPA, settings.cpaEnabled && surface, cpa, cpaUsable, false, AisRiskLevel.WARNING, "constant_velocity_close_approach", now)
        val nearLimit = settings.proximityMeters * if (active(AisRiskKind.PROXIMITY)) 1.2 else 1.0
        updateRisk(target, AisRiskKind.PROXIMITY, settings.proximityEnabled && surface, distanceUsable && distance!! < nearLimit, distanceUsable, distanceUsable && distance!! < nearLimit / 2, AisRiskLevel.WARNING, "close_to_own_position", now)
        var anchorDistance: Double? = distance
        if (settings.anchorUsesAnchorPoint) anchorDistance = if (own?.anchor != null && target.position != null && current && (target.positionAgeMillis ?: Long.MAX_VALUE) <= 180_000) AisGeometry.distanceMeters(own.anchor, target.position!!) else null
        val anchorValid = if (settings.anchorUsesAnchorPoint) anchorDistance != null && current else distanceUsable
        val anchorLimit = settings.anchorProximityMeters * if (active(AisRiskKind.ANCHOR_PROXIMITY)) 1.2 else 1.0
        updateRisk(target, AisRiskKind.ANCHOR_PROXIMITY, settings.anchorProximityEnabled && own?.anchored == true && surface, anchorValid && anchorDistance!! < anchorLimit, anchorValid,
            anchorValid && anchorDistance!! < anchorLimit / 2, AisRiskLevel.WARNING, if (settings.anchorUsesAnchorPoint) "close_to_anchor_area" else "close_to_anchored_vessel", now, anchorDistance)
        // 设备 TEST 永远不触发真实遇险；已激活但失去位置也不会自动声称解除。
        updateRisk(target, AisRiskKind.DISTRESS, settings.monitoringEnabled && target.kind in distressKinds, target.distress == AisDistressState.ACTIVE, target.distress != AisDistressState.UNKNOWN,
            true, AisRiskLevel.URGENT, if (target.state == AisTargetState.LOST) "distress_last_observation" else "distress_device_active", now)
        val lost = target.state in setOf(AisTargetState.LOST, AisTargetState.AGING, AisTargetState.INVALID, AisTargetState.CONFLICT)
        updateRisk(target, AisRiskKind.TARGET_LOST, settings.monitoringEnabled && settings.lostTargetAlerts && (record.hadRisk || target.watched), lost, true,
            true, AisRiskLevel.ATTENTION, "target_data_no_longer_confirmed", now)
    }

    private fun updateRisk(target: AisTarget, kind: AisRiskKind, enabled: Boolean, condition: Boolean, calculable: Boolean, immediate: Boolean, level: AisRiskLevel, reason: String, now: Long, distance: Double? = target.relative.distanceMeters) {
        val key = target.mmsi to kind
        val memory = risks[key] ?: if (enabled && condition) RiskMemory().also { risks[key] = it } else return
        val previous = memory.event
        if (!enabled) {
            memory.enteringAt = null; memory.leavingAt = null
            if (previous?.active == true) memory.event = previous.copy(active = false, updatedElapsed = now, reason = "monitoring_disabled")
            return
        }
        if (condition) {
            memory.leavingAt = null
            if (previous?.active == true) {
                memory.event = previous.copy(updatedElapsed = now, reason = reason, distanceMeters = distance, cpaMeters = target.relative.cpaMeters, tcpaSeconds = target.relative.tcpaSeconds)
                return
            }
            if (memory.enteringAt == null) memory.enteringAt = now
            if (immediate || now - memory.enteringAt!! >= 4_000) {
                memory.event = AisRiskEvent("ais:$runId:${target.mmsi}:${kind.name}:${++sequence}", target.mmsi, kind, level, now, now, reason = reason, distanceMeters = distance, cpaMeters = target.relative.cpaMeters, tcpaSeconds = target.relative.tcpaSeconds)
                memory.enteringAt = null
            }
            return
        }
        memory.enteringAt = null
        if (previous?.active != true) return
        if (!calculable) {
            memory.leavingAt = null
            memory.event = previous.copy(updatedElapsed = now, reason = "risk_cannot_be_reconfirmed")
            return
        }
        if (memory.leavingAt == null) memory.leavingAt = now
        if (now - memory.leavingAt!! >= 15_000) {
            memory.event = previous.copy(active = false, updatedElapsed = now, reason = "outside_attention_rule")
            memory.leavingAt = null
        }
    }

    private fun merge(old: AisStaticData, fresh: AisStaticData) = AisStaticData(
        name = fresh.name ?: old.name, callSign = fresh.callSign ?: old.callSign, shipType = fresh.shipType ?: old.shipType,
        imo = fresh.imo ?: old.imo, dimensions = fresh.dimensions ?: old.dimensions, destination = fresh.destination ?: old.destination,
        eta = fresh.eta ?: old.eta, draughtMeters = fresh.draughtMeters ?: old.draughtMeters, motherShipMmsi = fresh.motherShipMmsi ?: old.motherShipMmsi,
        vendor = fresh.vendor ?: old.vendor, aidType = fresh.aidType ?: old.aidType,
    )
    companion object { private val distressKinds = setOf(AisEntityKind.SART, AisEntityKind.MOB, AisEntityKind.EPIRB) }
}
