package com.yokuli.marine.map.offline

import com.yokuli.marine.map.domain.ChartPackageImportException
import com.yokuli.marine.map.domain.ChartPackageImportFailure
import com.yokuli.marine.map.domain.GeoBounds

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
)

internal object MbTilesMetadataParser {
    private val rasterFormats = setOf("png", "jpg", "jpeg", "webp")

    fun parse(values: Map<String, String>): MbTilesMetadata {
        val bounds = values["bounds"]?.split(',')?.map { it.trim().toDoubleOrNull() }
        if (bounds == null || bounds.size != 4 || bounds.any { it == null }) {
            throw ChartPackageImportException(
                ChartPackageImportFailure.INVALID_METADATA,
                "MBTiles metadata must contain numeric west,south,east,north bounds",
            )
        }
        val west = requireNotNull(bounds[0])
        val south = requireNotNull(bounds[1])
        val east = requireNotNull(bounds[2])
        val north = requireNotNull(bounds[3])
        val minZoom = values["minzoom"]?.toIntOrNull()
            ?: throw ChartPackageImportException(ChartPackageImportFailure.INVALID_METADATA, "MBTiles metadata is missing minzoom")
        val maxZoom = values["maxzoom"]?.toIntOrNull()
            ?: throw ChartPackageImportException(ChartPackageImportFailure.INVALID_METADATA, "MBTiles metadata is missing maxzoom")
        val format = values["format"]?.trim()?.lowercase()
            ?: throw ChartPackageImportException(ChartPackageImportFailure.INVALID_METADATA, "MBTiles metadata is missing raster format")
        if (format !in rasterFormats) {
            throw ChartPackageImportException(
                ChartPackageImportFailure.UNSUPPORTED_FORMAT,
                "Only raster PNG/JPEG/WebP MBTiles are supported; found $format",
            )
        }
        return try {
            MbTilesMetadata(
                name = values["name"].orEmpty().trim(),
                source = values["source"].orEmpty().trim(),
                license = values["license"].orEmpty().trim(),
                attribution = values["attribution"].orEmpty().trim(),
                version = values["version"].orEmpty().trim(),
                coverage = GeoBounds(south, west, north, east),
                minZoom = minZoom,
                maxZoom = maxZoom,
                rasterFormat = format,
            )
        } catch (error: IllegalArgumentException) {
            throw ChartPackageImportException(
                ChartPackageImportFailure.INVALID_METADATA,
                "Invalid MBTiles coverage or zoom metadata",
                error,
            )
        }
    }
}
