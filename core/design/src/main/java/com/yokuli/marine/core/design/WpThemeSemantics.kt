package com.yokuli.marine.core.design

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

val WpTileAccentNameKey = SemanticsPropertyKey<String>("WpTileAccentName")
var SemanticsPropertyReceiver.wpTileAccentName by WpTileAccentNameKey

val WpThemeModeNameKey = SemanticsPropertyKey<String>("WpThemeModeName")
var SemanticsPropertyReceiver.wpThemeModeName by WpThemeModeNameKey
