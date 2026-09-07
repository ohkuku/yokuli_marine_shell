package com.yokuli.marine.feature.chartlibrary

import com.yokuli.marine.map.domain.chartlibrary.ChartAsset
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetAccessState
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetValidationState
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogSnapshot
import com.yokuli.marine.map.domain.chartlibrary.ChartLibraryRuntimeMetrics
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySource
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySourceKind
import com.yokuli.marine.map.domain.chartlibrary.ChartLibraryStorageSnapshot
import com.yokuli.marine.map.domain.chartlibrary.ChartLayer
import com.yokuli.marine.map.domain.chartlibrary.ChartLayerSummaryProjector
import com.yokuli.marine.map.domain.chartlibrary.ChartMapView
import com.yokuli.marine.map.domain.chartlibrary.ChartBuiltInBaseStyle
import com.yokuli.marine.map.domain.chartlibrary.ChartViewDisplayPlanner
import com.yokuli.marine.map.domain.chartlibrary.ChartViewId
import com.yokuli.marine.map.domain.chartlibrary.ChartViewLayer
import com.yokuli.marine.map.domain.chartlibrary.ChartManagedCopyCapability
import com.yokuli.marine.map.domain.chartlibrary.ChartGrantState
import com.yokuli.marine.map.domain.chartlibrary.ChartScanStatus
import com.yokuli.marine.map.domain.chartlibrary.ChartValidationSnapshot

object ChartLibraryProjector {
    fun project(
        catalog: ChartCatalogSnapshot,
        sources: List<ChartLibrarySource>,
        assets: List<ChartAsset>,
        layers: List<ChartLayer> = emptyList(),
        views: List<ChartMapView> = emptyList(),
        validation: ChartValidationSnapshot,
        storage: ChartLibraryStorageSnapshot,
        metrics: ChartLibraryRuntimeMetrics,
        local: ChartLibraryLocalState,
        notice: ChartLibraryNoticeUi? = null,
        busy: Boolean = false,
        originalByManaged: Map<com.yokuli.marine.map.domain.chartlibrary.ChartAssetId, com.yokuli.marine.map.domain.chartlibrary.ChartAssetId> = emptyMap(),
        managedByOriginal: Map<com.yokuli.marine.map.domain.chartlibrary.ChartAssetId, com.yokuli.marine.map.domain.chartlibrary.ChartAssetId> = emptyMap(),
    ): ChartLibraryUiState {
        val sourceById = sources.associateBy(ChartLibrarySource::id)
        val assetById = assets.associateBy(ChartAsset::id)
        val rows = assets.map { asset ->
            ChartLibraryAssetRowUi(
                id = asset.id,
                title = asset.displayPath.substringAfterLast('/').ifBlank { asset.displayPath },
                displayPath = asset.displayPath,
                sourceNames = asset.memberships.mapNotNull { sourceById[it]?.displayName }.sortedWith(String.CASE_INSENSITIVE_ORDER),
                enabled = asset.enabled,
                role = asset.role,
                priority = asset.priority,
                access = asset.access,
                validation = asset.validation,
                sizeBytes = asset.facts.sizeBytes,
                format = asset.facts.format,
                rasterMimeType = asset.facts.rasterMimeType,
                tileSize = asset.facts.tileSize,
                tileScheme = asset.facts.tileScheme,
                minZoom = asset.facts.minZoom,
                maxZoom = asset.facts.maxZoom,
                tileCount = asset.facts.tileCount,
                bounds = asset.facts.bounds,
                attribution = asset.facts.attribution,
                attributionProvenance = asset.facts.attributionProvenance,
                revisionSummary = asset.revision.contentSha256?.let { "SHA-256 ${it.take(12)}…" }
                    ?: listOfNotNull(asset.revision.observedSizeBytes, asset.revision.observedModifiedAtMillis)
                        .joinToString(" · ").takeIf { it.isNotBlank() },
                selected = asset.id in local.selectedAssetIds,
                managedCopyAvailable = storage.copyCapability == ChartManagedCopyCapability.AVAILABLE &&
                    asset.memberships.none { sourceById[it]?.kind == ChartLibrarySourceKind.MANAGED },
                isManagedAsset = asset.memberships.any { sourceById[it]?.kind == ChartLibrarySourceKind.MANAGED },
                originalAssetId = originalByManaged[asset.id],
                managedCopyAssetId = managedByOriginal[asset.id],
                validationJob = validation.jobs[asset.id],
                copyJob = storage.copyJobs[asset.id],
                accessMode = asset.accessMode,
                compatibilityWarnings = asset.compatibilityWarnings,
            )
        }.sortedWith(compareByDescending<ChartLibraryAssetRowUi> { it.priority }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })

