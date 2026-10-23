package com.vauth.foxyvpn.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun paletteColorScheme(palette: FoxyPalette) = lightColorScheme(
    primary = palette.accentDeep,
    onPrimary = Color.White,
    primaryContainer = palette.gradientTop,
    onPrimaryContainer = Color(0xFF10231A),
    secondary = palette.accent,
    onSecondary = Color.White,
    secondaryContainer = palette.gradientTop,
    onSecondaryContainer = Color(0xFF10231A),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    background = palette.gradientBottom,
    onBackground = Color(0xFF1A1D21),
    surface = Color.White,
    onSurface = Color(0xFF1A1D21),
    surfaceVariant = Color(0xFFEDF1F5),
    onSurfaceVariant = Color(0xFF5A6470),
    surfaceTint = palette.accent,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE4E8ED),
    surfaceContainer = Color(0xFFF4F6F9),
    surfaceContainerHigh = palette.cardBackground,
    surfaceContainerHighest = Color(0xFFEDF0F4),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainerLowest = Color.White,
    outline = Color(0xFF8B939D),
    outlineVariant = Color(0xFFD9DEE5),
    inverseSurface = Color(0xFF22262B),
    inverseOnSurface = Color(0xFFF2F4F7),
    scrim = Color.Black,
)

@Composable
fun FoxyVpnTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // The distributor wants a bright, pastel look; the palette itself is a
    // random pick per app open. Dark mode and dynamic color are retired.
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    val palette = FoxyPalettePicker.current
    val colorScheme = paletteColorScheme(palette)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    CompositionLocalProvider(
        LocalFoxyStatusColors provides FoxyStatusColors(
            connected = palette.accent,
            connecting = Color(0xFFB57A00),
            disconnected = Color(0xFF6C7681),
        ),
        LocalFoxyPalette provides palette,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = FoxyTypography,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
