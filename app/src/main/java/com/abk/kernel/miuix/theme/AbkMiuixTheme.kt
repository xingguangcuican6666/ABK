package com.abk.kernel.miuix.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Composable
fun AbkMiuixTheme(
    themeMode: String = "system",
    dynamicColorEnabled: Boolean = true,
    customThemeColorArgb: Int? = null,
    colorStyleName: String = "TonalSpot",
    colorSpecName: String = "Spec2021",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamicColor = dynamicColorEnabled && dynamicColorAvailable

    val mode = when {
        useDynamicColor -> when {
            darkTheme -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.MonetLight
        }
        else -> when {
            darkTheme -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.Light
        }
    }

    // 0 or null => no seed, so Monet modes fall back to the real system wallpaper
    // palette; otherwise generate the scheme from the user-picked ARGB value.
    val seedArgb = customThemeColorArgb?.takeIf { it != 0 }
    val keyColor = seedArgb?.let { Color(it) }

    val paletteStyle = remember(colorStyleName) {
        runCatching { ThemePaletteStyle.valueOf(colorStyleName) }
            .getOrDefault(ThemePaletteStyle.TonalSpot)
    }
    val colorSpec = remember(colorSpecName) {
        runCatching { ThemeColorSpec.valueOf(colorSpecName) }
            .getOrDefault(ThemeColorSpec.Spec2021)
    }

    val controller = remember(mode, keyColor, paletteStyle, colorSpec) {
        ThemeController(
            colorSchemeMode = mode,
            keyColor = keyColor,
            paletteStyle = paletteStyle,
            colorSpec = colorSpec,
        )
    }

    MiuixTheme(controller = controller, content = content)
}
