package com.abk.kernel.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abk.kernel.R
import com.abk.kernel.data.model.CustomFontSelection
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAppearanceUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun text(id: Int) = context.getString(id)

    private fun action(id: Int) = compose.onNode(hasClickAction() and hasText(text(id)))

    private fun choice(id: Int) = compose.onNode(isSelectable() and hasText(text(id)))

    @Composable
    private fun NarrowContent(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, 2f),
            LocalLayoutDirection provides LayoutDirection.Rtl,
        ) {
            AbkTheme(reduceMotion = true) { Column(Modifier.width(320.dp)) { content() } }
        }
    }

    @Composable
    private fun Appearance(
        themeMode: String = "system",
        dynamicColorAvailable: Boolean = true,
        dynamicColorEnabled: Boolean = true,
        customThemeColorArgb: Int? = null,
        customAccentColorArgb: Int? = null,
        customFont: CustomFontSelection? = null,
        backgroundUri: String? = null,
        backgroundImageEnabled: Boolean = false,
        blurEnabled: Boolean = false,
        blurBackgroundExpEnabled: Boolean = false,
        predictiveBackEnabled: Boolean = false,
        onThemeModeChange: (String) -> Unit = {},
        onDynamicColorEnabledChange: (Boolean, Int?, Int?) -> Unit = { _, _, _ -> },
        onCustomThemeColorsChange: (Int, Int) -> Unit = { _, _ -> },
        onImportFont: () -> Unit = {},
        onUseSystemFont: () -> Unit = {},
        onChooseBackground: () -> Unit = {},
        onBackgroundImageChange: (String?) -> Unit = {},
        onBackgroundImageEnabledChange: (Boolean) -> Unit = {},
        onUiSurfaceAlphaChange: (Float) -> Unit = {},
        onUiSurfaceAlphaPreviewChange: (Float) -> Unit = {},
        onBlurEnabledChange: (Boolean) -> Unit = {},
        onBlurBackgroundExpEnabledChange: (Boolean) -> Unit = {},
        onPredictiveBackEnabledChange: (Boolean) -> Unit = {},
    ) {
        SettingsAppearanceContent(
            topBarHeight = 0.dp,
            themeMode = themeMode,
            dynamicColorAvailable = dynamicColorAvailable,
            dynamicColorEnabled = dynamicColorEnabled,
            customThemeColorArgb = customThemeColorArgb,
            customAccentColorArgb = customAccentColorArgb,
            customFont = customFont,
            backgroundUri = backgroundUri,
            backgroundImageEnabled = backgroundImageEnabled,
            uiSurfaceAlpha = 0.5f,
            blurEnabled = blurEnabled,
            blurBackgroundExpEnabled = blurBackgroundExpEnabled,
            predictiveBackEnabled = predictiveBackEnabled,
            onThemeModeChange = onThemeModeChange,
            onDynamicColorEnabledChange = onDynamicColorEnabledChange,
            onCustomThemeColorsChange = onCustomThemeColorsChange,
            onImportFont = onImportFont,
            onUseSystemFont = onUseSystemFont,
            onChooseBackground = onChooseBackground,
            onBackgroundImageChange = onBackgroundImageChange,
            onBackgroundImageEnabledChange = onBackgroundImageEnabledChange,
            onUiSurfaceAlphaChange = onUiSurfaceAlphaChange,
            onUiSurfaceAlphaPreviewChange = onUiSurfaceAlphaPreviewChange,
            onBlurEnabledChange = onBlurEnabledChange,
            onBlurBackgroundExpEnabledChange = onBlurBackgroundExpEnabledChange,
            onPredictiveBackEnabledChange = onPredictiveBackEnabledChange,
        )
    }

    @Test
    fun fontControlsReflectHostSelectionAndOnlyDispatchExplicitActions() {
        var selected by mutableStateOf<CustomFontSelection?>(null)
        val events = mutableListOf<String>()
        compose.setContent {
            NarrowContent {
                Appearance(
                    customFont = selected,
                    onImportFont = { events += "import" },
                    onUseSystemFont = {
                        events += "system"
                        selected = null
                    },
                )
            }
        }
        compose.onNodeWithText(text(R.string.settings_font_system)).assertExists()
        compose.runOnIdle { assertEquals(emptyList<String>(), events) }
        action(R.string.settings_font_import).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("import"), events)
            selected = CustomFontSelection("font-id", "Reader OTF")
        }
        compose.onNodeWithText("Reader OTF").assertExists()
        action(R.string.settings_font_replace).performScrollTo().performClick()
        action(R.string.settings_font_use_system).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.settings_font_system)).assertExists()
        compose.runOnIdle { assertEquals(listOf("import", "import", "system"), events) }
    }

    @Test
    fun modeSelectionUsesHostValueAndOnlyExplicitActionsDispatch() {
        var mode by mutableStateOf("system")
        val changes = mutableListOf<String>()
        compose.setContent {
            NarrowContent { Appearance(themeMode = mode, onThemeModeChange = { changes += it }) }
        }
        choice(R.string.settings_theme_system).assertIsSelected()
        compose.runOnIdle { assertEquals(emptyList<String>(), changes) }
        choice(R.string.settings_theme_dark).performScrollTo().performClick()
        choice(R.string.settings_theme_system).assertIsSelected()
        compose.runOnIdle {
            assertEquals(listOf("dark"), changes)
            mode = "light"
        }
        choice(R.string.settings_theme_light).assertIsSelected()
        compose.runOnIdle { assertEquals(listOf("dark"), changes) }
    }

    @Test
    fun dynamicColorSwitchCapturesCurrentRolesOnlyWhenDisabling() {
        var enabled by mutableStateOf(true)
        var primary = 0
        var secondary = 0
        val changes = mutableListOf<Triple<Boolean, Int?, Int?>>()
        compose.setContent {
            NarrowContent {
                primary = MaterialTheme.colorScheme.primary.toArgb()
                secondary = MaterialTheme.colorScheme.secondary.toArgb()
                Appearance(
                    dynamicColorEnabled = enabled,
                    onDynamicColorEnabledChange = { selected, theme, accent ->
                        changes += Triple(selected, theme, accent)
                        enabled = selected
                    },
                )
            }
        }
        compose.onNodeWithText(text(R.string.settings_custom_colors)).assertDoesNotExist()
        action(R.string.settings_monet).performScrollTo().assertIsOn().performClick()
        compose.onNodeWithText(text(R.string.settings_custom_colors)).assertExists()
        compose.runOnIdle { assertEquals(listOf(Triple(false, primary, secondary)), changes) }
        action(R.string.settings_monet).performScrollTo().assertIsOff().performClick()
        compose.onNodeWithText(text(R.string.settings_custom_colors)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(
                listOf(Triple(false, primary, secondary), Triple(true, null, null)),
                changes,
            )
        }
    }

    @Test
    fun unavailableDynamicColorRemainsOffAndDoesNotDispatch() {
        var changes = 0
        compose.setContent {
            NarrowContent {
                Appearance(
                    dynamicColorAvailable = false,
                    dynamicColorEnabled = true,
                    onDynamicColorEnabledChange = { _, _, _ -> changes++ },
                )
            }
        }
        action(R.string.settings_monet)
            .performScrollTo()
            .assertIsOff()
            .assertIsNotEnabled()
            .performTouchInput { click() }
        compose.onNodeWithText(text(R.string.settings_monet_unavailable_desc)).assertExists()
        compose.onNodeWithText(text(R.string.settings_custom_colors)).assertExists()
        compose.runOnIdle { assertEquals(0, changes) }
    }

    @Test
    fun presetChangesPreserveCompanionColorAndCurrentColorIsNotAnAction() {
        var primary by mutableStateOf(0xFF102030.toInt())
        var secondary by mutableStateOf(0xFF405060.toInt())
        val changes = mutableListOf<Pair<Int, Int>>()
        compose.setContent {
            NarrowContent {
                Appearance(
                    dynamicColorEnabled = false,
                    customThemeColorArgb = primary,
                    customAccentColorArgb = secondary,
                    onCustomThemeColorsChange = { theme, accent ->
                        changes += theme to accent
                        primary = theme
                        secondary = accent
                    },
                )
            }
        }
        val current =
            compose.onAllNodes(isSelectable() and hasText(text(R.string.settings_current_color)))
        current.assertCountEquals(2)
        current[0].assertIsSelected().assertIsNotEnabled()
        current[1].assertIsSelected().assertIsNotEnabled()
        compose
            .onAllNodes(isSelectable() and hasText(text(R.string.settings_color_blue)))[0]
            .performScrollTo()
            .performClick()
        compose
            .onAllNodes(isSelectable() and hasText(text(R.string.settings_color_purple)))[1]
            .performScrollTo()
            .performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(
                    0xFF42A5F5.toInt() to 0xFF405060.toInt(),
                    0xFF42A5F5.toInt() to 0xFF9575CD.toInt(),
                ),
                changes,
            )
        }
    }

    @Test
    fun presetSelectionIgnoresAlphaWithoutSubmittingDuringHostUpdates() {
        var selected by mutableStateOf(0x118BC34A)
        val changes = mutableListOf<Int>()
        compose.setContent {
            NarrowContent {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsThemeColorPicker("Fixture palette", "Fixture description", selected) {
                        changes += it
                    }
                }
            }
        }
        choice(R.string.settings_color_green).assertIsSelected()
        compose.onNodeWithText(text(R.string.settings_current_color)).assertDoesNotExist()
        compose.runOnIdle { selected = 0xFF010203.toInt() }
        choice(R.string.settings_current_color).assertIsSelected().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(emptyList<Int>(), changes) }
    }

    @Test
    fun missingBackgroundDisablesDependentControlsButKeepsExplicitPicker() {
        val events = mutableListOf<String>()
        compose.setContent {
            NarrowContent {
                Appearance(
                    backgroundImageEnabled = true,
                    onChooseBackground = { events += "choose" },
                    onBackgroundImageEnabledChange = { events += "enable" },
                    onUiSurfaceAlphaPreviewChange = { events += "preview" },
                    onUiSurfaceAlphaChange = { events += "commit" },
                )
            }
        }
        action(R.string.settings_custom_background)
            .performScrollTo()
            .assertIsNotEnabled()
            .assertIsOff()
        compose
            .onNodeWithContentDescription(text(R.string.settings_ui_opacity))
            .performScrollTo()
            .assertIsNotEnabled()
            .performTouchInput { swipeLeft() }
        action(R.string.settings_remove_background).assertDoesNotExist()
        action(R.string.settings_blur).assertDoesNotExist()
        action(R.string.settings_choose_background)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        compose.runOnIdle { assertEquals(listOf("choose"), events) }
    }

    @Test
    fun backgroundBlurAndNavigationGatesFollowHostWithoutAutomaticSideEffects() {
        var enabled by mutableStateOf(false)
        var blur by mutableStateOf(false)
        var backgroundBlur by mutableStateOf(false)
        var predictive by mutableStateOf(false)
        val events = mutableListOf<String>()
        compose.setContent {
            NarrowContent {
                Appearance(
                    backgroundUri = "content://fixture/image",
                    backgroundImageEnabled = enabled,
                    blurEnabled = blur,
                    blurBackgroundExpEnabled = backgroundBlur,
                    predictiveBackEnabled = predictive,
                    onChooseBackground = { events += "choose" },
                    onBackgroundImageChange = { events += "background:$it" },
                    onBackgroundImageEnabledChange = {
                        enabled = it
                        events += "enable:$it"
                    },
                    onBlurEnabledChange = {
                        blur = it
                        events += "blur:$it"
                    },
                    onBlurBackgroundExpEnabledChange = {
                        backgroundBlur = it
                        events += "blur-background:$it"
                    },
                    onPredictiveBackEnabledChange = {
                        predictive = it
                        events += "predictive:$it"
                    },
                )
            }
        }
        action(R.string.settings_blur).assertDoesNotExist()
        action(R.string.settings_change_background).performScrollTo().performClick()
        action(R.string.settings_custom_background).performScrollTo().performClick().assertIsOn()
        action(R.string.settings_blur_background).assertDoesNotExist()
        action(R.string.settings_blur).performScrollTo().performClick().assertIsOn()
        action(R.string.settings_blur_background).performScrollTo().performClick().assertIsOn()
        action(R.string.settings_predictive_back).performScrollTo().performClick().assertIsOn()
        action(R.string.settings_remove_background).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(
                    "choose",
                    "enable:true",
                    "blur:true",
                    "blur-background:true",
                    "predictive:true",
                    "background:null",
                ),
                events,
            )
            enabled = false
        }
        action(R.string.settings_blur).assertDoesNotExist()
        compose
            .onNodeWithContentDescription(text(R.string.settings_ui_opacity))
            .assertIsNotEnabled()
        compose.runOnIdle { assertEquals(6, events.size) }
    }

    @Test
    fun opacityClampsHostValueAndCommitsAfterPreview() {
        var alpha by mutableStateOf(2f)
        val events = mutableListOf<Pair<String, Float>>()
        compose.setContent {
            NarrowContent {
                SettingsBackgroundAlphaControl(
                    alpha,
                    enabled = true,
                    onAlphaChange = { events += "preview" to it },
                    onAlphaChangeFinished = { events += "commit" to it },
                )
            }
        }
        val slider = compose.onNodeWithContentDescription(text(R.string.settings_ui_opacity))
        slider.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(1f, 0f..1f),
            )
        )
        compose.runOnIdle {
            assertEquals(emptyList<Pair<String, Float>>(), events)
            alpha = 0.25f
        }
        slider.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(0.25f, 0f..1f),
            )
        )
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
        compose.runOnIdle { assertEquals(listOf("preview" to 0.75f, "commit" to 0.75f), events) }
        slider.performTouchInput { swipeLeft() }
        compose.runOnIdle {
            assertTrue(events.size > 2)
            assertEquals("commit", events.last().first)
            assertEquals(events[events.lastIndex - 1].second, events.last().second)
            assertTrue(events.all { it.second in 0f..1f })
        }
    }

    @Test
    fun hostDisposalSyncsPreviewWithoutPersistingOrLaunchingPicker() {
        var visible by mutableStateOf(true)
        var syncs = 0
        var operations = 0
        compose.setContent {
            NarrowContent {
                if (visible) {
                    ThemeSettingsScreen(
                        topBarHeight = 0.dp,
                        themeMode = "system",
                        dynamicColorEnabled = true,
                        customThemeColorArgb = null,
                        customAccentColorArgb = null,
                        customFont = null,
                        backgroundUri = null,
                        backgroundImageEnabled = false,
                        uiSurfaceAlpha = 0.5f,
                        blurEnabled = false,
                        blurBackgroundExpEnabled = false,
                        predictiveBackEnabled = false,
                        onThemeModeChange = { operations++ },
                        onDynamicColorEnabledChange = { _, _, _ -> operations++ },
                        onCustomThemeColorsChange = { _, _ -> operations++ },
                        onImportFont = { _, _ -> operations++ },
                        onUseSystemFont = { operations++ },
                        onBackgroundImageChange = { operations++ },
                        onBackgroundImageEnabledChange = { operations++ },
                        onUiSurfaceAlphaChange = { operations++ },
                        onUiSurfaceAlphaPreviewChange = { operations++ },
                        onBlurEnabledChange = { operations++ },
                        onBlurBackgroundExpEnabledChange = { operations++ },
                        onPredictiveBackEnabledChange = { operations++ },
                        onUiSurfaceAlphaSync = { syncs++ },
                    )
                }
            }
        }
        compose.runOnIdle {
            assertEquals(0, syncs)
            assertEquals(0, operations)
            visible = false
        }
        compose.runOnIdle {
            assertEquals(1, syncs)
            assertEquals(0, operations)
            visible = true
        }
        compose.runOnIdle {
            assertEquals(1, syncs)
            visible = false
        }
        compose.runOnIdle {
            assertEquals(2, syncs)
            assertEquals(0, operations)
        }
    }
}
