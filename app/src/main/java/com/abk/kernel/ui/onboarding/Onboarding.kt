package com.abk.kernel.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.abk.kernel.R
import com.abk.kernel.viewmodel.AuthStep
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.MainViewModel
import io.github.xingguangcuican6666.abk.ui.components.AbkAlert
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkDialog
import io.github.xingguangcuican6666.abk.ui.components.AbkHeading
import io.github.xingguangcuican6666.abk.ui.components.AbkHero
import io.github.xingguangcuican6666.abk.ui.components.AbkInlineCode
import io.github.xingguangcuican6666.abk.ui.components.AbkLinearProgress
import io.github.xingguangcuican6666.abk.ui.components.AbkScaffold
import io.github.xingguangcuican6666.abk.ui.components.AbkSize
import io.github.xingguangcuican6666.abk.ui.components.AbkText
import io.github.xingguangcuican6666.abk.ui.components.AbkTopBar
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

/**
 * 条款弹窗：必须滑到底部才能接受。协议内容较长，放在可滚动的对话框正文里。
 */
@Composable
fun TermsAgreementDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    val scrollState = rememberScrollState()
    val canAccept by remember {
        derivedStateOf { scrollState.maxValue == 0 || scrollState.value >= scrollState.maxValue }
    }
    AbkDialog(
        title = stringResource(R.string.terms_title),
        onDismissRequest = {},
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        actions = {
            AbkButton(onClick = onDecline, style = AbkButtonStyle.Ghost) {
                AbkText(stringResource(R.string.terms_decline))
            }
            AbkButton(
                onClick = onAccept,
                style = AbkButtonStyle.Primary,
                enabled = canAccept,
            ) {
                AbkText(
                    stringResource(
                        if (canAccept) R.string.terms_accept else R.string.terms_scroll_bottom
                    )
                )
            }
        },
    ) {
        // The dialog body already scrolls; constrain it so the action row stays put.
        Box(Modifier.heightIn(max = 380.dp)) {
            Column(
                Modifier.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(AbkSpacing.Sm),
            ) {
                TermsParagraph(stringResource(R.string.terms_version), muted = true)
                TermsParagraph(stringResource(R.string.terms_effective_date), muted = true)
                TermsParagraph(stringResource(R.string.terms_intro))
                TermsSection(
                    stringResource(R.string.terms_section_usage),
                    stringResource(R.string.terms_usage_1),
                    stringResource(R.string.terms_usage_2),
                )
                TermsSection(
                    stringResource(R.string.terms_section_risk),
                    stringResource(R.string.terms_risk_1),
                    stringResource(R.string.terms_risk_2),
                    stringResource(R.string.terms_risk_3),
                )
                TermsSection(
                    stringResource(R.string.terms_section_legal),
                    stringResource(R.string.terms_legal_1),
                    stringResource(R.string.terms_legal_2),
                    stringResource(R.string.terms_legal_3),
                )
                TermsSection(
                    stringResource(R.string.terms_section_third_party),
                    stringResource(R.string.terms_third_party_1),
                    stringResource(R.string.terms_third_party_2),
                    stringResource(R.string.terms_third_party_3),
                )
                TermsSection(
                    stringResource(R.string.terms_section_privacy),
                    stringResource(R.string.terms_privacy_1),
                    stringResource(R.string.terms_privacy_2),
                    stringResource(R.string.terms_privacy_3),
                )
                TermsSection(
                    stringResource(R.string.terms_section_disclaimer),
                    stringResource(R.string.terms_disclaimer_1),
                    stringResource(R.string.terms_disclaimer_2),
                    stringResource(R.string.terms_disclaimer_3),
                )
                TermsParagraph(stringResource(R.string.terms_accept_hint), muted = true)
            }
        }
    }
}

@Composable
private fun TermsSection(title: String, vararg paragraphs: String) {
    AbkText(title, style = MaterialTheme.typography.titleSmall, color = AbkTheme.colors.ink)
    paragraphs.forEach { TermsParagraph(it) }
}

@Composable
private fun TermsParagraph(text: String, muted: Boolean = false) {
    AbkText(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (muted) AbkTheme.colors.inkMuted else AbkTheme.colors.inkSoft,
    )
}

/**
 * 首次引导：介绍 → GitHub 登录（设备码）→ Fork 检查。
 * 每一步都可以跳过；跳过后在主界面切到构建页时仍会再次进入。
 */
