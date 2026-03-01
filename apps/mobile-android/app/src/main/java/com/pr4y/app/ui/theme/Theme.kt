package com.pr4y.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Colores identidad PR4Y — tonos suaves y legibles
val MidnightBlue = Color(0xFF0A0A0A)
val SurfaceDark = Color(0xFF1E1E1E)
val ElectricCyan = Color(0xFF0EA5E9)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    primaryContainer = ElectricCyan.copy(alpha = 0.25f),
    secondary = Color(0xFF81C784),
    background = MidnightBlue,
    surface = SurfaceDark,
    surfaceVariant = ElectricCyan.copy(alpha = 0.08f),
    onSurfaceVariant = Color(0xFFB3B3B3),
    onBackground = Color.White,
    onSurface = Color.White,
    outlineVariant = ElectricCyan.copy(alpha = 0.2f),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFF2E7D32),
    secondaryContainer = Color(0xFFC8E6C9),
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFE3F2FD),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454F),
    outlineVariant = Color(0xFFCAC4D0),
)

// Tipografía cálida y legible: serif en títulos, default en cuerpo (respeta tamaño del sistema vía sp)
private val Pr4yTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun Pr4yTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Pr4yTypography,
        content = content,
    )
}
