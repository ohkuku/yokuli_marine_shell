package com.yokuli.marine.navigation.domain

enum class NavigationLibraryFailure { IO, CORRUPT, FUTURE_SCHEMA, UNKNOWN }

sealed interface NavigationLibraryLoadResult {
    data class Ready(val library: NavigationLibrary, val quarantinedRecordCount: Int = 0) : NavigationLibraryLoadResult
    data class Failed(val failure: NavigationLibraryFailure) : NavigationLibraryLoadResult
}

sealed interface NavigationLibraryChange {
    data class PutWaypoint(val waypoint: Waypoint) : NavigationLibraryChange
    data class RemoveWaypoint(val id: String, val expectedRevision: Long) : NavigationLibraryChange
    data class PutRouteDraft(val draft: RouteDraft) : NavigationLibraryChange
    data class RemoveRouteDraft(val id: String, val expectedRevision: Long) : NavigationLibraryChange
    data class PutRoutePlan(val route: RoutePlan) : NavigationLibraryChange
    data class RemoveRoutePlan(val id: String, val expectedRevision: Long) : NavigationLibraryChange
    data class PutTrack(val track: NavigationTrack) : NavigationLibraryChange
    data class RemoveTrack(val id: String, val expectedRevision: Long) : NavigationLibraryChange
    data class ImportGpx(
        val waypoints: List<Waypoint>,
        val routePlans: List<RoutePlan>,
        val tracks: List<NavigationTrack>,
        val receipt: GpxImportReceipt,
        val mode: GpxImportMode = GpxImportMode.NEW_IMPORT,
    ) : NavigationLibraryChange
}

enum class GpxImportMode { NEW_IMPORT, IMPORT_AS_COPY }

enum class NavigationChangeRejection { ITEM_CONFLICT, ITEM_NOT_FOUND, DUPLICATE_IMPORT, ID_COLLISION, EMPTY_IMPORT }

sealed interface NavigationChangeResult {
    data class Applied(val library: NavigationLibrary) : NavigationChangeResult
    data class Rejected(val reason: NavigationChangeRejection) : NavigationChangeResult
}

object NavigationLibraryEditor {
    fun apply(library: NavigationLibrary, change: NavigationLibraryChange): NavigationChangeResult {
        val changed = when (change) {
            is NavigationLibraryChange.PutWaypoint -> library.copy(
                waypoints = put(library.waypoints, change.waypoint, Waypoint::id, Waypoint::revision)
                    ?: return rejectedConflict(),
            )
            is NavigationLibraryChange.RemoveWaypoint -> library.copy(
                waypoints = remove(library.waypoints, change.id, change.expectedRevision, Waypoint::id, Waypoint::revision)
                    ?: return rejectedMissingOrConflict(library.waypoints.any { it.id == change.id }),
            )
            is NavigationLibraryChange.PutRouteDraft -> library.copy(
                routeDrafts = put(library.routeDrafts, change.draft, RouteDraft::id, RouteDraft::revision)
                    ?: return rejectedConflict(),
            )
            is NavigationLibraryChange.RemoveRouteDraft -> library.copy(
                routeDrafts = remove(library.routeDrafts, change.id, change.expectedRevision, RouteDraft::id, RouteDraft::revision)
                    ?: return rejectedMissingOrConflict(library.routeDrafts.any { it.id == change.id }),
            )
            is NavigationLibraryChange.PutRoutePlan -> library.copy(
                routePlans = put(library.routePlans, change.route, RoutePlan::id, RoutePlan::revision)
                    ?: return rejectedConflict(),
            )
            is NavigationLibraryChange.RemoveRoutePlan -> library.copy(
                routePlans = remove(library.routePlans, change.id, change.expectedRevision, RoutePlan::id, RoutePlan::revision)
                    ?: return rejectedMissingOrConflict(library.routePlans.any { it.id == change.id }),
            )
            is NavigationLibraryChange.PutTrack -> library.copy(
                importedTracks = put(library.importedTracks, change.track, NavigationTrack::id, NavigationTrack::revision)
                    ?: return rejectedConflict(),
            )
            is NavigationLibraryChange.RemoveTrack -> library.copy(
                importedTracks = remove(
                    library.importedTracks,
                    change.id,
                    change.expectedRevision,
                    NavigationTrack::id,
                    NavigationTrack::revision,
                ) ?: return rejectedMissingOrConflict(library.importedTracks.any { it.id == change.id }),
            )
            is NavigationLibraryChange.ImportGpx -> importGpx(library, change) ?: return NavigationChangeResult.Rejected(
                when {
                    change.waypoints.isEmpty() && change.routePlans.isEmpty() && change.tracks.isEmpty() ->
                        NavigationChangeRejection.EMPTY_IMPORT
                    library.gpxImports.any { it.sha256 == change.receipt.sha256 } &&
                        change.mode == GpxImportMode.NEW_IMPORT -> NavigationChangeRejection.DUPLICATE_IMPORT
                    else -> NavigationChangeRejection.ID_COLLISION
                },
            )
        }
        return NavigationChangeResult.Applied(changed.copy(revision = library.revision + 1L))
    }

