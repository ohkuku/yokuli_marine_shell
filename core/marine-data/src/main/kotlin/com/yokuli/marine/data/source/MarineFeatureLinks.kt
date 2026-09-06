package com.yokuli.marine.data.source

import com.yokuli.marine.data.model.ConnectionId
import java.nio.charset.StandardCharsets

const val MAX_LINK_PAYLOAD_BYTES: Int = 128

@JvmInline
value class MarineFeatureLinkToken(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

sealed interface MarineFeatureDestination {
    data class DataSources(val connectionId: ConnectionId?) : MarineFeatureDestination
    data object DataSourcesAttention : MarineFeatureDestination
    data class NmeaInput(val connectionId: ConnectionId?) : MarineFeatureDestination
}

object MarineFeatureLinks {
    private const val SOURCES_ROOT = "sources.root"
    private const val SOURCES_CONNECTION = "sources.connection."
    private const val SOURCES_ATTENTION = "sources.attention"
    private const val NMEA_ROOT = "nmea.root"
    private const val NMEA_CONNECTION = "nmea.connection."

    val dataSourcesRoot = MarineFeatureLinkToken(SOURCES_ROOT)
    val dataSourcesAttention = MarineFeatureLinkToken(SOURCES_ATTENTION)
    val nmeaInputRoot = MarineFeatureLinkToken(NMEA_ROOT)

    fun dataSourcesForConnection(connectionId: ConnectionId): MarineFeatureLinkToken =
        encode(SOURCES_CONNECTION, connectionId)

    fun nmeaInputForConnection(connectionId: ConnectionId): MarineFeatureLinkToken =
        encode(NMEA_CONNECTION, connectionId)

    fun parse(token: MarineFeatureLinkToken): MarineFeatureDestination? = when {
        token.value == SOURCES_ROOT -> MarineFeatureDestination.DataSources(null)
        token.value == SOURCES_ATTENTION -> MarineFeatureDestination.DataSourcesAttention
        token.value == NMEA_ROOT -> MarineFeatureDestination.NmeaInput(null)
        token.value.startsWith(SOURCES_CONNECTION) -> decode(
            token.value.removePrefix(SOURCES_CONNECTION),
        )?.let(MarineFeatureDestination::DataSources)
        token.value.startsWith(NMEA_CONNECTION) -> decode(
            token.value.removePrefix(NMEA_CONNECTION),
        )?.let(MarineFeatureDestination::NmeaInput)
        else -> null
    }

    private fun encode(prefix: String, id: ConnectionId): MarineFeatureLinkToken {
        val bytes = id.value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_LINK_PAYLOAD_BYTES)
        return MarineFeatureLinkToken(prefix + bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) })
    }

    private fun decode(encoded: String): ConnectionId? {
        if (encoded.isEmpty() || encoded.length % 2 != 0 || encoded.length / 2 > MAX_LINK_PAYLOAD_BYTES) {
            return null
        }
        if (encoded.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        val bytes = ByteArray(encoded.length / 2) { index ->
            encoded.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        val decoded = bytes.toString(StandardCharsets.UTF_8)
        if (decoded.toByteArray(StandardCharsets.UTF_8).contentEquals(bytes).not()) return null
        return runCatching { ConnectionId(decoded) }.getOrNull()
    }
}
