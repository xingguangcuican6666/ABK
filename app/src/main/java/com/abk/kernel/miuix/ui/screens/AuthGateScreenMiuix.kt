package com.abk.kernel.miuix.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.GitHubRepo
import com.abk.kernel.miuix.ui.screens.flash.common.MiuixConfirmDialog
import com.abk.kernel.viewmodel.AuthStep
import com.abk.kernel.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val OOBE_SKIP_LOADING_DELAY_MS = 320L
private const val OOBE_SKIP_EXIT_DELAY_MS = 280L

/**
 * Buttons that sit side by side inside a card need less horizontal padding than
 * [ButtonDefaults.InsideMargin] (16.dp) so their icon + label still fits on one
 * line at the narrowest supported width.
 */
private val COMPACT_BUTTON_MARGIN = PaddingValues(horizontal = 10.dp, vertical = 11.dp)

@Composable
fun OobeScreenMiuix(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var skipInFlight by remember { mutableStateOf(false) }

    fun requestSkip() {
        if (skipInFlight) return
        skipInFlight = true
        scope.launch {
            delay(OOBE_SKIP_LOADING_DELAY_MS)
            delay(OOBE_SKIP_EXIT_DELAY_MS)
            vm.skipOobe()
        }
    }

    /**
     * Confirming the theme step. A style that differs from the current one makes
     * MainActivity swap the theme wrapper, which tears down this whole tree, so the
     * exit animation is deliberately skipped: it would spend its delay sliding the
     * overlay off the *outgoing* theme's main UI, and that reveal is the visual
     * residue users see before the new theme appears. Picking the current style
     * changes no wrapper, so that keeps the normal animated exit.
     */
    fun confirmUiStyle(selected: String) {
        if (skipInFlight) return
        if (selected == state.uiStyle) {
            requestSkip()
            return
        }
        skipInFlight = true
        vm.completeOobeWithUiStyle(selected)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when (state.authStep) {
                AuthStep.LOGIN -> LoginScreenMiuix(
                    isLoading = state.isLoading,
                    userCode = state.userCode,
                    verificationUri = state.verificationUri,
                    isPolling = state.isPollingToken,
                    error = state.error,
                    onLogin = { if (!skipInFlight) vm.startDeviceFlow() },
                    onSkip = ::requestSkip,
                    skipInFlight = skipInFlight,
                    onClearError = { vm.clearError() }
                )
                AuthStep.THEME_SELECT -> ThemeSelectScreenMiuix(
                    currentStyle = state.uiStyle,
                    onConfirm = ::confirmUiStyle
                )
                AuthStep.FORK_CHECK -> ForkCheckScreenMiuix(
                    isLoading = state.isLoading,
                    forkRepo = state.forkRepo,
                    behindBy = state.behindBy,
                    showSyncDialog = false,
                    error = state.error,
                    onFork = { if (!skipInFlight) vm.forkRepo() },
                    onSync = { if (!skipInFlight) vm.syncFork() },
                    onSkip = ::requestSkip,
                    showSkipAction = true,
                    skipInFlight = skipInFlight,
                    onClearError = { vm.clearError() }
                )
                else -> {}
            }
        }

        AnimatedVisibility(
            visible = skipInFlight && state.authStep != AuthStep.FORK_CHECK,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MiuixTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.loading),
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ── Login ─────────────────────────────────────────────────────────────────────

@Composable
private fun LoginScreenMiuix(
    isLoading: Boolean,
    userCode: String?,
    verificationUri: String?,
    isPolling: Boolean,
    error: String?,
    onLogin: () -> Unit,
    onSkip: () -> Unit,
    skipInFlight: Boolean,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    var showConsentDialog by remember { mutableStateOf(false) }

    if (showConsentDialog) {
        MiuixConfirmDialog(
            title = stringResource(R.string.github_auth_title),
            message = stringResource(R.string.github_auth_desc),
            confirmBtnText = stringResource(R.string.confirm),
            confirmBtnColor = null,
            onConfirm = {
                showConsentDialog = false
                onLogin()
            },
            onDismiss = { showConsentDialog = false }
        )
    }

    AuthShellMiuix {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.login_title),
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.login_desc),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (userCode != null) {
            DeviceCodeCardMiuix(
                code = userCode,
                verificationUri = verificationUri ?: "https://github.com/login/device",
                isPolling = isPolling,
                context = context
            )
        }

        if (error != null) {
            ErrorCardMiuix(error = error, onClearError = onClearError)
        }

        if (userCode == null) {
            Button(
                onClick = { showConsentDialog = true },
                enabled = !isLoading && !skipInFlight,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Default.Code, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.login_github),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }

        Button(
            onClick = onSkip,
            enabled = !skipInFlight,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.secondaryVariant,
                contentColor = MiuixTheme.colorScheme.onSecondaryVariant
            )
        ) {
            Text(
                text = stringResource(R.string.oobe_skip_for_now),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DeviceCodeCardMiuix(
    code: String,
    verificationUri: String,
    isPolling: Boolean,
    context: Context
) {
    var copied by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.auth_code_title),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.auth_code_desc),
                style = MiuixTheme.textStyles.body2,
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = code,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    style = MiuixTheme.textStyles.title2,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("user_code", code))
                        copied = true
                    },
                    modifier = Modifier.weight(1f),
                    insideMargin = COMPACT_BUTTON_MARGIN
                ) {
                    Icon(
                        if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (copied) stringResource(R.string.copied) else stringResource(R.string.copy),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(verificationUri)))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    insideMargin = COMPACT_BUTTON_MARGIN
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.open_browser),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
            if (isPolling) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MiuixTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.waiting_auth),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
        }
    }
}

// ── Theme Select ──────────────────────────────────────────────────────────────

