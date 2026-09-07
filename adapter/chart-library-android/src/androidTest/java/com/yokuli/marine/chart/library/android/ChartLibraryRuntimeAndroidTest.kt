package com.yokuli.marine.chart.library.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.chartlibrary.*
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChartLibraryRuntimeAndroidTest {
    @Test fun closingFeatureObserverDoesNotCloseChartReadLease() = runBlocking {
        fixture("observer").use { fixture ->
            val observer = launch { fixture.runtime.state.collect() }
            val session = (fixture.runtime.open(fixture.request()) as ChartOpenResult.Opened).session
            observer.cancelAndJoin()
            assertFalse(fixture.access.sessions.single().closed.get())
            assertTrue(session.hasTile(ChartTileKey(0, 0, 0), MapTileScheme.MBTILES_TMS))
            session.close()
        }
    }

    @Test fun runtimeLeasePreservesFallbackMetadataAndCoverageCapabilities() = runBlocking {
        fixture("capabilities").use { fixture ->
            val session = (fixture.runtime.open(fixture.request()) as ChartOpenResult.Opened).session
            session.use {
                assertEquals(ChartReadAccessMode.LOCAL_FALLBACK, it.accessMode)
                assertFalse(it.metadataPresent)
                assertEquals(7..9, it.readZoomRange())
                assertEquals(listOf(ChartStoredTileExtent(9, 1, 2, 3, 4)), it.readTileExtents())
            }
        }
    }

    @Test fun catalogRevisionOrSourceGenerationInvalidatesOldSessionBeforeNewTilesCanReturn() = runBlocking {
        fixture("revision").use { fixture ->
            val opened = fixture.runtime.open(fixture.request()) as ChartOpenResult.Opened
            val changed = fixture.asset.copy(
                revision = fixture.asset.revision.copy(observedModifiedAtMillis = 2),
                access = ChartAssetAccessState.CHANGED,
                validation = ChartAssetValidationState.DISCOVERED,
            )
            assertTrue(fixture.catalog.transact(ChartCatalogTransaction(
                "change-revision", mutations = listOf(ChartCatalogMutation.PutAsset(changed)),
            )) is ChartCatalogCommitResult.Committed)
            withTimeout(2_000) { while (!fixture.access.sessions.single().closed.get()) delay(10) }
            val failure = runCatching {
                opened.session.hasTile(ChartTileKey(0, 0, 0), MapTileScheme.MBTILES_TMS)
            }.exceptionOrNull()
            assertEquals(ChartReadFailure.SESSION_CLOSED, (failure as ChartReadException).failure)
            assertEquals(0, fixture.runtime.metrics.value.activeReadSessions)
        }
    }

    @Test fun globalReadBudgetBlocksThirteenthSessionUntilOneLeaseCloses() = runBlocking {
        fixture("budget").use { fixture ->
            val sessions = List(AndroidChartLibraryRuntime.MAX_OPEN_READ_SESSIONS) {
                (fixture.runtime.open(fixture.request()) as ChartOpenResult.Opened).session
            }
            assertEquals(12, fixture.runtime.metrics.value.activeReadSessions)
            val waiting = async { fixture.runtime.open(fixture.request()) }
            delay(100)
            assertFalse(waiting.isCompleted)
            sessions.first().close()
            val thirteenth = withTimeout(2_000) { waiting.await() }
            assertTrue(thirteenth is ChartOpenResult.Opened)
            sessions.drop(1).forEach(ChartReadSession::close)
            (thirteenth as ChartOpenResult.Opened).session.close()
            assertEquals(0, fixture.runtime.metrics.value.activeReadSessions)
            assertEquals(12, fixture.runtime.metrics.value.readSessionHighWater)
        }
    }

    @Test fun pendingReadQueueRejectsOverflowWithoutCreatingUnboundedSessions() = runBlocking {
        fixture("pending-budget").use { fixture ->
            val sessions = List(AndroidChartLibraryRuntime.MAX_OPEN_READ_SESSIONS) {
                (fixture.runtime.open(fixture.request()) as ChartOpenResult.Opened).session
            }
            val waiting = List(AndroidChartLibraryRuntime.MAX_PENDING_READ_REQUESTS) {
                async { fixture.runtime.open(fixture.request()) }
            }
            withTimeout(2_000) {
                while (fixture.runtime.metrics.value.queuedReadRequests != AndroidChartLibraryRuntime.MAX_PENDING_READ_REQUESTS) delay(10)
            }

            val overflow = fixture.runtime.open(fixture.request()) as ChartOpenResult.Rejected
            assertEquals(ChartReadFailure.RESOURCE_LIMIT, overflow.failure)
            assertEquals(1L, fixture.runtime.metrics.value.rejectedReadRequests)
            assertEquals(AndroidChartLibraryRuntime.MAX_PENDING_READ_REQUESTS, fixture.runtime.metrics.value.queuedReadHighWater)

            waiting.forEach { it.cancel() }
            waiting.forEach { runCatching { it.await() } }
            sessions.forEach(ChartReadSession::close)
            withTimeout(2_000) { while (fixture.runtime.metrics.value.queuedReadRequests != 0) delay(10) }
            assertEquals(0, fixture.runtime.metrics.value.activeReadSessions)
        }
    }

    @Test fun closedSessionsPublishBoundedReadEvidence() = runBlocking {
        fixture("read-evidence").use { fixture ->
            val session = (fixture.runtime.open(fixture.request()) as ChartOpenResult.Opened).session
            assertTrue(session.hasTile(ChartTileKey(0, 0, 0), MapTileScheme.MBTILES_TMS))
            assertEquals(1, session.readSourceRange(0, 1).size)
            session.close()

            assertEquals(1L, fixture.runtime.metrics.value.closedReadSessions)
            assertEquals(1L, fixture.runtime.metrics.value.tileQueries)
            assertEquals(1L, fixture.runtime.metrics.value.sourceBytesRead)
            fixture.runtime.metrics.value.let { metrics ->
                println(
                    "CL11_EVIDENCE " +
                        "{\"scenario\":\"runtime-bounds\",\"activeReads\":${metrics.activeReadSessions}," +
                        "\"readHighWater\":${metrics.readSessionHighWater},\"queuedReads\":${metrics.queuedReadRequests}," +
                        "\"queuedHighWater\":${metrics.queuedReadHighWater},\"rejectedReads\":${metrics.rejectedReadRequests}," +
                        "\"sourceBytesRead\":${metrics.sourceBytesRead},\"tileQueries\":${metrics.tileQueries}}",
                )
            }
        }
    }

    @Test fun processRestartRestoresRunningValidationAsInterruptedWithoutRestoringFd() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SharedPreferencesChartValidationJobStore(context).restoreInterrupted()
        val id = ChartAssetId(UUID.randomUUID().toString())
        SharedPreferencesChartValidationJobStore(context).running(id, ChartValidationJobKind.FULL)
        val restored = SharedPreferencesChartValidationJobStore(context).restoreInterrupted()
        assertEquals(ChartValidationJobStatus.INTERRUPTED, restored.getValue(id).status)
        assertTrue(SharedPreferencesChartValidationJobStore(context).restoreInterrupted().isEmpty())
    }

    @Test fun changedAssetCanBeValidatedButCannotRenderUntilValidationPublishes() = runBlocking {
        fixture("changed-purpose").use { fixture ->
            val changed = fixture.asset.copy(access = ChartAssetAccessState.CHANGED, validation = ChartAssetValidationState.DISCOVERED)
            fixture.catalog.transact(ChartCatalogTransaction(
                "mark-changed", mutations = listOf(ChartCatalogMutation.PutAsset(changed)),
            ))
            assertEquals(ChartReadFailure.REVISION_CHANGED, (fixture.runtime.open(fixture.request()) as ChartOpenResult.Rejected).failure)
            assertTrue(fixture.runtime.inspectBasic(changed.id) is ChartValidationCommandResult.Published)
            assertTrue(fixture.runtime.open(fixture.request()) is ChartOpenResult.Opened)
        }
    }

    private suspend fun fixture(name: String): Fixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "runtime-$name.db").also {
            it.delete(); File("${it.path}-wal").delete(); File("${it.path}-shm").delete()
        }
        val catalog = RoomChartCatalogRepository.create(context, file)
        val source = ChartLibrarySource(
            ChartSourceId(UUID.randomUUID().toString()), ChartLibrarySourceKind.SINGLE_DOCUMENT,
            ChartOpaqueLocator("content://runtime/document/chart"), "chart", recursive = false,
            grantState = ChartGrantState.GRANTED,
            scan = ChartSourceScanState(1, ChartScanStatus.COMPLETE, 1, 1, 0),
        )
        val asset = ChartAsset(
            ChartAssetId(UUID.randomUUID().toString()), ChartDocumentIdentity("runtime", "chart"),
            source.locator, setOf(source.id), "chart.mbtiles", ChartContentRevision("runtime:chart", 1, 1),
            access = ChartAssetAccessState.READABLE, validation = ChartAssetValidationState.BASIC_READABLE,
        )
        assertTrue(catalog.transact(ChartCatalogTransaction(
            "seed-$name", mutations = listOf(ChartCatalogMutation.PutSource(source), ChartCatalogMutation.PutAsset(asset)),
        )) is ChartCatalogCommitResult.Committed)
        val access = FakeAccess()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = AndroidChartSourceController(
            catalog, { _, _ -> ChartEnumerationResult.Complete(emptyList()) },
            object : PersistedChartGrantPort {
                override fun takeRead(locator: ChartOpaqueLocator) = true
                override fun releaseRead(locator: ChartOpaqueLocator) = true
            },
        )
        val runtime = AndroidChartLibraryRuntime.createForTest(
            catalog, access, controller, ChartRevisionProbe { it.revision }, scope,
        )
        return Fixture(catalog, runtime, access, source, asset, scope)
    }

    private data class Fixture(
        val catalog: RoomChartCatalogRepository,
        val runtime: AndroidChartLibraryRuntime,
        val access: FakeAccess,
        val source: ChartLibrarySource,
        val asset: ChartAsset,
        val scope: CoroutineScope,
    ) : AutoCloseable {
        fun request() = ChartReadRequest(asset.id, asset.locator, asset.revision, source.scan.generation)
        override fun close() { runtime.close(); scope.cancel() }
    }

    private class FakeAccess : ChartResourceAccessPort {
        val sessions = mutableListOf<FakeSession>()
        override suspend fun open(request: ChartReadRequest): ChartOpenResult = ChartOpenResult.Opened(
            FakeSession(request).also(sessions::add),
        )
    }

    private class FakeSession(override val request: ChartReadRequest) : ChartReadSession {
        val closed = AtomicBoolean(false)
        private var bytesRead = 0L
        private var queries = 0L
        override val sourceSizeBytes = 1L
        override val accessMode = ChartReadAccessMode.LOCAL_FALLBACK
        override val metadataPresent = false
        override fun readMetadata(limit: Int) = mapOf("scheme" to "tms")
        override fun readZoomRange() = 7..9
        override fun readTileExtents(limit: Int) = listOf(ChartStoredTileExtent(9, 1, 2, 3, 4)).take(limit)
        override fun readTile(key: ChartTileKey, scheme: MapTileScheme): ChartTilePayload? { ensureOpen(); queries++; return null }
        override fun hasTile(key: ChartTileKey, scheme: MapTileScheme): Boolean { ensureOpen(); queries++; return true }
        override fun readStoredTiles(offset: Long, limit: Int): List<ChartStoredTile> {
            ensureOpen()
            return if (offset == 0L) listOf(
                ChartStoredTile(ChartStoredTileKey(0, 0, 0), ChartTilePayload(byteArrayOf(1), "image/png", 256, 256)),
            ) else emptyList()
        }
        override fun readSourceRange(offset: Long, maxByteCount: Int): ByteArray {
            ensureOpen()
            return byteArrayOf(0).also { bytesRead += it.size }
        }
        override fun statistics() = ChartReadStatistics(sourceBytesRead = bytesRead, tileQueries = queries)
        override fun close() { closed.set(true) }
        private fun ensureOpen() {
            if (closed.get()) throw ChartReadException(ChartReadFailure.SESSION_CLOSED, "closed")
        }
    }
}
