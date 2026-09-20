package com.abk.kernel.ui.blur

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import com.abk.kernel.ui.theme.LocalUiSurfaceAlpha
import com.abk.kernel.ui.theme.uiSurfaceColor
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Viewport-aligned pre-blurred custom background copy. */
data class BlurredCardBackground(
    val image: ImageBitmap,
    val viewportSize: IntSize,
)

val LocalBlurredCardBackground = compositionLocalOf<BlurredCardBackground?> { null }
val LocalBlurBackgroundAnchor = compositionLocalOf<LayoutCoordinates?> { null }

/** Observable viewport size; [LocalBlurBackgroundAnchor]'s `size` is not observable. */
val LocalBlurBackgroundSize = compositionLocalOf { IntSize.Zero }

/** Synchronous enable signal, so surfaces stay translucent before the bitmap loads. */
val LocalBlurredCardBackgroundEnabled = compositionLocalOf { false }

internal const val AbkCardBlurRadius = 45f
private const val AbkCardBlurDownsample = 4

/** Debounce before re-running the full decode + StackBlur pass on viewport changes. */
private const val AbkCardBlurLoadDebounceMs = 200L

/** Module-scope cache so recompositions and transient resizes keep the previous backdrop. */
private var cachedBlurredCardBackground by mutableStateOf<BlurredCardBackground?>(null)
private var cachedBlurredCardUri by mutableStateOf<String?>(null)

/** Bump when the blur parameters change so old cache files are not reused. */
private const val AbkBlurCacheVersion = 1
/** Upper bound on on-disk cache entries; older ones are pruned on save. */
private const val AbkBlurCacheMaxFiles = 4

/** Collision-resistant cache key: SHA-256 of the URI, not a 32-bit hashCode(). */
private fun blurCacheKey(uri: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun blurCacheFile(context: android.content.Context, uri: String): File {
    val key = "v${AbkBlurCacheVersion}_r${AbkCardBlurRadius.toInt()}_d${AbkCardBlurDownsample}_${blurCacheKey(uri)}"
    return File(context.filesDir, "abk_blurred_custom_background_$key.jpg")
}

/** Deletes stale temp files and the oldest cache entries beyond [AbkBlurCacheMaxFiles]. */
private fun pruneBlurCache(context: android.content.Context) {
    runCatching {
        val prefix = "abk_blurred_custom_background_"
        val files = context.filesDir.listFiles { file -> file.name.startsWith(prefix) }
            ?: return@runCatching
        files.filter { it.name.endsWith(".tmp") }.forEach { it.delete() }
        files.filter { it.name.endsWith(".jpg") }
            .sortedByDescending { it.lastModified() }
            .drop(AbkBlurCacheMaxFiles)
            .forEach { it.delete() }
    }
}

/**
 * Decodes a small, downsampled copy of the custom background directly from [uri]
 * (local file or content stream). Used when the on-disk blur cache misses but the
 * shared wallpaper painter's bitmap has not landed yet, so the card backdrop does not
 * wait on the full-size wallpaper decode after a cold start.
 */
private fun decodeBlurSource(
    context: android.content.Context,
    uri: String,
    viewportW: Int,
    viewportH: Int,
): Bitmap? = runCatching {
    val parsed = Uri.parse(uri)
    val targetW = (viewportW / AbkCardBlurDownsample.toFloat()).roundToInt().coerceAtLeast(1)
    val targetH = (viewportH / AbkCardBlurDownsample.toFloat()).roundToInt().coerceAtLeast(1)
    val file = if (parsed.scheme == "file") parsed.path?.let { File(it) } else null

    // First pass: decode bounds only to pick a power-of-two sample size.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    if (file != null) {
        BitmapFactory.decodeFile(file.absolutePath, bounds)
    } else {
        context.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetW && bounds.outHeight / (sample * 2) >= targetH) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    if (file != null) {
        BitmapFactory.decodeFile(file.absolutePath, opts)
    } else {
        context.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }
}.getOrNull()

/** Loads a previously cached pre-blurred wallpaper that matches the viewport, if any. */
private fun loadBlurCache(
    context: android.content.Context,
    uri: String,
    viewportW: Int,
    viewportH: Int,
): BlurredCardBackground? {
    val file = blurCacheFile(context, uri)
    if (!file.exists()) return null
    return runCatching {
        val decoded = BitmapFactory.decodeFile(file.absolutePath)
        if (decoded == null) {
            file.delete()
            return@runCatching null
        }
        val expectedW = (viewportW / AbkCardBlurDownsample.toFloat()).roundToInt().coerceAtLeast(1)
        val expectedH = (viewportH / AbkCardBlurDownsample.toFloat()).roundToInt().coerceAtLeast(1)
        if (decoded.width != expectedW || decoded.height != expectedH) {
            decoded.recycle()
            return@runCatching null
        }
        BlurredCardBackground(
            image = decoded.asImageBitmap(),
            viewportSize = IntSize(viewportW, viewportH),
        )
    }.getOrNull()
}

