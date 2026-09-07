package com.yokuli.marine.chart.library.android

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.chartlibrary.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomChartCatalogRepository private constructor(
    private val database: ChartCatalogDatabase,
) : ChartCatalogReadPort, ChartLibraryCommandPort, AutoCloseable {
    private val dao = database.dao()
    private val closed = AtomicBoolean(false)
    private val writer = Mutex()
    private val mutableSnapshot = MutableStateFlow(
        runBlocking(Dispatchers.IO) {
            database.withTransaction {
                normalizeGeneratedLegacyDefaultView()
                readSnapshot()
            }
        },
    )
    override val snapshot: StateFlow<ChartCatalogSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun sources(offset: Int, limit: Int): ChartCatalogPage<ChartLibrarySource> = ioRead {
        checkPage(offset, limit)
        ChartCatalogPage(dao.sources(limit, offset).map(ChartSourceEntity::toDomain), offset, limit, dao.sourceCount())
    }

    override suspend fun source(id: ChartSourceId): ChartLibrarySource? = ioRead {
        dao.source(id.value)?.toDomain()
    }

    override suspend fun assets(query: ChartAssetQuery, offset: Int, limit: Int): ChartCatalogPage<ChartAsset> = ioRead {
        checkPage(offset, limit)
        database.withTransaction {
            val text = "%${query.text.escapeLike()}%"
            val access = query.access.map { it.name }.ifEmpty { listOf(ChartAssetAccessState.UNCHECKED.name) }
            val validation = query.validation.map { it.name }.ifEmpty { listOf(ChartAssetValidationState.DISCOVERED.name) }
            val rows = dao.assets(
                query.sourceId?.value, text, query.enabledOnly,
                query.access.isNotEmpty(), access,
                query.validation.isNotEmpty(), validation,
                limit, offset,
            )
            val items = rows.map { it.toDomain(dao.memberships(it.id)) }
            val total = dao.filteredAssetCount(
                query.sourceId?.value, text, query.enabledOnly,
                query.access.isNotEmpty(), access,
                query.validation.isNotEmpty(), validation,
            )
            ChartCatalogPage(items, offset, limit, total)
        }
    }

    override suspend fun asset(id: ChartAssetId): ChartAsset? = ioRead {
        dao.asset(id.value)?.let { it.toDomain(dao.memberships(it.id)) }
    }

    override suspend fun resolveLegacyAsset(legacyLogicalId: String, legacyVersionId: String?): ChartAssetId? = ioRead {
        dao.legacyAssetId(legacyLogicalId, legacyVersionId.orEmpty())?.let(::ChartAssetId)
    }

    override suspend fun managedCopyFor(originalAssetId: ChartAssetId): ChartAssetId? = ioRead {
        dao.managedCopyFor(originalAssetId.value)?.let(::ChartAssetId)
    }

    override suspend fun originalForManagedCopy(managedAssetId: ChartAssetId): ChartAssetId? = ioRead {
        dao.originalForManagedCopy(managedAssetId.value)?.let(::ChartAssetId)
    }

    override suspend fun layers(offset: Int, limit: Int): ChartCatalogPage<ChartLayer> = ioRead {
        checkPage(offset, limit)
        ChartCatalogPage(
            dao.layers(limit, offset).map { it.toDomain(dao.sourceIdsForLayer(it.id)) },
            offset,
            limit,
            dao.layerCount(),
        )
    }

    override suspend fun layer(id: ChartLayerId): ChartLayer? = ioRead {
        dao.layer(id.value)?.let { it.toDomain(dao.sourceIdsForLayer(it.id)) }
    }

    override suspend fun views(offset: Int, limit: Int): ChartCatalogPage<ChartMapView> = ioRead {
        checkPage(offset, limit)
        ChartCatalogPage(
            dao.views(limit, offset).map { it.toDomain(dao.viewLayers(it.id)) },
            offset,
            limit,
            dao.viewCount(),
        )
    }

    override suspend fun view(id: ChartViewId): ChartMapView? = ioRead {
        dao.view(id.value)?.let { it.toDomain(dao.viewLayers(it.id)) }
    }

    override suspend fun activeView(): ChartMapView? = ioRead {
        dao.metadata()?.activeViewId?.let { id -> dao.view(id)?.toDomain(dao.viewLayers(id)) }
    }

    override suspend fun transact(transaction: ChartCatalogTransaction): ChartCatalogCommitResult = writer.withLock {
        if (closed.get()) return@withLock ChartCatalogCommitResult.Failed(ChartCatalogFailure.CLOSED)
        try {
            val resolved = linkedMapOf<ChartAssetId, ChartAssetId>()
            val outcome = withContext(Dispatchers.IO) {
                database.withTransaction {
                    val metadata = dao.metadata() ?: ChartCatalogMetadataEntity(
                        revision = 0L,
                        lastTransactionId = null,
                        activeViewId = null,
                    )
                    if (dao.appliedTransactionRevision(transaction.transactionId) != null) {
                        return@withTransaction CommitOutcome(readSnapshot(), resolved)
                    }
                    if (transaction.expectedRevision != null && transaction.expectedRevision != metadata.revision) {
                        return@withTransaction CommitOutcome(conflict = metadata.revision)
                    }
                    transaction.mutations.forEach { mutation -> applyMutation(mutation, resolved) }
                    ensureDefaultLayersForSources()
                    val requestedActiveViewId = dao.metadata()?.activeViewId ?: metadata.activeViewId
                    val activeViewId = resolveActiveView(requestedActiveViewId)
                    val newRevision = metadata.revision + 1L
                    dao.putMetadata(
                        ChartCatalogMetadataEntity(
                            revision = newRevision,
                            lastTransactionId = transaction.transactionId,
                            activeViewId = activeViewId,
                        ),
                    )
                    dao.recordTransaction(ChartCatalogTransactionEntity(transaction.transactionId, newRevision))
                    dao.pruneTransactions(MAX_RECORDED_TRANSACTIONS)
                    CommitOutcome(readSnapshot(), resolved)
                }
            }
            if (outcome.conflict != null) return@withLock ChartCatalogCommitResult.Conflict(outcome.conflict)
            val published = requireNotNull(outcome.snapshot)
            mutableSnapshot.value = published
            ChartCatalogCommitResult.Committed(published, outcome.resolved)
        } catch (_: InvalidReferenceException) {
            ChartCatalogCommitResult.Failed(ChartCatalogFailure.INVALID_REFERENCE)
        } catch (_: IdentityConflictException) {
            ChartCatalogCommitResult.Failed(ChartCatalogFailure.IDENTITY_CONFLICT)
        } catch (_: Throwable) {
            ChartCatalogCommitResult.Failed(ChartCatalogFailure.PERSISTENCE)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) database.close()
    }

    private suspend fun applyMutation(
        mutation: ChartCatalogMutation,
        resolved: MutableMap<ChartAssetId, ChartAssetId>,
    ) {
        when (mutation) {
            is ChartCatalogMutation.PutSource -> dao.putSource(mutation.source.toEntity())
            is ChartCatalogMutation.RemoveSource -> {
                dao.deleteSource(mutation.sourceId.value)
                dao.deleteOrphanAssets()
                dao.deleteOrphanLayers()
            }
            is ChartCatalogMutation.PutAsset -> {
                val asset = mutation.asset
                if (asset.memberships.any { dao.source(it.value) == null }) throw InvalidReferenceException()
                val sameId = dao.asset(asset.id.value)
                if (sameId != null && (sameId.authority != asset.documentIdentity.authority || sameId.documentId != asset.documentIdentity.documentId)) {
                    throw IdentityConflictException()
                }
                val existing = dao.assetByIdentity(asset.documentIdentity.authority, asset.documentIdentity.documentId)
                val actualId = existing?.id?.let(::ChartAssetId) ?: asset.id
                resolved[asset.id] = actualId
                dao.putAsset(asset.copy(id = actualId).toEntity())
                dao.putMemberships(asset.memberships.map { ChartMembershipEntity(actualId.value, it.value) })
            }
            is ChartCatalogMutation.RemoveAssetMembership -> {
                dao.deleteMembership(mutation.assetId.value, mutation.sourceId.value)
                dao.deleteOrphanAssets()
            }
            is ChartCatalogMutation.PutLegacyMapping -> {
                if (dao.asset(mutation.mapping.assetId.value) == null) throw InvalidReferenceException()
                dao.putLegacyMapping(mutation.mapping.toEntity())
            }
            is ChartCatalogMutation.PutManagedCopyRelation -> {
                val relation = mutation.relation
                if (dao.asset(relation.originalAssetId.value) == null || dao.asset(relation.managedAssetId.value) == null) {
                    throw InvalidReferenceException()
                }
                dao.putManagedCopyRelation(relation.toEntity())
            }
            is ChartCatalogMutation.PutLayer -> putLayer(mutation.layer)
            is ChartCatalogMutation.PutView -> putView(mutation.view)
            is ChartCatalogMutation.RemoveView -> dao.deleteView(mutation.viewId.value)
            is ChartCatalogMutation.ActivateView -> {
                if (dao.view(mutation.viewId.value) == null) throw InvalidReferenceException()
                val metadata = dao.metadata() ?: ChartCatalogMetadataEntity(
                    revision = 0L,
                    lastTransactionId = null,
                    activeViewId = null,
                )
                dao.putMetadata(metadata.copy(activeViewId = mutation.viewId.value))
            }
        }
    }

    private suspend fun putLayer(layer: ChartLayer) {
        if (layer.sourceIds.any { dao.source(it.value) == null }) throw InvalidReferenceException()
        if (layer.sourceIds.any { sourceId -> dao.layerIdsForSource(sourceId.value).any { it != layer.id.value } }) {
            throw IdentityConflictException()
        }
        dao.putLayer(layer.toEntity())
        dao.deleteLayerSources(layer.id.value)
        dao.putLayerSources(layer.sourceIds.map { ChartLayerSourceEntity(layer.id.value, it.value) })
    }

    private suspend fun putView(view: ChartMapView) {
        if (view.layers.any { dao.layer(it.layerId.value) == null }) throw InvalidReferenceException()
        dao.putView(view.toEntity())
        dao.deleteViewLayers(view.id.value)
        dao.putViewLayers(view.layers.map { it.toEntity(view.id) })
    }

    private suspend fun ensureDefaultLayersForSources() {
        var offset = 0
        do {
            val page = dao.sources(DEFAULT_CATALOG_PAGE_SIZE, offset)
            page.filter { it.kind != ChartLibrarySourceKind.MANAGED.name && dao.layerIdsForSource(it.id).isEmpty() }
                .forEach { source ->
                    val id = ChartLayerId("source-${source.id}")
                    if (dao.layer(id.value) == null) {
                        putLayer(
                            ChartLayer(
                                id = id,
                                displayName = source.displayName,
                                sourceIds = setOf(ChartSourceId(source.id)),
                                visible = source.enabled,
                                opacity = 1f,
                                stackOrder = dao.layerCount().coerceAtMost(10_000),
                                role = ChartAssetRole.valueOf(source.defaultRole),
                            ),
                        )
                    }
                }
            offset += page.size
        } while (page.isNotEmpty() && offset < dao.sourceCount())
    }

    private suspend fun resolveActiveView(currentId: String?): String? {
        val validCurrent = currentId?.takeIf { dao.view(it) != null }
        return validCurrent ?: dao.allViewIds().firstOrNull()
    }

    /**
     * V4→V5 generated this record before Views were correctly defined as user content.
     * Remove only the untouched generated shape. The reserved ID alone is deliberately
     * insufficient: a renamed or otherwise edited View belongs to the user and survives.
     */
    private suspend fun normalizeGeneratedLegacyDefaultView() {
        val view = dao.view(LEGACY_GENERATED_VIEW_ID) ?: return
        if (view.displayName != LEGACY_GENERATED_VIEW_NAME ||
            view.baseStyle != ChartBuiltInBaseStyle.SATELLITE.name
        ) return
        val entries = dao.viewLayers(LEGACY_GENERATED_VIEW_ID)
        val layers = buildList {
            dao.allLayerIds().forEach { id -> dao.layer(id)?.let(::add) }
        }
        val untouched = entries.size == layers.size && entries.associateBy { it.layerId } ==
            layers.associate { layer ->
                layer.id to ChartViewLayerEntity(
                    viewId = LEGACY_GENERATED_VIEW_ID,
                    layerId = layer.id,
                    visible = layer.visible,
                    opacity = layer.opacity,
                    stackOrder = layer.stackOrder,
                )
            }
        if (!untouched) return
        dao.deleteView(LEGACY_GENERATED_VIEW_ID)
        dao.metadata()?.takeIf { it.activeViewId == LEGACY_GENERATED_VIEW_ID }?.let { metadata ->
            dao.putMetadata(metadata.copy(activeViewId = null))
        }
    }

    private suspend fun readSnapshot(): ChartCatalogSnapshot {
        val metadata = dao.metadata()
        return ChartCatalogSnapshot(
            revision = metadata?.revision ?: 0L,
            sourceCount = dao.sourceCount(),
            assetCount = dao.assetCount(),
            issueCount = dao.issueCount(),
            lastTransactionId = metadata?.lastTransactionId,
            layerCount = dao.layerCount(),
            viewCount = dao.viewCount(),
            activeViewId = metadata?.activeViewId?.takeIf { dao.view(it) != null }?.let(::ChartViewId),
        )
    }

    private suspend fun <T> ioRead(block: suspend () -> T): T {
        check(!closed.get()) { "Chart catalog is closed" }
        return withContext(Dispatchers.IO) { block() }
    }

    companion object {
        private const val MAX_RECORDED_TRANSACTIONS = 256

        fun create(context: Context, file: File): RoomChartCatalogRepository {
            file.parentFile?.mkdirs()
            val database = Room.databaseBuilder(context.applicationContext, ChartCatalogDatabase::class.java, file.absolutePath)
                .addMigrations(
                    CHART_CATALOG_MIGRATION_1_2,
                    CHART_CATALOG_MIGRATION_2_3,
                    CHART_CATALOG_MIGRATION_3_4,
                    CHART_CATALOG_MIGRATION_4_5,
                )
                .enableMultiInstanceInvalidation()
                .build()
            return RoomChartCatalogRepository(database)
        }
    }
}

