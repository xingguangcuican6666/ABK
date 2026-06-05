@file:android.annotation.SuppressLint("RestrictedApi")

package com.abk.kernel.ui.theme

import android.os.Build
import com.abk.kernel.utils.findActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.google.android.material.color.utilities.CorePalette
import com.google.android.material.color.utilities.TonalPalette

val LocalUiSurfaceAlpha = staticCompositionLocalOf { 1f }

@Composable
fun uiSurfaceColor(color: Color): Color {
    val alpha = LocalUiSurfaceAlpha.current
    return if (alpha >= 0.995f) color else color.copy(alpha = color.alpha * alpha)
}

@Composable
fun AbkTheme(
    themeMode: String = "system",
    dynamicColorEnabled: Boolean = true,
    customThemeColorArgb: Int? = null,
    customAccentColorArgb: Int? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val useDynamicColor = dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamicColor -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> customExpressiveColorScheme(
            darkTheme = darkTheme,
            themeColorArgb = customThemeColorArgb,
            accentColorArgb = customAccentColorArgb
        )
    }

    val view = LocalView.current
    val activity = context.findActivity()
    if (activity != null && !view.isInEditMode) {
        SideEffect {
            val window = activity.window
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

private fun customExpressiveColorScheme(
    darkTheme: Boolean,
    themeColorArgb: Int?,
    accentColorArgb: Int?
): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else expressiveLightColorScheme()
    val themeSeed = themeColorArgb ?: base.primary.toArgb()
    val primaryCore = CorePalette.of(themeSeed)
    val accentCore = accentColorArgb?.let { CorePalette.contentOf(it) }

    val primary = primaryCore.a1
    val secondary = accentCore?.a1 ?: primaryCore.a2
    val tertiary = accentCore?.a3 ?: primaryCore.a3
    val neutral = primaryCore.n1
    val neutralVariant = primaryCore.n2
    val error = primaryCore.error

    return if (darkTheme) {
        darkSeedColorScheme(base, primary, secondary, tertiary, neutral, neutralVariant, error)
    } else {
        lightSeedColorScheme(base, primary, secondary, tertiary, neutral, neutralVariant, error)
    }
}

private fun lightSeedColorScheme(
    base: ColorScheme,
    primary: TonalPalette,
    secondary: TonalPalette,
    tertiary: TonalPalette,
    neutral: TonalPalette,
    neutralVariant: TonalPalette,
    error: TonalPalette
): ColorScheme {
    return base.copy(
        primary = primary.toneColor(40),
        onPrimary = primary.toneColor(100),
        primaryContainer = primary.toneColor(90),
        onPrimaryContainer = primary.toneColor(10),
        inversePrimary = primary.toneColor(80),
        secondary = secondary.toneColor(40),
        onSecondary = secondary.toneColor(100),
        secondaryContainer = secondary.toneColor(90),
        onSecondaryContainer = secondary.toneColor(10),
        tertiary = tertiary.toneColor(40),
        onTertiary = tertiary.toneColor(100),
        tertiaryContainer = tertiary.toneColor(90),
        onTertiaryContainer = tertiary.toneColor(10),
        background = neutral.toneColor(99),
        onBackground = neutral.toneColor(10),
        surface = neutral.toneColor(99),
        onSurface = neutral.toneColor(10),
        surfaceVariant = neutralVariant.toneColor(90),
        onSurfaceVariant = neutralVariant.toneColor(30),
        surfaceTint = primary.toneColor(40),
        inverseSurface = neutral.toneColor(20),
        inverseOnSurface = neutral.toneColor(95),
        error = error.toneColor(40),
        onError = error.toneColor(100),
        errorContainer = error.toneColor(90),
        onErrorContainer = error.toneColor(10),
        outline = neutralVariant.toneColor(50),
        outlineVariant = neutralVariant.toneColor(80),
        scrim = neutral.toneColor(0),
        surfaceDim = neutral.toneColor(87),
        surfaceBright = neutral.toneColor(98),
        surfaceContainerLowest = neutral.toneColor(100),
        surfaceContainerLow = neutral.toneColor(96),
        surfaceContainer = neutral.toneColor(94),
        surfaceContainerHigh = neutral.toneColor(92),
        surfaceContainerHighest = neutral.toneColor(90)
    )
}

private fun darkSeedColorScheme(
    base: ColorScheme,
    primary: TonalPalette,
    secondary: TonalPalette,
    tertiary: TonalPalette,
    neutral: TonalPalette,
    neutralVariant: TonalPalette,
    error: TonalPalette
): ColorScheme {
    return base.copy(
        primary = primary.toneColor(80),
        onPrimary = primary.toneColor(20),
        primaryContainer = primary.toneColor(30),
        onPrimaryContainer = primary.toneColor(90),
        inversePrimary = primary.toneColor(40),
        secondary = secondary.toneColor(80),
        onSecondary = secondary.toneColor(20),
        secondaryContainer = secondary.toneColor(30),
        onSecondaryContainer = secondary.toneColor(90),
        tertiary = tertiary.toneColor(80),
        onTertiary = tertiary.toneColor(20),
        tertiaryContainer = tertiary.toneColor(30),
        onTertiaryContainer = tertiary.toneColor(90),
        background = neutral.toneColor(10),
        onBackground = neutral.toneColor(90),
        surface = neutral.toneColor(10),
        onSurface = neutral.toneColor(90),
        surfaceVariant = neutralVariant.toneColor(30),
        onSurfaceVariant = neutralVariant.toneColor(80),
        surfaceTint = primary.toneColor(80),
        inverseSurface = neutral.toneColor(90),
        inverseOnSurface = neutral.toneColor(20),
        error = error.toneColor(80),
        onError = error.toneColor(20),
        errorContainer = error.toneColor(30),
        onErrorContainer = error.toneColor(90),
        outline = neutralVariant.toneColor(60),
        outlineVariant = neutralVariant.toneColor(30),
        scrim = neutral.toneColor(0),
        surfaceDim = neutral.toneColor(6),
        surfaceBright = neutral.toneColor(24),
        surfaceContainerLowest = neutral.toneColor(4),
        surfaceContainerLow = neutral.toneColor(10),
        surfaceContainer = neutral.toneColor(12),
        surfaceContainerHigh = neutral.toneColor(17),
        surfaceContainerHighest = neutral.toneColor(22)
    )
}

private fun TonalPalette.toneColor(tone: Int): Color = Color(this.tone(tone))
