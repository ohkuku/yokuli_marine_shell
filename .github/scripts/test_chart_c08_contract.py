import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


class ChartC08ContractTest(unittest.TestCase):
    def test_domain_models_keep_tracks_segmented_and_read_only(self):
        model = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapModel.kt").read_text()
        interchange = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/GpxInterchange.kt").read_text()
        for symbol in ("ImportedTrack", "ImportedTrackSegment", "ImportedTrackPoint", "GpxImportRecord"):
            self.assertIn(symbol, model + interchange)
        self.assertIn("segments", model + interchange)
        self.assertIn("READ_ONLY", model + interchange)

    def test_reader_is_streaming_bounded_and_disables_external_xml(self):
        source = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/GpxInterchange.kt").read_text()
        for symbol in (
            "disallow-doctype-decl",
            "external-general-entities",
            "external-parameter-entities",
            "FEATURE_SECURE_PROCESSING",
            "MAX_FILE_BYTES",
            "MAX_TOTAL_POINTS",
            "MAX_ROUTE_POINTS",
            "MAX_TEXT_CHARS",
        ):
            self.assertIn(symbol, source)
        self.assertNotIn("readBytes()", source)
        self.assertNotIn("readText()", source)

    def test_import_requires_preview_and_explicit_duplicate_choice(self):
        source = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/GpxInterchange.kt").read_text()
        ui = (ROOT / "feature/chart/src/main/java/com/yokuli/marine/feature/chart/GpxImportCoordinator.kt").read_text()
        for symbol in ("GpxImportPreview", "GpxDuplicateDecision", "ImportAsCopy", "ConfirmImport", "Cancel"):
            self.assertIn(symbol, source + ui)
        self.assertIn("MapAction.ImportGpxBatch", ui)

    def test_storage_has_explicit_v4_migration_for_tracks_and_import_records(self):
        database = (ROOT / "adapter/map-storage/src/main/java/com/yokuli/marine/map/storage/MapLibraryDatabase.kt").read_text()
        persistence = (ROOT / "adapter/map-storage/src/main/java/com/yokuli/marine/map/storage/RoomMapPersistence.kt").read_text()
        self.assertIn("version = 4", database)
        self.assertIn("MIGRATION_3_4", database + persistence)
        for table in ("imported_tracks", "imported_track_segments", "imported_track_points", "gpx_import_records"):
            self.assertIn(table, database)
        self.assertNotIn("fallbackToDestructiveMigration", database + persistence)

    def test_gpx_writer_preserves_track_type_and_app_exposes_truthful_outcomes(self):
        source = (ROOT / "core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/GpxInterchange.kt").read_text()
        activity = (ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt").read_text()
        strings = (ROOT / "feature/chart/src/main/res/values/strings.xml").read_text()
        self.assertIn("writeTrack", source)
        self.assertIn("<trk>", source)
        self.assertIn("<trkseg>", source)
        self.assertIn("OpenDocument", activity)
        self.assertIn("CreateDocument", activity)
        for phrase in ("预览", "作为副本", "用户取消", "写入失败", "不保留未知扩展"):
            self.assertIn(phrase, strings)


if __name__ == "__main__":
    unittest.main()
