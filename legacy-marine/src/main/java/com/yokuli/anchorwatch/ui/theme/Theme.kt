package com.yokuli.anchorwatch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun YokuliTheme(accent: Color = Color(0xFF00ABA9), light: Boolean = false, content: @Composable () -> Unit) {
    val background=if(light)Color.White else Color.Black
    val foreground=if(light)Color.Black else Color.White
    val secondary=if(light)Color(0xFF666666) else Color(0xFFAAAAAA)
    val raised=if(light)Color(0xFFF0F0F0) else Color(0xFF151515)
    val scheme=if(light)lightColorScheme()else darkColorScheme()
    MaterialTheme(
        colorScheme=scheme.copy(primary=accent,onPrimary=Color.White,primaryContainer=raised,onPrimaryContainer=foreground,secondary=accent,onSecondary=Color.White,secondaryContainer=raised,onSecondaryContainer=foreground,tertiary=accent,background=background,onBackground=foreground,surface=background,onSurface=foreground,surfaceVariant=raised,onSurfaceVariant=secondary,surfaceTint=Color.Transparent,outline=secondary),
        typography=YokuliTypography,
        shapes=YokuliShapes,
    ) {
        // The embedded workspace is a Column, so it has no Material Surface to
        // supply this foreground. Inheriting Shell's color can hide dark titles.
        CompositionLocalProvider(LocalContentColor provides foreground, content=content)
    }
}
