package com.vauth.foxyvpn.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Three pastel themes (mint / rose / sky) in the style the distributor asked
 * for. One of them is picked uniformly at random on every cold app start.
 */
data class FoxyPalette(
    val name: String,
    val gradientTop: Color,
    val gradientBottom: Color,
    val accent: Color,
    val accentDeep: Color,
    val cardBackground: Color = Color(0xFFF7F9FC),
)

val MintPalette = FoxyPalette(
    name = "mint",
    gradientTop = Color(0xFFE7F8EF),
    gradientBottom = Color(0xFFF4FBF7),
    accent = Color(0xFF27C07D),
    accentDeep = Color(0xFF128A56),
)

val RosePalette = FoxyPalette(
    name = "rose",
    gradientTop = Color(0xFFFDE8EC),
    gradientBottom = Color(0xFFFEF4F6),
    accent = Color(0xFFE9486C),
    accentDeep = Color(0xFFB72749),
)

val SkyPalette = FoxyPalette(
    name = "sky",
    gradientTop = Color(0xFFE3F0FD),
    gradientBottom = Color(0xFFF2F8FE),
    accent = Color(0xFF2E9BF0),
    accentDeep = Color(0xFF1668B4),
)

val FoxyPalettes = listOf(MintPalette, RosePalette, SkyPalette)

/** One random palette per process (i.e. per app open). */
object FoxyPalettePicker {
    val current: FoxyPalette by lazy { FoxyPalettes.random() }
}

val LocalFoxyPalette = staticCompositionLocalOf { SkyPalette }

fun FoxyPalette.gradientBrush(): Brush = Brush.verticalGradient(listOf(gradientTop, gradientBottom))

fun FoxyPalette.accentBrush(): Brush = Brush.linearGradient(listOf(accent, accentDeep))
