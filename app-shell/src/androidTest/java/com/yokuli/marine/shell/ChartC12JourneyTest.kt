package com.yokuli.marine.shell

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import com.yokuli.marine.feature.chart.ChartDestinations
import com.yokuli.marine.feature.chart.ChartDestination
import com.yokuli.marine.feature.chart.ChartLaunchProjector
import com.yokuli.marine.feature.chart.ChartSearchProjection
import com.yokuli.marine.map.domain.ChartPackageImportRequest
import com.yokuli.marine.map.domain.DefaultMapReducer
import com.yokuli.marine.map.domain.GeoPoint
import com.yokuli.marine.map.domain.GpxDuplicateDecision
import com.yokuli.marine.map.domain.GpxImportPlanner
import com.yokuli.marine.map.domain.GpxReader
import com.yokuli.marine.map.domain.GpxWriter
import com.yokuli.marine.map.domain.ImportedTrackDisplayLod
import com.yokuli.marine.map.domain.ManualRouteDraft
import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapCamera
import com.yokuli.marine.map.domain.MapIdGenerator
import com.yokuli.marine.map.domain.MapLibraryLoadState
import com.yokuli.marine.map.domain.MapLibrarySnapshot
import com.yokuli.marine.map.domain.MapLoadResult
import com.yokuli.marine.map.domain.MapSaveState
import com.yokuli.marine.map.domain.MapSessionSnapshot
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.domain.MapSurface
import com.yokuli.marine.map.domain.MapTool
import com.yokuli.marine.map.domain.MeasurementMath
import com.yokuli.marine.map.domain.MonotonicTime
import com.yokuli.marine.map.domain.NoSourcePositionPort
import com.yokuli.marine.map.domain.ObservationIdentity
import com.yokuli.marine.map.domain.ObservationSource
import com.yokuli.marine.map.domain.ObservationValidity
import com.yokuli.marine.map.domain.OfflineCoverageFingerprint
import com.yokuli.marine.map.domain.OfflineCoverageRequest
import com.yokuli.marine.map.domain.PlaceCategory
import com.yokuli.marine.map.domain.PositionAvailability
import com.yokuli.marine.map.domain.PositionObservation
import com.yokuli.marine.map.domain.SavedPlace
import com.yokuli.marine.map.domain.VesselMarkerStyle
import com.yokuli.marine.map.domain.PositionRenderPolicy
import com.yokuli.marine.map.offline.AndroidMbTilesRepository
import com.yokuli.marine.map.offline.InstallCheckpoint
import com.yokuli.marine.map.offline.OfflineMapInstanceMetrics
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.engine.LauncherAction
import com.yokuli.shell.engine.ShellVisualSurface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ChartC12JourneyTest {
    @get:Rule
    val compose = createAndroidComposeRule<ShellActivity>()

    @Test
    fun journeyJ01OfflinePackagePlaceBridgeAndSearchDetail() = runBlocking {
        awaitMapReady()
        val viewModel = currentViewModel()
        val application = currentApplication()
        assertTrue(application.positionPort === NoSourcePositionPort)

        val source = File(application.cacheDir, "c12-j01.mbtiles")
        createRasterMbTiles(source, Color.rgb(31, 115, 179))
        val candidate = application.chartPackageRepository.inspect(source.toURI().toString())
        val installed = application.chartPackageRepository.commit(
            ChartPackageImportRequest(
                candidate.stagedImportId,
                "C12 harbour fixture",
                "Test fixture",
                "Test only",
                "C12 instrumentation",
                "1",
            ),
        )
        val installedPackages = application.chartPackageRepository.listInstalled()
        viewModel.mapStore.dispatch(MapAction.ChartPackagesChanged(installedPackages))
        viewModel.mapStore.dispatch(MapAction.SelectChartPackage(installed.id))
        viewModel.mapStore.dispatch(MapAction.CameraChanged(MapCamera(GeoPoint(0.0, 0.0), 0.0, 0.0)))

        compose.onNodeWithTag("tile-chart").performClick()
        awaitDisplayed("chart-surface-maplibre")
        compose.waitUntil(15_000) {
            currentMapState().activeChartPackageId == installed.id &&
                currentMapState().renderer.generation != null
        }

        val name = "C12 离线锚地"
        viewModel.mapStore.dispatch(
            MapAction.CreatePlace(
                point = GeoPoint(-36.8485, 174.7633),
                name = name,
                notes = "离线图面核对",
                category = PlaceCategory.ANCHORAGE,
                tags = listOf("c12", "offline"),
            ),
        )
        var placeId = ""
        compose.waitUntil(15_000) {
            currentMapState().places.firstOrNull { it.name == name }?.also { placeId = it.id } != null &&
                currentMapState().saveState == MapSaveState.SAVED
        }

        compose.onNodeWithTag("virtual-key-bridge").performClick()
        awaitDisplayed("start-screen")
        compose.onNodeWithTag("virtual-key-search").performClick()
        awaitDisplayed("launcher-search-field")
        compose.onNodeWithTag("launcher-search-field").performTextInput("离线锚地")
        val token = ChartDestinations.place(placeId).value.removePrefix("chart.")
        compose.onNodeWithTag("search-result-chart-$token").assertIsDisplayed().performClick()
        awaitDisplayed("map-place-detail-$placeId")

        val restored = currentMapState().places.single { it.id == placeId }
        assertEquals("离线图面核对", restored.notes)
        assertEquals(PlaceCategory.ANCHORAGE, restored.category)
        assertFalse(currentMapState().navigationActive)
    }

    @Test
    fun journeyJ02MeasurementEditUndoRedoConvertAndSaveRoute() {
        var nextId = 0
        val reducer = DefaultMapReducer(MapIdGenerator { namespace -> "$namespace-c12-${++nextId}" })
        var state = MapState(libraryLoadState = MapLibraryLoadState.READY_EMPTY)
        fun dispatch(action: MapAction) { state = reducer.reduce(state, action).state }

        val first = GeoPoint(-36.8485, 174.7633)
        val middle = GeoPoint(-36.82, 174.80)
        val last = GeoPoint(-36.7867, 174.86)
        dispatch(MapAction.SelectTool(MapTool.MEASURE))
        dispatch(MapAction.AddPoint(first))
        dispatch(MapAction.AddPoint(last))
        dispatch(MapAction.InsertMeasurementPoint(1, middle))
        val threePointSummary = MeasurementMath.summarize(requireNotNull(state.measurementDraft))
        assertEquals(2, threePointSummary.segments.size)
        assertTrue(threePointSummary.totalDistanceMeters > 0.0)
        assertTrue(threePointSummary.segments.all { it.initialBearingTrueDegrees != null })

        dispatch(MapAction.UndoMeasurementEdit)
        assertEquals(listOf(first, last), state.measurementDraft?.points)
        dispatch(MapAction.RedoMeasurementEdit)
        assertEquals(listOf(first, middle, last), state.measurementDraft?.points)
        dispatch(MapAction.ConvertMeasurementToManualRoute("C12 route"))
        dispatch(MapAction.SaveRoutePlan)
        dispatch(MapAction.PersistenceAck(state.libraryRevision))

        assertEquals(1, state.savedRoutes.size)
        assertEquals(3, state.savedRoutes.single().waypoints.size)
        assertEquals(null, state.savedRoutes.single().plannedSpeedKnots)
        assertFalse(state.navigationActive)
    }

    @Test
    fun journeyJ03TwoDraftsSearchBackBridgeAndDurableRestoreModel() {
        val camera = MapCamera(GeoPoint(-36.81, 174.82), 13.0, 24.0)
        val place = SavedPlace("c12-place", "C12 search harbour", GeoPoint(-36.80, 174.83), revision = 2L)
        val draftA = ManualRouteDraft(
            id = "draft-a",
            revision = 2L,
            name = "A",
            waypoints = listOf(GeoPoint(-36.8, 174.7), GeoPoint(-36.9, 174.8)),
        )
        val draftB = ManualRouteDraft(
            id = "draft-b",
            revision = 3L,
            name = "B",
            waypoints = listOf(GeoPoint(-36.7, 174.6), GeoPoint(-36.75, 174.72)),
        )
        val load = MapLoadResult.Ready(
            MapSessionSnapshot(camera = camera, activeRouteDraftId = draftB.id),
            MapLibrarySnapshot(revision = 9L, places = listOf(place), routeDrafts = listOf(draftA, draftB)),
        )
        val restored = DefaultMapReducer().reduce(
            MapState(libraryLoadState = MapLibraryLoadState.LOADING),
            MapAction.Restore(load),
        ).state

        assertEquals(listOf("draft-a", "draft-b"), restored.routeDrafts.map { it.id })
        assertEquals("draft-b", restored.activeRouteDraftId)
        assertEquals(camera, restored.camera)
        val result = ChartSearchProjection.search(restored, "search harbour").single()
        val destination = requireNotNull(ChartDestinations.parse(result.token))
        assertEquals(ChartDestination.Place(place.id), destination)
        val opened = DefaultMapReducer().reduce(restored, requireNotNull(ChartLaunchProjector.action(destination, restored))).state
        val backed = DefaultMapReducer().reduce(opened, MapAction.CloseSurface).state
        assertEquals(MapSurface.Root, backed.surface)
        assertEquals(2, backed.routeDrafts.size)
        assertFalse(backed.navigationActive)
    }

    @Test
    fun journeyJ04GpxCancelConfirmSegmentedTrackAndIndependentRoundTrip() {
        val preview = GpxReader().inspect(GPX.byteInputStream())
        val cancelled = MapState(libraryLoadState = MapLibraryLoadState.READY_EMPTY)
        assertTrue(cancelled.librarySnapshot().isEmpty)

        var nextId = 0
        val batch = GpxImportPlanner.materialize(
            preview,
            GpxDuplicateDecision.NEW_IMPORT,
            MapIdGenerator { namespace -> "$namespace-c12-${++nextId}" },
            nowMillis = 1_700_000_000_000L,
        )
        val reducer = DefaultMapReducer()
        var imported = reducer.reduce(cancelled, MapAction.ImportGpxBatch(batch)).state
        imported = reducer.reduce(imported, MapAction.PersistenceAck(imported.libraryRevision)).state
        val track = imported.importedTracks.single()
        assertEquals(2, track.segments.size)
        assertEquals(2, ImportedTrackDisplayLod.sample(track, 4.0).size)

        val output = ByteArrayOutputStream()
        GpxWriter.writeTrack(track, output)
        val independentlyRead = GpxReader().inspect(output.toByteArray().inputStream())
        assertEquals(1, independentlyRead.tracks.size)
        assertEquals(2, independentlyRead.tracks.single().segments.size)
        assertFalse(imported.navigationActive)
    }

    @Test
    fun journeyJ05VersionInstallCrashRecoveryRollbackAndCoverageInvalidation() = runBlocking {
        val application = currentApplication()
        val root = File(application.cacheDir, "c12-j05").also { it.deleteRecursively(); it.mkdirs() }
        val packages = File(root, "packages")
        val v1Source = File(root, "v1.mbtiles")
        val v2Source = File(root, "v2.mbtiles")
        createRasterMbTiles(v1Source, Color.rgb(20, 100, 180))
        createRasterMbTiles(v2Source, Color.rgb(220, 90, 35))

        val initial = AndroidMbTilesRepository(application.contentResolver, packages)
        val v1Candidate = initial.inspect(v1Source.toURI().toString())
        val v1 = initial.commit(v1Candidate.request("C12 V1", "1"))
        val crashing = AndroidMbTilesRepository(
            application.contentResolver,
            packages,
            installCheckpoint = { if (it == InstallCheckpoint.AFTER_PUBLISH) error("simulated process death") },
        )
        val v2Candidate = crashing.inspect(v2Source.toURI().toString())
        runCatching {
            crashing.commit(v2Candidate.request("C12 V2", "2", v1.logicalId))
        }

        val reopened = AndroidMbTilesRepository(application.contentResolver, packages)
        val usable = reopened.listInstalled().single()
        assertEquals(v1.logicalId, usable.logicalId)
        assertTrue(File(android.net.Uri.parse(usable.localUri).path!!).isFile)
        assertFalse(File(packages, ".install-journal.properties").exists())

        val route = listOf(GeoPoint(-36.85, 174.76), GeoPoint(-36.84, 174.77))
        val before = OfflineCoverageFingerprint.of(
            OfflineCoverageRequest("route", 1L, route, listOf(v1.versionId), 0),
        )
        val after = OfflineCoverageFingerprint.of(
            OfflineCoverageRequest("route", 1L, route, listOf(v2Candidate.versionId), 0),
        )
        assertNotEquals(before, after)
        assertNotNull(reopened.rollback(v1.logicalId) ?: usable)
        root.deleteRecursively()
        Unit
    }

    @Test
    fun journeyJ06ThreeTileSizesNoSourceTransitionsAndMapResourceBound() {
        val viewModel = currentViewModel()
        lateinit var reset: Job
        compose.activityRule.scenario.onActivity { reset = viewModel.resetLauncher() }
        runBlocking { reset.join() }
        viewModel.engine.dispatch(LauncherAction.ShowDesktop)
        compose.waitUntil(10_000) { viewModel.engine.state.value.surface == ShellVisualSurface.Desktop }
        awaitDisplayed("tile-chart")

        val observedSizes = linkedSetOf<MarineTileSize>()
        compose.onNodeWithTag("tile-chart").performTouchInput { longClick() }
        awaitDisplayed("resize-selected-tile")
        repeat(3) {
            val previous = viewModel.engine.state.value.start.document.placements.single { it.entryId.value == "chart" }.size
            compose.onNodeWithTag("resize-selected-tile").performClick()
            compose.waitUntil(10_000) {
                viewModel.engine.state.value.start.document.placements.single { it.entryId.value == "chart" }.size != previous &&
                    viewModel.engine.state.value.start.activeTransaction == null
            }
            observedSizes += viewModel.engine.state.value.start.document.placements.single { it.entryId.value == "chart" }.size
        }
        assertEquals(
            setOf(MarineTileSize.ICON_1X1, MarineTileSize.STANDARD_2X2, MarineTileSize.WIDE_4X2),
            observedSizes,
        )
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitDisplayed("start-screen")

        compose.waitUntil(10_000) { OfflineMapInstanceMetrics.liveCount == 0 }
        OfflineMapInstanceMetrics.resetForTest()
        compose.onNodeWithTag("tile-chart").performClick()
        awaitDisplayed("chart-surface-maplibre")
        compose.waitUntil(10_000) { OfflineMapInstanceMetrics.liveCount == 1 }

        val source1 = ObservationSource("c12-test", "epoch-1")
        val now1 = MonotonicTime("boot-c12", 10_000L)
        viewModel.mapStore.dispatch(MapAction.PositionSourceConnected(source1))
        viewModel.mapStore.dispatch(MapAction.ObservePosition(source1.fix("same", 1L, now1), now1))
        viewModel.mapStore.dispatch(MapAction.PositionSourceDisconnected(source1, MonotonicTime("boot-c12", 11_000L)))
        compose.waitUntil(10_000) { currentMapState().position.availability == PositionAvailability.STALE }
        assertEquals(VesselMarkerStyle.HISTORICAL, PositionRenderPolicy.resolve(currentMapState().position).markerStyle)

        val source2 = ObservationSource("c12-test", "epoch-2")
        val now2 = MonotonicTime("boot-c12", 12_000L)
        viewModel.mapStore.dispatch(MapAction.PositionSourceConnected(source2))
        viewModel.mapStore.dispatch(MapAction.ObservePosition(source2.fix("same", 1L, now2), now2))
        compose.waitUntil(10_000) { currentMapState().position.availability == PositionAvailability.FRESH }
        compose.onNodeWithTag("virtual-key-bridge").performClick()
        awaitDisplayed("start-screen")
        compose.waitUntil(10_000) { OfflineMapInstanceMetrics.liveCount == 0 }
        assertEquals(1, OfflineMapInstanceMetrics.peakLiveCount)
        assertFalse(currentMapState().navigationActive)
    }

    private fun currentViewModel(): ShellViewModel {
        lateinit var value: ShellViewModel
        compose.activityRule.scenario.onActivity { value = ViewModelProvider(it)[ShellViewModel::class.java] }
        return value
    }

    private fun currentApplication(): ShellApplication {
        lateinit var value: ShellApplication
        compose.activityRule.scenario.onActivity { value = it.application as ShellApplication }
        return value
    }

    private fun currentMapState(): MapState = currentViewModel().mapStore.state.value

    private fun awaitMapReady() {
        compose.waitUntil(15_000) {
            currentMapState().libraryLoadState in setOf(MapLibraryLoadState.READY, MapLibraryLoadState.READY_EMPTY)
        }
    }

    private fun awaitDisplayed(tag: String) {
        compose.waitUntil(15_000) {
            runCatching { compose.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess
        }
    }

    private fun createRasterMbTiles(file: File, color: Int) {
        file.delete()
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(0f, 0f, 256f, 256f, Paint().apply { this.color = color })
        val bytes = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        bitmap.recycle()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE metadata (name TEXT, value TEXT)")
            database.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
            mapOf(
                "name" to "C12 fixture",
                "bounds" to "-180,-85,180,85",
                "minzoom" to "0",
                "maxzoom" to "0",
                "format" to "png",
                "scheme" to "tms",
            ).forEach { (name, value) ->
                database.execSQL("INSERT INTO metadata(name,value) VALUES(?,?)", arrayOf(name, value))
            }
            database.execSQL(
                "INSERT INTO tiles(zoom_level,tile_column,tile_row,tile_data) VALUES(0,0,0,?)",
                arrayOf(bytes),
            )
        }
    }

    private fun com.yokuli.marine.map.domain.ChartPackageCandidate.request(
        name: String,
        version: String,
        replace: com.yokuli.marine.map.domain.ChartPackageLogicalId? = null,
    ) = ChartPackageImportRequest(
        stagedImportId,
        name,
        "Test fixture",
        "Test only",
        "C12 instrumentation",
        version,
        replace,
    )

    private fun ObservationSource.fix(id: String, sequence: Long, received: MonotonicTime) = PositionObservation(
        identity = ObservationIdentity(
            source = this,
            observationId = id,
            sequence = sequence,
            receivedAt = received,
        ),
        point = GeoPoint(-36.81, 174.82),
        validity = ObservationValidity.VALID,
        horizontalAccuracyMeters = 8.0,
    )

    private companion object {
        const val GPX = """<?xml version="1.0"?><gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
            <wpt lat="-36.8" lon="174.7"><name>泊位</name></wpt>
            <rte><name>Route</name><rtept lat="-36.8" lon="174.7"/><rtept lat="-36.9" lon="174.8"/></rte>
            <trk><name>Track</name><trkseg><trkpt lat="-36.8" lon="174.7"/><trkpt lat="-36.9" lon="174.8"/></trkseg><trkseg><trkpt lat="-36.7" lon="174.6"/><trkpt lat="-36.75" lon="174.65"/></trkseg></trk>
        </gpx>"""
    }
}
