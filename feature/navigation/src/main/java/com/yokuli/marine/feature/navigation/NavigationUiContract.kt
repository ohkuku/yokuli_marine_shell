package com.yokuli.marine.feature.navigation

import com.yokuli.marine.navigation.domain.ActiveNavigationCommand
import com.yokuli.marine.navigation.domain.ActiveNavigationSnapshot
import com.yokuli.marine.navigation.domain.NavigationLibrary
import com.yokuli.marine.navigation.domain.NavigationPosition
import com.yokuli.marine.navigation.domain.RoutePlan
import com.yokuli.marine.navigation.domain.RoutePoint
import com.yokuli.marine.navigation.domain.Waypoint
import com.yokuli.shell.contract.LaunchToken

enum class NavigationSection { OVERVIEW, WAYPOINTS, ROUTES, GPX, ACTIVE }

sealed interface NavigationPage {
    data object Root : NavigationPage
    data class WaypointEditor(val draft: WaypointDraftUi) : NavigationPage
    data class RouteDetail(val routeId: String) : NavigationPage
    data class RouteEditor(val draft: RouteDraftUi) : NavigationPage
}

data class WaypointDraftUi(
    val id: String,
    val editingRevision: Long?,
    val name: String,
    val latitude: String,
    val longitude: String,
    val notes: String,
)

data class RouteDraftUi(
    val id: String,
    val editingRevision: Long?,
    val name: String,
    val plannedSpeedKnots: String,
    val notes: String,
    val points: List<RoutePoint>,
)

enum class NavigationNotice {
    SAVED,
    DELETED,
    REVISION_CONFLICT,
    INVALID_INPUT,
    STORAGE_FAILED,
    ACTIVE_ROUTE_LOCKED,
    ACTION_QUEUE_FULL,
    NAVIGATION_REJECTED,
}

data class NavigationUiState(
    val section: NavigationSection = NavigationSection.OVERVIEW,
    val page: NavigationPage = NavigationPage.Root,
    val library: NavigationLibrary = NavigationLibrary(),
    val active: ActiveNavigationSnapshot = ActiveNavigationSnapshot.EMPTY,
    val loading: Boolean = true,
    val notice: NavigationNotice? = null,
) {
    val selectedRoute: RoutePlan?
        get() = (page as? NavigationPage.RouteDetail)?.routeId?.let { id -> library.routePlans.firstOrNull { it.id == id } }
}

sealed interface NavigationUiAction {
    data class Navigate(val section: NavigationSection) : NavigationUiAction
    data object NavigateUp : NavigationUiAction
    data object Refresh : NavigationUiAction
    data object DismissNotice : NavigationUiAction
    data object CreateWaypoint : NavigationUiAction
    data class EditWaypoint(val waypointId: String) : NavigationUiAction
    data class UpdateWaypointDraft(
        val name: String? = null,
        val latitude: String? = null,
        val longitude: String? = null,
        val notes: String? = null,
    ) : NavigationUiAction
    data object SaveWaypoint : NavigationUiAction
    data class DeleteWaypoint(val waypointId: String, val revision: Long) : NavigationUiAction
    data object CreateRoute : NavigationUiAction
    data class OpenRoute(val routeId: String) : NavigationUiAction
    data class EditRoute(val routeId: String) : NavigationUiAction
    data class UpdateRouteDraft(val name: String? = null, val plannedSpeedKnots: String? = null, val notes: String? = null) : NavigationUiAction
    data class AddWaypointToRoute(val waypointId: String) : NavigationUiAction
    data class MoveRoutePoint(val pointId: String, val delta: Int) : NavigationUiAction
    data class RemoveRoutePoint(val pointId: String) : NavigationUiAction
    data object SaveRoute : NavigationUiAction
    data class DeleteRoute(val routeId: String, val revision: Long) : NavigationUiAction
    data class ShowRouteInChart(val routeId: String) : NavigationUiAction
    data class StartRoute(val routeId: String, val revision: Long) : NavigationUiAction
    data class ActiveCommand(val command: ActiveNavigationCommand) : NavigationUiAction
}

sealed interface NavigationEffect {
    data class ShowRouteInChart(val routeId: String) : NavigationEffect
}

sealed interface NavigationDestination {
    data class Section(val section: NavigationSection) : NavigationDestination
    data class Route(val id: String) : NavigationDestination
}

object NavigationDestinations {
    val Overview = LaunchToken("navigation.overview")
    val Waypoints = LaunchToken("navigation.waypoints")
    val Routes = LaunchToken("navigation.routes")
    val Gpx = LaunchToken("navigation.gpx")
    val Active = LaunchToken("navigation.active")

    fun route(id: String): LaunchToken {
        val bytes = id.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= MAX_ID_BYTES)
        return LaunchToken(ROUTE_PREFIX + bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) })
    }

    fun parse(token: LaunchToken): NavigationDestination? = when (token) {
        Overview -> NavigationDestination.Section(NavigationSection.OVERVIEW)
        Waypoints -> NavigationDestination.Section(NavigationSection.WAYPOINTS)
        Routes -> NavigationDestination.Section(NavigationSection.ROUTES)
        Gpx -> NavigationDestination.Section(NavigationSection.GPX)
        Active -> NavigationDestination.Section(NavigationSection.ACTIVE)
        else -> token.value.removePrefix(ROUTE_PREFIX).takeIf { token.value.startsWith(ROUTE_PREFIX) }
            ?.let(::decodeHex)?.let(NavigationDestination::Route)
    }

    fun accepts(token: LaunchToken) = parse(token) != null

    private fun decodeHex(hex: String): String? {
        if (hex.isEmpty() || hex.length % 2 != 0 || hex.length > MAX_ID_BYTES * 2 || !hex.matches(Regex("[0-9a-f]+"))) return null
        return runCatching {
            ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
                .let { bytes -> bytes.toString(Charsets.UTF_8).takeIf { it.isNotBlank() && it.toByteArray().contentEquals(bytes) } }
        }.getOrNull()
    }

    private const val ROUTE_PREFIX = "navigation.route."
    private const val MAX_ID_BYTES = 512
}

fun WaypointDraftUi.positionOrNull(): NavigationPosition? {
    val latitude = latitude.trim().toDoubleOrNull() ?: return null
    val longitude = longitude.trim().toDoubleOrNull() ?: return null
    return runCatching { NavigationPosition(latitude, longitude) }.getOrNull()
}
