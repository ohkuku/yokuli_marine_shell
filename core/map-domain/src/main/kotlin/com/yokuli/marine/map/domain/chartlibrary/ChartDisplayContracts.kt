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
    /** Quick visibility overrides for assets that still belong to the durable selection. */
    val hiddenAssetIds: Set<ChartAssetId> = emptySet(),
) {
    init {
        require(assetOpacity.size <= MAX_CHART_DISPLAY_PREFERENCES)
        require(assetOpacity.values.all { it.isFinite() && it in 0f..1f })
        require(hiddenAssetIds.size <= MAX_CHART_DISPLAY_PREFERENCES)
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
    val logicalLayerId: ChartLayerId? = null,
    val logicalLayerName: String? = null,
) {
    init {
        require(displayName.isNotBlank())
        require(opacity.isFinite() && opacity in 0f..1f)
        require(tileSize == 256 || tileSize == 512)
        require(minZoom in 0..24 && maxZoom in minZoom..24)
    }
}

data class ChartLogicalDisplayLayer(
    val id: ChartLayerId,
    val displayName: String,
    val role: ChartAssetRole,
    val visible: Boolean,
    val opacity: Float,
    val health: ChartLayerHealth,
    val assetIds: List<ChartAssetId>,
) {
    init {
        require(displayName.isNotBlank())
        require(opacity.isFinite() && opacity in 0f..1f)
        require(assetIds.distinct().size == assetIds.size)
    }
}

enum class ChartDisplayIssue {
    PINNED_ASSET_MISSING,
    PINNED_ASSET_UNAVAILABLE,
    SOURCE_MISSING_OR_DISABLED,
    UNKNOWN_BOUNDS_UNFILTERED,
    NO_NATIVE_ZOOM,
    LAYER_LIMIT_REACHED,
    ACTIVE_VIEW_MISSING,
    LOGICAL_LAYER_MISSING,
    LOGICAL_LAYER_UNAVAILABLE,
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
    val activeViewId: ChartViewId? = null,
    val activeViewName: String? = null,
    val builtInBaseStyle: ChartBuiltInBaseStyle = ChartBuiltInBaseStyle.NONE,
    val logicalLayers: List<ChartLogicalDisplayLayer> = emptyList(),
) {
    init {
        require(generation >= 0L && catalogRevision >= 0L)
        require(fingerprint.matches(Regex("[0-9a-f]{64}")))
        require(layers.size <= MAX_ACTIVE_CHART_ASSETS && omittedLayerCount >= 0)
        require(layers.map(ChartDisplayLayer::assetId).distinct().size == layers.size)
        require(logicalLayers.size <= MAX_VIEW_LAYERS)
        require(logicalLayers.map(ChartLogicalDisplayLayer::id).distinct().size == logicalLayers.size)
        require(activeViewId != null || activeViewName == null)
    }

    companion object {
        private const val EMPTY_FINGERPRINT = "0000000000000000000000000000000000000000000000000000000000000000"
        val EMPTY = ChartDisplayPlan()
    }
}

/** Resolves one user-facing View into logical Layers and the concrete immutable files consumed by renderers. */
object ChartViewDisplayPlanner {
    fun plan(
        generation: Long,
        catalog: ChartCatalogSnapshot,
        sources: List<ChartLibrarySource>,
        assets: List<ChartAsset>,
        layers: List<ChartLayer>,
        view: ChartMapView?,
        viewport: ChartDisplayViewport?,
    ): ChartDisplayPlan {
        require(generation > 0L)
        val issues = linkedSetOf<ChartDisplayIssue>()
        if (view == null) issues += ChartDisplayIssue.ACTIVE_VIEW_MISSING
        val sourceById = sources.associateBy(ChartLibrarySource::id)
        val layerById = layers.associateBy(ChartLayer::id)
        val renderLayers = mutableListOf<ChartDisplayLayer>()
        val logicalPlans = mutableListOf<ChartLogicalDisplayLayer>()
        var omitted = 0

        view?.layers.orEmpty()
            .sortedWith(compareBy<ChartViewLayer>(ChartViewLayer::stackOrder).thenBy { it.layerId.value })
            .forEach { entry ->
                val layer = layerById[entry.layerId]
                if (layer == null) {
                    issues += ChartDisplayIssue.LOGICAL_LAYER_MISSING
                    return@forEach
                }
                val effectiveVisible = layer.visible && entry.visible
                val summary = ChartLayerSummaryProjector.project(layer, sources, assets)
                val concrete = if (!effectiveVisible) emptyList() else assets.asSequence()
                    .filter { asset -> asset.memberships.any(layer.sourceIds::contains) }
                    .filter(ChartAsset::isRenderableRaster)
                    .filter { asset -> asset.memberships.any { sourceId ->
                        sourceById[sourceId]?.let { it.enabled && it.scan.generation > 0L } == true
                    } }
                    .filter { asset -> asset.visibleIn(viewport, issues) }
                    .sortedWith(compareBy<ChartAsset> { it.displayPath.lowercase() }.thenBy { it.id.value })
                    .toList()
                if (effectiveVisible && concrete.isEmpty()) issues += ChartDisplayIssue.LOGICAL_LAYER_UNAVAILABLE
                logicalPlans += ChartLogicalDisplayLayer(
                    id = layer.id,
                    displayName = layer.displayName,
                    role = layer.role,
                    visible = effectiveVisible,
                    opacity = entry.opacity,
                    health = summary.health,
                    assetIds = concrete.map(ChartAsset::id),
                )
                concrete.forEachIndexed { assetIndex, asset ->
                    if (renderLayers.size >= MAX_ACTIVE_CHART_ASSETS) {
                        omitted++
                        issues += ChartDisplayIssue.LAYER_LIMIT_REACHED
                        return@forEachIndexed
                    }
                    val source = asset.memberships.asSequence()
                        .filter(layer.sourceIds::contains)
                        .mapNotNull(sourceById::get)
                        .filter { it.enabled && it.scan.generation > 0L }
                        .sortedBy { it.id.value }
                        .firstOrNull() ?: return@forEachIndexed
                    renderLayers += ChartDisplayLayer(
                        assetId = asset.id,
                        displayName = layer.displayName,
                        request = ChartReadRequest(asset.id, asset.locator, asset.revision, source.scan.generation),
                        role = layer.role,
                        priority = (entry.stackOrder * MAX_ASSETS_PER_LOGICAL_LAYER + assetIndex)
                            .coerceIn(-10_000, 10_000),
                        opacity = entry.opacity,
                        tileSize = requireNotNull(asset.facts.tileSize),
                        tileScheme = requireNotNull(asset.facts.tileScheme),
                        minZoom = asset.facts.minZoom ?: MIN_ZOOM,
                        maxZoom = asset.facts.maxZoom ?: MAX_ZOOM,
                        bounds = asset.facts.bounds,
                        attribution = asset.facts.attribution,
                        logicalLayerId = layer.id,
                        logicalLayerName = layer.displayName,
                    )
                }
            }

        val fingerprint = viewFingerprint(catalog.revision, view, viewport, renderLayers)
        return ChartDisplayPlan(
            generation = generation,
            catalogRevision = catalog.revision,
            fingerprint = fingerprint,
            layers = renderLayers,
            omittedLayerCount = omitted,
            issues = issues,
            activeViewId = view?.id,
            activeViewName = view?.displayName,
            builtInBaseStyle = view?.baseStyle ?: ChartBuiltInBaseStyle.NONE,
            logicalLayers = logicalPlans,
        )
    }

    private fun ChartAsset.visibleIn(
        viewport: ChartDisplayViewport?,
        issues: MutableSet<ChartDisplayIssue>,
    ): Boolean {
        val target = viewport ?: return true
        val zoomVisible = facts.minZoom?.let { min -> facts.maxZoom?.let { max -> target.zoom in min..max } } ?: true
        if (!zoomVisible) issues += ChartDisplayIssue.NO_NATIVE_ZOOM
        val boundsVisible = facts.bounds?.intersectsView(target.bounds) ?: run {
            issues += ChartDisplayIssue.UNKNOWN_BOUNDS_UNFILTERED
            true
        }
        return zoomVisible && boundsVisible
    }

    private fun GeoBounds.intersectsView(other: GeoBounds): Boolean {
        if (north < other.south || other.north < south) return false
        fun segments(value: GeoBounds): List<Pair<Double, Double>> = if (value.crossesAntimeridian) {
            listOf(value.west to 180.0, -180.0 to value.east)
        } else listOf(value.west to value.east)
        return segments(this).any { left -> segments(other).any { right -> left.first <= right.second && right.first <= left.second } }
    }

    private fun viewFingerprint(
        revision: Long,
        view: ChartMapView?,
        viewport: ChartDisplayViewport?,
        layers: List<ChartDisplayLayer>,
    ): String {
        val canonical = buildString {
            append(revision).append('|').append(view?.id?.value).append('|').append(view?.displayName)
            append('|').append(view?.baseStyle).append('|').append(viewport).append('|')
            layers.forEach { layer ->
                append(layer.logicalLayerId?.value).append(':').append(layer.assetId.value).append(':')
                    .append(layer.request.revision.cacheKey).append(':').append(layer.opacity).append(';')
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
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
                    .filterNot { asset -> asset.id in preferences.hiddenAssetIds }
                    .filter { asset ->
                        if (asset.role == ChartAssetRole.OVERLAY && !preferences.overlaysVisible) return@filter false
                        val bounds = asset.facts.bounds
                        if (bounds == null) {
                            issues += ChartDisplayIssue.UNKNOWN_BOUNDS_UNFILTERED
                            true
                        } else {
                            val target = viewport
                            target == null || bounds.intersects(target.bounds)
                        }
                    }
                    .filter { asset ->
                        val targetZoom = viewport?.zoom ?: return@filter true
                        val knownRange = asset.knownZoomRange() ?: return@filter true
                        val native = targetZoom in knownRange
                        if (!native) issues += ChartDisplayIssue.NO_NATIVE_ZOOM
                        native
                    }
                    .toList()
            }
        }
        val visibleSelection = selected.filterNot { it.id in preferences.hiddenAssetIds }
        if (
            preferences.selection is ChartDisplaySelection.PinnedAsset && viewport != null &&
            visibleSelection.any { asset -> asset.knownZoomRange()?.let { viewport.zoom !in it } == true }
        ) issues += ChartDisplayIssue.NO_NATIVE_ZOOM
        val ordered = visibleSelection.sortedWith(
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
                minZoom = asset.knownZoomRange()?.first ?: MIN_ZOOM,
                maxZoom = asset.knownZoomRange()?.last ?: MAX_ZOOM,
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
            facts.tileScheme != null &&
            memberships.any { sources[it]?.let { source -> source.enabled && source.scan.generation > 0L } == true }

    private fun ChartAsset.knownZoomRange(): IntRange? =
        if (facts.minZoom != null && facts.maxZoom != null) facts.minZoom..facts.maxZoom else null

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
const val MAX_ACTIVE_CHART_ASSETS = 32
const val MAX_CHART_DISPLAY_PREFERENCES = 256
private const val MAX_ASSETS_PER_LOGICAL_LAYER = 1_000

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
        expectedFingerprint: String,
    ): ChartDisplayCoverageResult
}
