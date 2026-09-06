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
import com.yokuli.marine.map.domain.MapViewMode
import com.yokuli.marine.map.domain.MeasurementDraft
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayPreferences
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplaySelection
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceId
import com.yokuli.marine.map.storage.proto.ChartAssetOpacityProto
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
    const val SCHEMA_VERSION = 5

    fun encodeSession(state: MapSessionSnapshot): MapStateProto = MapStateProto.newBuilder()
        .setSchemaVersion(SCHEMA_VERSION)
        .setCamera(state.camera.toProto())
        .also { builder ->
            state.activeRouteDraftId?.let { builder.activeRouteDraftId = it }
            state.activeRoutePlanId?.let { builder.activeRoutePlanId = it }
            state.activeChartPackageId?.let { builder.activeChartPackageId = it.value }
            builder.encodeDisplayPreferences(state.chartDisplayPreferences)
            builder.chartDisplayPreferencesInitialized = state.chartDisplayPreferencesInitialized
            builder.mapViewMode = state.mapViewMode.name
        }
        .build()

    fun decodeSession(proto: MapStateProto): MapSessionSnapshot = try {
        require(proto.schemaVersion in 0..SCHEMA_VERSION) { "Unsupported map schema ${proto.schemaVersion}" }
        MapSessionSnapshot(
            camera = if (proto.hasCamera()) proto.camera.toDomain() else MapCamera(),
            // The A/B ruler is a transient interaction. Legacy payloads are deliberately ignored.
            measurementDraft = null,
            activeRouteDraftId = proto.activeRouteDraftId.takeIf { it.isNotBlank() },
            activeRoutePlanId = proto.activeRoutePlanId.takeIf { it.isNotBlank() },
            activeChartPackageId = proto.activeChartPackageId.takeIf { it.isNotBlank() }?.let(::ChartPackageId),
            chartDisplayPreferences = proto.decodeDisplayPreferences(),
            chartDisplayPreferencesInitialized = proto.schemaVersion >= 4 && proto.chartDisplayPreferencesInitialized,
            mapViewMode = MapViewMode.entries.firstOrNull { it.name == proto.mapViewMode }
                ?: MapViewMode.SATELLITE,
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

    private fun MapStateProto.Builder.encodeDisplayPreferences(value: ChartDisplayPreferences) {
        chartDisplayOverlaysVisible = value.overlaysVisible
        when (val selection = value.selection) {
            ChartDisplaySelection.None -> chartDisplaySelectionKind = "none"
            is ChartDisplaySelection.PinnedAsset -> {
                chartDisplaySelectionKind = "asset"
                addChartDisplaySelectionIds(selection.assetId.value)
            }
            is ChartDisplaySelection.SourceSet -> {
                chartDisplaySelectionKind = "sources"
                addAllChartDisplaySelectionIds(selection.sourceIds.map { it.value }.sorted())
            }
        }
        addAllChartAssetOpacity(
            value.assetOpacity.entries.sortedBy { it.key.value }.map { (assetId, opacity) ->
                ChartAssetOpacityProto.newBuilder().setAssetId(assetId.value).setOpacity(opacity).build()
            },
        )
        addAllChartDisplayHiddenAssetIds(value.hiddenAssetIds.map { it.value }.sorted())
    }

    /** A malformed new preference must not quarantine otherwise valid routes, places or camera state. */
    private fun MapStateProto.decodeDisplayPreferences(): ChartDisplayPreferences = runCatching {
        if (schemaVersion < 4 || chartDisplaySelectionKind.isBlank()) return@runCatching ChartDisplayPreferences()
        val selection = when (chartDisplaySelectionKind) {
            "none" -> ChartDisplaySelection.None
            "asset" -> ChartDisplaySelection.PinnedAsset(ChartAssetId(chartDisplaySelectionIdsList.single()))
            "sources" -> ChartDisplaySelection.SourceSet(chartDisplaySelectionIdsList.mapTo(linkedSetOf(), ::ChartSourceId))
            else -> error("Unknown chart display selection")
        }
        val opacity = chartAssetOpacityList.associate { ChartAssetId(it.assetId) to it.opacity }
        val hidden = if (schemaVersion >= 5) {
            chartDisplayHiddenAssetIdsList.mapTo(linkedSetOf(), ::ChartAssetId)
        } else {
            emptySet()
        }
        ChartDisplayPreferences(selection, chartDisplayOverlaysVisible, opacity, hidden)
    }.getOrDefault(ChartDisplayPreferences())
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
        .setName(name).addAllWaypoints(waypoints.map { it.toProto() })
        .also { builder -> plannedSpeedKnots?.let { builder.plannedSpeedKnots = it } }
        .addAllUndo(undo.map { frame -> PointListProto.newBuilder().addAllPoints(frame.waypoints.map { it.toProto() }).build() })
        .addAllRedo(redo.map { frame -> PointListProto.newBuilder().addAllPoints(frame.waypoints.map { it.toProto() }).build() })
        .build()
    private fun ManualRouteDraftProto.toDomain() = ManualRouteDraft(
        id = "legacy-draft",
        revision = 1L,
        name = name,
        waypoints = waypointsList.map { it.toDomain() },
        plannedSpeedKnots = plannedSpeedKnots.takeIf { it > 0.0 },
        undo = undoList.mapIndexed { historyIndex, list ->
            val points = list.pointsList.map { it.toDomain() }
            com.yokuli.marine.map.domain.RouteGeometrySnapshot(
                points,
                points.indices.map { "legacy-draft-undo-$historyIndex-${it + 1}" },
                emptyMap(),
                points.size + 1,
            )
        },
        redo = redoList.mapIndexed { historyIndex, list ->
            val points = list.pointsList.map { it.toDomain() }
            com.yokuli.marine.map.domain.RouteGeometrySnapshot(
                points,
                points.indices.map { "legacy-draft-redo-$historyIndex-${it + 1}" },
                emptyMap(),
                points.size + 1,
            )
        },
    )
    private fun SavedRoute.toProto() = SavedRouteProto.newBuilder().setId(id).setName(name)
        .addAllWaypoints(waypoints.map { it.toProto() })
        .also { builder -> plannedSpeedKnots?.let { builder.plannedSpeedKnots = it } }
        .build()
    private fun SavedRouteProto.toDomain() = SavedRoute(
        id,
        name,
        waypointsList.map { it.toDomain() },
        plannedSpeedKnots.takeIf { it > 0.0 },
    )
    private fun ChartPackage.toProto() = ChartPackageProto.newBuilder().setId(id.value).setDisplayName(displayName)
        .setSource(source).setLicense(license).setAttribution(attribution).setSha256(sha256).setLocalUri(localUri)
        .setCoverage(coverage.toProto()).setMinZoom(minZoom).setMaxZoom(maxZoom).setVersion(version)
        .setRasterFormat(rasterFormat).setTileSize(tileSize).build()
    private fun ChartPackageProto.toDomain() = ChartPackage(
        ChartPackageId(id), displayName, source, license, attribution, sha256, localUri,
        coverage.toDomain(), minZoom, maxZoom, version,
        rasterFormat = rasterFormat.ifBlank { "png" },
        tileSize = tileSize.takeIf { it > 0 } ?: 256,
    )
}
