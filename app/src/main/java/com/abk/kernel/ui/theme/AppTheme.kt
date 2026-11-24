package com.abk.kernel.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.toColorInt
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.abk.kernel.data.repository.PreferencesRepository
import io.github.xingguangcuican6666.abk.ui.theme.AbkColorSource
import io.github.xingguangcuican6666.abk.ui.theme.AbkDefaultSeed
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme as AbkLibraryTheme

/** Whether a custom background image backs the surfaces; pages paint transparent when it does. */
val LocalAppBackgroundEnabled = staticCompositionLocalOf { false }

/** 0 = fully transparent surfaces, 1 = opaque. Only meaningful with a background image. */
val LocalUiSurfaceAlpha = staticCompositionLocalOf { 1f }

/**
 * Whether the system asks for animations to be switched off.
 *
 * The animator duration scale is zero when the user has turned animations off in the
 * system's accessibility settings. The library's own durations collapse to zero in
 * that case, so nothing travels even though the screens still change.
 */
private fun systemReduceMotion(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f) == 0f

/**
 * Applies the ABK design system from the user's preferences
 * (mode, dynamic colour, custom seed) and optionally paints a background image.
 */
@Composable
fun AppTheme(
    prefs: PreferencesRepository,
    backgroundUri: String? = null,
    backgroundEnabled: Boolean = false,
    uiSurfaceAlpha: Float = 1f,
    content: @Composable () -> Unit,
) {
    val themeMode by prefs.themeMode.collectAsState(initial = "system")
    val dynamicColorEnabled by prefs.dynamicColorEnabled.collectAsState(initial = true)
    val customThemeColorArgb by prefs.customThemeColorArgb.collectAsState(initial = null)
    val context = LocalContext.current
    val reduceMotion = remember(context) { systemReduceMotion(context) }

    AbkLibraryTheme(
        darkTheme = when (themeMode) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        },
        colorSource = if (dynamicColorEnabled) AbkColorSource.System else AbkColorSource.Seed,
        seed = customThemeColorArgb?.let(::colorFromArgb) ?: AbkDefaultSeed,
        reduceMotion = reduceMotion,
    ) {
        AppBackground(
            backgroundUri = backgroundUri,
            backgroundEnabled = backgroundEnabled,
            uiSurfaceAlpha = uiSurfaceAlpha,
            content = content,
        )
    }
}

/** Theme-only wrapper for screens that hold their own background (dialogs, overlays). */
@Composable
fun AppTheme(mode: String, dynamicColor: Boolean, themeColorArgb: Int?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember(context) { systemReduceMotion(context) }
    AbkLibraryTheme(
        darkTheme = when (mode) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        },
        colorSource = if (dynamicColor) AbkColorSource.System else AbkColorSource.Seed,
        seed = themeColorArgb?.let(::colorFromArgb) ?: AbkDefaultSeed,
        reduceMotion = reduceMotion,
        content = content,
    )
}

@Composable
fun AppBackground(
    backgroundUri: String?,
    backgroundEnabled: Boolean,
    uiSurfaceAlpha: Float,
    content: @Composable () -> Unit,
) {
    val hasBackground = backgroundEnabled && !backgroundUri.isNullOrBlank()
    if (!hasBackground) {
        CompositionLocalProvider(
            LocalAppBackgroundEnabled provides false,
            LocalUiSurfaceAlpha provides 1f,
        ) { content() }
        return
    }
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    // Decode once at display size; the painter is shared by every page that opts into it.
    val request = remember(backgroundUri, metrics.widthPixels, metrics.heightPixels) {
        ImageRequest.Builder(context)
            .data(backgroundUri)
            .size(metrics.widthPixels, metrics.heightPixels)
            .allowHardware(false)
            .crossfade(true)
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request, contentScale = ContentScale.Crop)

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0f))) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        CompositionLocalProvider(
            LocalAppBackgroundEnabled provides true,
            LocalUiSurfaceAlpha provides uiSurfaceAlpha.coerceIn(0f, 1f),
        ) { content() }
    }
}

fun colorFromArgb(argb: Int): Color = Color(argb.toLong() and 0xFFFFFFFFL)

/** Parses "#RRGGBB" / "#AARRGGBB"; null when the text is not a colour. */
fun Color.Companion.fromHexOrNull(hex: String): Color? =
    runCatching { Color(hex.toColorInt()) }.getOrNull()

/**
 * Page surface: transparent over a custom background so the image shows through,
 * otherwise the theme background.
 */
@Composable
fun pageBackgroundColor(): Color {
    val background = io.github.xingguangcuican6666.abk.ui.theme.AbkTheme.colors.background
    val hasBackground = LocalAppBackgroundEnabled.current
    if (!hasBackground) return background
    val alpha = LocalUiSurfaceAlpha.current
    return if (alpha >= 0.995f) background else background.copy(alpha = alpha)
}
