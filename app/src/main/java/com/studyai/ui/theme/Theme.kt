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
 * Hệ màu chuẩn đồng bộ toàn bộ 4 màn hình StudyAI (Light & Dark Mode).
 */
object StudyAiPalette {
    // Light Mode
    val CanvasLight = Color(0xFFF8FAFC)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceSubtleLight = Color(0xFFF1F5F9)
    val InkLight = Color(0xFF0F172A)
    val InkSecondaryLight = Color(0xFF334155)
    val InkMutedLight = Color(0xFF64748B)
    val BorderLight = Color(0xFFE2E8F0)

    // Dark Mode
    val CanvasDark = Color(0xFF0B1120)
    val SurfaceDark = Color(0xFF111827)
    val SurfaceSubtleDark = Color(0xFF1E293B)
    val InkDark = Color(0xFFF8FAFC)
    val InkSecondaryDark = Color(0xFFCBD5E1)
    val InkMutedDark = Color(0xFF94A3B8)
    val BorderDark = Color(0xFF1F2937)

    // Semantic Accents (Cố định dùng chung Light/Dark)
    val PrimaryCobalt = Color(0xFF2563EB)
    val PrimarySoftLight = Color(0xFFEFF6FF)
    val PrimarySoftDark = Color(0x293B82F6)
    val PrimaryTextLight = Color(0xFF1D4ED8)
    val PrimaryTextDark = Color(0xFF93C5FD)

    val Teal = Color(0xFF0D9488)          // Kiến thức bài trước / Ví dụ thực tế
    val TealSoftLight = Color(0xFFCCFBF1)
    val TealSoftDark = Color(0x2E14B8A6)
    val TealTextLight = Color(0xFF0F766E)
    val TealTextDark = Color(0xFF5EEAD4)

    val Amber = Color(0xFFD97706)         // Từ điển học thuật / Mẹo thi
    val AmberSoftLight = Color(0xFFFEF3C7)
    val AmberSoftDark = Color(0x2EF59E0B)
    val AmberTextLight = Color(0xFFB45309)
    val AmberTextDark = Color(0xFFFCD34D)

    val Violet = Color(0xFF7C3AED)        // Nhánh Mindmap thứ 4
    val VioletSoftLight = Color(0xFFEDE9FE)
    val VioletSoftDark = Color(0x2E8B5CF6)
    val VioletTextLight = Color(0xFF6D28D9)
    val VioletTextDark = Color(0xFFC4B5FD)

    val Rose = Color(0xFFE11D48)          // Cờ Chưa hiểu / Xóa
    val RoseSoftLight = Color(0xFFFFE4E6)
    val RoseSoftDark = Color(0x2EF43F5E)
    val RoseTextLight = Color(0xFFBE123C)
    val RoseTextDark = Color(0xFFFDA4AF)

    val CodeBg = Color(0xFF0F172A)
    val CodeInk = Color(0xFFE2E8F0)
}

val StudyAiLightColorScheme = lightColorScheme(
    primary = StudyAiPalette.PrimaryCobalt,
    onPrimary = Color.White,
    primaryContainer = StudyAiPalette.PrimarySoftLight,
    onPrimaryContainer = StudyAiPalette.PrimaryTextLight,
    secondary = StudyAiPalette.Teal,
    onSecondary = Color.White,
    secondaryContainer = StudyAiPalette.TealSoftLight,
    onSecondaryContainer = StudyAiPalette.TealTextLight,
    tertiary = StudyAiPalette.Amber,
    onTertiary = Color.White,
    tertiaryContainer = StudyAiPalette.AmberSoftLight,
    onTertiaryContainer = StudyAiPalette.AmberTextLight,
    background = StudyAiPalette.CanvasLight,
    onBackground = StudyAiPalette.InkLight,
    surface = StudyAiPalette.SurfaceLight,
    onSurface = StudyAiPalette.InkLight,
    surfaceVariant = StudyAiPalette.SurfaceSubtleLight,
    onSurfaceVariant = StudyAiPalette.InkMutedLight,
    outline = StudyAiPalette.BorderLight,
    error = StudyAiPalette.Rose,
    onError = Color.White,
    errorContainer = StudyAiPalette.RoseSoftLight,
    onErrorContainer = StudyAiPalette.RoseTextLight,
)

