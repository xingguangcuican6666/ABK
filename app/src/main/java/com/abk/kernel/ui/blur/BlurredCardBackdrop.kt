package com.abk.kernel.ui.blur

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil.imageLoader
import coil.request.ImageRequest
import com.abk.kernel.ui.theme.uiSurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Viewport-aligned pre-blurred copy of the custom background shared by cards and tiles
 * while "将自定义背景渲染到模糊" is enabled.
 *
 * Cards sample the rectangle directly beneath them in the shared background
 * coordinate space. The blur is computed once, while layout callbacks update the
 * sampled rectangle when cards move.
 */
data class BlurredCardBackground(
    val image: ImageBitmap,
    val viewportSize: IntSize,
)

val LocalBlurredCardBackground = compositionLocalOf<BlurredCardBackground?> { null }
val LocalBlurBackgroundAnchor = compositionLocalOf<LayoutCoordinates?> { null }

internal const val AbkCardBlurRadius = 45f
private const val AbkCardBlurDownsample = 4

@Composable
fun rememberBlurredCardBackground(
    uri: String?,
    enabled: Boolean,
): BlurredCardBackground? {
    val viewportSize = LocalBlurBackgroundAnchor.current?.size
    val widthPx = viewportSize?.width ?: 0
    val heightPx = viewportSize?.height ?: 0
    if (!enabled || uri.isNullOrBlank() || !isBlurCapableDevice() || widthPx <= 0 || heightPx <= 0) {
        return null
    }
    val context = LocalContext.current
    var bitmap by remember(uri, widthPx, heightPx) { mutableStateOf<BlurredCardBackground?>(null) }
    LaunchedEffect(uri, widthPx, heightPx) {
        bitmap = withContext(Dispatchers.Default) {
            runCatching {
                val loader = context.imageLoader
                val result = loader.execute(
                    ImageRequest.Builder(context).data(uri).allowHardware(false).build()
                )
                val source = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    ?: return@withContext null
                blurCoverBitmap(source, AbkCardBlurRadius, widthPx, heightPx)?.let { blurredBitmap ->
                    BlurredCardBackground(
                        image = blurredBitmap.asImageBitmap(),
                        viewportSize = IntSize(widthPx, heightPx),
                    )
                }
            }.getOrNull()
        }
    }
    return bitmap
}

/**
 * Surface tint used by cards/tiles. The live "界面不透明度" value is applied
 * directly, as ReSukiSU applies its card opacity without a separate hard cap.
 */
@Composable
fun blurredCardSurfaceColor(color: Color): Color = uiSurfaceColor(color)

/**
 * Draws the shared full-screen blurred custom background underneath this surface.
 * Every card or tile samples its own current screen-space rectangle, so adjacent
 * and nested surfaces use the same blur strength without inheriting or re-blurring
 * their parent's pixels.
 */
@Composable
fun Modifier.blurredCardBackground(
    shape: Shape,
    enabled: Boolean = true,
): Modifier = composed {
    if (!enabled) return@composed this
    val bitmap = LocalBlurredCardBackground.current ?: return@composed this
    val backgroundAnchor = LocalBlurBackgroundAnchor.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var layoutRevision by remember { mutableIntStateOf(0) }

    this
        .clip(shape)
        .onGloballyPositioned { newCoordinates ->
            coordinates = newCoordinates.takeIf { it.isAttached }
            layoutRevision += 1
        }
        .drawWithContent {
            layoutRevision
            val boundsInBackground = coordinates?.boundsInBackgroundNow(backgroundAnchor)
            if (boundsInBackground != null) {
                drawBitmapIntersection(bitmap, boundsInBackground)
            }
            drawContent()
        }
}

