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
import com.pr4y.app.data.prefs.DisplayPrefs

// ─── Paleta base ──────────────────────────────────────────────────────────────
val MidnightBlue = Color(0xFF0A0A0A)
val SurfaceDark  = Color(0xFF1E1E1E)
val ElectricCyan = Color(0xFF0EA5E9)

private val DarkColorScheme = darkColorScheme(
    primary          = ElectricCyan,
    primaryContainer = ElectricCyan.copy(alpha = 0.25f),
    secondary        = Color(0xFF81C784),
    background       = MidnightBlue,
    surface          = SurfaceDark,
    surfaceVariant   = ElectricCyan.copy(alpha = 0.08f),
    onSurfaceVariant = Color(0xFFB3B3B3),
    onBackground     = Color.White,
    onSurface        = Color.White,
    outlineVariant   = ElectricCyan.copy(alpha = 0.2f),
)

private val LightColorScheme = lightColorScheme(
    primary           = Color(0xFF0D47A1),
    primaryContainer  = Color(0xFFBBDEFB),
    secondary         = Color(0xFF2E7D32),
    secondaryContainer= Color(0xFFC8E6C9),
    background        = Color(0xFFF5F5F5),
    surface           = Color.White,
    surfaceVariant    = Color(0xFFE3F2FD),
    onPrimary         = Color.White,
    onSecondary       = Color.White,
    onBackground      = Color(0xFF1C1B1F),
    onSurface         = Color(0xFF1C1B1F),
    onSurfaceVariant  = Color(0xFF49454F),
    outlineVariant    = Color(0xFFCAC4D0),
)

// ─── Modo contemplativo ───────────────────────────────────────────────────────
// Oscuro: fondo profundo con tinte cálido, primario en azul pizarra suave.
private val ContemplativeDarkColorScheme = darkColorScheme(
    primary          = Color(0xFF7C9CBF),
    primaryContainer = Color(0xFF7C9CBF).copy(alpha = 0.25f),
    secondary        = Color(0xFF81C784),
    background       = Color(0xFF080608),
    surface          = Color(0xFF18151A),
    surfaceVariant   = Color(0xFF7C9CBF).copy(alpha = 0.08f),
    onSurfaceVariant = Color(0xFFA3A3A3),
    onBackground     = Color(0xFFF0EAE0),
    onSurface        = Color(0xFFF0EAE0),
    outlineVariant   = Color(0xFF7C9CBF).copy(alpha = 0.2f),
)

// Claro: fondo sepia/crema, tipografía en marrón profundo.
private val ContemplativeLightColorScheme = lightColorScheme(
    primary           = Color(0xFF6B4F3A),
    primaryContainer  = Color(0xFFDDC4A8),
    secondary         = Color(0xFF5C7A4A),
    background        = Color(0xFFF5EFE6),
    surface           = Color(0xFFFAF5EE),
    surfaceVariant    = Color(0xFFEDE0CC),
    onPrimary         = Color.White,
    onBackground      = Color(0xFF2C1E10),
    onSurface         = Color(0xFF2C1E10),
    onSurfaceVariant  = Color(0xFF5A4030),
    outlineVariant    = Color(0xFFD4C4A8),
)

// ─── Tipografía dinámica ──────────────────────────────────────────────────────
private fun buildTypography(prefs: DisplayPrefs): Typography {
    val sizeScale = when (prefs.fontSize) {
        "sm" -> 0.85f
        "lg" -> 1.15f
        "xl" -> 1.30f
        else -> 1.00f
    }
    val lineScale = when (prefs.lineSpacing) {
        "compact"  -> 0.90f
        "relaxed"  -> 1.20f
        else       -> 1.00f
    }
    // Familia para cuerpo de texto
    val bodyFamily = when (prefs.fontFamily) {
        "serif" -> FontFamily.Serif
        "mono"  -> FontFamily.Monospace
        else    -> FontFamily.Default
    }
    // Títulos: si el usuario eligió una familia explícita úsala; si no, mantener serif
    val titleFamily = when (prefs.fontFamily) {
        "mono"  -> FontFamily.Monospace
        "system"-> FontFamily.Serif
        else    -> FontFamily.Serif
    }

    fun style(family: FontFamily, sizeSp: Float, lineSp: Float) = TextStyle(
        fontFamily = family,
        fontSize   = (sizeSp * sizeScale).sp,
        lineHeight = (lineSp * sizeScale * lineScale).sp,
    )

    return Typography(
        displayLarge  = style(titleFamily, 32f, 40f),
        displayMedium = style(titleFamily, 28f, 36f),
        headlineLarge = style(titleFamily, 24f, 32f),
        headlineMedium= style(titleFamily, 22f, 28f),
        headlineSmall = style(titleFamily, 20f, 28f),
        titleLarge    = style(titleFamily, 18f, 24f),
        titleMedium   = style(bodyFamily,  16f, 24f),
        titleSmall    = style(bodyFamily,  14f, 20f),
        bodyLarge     = style(bodyFamily,  16f, 24f),
        bodyMedium    = style(bodyFamily,  14f, 20f),
        bodySmall     = style(bodyFamily,  12f, 16f),
        labelLarge    = style(bodyFamily,  14f, 20f),
        labelMedium   = style(bodyFamily,  12f, 16f),
        labelSmall    = style(bodyFamily,  11f, 16f),
    )
}

// ─── Composable público ───────────────────────────────────────────────────────
@Composable
fun Pr4yTheme(
    prefs: DisplayPrefs = DisplayPrefs(),
    content: @Composable () -> Unit,
) {
    val isDark = when (prefs.theme) {
        "light" -> false
        "dark"  -> true
        else    -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        isDark && prefs.contemplativeMode  -> ContemplativeDarkColorScheme
        !isDark && prefs.contemplativeMode -> ContemplativeLightColorScheme
        isDark                             -> DarkColorScheme
        else                               -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = buildTypography(prefs),
        content     = content,
    )
}
