@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.data.model.GitHubRepo
import com.abk.kernel.data.model.GitHubUser
import com.abk.kernel.ui.theme.LocalUiSurfaceAlpha
import com.abk.kernel.viewmodel.AuthStep
import com.abk.kernel.viewmodel.MainViewModel
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOTAL_OOBE_STEPS = 4

private const val OOBE_SKIP_LOADING_DELAY_MS = 320L
private const val OOBE_SKIP_EXIT_DELAY_MS = 280L
private const val OOBE_SKIP_BACK_VISUAL_EXPONENT = 1.8f
private const val OOBE_SKIP_BACK_SCALE_DELTA = 0.09f
private val OOBE_SKIP_MAX_CORNER = 32.dp

/** Progress index (0-based) for the top-bar segments, matching the 4-step wizard. */
private fun AuthStep.stepIndex(): Int = when (this) {
    AuthStep.INTRO -> 0
    AuthStep.LOGIN -> 1
    AuthStep.FORK_CHECK -> 2
    AuthStep.FINISH -> 3
}

@Composable
fun OobeScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var skipInFlight by remember { mutableStateOf(false) }
    var skipExitStarted by remember { mutableStateOf(false) }
    val motionScheme = MaterialTheme.motionScheme
    val animatedSkipExitProgress by animateFloatAsState(
        targetValue = if (skipExitStarted) 1f else 0f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "oobe-skip-exit-progress"
    )
    val visualSkipExitProgress = animatedSkipExitProgress
        .coerceIn(0f, 1f)
        .pow(OOBE_SKIP_BACK_VISUAL_EXPONENT)
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

    // Hardware/gesture back mirrors the top-bar back button. INTRO is the first
    // screen and falls through to the system (matches the prototype).
    BackHandler(enabled = state.authStep != AuthStep.INTRO && !skipInFlight) {
        vm.oobeBack()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val exitWidthPx = with(density) { maxWidth.toPx() }
        val exitCorner = with(density) {
            (OOBE_SKIP_MAX_CORNER.toPx() * visualSkipExitProgress).toDp()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = exitWidthPx * animatedSkipExitProgress
                    scaleX = 1f - OOBE_SKIP_BACK_SCALE_DELTA * visualSkipExitProgress
                    scaleY = 1f - OOBE_SKIP_BACK_SCALE_DELTA * visualSkipExitProgress
                    alpha = 1f - 0.08f * visualSkipExitProgress
                    shape = RoundedCornerShape(exitCorner)
                    clip = visualSkipExitProgress > 0.01f
                }
        ) {
            val onBack = { if (!skipInFlight) vm.oobeBack() }
            when (state.authStep) {
                AuthStep.INTRO -> OobeWelcomeScreen(
                    skipping = skipInFlight,
                    onStart = {
                        if (!skipInFlight) {
                            if (state.isLoggedIn) vm.openBuildOobe() else vm.continueOobeToLogin()
                        }
                    },
                    onSkip = ::requestSkip
                )
                AuthStep.LOGIN -> OobeLoginScreen(
                    isLoading = state.isLoading,
                    userCode = state.userCode,
                    verificationUri = state.verificationUri,
                    isPolling = state.isPollingToken,
                    error = state.error,
                    skipInFlight = skipInFlight,
                    onBack = onBack,
                    onLogin = { if (!skipInFlight) vm.startDeviceFlow() },
                    onSkip = ::requestSkip,
                    onClearError = { vm.clearError() }
                )
                AuthStep.FORK_CHECK -> OobeRepositoryScreen(
                    isLoading = state.isLoading,
                    forkRepo = state.forkRepo,
                    behindBy = state.behindBy,
                    error = state.error,
                    skipInFlight = skipInFlight,
                    onBack = onBack,
                    onFork = { if (!skipInFlight) vm.forkRepo() },
                    onSync = { if (!skipInFlight) vm.syncFork() },
                    onContinue = { if (!skipInFlight) vm.advanceOobeToFinish() },
                    onSkip = ::requestSkip,
                    onClearError = { vm.clearError() }
                )
                AuthStep.FINISH -> OobeFinishScreen(
                    user = state.user,
                    forkRepo = state.forkRepo,
                    behindBy = state.behindBy,
                    skipInFlight = skipInFlight,
                    onBack = onBack,
                    onFinish = { if (!skipInFlight) vm.finishOobe() }
                )
            }
        }

        if (skipInFlight) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LoadingIndicator(Modifier.size(28.dp))
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ── Chrome (shell / top progress bar / footer) ─────────────────────────────────

