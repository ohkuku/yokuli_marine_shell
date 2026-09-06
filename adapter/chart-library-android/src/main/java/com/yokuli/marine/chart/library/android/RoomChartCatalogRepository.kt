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
    private val mutableSnapshot = MutableStateFlow(runBlocking(Dispatchers.IO) { readSnapshot() })
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

    override suspend fun asset(id: ChartAssetId): ChartAsset? = ioRead {
        dao.asset(id.value)?.let { it.toDomain(dao.memberships(it.id)) }
    }

    override suspend fun resolveLegacyAsset(legacyLogicalId: String, legacyVersionId: String?): ChartAssetId? = ioRead {
        dao.legacyAssetId(legacyLogicalId, legacyVersionId.orEmpty())?.let(::ChartAssetId)
    }

    override suspend fun transact(transaction: ChartCatalogTransaction): ChartCatalogCommitResult = writer.withLock {
        if (closed.get()) return@withLock ChartCatalogCommitResult.Failed(ChartCatalogFailure.CLOSED)
        try {
            val resolved = linkedMapOf<ChartAssetId, ChartAssetId>()
            val outcome = withContext(Dispatchers.IO) {
                database.withTransaction {
                    val metadata = dao.metadata() ?: ChartCatalogMetadataEntity(revision = 0L, lastTransactionId = null)
                    if (dao.appliedTransactionRevision(transaction.transactionId) != null) {
                        return@withTransaction CommitOutcome(readSnapshot(), resolved)
                    }
                    if (transaction.expectedRevision != null && transaction.expectedRevision != metadata.revision) {
                        return@withTransaction CommitOutcome(conflict = metadata.revision)
                    }
                    transaction.mutations.forEach { mutation -> applyMutation(mutation, resolved) }
                    val newRevision = metadata.revision + 1L
                    dao.putMetadata(
                        ChartCatalogMetadataEntity(
                            revision = newRevision,
                            lastTransactionId = transaction.transactionId,
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
    facts.minZoom, facts.maxZoom, facts.tileCount, facts.tileSize, facts.tileScheme?.name,
    facts.attribution, facts.attributionProvenance.name,
    role.name, priority, enabled, access.name, validation.name,
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
        attribution, ChartFactProvenance.valueOf(attributionProvenance),
    ),
    ChartAssetRole.valueOf(role), priority, enabled,
    ChartAssetAccessState.valueOf(accessState), ChartAssetValidationState.valueOf(validationState),
)
private fun LegacyChartAssetMapping.toEntity() = LegacyChartMappingEntity(legacyLogicalId, legacyVersionId.orEmpty(), assetId.value)