        val sourceRows = sources.map { source ->
            val members = rows.filter { row -> assetById[row.id]?.memberships?.contains(source.id) == true }
            val knownSizes = members.mapNotNull(ChartLibraryAssetRowUi::sizeBytes)
            ChartLibrarySourceRowUi(
                id = source.id,
                name = source.displayName,
                kind = source.kind,
                provider = source.locator.providerAuthority(),
                enabled = source.enabled,
                recursive = source.recursive,
                defaultRole = source.defaultRole,
                grantState = source.grantState,
                scan = source.scan,
                assetCount = members.size,
                availableAssetCount = members.count(ChartLibraryAssetRowUi::available),
                attentionCount = members.count(ChartLibraryAssetRowUi::needsAttention) + source.scan.issueCount,
                referencedBytes = knownSizes.sum().takeIf { knownSizes.isNotEmpty() },
                unknownSizeCount = members.count { it.sizeBytes == null },
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        val layerRows = layers.map { layer ->
            val summary = ChartLayerSummaryProjector.project(layer, sources, assets)
            ChartLibraryLayerUi(
                id = layer.id,
                name = layer.displayName,
                sourceCount = layer.sourceIds.size,
                assetCount = summary.assetCount,
                readyAssetCount = summary.readyAssetCount,
                sizeBytes = summary.totalSizeBytes,
                bounds = summary.coverage,
                minZoom = summary.minZoom,
                maxZoom = summary.maxZoom,
                health = summary.health,
                warningCount = summary.warningCount,
                visible = layer.visible,
                opacity = layer.opacity,
                stackOrder = layer.stackOrder,
                selected = layer.id == local.selectedLayerId,
            )
        }.sortedWith(compareByDescending<ChartLibraryLayerUi> { it.stackOrder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val viewRows = views.map { view ->
            val viewLayers = view.layers.associateBy { it.layerId }
            val orderedLayers = layers.sortedByDescending { viewLayers[it.id]?.stackOrder ?: it.stackOrder }
            val viewSourceIds = layers.asSequence()
                .filter { it.id in viewLayers }
                .flatMap { it.sourceIds.asSequence() }
                .toSet()
            val viewAssets = rows.asSequence()
                .filter { row -> assetById[row.id]?.memberships?.any(viewSourceIds::contains) == true }
                .distinctBy(ChartLibraryAssetRowUi::id)
                .sortedWith(
                    compareByDescending<ChartLibraryAssetRowUi> { it.priority }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
                )
                .map { row ->
                    ChartLibraryViewAssetUi(
                        id = row.id,
                        name = row.title,
                        priority = row.priority,
                        available = row.available,
                        needsAttention = row.needsAttention,
                    )
                }
                .toList()
            ChartLibraryViewUi(
                id = view.id,
                name = view.displayName,
                baseStyle = view.baseStyle,
                layers = orderedLayers.map { layer ->
                    val item = viewLayers[layer.id]
                    ChartLibraryViewLayerUi(
                        id = layer.id,
                        name = layer.displayName,
                        included = item != null,
                        visible = item?.visible == true,
                        opacity = item?.opacity ?: layer.opacity,
                        stackOrder = item?.stackOrder ?: layer.stackOrder,
                    )
                },
                assets = viewAssets,
                visibleLayerCount = view.layers.count { it.visible },
                active = view.id == catalog.activeViewId,
                selected = view.id == local.selectedViewId,
            )
        }.sortedWith(compareByDescending<ChartLibraryViewUi> { it.active }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val selectedView = local.selectedViewId?.let { selectedId -> views.firstOrNull { it.id == selectedId } }
            ?: catalog.activeViewId?.let { activeId -> views.firstOrNull { it.id == activeId } }
            ?: views.firstOrNull()
        val displayGeneration = catalog.revision.coerceAtLeast(1L)
        val previewDisplayPlan = ChartViewDisplayPlanner.plan(
            generation = displayGeneration,
            catalog = catalog,
            sources = sources,
            assets = assets,
            layers = layers,
            view = selectedView,
            viewport = null,
        )
        val coverageLayers = local.selectedLayerId?.let { selectedId ->
            layers.filter { it.id == selectedId }
        } ?: layers
        val coverageView = ChartMapView(
            id = ChartViewId("coverage-preview"),
            displayName = "Coverage preview",
            baseStyle = ChartBuiltInBaseStyle.SATELLITE,
            layers = coverageLayers.sortedBy { it.stackOrder }.map { layer ->
                ChartViewLayer(
                    layerId = layer.id,
                    visible = true,
                    opacity = layer.opacity,
                    stackOrder = layer.stackOrder,
                )
            },
        )
        val coverageDisplayPlan = ChartViewDisplayPlanner.plan(
            generation = displayGeneration,
            catalog = catalog,
            sources = sources,
            assets = assets,
            layers = layers,
            view = coverageView,
            viewport = null,
        )

        val filteredAssets = rows.filter { row ->
            val matchesQuery = local.query.isBlank() || sequenceOf(row.title, row.displayPath)
                .plus(row.sourceNames.asSequence())
                .any { it.contains(local.query, ignoreCase = true) }
            matchesQuery && when (local.filter) {
                ChartLibraryFilter.ALL -> true
                ChartLibraryFilter.NEEDS_ATTENTION -> row.needsAttention
                ChartLibraryFilter.ENABLED -> row.enabled
                ChartLibraryFilter.DISABLED -> !row.enabled
            }
        }
        val filteredSources = sourceRows.filter { row ->
            val matchesQuery = local.query.isBlank() || row.name.contains(local.query, ignoreCase = true)
            matchesQuery && when (local.filter) {
                ChartLibraryFilter.ALL -> true
                ChartLibraryFilter.NEEDS_ATTENTION -> row.needsAttention
                ChartLibraryFilter.ENABLED -> row.enabled
                ChartLibraryFilter.DISABLED -> !row.enabled
            }
        }
        val externalAssets = assets.filter { asset ->
            asset.memberships.any { sourceById[it]?.kind != ChartLibrarySourceKind.MANAGED }
        }.distinctBy(ChartAsset::id)
        val referencedKnown = externalAssets.mapNotNull { it.facts.sizeBytes }
        val storageUi = ChartLibraryStorageUi(
            referencedOriginalBytes = referencedKnown.sum().takeIf { referencedKnown.isNotEmpty() },
            referencedUnknownSizeCount = externalAssets.count { it.facts.sizeBytes == null },
            managedCopyBytes = storage.managedCopyBytes,
            catalogBytes = storage.catalogBytes,
            cacheBytes = storage.cacheBytes,
            copyAvailable = storage.copyCapability == ChartManagedCopyCapability.AVAILABLE,
            availableCopyBytes = storage.availableCopyBytes,
            copyJobs = storage.copyJobs.values.sortedBy { it.sourceAssetId.value },
        )
        val page = when (val page = local.page) {
            ChartLibraryLocalPage.Overview -> ChartLibraryPageUi.Overview(filteredSources, filteredAssets)
            is ChartLibraryLocalPage.SourceDetail -> sourceRows.firstOrNull { it.id == page.sourceId }
                ?.let { source ->
                    ChartLibraryPageUi.SourceDetail(
                        source,
                        rows.filter { row -> assetById[row.id]?.memberships?.contains(source.id) == true },
                    )
                } ?: ChartLibraryPageUi.Overview(filteredSources, filteredAssets)
            is ChartLibraryLocalPage.AssetDetail -> rows.firstOrNull { it.id == page.assetId }
                ?.let(ChartLibraryPageUi::AssetDetail)
                ?: ChartLibraryPageUi.Overview(filteredSources, filteredAssets)
            ChartLibraryLocalPage.Storage -> ChartLibraryPageUi.Storage(storageUi)
            is ChartLibraryLocalPage.RemoveSourceConfirmation -> sourceRows.firstOrNull { it.id == page.sourceId }
                ?.let(ChartLibraryPageUi::RemoveSourceConfirmation)
                ?: ChartLibraryPageUi.Overview(filteredSources, filteredAssets)
            is ChartLibraryLocalPage.DeleteManagedCopyConfirmation -> rows.firstOrNull { it.id == page.assetId }
                ?.let(ChartLibraryPageUi::DeleteManagedCopyConfirmation)
                ?: ChartLibraryPageUi.Overview(filteredSources, filteredAssets)
            is ChartLibraryLocalPage.SaveManagedCopyConfirmation -> rows.firstOrNull { it.id == page.assetId }
                ?.let { ChartLibraryPageUi.SaveManagedCopyConfirmation(it, storage.availableCopyBytes) }
                ?: ChartLibraryPageUi.Overview(filteredSources, filteredAssets)
        }
        return ChartLibraryUiState(
            summary = ChartLibrarySummaryUi(
                sourceCount = catalog.sourceCount,
                assetCount = catalog.assetCount,
                availableAssetCount = rows.count(ChartLibraryAssetRowUi::available),
                attentionCount = catalog.issueCount + rows.count(ChartLibraryAssetRowUi::needsAttention) +
                    sourceRows.count { it.grantState !in setOf(ChartGrantState.GRANTED, ChartGrantState.NOT_REQUIRED) },
                scanningCount = sourceRows.count { it.scan.status == ChartScanStatus.RUNNING },
            ),
            workspaceMode = local.workspaceMode,
            query = local.query,
            filter = local.filter,
            selectedAssetIds = local.selectedAssetIds,
            page = page,
            notice = notice,
            // Background Basic checks are asset-local work. They must not freeze the whole App
            // or leave the user staring at an indefinite global "working" state.
            busy = busy,
            searchItems = sourceRows.map { ChartLibrarySearchItem.Source(it.id, it.name) } +
                rows.map { ChartLibrarySearchItem.Asset(it.id, it.title, it.sourceNames.joinToString(" · ")) },
            layers = layerRows,
            views = viewRows,
            coverageDisplayPlan = coverageDisplayPlan,
            previewDisplayPlan = previewDisplayPlan,
        )
    }

    private fun com.yokuli.marine.map.domain.chartlibrary.ChartOpaqueLocator.providerAuthority(): String? {
        val schemeEnd = value.indexOf("://")
        if (schemeEnd <= 0) return null
        return value.substring(schemeEnd + 3).substringBefore('/').takeIf { it.isNotBlank() }?.take(256)
    }
}
