package com.yokuli.marine.map.domain

data class ChartPackageCandidate(
    val stagedImportId: String,
    val suggestedDisplayName: String,
    val suggestedSource: String,
    val suggestedLicense: String,
    val suggestedAttribution: String,
    val suggestedVersion: String,
    val sha256: String,
    val coverage: GeoBounds,
    val minZoom: Int,
    val maxZoom: Int,
    val rasterFormat: String,
)

data class ChartPackageImportRequest(
    val stagedImportId: String,
    val displayName: String,
    val source: String,
    val license: String,
    val attribution: String,
    val version: String,
)

class ChartPackageImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface ChartPackageRepository {
    suspend fun inspect(sourceUri: String): ChartPackageCandidate
    suspend fun commit(request: ChartPackageImportRequest): ChartPackage
    suspend fun discard(stagedImportId: String)
    suspend fun listInstalled(): List<ChartPackage>
    suspend fun delete(packageId: ChartPackageId)
}
