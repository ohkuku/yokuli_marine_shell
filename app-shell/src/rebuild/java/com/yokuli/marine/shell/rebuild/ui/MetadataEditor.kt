package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yokuli.chartpackage.YokuliChartPackage
import com.yokuli.marine.shell.rebuild.OsStore

/** Folder descriptions remain separate from the metadata carried by individual source files. */
@Composable internal fun MetadataEditorDialog(
    os: OsStore,
    title: String,
    initial: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
    busy: Boolean = false,
    failure: String? = null,
) {
    var rows by remember { mutableStateOf(initial.entries.map { it.key to it.value }) }
    val duplicate = rows.map { it.first.trim() }.distinct().size != rows.size
    val candidate = rows.associate { it.first.trim() to it.second }
    val valid = !duplicate && runCatching { YokuliChartPackage.validateMetadata(candidate) }.isSuccess
    AppDialog(onDismissRequest = { if (!busy) onDismiss() }) { AppDialogSurface {
        AppDialogTitle(title)
        Label(os.t("这些资料描述整个文件夹；每个文件保留自己的原始资料。", "These details describe the folder; each file keeps its own source metadata."), 14, LocalMetro.current.muted)
        listOf("description" to os.t("说明", "Description"), "provider" to os.t("来源", "Provider"),
            "license" to os.t("许可", "Licence"), "attribution" to os.t("署名", "Attribution")).chunked(2).forEach { fields ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEach { (name,label) ->
                    MetroButton(label, { rows = rows + (name to "") }, Modifier.weight(1f),
                        enabled = !busy && rows.size < 64 && rows.none { it.first.trim() == name })
                }
            }
        }
        rows.forEachIndexed { index, row ->
            Field(os.t("字段名", "Field"), row.first, { value ->
                if (!busy) rows = rows.toMutableList().also { it[index] = value.take(80) to row.second }
            })
            Field(os.t("内容", "Value"), row.second, { value ->
                if (!busy) rows = rows.toMutableList().also { it[index] = row.first to value.take(8192) }
            }, multiline = true)
            MetroButton(os.t("移除此字段", "Remove field"), { rows = rows.filterIndexed { i, _ -> i != index } }, enabled = !busy)
        }
        MetroButton(os.t("添加字段", "Add field"), { rows = rows + ("" to "") }, enabled = !busy && rows.size < 64)
        if (!valid) Label(os.t("字段名不能为空或重复；最多 64 个字段，全部内容不能超过 128 KiB。", "Use distinct, nonempty field names. Up to 64 fields and 128 KiB of text are supported."), 13, Color(0xFFE47C4C))
        failure?.let { Label(it, 14, Color(0xFFE47C4C)) }
        if (busy) MetroProgress(os.t("正在保存…", "Saving…"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetroButton(os.t("保存", "Save"), { onSave(candidate) }, Modifier.weight(1f), primary = true, enabled = valid && !busy)
            MetroButton(os.t("取消", "Cancel"), onDismiss, Modifier.weight(1f), enabled = !busy)
        }
    } }
}
