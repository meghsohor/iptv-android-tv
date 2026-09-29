package dev.meghsohor.iptvtv.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark only, and no dynamic color: it would replace the brand palette.
private val MeghTVColorScheme =
  darkColorScheme(
    primary = MeghCyan,
    onPrimary = MeghBackground,
    secondary = MeghBlue,
    onSecondary = Color.White,
    tertiary = MeghMagenta,
    onTertiary = Color.White,
    background = MeghBackground,
    onBackground = MeghOnBackground,
    surface = MeghSurface,
    onSurface = MeghOnBackground,
    surfaceVariant = MeghSurfaceVariant,
    onSurfaceVariant = MeghOnSurfaceMuted,
  )

@Composable fun IPTVAndroidTVTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = MeghTVColorScheme, typography = Typography, content = content)
