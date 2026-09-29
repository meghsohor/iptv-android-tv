package dev.meghsohor.iptvtv.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.meghsohor.iptvtv.R

// Outfit (SIL Open Font License). Static weights: the variable font's weight axis needs API 26, minSdk is 23.
private val Outfit =
  FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
  )

private val Base = Typography()

val Typography =
  Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = Outfit),
    displayMedium = Base.displayMedium.copy(fontFamily = Outfit),
    displaySmall = Base.displaySmall.copy(fontFamily = Outfit),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Outfit),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Outfit),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Outfit),
    titleLarge = Base.titleLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = Base.titleSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Outfit, letterSpacing = 0.1.sp),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Outfit, letterSpacing = 0.1.sp),
    bodySmall = Base.bodySmall.copy(fontFamily = Outfit),
    labelLarge = Base.labelLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontFamily = Outfit),
    labelSmall = Base.labelSmall.copy(fontFamily = Outfit),
  )
