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

// Colores Identidad PR4Y
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
    primary = Color(0xFF3F51B5),
    secondary = Color(0xFF4CAF50),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
)

private val Pr4yTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
)

@Composable
fun Pr4yTheme(
    darkTheme: Boolean = true, // Forzamos modo oscuro por defecto para el concepto de búnker
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Pr4yTypography,
        content = content,
    )
}
