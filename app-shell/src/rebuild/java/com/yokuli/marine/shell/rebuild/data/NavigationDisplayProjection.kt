package com.yokuli.marine.shell.rebuild.data

import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.runtime.contract.navigation.*

/** 仅显示层覆盖导航指标；原始NMEA候选和全船仲裁数据始终保留，历史只接受本次新观测。 */
fun VesselDataSnapshot.withNavigation(state: NavigationState): VesselDataSnapshot {
    val session = state.session?.takeIf { it.ongoing }
    val guide = state.guidance?.takeIf { session != null && it.sessionId == session.id && it.sessionRevision == session.revision }
    val identity = session?.let { VesselSourceIdentity("navigation:${it.id}:${it.revision}", sourceType = VesselSourceType.APP_DERIVED,
        displayName = it.route?.name ?: guide?.positionSource ?: "Navigation") }
    fun <T> observation(value: T?, reference: VesselReference? = null, elapsed:Long?=guide?.positionElapsedMillis): VesselObservation<T> = VesselObservation(
        value = value, source = if (session == null) VesselDataSource.NONE else VesselDataSource.DERIVED,
        observedAtUtcMillis = guide?.positionObservedUtcMillis?.let {it+(elapsed?:0)-(guide?.positionElapsedMillis?:0)}, receivedElapsedRealtime = elapsed,
        quality = if (value == null) VesselDataQuality.UNKNOWN else VesselDataQuality.GOOD,
        freshness = when { value == null -> VesselDataFreshness.UNAVAILABLE; guide?.live == true -> VesselDataFreshness.FRESH; else -> VesselDataFreshness.HELD },
        provenance = identity?.displayName, sourceIdentity = identity, reference = reference,
        selectionReason = guide?.issue,
    )
    return copy(waypointBearingTrueDegrees = observation(guide?.bearingTrueDegrees, VesselReference.TrueNorth,guide?.bearingElapsedMillis),
        waypointDistanceNauticalMiles = observation(guide?.distanceMeters?.div(1852),elapsed=guide?.distanceElapsedMillis),
        crossTrackErrorNauticalMiles = observation(guide?.crossTrackMeters?.div(1852),elapsed=guide?.crossTrackElapsedMillis),
        destinationWaypoint = observation(session?.route?.let { "${it.name} · ${it.targetIndices.indexOf(session.targetIndex) + 1}/${it.targetIndices.size}" } ?: guide?.targetName),
        derived = derived.copy(vmcToWaypointKnots = observation(guide?.progressMetersPerSecond?.div(.5144444444),elapsed=guide?.progressElapsedMillis)))
}
