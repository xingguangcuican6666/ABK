package com.abk.kernel.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abk.kernel.R
import com.abk.kernel.data.model.KernelTcpCongestionControlState
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsToolsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun text(id: Int) = context.getString(id)

    private fun action(id: Int) = compose.onNode(hasClickAction() and hasText(text(id)))

    private fun algorithm(name: String) = compose.onNode(isSelectable() and hasText(name))

    private fun progress() =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))

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
    private fun Tools(
        loading: Boolean = false,
        refreshing: Boolean = false,
        enforcing: Boolean = true,
        pathCount: Int = 2,
        error: String? = null,
        actionId: String? = null,
        onSelinux: (Boolean) -> Unit = {},
        onBackup: () -> Unit = {},
        onRestore: () -> Unit = {},
    ) {
        SettingsManagerToolsContent(
            topBarHeight = 0.dp,
            loading = loading,
            showRefreshLoading = refreshing,
            selinuxMode = "Enforcing",
            selinuxEnforcing = enforcing,
            umountPathCount = pathCount,
            error = error,
            actionId = actionId,
            onSelinuxChange = onSelinux,
            onBackupAllowlist = onBackup,
            onRestoreAllowlist = onRestore,
        )
    }

    private fun tcp() =
        KernelTcpCongestionControlState(
            currentAlgorithm = "cubic",
            availableAlgorithms = listOf("cubic", "bbr", "reno"),
            allowedAlgorithms = listOf("cubic"),
        )

    @Composable
    private fun Kernel(
        loading: Boolean = false,
        refreshing: Boolean = false,
        snapshot: KernelTcpCongestionControlState? = tcp(),
        error: String? = null,
        actionId: String? = null,
        onRefresh: () -> Unit = {},
        onSelected: (String) -> Unit = {},
    ) {
        SettingsKernelCapabilitiesContent(
            topBarHeight = 0.dp,
            loading = loading,
            showRefreshLoading = refreshing,
            tcp = snapshot,
            error = error,
            actionId = actionId,
            onRefresh = onRefresh,
            onTcpAlgorithmSelected = onSelected,
        )
    }

    @Test
    fun toolsUseControlledSelinuxAndOnlyExplicitPickerCallbacks() {
        var enforcing by mutableStateOf(true)
        val events = mutableListOf<String>()
        compose.setContent {
            NarrowContent {
                Tools(
                    enforcing = enforcing,
                    onSelinux = { events += "selinux:$it" },
                    onBackup = { events += "backup" },
                    onRestore = { events += "restore" },
                )
            }
        }
        compose.runOnIdle { assertEquals(emptyList<String>(), events) }
        action(R.string.settings_selinux_mode)
            .performScrollTo()
            .assertIsOn()
            .performClick()
            .assertIsOn()
        action(R.string.settings_backup_allowlist).performScrollTo().performClick()
        action(R.string.settings_restore_allowlist).performScrollTo().performClick()
        compose
            .onNodeWithText(context.getString(R.string.settings_umount_path_count, 2))
            .assertExists()
        compose.runOnIdle {
            assertEquals(listOf("selinux:false", "backup", "restore"), events)
            enforcing = false
        }
        action(R.string.settings_selinux_mode).assertIsOff()
        compose.runOnIdle { assertEquals(3, events.size) }
    }

    @Test
    fun toolsPreserveIndependentBusyGatesAndShowProgress() {
        var busy by mutableStateOf<String?>("selinux_mode")
        var operations = 0
        compose.setContent {
            NarrowContent {
                Tools(
                    actionId = busy,
                    onSelinux = { operations++ },
                    onBackup = { operations++ },
                    onRestore = { operations++ },
                )
            }
        }
        action(R.string.settings_selinux_mode)
            .performScrollTo()
            .assertIsNotEnabled()
            .performTouchInput { click() }
        progress().assertExists()
        action(R.string.settings_backup_allowlist).assertIsEnabled()
        action(R.string.settings_restore_allowlist).assertIsEnabled()
        compose.runOnIdle { busy = "backup_allowlist" }
        action(R.string.settings_selinux_mode).assertIsEnabled()
        action(R.string.settings_backup_allowlist)
            .performScrollTo()
            .assertIsNotEnabled()
            .performTouchInput { click() }
        action(R.string.settings_restore_allowlist).assertIsEnabled()
        compose.runOnIdle { busy = "restore_allowlist" }
        action(R.string.settings_backup_allowlist).assertIsEnabled()
        action(R.string.settings_restore_allowlist)
            .performScrollTo()
            .assertIsNotEnabled()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, operations) }
    }

    @Test
    fun initialToolsLoadingAndExplicitRefreshHideActionsWithoutDispatch() {
        var refreshing by mutableStateOf(false)
        var loading by mutableStateOf(true)
        var paths by mutableStateOf(0)
        var operations = 0
        compose.setContent {
            NarrowContent {
                Tools(
                    loading = loading,
                    refreshing = refreshing,
                    pathCount = paths,
                    onBackup = { operations++ },
                )
            }
        }
        progress().assertExists()
        action(R.string.settings_backup_allowlist).assertDoesNotExist()
        compose.runOnIdle { loading = false }
        compose.onNodeWithText(text(R.string.settings_umount_no_paths)).assertExists()
        action(R.string.settings_backup_allowlist).assertIsEnabled()
        compose.runOnIdle {
            paths = 2
            refreshing = true
        }
        progress().assertExists()
        action(R.string.settings_selinux_mode).assertDoesNotExist()
        action(R.string.settings_backup_allowlist).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, operations) }
    }

    @Test
    fun toolsSnapshotLoadingDisablesOnlySelinuxAndErrorHasTextAndSemantics() {
        compose.setContent {
            NarrowContent { Tools(loading = true, error = "Fixture tools failure") }
        }
        action(R.string.settings_selinux_mode).performScrollTo().assertIsNotEnabled()
        action(R.string.settings_backup_allowlist).assertIsEnabled()
        action(R.string.settings_restore_allowlist).assertIsEnabled()
        compose
            .onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.Error, "Fixture tools failure")
            )
            .assertExists()
        compose.onNodeWithText("Fixture tools failure").assertExists()
    }

    @Test
    fun unavailableKernelUsesRefreshOnlyAndDoesNotInventAlgorithms() {
        var snapshot by mutableStateOf<KernelTcpCongestionControlState?>(null)
        var refreshes = 0
        var selections = 0
        compose.setContent {
            NarrowContent {
                Kernel(
                    snapshot = snapshot,
                    onRefresh = { refreshes++ },
                    onSelected = { selections++ },
                )
            }
        }
        compose
            .onNodeWithText(text(R.string.settings_kernel_capabilities_unavailable))
            .assertExists()
        algorithm("cubic").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, refreshes) }
        action(R.string.refresh).performScrollTo().performClick()
        compose.runOnIdle { snapshot = KernelTcpCongestionControlState() }
        action(R.string.refresh).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(2, refreshes)
            assertEquals(0, selections)
        }
    }

    @Test
    fun kernelSelectionUsesHostSnapshotAndPreservesAvailableRatherThanAllowedList() {
        var snapshot by mutableStateOf(tcp())
        val selections = mutableListOf<String>()
        compose.setContent {
            NarrowContent { Kernel(snapshot = snapshot, onSelected = { selections += it }) }
        }
        algorithm("cubic").assertIsSelected().assertIsNotEnabled()
        algorithm("bbr").performScrollTo().assertIsEnabled().performClick()
        algorithm("cubic").assertIsSelected()
        compose.runOnIdle {
            assertEquals(listOf("bbr"), selections)
            snapshot = snapshot.copy(currentAlgorithm = "reno")
        }
        algorithm("reno").assertIsSelected().assertIsNotEnabled()
        algorithm("cubic").assertIsEnabled()
        compose.runOnIdle { assertEquals(listOf("bbr"), selections) }
    }

    @Test
    fun kernelActionDisablesEveryAlgorithmAndReportsMatchingProgress() {
        var actionId by mutableStateOf<String?>("tcp_congestion:bbr")
        var selections = 0
        compose.setContent {
            NarrowContent { Kernel(actionId = actionId, onSelected = { selections++ }) }
        }
        algorithm("cubic").assertIsNotEnabled()
        algorithm("bbr").performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        algorithm("reno").assertIsNotEnabled()
        progress().assertExists()
        compose.runOnIdle { actionId = "unrecognized-busy-id" }
        algorithm("bbr").assertIsNotEnabled()
        progress().assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, selections) }
    }

    @Test
    fun initialKernelLoadingAndRefreshHideActionsWithoutAutomaticRequests() {
        var refreshing by mutableStateOf(false)
        var snapshot by mutableStateOf<KernelTcpCongestionControlState?>(null)
        var requests = 0
        compose.setContent {
            NarrowContent {
                Kernel(
                    loading = true,
                    refreshing = refreshing,
                    snapshot = snapshot,
                    onRefresh = { requests++ },
                    onSelected = { requests++ },
                )
            }
        }
        progress().assertExists()
        action(R.string.refresh).assertDoesNotExist()
        algorithm("bbr").assertDoesNotExist()
        compose.runOnIdle { snapshot = tcp() }
        algorithm("bbr").assertIsEnabled()
        compose.runOnIdle { refreshing = true }
        progress().assertExists()
        algorithm("bbr").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, requests) }
    }

    @Test
    fun kernelErrorRetainsSnapshotAndRefreshIsSeparateFromAlgorithmSelection() {
        var refreshes = 0
        val selections = mutableListOf<String>()
        compose.setContent {
            NarrowContent {
                Kernel(
                    error = "Fixture kernel failure",
                    onRefresh = { refreshes++ },
                    onSelected = { selections += it },
                )
            }
        }
        compose
            .onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.Error, "Fixture kernel failure")
            )
            .assertExists()
        compose.onNodeWithText("Fixture kernel failure").assertExists()
        algorithm("bbr").performScrollTo().performClick()
        action(R.string.refresh).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("bbr"), selections)
            assertEquals(1, refreshes)
        }
    }

    @Test
    fun kernelFailureWithoutSnapshotDoesNotRemainStuckInInitialLoading() {
        compose.setContent {
            NarrowContent {
                Kernel(loading = true, snapshot = null, error = "Fixture access failure")
            }
        }
        progress().assertDoesNotExist()
        action(R.string.refresh).assertIsEnabled()
        compose.onNodeWithText("Fixture access failure").assertExists()
        compose
            .onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.Error, "Fixture access failure")
            )
            .assertExists()
    }
}
