package com.yokuli.marine.shell

import com.yokuli.marine.feature.chart.ChartDestinations
import com.yokuli.marine.feature.chart.ChartShellContribution
import com.yokuli.marine.feature.settings.SettingsDestinations
import com.yokuli.marine.feature.settings.SettingsShellContribution
import com.yokuli.shell.android.StaticLauncherHostPort
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.WpTileSize
import com.yokuli.shell.engine.catalog.LauncherCatalog
import com.yokuli.shell.engine.layout.DesktopDocument
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.TilePlacement

/**
 * 中文：生产入口只安装已经实现且能讲真话的功能。
 * English: Production installs only implemented features whose UI can remain truthful.
 */
val productionContributions = listOf(ChartShellContribution, SettingsShellContribution)

val productionCatalog = LauncherCatalog.compose(revision = 1, contributions = productionContributions)

private val SettingsSectionTokens = listOf(
    SettingsDestinations.Overview,
    SettingsDestinations.Appearance,
    SettingsDestinations.StartScreen,
    SettingsDestinations.Map,
    SettingsDestinations.Language,
    SettingsDestinations.About,
)

val productionHostPort = StaticLauncherHostPort(
    catalog = productionCatalog.snapshot,
    launches = buildMap {
        put(ChartDestinations.Browse, ChartDestinations.AppId)
        SettingsSectionTokens.forEach { put(it, SettingsDestinations.AppId) }
    },
)

val defaultDesktopDocument = DesktopDocument(
    version = 1,
    columns = 4,
    placements = listOf(
        TilePlacement(
            tileId = TileInstanceId("tile-chart"),
            entryId = ChartDestinations.EntryId,
            size = WpTileSize.WIDE_4X2,
            cell = GridCell(column = 0, row = 0),
        ),
        TilePlacement(
            tileId = TileInstanceId("tile-settings"),
            entryId = SettingsDestinations.EntryId,
            size = WpTileSize.SMALL_1X1,
            cell = GridCell(column = 0, row = 2),
        ),
    ),
)
