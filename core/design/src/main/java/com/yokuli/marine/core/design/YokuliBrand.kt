package com.yokuli.marine.core.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Approved artwork colours; never substitutes for alarm, quality or chart semantics. */
object YokuliBrandColors {
    val Ink = Color(0xFF1F2A30)
    val Signal = Color(0xFF69877F)
    val Wake = Color(0xFF5C706D)
    val Paper = Color(0xFFFAF9F6)
    val Secondary = Color(0xFF757D80)
}

/** Small surfaces extract the same O, sail and wake. There is no independent Y mark. */
@Composable
fun YokuliBrandMark(
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = Color.White,
    accent: Color = color,
    contentDescription: String? = null,
) {
    Box(modifier.semantics { contentDescription?.let { this.contentDescription = it } }) {
        Image(painterResource(R.drawable.yokuli_mark_body), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(color))
        Image(painterResource(R.drawable.yokuli_mark_beacon), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(accent))
    }
}

/** Complete monochrome wordmark, retaining the sail inside O and subordinate OS. */
@Composable
fun YokuliBrandWordmark(
    modifier: Modifier = Modifier.width(112.dp).height(30.dp),
    color: Color = Color.White,
    contentDescription: String? = "Yokuli OS",
) {
    Image(painterResource(R.drawable.yokuli_wordmark), contentDescription, modifier,
        contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(color))
}

/** Full signature; never adds a second mark to the left of the integrated wordmark. */
@Composable
fun YokuliBrandSignature(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    accent: Color = YokuliBrandColors.Signal,
) {
    Box(modifier.size(172.dp, 46.dp).semantics { contentDescription = "Yokuli OS" }) {
        Image(painterResource(R.drawable.yokuli_wordmark_body), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(color))
        Image(painterResource(R.drawable.yokuli_wordmark_sail), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(accent))
        Image(painterResource(R.drawable.yokuli_wordmark_os), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(color.copy(alpha = .65f)))
    }
}
