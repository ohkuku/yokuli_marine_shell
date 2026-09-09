package com.yokuli.anchorwatch.map

import androidx.compose.runtime.*
import com.google.android.gms.maps.model.TileProvider
import com.google.maps.android.compose.TileOverlay

/** A renderer boundary owned by Chart Library. Every app sees the same selected
 * folder layers and per-file priority; the marine engine never opens a second
 * folder index or invents its own chart ordering. */
object SharedChartLayers {
    var managedByShell by mutableStateOf(false)
        private set
    var provider by mutableStateOf<TileProvider?>(null)
        private set
    var revision by mutableLongStateOf(0L)
        private set
    private var libraryOpener: (() -> Unit)? = null

    fun update(provider: TileProvider?, revision: Long, openLibrary: (() -> Unit)? = null) {
        if(openLibrary!=null) libraryOpener=openLibrary
        this.provider=provider
        this.revision=revision
        managedByShell=true
    }
    fun openLibrary() { libraryOpener?.invoke() }
}

@Composable
fun SharedMarineTileOverlay() {
    val provider=SharedChartLayers.provider ?: return
    key(provider,SharedChartLayers.revision) {
        TileOverlay(tileProvider=provider,fadeIn=false,zIndex=MapOverlayZ.OFFLINE_CHART)
    }
}
