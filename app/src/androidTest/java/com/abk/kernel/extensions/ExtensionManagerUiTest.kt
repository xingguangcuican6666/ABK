package com.abk.kernel.extensions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abk.kernel.R
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionManagerUiTest {
    @get:Rule val compose = createComposeRule()
    private val context
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun text(id: Int) = context.getString(id)

    private fun action(label: String) = compose.onNode(hasClickAction() and hasText(label))

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

    private fun item(
        id: String = "fixture",
        installed: Boolean = true,
        needsOobe: Boolean = true,
        canOobe: Boolean = true,
        canService: Boolean = false,
        canInstall: Boolean = true,
    ) =
        ExtensionItemState(
            id = id,
            name = "Fixture extension",
            description = "Fixture description",
            summary = "Fixture summary",
            companionLabel = "Fixture companion",
            companionInstalled = installed,
            needsOobe = needsOobe,
            canLaunchOobe = canOobe,
            canLaunchService = canService,
            canInstall = canInstall,
        )

    @Composable
    private fun Host(
        bootstrapMode: Boolean = false,
        focused: ExtensionItemState? = null,
        items: List<ExtensionItemState> = emptyList(),
        onInstall: (ExtensionItemState) -> Unit = {},
        onOpenOobe: (ExtensionItemState) -> Unit = {},
        onOpenService: (ExtensionItemState) -> Unit = {},
        onReset: (ExtensionItemState) -> Unit = {},
    ) {
        ExtensionsContent(
            topBarHeight = 0.dp,
            bootstrapMode = bootstrapMode,
            focused = focused,
            extensions = items,
            onInstall = onInstall,
            onOpenOobe = onOpenOobe,
            onOpenService = onOpenService,
            onReset = onReset,
        )
    }

    @Test
    fun emptyListShowsEmptyStateWithoutDispatchingWork() {
        var operations = 0
        compose.setContent {
            NarrowContent {
                Host(
                    onInstall = { operations++ },
                    onOpenOobe = { operations++ },
                    onOpenService = { operations++ },
                    onReset = { operations++ },
                )
            }
        }
        compose.onNodeWithText(text(R.string.extension_manager_empty_title)).assertExists()
        compose.onNodeWithText(text(R.string.extension_manager_empty_desc)).assertExists()
        compose.runOnIdle { assertEquals(0, operations) }
    }

    @Test
    fun missingCompanionOnlyOffersInstallAndDisablesItWithoutDownloadUrl() {
        val installs = mutableListOf<String>()
        val other = mutableListOf<String>()
        compose.setContent {
            NarrowContent {
                Host(
                    items = listOf(item(installed = false, canOobe = false, canInstall = false)),
                    onInstall = { installs += it.id },
                    onOpenOobe = { other += it.id },
                    onOpenService = { other += it.id },
                )
            }
        }
        compose.onNodeWithText("Fixture extension").assertExists()
        compose.onNodeWithText(text(R.string.extension_status_companion_missing)).assertExists()
        action(text(R.string.extension_install_action)).performScrollTo().assertIsNotEnabled()
        compose
            .onAllNodes(hasClickAction() and hasText(text(R.string.extension_run_oobe_action)))
            .assertCountEquals(0)
        compose
            .onAllNodes(hasClickAction() and hasText(text(R.string.extension_run_service_action)))
            .assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(emptyList<String>(), installs)
            assertEquals(emptyList<String>(), other)
        }
    }

    @Test
    fun serviceCapabilityTakesPriorityOverOobeAndDispatchesExactlyOnce() {
        val calls = mutableListOf<String>()
        compose.setContent {
            NarrowContent {
                Host(
                    items = listOf(item(needsOobe = false, canOobe = true, canService = true)),
                    onOpenOobe = { calls += "oobe" },
                    onOpenService = { calls += "service:${it.id}" },
                    onReset = { calls += "reset:${it.id}" },
                )
            }
        }
        compose.onNodeWithText(text(R.string.extension_status_oobe_complete)).assertExists()
        compose.onNodeWithText(text(R.string.extension_status_companion_ready)).assertExists()
        action(text(R.string.extension_run_service_action))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        compose
            .onAllNodes(hasClickAction() and hasText(text(R.string.extension_run_oobe_action)))
            .assertCountEquals(0)
        compose.runOnIdle { assertEquals(listOf("service:fixture"), calls) }
    }

    @Test
    fun oobeLaunchAndResetKeepSeparateHostCallbacks() {
        val calls = mutableListOf<String>()
        compose.setContent {
            NarrowContent {
                Host(
                    items = listOf(item()),
                    onOpenOobe = { calls += "oobe:${it.id}" },
                    onReset = { calls += "reset:${it.id}" },
                )
            }
        }
        action(text(R.string.extension_run_oobe_action)).performScrollTo().performClick()
        action(text(R.string.extension_reset_action)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("oobe:fixture", "reset:fixture"), calls) }
    }

    @Test
    fun bootstrapGuideAppearsBeforeTheFocusedCardAndDoesNotMutateTheList() {
        val calls = mutableListOf<String>()
        val focused = item(id = "bootstrap", canService = false)
        compose.setContent {
            NarrowContent {
                Host(
                    bootstrapMode = true,
                    focused = focused,
                    items = listOf(item(id = "other"), focused),
                    onOpenOobe = { calls += it.id },
                )
            }
        }
        compose
            .onNodeWithText(text(R.string.extension_bootstrap_oobe_required_title))
            .assertExists()
        compose.onNodeWithText("Fixture extension").assertExists()
        compose.onNodeWithText("Fixture description", substring = true).assertExists()
        compose.onAllNodes(hasText("Fixture extension")).assertCountEquals(2)
        action(text(R.string.extension_run_oobe_action)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("bootstrap"), calls) }
    }

    @Test
    fun missingCompanionDialogUsesHostGatesAndSingleExplicitInstall() {
        var dismissed = 0
        val installs = mutableListOf<String>()
        compose.setContent {
            NarrowContent {
                ExtensionMissingCompanionDialog(
                    extensionName = "Fixture extension",
                    companionLabel = "Fixture companion",
                    installEnabled = false,
                    onDismiss = { dismissed++ },
                    onInstall = { installs += "install" },
                )
            }
        }
        compose
            .onNodeWithText(text(R.string.extension_bootstrap_install_dialog_title))
            .assertExists()
        action(text(R.string.extension_install_action)).assertIsNotEnabled()
        action(context.getString(android.R.string.cancel)).performClick()
        compose.runOnIdle {
            assertEquals(1, dismissed)
            assertEquals(emptyList<String>(), installs)
        }
    }
}
