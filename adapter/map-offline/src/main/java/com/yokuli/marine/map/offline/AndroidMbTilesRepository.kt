package com.yokuli.marine.map.offline

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.graphics.BitmapFactory
import android.net.Uri
import com.yokuli.marine.map.domain.ChartPackage
import com.yokuli.marine.map.domain.ChartPackageCandidate
import com.yokuli.marine.map.domain.ChartPackageFactProvenance
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageImportException
import com.yokuli.marine.map.domain.ChartPackageImportFailure
import com.yokuli.marine.map.domain.ChartPackageImportRequest
import com.yokuli.marine.map.domain.ChartPackageInspectProgress
import com.yokuli.marine.map.domain.ChartPackageLease
import com.yokuli.marine.map.domain.ChartPackageLogicalId
import com.yokuli.marine.map.domain.ChartPackageOperationId
import com.yokuli.marine.map.domain.ChartPackageRepository
import com.yokuli.marine.map.domain.ChartPackageVersionId
import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.MapTileScheme
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class InstallCheckpoint { AFTER_PREPARE, AFTER_PUBLISH, AFTER_ACTIVATE }

/** App-private, versioned and recoverable MBTiles store. */
class AndroidMbTilesRepository(
    private val contentResolver: ContentResolver,
    private val root: File,
    private val installCheckpoint: (InstallCheckpoint) -> Unit = {},
) : ChartPackageRepository {
    private val mutex = Mutex()
    private val candidates = mutableMapOf<String, ChartPackageCandidate>()
    private val leaseLock = Any()
    private val leaseCounts = mutableMapOf<ChartPackageId, Int>()

    constructor(context: Context) : this(context.contentResolver, File(context.filesDir, "map_packages"))

    override suspend fun inspect(sourceUri: String): ChartPackageCandidate = inspect(
        sourceUri,
        ChartPackageOperationId(UUID.randomUUID().toString()),
    )

    override suspend fun inspect(
        sourceUri: String,
        operationId: ChartPackageOperationId,
        onProgress: (ChartPackageInspectProgress) -> Unit,
    ): ChartPackageCandidate = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            root.mkdirsChecked()
            reconcileLocked()
            cleanAbandonedStaging()
            val stagedId = operationId.value
            val staging = File(root, ".staging-$stagedId").also { it.mkdirsChecked() }
            val database = File(staging, DATABASE_FILE)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                openSource(sourceUri).use { input ->
                    copySource(input, database, sourceLength(sourceUri), digest, onProgress)
                }
                currentCoroutineContext().ensureActive()
                val metadata = try {
                    inspectDatabase(database, onProgress)
                } catch (error: SQLiteException) {
                    throw ChartPackageImportException(
                        ChartPackageImportFailure.INVALID_DATABASE,
                        "The selected document is not a readable SQLite MBTiles database",
                        error,
                    )
                }
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                ChartPackageCandidate(
                    stagedImportId = stagedId,
                    suggestedDisplayName = metadata.name,
                    suggestedSource = metadata.source,
                    suggestedLicense = metadata.license,
                    suggestedAttribution = metadata.attribution,
                    suggestedVersion = metadata.version,
                    sha256 = sha256,
                    coverage = metadata.coverage,
                    minZoom = metadata.minZoom,
                    maxZoom = metadata.maxZoom,
                    rasterFormat = metadata.rasterFormat,
                    tileSize = metadata.tileSize,
                    tileScheme = metadata.tileScheme,
                    logicalId = ChartPackageLogicalId("chart-${sha256.take(24)}"),
                    versionId = ChartPackageVersionId(sha256),
                    coverageProvenance = ChartPackageFactProvenance.DERIVED,
                ).also { candidates[stagedId] = it }
            } catch (error: CancellationException) {
                staging.deleteRecursively()
                throw error
            } catch (error: Throwable) {
                staging.deleteRecursively()
                if (error is ChartPackageImportException) throw error
                throw ChartPackageImportException(
                    ChartPackageImportFailure.IO_FAILURE,
                    "Unable to inspect MBTiles package",
                    error,
                )
            }
        }
    }

    override suspend fun commit(request: ChartPackageImportRequest): ChartPackage = withContext(Dispatchers.IO) {
        mutex.withLock {
            root.mkdirsChecked()
            reconcileLocked()
            val candidate = candidates[request.stagedImportId]
                ?: throw ChartPackageImportException(
                    ChartPackageImportFailure.STAGING_EXPIRED,
                    "The staged import is no longer available",
                )
            if (request.displayName.isBlank()) throw ChartPackageImportException(
                ChartPackageImportFailure.REQUIRED_FIELD_MISSING,
                "display name is required before import",
            )
            val staging = File(root, ".staging-${request.stagedImportId}")
            val database = File(staging, DATABASE_FILE)
            if (!database.isFile) throw ChartPackageImportException(
                ChartPackageImportFailure.STAGING_EXPIRED,
                "The staged MBTiles file is missing",
            )
            val logicalId = request.replaceLogicalPackageId ?: candidate.logicalId
            val destination = versionDirectory(candidate.versionId)
            val previousVersion = readActiveIndex()[logicalId.value]
            val installed = ChartPackage(
                id = ChartPackageId(candidate.versionId.value),
                displayName = request.displayName.trim(),
                source = request.source.trim().ifBlank { UNKNOWN_FACT },
                license = request.license.trim().ifBlank { UNKNOWN_FACT },
                attribution = request.attribution.trim().ifBlank { UNKNOWN_FACT },
                sha256 = candidate.sha256,
                localUri = "mbtiles://${File(destination, DATABASE_FILE).absolutePath}",
                coverage = candidate.coverage,
                minZoom = candidate.minZoom,
                maxZoom = candidate.maxZoom,
                version = request.version.trim().ifBlank { UNKNOWN_FACT },
                rasterFormat = candidate.rasterFormat,
                tileSize = candidate.tileSize,
                tileScheme = candidate.tileScheme,
                logicalId = logicalId,
                versionId = candidate.versionId,
                validationLevel = candidate.validationLevel,
            )
            if (!destination.isDirectory) {
                writeManifest(staging, installed)
                writeJournal(JournalPhase.PREPARED, staging.name, destination.name, logicalId, previousVersion)
                installCheckpoint(InstallCheckpoint.AFTER_PREPARE)
                if (!staging.renameTo(destination)) throw ChartPackageImportException(
                    ChartPackageImportFailure.INSTALL_FAILED,
                    "Could not atomically publish the chart package version",
                )
            } else {
                staging.deleteRecursively()
            }
            candidates.remove(request.stagedImportId)
            writeJournal(JournalPhase.PUBLISHED, staging.name, destination.name, logicalId, previousVersion)
            installCheckpoint(InstallCheckpoint.AFTER_PUBLISH)
            activateVersion(logicalId, candidate.versionId, previousVersion)
            writeJournal(JournalPhase.ACTIVATED, staging.name, destination.name, logicalId, previousVersion)
            installCheckpoint(InstallCheckpoint.AFTER_ACTIVATE)
            journalFile.delete()
            readManifest(destination)
        }
    }

    override suspend fun discard(stagedImportId: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            candidates.remove(stagedImportId)
            File(root, ".staging-$stagedImportId").deleteRecursively()
        }
    }

    override suspend fun reconcile(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock { root.mkdirsChecked(); reconcileLocked() }
    }

    override suspend fun listInstalled(): List<ChartPackage> = withContext(Dispatchers.IO) {
        mutex.withLock {
            root.mkdirsChecked()
            reconcileLocked()
            if (readActiveIndex().isEmpty()) migrateLegacyPackages()
            readActiveIndex().values.distinct().mapNotNull { version ->
                runCatching { readManifest(versionDirectory(ChartPackageVersionId(version))) }.getOrNull()
            }.sortedBy { it.displayName.lowercase() }
        }
    }

    override suspend fun rollback(logicalId: ChartPackageLogicalId): ChartPackage? = withContext(Dispatchers.IO) {
        mutex.withLock {
            reconcileLocked()
            val index = readActiveIndex()
            val current = index[logicalId.value] ?: return@withLock null
            val target = readHistory()[logicalId.value].orEmpty().lastOrNull { version ->
                version != current && versionDirectoryOrNull(version)?.let { runCatching { readManifest(it) }.isSuccess } == true
            } ?: return@withLock null
            index[logicalId.value] = target
            writeActiveIndex(index)
            readManifest(requireNotNull(versionDirectoryOrNull(target)))
        }
    }

    override suspend fun delete(packageId: ChartPackageId): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isLeased(packageId)) throw ChartPackageImportException(
                ChartPackageImportFailure.PACKAGE_IN_USE,
                "The chart package version is currently in use",
            )
            reconcileLocked()
            val index = readActiveIndex()
            val history = readHistory()
            index.entries.toList().filter { it.value == packageId.value }.forEach { entry ->
                val replacement = history[entry.key].orEmpty().lastOrNull { version ->
                    version != packageId.value && versionDirectoryOrNull(version)?.isDirectory == true
                }
                if (replacement == null) index.remove(entry.key) else index[entry.key] = replacement
            }
            writeActiveIndex(index)
            val directory = versionDirectoryOrNull(packageId.value)
            if (directory?.exists() == true && !directory.deleteRecursively()) throw ChartPackageImportException(
                ChartPackageImportFailure.IO_FAILURE,
                "Could not delete the selected chart package version",
            )
        }
    }

    override fun acquireLease(packageId: ChartPackageId): ChartPackageLease {
        synchronized(leaseLock) { leaseCounts[packageId] = (leaseCounts[packageId] ?: 0) + 1 }
        var closed = false
        return ChartPackageLease {
            synchronized(leaseLock) {
                if (!closed) {
                    closed = true
                    val remaining = (leaseCounts[packageId] ?: 1) - 1
                    if (remaining <= 0) leaseCounts.remove(packageId) else leaseCounts[packageId] = remaining
                }
            }
        }
    }

    private fun isLeased(packageId: ChartPackageId): Boolean = synchronized(leaseLock) {
        (leaseCounts[packageId] ?: 0) > 0
    }

    private fun sourceLength(sourceUri: String): Long? {
        val uri = Uri.parse(sourceUri)
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let(::File)?.length()
            else -> runCatching { contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }
                .getOrNull()?.takeIf { it >= 0L }
        }
    }

    private fun openSource(sourceUri: String): InputStream = Uri.parse(sourceUri).let { uri ->
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> FileInputStream(requireNotNull(uri.path))
            else -> contentResolver.openInputStream(uri)
                ?: throw ChartPackageImportException(
                    ChartPackageImportFailure.CANNOT_OPEN,
                    "The selected document cannot be opened",
                )
        }
    }

    private suspend fun copySource(
        input: InputStream,
        destination: File,
        totalBytes: Long?,
        digest: MessageDigest,
        onProgress: (ChartPackageInspectProgress) -> Unit,
    ) {
        var completed = 0L
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                completed += count
                if (completed > MAX_PACKAGE_BYTES) throw ChartPackageImportException(
                    ChartPackageImportFailure.RESOURCE_LIMIT,
                    "The selected package exceeds the supported import size",
                )
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                onProgress(ChartPackageInspectProgress.Copying(completed, totalBytes))
            }
            output.fd.sync()
        }
    }

    private suspend fun inspectDatabase(
        file: File,
        onProgress: (ChartPackageInspectProgress) -> Unit,
    ): MbTilesMetadata {
        val (metadata, scheme) = inspectReadOnly(file, onProgress)
        if (scheme == "xyz") normalizeXyzRows(file)
        return metadata.copy(tileScheme = MapTileScheme.MBTILES_TMS)
    }

    private suspend fun inspectReadOnly(
        file: File,
        onProgress: (ChartPackageInspectProgress) -> Unit,
    ): Pair<MbTilesMetadata, String> {
        val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return database.use { db ->
            val objects = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type IN ('table','view') AND name IN ('metadata','tiles')",
                null,
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            if (objects != setOf("metadata", "tiles")) throw ChartPackageImportException(
                ChartPackageImportFailure.INVALID_DATABASE,
                "MBTiles must contain metadata and tiles tables or views",
            )
            val values = readBoundedMetadata(db)
            val scheme = values["scheme"]?.trim()?.lowercase() ?: "tms"
            if (scheme !in setOf("tms", "xyz")) throw ChartPackageImportException(
                ChartPackageImportFailure.INVALID_METADATA,
                "MBTiles scheme must be tms or xyz",
            )
            val facts = deriveTileFacts(db, scheme, onProgress)
            MbTilesMetadataParser.parse(values, facts) to scheme
        }
    }

    private fun readBoundedMetadata(database: SQLiteDatabase): Map<String, String> {
        val result = linkedMapOf<String, String>()
        database.rawQuery("SELECT name, value FROM metadata ORDER BY name", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (result.size >= MAX_METADATA_ENTRIES) throw ChartPackageImportException(
                    ChartPackageImportFailure.RESOURCE_LIMIT, "MBTiles contains too many metadata entries",
                )
                val name = cursor.getString(0) ?: throw invalidMetadata("Metadata name is null")
                val value = cursor.getString(1) ?: throw invalidMetadata("Metadata value is null")
                if (name.length > MAX_METADATA_NAME_CHARS || value.length > MAX_METADATA_VALUE_CHARS) {
                    throw ChartPackageImportException(
                        ChartPackageImportFailure.RESOURCE_LIMIT, "MBTiles metadata exceeds bounded text limits",
                    )
                }
                if (result.put(name, value) != null) throw invalidMetadata("MBTiles contains duplicate metadata names")
            }
        }
        return result
    }

    private suspend fun deriveTileFacts(
        database: SQLiteDatabase,
        scheme: String,
        onProgress: (ChartPackageInspectProgress) -> Unit,
    ): MbTilesDerivedFacts {
        val duplicate = database.rawQuery(
            "SELECT 1 FROM tiles GROUP BY zoom_level,tile_column,tile_row HAVING COUNT(*)>1 LIMIT 1",
            null,
        ).use { it.moveToFirst() }
        if (duplicate) throw ChartPackageImportException(
            ChartPackageImportFailure.DUPLICATE_TILE, "MBTiles contains duplicate tile coordinates",
        )
        val totalTiles = database.rawQuery("SELECT COUNT(*) FROM tiles", null).use { cursor ->
            if (!cursor.moveToFirst()) 0L else cursor.getLong(0)
        }
        if (totalTiles <= 0L) throw ChartPackageImportException(
            ChartPackageImportFailure.EMPTY_PACKAGE, "MBTiles contains no tiles",
        )
        if (totalTiles > MAX_TILE_COUNT) throw ChartPackageImportException(
            ChartPackageImportFailure.RESOURCE_LIMIT, "MBTiles contains more tiles than one import may validate",
        )
        var minZoom = Int.MAX_VALUE
        var maxZoom = Int.MIN_VALUE
        var west = Double.POSITIVE_INFINITY
        var east = Double.NEGATIVE_INFINITY
        var south = Double.POSITIVE_INFINITY
        var north = Double.NEGATIVE_INFINITY
        var expectedSize: Int? = null
        var expectedFormat: String? = null
        var completed = 0L
        database.rawQuery(
            "SELECT zoom_level,tile_column,tile_row,tile_data FROM tiles ORDER BY zoom_level,tile_column,tile_row",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                if (cursor.getType(0) != Cursor.FIELD_TYPE_INTEGER || cursor.getType(1) != Cursor.FIELD_TYPE_INTEGER ||
                    cursor.getType(2) != Cursor.FIELD_TYPE_INTEGER || cursor.getType(3) != Cursor.FIELD_TYPE_BLOB
                ) throw invalidTileIndex("Tile coordinates must be integers and tile_data must be a blob")
                val zoomLong = cursor.getLong(0)
                val columnLong = cursor.getLong(1)
                val rowLong = cursor.getLong(2)
                if (zoomLong !in 0L..24L) throw invalidTileIndex("Tile zoom is outside 0..24")
                val zoom = zoomLong.toInt()
                val matrixSize = 1L shl zoom
                if (columnLong !in 0 until matrixSize || rowLong !in 0 until matrixSize) {
                    throw invalidTileIndex("Tile coordinate is outside its zoom matrix")
                }
                val tile = cursor.getBlob(3)
                if (tile.isEmpty() || tile.size > MAX_ENCODED_TILE_BYTES) throw ChartPackageImportException(
                    if (tile.isEmpty()) ChartPackageImportFailure.CORRUPT_TILE else ChartPackageImportFailure.RESOURCE_LIMIT,
                    "Tile payload is empty or exceeds the encoded byte limit",
                )
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(tile, 0, tile.size, boundsOptions)
                val format = when (boundsOptions.outMimeType) {
                    "image/png" -> "png"
                    "image/jpeg" -> "jpeg"
                    else -> throw corruptTile("Tile payload is not a supported PNG/JPEG image")
                }
                val size = boundsOptions.outWidth
                if (size <= 0 || size != boundsOptions.outHeight || size !in SUPPORTED_TILE_SIZES) {
                    throw corruptTile("Tile payload is not a supported square raster size")
                }
                if (expectedSize != null && expectedSize != size) throw corruptTile("All tiles must use one pixel size")
                if (expectedFormat != null && expectedFormat != format) throw corruptTile("All tiles must use one raster encoding")
                val decoded = BitmapFactory.decodeByteArray(tile, 0, tile.size)
                    ?: throw corruptTile("Tile payload failed full decode validation")
                decoded.recycle()
                expectedSize = size
                expectedFormat = format
                minZoom = minOf(minZoom, zoom)
                maxZoom = maxOf(maxZoom, zoom)
                val xyzRow = if (scheme == "tms") matrixSize - 1L - rowLong else rowLong
                west = minOf(west, tileLongitude(columnLong, matrixSize))
                east = maxOf(east, tileLongitude(columnLong + 1L, matrixSize))
                north = maxOf(north, tileLatitude(xyzRow, matrixSize))
                south = minOf(south, tileLatitude(xyzRow + 1L, matrixSize))
                completed++
                onProgress(ChartPackageInspectProgress.Inspecting(completed, totalTiles))
            }
        }
        return MbTilesDerivedFacts(
            coverage = GeoBounds(south, west, north, east),
            minZoom = minZoom,
            maxZoom = maxZoom,
            rasterFormat = requireNotNull(expectedFormat),
            tileSize = requireNotNull(expectedSize),
        )
    }

    private fun normalizeXyzRows(file: File) {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val objectType = db.rawQuery("SELECT type FROM sqlite_master WHERE name='tiles'", null).use { cursor ->
                if (!cursor.moveToFirst()) null else cursor.getString(0)
            }
            db.beginTransaction()
            try {
                if (objectType == "view") {
                    db.execSQL("CREATE TABLE _yokuli_tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
                    db.execSQL("INSERT INTO _yokuli_tiles SELECT zoom_level,tile_column,((1 << zoom_level)-1-tile_row),tile_data FROM tiles")
                    db.execSQL("DROP VIEW tiles")
                    db.execSQL("ALTER TABLE _yokuli_tiles RENAME TO tiles")
                } else {
                    db.execSQL("UPDATE tiles SET tile_row=((1 << zoom_level)-1-tile_row)")
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun tileLongitude(x: Long, matrixSize: Long): Double = x.toDouble() / matrixSize * 360.0 - 180.0

    private fun tileLatitude(y: Long, matrixSize: Long): Double {
        val mercator = PI * (1.0 - 2.0 * y.toDouble() / matrixSize)
        return Math.toDegrees(atan(sinh(mercator)))
    }

    private fun activateVersion(
        logicalId: ChartPackageLogicalId,
        versionId: ChartPackageVersionId,
        previousVersion: String?,
    ) {
        val index = readActiveIndex()
        index[logicalId.value] = versionId.value
        writeActiveIndex(index)
        val history = readHistory()
        val values = history.getOrPut(logicalId.value) { mutableListOf() }
        previousVersion?.takeIf { it !in values }?.let(values::add)
        if (versionId.value !in values) values.add(versionId.value)
        writeHistory(history)
    }

    private fun migrateLegacyPackages() {
        val index = linkedMapOf<String, String>()
        root.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith(VERSION_PREFIX) }.forEach { directory ->
            runCatching { readManifest(directory) }.getOrNull()?.let { value -> index[value.logicalId.value] = value.versionId.value }
        }
        if (index.isNotEmpty()) writeActiveIndex(index)
    }

    private fun reconcileLocked() {
        if (!journalFile.isFile) return
        val journal = Properties().apply { FileInputStream(journalFile).use(::load) }
        val phase = runCatching { JournalPhase.valueOf(journal.getProperty("phase")) }.getOrNull()
        val staging = journal.getProperty("staging")?.let { File(root, it) }
        val destination = journal.getProperty("destination")?.let { File(root, it) }
        val logical = journal.getProperty("logicalId")?.let { runCatching(::ChartPackageLogicalId).getOrNull() }
        val previous = journal.getProperty("previousVersion")?.takeIf(String::isNotBlank)
        when (phase) {
            JournalPhase.PREPARED -> staging?.deleteRecursively()
            JournalPhase.PUBLISHED, JournalPhase.ACTIVATED -> {
                val installed = destination?.let { runCatching { readManifest(it) }.getOrNull() }
                if (installed != null && logical != null) activateVersion(logical, installed.versionId, previous)
                else destination?.deleteRecursively()
                staging?.deleteRecursively()
            }
            null -> staging?.deleteRecursively()
        }
        journalFile.delete()
    }

    private fun writeJournal(
        phase: JournalPhase,
        staging: String,
        destination: String,
        logicalId: ChartPackageLogicalId,
        previousVersion: String?,
    ) {
        Properties().apply {
            setProperty("phase", phase.name)
            setProperty("staging", staging)
            setProperty("destination", destination)
            setProperty("logicalId", logicalId.value)
            setProperty("previousVersion", previousVersion.orEmpty())
        }.also(journalFile::storeAtomically)
    }

    private fun readActiveIndex(): MutableMap<String, String> = readProperties(activeIndexFile)
        .entries.associateTo(linkedMapOf()) { it.key.toString() to it.value.toString() }

    private fun writeActiveIndex(index: Map<String, String>) {
        Properties().apply { index.forEach(::setProperty) }.also(activeIndexFile::storeAtomically)
    }

    private fun readHistory(): MutableMap<String, MutableList<String>> = readProperties(historyFile)
        .entries.associateTo(linkedMapOf()) { entry ->
            entry.key.toString() to entry.value.toString().split(',').filter(String::isNotBlank).toMutableList()
        }

    private fun writeHistory(history: Map<String, List<String>>) {
        Properties().apply { history.forEach { (key, versions) -> setProperty(key, versions.joinToString(",")) } }
            .also(historyFile::storeAtomically)
    }

    private fun readProperties(file: File): Properties = Properties().apply {
        if (file.isFile) FileInputStream(file).use(::load)
    }

    private fun File.storeAtomically(properties: Properties) {
        val temporary = File(parentFile, "$name.tmp")
        FileOutputStream(temporary).use { output -> properties.store(output, null); output.fd.sync() }
        if (exists() && !delete()) throw ioFailure("Could not replace chart package index")
        if (!temporary.renameTo(this)) throw ioFailure("Could not publish chart package index")
    }

    private fun cleanAbandonedStaging() {
        root.listFiles().orEmpty().filter { it.name.startsWith(".staging-") }.forEach { directory ->
            val id = directory.name.removePrefix(".staging-")
            if (id !in candidates) directory.deleteRecursively()
        }
    }

    private fun writeManifest(directory: File, value: ChartPackage) {
        directory.mkdirsChecked()
        Properties().apply {
            setProperty("id", value.id.value)
            setProperty("logicalId", value.logicalId.value)
            setProperty("versionId", value.versionId.value)
            setProperty("displayName", value.displayName)
            setProperty("source", value.source)
            setProperty("license", value.license)
            setProperty("attribution", value.attribution)
            setProperty("sha256", value.sha256)
            setProperty("south", value.coverage.south.toString())
            setProperty("west", value.coverage.west.toString())
            setProperty("north", value.coverage.north.toString())
            setProperty("east", value.coverage.east.toString())
            setProperty("minZoom", value.minZoom.toString())
            setProperty("maxZoom", value.maxZoom.toString())
            setProperty("version", value.version)
            setProperty("rasterFormat", value.rasterFormat)
            setProperty("tileSize", value.tileSize.toString())
            setProperty("tileScheme", value.tileScheme.name)
            setProperty("validationLevel", value.validationLevel.name)
        }.also { File(directory, MANIFEST_FILE).storeAtomically(it) }
    }

    private fun readManifest(directory: File): ChartPackage {
        val properties = readProperties(File(directory, MANIFEST_FILE))
        fun required(key: String) = properties.getProperty(key)?.takeIf { it.isNotBlank() }
            ?: throw ChartPackageImportException(
                ChartPackageImportFailure.INVALID_DATABASE,
                "Installed package manifest is missing $key",
            )
        val database = File(directory, DATABASE_FILE)
        if (!database.isFile) throw ChartPackageImportException(
            ChartPackageImportFailure.INVALID_DATABASE,
            "Installed package database is missing",
        )
        val sha = required("sha256").lowercase()
        return ChartPackage(
            id = ChartPackageId(required("id")),
            displayName = required("displayName"),
            source = required("source"),
            license = required("license"),
            attribution = required("attribution"),
            sha256 = sha,
            localUri = "mbtiles://${database.absolutePath}",
            coverage = GeoBounds(
                required("south").toDouble(), required("west").toDouble(),
                required("north").toDouble(), required("east").toDouble(),
            ),
            minZoom = required("minZoom").toInt(),
            maxZoom = required("maxZoom").toInt(),
            version = required("version"),
            rasterFormat = properties.getProperty("rasterFormat")?.takeIf(String::isNotBlank) ?: "png",
            tileSize = properties.getProperty("tileSize")?.toIntOrNull() ?: 256,
            tileScheme = MapTileScheme.MBTILES_TMS,
            logicalId = properties.getProperty("logicalId")?.let(::ChartPackageLogicalId)
                ?: ChartPackageLogicalId("chart-${sha.take(24)}"),
            versionId = properties.getProperty("versionId")?.let(::ChartPackageVersionId)
                ?: ChartPackageVersionId(sha),
        )
    }

    private fun versionDirectory(versionId: ChartPackageVersionId): File = File(root, "$VERSION_PREFIX${versionId.value}")
    private fun versionDirectoryOrNull(value: String): File? = runCatching { versionDirectory(ChartPackageVersionId(value)) }.getOrNull()
    private val journalFile get() = File(root, JOURNAL_FILE)
    private val activeIndexFile get() = File(root, ACTIVE_INDEX_FILE)
    private val historyFile get() = File(root, HISTORY_FILE)

    private fun File.mkdirsChecked() {
        if (!isDirectory && !mkdirs()) throw ioFailure("Could not create chart package directory")
    }

    private fun corruptTile(detail: String) = ChartPackageImportException(ChartPackageImportFailure.CORRUPT_TILE, detail)
    private fun invalidTileIndex(detail: String) = ChartPackageImportException(ChartPackageImportFailure.INVALID_TILE_INDEX, detail)
    private fun invalidMetadata(detail: String) = ChartPackageImportException(ChartPackageImportFailure.INVALID_METADATA, detail)
    private fun ioFailure(detail: String) = ChartPackageImportException(ChartPackageImportFailure.IO_FAILURE, detail)

    private enum class JournalPhase { PREPARED, PUBLISHED, ACTIVATED }

    private companion object {
        const val DATABASE_FILE = "map.mbtiles"
        const val MANIFEST_FILE = "manifest.properties"
        const val JOURNAL_FILE = ".install-journal.properties"
        const val ACTIVE_INDEX_FILE = ".active-packages.properties"
        const val HISTORY_FILE = ".package-history.properties"
        const val VERSION_PREFIX = "package-"
        const val UNKNOWN_FACT = "Unknown"
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val MAX_PACKAGE_BYTES = 8L * 1024L * 1024L * 1024L
        const val MAX_TILE_COUNT = 2_000_000L
        const val MAX_ENCODED_TILE_BYTES = 8 * 1024 * 1024
        const val MAX_METADATA_ENTRIES = 512
        const val MAX_METADATA_NAME_CHARS = 128
        const val MAX_METADATA_VALUE_CHARS = 16_384
        val SUPPORTED_TILE_SIZES = setOf(128, 256, 512, 1024)
    }
}
