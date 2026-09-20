@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ShimmerLinearProgress
import com.abk.kernel.ui.theme.LocalUiSurfaceAlpha
import com.abk.kernel.viewmodel.AuthStep
import com.abk.kernel.viewmodel.MainViewModel
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val OOBE_SKIP_LOADING_DELAY_MS = 320L
private const val OOBE_SKIP_EXIT_DELAY_MS = 280L
private const val OOBE_SKIP_BACK_VISUAL_EXPONENT = 1.8f
private const val OOBE_SKIP_BACK_SCALE_DELTA = 0.09f
private val OOBE_SKIP_MAX_CORNER = 32.dp

/** Total steps advertised by the wizard progress rail. */
private const val OOBE_TOTAL_STEPS = 3

private fun AuthStep.stepIndex(): Int = when (this) {
    AuthStep.INTRO -> 0
    AuthStep.LOGIN -> 1
    AuthStep.FORK_CHECK -> 2
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .auroraBackground()
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
            WizardChrome(currentStep = state.authStep) {
                AnimatedContent(
                    targetState = state.authStep,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        val forward = targetState.stepIndex() >= initialState.stepIndex()
                        val dir = if (forward) 1 else -1
                        (
                            slideInHorizontally(motionScheme.slowSpatialSpec()) { full -> dir * full / 3 } +
                                fadeIn(motionScheme.defaultEffectsSpec())
                        ) togetherWith (
                            slideOutHorizontally(motionScheme.slowSpatialSpec()) { full -> -dir * full / 3 } +
                                fadeOut(motionScheme.fastEffectsSpec())
                        )
                    },
                    label = "oobe-step"
                ) { step ->
                    when (step) {
                        AuthStep.INTRO -> WelcomePage(
                            skipping = skipInFlight,
                            onContinue = {
                                if (!skipInFlight) {
                                    if (state.isLoggedIn) vm.openBuildOobe() else vm.continueOobeToLogin()
                                }
                            },
                            onSkip = ::requestSkip
                        )
                        AuthStep.LOGIN -> SignInPage(
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
                        AuthStep.FORK_CHECK -> ForkPage(
                            isLoading = state.isLoading,
                            hasFork = state.forkRepo != null,
                            behindBy = state.behindBy,
                            error = state.error,
                            onFork = { if (!skipInFlight) vm.forkRepo() },
                            onFinish = { if (!skipInFlight) vm.finishOobe() },
                            onSkip = ::requestSkip,
                            skipInFlight = skipInFlight,
                            onClearError = { vm.clearError() }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = skipInFlight,
            enter = fadeIn(motionScheme.defaultEffectsSpec()),
            exit = fadeOut(motionScheme.fastEffectsSpec())
        ) {
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

// ── Wizard chrome (progress rail + page host) ───────────────────────────────────

@Composable
private fun WizardChrome(
    currentStep: AuthStep,
    page: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(LocalUiSurfaceAlpha provides 1f) {
        // Only reserve the top (status bar) inset here. The bottom is left edge-to-edge
        // on purpose so each page's sticky footer background can extend down under the
        // gesture navigation pill — otherwise the strip behind the pill keeps the aurora
        // tint while the footer above it is solid surface, producing a visible seam.
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.statusBars
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StepProgressRail(activeIndex = currentStep.stepIndex(), total = OOBE_TOTAL_STEPS)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.oobe_setup_progress,
                        currentStep.stepIndex() + 1,
                        OOBE_TOTAL_STEPS
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                page()
            }
        }
    }
}

@Composable
private fun StepProgressRail(activeIndex: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val active = index == activeIndex
            val done = index < activeIndex
            val width by animateDpAsState(
                targetValue = if (active) 26.dp else 8.dp,
                animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                label = "rail-w-$index"
            )
            val color by animateColorAsState(
                targetValue = when {
                    active -> MaterialTheme.colorScheme.primary
                    done -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "rail-c-$index"
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// ── Reusable wizard page scaffold ───────────────────────────────────────────────

/**
 * Android-setup-wizard style page: a springy hero header up top, a scrollable body
 * in the middle, and a sticky footer button bar pinned to the bottom.
 */
@Composable
private fun ColumnScope.WizardPage(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    onContainer: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    badge: (@Composable () -> Unit)? = null,
    footer: @Composable ColumnScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit
) {
    // Fresh per-page entrance trigger: AnimatedContent recomposes this from scratch
    // on every step change, so a one-shot flag drives the staggered reveal.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Box(modifier = Modifier.fillMaxSize()) {
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 120.dp + navBarBottom),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WizardHero(icon = icon, accent = accent, container = container, onContainer = onContainer)
            Spacer(Modifier.height(28.dp))
            StaggeredReveal(entered, delayMs = 60) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(10.dp))
            StaggeredReveal(entered, delayMs = 120) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (badge != null) {
                Spacer(Modifier.height(16.dp))
                StaggeredReveal(entered, delayMs = 180) { badge() }
            }
            Spacer(Modifier.height(24.dp))
            StaggeredReveal(entered, delayMs = 240) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = body
                )
            }
        }

        // Sticky footer with a gentle scrim so scrolled content fades under it. The
        // solid-surface background is drawn BEFORE navigationBarsPadding so it fills the
        // strip behind the gesture pill, while the buttons themselves stay above the pill.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        1f to MaterialTheme.colorScheme.surface
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = footer
        )
    }
}

