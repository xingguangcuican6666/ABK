package com.abk.kernel.miuix.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun ManagerToolsScreenMiuix(vm: MainViewModel) {
    val navigator = LocalNavigator.current
    val state by vm.uiState.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()

    val selinuxBusy = state.managerToolActionId == "selinux_mode"
    val backupBusy = state.managerToolActionId == "backup_allowlist"
    val restoreBusy = state.managerToolActionId == "restore_allowlist"

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) vm.backupRootGrantAllowlist(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.restoreRootGrantAllowlist(uri)
    }

    LaunchedEffect(Unit) {
        vm.refreshManagerTools(force = true)
    }

    val selinuxMode = selinuxModeLabel(state.selinuxModeText)

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_tools),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .overScrollVertical()
                .scrollEndHaptic(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.dp,
                vertical = 16.dp
            )
        ) {
            // Card 1: System tools
            item(key = "system_tools") {
                Card {
                    SmallTitle(stringResource(R.string.settings_system_tools))
                    SwitchPreference(
                        title = stringResource(R.string.settings_selinux_mode),
                        summary = stringResource(R.string.settings_current_value, selinuxMode),
                        checked = state.selinuxEnforcing,
                        onCheckedChange = { vm.setSelinuxEnforcing(it) },
                        enabled = !state.managerToolsLoading && !selinuxBusy
                    )
                    if (state.umountPaths.isNotEmpty()) {
                        HorizontalDivider()
                        SmallTitle(stringResource(R.string.settings_umount_paths))
                        state.umountPaths.forEach { path ->
                            ArrowPreference(
                                title = path,
                                enabled = false
                            )
                        }
                    }
                }
            }

            // Card 2: Allowlist
            item(key = "allowlist") {
                Card {
                    SmallTitle(stringResource(R.string.settings_allowlist))
                    ArrowPreference(
                        title = stringResource(R.string.settings_backup_allowlist),
                        summary = stringResource(R.string.settings_backup_allowlist_desc),
                        enabled = !backupBusy,
                        onClick = {
                            if (!backupBusy) backupLauncher.launch("abk-root-allowlist.json")
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_restore_allowlist),
                        summary = stringResource(R.string.settings_restore_allowlist_desc),
                        enabled = !restoreBusy,
                        onClick = {
                            if (!restoreBusy) {
                                restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            }
                        }
                    )
                }
            }

            // Card 3: Error status (conditional)
            if (state.managerToolsError != null) {
                item(key = "error_status") {
                    Card {
                        SmallTitle(stringResource(R.string.settings_tool_status))
                        Text(
                            text = stringResource(R.string.settings_operation_incomplete) + "\n" +
                                (state.managerToolsError ?: ""),
                            color = MiuixTheme.colorScheme.error
                        )
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun selinuxModeLabel(mode: String): String =
    when (mode.trim().lowercase()) {
        "enforcing" -> stringResource(R.string.settings_selinux_enforcing)
        "permissive" -> stringResource(R.string.settings_selinux_permissive)
        "disabled" -> stringResource(R.string.settings_selinux_disabled)
        else -> mode.ifBlank { stringResource(R.string.settings_unknown) }
    }
