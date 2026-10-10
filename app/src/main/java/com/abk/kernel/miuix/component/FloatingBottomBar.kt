// Frosted-glass floating bottom bar rendered with Kyant0 Backdrop.
// Layout and drag animation adapted from Kyant0/AndroidLiquidGlass catalog LiquidBottomTabs
// — https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0).

package com.abk.kernel.miuix.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.abk.kernel.miuix.animation.DampedDragAnimation
import com.abk.kernel.miuix.animation.InteractiveHighlight
import com.abk.kernel.miuix.theme.isMiuixDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.sign

/** Scale applied to a tab's content while the bar is pressed. */
val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }

/**
 * Gaussian blur radius for the frosted bar surface. Kept close to the shared
 * `AbkBlurRadius` (25f) used by the top bars so both frosted surfaces read alike.
 */
private val FrostedBlurRadius = 24.dp

/** Hairline specular edge. Hoisted so recomposition doesn't allocate a new provider. */
private val PlainHighlightProvider: () -> Highlight? = { Highlight.Plain }

@Composable
fun RowScope.FloatingBottomBarItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalFloatingBottomBarTabScale.current
    Column(
        modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val scale = scale()
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/**
 * Floating pill-shaped bottom bar with a frosted-glass surface.
 *
 * When blur is on, three layers stack up (matching the upstream Kyant0 catalog structure):
 * 1. the frosted surface holding the tab content, exporting its own glass output as a backdrop;
 * 2. an invisible accent-tinted copy of the tabs, exported as a second backdrop;
 * 3. the selection indicator — a translucent glass pill sampling both, so the frosted surface
 *    shows through it and the tab underneath appears in the accent colour.
 *
 * @param selectedIndex Currently selected tab index, read lazily so selection changes don't
 * recompose the whole bar.
 * @param onSelected Invoked after the indicator finishes animating to a new index.
 * @param backdrop The Kyant0 [Backdrop] captured from the content behind the bar.
 * @param tabsCount Number of tabs; drives indicator width and the drag value range.
 * @param isBlurEnabled Whether to draw the frosted surface at all. When false the bar falls back
 * to an opaque container with a flat accent indicator.
 * @param isLiquidGlassEnabled Whether to apply the saturation boost, specular edge and the
 * indicator's refraction on top of the blur.
 */
@Composable
fun FloatingBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    isBlurEnabled: Boolean = true,
    isLiquidGlassEnabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val isInDark = isMiuixDarkTheme()
    val pillShape = remember { CircleShape }
    val accentColor = MiuixTheme.colorScheme.primary
    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
    // A heavier tint than the refractive design used: the strong blur behind it would
    // otherwise leave white icons hard to read.
    val containerColor = if (isBlurEnabled) {
        surfaceContainer.copy(alpha = if (isInDark) 0.62f else 0.55f)
    } else {
        surfaceContainer
    }

    // Captures the accent-tinted copy of the tabs so the glass indicator can pull the selected
    // tab's icon and label out of it in the accent colour.
    val tabsBackdrop = rememberLayerBackdrop()
    // The frosted surface exports its own output here. Sampling that instead of re-blurring
    // the wallpaper is what keeps the indicator translucent without a glass-on-glass loop.
    val surfaceBackdrop = rememberLayerBackdrop()
    val indicatorBackdrop = rememberCombinedBackdrop(surfaceBackdrop, tabsBackdrop)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex()) }

    class DampedDragAnimationHolder {
        var instance: DampedDragAnimation? = null
    }

    val holder = remember { DampedDragAnimationHolder() }

    val dampedDragAnimation = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex().toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                val anim = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false

                val currentValue = anim.value
                val indicatorX = currentValue * tabWidthPx
                val padding = with(density) { 4.dp.toPx() }
                val globalTouchX = if (isLtr) {
                    padding + indicatorX + offset.x
                } else {
                    totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                }
                globalTouchX in 0f..totalWidthPx
            },
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                currentIndex = targetIndex
                animateToValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            }
        ).also { holder.instance = it }
    }

    LaunchedEffect(selectedIndex) {
        snapshotFlow { selectedIndex() }.collectLatest { currentIndex = it }
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
            dampedDragAnimation.animateToValue(index.toFloat())
            onSelected(index)
        }
    }

    val interactiveHighlight = remember(animationScope, tabWidthPx) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { size, _ ->
                Offset(
                    if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                    else size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset,
                    size.height / 2f
                )
            }
        )
    }

    val tabScale = remember(dampedDragAnimation) {
        { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) }
    }

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                    tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                }
                .graphicsLayer { translationX = panelOffset }
                .dropShadow(
                    shape = pillShape,
                    shadow = Shadow(
                        radius = 10.dp,
                        color = Color.Black,
                        alpha = if (isInDark) 0.2f else 0.1f,
                    ),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .then(
                    if (isBlurEnabled) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            // Effect order is fixed by the library: color filter -> blur -> lens.
                            // No lens on the surface: the design calls for a plain gaussian frost,
                            // not refracted edges.
                            effects = {
                                if (isLiquidGlassEnabled) {
                                    colorControls(
                                        brightness = if (isInDark) 0f else 0.1f,
                                        saturation = 1.5f,
                                    )
                                }
                                // Decal makes the library reserve padding so the pill's rim
                                // samples real content instead of smearing edge pixels.
                                blur(
                                    FrostedBlurRadius.toPx(),
                                    edgeTreatment = TileMode.Decal,
                                )
                            },
                            highlight = if (isLiquidGlassEnabled) PlainHighlightProvider else null,
                            shadow = null,
                            layerBlock = {
                                val width = size.width.coerceAtLeast(1f)
                                val s = lerp(1f, 1f + 16.dp.toPx() / width, dampedDragAnimation.pressProgress)
                                scaleX = s
                                scaleY = s
                            },
                            // Publishes the finished frost (without the tab content) for the
                            // indicator to sample. Using exportedBackdrop rather than a
                            // layerBackdrop modifier avoids the glass-on-glass render loop that
                            // crashes the RenderThread.
                            exportedBackdrop = surfaceBackdrop,
                            onDrawSurface = {
                                drawRect(containerColor)
                                drawRect(Color.White.copy(alpha = 0.03f))
                            },
                        )
                    } else {
                        Modifier
                            .dropShadow(
                                shape = pillShape,
                                shadow = Shadow(
                                    radius = 6.dp,
                                    color = Color.Black,
                                    alpha = if (isInDark) 0.3f else 0.15f,
                                ),
                            )
                            .background(containerColor, pillShape)
                    }
                )
                .then(if (isBlurEnabled) interactiveHighlight.modifier else Modifier)
                .height(64.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Remembered so the static CompositionLocal value stays stable; the lambda reads
            // pressProgress lazily inside the item's graphicsLayer instead.
            CompositionLocalProvider(LocalFloatingBottomBarTabScale provides tabScale) {
                content()
            }
        }

        if (isBlurEnabled) {
            // Invisible accent-tinted duplicate of the tabs, captured as a backdrop. The
            // indicator samples it so whichever tab it covers shows up in the accent colour,
            // continuously through a drag.
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .height(56.dp)
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalFloatingBottomBarTabScale provides tabScale) {
                    content()
                }
            }
        }

        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        val progressOffset = dampedDragAnimation.value * tabWidthPx
                        translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                    }
                    .then(interactiveHighlight.gestureModifier)
                    .then(dampedDragAnimation.modifier)
                    .then(
                        if (isBlurEnabled) {
                            // Translucent glass pill: it refracts the frosted surface and the
                            // accent-tinted tabs beneath it rather than painting a flat colour.
                            Modifier.drawBackdrop(
                                backdrop = indicatorBackdrop,
                                shape = { pillShape },
                                effects = {
                                    if (isLiquidGlassEnabled) {
                                        val progress = dampedDragAnimation.pressProgress
                                        lens(
                                            10.dp.toPx() * progress,
                                            14.dp.toPx() * progress,
                                            depthEffect = true,
                                            chromaticAberration = true,
                                        )
                                    }
                                },
                                highlight = {
                                    if (isLiquidGlassEnabled) {
                                        Highlight.Plain.copy(alpha = dampedDragAnimation.pressProgress)
                                    } else {
                                        null
                                    }
                                },
                                shadow = null,
                                innerShadow = {
                                    val progress = dampedDragAnimation.pressProgress
                                    InnerShadow(radius = 8.dp * progress, alpha = progress)
                                },
                                layerBlock = {
                                    scaleX = dampedDragAnimation.scaleX
                                    scaleY = dampedDragAnimation.scaleY
                                    val velocity = dampedDragAnimation.velocity / 10f
                                    scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                    scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                                },
                                onDrawSurface = {
                                    // Only a neutral scrim. The accent tone comes from the
                                    // tinted-tabs backdrop showing through the glass, so the
                                    // pill stays translucent instead of being a flat fill.
                                    val progress = dampedDragAnimation.pressProgress
                                    drawRect(
                                        color = if (isInDark) {
                                            Color.White.copy(alpha = 0.1f)
                                        } else {
                                            Color.Black.copy(alpha = 0.1f)
                                        },
                                        alpha = 1f - progress,
                                    )
                                    drawRect(Color.Black.copy(alpha = 0.03f * progress))
                                },
                            )
                        } else {
                            Modifier.background(accentColor.copy(alpha = 0.15f), pillShape)
                        }
                    )
                    .height(56.dp)
                    .width(tabWidthDp)
            )
        }
    }
}
