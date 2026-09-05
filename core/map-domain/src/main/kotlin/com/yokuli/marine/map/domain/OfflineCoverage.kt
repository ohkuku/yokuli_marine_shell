package com.yokuli.marine.map.domain

import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sinh
import kotlin.math.tan

data class SlippyTileKey(val zoom: Int, val x: Int, val y: Int) {
    init {
        require(zoom in 0..24)
        val dimension = 1 shl zoom
        require(x in 0 until dimension)
        require(y in 0 until dimension)
    }
}

data class OfflineCoverageArea(
    val center: GeoPoint,
    val radiusNauticalMiles: Double,
) {
    init { require(radiusNauticalMiles.isFinite() && radiusNauticalMiles > 0.0) }
}

data class OfflineCoverageRequest(
    val routeId: String,
    val routeRevision: Long,
    val routePoints: List<GeoPoint>,
    val packageVersionIds: List<ChartPackageVersionId>,
    val targetZoom: Int,
    val halfWidthNauticalMiles: Double = 2.0,
    val alternateAreas: List<OfflineCoverageArea> = emptyList(),
    val maxRequiredKeys: Int = OfflineCoveragePlanner.MAX_REQUIRED_TILE_KEYS,
) {
    init {
        require(routeId.isNotBlank())
        require(routeRevision > 0L)
        require(routePoints.size >= 2)
        require(packageVersionIds.isNotEmpty() && packageVersionIds.distinct().size == packageVersionIds.size)
        require(targetZoom in 0..24)
        require(halfWidthNauticalMiles.isFinite() && halfWidthNauticalMiles > 0.0)
        require(maxRequiredKeys in 1..OfflineCoveragePlanner.MAX_REQUIRED_TILE_KEYS)
    }
}

@JvmInline
value class OfflineCoverageFingerprint(val value: String) {
    init { require(value.matches(Regex("[0-9a-f]{64}"))) }

    companion object {
        fun of(request: OfflineCoverageRequest): OfflineCoverageFingerprint {
            val canonical = buildString {
                append(request.routeId).append('|').append(request.routeRevision).append('|')
                request.routePoints.forEach { append(it.latitude.toHex()).append(',').append(it.longitude.toHex()).append(';') }
                append('|')
                request.packageVersionIds.map(ChartPackageVersionId::value).sorted().forEach { append(it).append(';') }
                append('|').append(request.targetZoom)
                append('|').append(request.halfWidthNauticalMiles.toHex())
                append('|')
                request.alternateAreas.forEach {
                    append(it.center.latitude.toHex()).append(',')
                    append(it.center.longitude.toHex()).append(',')
                    append(it.radiusNauticalMiles.toHex()).append(';')
                }
            }
            val bytes = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            return OfflineCoverageFingerprint(bytes.joinToString("") { "%02x".format(it) })
        }
    }
}

data class OfflineCoveragePlan(
    val fingerprint: OfflineCoverageFingerprint,
    val packageVersionIds: List<ChartPackageVersionId>,
    val targetZoom: Int,
    val requiredKeys: Set<SlippyTileKey>,
)

enum class TileAvailability { AVAILABLE, MISSING, UNKNOWN }
enum class ContentFootprint { NOT_VERIFIED, VERIFIED_VISIBLE }
enum class NavigationSuitability { NOT_ASSESSED }

data class OfflineCoverageResult(
    val fingerprint: OfflineCoverageFingerprint,
    val tileAvailability: TileAvailability,
    val contentFootprint: ContentFootprint,
    val navigationSuitability: NavigationSuitability = NavigationSuitability.NOT_ASSESSED,
    val requiredKeyCount: Int,
    val missingKeys: Set<SlippyTileKey>,
)

class OfflineCoverageTooLargeException(val maximumKeys: Int) : IllegalArgumentException(
    "Offline coverage requires more than $maximumKeys tile keys",
)

object OfflineCoveragePlanner {
    const val MAX_REQUIRED_TILE_KEYS = 200_000
    private const val METERS_PER_NAUTICAL_MILE = 1_852.0
    private const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686
    private const val MAX_MERCATOR_LATITUDE = 85.05112878

