package com.studyai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Bảng màu học thuật hiện đại dành cho StudyAI (Indigo + Teal + Amber)
private val IndigoPrimaryLight = Color(0xFF3730A3)
private val IndigoOnPrimaryLight = Color(0xFFFFFFFF)
private val IndigoContainerLight = Color(0xFFE0E7FF)
private val IndigoOnContainerLight = Color(0xFF1E1B4B)

private val TealSecondaryLight = Color(0xFF0D9488)
private val TealOnSecondaryLight = Color(0xFFFFFFFF)
private val TealContainerLight = Color(0xFFCCFBF1)
private val TealOnContainerLight = Color(0xFF134E4A)

private val AmberTertiaryLight = Color(0xFFD97706)
private val AmberOnTertiaryLight = Color(0xFFFFFFFF)
private val AmberContainerLight = Color(0xFFFEF3C7)
private val AmberOnContainerLight = Color(0xFF78350F)

private val LightBackground = Color(0xFFF8FAFC)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFF1F5F9)
private val LightOnSurface = Color(0xFF0F172A)
private val LightOnSurfaceVariant = Color(0xFF475569)
private val LightOutline = Color(0xFFCBD5E1)
private val LightError = Color(0xFFDC2626)
private val LightErrorContainer = Color(0xFFFEE2E2)
private val LightOnErrorContainer = Color(0xFF7F1D1D)

// Bảng màu Dark Mode dễ chịu khi học ban đêm
private val IndigoPrimaryDark = Color(0xFF818CF8)
private val IndigoOnPrimaryDark = Color(0xFF1E1B4B)
private val IndigoContainerDark = Color(0xFF312E81)
private val IndigoOnContainerDark = Color(0xFFE0E7FF)

private val TealSecondaryDark = Color(0xFF2DD4BF)
private val TealOnSecondaryDark = Color(0xFF042F2E)
private val TealContainerDark = Color(0xFF134E4A)
private val TealOnContainerDark = Color(0xFFCCFBF1)

private val AmberTertiaryDark = Color(0xFFFBBF24)
private val AmberOnTertiaryDark = Color(0xFF451A03)
private val AmberContainerDark = Color(0xFF78350F)
private val AmberOnContainerDark = Color(0xFFFEF3C7)

private val DarkBackground = Color(0xFF0B1120)
private val DarkSurface = Color(0xFF111827)
private val DarkSurfaceVariant = Color(0xFF1E293B)
private val DarkOnSurface = Color(0xFFF1F5F9)
private val DarkOnSurfaceVariant = Color(0xFF94A3B8)
private val DarkOutline = Color(0xFF334155)
private val DarkError = Color(0xFFF87171)
private val DarkErrorContainer = Color(0xFF7F1D1D)
private val DarkOnErrorContainer = Color(0xFFFEE2E2)

val StudyAiLightColorScheme = lightColorScheme(
    primary = IndigoPrimaryLight,
    onPrimary = IndigoOnPrimaryLight,
    primaryContainer = IndigoContainerLight,
    onPrimaryContainer = IndigoOnContainerLight,
    secondary = TealSecondaryLight,
    onSecondary = TealOnSecondaryLight,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = TealOnContainerLight,
    tertiary = AmberTertiaryLight,
    onTertiary = AmberOnTertiaryLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = AmberOnContainerLight,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = LightError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
)

val StudyAiDarkColorScheme = darkColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = IndigoOnPrimaryDark,
    primaryContainer = IndigoContainerDark,
    onPrimaryContainer = IndigoOnContainerDark,
    secondary = TealSecondaryDark,
    onSecondary = TealOnSecondaryDark,
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = TealOnContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = AmberOnTertiaryDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = AmberOnContainerDark,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = DarkError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
)

val StudyAiTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    ),
)

val StudyAiShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun StudyAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) StudyAiDarkColorScheme else StudyAiLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = StudyAiTypography,
        shapes = StudyAiShapes,
        content = content,
    )
}