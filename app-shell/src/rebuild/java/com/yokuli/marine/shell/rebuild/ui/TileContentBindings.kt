package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.shell.contract.TileBinding
import com.yokuli.shell.contract.TileBindingKind

/** 工坊通过稳定内容身份列出应用能力；业务页不承担桌面固定与布局管理。 */
internal fun currentTaskTileBinding(role: String) = TileBinding("yokuli", TileBindingKind.CURRENT_TASK, role)
internal fun savedPlaceTileBinding(id: String) = TileBinding("yokuli", TileBindingKind.SAVED_PLACE, id)
internal fun savedRouteTileBinding(id: String) = TileBinding("yokuli", TileBindingKind.SAVED_ROUTE, id)
internal fun aisOverviewTileBinding() = TileBinding("yokuli", TileBindingKind.OVERVIEW, "aisTraffic")
