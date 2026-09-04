package com.yokuli.shell.engine

import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.layout.AdaptiveTilePacker
import com.yokuli.shell.engine.layout.Spacer
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TileDocumentEntry
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTilePackerTest {
    private val profile = WpReferenceProfiles.PHONE_PORTRAIT_4COL

    @Test
    fun `mixed marine tiles pack deterministically without overlap`() {
        val document = document(
            tile("chart", MarineTileSize.LARGE_4X4, 0),
            tile("wind", MarineTileSize.COMPACT_2X1, 1),
            tile("depth", MarineTileSize.STANDARD_2X2, 2),
            tile("settings", MarineTileSize.ICON_1X1, 3),
            tile("status", MarineTileSize.TALL_2X4, 4),
        )

        val first = AdaptiveTilePacker.pack(document, columns = 4)
        val second = AdaptiveTilePacker.pack(document, columns = 4)

        assertEquals(first, second)
        assertEquals(document.placements.map { it.tileId }, first.tiles.map { it.entry.tileId })
        assertNoOverlap(first.occupiedCellsByItem.values)
    }

    @Test
    fun `one durable document repacks for four and six columns`() {
        val document = document(
            tile("a", MarineTileSize.WIDE_4X2, 0),
            tile("b", MarineTileSize.STANDARD_2X2, 1),
            tile("c", MarineTileSize.STANDARD_2X2, 2),
        )

        val phone = AdaptiveTilePacker.pack(document, columns = 4)
        val landscape = AdaptiveTilePacker.pack(document, columns = 6)

        assertEquals(document, document.copy())
        assertNotEquals(phone.tiles.map { it.cell }, landscape.tiles.map { it.cell })
        assertNoOverlap(phone.occupiedCellsByItem.values)
        assertNoOverlap(landscape.occupiedCellsByItem.values)
    }

    @Test
    fun `insertion changes rank order and vacated space is repacked`() {
        val document = document(
            tile("a", MarineTileSize.STANDARD_2X2, 0),
            tile("b", MarineTileSize.STANDARD_2X2, 1),
            tile("c", MarineTileSize.WIDE_4X2, 2),
        )

        val moved = AdaptiveTilePacker.insert(document, TileInstanceId("tile-c"), insertionIndex = 0)
        val packed = AdaptiveTilePacker.pack(moved, columns = 4)

        assertEquals(listOf("tile-c", "tile-a", "tile-b"), moved.placements.map { it.tileId.value })
        assertEquals(listOf(0L, 1024L, 2048L), moved.placements.map { it.rank })
        assertEquals(0, packed.tiles.first().cell.row)
        assertEquals(0, packed.tiles.first().cell.column)
        assertEquals(4, packed.documentHeightRows)
    }

    @Test
    fun `only explicit spacer reserves intentional whitespace`() {
        val withoutSpacer = document(
            tile("a", MarineTileSize.COMPACT_2X1, 0),
            tile("b", MarineTileSize.COMPACT_2X1, 1),
        )
        val withSpacer = withoutSpacer.copy(
            spacers = listOf(
                Spacer(
                    spacerId = TileInstanceId("spacer-weather-gap"),
                    size = MarineTileSize.COMPACT_2X1,
                    rank = 1,
                ),
            ),
            placements = withoutSpacer.placements.mapIndexed { index, entry ->
                entry.copy(rank = if (index == 0) 0 else 2)
            },
        )

        assertEquals(1, AdaptiveTilePacker.pack(withoutSpacer, 4).documentHeightRows)
        val packedWithSpacer = AdaptiveTilePacker.pack(withSpacer, 4)
        assertEquals(1, packedWithSpacer.spacers.size)
        assertEquals(2, packedWithSpacer.documentHeightRows)
    }

    @Test
    fun `seeded mixed documents always remain bounded and collision free`() {
        val random = Random(825)
        repeat(100) { sample ->
            val entries = List(30) { index ->
                tile(
                    id = "$sample-$index",
                    size = MarineTileSize.entries[random.nextInt(MarineTileSize.entries.size)],
                    rank = index.toLong(),
                )
            }
            for (columns in listOf(4, 6)) {
                val packed = AdaptiveTilePacker.pack(document(*entries.toTypedArray()), columns)
                assertTrue(packed.tiles.all { it.cell.column >= 0 && it.cell.column + it.entry.size.columns <= columns })
                assertNoOverlap(packed.occupiedCellsByItem.values)
            }
        }
    }

    private fun tile(id: String, size: MarineTileSize, rank: Long) = TileDocumentEntry(
        tileId = TileInstanceId("tile-$id"),
        entryId = LauncherEntryId(id),
        size = size,
        rank = rank,
    )

    private fun document(vararg entries: TileDocumentEntry) = StartDocument(
        schemaVersion = 2,
        profileId = profile.id,
        defaultLayoutVersion = 2,
        placements = entries.toList(),
    )

    private fun assertNoOverlap(cellSets: Collection<Set<com.yokuli.shell.engine.layout.GridCell>>) {
        val occupied = mutableSetOf<com.yokuli.shell.engine.layout.GridCell>()
        cellSets.forEach { cells -> assertTrue(cells.all(occupied::add)) }
    }
}
