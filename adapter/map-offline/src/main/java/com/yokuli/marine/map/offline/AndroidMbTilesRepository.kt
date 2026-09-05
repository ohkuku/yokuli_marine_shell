package com.yokuli.marine.map.offline

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import com.yokuli.marine.map.domain.ChartPackage
import com.yokuli.marine.map.domain.ChartPackageCandidate
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.ChartPackageImportException
import com.yokuli.marine.map.domain.ChartPackageImportFailure
import com.yokuli.marine.map.domain.ChartPackageImportRequest
import com.yokuli.marine.map.domain.ChartPackageRepository
import com.yokuli.marine.map.domain.GeoBounds
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidMbTilesRepository(
    private val contentResolver: ContentResolver,
    private val root: File,
) : ChartPackageRepository {
    private val mutex = Mutex()
    private val candidates = mutableMapOf<String, ChartPackageCandidate>()

    constructor(context: Context) : this(context.contentResolver, File(context.filesDir, "map_packages"))

    override suspend fun inspect(sourceUri: String): ChartPackageCandidate = withContext(Dispatchers.IO) {
        mutex.withLock {
            root.mkdirsChecked()
            cleanAbandonedStaging()
            val stagedId = UUID.randomUUID().toString()
            val staging = File(root, ".staging-$stagedId").also { it.mkdirsChecked() }
            val database = File(staging, DATABASE_FILE)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                openSource(sourceUri).use { raw ->
                    DigestInputStream(raw, digest).use { input ->
                        FileOutputStream(database).use { output -> input.copyTo(output) }
                    }
                }
                val metadata = try {
                    inspectDatabase(database)
                } catch (error: SQLiteException) {
                    throw ChartPackageImportException(
                        ChartPackageImportFailure.INVALID_DATABASE,
                        "The selected document is not a readable SQLite MBTiles database",
                        error,
                    )
                }
                val candidate = ChartPackageCandidate(
                    stagedImportId = stagedId,
                    suggestedDisplayName = metadata.name,
                    suggestedSource = metadata.source,
                    suggestedLicense = metadata.license,
                    suggestedAttribution = metadata.attribution,
                    suggestedVersion = metadata.version,
                    sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                    coverage = metadata.coverage,
                    minZoom = metadata.minZoom,
                    maxZoom = metadata.maxZoom,
                    rasterFormat = metadata.rasterFormat,
                )
                candidates[stagedId] = candidate
                candidate
            } catch (error: Throwable) {
                staging.deleteRecursively()
                if (error is ChartPackageImportException) throw error
                throw ChartPackageImportException(ChartPackageImportFailure.IO_FAILURE, "Unable to inspect MBTiles package", error)
            }
        }
    }

    override suspend fun commit(request: ChartPackageImportRequest): ChartPackage = withContext(Dispatchers.IO) {
        mutex.withLock {
            val candidate = candidates[request.stagedImportId]
                ?: throw ChartPackageImportException(
                    ChartPackageImportFailure.STAGING_EXPIRED,
                    "The staged import is no longer available",
                )
            val metadata = listOf(
                "display name" to request.displayName,
                "source" to request.source,
                "license" to request.license,
                "attribution" to request.attribution,
                "version" to request.version,
            )
            metadata.firstOrNull { it.second.isBlank() }?.let {
                throw ChartPackageImportException(
                    ChartPackageImportFailure.REQUIRED_FIELD_MISSING,
                    "${it.first} is required before import",
                )
            }
            candidates.remove(request.stagedImportId)
            val staging = File(root, ".staging-${request.stagedImportId}")
            val database = File(staging, DATABASE_FILE)
            if (!database.isFile) throw ChartPackageImportException(
                ChartPackageImportFailure.STAGING_EXPIRED,
                "The staged MBTiles file is missing",
            )
            val destination = File(root, "package-${candidate.sha256}")
            val installed = ChartPackage(
                id = ChartPackageId(candidate.sha256),
                displayName = request.displayName.trim(),
                source = request.source.trim(),
                license = request.license.trim(),
                attribution = request.attribution.trim(),
                sha256 = candidate.sha256,
                localUri = "mbtiles://${File(destination, DATABASE_FILE).absolutePath}",
                coverage = candidate.coverage,
                minZoom = candidate.minZoom,
                maxZoom = candidate.maxZoom,
                version = request.version.trim(),
            )
            if (destination.isDirectory) {
                staging.deleteRecursively()
                return@withLock readManifest(destination)
            }
            writeManifest(staging, installed)
            if (!staging.renameTo(destination)) {
                staging.deleteRecursively()
                throw ChartPackageImportException(
                    ChartPackageImportFailure.INSTALL_FAILED,
                    "Could not atomically install the chart package",
                )
            }
            installed
        }
    }

    override suspend fun discard(stagedImportId: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            candidates.remove(stagedImportId)
            File(root, ".staging-$stagedImportId").deleteRecursively()
        }
    }

    override suspend fun listInstalled(): List<ChartPackage> = withContext(Dispatchers.IO) {
        mutex.withLock {
            root.mkdirsChecked()
            root.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("package-") }
                .mapNotNull { directory -> runCatching { readManifest(directory) }.getOrNull() }
                .sortedBy { it.displayName.lowercase() }
        }
    }

    override suspend fun delete(packageId: ChartPackageId): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val directory = File(root, "package-${packageId.value}")
            if (directory.exists() && !directory.deleteRecursively()) {
                throw ChartPackageImportException(
                    ChartPackageImportFailure.IO_FAILURE,
                    "Could not delete chart package ${packageId.value}",
                )
            }
        }
    }

    private fun openSource(sourceUri: String) = Uri.parse(sourceUri).let { uri ->
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> FileInputStream(requireNotNull(uri.path))
            else -> contentResolver.openInputStream(uri)
                ?: throw ChartPackageImportException(
                    ChartPackageImportFailure.CANNOT_OPEN,
                    "The selected document cannot be opened",
                )
        }
    }

    private fun inspectDatabase(file: File): MbTilesMetadata {
        val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return database.use { db ->
            val tables = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('metadata','tiles')",
                null,
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            if (tables != setOf("metadata", "tiles")) {
                throw ChartPackageImportException(
                    ChartPackageImportFailure.INVALID_DATABASE,
                    "MBTiles must contain metadata and tiles tables",
                )
            }
            val hasTile = db.rawQuery("SELECT 1 FROM tiles LIMIT 1", null).use { it.moveToFirst() }
            if (!hasTile) throw ChartPackageImportException(
                ChartPackageImportFailure.EMPTY_PACKAGE,
                "MBTiles contains no tiles",
            )
            val values = db.rawQuery("SELECT name, value FROM metadata", null).use { cursor ->
                buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
            }
            MbTilesMetadataParser.parse(values)
        }
    }

    private fun cleanAbandonedStaging() {
        root.listFiles().orEmpty().filter { it.name.startsWith(".staging-") }.forEach { directory ->
            val id = directory.name.removePrefix(".staging-")
            if (id !in candidates) directory.deleteRecursively()
        }
    }

    private fun writeManifest(directory: File, value: ChartPackage) {
        val properties = Properties().apply {
            setProperty("id", value.id.value)
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
        }
        FileOutputStream(File(directory, MANIFEST_FILE)).use { properties.store(it, null) }
    }

    private fun readManifest(directory: File): ChartPackage {
        val properties = Properties().apply {
            FileInputStream(File(directory, MANIFEST_FILE)).use(::load)
        }
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
        return ChartPackage(
            id = ChartPackageId(required("id")),
            displayName = required("displayName"),
            source = required("source"),
            license = required("license"),
            attribution = required("attribution"),
            sha256 = required("sha256"),
            localUri = "mbtiles://${database.absolutePath}",
            coverage = GeoBounds(
                required("south").toDouble(), required("west").toDouble(),
                required("north").toDouble(), required("east").toDouble(),
            ),
            minZoom = required("minZoom").toInt(),
            maxZoom = required("maxZoom").toInt(),
            version = required("version"),
        )
    }

    private fun File.mkdirsChecked() {
        if (!isDirectory && !mkdirs()) throw ChartPackageImportException(
            ChartPackageImportFailure.IO_FAILURE,
            "Could not create chart package directory",
        )
    }

    private companion object {
        const val DATABASE_FILE = "map.mbtiles"
        const val MANIFEST_FILE = "manifest.properties"
    }
}
