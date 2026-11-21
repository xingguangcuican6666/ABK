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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.ui.screens.flash.DownloadedOutputRow
import com.abk.kernel.ui.screens.flash.FlashConfirmDialog
import com.abk.kernel.ui.screens.flash.FlashSlotOptions
import com.abk.kernel.ui.screens.flash.FlashTerminalDialog
import com.abk.kernel.utils.RootUtils.Ak3SlotTarget
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlashUiTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun terminalDoesNotCloseOrOfferRebootWhileRunning() {
        var running by mutableStateOf(true)
        var canReboot by mutableStateOf(true)
        var closes = 0
        var reboots = 0
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                FlashTerminalDialog(
                    title = "Test terminal",
                    running = running,
                    success = if (running) null else true,
                    logLines = listOf("test log"),
                    canReboot = canReboot,
                    onClose = { closes++ },
                    onReboot = { reboots++ },
                )
            }
        }
        val close = context.getString(R.string.close)
        val reboot = compose.onNodeWithText(context.getString(R.string.flash_reboot))
        val log = compose.onNodeWithText("test log")
        compose.onNodeWithContentDescription(close).assertDoesNotExist()
        compose.onNodeWithText(close).assertDoesNotExist()
        reboot.assertDoesNotExist()
        pressBack()
        log.assertExists()
        compose.runOnIdle { assertEquals(0, closes) }

        compose.runOnIdle {
            running = false
            canReboot = false
        }
        compose.onNodeWithText(context.getString(R.string.status_success)).assertExists()
        reboot.assertDoesNotExist()
        compose.runOnIdle { canReboot = true }
        reboot.performClick()
        compose.onNodeWithText(close).performClick()
        compose.runOnIdle {
            assertEquals(1, closes)
            assertEquals(1, reboots)
        }
    }

    @Test
    fun selectingAnInactiveSlotDoesNotExecuteUntilConfirmation() {
        var selected by mutableStateOf(Ak3SlotTarget.CURRENT)
        var confirmed: Ak3SlotTarget? = null
        var selections = 0
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                FlashConfirmDialog(
                    title = "Test confirmation",
                    message = "Review before proceeding",
                    confirmLabel = "Confirm test",
                    onConfirm = { confirmed = selected },
                    onDismiss = {},
                ) {
                    FlashSlotOptions("Current slot", "Inactive slot", selected) {
                        selected = it
                        selections++
                    }
                }
            }
        }
        val current = compose.onNodeWithText("Current slot")
        val inactive = compose.onNodeWithText("Inactive slot")
        current.assertIsSelected()
        inactive.performScrollTo().performClick().assertIsSelected()
        current.assertIsNotSelected()
        compose.runOnIdle {
            assertEquals(1, selections)
            assertNull(confirmed)
        }
        compose.onNodeWithText("Confirm test").performClick()
        compose.runOnIdle { assertEquals(Ak3SlotTarget.INACTIVE, confirmed) }
    }

    @Test
    fun downloadedFileActionsFollowCapabilitiesAtLargeFontInRtl() {
        var rootAllowed by mutableStateOf(false)
        var type by mutableStateOf(ArtifactType.ANYKERNEL3)
        val invoked = mutableListOf<String>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                AbkTheme(reduceMotion = true) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        DownloadedOutputRow(
                            artifact =
                                DownloadedArtifact(1, "test-artifact", "/unused", type, 1024),
                            onCopyPath = { invoked += "copy" },
                            onInstall = { invoked += "install" },
                            onFlash = { invoked += "flash" },
                            onDelete = { invoked += "delete" },
                            allowRootActions = rootAllowed,
                        )
                    }
                }
            }
        }
        val flash = compose.onNodeWithText(context.getString(R.string.flash_button_flash_ak3))
        val install = compose.onNodeWithText(context.getString(R.string.flash_install))
        val copy = compose.onNodeWithText(context.getString(R.string.flash_copy_path))
        flash.assertDoesNotExist()
        install.assertDoesNotExist()
        copy.performScrollTo().performClick()
        compose.runOnIdle { rootAllowed = true }
        flash.performScrollTo().performClick()
        compose.runOnIdle {
            rootAllowed = false
            type = ArtifactType.KSU_MANAGER
        }
        flash.assertDoesNotExist()
        copy.assertDoesNotExist()
        install.performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("copy", "flash", "install"), invoked) }
    }
}
