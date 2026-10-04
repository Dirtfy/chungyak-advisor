package com.chungyak.advisor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// 리디자인(docs/11): pillyze 레퍼런스 — 옅은 회색 바탕 + 흰 둥근 카드, 파란 브랜드.
val BrandBlue = Color(0xFF2F6BFF)
private val BrandBlueSoft = Color(0xFFEAF1FF)
private val Ink = Color(0xFF191F28)
private val InkSub = Color(0xFF6B7684)
private val Canvas = Color(0xFFF4F6F9)
private val CheckOrange = Color(0xFFF08A00)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueSoft,
    onPrimaryContainer = Color(0xFF1B4FD8),
    secondary = Color(0xFF4E8BFF),
    secondaryContainer = BrandBlueSoft,
    onSecondaryContainer = Color(0xFF1B4FD8),
    tertiary = CheckOrange,
    background = Canvas,
    onBackground = Ink,
    surface = Canvas,
    onSurface = Ink,
    onSurfaceVariant = InkSub,
    surfaceVariant = Color(0xFFEDF0F4),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outline = Color(0xFFB0B8C1),
    outlineVariant = Color(0xFFE5E8EB),
    error = Color(0xFFE5484D),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6E9BFF),
    onPrimary = Color(0xFF002A80),
    primaryContainer = Color(0xFF1C2B4D),
    onPrimaryContainer = Color(0xFFCFDDFF),
    secondaryContainer = Color(0xFF1C2B4D),
    onSecondaryContainer = Color(0xFFCFDDFF),
    tertiary = Color(0xFFFFB04A),
    background = Color(0xFF101318),
    surface = Color(0xFF101318),
    surfaceContainerLowest = Color(0xFF1B1F26),
    surfaceContainerLow = Color(0xFF1B1F26),
    surfaceContainer = Color(0xFF1B1F26),
    surfaceContainerHigh = Color(0xFF1B1F26),
    surfaceContainerHighest = Color(0xFF232832),
    outlineVariant = Color(0xFF2C323C),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp), // Card 기본
    large = RoundedCornerShape(24.dp),
)

private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun ChungyakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
