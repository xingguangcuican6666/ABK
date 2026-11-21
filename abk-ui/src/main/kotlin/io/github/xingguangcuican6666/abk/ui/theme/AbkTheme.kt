package io.github.xingguangcuican6666.abk.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingguangcuican6666.abk.ui.R

public object AbkSpacing {
    public val Xs = 4.dp
    public val Sm = 8.dp
    public val Md = 12.dp
    public val Lg = 16.dp
    public val Xl = 20.dp
    public val Xxl = 24.dp
    public val Section = 32.dp
    public val Wide = 40.dp
    public val Hero = 56.dp
}

public object AbkShapes {
    public val Small = RoundedCornerShape(8.dp)
    public val Medium = RoundedCornerShape(12.dp)
    public val Large = RoundedCornerShape(18.dp)
    public val ExtraLarge = RoundedCornerShape(26.dp)
    public val Pill = RoundedCornerShape(50)
}

public object AbkMotion {
    public const val Fast: Int = 120
    public const val Base: Int = 220
    public const val Slow: Int = 360
}

public object AbkFonts {
    public val Hand: FontFamily = FontFamily(
        Font(R.font.abk_wenkai_regular, FontWeight.Normal),
        Font(R.font.abk_wenkai_bold, FontWeight.Bold),
    )
    public val Mono: FontFamily = FontFamily(Font(R.font.abk_wenkai_mono_regular))
}

public fun abkTypography(fontFamily: FontFamily = AbkFonts.Hand): Typography {
    fun style(size: Int, line: Int, bold: Boolean = false) = TextStyle(
        fontFamily = fontFamily, fontSize = size.sp, lineHeight = line.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
    return Typography(
        displayLarge = style(36, 45, true), displayMedium = style(32, 40, true),
        displaySmall = style(28, 35, true), headlineLarge = style(28, 35, true),
        headlineMedium = style(24, 30, true), headlineSmall = style(22, 28, true),
        titleLarge = style(22, 28, true), titleMedium = style(18, 24, true),
        titleSmall = style(16, 22, true), bodyLarge = style(16, 26),
        bodyMedium = style(14, 23), bodySmall = style(12, 19),
        labelLarge = style(16, 22, true), labelMedium = style(14, 20, true),
        labelSmall = style(12, 16),
    )
}

@Immutable
internal data class AbkThemeValues(val colors: AbkColors, val reduceMotion: Boolean, val mono: FontFamily)
private val LocalAbkTheme = staticCompositionLocalOf {
    AbkThemeValues(abkSeedColors(), false, AbkFonts.Mono)
}

public object AbkTheme {
    public val colors: AbkColors @Composable @ReadOnlyComposable get() = LocalAbkTheme.current.colors
    public val reduceMotion: Boolean @Composable @ReadOnlyComposable get() = LocalAbkTheme.current.reduceMotion
    public val monoFont: FontFamily @Composable @ReadOnlyComposable get() = LocalAbkTheme.current.mono
}

/** No persistence or wallpaper permission: the host owns theme preferences. */
@Composable
public fun AbkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSource: AbkColorSource = AbkColorSource.System,
    seed: Color = AbkDefaultSeed,
    fontFamily: FontFamily = AbkFonts.Hand,
    monoFontFamily: FontFamily = AbkFonts.Mono,
    reduceMotion: Boolean = false,
    colorsOverride: AbkColors? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = remember(context, darkTheme, colorSource, seed, colorsOverride) {
        if (colorsOverride != null) colorsOverride
        else if (colorSource == AbkColorSource.System && Build.VERSION.SDK_INT >= 31) {
            abkColorsFromMaterial(
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context), darkTheme,
            )
        } else abkSeedColors(seed, darkTheme)
    }
    val typography = remember(fontFamily) { abkTypography(fontFamily) }
    CompositionLocalProvider(LocalAbkTheme provides AbkThemeValues(colors, reduceMotion, monoFontFamily)) {
        MaterialTheme(
            colorScheme = colors.material, typography = typography,
            shapes = Shapes(AbkShapes.Small, AbkShapes.Small, AbkShapes.Medium, AbkShapes.Large, AbkShapes.ExtraLarge),
            content = content,
        )
    }
}