@Composable
private fun WizardHero(
    icon: ImageVector,
    accent: Color,
    container: Color,
    onContainer: Color
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    // Overshoot entrance: scale past 1 then settle via spatial spring.
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.4f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "hero-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "hero-alpha"
    )

    val infinite = rememberInfiniteTransition(label = "hero-inf")
    // Slow breathing halo + subtle vertical float keep the hero alive.
    val haloPulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero-halo"
    )
    val floatPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hero-float"
    )
    val floatY = sin(floatPhase) * 5f

    Box(
        modifier = Modifier
            .size(148.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                translationY = floatY
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer diffuse halo
        Box(
            modifier = Modifier
                .size(148.dp)
                .graphicsLayer {
                    val s = 0.85f + 0.15f * haloPulse
                    scaleX = s
                    scaleY = s
                    this.alpha = 0.30f + 0.25f * haloPulse
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.9f), Color.Transparent)
                    )
                )
        )
        // Inner tonal disc holding the glyph
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(46.dp)
            )
        }
    }
}

/** Fade + slide-up reveal with a per-item delay, driven off a shared trigger. */
@Composable
private fun StaggeredReveal(
    visible: Boolean,
    delayMs: Int,
    content: @Composable () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 420, delayMillis = delayMs),
        label = "reveal-$delayMs"
    )
    val density = LocalDensity.current
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = with(density) { (18.dp.toPx()) * (1f - progress) }
        }
    ) {
        content()
    }
}

// ── Footer actions ──────────────────────────────────────────────────────────────

