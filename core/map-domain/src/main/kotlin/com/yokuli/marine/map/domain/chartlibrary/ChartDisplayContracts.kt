package com.yokuli.marine.map.domain.chartlibrary

import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.MapTileScheme
import java.security.MessageDigest

sealed interface ChartDisplaySelection {
    data object None : ChartDisplaySelection
    data class PinnedAsset(val assetId: ChartAssetId) : ChartDisplaySelection
    data class SourceSet(val sourceIds: Set<ChartSourceId>) : ChartDisplaySelection {
        init { require(sourceIds.isNotEmpty() && sourceIds.size <= MAX_SELECTED_CHART_SOURCES) }
    }
}

data class ChartDisplayPreferences(
    val selection: ChartDisplaySelection = ChartDisplaySelection.None,
    val overlaysVisible: Boolean = true,
    val assetOpacity: Map<ChartAssetId, Float> = emptyMap(),
) {
    init {
        require(assetOpacity.size <= MAX_CHART_DISPLAY_PREFERENCES)
        require(assetOpacity.values.all { it.isFinite() && it in 0f..1f })
    }
}

data class ChartDisplayViewport(val bounds: GeoBounds, val zoom: Int) {
    init { require(zoom in 0..24) }
}

data class ChartDisplayLayer(
    val assetId: ChartAssetId,
    val displayName: String,
    val request: ChartReadRequest,
    val role: ChartAssetRole,
    val priority: Int,
    val opacity: Float,
    val tileSize: Int,
    val tileScheme: MapTileScheme,
    val minZoom: Int,
    val maxZoom: Int,
    val bounds: GeoBounds?,
    val attribution: String?,
) {
    init {
        require(displayName.isNotBlank())
        require(opacity.isFinite() && opacity in 0f..1f)
        require(tileSize == 256 || tileSize == 512)
        require(minZoom in 0..24 && maxZoom in minZoom..24)
    }
}

enum class ChartDisplayIssue {
    PINNED_ASSET_MISSING,
    PINNED_ASSET_UNAVAILABLE,
    SOURCE_MISSING_OR_DISABLED,
    UNKNOWN_BOUNDS_EXCLUDED,
    NO_NATIVE_ZOOM,
    LAYER_LIMIT_REACHED,
}

data class ChartDisplayPlan(
    val generation: Long = 0L,
    val catalogRevision: Long = 0L,
    val fingerprint: String = EMPTY_FINGERPRINT,
    val selection: ChartDisplaySelection = ChartDisplaySelection.None,
    /** Back-to-front MapLibre order: base charts first, overlays last. */
    val layers: List<ChartDisplayLayer> = emptyList(),
    val omittedLayerCount: Int = 0,
    val issues: Set<ChartDisplayIssue> = emptySet(),
) {
    init {
        require(generation >= 0L && catalogRevision >= 0L)
        require(fingerprint.matches(Regex("[0-9a-f]{64}")))
        require(layers.size <= MAX_ACTIVE_CHART_LAYERS && omittedLayerCount >= 0)
        require(layers.map(ChartDisplayLayer::assetId).distinct().size == layers.size)
    }

    companion object {
        private const val EMPTY_FINGERPRINT = "0000000000000000000000000000000000000000000000000000000000000000"
        val EMPTY = ChartDisplayPlan()
    }
}

