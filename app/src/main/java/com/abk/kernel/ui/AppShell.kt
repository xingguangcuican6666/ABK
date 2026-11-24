package com.abk.kernel.ui

import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.ui.build.BuildDestination
import com.abk.kernel.ui.flash.FlashDestination
import com.abk.kernel.ui.home.StatusDestination
import com.abk.kernel.ui.modules.ModulesDestination
import com.abk.kernel.ui.settings.SettingsDestination
import com.abk.kernel.utils.findActivity
import com.abk.kernel.viewmodel.MainViewModel
import io.github.xingguangcuican6666.abk.ui.components.AbkBottomNavigation
import io.github.xingguangcuican6666.abk.ui.components.AbkNavigationItem
import io.github.xingguangcuican6666.abk.ui.components.AbkPageSwitch
import io.github.xingguangcuican6666.abk.ui.components.AbkSnackbarHost
import io.github.xingguangcuican6666.abk.ui.components.rememberAbkSnackbarHostState
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkMotion
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone
import io.github.xingguangcuican6666.abk.ui.theme.abkTween
import kotlinx.coroutines.CancellationException

/** Top-level destinations: equal weight, listed in bottom-bar order. */
enum class AbkDestination(val labelRes: Int, val icon: ImageVector) {
    Status(R.string.nav_status, AbkIcons.Home),
    Build(R.string.nav_build, AbkIcons.Build),
    Flash(R.string.nav_flash, AbkIcons.Upload),
    Modules(R.string.nav_modules, AbkIcons.Modules),
    Settings(R.string.nav_settings, AbkIcons.Settings),
}

private const val EXIT_BACK_INTERVAL_MS = 2_000L

/**
 * The application shell: a single background, five equal-weight destinations,
 * a snackbar host driven by the view model's message/error fields, and a
 * second-press-to-exit back handler for top-level pages.
 *
 * Child pages hide the bar by reporting [onChildPageVisibleChange] upwards.
 *
 * Switching destinations slides the pages sideways — towards the far end of
 * the reading direction when moving forward in the bar and towards the near
 * end when moving back — and the predictive back gesture lifts the whole shell
 * out of the way while the finger is still down.
 */
@Composable
fun AppShell(
    vm: MainViewModel,
    state: com.abk.kernel.viewmodel.MainUiState,
    pendingModuleInstallUri: String? = null,
    onModuleInstallUriConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(AbkDestination.Status) }
    var childPageVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.markMainUiEntered() }

    LaunchedEffect(selected) { childPageVisible = false }

    LaunchedEffect(pendingModuleInstallUri) {
        if (!pendingModuleInstallUri.isNullOrBlank()) {
            selected = AbkDestination.Modules
        }
    }

    val snackbar = rememberAbkSnackbarHostState()
    val closeError = stringResource(R.string.close_error)
    LaunchedEffect(state.snackbarMessage, state.error, state.snackbarLongDuration) {
        val message = state.snackbarMessage
        if (message != null) {
            val hadError = state.error != null
            vm.clearSnackbar()
            if (hadError) vm.clearError()
            snackbar.show(
                message = message,
                tone = if (hadError) AbkTone.Danger else AbkTone.Neutral,
                withDismissAction = true,
                duration = if (state.snackbarLongDuration) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        } else {
            val error = state.error ?: return@LaunchedEffect
            vm.clearError()
            snackbar.show(
                message = error,
                tone = AbkTone.Danger,
                actionLabel = closeError,
                duration = SnackbarDuration.Long,
            )
        }
    }

    var lastBackAt by remember { mutableLongStateOf(0L) }
    // The same action the button triggers, so the gesture and the button never disagree.
    fun exitOrAskToExit() {
        val now = System.currentTimeMillis()
        if (now - lastBackAt > EXIT_BACK_INTERVAL_MS) {
            lastBackAt = now
            Toast.makeText(context, context.getString(R.string.press_again_exit), Toast.LENGTH_SHORT).show()
        } else {
            context.findActivity()?.finish()
        }
    }

    BackHandler(enabled = !childPageVisible) { exitOrAskToExit() }

    // The progress of the edge swipe, 0 when the finger is not down. It drives the preview
    // below and is reset when the gesture ends, whatever the outcome.
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backFromLeftEdge by remember { mutableStateOf(true) }
    PredictiveBackHandler(enabled = state.predictiveBackEnabled && !childPageVisible) { progress ->
        try {
            progress.collect { event ->
                backFromLeftEdge = event.swipeEdge != BackEventCompat.EDGE_RIGHT
                backProgress = event.progress
            }
            // The gesture was let go of, so it commits: the same action the button triggers.
            exitOrAskToExit()
        } catch (cancelled: CancellationException) {
            // The gesture was taken back, so nothing happens.
        } finally {
            backProgress = 0f
        }
    }

    // The shell lifts away from the finger: it slides towards the far edge and shrinks a
    // little, so the gesture shows the app leaving before the app actually leaves.
    val previewProgress by animateFloatAsState(
        targetValue = backProgress,
        animationSpec = abkTween(AbkMotion.Fast, AbkMotion.EaseOut),
        label = "abkBackPreview",
    )

    Box(Modifier.fillMaxSize().background(AbkTheme.colors.background)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val travel = previewProgress * 48.dp.toPx()
                translationX = if (backFromLeftEdge) travel else -travel
                scaleX = 1f - previewProgress * 0.04f
                scaleY = 1f - previewProgress * 0.04f
                alpha = 1f - previewProgress * 0.2f
            },
        ) {
            AbkPageSwitch(
                target = selected,
                modifier = Modifier.fillMaxSize(),
                forward = { from, to -> to.ordinal > from.ordinal },
            ) { destination ->
                when (destination) {
                    AbkDestination.Status -> StatusDestination(
                        vm = vm,
                        state = state,
                        onChildPageVisibleChange = { childPageVisible = it },
                    )
                    AbkDestination.Build -> BuildDestination(
                        vm = vm,
                        state = state,
                        onChildPageVisibleChange = { childPageVisible = it },
                    )
                    AbkDestination.Flash -> FlashDestination(
                        vm = vm,
                        state = state,
                        onChildPageVisibleChange = { childPageVisible = it },
                    )
                    AbkDestination.Modules -> ModulesDestination(
                        vm = vm,
                        state = state,
                        pendingModuleInstallUri = pendingModuleInstallUri,
                        onPendingModuleInstallUriConsumed = onModuleInstallUriConsumed,
                    )
                    AbkDestination.Settings -> SettingsDestination(
                        vm = vm,
                        state = state,
                        onChildPageVisibleChange = { childPageVisible = it },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !childPageVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(abkTween(AbkMotion.Base)) + slideInVertically(abkTween(AbkMotion.Base)) { it },
            exit = fadeOut(abkTween(AbkMotion.Fast)) + slideOutVertically(abkTween(AbkMotion.Fast)) { it },
        ) {
            AbkBottomNavigation(
                items = AbkDestination.entries.map {
                    AbkNavigationItem(id = it.name, label = stringResource(it.labelRes), icon = it.icon)
                },
                selectedId = selected.name,
                onSelect = { id ->
                    AbkDestination.entries.firstOrNull { it.name == id }?.let { selected = it }
                },
            )
        }

        AbkSnackbarHost(
            state = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (childPageVisible) 12.dp else 84.dp),
        )
    }
}
