package com.yokuli.runtime.marine.chart.store

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.AtomicFile
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.yokuli.anchorwatch.data.preferences.SettingsRepository
import com.yokuli.anchorwatch.domain.model.AppLanguage
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.hardware.VirtualHostServices
import com.yokuli.runtime.marine.ipc.MarineCoreProcess
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Core 唯一下载账本。传输交给 Android，校验与已安装海图册互不拥有彼此文件。 */
@Singleton
class LocalOfficialChartStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: SettingsRepository,
) : OfficialChartStore {
    private data class Record(val view: OfficialChartDownload, val managerId: Long? = null, val requestIds: List<String> = emptyList())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val refreshMutex = Mutex()
    private val verificationMutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val manager = context.getSystemService(DownloadManager::class.java)
    private val gson = Gson()
    private val directory = File(context.noBackupFilesDir, "official-chart-store")
    private val ledger = AtomicFile(File(directory, "downloads-v1.json"))
    private val cache = AtomicFile(File(directory, "catalogue-v1.json"))
    private val mutable = MutableStateFlow(OfficialChartStoreState())
    override val state: StateFlow<OfficialChartStoreState> = mutable
    private var storageFailure = false
    private var catalogue: List<OfficialChartPackage> = emptyList()
    private var records = listOf<Record>()
    private var poll: Job? = null
    private var activeVerification: Pair<String, Job>? = null
    @Volatile private var language = AppLanguage.SYSTEM

    init {
        check(MarineCoreProcess.isCore(context)) { "CHART_STORE_CORE_REQUIRED" }
        scope.launch {
            preferences.settings.map { it.appLanguage }.distinctUntilChanged()
                .retryWhen { _, _ -> delay(5_000); true }.collect { language = it }
        }
        scope.launch {
            try {
                directory.mkdirs()
                val bundled = context.assets.open("chart-store/catalogue.json").use { OfficialChartCatalogue.parse(readLimited(it)) }
                val withdrawn = bundled.filter { it.status == "withdrawn" }.mapTo(hashSetOf()) { it.id }
                // 升级后即使离线也撤下旧下载入口；已下载的用户文件与记录仍由用户管理。
                catalogue = runCatching { cache.openRead().use { OfficialChartCatalogue.parse(readLimited(it)) } }
                    .getOrDefault(bundled).map { if(it.id in withdrawn)it.copy(status="withdrawn")else it }
                val stored = try { ledger.openRead().use { JSONObject(readLimited(it)) } } catch (error: java.io.FileNotFoundException) {
                    if (ledger.baseFile.exists() || File(ledger.baseFile.path + ".bak").exists()) throw error
                    null
                }
                if (stored != null) {
                    require(stored.optInt("version") == 1) { "CHART_STORE_LEDGER_INVALID" }
                    val array = stored.getJSONArray("downloads")
                    require(array.length() <= MAX_JOBS) { "CHART_STORE_LEDGER_INVALID" }
                    records = (0 until array.length()).map { i ->
                        val entry = array.getJSONObject(i)
                        val encoded = entry.getJSONObject("view")
                        val view = gson.fromJson(encoded.toString(), OfficialChartDownload::class.java)
                        require(ChartDownloadPhase.entries.any { it.name == encoded.optString("phase") }) { "CHART_STORE_LEDGER_INVALID" }
                        require(view.id.matches(Regex("[a-zA-Z0-9-]{1,80}")) && view.attempt in 1..1_000_000) { "CHART_STORE_LEDGER_INVALID" }
                        val item = view.item
                        require(item.id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")) && item.collectionId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,119}"))) { "CHART_STORE_LEDGER_INVALID" }
                        require(item.fileName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")) && item.fileName.endsWith(".yklpkg") && ".." !in item.fileName) { "CHART_STORE_LEDGER_INVALID" }
                        require(item.downloadSubdirectory.split('/').size in 1..5 && item.downloadSubdirectory.split('/').all { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9 _.-]{0,79}")) && it != "." && it != ".." }) { "CHART_STORE_LEDGER_INVALID" }
                        require(item.sha256.matches(Regex("[a-f0-9]{64}")) && item.bytes in 1..(32L * 1024 * 1024 * 1024) && OfficialChartCatalogue.allowedDownloadUrl(item.downloadUrl)) { "CHART_STORE_LEDGER_INVALID" }
                        require(view.destination == destination(item)) { "CHART_STORE_LEDGER_INVALID" }
                        val requests = entry.optJSONArray("requests")
                        Record(view, entry.optLong("managerId", -1).takeIf { it >= 0 },
                            (0 until minOf(requests?.length() ?: 0, 64)).map { requests!!.getString(it) })
                    }
                }
                mutable.value = mutable.value.copy(loading = false, catalogueRevision = 1, updatedAtMillis = cache.baseFile.lastModified().takeIf { it > 0 })
                publish()
            } catch (_: Exception) {
                storageFailure = true
                mutable.value = mutable.value.copy(loading = false, catalogueError = "CHART_STORE_STORAGE_UNAVAILABLE")
            } finally { ready.complete(Unit) }
            runCatching { reconcile() }
            ensurePolling()
        }
        scope.launch {
            ready.await()
            VirtualHostServices.power.collect {
                if (VirtualHostServices.virtual) mutex.withLock { activeVerification?.second?.cancel() }
                else ensurePolling()
            }
        }
    }

    override suspend fun browse(query: String, country: String, offset: Int, limit: Int): OfficialChartPage {
        ready.await()
        val needle = query.take(160).trim().lowercase()
        return mutex.withLock {
            val selected = catalogue.asSequence().filter { item ->
                (item.status !in setOf("draft", "withdrawn")) && (country.isBlank() || item.country == country) && (needle.isEmpty() || listOf(item.id, item.collectionId, item.name, item.nameEn, item.countryName, item.countryNameEn, item.description).any { it.lowercase().contains(needle) })
            }.sortedWith(compareByDescending<OfficialChartPackage> { it.recommended }.thenBy { it.country }.thenBy { it.nameEn }).toList()
            val start = offset.coerceAtLeast(0).coerceAtMost(selected.size)
            val values = selected.drop(start).take(limit.coerceIn(1, 80))
            OfficialChartPage(values, selected.size, start + values.size < selected.size)
        }
    }

    override suspend fun refresh() {
        ready.await()
        refreshMutex.withLock {
            mutex.withLock { mutable.value = mutable.value.copy(refreshing = true, catalogueError = if (storageFailure) "CHART_STORE_STORAGE_UNAVAILABLE" else null) }
            try {
                val text = withContext(Dispatchers.IO) { fetchCatalogue() }
                val parsed = OfficialChartCatalogue.parse(text)
                mutex.withLock {
                    atomicWrite(cache, text)
                    catalogue = parsed
                    mutable.value = mutable.value.copy(refreshing = false, catalogueRevision = mutable.value.catalogueRevision + 1, updatedAtMillis = System.currentTimeMillis())
                    publish()
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutex.withLock { mutable.value = mutable.value.copy(refreshing = false, catalogueError = "CHART_STORE_CATALOGUE_UNAVAILABLE") } }
            finally { withContext(NonCancellable) { mutex.withLock { if (mutable.value.refreshing) mutable.value = mutable.value.copy(refreshing = false) } } }
        }
    }

    override suspend fun download(packageId: String, requestId: String): String {
        ready.await()
        requireStorage()
        requireReal()
        require(requestId.isNotBlank() && requestId.length <= 160) { "CHART_STORE_REQUEST_INVALID" }
        val id = mutex.withLock {
            requireReal()
            records.firstOrNull { requestId in it.requestIds }?.let {
                require(it.view.item.id == packageId) { "CHART_STORE_REQUEST_CONFLICT" }
                return@withLock it.view.id
            }
            val item = catalogue.firstOrNull { it.id == packageId } ?: error("CHART_STORE_PACKAGE_NOT_FOUND")
            require(item.status == "published") { "CHART_STORE_NOT_PUBLISHED" }
            records.firstOrNull { it.view.item.sha256 == item.sha256 }?.let { existing ->
                require(existing.requestIds.size < 64) { "CHART_STORE_REQUEST_LIMIT" }
                save(records.map { if (it.view.id == existing.view.id) it.copy(requestIds = it.requestIds + requestId) else it })
                return@withLock existing.view.id
            }
            require(records.size < MAX_JOBS) { "CHART_STORE_DOWNLOAD_LIMIT" }
            require(!needsPermission()) { "CHART_STORE_STORAGE_PERMISSION" }
            val record = Record(OfficialChartDownload(UUID.randomUUID().toString(), item, destination = destination(item), createdAtMillis = System.currentTimeMillis()), requestIds = listOf(requestId))
            save(records + record) // enqueue 之前提交意图，死亡后按来源和目标 URI 对账。
            enqueue(record.view.id)
            record.view.id
        }
        ensurePolling()
        return id
    }

    override suspend fun cancel(downloadId: String) {
        ready.await()
        requireStorage()
        requireReal()
        mutex.withLock {
            requireReal()
            val old = record(downloadId)
            require(old.view.phase != ChartDownloadPhase.READY) { "CHART_STORE_ALREADY_READY" }
            // 先持久化取消，崩溃后不会将迟到的系统完成事件重新变成 READY。
            save(records.map { if (it.view.id == downloadId) it.copy(view = it.view.copy(phase = ChartDownloadPhase.CANCELLED, reason = null)) else it })
            activeVerification?.takeIf { it.first == downloadId }?.second?.cancel()
            old.managerId?.let { managerId ->
                if (runCatching { manager.remove(managerId) }.isSuccess) {
                    save(records.map { if (it.view.id == downloadId) it.copy(managerId = null) else it })
                }
            }
        }
        ensurePolling()
    }

    override suspend fun retry(downloadId: String) {
        ready.await()
        requireStorage()
        requireReal()
        mutex.withLock {
            requireReal()
            val old = record(downloadId)
            require(old.view.phase in setOf(ChartDownloadPhase.FAILED, ChartDownloadPhase.CANCELLED, ChartDownloadPhase.MISSING)) { "CHART_STORE_RETRY_NOT_AVAILABLE" }
            require(!needsPermission()) { "CHART_STORE_STORAGE_PERMISSION" }
            old.managerId?.let { runCatching { manager.remove(it) } }
            val updated = old.copy(managerId = null, view = old.view.copy(phase = ChartDownloadPhase.QUEUED, receivedBytes = 0, verifiedBytes = 0, reason = null, attempt = old.view.attempt + 1, updatedAtMillis = System.currentTimeMillis()))
            save(records.map { if (it.view.id == downloadId) updated else it })
            enqueue(downloadId)
        }
        ensurePolling()
    }

    override suspend fun remove(downloadId: String, deleteFile: Boolean) {
        ready.await()
        requireStorage()
        requireReal()
        mutex.withLock {
            requireReal()
            val old = record(downloadId)
            require(old.view.phase !in ACTIVE) { "CHART_STORE_CANCEL_FIRST" }
            if (deleteFile && old.managerId != null) {
                require(runCatching { manager.remove(old.managerId); !File(old.view.destination).exists() }.getOrDefault(false)) { "CHART_STORE_DELETE_FAILED" }
            }
            save(records.filterNot { it.view.id == downloadId })
        }
    }

    override suspend fun readyUri(downloadId: String): String {
        ready.await()
        requireStorage()
        requireReal()
        return mutex.withLock {
            requireReal()
            val old = record(downloadId)
            require(old.view.phase == ChartDownloadPhase.READY) { "CHART_STORE_NOT_READY" }
            val uri = readableUri(old)
            if (uri == null) {
                save(records.map { if (it.view.id == downloadId) it.copy(view = it.view.copy(phase = ChartDownloadPhase.MISSING, reason = "CHART_STORE_FILE_MISSING")) else it })
                error("CHART_STORE_FILE_MISSING")
            }
            uri.toString()
        }
    }

    /** JobScheduler 的校验任务与 Core 前台查询共用此实例及同一把锁。 */
    internal suspend fun finishSystemDownloads() {
        ready.await()
        if (VirtualHostServices.virtual) return
        reconcile()
        verificationMutex.withLock {
            while (currentCoroutineContext().isActive && !VirtualHostServices.virtual) {
                val next = mutex.withLock { records.firstOrNull { it.view.phase == ChartDownloadPhase.VERIFYING } } ?: break
                supervisorScope {
                    val job = async { verify(next) }
                    mutex.withLock { activeVerification = next.view.id to job }
                    try { job.await() }
                    catch (error: CancellationException) { currentCoroutineContext().ensureActive() }
                    finally { withContext(NonCancellable) { mutex.withLock { if (activeVerification?.first == next.view.id) activeVerification = null } } }
                }
            }
        }
    }

    private fun ensurePolling() {
        if (VirtualHostServices.virtual) return
        synchronized(this) {
            if (poll?.isActive == true) return
            poll = scope.launch {
                do {
                    runCatching { reconcile() }
                    val verifying = mutex.withLock { records.any { it.view.phase == ChartDownloadPhase.VERIFYING } }
                    if (verifying && !VirtualHostServices.virtual) OfficialChartDownloadJobs.schedule(context)
                    delay(2000)
                } while (!VirtualHostServices.virtual && mutex.withLock { records.any { it.view.phase in ACTIVE || it.view.phase == ChartDownloadPhase.CANCELLED && it.managerId != null } })
            }
        }
    }

    private suspend fun reconcile() = mutex.withLock {
        if (VirtualHostServices.virtual) return@withLock
        requireStorage()
        var updated = records
        for (record in records) {
            if (VirtualHostServices.virtual) return@withLock
            if (record.view.phase == ChartDownloadPhase.CANCELLED) {
                record.managerId?.let { managerId ->
                    if (runCatching { manager.remove(managerId) }.isSuccess) updated = updated.replace(record.copy(managerId = null))
                }
                continue
            }
            if (record.view.phase == ChartDownloadPhase.READY) {
                if (readableUri(record) == null) updated = updated.replace(record.copy(view = record.view.copy(phase = ChartDownloadPhase.MISSING, reason = "CHART_STORE_FILE_MISSING")))
                continue
            }
            if (record.view.phase !in ACTIVE) continue
            var current = record
            if (current.managerId == null) {
                val recovered = recoverManagerId(current)
                if (recovered != null) current = current.copy(managerId = recovered)
                else {
                    // 查询完成后仍无回执，才重新执行已经持久化的提交意图。
                    if (needsPermission()) current = current.copy(view = current.view.copy(phase = ChartDownloadPhase.FAILED, reason = "CHART_STORE_STORAGE_PERMISSION"))
                    else {
                        records = updated.replace(current)
                        enqueue(current.view.id)
                        updated = records
                        continue
                    }
                }
            }
            val managerId = current.managerId
            if (managerId != null) {
                manager.query(DownloadManager.Query().setFilterById(managerId))?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        current = current.copy(view = current.view.copy(phase = ChartDownloadPhase.MISSING, reason = "CHART_STORE_FILE_MISSING"))
                    } else {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        val received = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)).coerceAtLeast(0)
                        val phase = when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> ChartDownloadPhase.VERIFYING
                            DownloadManager.STATUS_FAILED -> ChartDownloadPhase.FAILED
                            DownloadManager.STATUS_PAUSED -> ChartDownloadPhase.WAITING
                            DownloadManager.STATUS_RUNNING -> ChartDownloadPhase.DOWNLOADING
                            else -> ChartDownloadPhase.QUEUED
                        }
                        current = current.copy(view = current.view.copy(phase = phase, receivedBytes = received.coerceAtMost(current.view.item.bytes), reason = when (phase) {
                            ChartDownloadPhase.WAITING -> when (reason) {
                                DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "CHART_STORE_WAITING_NETWORK"
                                DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "CHART_STORE_WAITING_WIFI"
                                DownloadManager.PAUSED_WAITING_TO_RETRY -> "CHART_STORE_WAITING_RETRY"
                                else -> "CHART_STORE_WAITING_SYSTEM"
                            }
                            ChartDownloadPhase.FAILED -> when (reason) {
                                DownloadManager.ERROR_INSUFFICIENT_SPACE -> "CHART_STORE_NO_SPACE"
                                DownloadManager.ERROR_DEVICE_NOT_FOUND -> "CHART_STORE_STORAGE_UNAVAILABLE"
                                DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "CHART_STORE_FILE_EXISTS"
                                DownloadManager.ERROR_CANNOT_RESUME -> "CHART_STORE_CANNOT_RESUME"
                                in 400..599 -> "CHART_STORE_SERVER_ERROR"
                                else -> "CHART_STORE_DOWNLOAD_FAILED"
                            }
                            else -> null
                        }))
                    }
                }
            }
            updated = updated.replace(current)
        }
        if (updated != records) {
            val persist = updated.zip(records).any { (a,b) -> a.managerId != b.managerId || a.view.phase != b.view.phase || a.view.reason != b.view.reason }
            if (persist) save(updated) else { records = updated; publish() }
        } else publish()
    }

    private fun enqueue(id: String) {
        requireReal()
        val old = record(id)
        try {
            recoverManagerId(old)?.let { managerId -> save(records.replace(old.copy(managerId = managerId))); return }
            require(!File(old.view.destination).exists()) { "CHART_STORE_FILE_EXISTS" }
            val item = old.view.item
            val chinese = when(language) {
                AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.TRADITIONAL_CHINESE -> true
                AppLanguage.SYSTEM -> context.resources.configuration.locales[0].language.startsWith("zh")
                else -> false
            }
            val request = DownloadManager.Request(Uri.parse(item.downloadUrl))
                .setTitle(if (chinese) item.name else item.nameEn)
                .setDescription(if (chinese) "官方海图包 · 下载完成后核对文件" else "Official chart package · verify after download")
                .setMimeType("application/octet-stream")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOCUMENTS, relativeDestination(item))
            requireReal()
            val managerId = manager.enqueue(request)
            save(records.replace(old.copy(managerId = managerId)))
        } catch (error: Exception) {
            if (VirtualHostServices.virtual) throw IllegalStateException("CHART_STORE_REAL_WORLD_REQUIRED")
            val code = when {
                error is SecurityException -> "CHART_STORE_STORAGE_PERMISSION"
                error.message == "CHART_STORE_FILE_EXISTS" -> "CHART_STORE_FILE_EXISTS"
                else -> "CHART_STORE_DOWNLOAD_FAILED"
            }
            // 保存回执失败时保留 QUEUED 意图，下次先向 DownloadManager 对账，不能再提交副本。
            val recovered = runCatching { recoverManagerId(old) }.getOrNull()
            if (recovered != null) save(records.replace(old.copy(managerId = recovered)))
            else save(records.replace(old.copy(view = old.view.copy(phase = ChartDownloadPhase.FAILED, reason = code))))
        }
    }

    private fun recoverManagerId(record: Record): Long? {
        manager.query(DownloadManager.Query())?.use { cursor ->
            while (cursor.moveToNext()) {
                val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
                val local = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                if (uri == record.view.item.downloadUrl && local != null && Uri.parse(local).scheme == "file" && Uri.parse(local).path == record.view.destination)
                    return cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
            }
        }
        return null
    }

    private suspend fun verify(record: Record) {
        var read = 0L
        try {
            val id = record.managerId ?: error("CHART_STORE_FILE_MISSING")
            val digest = MessageDigest.getInstance("SHA-256")
            manager.openDownloadedFile(id).use { descriptor ->
                require(descriptor.statSize == record.view.item.bytes || descriptor.statSize < 0) { "CHART_STORE_SIZE_MISMATCH" }
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
                    val buffer = ByteArray(256 * 1024)
                    var lastProgress = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (VirtualHostServices.virtual) throw CancellationException("CHART_STORE_REAL_WORLD_REQUIRED")
                        val count = stream.read(buffer)
                        if (count < 0) break
                        read += count
                        require(read <= record.view.item.bytes) { "CHART_STORE_SIZE_MISMATCH" }
                        digest.update(buffer, 0, count)
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastProgress >= 500) {
                            lastProgress = now
                            mutex.withLock { records = records.map { if (it.view.id == record.view.id && it.view.phase == ChartDownloadPhase.VERIFYING) it.copy(view = it.view.copy(verifiedBytes = read)) else it }; publish() }
                        }
                    }
                }
            }
            require(read == record.view.item.bytes) { "CHART_STORE_SIZE_MISMATCH" }
            require(digest.digest().joinToString("") { "%02x".format(it) } == record.view.item.sha256) { "CHART_STORE_CHECKSUM_MISMATCH" }
            mutex.withLock {
                val live = records.firstOrNull { it.view.id == record.view.id && it.managerId == record.managerId && it.view.phase == ChartDownloadPhase.VERIFYING }
                if (live != null) save(records.replace(live.copy(view = live.view.copy(phase = ChartDownloadPhase.READY, verifiedBytes = read, receivedBytes = read, reason = null))))
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            val reason = when (error.message) { "CHART_STORE_SIZE_MISMATCH", "CHART_STORE_CHECKSUM_MISMATCH" -> error.message!!; else -> "CHART_STORE_VERIFY_FAILED" }
            mutex.withLock {
                val live = records.firstOrNull { it.view.id == record.view.id && it.managerId == record.managerId && it.view.phase == ChartDownloadPhase.VERIFYING }
                if (live != null) save(records.replace(live.copy(view = live.view.copy(phase = ChartDownloadPhase.FAILED, reason = reason, verifiedBytes = read))))
            }
        }
    }

    private fun readableUri(record: Record): Uri? = runCatching {
        val id = record.managerId ?: return null
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst() || cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) != DownloadManager.STATUS_SUCCESSFUL) return null
        } ?: return null
        val uri = manager.getUriForDownloadedFile(id) ?: return null
        context.contentResolver.openFileDescriptor(uri, "r")?.use { require(it.statSize == record.view.item.bytes || it.statSize < 0) } ?: return null
        uri
    }.getOrNull()

    private fun record(id: String): Record = records.firstOrNull { it.view.id == id } ?: error("CHART_STORE_DOWNLOAD_NOT_FOUND")
    private fun List<Record>.replace(record: Record) = map { if (it.view.id == record.view.id) record else it }
    private fun relativeDestination(item: OfficialChartPackage) = "Yokuli OS Documents/${item.downloadSubdirectory}/${item.collectionId}/${item.fileName}"
    @Suppress("DEPRECATION")
    private fun destination(item: OfficialChartPackage) = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), relativeDestination(item)).absolutePath
    private fun needsPermission() = Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
    private fun publish() {
        mutable.value = mutable.value.copy(
            regions = catalogue.filter { it.status !in setOf("draft", "withdrawn") }.groupBy { it.country }.map { (country, entries) -> OfficialChartRegion(country, entries.first().countryName, entries.first().countryNameEn, entries.size) }.sortedBy { it.nameEn },
            downloads = records.map { it.view }.sortedByDescending { it.createdAtMillis }, storagePermissionRequired = needsPermission(),
        )
    }
    private fun requireReal() { check(!VirtualHostServices.virtual) { "CHART_STORE_REAL_WORLD_REQUIRED" } }
    private fun requireStorage() { check(!storageFailure) { "CHART_STORE_STORAGE_UNAVAILABLE" } }
    private fun save(input: List<Record>) {
        requireStorage()
        val now = System.currentTimeMillis()
        val values = input.map { value ->
            val old = records.firstOrNull { it.view.id == value.view.id }
            if (old == null || old.view.phase != value.view.phase) value.copy(view = value.view.copy(updatedAtMillis = now)) else value
        }
        val array = JSONArray()
        values.forEach { value -> array.put(JSONObject().put("view", JSONObject(gson.toJson(value.view))).put("managerId", value.managerId ?: -1).put("requests", JSONArray(value.requestIds))) }
        atomicWrite(ledger, JSONObject().put("version", 1).put("downloads", array).toString())
        records = values
        publish()
    }
    private fun atomicWrite(file: AtomicFile, value: String) {
        try {
            file.baseFile.parentFile?.mkdirs()
            val bytes = value.toByteArray(Charsets.UTF_8)
            require(bytes.size <= OfficialChartCatalogue.MAX_BYTES) { "CHART_STORE_STORAGE_LIMIT" }
            val stream = file.startWrite()
            try { stream.write(bytes); file.finishWrite(stream) }
            catch (error: Exception) { file.failWrite(stream); throw error }
            require(file.openRead().use { it.readBytes().contentEquals(bytes) }) { "CHART_STORE_STORAGE_UNAVAILABLE" }
        } catch (error: Exception) {
            throw IllegalStateException(if (error.message == "CHART_STORE_STORAGE_LIMIT") "CHART_STORE_STORAGE_LIMIT" else "CHART_STORE_STORAGE_UNAVAILABLE", error)
        }
    }
    private fun fetchCatalogue(): String {
        fun fetch(address: String): Pair<Int,String?> {
            val connection = URL(address).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000; connection.readTimeout = 20_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Yokuli-OS-Chart-Store/1")
                val code = connection.responseCode
                return code to if (code == 200) connection.inputStream.use { readLimited(it) } else null
            } finally { connection.disconnect() }
        }
        val primary = fetch(OfficialChartCatalogue.PRIMARY)
        if (primary.first == 200) return primary.second!!
        if (primary.first == 404) {
            val fallback = fetch(OfficialChartCatalogue.FALLBACK)
            if (fallback.first == 200) return fallback.second!!
        }
        error("CHART_STORE_CATALOGUE_UNAVAILABLE")
    }
    private fun readLimited(stream: InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer, 0, minOf(buffer.size, OfficialChartCatalogue.MAX_BYTES + 1 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
            require(output.size() <= OfficialChartCatalogue.MAX_BYTES) { "CHART_STORE_CATALOGUE_TOO_LARGE" }
        }
        return String(output.toByteArray(), Charsets.UTF_8)
    }
    private companion object {
        const val MAX_JOBS = 32
        val ACTIVE = setOf(ChartDownloadPhase.QUEUED, ChartDownloadPhase.DOWNLOADING, ChartDownloadPhase.WAITING, ChartDownloadPhase.VERIFYING)
    }
}
