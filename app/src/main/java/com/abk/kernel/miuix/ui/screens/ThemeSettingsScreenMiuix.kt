package com.abk.kernel.miuix.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.miuix.theme.miuixKeyColorOptions
import com.abk.kernel.miuix.viewmodel.MiuixSettingsViewModel
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun ThemeSettingsScreenMiuix(
    vm: MainViewModel,
    miuixVm: MiuixSettingsViewModel,
    onBack: () -> Unit,
    onUiStyleChange: (String) -> Unit = { vm.setUiStyle(it) },
) {
    val state by vm.uiState.collectAsState()
    val miuixState by miuixVm.state.collectAsState()
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_color_appearance),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .overScrollVertical()
                .scrollEndHaptic()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Section 1: UI 风格
            Card {
                val uiStyleOptions = listOf("material" to "Material 3", "miuix" to "MIUIX")
                val uiStyleIndex = if (state.uiStyle == "miuix") 1 else 0
                OverlayDropdownPreference(
                    title = stringResource(R.string.settings_ui_style),
                    items = uiStyleOptions.map { it.second },
                    selectedIndex = uiStyleIndex,
                    renderInRootScaffold = true,
                    onSelectedIndexChange = { index ->
                        onUiStyleChange(uiStyleOptions[index].first)
                    }
                )
            }

            // Section 2: 外观模式
            Card {
                val themeModeOptions = listOf(
                    "system" to stringResource(R.string.settings_theme_system),
                    "light" to stringResource(R.string.settings_theme_light),
                    "dark" to stringResource(R.string.settings_theme_dark)
                )
                val themeModeIndex = themeModeOptions.indexOfFirst {
                    it.first == state.themeMode
                }.takeIf { it >= 0 } ?: 0
                OverlayDropdownPreference(
                    title = stringResource(R.string.settings_appearance_mode),
                    items = themeModeOptions.map { it.second },
                    selectedIndex = themeModeIndex,
                    renderInRootScaffold = true,
                    onSelectedIndexChange = { index ->
                        vm.setThemeMode(themeModeOptions[index].first)
                    }
                )
            }

            // Section 3: 颜色来源
            SectionTitleMiuix(stringResource(R.string.settings_color_source))
            Card {
                SwitchPreference(
                    title = stringResource(R.string.settings_monet),
                    startAction = {
                        Icon(
                            Icons.Rounded.Wallpaper,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = stringResource(R.string.settings_monet),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    },
                    checked = dynamicColorAvailable && miuixState.miuixDynamicColorEnabled,
                    onCheckedChange = { enabled ->
                        miuixVm.setMiuixDynamicColorEnabled(enabled)
                    }
                )

                AnimatedVisibility(
                    visible = dynamicColorAvailable && miuixState.miuixDynamicColorEnabled
                ) {
                    Column {
                        val colorItems = listOf(stringResource(R.string.settings_key_color_default)) + listOf(
                            stringResource(R.string.color_red),
                            stringResource(R.string.settings_color_pink),
                            stringResource(R.string.settings_color_purple),
                            stringResource(R.string.color_deep_purple),
                            stringResource(R.string.color_indigo),
                            stringResource(R.string.settings_color_blue),
                            stringResource(R.string.settings_color_cyan),
                            stringResource(R.string.color_teal),
                            stringResource(R.string.settings_color_green),
                            stringResource(R.string.color_yellow),
                            stringResource(R.string.color_amber),
                            stringResource(R.string.settings_color_orange),
                            stringResource(R.string.color_brown),
                            stringResource(R.string.color_blue_grey),
                            stringResource(R.string.color_sakura),
                        )
                        val colorValues = listOf(0) + miuixKeyColorOptions
                        val currentArgb = miuixState.miuixThemeColorArgb ?: 0
                        val currentColorIndex = colorValues.indexOf(currentArgb).takeIf { it >= 0 } ?: 0
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_key_color),
                            items = colorItems,
                            selectedIndex = currentColorIndex,
                            renderInRootScaffold = true,
                            onSelectedIndexChange = { index ->
                                miuixVm.setMiuixThemeColor(colorValues[index])
                            }
                        )

                        AnimatedVisibility(visible = currentArgb != 0) {
                            Column {
                                val paletteStyles = listOf(
                                    ThemePaletteStyle.TonalSpot,
                                    ThemePaletteStyle.Neutral,
                                    ThemePaletteStyle.Vibrant,
                                    ThemePaletteStyle.Expressive,
                                    ThemePaletteStyle.Monochrome,
                                )
                                val paletteIndex = paletteStyles.indexOfFirst {
                                    it.name == miuixState.miuixColorStyle
                                }.takeIf { it >= 0 } ?: 0
                                OverlayDropdownPreference(
                                    title = stringResource(R.string.settings_color_style),
                                    items = paletteStyles.map { it.name },
                                    selectedIndex = paletteIndex,
                                    renderInRootScaffold = true,
                                    onSelectedIndexChange = { index ->
                                        miuixVm.setMiuixColorStyle(paletteStyles[index].name)
                                    }
                                )

                                val colorSpecs = listOf(
                                    ThemeColorSpec.Spec2021,
                                    ThemeColorSpec.Spec2025,
                                )
                                val specIndex = colorSpecs.indexOfFirst {
                                    it.name == miuixState.miuixColorSpec
                                }.takeIf { it >= 0 } ?: 0
                                OverlayDropdownPreference(
                                    title = stringResource(R.string.settings_color_spec),
                                    items = colorSpecs.map { it.name },
                                    selectedIndex = specIndex,
                                    renderInRootScaffold = true,
                                    onSelectedIndexChange = { index ->
                                        miuixVm.setMiuixColorSpec(colorSpecs[index].name)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Section 4: 视觉效果
            SectionTitleMiuix(stringResource(R.string.settings_visual_effects))
            Card {
                SwitchPreference(
                    title = stringResource(R.string.settings_blur),
                    summary = stringResource(R.string.settings_blur_summary),
                    startAction = {
                        Icon(
                            Icons.Rounded.BlurOn,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "模糊",
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    },
                    checked = state.miuixBlurEnabled,
                    onCheckedChange = { vm.setMiuixBlurEnabled(it) }
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_floating_bottom_bar),
                    summary = stringResource(R.string.settings_floating_bottom_bar_summary),
                    startAction = {
                        Icon(
                            Icons.Rounded.CallToAction,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "悬浮底栏",
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    },
                    checked = state.miuixFloatingBottomBarEnabled,
                    onCheckedChange = { vm.setMiuixFloatingBottomBarEnabled(it) }
                )
                AnimatedVisibility(visible = state.miuixFloatingBottomBarEnabled) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_liquid_glass),
                        summary = stringResource(R.string.settings_liquid_glass_summary),
                        startAction = {
                            Icon(
                                Icons.Rounded.WaterDrop,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "液态玻璃",
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        },
                        checked = state.miuixLiquidGlassEnabled,
                        onCheckedChange = { vm.setMiuixLiquidGlassEnabled(it) }
                    )
                }
            }

            // Section 5: predictive back switch (MIUIX-only; controls NavDisplay predictive back;
            // MD3 PredictiveChildPageBack is independently toggled from SettingsScreen).
            // No SectionTitleMiuix label — switch lives directly after the "视觉效果" Card.
            Card {
                SwitchPreference(
                    title = stringResource(R.string.settings_predictive_back_gesture),
                    summary = stringResource(R.string.settings_predictive_back_summary),
                    startAction = {
                        Icon(
                            Icons.AutoMirrored.Rounded.MenuOpen,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "预测性返回手势",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    },
                    checked = state.miuixPredictiveBackEnabled,
                    onCheckedChange = { vm.setMiuixPredictiveBackEnabled(it) }
                )
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun SectionTitleMiuix(title: String) {
    Text(
        text = title,
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}
