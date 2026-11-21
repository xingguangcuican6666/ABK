package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
class AuthGateScreenTest {
  @get:Rule val compose = createComposeRule()
  private val context
    get() = ApplicationProvider.getApplicationContext<android.content.Context>()

  private fun text(id: Int) = context.getString(id)

  private fun action(label: String) = compose.onNode(hasClickAction() and hasText(label))

  @Composable
  private fun NarrowRtlContent(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, 2f),
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
      AbkTheme(reduceMotion = true) { Box(Modifier.width(320.dp).height(700.dp)) { content() } }
    }
  }

  @Test
  fun introRendersBothExplanationsAndOnlyDispatchesExplicitActions() {
    val actions = mutableListOf<String>()
    compose.setContent {
      NarrowRtlContent {
        OobeIntroContent(
            loggedIn = false,
            skipping = false,
            onContinue = { actions += "continue" },
            onSkip = { actions += "skip" },
        )
      }
    }
    compose.onNodeWithText(text(R.string.oobe_title)).assertExists()
    compose.onNodeWithText(text(R.string.oobe_build_title)).assertExists()
    compose.onNodeWithText(text(R.string.oobe_flash_title)).performScrollTo().assertExists()
    compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
    action(text(R.string.login_github)).performScrollTo().performClick()
    action(text(R.string.oobe_skip_for_now)).performScrollTo().performClick()
    compose.runOnIdle { assertEquals(listOf("continue", "skip"), actions) }
  }

  @Test
  fun loggedInIntroUsesSetupActionAndDisablesActionsWhileSkipping() {
    var continues = 0
    compose.setContent {
      NarrowRtlContent {
        OobeIntroContent(
            loggedIn = true,
            skipping = true,
            onContinue = { continues++ },
            onSkip = {},
        )
      }
    }
    action(text(R.string.oobe_continue_setup)).assertExists().assertIsNotEnabled()
    action(text(R.string.oobe_skip_for_now)).assertExists().assertIsNotEnabled()
    compose.runOnIdle { assertEquals(0, continues) }
  }

  @Test
  fun loginRequiresConsentAndCancelDoesNotStartDeviceFlow() {
    var starts = 0
    val skips = mutableListOf<String>()
    compose.setContent {
      NarrowRtlContent {
        LoginContent(
            isLoading = false,
            userCode = null,
            verificationUri = null,
            isPolling = false,
            error = null,
            onLogin = { starts++ },
            onSkip = { skips += "skip" },
            skipInFlight = false,
            onClearError = {},
            onCopyCode = {},
            onOpenVerificationUrl = {},
        )
      }
    }
    compose.runOnIdle { assertEquals(0, starts) }
    action(text(R.string.login_github)).performScrollTo().performClick()
    compose.onNodeWithText(text(R.string.github_auth_title)).assertExists()
    compose.onNodeWithText(text(R.string.github_auth_desc)).assertExists()
    action(text(R.string.cancel)).performClick()
    compose.runOnIdle { assertEquals(0, starts) }
    compose.onNodeWithText(text(R.string.github_auth_title)).assertDoesNotExist()
  }

  @Test
  fun loginStartsDeviceFlowOnlyAfterConsentConfirmation() {
    var starts = 0
    compose.setContent {
      NarrowRtlContent {
        LoginContent(
            isLoading = false,
            userCode = null,
            verificationUri = null,
            isPolling = false,
            error = null,
            onLogin = { starts++ },
            onSkip = {},
            skipInFlight = false,
            onClearError = {},
            onCopyCode = {},
            onOpenVerificationUrl = {},
        )
      }
    }
    action(text(R.string.login_github)).performScrollTo().performClick()
    compose.runOnIdle { assertEquals(0, starts) }
    action(text(R.string.confirm)).performClick()
    compose.runOnIdle { assertEquals(1, starts) }
  }

  @Test
  fun deviceCodeActionsAreExplicitAndUseTheFallbackVerificationUrl() {
    val copied = mutableListOf<String>()
    val opened = mutableListOf<String>()
    var skips = 0
    compose.setContent {
      NarrowRtlContent {
        LoginContent(
            isLoading = false,
            userCode = "ABCD-EFGH",
            verificationUri = null,
            isPolling = true,
            error = null,
            onLogin = {},
            onSkip = { skips++ },
            skipInFlight = false,
            onClearError = {},
            onCopyCode = { copied += it },
            onOpenVerificationUrl = { opened += it },
        )
      }
    }
    compose.onNodeWithText("ABCD-EFGH").assertExists()
    compose.onNodeWithText(text(R.string.waiting_auth)).assertExists()
    compose.runOnIdle {
      assertEquals(emptyList<String>(), copied)
      assertEquals(emptyList<String>(), opened)
      assertEquals(0, skips)
    }
    action(text(R.string.copy)).performScrollTo().performClick()
    action(text(R.string.open_browser)).performScrollTo().performClick()
    compose.runOnIdle {
      assertEquals(listOf("ABCD-EFGH"), copied)
      assertEquals(listOf("https://github.com/login/device"), opened)
    }
  }

  @Test
  fun loginErrorIsVisibleAndClearedOnlyOnRequest() {
    var clears = 0
    compose.setContent {
      NarrowRtlContent {
        LoginContent(
            isLoading = true,
            userCode = null,
            verificationUri = null,
            isPolling = false,
            error = "Fixture authorization failure",
            onLogin = {},
            onSkip = {},
            skipInFlight = true,
            onClearError = { clears++ },
            onCopyCode = {},
            onOpenVerificationUrl = {},
        )
      }
    }
    compose.onNodeWithText("Fixture authorization failure").assertExists()
    action(text(R.string.login_github)).assertExists().assertIsNotEnabled()
    compose.runOnIdle { assertEquals(0, clears) }
    compose.onNodeWithContentDescription(text(R.string.close_error)).performClick()
    compose.runOnIdle { assertEquals(1, clears) }
  }

  @Test
  fun forkLoadingAndMissingForkOnlyDispatchExplicitForkOrSkip() {
    var loading by mutableStateOf(true)
    val actions = mutableListOf<String>()
    compose.setContent {
      NarrowRtlContent {
        ForkCheckContent(
            isLoading = loading,
            hasFork = false,
            behindBy = 0,
            showSyncDialog = false,
            error = null,
            onFork = { actions += "fork" },
            onSync = { actions += "sync" },
            onSkip = { actions += "skip" },
            showSkipAction = true,
            onClearError = { actions += "clear" },
        )
      }
    }
    compose.onNodeWithText(text(R.string.fork_checking_title)).assertExists()
    compose.onNodeWithContentDescription(text(R.string.fork_checking_title)).assertExists()
    compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
    compose.runOnIdle { loading = false }
    compose.waitForIdle()
    compose.onNodeWithText(text(R.string.fork_title)).assertExists()
    compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
    action(text(R.string.fork_action)).performScrollTo().performClick()
    action(text(R.string.oobe_skip_for_now)).performScrollTo().performClick()
    compose.runOnIdle { assertEquals(listOf("fork", "skip"), actions) }
  }

  @Test
  fun readyForkShowsBehindCountAndErrorClearRemainsExplicit() {
    val actions = mutableListOf<String>()
    compose.setContent {
      NarrowRtlContent {
        ForkCheckContent(
            isLoading = false,
            hasFork = true,
            behindBy = 3,
            showSyncDialog = false,
            error = "Fixture fork warning",
            onFork = { actions += "fork" },
            onSync = { actions += "sync" },
            onSkip = { actions += "skip" },
            showSkipAction = true,
            onClearError = { actions += "clear" },
        )
      }
    }
    compose.onNodeWithText(text(R.string.fork_ready_title)).assertExists()
    compose.onNodeWithText(context.getString(R.string.fork_ready_behind, 3)).assertExists()
    compose.onNodeWithText("Fixture fork warning").assertExists()
    compose.onAllNodesWithText(text(R.string.sync_action)).assertCountEquals(0)
    compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
    compose.onNodeWithContentDescription(text(R.string.close_error)).performClick()
    compose.runOnIdle { assertEquals(listOf("clear"), actions) }
  }

  @Test
  fun upToDateForkShowsReadyStateWithoutOfferingSync() {
    val actions = mutableListOf<String>()
    compose.setContent {
      NarrowRtlContent {
        ForkCheckContent(
            isLoading = false,
            hasFork = true,
            behindBy = 0,
            showSyncDialog = false,
            error = null,
            onFork = { actions += "fork" },
            onSync = { actions += "sync" },
            onSkip = { actions += "skip" },
            showSkipAction = true,
            onClearError = { actions += "clear" },
        )
      }
    }
    compose.onNodeWithText(text(R.string.fork_ready_title)).assertExists()
    compose.onNodeWithText(text(R.string.fork_ready_ok)).assertExists()
    compose.onAllNodesWithText(text(R.string.sync_action)).assertCountEquals(0)
    compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
  }
}
