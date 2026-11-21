package com.abk.kernel.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abk.kernel.R
import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.data.model.AbkRuntimeStatus
import com.abk.kernel.viewmodel.RuntimeModuleUpdateInfo
import com.abk.kernel.viewmodel.RuntimeModuleUpdateTarget
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun text(id: Int) = context.getString(id)

    private val module =
        AbkRuntimeModule(
            id = "fixture",
            name = "Fixture module",
            type = "standard",
            controllable = true,
            version = "1.0",
            hasWebUi = true,
            actionSupported = true,
        )
    private val update = RuntimeModuleUpdateInfo("2.0", 2, "https://example.invalid/module.zip", "")

    @Test
    fun moduleActionsFollowCapabilitiesAtLargeFontInNarrowRtlLayout() {
        var current by mutableStateOf(module)
        var inFlight by mutableStateOf(false)
        val actions = mutableListOf<String>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                AbkTheme(reduceMotion = true) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        InstalledRuntimeModuleCard(
                            module = current,
                            updateCandidate = RuntimeModuleUpdateTarget(current, update),
                            actionInFlight = inFlight,
                            onSetEnabled = {
                                current = current.copy(enabled = it)
                                actions += "toggle"
                            },
                            onRequestUpdate = { actions += "update" },
                            onRequestUninstall = { actions += "uninstall" },
                            onRunAction = { actions += "action" },
                            onOpenWebUi = { actions += "webui" },
                        )
                    }
                }
            }
        }
        val toggle = compose.onNode(isToggleable())
        val webui = compose.onNodeWithText(text(R.string.runtime_open_webui))
        val action = compose.onNodeWithText(text(R.string.runtime_run_action))
        val updateButton = compose.onNodeWithText(text(R.string.runtime_update_module))
        val uninstall = compose.onNodeWithText(text(R.string.runtime_uninstall))
        compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
        toggle.performScrollTo().assertIsOn().performClick().assertIsOff()
        webui.assertIsNotEnabled()
        action.assertIsNotEnabled()
        toggle.performScrollTo().performClick().assertIsOn()
        webui.performScrollTo().performClick()
        action.performScrollTo().performClick()
        updateButton.performScrollTo().performClick()
        uninstall.performScrollTo().performClick()
        compose.runOnIdle { inFlight = true }
        toggle.assertIsNotEnabled()
        action.assertIsNotEnabled()
        updateButton.assertIsNotEnabled()
        uninstall.assertIsNotEnabled()
        action.performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(
                listOf("toggle", "toggle", "webui", "action", "update", "uninstall"),
                actions,
            )
            inFlight = false
            current = current.copy(update = true)
        }
        toggle.assertIsNotEnabled()
        webui.assertIsNotEnabled()
        compose.runOnIdle { current = current.copy(update = false, remove = true) }
        webui.assertIsNotEnabled()
        updateButton.assertIsNotEnabled()
        uninstall.assertDoesNotExist()
        compose.onNodeWithText(text(R.string.runtime_revoke)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("uninstall", actions.last()) }
    }

    @Test
    fun readOnlyAndScriptMetadataDoNotCreateControlOrActionCapabilities() {
        var current by
            mutableStateOf(
                module.copy(
                    readonly = true,
                    hasWebUi = false,
                    actionSupported = false,
                    hasActionScript = true,
                )
            )
        val actions = mutableListOf<String>()
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    InstalledRuntimeModuleCard(
                        current,
                        null,
                        false,
                        { actions += "toggle" },
                        { actions += "update" },
                        { actions += "uninstall" },
                        { actions += "action" },
                        { actions += "webui" },
                    )
                }
            }
        }
        compose.onNode(isToggleable()).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.runtime_uninstall)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.runtime_run_action)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.runtime_open_webui)).assertDoesNotExist()
        compose.runOnIdle { current = current.copy(readonly = false, type = "kpm") }
        compose.onNodeWithText(text(R.string.runtime_uninstall)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
    }

    @Test
    fun installExecutionCannotCloseOrRebootUntilSuccessfulCompletion() {
        var running by mutableStateOf(true)
        var success by mutableStateOf<Boolean?>(null)
        var closes = 0
        var reboots = 0
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                RuntimeModuleInstallDialog(
                    running,
                    success,
                    listOf("fixture log"),
                    { closes++ },
                    { reboots++ },
                )
            }
        }
        compose.onNodeWithContentDescription(text(R.string.close)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.close)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.runtime_reboot)).assertDoesNotExist()
        pressBack()
        compose.onNodeWithText("fixture log").assertExists()
        compose.runOnIdle {
            assertEquals(0, closes)
            running = false
            success = false
        }
        compose.onNodeWithText(text(R.string.status_failure)).assertExists()
        compose.onNodeWithText(text(R.string.runtime_reboot)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.close)).performClick()
        compose.runOnIdle {
            assertEquals(1, closes)
            success = true
        }
        compose.onNodeWithText(text(R.string.status_success)).assertExists()
        compose.onNodeWithText(text(R.string.runtime_reboot)).performClick()
        compose.runOnIdle { assertEquals(1, reboots) }
    }

    @Test
    fun actionOutputRemainsDismissibleWithoutIntroducingReboot() {
        var closes = 0
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                RuntimeModuleActionDialog(
                    "Fixture action",
                    true,
                    listOf("fixture output"),
                    { closes++ },
                )
            }
        }
        compose.onNodeWithText(text(R.string.runtime_reboot)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.close)).performClick()
        compose.runOnIdle { assertEquals(1, closes) }
    }

    @Test
    fun permissionFallbackIsDistinctFromGrantAndCompositionDoesNothing() {
        val actions = mutableListOf<String>()
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                RuntimeModuleFileAccessDialog(
                    { actions += "dismiss" },
                    { actions += "grant" },
                    { actions += "picker" },
                )
            }
        }
        compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
        compose.onNodeWithText(text(R.string.runtime_system_picker)).performClick()
        compose.onNodeWithText(text(R.string.runtime_grant_permission)).performClick()
        compose.runOnIdle { assertEquals(listOf("picker", "grant"), actions) }
    }

    @Test
    fun installUpdateAndUninstallWaitForTheirOwnConfirmation() {
        var stage by mutableStateOf(0)
        val actions = mutableListOf<String>()
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                when (stage) {
                    0 ->
                        RuntimeModuleInstallConfirmDialog(
                            Uri.parse("content://fixture/module"),
                            "fixture.zip",
                            { actions += "cancel" },
                            { actions += "install" },
                        )
                    1 ->
                        RuntimeModuleUpdateContentDialog(
                            module,
                            update,
                            "Fixture changelog",
                            "/fixture/downloads",
                            { actions += "cancel" },
                            { actions += "update" },
                        )
                    else ->
                        RuntimeModuleUninstallConfirmDialog(
                            module,
                            stage == 2,
                            { actions += "cancel" },
                            { actions += if (stage == 2) "uninstall" else "revoke" },
                        )
                }
            }
        }
        compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        compose.onNodeWithText(text(R.string.runtime_confirm_flash)).performClick()
        compose.runOnIdle { stage = 1 }
        // The title shares the update label; only the action has a click handler.
        compose.onNodeWithText("Fixture changelog").assertExists()
        compose
            .onNode(hasText(text(R.string.runtime_update_module)) and hasClickAction())
            .performClick()
        compose.runOnIdle { stage = 2 }
        compose.onNodeWithText(text(R.string.runtime_uninstall)).performClick()
        compose.runOnIdle { stage = 3 }
        compose.onNodeWithText(text(R.string.runtime_revoke)).performClick()
        compose.runOnIdle {
            assertEquals(listOf("cancel", "install", "update", "uninstall", "revoke"), actions)
        }
    }

    @Test
    fun inactiveManagerDoesNotInferPermissionFromStatusAndPatchEntryIsExplicit() {
        var permitted by mutableStateOf(false)
        var status by mutableStateOf<AbkRuntimeStatus?>(AbkRuntimeStatus())
        val actions = mutableListOf<String>()
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RuntimeStatusHeader(
                        status,
                        permitted,
                        false,
                        null,
                        { actions += "refresh" },
                        { actions += "patch" },
                    )
                }
            }
        }
        compose.onNodeWithText(text(R.string.runtime_manager_inactive_title)).assertExists()
        compose.runOnIdle {
            assertEquals(emptyList<String>(), actions)
            permitted = true
        }
        compose.onNodeWithText(text(R.string.runtime_manager_active)).assertExists()
        compose.onNodeWithText(text(R.string.root_patch_title)).performScrollTo().performClick()
        compose.runOnIdle { status = null }
        compose.onNodeWithText(text(R.string.runtime_recheck)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("patch", "refresh"), actions) }
    }
}
