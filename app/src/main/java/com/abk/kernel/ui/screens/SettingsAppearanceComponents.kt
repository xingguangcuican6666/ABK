package com.abk.kernel.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.abk.kernel.R
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.screens.flash.FlashAction
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkOption
import io.github.xingguangcuican6666.abk.ui.components.AbkSettingSwitch
import io.github.xingguangcuican6666.abk.ui.components.AbkSlider
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
internal fun SettingsAppearanceContent(
    topBarHeight: Dp,
    themeMode: String,
    dynamicColorAvailable: Boolean,
    dynamicColorEnabled: Boolean,
    customThemeColorArgb: Int?,
    customAccentColorArgb: Int?,
    backgroundUri: String?,
    backgroundImageEnabled: Boolean,
    uiSurfaceAlpha: Float,
    blurEnabled: Boolean,
    blurBackgroundExpEnabled: Boolean,
    predictiveBackEnabled: Boolean,
    onThemeModeChange: (String) -> Unit,
    onDynamicColorEnabledChange: (Boolean, Int?, Int?) -> Unit,
    onCustomThemeColorsChange: (Int, Int) -> Unit,
    onChooseBackground: () -> Unit,
    onBackgroundImageChange: (String?) -> Unit,
    onBackgroundImageEnabledChange: (Boolean) -> Unit,
    onUiSurfaceAlphaChange: (Float) -> Unit,
    onUiSurfaceAlphaPreviewChange: (Float) -> Unit,
    onBlurEnabledChange: (Boolean) -> Unit,
    onBlurBackgroundExpEnabledChange: (Boolean) -> Unit,
    onPredictiveBackEnabledChange: (Boolean) -> Unit,
) {
    val effectiveDynamicColorEnabled = dynamicColorAvailable && dynamicColorEnabled
    val colorScheme = MaterialTheme.colorScheme
    val selectedThemeColorArgb = customThemeColorArgb ?: colorScheme.primary.toArgb()
    val selectedAccentColorArgb = customAccentColorArgb ?: colorScheme.secondary.toArgb()
    val backgroundConfigured = backgroundImageEnabled && !backgroundUri.isNullOrBlank()

    Column(
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(topBarHeight + 16.dp))
        SettingsMainCard(stringResource(R.string.settings_appearance_mode)) {
            SettingsPreferenceChoices(
                title = stringResource(R.string.settings_theme),
                options =
                    listOf(
                        AbkOption("system", stringResource(R.string.settings_theme_system)),
                        AbkOption("light", stringResource(R.string.settings_theme_light)),
                        AbkOption("dark", stringResource(R.string.settings_theme_dark)),
                    ),
                selectedId = themeMode,
                onSelect = onThemeModeChange,
            )
        }
        SettingsMainCard(stringResource(R.string.settings_color_source)) {
            AbkSettingSwitch(
                title = stringResource(R.string.settings_monet),
                description =
                    stringResource(
                        if (dynamicColorAvailable) R.string.settings_monet_desc
                        else R.string.settings_monet_unavailable_desc
                    ),
                checked = effectiveDynamicColorEnabled,
                enabled = dynamicColorAvailable,
                onCheckedChange = { enabled ->
                    onDynamicColorEnabledChange(
                        enabled,
                        if (!enabled) colorScheme.primary.toArgb() else null,
                        if (!enabled) colorScheme.secondary.toArgb() else null,
                    )
                },
            )
        }
        if (!effectiveDynamicColorEnabled) {
            SettingsMainCard(stringResource(R.string.settings_custom_colors)) {
                SettingsThemeColorPicker(
                    title = stringResource(R.string.settings_theme_color),
                    subtitle = stringResource(R.string.settings_theme_color_desc),
                    selectedColorArgb = selectedThemeColorArgb,
                    onColorSelected = { color ->
                        onCustomThemeColorsChange(color, selectedAccentColorArgb)
                    },
                )
                AbkDivider()
                SettingsThemeColorPicker(
                    title = stringResource(R.string.settings_accent_color),
                    subtitle = stringResource(R.string.settings_accent_color_desc),
                    selectedColorArgb = selectedAccentColorArgb,
                    onColorSelected = { color ->
                        onCustomThemeColorsChange(selectedThemeColorArgb, color)
                    },
                )
            }
        }
        SettingsMainCard(stringResource(R.string.settings_navigation)) {
            AbkSettingSwitch(
                title = stringResource(R.string.settings_predictive_back),
                description = stringResource(R.string.settings_predictive_back_desc),
                checked = predictiveBackEnabled,
                onCheckedChange = onPredictiveBackEnabledChange,
            )
        }
        SettingsMainCard(stringResource(R.string.settings_background)) {
            AbkSettingSwitch(
                title = stringResource(R.string.settings_custom_background),
                description =
                    stringResource(
                        if (backgroundUri.isNullOrBlank()) R.string.settings_custom_background_desc
                        else R.string.settings_background_selected
                    ),
                checked = backgroundConfigured,
                enabled = !backgroundUri.isNullOrBlank(),
                onCheckedChange = onBackgroundImageEnabledChange,
            )
            Text(
                stringResource(R.string.settings_choose_background_desc),
                color = AbkTheme.colors.inkMuted,
            )
            FlashAction(
                stringResource(
                    if (backgroundUri.isNullOrBlank()) R.string.settings_choose_background
                    else R.string.settings_change_background
                ),
                AbkIcons.Plus,
                onChooseBackground,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!backgroundUri.isNullOrBlank()) {
                Text(
                    stringResource(R.string.settings_remove_background_desc),
                    color = AbkTheme.colors.inkMuted,
                )
                FlashAction(
                    stringResource(R.string.settings_remove_background),
                    AbkIcons.Trash,
                    { onBackgroundImageChange(null) },
                    modifier = Modifier.fillMaxWidth(),
                    style = AbkButtonStyle.Ghost,
                )
            }
            AnimatedVisibility(
                visible = backgroundConfigured,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AbkSettingSwitch(
                        title = stringResource(R.string.settings_blur),
                        description = stringResource(R.string.settings_blur_desc),
                        checked = blurEnabled,
                        onCheckedChange = onBlurEnabledChange,
                    )
                    AnimatedVisibility(
                        visible = blurEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        AbkSettingSwitch(
                            title = stringResource(R.string.settings_blur_background),
                            description = stringResource(R.string.settings_blur_background_desc),
                            checked = blurBackgroundExpEnabled,
                            enabled = backgroundConfigured,
                            onCheckedChange = onBlurBackgroundExpEnabledChange,
                        )
                    }
                }
            }
            SettingsBackgroundAlphaControl(
                alpha = uiSurfaceAlpha,
                enabled = backgroundConfigured,
                onAlphaChange = onUiSurfaceAlphaPreviewChange,
                onAlphaChangeFinished = onUiSurfaceAlphaChange,
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
internal fun SettingsBackgroundAlphaControl(
    alpha: Float,
    enabled: Boolean,
    onAlphaChange: (Float) -> Unit,
    onAlphaChangeFinished: ((Float) -> Unit)? = null,
) {
    // Keep drag state local; persist only when the interaction finishes.
    var previewAlpha by remember(alpha) { mutableFloatStateOf(alpha.coerceIn(0f, 1f)) }
    AbkSlider(
        value = previewAlpha,
        onValueChange = {
            previewAlpha = it
            onAlphaChange(it)
        },
        onValueChangeFinished = { onAlphaChangeFinished?.invoke(previewAlpha) },
        label = stringResource(R.string.settings_ui_opacity),
        valueLabel = "${(previewAlpha * 100).toInt()}%",
        valueRange = 0f..1f,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(R.string.settings_ui_opacity_desc),
        style = MaterialTheme.typography.bodySmall,
        color = AbkTheme.colors.inkMuted,
    )
}

@Composable
internal fun SettingsThemeColorPicker(
    title: String,
    subtitle: String,
    selectedColorArgb: Int,
    onColorSelected: (Int) -> Unit,
) {
    val presets = themeColorPresets()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, color = AbkTheme.colors.inkMuted)
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            if (presets.none { colorsMatch(selectedColorArgb, it.argb) }) {
                ThemeColorSwatch(
                    preset =
                        ThemeColorPreset(
                            stringResource(R.string.settings_current_color),
                            selectedColorArgb,
                        ),
                    selected = true,
                    enabled = false,
                    onClick = {},
                )
            }
            presets.forEach { preset ->
                ThemeColorSwatch(
                    preset = preset,
                    selected = colorsMatch(selectedColorArgb, preset.argb),
                    onClick = { onColorSelected(preset.argb) },
                )
            }
        }
    }
}

