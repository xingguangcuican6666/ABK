package com.abk.kernel.miuix.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.RootGrantApp
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

// ── Known groups for the groups dialog ──

private val knownGroups = listOf(
    GroupInfo("ROOT", 0),
    GroupInfo("SYSTEM", 1000),
    GroupInfo("SHELL", 2000),
    GroupInfo("CACHE", 2001),
    GroupInfo("LOG", 2007),
    GroupInfo("MEDIA_RW", 1023),
    GroupInfo("INET", 3003),
    GroupInfo("NET_ADMIN", 3004),
    GroupInfo("NET_RAW", 3005),
    GroupInfo("EVERYBODY", 9997),
)

private data class GroupInfo(val name: String, val gid: Int)

// ── Known capabilities for the capabilities dialog ──

private val knownCaps = listOf(
    CapInfo("CHOWN", 0),
    CapInfo("DAC_OVERRIDE", 1),
    CapInfo("DAC_READ_SEARCH", 2),
    CapInfo("FOWNER", 3),
    CapInfo("FSETID", 4),
    CapInfo("KILL", 5),
    CapInfo("SETGID", 6),
    CapInfo("SETUID", 7),
    CapInfo("SETPCAP", 8),
    CapInfo("NET_BIND_SERVICE", 10),
    CapInfo("NET_ADMIN", 12),
    CapInfo("NET_RAW", 13),
    CapInfo("IPC_LOCK", 14),
    CapInfo("IPC_OWNER", 15),
    CapInfo("SYS_MODULE", 16),
    CapInfo("SYS_RAWIO", 17),
    CapInfo("SYS_CHROOT", 18),
    CapInfo("SYS_PTRACE", 19),
    CapInfo("SYS_ADMIN", 21),
    CapInfo("SYS_BOOT", 22),
    CapInfo("SYS_NICE", 23),
    CapInfo("SYS_RESOURCE", 24),
    CapInfo("SYS_TIME", 25),
    CapInfo("MKNOD", 27),
    CapInfo("AUDIT_WRITE", 29),
    CapInfo("AUDIT_CONTROL", 30),
    CapInfo("SETFCAP", 31),
    CapInfo("SYSLOG", 34),
    CapInfo("WAKE_ALARM", 35),
)

private data class CapInfo(val name: String, val value: Int)

// ── Main screen ──

