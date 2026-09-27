package com.yokuli.marine.shell.rebuild.extensions

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

/** SDK 包是静态网页资源，不是可以进入 Shell/Core 进程的 APK、DEX 或原生库。 */
data class ExtensionManifest(
    val id: String,
    val name: String,
    val nameEn: String,
    val version: Int,
    val description: String,
    val permissions: Set<String>,
    val entry: String = "index.html",
    val sdk: Int = 1,
    val descriptionEn: String = description,
)

data class ExtensionInstalled(
    val manifest: ExtensionManifest,
    val digest: String,
    val installedAt: Long,
    val grants: Set<String>,
    val directory: File,
)

/** 只有包管理器能建立的候选；预览确认前不会获得任何系统权限。 */
class ExtensionCandidate internal constructor(
    val manifest: ExtensionManifest,
    val digest: String,
    internal val stagingDirectory: File,
    internal val managerRoot: File,
)

/**
 * Shell 唯一实例。安装目录不可变，注册表原子切换指针后才成为有效安装。
 * 失败不会移除旧版本；注册表损坏时阻止写入，绝不以空列表覆盖用户的安装记录。
 */
class ExtensionPackageManager(context: Context) {
    private val root = File(context.applicationContext.filesDir, "extension-apps")
    private val packages = File(root, "packages")
    private val staging = File(root, "staging")
    private val storage = File(root, "storage")
    private val registry = AtomicFile(File(root, "registry.json"))
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _installed = MutableStateFlow<List<ExtensionInstalled>>(emptyList())
    val installed: StateFlow<List<ExtensionInstalled>> = _installed.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _errors = MutableStateFlow<String?>(null)
    val errors: StateFlow<String?> = _errors.asStateFlow()
    private var initializationFailure: Throwable? = null

    init {
        scope.launch {
            mutex.withLock {
                try {
                    listOf(root, packages, staging, storage).forEach { directory ->
                        check(directory.isDirectory || directory.mkdirs()) { "无法创建应用存储目录" }
                    }
                    _installed.value = readRegistry()
                    // 仅清理本管理器的临时目录；安装文件和用户存储不属于清理对象。
                    staging.listFiles()?.take(MAX_STAGING_DIRS)?.forEach { it.deleteRecursively() }
                } catch (failure: Exception) {
                    initializationFailure = failure
                    _errors.value = failure.message ?: "无法读取应用安装记录"
                } finally { _ready.value = true }
            }
        }
    }