val StudyAiDarkColorScheme = darkColorScheme(
    primary = StudyAiPalette.PrimaryCobalt,
    onPrimary = Color.White,
    primaryContainer = StudyAiPalette.PrimarySoftDark,
    onPrimaryContainer = StudyAiPalette.PrimaryTextDark,
    secondary = StudyAiPalette.Teal,
    onSecondary = Color.White,
    secondaryContainer = StudyAiPalette.TealSoftDark,
    onSecondaryContainer = StudyAiPalette.TealTextDark,
    tertiary = StudyAiPalette.Amber,
    onTertiary = StudyAiPalette.CanvasDark,
    tertiaryContainer = StudyAiPalette.AmberSoftDark,
    onTertiaryContainer = StudyAiPalette.AmberTextDark,
    background = StudyAiPalette.CanvasDark,
    onBackground = StudyAiPalette.InkDark,
    surface = StudyAiPalette.SurfaceDark,
    onSurface = StudyAiPalette.InkDark,
    surfaceVariant = StudyAiPalette.SurfaceSubtleDark,
    onSurfaceVariant = StudyAiPalette.InkMutedDark,
    outline = StudyAiPalette.BorderDark,
    error = StudyAiPalette.Rose,
    onError = Color.White,
    errorContainer = StudyAiPalette.RoseSoftDark,
    onErrorContainer = StudyAiPalette.RoseTextDark,
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
    codeSurface = StudyAiPalette.CodeBg,
    codeBorder = StudyAiPalette.BorderDark,
    codeText = StudyAiPalette.CodeInk,
    coreBg = StudyAiPalette.PrimarySoftLight,
    coreBorder = StudyAiPalette.PrimaryCobalt,
    coreBadgeBg = StudyAiPalette.PrimarySoftLight,
    coreBadgeFg = StudyAiPalette.PrimaryTextLight,
    exampleBg = StudyAiPalette.TealSoftLight,
    exampleBorder = StudyAiPalette.Teal,
    exampleBadgeBg = StudyAiPalette.TealSoftLight,
    exampleBadgeFg = StudyAiPalette.TealTextLight,
    tipBg = StudyAiPalette.AmberSoftLight,
    tipBorder = StudyAiPalette.Amber,
    tipBadgeBg = StudyAiPalette.AmberSoftLight,
    tipBadgeFg = StudyAiPalette.AmberTextLight,
    successBg = StudyAiPalette.TealSoftLight,
    successBorder = StudyAiPalette.Teal,
    successFg = StudyAiPalette.TealTextLight,
    scholarBg = StudyAiPalette.RoseSoftLight,
    scholarFg = StudyAiPalette.RoseTextLight,
    wikiBg = StudyAiPalette.PrimarySoftLight,
    wikiFg = StudyAiPalette.PrimaryTextLight,
    videoBg = StudyAiPalette.AmberSoftLight,
    videoFg = StudyAiPalette.AmberTextLight,
)

private val DarkExtendedColors = StudyAiExtendedColors(
    codeSurface = StudyAiPalette.CodeBg,
    codeBorder = StudyAiPalette.BorderDark,
    codeText = StudyAiPalette.CodeInk,
    coreBg = StudyAiPalette.PrimarySoftDark,
    coreBorder = StudyAiPalette.PrimaryCobalt,
    coreBadgeBg = StudyAiPalette.PrimarySoftDark,
    coreBadgeFg = StudyAiPalette.PrimaryTextDark,
    exampleBg = StudyAiPalette.TealSoftDark,
    exampleBorder = StudyAiPalette.Teal,
    exampleBadgeBg = StudyAiPalette.TealSoftDark,
    exampleBadgeFg = StudyAiPalette.TealTextDark,
    tipBg = StudyAiPalette.AmberSoftDark,
    tipBorder = StudyAiPalette.Amber,
    tipBadgeBg = StudyAiPalette.AmberSoftDark,
    tipBadgeFg = StudyAiPalette.AmberTextDark,
    successBg = StudyAiPalette.TealSoftDark,
    successBorder = StudyAiPalette.Teal,
    successFg = StudyAiPalette.TealTextDark,
    scholarBg = StudyAiPalette.RoseSoftDark,
    scholarFg = StudyAiPalette.RoseTextDark,
    wikiBg = StudyAiPalette.PrimarySoftDark,
    wikiFg = StudyAiPalette.PrimaryTextDark,
    videoBg = StudyAiPalette.AmberSoftDark,
    videoFg = StudyAiPalette.AmberTextDark,
)

val LocalStudyAiExtendedColors = staticCompositionLocalOf { LightExtendedColors }
val LocalStudyAiIsDark = staticCompositionLocalOf { false }

object StudyAiExtras {
    val colors: StudyAiExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStudyAiExtendedColors.current

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalStudyAiIsDark.current
}

val StudyAiCodeTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 11.5.sp,
    lineHeight = 17.sp,
)

val StudyAiTimestampTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.5.sp,
    lineHeight = 14.sp,
)

val StudyAiTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 19.sp,
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
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun StudyAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) StudyAiDarkColorScheme else StudyAiLightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(
        LocalStudyAiExtendedColors provides extendedColors,
        LocalStudyAiIsDark provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StudyAiTypography,
            shapes = StudyAiShapes,
            content = content,
        )
    }
}
