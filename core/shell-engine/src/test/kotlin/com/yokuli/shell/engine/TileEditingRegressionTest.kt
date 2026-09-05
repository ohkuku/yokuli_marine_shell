package com.yokuli.shell.engine

import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.geometry.ProfileId
import com.yokuli.shell.engine.interaction.EditControlRect
import com.yokuli.shell.engine.interaction.ShellOffset
import com.yokuli.shell.engine.interaction.TileDragCoordinates
import com.yokuli.shell.engine.interaction.TileEditControlGeometry
import com.yokuli.shell.engine.layout.AdaptiveTilePacker
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.InsertionSide
import com.yokuli.shell.engine.layout.Spacer
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TileDocumentEntry
import com.yokuli.shell.engine.layout.TileInsertionTarget
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import org.junit.Test

class TileEditingRegressionTest {
    private fun tile(id: String, size: MarineTileSize, rank: Long) =
        TileDocumentEntry(TileInstanceId(id), LauncherEntryId(id), size, rank)
    private fun document(entries: List<TileDocumentEntry>, spacers: List<Spacer> = emptyList()) =
        StartDocument(2, ProfileId("PHONE_PORTRAIT_4COL"), 2, entries, spacers)
    private fun mixed() = document(listOf(
        tile("A", MarineTileSize.STANDARD_2X2, 0), tile("B", MarineTileSize.WIDE_4X2, 1024),
        tile("C", MarineTileSize.ICON_1X1, 2048), tile("D", MarineTileSize.ICON_1X1, 3072),
    ))

    @Test fun visualOrderIsNeverUsedAsRankIndex() {
        val doc = mixed()
        val positions = AdaptiveTilePacker.pack(doc, 4).tiles.associate { it.entry.tileId.value to it.cell }
        check(positions == mapOf("A" to GridCell(0, 0), "B" to GridCell(0, 2), "C" to GridCell(2, 0), "D" to GridCell(3, 0)))
        val moving = TileInstanceId("D")
        val index = AdaptiveTilePacker.insertionIndexForCell(doc, 4, GridCell(0, 2), moving)
        check(index == 1) // B is visually fourth but durably second.
        check(AdaptiveTilePacker.insert(doc, moving, index).placements.map { it.tileId.value } == listOf("A", "D", "B", "C"))
    }

    @Test fun wholeWideRectangleHasOneStableAnchorAndTwoSides() {
        val before = AdaptiveTilePacker.insertionTargetForCell(mixed(), 4, GridCell(0, 3), TileInstanceId("D"))
        val after = AdaptiveTilePacker.insertionTargetForCell(mixed(), 4, GridCell(3, 3), TileInstanceId("D"))
        check(before == TileInsertionTarget(TileInstanceId("B"), InsertionSide.BEFORE))
        check(after == TileInsertionTarget(TileInstanceId("B"), InsertionSide.AFTER))
        check(AdaptiveTilePacker.insertionIndexForTarget(mixed(), after!!, TileInstanceId("D")) == 2)
    }

    @Test fun spacersAndSparseRanksRemainInTheSameOrderDomain() {
        val doc = document(
            listOf(tile("A", MarineTileSize.STANDARD_2X2, 50), tile("B", MarineTileSize.WIDE_4X2, 500), tile("D", MarineTileSize.ICON_1X1, 900)),
            listOf(Spacer(TileInstanceId("gap"), MarineTileSize.ICON_1X1, 100)),
        )
        val target = TileInsertionTarget(TileInstanceId("B"), InsertionSide.BEFORE)
        check(AdaptiveTilePacker.insertionIndexForTarget(doc, target, TileInstanceId("D")) == 2)
        val moved = AdaptiveTilePacker.insert(doc, TileInstanceId("D"), target)
        check(moved.spacers.single().rank < moved.placements.single { it.tileId.value == "D" }.rank)
        check(moved.placements.single { it.tileId.value == "D" }.rank < moved.placements.single { it.tileId.value == "B" }.rank)
    }

    @Test fun stationaryAndOutAndBackDragsAreTrueNoOps() {
        val doc = mixed().copy(placements = mixed().placements.map { it.copy(rank = it.rank + 71) })
        val id = TileInstanceId("D")
        val original = AdaptiveTilePacker.insertionIndexOf(doc, id)
        check(AdaptiveTilePacker.insert(doc, id, original) === doc)
        check(AdaptiveTilePacker.insertionIndexForCell(doc, 4, GridCell(3, 0), id) == original)
        check(AdaptiveTilePacker.insert(doc, id, TileInsertionTarget(TileInstanceId("deleted"), InsertionSide.BEFORE)) === doc)
    }

