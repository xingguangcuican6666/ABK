package com.abk.kernel.ui.blur

import android.os.Build
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
 * Creates a [LayerBackdrop] capturing the content drawn beneath the blurred bars.
 *
 * The draw callback first paints an optional background [Painter] (the custom
 * background image, when enabled), then a dimmed surface base for contrast, then the
 * content that will be captured as the blur source. Returns `null` when blur is
 * disabled or the device cannot run the frosted-glass shader (API < 33), in which
 * case the app falls back to opaque surfaces.
 */
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
        } else {
            drawRect(surfaceColor)
        }
        drawRect(surfaceColor.copy(alpha = backgroundDim))
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

/**
 * Marks content as the blur source for the active backdrop.
 *
 * Attach to the page content box (inside the Scaffold body, not the bar itself) so
 * that content scrolling beneath the bars is captured and blurred.
 */
@Composable
fun Modifier.blurSource(): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return LocalBlurState.current?.let { backdrop ->
        this.then(Modifier.layerBackdrop(backdrop))
    } ?: this
}

/**
 * Applies a frosted-glass effect to a bar using the active backdrop.
 *
 * Attach to a top/bottom bar whose container color is [Color.Transparent] when the
 * backdrop is active, so the blurred content shows through.
 *
 * @param blendColor Optional tint blended over the blurred content. Defaults to the
 * theme's surfaceContainer using the live "界面不透明度" value directly, matching
 * the card tint and ReSukiSU's card-alpha behavior.
 */
@Composable
fun Modifier.blurEffect(blendColor: Color = Color.Unspecified): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return LocalBlurState.current?.let { backdrop ->
        val effective = if (blendColor == Color.Unspecified) {
            MaterialTheme.colorScheme.surfaceContainer.copy(
                alpha = LocalUiSurfaceAlpha.current.coerceIn(0f, 1f)
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
