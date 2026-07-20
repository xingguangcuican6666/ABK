package com.abk.kernel.miuix.ui.screens.flash.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.abk.kernel.R
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.miuix.ui.screens.flash.MiuixTerminalDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.TextUnit

// ─────────────────────────────────────────────────────────────────────────────
// Constants — Font sizes
// ─────────────────────────────────────────────────────────────────────────────

val FlashChipFontSize: TextUnit = 11.sp
val FlashCompactButtonFontSize: TextUnit = 12.sp
val FlashSmallFontSize: TextUnit = 10.sp

// ─────────────────────────────────────────────────────────────────────────────
// Constants — Icon sizes
// ─────────────────────────────────────────────────────────────────────────────

val FlashButtonIconSize: Dp = 15.dp
val FlashStatusIconSize: Dp = 16.dp
val FlashMediumIconSize: Dp = 18.dp
val FlashCardHeaderIconSize: Dp = 20.dp
val FlashRunStatusIconSize: Dp = 22.dp

// ─────────────────────────────────────────────────────────────────────────────
// Constants — Shapes
// ─────────────────────────────────────────────────────────────────────────────

val FlashTagChipShape = RoundedCornerShape(5.dp)
val FlashPillChipShape = RoundedCornerShape(50)
val FlashTerminalBgShape = RoundedCornerShape(8.dp)

// ─────────────────────────────────────────────────────────────────────────────
// Constants — Alpha
// ─────────────────────────────────────────────────────────────────────────────

const val FlashChipBgAlpha = 0.12f
const val FlashPillChipBgAlpha = 0.14f

// ─────────────────────────────────────────────────────────────────────────────
// Constants — Progress indicator colors
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Pre-configured progress-bar colors matching the MIUIX theme. Use with
 * [LinearProgressIndicator] directly, or prefer [MiuixThemedLinearProgress].
 */
@Composable
fun flashProgressColors() = ProgressIndicatorDefaults.progressIndicatorColors(
    foregroundColor = MiuixTheme.colorScheme.primary,
    backgroundColor = MiuixTheme.colorScheme.surface
)

// ─────────────────────────────────────────────────────────────────────────────
// A1. Themed LinearProgressIndicator
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MIUIX-themed [LinearProgressIndicator] using the standard primary/surface
 * color pair. Pass `progress = null` for an indeterminate indicator.
 */
@Composable
fun MiuixThemedLinearProgress(
    progress: Float?,
    modifier: Modifier = Modifier
) {
    LinearProgressIndicator(
        modifier = modifier,
        progress = progress,
        colors = flashProgressColors()
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// A2. Generic confirm dialog (replaces 3 near-identical dialog skeletons)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MIUIX-styled confirmation dialog backed by [WindowDialog].
 *
 * @param confirmBtnColor If non-null, the confirm button uses `textColor`;
 *   if null, the confirm button uses the primary text-button style.
 */
@Composable
fun MiuixConfirmDialog(
    title: String,
    message: String,
    confirmBtnText: String,
    confirmBtnColor: Color? = MiuixTheme.colorScheme.error,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                if (confirmBtnColor != null) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = confirmBtnText,
                        colors = ButtonDefaults.textButtonColors(
                            textColor = confirmBtnColor
                        ),
                        onClick = onConfirm
                    )
                } else {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = confirmBtnText,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = onConfirm
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// A3. Terminal dialog driven by FlashTerminalState
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bridges [FlashTerminalState] to [MiuixTerminalDialog].
 *
 * The reboot button is shown only when the operation succeeded **and**
 * either [canReboot] is `true` or the state's [FlashTerminalState.canReboot]
 * flow is `true`.
 */
@Composable
fun TerminalDialogFromState(
    state: FlashTerminalState,
    canReboot: Boolean = false,
    onReboot: (() -> Unit)? = null
) {
    val showDialog by state.showDialog.collectAsState()
    val title by state.title.collectAsState()
    val isRunning by state.isRunning.collectAsState()
    val success by state.success.collectAsState()
    val log by state.log.collectAsState()
    val stateCanReboot by state.canReboot.collectAsState()

    if (showDialog) {
        MiuixTerminalDialog(
            title = title,
            running = isRunning,
            success = success,
            onClose = { state.dismiss() },
            onReboot = if (success == true && (canReboot || stateCanReboot)) {
                onReboot
            } else {
                null
            },
            terminalLog = log
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// B1. Clipboard helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Copy a downloaded artifact's file path to the system clipboard and show
 * a toast confirmation.
 */
fun copyArtifactPath(
    context: Context,
    artifact: DownloadedArtifact,
    onFeedback: (String) -> Unit
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(artifact.name, artifact.filePath))
    onFeedback(context.getString(R.string.flash_copy_path_done))
}
