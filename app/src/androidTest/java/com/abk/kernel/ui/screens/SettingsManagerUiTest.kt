package com.abk.kernel.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abk.kernel.R
import com.abk.kernel.data.model.ManagerSettingItem
import com.abk.kernel.data.model.ManagerSettingKind
import com.abk.kernel.data.model.ManagerSettingStatus
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsManagerUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Composable
    private fun NarrowRtlContent(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, 2f),
            LocalLayoutDirection provides LayoutDirection.Rtl,
        ) {
            AbkTheme(reduceMotion = true) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) { content() }
            }
        }
    }

    private fun action(label: String) = compose.onNode(hasClickAction() and hasText(label))

    @Test
    fun navigationIdsStaySeparateAndDisabledEntriesDoNotDispatch() {
        var denied by mutableStateOf(true)
        val actions = mutableListOf<String>()
        val ids =
            listOf(
                "app_profile_templates",
                "manager_tools",
                "kernel_capabilities",
                "susfs_control",
                "kpm",
            )
        compose.setContent {
            NarrowRtlContent {
                SettingsManagerCard(
                    title = "Fixture manager",
                    items =
                        ids.map { id ->
                            ManagerSettingItem(
                                id = id,
                                title = "Fixture $id",
                                subtitle = "Fixture capability description",
                                kind = ManagerSettingKind.NAVIGATION,
                                enabled = !denied,
                            )
                        },
                    loading = false,
                    showRefreshLoading = false,
                    error = null,
                    actionId = null,
                    onRetry = { actions += "retry" },
                    onNavigate = { actions += it },
                    onModeChange = { _, _ -> actions += "mode" },
                    onCheckedChange = { _, _ -> actions += "switch" },
                )
            }
        }
        ids.forEach {
            action("Fixture $it").performScrollTo().assertIsNotEnabled().performTouchInput {
                click()
            }
        }
        compose.runOnIdle {
            assertEquals(emptyList<String>(), actions)
            denied = false
        }
        ids.forEach { action("Fixture $it").performScrollTo().performClick() }
        compose.runOnIdle { assertEquals(ids, actions) }
    }

    @Test
    fun switchesKeepHostValuesAndPerItemBusyGateWithoutAutomaticDispatch() {
        var checked by mutableStateOf(false)
        var busy by mutableStateOf<String?>("locked")
        val actions = mutableListOf<Pair<String, Boolean>>()
        compose.setContent {
            NarrowRtlContent {
                SettingsManagerCard(
                    title = "Fixture manager",
                    items =
                        listOf(
                            ManagerSettingItem(
                                id = "locked",
                                title = "Fixture locked",
                                checked = checked,
                            ),
                            ManagerSettingItem(id = "available", title = "Fixture available"),
                            ManagerSettingItem(
                                id = "unsupported",
                                title = "Fixture unsupported",
                                enabled = false,
                                status = ManagerSettingStatus.UNSUPPORTED,
                            ),
                            ManagerSettingItem(
                                id = "managed",
                                title = "Fixture managed",
                                enabled = false,
                                status = ManagerSettingStatus.MANAGED,
                            ),
                        ),
                    loading = false,
                    showRefreshLoading = false,
                    error = null,
                    actionId = busy,
                    onRetry = {},
                    onNavigate = {},
                    onModeChange = { _, _ -> },
                    onCheckedChange = { id, value -> actions += id to value },
                )
            }
        }
        listOf("locked", "unsupported", "managed").forEach {
            action("Fixture $it").performScrollTo().assertIsNotEnabled().performTouchInput {
                click()
            }
        }
        action("Fixture available").performScrollTo().assertIsEnabled().performClick().assertIsOff()
        compose.runOnIdle {
            assertEquals(listOf("available" to true), actions)
            busy = null
            checked = true
        }
        action("Fixture locked").performScrollTo().assertIsOn().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(listOf("available" to true, "locked" to false), actions) }
    }

    @Test
    fun modeTrimsAndFiltersOptionsClampsIndexAndOnlyDispatchesNewSelection() {
        val actions = mutableListOf<Int>()
        compose.setContent {
            NarrowRtlContent {
                SettingsManagerModeItem(
                    item =
                        ManagerSettingItem(
                            title = "Fixture mode",
                            kind = ManagerSettingKind.MODE,
                            options = listOf("  ", " First ", "", " Second "),
                            selectedIndex = 90,
                        ),
                    actionInFlight = false,
                    onSelected = { actions += it },
                )
            }
        }
        val select = compose.onNodeWithContentDescription("Fixture mode")
        select.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Second"))
        select.performClick()
        compose.onNode(hasAnyAncestor(isPopup()) and hasText("Second")).performClick()
        compose.runOnIdle { assertEquals(emptyList<Int>(), actions) }
        select.performClick()
        compose.onNode(hasAnyAncestor(isPopup()) and hasText("First")).performClick()
        compose.runOnIdle { assertEquals(listOf(0), actions) }
        select.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Second"))
    }

    @Test
    fun modeBusyDisabledAndEmptyOptionsCannotOpenOrDispatch() {
        var busy by mutableStateOf(true)
        val actions = mutableListOf<Int>()
        compose.setContent {
            NarrowRtlContent {
                listOf(
                        ManagerSettingItem(title = "Fixture busy", options = listOf("One", "Two")),
                        ManagerSettingItem(
                            title = "Fixture denied",
                            options = listOf("One", "Two"),
                            enabled = false,
                        ),
                        ManagerSettingItem(title = "Fixture empty", options = listOf("", " ")),
                    )
                    .forEach { item ->
                        SettingsManagerModeItem(
                            item = item,
                            actionInFlight = item.title == "Fixture busy" && busy,
                            onSelected = { actions += it },
                        )
                    }
            }
        }
        listOf("Fixture busy", "Fixture denied", "Fixture empty").forEach {
            compose
                .onNodeWithContentDescription(it)
                .performScrollTo()
                .assertIsNotEnabled()
                .performTouchInput { click() }
        }
        compose.onNode(isPopup()).assertDoesNotExist()
        compose
            .onNodeWithContentDescription("Fixture empty")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(R.string.settings_unknown),
                )
            )
        compose.runOnIdle {
            assertEquals(emptyList<Int>(), actions)
            busy = false
        }
        compose.onNodeWithContentDescription("Fixture busy").performScrollTo().performClick()
        compose.onNode(isPopup()).assertExists()
        pressBack()
        compose.runOnIdle { assertEquals(emptyList<Int>(), actions) }
    }

    @Test
    fun modeOptionsAndSelectionTrackHostUpdatesIncludingDuplicateLabels() {
        var item by
            mutableStateOf(ManagerSettingItem(title = "Fixture mode", options = listOf("Old")))
        val actions = mutableListOf<Int>()
        compose.setContent {
            NarrowRtlContent {
                SettingsManagerModeItem(
                    item,
                    actionInFlight = false,
                    onSelected = { actions += it },
                )
            }
        }
        val select = compose.onNodeWithContentDescription("Fixture mode")
        compose.runOnIdle { item = item.copy(options = listOf("Same", "Same"), selectedIndex = 1) }
        select
            .assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Same"))
        select.performClick()
        pressBack()
        compose.runOnIdle {
            assertEquals(emptyList<Int>(), actions)
            item = item.copy(options = listOf(" New ", "Other"), selectedIndex = -5)
        }
        select.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "New"))
        select.performClick()
        compose.onNode(hasAnyAncestor(isPopup()) and hasText("Other")).performClick()
        compose.runOnIdle { assertEquals(listOf(1), actions) }
    }

    @Test
    fun modeCardDispatchesItsOwnIdAndIndexAndReflectsHostBusyState() {
        var selected by mutableStateOf(0)
        var busy by mutableStateOf<String?>(null)
        val actions = mutableListOf<String>()
        compose.setContent {
            NarrowRtlContent {
                SettingsManagerCard(
                    title = "Fixture manager",
                    items =
                        listOf(
                            ManagerSettingItem(
                                id = "su_compat",
                                title = "Fixture mode",
                                kind = ManagerSettingKind.MODE,
                                options = listOf("First", "Second"),
                                selectedIndex = selected,
                            ),
                            ManagerSettingItem(
                                id = "manager_tools",
                                title = "Fixture tools",
                                kind = ManagerSettingKind.NAVIGATION,
                            ),
                        ),
                    loading = false,
                    showRefreshLoading = false,
                    error = null,
                    actionId = busy,
                    onRetry = { actions += "retry" },
                    onNavigate = { actions += "navigate:$it" },
                    onModeChange = { id, index -> actions += "mode:$id:$index" },
                    onCheckedChange = { id, checked -> actions += "switch:$id:$checked" },
                )
            }
        }
        val select = compose.onNodeWithContentDescription("Fixture mode")
        compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
        select.performScrollTo().performClick()
        compose.onNode(hasAnyAncestor(isPopup()) and hasText("Second")).performClick()
        compose.runOnIdle {
            assertEquals(listOf("mode:su_compat:1"), actions)
            selected = 1
            busy = "su_compat"
        }
        select
            .performScrollTo()
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Second"))
            .performTouchInput { click() }
        compose.onNode(isPopup()).assertDoesNotExist()
        action("Fixture tools").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("mode:su_compat:1", "navigate:manager_tools"), actions)
        }
    }

    @Test
    fun refreshHidesEntriesUntilHostCompletesAndErrorsOnlyRetryExplicitly() {
        var refreshing by mutableStateOf(true)
        var error by mutableStateOf<String?>("Fixture load failure")
        val actions = mutableListOf<String>()
        compose.setContent {
            NarrowRtlContent {
                SettingsManagerCard(
                    title = "Fixture manager",
                    items =
                        listOf(
                            ManagerSettingItem(
                                id = "manager_tools",
                                title = "Fixture tools",
                                kind = ManagerSettingKind.NAVIGATION,
                            )
                        ),
                    loading = refreshing,
                    showRefreshLoading = refreshing,
                    error = error,
                    actionId = null,
                    onRetry = { actions += "retry" },
                    onNavigate = { actions += it },
                    onModeChange = { _, _ -> },
                    onCheckedChange = { _, _ -> },
                )
            }
        }
        action("Fixture tools").assertDoesNotExist()
        compose
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Fixture load failure"))
            .assertExists()
        compose.runOnIdle {
            assertEquals(emptyList<String>(), actions)
            refreshing = false
        }
        action("Fixture tools").performScrollTo().performClick()
        action(context.getString(R.string.retry)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("manager_tools", "retry"), actions)
            error = null
        }
        compose.onNodeWithText("Fixture load failure").assertDoesNotExist()
    }

    @Test
    fun initialLoadingDoesNotRetryOrInventSettingsAndRetainsProgressSemantics() {
        var loading by mutableStateOf(true)
        val actions = mutableListOf<String>()
        compose.setContent {
            NarrowRtlContent {
                SettingsManagerCard(
                    title = "Fixture manager",
                    items = emptyList(),
                    loading = loading,
                    showRefreshLoading = false,
                    error = if (loading) null else "Fixture empty error",
                    actionId = null,
                    onRetry = { actions += "retry" },
                    onNavigate = { actions += it },
                    onModeChange = { _, _ -> actions += "mode" },
                    onCheckedChange = { _, _ -> actions += "switch" },
                )
            }
        }
        compose
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertExists()
        action(context.getString(R.string.retry)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(emptyList<String>(), actions)
            loading = false
        }
        action(context.getString(R.string.retry)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("retry"), actions) }
    }
}
