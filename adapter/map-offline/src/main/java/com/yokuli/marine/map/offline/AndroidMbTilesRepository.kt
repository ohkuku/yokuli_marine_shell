package com.yokuli.marine.map.offline

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.graphics.BitmapFactory
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
                openSource(sourceUri).use { raw ->
                    FileOutputStream(database).use { output -> raw.copyTo(output) }
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
                    sha256 = database.sha256(),
                    coverage = metadata.coverage,
                    minZoom = metadata.minZoom,
                    maxZoom = metadata.maxZoom,
                    rasterFormat = metadata.rasterFormat,
                    tileSize = metadata.tileSize,
                    tileScheme = metadata.tileScheme,
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
                rasterFormat = candidate.rasterFormat,
                tileSize = candidate.tileSize,
                tileScheme = candidate.tileScheme,
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
        val (metadata, scheme) = inspectReadOnly(file)
        if (scheme == "xyz") {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.beginTransaction()
                try {
                    db.execSQL("UPDATE tiles SET tile_row = ((1 << zoom_level) - 1 - tile_row)")
                    db.execSQL("DELETE FROM metadata WHERE name = 'scheme'")
                    db.execSQL("INSERT INTO metadata(name,value) VALUES('scheme','tms')")
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            return inspectReadOnly(file).first
        }
        return metadata
    }

    private fun inspectReadOnly(file: File): Pair<MbTilesMetadata, String> {
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
            val scheme = values["scheme"]?.trim()?.lowercase() ?: "tms"
            if (scheme !in setOf("tms", "xyz")) throw ChartPackageImportException(
                ChartPackageImportFailure.INVALID_METADATA,
                "MBTiles scheme must be tms or xyz",
            )
            val metadata = MbTilesMetadataParser.parse(values)
            metadata.copy(tileSize = validateRasterTiles(db, metadata)) to scheme
        }
    }

    private fun validateRasterTiles(database: SQLiteDatabase, metadata: MbTilesMetadata): Int {
        var expectedSize: Int? = null
        database.rawQuery(
            "SELECT zoom_level, tile_column, tile_row, tile_data FROM tiles ORDER BY zoom_level, tile_column, tile_row",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val zoom = cursor.getInt(0)
                val column = cursor.getInt(1)
                val row = cursor.getInt(2)
                val tile = cursor.getBlob(3)
                val maximumCoordinate = (1 shl zoom.coerceIn(0, 24)) - 1
                if (zoom !in metadata.minZoom..metadata.maxZoom || column !in 0..maximumCoordinate || row !in 0..maximumCoordinate) {
                    throw corruptTile("Tile coordinate is outside declared zoom or matrix bounds")
                }
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(tile, 0, tile.size, options)
                val formatMatches = when (metadata.rasterFormat) {
                    "png" -> options.outMimeType == "image/png"
                    "jpg", "jpeg" -> options.outMimeType == "image/jpeg"
                    "webp" -> options.outMimeType == "image/webp"
                    else -> false
                }
                if (!formatMatches || options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth != options.outHeight) {
                    throw corruptTile("Tile payload is not a square ${metadata.rasterFormat.uppercase()} image")
                }
                if (options.outWidth !in SUPPORTED_TILE_SIZES) {
                    throw corruptTile("Tile edge must be one of ${SUPPORTED_TILE_SIZES.joinToString()}")
                }
                if (expectedSize != null && expectedSize != options.outWidth) {
                    throw corruptTile("All tiles in one package must use one tile size")
                }
                expectedSize = options.outWidth
            }
        }
        return expectedSize ?: throw ChartPackageImportException(
            ChartPackageImportFailure.EMPTY_PACKAGE,
            "MBTiles contains no tiles",
        )
    }

    private fun corruptTile(detail: String) = ChartPackageImportException(
        ChartPackageImportFailure.CORRUPT_TILE,
        detail,
    )

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
            setProperty("rasterFormat", value.rasterFormat)
            setProperty("tileSize", value.tileSize.toString())
            setProperty("tileScheme", value.tileScheme.name)
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
            rasterFormat = properties.getProperty("rasterFormat")?.takeIf { it.isNotBlank() } ?: "png",
            tileSize = properties.getProperty("tileSize")?.toIntOrNull() ?: 256,
        )
    }

    private fun File.mkdirsChecked() {
        if (!isDirectory && !mkdirs()) throw ChartPackageImportException(
            ChartPackageImportFailure.IO_FAILURE,
            "Could not create chart package directory",
        )
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val DATABASE_FILE = "map.mbtiles"
        const val MANIFEST_FILE = "manifest.properties"
        val SUPPORTED_TILE_SIZES = setOf(128, 256, 512, 1024)
    }
}
