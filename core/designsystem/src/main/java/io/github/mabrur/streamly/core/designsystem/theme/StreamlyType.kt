package io.github.mabrur.streamly.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Sans = FontFamily.SansSerif

/**
 * Scale read off `streamly.dc.html`. Headings are ExtraBold (800) — that weight is the
 * design's most recognisable characteristic, so it is preserved exactly.
 */
val StreamlyType = Typography(
    // Onboarding hero — 28/800
    displaySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp, lineHeight = 34.sp,
    ),
    // Screen headers ("Downloads") — 22/800
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    // App bar wordmark — 20/800
    headlineSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    // Player title, dialog title, profile name — 17/800
    titleLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold,
        fontSize = 17.sp, lineHeight = 22.sp,
    ),
    // Feed card title — 15/700
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    // Download row title — 14.5/700
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 14.5.sp, lineHeight = 19.sp,
    ),
    // Profile rows — 15/600
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 21.sp,
    ),
    // Dialog body, shorts caption — 13.5/400
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp, lineHeight = 20.sp,
    ),
    // Card meta — 12.5/400
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp, lineHeight = 17.sp,
    ),
    // Buttons — 14/700
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, lineHeight = 18.sp,
    ),
    // Chips, status lines — 13/700
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 13.sp, lineHeight = 17.sp,
    ),
    // Duration badge — 11/700
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 14.sp,
    ),
)
