package com.abk.kernel.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.ui.blur.blurredCardBackground
import com.abk.kernel.ui.blur.blurredCardSurfaceColor
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkDialog
import io.github.xingguangcuican6666.abk.ui.components.AbkEmptyState
import io.github.xingguangcuican6666.abk.ui.components.AbkHeading
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

/**
 * Host-derived presentation of one managed extension. The extension host still owns installation,
 * OOBE, service launching and state reset; this type only carries what the page renders.
 */
internal data class ExtensionItemState(
    val id: String,
    val name: String,
    val description: String,
    val summary: String,
    val companionLabel: String,
    val companionInstalled: Boolean,
    val needsOobe: Boolean,
    val canLaunchOobe: Boolean,
    val canLaunchService: Boolean,
    val canInstall: Boolean,
)

@Composable
internal fun ExtensionsContent(
    topBarHeight: Dp,
    bootstrapMode: Boolean,
    focused: ExtensionItemState?,
    extensions: List<ExtensionItemState>,
    onInstall: (ExtensionItemState) -> Unit,
    onOpenOobe: (ExtensionItemState) -> Unit,
    onOpenService: (ExtensionItemState) -> Unit,
    onReset: (ExtensionItemState) -> Unit,
) {
    if (extensions.isEmpty() && focused == null) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(top = topBarHeight + 16.dp)
                    .padding(horizontal = AbkScreenHorizontalPadding)
                    .padding(bottom = 16.dp)
        ) {
            AbkEmptyState(
                title = stringResource(R.string.extension_manager_empty_title),
                description = stringResource(R.string.extension_manager_empty_desc),
                icon = AbkIcons.Modules,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = AbkScreenHorizontalPadding,
                top = topBarHeight + 16.dp,
                end = AbkScreenHorizontalPadding,
                bottom = 80.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (bootstrapMode && focused != null) {
            item("focus-guide-${focused.id}") {
                ExtensionBootstrapGuide(
                    extension = focused,
                    onInstall = { onInstall(focused) },
                    onOpenOobe = { onOpenOobe(focused) },
                    onOpenService = { onOpenService(focused) },
                )
            }
        }

        focused?.let { extension ->
            item("focus-${extension.id}") {
                ExtensionItemCard(
                    extension = extension,
                    emphasize = true,
                    onInstall = onInstall,
                    onOpenOobe = onOpenOobe,
                    onOpenService = onOpenService,
                    onReset = onReset,
                )
            }
        }

        items(items = extensions.filter { it.id != focused?.id }, key = { it.id }) { extension ->
            ExtensionItemCard(
                extension = extension,
                emphasize = false,
                onInstall = onInstall,
                onOpenOobe = onOpenOobe,
                onOpenService = onOpenService,
                onReset = onReset,
            )
        }
    }
}

@Composable
private fun ExtensionBootstrapGuide(
    extension: ExtensionItemState,
    onInstall: () -> Unit,
    onOpenOobe: () -> Unit,
    onOpenService: () -> Unit,
) {
    val titleRes =
        when {
            !extension.companionInstalled -> R.string.extension_bootstrap_install_title
            extension.canLaunchService -> R.string.extension_bootstrap_service_title
            extension.canLaunchOobe -> R.string.extension_bootstrap_oobe_required_title
            else -> R.string.extension_bootstrap_oobe_missing_title
        }
    val subtitle =
        when {
            !extension.companionInstalled ->
                stringResource(R.string.extension_bootstrap_install_desc, extension.companionLabel)
            extension.canLaunchService -> stringResource(R.string.extension_bootstrap_service_desc)
            extension.canLaunchOobe ->
                stringResource(R.string.extension_bootstrap_oobe_required_desc)
            else ->
                stringResource(
                    R.string.extension_bootstrap_oobe_missing_desc,
                    extension.companionLabel,
                )
        }

    ExtensionCardSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AbkHeading(stringResource(titleRes))
            Text(subtitle, color = AbkTheme.colors.inkMuted)
        }
        when {
            !extension.companionInstalled ->
                ExtensionAction(
                    label = stringResource(R.string.extension_install_action),
                    icon = AbkIcons.Download,
                    enabled = extension.canInstall,
                    onClick = onInstall,
                )
            extension.canLaunchService ->
                ExtensionAction(
                    label = stringResource(R.string.extension_run_service_action),
                    icon = AbkIcons.Forward,
                    onClick = onOpenService,
                )
            extension.canLaunchOobe ->
                ExtensionAction(
                    label = stringResource(R.string.extension_run_oobe_action),
                    icon = AbkIcons.Settings,
                    onClick = onOpenOobe,
                )
        }
    }
}

