@file:OptIn(ExperimentalTextApi::class)

package com.lifepowers.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lifepowers.app.R

/** One Nunito weight from the bundled variable font, with the matching wght axis set. */
private fun nunito(weight: Int): Font = Font(
    R.font.nunito,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Nunito at weights 500 to 900, from res/font/nunito.ttf (a variable font). */
val LifePowersFontFamily: FontFamily = FontFamily(
    nunito(500),
    nunito(600),
    nunito(700),
    nunito(800),
    nunito(900),
)

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
