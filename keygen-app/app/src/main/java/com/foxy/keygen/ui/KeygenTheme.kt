package com.foxy.keygen.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class KeygenColors(
    val accent: Color = Color(0xFF0E9F8A),
    val accentDeep: Color = Color(0xFF0A7264),
    val background: Color = Color(0xFFF6F8FB),
)

val LocalKeygenColors = staticCompositionLocalOf { KeygenColors() }

@Composable
fun rememberKeygenAccent(): Color = LocalKeygenColors.current.accent

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A7264),
    onPrimary = Color.White,
    secondary = Color(0xFF0E9F8A),
    onSecondary = Color.White,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF17222C),
    surface = Color.White,
    onSurface = Color(0xFF17222C),
    surfaceVariant = Color(0xFFEDF1F5),
    onSurfaceVariant = Color(0xFF5A6470),
    outline = Color(0xFF8B939D),
    outlineVariant = Color(0xFFD9DEE5),
)

private val KeygenTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
)

@Composable
fun KeygenTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKeygenColors provides KeygenColors()) {
        MaterialTheme(
            colorScheme = LightColors,
            typography = KeygenTypography,
            content = content,
        )
    }
}

@Composable
fun fieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accent,
    unfocusedBorderColor = Color(0xFFD9DEE5),
    focusedLabelColor = accent,
    cursorColor = accent,
    focusedLeadingIconColor = accent,
)
