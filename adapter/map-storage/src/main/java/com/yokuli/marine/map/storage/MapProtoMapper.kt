package com.yokuli.marine.map.storage

import androidx.datastore.core.CorruptionException
import com.yokuli.marine.map.domain.ChartPackage
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.ManualRouteDraft
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapPersistedState
import com.yokuli.marine.map.domain.MapSessionSnapshot
import com.yokuli.marine.map.domain.MeasurementDraft
import com.yokuli.marine.map.domain.SavedPlace
import com.yokuli.marine.map.domain.SavedRoute
import com.yokuli.marine.map.storage.proto.ChartPackageProto
import com.yokuli.marine.map.storage.proto.GeoBoundsProto
import com.yokuli.marine.map.storage.proto.GeoPointProto
import com.yokuli.marine.map.storage.proto.ManualRouteDraftProto
import com.yokuli.marine.map.storage.proto.MapCameraProto
import com.yokuli.marine.map.storage.proto.MapStateProto
import com.yokuli.marine.map.storage.proto.MeasurementDraftProto
import com.yokuli.marine.map.storage.proto.PointListProto
import com.yokuli.marine.map.storage.proto.SavedPlaceProto
import com.yokuli.marine.map.storage.proto.SavedRouteProto

internal object MapProtoMapper {
    const val SCHEMA_VERSION = 2

    fun encodeSession(state: MapSessionSnapshot): MapStateProto = MapStateProto.newBuilder()
        .setSchemaVersion(SCHEMA_VERSION)
        .setCamera(state.camera.toProto())
        .also { builder ->
            state.measurementDraft?.let { builder.measurementDraft = it.toProto() }
            state.activeRouteDraftId?.let { builder.activeRouteDraftId = it }
            state.activeChartPackageId?.let { builder.activeChartPackageId = it.value }
        }
        .build()

    fun decodeSession(proto: MapStateProto): MapSessionSnapshot = try {
        require(proto.schemaVersion in 0..SCHEMA_VERSION) { "Unsupported map schema ${proto.schemaVersion}" }
        MapSessionSnapshot(
            camera = if (proto.hasCamera()) proto.camera.toDomain() else MapCamera(),
            measurementDraft = if (proto.hasMeasurementDraft()) proto.measurementDraft.toDomain() else null,
            activeRouteDraftId = proto.activeRouteDraftId.takeIf { it.isNotBlank() },
            activeChartPackageId = proto.activeChartPackageId.takeIf { it.isNotBlank() }?.let(::ChartPackageId),
        )
    } catch (error: IllegalArgumentException) {
        throw CorruptionException("Invalid map session", error)
    }

    fun encode(state: MapPersistedState): MapStateProto = MapStateProto.newBuilder()
        .setSchemaVersion(SCHEMA_VERSION)
        .setCamera(state.camera.toProto())
        .addAllPlaces(state.places.map { it.toProto() })
        .addAllSavedRoutes(state.savedRoutes.map { it.toProto() })
        .addAllChartPackages(state.chartPackages.map { it.toProto() })
        .also { builder ->
            state.measurementDraft?.let { builder.measurementDraft = it.toProto() }
            state.routeDraft?.let { builder.routeDraft = it.toProto() }
            state.activeChartPackageId?.let { builder.activeChartPackageId = it.value }
        }
        .build()

    fun decode(proto: MapStateProto): MapPersistedState = try {
        require(proto.schemaVersion in 0..SCHEMA_VERSION) { "Unsupported map schema ${proto.schemaVersion}" }
        MapPersistedState(
            camera = if (proto.hasCamera()) proto.camera.toDomain() else MapCamera(),
            places = proto.placesList.map { it.toDomain() },
            measurementDraft = if (proto.hasMeasurementDraft()) proto.measurementDraft.toDomain() else null,
            routeDraft = if (proto.hasRouteDraft()) proto.routeDraft.toDomain() else null,
            savedRoutes = proto.savedRoutesList.map { it.toDomain() },
            chartPackages = proto.chartPackagesList.map { it.toDomain() },
            activeChartPackageId = proto.activeChartPackageId.takeIf { it.isNotBlank() }?.let(::ChartPackageId),
            navigationActive = false,
            positionObservation = null,
        )
    } catch (error: IllegalArgumentException) {
        throw CorruptionException("Invalid map state", error)
    }

    private fun GeoPoint.toProto() = GeoPointProto.newBuilder().setLatitude(latitude).setLongitude(longitude).build()
    private fun GeoPointProto.toDomain() = GeoPoint(latitude, longitude)
    private fun GeoBounds.toProto() = GeoBoundsProto.newBuilder().setSouth(south).setWest(west).setNorth(north).setEast(east).build()
    private fun GeoBoundsProto.toDomain() = GeoBounds(south, west, north, east)
    private fun MapCamera.toProto() = MapCameraProto.newBuilder().setCenter(center.toProto()).setZoom(zoom).setBearing(bearing).build()
    private fun MapCameraProto.toDomain() = MapCamera(center.toDomain(), zoom, bearing)
    private fun SavedPlace.toProto() = SavedPlaceProto.newBuilder().setId(id).setName(name).setPoint(point.toProto()).build()
    private fun SavedPlaceProto.toDomain() = SavedPlace(id, name, point.toDomain())
    private fun MeasurementDraft.toProto() = MeasurementDraftProto.newBuilder().addAllPoints(points.map { it.toProto() }).build()
    private fun MeasurementDraftProto.toDomain() = MeasurementDraft(pointsList.map { it.toDomain() })
    private fun ManualRouteDraft.toProto() = ManualRouteDraftProto.newBuilder()
        .setName(name).addAllWaypoints(waypoints.map { it.toProto() }).setPlannedSpeedKnots(plannedSpeedKnots)
        .addAllUndo(undo.map { points -> PointListProto.newBuilder().addAllPoints(points.map { it.toProto() }).build() })
        .addAllRedo(redo.map { points -> PointListProto.newBuilder().addAllPoints(points.map { it.toProto() }).build() })
        .build()
    private fun ManualRouteDraftProto.toDomain() = ManualRouteDraft(
        id = "legacy-draft",
        revision = 1L,
        name = name,
        waypoints = waypointsList.map { it.toDomain() },
        plannedSpeedKnots = plannedSpeedKnots.takeIf { it > 0.0 } ?: 5.0,
        undo = undoList.map { list -> list.pointsList.map { it.toDomain() } },
        redo = redoList.map { list -> list.pointsList.map { it.toDomain() } },
    )
    private fun SavedRoute.toProto() = SavedRouteProto.newBuilder().setId(id).setName(name)
        .addAllWaypoints(waypoints.map { it.toProto() }).setPlannedSpeedKnots(plannedSpeedKnots).build()
    private fun SavedRouteProto.toDomain() = SavedRoute(id, name, waypointsList.map { it.toDomain() }, plannedSpeedKnots)
    private fun ChartPackage.toProto() = ChartPackageProto.newBuilder().setId(id.value).setDisplayName(displayName)
        .setSource(source).setLicense(license).setAttribution(attribution).setSha256(sha256).setLocalUri(localUri)
        .setCoverage(coverage.toProto()).setMinZoom(minZoom).setMaxZoom(maxZoom).setVersion(version).build()
    private fun ChartPackageProto.toDomain() = ChartPackage(
        ChartPackageId(id), displayName, source, license, attribution, sha256, localUri,
        coverage.toDomain(), minZoom, maxZoom, version,
    )
}
