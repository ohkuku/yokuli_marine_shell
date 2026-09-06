package com.yokuli.marine.chart.library.android

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import com.yokuli.marine.map.domain.chartlibrary.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

interface PersistedChartGrantPort {
    fun takeRead(locator: ChartOpaqueLocator): Boolean
    fun releaseRead(locator: ChartOpaqueLocator): Boolean
}

class AndroidPersistedChartGrantPort(private val resolver: ContentResolver) : PersistedChartGrantPort {
    override fun takeRead(locator: ChartOpaqueLocator): Boolean = runCatching {
        resolver.takePersistableUriPermission(Uri.parse(locator.value), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.isSuccess

    override fun releaseRead(locator: ChartOpaqueLocator): Boolean = runCatching {
        resolver.releasePersistableUriPermission(Uri.parse(locator.value), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.isSuccess
}

class AndroidChartSourceController(
    private val catalog: RoomChartCatalogRepository,
    private val enumerator: ChartDocumentEnumerationPort,
    private val grants: PersistedChartGrantPort,
    private val planner: ChartSourceScanPlanner = ChartSourceScanPlanner(),
) : ChartSourceCommandPort {
    private val workerBudget = Semaphore(2)
    private val sourceLocks = Array(LOCK_STRIPES) { Mutex() }
    private val operationEpoch = ConcurrentHashMap<String, AtomicLong>()

    override suspend fun acceptPicker(selection: ChartPickerSelection): ChartSourceCommandResult {
        if (!selection.persistableReadGranted || !grants.takeRead(selection.locator)) {
            return ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.READ_GRANT_MISSING)
        }
        val existing = allSources().firstOrNull { it.locator == selection.locator }
        if (existing != null) return ChartSourceCommandResult.Accepted(existing.id)
        val id = ChartSourceId(UUID.randomUUID().toString())
        val source = ChartLibrarySource(
            id = id,
            kind = when (selection.kind) {
                ChartPickerKind.TREE -> ChartLibrarySourceKind.TREE
                ChartPickerKind.SINGLE_DOCUMENT -> ChartLibrarySourceKind.SINGLE_DOCUMENT
            },
            locator = selection.locator,
            displayName = selection.displayName,
            recursive = selection.kind == ChartPickerKind.TREE,
            grantState = ChartGrantState.GRANTED,
        )
        return when (catalog.transact(ChartCatalogTransaction("picker:${selection.operationId.value}", mutations = listOf(ChartCatalogMutation.PutSource(source))))) {
            is ChartCatalogCommitResult.Committed -> ChartSourceCommandResult.Accepted(id)
            else -> {
                releaseIfUnused(selection.locator)
                ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.PERSISTENCE)
            }
        }
    }

    override suspend fun repair(
        sourceId: ChartSourceId,
        selection: ChartPickerSelection,
    ): ChartSourceCommandResult = sourceLock(sourceId).withLock {
        val source = catalog.source(sourceId)
            ?: return@withLock ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.SOURCE_NOT_FOUND)
        val expectedPickerKind = when (source.kind) {
            ChartLibrarySourceKind.TREE -> ChartPickerKind.TREE
            ChartLibrarySourceKind.SINGLE_DOCUMENT -> ChartPickerKind.SINGLE_DOCUMENT
            ChartLibrarySourceKind.MANAGED -> return@withLock ChartSourceCommandResult.Rejected(
                ChartSourceCommandFailure.STALE_OPERATION,
            )
        }
        if (selection.kind != expectedPickerKind || !selection.persistableReadGranted || !grants.takeRead(selection.locator)) {
            return@withLock ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.READ_GRANT_MISSING)
        }
        val repaired = source.copy(locator = selection.locator, grantState = ChartGrantState.GRANTED)
        return@withLock when (
            catalog.transact(
                ChartCatalogTransaction(
                    "source-repair:${sourceId.value}:${selection.operationId.value}",
                    mutations = listOf(ChartCatalogMutation.PutSource(repaired)),
                ),
            )
        ) {
            is ChartCatalogCommitResult.Committed -> {
                if (source.locator != repaired.locator) releaseIfUnused(source.locator)
                ChartSourceCommandResult.Accepted(sourceId)
            }
            else -> {
                if (source.locator != repaired.locator) releaseIfUnused(repaired.locator)
                ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.PERSISTENCE)
            }
        }
    }

    override suspend fun refresh(sourceId: ChartSourceId): ChartSourceCommandResult =
        sourceLock(sourceId).withLock {
            workerBudget.withPermit {
                val source = catalog.source(sourceId)
                    ?: return@withPermit ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.SOURCE_NOT_FOUND)
                val epochCounter = operationEpoch.computeIfAbsent(sourceId.value) { AtomicLong(0L) }
                val epoch = epochCounter.incrementAndGet()
                val generation = source.scan.generation + 1L
                try {
                    val running = source.copy(
                        scan = source.scan.copy(generation = generation, status = ChartScanStatus.RUNNING, discoveredCount = 0L, issueCount = 0),
                    )
                    if (catalog.transact(ChartCatalogTransaction("scan-start:${sourceId.value}:$generation", mutations = listOf(ChartCatalogMutation.PutSource(running)))) !is ChartCatalogCommitResult.Committed) {
                        return@withPermit ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.PERSISTENCE)
                    }
                    val existing = allAssets(ChartAssetQuery(sourceId = sourceId))
                    val enumeration = enumerator.enumerate(running) {
                        operationEpoch[sourceId.value]?.get() != epoch
                    }
                    if (operationEpoch[sourceId.value]?.get() != epoch) {
                        return@withPermit ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.STALE_OPERATION)
                    }
                    val plan = planner.plan(source, generation, enumeration, existing)
                    val contentMutations: List<ChartCatalogMutation> = buildList {
                        plan.assetsToPut.forEach { add(ChartCatalogMutation.PutAsset(it)) }
                        existing.filter { it.id in plan.missingAssetIds }.forEach {
                            add(ChartCatalogMutation.PutAsset(it.copy(access = ChartAssetAccessState.MISSING)))
                        }
                        plan.membershipsToRemove.forEach {
                            add(ChartCatalogMutation.RemoveAssetMembership(it, sourceId))
                        }
                    }
                    contentMutations.chunked(MAX_SCAN_MUTATIONS_PER_TRANSACTION).forEachIndexed { index, mutations ->
                        if (operationEpoch[sourceId.value]?.get() != epoch) {
                            return@withPermit ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.STALE_OPERATION)
                        }
                        if (catalog.transact(
                                ChartCatalogTransaction(
                                    "scan-content:${sourceId.value}:$generation:$index",
                                    mutations = mutations,
                                ),
                            ) !is ChartCatalogCommitResult.Committed
                        ) {
                            publishInterruptedScan(sourceId, generation)
                            return@withPermit ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.PERSISTENCE)
                        }
                    }
                    if (operationEpoch[sourceId.value]?.get() != epoch) {
                        return@withPermit ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.STALE_OPERATION)
                    }
                    return@withPermit when (
                        catalog.transact(
                            ChartCatalogTransaction(
                                "scan-finish:${sourceId.value}:$generation",
                                mutations = listOf(ChartCatalogMutation.PutSource(plan.source)),
                            ),
                        )
                    ) {
                        is ChartCatalogCommitResult.Committed ->
                            ChartSourceCommandResult.ScanPublished(sourceId, generation, plan.source.scan.status)
                        else -> {
                            publishInterruptedScan(sourceId, generation)
                            ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.PERSISTENCE)
                        }
                    }
                } finally {
                    operationEpoch.remove(sourceId.value, epochCounter)
                }
            }
        }

    override suspend fun cancel(sourceId: ChartSourceId): ChartSourceCommandResult {
        operationEpoch[sourceId.value]?.incrementAndGet()
        val source = catalog.source(sourceId)
            ?: return ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.SOURCE_NOT_FOUND)
        val cancelled = source.copy(scan = source.scan.copy(status = ChartScanStatus.CANCELLED, issueCount = source.scan.issueCount + 1))
        return when (catalog.transact(ChartCatalogTransaction("scan-cancel:${sourceId.value}:${UUID.randomUUID()}", mutations = listOf(ChartCatalogMutation.PutSource(cancelled))))) {
            is ChartCatalogCommitResult.Committed -> ChartSourceCommandResult.ScanPublished(sourceId, cancelled.scan.generation, ChartScanStatus.CANCELLED)
            else -> ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.PERSISTENCE)
        }
    }

    override suspend fun remove(sourceId: ChartSourceId): ChartSourceCommandResult {
        val source = catalog.source(sourceId)
            ?: return ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.SOURCE_NOT_FOUND)
        operationEpoch[sourceId.value]?.incrementAndGet()
        return when (catalog.transact(ChartCatalogTransaction("source-remove:${sourceId.value}:${UUID.randomUUID()}", mutations = listOf(ChartCatalogMutation.RemoveSource(sourceId))))) {
            is ChartCatalogCommitResult.Committed -> {
                releaseIfUnused(source.locator)
                operationEpoch.remove(sourceId.value)
                ChartSourceCommandResult.Accepted(sourceId)
            }
            else -> ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.PERSISTENCE)
        }
    }

    private suspend fun releaseIfUnused(locator: ChartOpaqueLocator) {
        if (allSources().none { it.locator == locator }) grants.releaseRead(locator)
    }

    private suspend fun publishInterruptedScan(sourceId: ChartSourceId, generation: Long) {
        val current = catalog.source(sourceId)?.takeIf {
            it.scan.generation == generation && it.scan.status == ChartScanStatus.RUNNING
        } ?: return
        catalog.transact(
            ChartCatalogTransaction(
                "scan-interrupted:${sourceId.value}:$generation",
                mutations = listOf(
                    ChartCatalogMutation.PutSource(
                        current.copy(
                            scan = current.scan.copy(
                                status = ChartScanStatus.PARTIAL,
                                issueCount = current.scan.issueCount + 1,
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun sourceLock(sourceId: ChartSourceId): Mutex =
        sourceLocks[(sourceId.value.hashCode() and Int.MAX_VALUE) % sourceLocks.size]

    private suspend fun allSources(): List<ChartLibrarySource> = buildList {
        var offset = 0
        do {
            val page = catalog.sources(offset)
            addAll(page.items)
            offset += page.items.size
        } while (offset < page.total && page.items.isNotEmpty())
    }

    private suspend fun allAssets(query: ChartAssetQuery): List<ChartAsset> = buildList {
        var offset = 0
        do {
            val page = catalog.assets(query, offset)
            addAll(page.items)
            offset += page.items.size
        } while (offset < page.total && page.items.isNotEmpty())
    }

    private companion object {
        const val LOCK_STRIPES = 64
        const val MAX_SCAN_MUTATIONS_PER_TRANSACTION = 1_000
    }
}
