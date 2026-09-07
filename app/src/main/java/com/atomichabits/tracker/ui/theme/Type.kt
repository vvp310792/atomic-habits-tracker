package com.atomichabits.tracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// MyGenetics brand type stack is 'Helvetica Neue', Helvetica, Arial - the
// brand's own paid font (Stem) is intentionally not used, per the brand kit
// itself: it's unavailable on ordinary machines and the brand style
// explicitly permits this Arial/Helvetica fallback instead. FontFamily.Default
// already resolves to the platform sans-serif on Android, but it's made
// explicit here rather than left implicit, so this typography is legibly
// "the brand's fallback stack" rather than "whatever Compose happens to default to".
private val BrandFontFamily = FontFamily.SansSerif

val AppTypography = Typography(
    headlineMedium = TextStyle(fontFamily = BrandFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = BrandFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = BrandFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = BrandFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = BrandFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)
