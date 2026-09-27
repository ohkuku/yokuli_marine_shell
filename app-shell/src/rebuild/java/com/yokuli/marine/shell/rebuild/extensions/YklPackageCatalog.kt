package com.yokuli.marine.shell.rebuild.extensions

import android.content.Context
import com.yokuli.marine.shell.rebuild.AppId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** .ykl 的两种执行后端共用身份、目录、路由与 Shell 生命周期。host-kotlin 仅能来自签名 APK 的只读资源。 */
enum class YklRuntime { HOST_KOTLIN, WEB }
enum class YklOrigin { SYSTEM_IMAGE, USER_INSTALLED }
data class YklPackageEntry(
    val id: String, val name: String, val nameEn: String, val version: Int,
    val runtime: YklRuntime, val origin: YklOrigin, val rootRoute: String,
    val launcherId: String, val rootToken: String, val digest: String,
    val exactRoutes: Set<String>, val routePrefixes: Set<String>,
    val hostApp: AppId? = null, val component: String? = null,
    val installed: ExtensionInstalled? = null, val error: String? = null,
) {
    val removable: Boolean get() = origin == YklOrigin.USER_INSTALLED
    fun owns(route: String): Boolean = route in exactRoutes || routePrefixes.any(route::startsWith)
}

/**
 * APK 签名是内置包信任根；资源索引散列检测不匹配，不充当第三方发布者认证。
 * 保留既有 launcherId/rootToken 让用户磁贴与返回栈连续。每次实际启动仍检查组件与包身份。
 */
class YklPackageCatalog(context: Context, extensions: ExtensionPackageManager, scope: CoroutineScope) {
    val system: List<YklPackageEntry> = loadSystem(context)
    private val mutable = MutableStateFlow(system + extensions.installed.value.map(::installedEntry))
    val entries: StateFlow<List<YklPackageEntry>> = mutable.asStateFlow()
    init { scope.launch { extensions.installed.collect { installed ->
        mutable.value = system + installed.map(::installedEntry)
    } } }
    fun resolve(route: String): YklPackageEntry? = entries.value.singleOrNull { it.owns(route) }
    fun systemApp(app: AppId): YklPackageEntry = system.first { it.hostApp == app }

    private fun loadSystem(context: Context): List<YklPackageEntry> {
        val index = runCatching { context.assets.open("ykl/system/catalog.json").use { input ->
            JSONObject(String(input.readBytes(), Charsets.UTF_8)).getJSONArray("apps")
        } }
        return AppId.entries.map { app ->
            val expected = SystemYklApps.identity(app)
            try {
                val rows = index.getOrThrow()
                val row = (0 until rows.length()).map(rows::getJSONObject).single { it.getString("id") == expected.id }
                val bytes = context.assets.open("ykl/system/${expected.id}.ykl").use { it.readBytes().also { data -> require(data.size <= 64 * 1024) } }
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                require(digest == row.getString("sha256")) { "System package digest mismatch" }
                val manifest = ZipInputStream(bytes.inputStream()).use { zip ->
                    val entry = zip.nextEntry ?: error("Missing system manifest")
                    require(entry.name == "manifest.json" && !entry.isDirectory)
                    val data = zip.readBytes(); require(data.size <= 32 * 1024)
                    require(zip.nextEntry == null) { "Unexpected system executable" }
                    parseExtensionJson(String(data, Charsets.UTF_8))
                }
                require(manifest.getString("id") == expected.id && manifest.getString("runtime") == "host-kotlin" &&
                    manifest.getString("component") == expected.component && manifest.getString("hostApp") == app.name &&
                    manifest.getString("entry") == expected.rootRoute && manifest.getString("launcherId") == expected.launcherId &&
                    manifest.getString("rootToken") == expected.rootToken && !manifest.getBoolean("removable") && manifest.getInt("sdk") == 2)
                val routes = manifest.getJSONObject("routes")
                fun strings(key: String): Set<String> = routes.getJSONArray(key).let { array -> (0 until array.length()).map(array::getString).toSet() }
                val exact = strings("exact"); val prefixes = strings("prefix")
                require(expected.rootRoute in exact && exact.size <= 16 && prefixes.size <= 16 && prefixes.all { it.endsWith(':') })
                expected.copy(name = manifest.getString("name"), nameEn = manifest.getString("nameEn"), version = manifest.getInt("version"),
                    digest = digest, exactRoutes = exact, routePrefixes = prefixes)
            } catch (failure: Exception) {
                // 包损坏保留应用身份，但拒绝启动不匹配的组件；不会回退到绕过包管理的旧分派路径。
                expected.copy(error = failure.message ?: "System package could not be loaded")
            }
        }.also { entries ->
            val exact = entries.flatMap { app -> app.exactRoutes.map { route -> route to app.id } }
            require(exact.map { it.first }.distinct().size == exact.size) { "System package route ownership overlaps" }
            require(entries.flatMap { it.routePrefixes }.distinct().size == entries.sumOf { it.routePrefixes.size }) { "System package prefix ownership overlaps" }
        }
    }

    companion object {
        fun installedEntry(installed: ExtensionInstalled): YklPackageEntry {
            val manifest = installed.manifest
            return YklPackageEntry(manifest.id, manifest.name, manifest.nameEn, manifest.version,
                YklRuntime.WEB, YklOrigin.USER_INSTALLED, "extension:${manifest.id}", "extension.${manifest.id}", "extension:${manifest.id}",
                installed.digest, setOf("extension:${manifest.id}"), emptySet(), installed = installed)
        }
    }
}
