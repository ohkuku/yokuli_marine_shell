package com.yokuli.marine.map.domain.chartlibrary

import java.util.UUID

@JvmInline
value class ChartLibraryOperationId(val value: String) {
    init { require(value.isNotBlank() && value.length <= 128) }
}

enum class ChartPickerKind { TREE, SINGLE_DOCUMENT }

sealed interface ChartLibraryPickerEffect {
    val operationId: ChartLibraryOperationId
    data class OpenTree(override val operationId: ChartLibraryOperationId) : ChartLibraryPickerEffect
    data class OpenDocument(override val operationId: ChartLibraryOperationId) : ChartLibraryPickerEffect
}

data class ChartPickerSelection(
    val operationId: ChartLibraryOperationId,
    val kind: ChartPickerKind,
    val locator: ChartOpaqueLocator,
    val displayName: String,
    val persistableReadGranted: Boolean,
) {
    init { require(displayName.isNotBlank() && displayName.length <= 256) }
}

data class ChartDiscoveredDocument(
    val identity: ChartDocumentIdentity,
    val locator: ChartOpaqueLocator,
    val displayPath: String,
    val sizeBytes: Long?,
    val modifiedAtMillis: Long?,
    val providerRevisionHint: String? = null,
    val mimeType: String? = null,
    val pending: Boolean = false,
) {
    init {
        require(displayPath.isNotBlank() && displayPath.length <= 1_024)
        require(sizeBytes == null || sizeBytes >= 0L)
        require(modifiedAtMillis == null || modifiedAtMillis >= 0L)
        require(providerRevisionHint == null || providerRevisionHint.length <= 512)
        require(mimeType == null || mimeType.length <= 256)
    }

    val revision: ChartContentRevision = ChartContentRevision(
        identity = "${identity.authority}:${identity.documentId}",
        observedSizeBytes = sizeBytes,
        observedModifiedAtMillis = modifiedAtMillis,
        providerRevisionHint = providerRevisionHint,
    )
}

data class ChartEnumerationIssue(val displayPath: String?, val kind: ChartEnumerationIssueKind) {
    init { require(displayPath == null || displayPath.length <= 1_024) }
}
enum class ChartEnumerationIssueKind { QUERY_FAILED, PERMISSION_LOST, LIMIT_REACHED, DEPTH_LIMIT, CANCELLED }

sealed interface ChartEnumerationResult {
    val documents: List<ChartDiscoveredDocument>
    data class Complete(override val documents: List<ChartDiscoveredDocument>) : ChartEnumerationResult
    data class Partial(
        override val documents: List<ChartDiscoveredDocument>,
        val issues: List<ChartEnumerationIssue>,
    ) : ChartEnumerationResult
    data class Failed(val issue: ChartEnumerationIssue) : ChartEnumerationResult {
        override val documents: List<ChartDiscoveredDocument> = emptyList()
    }
    data class Cancelled(override val documents: List<ChartDiscoveredDocument>) : ChartEnumerationResult
}

fun interface ChartDocumentEnumerationPort {
    suspend fun enumerate(
        source: ChartLibrarySource,
        shouldCancel: () -> Boolean,
    ): ChartEnumerationResult
}

data class ChartScanPlan(
    val source: ChartLibrarySource,
    val assetsToPut: List<ChartAsset>,
    val missingAssetIds: Set<ChartAssetId>,
)

fun interface ChartAssetIdFactory {
    fun next(): ChartAssetId
}

object RandomChartAssetIdFactory : ChartAssetIdFactory {
    override fun next() = ChartAssetId(UUID.randomUUID().toString())
}

