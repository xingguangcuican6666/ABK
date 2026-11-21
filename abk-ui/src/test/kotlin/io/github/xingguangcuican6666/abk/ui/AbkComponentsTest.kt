package io.github.xingguangcuican6666.abk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.xingguangcuican6666.abk.ui.components.AbkAccordion
import io.github.xingguangcuican6666.abk.ui.components.AbkBottomNavigation
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkCheckbox
import io.github.xingguangcuican6666.abk.ui.components.AbkDialog
import io.github.xingguangcuican6666.abk.ui.components.AbkMenu
import io.github.xingguangcuican6666.abk.ui.components.AbkMenuEntry
import io.github.xingguangcuican6666.abk.ui.components.AbkNavigationItem
import io.github.xingguangcuican6666.abk.ui.components.AbkNavigationRail
import io.github.xingguangcuican6666.abk.ui.components.AbkSwitch
import io.github.xingguangcuican6666.abk.ui.components.AbkOption
import io.github.xingguangcuican6666.abk.ui.components.AbkRadioGroup
import io.github.xingguangcuican6666.abk.ui.components.AbkSegmented
import io.github.xingguangcuican6666.abk.ui.components.AbkSelect
import io.github.xingguangcuican6666.abk.ui.components.AbkSnackbarHost
import io.github.xingguangcuican6666.abk.ui.components.AbkSnackbarHostState
import io.github.xingguangcuican6666.abk.ui.components.AbkTextField
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkColorSource
import io.github.xingguangcuican6666.abk.ui.theme.AbkColors
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing
import io.github.xingguangcuican6666.abk.ui.theme.abkSeedColors
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp")
public class AbkComponentsTest {
    @get:Rule public val compose: ComposeContentTestRule = createComposeRule()

    private fun content(body: @Composable () -> Unit) {
        compose.setContent {
            AbkTheme(colorSource = AbkColorSource.Seed, reduceMotion = true, content = body)
        }
    }

