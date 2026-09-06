package com.yokuli.marine.data.source

import com.yokuli.marine.data.catalog.Freshness
import com.yokuli.marine.data.catalog.ObservationCatalogSnapshot
import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.ObservationValidity
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.phone.METERS_PER_SECOND_TO_KNOTS
import com.yokuli.marine.data.phone.PHONE_SYSTEM_LOCATION_SOURCE
import com.yokuli.marine.data.phone.PhoneLocationPermission
import com.yokuli.marine.data.phone.PhoneLocationSnapshot
import com.yokuli.marine.data.phone.PhoneLocationState

class SourceCatalogProjector {
    fun project(
        observationCatalog: ObservationCatalogSnapshot,
        connectionNames: Map<com.yokuli.marine.data.model.ConnectionId, String>,
        phone: PhoneLocationSnapshot,
        nowMillis: Long,
        activeConnectionIds: Set<com.yokuli.marine.data.model.ConnectionId> = connectionNames.keys,
    ): SourceCatalogSnapshot {
        require(nowMillis >= 0L)
        val nmeaCandidates = observationCatalog.candidates.map { candidate ->
            val selected = candidate.selectedObservation
            SourceCandidate(
                id = candidate.id,
                descriptor = SourceDescriptor(
                    identity = candidate.id.source,
                    kind = SourceKind.NMEA,
                    displayName = connectionNames[candidate.id.source.connectionId]
                        ?: candidate.id.source.connectionId.value,
                    detail = candidate.contributingFormatters.sorted().joinToString("/").ifBlank { "NMEA" },
                    present = candidate.id.source.connectionId in activeConnectionIds,
                ),
                value = selected.value.takeIf { selected.validity == ObservationValidity.VALID },
                lastValidValue = candidate.lastValidObservation?.value,
                availability = candidate.freshness.state.toCandidateAvailability(),
                ageMillis = candidate.freshness.ageMillis,
                receivedAtMillis = selected.measuredAtMillis,
                groupId = selected.groupId,
                evidence = SourceEvidence.Nmea(
                    sentenceIds = candidate.contributingSentenceIds,
                    formatters = candidate.contributingFormatters,
                ),
            )
        }
        val phoneCandidates = phone.latestFix?.let { fix ->
            val availability = phone.availability(nowMillis, fix.receivedAtMillis)
            val descriptor = SourceDescriptor(
                identity = PHONE_SYSTEM_LOCATION_SOURCE,
                kind = SourceKind.PHONE_SYSTEM_LOCATION,
                // This is an internal stable label. Feature resources localize the visible title.
                displayName = PHONE_SYSTEM_LOCATION_SOURCE.connectionId.value,
                detail = fix.provider,
            )
            val evidence = SourceEvidence.PhoneSystemLocation(
                provider = fix.provider,
                permissionIsApproximate = phone.permission == PhoneLocationPermission.APPROXIMATE,
            )
            buildList {
                add(
                    phoneCandidate(
                        DataKey.Position,
                        MarineValue.Position(fix.latitudeDegrees, fix.longitudeDegrees),
                        descriptor,
                        evidence,
                        availability,
                        fix.receivedAtMillis,
                        fix.sequence,
                        nowMillis,
                    ),
                )
                fix.speedKnots?.let { speed ->
                    add(
                        phoneCandidate(
                            DataKey.SpeedOverGround,
                            MarineValue.Decimal(speed, MarineUnit.KNOTS),
                            descriptor,
                            evidence,
                            availability,
                            fix.receivedAtMillis,
                            fix.sequence,
                            nowMillis,
                        ),
                    )
                }
                fix.courseOverGroundDegrees?.let { course ->
                    add(
                        phoneCandidate(
                            DataKey.CourseOverGround,
                            MarineValue.Decimal(course, MarineUnit.DEGREES),
                            descriptor,
                            evidence,
                            availability,
                            fix.receivedAtMillis,
                            fix.sequence,
                            nowMillis,
                        ),
                    )
                }
                fix.horizontalAccuracyMeters?.let { accuracy ->
                    add(
                        phoneCandidate(
                            DataKey.PositionAccuracy,
                            MarineValue.Decimal(accuracy, MarineUnit.METERS),
                            descriptor,
                            evidence,
                            availability,
                            fix.receivedAtMillis,
                            fix.sequence,
                            nowMillis,
                        ),
                    )
                }
            }
        }.orEmpty()

        val candidates = (nmeaCandidates + phoneCandidates).sortedBy { it.id.stableKey() }
        return SourceCatalogSnapshot(
            candidates = candidates,
            evaluatedAtMillis = nowMillis,
            revision = maxOf(observationCatalog.evaluatedAtMillis, phone.revision, nowMillis),
        )
    }
}

private fun phoneCandidate(
    key: DataKey,
    value: MarineValue,
    descriptor: SourceDescriptor,
    evidence: SourceEvidence.PhoneSystemLocation,
    availability: SourceCandidateAvailability,
    receivedAtMillis: Long,
    sequence: Long,
    nowMillis: Long,
) = SourceCandidate(
    id = CandidateId(key, PHONE_SYSTEM_LOCATION_SOURCE),
    descriptor = descriptor,
    value = value,
    lastValidValue = value,
    availability = availability,
    ageMillis = (nowMillis - receivedAtMillis).coerceAtLeast(0L),
    receivedAtMillis = receivedAtMillis,
    groupId = ObservationGroupId(sequence),
    evidence = evidence,
)

private fun PhoneLocationSnapshot.availability(
    nowMillis: Long,
    receivedAtMillis: Long,
): SourceCandidateAvailability {
    if (!enabledByUser || state != PhoneLocationState.RECEIVING) {
        return SourceCandidateAvailability.UNAVAILABLE
    }
    val age = nowMillis - receivedAtMillis
    if (age < 0L) return SourceCandidateAvailability.UNAVAILABLE
    return when {
        age < 3_000L -> SourceCandidateAvailability.LIVE
        age < 10_000L -> SourceCandidateAvailability.HELD
        else -> SourceCandidateAvailability.STALE
    }
}

private fun Freshness.toCandidateAvailability(): SourceCandidateAvailability = when (this) {
    Freshness.LIVE -> SourceCandidateAvailability.LIVE
    Freshness.HELD -> SourceCandidateAvailability.HELD
    Freshness.STALE -> SourceCandidateAvailability.STALE
    Freshness.INVALID -> SourceCandidateAvailability.INVALID
    Freshness.UNAVAILABLE -> SourceCandidateAvailability.UNAVAILABLE
}

private fun CandidateId.stableKey(): String = "$key|${source.connectionId.value}|${source.udpOrigin}"
