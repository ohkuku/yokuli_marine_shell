package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.ManualRouteDraft
import com.yokuli.marine.navigation.domain.NavigationLibrary
import com.yokuli.marine.navigation.domain.NavigationLibraryChange
import com.yokuli.marine.navigation.domain.NavigationPosition
import com.yokuli.marine.navigation.domain.RoutePlan
import com.yokuli.marine.navigation.domain.RoutePoint
import com.yokuli.marine.navigation.domain.Waypoint
import com.yokuli.marine.navigation.domain.WaypointRevisionReference

/** Pure Chart-operation -> canonical Navigation mutation boundary. */
object ChartNavigationCommitPlanner {
    fun quickMark(
        library: NavigationLibrary,
        point: GeoPoint,
        id: String,
        nowMillis: Long,
    ): NavigationLibraryChange.PutWaypoint {
        val nextOrdinal = library.waypoints.asSequence().mapNotNull { waypoint ->
            Regex("^WP\\s+(\\d{1,6})$", RegexOption.IGNORE_CASE).matchEntire(waypoint.name.trim())
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
        }.maxOrNull()?.plus(1) ?: 1
        return NavigationLibraryChange.PutWaypoint(
            Waypoint(
                id = id,
                revision = 1L,
                name = "WP %03d".format(nextOrdinal),
                position = NavigationPosition(point.latitude, point.longitude),
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis,
            ),
        )
    }

    fun saveRoute(
        library: NavigationLibrary,
        draft: ManualRouteDraft,
        nameOverride: String? = null,
    ): NavigationLibraryChange.PutRoutePlan? {
        if (draft.waypoints.size < 2) return null
        val saveAsCopy = nameOverride != null && draft.basePlanId != null
        val targetId = if (saveAsCopy) draft.id else draft.basePlanId ?: draft.id
        val current = library.routePlans.firstOrNull { it.id == targetId }
        if (!saveAsCopy && draft.basePlanId != null && current?.revision != draft.basePlanRevision) return null
        val name = nameOverride?.trim()?.takeIf(String::isNotEmpty)
            ?: draft.name.trim().takeIf(String::isNotEmpty)
            ?: nextRouteName(library)
        return NavigationLibraryChange.PutRoutePlan(
            RoutePlan(
                id = targetId,
                revision = (current?.revision ?: 0L) + 1L,
                name = name,
                points = draft.waypoints.mapIndexed { index, point ->
                    RoutePoint(
                        id = draft.waypointIds[index],
                        position = NavigationPosition(point.latitude, point.longitude),
                        sourceWaypoint = draft.waypointPlaceReferences[index]?.let { reference ->
                            WaypointRevisionReference(reference.placeId, reference.revision)
                        },
                    )
                },
                plannedSpeedKnots = draft.plannedSpeedKnots,
                notes = draft.notes,
                sourceDraftId = draft.id,
                sourceDraftRevision = draft.revision,
            ),
        )
    }

    private fun nextRouteName(library: NavigationLibrary): String {
        val ordinal = library.routePlans.asSequence().mapNotNull { route ->
            Regex("^Route\\s+(\\d{1,6})$", RegexOption.IGNORE_CASE).matchEntire(route.name.trim())
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
        }.maxOrNull()?.plus(1) ?: 1
        return "Route %03d".format(ordinal)
    }
}
