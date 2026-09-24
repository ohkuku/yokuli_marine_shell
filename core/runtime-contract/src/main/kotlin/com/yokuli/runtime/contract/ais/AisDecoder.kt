package com.yokuli.runtime.contract.ais

import kotlin.math.abs
import kotlin.math.sign

internal data class DecodedAis(
    val mmsi: Int, val kind: AisEntityKind, val own: Boolean,
    val raw: AisRawMessage, val dynamic: AisDynamicReport? = null,
    val staticData: AisStaticData = AisStaticData(), val safety: AisSafetyMessage? = null,
    val distress: AisDistressState? = null,
)
internal sealed interface AisDecodeResult {
    data class Message(val value: DecodedAis) : AisDecodeResult
    data class Rejected(val reason: String) : AisDecodeResult
    data object Pending : AisDecodeResult
    data object NotAis : AisDecodeResult
}

/** 有界传输装配。正文和 tag 各自校验，任何一个失败都不采用；不把 tag 时间当可信观测时间。 */
internal class AisDecoder {
    private data class Group(val total: Int, val started: Long, val parts: MutableMap<Int, String>, var source: AisSource, var tagTime: String?, var fill: Int = 0, var next: Int = 1)
    private val groups = LinkedHashMap<String, Group>()
    private val blocked = LinkedHashMap<String, Long>()
    private var expiredGroups = 0L
    fun expire(now: Long): Long {
        val expired = groups.filterValues { now - it.started > 10_000 || now < it.started }.keys
        expired.forEach(groups::remove)
        blocked.entries.removeAll { now >= it.value }
        expiredGroups += expired.size
        return expiredGroups
    }
    fun accept(frame: AisFrame): AisDecodeResult {
        expire(frame.receivedElapsed)
        return try { decodeFrame(frame) } catch (_: IllegalArgumentException) { AisDecodeResult.Rejected("invalid_bit_fields") }
    }
    private fun decodeFrame(frame: AisFrame): AisDecodeResult {
        var text = frame.sentence.trimEnd('\r', '\n')
        if (text.length > 512 || text.any { it.code !in 32..126 }) return AisDecodeResult.Rejected("frame_length_or_characters")
        var tagSource: String? = null
        var tagTime: String? = null
        var tagGroup: String? = null
        var tagCount: Int? = null
        var tagIndex: Int? = null
        if (text.startsWith('\\')) {
            val end = text.indexOf('\\', 1)
            if (end !in 4..256) return AisDecodeResult.Rejected("tag_block_invalid")
            val tag = text.substring(1, end)
            if (!checksum(tag, 0)) return AisDecodeResult.Rejected("tag_checksum")
            val fields = tag.substringBeforeLast('*').split(',')
            if (fields.any { it.length < 3 || it[1] != ':' }) return AisDecodeResult.Rejected("tag_fields")
            fields.forEach { field ->
                val value = field.substring(2)
                when (field[0]) {
                    's' -> tagSource = value.take(64)
                    'c' -> tagTime = value.takeIf { it.length <= 20 && it.all(Char::isDigit) }
                    'g' -> {
                        val pieces = value.split('-')
                        if (pieces.size != 3) return AisDecodeResult.Rejected("tag_group")
                        tagIndex = pieces[0].toIntOrNull()
                        tagCount = pieces[1].toIntOrNull()
                        tagGroup = pieces[2].takeIf { it.isNotEmpty() && it.length <= 32 && it.all(Char::isDigit) }
                        if (tagIndex == null || tagCount == null || tagGroup == null || tagIndex!! !in 1..tagCount!! || tagCount!! !in 1..9) return AisDecodeResult.Rejected("tag_group")
                    }
                }
            }
            text = text.substring(end + 1)
        }
        if (text.length < 7 || text[0] !in "!$") return AisDecodeResult.NotAis
        val address = text.substring(1, 6)
        if (!address.endsWith("VDM") && !address.endsWith("VDO")) return AisDecodeResult.NotAis
        if (address.take(2).any { !it.isLetterOrDigit() || it.isLowerCase() } || text[6] != ',') return AisDecodeResult.Rejected("formatter")
        if (text.length > 128 || !checksum(text, 1)) return AisDecodeResult.Rejected("body_checksum_or_length")
        val fields = text.substring(1, text.lastIndexOf('*')).split(',')
        if (fields.size != 7) return AisDecodeResult.Rejected("envelope_fields")
        val total = fields[1].toIntOrNull() ?: return AisDecodeResult.Rejected("fragment_count")
        val index = fields[2].toIntOrNull() ?: return AisDecodeResult.Rejected("fragment_index")
        val sequence = fields[3]
        val channel = fields[4]
        val payload = fields[5]
        val fill = fields[6].toIntOrNull() ?: return AisDecodeResult.Rejected("fill_bits")
        if (total !in 1..9 || index !in 1..total || fill !in 0..5 || (index < total && fill != 0)) return AisDecodeResult.Rejected("fragment_bounds")
        if (sequence.length > 1 || sequence.any { !it.isDigit() } || channel !in listOf("", "A", "B", "1", "2")) return AisDecodeResult.Rejected("sequence_or_channel")
        if (payload.isEmpty() || payload.length > 100 || payload.any { it !in '0'..'W' && it !in '`'..'w' }) return AisDecodeResult.Rejected("payload_characters")
        if (fill > 0 && (six(payload.last()) and ((1 shl fill) - 1)) != 0) return AisDecodeResult.Rejected("nonzero_padding")
        val source = AisSource(frame.connectionId, frame.generation, frame.peer, address, channel, tagSource)
        if (total == 1) return decodePayload(payload, fill, source, frame.receivedElapsed, tagTime)
        // g 可以关联多条独立句子；只有总片数/片号相符时才把它用作该 NMEA 多片消息的键。
        val grouping = if (tagCount == total && tagIndex == index) tagGroup else null
        val key = "${source.copy(tagSource = null).key}|${if (sequence.isNotEmpty()) "s:$sequence" else grouping?.let { "g:$it" } ?: "anonymous"}"
        if (blocked.containsKey(key)) return AisDecodeResult.Rejected("fragment_group_quarantined")
        val anonymous = sequence.isEmpty() && grouping == null
        if (anonymous && index != 1 && !groups.containsKey(key)) return AisDecodeResult.Rejected("anonymous_fragment_without_start")
        if (!groups.containsKey(key) && groups.size >= 64) {
            groups.remove(groups.keys.first())
            return AisDecodeResult.Rejected("fragment_capacity")
        }
        val group = groups.getOrPut(key) { Group(total, frame.receivedElapsed, linkedMapOf(), source, tagTime) }
        fun ambiguous(): AisDecodeResult {
            groups.remove(key)
            if (blocked.size >= 64) blocked.remove(blocked.keys.first())
            blocked[key] = frame.receivedElapsed + 10_000
            return AisDecodeResult.Rejected("fragment_collision")
        }
        if (group.total != total) return ambiguous()
        if (group.source.tagSource != null && source.tagSource != null && group.source.tagSource != source.tagSource) return ambiguous()
        if (group.source.tagSource == null && source.tagSource != null) group.source = source
        if (group.tagTime == null) group.tagTime = tagTime
        val old = group.parts[index]
        if (old != null) {
            // 无组标识的第一片再次出现，无法证明是重发还是新报文，宁可放弃而不拼接两次广播。
            if (anonymous && index == 1) return ambiguous()
            if (old != payload || (index == total && group.fill != fill)) return ambiguous()
            return AisDecodeResult.Pending
        }
        if (anonymous && index != group.next) return ambiguous()
        group.parts[index] = payload
        group.next = index + 1
        if (index == total) group.fill = fill
        if (group.parts.values.sumOf(String::length) > 178) return ambiguous()
        if (group.parts.size != total) return AisDecodeResult.Pending
        groups.remove(key)
        return decodePayload((1..total).joinToString("") { group.parts.getValue(it) }, group.fill, group.source, frame.receivedElapsed, group.tagTime)
    }