@Composable
private fun ExtensionItemCard(
    extension: ExtensionItemState,
    emphasize: Boolean,
    onInstall: (ExtensionItemState) -> Unit,
    onOpenOobe: (ExtensionItemState) -> Unit,
    onOpenService: (ExtensionItemState) -> Unit,
    onReset: (ExtensionItemState) -> Unit,
) {
    ExtensionCardSurface {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AbkHeading(extension.name)
            if (extension.description.isNotBlank()) {
                Text(extension.description, color = AbkTheme.colors.inkMuted)
            }
            if (extension.summary.isNotBlank()) {
                Text(
                    extension.summary,
                    color = AbkTheme.colors.inkMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AbkBadge(
                text =
                    stringResource(
                        if (extension.needsOobe) {
                            R.string.extension_status_pending_oobe
                        } else {
                            R.string.extension_status_oobe_complete
                        }
                    ),
                tone = if (extension.needsOobe) AbkTone.Warning else AbkTone.Success,
            )
            AbkBadge(
                text =
                    stringResource(
                        if (extension.companionInstalled) {
                            R.string.extension_status_companion_ready
                        } else {
                            R.string.extension_status_companion_missing
                        }
                    ),
                tone = if (extension.companionInstalled) AbkTone.Neutral else AbkTone.Danger,
            )
        }
        if (!extension.companionInstalled) {
            Text(
                stringResource(R.string.extension_missing_companion_desc, extension.companionLabel),
                color = AbkTheme.colors.inkMuted,
            )
        }
        when {
            !extension.companionInstalled ->
                ExtensionAction(
                    label = stringResource(R.string.extension_install_action),
                    icon = AbkIcons.Download,
                    enabled = extension.canInstall,
                    onClick = { onInstall(extension) },
                )
            extension.canLaunchService ->
                ExtensionAction(
                    label = stringResource(R.string.extension_run_service_action),
                    icon = AbkIcons.Forward,
                    onClick = { onOpenService(extension) },
                )
            extension.canLaunchOobe ->
                ExtensionAction(
                    label = stringResource(R.string.extension_run_oobe_action),
                    icon = AbkIcons.Settings,
                    onClick = { onOpenOobe(extension) },
                )
        }
        ExtensionAction(
            label = stringResource(R.string.extension_reset_action),
            icon = AbkIcons.Trash,
            style = if (emphasize) AbkButtonStyle.Outline else AbkButtonStyle.Ghost,
            onClick = { onReset(extension) },
        )
    }
}

@Composable
internal fun ExtensionMissingCompanionDialog(
    extensionName: String,
    companionLabel: String,
    installEnabled: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    AbkDialog(
        title = stringResource(R.string.extension_bootstrap_install_dialog_title),
        onDismissRequest = onDismiss,
        dismissDescription = stringResource(android.R.string.cancel),
        actions = {
            AbkButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                style = AbkButtonStyle.Ghost,
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            AbkButton(
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth(),
                style = AbkButtonStyle.Primary,
                enabled = installEnabled,
            ) {
                Text(stringResource(R.string.extension_install_action))
            }
        },
    ) {
        Text(
            stringResource(
                R.string.extension_bootstrap_install_dialog_desc,
                extensionName,
                companionLabel,
            ),
            color = AbkTheme.colors.inkMuted,
        )
    }
}

@Composable
private fun ExtensionCardSurface(content: @Composable ColumnScope.() -> Unit) {
    AbkCard(
        modifier = Modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
        containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised),
        content = content,
    )
}

@Composable
private fun ExtensionAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    style: AbkButtonStyle = AbkButtonStyle.Outline,
) {
    AbkButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        style = style,
        enabled = enabled,
    ) {
        AbkIcon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(label)
    }
}