@Composable
fun OnboardingScreen(vm: MainViewModel, state: MainUiState) {
    // The onboarding is a full-screen opaque flow; back acts like "skip".
    BackHandler { vm.skipOobe() }

    AbkScaffold(
        topBar = { AbkTopBar(title = stringResource(R.string.app_name), subtitle = stringResource(R.string.oobe_first_launch)) },
        modifier = Modifier.background(AbkTheme.colors.background),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(AbkSpacing.Lg),
        ) {
            when (state.authStep) {
                AuthStep.INTRO -> IntroStep(
                    onContinue = vm::continueOobeToLogin,
                    onSkip = vm::skipOobe,
                )
                AuthStep.LOGIN -> LoginStep(vm = vm, state = state)
                AuthStep.FORK_CHECK -> ForkCheckStep(vm = vm, state = state)
            }
            state.error?.let { error ->
                AbkAlert(
                    title = stringResource(R.string.github_auth_required),
                    message = error,
                    tone = AbkTone.Danger,
                    actions = {
                        AbkButton(onClick = vm::clearError, style = AbkButtonStyle.Ghost, size = AbkSize.Small) {
                            AbkText(stringResource(R.string.close_error))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun IntroStep(onContinue: () -> Unit, onSkip: () -> Unit) {
    AbkHero(
        title = stringResource(R.string.oobe_title),
        eyebrow = stringResource(R.string.oobe_first_launch),
        description = stringResource(R.string.oobe_desc),
    )
    AbkCard {
        AbkHeading(stringResource(R.string.oobe_build_title))
        AbkText(stringResource(R.string.oobe_build_desc), color = AbkTheme.colors.inkSoft)
        AbkText(stringResource(R.string.oobe_build_detail), color = AbkTheme.colors.inkMuted)
    }
    AbkCard {
        AbkHeading(stringResource(R.string.oobe_flash_title))
        AbkText(stringResource(R.string.oobe_flash_desc), color = AbkTheme.colors.inkSoft)
        AbkText(stringResource(R.string.oobe_flash_detail), color = AbkTheme.colors.inkMuted)
    }
    AbkButton(onClick = onContinue, modifier = Modifier.fillMaxWidth(), style = AbkButtonStyle.Primary, size = AbkSize.Large) {
        AbkText(stringResource(R.string.oobe_continue_setup))
    }
    AbkButton(onClick = onSkip, modifier = Modifier.fillMaxWidth(), style = AbkButtonStyle.Ghost) {
        AbkText(stringResource(R.string.oobe_skip_for_now))
    }
}

@Composable
private fun LoginStep(vm: MainViewModel, state: MainUiState) {
    @Suppress("DEPRECATION")
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    AbkHero(
        title = stringResource(R.string.login_title),
        description = stringResource(R.string.login_desc),
    )

    if (state.userCode.isNullOrBlank()) {
        AbkButton(
            onClick = vm::startDeviceFlow,
            modifier = Modifier.fillMaxWidth(),
            style = AbkButtonStyle.Primary,
            size = AbkSize.Large,
            enabled = !state.isLoading,
            loading = state.isLoading,
        ) {
            AbkText(stringResource(R.string.login_github))
        }
    } else {
        AbkCard {
            AbkHeading(stringResource(R.string.auth_code_title))
            AbkText(stringResource(R.string.auth_code_desc), color = AbkTheme.colors.inkSoft)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
                AbkInlineCode(state.userCode.orEmpty())
                AbkButton(
                    onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(state.userCode.orEmpty())) },
                    style = AbkButtonStyle.Ghost,
                    size = AbkSize.Small,
                ) { AbkText(stringResource(R.string.copy)) }
            }
            if (state.isPollingToken) {
                AbkLinearProgress(progress = null, label = stringResource(R.string.waiting_auth))
                AbkText(stringResource(R.string.waiting_auth), color = AbkTheme.colors.inkMuted)
            }
        }
    }

    AbkButton(onClick = vm::skipOobe, modifier = Modifier.fillMaxWidth(), style = AbkButtonStyle.Ghost) {
        AbkText(stringResource(R.string.oobe_skip_for_now))
    }
}

@Composable
private fun ForkCheckStep(vm: MainViewModel, state: MainUiState) {
    AbkHero(
        title = stringResource(R.string.fork_title),
        description = stringResource(R.string.fork_desc),
    )
    when {
        state.isLoading -> {
            AbkCard {
                AbkHeading(stringResource(R.string.fork_checking_title))
                AbkText(stringResource(R.string.fork_checking_desc), color = AbkTheme.colors.inkMuted)
                AbkLinearProgress(progress = null, label = stringResource(R.string.fork_checking_title))
            }
        }
        state.forkRepo == null -> {
            AbkCard {
                AbkText(stringResource(R.string.fork_desc), color = AbkTheme.colors.inkSoft)
                AbkButton(onClick = vm::forkRepo, style = AbkButtonStyle.Primary, enabled = !state.isLoading) {
                    AbkText(stringResource(R.string.fork_action))
                }
            }
        }
        else -> {
            AbkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AbkHeading(stringResource(R.string.fork_ready_title), Modifier.weight(1f))
                }
                AbkText(
                    if (state.behindBy > 0) stringResource(R.string.fork_ready_behind, state.behindBy)
                    else stringResource(R.string.fork_ready_ok),
                    color = AbkTheme.colors.inkSoft,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
                    if (state.behindBy > 0) {
                        AbkButton(onClick = vm::syncFork, style = AbkButtonStyle.Primary, enabled = !state.isLoading) {
                            AbkText(stringResource(R.string.sync_action))
                        }
                    }
                    AbkButton(onClick = vm::skipOobe, style = AbkButtonStyle.Secondary) {
                        AbkText(stringResource(R.string.fork_enter_main))
                    }
                }
            }
        }
    }
    AbkButton(onClick = vm::skipOobe, modifier = Modifier.fillMaxWidth(), style = AbkButtonStyle.Ghost) {
        AbkText(stringResource(R.string.oobe_skip_for_now))
    }
}