@Composable
private fun PrimaryWizardButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "primary-press"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        AnimatedContent(targetState = loading, label = "primary-loading") { isLoading ->
            if (isLoading) {
                LoadingIndicator(Modifier.size(24.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SkipWizardButton(onSkip: () -> Unit, enabled: Boolean) {
    TextButton(
        onClick = onSkip,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.oobe_skip_for_now))
    }
}

// ── Welcome ──────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.WelcomePage(
    skipping: Boolean,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    WizardPage(
        icon = Icons.Default.RocketLaunch,
        title = stringResource(R.string.oobe_title),
        subtitle = stringResource(R.string.oobe_welcome_tagline),
        badge = {
            ExpressiveStatusChip(
                label = stringResource(R.string.oobe_first_launch),
                icon = Icons.Default.AutoAwesome,
                color = MaterialTheme.colorScheme.primary
            )
        },
        footer = {
            PrimaryWizardButton(
                text = stringResource(R.string.oobe_get_started),
                icon = Icons.Default.ArrowForward,
                onClick = onContinue,
                enabled = !skipping
            )
            SkipWizardButton(onSkip = onSkip, enabled = !skipping)
        }
    ) {
        FeatureRow(
            icon = Icons.Default.Code,
            title = stringResource(R.string.oobe_build_title),
            desc = stringResource(R.string.oobe_build_detail)
        )
        FeatureRow(
            icon = Icons.Default.CloudDownload,
            title = stringResource(R.string.oobe_flash_title),
            desc = stringResource(R.string.oobe_flash_detail)
        )
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, desc: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Sign in ──────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.SignInPage(
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

    WizardPage(
        icon = Icons.Default.Code,
        title = stringResource(R.string.login_title),
        subtitle = stringResource(R.string.login_desc),
        accent = MaterialTheme.colorScheme.secondary,
        container = MaterialTheme.colorScheme.secondaryContainer,
        onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
        badge = {
            ExpressiveStatusChip(
                label = if (isPolling) {
                    stringResource(R.string.github_waiting_confirm)
                } else {
                    stringResource(R.string.github_auth_required)
                },
                icon = if (isPolling) Icons.Default.Sync else Icons.Default.VerifiedUser,
                color = if (isPolling) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
            )
        },
        footer = {
            if (userCode == null) {
                PrimaryWizardButton(
                    text = stringResource(R.string.login_github),
                    icon = Icons.Default.Code,
                    onClick = { showConsentDialog = true },
                    enabled = !isLoading && !skipInFlight,
                    loading = isLoading
                )
            }
            SkipWizardButton(onSkip = onSkip, enabled = !skipInFlight)
        }
    ) {
        AnimatedVisibility(
            visible = userCode != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            userCode?.let { code ->
                DeviceCodeCard(
                    code = code,
                    verificationUri = verificationUri ?: "https://github.com/login/device",
                    isPolling = isPolling,
                    context = context
                )
            }
        }
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            error?.let { ErrorCard(error = it, onClearError = onClearError) }
        }
    }
}

@Composable
private fun DeviceCodeCard(
    code: String,
    verificationUri: String,
    isPolling: Boolean,
    context: Context
) {
    var copied by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.auth_code_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.auth_code_desc),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedCard {
                Text(
                    code,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Button(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(verificationUri)))
                    }
                }) {
                    Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.open_browser))
                }
            }
            if (isPolling) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LoadingIndicator(Modifier.size(22.dp))
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

