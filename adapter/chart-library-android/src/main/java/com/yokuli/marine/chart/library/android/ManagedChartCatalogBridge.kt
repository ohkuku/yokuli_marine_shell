package com.yokuli.marine.chart.library.android

import android.net.Uri
import com.yokuli.marine.map.domain.ChartPackage
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageImportRequest
import com.yokuli.marine.map.domain.ChartPackageInspectProgress
import com.yokuli.marine.map.domain.ChartPackageOperationId
import com.yokuli.marine.map.domain.ChartPackageRepository
import com.yokuli.marine.map.domain.ChartPackageVersionId
import com.yokuli.marine.map.domain.ManagedChartPackageStoreSnapshot
import com.yokuli.marine.map.domain.chartlibrary.ChartAsset
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetAccessState
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetFacts
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetFormat
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetQuery
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetRole
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetValidationState
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogCommitResult
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogMutation
import com.yokuli.marine.map.domain.chartlibrary.ChartCatalogTransaction
import com.yokuli.marine.map.domain.chartlibrary.ChartContentRevision
import com.yokuli.marine.map.domain.chartlibrary.ChartDocumentIdentity
import com.yokuli.marine.map.domain.chartlibrary.ChartFactProvenance
import com.yokuli.marine.map.domain.chartlibrary.ChartGrantState
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySource
import com.yokuli.marine.map.domain.chartlibrary.ChartLibrarySourceKind
import com.yokuli.marine.map.domain.chartlibrary.LegacyChartAssetMapping
import com.yokuli.marine.map.domain.chartlibrary.ChartManagedCopyRelation
import com.yokuli.marine.map.domain.chartlibrary.ChartOpaqueLocator
import com.yokuli.marine.map.domain.chartlibrary.ChartReadAccessMode
import com.yokuli.marine.map.domain.chartlibrary.ChartScanStatus
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceId
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceScanState
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Converges the already-transactional app-private package store into the new catalog. It never
 * reads package payloads and is intentionally safe to rerun after any process boundary.
 */