    @Test public fun loadingAndDisabledButtonsIgnoreTouchWhileEnabledButtonWorks() {
        var clicks = 0
        content {
            Column {
                AbkButton({ clicks++ }, loading = true) { Text("保存") }
                AbkButton({ clicks++ }, enabled = false) { Text("不可用") }
                AbkButton({ clicks++ }) { Text("可用") }
            }
        }
        compose.onNodeWithText("保存").assertIsNotEnabled().assert(hasStateDescription("加载中"))
            .assertHeightIsAtLeast(48.dp).performTouchInput { click() }
        compose.onNodeWithText("不可用").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, clicks) }
        compose.onNodeWithText("可用").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test public fun textFieldUsesExternalLabelAndRecomposesAfterInput() {
        var value by mutableStateOf("")
        content { AbkTextField(value, { value = it }, "分支", placeholder = "main") }
        compose.onNodeWithText("分支").assertExists()
        val input = compose.onNodeWithContentDescription("分支")
        input.performTextInput("release")
        input.assertTextEquals("release")
        input.performTextInput("/stable")
        input.assertTextEquals("release/stable")
        compose.runOnIdle { assertEquals("release/stable", value) }
    }

    @Test public fun textFieldExposesErrorAndReadOnlyFieldRejectsKeyboardEdits() {
        var edits = 0
        content {
            Column {
                AbkTextField("", {}, "名称", error = "名称不能为空")
                AbkTextField("main", { edits++ }, "只读分支", readOnly = true)
                AbkTextField("locked", { edits++ }, "禁用分支", enabled = false)
            }
        }
        compose.onNodeWithContentDescription("名称")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "名称不能为空"))
        compose.onNodeWithText("名称不能为空").assertExists()
        compose.onNodeWithContentDescription("只读分支").assert(isEditable().not()).requestFocus()
            .performKeyInput { pressKey(Key.A) }.assertTextEquals("main")
        compose.onNodeWithContentDescription("禁用分支").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, edits) }
    }

    @Test public fun textFieldInputModifierTracksRealFocusAndPreservesCodeTypography() {
        var value by mutableStateOf("main")
        var focused = false
        var focusLosses = 0
        content {
            Column {
                AbkTextField(value, { value = it }, "源码 ref",
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontSize = 22.sp),
                    inputModifier = Modifier.onFocusChanged {
                        if (focused && !it.isFocused) focusLosses++
                        focused = it.isFocused
                    })
                AbkTextField("", {}, "下一个字段")
            }
        }
        compose.runOnIdle { assertEquals(0, focusLosses) }
        val input = compose.onNodeWithContentDescription("源码 ref")
        input.requestFocus()
        compose.runOnIdle { assertTrue(focused); assertEquals(0, focusLosses) }
        val layouts = mutableListOf<TextLayoutResult>()
        input.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        compose.runOnIdle {
            assertTrue(layouts.isNotEmpty())
            assertEquals(FontFamily.Monospace, layouts.single().layoutInput.style.fontFamily)
            assertEquals(22.sp, layouts.single().layoutInput.style.fontSize)
        }
        input.performTextInput("-stable")
        compose.onNodeWithContentDescription("下一个字段").requestFocus()
        compose.runOnIdle { assertTrue(!focused); assertEquals(1, focusLosses) }
    }

    @Test public fun checkboxHasOneToggleTargetAndCallsBackOnce() {
        var state by mutableStateOf(ToggleableState.Indeterminate)
        var callbacks = 0
        content { AbkCheckbox(state, { callbacks++; state = ToggleableState.On }, "选择全部") }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertCountEquals(1)
        compose.onNodeWithText("选择全部")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Indeterminate))
            .performClick().assertIsOn()
        compose.runOnIdle { assertEquals(1, callbacks) }
    }

    @Test public fun radioGroupCallsBackOnceAndDisabledOptionDoesNotSelect() {
        var selected by mutableStateOf("one")
        var callbacks = 0
        content {
            AbkRadioGroup(listOf(AbkOption("one", "默认"), AbkOption("two", "另一项"),
                AbkOption("three", "禁用选项", enabled = false)), selected, { selected = it; callbacks++ }, cards = true)
        }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(3)
        compose.onNodeWithText("另一项").performClick().assertIsSelected()
        compose.onNodeWithText("默认").assertIsNotSelected()
        compose.onNodeWithText("禁用选项").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals("two", selected); assertEquals(1, callbacks) }
    }

    @Test public fun segmentedSelectionIsControlledAndHonorsDisabledOption() {
        var selected by mutableStateOf("one")
        content {
            AbkSegmented(listOf(AbkOption("one", "浅色"), AbkOption("two", "深色"),
                AbkOption("three", "跟随系统", enabled = false)), selected, { selected = it })
        }
        compose.onNodeWithText("深色").performClick().assertIsSelected()
        compose.onNodeWithText("浅色").assertIsNotSelected()
        compose.onNodeWithText("跟随系统").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals("two", selected) }
    }

    @Test public fun bottomNavigationKeepsEqualWidthsAndUpdatesSelectedSemantics() {
        var selected by mutableStateOf("one")
        content {
            AbkBottomNavigation(destinations(), selected, { selected = it }, Modifier.width(390.dp),
                windowInsets = WindowInsets(0, 0, 0, 0))
        }
        val tabs = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        tabs.assertCountEquals(3)
        val widths = tabs.fetchSemanticsNodes().map { it.boundsInRoot.width }
        assertTrue("Destination widths must differ by at most one rounding pixel", widths.max() - widths.min() <= 1f)
        tabs[0].assertIsSelected()
        tabs[1].assertHeightIsAtLeast(56.dp).performClick().assertIsSelected()
        tabs[0].assertIsNotSelected()
        compose.runOnIdle { assertEquals("two", selected) }
    }

    @Test public fun narrowRtlNavigationSupportsDoubleFontScaleWithoutOverlappingTargets() {
        content {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                AbkBottomNavigation(destinations(), "one", {}, Modifier.width(320.dp),
                    windowInsets = WindowInsets(0, 0, 0, 0))
            }
        }
        val tabs = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        val bounds = tabs.fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(3, bounds.size)
        assertTrue("RTL reverses destination positions", bounds[0].left > bounds[1].left && bounds[1].left > bounds[2].left)
        bounds.sortedBy { it.left }.zipWithNext().forEach { (a, b) -> assertTrue(a.right <= b.left + 1f) }
        repeat(3) { tabs[it].assertHeightIsAtLeast(56.dp) }
    }

    @Test public fun accordionOnlyShowsContentWhileExpanded() {
        var expanded by mutableStateOf(false)
        var callbacks = 0
        content { AbkAccordion("高级设置", expanded, { expanded = it; callbacks++ }) { Text("设置内容") } }
        compose.onNodeWithText("设置内容").assertDoesNotExist()
        compose.onNodeWithText("高级设置").assert(hasStateDescription("已收起")).performClick()
        compose.onNodeWithText("设置内容").assertExists()
        compose.onNodeWithText("高级设置").assert(hasStateDescription("已展开")).performClick()
        compose.onNodeWithText("设置内容").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, callbacks) }
    }

    @Test public fun selectIgnoresDisabledOptionAndReportsSelectedValue() {
        var selected by mutableStateOf<String?>(null)
        var callbacks = 0
        content {
            AbkSelect(listOf(AbkOption("arm64", "arm64-v8a"), AbkOption("arm32", "armeabi-v7a", enabled = false)),
                selected, { selected = it; callbacks++ }, "架构")
        }
        compose.onNodeWithContentDescription("架构").performClick()
        compose.onNodeWithText("armeabi-v7a").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, callbacks) }
        compose.onNodeWithText("arm64-v8a").performClick()
        compose.onNodeWithContentDescription("架构").assert(hasStateDescription("arm64-v8a"))
        compose.runOnIdle { assertEquals("arm64", selected); assertEquals(1, callbacks) }
    }

    @Test public fun menuDismissesBeforeCallingActionAndIgnoresDisabledEntry() {
        var expanded by mutableStateOf(false)
        val events = mutableListOf<String>()
        content {
            Box {
                AbkButton({ expanded = true }) { Text("打开菜单") }
                AbkMenu(expanded, { events += "dismiss"; expanded = false }, listOf(
                    AbkMenuEntry.Label("记录"), AbkMenuEntry.Action("copy", "复制"), AbkMenuEntry.Divider,
                    AbkMenuEntry.Action("delete", "删除", enabled = false, danger = true),
                ), { events += it })
            }
        }
        compose.onNodeWithText("打开菜单").performClick()
        compose.onNodeWithText("删除").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertTrue(events.isEmpty()) }
        compose.onNodeWithText("复制").performClick()
        compose.onNodeWithText("复制").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("dismiss", "copy"), events) }
    }

    @Test public fun closingDialogCanImmediatelyShowFeedback() {
        var open by mutableStateOf(true)
        var dismissals = 0
        val snackbar = AbkSnackbarHostState()
        content {
            val scope = rememberCoroutineScope()
            AbkSnackbarHost(snackbar)
            if (open) AbkDialog("确认操作", {
                open = false; dismissals++
                scope.launch { snackbar.show("对话框已关闭") }
            }) { Text("对话框内容") }
        }
        compose.onNodeWithContentDescription("关闭对话框").performClick()
        compose.onNodeWithText("对话框内容").assertDoesNotExist()
        compose.onNodeWithText("对话框已关闭").assertExists()
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test public fun hiddenSwitchLabelRetainsOneAccessibleTargetAndCallsBackOnce() {
        var checked by mutableStateOf(false)
        var callbacks = 0
        content {
            AbkSwitch(checked, { checked = it; callbacks++ }, "模块开关", showLabel = false)
        }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertCountEquals(1)
        compose.onNodeWithText("模块开关").assertDoesNotExist()
        compose.onNodeWithContentDescription("模块开关")
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick().assertIsOn()
        compose.runOnIdle { assertEquals(1, callbacks) }
    }

    @Test public fun nonDismissibleDialogHidesCloseAndIgnoresBack() {
        var dismissals = 0
        content {
            AbkDialog("正在写入", { dismissals++ }, dismissible = false) { Text("请勿断开") }
        }
        compose.onNodeWithContentDescription("关闭对话框").assertDoesNotExist()
        compose.onNodeWithText("请勿断开").performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithText("请勿断开").assertExists()
        compose.runOnIdle { assertEquals(0, dismissals) }
    }

    @Test public fun navigationRailKeepsStableAccessibleTargetsAtDoubleFontScale() {
        var selected by mutableStateOf("one")
        content {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                AbkNavigationRail(destinations(), selected, { selected = it }, Modifier.width(92.dp),
                    windowInsets = WindowInsets(0, 0, 0, 0))
            }
        }
        val tabs = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        tabs.assertCountEquals(3)
        val bounds = tabs.fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue(bounds.maxOf { it.width } - bounds.minOf { it.width } <= 1f)
        bounds.zipWithNext().forEach { (a, b) -> assertTrue(a.bottom <= b.top + 1f) }
        tabs[0].assertIsSelected()
        tabs[1].assertHeightIsAtLeast(72.dp).performClick().assertIsSelected()
        tabs[0].assertIsNotSelected()
        compose.runOnIdle { assertEquals("two", selected) }
    }

    @Test public fun hostColorOverrideMatchesMaterialRolesAndUpdates() {
        var dark by mutableStateOf(false)
        var observed: AbkColors? = null
        var materialSurface = 0
        var materialPrimary = 0
        compose.setContent {
            val colors = abkSeedColors(dark = dark)
            AbkTheme(colorsOverride = colors, reduceMotion = true) {
                val current = AbkTheme.colors
                val scheme = MaterialTheme.colorScheme
                SideEffect {
                    observed = current
                    materialSurface = scheme.surface.toArgb()
                    materialPrimary = scheme.primary.toArgb()
                }
                Text("宿主主题")
            }
        }
        fun assertRoles() = compose.runOnIdle {
            val expected = abkSeedColors(dark = dark)
            assertEquals(seedPaletteRoles(expected), seedPaletteRoles(checkNotNull(observed)))
            assertEquals(expected.material.surface.toArgb(), materialSurface)
            assertEquals(expected.accent.color.toArgb(), materialPrimary)
        }
        assertRoles()
        compose.runOnIdle { dark = true }
        assertRoles()
    }

    @Test @Config(sdk = [23]) public fun systemColorSourceFallsBackToSeedOnOldAndroid() {
        var observed: AbkColors? = null
        compose.setContent {
            AbkTheme(darkTheme = true, colorSource = AbkColorSource.System, reduceMotion = true) {
                val colors = AbkTheme.colors
                SideEffect { observed = colors }
                Text("旧版本回退")
            }
        }
        compose.runOnIdle {
            assertEquals(seedPaletteRoles(abkSeedColors(dark = true)), seedPaletteRoles(checkNotNull(observed)))
        }
    }

    @Test public fun spacingAndShapeTokensMatchTheWebScale() {
        assertEquals(4.dp, AbkSpacing.Xs)
        assertEquals(8.dp, AbkSpacing.Sm)
        assertEquals(12.dp, AbkSpacing.Md)
        assertEquals(16.dp, AbkSpacing.Lg)
        assertEquals(20.dp, AbkSpacing.Xl)
        assertEquals(24.dp, AbkSpacing.Xxl)
        assertEquals(32.dp, AbkSpacing.Section)
        assertEquals(40.dp, AbkSpacing.Wide)
        assertEquals(56.dp, AbkSpacing.Hero)
        val size = Size(100f, 100f)
        val density = Density(1f)
        assertEquals(8f, AbkShapes.Small.topStart.toPx(size, density), 0f)
        assertEquals(12f, AbkShapes.Medium.topStart.toPx(size, density), 0f)
        assertEquals(18f, AbkShapes.Large.topStart.toPx(size, density), 0f)
        assertEquals(26f, AbkShapes.ExtraLarge.topStart.toPx(size, density), 0f)
        assertEquals(50f, AbkShapes.Pill.topStart.toPx(size, density), 0f)
    }

    @Test public fun iconRegistryCoversSemanticRolesAndRtlDirections() {
        assertEquals(setOf(
            "home", "search", "edit", "plus", "refresh", "upload", "download", "settings", "menu", "more",
            "check", "close", "back", "forward", "moon", "sun", "pending", "warning", "arrow-right", "build",
            "modules", "copy", "info", "info-mark", "alert-mark", "dot", "cpu", "terminal", "controls", "trash",
        ), AbkIcons.all.keys)
        assertEquals(setOf("back", "forward", "arrow-right"), AbkIcons.all.filterValues { it.autoMirror }.keys)
        AbkIcons.all.forEach { (role, icon) ->
            assertEquals("$role width", 24.dp, icon.defaultWidth)
            assertEquals("$role height", 24.dp, icon.defaultHeight)
            assertTrue("$role geometry is not empty", icon.root.size > 0)
        }
        val infoMark = AbkIcons.InfoMark
        assertEquals("Abk.InfoMark", infoMark.name)
        assertEquals(1, infoMark.root.size)
        val path = infoMark.root[0] as VectorPath
        assertEquals(listOf(PathNode.MoveTo(12f, 9f), PathNode.RelativeVerticalTo(7f),
            PathNode.MoveTo(12f, 4f), PathNode.RelativeHorizontalTo(0.01f)), path.pathData)
    }

    private fun seedPaletteRoles(colors: AbkColors): Map<String, Int> = linkedMapOf(
        "background" to colors.background.toArgb(),
        "raised" to colors.raised.toArgb(),
        "sunken" to colors.sunken.toArgb(),
        "input" to colors.input.toArgb(),
        "ink" to colors.ink.toArgb(),
        "inkSoft" to colors.inkSoft.toArgb(),
        "inkMuted" to colors.inkMuted.toArgb(),
        "inkFaint" to colors.inkFaint.toArgb(),
        "line" to colors.line.toArgb(),
        "lineStrong" to colors.lineStrong.toArgb(),
        "accent" to colors.accent.color.toArgb(),
        "accentOn" to colors.accent.onColor.toArgb(),
        "accentContainer" to colors.accent.container.toArgb(),
        "accentOnContainer" to colors.accent.onContainer.toArgb(),
        "success" to colors.success.color.toArgb(),
        "successOn" to colors.success.onColor.toArgb(),
        "successContainer" to colors.success.container.toArgb(),
        "successOnContainer" to colors.success.onContainer.toArgb(),
        "warning" to colors.warning.color.toArgb(),
        "warningOn" to colors.warning.onColor.toArgb(),
        "warningContainer" to colors.warning.container.toArgb(),
        "warningOnContainer" to colors.warning.onContainer.toArgb(),
        "danger" to colors.danger.color.toArgb(),
        "dangerOn" to colors.danger.onColor.toArgb(),
        "dangerContainer" to colors.danger.container.toArgb(),
        "dangerOnContainer" to colors.danger.onContainer.toArgb(),
        "info" to colors.info.color.toArgb(),
        "infoOn" to colors.info.onColor.toArgb(),
        "infoContainer" to colors.info.container.toArgb(),
        "infoOnContainer" to colors.info.onContainer.toArgb(),
        "scrim" to colors.scrim.toArgb(),
    )

    private fun destinations(): List<AbkNavigationItem> = listOf(
        AbkNavigationItem("one", "构建", AbkIcons.Build),
        AbkNavigationItem("two", "模块", AbkIcons.Modules),
        AbkNavigationItem("three", "设置", AbkIcons.Settings),
    )
}
