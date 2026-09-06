package com.yokuli.marine.shell

import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.ResolvedDatum
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MonotonicTime
import com.yokuli.marine.map.domain.ObservationIdentity
import com.yokuli.marine.map.domain.ObservationSource
import com.yokuli.marine.map.domain.ObservationValidity
import com.yokuli.marine.map.domain.PositionObservation
import com.yokuli.marine.map.domain.PositionPortEvent
import com.yokuli.marine.map.domain.ReadOnlyPositionPort
import com.yokuli.marine.map.domain.SampleTimeConfidence
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/**
 * Composition adapter from the single OS source decision to Chart's existing read-only port.
 * It never selects, fails over, requests permission, starts a transport, or exposes endpoint data.
 */
class MarineSourcePositionPort(
    private val snapshots: StateFlow<MarineSourceSnapshot>,
    private val sourceEpoch: String,
) : ReadOnlyPositionPort {
    init {
        require(sourceEpoch.isNotBlank())
    }

    override val events: Flow<PositionPortEvent> = flow {
        var activeSource: ObservationSource? = null
        var lastObservationId: String? = null
        snapshots.collect { snapshot ->
            val projected = snapshot.projectPosition()
            if (projected == null) {
                activeSource?.let { emit(PositionPortEvent.SourceDisconnected(it)) }
                activeSource = null
                lastObservationId = null
                return@collect
            }

            if (activeSource != projected.source) {
                activeSource?.let { emit(PositionPortEvent.SourceDisconnected(it)) }
                emit(PositionPortEvent.SourceConnected(projected.source))
                activeSource = projected.source
                lastObservationId = null
            }
            if (lastObservationId != projected.observation.identity.observationId) {
                emit(PositionPortEvent.Position(projected.observation))
                lastObservationId = projected.observation.identity.observationId
            }
        }
    }

    private fun MarineSourceSnapshot.projectPosition(): ProjectedPosition? {
        val datum = resolvedData.items[DataKey.Position] ?: return null
        if (datum.availability !in CONNECTED_AVAILABILITY) return null
        val value = datum.value as? MarineValue.Position ?: return null
        val candidate = datum.candidate ?: return null
        val receivedAt = candidate.receivedAtMillis ?: return null
        val group = candidate.groupId ?: return null
        val source = datum.source.toObservationSource(sourceEpoch)
        val sourceTime = resolvedData.items[DataKey.SourceTime]
            ?.takeIf { it.source == datum.source && it.availability in CONNECTED_AVAILABILITY }
            ?.value
            .let { it as? MarineValue.UtcEpochMillis }
            ?.value
            ?.takeIf { it >= 0L }
        val accuracy = resolvedData.items[DataKey.PositionAccuracy].accuracyFor(datum)
        val identity = ObservationIdentity(
            source = source,
            observationId = "position:$receivedAt:${group.frameSequence}",
            sampledAtUtcMillis = sourceTime,
            sampleTimeConfidence = if (sourceTime == null) {
                SampleTimeConfidence.ARRIVAL_ONLY
            } else {
                SampleTimeConfidence.REPORTED_UTC
            },
            receivedAt = MonotonicTime(sourceEpoch, receivedAt),
        )
        return ProjectedPosition(
            source,
            PositionObservation(
                identity = identity,
                point = GeoPoint(value.latitudeDegrees, value.longitudeDegrees),
                validity = ObservationValidity.VALID,
                horizontalAccuracyMeters = accuracy,
            ),
        )
    }

    private fun ResolvedDatum?.accuracyFor(position: ResolvedDatum): Double? {
        if (this == null || source != position.source || availability !in CONNECTED_AVAILABILITY) return null
        val decimal = value as? MarineValue.Decimal ?: return null
        return decimal.value.takeIf { decimal.unit == MarineUnit.METERS && it >= 0.0 }
    }

    private data class ProjectedPosition(
        val source: ObservationSource,
        val observation: PositionObservation,
    )

    private companion object {
        val CONNECTED_AVAILABILITY = setOf(SourceCandidateAvailability.LIVE, SourceCandidateAvailability.HELD)
    }
}

private fun SourceIdentity.toObservationSource(epoch: String): ObservationSource {
    val stableInput = buildString {
        append(connectionId.value)
        udpOrigin?.let {
            append('|')
            append(it.hostAddress)
            append('|')
            append(it.port ?: "host")
        }
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(stableInput.toByteArray(Charsets.UTF_8))
    val opaqueId = digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return ObservationSource(sourceId = "marine-$opaqueId", sourceEpoch = epoch)
}
