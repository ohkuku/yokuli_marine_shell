package com.yokuli.marine.map.domain

enum class ChartSourceId { USER_IMPORT, NOAA_NCDS }

enum class ChartSourceCapability {
    OFFLINE_DISPLAY,
    USER_IMPORT,
    REDISTRIBUTION,
    AUTOMATED_DOWNLOAD,
}

enum class ChartAcquisitionMode { IMPORT_ONLY, VERIFIED_DOWNLOAD }

enum class SourceDeliveryStatus { AVAILABLE, BLOCKED_EXTERNAL }

data class ChartSourceDescriptor(
    val id: ChartSourceId,
    val publisher: String,
    val informationUrl: String,
    val licenseEvidenceUrl: String,
    val reviewedAtUtc: String,
    val useLimitations: String,
    val attribution: String,
    val sourceVersion: String,
    val capabilities: Set<ChartSourceCapability>,
    val acquisition: ChartAcquisitionMode,
    val deliveryStatus: SourceDeliveryStatus,
) {
    init {
        require(publisher.isNotBlank())
        require(informationUrl.startsWith("https://"))
        require(licenseEvidenceUrl.startsWith("https://"))
        require(reviewedAtUtc.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        require(useLimitations.isNotBlank())
        require(attribution.isNotBlank())
        require(sourceVersion.isNotBlank())
        require(ChartSourceCapability.OFFLINE_DISPLAY in capabilities)
        require(ChartSourceCapability.USER_IMPORT in capabilities)
        require(
            acquisition != ChartAcquisitionMode.VERIFIED_DOWNLOAD ||
                ChartSourceCapability.AUTOMATED_DOWNLOAD in capabilities,
        )
    }
}

/**
 * Audited source facts do not turn a display product into a navigation-suitability claim.
 * Yokuli currently exposes the same real SAF import path for both entries and no in-app downloader.
 */
object ProductionChartSources {
    val entries: List<ChartSourceDescriptor> = listOf(
        ChartSourceDescriptor(
            id = ChartSourceId.USER_IMPORT,
            publisher = "User selected source",
            informationUrl = "https://github.com/ohkuku/yokuli_marine_shell",
            licenseEvidenceUrl = "https://github.com/ohkuku/yokuli_marine_shell",
            reviewedAtUtc = "2026-09-05",
            useLimitations = "The user must verify permission, currency, coverage and fitness for their use.",
            attribution = "Read from the imported package or retained as Unknown.",
            sourceVersion = "Unknown",
            capabilities = setOf(ChartSourceCapability.OFFLINE_DISPLAY, ChartSourceCapability.USER_IMPORT),
            acquisition = ChartAcquisitionMode.IMPORT_ONLY,
            deliveryStatus = SourceDeliveryStatus.AVAILABLE,
        ),
        ChartSourceDescriptor(
            id = ChartSourceId.NOAA_NCDS,
            publisher = "NOAA Office of Coast Survey",
            informationUrl = "https://distribution.charts.noaa.gov/ncds/index.html",
            licenseEvidenceUrl = "https://www.nauticalcharts.noaa.gov/data/data-licensing.html",
            reviewedAtUtc = "2026-09-05",
            useLimitations = "United States coverage; display data does not establish carriage compliance or route safety.",
            attribution = "Provided by NOAA Office of Coast Survey",
            sourceVersion = "Unknown until the selected package is inspected",
            capabilities = setOf(
                ChartSourceCapability.OFFLINE_DISPLAY,
                ChartSourceCapability.USER_IMPORT,
                ChartSourceCapability.REDISTRIBUTION,
            ),
            acquisition = ChartAcquisitionMode.IMPORT_ONLY,
            deliveryStatus = SourceDeliveryStatus.BLOCKED_EXTERNAL,
        ),
    )
}
