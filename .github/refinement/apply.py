from pathlib import Path
root = Path.cwd()
def put(p,s):
 f=root/p; f.parent.mkdir(parents=True,exist_ok=True); f.write_text(s)
def edit(p,a,b):
 f=root/p;s=f.read_text();assert a in s,(p,a[:100]);f.write_text(s.replace(a,b))
put('core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineTile.kt','''package com.yokuli.shell.contract

enum class MarineTileContentLayout {
    ICON,
    STANDARD_FACTS,
    WIDE_PREVIEW,
}

/** Width × height in the smallest WP8 grid cell. No product-specific extra shapes. */
enum class MarineTileSize(
    val columns: Int,
    val rows: Int,
    val contentLayout: MarineTileContentLayout,
) {
    ICON_1X1(1, 1, MarineTileContentLayout.ICON),
    STANDARD_2X2(2, 2, MarineTileContentLayout.STANDARD_FACTS),
    WIDE_4X2(4, 2, MarineTileContentLayout.WIDE_PREVIEW);

    companion object {
        /**
         * 中文：仅在持久化边界识别旧形状；保留磁贴身份、顺序和分组，不重置桌面。
         * English: Decode retired shapes only at the storage boundary, preserving identity/order.
         */
        fun fromPersistedName(name: String): MarineTileSize? = when (name) {
            "COMPACT_2X1" -> STANDARD_2X2
            "TALL_2X4", "LARGE_4X4" -> WIDE_4X2
            else -> entries.firstOrNull { it.name == name }
        }
    }
}

enum class TilePresentationKind(val allowsAutomaticCycling: Boolean = false) {
    STATIC,
    ICONIC,
    METRIC,
    STATUS,
    CYCLE(allowsAutomaticCycling = true),
    MAP_PREVIEW,
    SAFETY,
}
''')
p='feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartShellContribution.kt'
edit(p,'                MarineTileSize.STANDARD_2X2,\n                MarineTileSize.WIDE_4X2,\n                MarineTileSize.LARGE_4X4,','                MarineTileSize.ICON_1X1,\n                MarineTileSize.STANDARD_2X2,\n                MarineTileSize.WIDE_4X2,')
p='feature/settings/src/main/java/com/yokuli/marine/feature/settings/SettingsShellContribution.kt'
edit(p,'                MarineTileSize.COMPACT_2X1,\n','')
p='feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartLauncherPresentation.kt'
edit(p,'        tileRenderers = mapOf(\n','        tileRenderers = mapOf(\n            MarineTileSize.ICON_1X1 to LauncherTileRenderer { context ->\n                Box(context.modifier.fillMaxSize(), contentAlignment = Alignment.Center) {\n                    ChartLauncherIcon(context.contentColor, Modifier.size(34.dp))\n                }\n            },\n')
edit(p,'            MarineTileSize.LARGE_4X4 to LauncherTileRenderer { context ->\n                ChartLargeTile(context, title, headline, detail)\n            },\n','')
f=root/p;s=f.read_text();start=s.index('@Composable\nprivate fun ChartLargeTile');end=s.index('@Composable\nprivate fun ChartLauncherIcon',start);f.write_text(s[:start]+s[end:])
p='feature/settings/src/main/java/com/yokuli/marine/feature/settings/SettingsLauncherPresentation.kt'
edit(p,'            MarineTileSize.COMPACT_2X1 to LauncherTileRenderer { context ->\n                SettingsCompactTile(context, title, headline)\n            },\n','')
f=root/p;s=f.read_text();start=s.index('@Composable\nprivate fun SettingsCompactTile');end=s.index('@Composable\nprivate fun SettingsStandardTile',start);f.write_text(s[:start]+s[end:])
p='feature/shell-lab/src/main/java/com/yokuli/marine/feature/shell/lab/ShellLabActivity.kt'
for old in ['COMPACT_2X1','TALL_2X4','LARGE_4X4']:edit(p,f'    MarineTileSize.{old},\n','')
p='adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherProtoMapper.kt'
edit(p,'MarineTileSize.entries.firstOrNull { it.name == placement.size }','MarineTileSize.fromPersistedName(placement.size)')
edit(p,'MarineTileSize.entries.firstOrNull { it.name == spacer.size }','MarineTileSize.fromPersistedName(spacer.size)')
p='adapter/shell-storage/src/test/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistenceTest.kt'
edit(p,'MarineTileSize.COMPACT_2X1','MarineTileSize.STANDARD_2X2')
p='ui/shell-compose/src/test/java/com/yokuli/shell/compose/LauncherPresentationTest.kt'
edit(p,'MarineTileSize.LARGE_4X4','MarineTileSize.ICON_1X1')
p='core/shell-engine/src/test/kotlin/com/yokuli/shell/engine/AdaptiveTilePackerTest.kt'
for old,new in [('LARGE_4X4','WIDE_4X2'),('TALL_2X4','STANDARD_2X2'),('COMPACT_2X1','STANDARD_2X2')]:edit(p,f'MarineTileSize.{old}',f'MarineTileSize.{new}')
edit(p,'assertEquals(1, AdaptiveTilePacker.pack(withoutSpacer, 4).documentHeightRows)','assertEquals(2, AdaptiveTilePacker.pack(withoutSpacer, 4).documentHeightRows)')
edit(p,'assertEquals(2, packedWithSpacer.documentHeightRows)','assertEquals(4, packedWithSpacer.documentHeightRows)')
p='core/shell-engine/src/test/kotlin/com/yokuli/shell/engine/EditInteractionTest.kt'
f=root/p;s=f.read_text();start=s.index('    @Test\n    fun sixSizeResizeCycle');end=s.index('    @Test\n    fun resizeFollowsOnly',start)
s=s[:start]+'''    @Test
    fun threeSizeResizeCycleIsExactAndEveryStepIsImmediate() {
        val first = resize(initial())
        val second = resize(first)
        val third = resize(second)
        assertEquals(MarineTileSize.STANDARD_2X2, first.start.document.size("a"))
        assertEquals(MarineTileSize.WIDE_4X2, second.start.document.size("a"))
        assertEquals(MarineTileSize.ICON_1X1, third.start.document.size("a"))
        listOf(first, second, third).forEach { state ->
            assertTrue(state.start.activeTransaction == null)
            assertEquals(StartInteractionState.EditIdle(TileInstanceId("tile-a")), state.start.interaction)
        }
    }

'''+s[end:]
s=s.replace('MarineTileSize.COMPACT_2X1','MarineTileSize.STANDARD_2X2').replace('MarineTileSize.LARGE_4X4','MarineTileSize.STANDARD_2X2')
f.write_text(s)
p='app-shell/src/androidTest/java/com/yokuli/marine/shell/ShellActivityStoryTest.kt'
f=root/p;s=f.read_text();start=s.index('    @Test\n    fun chartResizeCommits');end=s.index('    @Test\n    fun smallTile',start)
s=s[:start]+'''    @Test
    fun chartResizeCommitsOnOneTapWithoutConfirmationUi() {
        compose.onNodeWithTag("tile-chart").performTouchInput { longClick() }
        awaitExists("resize-selected-tile")
        val sizes = listOf(
            com.yokuli.shell.contract.MarineTileSize.ICON_1X1,
            com.yokuli.shell.contract.MarineTileSize.STANDARD_2X2,
            com.yokuli.shell.contract.MarineTileSize.WIDE_4X2,
        )
        sizes.forEach { expected ->
            compose.onNodeWithTag("resize-selected-tile").performTouchInput { click(center) }
            compose.waitUntil(5_000) {
                var matches = false
                compose.activityRule.scenario.onActivity { activity ->
                    val state = ViewModelProvider(activity)[ShellViewModel::class.java].engine.state.value
                    matches = state.start.document.placements.single { it.entryId.value == "chart" }.size == expected &&
                        state.start.activeTransaction == null
                }
                matches
            }
            compose.onNodeWithTag("resize-selected-tile").assertIsDisplayed()
            compose.onNodeWithTag("commit-tile-resize").assertDoesNotExist()
            compose.onNodeWithTag("cancel-tile-resize").assertDoesNotExist()
        }
    }

'''+s[end:]
s=s.replace('MarineTileSize.COMPACT_2X1','MarineTileSize.STANDARD_2X2')
s=s.replace('tileBounds.width > tileBounds.height && unpinBounds.center.x > tileBounds.center.x','abs(tileBounds.width - tileBounds.height) <= 1f && unpinBounds.center.x > tileBounds.center.x')
f.write_text(s)
p='.github/scripts/test_launcher_baseline_contract.py'
edit(p,'test_product_correction_extends_the_wp_geometry_with_marine_tile_sizes','test_product_uses_exactly_three_classic_wp8_tile_sizes')
edit(p,'["ICON_1X1", "COMPACT_2X1", "STANDARD_2X2", "WIDE_4X2", "TALL_2X4", "LARGE_4X4"]','["ICON_1X1", "STANDARD_2X2", "WIDE_4X2"]')
p='.github/scripts/test_marine_shell_final_correction_contract.py'
edit(p,'test_marine_tile_contract_has_all_six_sizes','test_marine_tile_contract_exposes_classic_wp8_sizes')
edit(p,'("ICON_1X1", "COMPACT_2X1", "STANDARD_2X2", "WIDE_4X2", "TALL_2X4", "LARGE_4X4")','("ICON_1X1", "STANDARD_2X2", "WIDE_4X2")')
edit(p,'("ICON_1X1", "COMPACT_2X1", "STANDARD_2X2", "WIDE_4X2", "LARGE_4X4")','("ICON_1X1", "STANDARD_2X2", "WIDE_4X2")')
put('core/shell-contract/src/test/kotlin/com/yokuli/shell/contract/ClassicTileSizeTest.kt','''package com.yokuli.shell.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassicTileSizeTest {
    @Test fun onlyClassicShapesCanBeRegisteredOrResized() {
        assertEquals(listOf(1 to 1, 2 to 2, 4 to 2), MarineTileSize.entries.map { it.columns to it.rows })
    }
    @Test fun retiredNamesDecodeToClassicShapesWithoutBecomingSelectable() {
        assertEquals(MarineTileSize.STANDARD_2X2, MarineTileSize.fromPersistedName("COMPACT_2X1"))
        assertEquals(MarineTileSize.WIDE_4X2, MarineTileSize.fromPersistedName("TALL_2X4"))
        assertEquals(MarineTileSize.WIDE_4X2, MarineTileSize.fromPersistedName("LARGE_4X4"))
        MarineTileSize.entries.forEach { assertEquals(it, MarineTileSize.fromPersistedName(it.name)) }
        assertNull(MarineTileSize.fromPersistedName("UNKNOWN_FUTURE_SIZE"))
    }
}
''')
put('adapter/shell-storage/src/test/java/com/yokuli/shell/storage/ClassicTileMigrationTest.kt','''package com.yokuli.shell.storage

import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.storage.proto.LauncherStateProto
import com.yokuli.shell.storage.proto.StartDocumentProto
import com.yokuli.shell.storage.proto.TilePlacementProto
import com.yokuli.shell.storage.proto.SpacerProto
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassicTileMigrationTest {
    @Test fun binaryUpgradePreservesTileIdentityOrderAndGroups() {
        val legacy = StartDocumentProto.newBuilder().setSchemaVersion(2)
            .setDefaultLayoutVersion(2).setProfileId("phone-portrait-4col")
        listOf("COMPACT_2X1", "LARGE_4X4", "TALL_2X4").forEachIndexed { index, size ->
            legacy.addPlacements(TilePlacementProto.newBuilder().setTileId("tile-$index")
                .setEntryId(if (index == 0) "settings" else "chart").setSize(size)
                .setRank((index + 7) * 1024L).setGroupId("my-group"))
        }
        legacy.addSpacers(SpacerProto.newBuilder().setSpacerId("gap").setSize("COMPACT_2X1").setRank(99L))
        val bytes = LauncherStateProto.newBuilder().setSchemaVersion(2).setStartDocument(legacy).build().toByteArray()
        val decoded = LauncherProtoMapper.decode(LauncherStateProto.parseFrom(bytes))
        val document = requireNotNull(decoded.document)
        assertEquals(2, document.schemaVersion)
        assertEquals(listOf("tile-0", "tile-1", "tile-2"), document.placements.map { it.tileId.value })
        assertEquals(listOf(7168L, 8192L, 9216L), document.placements.map { it.rank })
        assertEquals(listOf("my-group", "my-group", "my-group"), document.placements.map { it.groupId })
        assertEquals(listOf(MarineTileSize.STANDARD_2X2, MarineTileSize.WIDE_4X2, MarineTileSize.WIDE_4X2), document.placements.map { it.size })
        assertEquals("gap", document.spacers.single().spacerId.value)
        assertEquals(99L, document.spacers.single().rank)
        assertEquals(decoded, LauncherProtoMapper.decode(LauncherProtoMapper.encode(decoded)))
    }
}
''')
Path('/tmp/refinement-message.txt').write_text('fix(tiles): restore classic WP8 sizes and migrate existing layouts')
