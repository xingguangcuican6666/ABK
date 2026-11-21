package com.abk.kernel.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.ui.theme.LocalUiSurfaceAlpha
import io.github.xingguangcuican6666.abk.ui.components.AbkAlert
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkDialog
import io.github.xingguangcuican6666.abk.ui.components.AbkHeading
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.components.AbkLinearProgress
import io.github.xingguangcuican6666.abk.ui.components.AbkPanel
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

@Composable
internal fun OobeIntroContent(
    loggedIn: Boolean,
    skipping: Boolean,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
  AuthShell {
    AuthHeroPanel(
        title = stringResource(R.string.oobe_title),
        subtitle = stringResource(R.string.oobe_desc),
        icon = AbkIcons.Build,
        badge = stringResource(R.string.oobe_first_launch),
        badgeTone = AbkTone.Accent,
    )
    AuthInfoPanel(
        title = stringResource(R.string.oobe_build_title),
        subtitle = stringResource(R.string.oobe_build_desc),
        detail = stringResource(R.string.oobe_build_detail),
        icon = AbkIcons.Build,
    )
    AuthInfoPanel(
        title = stringResource(R.string.oobe_flash_title),
        subtitle = stringResource(R.string.oobe_flash_desc),
        detail = stringResource(R.string.oobe_flash_detail),
        icon = AbkIcons.Download,
    )
    AuthActionButton(
        label =
            stringResource(if (loggedIn) R.string.oobe_continue_setup else R.string.login_github),
        icon = AbkIcons.ArrowRight,
        enabled = !skipping,
        onClick = onContinue,
    )
    AuthSkipButton(enabled = !skipping, onClick = onSkip)
  }
}

@Composable
internal fun LoginContent(
    isLoading: Boolean,
    userCode: String?,
    verificationUri: String?,
    isPolling: Boolean,
    error: String?,
    onLogin: () -> Unit,
    onSkip: () -> Unit,
    skipInFlight: Boolean,
    onClearError: () -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenVerificationUrl: (String) -> Unit,
) {
  var showConsentDialog by remember { mutableStateOf(false) }

  if (showConsentDialog) {
    AbkDialog(
        title = stringResource(R.string.github_auth_title),
        onDismissRequest = { showConsentDialog = false },
        dismissDescription = stringResource(R.string.close),
        actions = {
          AbkButton(onClick = { showConsentDialog = false }, style = AbkButtonStyle.Outline) {
            Text(stringResource(R.string.cancel))
          }
          AbkButton(
              onClick = {
                showConsentDialog = false
                onLogin()
              },
              style = AbkButtonStyle.Primary,
          ) {
            Text(stringResource(R.string.confirm))
          }
        },
    ) {
      Text(stringResource(R.string.github_auth_desc), color = AbkTheme.colors.inkMuted)
    }
  }

  AuthShell {
    AuthHeroPanel(
        title = stringResource(R.string.login_title),
        subtitle = stringResource(R.string.login_desc),
        icon = AbkIcons.Build,
        badge =
            stringResource(
                if (isPolling) R.string.github_waiting_confirm else R.string.github_auth_required
            ),
        badgeTone = if (isPolling) AbkTone.Info else AbkTone.Accent,
    )

    if (userCode != null) {
      DeviceCodePanel(
          code = userCode,
          verificationUri = verificationUri ?: "https://github.com/login/device",
          isPolling = isPolling,
          onCopyCode = onCopyCode,
          onOpenVerificationUrl = onOpenVerificationUrl,
      )
    }

    if (error != null) AuthErrorPanel(error = error, onClearError = onClearError)

    if (userCode == null) {
      AuthActionButton(
          label = stringResource(R.string.login_github),
          icon = AbkIcons.Build,
          enabled = !isLoading && !skipInFlight,
          loading = isLoading,
          onClick = { showConsentDialog = true },
      )
    }

    AuthSkipButton(enabled = !skipInFlight, onClick = onSkip)
  }
}

@Composable
internal fun ForkCheckContent(
    isLoading: Boolean,
    hasFork: Boolean,
    behindBy: Int,
    showSyncDialog: Boolean,
    error: String?,
    onFork: () -> Unit,
    onSync: () -> Unit,
    onSkip: () -> Unit,
    showSkipAction: Boolean = false,
    skipInFlight: Boolean = false,
    onClearError: () -> Unit,
) {
  if (showSyncDialog) {
    AbkDialog(
        title = stringResource(R.string.sync_title),
        onDismissRequest = onSkip,
        dismissDescription = stringResource(R.string.close),
        actions = {
          AbkButton(onClick = onSkip, style = AbkButtonStyle.Outline) {
            Text(stringResource(R.string.skip))
          }
          AbkButton(onClick = onSync, style = AbkButtonStyle.Primary) {
            Text(stringResource(R.string.sync_action))
          }
        },
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sync_desc), color = AbkTheme.colors.inkMuted)
        Text(stringResource(R.string.sync_behind_commits, behindBy))
      }
    }
  }

  AuthShell {
    when {
      isLoading -> {
        AuthHeroPanel(
            title = stringResource(R.string.fork_checking_title),
            subtitle = stringResource(R.string.fork_checking_desc),
            icon = AbkIcons.Refresh,
            badge = stringResource(R.string.loading),
            badgeTone = AbkTone.Info,
        )
        AbkPanel(Modifier.fillMaxWidth()) {
          AbkLinearProgress(null, label = stringResource(R.string.fork_checking_title))
        }
      }
      !hasFork -> {
        AuthHeroPanel(
            title = stringResource(R.string.fork_title),
            subtitle = stringResource(R.string.fork_desc),
            icon = AbkIcons.Modules,
            badge = stringResource(R.string.fork_create_badge),
            badgeTone = AbkTone.Accent,
        )
        AuthActionButton(
            label = stringResource(R.string.fork_action),
            icon = AbkIcons.Modules,
            enabled = !skipInFlight,
            loading = isLoading,
            onClick = onFork,
        )
      }
      else -> {
        val behind = behindBy > 0
        AuthHeroPanel(
            title = stringResource(R.string.fork_ready_title),
            subtitle =
                if (behind) {
                  stringResource(R.string.fork_ready_behind, behindBy)
                } else {
                  stringResource(R.string.fork_ready_ok)
                },
            icon = AbkIcons.Check,
            badge =
                stringResource(
                    if (behind) R.string.fork_sync_recommended else R.string.fork_enter_main
                ),
            badgeTone = if (behind) AbkTone.Warning else AbkTone.Success,
        )
      }
    }

    if (error != null) AuthErrorPanel(error = error, onClearError = onClearError)
    if (showSkipAction) AuthSkipButton(enabled = !skipInFlight, onClick = onSkip)
  }
}

