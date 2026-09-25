package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.yokuli.marine.shell.rebuild.*
import com.yokuli.shell.contract.*

/** 中文：角色入口读取此刻的导航，只改本次地图访问，不启动任务、不换来源、不自动移动相机。 */
@Composable internal fun CurrentNavigationTileDestination(os: OsStore) {
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!entered) {
            os.maps.view("chart", os.center, os.zoom).apply {
                previewRoute = null; previewTrack = emptyList(); previewTitle = null
                selectedPlaceId = null; selectedAisMmsi = null
            }
            os.displayedRouteId = os.activeRouteId
            os.showCrosshair = false
            entered = true
        }
    }
    if (os.activeRoute == null) Column(Modifier.fillMaxSize()) {
        PageHeader(os, os.t("当前导航", "Current navigation"), os.title(AppId.CHART))
        PageBody {
            AppSection(os.t("尚未开始导航", "No active navigation"),
                os.t("在海图选择地点或已保存的航线，再明确开始导航。", "Choose a place or saved route on Chart, then start navigation when ready."))
            MetroButton(os.t("查看海图", "Open Chart"), { os.open("chart") }, primary = true)
        }
    } else ChartAppScreen(os)
}

/**
 * 中文：保存对象先从其所属资料库解析，不能用活动导航快照冒充已删除的收藏。
 * 加载/失败/删除各有明确状态；更换或移除仅调用 Shell 的实例编辑，不改资料或任务。
 */
@Composable internal fun SavedTileDestination(os: OsStore, binding: TileBinding) {
    val persistence by os.persistenceState.collectAsState()
    val shell by os.shell.engine.state.collectAsState()
    val roomObject = binding.kind == TileBindingKind.SAVED_PLACE && binding.contentId.startsWith("spot:")
    val loading = if (roomObject) !os.sailing.loaded && !os.sailing.error else os.contentReadInProgress
    val failed = if (roomObject) os.sailing.error else persistence.readFailure != null || os.contentRecoveryFailure != null
    val exists = if (binding.kind == TileBindingKind.SAVED_ROUTE) os.routes.any { it.id == binding.contentId }
        else os.allPlaces.any { it.id == binding.contentId }
    if (!loading && !failed && exists) {
        if (binding.kind == TileBindingKind.SAVED_ROUTE) RouteScreen(os, binding.contentId)
        else PlaceScreen(os, binding.contentId)
        return
    }
    val tile = shell.start.document.placements.firstOrNull { tileBinding(it).contentKey == binding.contentKey }
    Column(Modifier.fillMaxSize()) {
        PageHeader(os, os.t("收藏内容", "Saved content"), os.title(AppId.PLACES))
        PageBody {
            when {
                loading -> MetroProgress(os.t("正在读取资料…", "Reading saved content…"))
                failed -> {
                    AppSection(os.t("资料暂未读出", "Could not read saved content"),
                        os.t("原磁贴与资料仍保留。重新读取后再查看。", "Your tile and saved data are retained. Try reading them again."))
                    MetroButton(os.t("重新读取", "Retry reading"), {
                        if (roomObject) os.sailing.retryLoading() else os.retryContentRead()
                    }, primary = true)
                }
                else -> AppSection(os.t("这项收藏已删除", "This saved item was removed"),
                    os.t("可以给这块磁贴更换内容，或将它从开始屏幕移除。正在进行的航行不受影响。", "Choose other content for this tile, or remove it from Start. Active voyages are unchanged."))
            }
            tile?.let {
                MetroButton(os.t("编辑这块磁贴", "Edit this tile"), { os.shell.tileWorkshop.beginEdit(it.tileId) })
                MetroButton(os.t("从开始屏幕移除", "Unpin from Start"), { os.shell.tileWorkshop.unpin(it.tileId) })
            }
        }
    }
}