@Composable
fun SuperUserProfileScreenMiuix(
    vm: MainViewModel,
    uid: Int,
    onBack: () -> Unit,
) {
    val state by vm.uiState.collectAsState()

    // ── Find matching app(s) for this UID ──
    val matchingApps = remember(state.rootGrantApps, uid) {
        state.rootGrantApps.filter { it.uid == uid }
    }
    val primaryApp = matchingApps.firstOrNull()
    val profile = primaryApp?.profile

    // ── Editable profile state ──
    var allowSu by rememberSaveable(uid) { mutableStateOf(profile?.allowSu ?: true) }
    var profileType by rememberSaveable(uid) {
        mutableStateOf(
            when {
                profile?.rootUseDefault == true -> "default"
                profile?.rootTemplate?.isNotBlank() == true -> "template"
                else -> "custom"
            }
        )
    }
    var templateName by rememberSaveable(uid) { mutableStateOf(profile?.rootTemplate ?: "") }
    var uidText by rememberSaveable(uid) { mutableStateOf(profile?.uid?.toString() ?: uid.toString()) }
    var gidText by rememberSaveable(uid) { mutableStateOf(profile?.gid?.toString() ?: "") }
    var groupsList by rememberSaveable(uid) { mutableStateOf(profile?.groups ?: emptyList()) }
    var capsList by rememberSaveable(uid) { mutableStateOf(profile?.capabilities ?: emptyList()) }
    var contextText by rememberSaveable(uid) { mutableStateOf(profile?.context ?: "") }
    var namespaceIndex by rememberSaveable(uid) { mutableStateOf(profile?.namespace ?: 0) }
    var umountModules by rememberSaveable(uid) { mutableStateOf(profile?.umountModules ?: true) }
    var nonRootUseDefault by rememberSaveable(uid) { mutableStateOf(profile?.nonRootUseDefault ?: true) }

    // ── Dialog visibility states ──
    var showUidDialog by remember { mutableStateOf(false) }
    var showGidDialog by remember { mutableStateOf(false) }
    var showGroupsDialog by remember { mutableStateOf(false) }
    var showCapsDialog by remember { mutableStateOf(false) }
    var showSelinuxDialog by remember { mutableStateOf(false) }
    var showMorePopup by remember { mutableStateOf(false) }

    // ── Profile mode options ──
    val profileModeOptions = if (allowSu) {
        listOf(
            stringResource(R.string.root_auth_default),
            stringResource(R.string.root_auth_template),
            stringResource(R.string.root_auth_custom),
        )
    } else {
        listOf(
            stringResource(R.string.root_auth_default),
            stringResource(R.string.root_auth_custom),
        )
    }
    val currentModeIndex = remember(profileType, allowSu) {
        when {
            profileType == "default" -> 0
            profileType == "template" && allowSu -> 1
            profileType == "custom" && allowSu -> 2
            profileType == "custom" && !allowSu -> 1
            else -> 0
        }
    }

    // ── Save function (instant save) ──
    fun doSave() {
        val app = primaryApp ?: return
        if (profile == null) return
        val gid = gidText.toIntOrNull() ?: uid
        val updatedProfile = profile.copy(
            name = app.packageName,
            currentUid = uid,
            allowSu = allowSu,
            rootUseDefault = profileType == "default",
            rootTemplate = if (profileType == "template") templateName.trim() else "",
            uid = uid,
            gid = gid,
            groups = groupsList,
            capabilities = capsList,
            context = contextText.trim().ifBlank { "u:r:ksu:s0" },
            namespace = namespaceIndex,
            umountModules = umountModules,
            nonRootUseDefault = nonRootUseDefault,
        )
        vm.saveRootGrantProfile(updatedProfile)
    }

    // ── Blur ──
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)
    val barColor = if (backdrop != null) Color.Transparent else surfaceColor

    // ── Scroll behavior ──
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            BlurredBar(backdrop, surfaceColor) {
                TopAppBar(
                    color = barColor,
                    title = "App Profile",
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.settings_back),
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(
                                onClick = { showMorePopup = true },
                                holdDownState = showMorePopup,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.MoreCircle,
                                    tint = MiuixTheme.colorScheme.onSurface,
                                    contentDescription = "More",
                                )
                            }
                            OverlayListPopup(
                                show = showMorePopup,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                alignment = PopupPositionProvider.Align.TopEnd,
                                onDismissRequest = { showMorePopup = false },
                            ) {
                                ListPopupColumn {
                                    listOf("Launch", "Force Stop", "Restart").forEachIndexed { index, text ->
                                        DropdownImpl(
                                            text = text,
                                            optionSize = 3,
                                            isSelected = false,
                                            index = index,
                                            onSelectedIndexChange = { showMorePopup = false },
                                        )
                                    }
                                }
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier.then(
                if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
            )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                ),
                overscrollEffect = null,
            ) {
                if (primaryApp != null) {
                    // ── Header card ──
                    item(key = "header") {
                        AppHeaderCard(primaryApp = primaryApp)
                    }

                    // ── Superuser toggle ──
                    item(key = "superuser_toggle") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                        ) {
                            SwitchPreference(
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                        tint = MiuixTheme.colorScheme.onBackground,
                                    )
                                },
                                title = stringResource(R.string.root_auth_title),
                                checked = allowSu,
                                onCheckedChange = {
                                    allowSu = it
                                    doSave()
                                },
                            )
                        }
                    }

                    // ── Profile mode selector ──
                    item(key = "profile_mode") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                        ) {
                            OverlayDropdownPreference(
                                title = "App Profile",
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                        tint = MiuixTheme.colorScheme.onBackground,
                                    )
                                },
                                items = profileModeOptions,
                                selectedIndex = currentModeIndex,
                                renderInRootScaffold = true,
                                onSelectedIndexChange = { index ->
                                    profileType = when {
                                        allowSu && index == 0 -> "default"
                                        allowSu && index == 1 -> "template"
                                        allowSu && index == 2 -> "custom"
                                        !allowSu && index == 0 -> "default"
                                        !allowSu && index == 1 -> "custom"
                                        else -> "default"
                                    }
                                    if (profileType == "template" && templateName.isBlank()) {
                                        templateName = "default"
                                    }
                                    doSave()
                                },
                            )
                        }
                    }

                    // ── Template config ──
                    item(key = "template_config") {
                        AnimatedVisibility(
                            visible = allowSu && profileType == "template",
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                            ) {
                                Column {
                                    OverlayDropdownPreference(
                                        title = stringResource(R.string.root_auth_template),
                                        items = listOf(templateName.ifBlank { stringResource(R.string.root_auth_default) }),
                                        selectedIndex = 0,
                                        renderInRootScaffold = true,
                                        onSelectedIndexChange = { },
                                    )
                                    ArrowPreference(
                                        title = stringResource(R.string.settings_app_profile_templates),
                                        onClick = { /* view template details */ },
                                    )
                                }
                            }
                        }
                    }

                    // ── Custom config ──
                    item(key = "custom_config") {
                        AnimatedVisibility(
                            visible = allowSu && profileType == "custom",
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(top = 12.dp, bottom = 12.dp),
                            ) {
                                Column {
                                    ArrowPreference(
                                        title = "UID",
                                        summary = uidText,
                                        onClick = { showUidDialog = true },
                                    )
                                    ArrowPreference(
                                        title = "GID",
                                        summary = gidText.ifBlank { uid.toString() },
                                        onClick = { showGidDialog = true },
                                    )
                                    ArrowPreference(
                                        title = "Groups",
                                        summary = groupsList.joinToString(",").take(50),
                                        onClick = { showGroupsDialog = true },
                                    )
                                    ArrowPreference(
                                        title = "Capabilities",
                                        summary = capsList.joinToString(",").take(50),
                                        onClick = { showCapsDialog = true },
                                    )
                                    ArrowPreference(
                                        title = "SELinux Context",
                                        summary = contextText.ifBlank { "u:r:ksu:s0" },
                                        onClick = { showSelinuxDialog = true },
                                    )
                                    OverlayDropdownPreference(
                                        title = "Mount Namespace",
                        items = listOf("Inherited", "Global", "Individual"),
                                        selectedIndex = namespaceIndex,
                                        renderInRootScaffold = true,
                                        onSelectedIndexChange = { index ->
                                            namespaceIndex = index
                                            doSave()
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // ── Non-root config ──
                    item(key = "nonroot_config") {
                        AnimatedVisibility(
                            visible = !allowSu,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(top = 12.dp, bottom = 12.dp),
                            ) {
                                AnimatedVisibility(
                                    visible = profileType != "default",
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically(),
                                ) {
                                    SwitchPreference(
                                        title = "Umount modules",
                                        summary = "Unmount modules when app is in background",
                                        checked = umountModules,
                                        onCheckedChange = {
                                            umountModules = it
                                            doSave()
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // ── UID group: affected apps ──
                    if (matchingApps.size > 1) {
                        item(key = "affected_title") {
                            SmallTitle(
                                text = "Affects following apps",
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        item(key = "affected_apps") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp),
                            ) {
                                Column {
                                    Spacer(Modifier.height(3.dp))
                                    matchingApps.forEach { app ->
                                        BasicComponent(
                                            startAction = {
                                                AppIconImage(
                                                    packageName = app.packageName,
                                                    label = app.label,
                                                    modifier = Modifier.size(48.dp),
                                                )
                                            },
                                            title = app.label,
                                            summary = app.packageName,
                                            insideMargin = PaddingValues(
                                                start = 11.dp,
                                                end = 16.dp,
                                                top = 8.dp,
                                                bottom = 8.dp,
                                            ),
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                }
                            }
                        }
                    }

                    // ── Bottom spacer ──
                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(60.dp))
                    }
                } else {
                    // ── Empty state ──
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.root_auth_no_apps),
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ──

    val dialogTextColor = MiuixTheme.colorScheme.onSurface
    val dialogTextFieldBg = MiuixTheme.colorScheme.surfaceVariant
    val dialogPrimaryColor = MiuixTheme.colorScheme.primary

    if (showUidDialog) {
        var editText by remember(uidText) { mutableStateOf(uidText) }
        WindowDialog(
            show = true,
            title = "UID",
            onDismissRequest = { showUidDialog = false },
        ) {
            Card {
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(dialogTextFieldBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    singleLine = true,
                    textStyle = MiuixTheme.textStyles.body1.copy(color = dialogTextColor),
                    cursorBrush = SolidColor(dialogPrimaryColor),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showUidDialog = false },
                )
                Button(
                    onClick = {
                        uidText = editText
                        showUidDialog = false
                        doSave()
                    },
                    colors = ButtonDefaults.buttonColors(
                        color = dialogPrimaryColor,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    if (showGidDialog) {
        var editText by remember(gidText) { mutableStateOf(gidText) }
        WindowDialog(
            show = true,
            title = "GID",
            onDismissRequest = { showGidDialog = false },
        ) {
            Card {
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(dialogTextFieldBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    singleLine = true,
                    textStyle = MiuixTheme.textStyles.body1.copy(color = dialogTextColor),
                    cursorBrush = SolidColor(dialogPrimaryColor),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showGidDialog = false },
                )
                Button(
                    onClick = {
                        gidText = editText
                        showGidDialog = false
                        doSave()
                    },
                    colors = ButtonDefaults.buttonColors(
                        color = dialogPrimaryColor,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    if (showGroupsDialog) {
        var selected by remember(groupsList) { mutableStateOf(groupsList.toSet()) }
        WindowDialog(
            show = true,
            title = "Groups",
            onDismissRequest = { showGroupsDialog = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Card {
                    knownGroups.forEach { group ->
                        val isChecked = group.gid in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (isChecked) {
                                        selected - group.gid
                                    } else {
                                        selected + group.gid
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isChecked) "\u2611" else "\u2610",
                                color = if (isChecked) dialogPrimaryColor else dialogTextColor,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.name,
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "GID ${group.gid}",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showGroupsDialog = false },
                )
                Button(
                    onClick = {
                        groupsList = selected.toList()
                        showGroupsDialog = false
                        doSave()
                    },
                    colors = ButtonDefaults.buttonColors(
                        color = dialogPrimaryColor,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    if (showCapsDialog) {
        var selected by remember(capsList) { mutableStateOf(capsList.toSet()) }
        WindowDialog(
            show = true,
            title = "Capabilities",
            onDismissRequest = { showCapsDialog = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Card {
                    knownCaps.forEach { cap ->
                        val isChecked = cap.value in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (isChecked) {
                                        selected - cap.value
                                    } else {
                                        selected + cap.value
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isChecked) "\u2611" else "\u2610",
                                color = if (isChecked) dialogPrimaryColor else dialogTextColor,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cap.name,
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "cap ${cap.value}",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showCapsDialog = false },
                )
                Button(
                    onClick = {
                        capsList = selected.toList()
                        showCapsDialog = false
                        doSave()
                    },
                    colors = ButtonDefaults.buttonColors(
                        color = dialogPrimaryColor,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    if (showSelinuxDialog) {
        var editContext by remember(contextText) { mutableStateOf(contextText) }
        WindowDialog(
            show = true,
            title = "SELinux Context",
            onDismissRequest = { showSelinuxDialog = false },
        ) {
            Card {
                BasicTextField(
                    value = editContext,
                    onValueChange = { editContext = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(dialogTextFieldBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    singleLine = true,
                    textStyle = MiuixTheme.textStyles.body1.copy(color = dialogTextColor),
                    cursorBrush = SolidColor(dialogPrimaryColor),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (editContext.isEmpty()) {
                                Text(
                                    text = "u:r:ksu:s0",
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showSelinuxDialog = false },
                )
                Button(
                    onClick = {
                        contextText = editContext
                        showSelinuxDialog = false
                        doSave()
                    },
                    colors = ButtonDefaults.buttonColors(
                        color = dialogPrimaryColor,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

// ── App header card ──

@Composable
private fun AppHeaderCard(primaryApp: RootGrantApp) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        insideMargin = PaddingValues(
            start = 12.dp,
            end = 16.dp,
            top = 10.dp,
            bottom = 10.dp,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(
                packageName = primaryApp.packageName,
                label = primaryApp.label,
                modifier = Modifier.size(64.dp),
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp, end = 8.dp)
                    .weight(1f),
            ) {
                Text(
                    text = primaryApp.label.ifBlank { primaryApp.packageName },
                    fontWeight = FontWeight(550),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.basicMarquee(),
                )
                Text(
                    text = primaryApp.packageName,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.basicMarquee(),
                )
            }
            // UID status tag
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.8f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "UID ${primaryApp.uid}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight(750),
                        color = MiuixTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

// ── App icon loader ──

@Composable
private fun AppIconImage(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val drawable = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }
    val iconModifier = modifier.clip(RoundedCornerShape(12.dp))

    if (drawable != null) {
        val bitmap = remember(drawable) {
            val bmp = runCatching {
                Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
            }.getOrNull()
            if (bmp != null) {
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }
            bmp
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label.ifBlank { packageName },
                modifier = iconModifier,
            )
        } else {
            AppIconPlaceholder(modifier = iconModifier, label = label)
        }
    } else {
        AppIconPlaceholder(modifier = iconModifier, label = label)
    }
}

@Composable
private fun AppIconPlaceholder(
    modifier: Modifier = Modifier,
    label: String = "",
) {
    Box(
        modifier = modifier.background(
            MiuixTheme.colorScheme.primaryContainer,
            RoundedCornerShape(12.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (label.firstOrNull()?.toString() ?: "?").uppercase(),
            color = MiuixTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
    }
}