private data class CommitOutcome(
    val snapshot: ChartCatalogSnapshot? = null,
    val resolved: Map<ChartAssetId, ChartAssetId> = emptyMap(),
    val conflict: Long? = null,
)
private class InvalidReferenceException : Exception()
private class IdentityConflictException : Exception()

private fun checkPage(offset: Int, limit: Int) {
    require(offset >= 0 && limit in 1..MAX_CATALOG_PAGE_SIZE)
}
private fun String.escapeLike() = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

private fun ChartLibrarySource.toEntity() = ChartSourceEntity(
    id.value, kind.name, locator.value, displayName, enabled, recursive, defaultRole.name, grantState.name,
    scan.generation, scan.status.name, scan.lastSuccessfulGeneration, scan.discoveredCount, scan.issueCount,
)
private fun ChartSourceEntity.toDomain() = ChartLibrarySource(
    ChartSourceId(id), ChartLibrarySourceKind.valueOf(kind), ChartOpaqueLocator(locator), displayName,
    enabled, recursive, ChartAssetRole.valueOf(defaultRole), ChartGrantState.valueOf(grantState),
    ChartSourceScanState(scanGeneration, ChartScanStatus.valueOf(scanStatus), lastSuccessfulGeneration, discoveredCount, issueCount),
)

