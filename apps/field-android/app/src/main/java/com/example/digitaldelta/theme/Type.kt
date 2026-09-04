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

val Typography =
  Typography(
    displaySmall = TextStyle(
      fontFamily = NotoSansBengali,
      fontWeight = FontWeight.Bold,
      fontSize = 32.sp,
      lineHeight = 40.sp,
      letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
      fontFamily = NotoSansBengali,
      fontWeight = FontWeight.Bold,
      fontSize = 24.sp,
      lineHeight = 32.sp,
      letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
      fontFamily = NotoSansBengali,
      fontWeight = FontWeight.SemiBold,
      fontSize = 20.sp,
      lineHeight = 28.sp,
      letterSpacing = 0.sp,
    ),
    bodyLarge =
      TextStyle(
        fontFamily = NotoSansBengali,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
      ),
    bodyMedium = TextStyle(
      fontFamily = NotoSansBengali,
      fontWeight = FontWeight.Normal,
      fontSize = 14.sp,
      lineHeight = 22.sp,
      letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
      fontFamily = NotoSansBengali,
      fontWeight = FontWeight.SemiBold,
      fontSize = 14.sp,
      lineHeight = 20.sp,
      letterSpacing = 0.sp,
    ),
  )