@Composable
private fun ThemeSelectScreenMiuix(
    currentStyle: String,
    onConfirm: (String) -> Unit
) {
    var selected by remember { mutableStateOf(currentStyle) }

    AuthShellMiuix {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.oobe_theme_title),
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            onClick = { selected = "material" },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = if (selected == "material") MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainer,
                contentColor = if (selected == "material") MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == "material",
                    onClick = { selected = "material" }
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.oobe_theme_m3e),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected == "material") MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface
                )
            }
        }

        Card(
            onClick = { selected = "miuix" },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = if (selected == "miuix") MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainer,
                contentColor = if (selected == "miuix") MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == "miuix",
                    onClick = { selected = "miuix" }
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.oobe_theme_miuix),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected == "miuix") MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface
                )
            }
        }

        Button(
            onClick = { onConfirm(selected) },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                text = stringResource(R.string.oobe_theme_confirm),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── Fork Check ────────────────────────────────────────────────────────────────

@Composable
private fun StatusBadgeMiuix(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    color: Color = MiuixTheme.colorScheme.primary,
    scale: Float = 1f,
    heroStyle: Boolean = false
) {
    val horizontalPad = if (heroStyle) 10.dp else 8.dp * scale
    val verticalPad = if (heroStyle) 5.dp else 1.dp * scale
    val iconSize = if (heroStyle) 14.dp else 14.dp * scale
    val cornerSize = if (heroStyle) 8.dp else 8.dp * scale
    val textFontSize = if (heroStyle) 14.sp else MiuixTheme.textStyles.body2.fontSize * scale
    val textFontWeight = if (heroStyle) FontWeight.Bold else FontWeight.Normal
    val spacing = if (heroStyle) 4.dp else 4.dp * scale

    val baseModifier = modifier
        .wrapContentWidth()
    val bgModifier = if (heroStyle) {
        baseModifier
            .border(BorderStroke(1.dp, color.copy(alpha = 0.35f)), RoundedCornerShape(cornerSize))
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(cornerSize))
    } else {
        baseModifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(cornerSize))
    }

    Row(
        modifier = bgModifier.padding(start = horizontalPad, end = horizontalPad, top = verticalPad, bottom = verticalPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(iconSize)
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2.copy(fontSize = textFontSize),
            fontWeight = textFontWeight,
            color = color
        )
    }
}

@Composable
private fun ForkCheckScreenMiuix(
    isLoading: Boolean,
    forkRepo: GitHubRepo?,
    behindBy: Int,
    showSyncDialog: Boolean,
    error: String?,
    onFork: () -> Unit,
    onSync: () -> Unit,
    onSkip: () -> Unit,
    showSkipAction: Boolean = false,
    skipInFlight: Boolean = false,
    onClearError: () -> Unit
) {
    if (showSyncDialog) {
        MiuixConfirmDialog(
            title = stringResource(R.string.sync_title),
            message = "${stringResource(R.string.sync_desc)}\n\n${stringResource(R.string.sync_behind_commits, behindBy)}",
            confirmBtnText = stringResource(R.string.sync_action),
            confirmBtnColor = null,
            onConfirm = onSync,
            onDismiss = onSkip
        )
    }

    AuthShellMiuix {
        if (isLoading) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.fork_checking_title),
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.fork_checking_desc),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!skipInFlight) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = null,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = MiuixTheme.colorScheme.primary,
                                backgroundColor = MiuixTheme.colorScheme.surface
                            )
                        )
                    }
                }
            }
        } else if (forkRepo == null) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ForkRight,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.fork_title),
                                style = MiuixTheme.textStyles.subtitle,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = stringResource(R.string.fork_desc),
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Normal,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    StatusBadgeMiuix(
                        modifier = Modifier.padding(top = 13.dp),
                        heroStyle = true,
                        label = stringResource(R.string.fork_create_badge),
                        icon = Icons.AutoMirrored.Filled.CallSplit,
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            }
            Button(
                onClick = onFork,
                enabled = !skipInFlight,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Icon(Icons.Default.ForkRight, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.fork_action),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = if (behindBy > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (behindBy > 0) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.fork_ready_title),
                                style = MiuixTheme.textStyles.subtitle,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = if (behindBy > 0) {
                                    stringResource(R.string.fork_ready_behind, behindBy)
                                } else {
                                    stringResource(R.string.fork_ready_ok)
                                },
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Normal,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    StatusBadgeMiuix(
                        label = if (behindBy > 0) {
                            stringResource(R.string.fork_sync_recommended)
                        } else {
                            stringResource(R.string.fork_enter_main)
                        },
                        icon = if (behindBy > 0) Icons.Default.Warning else Icons.Default.Verified,
                        color = if (behindBy > 0) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
                    )
                }
            }
        }

        if (error != null) {
            ErrorCardMiuix(error = error, onClearError = onClearError)
        }

        if (showSkipAction) {
            Button(
                onClick = onSkip,
                enabled = !skipInFlight,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                if (skipInFlight) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MiuixTheme.colorScheme.primary,
                        trackColor = MiuixTheme.colorScheme.secondaryVariant
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = stringResource(R.string.oobe_skip_for_now),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── Error Card ────────────────────────────────────────────────────────────────

@Composable
private fun ErrorCardMiuix(error: String, onClearError: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.error
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = error,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.body2
            )
            IconButton(onClick = onClearError) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.close_error),
                    tint = MiuixTheme.colorScheme.error
                )
            }
        }
    }
}

// ── Auth Shell ────────────────────────────────────────────────────────────────

@Composable
private fun AuthShellMiuix(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    .add(WindowInsets.displayCutout)
            )
            .windowInsetsPadding(
                WindowInsets.statusBars.only(WindowInsetsSides.Top)
                    .add(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}
