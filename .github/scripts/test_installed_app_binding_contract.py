from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
GRAPH = ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt"
ACTIVITY = ROOT / "app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt"
UI_CONTRACT = ROOT / "feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt"


class InstalledAppBindingContractTest(unittest.TestCase):
    def test_one_binding_owns_catalog_launch_visual_and_internal_host(self):
        graph = GRAPH.read_text()
        self.assertIn("data class InstalledAppBinding(", graph)
        for field in (
            "catalogContribution:",
            "launchRegistrations:",
            "visualContributions:",
            "internalAppHost:",
        ):
            self.assertIn(field, graph)
        self.assertIn("productionInstalledApps = listOf(", graph)
        self.assertEqual(2, graph.count("InstalledAppBinding(" ) - 1)

    def test_every_runtime_registry_is_derived_from_the_binding_list(self):
        graph = GRAPH.read_text()
        for derived in (
            "productionInstalledApps.map { it.catalogContribution }",
            "productionInstalledApps.flatMap { it.launchRegistrations.entries }",
            "productionInstalledApps.flatMap { it.visualContributions }",
            "productionInstalledApps.map { it.internalAppHost }",
        ):
            self.assertIn(derived, graph)

    def test_desktop_visual_mapping_has_no_product_id_switch(self):
        ui = UI_CONTRACT.read_text()
        self.assertNotIn('entryId.value == "chart"', ui)
        self.assertNotIn('entryId.value == "settings"', ui)
        self.assertNotIn("catalog.entries.single", ui)
        self.assertIn("visualContributions", ui)

    def test_activity_uses_the_derived_host_resolver(self):
        activity = ACTIVITY.read_text()
        self.assertNotIn("DefaultInternalAppHostResolver(", activity)
        self.assertNotIn("InternalAppHost(", activity)
        self.assertIn("productionInternalAppHostResolver", activity)


if __name__ == "__main__":
    unittest.main()