class ChartSourceScanPlanner(private val ids: ChartAssetIdFactory = RandomChartAssetIdFactory) {
    fun plan(
        source: ChartLibrarySource,
        generation: Long,
        enumeration: ChartEnumerationResult,
        existing: List<ChartAsset>,
    ): ChartScanPlan {
        require(generation > source.scan.generation)
        val existingByIdentity = existing.associateBy(ChartAsset::documentIdentity)
        val seen = linkedSetOf<ChartAssetId>()
        val discoveredPuts = enumeration.documents.distinctBy(ChartDiscoveredDocument::identity).map { document ->
            val prior = existingByIdentity[document.identity]
            val changed = prior != null && prior.revision.cacheKey != document.revision.cacheKey
            val asset = if (prior == null) {
                ChartAsset(
                    id = ids.next(),
                    documentIdentity = document.identity,
                    locator = document.locator,
                    memberships = setOf(source.id),
                    displayPath = document.displayPath,
                    revision = document.revision,
                    facts = ChartAssetFacts(sizeBytes = document.sizeBytes),
                    role = source.defaultRole,
                    access = if (document.pending) ChartAssetAccessState.UNCHECKED else ChartAssetAccessState.UNCHECKED,
                    validation = ChartAssetValidationState.DISCOVERED,
                )
            } else {
                prior.copy(
                    locator = document.locator,
                    memberships = prior.memberships + source.id,
                    displayPath = document.displayPath,
                    revision = document.revision,
                    facts = prior.facts.copy(sizeBytes = document.sizeBytes),
                    access = when {
                        document.pending -> ChartAssetAccessState.UNCHECKED
                        changed -> ChartAssetAccessState.CHANGED
                        prior.access == ChartAssetAccessState.MISSING -> ChartAssetAccessState.UNCHECKED
                        else -> prior.access
                    },
                    validation = if (changed) ChartAssetValidationState.DISCOVERED else prior.validation,
                )
            }
            seen += asset.id
            asset
        }
        val permissionLost = (enumeration as? ChartEnumerationResult.Failed)?.issue?.kind ==
            ChartEnumerationIssueKind.PERMISSION_LOST
        val put = if (permissionLost) {
            discoveredPuts + existing.filter { source.id in it.memberships && it.id !in seen }
                .map { it.copy(access = ChartAssetAccessState.PERMISSION_LOST) }
        } else discoveredPuts
        val missing = if (enumeration is ChartEnumerationResult.Complete) {
            existing.filter { source.id in it.memberships && it.id !in seen }.mapTo(linkedSetOf(), ChartAsset::id)
        } else emptySet()
        val status = when (enumeration) {
            is ChartEnumerationResult.Complete -> ChartScanStatus.COMPLETE
            is ChartEnumerationResult.Partial -> ChartScanStatus.PARTIAL
            is ChartEnumerationResult.Failed -> ChartScanStatus.FAILED
            is ChartEnumerationResult.Cancelled -> ChartScanStatus.CANCELLED
        }
        val issueCount = when (enumeration) {
            is ChartEnumerationResult.Partial -> enumeration.issues.size
            is ChartEnumerationResult.Failed -> 1
            is ChartEnumerationResult.Cancelled -> 1
            else -> 0
        }
        return ChartScanPlan(
            source = source.copy(
                grantState = if (permissionLost) ChartGrantState.REVOKED else source.grantState,
                scan = ChartSourceScanState(
                    generation = generation,
                    status = status,
                    lastSuccessfulGeneration = if (status == ChartScanStatus.COMPLETE) generation else source.scan.lastSuccessfulGeneration,
                    discoveredCount = enumeration.documents.size.toLong(),
                    issueCount = issueCount,
                ),
            ),
            assetsToPut = put,
            missingAssetIds = missing,
        )
    }
}

sealed interface ChartSourceCommandResult {
    data class Accepted(val sourceId: ChartSourceId) : ChartSourceCommandResult
    data class ScanPublished(val sourceId: ChartSourceId, val generation: Long, val status: ChartScanStatus) : ChartSourceCommandResult
    data class Rejected(val reason: ChartSourceCommandFailure) : ChartSourceCommandResult
}
enum class ChartSourceCommandFailure { PICKER_CANCELLED, READ_GRANT_MISSING, SOURCE_NOT_FOUND, STALE_OPERATION, PERSISTENCE }

interface ChartSourceCommandPort {
    suspend fun acceptPicker(selection: ChartPickerSelection): ChartSourceCommandResult
    suspend fun refresh(sourceId: ChartSourceId): ChartSourceCommandResult
    suspend fun cancel(sourceId: ChartSourceId): ChartSourceCommandResult
    suspend fun remove(sourceId: ChartSourceId): ChartSourceCommandResult
}
