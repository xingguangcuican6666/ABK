package com.abk.kernel.miuix.ui.screens.runtime

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.miuix.component.KeyEventBlocker
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// ─────────────────────────────────────────────────────────────────────────────
// ModuleActionTerminalScreenMiuix — full-page terminal log for module action execution
//
// Execution itself belongs to RuntimeCoordinator.runRuntimeModuleAction: it owns
// backend selection, the in-flight marker other screens gate on, output streaming
// and error reporting. This screen only starts it and renders the shared output,
// the same split the M3 dialog uses.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ModuleActionTerminalScreenMiuix(
    params: ModuleActionTerminalParams,
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val runningId = state.abkRuntimeModuleActionId
    var launched by remember { mutableStateOf(false) }
    var observedRunning by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf(false) }

    val fabVisible by remember {
        var previousScroll = 0
        var scrollDelta = 0f
        var visible = true
        derivedStateOf {
            val currentScroll = scrollState.value
            val delta = (currentScroll - previousScroll).toFloat()
            scrollDelta = (scrollDelta + delta).coerceIn(-FAB_SCROLL_THRESHOLD_PX, FAB_SCROLL_THRESHOLD_PX)
            previousScroll = currentScroll
            if (currentScroll <= 0) {
                visible = scrollState.maxValue <= 0
                scrollDelta = 0f
            } else if (!visible && scrollDelta >= FAB_SCROLL_THRESHOLD_PX) {
                visible = true
                scrollDelta = 0f
            } else if (visible && scrollDelta <= -FAB_SCROLL_THRESHOLD_PX) {
                visible = false
                scrollDelta = 0f
            }
            visible
        }
    }
    val closeOffset by animateDpAsState(
        targetValue = if (fabVisible) 0.dp else 180.dp,
        animationSpec = tween(durationMillis = 350),
        label = "module-action-fab-offset"
    )

    val waitRootShell = stringResource(R.string.runtime_wait_root_shell)
    val operationIncomplete = stringResource(R.string.settings_operation_incomplete)
    val statusFailed = stringResource(R.string.flash_terminal_status_failed)
    val statusSuccess = stringResource(R.string.flash_terminal_status_success)
    val back = stringResource(R.string.flash_back)
    val close = stringResource(R.string.close)

    // ── Key event blocker (volume keys) ─────────────────────────────────────
    KeyEventBlocker {
        it.key == Key.VolumeDown || it.key == Key.VolumeUp
    }

    // ── Start the action through the shared coordinator ──────────────────────
    LaunchedEffect(Unit) {
        if (launched) return@LaunchedEffect
        launched = true
        val inFlight = vm.uiState.value.abkRuntimeModuleActionId
        if (inFlight == params.moduleId) {
            // Already running from an earlier visit to this page - attach to it
            // rather than dispatching the script a second time.
            observedRunning = true
            return@LaunchedEffect
        }
        if (inFlight != null) {
            // Only one action runs at a time and the coordinator would refuse.
            // M3 drops the click in this case, so nothing runs here either; say
            // so rather than leaving the page waiting on something that will
            // never start.
            refused = true
            return@LaunchedEffect
        }
        vm.runRuntimeModuleAction(params.moduleId)
    }

    // ── Track completion off the shared in-flight marker ─────────────────────
    LaunchedEffect(runningId) {
        if (runningId == params.moduleId) {
            observedRunning = true
        } else if (observedRunning && !finished) {
            finished = true
            failed = state.abkRuntimeError != null
            if (!failed) vm.refreshAbkRuntimeStatus()
        }
    }

    // ── Release the shared output buffer once the run is over ────────────────
    // Mirrors what the M3 dialog does on dismiss. Never while running: the run
    // owns the buffer and is still appending to it.
    DisposableEffect(Unit) {
        onDispose {
            if (vm.uiState.value.abkRuntimeModuleActionId == null) {
                vm.dismissRuntimeModuleActionOutput()
            }
        }
    }

    // The output buffer is shared, so ignore it until our own run owns it -
    // otherwise a queued visit would briefly render another module's log.
    val output = if (observedRunning) state.abkRuntimeModuleActionOutput else emptyList()
    val logText = (
        listOf("\$ module action: ${params.moduleName}", "") +
            when {
                refused -> listOf(operationIncomplete)
                output.isEmpty() -> listOf(waitRootShell)
                else -> output
            }
        ).joinToString("\n")

    // ── Auto scroll ─────────────────────────────────────────────────────────
    LaunchedEffect(logText) {
        if (logText.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = when {
                    finished && failed -> statusFailed
                    finished -> statusSuccess
                    else -> params.moduleName
                },
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 16.dp),
                        onClick = onBack
                    ) {
                        val layoutDirection = LocalLayoutDirection.current
                        Icon(
                            modifier = Modifier.graphicsLayer {
                                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                            },
                            imageVector = MiuixIcons.Back,
                            contentDescription = back,
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (finished || refused) {
                FloatingActionButton(
                    onClick = onBack,
                    modifier = Modifier
                        .offset { IntOffset(0, closeOffset.roundToPx()) }
                        .padding(bottom = 20.dp, end = 20.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = close
                    )
                }
            }
        },
        popupHost = { }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .scrollEndHaptic()
                .verticalScroll(scrollState)
        ) {
            Text(
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                text = logText,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}

private const val FAB_SCROLL_THRESHOLD_PX = 100f
