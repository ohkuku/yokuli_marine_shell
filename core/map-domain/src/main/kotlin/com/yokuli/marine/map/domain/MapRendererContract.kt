package com.yokuli.marine.map.domain

/** Monotonically increasing identity for one native renderer instance. */
@JvmInline
value class MapRendererGeneration(val value: Long) {
    init {
        require(value > 0L) { "Renderer generation must be positive" }
    }
}

@JvmInline
value class MapCameraCommandId(val value: Long) {
    init {
        require(value > 0L) { "Camera command ID must be positive" }
    }
}

data class MapViewportInsets(
    val leftPx: Int = 0,
    val topPx: Int = 0,
    val rightPx: Int = 0,
    val bottomPx: Int = 0,
) {
    init {
        require(leftPx >= 0 && topPx >= 0 && rightPx >= 0 && bottomPx >= 0)
    }
}

data class MapScreenPoint(val xPx: Double, val yPx: Double) {
    init {
        require(xPx.isFinite() && yPx.isFinite())
    }
}

sealed interface MapCameraTarget {
    data class Exact(val camera: MapCamera) : MapCameraTarget
    data class Bounds(val bounds: GeoBounds, val fallbackZoom: Double? = null) : MapCameraTarget {
        init {
            require(fallbackZoom == null || fallbackZoom.isFinite() && fallbackZoom in 0.0..24.0)
        }
    }
}

enum class MapCameraIntent { RESTORE, NORTH_RESET, VIEW_PLACE, VIEW_ROUTE, VIEW_PACKAGE }

data class MapCameraCommand(
    val id: MapCameraCommandId,
    val target: MapCameraTarget,
    val intent: MapCameraIntent,
    val viewportInsets: MapViewportInsets = MapViewportInsets(),
)

enum class MapRendererReadiness { DETACHED, HOST_READY, RENDERER_READY, ERROR }

/**
 * PACKAGE_ATTACHED means the local source was accepted by the style. It deliberately does not
 * claim that every tile exists or has rendered.
 */
enum class MapTileCoverageStatus { NO_PACKAGE, CHECKING, PACKAGE_ATTACHED, PACKAGE_MISSING, DEGRADED, ERROR }

enum class MapRendererFailure { INITIALIZATION, STYLE, PACKAGE_MISSING, PROJECTION, UNKNOWN }

data class MapRendererState(
    val generation: MapRendererGeneration? = null,
    val readiness: MapRendererReadiness = MapRendererReadiness.DETACHED,
    val tileCoverage: MapTileCoverageStatus = MapTileCoverageStatus.NO_PACKAGE,
    val pendingCameraCommand: MapCameraCommand? = null,
    val lastAcknowledgedCameraCommandId: MapCameraCommandId? = null,
    val cameraInputEnabled: Boolean = false,
    val nextCameraCommandId: Long = 1L,
    val failure: MapRendererFailure? = null,
) {
    init {
        require(nextCameraCommandId > 0L)
    }
}

/** Stable IDs form the only renderer-facing overlay namespace. */
enum class MapOverlayId(val wireValue: String) {
    SAVED_PLACES("saved-places"),
    SELECTION("map-selection"),
    MEASUREMENT("measurement-draft"),
    MEASUREMENT_POINTS("measurement-points"),
    MANUAL_ROUTE("manual-route-draft"),
    MANUAL_ROUTE_POINTS("manual-route-points"),
    POSITION_OBSERVATION("position-observation"),
}

data class MapHitResult(val overlayId: MapOverlayId, val objectId: String)

/** Narrow, SDK-free projection/hit-test boundary implemented by the native adapter. */
interface MapRendererQueryPort {
    fun project(point: GeoPoint): MapScreenPoint?
    fun unproject(point: MapScreenPoint): GeoPoint?
    fun query(point: MapScreenPoint, overlayIds: Set<MapOverlayId>): List<MapHitResult>
}