object ChartDisplayPlanner {
    fun plan(
        generation: Long,
        catalog: ChartCatalogSnapshot,
        sources: List<ChartLibrarySource>,
        assets: List<ChartAsset>,
        preferences: ChartDisplayPreferences,
        viewport: ChartDisplayViewport?,
    ): ChartDisplayPlan {
        require(generation > 0L)
        val sourceById = sources.associateBy(ChartLibrarySource::id)
        val assetById = assets.associateBy(ChartAsset::id)
        val issues = linkedSetOf<ChartDisplayIssue>()
        val selected = when (val selection = preferences.selection) {
            ChartDisplaySelection.None -> emptyList()
            is ChartDisplaySelection.PinnedAsset -> {
                val pinned = assetById[selection.assetId]
                when {
                    pinned == null -> {
                        issues += ChartDisplayIssue.PINNED_ASSET_MISSING
                        emptyList()
                    }
                    !pinned.isDisplayEligible(sourceById) -> {
                        issues += ChartDisplayIssue.PINNED_ASSET_UNAVAILABLE
                        emptyList()
                    }
                    else -> listOf(pinned)
                }
            }
            is ChartDisplaySelection.SourceSet -> {
                val enabledIds = selection.sourceIds.filterTo(linkedSetOf()) { sourceById[it]?.enabled == true }
                if (enabledIds.size != selection.sourceIds.size) issues += ChartDisplayIssue.SOURCE_MISSING_OR_DISABLED
                assets.asSequence()
                    .filter { asset -> asset.memberships.any(enabledIds::contains) && asset.isDisplayEligible(sourceById) }
                    .filter { asset ->
                        if (asset.role == ChartAssetRole.OVERLAY && !preferences.overlaysVisible) return@filter false
                        val bounds = asset.facts.bounds
                        if (bounds == null) {
                            issues += ChartDisplayIssue.UNKNOWN_BOUNDS_EXCLUDED
                            false
                        } else {
                            val target = viewport
                            target == null || bounds.intersects(target.bounds)
                        }
                    }
                    .filter { asset ->
                        val targetZoom = viewport?.zoom ?: return@filter true
                        val native = targetZoom in requireNotNull(asset.facts.minZoom)..requireNotNull(asset.facts.maxZoom)
                        if (!native) issues += ChartDisplayIssue.NO_NATIVE_ZOOM
                        native
                    }
                    .toList()
            }
        }
        if (
            preferences.selection is ChartDisplaySelection.PinnedAsset && viewport != null &&
            selected.any { viewport.zoom !in requireNotNull(it.facts.minZoom)..requireNotNull(it.facts.maxZoom) }
        ) issues += ChartDisplayIssue.NO_NATIVE_ZOOM
        val ordered = selected.sortedWith(
            compareBy<ChartAsset> { if (it.role == ChartAssetRole.BASE) 0 else 1 }
                .thenBy(ChartAsset::priority)
                .thenBy { it.id.value },
        )
        val visible = ordered.take(MAX_ACTIVE_CHART_LAYERS)
        val omitted = ordered.size - visible.size
        if (omitted > 0) issues += ChartDisplayIssue.LAYER_LIMIT_REACHED
        val layers = visible.mapNotNull { asset ->
            val source = asset.memberships.asSequence().mapNotNull(sourceById::get)
                .filter { it.enabled && it.scan.generation > 0L }
                .sortedBy { it.id.value }
                .firstOrNull() ?: return@mapNotNull null
            ChartDisplayLayer(
                assetId = asset.id,
                displayName = asset.displayPath.substringAfterLast('/').ifBlank { asset.displayPath },
                request = ChartReadRequest(asset.id, asset.locator, asset.revision, source.scan.generation),
                role = asset.role,
                priority = asset.priority,
                opacity = preferences.assetOpacity[asset.id] ?: if (asset.role == ChartAssetRole.BASE) 1f else .85f,
                tileSize = requireNotNull(asset.facts.tileSize),
                tileScheme = requireNotNull(asset.facts.tileScheme),
                minZoom = requireNotNull(asset.facts.minZoom),
                maxZoom = requireNotNull(asset.facts.maxZoom),
                bounds = asset.facts.bounds,
                attribution = asset.facts.attribution,
            )
        }
        return ChartDisplayPlan(
            generation = generation,
            catalogRevision = catalog.revision,
            fingerprint = fingerprint(catalog.revision, preferences, viewport, layers),
            selection = preferences.selection,
            layers = layers,
            omittedLayerCount = omitted + (visible.size - layers.size),
            issues = issues,
        )
    }

    private fun ChartAsset.isDisplayEligible(sources: Map<ChartSourceId, ChartLibrarySource>): Boolean =
        enabled && access == ChartAssetAccessState.READABLE &&
            validation in setOf(ChartAssetValidationState.BASIC_READABLE, ChartAssetValidationState.FULL_VERIFIED) &&
            facts.format == ChartAssetFormat.RASTER_MBTILES && facts.tileSize in setOf(256, 512) &&
            facts.tileScheme != null && facts.minZoom != null && facts.maxZoom != null &&
            memberships.any { sources[it]?.let { source -> source.enabled && source.scan.generation > 0L } == true }

    private fun GeoBounds.intersects(other: GeoBounds): Boolean {
        if (north < other.south || other.north < south) return false
        return longitudeSegments().any { left -> other.longitudeSegments().any { right -> left.first <= right.second && right.first <= left.second } }
    }

