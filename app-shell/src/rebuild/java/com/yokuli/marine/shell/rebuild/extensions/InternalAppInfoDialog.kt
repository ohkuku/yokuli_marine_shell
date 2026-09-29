package com.yokuli.marine.shell.rebuild.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.marine.shell.rebuild.ui.*
import com.yokuli.shell.contract.LauncherEntryId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 信息和卸载只针对 Yokuli 包；不把所有内部应用送往同一个 Android 宿主信息页。 */
@Composable
fun InternalAppInfoDialog(os: OsStore, entryId: LauncherEntryId, requestUninstall: Boolean = false, onDismiss: () -> Unit) {
    val packages by os.packages.entries.collectAsState()
    val entry = packages.firstOrNull { it.launcherId == entryId.value }
    var confirm by remember(entryId, requestUninstall) { mutableStateOf(requestUninstall) }
    var busy by remember(entryId) { mutableStateOf(false) }
    var error by remember(entryId) { mutableStateOf<String?>(null) }
    LaunchedEffect(entry) { if (entry == null && !busy) onDismiss() }
    if (entry == null) return
    val installed = entry.installed
    AppDialog(onDismissRequest = { if (!busy) onDismiss() }) { AppDialogSurface {
        AppDialogTitle(os.t(entry.name, entry.nameEn))
        if (confirm && installed != null) {
            Label(os.t("卸载此应用并删除它的本地资料？系统航行记录和其他应用不受影响。", "Uninstall this app and delete its local data? Vessel records and other apps are kept."), 14)
            Label(os.t("桌面上已固定的快捷磁贴会保留，可手动移除或重新安装后继续使用。", "Pinned shortcuts remain; remove them or reinstall the app to use them again."), 13, LocalMetro.current.muted)
            error?.let { Label(it, 14) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetroButton(os.t("卸载", "Uninstall"), {
                    if (!busy) { busy = true; error = null; os.scope.launch {
                        try { os.extensions.uninstall(installed.manifest.id); onDismiss() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = os.t("未能卸载，请稍后重试。", "Could not uninstall. Please try again.") }
                        finally { busy = false }
                    } }
                }, Modifier.weight(1f), primary = true, enabled = !busy)
                MetroButton(os.t("保留", "Keep app"), { confirm = false }, Modifier.weight(1f), enabled = !busy)
            }
        } else {
            Label(os.t("版本 ${entry.version}", "Version ${entry.version}"), 14)
            Label(if (installed == null) os.t("Yokuli 系统应用 · 随系统更新", "Yokuli system app · Updated with the system")
                else os.t(installed.manifest.description, installed.manifest.descriptionEn), 14, LocalMetro.current.muted)
            if (installed != null) {
                Label(os.t("应用授权", "App access"), 15)
                if (installed.manifest.permissions.isEmpty()) Label(os.t("无需系统数据权限", "No system data access required"), 13, LocalMetro.current.muted)
                installed.manifest.permissions.sorted().forEach { permission ->
                    Toggle(permissionTitle(os, permission), permission in installed.grants, onChange = { checked ->
                        if (!busy) { busy = true; error = null; os.scope.launch {
                            try { os.extensions.setGrants(installed.manifest.id, if (checked) installed.grants + permission else installed.grants - permission) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { error = os.t("授权未保存，请重试。", "Access changes were not saved. Please retry.") }
                            finally { busy = false }
                        } }
                    })
                }
            }
            entry.error?.let { Label(os.t("应用资源暂不可用，请更新 Yokuli。", "App resources are unavailable. Update Yokuli."), 14) }
            error?.let { Label(it, 14) }
            MetroButton(os.t("打开", "Open"), { onDismiss(); os.shell.openLinked(entry.rootRoute) }, primary = true, enabled = !busy && entry.error == null)
            if (entry.removable && installed != null) MetroButton(os.t("卸载应用…", "Uninstall app…"), { confirm = true }, enabled = !busy)
            MetroButton(os.t("完成", "Done"), onDismiss, enabled = !busy)
        }
    } }
}
