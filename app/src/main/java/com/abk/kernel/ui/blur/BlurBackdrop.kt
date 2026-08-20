package com.abk.kernel.ui.blur

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import com.abk.kernel.ui.theme.LocalUiSurfaceAlpha
import kotlin.math.max

internal const val AbkBlurRadius = 25f
internal const val AbkBlurBackgroundDim = 0.35f

/**
 * Caps the surface tint applied over the frosted-glass backdrop. Keeping this below
 * 1 keeps the blur visible even when [LocalUiSurfaceAlpha] is at its default 1f
 * (no custom background, or background at 100% opacity).
 */
internal const val AbkBlurTintAlpha = 0.85f

/** Creates a [LayerBackdrop] capturing content drawn beneath blurred bars. */
@Composable
fun rememberBlurBackdrop(
    enableBlur: Boolean,
    surfaceColor: Color,
    backgroundPainter: Painter? = null,
    backgroundDim: Float = AbkBlurBackgroundDim,
): LayerBackdrop? {
    if (!enableBlur || !isBlurCapableDevice()) return null
    return rememberLayerBackdrop {
        if (backgroundPainter != null) {
            drawCroppedPainter(backgroundPainter)
            // Dim the wallpaper so it blends into the surface tone behind the bar.
            drawRect(surfaceColor.copy(alpha = backgroundDim))
        } else {
            // Opaque fallback; a dim overlay on it would be a no-op.
            drawRect(surfaceColor)
        }
        drawContent()
    }
}

private fun ContentDrawScope.drawCroppedPainter(painter: Painter) {
    val targetSize = drawContext.size
    val sourceSize = painter.intrinsicSize
    if (!sourceSize.isSpecified || sourceSize.width <= 0f || sourceSize.height <= 0f) {
        with(painter) { draw(size = targetSize) }
        return
    }

    val scale = max(
        targetSize.width / sourceSize.width,
        targetSize.height / sourceSize.height,
    )
    val scaledSize = Size(sourceSize.width * scale, sourceSize.height * scale)
    val left = (targetSize.width - scaledSize.width) / 2f
    val top = (targetSize.height - scaledSize.height) / 2f
    clipRect {
        translate(left = left, top = top) {
            with(painter) { draw(size = scaledSize) }
        }
    }
}

/** Marks content as the blur source for the active backdrop. */
@Composable
fun Modifier.blurSource(): Modifier {
    // layerBackdrop needs AGSL runtime shaders (API 33+). Gating on the real
    // capability instead of a lower SDK constant keeps the fallback accurate.
    if (!isBlurCapableDevice()) return this
    return LocalBlurState.current?.let { backdrop ->
        this.then(Modifier.layerBackdrop(backdrop))
    } ?: this
}

/** Applies a frosted-glass effect using the active backdrop. */
@Composable
fun Modifier.blurEffect(blendColor: Color = Color.Unspecified): Modifier {
    // textureBlur needs AGSL runtime shaders (API 33+).
    if (!isBlurCapableDevice()) return this
    return LocalBlurState.current?.let { backdrop ->
        val effective = if (blendColor == Color.Unspecified) {
            MaterialTheme.colorScheme.surfaceContainer.copy(
                // Decoupled from LocalUiSurfaceAlpha so the frosted glass stays
                // visible at the default opaque surface alpha (see AbkBlurTintAlpha).
                alpha = (LocalUiSurfaceAlpha.current * AbkBlurTintAlpha).coerceIn(0f, 1f)
            )
        } else {
            blendColor
        }
        this.then(
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = AbkBlurRadius,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = effective),
                    ),
                ),
            )
        )
    } ?: this
}
