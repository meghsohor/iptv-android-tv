package dev.meghsohor.iptvtv.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Always dark, always brand — like every other streaming app, there's no light theme, and system
// dynamic color would replace the brand palette with wallpaper-derived colors. Neither makes sense here.
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