internal class ManagedChartCatalogBridge(
    private val catalog: RoomChartCatalogRepository,
    private val repository: ChartPackageRepository,
    private val managedRoot: File,
    private val migrationCheckpoint: (ManagedCatalogMigrationCheckpoint) -> Unit = {},
) {
    fun hasSpaceFor(knownBytes: Long?): Boolean = knownBytes == null ||
        managedRoot.usableSpace >= knownBytes + MIN_FREE_SPACE_BYTES

    fun availableSpaceBytes(): Long = (managedRoot.usableSpace - MIN_FREE_SPACE_BYTES).coerceAtLeast(0L)

    suspend fun synchronize(): ManagedChartPackageStoreSnapshot {
        val store = repository.managedStoreSnapshot()
        val current = catalog.source(MANAGED_SOURCE_ID)
        val currentAssets = readManagedAssets()
        val expectedIds = store.packages.mapTo(hashSetOf()) { it.managedAssetId() }
        if (
            current?.scan?.status == ChartScanStatus.COMPLETE &&
            current.scan.discoveredCount == store.packages.size.toLong() &&
            currentAssets.mapTo(hashSetOf(), ChartAsset::id) == expectedIds
        ) return store

        val generation = current?.scan?.generation?.takeIf { current.scan.status == ChartScanStatus.RUNNING }
            ?: ((current?.scan?.generation ?: 0L) + 1L)
        val source = ChartLibrarySource(
            id = MANAGED_SOURCE_ID,
            kind = ChartLibrarySourceKind.MANAGED,
            locator = ChartOpaqueLocator("yokuli-managed://map-packages"),
            displayName = "Yokuli managed copies",
            enabled = current?.enabled ?: true,
            recursive = false,
            defaultRole = ChartAssetRole.BASE,
            grantState = ChartGrantState.NOT_REQUIRED,
            scan = ChartSourceScanState(
                generation = generation,
                status = ChartScanStatus.RUNNING,
                lastSuccessfulGeneration = current?.scan?.lastSuccessfulGeneration,
                discoveredCount = 0L,
                issueCount = 0,
            ),
        )
        requireCommitted(
            ChartCatalogTransaction(
                transactionId = "managed-source:${store.signature().take(48)}",
                mutations = listOf(ChartCatalogMutation.PutSource(source)),
            ),
        )
        migrationCheckpoint(ManagedCatalogMigrationCheckpoint.AFTER_SOURCE_PREPARED)

        store.packages.chunked(PACKAGE_BATCH_SIZE).forEachIndexed { index, batch ->
            val mutations = buildList {
                batch.forEach { value ->
                    val asset = value.toManagedAsset(source.id, generation)
                    add(ChartCatalogMutation.PutAsset(asset))
                    add(ChartCatalogMutation.PutLegacyMapping(value.legacyMapping(asset.id, value.versionId.value)))
                    // Old MapSession versions stored ChartPackageId as the lookup key, not logicalId.
                    add(ChartCatalogMutation.PutLegacyMapping(LegacyChartAssetMapping(value.id.value, null, asset.id)))
                    if (store.activeByLogicalId[value.logicalId] == value.versionId) {
                        add(ChartCatalogMutation.PutLegacyMapping(value.legacyMapping(asset.id, null)))
                    }
                }
            }
            requireCommitted(
                ChartCatalogTransaction(
                    transactionId = "managed-packages:${store.signature().take(40)}:$index",
                    mutations = mutations,
                ),
            )
        }
        migrationCheckpoint(ManagedCatalogMigrationCheckpoint.AFTER_PACKAGES_REGISTERED)
        val recoveredRelations = mutableListOf<ChartManagedCopyRelation>()
        store.packages.forEach { value ->
            val originalId = value.copiedFromCatalogAssetId
                ?.let { raw -> runCatching { ChartAssetId(raw) }.getOrNull() }
            if (originalId != null && catalog.asset(originalId) != null) {
                recoveredRelations += ChartManagedCopyRelation(originalId, value.managedAssetId())
            }
        }
        recoveredRelations.chunked(STALE_BATCH_SIZE).forEachIndexed { index, batch ->
            requireCommitted(
                ChartCatalogTransaction(
                    transactionId = "managed-relations:${store.signature().take(40)}:$index",
                    mutations = batch.map { ChartCatalogMutation.PutManagedCopyRelation(it) },
                ),
            )
        }
        migrationCheckpoint(ManagedCatalogMigrationCheckpoint.AFTER_COPY_RELATIONS)
        store.activeByLogicalId.entries.chunked(STALE_BATCH_SIZE).forEachIndexed { index, batch ->
            val packageByVersion = store.packages.associateBy(ChartPackage::versionId)
            val activeMappings = batch.mapNotNull { (logicalId, versionId) ->
                packageByVersion[versionId]?.let { value ->
                    ChartCatalogMutation.PutLegacyMapping(LegacyChartAssetMapping(logicalId.value, null, value.managedAssetId()))
                }
            }
            if (activeMappings.isNotEmpty()) requireCommitted(
                ChartCatalogTransaction(
                    transactionId = "managed-active:${store.signature().take(40)}:$index",
                    mutations = activeMappings,
                ),
            )
        }
        migrationCheckpoint(ManagedCatalogMigrationCheckpoint.AFTER_ACTIVE_MAPPINGS)

        val stale = currentAssets.filter { it.id !in expectedIds }
        stale.chunked(STALE_BATCH_SIZE).forEachIndexed { index, batch ->
            requireCommitted(
                ChartCatalogTransaction(
                    transactionId = "managed-prune:${store.signature().take(40)}:$index",
                    mutations = batch.map {
                        ChartCatalogMutation.RemoveAssetMembership(it.id, MANAGED_SOURCE_ID)
                    },
                ),
            )
        }
        migrationCheckpoint(ManagedCatalogMigrationCheckpoint.AFTER_STALE_MEMBERSHIPS_PRUNED)
        requireCommitted(
            ChartCatalogTransaction(
                transactionId = "managed-complete:${store.signature().take(40)}",
                mutations = listOf(
                    ChartCatalogMutation.PutSource(
                        source.copy(
                            scan = ChartSourceScanState(
                                generation = generation,
                                status = ChartScanStatus.COMPLETE,
                                lastSuccessfulGeneration = generation,
                                discoveredCount = store.packages.size.toLong(),
                                issueCount = 0,
                            ),
                        ),
                    ),
                ),
            ),
        )
        migrationCheckpoint(ManagedCatalogMigrationCheckpoint.AFTER_COMPLETE_PUBLISHED)
        return store
    }

    suspend fun copy(
        original: ChartAsset,
        operationId: ChartPackageOperationId,
        onProgress: (ChartPackageInspectProgress) -> Unit,
        onPublishing: () -> Unit,
    ): ChartAssetId {
        val candidate = repository.inspect(original.locator.value, operationId, onProgress)
        onPublishing()
        val installed = repository.commit(
            ChartPackageImportRequest(
                stagedImportId = candidate.stagedImportId,
                displayName = original.displayPath.substringAfterLast('/').ifBlank { candidate.suggestedDisplayName },
                source = candidate.suggestedSource,
                license = candidate.suggestedLicense,
                attribution = original.facts.attribution ?: candidate.suggestedAttribution,
                version = candidate.suggestedVersion,
                copiedFromCatalogAssetId = original.id.value,
            ),
        )
        synchronize()
        val managedId = installed.managedAssetId()
        requireCommitted(
            ChartCatalogTransaction(
                transactionId = "managed-relation:${original.id.value}:${installed.versionId.value}".take(128),
                mutations = listOf(
                    ChartCatalogMutation.PutManagedCopyRelation(ChartManagedCopyRelation(original.id, managedId)),
                ),
            ),
        )
        check(catalog.managedCopyFor(original.id) == managedId) { "Managed copy relationship was not recovered" }
        return managedId
    }

    suspend fun delete(asset: ChartAsset) {
        val version = asset.managedVersion()
        repository.delete(ChartPackageId(version.value))
        val result = catalog.transact(
            ChartCatalogTransaction(
                transactionId = "managed-delete:${version.value}".take(128),
                mutations = listOf(ChartCatalogMutation.RemoveAssetMembership(asset.id, MANAGED_SOURCE_ID)),
            ),
        )
        if (result !is ChartCatalogCommitResult.Committed) {
            // Physical deletion already happened. Immediate convergence prevents a stale readable claim.
            synchronize()
            error("Managed catalog deletion did not commit: $result")
        }
        synchronize()
    }

    fun managedFile(version: ChartPackageVersionId): File =
        File(managedRoot, "package-${version.value}/map.mbtiles")

    private suspend fun readManagedAssets(): List<ChartAsset> = buildList {
        var offset = 0
        do {
            val page = catalog.assets(ChartAssetQuery(sourceId = MANAGED_SOURCE_ID), offset)
            addAll(page.items)
            offset += page.items.size
        } while (page.items.isNotEmpty() && offset < page.total && size <= MAX_MANAGED_CATALOG_ITEMS)
        check(size <= MAX_MANAGED_CATALOG_ITEMS) { "Managed catalog exceeds bounded migration capacity" }
    }

    private suspend fun requireCommitted(transaction: ChartCatalogTransaction) {
        check(catalog.transact(transaction) is ChartCatalogCommitResult.Committed) {
            "Managed catalog transaction failed: ${transaction.transactionId}"
        }
    }

    private fun ChartPackage.toManagedAsset(sourceId: ChartSourceId, generation: Long): ChartAsset {
        val file = managedFile(versionId)
        val supportedTileSize = tileSize.takeIf { it == 256 || it == 512 }
        val mime = when (rasterFormat) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> null
        }
        return ChartAsset(
            id = managedAssetId(),
            documentIdentity = ChartDocumentIdentity(MANAGED_AUTHORITY, versionId.value),
            locator = ChartOpaqueLocator("yokuli-managed://${versionId.value}"),
            memberships = setOf(sourceId),
            displayPath = displayName.take(1_024),
            revision = ChartContentRevision(
                identity = "managed:${versionId.value}",
                observedSizeBytes = file.length().takeIf { file.isFile },
                observedModifiedAtMillis = file.lastModified().takeIf { file.isFile && it > 0L },
                providerRevisionHint = "managed-generation:$generation",
                contentSha256 = sha256.lowercase(),
            ),
            facts = ChartAssetFacts(
                format = ChartAssetFormat.RASTER_MBTILES,
                sizeBytes = file.length().takeIf { file.isFile },
                bounds = coverage,
                minZoom = minZoom,
                maxZoom = maxZoom,
                tileSize = supportedTileSize,
                tileScheme = tileScheme,
                attribution = attribution,
                attributionProvenance = ChartFactProvenance.EMBEDDED,
                rasterMimeType = mime,
            ),
            role = ChartAssetRole.BASE,
            access = if (file.isFile) ChartAssetAccessState.READABLE else ChartAssetAccessState.MISSING,
            validation = if (file.isFile && supportedTileSize != null && mime != null) {
                ChartAssetValidationState.FULL_VERIFIED
            } else {
                ChartAssetValidationState.UNSUPPORTED_FORMAT
            },
            accessMode = ChartReadAccessMode.MANAGED_COPY,
        )
    }

    private fun ChartPackage.legacyMapping(assetId: ChartAssetId, versionKey: String?) =
        LegacyChartAssetMapping(logicalId.value, versionKey, assetId)

    private fun ChartPackage.managedAssetId(): ChartAssetId = ChartAssetId(
        UUID.nameUUIDFromBytes("yokuli-managed:${versionId.value}".encodeToByteArray()).toString(),
    )

    private fun ChartAsset.managedVersion(): ChartPackageVersionId {
        require(MANAGED_SOURCE_ID in memberships)
        val uri = Uri.parse(locator.value)
        require(uri.scheme == "yokuli-managed")
        return ChartPackageVersionId(uri.host.orEmpty())
    }

    private fun ManagedChartPackageStoreSnapshot.signature(): String {
        val canonical = buildString {
            packages.sortedBy { it.versionId.value }.forEach {
                append(it.logicalId.value).append(':').append(it.versionId.value).append(';')
            }
            activeByLogicalId.toSortedMap(compareBy { it.value }).forEach { (logical, version) ->
                append('A').append(logical.value).append(':').append(version.value).append(';')
            }
            historyByLogicalId.toSortedMap(compareBy { it.value }).forEach { (logical, versions) ->
                append('H').append(logical.value).append(':')
                append(versions.joinToString(",") { it.value }).append(';')
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        val MANAGED_SOURCE_ID = ChartSourceId(
            UUID.nameUUIDFromBytes("com.yokuli.marine.chart-library.managed".encodeToByteArray()).toString(),
        )
        private const val MANAGED_AUTHORITY = "com.yokuli.marine.managed"
        private const val PACKAGE_BATCH_SIZE = 250
        private const val STALE_BATCH_SIZE = 1_000
        private const val MAX_MANAGED_CATALOG_ITEMS = 2_048
        private const val MIN_FREE_SPACE_BYTES = 16L * 1024L * 1024L
    }
}

internal enum class ManagedCatalogMigrationCheckpoint {
    AFTER_SOURCE_PREPARED,
    AFTER_PACKAGES_REGISTERED,
    AFTER_COPY_RELATIONS,
    AFTER_ACTIVE_MAPPINGS,
    AFTER_STALE_MEMBERSHIPS_PRUNED,
    AFTER_COMPLETE_PUBLISHED,
}