    private fun importGpx(library: NavigationLibrary, change: NavigationLibraryChange.ImportGpx): NavigationLibrary? {
        if (change.waypoints.isEmpty() && change.routePlans.isEmpty() && change.tracks.isEmpty()) return null
        if (
            library.gpxImports.any { it.sha256 == change.receipt.sha256 } &&
            change.mode == GpxImportMode.NEW_IMPORT
        ) return null
        if (change.receipt.id in library.gpxImports.map { it.id }) return null
        if (change.waypoints.map(Waypoint::id).distinct().size != change.waypoints.size) return null
        if (change.routePlans.map(RoutePlan::id).distinct().size != change.routePlans.size) return null
        if (change.tracks.map(NavigationTrack::id).distinct().size != change.tracks.size) return null
        if (change.waypoints.any { incoming -> library.waypoints.any { it.id == incoming.id } }) return null
        if (change.routePlans.any { incoming -> library.routePlans.any { it.id == incoming.id } }) return null
        if (change.tracks.any { incoming -> library.importedTracks.any { it.id == incoming.id } }) return null
        return library.copy(
            waypoints = library.waypoints + change.waypoints,
            routePlans = library.routePlans + change.routePlans,
            importedTracks = library.importedTracks + change.tracks,
            gpxImports = library.gpxImports + change.receipt,
        )
    }

    private fun <T> put(items: List<T>, incoming: T, id: (T) -> String, revision: (T) -> Long): List<T>? {
        val current = items.firstOrNull { id(it) == id(incoming) }
        if (current == null) return if (revision(incoming) == 1L) items + incoming else null
        if (revision(incoming) != revision(current) + 1L) return null
        return items.map { if (id(it) == id(incoming)) incoming else it }
    }

    private fun <T> remove(
        items: List<T>,
        itemId: String,
        expectedRevision: Long,
        id: (T) -> String,
        revision: (T) -> Long,
    ): List<T>? {
        val current = items.firstOrNull { id(it) == itemId } ?: return null
        if (revision(current) != expectedRevision) return null
        return items.filterNot { id(it) == itemId }
    }

    private fun rejectedConflict() = NavigationChangeResult.Rejected(NavigationChangeRejection.ITEM_CONFLICT)

    private fun rejectedMissingOrConflict(found: Boolean) = NavigationChangeResult.Rejected(
        if (found) NavigationChangeRejection.ITEM_CONFLICT else NavigationChangeRejection.ITEM_NOT_FOUND,
    )
}

sealed interface NavigationLibraryCommitResult {
    data class Committed(val revision: Long) : NavigationLibraryCommitResult
    data class Conflict(val currentRevision: Long) : NavigationLibraryCommitResult
    data class Rejected(val reason: NavigationChangeRejection) : NavigationLibraryCommitResult
    data class Failed(val failure: NavigationLibraryFailure) : NavigationLibraryCommitResult
}

interface NavigationLibraryPort {
    suspend fun loadNavigationLibrary(): NavigationLibraryLoadResult
    suspend fun commitNavigationChange(
        expectedLibraryRevision: Long,
        change: NavigationLibraryChange,
    ): NavigationLibraryCommitResult
}
