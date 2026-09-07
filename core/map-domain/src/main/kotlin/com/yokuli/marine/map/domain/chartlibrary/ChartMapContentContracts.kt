package com.yokuli.marine.map.domain.chartlibrary

import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.computeGeoBounds

@JvmInline
value class ChartLayerId(val value: String) {
    init { require(value.matches(MAP_CONTENT_ID)) }
}

@JvmInline
value class ChartViewId(val value: String) {
    init { require(value.matches(MAP_CONTENT_ID)) }
}

enum class ChartBuiltInBaseStyle { NONE, STANDARD, SATELLITE }

data class ChartLayer(
    val id: ChartLayerId,
    val displayName: String,
    val sourceIds: Set<ChartSourceId>,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val stackOrder: Int = 0,
    val role: ChartAssetRole = ChartAssetRole.BASE,
) {
    init {
        require(displayName.isNotBlank() && displayName.length <= MAX_MAP_CONTENT_NAME_LENGTH)
        require(sourceIds.isNotEmpty() && sourceIds.size <= MAX_LAYER_SOURCES)
        require(opacity.isFinite() && opacity in 0f..1f)
        require(stackOrder in -10_000..10_000)
    }
}

data class ChartViewLayer(
    val layerId: ChartLayerId,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val stackOrder: Int = 0,
) {
    init {
        require(opacity.isFinite() && opacity in 0f..1f)
        require(stackOrder in -10_000..10_000)
    }
}

data class ChartMapView(
    val id: ChartViewId,
    val displayName: String,
    val baseStyle: ChartBuiltInBaseStyle,
    val layers: List<ChartViewLayer>,
) {
    init {
        require(displayName.isNotBlank() && displayName.length <= MAX_MAP_CONTENT_NAME_LENGTH)
        require(layers.size <= MAX_VIEW_LAYERS)
        require(layers.map(ChartViewLayer::layerId).distinct().size == layers.size)
    }
}

enum class ChartLayerHealth { READY, WARNING, PARTIAL, PERMISSION_LOST, UNAVAILABLE, EMPTY }

data class ChartLayerSummary(
    val layer: ChartLayer,
    val health: ChartLayerHealth,
    val assetCount: Int,
    val readyAssetCount: Int,
    val totalSizeBytes: Long,
    val coverage: GeoBounds?,
    val minZoom: Int?,
    val maxZoom: Int?,
    val warningCount: Int,
) {
    init {
        require(assetCount >= 0 && readyAssetCount in 0..assetCount)
        require(totalSizeBytes >= 0L && warningCount >= 0)
        require(minZoom == null || maxZoom == null || minZoom <= maxZoom)
    }
}

object ChartLayerSummaryProjector {
    fun project(
        layer: ChartLayer,
        sources: Collection<ChartLibrarySource>,
        assets: Collection<ChartAsset>,
    ): ChartLayerSummary {
        val ownedSources = sources.filter { it.id in layer.sourceIds }
        val ownedAssets = assets.filter { asset -> asset.memberships.any(layer.sourceIds::contains) }
        val readyAssets = ownedAssets.filter(ChartAsset::isRenderableRaster)
        val permissionLost = ownedSources.any { it.grantState in setOf(ChartGrantState.DENIED, ChartGrantState.REVOKED) } ||
            ownedAssets.any { it.access == ChartAssetAccessState.PERMISSION_LOST }
        val partial = ownedSources.any { it.scan.status in setOf(ChartScanStatus.PARTIAL, ChartScanStatus.FAILED, ChartScanStatus.CANCELLED) }
        val warnings = ownedAssets.sumOf { it.compatibilityWarnings.size } + ownedSources.sumOf { it.scan.issueCount }
        val health = when {
            permissionLost -> ChartLayerHealth.PERMISSION_LOST
            ownedAssets.isEmpty() -> ChartLayerHealth.EMPTY
            readyAssets.isEmpty() -> ChartLayerHealth.UNAVAILABLE
            partial -> ChartLayerHealth.PARTIAL
            warnings > 0 || readyAssets.size != ownedAssets.size -> ChartLayerHealth.WARNING
            else -> ChartLayerHealth.READY
        }
        val bounds = readyAssets.mapNotNull { it.facts.bounds }.unionBounds()
        val zooms = readyAssets.mapNotNull { asset ->
            asset.facts.minZoom?.let { min -> asset.facts.maxZoom?.let { max -> min..max } }
        }
        return ChartLayerSummary(
            layer = layer,
            health = health,
            assetCount = ownedAssets.size,
            readyAssetCount = readyAssets.size,
            totalSizeBytes = ownedAssets.fold(0L) { total, asset -> total.saturatedPlus(asset.facts.sizeBytes ?: 0L) },
            coverage = bounds,
            minZoom = zooms.minOfOrNull(IntRange::first),
            maxZoom = zooms.maxOfOrNull(IntRange::last),
            warningCount = warnings,
        )
    }
}

internal fun ChartAsset.isRenderableRaster(): Boolean =
    enabled && access == ChartAssetAccessState.READABLE &&
        validation in setOf(ChartAssetValidationState.BASIC_READABLE, ChartAssetValidationState.FULL_VERIFIED) &&
        facts.format == ChartAssetFormat.RASTER_MBTILES && facts.tileSize in setOf(256, 512) &&
        facts.tileScheme != null

private fun Collection<GeoBounds>.unionBounds(): GeoBounds? {
    if (isEmpty()) return null
    val points = flatMap { bounds ->
        listOf(
            GeoPoint(bounds.south, bounds.west),
            GeoPoint(bounds.south, bounds.east),
            GeoPoint(bounds.north, bounds.west),
            GeoPoint(bounds.north, bounds.east),
        )
    }
    return computeGeoBounds(points)
}

private fun Long.saturatedPlus(other: Long): Long =
    if (other > Long.MAX_VALUE - this) Long.MAX_VALUE else this + other

const val MAX_LAYER_SOURCES = 32
const val MAX_VIEW_LAYERS = 32
const val MAX_MAP_CONTENT_NAME_LENGTH = 128

private val MAP_CONTENT_ID = Regex("[A-Za-z0-9._-]{1,128}")