@Composable
private fun ThemeColorSwatch(
    preset: ThemeColorPreset,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) AbkTheme.colors.ink else AbkTheme.colors.lineStrong
    Row(
        Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp)
                .background(Color(preset.argb), CircleShape)
                .border(BorderStroke(if (selected) 3.dp else 1.dp, borderColor), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                AbkIcon(
                    AbkIcons.Check,
                    null,
                    tint = readableSwatchContentColor(preset.argb),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(preset.label, Modifier.weight(1f))
    }
}

private data class ThemeColorPreset(val label: String, val argb: Int)

@Composable
private fun themeColorPresets(): List<ThemeColorPreset> =
    listOf(
        ThemeColorPreset(stringResource(R.string.settings_color_green), 0xFF8BC34A.toInt()),
        ThemeColorPreset(stringResource(R.string.settings_color_blue), 0xFF42A5F5.toInt()),
        ThemeColorPreset(stringResource(R.string.settings_color_purple), 0xFF9575CD.toInt()),
        ThemeColorPreset(stringResource(R.string.settings_color_pink), 0xFFEC6A9A.toInt()),
        ThemeColorPreset(stringResource(R.string.settings_color_orange), 0xFFFFA726.toInt()),
        ThemeColorPreset(stringResource(R.string.settings_color_cyan), 0xFF26C6DA.toInt()),
    )

private fun colorsMatch(left: Int, right: Int): Boolean =
    (left or 0xFF000000.toInt()) == (right or 0xFF000000.toInt())

private fun readableSwatchContentColor(argb: Int): Color =
    if (ColorUtils.calculateLuminance(argb) > 0.5) Color(0xFF11140F.toInt())
    else Color(0xFFFFFFFF.toInt())
