package com.yokuli.marine.map.domain

import java.util.UUID

@JvmInline
value class ChartPackageLogicalId(val value: String) {
    init {
        require(value.isNotBlank() && value.length <= 128)
        require(value.matches(Regex("[A-Za-z0-9._-]+")))
    }
}

@JvmInline
value class ChartPackageVersionId(val value: String) {
    init {
        require(value.matches(Regex("[0-9a-f]{64}")))
    }
}

@JvmInline
value class ChartPackageOperationId(val value: String) {
    init { require(value.isNotBlank()) }
}

sealed interface ChartPackageInspectProgress {
    data class Copying(val completedBytes: Long, val totalBytes: Long?) : ChartPackageInspectProgress
    data class Inspecting(val completedTiles: Long, val totalTiles: Long?) : ChartPackageInspectProgress
}

enum class ChartPackageValidationLevel { FULL_TILE_DECODED }
enum class ChartPackageFactProvenance { EMBEDDED, DERIVED, USER_DECLARED, UNKNOWN }

data class ChartPackageCandidate(
    val stagedImportId: String,
    val suggestedDisplayName: String,
    val suggestedSource: String,
    val suggestedLicense: String,
    val suggestedAttribution: String,
    val suggestedVersion: String,
    val sha256: String,
    val coverage: GeoBounds,
    val minZoom: Int,
    val maxZoom: Int,
    val rasterFormat: String,
    val tileSize: Int = 256,
    val tileScheme: MapTileScheme = MapTileScheme.MBTILES_TMS,
    val logicalId: ChartPackageLogicalId = ChartPackageLogicalId("chart-${sha256.take(24).lowercase()}"),
    val versionId: ChartPackageVersionId = ChartPackageVersionId(sha256.lowercase()),
    val validationLevel: ChartPackageValidationLevel = ChartPackageValidationLevel.FULL_TILE_DECODED,
    val coverageProvenance: ChartPackageFactProvenance = ChartPackageFactProvenance.DERIVED,
)

data class ChartPackageImportRequest(
    val stagedImportId: String,
    val displayName: String,
    val source: String,
    val license: String,
    val attribution: String,
    val version: String,
    val replaceLogicalPackageId: ChartPackageLogicalId? = null,
    val copiedFromCatalogAssetId: String? = null,
)

enum class ChartPackageImportFailure {
    CANNOT_OPEN,
    INVALID_DATABASE,
    CORRUPT_TILE,
    EMPTY_PACKAGE,
    UNSUPPORTED_FORMAT,
    INVALID_METADATA,
    INVALID_TILE_INDEX,
    DUPLICATE_TILE,
    RESOURCE_LIMIT,
    INSUFFICIENT_SPACE,
    REQUIRED_FIELD_MISSING,
    STAGING_EXPIRED,
    INSTALL_FAILED,
    IO_FAILURE,
    PACKAGE_IN_USE,
}

class ChartPackageImportException(
    val reason: ChartPackageImportFailure,
    val technicalDetail: String,
    cause: Throwable? = null,
) : Exception(technicalDetail, cause)

interface ChartPackageRepository {
    suspend fun inspect(sourceUri: String): ChartPackageCandidate
    suspend fun inspect(
        sourceUri: String,
        operationId: ChartPackageOperationId = ChartPackageOperationId(UUID.randomUUID().toString()),
        onProgress: (ChartPackageInspectProgress) -> Unit = {},
    ): ChartPackageCandidate = inspect(sourceUri)
    suspend fun commit(request: ChartPackageImportRequest): ChartPackage
    suspend fun discard(stagedImportId: String)
    suspend fun listInstalled(): List<ChartPackage>
    suspend fun delete(packageId: ChartPackageId)
    suspend fun reconcile() = Unit
    suspend fun rollback(logicalId: ChartPackageLogicalId): ChartPackage? = null
    /**
     * A bounded, read-only view of the app-private package store used only for catalog migration.
     * Implementations reconcile their journal first and never copy payloads to produce it.
     */
    suspend fun managedStoreSnapshot(): ManagedChartPackageStoreSnapshot {
        val installed = listInstalled()
        return ManagedChartPackageStoreSnapshot(
            packages = installed,
            activeByLogicalId = installed.associate { it.logicalId to it.versionId },
            historyByLogicalId = installed.groupBy(ChartPackage::logicalId)
                .mapValues { (_, values) -> values.map(ChartPackage::versionId) },
            storageBytes = null,
        )
    }
    fun acquireLease(packageId: ChartPackageId): ChartPackageLease = ChartPackageLease {}
}

data class ManagedChartPackageStoreSnapshot(
    /** All readable published versions, including inactive history. */
    val packages: List<ChartPackage>,
    val activeByLogicalId: Map<ChartPackageLogicalId, ChartPackageVersionId>,
    val historyByLogicalId: Map<ChartPackageLogicalId, List<ChartPackageVersionId>>,
    val storageBytes: Long?,
) {
    init {
        require(packages.size <= MAX_MANAGED_PACKAGE_VERSIONS)
        require(packages.map(ChartPackage::versionId).distinct().size == packages.size)
        require(storageBytes == null || storageBytes >= 0L)
        val published = packages.mapTo(hashSetOf(), ChartPackage::versionId)
        require(activeByLogicalId.values.all(published::contains))
        require(historyByLogicalId.values.flatten().all(published::contains))
    }
}

fun interface ChartPackageLease : AutoCloseable {
    override fun close()
}

const val MAX_MANAGED_PACKAGE_VERSIONS = 2_048
