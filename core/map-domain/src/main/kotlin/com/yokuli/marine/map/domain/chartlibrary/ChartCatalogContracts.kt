package com.yokuli.marine.map.domain.chartlibrary

import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.MapTileScheme
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

enum class ChartLibrarySourceKind { TREE, SINGLE_DOCUMENT, MANAGED }
enum class ChartAssetRole { BASE, OVERLAY }
enum class ChartGrantState { UNCHECKED, GRANTED, DENIED, REVOKED, NOT_REQUIRED }
enum class ChartScanStatus { NEVER_SCANNED, RUNNING, COMPLETE, PARTIAL, FAILED, CANCELLED }
enum class ChartAssetFormat { RASTER_MBTILES, UNKNOWN }
enum class ChartAssetAccessState {
    UNCHECKED,
    READABLE,
    PERMISSION_LOST,
    SOURCE_OFFLINE,
    MISSING,
    CHANGED,
    PENDING,
    DIRECT_READ_UNSUPPORTED,
}
enum class ChartAssetValidationState {
    DISCOVERED,
    BASIC_READABLE,
    FULL_VERIFIED,
    INVALID,
    UNSUPPORTED_FORMAT,
    CANCELLED_OR_INTERRUPTED,
}
enum class ChartFactProvenance { EMBEDDED, DERIVED, USER_DECLARED, UNKNOWN }

data class ChartSourceScanState(
    val generation: Long = 0L,
    val status: ChartScanStatus = ChartScanStatus.NEVER_SCANNED,
    val lastSuccessfulGeneration: Long? = null,
    val discoveredCount: Long = 0L,
    val issueCount: Int = 0,
) {
    init {
        require(generation >= 0L && discoveredCount >= 0L && issueCount >= 0)
        require(lastSuccessfulGeneration == null || lastSuccessfulGeneration in 1..generation)
    }
}

data class ChartLibrarySource(
    val id: ChartSourceId,
    val kind: ChartLibrarySourceKind,
    val locator: ChartOpaqueLocator,
    val displayName: String,
    val enabled: Boolean = true,
    val recursive: Boolean = kind == ChartLibrarySourceKind.TREE,
    val defaultRole: ChartAssetRole = ChartAssetRole.BASE,
    val grantState: ChartGrantState = ChartGrantState.UNCHECKED,
    val scan: ChartSourceScanState = ChartSourceScanState(),
) {
    init {
        require(runCatching { UUID.fromString(id.value) }.isSuccess) { "Source IDs must be UUIDs" }
        require(displayName.isNotBlank() && displayName.length <= 256)
        require(kind == ChartLibrarySourceKind.TREE || !recursive)
        require(kind != ChartLibrarySourceKind.MANAGED || grantState == ChartGrantState.NOT_REQUIRED)
    }
}

data class ChartDocumentIdentity(val authority: String, val documentId: String) {
    init {
        require(authority.isNotBlank() && authority.length <= 256)
        require(documentId.isNotBlank() && documentId.length <= 1024)
        require('\u0000' !in authority && '\u0000' !in documentId)
    }
}

data class ChartAssetFacts(
    val format: ChartAssetFormat = ChartAssetFormat.UNKNOWN,
    val sizeBytes: Long? = null,
    val bounds: GeoBounds? = null,
    val minZoom: Int? = null,
    val maxZoom: Int? = null,
    val tileCount: Long? = null,
    val tileSize: Int? = null,
    val tileScheme: MapTileScheme? = null,
    val attribution: String? = null,
    val attributionProvenance: ChartFactProvenance = ChartFactProvenance.UNKNOWN,
    val rasterMimeType: String? = null,
) {
    init {
        require(sizeBytes == null || sizeBytes >= 0L)
        require(tileCount == null || tileCount >= 0L)
        require(minZoom == null || minZoom in 0..24)
        require(maxZoom == null || maxZoom in 0..24)
        require(minZoom == null || maxZoom == null || minZoom <= maxZoom)
        require(tileSize == null || tileSize == 256 || tileSize == 512)
        require(rasterMimeType == null || rasterMimeType in SUPPORTED_RASTER_MIME_TYPES)
        require(attribution == null || attribution.length <= 2_048)
    }
}

data class ChartAsset(
    val id: ChartAssetId,
    val documentIdentity: ChartDocumentIdentity,
    val locator: ChartOpaqueLocator,
    val memberships: Set<ChartSourceId>,
    val displayPath: String,
    val revision: ChartContentRevision,
    val facts: ChartAssetFacts = ChartAssetFacts(),
    val role: ChartAssetRole = ChartAssetRole.BASE,
    val priority: Int = 0,
    val enabled: Boolean = true,
    val access: ChartAssetAccessState = ChartAssetAccessState.UNCHECKED,
    val validation: ChartAssetValidationState = ChartAssetValidationState.DISCOVERED,
) {
    init {
        require(runCatching { UUID.fromString(id.value) }.isSuccess) { "External asset IDs must be UUIDs" }
        require(memberships.isNotEmpty())
        require(displayPath.isNotBlank() && displayPath.length <= 1_024 && '\u0000' !in displayPath)
        require(priority in -10_000..10_000)
        require(validation != ChartAssetValidationState.FULL_VERIFIED || revision.contentSha256 != null)
    }
}