// ── Fork check ─────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.ForkPage(
    isLoading: Boolean,
    hasFork: Boolean,
    behindBy: Int,
    error: String?,
    onFork: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    skipInFlight: Boolean,
    onClearError: () -> Unit
) {
    val icon: ImageVector
    val title: String
    val subtitle: String
    val accent: Color
    val container: Color
    val onContainer: Color
    when {
        isLoading -> {
            icon = Icons.Default.Sync
            title = stringResource(R.string.fork_checking_title)
            subtitle = stringResource(R.string.fork_checking_desc)
            accent = MaterialTheme.colorScheme.tertiary
            container = MaterialTheme.colorScheme.tertiaryContainer
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer
        }
        !hasFork -> {
            icon = Icons.Default.ForkRight
            title = stringResource(R.string.fork_title)
            subtitle = stringResource(R.string.fork_desc)
            accent = MaterialTheme.colorScheme.primary
            container = MaterialTheme.colorScheme.primaryContainer
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        }
        else -> {
            icon = Icons.Default.CheckCircle
            title = stringResource(R.string.fork_ready_title)
            subtitle = if (behindBy > 0) {
                stringResource(R.string.fork_ready_behind, behindBy)
            } else {
                stringResource(R.string.fork_ready_ok)
            }
            accent = MaterialTheme.colorScheme.secondary
            container = MaterialTheme.colorScheme.secondaryContainer
            onContainer = MaterialTheme.colorScheme.onSecondaryContainer
        }
    }

    WizardPage(
        icon = icon,
        title = title,
        subtitle = subtitle,
        accent = accent,
        container = container,
        onContainer = onContainer,
        badge = {
            when {
                isLoading -> ExpressiveStatusChip(
                    label = stringResource(R.string.loading),
                    icon = Icons.Default.HourglassTop,
                    color = MaterialTheme.colorScheme.tertiary
                )
                !hasFork -> ExpressiveStatusChip(
                    label = stringResource(R.string.fork_create_badge),
                    icon = Icons.Default.CallSplit,
                    color = MaterialTheme.colorScheme.primary
                )
                behindBy > 0 -> ExpressiveStatusChip(
                    label = stringResource(R.string.fork_sync_recommended),
                    icon = Icons.Default.Warning,
                    color = MaterialTheme.colorScheme.error
                )
                else -> ExpressiveStatusChip(
                    label = stringResource(R.string.fork_enter_main),
                    icon = Icons.Default.Verified,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        footer = {
            when {
                isLoading -> SkipWizardButton(onSkip = onSkip, enabled = !skipInFlight)
                !hasFork -> {
                    PrimaryWizardButton(
                        text = stringResource(R.string.fork_action),
                        icon = Icons.Default.ForkRight,
                        onClick = onFork,
                        enabled = !skipInFlight
                    )
                    SkipWizardButton(onSkip = onSkip, enabled = !skipInFlight)
                }
                else -> {
                    // Fork ready: this is the last step. Completion is persisted only
                    // here, on an explicit tap — never automatically.
                    PrimaryWizardButton(
                        text = stringResource(R.string.oobe_finish),
                        icon = Icons.Default.CheckCircle,
                        onClick = onFinish,
                        enabled = !skipInFlight
                    )
                }
            }
        }
    ) {
        if (isLoading) {
            ShimmerLinearProgress(
                progress = { null },
                modifier = Modifier.fillMaxWidth()
            )
        }
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            error?.let { ErrorCard(error = it, onClearError = onClearError) }
        }
    }
}

@Composable
private fun ErrorCard(error: String, onClearError: () -> Unit) {
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

// ── Ambient aurora backdrop ──────────────────────────────────────────────────────

/**
 * Slowly drifting radial-gradient blobs behind the whole flow. Kept low-alpha over
 * the surface so text contrast is untouched, echoing Android 17's living wallpaper.
 */
@Composable
private fun Modifier.auroraBackground(): Modifier {
    val infinite = rememberInfiniteTransition(label = "aurora")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora-phase"
    )
    val c1 = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val c2 = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
    val c3 = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
    return this.drawBehind {
        val w = size.width
        val h = size.height
        val r = maxOf(w, h) * 0.6f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(c1, Color.Transparent),
                center = Offset(w * (0.25f + 0.1f * sin(phase)), h * (0.18f + 0.05f * sin(phase + 1f))),
                radius = r
            ),
            radius = r,
            center = Offset(w * (0.25f + 0.1f * sin(phase)), h * (0.18f + 0.05f * sin(phase + 1f)))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(c2, Color.Transparent),
                center = Offset(w * (0.8f + 0.08f * sin(phase + 2f)), h * (0.32f + 0.06f * sin(phase))),
                radius = r
            ),
            radius = r,
            center = Offset(w * (0.8f + 0.08f * sin(phase + 2f)), h * (0.32f + 0.06f * sin(phase)))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(c3, Color.Transparent),
                center = Offset(w * (0.5f + 0.12f * sin(phase + 4f)), h * (0.85f + 0.05f * sin(phase + 2f))),
                radius = r
            ),
            radius = r,
            center = Offset(w * (0.5f + 0.12f * sin(phase + 4f)), h * (0.85f + 0.05f * sin(phase + 2f)))
        )
    }
}
