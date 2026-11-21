package io.github.xingguangcuican6666.abk.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot

public val AbkDefaultSeed: Color = Color(0xFF496A78)

public enum class AbkColorSource { System, Seed }
public enum class AbkTone { Neutral, Accent, Success, Warning, Danger, Info }

@Immutable
public data class AbkStatusColors(
    val color: Color,
    val onColor: Color,
    val container: Color,
    val onContainer: Color,
)

@Immutable
public data class AbkColors(
    val material: ColorScheme,
    val background: Color,
    val raised: Color,
    val sunken: Color,
    val input: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val line: Color,
    val lineStrong: Color,
    val accent: AbkStatusColors,
    val success: AbkStatusColors,
    val warning: AbkStatusColors,
    val danger: AbkStatusColors,
    val info: AbkStatusColors,
    val scrim: Color,
) {
    public fun status(tone: AbkTone): AbkStatusColors = when (tone) {
        AbkTone.Neutral -> AbkStatusColors(inkMuted, raised, sunken, inkSoft)
        AbkTone.Accent -> accent
        AbkTone.Success -> success
        AbkTone.Warning -> warning
        AbkTone.Danger -> danger
        AbkTone.Info -> info
    }
}

private fun tonalStatus(seed: Color, dark: Boolean): AbkStatusColors {
    val palette = TonalPalette.fromInt(seed.toArgb())
    return AbkStatusColors(
        Color(palette.tone(if (dark) 80 else 40)),
        Color(palette.tone(if (dark) 20 else 100)),
        Color(palette.tone(if (dark) 30 else 90)),
        Color(palette.tone(if (dark) 90 else 10)),
    )
}

/** Deterministic counterpart of demo-palette.js: Tonal Spot, 2021, phone, contrast 0. */
public fun abkSeedColors(seed: Color = AbkDefaultSeed, dark: Boolean = false): AbkColors {
    val s = SchemeTonalSpot(
        Hct.fromInt(seed.toArgb()), dark, 0.0,
        ColorSpec.SpecVersion.SPEC_2021, DynamicScheme.Platform.PHONE,
    )
    val material = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = Color(s.primary), onPrimary = Color(s.onPrimary),
        primaryContainer = Color(s.primaryContainer), onPrimaryContainer = Color(s.onPrimaryContainer),
        inversePrimary = Color(s.inversePrimary),
        secondary = Color(s.secondary), onSecondary = Color(s.onSecondary),
        secondaryContainer = Color(s.secondaryContainer), onSecondaryContainer = Color(s.onSecondaryContainer),
        tertiary = Color(s.tertiary), onTertiary = Color(s.onTertiary),
        tertiaryContainer = Color(s.tertiaryContainer), onTertiaryContainer = Color(s.onTertiaryContainer),
        background = Color(s.surface), onBackground = Color(s.onSurface),
        surface = Color(s.surface), onSurface = Color(s.onSurface),
        surfaceVariant = Color(s.surfaceVariant), onSurfaceVariant = Color(s.onSurfaceVariant),
        surfaceTint = Color(s.primary), inverseSurface = Color(s.inverseSurface),
        inverseOnSurface = Color(s.inverseOnSurface),
        error = Color(s.error), onError = Color(s.onError),
        errorContainer = Color(s.errorContainer), onErrorContainer = Color(s.onErrorContainer),
        outline = Color(s.outline), outlineVariant = Color(s.outlineVariant),
        scrim = Color(s.scrim), surfaceDim = Color(s.surfaceDim), surfaceBright = Color(s.surfaceBright),
        surfaceContainerLowest = Color(s.surfaceContainerLowest),
        surfaceContainerLow = Color(s.surfaceContainerLow), surfaceContainer = Color(s.surfaceContainer),
        surfaceContainerHigh = Color(s.surfaceContainerHigh),
        surfaceContainerHighest = Color(s.surfaceContainerHighest),
    )
    return abkColorsFromMaterial(material, dark).copy(
        inkMuted = Color(s.neutralVariantPalette.tone(if (dark) 78 else 36)),
        inkFaint = Color(s.neutralVariantPalette.tone(if (dark) 75 else 40)),
    )
}

public fun abkColorsFromMaterial(material: ColorScheme, dark: Boolean): AbkColors = AbkColors(
    material = material,
    background = material.surface,
    raised = if (dark) material.surfaceContainer else material.surfaceContainerLowest,
    sunken = if (dark) material.surfaceContainerLowest else material.surfaceContainer,
    input = if (dark) material.surfaceContainerHigh else material.surfaceContainerLow,
    ink = material.onSurface,
    inkSoft = material.onSurfaceVariant,
    // System palettes are platform-owned; use accessible foreground roles instead of outline for text.
    inkMuted = material.onSurfaceVariant,
    inkFaint = material.onSurfaceVariant,
    line = material.outlineVariant,
    lineStrong = material.outline,
    accent = AbkStatusColors(material.primary, material.onPrimary, material.primaryContainer, material.onPrimaryContainer),
    success = tonalStatus(Color(0xFF3F7A4E), dark),
    warning = tonalStatus(Color(0xFFA86B12), dark),
    danger = AbkStatusColors(material.error, material.onError, material.errorContainer, material.onErrorContainer),
    info = AbkStatusColors(material.secondary, material.onSecondary, material.secondaryContainer, material.onSecondaryContainer),
    scrim = material.scrim.copy(alpha = if (dark) 0x94 / 255f else 0x6B / 255f),
)