data class LegacyChartAssetMapping(
    val legacyLogicalId: String,
    val legacyVersionId: String?,
    val assetId: ChartAssetId,
) {
    init {
        require(legacyLogicalId.isNotBlank() && legacyLogicalId.length <= 128)
        require(legacyVersionId == null || legacyVersionId.length <= 128)
    }
}

/** An explicit user-created managed copy remains related to its external original. */
data class ChartManagedCopyRelation(
    val originalAssetId: ChartAssetId,
    val managedAssetId: ChartAssetId,
) {
    init { require(originalAssetId != managedAssetId) }
}

data class ChartCatalogSnapshot(
    val revision: Long = 0L,
    val sourceCount: Int = 0,
    val assetCount: Int = 0,
    val issueCount: Int = 0,
    val lastTransactionId: String? = null,
) {
    init { require(revision >= 0L && sourceCount >= 0 && assetCount >= 0 && issueCount >= 0) }
}

data class ChartCatalogPage<T>(val items: List<T>, val offset: Int, val limit: Int, val total: Int) {
    init { require(offset >= 0 && limit in 1..MAX_CATALOG_PAGE_SIZE && total >= items.size) }
}

data class ChartAssetQuery(
    val sourceId: ChartSourceId? = null,
    val text: String = "",
    val enabledOnly: Boolean = false,
    val access: Set<ChartAssetAccessState> = emptySet(),
    val validation: Set<ChartAssetValidationState> = emptySet(),
) {
    init { require(text.length <= 256) }
}

interface ChartCatalogReadPort {
    val snapshot: StateFlow<ChartCatalogSnapshot>
    suspend fun sources(offset: Int = 0, limit: Int = DEFAULT_CATALOG_PAGE_SIZE): ChartCatalogPage<ChartLibrarySource>
    suspend fun source(id: ChartSourceId): ChartLibrarySource?
    suspend fun assets(
        query: ChartAssetQuery = ChartAssetQuery(),
        offset: Int = 0,
        limit: Int = DEFAULT_CATALOG_PAGE_SIZE,
    ): ChartCatalogPage<ChartAsset>
    suspend fun asset(id: ChartAssetId): ChartAsset?
    suspend fun resolveLegacyAsset(legacyLogicalId: String, legacyVersionId: String? = null): ChartAssetId?
    suspend fun managedCopyFor(originalAssetId: ChartAssetId): ChartAssetId? = null
    suspend fun originalForManagedCopy(managedAssetId: ChartAssetId): ChartAssetId? = null
}

sealed interface ChartCatalogMutation {
    data class PutSource(val source: ChartLibrarySource) : ChartCatalogMutation
    data class RemoveSource(val sourceId: ChartSourceId) : ChartCatalogMutation
    data class PutAsset(val asset: ChartAsset) : ChartCatalogMutation
    data class RemoveAssetMembership(val assetId: ChartAssetId, val sourceId: ChartSourceId) : ChartCatalogMutation
    data class PutLegacyMapping(val mapping: LegacyChartAssetMapping) : ChartCatalogMutation
    data class PutManagedCopyRelation(val relation: ChartManagedCopyRelation) : ChartCatalogMutation
}

data class ChartCatalogTransaction(
    val transactionId: String,
    val expectedRevision: Long? = null,
    val mutations: List<ChartCatalogMutation>,
) {
    init {
        require(transactionId.isNotBlank() && transactionId.length <= 128)
        require(expectedRevision == null || expectedRevision >= 0L)
        require(mutations.isNotEmpty() && mutations.size <= 1_024)
    }
}

sealed interface ChartCatalogCommitResult {
    data class Committed(
        val snapshot: ChartCatalogSnapshot,
        val resolvedAssetIds: Map<ChartAssetId, ChartAssetId> = emptyMap(),
    ) : ChartCatalogCommitResult
    data class Conflict(val actualRevision: Long) : ChartCatalogCommitResult
    data class Failed(val reason: ChartCatalogFailure) : ChartCatalogCommitResult
}

enum class ChartCatalogFailure { PERSISTENCE, INVALID_REFERENCE, IDENTITY_CONFLICT, CLOSED }

fun interface ChartLibraryCommandPort {
    suspend fun transact(transaction: ChartCatalogTransaction): ChartCatalogCommitResult
}

const val DEFAULT_CATALOG_PAGE_SIZE = 100
const val MAX_CATALOG_PAGE_SIZE = 100
