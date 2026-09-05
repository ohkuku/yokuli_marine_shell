package com.yokuli.marine.map.offline

import com.yokuli.marine.map.domain.ChartPackageImportException
import com.yokuli.marine.map.domain.ChartPackageImportFailure
import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.MapTileScheme

internal data class MbTilesMetadata(
    val name: String,
    val source: String,
    val license: String,
    val attribution: String,
    val version: String,
    val coverage: GeoBounds,
    val minZoom: Int,
    val maxZoom: Int,
    val rasterFormat: String,
    val tileSize: Int = 256,
    val tileScheme: MapTileScheme = MapTileScheme.MBTILES_TMS,
)

internal data class MbTilesDerivedFacts(
    val coverage: GeoBounds,
    val minZoom: Int,
    val maxZoom: Int,
    val rasterFormat: String,
    val tileSize: Int,
)

internal object MbTilesMetadataParser {
    private val rasterFormats = setOf("png", "jpg", "jpeg")

    fun parse(values: Map<String, String>, derived: MbTilesDerivedFacts? = null): MbTilesMetadata {
        val bounds = values["bounds"]?.split(',')?.map { it.trim().toDoubleOrNull() }
        if (bounds != null && (bounds.size != 4 || bounds.any { it == null })) {
            throw ChartPackageImportException(
                ChartPackageImportFailure.INVALID_METADATA,
                "MBTiles bounds must contain numeric west,south,east,north values",
            )
        }
        val coverage = if (bounds != null) {
            GeoBounds(requireNotNull(bounds[1]), requireNotNull(bounds[0]), requireNotNull(bounds[3]), requireNotNull(bounds[2]))
        } else derived?.coverage ?: throw ChartPackageImportException(
            ChartPackageImportFailure.INVALID_METADATA,
            "MBTiles metadata is missing bounds and they could not be derived",
        )
        val declaredMinZoom = values["minzoom"]?.let { raw ->
            raw.toIntOrNull() ?: throw ChartPackageImportException(
                ChartPackageImportFailure.INVALID_METADATA, "MBTiles minzoom is not an integer",
            )
        }
        val declaredMaxZoom = values["maxzoom"]?.let { raw ->
            raw.toIntOrNull() ?: throw ChartPackageImportException(
                ChartPackageImportFailure.INVALID_METADATA, "MBTiles maxzoom is not an integer",
            )
        }
        val minZoom = declaredMinZoom ?: derived?.minZoom
            ?: throw ChartPackageImportException(ChartPackageImportFailure.INVALID_METADATA, "MBTiles metadata is missing minzoom")
        val maxZoom = declaredMaxZoom ?: derived?.maxZoom
            ?: throw ChartPackageImportException(ChartPackageImportFailure.INVALID_METADATA, "MBTiles metadata is missing maxzoom")
        if (derived != null && (derived.minZoom < minZoom || derived.maxZoom > maxZoom)) {
            throw ChartPackageImportException(
                ChartPackageImportFailure.INVALID_TILE_INDEX,
                "Tile zoom is outside the declared minzoom/maxzoom range",
            )
        }
        val format = values["format"]?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: derived?.rasterFormat
            ?: throw ChartPackageImportException(ChartPackageImportFailure.INVALID_METADATA, "MBTiles metadata is missing raster format")
        if (format !in rasterFormats) {
            throw ChartPackageImportException(
                ChartPackageImportFailure.UNSUPPORTED_FORMAT,
                "Only raster PNG/JPEG MBTiles are supported; found $format",
            )
        }
        if (derived != null && format.normalizedRasterFormat() != derived.rasterFormat.normalizedRasterFormat()) {
            throw ChartPackageImportException(
                ChartPackageImportFailure.CORRUPT_TILE,
                "Tile payload encoding does not match declared raster format",
            )
        }
        return try {
            MbTilesMetadata(
                name = values["name"].orEmpty().trim(),
                source = values["source"].orEmpty().trim(),
                license = values["license"].orEmpty().trim(),
                attribution = values["attribution"].orEmpty().trim(),
                version = values["version"].orEmpty().trim(),
                coverage = derived?.coverage ?: coverage,
                minZoom = minZoom,
                maxZoom = maxZoom,
                rasterFormat = derived?.rasterFormat ?: format,
                tileSize = derived?.tileSize ?: 256,
            )
        } catch (error: IllegalArgumentException) {
            throw ChartPackageImportException(
                ChartPackageImportFailure.INVALID_METADATA,
                "Invalid MBTiles coverage or zoom metadata",
                error,
            )
        }
    }

    private fun String.normalizedRasterFormat(): String = if (this == "jpg") "jpeg" else this
}
