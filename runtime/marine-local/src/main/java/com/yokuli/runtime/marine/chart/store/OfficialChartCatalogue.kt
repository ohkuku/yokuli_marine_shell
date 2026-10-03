package com.yokuli.runtime.marine.chart.store

import com.yokuli.runtime.contract.chart.OfficialChartPackage
import org.json.JSONObject
import java.net.URI

/** 目录是可信分发清单，但仍需对网络响应逐字段限界；下载不能转向任意站点。 */
internal object OfficialChartCatalogue {
    const val MAX_BYTES = 4 * 1024 * 1024
    const val PRIMARY = "https://ohkuku.github.io/yokuli_marine_shell/charts/catalogue.json"
    const val FALLBACK = "https://raw.githubusercontent.com/ohkuku/yokuli_marine_shell/gh-pages/charts/catalogue.json"
    private val identifier = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")
    private val segment = Regex("[A-Za-z0-9][A-Za-z0-9 _.-]{0,79}")
    private val hash = Regex("[a-fA-F0-9]{64}")
    private val downloadHosts = setOf("github.com", "raw.githubusercontent.com", "media.githubusercontent.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    fun allowedDownloadUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host in downloadHosts && uri.userInfo == null && uri.port in listOf(-1, 443) && uri.fragment == null && value.length <= 4096
    }.getOrDefault(false)

    fun parse(text: String): List<OfficialChartPackage> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "CHART_STORE_CATALOGUE_TOO_LARGE" }
        val root = JSONObject(text)
        require(root.optString("format") == "yokuli.chart-catalogue" && root.optInt("version") == 1) { "CHART_STORE_CATALOGUE_INVALID" }
        val entries = root.getJSONArray("collections")
        require(entries.length() <= 4096) { "CHART_STORE_CATALOGUE_TOO_LARGE" }
        val ids = HashSet<String>()
        return (0 until entries.length()).map { index ->
            val value = entries.getJSONObject(index)
            fun text(key: String, fallback: String = "", max: Int = 4096): String = value.optString(key, fallback).also {
                require(it.length <= max && '\u0000' !in it) { "CHART_STORE_CATALOGUE_INVALID" }
            }
            val id = text("id", max = 120)
            val collection = text("collectionId", id, 120)
            val fileName = text("fileName", text("file").substringAfterLast('/'), 160)
            val directory = text("downloadSubdirectory", "Chart Packages", 240)
            val sha = text("sha256").lowercase()
            val bytes = value.getLong("bytes")
            val url = text("downloadUrl")
            require(identifier.matches(id) && ids.add(id) && identifier.matches(collection)) { "CHART_STORE_CATALOGUE_INVALID" }
            require(identifier.matches(fileName) && fileName.endsWith(".yklpkg") && !fileName.contains("..")) { "CHART_STORE_CATALOGUE_INVALID" }
            require(directory.split('/').size in 1..5 && directory.split('/').all { segment.matches(it) && it != "." && it != ".." && !it.endsWith('.') }) { "CHART_STORE_CATALOGUE_INVALID" }
            require(bytes in 1..(32L * 1024 * 1024 * 1024) && hash.matches(sha) && allowedDownloadUrl(url)) { "CHART_STORE_CATALOGUE_INVALID" }
            val location = value.optJSONObject("location") ?: JSONObject()
            val types = value.optJSONArray("contentTypes")
            val contentTypes = (0 until minOf(types?.length() ?: 0, 16)).map { types!!.getString(it).take(80) }
            OfficialChartPackage(
                id = id, collectionId = collection, version = text("releaseVersion", text("createdAt"), 120),
                name = text("name", id, 160), nameEn = text("nameEn", text("name", id), 160),
                description = text("description"), descriptionEn = text("descriptionEn"),
                country = location.optString("country").take(12), countryName = location.optString("countryName").take(120), countryNameEn = location.optString("countryNameEn").take(120),
                bytes = bytes, sha256 = sha, downloadUrl = url, fileName = fileName, downloadSubdirectory = directory,
                contentTypes = contentTypes, recommended = value.optBoolean("recommended"), status = text("status", "draft", 40),
                provider = text("provider"), license = text("license"), attribution = text("attribution"),
                sourceDate = text("downloadedAt"), createdAt = text("createdAt"), sourceNotice = text("sourceNotice", text("sourceUpdateNotice")), sourceNoticeEn = text("sourceNoticeEn", text("sourceUpdateNotice")),
            )
        }
    }
}
