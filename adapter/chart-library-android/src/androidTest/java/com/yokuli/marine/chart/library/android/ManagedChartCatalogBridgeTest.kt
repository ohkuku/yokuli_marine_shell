package com.yokuli.marine.chart.library.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.map.domain.ChartPackage
import com.yokuli.marine.map.domain.ChartPackageCandidate
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageImportRequest
import com.yokuli.marine.map.domain.ChartPackageLogicalId
import com.yokuli.marine.map.domain.ChartPackageRepository
import com.yokuli.marine.map.domain.ChartPackageVersionId
import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.ManagedChartPackageStoreSnapshot
import com.yokuli.marine.map.domain.chartlibrary.ChartAsset
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetAccessState
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetFacts
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetFormat
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
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
import com.yokuli.marine.map.domain.chartlibrary.ChartOpaqueLocator
import com.yokuli.marine.map.domain.chartlibrary.ChartScanStatus
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceId
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceScanState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagedChartCatalogBridgeTest {
    @Test fun existingStoreRegistersIdempotentlyWithoutInspectOrCopy() = runBlocking {
        fixture("migration").use { fixture ->
            val first = fixture.repository.seed("a".repeat(64), "harbour")
            val beforeFiles = fixture.root.walkTopDown().filter(File::isFile).map { it.relativeTo(fixture.root).path }.toSet()

            fixture.bridge.synchronize()
            val firstRevision = fixture.catalog.snapshot.value.revision
            fixture.bridge.synchronize()

            val managedSource = fixture.catalog.source(ManagedChartCatalogBridge.MANAGED_SOURCE_ID)
            assertEquals(ChartLibrarySourceKind.MANAGED, managedSource?.kind)
            assertEquals(ChartGrantState.NOT_REQUIRED, managedSource?.grantState)
            assertEquals(firstRevision, fixture.catalog.snapshot.value.revision)
            assertEquals(0, fixture.repository.inspectCount)
            assertEquals(beforeFiles, fixture.root.walkTopDown().filter(File::isFile).map { it.relativeTo(fixture.root).path }.toSet())
            assertNotNull(fixture.catalog.resolveLegacyAsset(first.logicalId.value))
            assertNotNull(fixture.catalog.resolveLegacyAsset(first.id.value))
        }
    }

    @Test fun explicitCopyPublishesManagedAssetAndKeepsItsOriginalRelationship() = runBlocking {
        fixture("copy").use { fixture ->
            fixture.bridge.synchronize()
            val source = externalSource()
            val original = externalAsset(source.id)
            assertTrue(fixture.catalog.transact(ChartCatalogTransaction("external", mutations = listOf(
                ChartCatalogMutation.PutSource(source), ChartCatalogMutation.PutAsset(original),
            ))) is ChartCatalogCommitResult.Committed)

            val managedId = fixture.bridge.copy(original, com.yokuli.marine.map.domain.ChartPackageOperationId("copy-one"), {}, {})

            assertEquals(managedId, fixture.catalog.managedCopyFor(original.id))
            assertEquals(original.id, fixture.catalog.originalForManagedCopy(managedId))
            val managed = requireNotNull(fixture.catalog.asset(managedId))
            assertEquals(ChartAssetAccessState.READABLE, managed.access)
            assertEquals(ChartAssetValidationState.FULL_VERIFIED, managed.validation)
            assertEquals(1, fixture.repository.inspectCount)
        }
    }

    @Test fun everyCatalogMigrationCheckpointResumesWithoutCopyOrMissingLegacyChoice() = runBlocking {
        ManagedCatalogMigrationCheckpoint.entries.forEach { checkpoint ->
            fixture("checkpoint-${checkpoint.name}", checkpoint).use { fixture ->
                val externalSource = externalSource()
                val original = externalAsset(externalSource.id)
                assertTrue(fixture.catalog.transact(ChartCatalogTransaction("external-${checkpoint.name}", mutations = listOf(
                    ChartCatalogMutation.PutSource(externalSource), ChartCatalogMutation.PutAsset(original),
                ))) is ChartCatalogCommitResult.Committed)
                val old = fixture.repository.seed("c".repeat(64), "harbour", original.id.value)
                runCatching { fixture.bridge.synchronize() }

                val restarted = ManagedChartCatalogBridge(fixture.catalog, fixture.repository, fixture.root)
                restarted.synchronize()
                val completedRevision = fixture.catalog.snapshot.value.revision
                restarted.synchronize()

                assertEquals(ChartScanStatus.COMPLETE, fixture.catalog.source(ManagedChartCatalogBridge.MANAGED_SOURCE_ID)?.scan?.status)
                assertNotNull(fixture.catalog.resolveLegacyAsset(old.logicalId.value))
                assertNotNull(fixture.catalog.resolveLegacyAsset(old.id.value))
                assertNotNull(fixture.catalog.managedCopyFor(original.id))
                assertEquals(0, fixture.repository.inspectCount)
                assertEquals(completedRevision, fixture.catalog.snapshot.value.revision)
            }
        }
    }

    private fun fixture(name: String, failAt: ManagedCatalogMigrationCheckpoint? = null): Fixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = File(context.cacheDir, "managed-bridge-$name").also { it.deleteRecursively(); it.mkdirs() }
        val root = File(base, "map_packages").also { it.mkdirs() }
        val catalog = RoomChartCatalogRepository.create(context, File(base, "catalog.db"))
        val repository = FakeManagedRepository(root)
        return Fixture(
            base,
            root,
            catalog,
            repository,
            ManagedChartCatalogBridge(catalog, repository, root) { checkpoint ->
                if (checkpoint == failAt) error("simulated process death at $checkpoint")
            },
        )
    }

    private data class Fixture(
        val base: File,
        val root: File,
        val catalog: RoomChartCatalogRepository,
        val repository: FakeManagedRepository,
        val bridge: ManagedChartCatalogBridge,
    ) : AutoCloseable {
        override fun close() { catalog.close(); base.deleteRecursively() }
    }

    private class FakeManagedRepository(private val root: File) : ChartPackageRepository {
        private val values = mutableListOf<ChartPackage>()
        var inspectCount = 0
        private var candidate: ChartPackageCandidate? = null

        fun seed(sha: String, logical: String, copiedFrom: String? = null): ChartPackage =
            chartPackage(sha, logical).copy(copiedFromCatalogAssetId = copiedFrom).also {
                publishFile(it)
                values += it
            }

        override suspend fun inspect(sourceUri: String): ChartPackageCandidate {
            inspectCount += 1
            return ChartPackageCandidate(
                stagedImportId = "candidate-$inspectCount",
                suggestedDisplayName = "Copied chart",
                suggestedSource = "fixture",
                suggestedLicense = "unknown",
                suggestedAttribution = "fixture",
                suggestedVersion = "1",
                sha256 = "b".repeat(64),
                coverage = GeoBounds(-47.0, 166.0, -34.0, 179.0),
                minZoom = 0,
                maxZoom = 12,
                rasterFormat = "png",
            ).also { candidate = it }
        }

        override suspend fun commit(request: ChartPackageImportRequest): ChartPackage {
            val value = requireNotNull(candidate)
            return chartPackage(value.sha256, value.logicalId.value).copy(
                copiedFromCatalogAssetId = request.copiedFromCatalogAssetId,
            ).also {
                publishFile(it)
                values.removeAll { old -> old.versionId == it.versionId }
                values += it
            }
        }

        override suspend fun discard(stagedImportId: String) = Unit
        override suspend fun listInstalled(): List<ChartPackage> = values.toList()
        override suspend fun delete(packageId: ChartPackageId) { values.removeAll { it.id == packageId } }
        override suspend fun managedStoreSnapshot(): ManagedChartPackageStoreSnapshot = ManagedChartPackageStoreSnapshot(
            packages = values.toList(),
            activeByLogicalId = values.associate { it.logicalId to it.versionId },
            historyByLogicalId = values.groupBy { it.logicalId }.mapValues { (_, packages) -> packages.map { it.versionId } },
            storageBytes = root.walkTopDown().filter(File::isFile).sumOf(File::length),
        )

        private fun publishFile(value: ChartPackage) {
            File(root, "package-${value.versionId.value}/map.mbtiles").apply {
                parentFile?.mkdirs()
                writeText("fixture")
            }
        }

        private fun chartPackage(sha: String, logical: String) = ChartPackage(
            id = ChartPackageId(sha),
            displayName = "Harbour",
            source = "fixture",
            license = "unknown",
            attribution = "fixture",
            sha256 = sha,
            localUri = "mbtiles://managed",
            coverage = GeoBounds(-47.0, 166.0, -34.0, 179.0),
            minZoom = 0,
            maxZoom = 12,
            version = "1",
            logicalId = ChartPackageLogicalId(logical),
            versionId = ChartPackageVersionId(sha),
        )
    }

    private fun externalSource() = ChartLibrarySource(
        ChartSourceId(UUID.randomUUID().toString()),
        ChartLibrarySourceKind.SINGLE_DOCUMENT,
        ChartOpaqueLocator("content://fixture/chart.mbtiles"),
        "External",
        recursive = false,
        grantState = ChartGrantState.GRANTED,
        scan = ChartSourceScanState(1, ChartScanStatus.COMPLETE, 1, 1),
    )

    private fun externalAsset(sourceId: ChartSourceId) = ChartAsset(
        ChartAssetId(UUID.randomUUID().toString()),
        ChartDocumentIdentity("fixture", "external"),
        ChartOpaqueLocator("content://fixture/chart.mbtiles"),
        setOf(sourceId),
        "External.mbtiles",
        ChartContentRevision("external", 7L, 1L),
        ChartAssetFacts(
            ChartAssetFormat.RASTER_MBTILES,
            7L,
            GeoBounds(-47.0, 166.0, -34.0, 179.0),
            0,
            12,
            1L,
            256,
            com.yokuli.marine.map.domain.MapTileScheme.MBTILES_TMS,
            "fixture",
            ChartFactProvenance.EMBEDDED,
            "image/png",
        ),
        ChartAssetRole.BASE,
        access = ChartAssetAccessState.READABLE,
        validation = ChartAssetValidationState.BASIC_READABLE,
    )
}
