@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.viewmodel.AuthStep
import com.abk.kernel.viewmodel.MainViewModel
import io.github.xingguangcuican6666.abk.ui.components.AbkSpinner
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val OOBE_SKIP_LOADING_DELAY_MS = 320L
private const val OOBE_SKIP_EXIT_DELAY_MS = 280L
private const val OOBE_SKIP_BACK_VISUAL_EXPONENT = 1.8f
private const val OOBE_SKIP_BACK_SCALE_DELTA = 0.09f
private val OOBE_SKIP_MAX_CORNER = 32.dp

@Composable
fun OobeScreen(vm: MainViewModel) {
  val state by vm.uiState.collectAsState()
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var skipInFlight by remember { mutableStateOf(false) }
  var skipExitStarted by remember { mutableStateOf(false) }
  val motionScheme = MaterialTheme.motionScheme
  val animatedSkipExitProgress by
      animateFloatAsState(
          targetValue = if (skipExitStarted) 1f else 0f,
          animationSpec = motionScheme.fastSpatialSpec(),
          label = "oobe-skip-exit-progress",
      )
  val visualSkipExitProgress =
      animatedSkipExitProgress.coerceIn(0f, 1f).pow(OOBE_SKIP_BACK_VISUAL_EXPONENT)
  val density = LocalDensity.current

  fun requestSkip() {
    if (skipInFlight) return
    skipInFlight = true
    scope.launch {
      delay(OOBE_SKIP_LOADING_DELAY_MS)
      skipExitStarted = true
      delay(OOBE_SKIP_EXIT_DELAY_MS)
      vm.skipOobe()
    }
  }

  BoxWithConstraints(
      modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
  ) {
    val exitWidthPx = with(density) { maxWidth.toPx() }
    val exitCorner = with(density) { (OOBE_SKIP_MAX_CORNER.toPx() * visualSkipExitProgress).toDp() }

    Box(
        modifier =
            Modifier.fillMaxSize().graphicsLayer {
              translationX = exitWidthPx * animatedSkipExitProgress
              scaleX = 1f - OOBE_SKIP_BACK_SCALE_DELTA * visualSkipExitProgress
              scaleY = 1f - OOBE_SKIP_BACK_SCALE_DELTA * visualSkipExitProgress
              alpha = 1f - 0.08f * visualSkipExitProgress
              shape = RoundedCornerShape(exitCorner)
              clip = visualSkipExitProgress > 0.01f
            }
    ) {
      when (state.authStep) {
        AuthStep.INTRO ->
            OobeIntroContent(
                loggedIn = state.isLoggedIn,
                skipping = skipInFlight,
                onContinue = {
                  if (!skipInFlight) {
                    if (state.isLoggedIn) {
                      vm.openBuildOobe()
                    } else {
                      vm.continueOobeToLogin()
                    }
                  }
                },
                onSkip = ::requestSkip,
            )
        AuthStep.LOGIN ->
            LoginContent(
                isLoading = state.isLoading,
                userCode = state.userCode,
                verificationUri = state.verificationUri,
                isPolling = state.isPollingToken,
                error = state.error,
                onLogin = { if (!skipInFlight) vm.startDeviceFlow() },
                onSkip = ::requestSkip,
                skipInFlight = skipInFlight,
                onClearError = { vm.clearError() },
                onCopyCode = { code ->
                  val clipboard =
                      context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  clipboard.setPrimaryClip(ClipData.newPlainText("user_code", code))
                },
                onOpenVerificationUrl = { url ->
                  runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                },
            )
        AuthStep.FORK_CHECK ->
            ForkCheckContent(
                isLoading = state.isLoading,
                hasFork = state.forkRepo != null,
                behindBy = state.behindBy,
                showSyncDialog = false,
                error = state.error,
                onFork = { if (!skipInFlight) vm.forkRepo() },
                onSync = { if (!skipInFlight) vm.syncFork() },
                onSkip = ::requestSkip,
                showSkipAction = true,
                skipInFlight = skipInFlight,
                onClearError = { vm.clearError() },
            )
      }
    }

    if (skipInFlight) {
      Box(
          modifier =
              Modifier.fillMaxSize()
                  .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)),
          contentAlignment = Alignment.Center,
      ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
          Row(
              modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            AbkSpinner(Modifier.size(28.dp), label = stringResource(R.string.loading))
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
          }
        }
      }
    }
  }
}
