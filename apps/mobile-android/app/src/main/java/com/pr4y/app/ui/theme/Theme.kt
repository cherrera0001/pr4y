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

// onSurfaceVariant ≥ 4.5:1 sobre #121212 para modo oración (accesibilidad en poca luz)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7986CB),
    secondary = Color(0xFF81C784),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFB3B3B3),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3F51B5),
    secondary = Color(0xFF4CAF50),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
)

/** Tipografía con cuerpo de lectura cómoda para oración en poca luz: 16sp, lineHeight ≥24sp (accesibilidad). */
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
