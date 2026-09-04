package com.yokuli.marine.shell

import com.yokuli.marine.core.model.TileId
import com.yokuli.marine.core.model.TileSize
import com.yokuli.marine.core.shell.LauncherRegistry
import com.yokuli.marine.core.shell.engine.layout.DesktopDocument
import com.yokuli.marine.core.shell.engine.layout.GridCell
import com.yokuli.marine.core.shell.engine.layout.TilePlacement
import com.yokuli.marine.feature.chart.ChartDestinations
import com.yokuli.marine.feature.chart.ChartShellContribution
import com.yokuli.marine.feature.settings.SettingsDestinations
import com.yokuli.marine.feature.settings.SettingsShellContribution

/**
 * 中文：生产入口只安装已经实现且能讲真话的功能。
 * English: Production installs only implemented features whose UI can remain truthful.
 */
val productionContributions = listOf(ChartShellContribution, SettingsShellContribution)

val productionRegistry = LauncherRegistry(productionContributions)

val defaultDesktopDocument = DesktopDocument(
    version = 1,
    columns = 4,
    placements = listOf(
        TilePlacement(
            tileId = TileId("tile-chart"),
            entryId = ChartDestinations.EntryId,
            size = TileSize.WIDE_4X2,
            cell = GridCell(column = 0, row = 0),
        ),
        TilePlacement(
            tileId = TileId("tile-settings"),
            entryId = SettingsDestinations.EntryId,
            size = TileSize.SMALL_1X1,
            cell = GridCell(column = 0, row = 2),
        ),
    ),
)