    fun plan(request: OfflineCoverageRequest): OfflineCoveragePlan {
        val required = linkedSetOf<SlippyTileKey>()
        val corridorMeters = request.halfWidthNauticalMiles * METERS_PER_NAUTICAL_MILE
        request.routePoints.zipWithNext().forEach { (from, to) ->
            val distance = Wgs84Geodesic.inverse(from, to).distanceMeters
            val sampleSpacing = conservativeSampleSpacing(request.targetZoom, (from.latitude + to.latitude) / 2.0)
            val sampleCount = max(1, kotlin.math.ceil(distance / sampleSpacing).toInt())
            if (sampleCount.toLong() > request.maxRequiredKeys.toLong() * 16L) {
                throw OfflineCoverageTooLargeException(request.maxRequiredKeys)
            }
            Wgs84Geodesic.densify(from, to, sampleSpacing).forEach { point ->
                addDiscBounds(
                    required,
                    point,
                    corridorMeters + sampleSpacing / 2.0,
                    request.targetZoom,
                    request.maxRequiredKeys,
                )
            }
        }
        request.alternateAreas.forEach { area ->
            addDiscBounds(
                required,
                area.center,
                area.radiusNauticalMiles * METERS_PER_NAUTICAL_MILE,
                request.targetZoom,
                request.maxRequiredKeys,
            )
        }
        return OfflineCoveragePlan(
            fingerprint = OfflineCoverageFingerprint.of(request),
            packageVersionIds = request.packageVersionIds,
            targetZoom = request.targetZoom,
            requiredKeys = required,
        )
    }

    private fun conservativeSampleSpacing(zoom: Int, latitude: Double): Double {
        val metersPerTile = EARTH_CIRCUMFERENCE_METERS *
            cos(Math.toRadians(latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE))) /
            (1 shl zoom)
        return max(25.0, metersPerTile / 2.0)
    }

    private fun addDiscBounds(
        output: MutableSet<SlippyTileKey>,
        center: GeoPoint,
        radiusMeters: Double,
        zoom: Int,
        maximum: Int,
    ) {
        val dimension = 1 shl zoom
        val latitudeRadius = Math.toDegrees(radiusMeters / 6_371_008.8)
        val cosine = cos(Math.toRadians(center.latitude)).coerceAtLeast(1e-6)
        val longitudeRadius = min(180.0, latitudeRadius / cosine)
        val north = (center.latitude + latitudeRadius).coerceAtMost(MAX_MERCATOR_LATITUDE)
        val south = (center.latitude - latitudeRadius).coerceAtLeast(-MAX_MERCATOR_LATITUDE)
        val yStart = latitudeToTileY(north, zoom)
        val yEnd = latitudeToTileY(south, zoom)
        val centerX = (center.longitude + 180.0) / 360.0 * dimension
        val xRadius = longitudeRadius / 360.0 * dimension
        val rawXStart = floor(centerX - xRadius).toInt()
        val rawXEnd = floor(centerX + xRadius).toInt()
        for (rawX in rawXStart..rawXEnd) {
            val x = ((rawX % dimension) + dimension) % dimension
            for (y in yStart..yEnd) {
                output += SlippyTileKey(zoom, x, y)
                if (output.size > maximum) throw OfflineCoverageTooLargeException(maximum)
            }
        }
    }

    private fun latitudeToTileY(latitude: Double, zoom: Int): Int {
        val lat = latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)
        val radians = Math.toRadians(lat)
        val value = (1.0 - asinh(tan(radians)) / PI) / 2.0 * (1 shl zoom)
        return floor(value).toInt().coerceIn(0, (1 shl zoom) - 1)
    }
}

object OfflineCoverageEvaluator {
    fun evaluate(
        plan: OfflineCoveragePlan,
        availableKeysByPackage: Map<ChartPackageVersionId, Set<SlippyTileKey>>,
    ): OfflineCoverageResult {
        val knownVersions = plan.packageVersionIds.filter { it in availableKeysByPackage }
        if (knownVersions.isEmpty()) {
            return OfflineCoverageResult(
                fingerprint = plan.fingerprint,
                tileAvailability = TileAvailability.UNKNOWN,
                contentFootprint = ContentFootprint.NOT_VERIFIED,
                requiredKeyCount = plan.requiredKeys.size,
                missingKeys = plan.requiredKeys,
            )
        }
        val available = knownVersions.flatMapTo(hashSetOf()) { availableKeysByPackage.getValue(it) }
        val missing = plan.requiredKeys - available
        return OfflineCoverageResult(
            fingerprint = plan.fingerprint,
            tileAvailability = if (missing.isEmpty()) TileAvailability.AVAILABLE else TileAvailability.MISSING,
            contentFootprint = ContentFootprint.NOT_VERIFIED,
            requiredKeyCount = plan.requiredKeys.size,
            missingKeys = missing,
        )
    }
}

interface LocalChartTileIndex {
    suspend fun availableKeys(chartPackage: ChartPackage, requiredKeys: Set<SlippyTileKey>): Set<SlippyTileKey>
}

private fun Double.toHex(): String = java.lang.Double.toHexString(this)
