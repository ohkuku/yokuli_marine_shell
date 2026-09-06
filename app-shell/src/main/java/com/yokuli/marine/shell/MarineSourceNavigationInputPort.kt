package com.yokuli.marine.shell

import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.ResolvedDatum
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.marine.navigation.domain.NavigationFix
import com.yokuli.marine.navigation.domain.NavigationInputPort
import com.yokuli.marine.navigation.domain.NavigationInputStatus
import com.yokuli.marine.navigation.domain.NavigationPosition
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Read-only composition adapter from the OS selection result to active navigation.
 * It neither selects sources nor starts Phone/NMEA inputs, and it never reveals endpoint details.
 */
class MarineSourceNavigationInputPort(
    snapshots: StateFlow<MarineSourceSnapshot>,
    scope: CoroutineScope,
) : NavigationInputPort {
    override val state: StateFlow<NavigationFix> = snapshots
        .map(MarineSourceSnapshot::toNavigationFix)
        .stateIn(scope, SharingStarted.Eagerly, snapshots.value.toNavigationFix())
}

private fun MarineSourceSnapshot.toNavigationFix(): NavigationFix {
    val positionDatum = resolvedData.items[DataKey.Position]
    val positionValue = positionDatum?.value as? MarineValue.Position
    val positionCandidate = positionDatum?.candidate
    val rawPositionStatus = positionDatum?.availability.toNavigationStatus()
    val positionUsable = positionValue != null && positionCandidate?.receivedAtMillis != null &&
        rawPositionStatus in setOf(NavigationInputStatus.LIVE, NavigationInputStatus.HELD)
    val positionStatus = if (rawPositionStatus in setOf(NavigationInputStatus.LIVE, NavigationInputStatus.HELD) && !positionUsable) {
        NavigationInputStatus.UNAVAILABLE
    } else rawPositionStatus
    val speedDatum = resolvedData.items[DataKey.SpeedOverGround]
        .usableDecimal(MarineUnit.KNOTS) { it >= 0.0 }
    val courseDatum = resolvedData.items[DataKey.CourseOverGround]
        .usableDecimal(MarineUnit.DEGREES) { it in 0.0..<360.0 }
    val motionAnchor = speedDatum ?: courseDatum
    val atomicCourse = courseDatum?.takeIf { motionAnchor == null || it.sameFrameAs(motionAnchor) }
    return NavigationFix(
        revision = revision,
        position = positionValue?.takeIf { positionUsable }?.let { NavigationPosition(it.latitudeDegrees, it.longitudeDegrees) },
        positionStatus = positionStatus,
        positionSourceId = positionDatum?.source?.opaqueId()?.takeIf { positionUsable },
        motionSourceId = motionAnchor?.source?.opaqueId(),
        receivedAtMonotonicMillis = positionCandidate?.receivedAtMillis,
        ageMillis = positionCandidate?.ageMillis,
        speedOverGroundKnots = speedDatum?.decimalValue(MarineUnit.KNOTS),
        courseOverGroundTrueDegrees = atomicCourse?.decimalValue(MarineUnit.DEGREES),
    )
}

private fun ResolvedDatum?.usableDecimal(unit: MarineUnit, valid: (Double) -> Boolean): ResolvedDatum? = this?.takeIf {
    val number = decimalValue(unit)
    availability in USABLE_AVAILABILITY && number?.isFinite() == true && valid(number)
}

private fun ResolvedDatum.decimalValue(unit: MarineUnit): Double? =
    (value as? MarineValue.Decimal)?.takeIf { it.unit == unit }?.value

private fun ResolvedDatum.sameFrameAs(other: ResolvedDatum): Boolean =
    source == other.source && candidate?.groupId != null && candidate?.groupId == other.candidate?.groupId

private fun SourceCandidateAvailability?.toNavigationStatus(): NavigationInputStatus = when (this) {
    SourceCandidateAvailability.LIVE -> NavigationInputStatus.LIVE
    SourceCandidateAvailability.HELD -> NavigationInputStatus.HELD
    SourceCandidateAvailability.STALE -> NavigationInputStatus.STALE
    SourceCandidateAvailability.INVALID -> NavigationInputStatus.INVALID
    SourceCandidateAvailability.UNAVAILABLE, SourceCandidateAvailability.MISSING, null -> NavigationInputStatus.UNAVAILABLE
}

private fun SourceIdentity.opaqueId(): String {
    val stable = buildString {
        append(connectionId.value)
        udpOrigin?.let { append('|').append(it.hostAddress).append('|').append(it.port ?: "host") }
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(stable.toByteArray(Charsets.UTF_8))
    return "marine-" + digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private val USABLE_AVAILABILITY = setOf(SourceCandidateAvailability.LIVE, SourceCandidateAvailability.HELD)