private fun ChartAsset.toEntity() = ChartAssetEntity(
    id.value, documentIdentity.authority, documentIdentity.documentId, locator.value, displayPath,
    revision.identity, revision.observedSizeBytes, revision.observedModifiedAtMillis,
    revision.providerRevisionHint, revision.contentSha256,
    facts.format.name, facts.sizeBytes, facts.bounds?.west, facts.bounds?.south, facts.bounds?.east, facts.bounds?.north,
    facts.minZoom, facts.maxZoom, facts.tileCount, facts.tileSize, facts.tileScheme?.name, facts.rasterMimeType,
    facts.attribution, facts.attributionProvenance.name,
    role.name, priority, enabled, access.name, validation.name,
    accessMode?.name, compatibilityWarnings.map { it.name }.sorted().joinToString(","),
)
private fun ChartAssetEntity.toDomain(membershipIds: List<String>): ChartAsset = ChartAsset(
    ChartAssetId(id), ChartDocumentIdentity(authority, documentId), ChartOpaqueLocator(locator),
    membershipIds.mapTo(linkedSetOf(), ::ChartSourceId), displayPath,
    ChartContentRevision(revisionIdentity, revisionSizeBytes, revisionModifiedAtMillis, revisionProviderHint, revisionSha256),
    ChartAssetFacts(
        ChartAssetFormat.valueOf(format), sizeBytes,
        if (west != null && south != null && east != null && north != null) {
            GeoBounds(south = south, west = west, north = north, east = east)
        } else null,
        minZoom, maxZoom, tileCount, tileSize, tileScheme?.let(MapTileScheme::valueOf),
        attribution, ChartFactProvenance.valueOf(attributionProvenance), rasterMimeType,
    ),
    ChartAssetRole.valueOf(role), priority, enabled,
    ChartAssetAccessState.valueOf(accessState), ChartAssetValidationState.valueOf(validationState),
    accessMode?.let(ChartReadAccessMode::valueOf),
    compatibilityWarnings.split(',').filter(String::isNotBlank).mapTo(linkedSetOf(), ChartCompatibilityWarning::valueOf),
)
private fun LegacyChartAssetMapping.toEntity() = LegacyChartMappingEntity(legacyLogicalId, legacyVersionId.orEmpty(), assetId.value)
private fun ChartManagedCopyRelation.toEntity() = ChartManagedCopyRelationEntity(originalAssetId.value, managedAssetId.value)
private fun ChartLayer.toEntity() = ChartLayerEntity(id.value, displayName, visible, opacity, stackOrder, role.name)
private fun ChartLayerEntity.toDomain(sourceIds: List<String>) = ChartLayer(
    ChartLayerId(id), displayName, sourceIds.mapTo(linkedSetOf(), ::ChartSourceId), visible, opacity, stackOrder,
    ChartAssetRole.valueOf(role),
)
private fun ChartMapView.toEntity() = ChartViewEntity(id.value, displayName, baseStyle.name)
private fun ChartViewLayer.toEntity(viewId: ChartViewId) = ChartViewLayerEntity(
    viewId.value, layerId.value, visible, opacity, stackOrder,
)
private fun ChartViewEntity.toDomain(layers: List<ChartViewLayerEntity>) = ChartMapView(
    ChartViewId(id), displayName, ChartBuiltInBaseStyle.valueOf(baseStyle),
    layers.map { ChartViewLayer(ChartLayerId(it.layerId), it.visible, it.opacity, it.stackOrder) },
)

private const val LEGACY_GENERATED_VIEW_ID = "default-view-v1"
private const val LEGACY_GENERATED_VIEW_NAME = "Sailing"
