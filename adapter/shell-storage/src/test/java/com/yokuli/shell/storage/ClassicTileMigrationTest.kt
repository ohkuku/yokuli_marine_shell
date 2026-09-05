package com.yokuli.shell.storage

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
