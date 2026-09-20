package com.abk.kernel.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralised design tokens for the ABK UI, modelled on the Material 3 Expressive
 * language used by Android 17's system apps: one continuous surface, generously
 * rounded floating containers, and a single spacing rhythm.
 *
 * Before this file the only shared constant was `AbkScreenHorizontalPadding` and every
 * screen invented its own corner radii (5/7/8/10/12/14/18dp…), inner paddings, and
 * vertical rhythm. Route all new/rewritten UI through these tokens so the app reads as
 * one design instead of a dozen hand-rolled ones.
 */
object AbkSpacing {
    /** 4dp — hairline gaps, chip internal spacing. */
    val xs: Dp = 4.dp
    /** 8dp — tight stacks, related controls. */
    val sm: Dp = 8.dp
    /** 12dp — default gap between list rows / cards in a group. */
    val md: Dp = 12.dp
    /** 16dp — card inner padding, section body spacing. */
    val lg: Dp = 16.dp
    /** 24dp — screen horizontal margin, gap between major sections. */
    val xl: Dp = 24.dp
    /** 32dp — hero spacing, large vertical breaks. */
    val xxl: Dp = 32.dp
}

/**
 * Corner radii. Android 17 leans large — cards and bars carry noticeably rounder
 * corners than stock M3. Kept as [RoundedCornerShape] so they can be dropped straight
 * onto `Modifier.clip`/`Card(shape = …)`.
 */
object AbkRadius {
    /** 12dp — chips, small inline controls, dense rows. */
    val small = RoundedCornerShape(12.dp)
    /** 20dp — list items, secondary cards. */
    val medium = RoundedCornerShape(20.dp)
    /** 28dp — primary section cards, hero cards. */
    val large = RoundedCornerShape(28.dp)
    /** 36dp — full-bleed feature surfaces, bottom sheets. */
    val xlarge = RoundedCornerShape(36.dp)

    val smallDp: Dp = 12.dp
    val mediumDp: Dp = 20.dp
    val largeDp: Dp = 28.dp
    val xlargeDp: Dp = 36.dp
}

/**
 * Shared inset rhythm for scrollable page bodies, so every screen reserves the same
 * space under the collapsing top bar and above the floating bottom navigation instead
 * of the current 80/96/32/24dp free-for-all.
 */
object AbkInsets {
    /** Gap between the top-bar bottom edge and the first content item. */
    val contentTopGap: Dp = 16.dp
    /** Extra bottom padding so the last item clears the floating bottom bar. */
    val contentBottomGap: Dp = 96.dp
}

/**
 * Whether the current screen is rendered over a user wallpaper. Screens use this to
 * decide between the opaque one-surface look and the translucent frosted look without
 * each re-deriving it from [LocalAppBackgroundEnabled].
 */
val LocalAbkOnWallpaper = staticCompositionLocalOf { false }
