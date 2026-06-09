package com.abk.kernel.ui.screens.miuix

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.viewmodel.AuthStep
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import androidx.core.net.toUri

@Composable
fun MiuixOobeScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (state.authStep) {
                AuthStep.INTRO -> MiuixOobeIntro(vm = vm)
                AuthStep.LOGIN -> MiuixLogin(vm = vm, context = context)
                AuthStep.FORK_CHECK -> MiuixForkCheck(vm = vm)
            }
        }
    }
}

@Composable
fun MiuixTermsAgreementDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val scrollState = rememberScrollState()
    val canAccept by remember { derivedStateOf { scrollState.maxValue == 0 || scrollState.value >= scrollState.maxValue } }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.terms_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TermsSection(stringResource(R.string.terms_section_risk), stringResource(R.string.terms_risk_1))
                TermsSection(stringResource(R.string.terms_section_legal), stringResource(R.string.terms_legal_1))
                TermsSection(stringResource(R.string.terms_section_third_party), stringResource(R.string.terms_third_party_1))
                TermsSection(stringResource(R.string.terms_section_disclaimer), stringResource(R.string.terms_disclaimer_1))
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(text = stringResource(R.string.terms_decline), onClick = onDecline)
                Button(
                    onClick = onAccept,
                    enabled = canAccept,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (canAccept) stringResource(R.string.terms_accept)
                        else stringResource(R.string.terms_scroll_bottom)
                    )
                }
            }
        }
    }
}

@Composable
private fun TermsSection(title: String, content: String) {
    Column {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colorScheme.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(content, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
    }
}

// ── OOBE Intro ──

@Composable
private fun MiuixOobeIntro(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val needsLogin = !state.isLoggedIn

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.oobe_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (needsLogin) stringResource(R.string.oobe_build_desc)
                       else stringResource(R.string.oobe_build_detail),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center
            )
        }
    }

    Button(
        onClick = vm::continueOobeToLogin,
        colors = ButtonDefaults.buttonColorsPrimary(),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Text(if (needsLogin) stringResource(R.string.login_github) else stringResource(R.string.oobe_continue_setup))
    }

    TextButton(text = stringResource(R.string.skip), onClick = vm::skipOobe)
}

// ── Login Screen ──

@Composable
private fun MiuixLogin(vm: MainViewModel, context: Context) {
    val state by vm.uiState.collectAsState()
    val userCode = state.userCode

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.login_title), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.login_desc), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.Center)
        }
    }

    if (userCode != null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("请在浏览器中输入以下验证码", fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(color = colorScheme.secondaryContainer)) {
                    Text(userCode, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp).fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("code", userCode))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("复制") }
                    Button(
                        onClick = {
                            state.verificationUri?.let { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.open_browser)) }
                }
                if (state.isPollingToken) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            size = 24.dp,
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.waiting_auth),
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }
        }
    } else if (!state.isPollingToken) {
        Button(
            onClick = vm::startDeviceFlow,
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(stringResource(R.string.login_github)) }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                size = 28.dp,
                strokeWidth = 3.dp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.waiting_auth),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariantSummary
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    TextButton(text = stringResource(R.string.skip), onClick = vm::skipOobe)
}

// ── Fork Check Screen ──

@Composable
private fun MiuixForkCheck(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val hasFork = state.forkRepo != null
    val behindBy = state.behindBy

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.fork_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    !hasFork -> stringResource(R.string.fork_desc)
                    behindBy > 0 -> stringResource(R.string.sync_behind_commits, behindBy)
                    else -> stringResource(R.string.fork_ready_ok)
                },
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
    }

    when {
        !hasFork -> Button(
            onClick = vm::forkRepo,
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(stringResource(R.string.fork_action)) }

        behindBy > 0 -> Button(
            onClick = vm::syncFork,
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(stringResource(R.string.sync_action)) }

        else -> Button(
            onClick = vm::skipOobe,
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(stringResource(R.string.fork_enter_main)) }
    }

    Spacer(Modifier.height(8.dp))
    TextButton(text = stringResource(R.string.skip), onClick = vm::skipOobe)
}

