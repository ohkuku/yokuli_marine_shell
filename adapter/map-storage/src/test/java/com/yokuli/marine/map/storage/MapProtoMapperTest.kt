package com.yokuli.marine.map.storage

import androidx.datastore.core.CorruptionException
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapSessionSnapshot
import com.yokuli.marine.map.domain.MeasurementDraft
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplayPreferences
import com.yokuli.marine.map.domain.chartlibrary.ChartDisplaySelection
import com.yokuli.marine.map.domain.chartlibrary.ChartSourceId
import com.yokuli.marine.map.storage.proto.MapStateProto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MapProtoMapperTest {
    @Test
    fun `session round trip excludes every user library collection`() {
        val snapshot = MapSessionSnapshot(
            camera = MapCamera(GeoPoint(-36.8, 174.8), 12.0),
            measurementDraft = MeasurementDraft(listOf(GeoPoint(-36.8, 174.8))),
            activeRouteDraftId = "draft-stable",
            activeChartPackageId = ChartPackageId("chart-stable"),
            chartDisplayPreferences = ChartDisplayPreferences(
                selection = ChartDisplaySelection.SourceSet(
                    setOf(ChartSourceId("00000000-0000-0000-0000-000000000001")),
                ),
                overlaysVisible = false,
                assetOpacity = mapOf(ChartAssetId("10000000-0000-0000-0000-000000000001") to .4f),
                hiddenAssetIds = setOf(ChartAssetId("10000000-0000-0000-0000-000000000002")),
            ),
            chartDisplayPreferencesInitialized = true,
        )

        val proto = MapProtoMapper.encodeSession(snapshot)
        val restored = MapProtoMapper.decodeSession(proto)

        assertEquals(snapshot, restored)
        assertEquals(0, proto.placesCount)
        assertEquals(0, proto.savedRoutesCount)
        assertEquals(0, proto.chartPackagesCount)
        assertEquals(false, proto.hasRouteDraft())
    }

    @Test
    fun `legacy session keeps its package id and exposes display migration as pending`() {
        val legacy = MapStateProto.newBuilder()
            .setSchemaVersion(3)
            .setActiveChartPackageId("legacy-chart")
            .build()

        val restored = MapProtoMapper.decodeSession(legacy)

        assertEquals(ChartPackageId("legacy-chart"), restored.activeChartPackageId)
        assertEquals(ChartDisplayPreferences(), restored.chartDisplayPreferences)
        assertEquals(false, restored.chartDisplayPreferencesInitialized)
    }

    @Test
    fun `future session schema is reported and never rewritten as defaults`() {
        val future = MapStateProto.newBuilder().setSchemaVersion(MapProtoMapper.SCHEMA_VERSION + 1).build()
        assertThrows(CorruptionException::class.java) { MapProtoMapper.decodeSession(future) }
    }
}