@Composable
private fun AuthShell(content: @Composable ColumnScope.() -> Unit) {
  CompositionLocalProvider(LocalUiSurfaceAlpha provides 1f) {
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
      Box(
          modifier = Modifier.fillMaxSize().padding(padding),
          contentAlignment = Alignment.Center,
      ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
      }
    }
  }
}

@Composable
private fun AuthHeroPanel(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String,
    badgeTone: AbkTone,
) {
  AbkPanel(Modifier.fillMaxWidth()) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
      Box(
          modifier =
              Modifier.size(48.dp)
                  .background(AbkTheme.colors.accent.container, RoundedCornerShape(16.dp)),
          contentAlignment = Alignment.Center,
      ) {
        AbkIcon(icon, null, tint = AbkTheme.colors.accent.onContainer, size = 24.dp)
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AbkHeading(title)
        Text(subtitle, color = AbkTheme.colors.inkMuted)
      }
    }
    AbkBadge(badge, tone = badgeTone)
  }
}

@Composable
private fun AuthInfoPanel(title: String, subtitle: String, detail: String, icon: ImageVector) {
  AbkPanel(Modifier.fillMaxWidth()) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      AbkIcon(icon, null, tint = AbkTheme.colors.accent.color, size = 20.dp)
      AbkHeading(title)
    }
    Text(subtitle, color = AbkTheme.colors.inkMuted)
    Text(detail, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun AuthActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    loading: Boolean = false,
) {
  AbkButton(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth(),
      style = AbkButtonStyle.Primary,
      enabled = enabled,
      loading = loading,
      loadingDescription = stringResource(R.string.loading),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (!loading) AbkIcon(icon, null, size = 18.dp)
      Spacer(Modifier.size(8.dp))
      Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
    }
  }
}

@Composable
private fun AuthSkipButton(enabled: Boolean, onClick: () -> Unit) {
  AbkButton(onClick = onClick, style = AbkButtonStyle.Ghost, enabled = enabled) {
    Text(stringResource(R.string.oobe_skip_for_now))
  }
}

@Composable
private fun DeviceCodePanel(
    code: String,
    verificationUri: String,
    isPolling: Boolean,
    onCopyCode: (String) -> Unit,
    onOpenVerificationUrl: (String) -> Unit,
) {
  var copied by remember { mutableStateOf(false) }

  AbkPanel(Modifier.fillMaxWidth()) {
    AbkHeading(stringResource(R.string.auth_code_title))
    Text(
        stringResource(R.string.auth_code_desc),
        style = MaterialTheme.typography.bodySmall,
        color = AbkTheme.colors.inkMuted,
        textAlign = TextAlign.Center,
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AbkTheme.colors.sunken,
        border = androidx.compose.foundation.BorderStroke(1.dp, AbkTheme.colors.line),
    ) {
      Text(
          code,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
          style = MaterialTheme.typography.headlineSmall,
          fontFamily = AbkTheme.monoFont,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
          color = AbkTheme.colors.accent.color,
      )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      AbkButton(
          onClick = {
            onCopyCode(code)
            copied = true
          },
          modifier = Modifier.fillMaxWidth(),
          style = AbkButtonStyle.Outline,
      ) {
        AbkIcon(if (copied) AbkIcons.Check else AbkIcons.Copy, null, size = 18.dp)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(if (copied) R.string.copied else R.string.copy))
      }
      AbkButton(
          onClick = { onOpenVerificationUrl(verificationUri) },
          modifier = Modifier.fillMaxWidth(),
          style = AbkButtonStyle.Primary,
      ) {
        AbkIcon(AbkIcons.Forward, null, size = 18.dp)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.open_browser))
      }
    }
    if (isPolling) {
      Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        AbkLinearProgress(
            null,
            Modifier.weight(1f),
            label = stringResource(R.string.waiting_auth),
        )
        Text(
            stringResource(R.string.waiting_auth),
            style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

@Composable
private fun AuthErrorPanel(error: String, onClearError: () -> Unit) {
  AbkAlert(
      title = stringResource(R.string.error_unknown),
      message = error,
      tone = AbkTone.Danger,
      actions = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          AbkIconButton(
              icon = AbkIcons.Close,
              contentDescription = stringResource(R.string.close_error),
              onClick = onClearError,
          )
        }
      },
  )
}
