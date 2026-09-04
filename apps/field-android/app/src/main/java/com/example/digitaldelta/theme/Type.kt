package com.example.digitaldelta.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.digitaldelta.R

private val NotoSansBengali = FontFamily(
    Font(R.font.noto_sans_bengali_regular, weight = FontWeight.Normal),
)

// Every role uses the bundled bilingual family. sp respects system font scaling.
private fun fieldText(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = NotoSansBengali,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
)

val Typography = Typography(
    displayLarge = fieldText(48, 60, FontWeight.Bold),
    displayMedium = fieldText(40, 52, FontWeight.Bold),
    displaySmall = fieldText(34, 44, FontWeight.Bold),
    headlineLarge = fieldText(30, 40, FontWeight.Bold),
    headlineMedium = fieldText(26, 36, FontWeight.Bold),
    headlineSmall = fieldText(24, 34, FontWeight.Bold),
    titleLarge = fieldText(22, 32, FontWeight.SemiBold),
    titleMedium = fieldText(18, 28, FontWeight.SemiBold),
    titleSmall = fieldText(16, 24, FontWeight.SemiBold),
    bodyLarge = fieldText(18, 28),
    bodyMedium = fieldText(16, 26),
    bodySmall = fieldText(14, 22),
    labelLarge = fieldText(16, 24, FontWeight.SemiBold),
    labelMedium = fieldText(14, 22, FontWeight.SemiBold),
    labelSmall = fieldText(13, 20, FontWeight.SemiBold),
)