    @Test fun belowTheDocumentAppendsInDurableNotVisualOrder() {
        check(AdaptiveTilePacker.insertionIndexForCell(mixed(), 4, GridCell(0, 100), TileInstanceId("A")) == 3)
    }

    @Test fun pointerMotionCannotOverwriteConsumedAutoScroll() {
        val start = TileDragCoordinates(ShellOffset(40f, 100f), startScrollPx = 20f)
        val moved = start.movedTo(ShellOffset(55f, 145f))
        check(moved.contentOffset(100f) == ShellOffset(15f, 125f))
        val next = moved.movedTo(ShellOffset(56f, 146f))
        check(next.contentOffset(100f) == ShellOffset(16f, 126f))
        check(next.contentOffset(90f) == ShellOffset(16f, 116f))
        // Screen position is content position minus actual scroll: the same grab point stays under the finger.
        for (scroll in listOf(20f, 99f, 110f, 75f, 20f)) {
            val offset = next.contentOffset(scroll)
            check(80f + offset.y - (scroll - 20f) == 126f)
        }
    }

    @Test fun smallTileControlsAreDisjointAndLeaveTheDragCenterFreeAcrossEdgesAndDensities() {
        for (density in listOf(1f, 1.5f, 2.625f, 3f)) {
            val viewportWidth = 320f * density
            val viewportHeight = 240f * density
            for (column in 0..3) for (y in listOf(-30f, 0f, 17f, 90f, 175f, 215f)) {
                val tile = EditControlRect((16f + column * 74f) * density, y * density,
                    (82f + column * 74f) * density, (y + 66f) * density)
                val controls = checkNotNull(TileEditControlGeometry.resolve(tile, viewportWidth, viewportHeight, 48f * density, true, true))
                val resize = checkNotNull(controls.resize)
                check(!controls.unpin.overlaps(resize))
                val cx = (max(0f, tile.left) + min(viewportWidth, tile.right)) / 2f
                val cy = (max(0f, tile.top) + min(viewportHeight, tile.bottom)) / 2f
                check(!controls.contains(cx, cy))
                listOf(controls.unpin, resize).forEach {
                    check(it.width >= 48f * density && it.height >= 48f * density)
                    check(it.left >= 0f && it.top >= 0f && it.right <= viewportWidth && it.bottom <= viewportHeight)
                    check(it.left % 1f == 0f && it.top % 1f == 0f)
                }
            }
        }
    }

    @Test fun everySupportedSizeHasUsableControlsWithoutInventingAResizeForSingleSizeApps() {
        for (size in MarineTileSize.entries) {
            val tile = EditControlRect(16f, 17f, 16f + size.columns * 74f - 8f, 17f + size.rows * 74f - 8f)
            val controls = checkNotNull(TileEditControlGeometry.resolve(tile, 320f, 240f, 48f, size == MarineTileSize.ICON_1X1, true))
            check(!controls.unpin.overlaps(checkNotNull(controls.resize)))
            val single = checkNotNull(TileEditControlGeometry.resolve(tile, 320f, 240f, 48f, false, false))
            check(single.resize == null)
        }
        check(TileEditControlGeometry.resolve(EditControlRect(16f, -100f, 82f, -34f), 320f, 240f, 48f, true, true) == null)
    }

    @Test fun randomizedMixedSizesStayDeterministicAndNeverOverlapAfterAnchoredMoves() {
        val random = Random(82741)
        repeat(300) {
            val entries = (0 until 18).map {
                tile(
                    "tile-$it",
                    MarineTileSize.entries[random.nextInt(MarineTileSize.entries.size)],
                    it * 1009L,
                )
            }
            val source = document(entries.shuffled(random))
            val moving = entries[random.nextInt(entries.size)].tileId
            val anchor = entries.filterNot { it.tileId == moving }.random(random).tileId
            val target = TileInsertionTarget(anchor, if (random.nextBoolean()) InsertionSide.BEFORE else InsertionSide.AFTER)
            val after = AdaptiveTilePacker.insert(source, moving, target)
            val expectedOrder = entries.map { it.tileId }.filterNot { it == moving }.toMutableList()
            expectedOrder.add(expectedOrder.indexOf(anchor) + if (target.side == InsertionSide.AFTER) 1 else 0, moving)
            check(after.placements.sortedBy { it.rank }.map { it.tileId } == expectedOrder)
            for (columns in listOf(4, 6, 8)) {
                val packed = AdaptiveTilePacker.pack(after, columns)
                check(packed == AdaptiveTilePacker.pack(after, columns))
                val cells = packed.occupiedCellsByItem.values.flatten()
                check(cells.size == cells.toSet().size)
                check(cells.all { it.column in 0 until columns && it.row >= 0 })
            }
        }
    }
}
