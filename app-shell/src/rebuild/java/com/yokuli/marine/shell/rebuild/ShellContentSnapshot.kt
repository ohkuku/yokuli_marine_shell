package com.yokuli.marine.shell.rebuild

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** 区分首次没有资料与已有文件无法读取；失败绝不能被解释成允许覆写的空资料库。 */
internal data class ShellContentRead(val document: JSONObject? = null, val failure: String? = null)

internal fun readShellContent(file: AtomicFile): ShellContentRead {
    if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return ShellContentRead(JSONObject())
    return try {
        val raw = file.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size().toLong() + count <= 64L * 1024 * 1024) { "CONTENT_TOO_LARGE" }
                output.write(buffer, 0, count)
            }
            output.toByteArray().toString(Charsets.UTF_8)
        }
        val value = JSONObject(raw)
        require(!value.has("schemaVersion") || value.getInt("schemaVersion") == 1) { "CONTENT_VERSION_UNSUPPORTED" }
        // 不再通过 objects/mapNotNull 忽略损坏的一条资料后把剩余内容覆写回原文件。
        listOf("places", "routes", "draft").forEach { field ->
            if (value.has(field)) {
                val array = value.getJSONArray(field)
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    when (field) {
                        "places" -> {
                            require(Place.from(item).id.isNotBlank())
                            if (item.has("kind")) require(item.getString("kind") in PlaceKind.entries.map { it.name })
                        }
                        "routes" -> validateSavedRoute(item)
                        else -> GeoPoint.from(item)
                    }
                }
            }
        }
        if (value.has("navigationSnapshot") && !value.isNull("navigationSnapshot")) validateSavedRoute(value.getJSONObject("navigationSnapshot"))
        ShellContentRead(value)
    } catch (error: Exception) {
        ShellContentRead(failure = if (error.message == "CONTENT_VERSION_UNSUPPORTED") "CONTENT_VERSION_UNSUPPORTED" else "CONTENT_READ_FAILED")
    }
}

private fun validateSavedRoute(value: JSONObject) {
    require(value.getString("id").isNotBlank())
    value.getString("name")
    val points = value.getJSONArray("points")
    for (index in 0 until points.length()) GeoPoint.from(points.getJSONObject(index))
}

/** 恢复读取后合并本次临时改动前，保留完整原快照；不覆盖旧恢复副本。 */
internal fun preserveRecoveredShellContent(file: AtomicFile, document: JSONObject): File {
    val target = AtomicFile(File(file.baseFile.parentFile, "experience-recovered-${uid()}.json"))
    val text = document.toString()
    val stream = target.startWrite()
    try {
        stream.write(text.toByteArray(Charsets.UTF_8)); stream.fd.sync(); target.finishWrite(stream)
        if (target.baseFile.readText(Charsets.UTF_8) != text) throw IOException("RECOVERY_COPY_NOT_COMMITTED")
    } catch (error: Exception) {
        target.failWrite(stream)
        throw error
    }
    return target.baseFile
}
