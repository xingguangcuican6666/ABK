package com.abk.kernel.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.RootGrantApp
import com.abk.kernel.data.model.RootGrantProfile
import com.abk.kernel.ui.blur.blurredCardBackground
import com.abk.kernel.ui.blur.blurredCardSurfaceColor
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.screens.flash.FlashAction
import io.github.xingguangcuican6666.abk.ui.components.*
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

@Composable
private fun RootGrantCard(content: @Composable ColumnScope.() -> Unit) {
    AbkCard(
        modifier = Modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
        containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised),
        content = content,
    )
}

@Composable
private fun RootGrantSection(
    title: String,
    description: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    RootGrantCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AbkIcon(icon, null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AbkHeading(title)
                Text(description, color = AbkTheme.colors.inkMuted)
            }
        }
        content()
    }
}

@Composable
internal fun RootGrantSearchField(query: String, onQueryChange: (String) -> Unit) {
    AbkTextField(
        value = query,
        onValueChange = onQueryChange,
        label = stringResource(R.string.root_auth_search_apps),
        modifier = Modifier.fillMaxWidth(),
        prefix = { AbkIcon(AbkIcons.Search, null, size = 18.dp) },
    )
}

@Composable
internal fun RootGrantControlsCard(
    showSystemApps: Boolean,
    onShowSystemAppsChange: (Boolean) -> Unit,
) {
    RootGrantSection(
        stringResource(R.string.root_auth_section_title),
        stringResource(R.string.root_auth_section_desc),
        AbkIcons.Controls,
    ) {
        AbkSettingSwitch(
            title = stringResource(R.string.root_auth_show_system_apps),
            description = null,
            checked = showSystemApps,
            onCheckedChange = onShowSystemAppsChange,
        )
    }
}

@Composable
internal fun RootGrantAppCard(
    app: RootGrantApp,
    saving: Boolean,
    anySaving: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    icon: @Composable () -> Unit = { AbkIcon(AbkIcons.Modules, null, size = 40.dp) },
) {
    RootGrantCard {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(role = Role.Button, onClick = onOpen),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            icon()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AbkHeading(app.label.ifBlank { app.packageName })
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = AbkTheme.monoFont,
                )
                Text(
                    listOf("UID ${app.uid}", app.userName)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = AbkTheme.colors.inkMuted,
                )
            }
            AbkIcon(AbkIcons.Forward, null, size = 18.dp)
        }
        AbkCluster {
            AbkBadge(
                stringResource(
                    if (app.profile.allowSu) R.string.root_auth_allow else R.string.root_auth_deny
                ),
                tone = if (app.profile.allowSu) AbkTone.Success else AbkTone.Neutral,
            )
            if (app.isSystemApp)
                AbkBadge(stringResource(R.string.root_auth_system_app), dot = false)
            if (app.profileLoaded) {
                AbkBadge(
                    stringResource(
                        if (app.profile.rootUseDefault) R.string.root_auth_default_profile
                        else R.string.root_auth_custom_profile
                    ),
                    dot = false,
                )
                if (app.profile.umountModules)
                    AbkBadge(stringResource(R.string.root_auth_umount_modules), dot = false)
            }
        }
        AbkDivider()
        AbkSettingSwitch(
            title = stringResource(R.string.root_auth_title) + ": " + app.packageName,
            description =
                stringResource(
                    if (app.profile.allowSu) R.string.root_auth_allow_request
                    else R.string.root_auth_deny_request
                ),
            checked = app.profile.allowSu,
            enabled = !saving && !anySaving,
            onCheckedChange = onToggle,
        )
        if (saving) AbkSpinner(label = stringResource(R.string.save))
    }
}

