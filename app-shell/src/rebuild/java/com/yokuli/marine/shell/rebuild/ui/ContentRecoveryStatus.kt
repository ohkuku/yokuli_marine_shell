package com.yokuli.marine.shell.rebuild.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.OsStore

/** 原文件读取失败与当前快照写失败分开处理；只有真正读回原文件后才允许保存。 */
@Composable internal fun ContentRecoveryStatus(os: OsStore, showRecoveredCopy: Boolean = true) {
    val persistence by os.persistenceState.collectAsState()
    val c = LocalMetro.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) os.exportRecoveredContent(uri)
    }
    if (persistence.readFailure != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(os.t("原有资料暂未读出", "Existing content could not be read"), 17, c.accent)
        Label(if (persistence.readFailure == "CONTENT_VERSION_UNSUPPORTED")
            os.t("资料来自不兼容的版本，原文件已保护。使用能够读取它的版本恢复，当前改动只保留在本次运行。", "This content uses an incompatible version. The original file is protected. Restore with a compatible version; current changes remain in this session.")
        else os.t("为保护原文件，已暂停保存。当前改动只保留在本次运行；重新读取成功后会合并收藏与航线。", "Saving is paused to protect the original file. Current changes remain in this session. Reloading will merge saved places and routes."), 14, c.muted)
        if (os.contentRecoveryFailure != null) Label(os.t("仍未能完整读取或保留原资料副本，写保护继续生效。", "The original content could not be fully read or backed up. Write protection remains active."), 14, c.muted)
        MetroButton(if (os.contentReadInProgress) os.t("正在重新读取…", "reloading…") else os.t("重新读取原资料", "reload original content"),
            os::retryContentRead, enabled = !os.contentReadInProgress)
    }
    if (showRecoveredCopy && os.recoveredContentFile != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(os.t("已保留恢复前的完整资料", "Original recovery copy retained"), 15, c.muted)
        Label(os.t("含原来的草稿与导航快照。恢复资料不会移动地图或重新开始导航。", "Includes the original draft and navigation snapshot. Restoring content does not move the map or restart navigation."), 14, c.muted)
        if (os.contentExportFailed) Label(os.t("导出未完成，可以重新选择保存位置。", "Export did not complete. Choose a save location again."), 14, c.accent)
        MetroButton(if (os.contentExportInProgress) os.t("正在导出…", "exporting…") else os.t("导出原始资料副本", "export original content copy"),
            { exporter.launch("Yokuli-original-content.json") }, enabled = !os.contentExportInProgress)
    }
}