private fun ContentDrawScope.drawBitmapIntersection(
    background: BlurredCardBackground,
    boundsInBackground: Rect,
) {
    val bitmap = background.image
    val viewportWidth = background.viewportSize.width.toFloat()
    val viewportHeight = background.viewportSize.height.toFloat()
    if (
        bitmap.width <= 0 || bitmap.height <= 0 ||
        viewportWidth <= 0f || viewportHeight <= 0f ||
        size.width <= 0f || size.height <= 0f ||
        boundsInBackground.width <= 0f || boundsInBackground.height <= 0f
    ) return

    val sourceLeft = maxOf(0f, boundsInBackground.left)
    val sourceTop = maxOf(0f, boundsInBackground.top)
    val sourceRight = minOf(viewportWidth, boundsInBackground.right)
    val sourceBottom = minOf(viewportHeight, boundsInBackground.bottom)
    if (sourceRight <= sourceLeft || sourceBottom <= sourceTop) return

    val bitmapScaleX = bitmap.width / viewportWidth
    val bitmapScaleY = bitmap.height / viewportHeight
    val sourceLeftPx = floor(sourceLeft * bitmapScaleX).toInt().coerceIn(0, bitmap.width - 1)
    val sourceTopPx = floor(sourceTop * bitmapScaleY).toInt().coerceIn(0, bitmap.height - 1)
    val sourceRightPx = ceil(sourceRight * bitmapScaleX).toInt().coerceIn(sourceLeftPx + 1, bitmap.width)
    val sourceBottomPx = ceil(sourceBottom * bitmapScaleY).toInt().coerceIn(sourceTopPx + 1, bitmap.height)
    val destinationLeft = ((sourceLeft - boundsInBackground.left) / boundsInBackground.width * size.width)
        .roundToInt()
    val destinationTop = ((sourceTop - boundsInBackground.top) / boundsInBackground.height * size.height)
        .roundToInt()
    val destinationRight = ((sourceRight - boundsInBackground.left) / boundsInBackground.width * size.width)
        .roundToInt()
    val destinationBottom = ((sourceBottom - boundsInBackground.top) / boundsInBackground.height * size.height)
        .roundToInt()

    drawImage(
        image = bitmap,
        srcOffset = IntOffset(sourceLeftPx, sourceTopPx),
        srcSize = IntSize(sourceRightPx - sourceLeftPx, sourceBottomPx - sourceTopPx),
        dstOffset = IntOffset(destinationLeft, destinationTop),
        dstSize = IntSize(
            (destinationRight - destinationLeft).coerceAtLeast(1),
            (destinationBottom - destinationTop).coerceAtLeast(1),
        ),
    )
}

private fun LayoutCoordinates.boundsInBackgroundNow(
    backgroundCoordinates: LayoutCoordinates?,
): Rect? {
    if (!isAttached || size.width <= 0 || size.height <= 0) return null
    return backgroundCoordinates
        ?.takeIf { it.isAttached && it.size.width > 0 && it.size.height > 0 }
        ?.let { boundsInCoordinatesNow(it) }
        ?: localBoundsInWindowNow()
}