    suspend fun inspect(input: InputStream): ExtensionCandidate = try { withContext(Dispatchers.IO) {
        mutex.withLock {
            requireReady()
            val stage = File(staging, UUID.randomUUID().toString())
            check(stage.mkdir()) { "无法准备应用安装" }
            try {
                val archive = File(stage, "package.zip")
                val hash = MessageDigest.getInstance("SHA-256")
                input.use { source ->
                    archive.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var length = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            length += count
                            require(length <= MAX_TOTAL_BYTES) { "安装包不能超过 32 MiB" }
                            hash.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                val content = File(stage, "content")
                check(content.mkdir()) { "无法准备应用资源" }
                val names = mutableSetOf<String>()
                var total = 0L
                ZipInputStream(archive.inputStream().buffered()).use { zip ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val entry = zip.nextEntry ?: break
                        val name = entry.name.removeSuffix("/")
                        require(validResourcePath(name)) { "安装包包含非法资源路径" }
                        require(!name.startsWith("_sdk/") && name != "_sdk") { "安装包不能替换系统 SDK" }
                        require(names.add(name) && names.size <= MAX_FILES) { "安装包有重复资源或超过 256 个条目" }
                        val target = File(content, name)
                        require(target.canonicalPath.startsWith(content.canonicalPath + File.separator)) { "资源超出安装目录" }
                        if (entry.isDirectory) {
                            check(target.isDirectory || target.mkdirs()) { "无法建立资源目录" }
                        } else {
                            require(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "无法建立资源目录" }
                            var fileBytes = 0L
                            target.outputStream().use { output ->
                                val buffer = ByteArray(16 * 1024)
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    fileBytes += count
                                    total += count
                                    require(fileBytes <= MAX_FILE_BYTES && total <= MAX_TOTAL_BYTES) { "应用资源超出大小限制" }
                                    output.write(buffer, 0, count)
                                }
                                output.fd.sync()
                            }
                        }
                        zip.closeEntry()
                    }
                }
                val manifestFile = File(content, "manifest.json")
                require(manifestFile.isFile && manifestFile.length() <= MAX_MANIFEST_BYTES) { "缺少有效的 manifest.json" }
                val manifest = parseManifest(parseExtensionJson(manifestFile.readText()))
                require(File(content, manifest.entry).isFile) { "安装包没有声明的首页" }
                check(archive.delete()) { "无法完成安装包准备" }
                ExtensionCandidate(manifest, hash.digest().joinToString("") { "%02x".format(it) }, stage, root)
            } catch (failure: Throwable) {
                stage.deleteRecursively()
                throw failure
            }
        }
    } } finally { runCatching { input.close() } }

    suspend fun discard(candidate: ExtensionCandidate) = withContext(Dispatchers.IO) {
        mutex.withLock { requireCandidate(candidate, mustExist = false); candidate.stagingDirectory.deleteRecursively(); Unit }
    }

    suspend fun install(candidate: ExtensionCandidate, grants: Set<String>): ExtensionInstalled = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireReady()
            requireCandidate(candidate)
            require(grants.all { it in candidate.manifest.permissions }) { "只能授予应用声明的权限" }
            val previous = _installed.value.firstOrNull { it.manifest.id == candidate.manifest.id }
            if (previous?.digest == candidate.digest) {
                candidate.stagingDirectory.deleteRecursively()
                val updated = previous.copy(grants = grants.toSet())
                if (updated.grants != previous.grants) commit(_installed.value.map { if (it.manifest.id == updated.manifest.id) updated else it })
                return@withLock updated
            }
            require(previous == null || candidate.manifest.version > previous.manifest.version) { "新版本号必须大于已安装版本" }
            val appDirectory = File(packages, candidate.manifest.id)
            check(appDirectory.isDirectory || appDirectory.mkdirs()) { "无法创建应用目录" }
            val destination = File(appDirectory, candidate.digest)
            // 先前掉电可能留下未登记的版本。它不属于当前版本，不能被当作有效安装复用。
            if (destination.exists()) check(destination.deleteRecursively()) { "无法清理未完成的安装" }
            val content = File(candidate.stagingDirectory, "content")
            check(content.renameTo(destination)) { "无法保存应用资源" }
            val installed = ExtensionInstalled(candidate.manifest, candidate.digest, System.currentTimeMillis(), grants.toSet(), destination)
            try {
                commit(_installed.value.filterNot { it.manifest.id == installed.manifest.id } + installed)
            } catch (failure: Throwable) {
                // 注册表写入失败仍保留候选内容，恢复空间等问题后可以重试；旧安装不变。
                if (!destination.renameTo(content)) {
                    destination.deleteRecursively()
                    candidate.stagingDirectory.deleteRecursively()
                    throw IllegalStateException("安装未完成，请重新选择安装包；原有应用仍然保留", failure)
                }
                throw failure
            }
            candidate.stagingDirectory.deleteRecursively()
            // 保留一个旧版本，AtomicFile 回滚或安装故障恢复时仍有完整资源。
            appDirectory.listFiles()?.filter { it != destination && it != previous?.directory }?.forEach { it.deleteRecursively() }
            installed
        }
    }

    suspend fun uninstall(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireReady()
            val installed = _installed.value.firstOrNull { it.manifest.id == id }
                ?: error("没有这个可卸载应用；系统应用不能卸载")
            commit(_installed.value.filterNot { it.manifest.id == id })
            // 此后所有 SDK 请求先查注册表，卸载即撤销权限。缓存清理失败不恢复授权。
            if (!installed.directory.parentFile!!.deleteRecursively()) _errors.value = "应用已卸载，部分资源等待清理"
            AtomicFile(File(storage, "$id.json")).delete()
            Unit
        }
    }

    suspend fun setGrants(id: String, grants: Set<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireReady()
            val installed = _installed.value.firstOrNull { it.manifest.id == id } ?: error("应用未安装")
            require(grants.all { it in installed.manifest.permissions }) { "应用未声明所选权限" }
            commit(_installed.value.map { if (it.manifest.id == id) it.copy(grants = grants.toSet()) else it })
        }
    }

    suspend fun readStorage(id: String, expectedDigest: String? = null, expectedInstalledAt: Long? = null,
        expectedGrants: Set<String>? = null): JSONObject = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireReady(); requireInstalled(id, expectedDigest, expectedInstalledAt, expectedGrants)
            val file = AtomicFile(File(storage, "$id.json"))
            if (!exists(file)) JSONObject() else {
                val bytes = readBounded(file, MAX_STORAGE_BYTES)
                parseExtensionJson(bytes.toString(Charsets.UTF_8))
            }
        }
    }

    suspend fun writeStorage(id: String, value: JSONObject, expectedDigest: String? = null, expectedInstalledAt: Long? = null,
        expectedGrants: Set<String>? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireReady(); requireInstalled(id, expectedDigest, expectedInstalledAt, expectedGrants)
            val bytes = value.toString().toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_STORAGE_BYTES) { "单个应用的存储不能超过 64 KiB" }
            atomicWrite(AtomicFile(File(storage, "$id.json")), bytes)
        }
    }

    fun close() { scope.cancel() }

    private fun requireReady() {
        check(_ready.value) { "应用中心正在读取安装记录，请稍后重试" }
        initializationFailure?.let { throw IllegalStateException("安装记录无法读取，请保留原文件并重启后重试", it) }
    }

    private fun requireInstalled(id: String, digest: String?, installedAt: Long?, grants: Set<String>?) {
        val app = _installed.value.firstOrNull { it.manifest.id == id } ?: error("应用未安装或已卸载")
        require((digest == null || app.digest == digest) && (installedAt == null || app.installedAt == installedAt) &&
            (grants == null || app.grants == grants)) { "应用安装或权限已改变，请重新打开应用" }
    }

    private fun requireCandidate(candidate: ExtensionCandidate, mustExist: Boolean = true) {
        require(candidate.managerRoot == root && candidate.stagingDirectory.parentFile == staging &&
            candidate.stagingDirectory.name.matches(Regex("[a-f0-9-]{36}"))) { "无效的安装候选" }
        if (mustExist) require(File(candidate.stagingDirectory, "content").isDirectory) { "安装候选已过期，请重新选择文件" }
    }

    private fun readRegistry(): List<ExtensionInstalled> {
        if (!exists(registry)) return emptyList()
        val bytes = readBounded(registry, MAX_REGISTRY_BYTES)
        val json = parseExtensionJson(bytes.toString(Charsets.UTF_8))
        require(json.getInt("schema") == 1) { "安装记录来自不兼容的系统版本" }
        val list = json.getJSONArray("apps")
        require(list.length() <= MAX_APPS) { "安装数量超出限制" }
        val ids = mutableSetOf<String>()
        return List(list.length()) { index ->
            val row = list.getJSONObject(index)
            val manifest = parseManifest(row.getJSONObject("manifest"))
            require(ids.add(manifest.id)) { "安装记录有重复应用" }
            val digest = row.getString("digest")
            require(digest.matches(Regex("[a-f0-9]{64}"))) { "安装记录校验信息已损坏" }
            val directory = File(File(packages, manifest.id), digest)
            val storedManifest = File(directory, "manifest.json")
            require(storedManifest.isFile && storedManifest.length() <= MAX_MANIFEST_BYTES &&
                parseManifest(parseExtensionJson(storedManifest.readText())) == manifest && File(directory, manifest.entry).isFile) { "应用 ${manifest.name} 的资源丢失或已损坏" }
            val grants = row.getJSONArray("grants").stringSet()
            require(grants.all { it in manifest.permissions }) { "安装权限记录已损坏" }
            ExtensionInstalled(manifest, digest, row.getLong("installedAt"), grants, directory)
        }
    }

    private fun commit(apps: List<ExtensionInstalled>) {
        require(apps.size <= MAX_APPS) { "最多安装 64 个扩展应用" }
        val json = JSONObject().put("schema", 1).put("apps", JSONArray().apply {
            apps.forEach { app -> put(JSONObject().put("manifest", app.manifest.toJson())
                .put("digest", app.digest).put("installedAt", app.installedAt).put("grants", JSONArray(app.grants.sorted()))) }
        })
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_REGISTRY_BYTES) { "安装记录超出限制" }
        atomicWrite(registry, bytes)
        _installed.value = apps.sortedBy { it.manifest.name }
    }

    private fun atomicWrite(file: AtomicFile, bytes: ByteArray) {
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (failure: Throwable) { file.failWrite(stream); throw failure }
    }

    private fun exists(file: AtomicFile): Boolean = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    private fun readBounded(file: AtomicFile, limit: Int): ByteArray = file.openRead().use { input ->
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(result.size() + count <= limit) { "应用存储记录超出限制" }
            result.write(buffer, 0, count)
        }
        result.toByteArray()
    }

    companion object {
        val supportedPermissions: Set<String> = setOf("marine.read", "nmea.read", "navigation.open")
        private const val MAX_FILE_BYTES = 8L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 32L * 1024 * 1024
        private const val MAX_FILES = 256
        private const val MAX_APPS = 64
        private const val MAX_MANIFEST_BYTES = 64L * 1024
        private const val MAX_REGISTRY_BYTES = 512 * 1024
        private const val MAX_STORAGE_BYTES = 64 * 1024
        private const val MAX_STAGING_DIRS = 128

        internal fun validResourcePath(path: String): Boolean = path.isNotEmpty() && path.length <= 240 &&
            !path.startsWith('/') && !path.any { it == '\\' || it == ':' || it == '?' || it == '#' || it == '%' || it.code < 32 } &&
            path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }

        fun parseManifest(json: JSONObject): ExtensionManifest {
            val id = json.getString("id")
            require(id.length <= 120 && id.matches(Regex("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+")) &&
                id.split('.').all { it.length <= 40 && !it.endsWith('-') } && !id.startsWith("com.yokuli.") && id != "com.yokuli") { "应用 ID 必须是唯一的反向域名，且不能冒用系统标识" }
            val name = json.getString("name").trim()
            val nameEn = json.optString("nameEn", name).trim()
            val description = json.optString("description").trim()
            val descriptionEn = json.optString("descriptionEn", description).trim()
            require(name.isNotBlank() && name.length <= 48 && nameEn.isNotBlank() && nameEn.length <= 64 &&
                description.length <= 600 && descriptionEn.length <= 600) { "应用名称或介绍无效" }
            require(listOf(name, nameEn, description, descriptionEn).none { text -> text.any { it.code < 32 && it != '\n' } }) { "应用信息含有控制字符" }
            val version = json.getInt("version")
            val sdk = json.optInt("sdk", 1)
            require(version > 0 && sdk == 1) { "不支持这个应用或 SDK 版本" }
            val entry = json.optString("entry", "index.html")
            require(validResourcePath(entry) && entry.endsWith(".html") && !entry.startsWith("_sdk/")) { "首页必须是包内的 HTML 文件" }
            val permissions = (json.optJSONArray("permissions") ?: JSONArray()).stringSet()
            require(permissions.all { it in supportedPermissions }) { "应用要求此版本不支持的权限" }
            return ExtensionManifest(id, name, nameEn, version, description, permissions, entry, sdk, descriptionEn)
        }

        private fun JSONArray.stringSet(): Set<String> = (0 until length()).map { getString(it) }.toSet()
        private fun ExtensionManifest.toJson(): JSONObject = JSONObject().put("id", id).put("name", name)
            .put("nameEn", nameEn).put("version", version).put("description", description)
            .put("descriptionEn", descriptionEn)
            .put("permissions", JSONArray(permissions.sorted())).put("entry", entry).put("sdk", sdk)
    }
}

/** 外部 JSON 在递归解析前限制深度；小体积深嵌套也不能耗尽 Shell 的调用栈。 */
internal fun parseExtensionJson(text: String): JSONObject {
    var depth = 0
    var quoted = false
    var escaped = false
    for (character in text) {
        if (quoted) {
            if (escaped) escaped = false
            else if (character == '\\') escaped = true
            else if (character == '"') quoted = false
        } else when (character) {
            '"' -> quoted = true
            '{', '[' -> { depth++; require(depth <= 32) { "JSON nesting exceeds 32 levels" } }
            '}', ']' -> { depth--; require(depth >= 0) { "Invalid JSON structure" } }
        }
    }
    require(!quoted && depth == 0) { "Incomplete JSON structure" }
    return JSONObject(text)
}
