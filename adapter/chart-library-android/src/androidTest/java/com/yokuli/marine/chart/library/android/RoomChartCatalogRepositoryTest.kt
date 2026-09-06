package com.yokuli.marine.chart.library.android

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.map.domain.chartlibrary.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomChartCatalogRepositoryTest {
    @Test fun transactionIsAtomicPagedAndSurvivesRestart() = runBlocking {
        val file = freshDatabase("catalog-restart")
        val source = source("source")
        val assets = (1..3).map { asset(source.id, "doc-$it", "folder/chart-$it.mbtiles") }
        RoomChartCatalogRepository.create(context, file).use { repository ->
            val result = repository.transact(ChartCatalogTransaction("seed", mutations = listOf(
                ChartCatalogMutation.PutSource(source),
                *assets.map(ChartCatalogMutation::PutAsset).toTypedArray(),
            ))) as ChartCatalogCommitResult.Committed
            assertEquals(1L, result.snapshot.revision)
            assertEquals(3, result.snapshot.assetCount)
            val first = repository.assets(offset = 0, limit = 2)
            assertEquals(2, first.items.size)
            assertEquals(3, first.total)
            assertTrue(repository.assets(offset = 2, limit = 2).items.single().displayPath.endsWith("3.mbtiles"))
        }
        RoomChartCatalogRepository.create(context, file).use { restored ->
            assertEquals(1L, restored.snapshot.value.revision)
            assertEquals(3, restored.assets().total)
        }
    }

    @Test fun confirmedDocumentIdentityDeduplicatesMembershipAliases() = runBlocking {
        RoomChartCatalogRepository.create(context, freshDatabase("catalog-dedup")).use { repository ->
            val firstSource = source("tree")
            val secondSource = source("single", ChartLibrarySourceKind.SINGLE_DOCUMENT)
            val first = asset(firstSource.id, "same-document", "tree/chart.mbtiles")
            val alias = first.copy(id = ChartAssetId(UUID.randomUUID().toString()), memberships = setOf(secondSource.id), displayPath = "chart.mbtiles")
            repository.transact(ChartCatalogTransaction("sources", mutations = listOf(
                ChartCatalogMutation.PutSource(firstSource), ChartCatalogMutation.PutSource(secondSource),
            )))
            repository.transact(ChartCatalogTransaction("first", mutations = listOf(ChartCatalogMutation.PutAsset(first))))
            val committed = repository.transact(
                ChartCatalogTransaction("alias", mutations = listOf(ChartCatalogMutation.PutAsset(alias))),
            ) as ChartCatalogCommitResult.Committed
            assertEquals(first.id, committed.resolvedAssetIds[alias.id])
            assertEquals(setOf(firstSource.id, secondSource.id), repository.asset(first.id)?.memberships)
            assertEquals(1, repository.snapshot.value.assetCount)
            val sameNameDifferentDocument = asset(firstSource.id, "another-document", alias.displayPath)
            assertTrue(
                repository.transact(
                    ChartCatalogTransaction("same-name", mutations = listOf(ChartCatalogMutation.PutAsset(sameNameDifferentDocument))),
                ) is ChartCatalogCommitResult.Committed,
            )
            assertEquals(2, repository.snapshot.value.assetCount)
            repository.transact(ChartCatalogTransaction("remove-one", mutations = listOf(
                ChartCatalogMutation.RemoveAssetMembership(first.id, firstSource.id),
            )))
            assertEquals(setOf(secondSource.id), repository.asset(first.id)?.memberships)
        }
    }

    @Test fun validationUnknownFactsAndLegacyMappingRoundTripIdempotently() = runBlocking {
        RoomChartCatalogRepository.create(context, freshDatabase("catalog-legacy")).use { repository ->
            val source = source("managed", ChartLibrarySourceKind.MANAGED)
            val asset = asset(source.id, "legacy-doc", "map_packages/current.mbtiles").copy(
                facts = ChartAssetFacts(),
                access = ChartAssetAccessState.READABLE,
                validation = ChartAssetValidationState.BASIC_READABLE,
            )
            val mapping = LegacyChartAssetMapping("logical-old", "version-old", asset.id)
            val transaction = ChartCatalogTransaction("legacy-v1", mutations = listOf(
                ChartCatalogMutation.PutSource(source), ChartCatalogMutation.PutAsset(asset),
                ChartCatalogMutation.PutLegacyMapping(mapping),
            ))
            assertTrue(repository.transact(transaction) is ChartCatalogCommitResult.Committed)
            assertTrue(repository.transact(transaction) is ChartCatalogCommitResult.Committed)
            assertEquals(1L, repository.snapshot.value.revision)
            assertEquals(asset.id, repository.resolveLegacyAsset("logical-old", "version-old"))
            assertNull(repository.asset(asset.id)?.facts?.tileCount)
            assertEquals(ChartAssetValidationState.BASIC_READABLE, repository.asset(asset.id)?.validation)
        }
    }

    @Test fun optimisticConflictAndClosedDatabaseFailureDoNotPublishPartialState() = runBlocking {
        val repository = RoomChartCatalogRepository.create(context, freshDatabase("catalog-failure"))
        val source = source("first")
        assertTrue(repository.transact(ChartCatalogTransaction("seed", mutations = listOf(ChartCatalogMutation.PutSource(source)))) is ChartCatalogCommitResult.Committed)
        val before = repository.snapshot.value
        val conflict = repository.transact(ChartCatalogTransaction("stale", expectedRevision = 0, mutations = listOf(ChartCatalogMutation.RemoveSource(source.id))))
        assertEquals(1L, (conflict as ChartCatalogCommitResult.Conflict).actualRevision)
        assertEquals(before, repository.snapshot.value)
        repository.close()
        val failed = repository.transact(ChartCatalogTransaction("closed", mutations = listOf(ChartCatalogMutation.RemoveSource(source.id))))
        assertTrue(failed is ChartCatalogCommitResult.Failed)
        assertEquals(before, repository.snapshot.value)
    }

    @Test fun managedCopyRelationshipSurvivesRestartAndCascadesWithTheManagedAsset() = runBlocking {
        val file = freshDatabase("catalog-copy-relation")
        val externalSource = source("external")
        val managedSource = source("managed-copy", ChartLibrarySourceKind.MANAGED)
        val original = asset(externalSource.id, "original", "original.mbtiles")
        val managed = asset(managedSource.id, "managed", "copy.mbtiles")
        RoomChartCatalogRepository.create(context, file).use { repository ->
            assertTrue(repository.transact(ChartCatalogTransaction("copy-seed", mutations = listOf(
                ChartCatalogMutation.PutSource(externalSource),
                ChartCatalogMutation.PutSource(managedSource),
                ChartCatalogMutation.PutAsset(original),
                ChartCatalogMutation.PutAsset(managed),
                ChartCatalogMutation.PutManagedCopyRelation(ChartManagedCopyRelation(original.id, managed.id)),
            ))) is ChartCatalogCommitResult.Committed)
            assertEquals(managed.id, repository.managedCopyFor(original.id))
            assertEquals(original.id, repository.originalForManagedCopy(managed.id))
        }
        RoomChartCatalogRepository.create(context, file).use { restored ->
            assertEquals(managed.id, restored.managedCopyFor(original.id))
            restored.transact(ChartCatalogTransaction("remove-copy", mutations = listOf(
                ChartCatalogMutation.RemoveAssetMembership(managed.id, managedSource.id),
            )))
            assertNull(restored.managedCopyFor(original.id))
        }
    }

    @Test fun oneDeduplicatedManagedCopyCanRelateToMultipleExternalCatalogItems() = runBlocking {
        RoomChartCatalogRepository.create(context, freshDatabase("catalog-copy-aliases")).use { repository ->
            val externalSource = source("external-aliases")
            val managedSource = source("managed-alias", ChartLibrarySourceKind.MANAGED)
            val first = asset(externalSource.id, "first-original", "first.mbtiles")
            val second = asset(externalSource.id, "second-original", "second.mbtiles")
            val managed = asset(managedSource.id, "one-copy", "copy.mbtiles")
            assertTrue(repository.transact(ChartCatalogTransaction("aliases", mutations = listOf(
                ChartCatalogMutation.PutSource(externalSource),
                ChartCatalogMutation.PutSource(managedSource),
                ChartCatalogMutation.PutAsset(first),
                ChartCatalogMutation.PutAsset(second),
                ChartCatalogMutation.PutAsset(managed),
                ChartCatalogMutation.PutManagedCopyRelation(ChartManagedCopyRelation(first.id, managed.id)),
                ChartCatalogMutation.PutManagedCopyRelation(ChartManagedCopyRelation(second.id, managed.id)),
            ))) is ChartCatalogCommitResult.Committed)

            assertEquals(managed.id, repository.managedCopyFor(first.id))
            assertEquals(managed.id, repository.managedCopyFor(second.id))
            assertNotNull(repository.originalForManagedCopy(managed.id))
        }
    }

    @Test fun thousandAssetCatalogIsConsumedInBoundedDatabasePages() = runBlocking {
        val file = freshDatabase("catalog-thousand")
        val source = source("thousand")
        val assets = (0 until 1_000).map { index ->
            asset(source.id, "document-$index", "region/chart-${index.toString().padStart(4, '0')}.mbtiles")
        }
        val startedAt = SystemClock.elapsedRealtime()
        RoomChartCatalogRepository.create(context, file).use { repository ->
            val mutations = buildList {
                add(ChartCatalogMutation.PutSource(source))
                assets.forEach { add(ChartCatalogMutation.PutAsset(it)) }
            }
            assertTrue(repository.transact(ChartCatalogTransaction("thousand-assets", mutations = mutations)) is ChartCatalogCommitResult.Committed)
            val observed = linkedSetOf<ChartAssetId>()
            var offset = 0
            do {
                val page = repository.assets(offset = offset)
                assertTrue(page.items.size <= MAX_CATALOG_PAGE_SIZE)
                observed += page.items.map(ChartAsset::id)
                offset += page.items.size
            } while (offset < 1_000)
            assertEquals(assets.mapTo(linkedSetOf(), ChartAsset::id), observed)
            assertEquals(1_000, repository.snapshot.value.assetCount)
        }
        println(
            "CL11_EVIDENCE " +
                "{\"scenario\":\"catalog-1000\",\"assetCount\":1000,\"pageSize\":100," +
                "\"catalogBytes\":${file.length()},\"elapsedMillis\":${SystemClock.elapsedRealtime() - startedAt}}",
        )
    }

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun freshDatabase(name: String) = File(context.cacheDir, "$name.db").also {
        it.delete(); File("${it.path}-wal").delete(); File("${it.path}-shm").delete()
    }

    private fun source(name: String, kind: ChartLibrarySourceKind = ChartLibrarySourceKind.TREE): ChartLibrarySource =
        ChartLibrarySource(
            ChartSourceId(UUID.nameUUIDFromBytes(name.encodeToByteArray()).toString()),
            kind,
            ChartOpaqueLocator(if (kind == ChartLibrarySourceKind.MANAGED) "managed://$name" else "content://provider/$name"),
            name,
            recursive = kind == ChartLibrarySourceKind.TREE,
            grantState = if (kind == ChartLibrarySourceKind.MANAGED) ChartGrantState.NOT_REQUIRED else ChartGrantState.GRANTED,
        )

    private fun asset(sourceId: ChartSourceId, documentId: String, path: String): ChartAsset = ChartAsset(
        ChartAssetId(UUID.nameUUIDFromBytes("asset:$documentId".encodeToByteArray()).toString()),
        ChartDocumentIdentity("test.provider", documentId),
        ChartOpaqueLocator("content://test.provider/document/$documentId"),
        setOf(sourceId), path,
        ChartContentRevision("test.provider:$documentId", null, null),
    )
}
