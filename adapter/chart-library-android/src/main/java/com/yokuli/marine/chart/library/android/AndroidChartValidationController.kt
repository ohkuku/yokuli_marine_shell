package com.yokuli.marine.chart.library.android

import com.yokuli.marine.map.domain.chartlibrary.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class AndroidChartValidationController(
    private val catalog: RoomChartCatalogRepository,
    access: ChartResourceAccessPort,
    private val revisionProbe: ChartRevisionProbe,
) : ChartValidationCommandPort {
    private val basic = ChartBasicInspector(access)
    private val full = ChartFullVerifier(access, revisionProbe)
    private val budget = Semaphore(2)
    private val assetLocks = ConcurrentHashMap<String, Mutex>()
    private val epochs = ConcurrentHashMap<String, AtomicLong>()
    private val mutableState = MutableStateFlow(ChartValidationSnapshot())
    override val state: StateFlow<ChartValidationSnapshot> = mutableState.asStateFlow()

    override suspend fun inspectBasic(assetId: ChartAssetId): ChartValidationCommandResult = runLocked(assetId, ChartValidationJobKind.BASIC) { asset, generation, epoch ->
        when (val result = basic.inspect(asset, generation)) {
            is ChartBasicInspectionResult.Readable -> publishIfCurrent(asset, epoch) {
                it.copy(
                    facts = result.inspection.facts.copy(sizeBytes = it.facts.sizeBytes),
                    access = ChartAssetAccessState.READABLE,
                    validation = ChartAssetValidationState.BASIC_READABLE,
                )
            }
            is ChartBasicInspectionResult.Rejected -> reject(assetId, result.issue)
        }
    }

    override suspend fun verifyFull(assetId: ChartAssetId): ChartValidationCommandResult = runLocked(assetId, ChartValidationJobKind.FULL) { asset, generation, epoch ->
        when (val result = full.verify(asset, generation, { epochChanged(assetId, epoch) }) { progress ->
            updateJob(ChartValidationJob(assetId, ChartValidationJobKind.FULL, ChartValidationJobStatus.RUNNING, progress))
        }) {
            is ChartFullVerificationResult.Verified -> publishIfCurrent(asset, epoch) {
                it.copy(
                    revision = result.revision,
                    facts = result.facts.copy(sizeBytes = it.facts.sizeBytes),
                    access = ChartAssetAccessState.READABLE,
                    validation = ChartAssetValidationState.FULL_VERIFIED,
                )
            }
            is ChartFullVerificationResult.Rejected -> reject(assetId, result.issue)
            ChartFullVerificationResult.Cancelled -> cancelled(assetId)
        }
    }

    override fun cancel(assetId: ChartAssetId) {
        epochs.computeIfAbsent(assetId.value) { AtomicLong() }.incrementAndGet()
        updateJob(ChartValidationJob(assetId, state.value.jobs[assetId]?.kind ?: ChartValidationJobKind.BASIC, ChartValidationJobStatus.CANCELLED))
    }

    private suspend fun runLocked(
        assetId: ChartAssetId,
        kind: ChartValidationJobKind,
        block: suspend (ChartAsset, Long, Long) -> ChartValidationCommandResult,
    ): ChartValidationCommandResult = assetLocks.computeIfAbsent(assetId.value) { Mutex() }.withLock {
        budget.withPermit {
            val asset = catalog.asset(assetId) ?: return@withPermit reject(assetId, ChartValidationIssue.OPEN_FAILED)
            val generation = asset.memberships.mapNotNull { catalog.source(it)?.scan?.generation }.maxOrNull()
                ?.takeIf { it > 0L } ?: return@withPermit reject(assetId, ChartValidationIssue.OPEN_FAILED)
            val epoch = epochs.computeIfAbsent(assetId.value) { AtomicLong() }.incrementAndGet()
            updateJob(ChartValidationJob(assetId, kind, ChartValidationJobStatus.RUNNING))
            block(asset, generation, epoch)
        }
    }

    private suspend fun publishIfCurrent(
        original: ChartAsset,
        epoch: Long,
        transform: (ChartAsset) -> ChartAsset,
    ): ChartValidationCommandResult {
        if (epochChanged(original.id, epoch)) return cancelled(original.id)
        val current = catalog.asset(original.id)
            ?: return reject(original.id, ChartValidationIssue.REVISION_CHANGED)
        if (current.revision.cacheKey != original.revision.cacheKey) {
            return reject(original.id, ChartValidationIssue.REVISION_CHANGED)
        }
        val updated = transform(current)
        return when (catalog.transact(ChartCatalogTransaction(
            "validation:${original.id.value}:${UUID.randomUUID()}", mutations = listOf(ChartCatalogMutation.PutAsset(updated)),
        ))) {
            is ChartCatalogCommitResult.Committed -> {
                updateJob(ChartValidationJob(original.id, state.value.jobs.getValue(original.id).kind, ChartValidationJobStatus.COMPLETED))
                ChartValidationCommandResult.Published(updated)
            }
            else -> reject(original.id, ChartValidationIssue.READ_FAILED)
        }
    }

    private fun epochChanged(assetId: ChartAssetId, expected: Long): Boolean = epochs[assetId.value]?.get() != expected

    private fun reject(assetId: ChartAssetId, issue: ChartValidationIssue): ChartValidationCommandResult.Rejected {
        updateJob(ChartValidationJob(assetId, state.value.jobs[assetId]?.kind ?: ChartValidationJobKind.BASIC, ChartValidationJobStatus.FAILED, issue = issue))
        return ChartValidationCommandResult.Rejected(issue)
    }

    private fun cancelled(assetId: ChartAssetId): ChartValidationCommandResult.Cancelled {
        updateJob(ChartValidationJob(assetId, state.value.jobs[assetId]?.kind ?: ChartValidationJobKind.BASIC, ChartValidationJobStatus.CANCELLED))
        return ChartValidationCommandResult.Cancelled
    }

    private fun updateJob(job: ChartValidationJob) {
        mutableState.update { snapshot -> ChartValidationSnapshot(
            (snapshot.jobs + (job.assetId to job)).entries.toList()
                .takeLast(MAX_JOBS)
                .associate { entry -> entry.toPair() },
        ) }
    }

    companion object { private const val MAX_JOBS = 256 }
}