/** Persists the pre-blurred wallpaper so the next launch / matching viewport reuses it. */
private fun saveBlurCache(
    context: android.content.Context,
    uri: String,
    background: BlurredCardBackground,
) {
    runCatching {
        val file = blurCacheFile(context, uri)
        file.parentFile?.mkdirs()
        val bitmap = background.image.asAndroidBitmap()
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { it ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }
}

@Composable
fun rememberBlurredCardBackground(
    uri: String?,
    enabled: Boolean,
): BlurredCardBackground? {
    val viewportSize = LocalBlurBackgroundSize.current
    val widthPx = viewportSize.width
    val heightPx = viewportSize.height
    if (!enabled || uri.isNullOrBlank() || widthPx <= 0 || heightPx <= 0) {
        return null
    }
    val context = LocalContext.current
    // Reuse the bitmap the enclosing AppBackgroundHost already decoded for the shared
    // wallpaper painter instead of issuing a second Coil decode. This keeps the wallpaper
    // to a single decode and stops the blur pass from competing with the wallpaper decode
    // on cold start (the black-flash window on low-end devices). The painter's `state` is a
    // Compose snapshot state, so reading it here re-runs the effect once the decode lands.
    val sharedPainter = LocalBlurredBackgroundPainter.current as? AsyncImagePainter
    val sourceBitmap = (sharedPainter?.state as? AsyncImagePainter.State.Success)
        ?.result
        ?.drawable
        ?.let { it as? BitmapDrawable }
        ?.bitmap
    // Re-blur only when the viewport grows past the cached blur; shrink (IME) is covered.
    LaunchedEffect(uri, enabled, widthPx, heightPx, sourceBitmap) {
        val previousUri = cachedBlurredCardUri
        if (previousUri != null && previousUri != uri) {
            cachedBlurredCardBackground = null
        }
        cachedBlurredCardUri = uri
        // Debounce only viewport-change re-runs. The first load for a URI — and the
        // re-run once the wallpaper decode lands after a cold start — must not wait,
        // so the frosted backdrop appears as soon as possible.
        if (cachedBlurredCardBackground != null) {
            delay(AbkCardBlurLoadDebounceMs)
        }
        if (widthPx <= 0 || heightPx <= 0) return@LaunchedEffect
        val targetWidth = widthPx
        val targetHeight = heightPx
        val cached = cachedBlurredCardBackground
        if (cached != null && cached.viewportSize.width >= targetWidth && cached.viewportSize.height >= targetHeight) {
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.Default) {
            loadBlurCache(context, uri, targetWidth, targetHeight)
                ?: sourceBitmap?.let { source ->
                    try {
                        blurSourceToBackground(source, targetWidth, targetHeight)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
                ?: decodeBlurSource(context, uri, targetWidth, targetHeight)
                    ?.let { source ->
                        try {
                            blurSourceToBackground(source, targetWidth, targetHeight)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                ?: if (sharedPainter == null) {
                    // Defensive fallback: no shared painter to reuse, decode independently.
                    try {
                        val result = context.imageLoader.execute(
                            ImageRequest.Builder(context)
                                .data(uri)
                                .size(
                                    (targetWidth / AbkCardBlurDownsample).coerceAtLeast(1),
                                    (targetHeight / AbkCardBlurDownsample).coerceAtLeast(1),
                                )
                                .allowHardware(false)
                                .build()
                        )
                        (result.drawable as? BitmapDrawable)?.bitmap
                            ?.let { blurSourceToBackground(it, targetWidth, targetHeight) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    // Shared painter exists but its bitmap has not landed yet; the effect
                    // re-runs when the painter's state changes.
                    null
                }
        }
        if (loaded != null) {
            cachedBlurredCardBackground = loaded
            withContext(Dispatchers.IO) {
                saveBlurCache(context, uri, loaded)
                pruneBlurCache(context)
            }
        }
    }
    return cachedBlurredCardBackground?.takeIf { cachedBlurredCardUri == uri }
}

@Composable
/**
 * Surface tint used by cards/tiles. While the render-custom-background-into-blur feature
 * is enabled the tint is translucent (capped by [AbkBlurTintAlpha]) regardless of whether
 * the pre-blurred bitmap has loaded yet, so cards never flash from opaque to translucent
 * when the frosted backdrop arrives. When the feature is off it follows the regular
 * [uiSurfaceColor] opacity rule.
 */
fun blurredCardSurfaceColor(color: Color): Color {
    if (!LocalBlurredCardBackgroundEnabled.current) return uiSurfaceColor(color)
    val alpha = (LocalUiSurfaceAlpha.current * AbkBlurTintAlpha).coerceIn(0f, 1f)
    return if (alpha >= 0.995f) color else color.copy(alpha = color.alpha * alpha)
}

@Composable
/** Draws shared blurred custom background underneath this surface. */
fun Modifier.blurredCardBackground(
    shape: Shape,
    enabled: Boolean = true,
): Modifier = composed {
    if (!enabled) return@composed this
    val bitmap = LocalBlurredCardBackground.current ?: return@composed this
    val backgroundAnchor = LocalBlurBackgroundAnchor.current
    val anchorSize = LocalBlurBackgroundSize.current
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
            if (
                bitmap.viewportSize.width >= anchorSize.width &&
                bitmap.viewportSize.height >= anchorSize.height
            ) {
                val boundsInBackground = coordinates?.boundsInBackgroundNow(backgroundAnchor)
                if (boundsInBackground != null) {
                    drawBitmapIntersection(bitmap, boundsInBackground)
                }
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
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    // Single coordinate-space hop (no screen round-trip) per corner.
    val points = listOf(
        targetCoordinates.localPositionOf(this, Offset.Zero),
        targetCoordinates.localPositionOf(this, Offset(width, 0f)),
        targetCoordinates.localPositionOf(this, Offset(0f, height)),
        targetCoordinates.localPositionOf(this, Offset(width, height)),
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

/** Downsamples + blurs [source] into a viewport-aligned [BlurredCardBackground]. */
private fun blurSourceToBackground(source: Bitmap, viewportW: Int, viewportH: Int): BlurredCardBackground? =
    blurCoverBitmap(source, AbkCardBlurRadius, viewportW, viewportH)?.let { blurred ->
        BlurredCardBackground(
            image = blurred.asImageBitmap(),
            viewportSize = IntSize(viewportW, viewportH),
        )
    }

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
