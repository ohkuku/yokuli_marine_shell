package com.yokuli.marine.map.domain.chartlibrary

import kotlinx.coroutines.flow.StateFlow

data class ChartLibraryRuntimeMetrics(
    val activeReadSessions: Int = 0,
    val readSessionHighWater: Int = 0,
    val queuedBasicChecks: Int = 0,
    val rejectedBasicChecks: Long = 0L,
) {
    init {
        require(activeReadSessions >= 0 && readSessionHighWater >= activeReadSessions)
        require(queuedBasicChecks >= 0 && rejectedBasicChecks >= 0L)
    }
}

enum class ChartManagedCopyCapability { UNAVAILABLE, AVAILABLE }
enum class ChartManagedCopyStatus {
    QUEUED,
    COPYING,
    VERIFYING,
    PUBLISHING,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
}

data class ChartManagedCopyProgress(
    val sourceAssetId: ChartAssetId,
    val status: ChartManagedCopyStatus,
    val copiedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val managedAssetId: ChartAssetId? = null,
    val failure: ChartManagedCopyFailure? = null,
) {
    init {
        require(copiedBytes >= 0L)
        require(totalBytes == null || totalBytes >= copiedBytes)
        require(status != ChartManagedCopyStatus.COMPLETED || managedAssetId != null)
        require(status == ChartManagedCopyStatus.FAILED || failure == null)
    }
}

data class ChartLibraryStorageSnapshot(
    /** Null means the runtime cannot currently measure the corresponding store. */
    val managedCopyBytes: Long? = null,
    val catalogBytes: Long? = null,
    val cacheBytes: Long? = null,
    val availableCopyBytes: Long? = null,
    val copyCapability: ChartManagedCopyCapability = ChartManagedCopyCapability.UNAVAILABLE,
    val copyJobs: Map<ChartAssetId, ChartManagedCopyProgress> = emptyMap(),
) {
    init {
        require(managedCopyBytes == null || managedCopyBytes >= 0L)
        require(catalogBytes == null || catalogBytes >= 0L)
        require(cacheBytes == null || cacheBytes >= 0L)
        require(availableCopyBytes == null || availableCopyBytes >= 0L)
        require(copyJobs.size <= MAX_COPY_JOB_HISTORY)
        require(copyJobs.all { (assetId, job) -> assetId == job.sourceAssetId })
    }

    companion object {
        val EMPTY = ChartLibraryStorageSnapshot()
    }
}

enum class ChartManagedCopyFailure { NOT_AVAILABLE, ASSET_NOT_FOUND, INSUFFICIENT_SPACE, ACTIVE_LEASE, PERSISTENCE }

sealed interface ChartManagedCopyCommandResult {
    data class Accepted(val sourceAssetId: ChartAssetId) : ChartManagedCopyCommandResult
    data class Rejected(val failure: ChartManagedCopyFailure) : ChartManagedCopyCommandResult
}

data class ChartManagedDeleteImpact(
    val assetId: ChartAssetId,
    /** True when deleting can invalidate a current or restored display plan; confirmation stays conservative. */
    val mayAffectDisplay: Boolean,
)

sealed interface ChartManagedDeleteResult {
    data class ConfirmationRequired(val impact: ChartManagedDeleteImpact) : ChartManagedDeleteResult
    data object Deleted : ChartManagedDeleteResult
    data class Rejected(val failure: ChartManagedCopyFailure) : ChartManagedDeleteResult
}

interface ChartManagedCopyPort {
    val storage: StateFlow<ChartLibraryStorageSnapshot>
    suspend fun saveManagedCopy(assetId: ChartAssetId): ChartManagedCopyCommandResult
    fun cancelManagedCopy(assetId: ChartAssetId)
    suspend fun deleteManagedCopy(assetId: ChartAssetId, confirmed: Boolean): ChartManagedDeleteResult =
        ChartManagedDeleteResult.Rejected(ChartManagedCopyFailure.NOT_AVAILABLE)
}

interface ChartLibraryRuntimePort :
    ChartCatalogReadPort,
    ChartLibraryCommandPort,
    ChartSourceCommandPort,
    ChartValidationCommandPort,
    ChartResourceAccessPort,
    ChartManagedCopyPort {
    val metrics: StateFlow<ChartLibraryRuntimeMetrics>
}

const val MAX_COPY_JOB_HISTORY = 64
