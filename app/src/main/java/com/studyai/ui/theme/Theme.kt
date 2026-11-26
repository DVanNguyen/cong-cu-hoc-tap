package com.studyai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hệ màu "Editorial Academic Notebook" cho StudyAI:
 * - Light Mode: Warm Paper & Oxford Ink — dịu mắt khi đọc giáo trình & chép bài ban ngày.
 * - Dark Mode: Night Study Desk — độ tương phản cao >= 4.5:1, không chói mắt ban đêm.
 */

// --- LIGHT PALETTE (Warm Paper & Oxford Ink) ---
private val LightPrimary = Color(0xFF1E3A8A)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFDBEAFE)
private val LightOnPrimaryContainer = Color(0xFF172554)

private val LightSecondary = Color(0xFF0F766E)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFCCFBF1)
private val LightOnSecondaryContainer = Color(0xFF115E59)

private val LightTertiary = Color(0xFFB45309)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFFEF3C7)
private val LightOnTertiaryContainer = Color(0xFF78350F)

private val LightBackground = Color(0xFFFBF9F5)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFF2EFE9)
private val LightOnSurface = Color(0xFF18181B)
private val LightOnSurfaceVariant = Color(0xFF52525B)
private val LightOutline = Color(0xFFDAD4C8)
private val LightError = Color(0xFFB91C1C)
private val LightErrorContainer = Color(0xFFFEE2E2)
private val LightOnErrorContainer = Color(0xFF7F1D1D)

// --- DARK PALETTE (Night Study Desk) ---
private val DarkPrimary = Color(0xFF93C5FD)
private val DarkOnPrimary = Color(0xFF0F172A)
private val DarkPrimaryContainer = Color(0xFF1E3A5F)
private val DarkOnPrimaryContainer = Color(0xFFDBEAFE)

private val DarkSecondary = Color(0xFF5EEAD4)
private val DarkOnSecondary = Color(0xFF042F2E)
private val DarkSecondaryContainer = Color(0xFF134E4A)
private val DarkOnSecondaryContainer = Color(0xFFCCFBF1)

private val DarkTertiary = Color(0xFFFCD34D)
private val DarkOnTertiary = Color(0xFF451A03)
private val DarkTertiaryContainer = Color(0xFF5C3B09)
private val DarkOnTertiaryContainer = Color(0xFFFEF3C7)

private val DarkBackground = Color(0xFF0D1117)
private val DarkSurface = Color(0xFF161B22)
private val DarkSurfaceVariant = Color(0xFF21262D)
private val DarkOnSurface = Color(0xFFF0F3F6)
private val DarkOnSurfaceVariant = Color(0xFF9CA3AF)
private val DarkOutline = Color(0xFF30363D)
private val DarkError = Color(0xFFFCA5A5)
private val DarkErrorContainer = Color(0xFF5F1919)
private val DarkOnErrorContainer = Color(0xFFFEE2E2)

val StudyAiLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
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
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
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

@Immutable
data class StudyAiExtendedColors(
    val codeSurface: Color,
    val codeBorder: Color,
    val codeText: Color,
    val coreBg: Color,
    val coreBorder: Color,
    val coreBadgeBg: Color,
    val coreBadgeFg: Color,
    val exampleBg: Color,
    val exampleBorder: Color,
    val exampleBadgeBg: Color,
    val exampleBadgeFg: Color,
    val tipBg: Color,
    val tipBorder: Color,
    val tipBadgeBg: Color,
    val tipBadgeFg: Color,
    val successBg: Color,
    val successBorder: Color,
    val successFg: Color,
    val scholarBg: Color,
    val scholarFg: Color,
    val wikiBg: Color,
    val wikiFg: Color,
    val videoBg: Color,
    val videoFg: Color,
)

private val LightExtendedColors = StudyAiExtendedColors(
    codeSurface = Color(0xFFF3F4F6),
    codeBorder = Color(0xFFD6DAE3),
    codeText = Color(0xFF0F172A),
    coreBg = Color(0xFFEEF6FF),
    coreBorder = Color(0xFFBFDBFE),
    coreBadgeBg = Color(0xFFDBEAFE),
    coreBadgeFg = Color(0xFF1E3A8A),
    exampleBg = Color(0xFFECFDF5),
    exampleBorder = Color(0xFFA7F3D0),
    exampleBadgeBg = Color(0xFFD1FAE5),
    exampleBadgeFg = Color(0xFF065F46),
    tipBg = Color(0xFFFFFBEB),
    tipBorder = Color(0xFFFDE68A),
    tipBadgeBg = Color(0xFFFEF3C7),
    tipBadgeFg = Color(0xFF92400E),
    successBg = Color(0xFFECFDF5),
    successBorder = Color(0xFF6EE7B7),
    successFg = Color(0xFF065F46),
    scholarBg = Color(0xFFFEF2F2),
    scholarFg = Color(0xFF991B1B),
    wikiBg = Color(0xFFEFF6FF),
    wikiFg = Color(0xFF1E40AF),
    videoBg = Color(0xFFFFF7ED),
    videoFg = Color(0xFF9A3412),
)

private val DarkExtendedColors = StudyAiExtendedColors(
    codeSurface = Color(0xFF0B0F15),
    codeBorder = Color(0xFF2B323D),
    codeText = Color(0xFFE2E8F0),
    coreBg = Color(0xFF122136),
    coreBorder = Color(0xFF284770),
    coreBadgeBg = Color(0xFF1E3A5F),
    coreBadgeFg = Color(0xFFBFDBFE),
    exampleBg = Color(0xFF0F2922),
    exampleBorder = Color(0xFF1F5446),
    exampleBadgeBg = Color(0xFF134E4A),
    exampleBadgeFg = Color(0xFF99F6E4),
    tipBg = Color(0xFF2A200B),
    tipBorder = Color(0xFF5C4415),
    tipBadgeBg = Color(0xFF4B350C),
    tipBadgeFg = Color(0xFFFDE68A),
    successBg = Color(0xFF0F2922),
    successBorder = Color(0xFF23624D),
    successFg = Color(0xFFA7F3D0),
    scholarBg = Color(0xFF3A181A),
    scholarFg = Color(0xFFFCA5A5),
    wikiBg = Color(0xFF172742),
    wikiFg = Color(0xFF93C5FD),
    videoBg = Color(0xFF3A2112),
    videoFg = Color(0xFFFDBA74),
)

val LocalStudyAiExtendedColors = staticCompositionLocalOf { LightExtendedColors }

object StudyAiExtras {
    val colors: StudyAiExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStudyAiExtendedColors.current
}

val StudyAiCodeTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 19.sp,
)

val StudyAiTimestampTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.5.sp,
    lineHeight = 16.sp,
)

val StudyAiTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.5.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.5.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
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
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun StudyAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) StudyAiDarkColorScheme else StudyAiLightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalStudyAiExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StudyAiTypography,
            shapes = StudyAiShapes,
            content = content,
        )
    }
}
