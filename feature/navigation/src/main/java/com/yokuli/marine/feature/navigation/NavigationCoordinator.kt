package com.yokuli.marine.feature.navigation

import com.yokuli.marine.navigation.domain.ActiveNavigationCommand
import com.yokuli.marine.navigation.domain.ActiveNavigationCommandResult
import com.yokuli.marine.navigation.domain.ActiveNavigationRuntimePort
import com.yokuli.marine.navigation.domain.NavigationChangeResult
import com.yokuli.marine.navigation.domain.NavigationLibrary
import com.yokuli.marine.navigation.domain.NavigationLibraryChange
import com.yokuli.marine.navigation.domain.NavigationLibraryCommitResult
import com.yokuli.marine.navigation.domain.NavigationLibraryEditor
import com.yokuli.marine.navigation.domain.NavigationLibraryLoadResult
import com.yokuli.marine.navigation.domain.NavigationLibraryPort
import com.yokuli.marine.navigation.domain.RoutePlan
import com.yokuli.marine.navigation.domain.RoutePoint
import com.yokuli.marine.navigation.domain.Waypoint
import com.yokuli.marine.navigation.domain.WaypointRevisionReference
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import com.yokuli.shell.contract.LaunchToken

class NavigationCoordinator(
    private val libraryPort: NavigationLibraryPort,
    private val activeRuntime: ActiveNavigationRuntimePort,
    private val nowMillis: () -> Long,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    scope: CoroutineScope,
) {
    private sealed interface Request {
        data object Refresh : Request
        data class Commit(
            val change: NavigationLibraryChange,
            val successPage: NavigationPage,
            val startAfterSave: Boolean = false,
        ) : Request
        data class Active(val command: ActiveNavigationCommand) : Request
    }

    private val lock = Any()
    private val requests = Channel<Request>(32)
    private val effectChannel = Channel<NavigationEffect>(8)
    private var library = NavigationLibrary()
    private var active = activeRuntime.state.value
    private var section = NavigationSection.OVERVIEW
    private var page: NavigationPage = NavigationPage.Root
    private var loading = true
    private var notice: NavigationNotice? = null
    private val mutableState = MutableStateFlow(projectLocked())
    val state: StateFlow<NavigationUiState> = mutableState.asStateFlow()
    val effects: Flow<NavigationEffect> = effectChannel.receiveAsFlow()

    init {
        scope.launch { activeRuntime.state.collect { synchronized(lock) { active = it; publishLocked() } } }
        scope.launch { for (request in requests) execute(request) }
        offer(Request.Refresh)
    }

    fun dispatch(action: NavigationUiAction) {
        val request = synchronized(lock) {
            val result = reduceLocked(action)
            publishLocked()
            result
        }
        request?.let(::offer)
    }

    fun open(token: LaunchToken): NavigationDestination? = synchronized(lock) {
        val destination = NavigationDestinations.parse(token) ?: return null
        when (destination) {
            is NavigationDestination.Section -> {
                section = destination.section
                page = NavigationPage.Root
            }
            is NavigationDestination.Route -> {
                section = NavigationSection.ROUTES
                page = NavigationPage.RouteDetail(destination.id)
            }
        }
        publishLocked()
        destination
    }

    fun handleBack(): Boolean = synchronized(lock) {
        if (page != NavigationPage.Root) {
            navigateUpLocked()
            publishLocked()
            true
        } else if (section != NavigationSection.OVERVIEW) {
            section = NavigationSection.OVERVIEW
            publishLocked()
            true
        } else false
    }

    private fun reduceLocked(action: NavigationUiAction): Request? {
        return when (action) {
        is NavigationUiAction.Navigate -> { section = action.section; page = NavigationPage.Root; null }
        NavigationUiAction.NavigateUp -> { navigateUpLocked(); null }
        NavigationUiAction.Refresh -> Request.Refresh
        NavigationUiAction.DismissNotice -> { notice = null; null }
        NavigationUiAction.CreateWaypoint -> {
            page = NavigationPage.WaypointEditor(WaypointDraftUi(newId(), null, "", "", "", "")); null
        }
        is NavigationUiAction.EditWaypoint -> {
            library.waypoints.firstOrNull { it.id == action.waypointId }?.let { point ->
                page = NavigationPage.WaypointEditor(
                    WaypointDraftUi(point.id, point.revision, point.name, point.position.latitude.toString(), point.position.longitude.toString(), point.notes),
                )
            }; null
        }
        is NavigationUiAction.UpdateWaypointDraft -> {
            val current = (page as? NavigationPage.WaypointEditor)?.draft ?: return invalidLocked()
            page = NavigationPage.WaypointEditor(
                current.copy(
                    name = action.name ?: current.name,
                    latitude = action.latitude ?: current.latitude,
                    longitude = action.longitude ?: current.longitude,
                    notes = action.notes ?: current.notes,
                ),
            ); null
        }
        NavigationUiAction.SaveWaypoint -> saveWaypointLocked()
        is NavigationUiAction.DeleteWaypoint -> Request.Commit(
            NavigationLibraryChange.RemoveWaypoint(action.waypointId, action.revision), NavigationPage.Root,
        )
        NavigationUiAction.CreateRoute -> {
            page = NavigationPage.RouteEditor(
                RouteDraftUi(newId(), null, nextRouteNameLocked(), "", "", emptyList()),
            ); null
        }
        is NavigationUiAction.OpenRoute -> { page = NavigationPage.RouteDetail(action.routeId); null }
        is NavigationUiAction.EditRoute -> {
            library.routePlans.firstOrNull { it.id == action.routeId }?.let { route ->
                page = NavigationPage.RouteEditor(
                    RouteDraftUi(route.id, route.revision, route.name, route.plannedSpeedKnots?.toString().orEmpty(), route.notes, route.points),
                )
            }; null
        }
        is NavigationUiAction.UpdateRouteDraft -> {
            val current = editableRouteDraftLocked() ?: return invalidLocked()
            page = NavigationPage.RouteEditor(
                current.copy(
                    name = action.name ?: current.name,
                    plannedSpeedKnots = action.plannedSpeedKnots ?: current.plannedSpeedKnots,
                    notes = action.notes ?: current.notes,
                ),
            ); null
        }
        is NavigationUiAction.ReplaceRouteGeometry -> {
            val current = editableRouteDraftLocked() ?: return invalidLocked()
            if (action.points.size > MAX_ROUTE_POINTS ||
                action.points.map(RoutePoint::id).distinct().size != action.points.size
            ) return invalidLocked()
            page = NavigationPage.RouteEditor(current.copy(points = action.points)); null
        }
        is NavigationUiAction.AddWaypointToRoute -> {
            val current = editableRouteDraftLocked() ?: return invalidLocked()
            val waypoint = library.waypoints.firstOrNull { it.id == action.waypointId } ?: return invalidLocked()
            page = NavigationPage.RouteEditor(
                current.copy(
                    points = current.points + RoutePoint(
                        id = newId(), position = waypoint.position,
                        sourceWaypoint = WaypointRevisionReference(waypoint.id, waypoint.revision),
                    ),
                ),
            ); null
        }
        is NavigationUiAction.MoveRoutePoint -> {
            val current = editableRouteDraftLocked() ?: return invalidLocked()
            val index = current.points.indexOfFirst { it.id == action.pointId }
            val target = index + action.delta
            if (index < 0 || target !in current.points.indices) return invalidLocked()
            val changed = current.points.toMutableList().apply { add(target, removeAt(index)) }
            page = NavigationPage.RouteEditor(current.copy(points = changed)); null
        }
        is NavigationUiAction.RemoveRoutePoint -> {
            val current = editableRouteDraftLocked() ?: return invalidLocked()
            page = NavigationPage.RouteEditor(current.copy(points = current.points.filterNot { it.id == action.pointId })); null
        }
        NavigationUiAction.SaveRoute -> saveRouteLocked()
        NavigationUiAction.SaveAndStartRoute -> saveRouteLocked(startAfterSave = true)
        NavigationUiAction.RequestCloseRouteDraft -> {
            val current = editableRouteDraftLocked() ?: return invalidLocked()
            if (current.points.isEmpty()) page = NavigationPage.Root
            else page = NavigationPage.RouteCloseConfirmation(current)
            null
        }
        NavigationUiAction.DiscardRouteDraft -> {
            if (editableRouteDraftLocked() == null) return invalidLocked()
            page = NavigationPage.Root
            section = NavigationSection.ROUTES
            null
        }
        NavigationUiAction.CancelRouteClose -> {
            val current = (page as? NavigationPage.RouteCloseConfirmation)?.draft ?: return invalidLocked()
            page = NavigationPage.RouteEditor(current)
            null
        }
        is NavigationUiAction.DeleteRoute -> {
            if (active.session?.routeId == action.routeId) { notice = NavigationNotice.ACTIVE_ROUTE_LOCKED; null }
            else Request.Commit(NavigationLibraryChange.RemoveRoutePlan(action.routeId, action.revision), NavigationPage.Root)
        }
        is NavigationUiAction.ShowRouteInChart -> {
            if (effectChannel.trySend(NavigationEffect.ShowRouteInChart(action.routeId)).isFailure) notice = NavigationNotice.ACTION_QUEUE_FULL
            null
        }
        is NavigationUiAction.StartRoute -> Request.Active(ActiveNavigationCommand.Start(action.routeId, action.revision))
        is NavigationUiAction.ActiveCommand -> Request.Active(action.command)
        }
    }

    private fun saveWaypointLocked(): Request? {
        val draft = (page as? NavigationPage.WaypointEditor)?.draft ?: return invalidLocked()
        val position = draft.positionOrNull() ?: return invalidLocked()
        val name = draft.name.trim().takeIf(String::isNotEmpty) ?: return invalidLocked()
        val existing = draft.editingRevision?.let { revision -> library.waypoints.firstOrNull { it.id == draft.id && it.revision == revision } }
        if (draft.editingRevision != null && existing == null) return conflictLocked()
        val waypoint = Waypoint(
            id = draft.id,
            revision = (draft.editingRevision ?: 0) + 1,
            name = name,
            position = position,
            notes = draft.notes.trim(),
            category = existing?.category ?: com.yokuli.marine.navigation.domain.WaypointCategory.PERSONAL_MARKER,
            tags = existing?.tags.orEmpty(),
            createdAtMillis = existing?.createdAtMillis ?: nowMillis(),
            updatedAtMillis = nowMillis(),
        )
        return Request.Commit(NavigationLibraryChange.PutWaypoint(waypoint), NavigationPage.Root)
    }

    private fun saveRouteLocked(startAfterSave: Boolean = false): Request? {
        val draft = editableRouteDraftLocked() ?: return invalidLocked()
        val name = draft.name.trim().ifEmpty { nextRouteNameLocked() }
        if (draft.points.size < 2) return invalidLocked()
        val speed = draft.plannedSpeedKnots.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull()
        if (draft.plannedSpeedKnots.isNotBlank() && (speed == null || !speed.isFinite() || speed <= 0.0)) return invalidLocked()
        val existing = draft.editingRevision?.let { revision -> library.routePlans.firstOrNull { it.id == draft.id && it.revision == revision } }
        if (draft.editingRevision != null && existing == null) return conflictLocked()
        val route = RoutePlan(
            id = draft.id,
            revision = (draft.editingRevision ?: 0) + 1,
            name = name,
            points = draft.points,
            plannedSpeedKnots = speed,
            notes = draft.notes.trim(),
            sourceDraftId = existing?.sourceDraftId,
            sourceDraftRevision = existing?.sourceDraftRevision,
        )
        return Request.Commit(
            NavigationLibraryChange.PutRoutePlan(route),
            NavigationPage.RouteDetail(route.id),
            startAfterSave = startAfterSave,
        )
    }

    private suspend fun execute(request: Request) = try {
        when (request) {
            Request.Refresh -> refresh()
            is Request.Commit -> commit(request)
            is Request.Active -> {
                val result = activeRuntime.execute(request.command)
                synchronized(lock) {
                    notice = when (result) {
                        is ActiveNavigationCommandResult.Accepted -> NavigationNotice.SAVED
                        is ActiveNavigationCommandResult.ReplacementRequired -> NavigationNotice.REPLACEMENT_REQUIRED
                        is ActiveNavigationCommandResult.Rejected -> NavigationNotice.NAVIGATION_REJECTED
                    }
                    if (result is ActiveNavigationCommandResult.Accepted && request.command is ActiveNavigationCommand.Start) {
                        section = NavigationSection.ACTIVE
                        page = NavigationPage.Root
                    }
                    publishLocked()
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        synchronized(lock) { loading = false; notice = NavigationNotice.STORAGE_FAILED; publishLocked() }
    }

    private suspend fun commit(request: Request.Commit) {
        val expected = synchronized(lock) { library.revision }
        when (val result = libraryPort.commitNavigationChange(expected, request.change)) {
            is NavigationLibraryCommitResult.Committed -> {
                val projected = synchronized(lock) { NavigationLibraryEditor.apply(library, request.change) }
                var routeToStart: RoutePlan? = null
                synchronized(lock) {
                    if (projected is NavigationChangeResult.Applied && projected.library.revision == result.revision) {
                        library = projected.library
                        page = request.successPage
                        notice = if (request.change is NavigationLibraryChange.RemoveWaypoint || request.change is NavigationLibraryChange.RemoveRoutePlan) {
                            NavigationNotice.DELETED
                        } else NavigationNotice.SAVED
                        if (request.startAfterSave && request.change is NavigationLibraryChange.PutRoutePlan) {
                            routeToStart = request.change.route
                        }
                    } else notice = NavigationNotice.REVISION_CONFLICT
                    publishLocked()
                }
                routeToStart?.let { route ->
                    val start = activeRuntime.execute(ActiveNavigationCommand.Start(route.id, route.revision))
                    synchronized(lock) {
                        if (start is ActiveNavigationCommandResult.Accepted) {
                            section = NavigationSection.ACTIVE
                            page = NavigationPage.Root
                            notice = NavigationNotice.SAVED
                        } else if (start is ActiveNavigationCommandResult.ReplacementRequired) {
                            section = NavigationSection.ACTIVE
                            page = NavigationPage.Root
                            notice = NavigationNotice.REPLACEMENT_REQUIRED
                        } else {
                            notice = NavigationNotice.NAVIGATION_REJECTED
                        }
                        publishLocked()
                    }
                }
            }
            is NavigationLibraryCommitResult.Conflict,
            is NavigationLibraryCommitResult.Rejected -> synchronized(lock) { notice = NavigationNotice.REVISION_CONFLICT; publishLocked() }
            is NavigationLibraryCommitResult.Failed -> synchronized(lock) { notice = NavigationNotice.STORAGE_FAILED; publishLocked() }
        }
    }

    private suspend fun refresh() {
        when (val result = libraryPort.loadNavigationLibrary()) {
            is NavigationLibraryLoadResult.Ready -> synchronized(lock) {
                library = result.library; loading = false
                val detail = page as? NavigationPage.RouteDetail
                if (detail != null && library.routePlans.none { it.id == detail.routeId }) page = NavigationPage.Root
                publishLocked()
            }
            is NavigationLibraryLoadResult.Failed -> synchronized(lock) {
                loading = false; notice = NavigationNotice.STORAGE_FAILED; publishLocked()
            }
        }
    }

    private fun invalidLocked(): Request? { notice = NavigationNotice.INVALID_INPUT; return null }
    private fun conflictLocked(): Request? { notice = NavigationNotice.REVISION_CONFLICT; return null }
    private fun offer(request: Request) {
        if (requests.trySend(request).isFailure) synchronized(lock) { notice = NavigationNotice.ACTION_QUEUE_FULL; publishLocked() }
    }
    private fun projectLocked() = NavigationUiState(section, page, library, active, loading, notice)
    private fun publishLocked() { mutableState.value = projectLocked() }

    private fun navigateUpLocked() {
        page = when (val current = page) {
            is NavigationPage.RouteEditor -> if (current.draft.points.isEmpty()) {
                NavigationPage.Root
            } else {
                NavigationPage.RouteCloseConfirmation(current.draft)
            }
            is NavigationPage.RouteCloseConfirmation -> NavigationPage.RouteEditor(current.draft)
            NavigationPage.Root -> NavigationPage.Root
            else -> NavigationPage.Root
        }
    }

    private fun editableRouteDraftLocked(): RouteDraftUi? = when (val current = page) {
        is NavigationPage.RouteEditor -> current.draft
        is NavigationPage.RouteCloseConfirmation -> current.draft
        else -> null
    }

    private fun nextRouteNameLocked(): String {
        val ordinal = library.routePlans.asSequence().mapNotNull { route ->
            Regex("^Route\\s+(\\d{1,6})$", RegexOption.IGNORE_CASE).matchEntire(route.name.trim())
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
        }.maxOrNull()?.plus(1) ?: 1
        return "Route %03d".format(ordinal)
    }

    private companion object {
        const val MAX_ROUTE_POINTS = 2_000
    }
}
