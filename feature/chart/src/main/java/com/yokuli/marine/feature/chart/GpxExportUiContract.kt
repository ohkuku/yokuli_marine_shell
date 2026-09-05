package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.GpxWriter
import com.yokuli.marine.map.domain.ImportedTrack
import com.yokuli.marine.map.domain.SavedPlace
import com.yokuli.marine.map.domain.SavedRoute
import java.io.OutputStream

sealed interface GpxExportTarget {
    val stableId: String
    val suggestedFileName: String
    fun writeTo(output: OutputStream)

    data class Place(val value: SavedPlace) : GpxExportTarget {
        override val stableId = value.id
        override val suggestedFileName = "${value.name.safeFileStem()}-place.gpx"
        override fun writeTo(output: OutputStream) = GpxWriter.writePlace(value, output)
    }

    data class Route(val value: SavedRoute) : GpxExportTarget {
        override val stableId = value.id
        override val suggestedFileName = "${value.name.safeFileStem()}-route.gpx"
        override fun writeTo(output: OutputStream) = GpxWriter.writeRoute(value, output)
    }

    data class Track(val value: ImportedTrack) : GpxExportTarget {
        override val stableId = value.id
        override val suggestedFileName = "${value.name.safeFileStem()}-track.gpx"
        override fun writeTo(output: OutputStream) = GpxWriter.writeTrack(value, output)
    }
}

sealed interface GpxExportUiState {
    data object Idle : GpxExportUiState
    data class AwaitingDestination(val targetId: String) : GpxExportUiState
    data class Writing(val targetId: String) : GpxExportUiState
    data class Succeeded(val targetId: String) : GpxExportUiState
    data class TargetCancelled(val targetId: String) : GpxExportUiState
    data class WriteFailed(val targetId: String) : GpxExportUiState
    data class PreparingShare(val targetId: String) : GpxExportUiState
    /** Truthful boundary: the Android sharesheet opened; delivery to another app is not claimed. */
    data class ShareOffered(val targetId: String) : GpxExportUiState
    data class ShareFailed(val targetId: String) : GpxExportUiState
}

private fun String.safeFileStem(): String = trim()
    .replace(Regex("[^A-Za-z0-9._\\-\\p{L}]+"), "-")
    .trim('-')
    .take(80)
    .ifBlank { "yokuli" }