    private fun GeoBounds.longitudeSegments(): List<Pair<Double, Double>> = if (crossesAntimeridian) {
        listOf(west to 180.0, -180.0 to east)
    } else listOf(west to east)

    private fun fingerprint(
        catalogRevision: Long,
        preferences: ChartDisplayPreferences,
        viewport: ChartDisplayViewport?,
        layers: List<ChartDisplayLayer>,
    ): String {
        val canonical = buildString {
            append(catalogRevision).append('|').append(
                when (val selection = preferences.selection) {
                    ChartDisplaySelection.None -> "none"
                    is ChartDisplaySelection.PinnedAsset -> "asset:${selection.assetId.value}"
                    is ChartDisplaySelection.SourceSet -> "sources:${selection.sourceIds.map { it.value }.sorted().joinToString(",")}" 
                },
            ).append('|')
            append(viewport?.bounds).append('|').append(viewport?.zoom).append('|')
            layers.forEach { layer ->
                append(layer.assetId.value).append(':').append(layer.request.revision.cacheKey).append(':')
                    .append(layer.request.sourceGeneration).append(':').append(layer.opacity).append(';')
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

const val MAX_SELECTED_CHART_SOURCES = 8
const val MAX_ACTIVE_CHART_LAYERS = 8
const val MAX_CHART_DISPLAY_PREFERENCES = 256

enum class ChartDisplayCoverageStatus { NOT_CHECKED, COMPLETE, PARTIAL, UNREADABLE, STALE }

data class ChartDisplayCoverageResult(
    val planFingerprint: String,
    val targetZoom: Int,
    val status: ChartDisplayCoverageStatus,
    val requiredKeyCount: Int,
    val missingKeys: Set<com.yokuli.marine.map.domain.SlippyTileKey>,
    val checkedBaseAssetIds: Set<ChartAssetId>,
) {
    init {
        require(planFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(targetZoom in 0..24 && requiredKeyCount >= missingKeys.size)
        require(checkedBaseAssetIds.size <= MAX_ACTIVE_CHART_LAYERS)
    }
}

object ChartDisplayCoverageEvaluator {
    fun evaluate(
        plan: ChartDisplayPlan,
        targetZoom: Int,
        requiredKeys: Set<com.yokuli.marine.map.domain.SlippyTileKey>,
        availableByAsset: Map<ChartAssetId, Set<com.yokuli.marine.map.domain.SlippyTileKey>>,
        expectedFingerprint: String = plan.fingerprint,
    ): ChartDisplayCoverageResult {
        if (expectedFingerprint != plan.fingerprint) return ChartDisplayCoverageResult(
            plan.fingerprint,
            targetZoom,
            ChartDisplayCoverageStatus.STALE,
            requiredKeys.size,
            requiredKeys,
            emptySet(),
        )
        val eligibleBase = plan.layers.filter { it.role == ChartAssetRole.BASE && targetZoom in it.minZoom..it.maxZoom }
        val checked = eligibleBase.filter { availableByAsset.containsKey(it.assetId) }
        if (checked.isEmpty()) return ChartDisplayCoverageResult(
            plan.fingerprint,
            targetZoom,
            if (requiredKeys.isEmpty()) ChartDisplayCoverageStatus.COMPLETE else ChartDisplayCoverageStatus.UNREADABLE,
            requiredKeys.size,
            requiredKeys,
            emptySet(),
        )
        val available = checked.flatMapTo(hashSetOf()) { availableByAsset.getValue(it.assetId) }
        val missing = requiredKeys - available
        return ChartDisplayCoverageResult(
            plan.fingerprint,
            targetZoom,
            if (missing.isEmpty()) ChartDisplayCoverageStatus.COMPLETE else ChartDisplayCoverageStatus.PARTIAL,
            requiredKeys.size,
            missing,
            checked.mapTo(linkedSetOf(), ChartDisplayLayer::assetId),
        )
    }
}

fun interface ChartDisplayCoveragePort {
    suspend fun evaluate(
        plan: ChartDisplayPlan,
        targetZoom: Int,
        requiredKeys: Set<com.yokuli.marine.map.domain.SlippyTileKey>,
        expectedFingerprint: String = plan.fingerprint,
    ): ChartDisplayCoverageResult
}
