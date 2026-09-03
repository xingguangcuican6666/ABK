package com.abk.kernel.miuix.ui.screens.runtime

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.miuix.component.KeyEventBlocker
import com.abk.kernel.miuix.ui.screens.flash.common.rememberFlashTerminalLogState
import com.abk.kernel.ui.screens.copyRuntimeModuleUriToCache
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
// ModuleInstallLogScreenMiuix — full-page terminal log for module installation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ModuleInstallLogScreenMiuix(
    params: ModuleInstallParams,
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logState = rememberFlashTerminalLogState()
    val executionStarted = remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // ── Pre-resolve string resources (cannot call stringResource inside LaunchedEffect) ──
    val flashWaitRootShell = stringResource(R.string.runtime_wait_root_shell)
    val flashCommandDoneNoOutput = stringResource(R.string.runtime_module_install_done_no_output)
    val flashCommandFailedNoLog = stringResource(R.string.runtime_module_install_failed_no_log)

    // ── Key event blocker (volume keys) ─────────────────────────────────────
    KeyEventBlocker {
        it.key == Key.VolumeDown || it.key == Key.VolumeUp
    }

    // ── Auto scroll ─────────────────────────────────────────────────────────
    LaunchedEffect(logState.logText) {
        if (logState.logText.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // ── Execution ───────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        if (executionStarted.value) return@LaunchedEffect
        executionStarted.value = true
        logState.setRunning()

        logState.appendLine("$ module install")
        logState.appendLine("source: ${params.uri}")
        logState.appendLine("")
        logState.appendLine(context.getString(R.string.runtime_copying_module))

        val result = withContext(NonCancellable + Dispatchers.IO) {
            var stagedFile: File? = null
            runCatching {
                val uri = Uri.parse(params.uri)
                stagedFile = copyRuntimeModuleUriToCache(context, uri).also {
                    logState.appendLine("file: ${it.absolutePath}")
                }
                logState.appendLine(flashWaitRootShell)
                if (!RootUtils.refreshRootState()) {
                    RootUtils.ShellResult(false, listOf(context.getString(R.string.runtime_manager_inactive)))
                } else {
                    RootUtils.installModule(stagedFile.absolutePath) { line ->
                        logState.appendLine(line)
                    }
                }
            }.getOrElse { e ->
                RootUtils.ShellResult(false, listOf(context.getString(R.string.runtime_module_file_read_failed)))
            }.also {
                stagedFile?.delete()
            }
        }

        // onOutput 回调已在执行期间实时追加所有行，此处不再重复追加 result.output
        if (result.output.isEmpty()) {
            logState.appendLine(
                if (result.success) flashCommandDoneNoOutput
                else flashCommandFailedNoLog
            )
        }

        if (result.success) {
            logState.setSuccess()
            vm.refreshAbkRuntimeStatus()
        } else {
            logState.setFailed(result.output.joinToString("\n"))
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(
                    when {
                        !logState.isSuccess && logState.isCompleted -> R.string.flash_terminal_status_failed
                        logState.isSuccess -> R.string.flash_terminal_status_success
                        else -> R.string.runtime_installing_module
                    }
                ),
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
                            contentDescription = stringResource(R.string.flash_back),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (logState.isCompleted) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { RootUtils.reboot() }
                        }
                    },
                    modifier = Modifier.padding(bottom = 20.dp, end = 20.dp)
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        tint = Color.White,
                        contentDescription = stringResource(R.string.flash_terminal_reboot)
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
                text = logState.logText,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}
