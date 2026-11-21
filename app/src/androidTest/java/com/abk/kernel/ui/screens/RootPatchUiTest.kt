package com.abk.kernel.ui.screens

import android.content.Context
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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
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
import com.abk.kernel.utils.ClientNoticeManifest
import com.abk.kernel.utils.FeatureStatusManifest
import com.abk.kernel.utils.KernelSourceManifest
import com.abk.kernel.utils.SignedBundleManifest
import com.abk.kernel.utils.SkippedFeatureManifest
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootPatchUiTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun modesFollowRootCapabilityAndLockDuringExecution() {
        var root by mutableStateOf(false)
        var running by mutableStateOf(false)
        var selected by mutableStateOf<LkmPatchInstallMode?>(null)
        val selections = mutableListOf<LkmPatchInstallMode>()
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RootPatchModeOptions(
                        selected,
                        root,
                        running,
                        "boot-test.img",
                        "kernel-test.zip",
                    ) {
                        selected = it
                        selections += it
                    }
                }
            }
        }
        val file = compose.onNodeWithText(context.getString(R.string.root_patch_select_file))
        val direct = compose.onNodeWithText(context.getString(R.string.root_patch_direct_install))
        val ota = compose.onNodeWithText(context.getString(R.string.root_patch_ota_install))
        val ak3 = compose.onNodeWithText(context.getString(R.string.root_patch_anykernel))
        direct.assertDoesNotExist()
        ota.assertDoesNotExist()
        ak3.assertDoesNotExist()
        file.performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { root = true }
        direct.performScrollTo().performClick().assertIsSelected()
        file.assertIsNotSelected()
        ota.performScrollTo().performClick().assertIsSelected()
        ak3.performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { running = true }
        file.assertIsNotEnabled()
        direct.assertIsNotEnabled()
        ota.assertIsNotEnabled()
        ak3.assertIsNotEnabled()
        file.performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(LkmPatchInstallMode.entries, selections)
            assertEquals(LkmPatchInstallMode.AnyKernel3, selected)
        }
    }

    @Test
    fun advancedChoicesStayControlledAndDisabledDuringExecutionInNarrowRtlLayout() {
        var expanded by mutableStateOf(false)
        var running by mutableStateOf(false)
        var shell by mutableStateOf(false)
        var adb by mutableStateOf(false)
        var shellChanges = 0
        var adbChanges = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                AbkTheme(reduceMotion = true) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        RootPatchGroupCard {
                            RootPatchAdvancedOptions(
                                expanded,
                                running,
                                shell,
                                adb,
                                onExpandedChange = { expanded = it },
                                onAllowShellChange = {
                                    shell = it
                                    shellChanges++
                                },
                                onEnableAdbChange = {
                                    adb = it
                                    adbChanges++
                                },
                            )
                        }
                    }
                }
            }
        }
        val expand = compose.onNodeWithText(context.getString(R.string.root_patch_advanced_options))
        val shellChoice =
            compose.onNodeWithText(context.getString(R.string.root_patch_allow_shell_root))
        val adbChoice =
            compose.onNodeWithText(context.getString(R.string.root_patch_enable_adb_debug))
        shellChoice.assertDoesNotExist()
        expand.performScrollTo().performClick()
        shellChoice.performScrollTo().assertIsOff().performClick().assertIsOn()
        adbChoice.performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { running = true }
        shellChoice.assertIsNotEnabled()
        adbChoice.assertIsNotEnabled()
        shellChoice.performScrollTo().performClick()
        adbChoice.performScrollTo().performClick()
        expand.performScrollTo().performClick()
        shellChoice.assertDoesNotExist()
        expand.performClick()
        shellChoice.performScrollTo().assertIsOn()
        adbChoice.performScrollTo().assertIsOn()
        compose.runOnIdle {
            assertEquals(1, shellChanges)
            assertEquals(1, adbChanges)
        }
    }

    @Test
    fun resultActionsAndRebootReflectHostCapabilitiesWithoutExecutingOnComposition() {
        var running by mutableStateOf(true)
        var success by mutableStateOf<Boolean?>(null)
        var canReboot by mutableStateOf(true)
        var canFlash by mutableStateOf(false)
        val invoked = mutableListOf<String>()
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    RootPatchedImageCard(
                        "/unused/boot-patched-test.img",
                        canFlash,
                        onCopy = { invoked += "copy" },
                        onFlash = { invoked += "flash" },
                    )
                    RootPatchLogCard(
                        running,
                        success,
                        "Test action",
                        listOf("test log"),
                        canReboot,
                    ) {
                        invoked += "reboot"
                    }
                }
            }
        }
        val flash = compose.onNodeWithText(context.getString(R.string.root_patch_flash))
        val reboot = compose.onNodeWithText(context.getString(R.string.root_patch_reboot))
        compose.runOnIdle { assertEquals(emptyList<String>(), invoked) }
        flash.assertDoesNotExist()
        reboot.assertDoesNotExist()
        compose
            .onNodeWithText(context.getString(R.string.root_patch_copy_path))
            .performScrollTo()
            .performClick()
        compose.runOnIdle {
            running = false
            success = false
            canReboot = false
        }
        compose.onNodeWithText(context.getString(R.string.status_failure)).assertExists()
        reboot.assertDoesNotExist()
        compose.runOnIdle {
            success = true
            canFlash = true
        }
        compose.onNodeWithText(context.getString(R.string.status_success)).assertExists()
        reboot.assertDoesNotExist()
        flash.performScrollTo().performClick()
        compose.runOnIdle { canReboot = true }
        reboot.performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("copy", "flash", "reboot"), invoked) }
    }

    @Test
    fun manifestNoticeAcknowledgementIsSeparateFromFlashConfirmation() {
        var confirmFlash by mutableStateOf(false)
        var acknowledgements = 0
        var executions = 0
        var dismissals = 0
        val manifest =
            SignedBundleManifest(
                bundleName = "test.bundle.zip",
                artifactType = "anykernel3",
                runId = 0,
                payloadName = "test.zip",
                payloadSha256 = "test",
                payloadSizeBytes = 0,
                kernelSource =
                    KernelSourceManifest(
                        url = "https://example.invalid/test/kernel",
                        requestedRef = "test-ref",
                        access = "github_private",
                        defconfigs = listOf("test_defconfig"),
                    ),
                featureStatus =
                    FeatureStatusManifest(
                        requested = mapOf("feature-test" to true),
                        effective = mapOf("feature-test" to false),
                        skipped =
                            listOf(SkippedFeatureManifest("feature-test", "test", "test skipped")),
                    ),
                clientNotice = ClientNoticeManifest(reviewBeforeFlash = true),
            )
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                RootPatchManifestDialog(
                    manifest,
                    confirmFlash,
                    onConfirm = { if (confirmFlash) executions++ else acknowledgements++ },
                    onDismiss = { dismissals++ },
                )
            }
        }
        compose
            .onNodeWithText(context.getString(R.string.flash_custom_source_private_warning))
            .assertExists()
        compose
            .onNodeWithText(
                context.getString(R.string.flash_custom_source_defconfigs, "test_defconfig")
            )
            .assertExists()
        compose.onNodeWithText("• feature-test: test skipped").assertExists()
        compose.onNodeWithText(context.getString(R.string.confirm)).performClick()
        compose.runOnIdle {
            assertEquals(1, acknowledgements)
            assertEquals(0, executions)
            confirmFlash = true
        }
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.runOnIdle {
            assertEquals(1, dismissals)
            assertEquals(0, executions)
        }
        compose.onNodeWithText(context.getString(R.string.flash_confirm)).performClick()
        compose.runOnIdle { assertEquals(1, executions) }
    }
}
