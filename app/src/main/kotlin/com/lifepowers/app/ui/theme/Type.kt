package com.lifepowers.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * The design calls for Nunito at weights 500 to 900. Loading it through the Google Fonts provider
 * needs the certificates resource (R.array.com_google_android_gms_fonts_certs), which the UI layer
 * cannot add. Until it exists the app uses the platform rounded sans serif at the same weights.
 * See app/NEEDS-ui.md.
 */
val LifePowersFontFamily: FontFamily = FontFamily.SansSerif

val LifePowersTypography = Typography(
    displayLarge = TextStyle(fontFamily = LifePowersFontFamily, fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 42.sp),
    displayMedium = TextStyle(fontFamily = LifePowersFontFamily, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 30.sp),
    headlineLarge = TextStyle(fontFamily = LifePowersFontFamily, fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = LifePowersFontFamily, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = LifePowersFontFamily, fontWeight = FontWeight.Black, fontSize = 22.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = LifePowersFontFamily, fontWeight = FontWeight.Black, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = LifePowersFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = LifePowersFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = LifePowersFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = LifePowersFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = LifePowersFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = LifePowersFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = LifePowersFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = LifePowersFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp),
)
