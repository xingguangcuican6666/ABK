package com.abk.kernel.miuix.ui.screens.flash

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.miuix.component.KeyEventBlocker
import com.abk.kernel.miuix.ui.screens.flash.common.FlashTerminalParams
import com.abk.kernel.miuix.ui.screens.flash.common.rememberFlashTerminalLogState
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.RootUtils
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
// FlashTerminalLogScreenMiuix — full-page terminal log for flash operations
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FlashTerminalLogScreenMiuix(
    params: FlashTerminalParams,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logState = rememberFlashTerminalLogState()
    val executionStarted = remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // ── Pre-resolve string resources (cannot call stringResource inside LaunchedEffect) ──
    val flashWaitRootShell = stringResource(R.string.flash_wait_root_shell)
    val flashCommandDoneNoOutput = stringResource(R.string.flash_command_done_no_output)
    val flashCommandFailedNoLog = stringResource(R.string.flash_command_failed_no_log)

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

        logState.appendLine("$ ${params.operationTitle}")
        logState.appendLine("file: ${params.artifactPath}")
        logState.appendLine("")
        logState.appendLine(flashWaitRootShell)

        val result = runCatching {
            withContext(NonCancellable + Dispatchers.IO) {
                val item = DownloadedArtifact(
                    id = 0L,
                    name = params.artifactName,
                    filePath = params.artifactPath,
                    type = ArtifactType.valueOf(params.artifactType),
                    sizeBytes = 0L
                )
                val prepared = DownloadUtils.prepareDownloadedArtifact(
                    context = context,
                    artifact = item,
                    allowHighRiskFallback = params.allowHighRiskFallback
                )
                try {
                    if (prepared.cleanupDir != null) {
                        logState.appendLine("[ABK] 已解包下载包到缓存目录")
                        logState.appendLine("[ABK] Payload: ${prepared.file.absolutePath}")
                        if (prepared.dependencyModules.isNotEmpty()) {
                            logState.appendLine(
                                "[ABK] 附带 Magisk 依赖模块: ${prepared.dependencyModules.joinToString { it.name }}"
                            )
                        }
                        if (prepared.dependencyApps.isNotEmpty()) {
                            logState.appendLine(
                                "[ABK] 附带扩展应用: ${prepared.dependencyApps.joinToString { it.name }}"
                            )
                        }
                    }

                    val flashType = prepared.resolvedType ?: item.type

                    // Install dependency apps and modules for kernel / AK3
                    if (flashType == ArtifactType.KERNEL_IMG || flashType == ArtifactType.ANYKERNEL3) {
                        prepared.dependencyApps.forEach { dependency ->
                            logState.appendLine("[ABK] 先安装依赖扩展应用: ${dependency.name}")
                            val depResult = RootUtils.installApk(
                                context, dependency.absolutePath
                            ) { line -> logState.appendLine(line) }
                            if (!depResult.success) return@withContext depResult
                        }
                        prepared.dependencyModules.forEach { dependency ->
                            logState.appendLine("[ABK] 先安装依赖模块: ${dependency.name}")
                            val depResult = RootUtils.installModule(
                                dependency.absolutePath
                            ) { line -> logState.appendLine(line) }
                            if (!depResult.success) return@withContext depResult
                        }
                    }

                    when (flashType) {
                        ArtifactType.KERNEL_IMG ->
                            RootUtils.flashImage(prepared.file.absolutePath) { line -> logState.appendLine(line) }
                        ArtifactType.ANYKERNEL3 -> {
                            val slotTarget = params.ak3SlotTarget?.let {
                                RootUtils.Ak3SlotTarget.valueOf(it)
                            } ?: RootUtils.Ak3SlotTarget.CURRENT
                            RootUtils.flashAnyKernel3(
                                context, prepared.file.absolutePath, targetSlot = slotTarget
                            ) { line -> logState.appendLine(line) }
                        }
                        ArtifactType.SUSFS_MODULE ->
                            RootUtils.installModule(prepared.file.absolutePath) { line -> logState.appendLine(line) }
                        ArtifactType.KSU_MANAGER ->
                            RootUtils.installApk(context, prepared.file.absolutePath) { line -> logState.appendLine(line) }
                        else ->
                            RootUtils.ShellResult(false, listOf(context.getString(R.string.flash_unsupported_auto_flash)))
                    }
                } finally {
                    prepared.cleanupDir?.deleteRecursively()
                }
            }
        }.getOrElse { error ->
            RootUtils.ShellResult(false, listOf(error.message ?: error::class.java.simpleName))
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
                        else -> R.string.flash_terminal_status_flashing
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
