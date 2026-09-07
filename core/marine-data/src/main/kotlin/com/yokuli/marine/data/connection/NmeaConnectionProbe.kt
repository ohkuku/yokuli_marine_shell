package com.yokuli.marine.data.connection

import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.runtime.NmeaRuntimeFailure

const val DEFAULT_NMEA_PROBE_TIMEOUT_MILLIS: Long = 8_000L

sealed interface NmeaConnectionProbeResult {
    data class Detected(
        val dataKeys: Set<DataKey>,
        val legalFrameCount: Long,
    ) : NmeaConnectionProbeResult {
        init {
            require(dataKeys.isNotEmpty())
            require(legalFrameCount > 0L)
        }
    }

    /** Transport readiness and legal-but-unknown NMEA never masquerade as usable semantic data. */
    data class NoSemanticData(
        val transportReady: Boolean,
        val legalFrameCount: Long,
    ) : NmeaConnectionProbeResult {
        init { require(legalFrameCount >= 0L) }
    }

    data class Failed(val failure: NmeaRuntimeFailure) : NmeaConnectionProbeResult
}

/** Temporary, non-persisting connection test owned by a platform adapter. */
fun interface NmeaConnectionProbePort {
    suspend fun probe(
        config: NmeaConnectionConfig,
        timeoutMillis: Long,
    ): NmeaConnectionProbeResult
}
