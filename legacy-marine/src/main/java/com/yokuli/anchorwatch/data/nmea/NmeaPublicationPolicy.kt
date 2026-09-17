package com.yokuli.anchorwatch.data.nmea

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** 用户选择的是数据能力，不是难懂的 NMEA 句型；接收与发送共用这些稳定标识。 */
enum class NmeaCapability(val id: String, val zh: String, val en: String) {
    POSITION("position", "船位与航速", "position & speed"),
    HEADING("heading", "船首向", "heading"),
    ROTATION("rotation", "转向速率", "rate of turn"),
    ATTITUDE("attitude", "横倾与纵倾", "heel & pitch"),
    PRESSURE("pressure", "气压", "pressure"),
    APPARENT_WIND("apparent_wind", "视风", "apparent wind"),
    TRUE_WIND("true_wind", "真风", "true wind"),
    WATER_SPEED("water_speed", "对水航速", "speed through water"),
    DEPTH("depth", "水深读数", "depth reading"),
    TEMPERATURE("temperature", "水温", "water temperature"),
    OTHER("other", "其他原始数据", "other raw data");

    companion object {
        val generated = entries.filter { it != OTHER }.mapTo(linkedSetOf()) { it.id }
        val phone = setOf(POSITION.id, HEADING.id, ROTATION.id, ATTITUDE.id, PRESSURE.id)
        val all = entries.mapTo(linkedSetOf()) { it.id }
    }
}

/** 每个目的地拥有独立能力开关；null 仅用于兼容旧配置，空集合明确表示一项也不发送。 */
object NmeaPublicationPolicy {
    fun selected(spec: NmeaConnectionSpec): Set<String> = spec.capabilities ?: when (spec.feed) {
        NmeaFeed.PHONE -> NmeaCapability.phone
        NmeaFeed.RAW -> NmeaCapability.all
        NmeaFeed.SYSTEM -> NmeaCapability.generated
    }

    fun type(sentence: String) = sentence.trim().removePrefix("$").removePrefix("!").substringBefore(',').takeLast(3).uppercase()

    /** 原始 XDR 可能混装姿态与气压，按四字段数据组筛选并重新计算校验，避免关闭项泄漏。 */
    fun filter(sentence: String, capabilities: Set<String>): String? {
        val fields = sentence.trim().substringBefore('*').split(',')
        if (fields.isEmpty()) return null
        val kind = type(sentence)
        fun allowed(capability: NmeaCapability) = capability.id in capabilities
        if(kind=="VHW"||kind=="MDA"){
            val result=fields.toMutableList()
            val groups=if(kind=="VHW")listOf(
                NmeaCapability.HEADING to (1..4),NmeaCapability.WATER_SPEED to (5..8)
            )else listOf(
                NmeaCapability.PRESSURE to (1..4),NmeaCapability.TEMPERATURE to (5..8),
                NmeaCapability.OTHER to (9..10),NmeaCapability.TEMPERATURE to (11..12),
                NmeaCapability.TRUE_WIND to (13..20)
            )
            var kept=false
            groups.forEach{(capability,indices)->
                if(allowed(capability)){if(indices.any{result.getOrNull(it)?.toDoubleOrNull()!=null})kept=true}
                else indices.forEach{if(it<result.size)result[it]=""}
            }
            if(!kept)return null
            return NmeaChecksum.append(result.joinToString(",").removePrefix("$"))+"\r\n"
        }
        if (kind == "XDR") {
            val groups = fields.drop(1).chunked(4).filter { group ->
                group.size == 4 && allowed(when (group[0].uppercase()) {
                    "P" -> NmeaCapability.PRESSURE
                    "A" -> NmeaCapability.ATTITUDE
                    "C" -> NmeaCapability.TEMPERATURE
                    else -> NmeaCapability.OTHER
                })
            }
            if (groups.isEmpty()) return null
            return NmeaChecksum.append((listOf(fields.first().removePrefix("$")) + groups.flatten()).joinToString(",")) + "\r\n"
        }
        val capability = when (kind) {
            "RMC", "GGA", "GLL", "GNS", "VTG", "ZDA", "GSA", "GSV" -> NmeaCapability.POSITION
            "HDT", "HDG", "HDM", "THS" -> NmeaCapability.HEADING
            "ROT" -> NmeaCapability.ROTATION
            "MWV" -> if (fields.getOrNull(2) == "T") NmeaCapability.TRUE_WIND else NmeaCapability.APPARENT_WIND
            "MWD", "VWT" -> NmeaCapability.TRUE_WIND
            "VWR" -> NmeaCapability.APPARENT_WIND
            "VHW", "VBW" -> NmeaCapability.WATER_SPEED
            "DPT", "DBT", "DBS", "DBK" -> NmeaCapability.DEPTH
            "MTW" -> NmeaCapability.TEMPERATURE
            else -> NmeaCapability.OTHER
        }
        return sentence.takeIf { allowed(capability) }
    }

    fun filter(spec: NmeaConnectionSpec, sentence: String): String? {
        if (spec.sentenceTypes.isNotEmpty() && type(sentence) !in spec.sentenceTypes) return null
        return filter(sentence, selected(spec))
    }
}

/** 防止同 IP 的数据回流，端口不同也不能绕过；解析设备别名和 IPv4/IPv6 地址。仅在 IO 协程调用。 */
@Singleton
class NmeaPeerGuard @Inject constructor() {
    private data class Entry(val addresses: Set<String>, val expiresAt: Long)
    private val cache = ConcurrentHashMap<String, Entry>()

    fun sameHost(source: String?, destination: String): Boolean {
        if (source.isNullOrBlank() || destination.isBlank()) return false
        val from = host(source)
        val to = host(destination)
        if (from == to) return true
        return addresses(from).intersect(addresses(to)).isNotEmpty()
    }

    private fun addresses(value: String): Set<String> {
        val now = System.nanoTime() / 1_000_000L
        cache[value]?.takeIf { it.expiresAt > now }?.let { return it.addresses }
        val result = runCatching { InetAddress.getAllByName(value).mapTo(mutableSetOf()) { it.hostAddress.orEmpty().substringBefore('%').lowercase() } }.getOrDefault(emptySet())
        cache[value] = Entry(result, now + if (result.isEmpty()) 5_000 else 60_000)
        return result
    }

    companion object {
        /** Transport 层统一格式化，不能把 IPv6 的最后一段与端口混淆。 */
        fun endpoint(address:InetAddress,port:Int):String {
            val host=address.hostAddress.orEmpty()
            return if(':' in host)"[$host]:$port" else "$host:$port"
        }
        /** 支持 /ip:port、hostname/ip:port、[IPv6]:port；不把未加括号的 IPv6 尾段误当端口。 */
        fun host(peer: String): String {
            val value = peer.trim().substringAfterLast('/').lowercase()
            return when {
                value.startsWith("[") -> value.substringAfter('[').substringBefore(']')
                value.count { it == ':' } == 1 -> value.substringBefore(':')
                else -> value
            }.substringBefore('%').removeSuffix(".")
        }
    }
}
