package com.opushkarev.workbridge

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2457F5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE6FF),
    onPrimaryContainer = Color(0xFF001B4D),
    secondary = Color(0xFF1B8C80),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC9F3EA),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFFF0642E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF3F6FC),
    onBackground = Color(0xFF11161F),
    surface = Color(0xFFFDFEFF),
    onSurface = Color(0xFF11161F),
    surfaceVariant = Color(0xFFE3EAF6),
    onSurfaceVariant = Color(0xFF404756),
    outline = Color(0xFF70798C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C8FF),
    onPrimary = Color(0xFF002E7A),
    primaryContainer = Color(0xFF2148BA),
    onPrimaryContainer = Color(0xFFD9E5FF),
    secondary = Color(0xFF97E2D5),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005449),
    onSecondaryContainer = Color(0xFFC1F1E6),
    tertiary = Color(0xFFFFB69A),
    onTertiary = Color(0xFF5E1F00),
    background = Color(0xFF0A1220),
    onBackground = Color(0xFFE6ECF7),
    surface = Color(0xFF111B2D),
    onSurface = Color(0xFFE6ECF7),
    surfaceVariant = Color(0xFF263246),
    onSurfaceVariant = Color(0xFFC0C7D9),
    outline = Color(0xFF8A93A6),
)

private val WorkBridgeTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)

@Composable
fun WorkBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = WorkBridgeTypography,
        content = content,
    )
}
