package com.yokuli.marine.map.domain

sealed interface MapSurface {
    data object Root : MapSurface
    data object Places : MapSurface
    data object Routes : MapSurface
    data object ChartPackages : MapSurface
    data object Measurement : MapSurface
    data object CoordinateInput : MapSurface
    data class PlaceDetail(val placeId: String) : MapSurface
    data class RouteDetail(val routeId: String) : MapSurface
    data class ChartPackageDetail(val packageId: ChartPackageId) : MapSurface
}

enum class MapTool { BROWSE, MEASURE, MANUAL_ROUTE }

enum class PointCandidateOrigin { MAP_TAP, MAP_LONG_PRESS, CROSSHAIR, COORDINATE_INPUT }

sealed interface MapTransient {
    data class PointCandidate(
        val point: GeoPoint,
        val origin: PointCandidateOrigin,
    ) : MapTransient

    data class SelectedObject(val hit: MapHitResult) : MapTransient
    data class ObjectCandidates(val hits: List<MapHitResult>) : MapTransient {
        init {
            require(hits.size >= 2) { "Overlapping-object selection requires at least two candidates" }
        }
    }

    data class UnavailableObject(val objectId: String) : MapTransient
}

@JvmInline
value class MapGestureId(val value: String) {
    init {
        require(value.isNotBlank()) { "Gesture ID is required" }
    }
}

sealed interface MapEditTarget {
    data class MeasurementPoint(val index: Int) : MapEditTarget {
        init {
            require(index >= 0)
        }
    }

    data class RoutePoint(val draftId: String, val index: Int) : MapEditTarget {
        init {
            require(draftId.isNotBlank())
            require(index >= 0)
        }
    }
}

sealed interface MapPrecisePointEdit {
    data class Move(val target: MapEditTarget) : MapPrecisePointEdit
    data class InsertMeasurement(val index: Int) : MapPrecisePointEdit {
        init {
            require(index >= 0)
        }
    }
}

data class MapEditGesture(
    val id: MapGestureId,
    val target: MapEditTarget,
    val originalPoint: GeoPoint,
    val previewPoint: GeoPoint = originalPoint,
)

data class MapViewport(
    val widthPx: Int,
    val heightPx: Int,
    val obscuredInsets: MapViewportInsets = MapViewportInsets(),
    val revision: Long,
) {
    init {
        require(widthPx > 0 && heightPx > 0) { "Map viewport must be non-empty" }
        require(revision > 0L) { "Map viewport revision must be positive" }
        require(obscuredInsets.leftPx + obscuredInsets.rightPx < widthPx)
        require(obscuredInsets.topPx + obscuredInsets.bottomPx < heightPx)
    }
}

/**
 * Returns only the action for the uppermost feature plane. A null result means the Shell owns Back.
 * IME dismissal is handled one level above this pure policy because keyboard visibility is a host fact.
 */
object MapFeatureBackPolicy {
    fun actionFor(state: MapState): MapAction? = when {
        state.transient != null -> MapAction.DismissTransient
        state.editGesture != null -> MapAction.CancelPointDrag(state.editGesture.id)
        state.surface == MapSurface.CoordinateInput -> MapAction.CloseSurface
        state.precisePointEdit != null -> MapAction.CancelPrecisePointEdit
        state.tool != MapTool.BROWSE -> MapAction.SelectTool(MapTool.BROWSE)
        state.surface != MapSurface.Root -> MapAction.CloseSurface
        state.selection != null -> MapAction.ClearSelection
        else -> null
    }
}