@Composable
private fun OobeScaffold(
    step: AuthStep,
    showBack: Boolean,
    onBack: () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalUiSurfaceAlpha provides 1f) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = { OobeTopBar(step.stepIndex(), showBack, onBack) },
            bottomBar = footer,
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun OobeTopBar(stepIndex: Int, showBack: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.oobe_back))
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(TOTAL_OOBE_STEPS) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i <= stepIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun OobeFooter(
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    primaryLoading: Boolean = false,
    primaryIcon: ImageVector? = null,
    secondary: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled && !primaryLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                if (primaryLoading) {
                    LoadingIndicator(Modifier.size(24.dp))
                } else {
                    if (primaryIcon != null) {
                        Icon(primaryIcon, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(primaryLabel, fontWeight = FontWeight.SemiBold)
                }
            }
            secondary?.invoke()
        }
    }
}

// ── Shared pieces ──────────────────────────────────────────────────────────────

@Composable
private fun ScreenHeading(eyebrow: String?, title: String, desc: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (eyebrow != null) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            desc,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OobeBrandMark(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        val unit = size * 0.12f
        val gap = size * 0.09f
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            // Three staggered rows of blocks forming a compact "ABK" glyph.
            val widths = listOf(
                listOf(unit * 2.6f, unit),
                listOf(unit, unit * 2.6f),
                listOf(unit * 1.6f, unit, unit)
            )
            widths.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { w ->
                        Box(
                            modifier = Modifier
                                .width(w)
                                .height(unit)
                                .clip(RoundedCornerShape(unit * 0.35f))
                                .background(MaterialTheme.colorScheme.onPrimary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OobeErrorCard(error: String, onClearError: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(
                error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClearError) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.close_error),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun OobeInfoRow(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OobeStepRow(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ── Step 0 · Welcome ───────────────────────────────────────────────────────────

@Composable
private fun OobeWelcomeScreen(
    skipping: Boolean,
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    OobeScaffold(
        step = AuthStep.INTRO,
        showBack = false,
        onBack = {},
        footer = {
            OobeFooter(
                primaryLabel = stringResource(R.string.oobe_start_setup),
                onPrimary = onStart,
                primaryEnabled = !skipping,
                primaryIcon = Icons.Default.RocketLaunch,
                secondary = {
                    TextButton(onClick = onSkip, enabled = !skipping) {
                        Text(stringResource(R.string.oobe_skip_for_now))
                    }
                }
            )
        }
    ) {
        Spacer(Modifier.height(12.dp))
        OobeWelcomeGraphic()
        Spacer(Modifier.height(4.dp))
        ScreenHeading(
            eyebrow = null,
            title = stringResource(R.string.oobe_welcome_title),
            desc = stringResource(R.string.oobe_welcome_desc)
        )
    }
}

@Composable
private fun OobeWelcomeGraphic() {
    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(220.dp).clip(CircleShape).background(cs.primaryContainer.copy(alpha = 0.22f))
        )
        Box(
            Modifier.size(160.dp).clip(CircleShape).background(cs.primaryContainer.copy(alpha = 0.45f))
        )
        // Phone silhouette holding the brand mark.
        Box(
            modifier = Modifier
                .size(width = 104.dp, height = 140.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(cs.surface)
                .border(1.dp, cs.outlineVariant, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) {
            OobeBrandMark(size = 58.dp)
        }
        // Success badge (top-right) and account badge (bottom-left).
        WelcomeBadge(Icons.Default.CheckCircle, cs.primary, cs.onPrimary, Alignment.TopEnd)
        WelcomeBadge(Icons.Default.AccountCircle, cs.secondaryContainer, cs.onSecondaryContainer, Alignment.BottomStart)
    }
}

@Composable
private fun BoxScope.WelcomeBadge(icon: ImageVector, bg: Color, fg: Color, alignment: Alignment) {
    Box(
        modifier = Modifier
            .align(alignment)
            .padding(14.dp)
            .size(44.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp))
    }
}

// ── Step 1 · Login ─────────────────────────────────────────────────────────────

@Composable
private fun OobeLoginScreen(
    isLoading: Boolean,
    userCode: String?,
    verificationUri: String?,
    isPolling: Boolean,
    error: String?,
    skipInFlight: Boolean,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onSkip: () -> Unit,
    onClearError: () -> Unit,
) {
    val context = LocalContext.current
    var showConsentDialog by remember { mutableStateOf(false) }
    val hasCode = userCode != null
    val uri = verificationUri ?: "https://github.com/login/device"

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            icon = { Icon(Icons.Default.VerifiedUser, null) },
            title = { Text(stringResource(R.string.github_auth_title)) },
            text = { Text(stringResource(R.string.github_auth_desc)) },
            confirmButton = {
                Button(onClick = {
                    showConsentDialog = false
                    onLogin()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    OobeScaffold(
        step = AuthStep.LOGIN,
        showBack = true,
        onBack = onBack,
        footer = {
            val skipSlot: @Composable () -> Unit = {
                TextButton(onClick = onSkip, enabled = !skipInFlight) {
                    Text(stringResource(R.string.oobe_skip_for_now))
                }
            }
            if (hasCode) {
                OobeFooter(
                    primaryLabel = stringResource(R.string.oobe_open_github),
                    onPrimary = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
                    },
                    primaryEnabled = !skipInFlight,
                    primaryIcon = Icons.Default.OpenInBrowser,
                    secondary = skipSlot
                )
            } else {
                OobeFooter(
                    primaryLabel = stringResource(R.string.oobe_generate_code),
                    onPrimary = { if (!skipInFlight) showConsentDialog = true },
                    primaryEnabled = !isLoading && !skipInFlight,
                    primaryLoading = isLoading,
                    primaryIcon = Icons.Default.Code,
                    secondary = skipSlot
                )
            }
        }
    ) {
        ScreenHeading(
            eyebrow = stringResource(R.string.oobe_login_eyebrow),
            title = stringResource(R.string.login_title),
            desc = stringResource(R.string.login_desc)
        )
        if (hasCode) {
            OobeDeviceCodeCard(code = userCode!!, isPolling = isPolling, context = context)
            OobeStepRow(1, stringResource(R.string.oobe_code_step1))
            OobeStepRow(2, stringResource(R.string.oobe_code_step2))
        } else {
            OobeGitHubShield()
            OobeInfoRow(
                Icons.Default.VerifiedUser,
                stringResource(R.string.oobe_login_info1_title),
                stringResource(R.string.oobe_login_info1_desc)
            )
            OobeInfoRow(
                Icons.Default.Lock,
                stringResource(R.string.oobe_login_info2_title),
                stringResource(R.string.oobe_login_info2_desc)
            )
        }
        if (error != null) OobeErrorCard(error, onClearError)
    }
}

@Composable
private fun OobeGitHubShield() {
    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(cs.primaryContainer.copy(alpha = 0.30f)))
        Box(
            modifier = Modifier.size(84.dp).clip(CircleShape).background(cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.VerifiedUser,
                null,
                tint = cs.onPrimaryContainer,
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

@Composable
private fun OobeDeviceCodeCard(code: String, isPolling: Boolean, context: Context) {
    var copied by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.oobe_code_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                code,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("user_code", code))
                copied = true
            }) {
                Icon(
                    if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (copied) stringResource(R.string.copied) else stringResource(R.string.copy))
            }
            Text(
                stringResource(R.string.oobe_code_expiry),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isPolling) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LoadingIndicator(Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.waiting_auth),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Step 2 · Repository ────────────────────────────────────────────────────────

private enum class RepoPhase { CHECKING, NEEDS_FORK, FORKING, READY, SYNCING }
private enum class RepoAction { NONE, FORK, SYNC }

@Composable
private fun OobeRepositoryScreen(
    isLoading: Boolean,
    forkRepo: GitHubRepo?,
    behindBy: Int,
    error: String?,
    skipInFlight: Boolean,
    onBack: () -> Unit,
    onFork: () -> Unit,
    onSync: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onClearError: () -> Unit,
) {
    var pendingAction by remember { mutableStateOf(RepoAction.NONE) }
    var justForked by remember { mutableStateOf(false) }
    // A single isLoading flag backs check/fork/sync; clear the intent when it settles.
    LaunchedEffect(isLoading) { if (!isLoading) pendingAction = RepoAction.NONE }

    val hasFork = forkRepo != null
    val phase = when {
        isLoading && pendingAction == RepoAction.FORK -> RepoPhase.FORKING
        isLoading && pendingAction == RepoAction.SYNC -> RepoPhase.SYNCING
        isLoading -> RepoPhase.CHECKING
        hasFork -> RepoPhase.READY
        else -> RepoPhase.NEEDS_FORK
    }

    OobeScaffold(
        step = AuthStep.FORK_CHECK,
        showBack = true,
        onBack = onBack,
        footer = {
            val skipSlot: @Composable () -> Unit = {
                TextButton(onClick = onSkip, enabled = !skipInFlight) {
                    Text(stringResource(R.string.oobe_skip_for_now))
                }
            }
            when (phase) {
                RepoPhase.CHECKING -> OobeFooter(
                    primaryLabel = stringResource(R.string.oobe_repo_checking_now),
                    onPrimary = {}, primaryEnabled = false, primaryLoading = true, secondary = skipSlot
                )
                RepoPhase.NEEDS_FORK -> OobeFooter(
                    primaryLabel = stringResource(R.string.oobe_repo_create_fork),
                    onPrimary = { if (!skipInFlight) { pendingAction = RepoAction.FORK; justForked = true; onFork() } },
                    primaryEnabled = !skipInFlight,
                    primaryIcon = Icons.Default.CallSplit,
                    secondary = skipSlot
                )
                RepoPhase.FORKING -> OobeFooter(
                    primaryLabel = stringResource(R.string.oobe_repo_creating_now),
                    onPrimary = {}, primaryEnabled = false, primaryLoading = true, secondary = skipSlot
                )
                RepoPhase.SYNCING -> OobeFooter(
                    primaryLabel = stringResource(R.string.oobe_repo_syncing_now),
                    onPrimary = {}, primaryEnabled = false, primaryLoading = true, secondary = skipSlot
                )
                RepoPhase.READY -> OobeFooter(
                    primaryLabel = stringResource(R.string.oobe_continue),
                    onPrimary = onContinue,
                    primaryEnabled = !skipInFlight,
                    secondary = {
                        if (behindBy > 0) {
                            TextButton(
                                onClick = { if (!skipInFlight) { pendingAction = RepoAction.SYNC; onSync() } },
                                enabled = !skipInFlight
                            ) { Text(stringResource(R.string.oobe_sync_now)) }
                        } else {
                            skipSlot()
                        }
                    }
                )
            }
        }
    ) {
        ScreenHeading(
            eyebrow = stringResource(R.string.oobe_repo_eyebrow),
            title = stringResource(R.string.fork_title),
            desc = stringResource(R.string.fork_desc)
        )
        OobeRepoCard(forkRepo)
        OobeRepoTimeline(phase = phase, justForked = justForked, behindBy = behindBy)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Info,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                stringResource(R.string.oobe_repo_permission_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (error != null) OobeErrorCard(error, onClearError)
    }
}

@Composable
private fun OobeRepoCard(forkRepo: GitHubRepo?) {
    val cs = MaterialTheme.colorScheme
    val name = forkRepo?.fullName ?: "${BuildConfig.SOURCE_REPO_OWNER}/${BuildConfig.SOURCE_REPO_NAME}"
    val isPrivate = forkRepo?.private == true
    val branch = forkRepo?.defaultBranch ?: "main"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CallSplit, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(if (isPrivate) R.string.oobe_repo_private else R.string.oobe_repo_public),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = cs.outlineVariant)
            Text(
                "${stringResource(R.string.oobe_repo_default_branch, branch)}  ·  ${stringResource(R.string.oobe_repo_template)}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

private enum class NodeStatus { PENDING, RUNNING, DONE }

@Composable
private fun OobeRepoTimeline(phase: RepoPhase, justForked: Boolean, behindBy: Int) {
    val node1Running = phase == RepoPhase.CHECKING
    val node1FoundExisting = !node1Running && !justForked &&
        (phase == RepoPhase.READY || phase == RepoPhase.SYNCING || phase == RepoPhase.FORKING)
    val node1Status = if (node1Running) NodeStatus.RUNNING else NodeStatus.DONE
    val node1Sub = when {
        node1Running -> stringResource(R.string.oobe_repo_timeline_check_running)
        node1FoundExisting -> stringResource(R.string.oobe_repo_timeline_check_found)
        phase == RepoPhase.CHECKING -> stringResource(R.string.oobe_repo_timeline_check_idle)
        else -> stringResource(R.string.oobe_repo_timeline_check_missing)
    }

    val node2Status = when (phase) {
        RepoPhase.CHECKING, RepoPhase.NEEDS_FORK -> NodeStatus.PENDING
        RepoPhase.FORKING -> NodeStatus.RUNNING
        RepoPhase.SYNCING, RepoPhase.READY -> NodeStatus.DONE
    }
    val node2Sub = when (phase) {
        RepoPhase.CHECKING, RepoPhase.NEEDS_FORK -> stringResource(R.string.oobe_repo_timeline_prepare_wait)
        RepoPhase.FORKING -> stringResource(R.string.oobe_repo_timeline_prepare_running)
        RepoPhase.SYNCING -> stringResource(R.string.oobe_repo_timeline_prepare_running)
        RepoPhase.READY -> if (justForked) {
            stringResource(R.string.oobe_repo_timeline_prepare_done)
        } else {
            stringResource(R.string.oobe_repo_timeline_prepare_ready)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        TimelineNode(
            index = 1,
            isLast = false,
            status = node1Status,
            title = stringResource(R.string.oobe_repo_timeline_check_title),
            subtitle = node1Sub
        )
        TimelineNode(
            index = 2,
            isLast = true,
            status = node2Status,
            title = stringResource(R.string.oobe_repo_timeline_prepare_title),
            subtitle = node2Sub
        )
    }
}

@Composable
private fun TimelineNode(index: Int, isLast: Boolean, status: NodeStatus, title: String, subtitle: String) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (status == NodeStatus.PENDING) cs.surfaceVariant else cs.primary),
                contentAlignment = Alignment.Center
            ) {
                when (status) {
                    NodeStatus.PENDING -> Text(
                        index.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant
                    )
                    NodeStatus.RUNNING -> LoadingIndicator(Modifier.size(16.dp))
                    NodeStatus.DONE -> Icon(
                        Icons.Default.Check, null, tint = cs.onPrimary, modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (!isLast) {
                Box(Modifier.width(2.dp).height(28.dp).background(cs.outlineVariant))
            }
        }
        Column(
            modifier = Modifier.padding(bottom = if (isLast) 0.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

// ── Step 3 · Finish ────────────────────────────────────────────────────────────

@Composable
private fun OobeFinishScreen(
    user: GitHubUser?,
    forkRepo: GitHubRepo?,
    behindBy: Int,
    skipInFlight: Boolean,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    OobeScaffold(
        step = AuthStep.FINISH,
        showBack = true,
        onBack = onBack,
        footer = {
            OobeFooter(
                primaryLabel = stringResource(R.string.oobe_finish_action),
                onPrimary = onFinish,
                primaryEnabled = !skipInFlight,
                primaryIcon = Icons.Default.RocketLaunch
            )
        }
    ) {
        Spacer(Modifier.height(8.dp))
        OobeDoneCircle()
        Spacer(Modifier.height(4.dp))
        ScreenHeading(
            eyebrow = null,
            title = stringResource(R.string.oobe_finish_title),
            desc = stringResource(R.string.oobe_finish_desc)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                OobeSummaryRow(
                    stringResource(R.string.oobe_summary_github),
                    user?.login ?: "—",
                    showDivider = true
                )
                OobeSummaryRow(
                    stringResource(R.string.oobe_summary_repo),
                    forkRepo?.fullName ?: "—",
                    showDivider = true
                )
                OobeSummaryRow(
                    stringResource(R.string.oobe_summary_status),
                    stringResource(
                        if (behindBy > 0) R.string.oobe_summary_status_behind else R.string.oobe_summary_status_ok
                    ),
                    showDivider = false
                )
            }
        }
    }
}

@Composable
private fun OobeDoneCircle() {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(118.dp).clip(CircleShape).background(cs.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(cs.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, null, tint = cs.onPrimary, modifier = Modifier.size(44.dp))
        }
    }
}
@Composable
private fun OobeSummaryRow(label: String, value: String, showDivider: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