@Composable
internal fun RootGrantProfilePage(
    app: RootGrantApp,
    topBarHeight: Dp,
    saving: Boolean,
    warning: String?,
    onSave: (RootGrantProfile) -> Unit,
    icon: @Composable () -> Unit = { AbkIcon(AbkIcons.Modules, null, size = 48.dp) },
) {
    val profile = app.profile
    var allowSu by rememberSaveable(app.packageName) { mutableStateOf(profile.allowSu) }
    var rootUseDefault by
        rememberSaveable(app.packageName) { mutableStateOf(profile.rootUseDefault) }
    var rootTemplate by rememberSaveable(app.packageName) { mutableStateOf(profile.rootTemplate) }
    var uidText by rememberSaveable(app.packageName) { mutableStateOf(profile.uid.toString()) }
    var gidText by rememberSaveable(app.packageName) { mutableStateOf(profile.gid.toString()) }
    var groupsText by
        rememberSaveable(app.packageName) { mutableStateOf(profile.groups.joinToString(",")) }
    var capabilitiesText by
        rememberSaveable(app.packageName) { mutableStateOf(profile.capabilities.joinToString(",")) }
    var contextText by rememberSaveable(app.packageName) { mutableStateOf(profile.context) }
    var namespaceText by
        rememberSaveable(app.packageName) { mutableStateOf(profile.namespace.toString()) }
    var nonRootUseDefault by
        rememberSaveable(app.packageName) { mutableStateOf(profile.nonRootUseDefault) }
    var umountModules by rememberSaveable(app.packageName) { mutableStateOf(profile.umountModules) }
    var rulesText by rememberSaveable(app.packageName) { mutableStateOf(profile.rules) }

    fun saveProfile() {
        onSave(
            profile.copy(
                name = app.packageName,
                currentUid = app.uid,
                allowSu = allowSu,
                rootUseDefault = rootUseDefault,
                rootTemplate = rootTemplate.trim(),
                uid = uidText.toIntOrNull() ?: 0,
                gid = gidText.toIntOrNull() ?: 0,
                groups = parseIntList(groupsText),
                capabilities = parseIntList(capabilitiesText),
                context = contextText.trim().ifBlank { "u:r:ksu:s0" },
                namespace = namespaceText.toIntOrNull() ?: 0,
                nonRootUseDefault = nonRootUseDefault,
                umountModules = umountModules,
                rules = rulesText,
            )
        )
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(topBarHeight + 16.dp))
        if (!warning.isNullOrBlank()) {
            RootGrantSection(
                stringResource(R.string.root_auth_profile_read_disabled_title),
                warning,
                AbkIcons.Warning,
            ) {}
        }
        RootGrantCard {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                icon()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AbkHeading(app.label.ifBlank { app.packageName })
                    SelectionContainer {
                        Text(
                            app.packageName,
                            fontFamily = AbkTheme.monoFont,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            AbkBadge("UID ${app.uid}", dot = false)
        }
        RootGrantSection(
            stringResource(R.string.root_auth_title),
            stringResource(
                if (allowSu) R.string.root_auth_allow_request else R.string.root_auth_deny_request
            ),
            AbkIcons.Controls,
        ) {
            AbkSettingSwitch(
                title = stringResource(R.string.root_auth_title),
                description =
                    stringResource(
                        if (allowSu) R.string.root_auth_allowed else R.string.root_auth_not_allowed
                    ),
                checked = allowSu,
                enabled = !saving,
                onCheckedChange = { allowSu = it },
            )
        }
        RootGrantSection(
            stringResource(R.string.root_auth_profile_title),
            stringResource(
                if (allowSu) {
                    if (rootUseDefault) R.string.root_auth_default else R.string.root_auth_custom
                } else {
                    if (nonRootUseDefault) R.string.root_auth_default_non_root
                    else R.string.root_auth_custom_non_root
                }
            ),
            AbkIcons.Settings,
        ) {
            if (allowSu) {
                AbkRadioGroup(
                    options =
                        listOf(
                            AbkOption(
                                "default",
                                stringResource(R.string.root_auth_default),
                                enabled = !saving,
                            ),
                            AbkOption(
                                "template",
                                stringResource(R.string.root_auth_template),
                                enabled = !saving,
                            ),
                            AbkOption(
                                "custom",
                                stringResource(R.string.root_auth_custom),
                                enabled = !saving,
                            ),
                        ),
                    selectedId =
                        if (rootUseDefault) "default"
                        else if (rootTemplate.isNotBlank()) "template" else "custom",
                    onSelect = { selected ->
                        when (selected) {
                            "default" -> rootUseDefault = true
                            "template" -> {
                                rootUseDefault = false
                                if (rootTemplate.isBlank()) rootTemplate = "default"
                            }
                            "custom" -> {
                                rootUseDefault = false
                                rootTemplate = ""
                            }
                        }
                    },
                )
                if (!rootUseDefault && rootTemplate.isNotBlank()) {
                    RootGrantTextField(
                        stringResource(R.string.root_auth_template),
                        rootTemplate,
                        { rootTemplate = it },
                        stringResource(R.string.root_auth_template_name),
                        enabled = !saving,
                    )
                }
                if (!rootUseDefault) {
                    RootGrantTextField("UID", uidText, { uidText = it }, enabled = !saving)
                    RootGrantTextField("GID", gidText, { gidText = it }, enabled = !saving)
                    RootGrantTextField(
                        "Groups",
                        groupsText,
                        { groupsText = it },
                        stringResource(R.string.root_auth_comma_separated),
                        enabled = !saving,
                    )
                    RootGrantTextField(
                        "Capabilities",
                        capabilitiesText,
                        { capabilitiesText = it },
                        stringResource(R.string.root_auth_comma_separated),
                        enabled = !saving,
                    )
                    RootGrantTextField(
                        "SELinux Context",
                        contextText,
                        { contextText = it },
                        enabled = !saving,
                    )
                    RootGrantTextField(
                        "Namespace",
                        namespaceText,
                        { namespaceText = it },
                        stringResource(R.string.root_auth_namespace_hint),
                        enabled = !saving,
                    )
                    RootGrantTextField(
                        "SEPolicy Rules",
                        rulesText,
                        { rulesText = it },
                        stringResource(R.string.root_auth_optional_empty),
                        singleLine = false,
                        enabled = !saving,
                    )
                }
            } else {
                AbkSettingSwitch(
                    stringResource(R.string.root_auth_use_default_non_root),
                    null,
                    nonRootUseDefault,
                    { nonRootUseDefault = it },
                    enabled = !saving,
                )
                AbkSettingSwitch(
                    stringResource(R.string.root_auth_umount_modules),
                    null,
                    umountModules,
                    { umountModules = it },
                    enabled = !saving,
                )
            }
        }
        FlashAction(
            stringResource(R.string.save),
            AbkIcons.Check,
            ::saveProfile,
            modifier = Modifier.fillMaxWidth(),
            style = AbkButtonStyle.Primary,
            enabled = !saving,
            loading = saving,
        )
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun RootGrantTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    AbkTextField(
        value,
        onValueChange,
        label,
        modifier = Modifier.fillMaxWidth(),
        hint = hint.takeIf { it.isNotBlank() },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = AbkTheme.monoFont),
    )
}

@Composable
internal fun RootGrantMessageCard(message: String, onRefresh: () -> Unit) {
    RootGrantCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AbkIcon(AbkIcons.Warning, null, tint = AbkTheme.colors.danger.color)
            Text(message, Modifier.weight(1f), color = AbkTheme.colors.danger.color)
        }
        FlashAction(stringResource(R.string.runtime_recheck), AbkIcons.Refresh, onRefresh)
    }
}

private fun parseIntList(value: String): List<Int> =
    value.split(',', ' ', '\n', '\t').mapNotNull { it.trim().toIntOrNull() }.distinct()
