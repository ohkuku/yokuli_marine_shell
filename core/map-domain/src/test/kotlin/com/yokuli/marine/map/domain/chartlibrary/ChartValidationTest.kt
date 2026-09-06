package com.yokuli.marine.map.domain.chartlibrary

import com.yokuli.marine.map.domain.MapTileScheme
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChartValidationTest {
    @Test fun tmsAndXyzUseOneReversibleCoordinateMapping() {
        val stored = ChartStoredTileKey(3, 4, 2)
        val tms = requireNotNull(ChartTileCoordinateMapper.externalKey(stored, MapTileScheme.MBTILES_TMS))
        val xyz = requireNotNull(ChartTileCoordinateMapper.externalKey(stored, MapTileScheme.XYZ))
        assertEquals(5L, tms.row)
        assertEquals(2L, xyz.row)
        assertEquals(stored.storageRow, tms.storageRow(MapTileScheme.MBTILES_TMS))
        assertNull(ChartTileCoordinateMapper.externalKey(ChartStoredTileKey(25, 0, 0), MapTileScheme.XYZ))
    }

    @Test fun basicInspectionSamplesWithoutHashOrClaimingFull() = runBlocking {
        val session = FakeSession(metadata = mapOf("scheme" to "tms", "minzoom" to "0", "maxzoom" to "0"))
        val result = ChartBasicInspector(ChartResourceAccessPort { ChartOpenResult.Opened(session) }).inspect(asset(), 1)
        assertTrue(result is ChartBasicInspectionResult.Readable)
        val inspection = (result as ChartBasicInspectionResult.Readable).inspection
        assertEquals(1, inspection.sampledTileCount)
        assertNull(inspection.facts.tileCount)
        assertEquals(0, session.sourceReads)
        assertEquals(1, session.sampleReads)
        assertEquals(0, session.orderedTileReads)
    }

    @Test fun fullVerificationCancellationNeverHashesOrPublishesVerified() = runBlocking {
        val session = FakeSession()
        val item = asset()
        val verifier = ChartFullVerifier(
            ChartResourceAccessPort { ChartOpenResult.Opened(session) },
            ChartRevisionProbe { it.revision },
        )
        assertEquals(ChartFullVerificationResult.Cancelled, verifier.verify(item, 1, { true }))
        assertEquals(0, session.sourceReads)
    }

    @Test fun revisionChangeDuringFullVerificationRejectsResult() = runBlocking {
        val session = FakeSession(source = "bytes".encodeToByteArray())
        val item = asset()
        var probes = 0
        val result = ChartFullVerifier(
            ChartResourceAccessPort { ChartOpenResult.Opened(session) },
            ChartRevisionProbe {
                probes++
                if (probes == 1) it.revision else it.revision.copy(observedModifiedAtMillis = 2)
            },
        ).verify(item, 1, { false })
        assertEquals(ChartValidationIssue.REVISION_CHANGED, (result as ChartFullVerificationResult.Rejected).issue)
    }

    @Test fun fullVerificationRejectsDuplicateAndInvalidCoordinates() = runBlocking {
        val duplicate = ChartStoredTile(ChartStoredTileKey(0, 0, 0), payload())
        val item = asset()
        val result = ChartFullVerifier(
            ChartResourceAccessPort { ChartOpenResult.Opened(FakeSession(tiles = listOf(duplicate, duplicate))) },
            ChartRevisionProbe { it.revision },
        ).verify(item, 1, { false })
        assertEquals(ChartValidationIssue.DUPLICATE_COORDINATE, (result as ChartFullVerificationResult.Rejected).issue)
    }

    @Test fun basicInspectionReadsAValidTileFromLongSizedSourceWithoutScanningIt() = runBlocking {
        val session = FakeSession(reportedSize = (4L * 1024 * 1024 * 1024) + 8192L)
        val result = ChartBasicInspector(ChartResourceAccessPort { ChartOpenResult.Opened(session) }).inspect(asset(), 1)
        assertTrue(result is ChartBasicInspectionResult.Readable)
        assertEquals(0, session.sourceReads)
        assertTrue(session.sourceSizeBytes > Int.MAX_VALUE)
    }

    @Test fun basicInspectionPreservesPermissionFailureAsARecoverableTypedIssue() = runBlocking {
        val result = ChartBasicInspector(
            ChartResourceAccessPort {
                ChartOpenResult.Rejected(ChartReadFailure.PERMISSION_LOST, "permission revoked")
            },
        ).inspect(asset(), 1)

        assertEquals(ChartValidationIssue.PERMISSION_LOST, (result as ChartBasicInspectionResult.Rejected).issue)
    }

    private fun asset() = ChartAsset(
        ChartAssetId(UUID.randomUUID().toString()),
        ChartDocumentIdentity("provider", "chart"),
        ChartOpaqueLocator("content://provider/document/chart"),
        setOf(ChartSourceId(UUID.randomUUID().toString())),
        "chart.mbtiles",
        ChartContentRevision("chart", 5, 1),
    )

    private fun payload(size: Int = 256) = ChartTilePayload(byteArrayOf(1), "image/png", size, size)

    private class FakeSession(
        private val metadata: Map<String, String> = mapOf("scheme" to "tms"),
        private val tiles: List<ChartStoredTile> = listOf(
            ChartStoredTile(ChartStoredTileKey(0, 0, 0), ChartTilePayload(byteArrayOf(1), "image/png", 256, 256)),
        ),
        private val source: ByteArray = ByteArray(5) { it.toByte() },
        private val reportedSize: Long = source.size.toLong(),
    ) : ChartReadSession {
        override val request = ChartReadRequest(
            ChartAssetId("fake"), ChartOpaqueLocator("content://fake/document/chart"),
            ChartContentRevision("fake", source.size.toLong(), 1), 1,
        )
        override val sourceSizeBytes = reportedSize
        var sourceReads = 0
        var sampleReads = 0
        var orderedTileReads = 0
        override fun readMetadata(limit: Int) = metadata
        override fun readTile(key: ChartTileKey, scheme: MapTileScheme) = tiles.firstOrNull()?.payload
        override fun hasTile(key: ChartTileKey, scheme: MapTileScheme) = tiles.isNotEmpty()
        override fun readSampleTiles(limit: Int): List<ChartStoredTile> {
            sampleReads++
            return tiles.take(limit)
        }
        override fun readStoredTiles(offset: Long, limit: Int): List<ChartStoredTile> {
            orderedTileReads++
            return tiles.drop(offset.toInt()).take(limit)
        }
        override fun readSourceRange(offset: Long, maxByteCount: Int): ByteArray {
            sourceReads++
            return source.copyOfRange(offset.toInt(), minOf(source.size, offset.toInt() + maxByteCount))
        }
        override fun statistics() = ChartReadStatistics()
        override fun close() = Unit
    }
}
