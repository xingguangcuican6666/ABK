@file:android.annotation.SuppressLint("RestrictedApi")

package com.abk.kernel.ui.theme

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.abk.kernel.utils.findActivity
import io.github.xingguangcuican6666.abk.ui.theme.AbkColors
import io.github.xingguangcuican6666.abk.ui.theme.AbkDefaultSeed
import io.github.xingguangcuican6666.abk.ui.theme.abkColorsFromMaterial
import io.github.xingguangcuican6666.abk.ui.theme.abkSeedColors
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme as ComponentTheme

val LocalUiSurfaceAlpha = staticCompositionLocalOf { 1f }
val LocalAppBackgroundEnabled = staticCompositionLocalOf { false }

@Composable
fun uiSurfaceColor(color: Color): Color {
    val alpha = LocalUiSurfaceAlpha.current
    return if (alpha >= 0.995f) color else color.copy(alpha = color.alpha * alpha)
}

@Composable
fun appPageBackgroundColor(color: Color): Color =
    if (LocalAppBackgroundEnabled.current) Color.Transparent else color

/** Preferences and window integration stay in the host, not in the component library. */
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
    val colors = remember(context, darkTheme, dynamicColorEnabled, customThemeColorArgb, customAccentColorArgb) {
        if (dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            abkColorsFromMaterial(
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
                darkTheme
            )
        } else {
            appSeedColors(darkTheme, customThemeColorArgb, customAccentColorArgb)
        }
    }
    val view = LocalView.current
    val activity = context.findActivity()
    if (activity != null && !view.isInEditMode) {
        SideEffect {
            val window = activity.window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.raised.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    ComponentTheme(
        darkTheme = darkTheme,
        colorsOverride = colors,
        reduceMotion = rememberReducedMotion(),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.ink, content = content)
    }
}

internal fun appSeedColors(dark: Boolean, themeArgb: Int?, accentArgb: Int?): AbkColors {
    val base = abkSeedColors(themeArgb?.let(::Color) ?: AbkDefaultSeed, dark)
    if (accentArgb == null) return base
    val accent = abkSeedColors(Color(accentArgb), dark).material
    // Retain the existing secondary-accent preference without replacing neutral surfaces.
    val material = base.material.copy(
        secondary = accent.primary,
        onSecondary = accent.onPrimary,
        secondaryContainer = accent.primaryContainer,
        onSecondaryContainer = accent.onPrimaryContainer,
        tertiary = accent.tertiary,
        onTertiary = accent.onTertiary,
        tertiaryContainer = accent.tertiaryContainer,
        onTertiaryContainer = accent.onTertiaryContainer,
    )
    return abkColorsFromMaterial(material, dark).copy(inkMuted = base.inkMuted, inkFaint = base.inkFaint)
}

@Composable
private fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun disabled() = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
    var reduced by remember(resolver) { mutableStateOf(disabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = disabled() }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