private fun LayoutCoordinates.boundsInCoordinatesNow(
    targetCoordinates: LayoutCoordinates,
): Rect? {
    fun localToTarget(point: Offset): Offset {
        val screenPoint = localToScreen(point)
        if (!screenPoint.x.isFinite() || !screenPoint.y.isFinite()) return Offset.Unspecified
        return targetCoordinates.screenToLocal(screenPoint)
    }

    val width = size.width.toFloat()
    val height = size.height.toFloat()
    val points = listOf(
        localToTarget(Offset.Zero),
        localToTarget(Offset(width, 0f)),
        localToTarget(Offset(0f, height)),
        localToTarget(Offset(width, height)),
    )
    if (points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
    return Rect(
        left = points.minOf { it.x },
        top = points.minOf { it.y },
        right = points.maxOf { it.x },
        bottom = points.maxOf { it.y },
    )
}

private fun LayoutCoordinates.localBoundsInWindowNow(): Rect? {
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    val points = listOf(
        localToWindow(Offset.Zero),
        localToWindow(Offset(width, 0f)),
        localToWindow(Offset(0f, height)),
        localToWindow(Offset(width, height)),
    )
    if (points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
    return Rect(
        left = points.minOf { it.x },
        top = points.minOf { it.y },
        right = points.maxOf { it.x },
        bottom = points.maxOf { it.y },
    )
}

/**
 * Cover-crops [source] to the viewport and applies the same software blur fallback
 * used by ReSukiSU for bitmap-backed canvases.
 */
private fun blurCoverBitmap(source: Bitmap, radiusPx: Float, viewportW: Int, viewportH: Int): Bitmap? {
    if (source.isRecycled || viewportW <= 0 || viewportH <= 0) return null
    val sampleWidth = (viewportW / AbkCardBlurDownsample.toFloat()).roundToInt().coerceAtLeast(1)
    val sampleHeight = (viewportH / AbkCardBlurDownsample.toFloat()).roundToInt().coerceAtLeast(1)
    val work = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(work)
    val scale = max(sampleWidth / source.width.toFloat(), sampleHeight / source.height.toFloat())
    val matrix = Matrix().apply {
        setScale(scale, scale)
        postTranslate((sampleWidth - source.width * scale) / 2f, (sampleHeight - source.height * scale) / 2f)
    }
    canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    val blurred = work.softwareFastBlur(
        (radiusPx / AbkCardBlurDownsample).roundToInt().coerceIn(1, 25)
    )
    if (blurred !== work) work.recycle()
    return blurred
}

/**
 * StackBlur (Gaussian approximation) ported from ReSukiSU / Mario Klingemann's
 * classic StackBlur. Works in pure software, no API-level dependencies.
 */
private fun Bitmap.softwareFastBlur(radius: Int): Bitmap {
    if (radius < 1) return this

    val w = width
    val h = height
    val pix = IntArray(w * h)
    getPixels(pix, 0, w, 0, 0, w, h)

    val wm = w - 1
    val hm = h - 1
    val wh = w * h
    val div = radius + radius + 1

    val r = IntArray(wh)
    val g = IntArray(wh)
    val b = IntArray(wh)
    var rsum: Int
    var gsum: Int
    var bsum: Int
    var p: Int
    var yp: Int
    var yi: Int
    val vmin = IntArray(w.coerceAtLeast(h))

    var divsum = (div + 1) shr 1
    divsum *= divsum
    val dv = IntArray(256 * divsum)
    for (i in 0 until 256 * divsum) {
        dv[i] = i / divsum
    }

    var yw = 0
    yi = 0

    val stack = Array(div) { IntArray(3) }
    var stackpointer: Int
    var stackstart: Int
    var sir: IntArray
    var rbs: Int
    val r1 = radius + 1
    var routsum: Int
    var goutsum: Int
    var boutsum: Int
    var rinsum: Int
    var ginsum: Int
    var binsum: Int

    for (y in 0 until h) {
        bsum = 0
        gsum = 0
        rsum = 0
        boutsum = 0
        goutsum = 0
        routsum = 0
        binsum = 0
        ginsum = 0
        rinsum = 0
        for (i in -radius..radius) {
            p = pix[yi + wm.coerceAtMost(i.coerceAtLeast(0))]
            sir = stack[i + radius]
            sir[0] = (p and 0xff0000) shr 16
            sir[1] = (p and 0x00ff00) shr 8
            sir[2] = p and 0x0000ff
            rbs = r1 - abs(i)
            rsum += sir[0] * rbs
            gsum += sir[1] * rbs
            bsum += sir[2] * rbs
            if (i > 0) {
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
            } else {
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
            }
        }
        stackpointer = radius

        for (x in 0 until w) {
            r[yi] = dv[rsum]
            g[yi] = dv[gsum]
            b[yi] = dv[bsum]

            rsum -= routsum
            gsum -= goutsum
            bsum -= boutsum

            stackstart = stackpointer - radius + div
            sir = stack[stackstart % div]

            routsum -= sir[0]
            goutsum -= sir[1]
            boutsum -= sir[2]

            if (y == 0) vmin[x] = (x + radius + 1).coerceAtMost(wm)
            p = pix[yw + vmin[x]]

            sir[0] = (p and 0xff0000) shr 16
            sir[1] = (p and 0x00ff00) shr 8
            sir[2] = p and 0x0000ff

            rinsum += sir[0]
            ginsum += sir[1]
            binsum += sir[2]

            rsum += rinsum
            gsum += ginsum
            bsum += binsum

            stackpointer = (stackpointer + 1) % div
            sir = stack[stackpointer % div]

            routsum += sir[0]
            goutsum += sir[1]
            boutsum += sir[2]

            rinsum -= sir[0]
            ginsum -= sir[1]
            binsum -= sir[2]

            yi++
        }
        yw += w
    }

    for (x in 0 until w) {
        bsum = 0
        gsum = 0
        rsum = 0
        boutsum = 0
        goutsum = 0
        routsum = 0
        binsum = 0
        ginsum = 0
        rinsum = 0
        yp = -radius * w
        for (i in -radius..radius) {
            yi = yp.coerceAtLeast(0) + x
            sir = stack[i + radius]
            sir[0] = r[yi]
            sir[1] = g[yi]
            sir[2] = b[yi]
            rbs = r1 - abs(i)
            rsum += r[yi] * rbs
            gsum += g[yi] * rbs
            bsum += b[yi] * rbs
            if (i > 0) {
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
            } else {
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
            }
            if (i < hm) yp += w
        }
        yi = x
        stackpointer = radius
        for (y in 0 until h) {
            pix[yi] = (-0x1000000 and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
            rsum -= routsum
            gsum -= goutsum
            bsum -= boutsum
            stackstart = stackpointer - radius + div
            sir = stack[stackstart % div]
            routsum -= sir[0]
            goutsum -= sir[1]
            boutsum -= sir[2]
            if (x == 0) vmin[y] = (y + r1).coerceAtMost(hm) * w
            p = x + vmin[y]
            sir[0] = r[p]
            sir[1] = g[p]
            sir[2] = b[p]
            rinsum += sir[0]
            ginsum += sir[1]
            binsum += sir[2]
            rsum += rinsum
            gsum += ginsum
            bsum += binsum
            stackpointer = (stackpointer + 1) % div
            sir = stack[stackpointer]
            routsum += sir[0]
            goutsum += sir[1]
            boutsum += sir[2]
            rinsum -= sir[0]
            ginsum -= sir[1]
            binsum -= sir[2]
            yi += w
        }
    }

    val outputBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    outputBitmap.setPixels(pix, 0, w, 0, 0, w, h)
    return outputBitmap
}