    private fun decodePayload(payload: String, fill: Int, source: AisSource, now: Long, tagTime: String?): AisDecodeResult {
        val b = Bits(payload, fill)
        if (b.size !in 38..1064) return AisDecodeResult.Rejected("message_length")
        val type = b.u(0, 6)
        val mmsi = b.u(8, 30)
        if (type !in 1..63 || mmsi !in 1..999_999_999) return AisDecodeResult.Rejected("message_identity")
        val range = when (type) {
            1, 2, 3, 4, 9, 11, 18 -> 168..168
            5 -> 424..424
            19 -> 312..312
            21 -> 272..360
            24 -> 160..168
            27 -> 96..96
            12 -> 72..1008
            14 -> 40..1008
            6 -> 88..1008
            7, 13 -> 72..168
            8 -> 56..1008
            10 -> 72..72
            15 -> 88..160
            16 -> 96..144
            17 -> 80..816
            20 -> 72..160
            22 -> 168..168
            23 -> 160..160
            25 -> 40..168
            26 -> 60..1064
            28 -> 168..168
            else -> 38..1064
        }
        if (b.size !in range) return AisDecodeResult.Rejected("type_${type}_length")
        var kind = when (type) {
            1, 2, 3, 5 -> AisEntityKind.CLASS_A
            18, 19, 24 -> AisEntityKind.CLASS_B
            27 -> AisEntityKind.LONG_RANGE
            4, 11 -> AisEntityKind.BASE_STATION
            9 -> AisEntityKind.SAR_AIRCRAFT
            21 -> if (b.flag(269)) AisEntityKind.VIRTUAL_AID else AisEntityKind.AID_TO_NAVIGATION
            else -> AisEntityKind.UNKNOWN
        }
        kind = when (mmsi / 1_000_000) { 970 -> AisEntityKind.SART; 972 -> AisEntityKind.MOB; 974 -> AisEntityKind.EPIRB; else -> kind }
        fun <T> field(value: T?) = value?.let { AisStaticValue(it, source, now) }
        var stat = AisStaticData()
        var dynamic: AisDynamicReport? = null
        var safety: AisSafetyMessage? = null
        val invalid = linkedSetOf<String>()
        fun pos(lonOffset: Int, lonLength: Int, latOffset: Int, latLength: Int, divisor: Double): AisPoint? {
            val lon = b.s(lonOffset, lonLength) / divisor
            val lat = b.s(latOffset, latLength) / divisor
            return if (lon in -180.0..180.0 && lat in -90.0..90.0) AisPoint(lat, lon) else { invalid += "position"; null }
        }
        fun speed(offset: Int, length: Int = 10, divisor: Double = 10.0, unavailable: Int = 1023): Double? {
            val raw = b.u(offset, length)
            if (raw == unavailable) { invalid += "sog"; return null }
            if (raw == unavailable - 1) invalid += "sog_lower_bound"
            return raw / divisor * 1852.0 / 3600.0
        }
        fun angle(offset: Int, length: Int, divisor: Double, key: String): Double? {
            val raw = b.u(offset, length) / divisor
            return raw.takeIf { it < 360.0 } ?: run { invalid += key; null }
        }
        fun dims(offset: Int): AisDimensions? = AisDimensions(b.u(offset, 9), b.u(offset + 9, 9), b.u(offset + 18, 6), b.u(offset + 24, 6)).takeIf { it.lengthMeters > 0 || it.beamMeters > 0 }
        when (type) {
            1, 2, 3 -> {
                val turn = b.s(42, 8)
                if (turn == -127 || turn == 127) invalid += "rot_direction_only"
                dynamic = AisDynamicReport(source, now, type, pos(61, 28, 89, 27, 600000.0), speed(50), angle(116, 12, 10.0, "cog"), angle(128, 9, 1.0, "heading"), b.u(38, 4),
                    turn.takeIf { it !in listOf(-128, -127, 127) }?.let { sign(it.toDouble()) * (abs(it) / 4.733).let { v -> v * v } }, b.flag(60), b.flag(148), b.u(137, 6), tagTime)
            }
            4, 11 -> {
                val year = b.u(38, 14); val month = b.u(52, 4); val day = b.u(56, 5); val hour = b.u(61, 5); val minute = b.u(66, 6); val second = b.u(72, 6)
                val utc = if (year in 1..9999 && month in 1..12 && day in 1..31 && hour < 24 && minute < 60 && second < 60) "$year-$month-$day $hour:$minute:$second UTC (broadcast)" else tagTime
                dynamic = AisDynamicReport(source, now, type, pos(79, 28, 107, 27, 600000.0), positionAccurate = b.flag(78), raim = b.flag(148), utcSecond = second, sourceTimestampText = utc)
            }
            5 -> {
                val month = b.u(274, 4); val day = b.u(278, 5); val hour = b.u(283, 5); val minute = b.u(288, 6)
                val eta = if (month in 1..12 && day in 1..31 && hour < 24 && minute < 60) "%02d-%02d %02d:%02d UTC".format(month, day, hour, minute) else null
                stat = AisStaticData(name = field(b.text(112, 120)), callSign = field(b.text(70, 42)), shipType = field(b.u(232, 8).takeIf { it > 0 }), imo = field(b.u(40, 30).takeIf { it > 0 }), dimensions = field(dims(240)), destination = field(b.text(302, 120)), eta = field(eta), draughtMeters = field(b.u(294, 8).takeIf { it > 0 }?.div(10.0)))
            }
            9 -> dynamic = AisDynamicReport(source, now, type, pos(61, 28, 89, 27, 600000.0), speed(50, divisor = 1.0), angle(116, 12, 10.0, "cog"), positionAccurate = b.flag(60), raim = b.flag(147), utcSecond = b.u(128, 6), sourceTimestampText = tagTime, altitudeMeters = b.u(38, 12).takeIf { it < 4095 })
            12, 14 -> safety = AisSafetyMessage(b.text(if (type == 12) 72 else 40, b.size - if (type == 12) 72 else 40).orEmpty(), if (type == 12) b.u(40, 30) else null, now, source, type)
            18, 19 -> {
                dynamic = AisDynamicReport(source, now, type, pos(57, 28, 85, 27, 600000.0), speed(46), angle(112, 12, 10.0, "cog"), angle(124, 9, 1.0, "heading"), positionAccurate = b.flag(56), raim = b.flag(if (type == 18) 147 else 305), utcSecond = b.u(133, 6), sourceTimestampText = tagTime)
                if (type == 19) stat = AisStaticData(name = field(b.text(143, 120)), shipType = field(b.u(263, 8).takeIf { it > 0 }), dimensions = field(dims(271)))
            }
            21 -> {
                val name = (b.text(43, 120).orEmpty() + if (b.size > 272) b.text(272, b.size - 272).orEmpty() else "").trim().takeIf(String::isNotEmpty)
                stat = AisStaticData(name = field(name), dimensions = field(dims(219)), aidType = field(b.u(38, 5)))
                dynamic = AisDynamicReport(source, now, type, pos(164, 28, 192, 27, 600000.0), positionAccurate = b.flag(163), raim = b.flag(268), utcSecond = b.u(253, 6), sourceTimestampText = tagTime, offPosition = b.flag(259))
            }
            24 -> when (b.u(38, 2)) {
                0 -> if (b.size == 160 || b.size == 168) stat = AisStaticData(name = field(b.text(40, 120))) else return AisDecodeResult.Rejected("type_24a_length")
                1 -> {
                    if (b.size != 168) return AisDecodeResult.Rejected("type_24b_length")
                    val auxiliary = mmsi / 10_000_000 == 98
                    stat = AisStaticData(callSign = field(b.text(90, 42)), shipType = field(b.u(40, 8).takeIf { it > 0 }), vendor = field(b.text(48, 18)), dimensions = if (auxiliary) null else field(dims(132)), motherShipMmsi = if (auxiliary) field(b.u(132, 30).takeIf { it in 1..999_999_999 }) else null)
                }
                else -> return AisDecodeResult.Rejected("type_24_part")
            }
            27 -> dynamic = AisDynamicReport(source, now, type, pos(44, 18, 62, 17, 600.0), speed(79, 6, 1.0, 63), angle(85, 9, 1.0, "cog"), navigationStatus = b.u(40, 4), positionAccurate = b.flag(38), raim = b.flag(39), sourceTimestampText = tagTime, lowResolution = true, invalidFields = if (b.flag(94)) setOf("position_latency_over_five_seconds") else emptySet())
        }
        dynamic = dynamic?.let { report -> report.copy(invalidFields = report.invalidFields + invalid + when (report.utcSecond) { 61 -> setOf("manual_position"); 62 -> setOf("estimated_position"); 63 -> setOf("position_system_inoperative"); else -> emptySet() }) }
        val distress = if (kind in setOf(AisEntityKind.SART, AisEntityKind.MOB, AisEntityKind.EPIRB)) {
            val text = safety?.text
            val device = when (kind) { AisEntityKind.SART -> "SART"; AisEntityKind.MOB -> "MOB"; else -> "EPIRB" }
            fun matches(status: String): Boolean = text == "$device $status" || text?.matches(Regex("$device $status [0-9]{3}")) == true
            when {
                matches("TEST") -> AisDistressState.TEST
                matches("OFF") || matches("CANCEL") -> AisDistressState.INACTIVE
                matches("ACTIVE") || text == "ACTIVE $device" -> AisDistressState.ACTIVE
                dynamic?.navigationStatus == 14 -> AisDistressState.ACTIVE
                dynamic?.navigationStatus == 15 -> AisDistressState.TEST
                else -> null
            }
        } else null
        return AisDecodeResult.Message(DecodedAis(mmsi, kind, source.formatter.endsWith("VDO"), AisRawMessage(type, now, source, payload, fill), dynamic, stat, safety, distress))
    }
    private fun checksum(text: String, start: Int): Boolean {
        val star = text.lastIndexOf('*')
        if (star < start || star != text.length - 3) return false
        val expected = text.substring(star + 1).toIntOrNull(16) ?: return false
        var actual = 0
        for (i in start until star) actual = actual xor text[i].code
        return actual == expected
    }
    private class Bits(payload: String, fill: Int) {
        private val values = payload.map(::six)
        val size = payload.length * 6 - fill
        fun u(offset: Int, count: Int): Int {
            require(offset >= 0 && count in 1..30 && offset + count <= size)
            var value = 0
            for (i in offset until offset + count) value = (value shl 1) or ((values[i / 6] shr (5 - i % 6)) and 1)
            return value
        }
        fun s(offset: Int, count: Int): Int { val value = u(offset, count); return if (value and (1 shl (count - 1)) != 0) value - (1 shl count) else value }
        fun flag(offset: Int) = u(offset, 1) == 1
        fun text(offset: Int, count: Int): String? = buildString {
            for (i in 0 until count / 6) { val value = u(offset + i * 6, 6); if (value == 0) break; append((if (value < 32) value + 64 else value).toChar()) }
        }.trim().takeIf(String::isNotEmpty)
    }
    companion object { private fun six(c: Char): Int = (c.code - 48).let { if (it > 40) it - 8 else it } }
}
