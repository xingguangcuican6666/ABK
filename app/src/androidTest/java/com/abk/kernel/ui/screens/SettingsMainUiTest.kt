package com.abk.kernel.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abk.kernel.R
import com.abk.kernel.data.model.AppUpdateCheckResult
import com.abk.kernel.data.model.AppUpdateEntry
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsMainUiTest {
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

    @Test
    fun accountActionsStaySeparateAndDoNotRunDuringComposition() {
        val actions = mutableListOf<String>()
        compose.setContent {
            NarrowRtlContent {
                SettingsAccountCard(
                    login = "fixture-account-with-a-long-name",
                    description = "Fixture account description",
                    forkName = "fixture-owner/fixture-kernel-repository",
                    canOpenFork = true,
                    onLogin = { actions += "login" },
                    onLogout = { actions += "logout" },
                    onOpenFork = { actions += "fork" },
                    onOpenRunner = { actions += "runner" },
                    avatar = { Text("Fixture avatar") },
                )
            }
        }
        compose.onNodeWithText("Fixture avatar").assertExists()
        compose.onNodeWithText("fixture-owner/fixture-kernel-repository").assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_login_hint)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
        compose
            .onNodeWithText(context.getString(R.string.settings_logout_desc))
            .performScrollTo()
            .performClick()
        compose
            .onNode(hasClickAction() and hasText(context.getString(R.string.settings_fork_repo)))
            .performScrollTo()
            .performClick()
        compose
            .onNodeWithText(context.getString(R.string.settings_kernel_runner_title))
            .performScrollTo()
            .performClick()
        compose.runOnIdle { assertEquals(listOf("logout", "fork", "runner"), actions) }
    }

    @Test
    fun signedOutAndWaitingForkStatesOnlyDispatchExplicitActions() {
        var login by mutableStateOf<String?>(null)
        val actions = mutableListOf<String>()
        compose.setContent {
            NarrowRtlContent {
                SettingsAccountCard(
                    login = login,
                    description = null,
                    forkName = null,
                    canOpenFork = false,
                    onLogin = { actions += "login" },
                    onLogout = { actions += "logout" },
                    onOpenFork = { actions += "fork" },
                    onOpenRunner = { actions += "runner" },
                )
            }
        }
        compose
            .onNodeWithText(context.getString(R.string.settings_logout_desc))
            .assertDoesNotExist()
        compose
            .onNodeWithText(context.getString(R.string.settings_waiting_fork))
            .assertDoesNotExist()
        compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
        compose
            .onNodeWithText(context.getString(R.string.settings_login_hint))
            .performScrollTo()
            .performClick()
        compose.runOnIdle { login = "fixture-account" }
        compose.onNodeWithText(context.getString(R.string.settings_waiting_fork)).assertExists()
        compose
            .onNode(hasClickAction() and hasText(context.getString(R.string.settings_fork_repo)))
            .performScrollTo()
            .assertIsNotEnabled()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("login"), actions) }
    }

    @Test
    fun logoutRequiresConfirmationAndCancelOrCloseDoesNotLogOut() {
        var open by mutableStateOf(true)
        var logouts = 0
        compose.setContent {
            AbkTheme(reduceMotion = true) {
                if (open)
                    SettingsLogoutDialog(
                        onDismiss = { open = false },
                        onConfirm = {
                            logouts++
                            open = false
                        },
                    )
            }
        }
        compose.runOnIdle { assertEquals(0, logouts) }
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.runOnIdle {
            assertEquals(0, logouts)
            open = true
        }
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        compose.runOnIdle {
            assertEquals(0, logouts)
            open = true
        }
        compose
            .onNodeWithText(context.getString(R.string.confirm))
            .performClick()
            .assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, logouts) }
    }

    @Test
    fun updateResultsKeepDownloadGatesAndNeverStartDownloadsOnTheirOwn() {
        var info by mutableStateOf<AppUpdateCheckResult?>(null)
        val actions = mutableListOf<String>()
        compose.setContent {
            NarrowRtlContent {
                SettingsAppUpdateFeedback(
                    checking = false,
                    downloading = false,
                    downloadProgress = 0,
                    info = info,
                    checkSubtitle = "Fixture channel and line",
                    resultSubtitle = info?.let { "Fixture version result" },
                    error = null,
                    onCheck = { actions += "check" },
                    onDownload = { actions += "download" },
                )
            }
        }
        val download =
            compose.onNodeWithText(context.getString(R.string.settings_download_install_update))
        download.assertDoesNotExist()
        compose.runOnIdle { info = fixtureUpdate(hasUpdate = false) }
        compose
            .onNodeWithText(context.getString(R.string.settings_app_update_latest))
            .assertExists()
        download.assertDoesNotExist()
        compose.runOnIdle { info = fixtureUpdate(downloadUrl = "") }
        compose
            .onNodeWithText(context.getString(R.string.settings_app_update_link_missing))
            .assertExists()
        download.performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(emptyList<String>(), actions)
            info = fixtureUpdate()
        }
        compose.onNodeWithText("https://fixture.example/app.apk").assertExists()
        compose
            .onNodeWithText(context.getString(R.string.settings_check_app_update))
            .performScrollTo()
            .performClick()
        download.performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("check", "download"), actions) }
    }

    @Test
    fun updateLoadingAndProgressExposeSemanticsAndPreventDuplicateActions() {
        var checking by mutableStateOf(true)
        var downloading by mutableStateOf(false)
        val actions = mutableListOf<String>()
        compose.setContent {
            NarrowRtlContent {
                SettingsAppUpdateFeedback(
                    checking = checking,
                    downloading = downloading,
                    downloadProgress = 42,
                    info = fixtureUpdate(),
                    checkSubtitle = "Fixture update state",
                    resultSubtitle = "Fixture result",
                    error = null,
                    onCheck = { actions += "check" },
                    onDownload = { actions += "download" },
                )
            }
        }
        compose
            .onNodeWithContentDescription(context.getString(R.string.settings_app_update_checking))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate,
                )
            )
        compose
            .onNode(
                hasClickAction() and hasText(context.getString(R.string.settings_check_app_update))
            )
            .performScrollTo()
            .assertIsNotEnabled()
            .performTouchInput { click() }
        compose.runOnIdle {
            checking = false
            downloading = true
        }
        compose
            .onNodeWithContentDescription(
                context.getString(R.string.settings_app_update_downloading_progress, 42)
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(0.42f, 0f..1f),
                )
            )
        compose
            .onNode(
                hasClickAction() and
                    hasText(context.getString(R.string.settings_download_install_update))
            )
            .performScrollTo()
            .assertIsNotEnabled()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
    }

    @Test
    fun updateErrorAndDiagnosticsStayVisibleWithoutAutomaticSideEffects() {
        var exporting by mutableStateOf(true)
        var exports = 0
        compose.setContent {
            NarrowRtlContent {
                SettingsMainCard("Fixture update and diagnostics") {
                    SettingsAppUpdateFeedback(
                        checking = false,
                        downloading = false,
                        downloadProgress = 0,
                        info = null,
                        checkSubtitle = "Fixture channel",
                        resultSubtitle = null,
                        error = "Fixture connection failure",
                        onCheck = {},
                        onDownload = { error("Download must not be offered") },
                    )
                    SettingsDiagnosticsAction(exporting) { exports++ }
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.settings_app_update_error)).assertExists()
        compose
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Error,
                    "Fixture connection failure",
                )
            )
            .assertExists()
        val export =
            compose.onNode(
                hasClickAction() and
                    hasText(context.getString(R.string.settings_export_diagnostics))
            )
        export.performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(0, exports)
            exporting = false
        }
        export.performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, exports) }
    }

    @Test
    fun disabledDestinationCannotOpenAndHostEnableDoesNotDispatch() {
        var enabled by mutableStateOf(false)
        var opens = 0
        compose.setContent {
            NarrowRtlContent {
                SettingsMainDestination(
                    title = "Fixture destination",
                    description = "Fixture destination description that wraps on a narrow display",
                    icon = io.github.xingguangcuican6666.abk.ui.icons.AbkIcons.Info,
                    enabled = enabled,
                    onClick = { opens++ },
                )
            }
        }
        val destination = compose.onNodeWithText("Fixture destination")
        destination.performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(0, opens)
            enabled = true
        }
        compose.runOnIdle { assertEquals(0, opens) }
        destination.performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opens) }
    }

    private fun fixtureUpdate(
        hasUpdate: Boolean = true,
        downloadUrl: String = "https://fixture.example/app.apk",
    ) =
        AppUpdateCheckResult(
            stability = "stable",
            line = "normal",
            currentVersionName = "1.0-fixture",
            currentVersionCode = 1L,
            currentBuildTimestampEpochMillis = 0L,
            remote =
                AppUpdateEntry(
                    versionName = "2.0-fixture",
                    versionCode = 2L,
                    downloadUrl = downloadUrl,
                ),
            hasUpdate = hasUpdate,
        )
}
