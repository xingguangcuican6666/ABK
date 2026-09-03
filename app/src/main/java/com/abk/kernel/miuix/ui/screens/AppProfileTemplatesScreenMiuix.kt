package com.abk.kernel.miuix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun AppProfileTemplatesScreenMiuix(vm: MainViewModel) {
    val navigator = LocalNavigator.current
    val state by vm.uiState.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()

    // Local editor state
    var editingId by rememberSaveable { mutableStateOf("") }
    var editingContent by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    // Blur backdrop
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)
    val barColor = if (backdrop != null) Color.Transparent else surfaceColor

    // Sync editor when ViewModel loads template content
    LaunchedEffect(state.selectedAppProfileTemplateId, state.selectedAppProfileTemplateContent) {
        if (!state.selectedAppProfileTemplateId.isNullOrBlank()) {
            creating = false
            editingId = state.selectedAppProfileTemplateId.orEmpty()
            editingContent = state.selectedAppProfileTemplateContent
        }
    }

    // Load templates on first composition
    LaunchedEffect(Unit) {
        vm.refreshAppProfileTemplates()
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, surfaceColor) {
                TopAppBar(
                    title = stringResource(R.string.settings_app_profile_templates),
                    scrollBehavior = scrollBehavior,
                    color = barColor,
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
        }
    ) { padding ->
        Box(
            modifier = Modifier.then(
                if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
            )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .overScrollVertical()
                    .scrollEndHaptic(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // ── Card 1: 本地模板 ──
                item(key = "template_list") {
                    Card {
                        SmallTitle(stringResource(R.string.settings_local_templates))

                        // Loading state
                        if (state.appProfileTemplatesLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    progress = null,
                                    strokeWidth = 2.dp,
                                    size = 20.dp
                                )
                                Text(
                                    text = stringResource(R.string.settings_templates_loading_desc),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }

                        // Empty state
                        if (!state.appProfileTemplatesLoading && state.appProfileTemplates.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_templates_empty),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        // Template rows
                        state.appProfileTemplates.forEach { template ->
                            ArrowPreference(
                                title = template.id,
                                summary = if (state.selectedAppProfileTemplateId == template.id)
                                    stringResource(R.string.settings_template_editing)
                                else
                                    stringResource(R.string.settings_app_profile_templates),
                                onClick = { vm.selectAppProfileTemplate(template.id) }
                            )
                        }

                        // Create new template
                        ArrowPreference(
                            title = stringResource(R.string.settings_new_template),
                            summary = stringResource(R.string.settings_new_template_desc),
                            onClick = {
                                creating = true
                                editingId = ""
                                editingContent = defaultAppProfileTemplateJson()
                                vm.selectAppProfileTemplate(null)
                            }
                        )
                    }
                }

                // ── Card 2: 错误状态 (conditional) ──
                if (state.appProfileTemplatesError != null) {
                    item(key = "error") {
                        Card {
                            SmallTitle(stringResource(R.string.settings_status))
                            Text(
                                text = stringResource(R.string.settings_operation_incomplete) + "\n" +
                                    (state.appProfileTemplatesError ?: ""),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.error
                            )
                        }
                    }
                }

                // ── Card 3: 编辑模板 (conditional) ──
                val hasEditor = creating || !state.selectedAppProfileTemplateId.isNullOrBlank()
                if (hasEditor) {
                    item(key = "editor") {
                        Card {
                            SmallTitle(stringResource(R.string.settings_edit_template))

                            Spacer(Modifier.height(4.dp))

                            // Template name input
                            MiuixTextInput(
                                value = editingId,
                                onValueChange = { editingId = it },
                                label = stringResource(R.string.settings_template_name),
                                singleLine = true,
                                enabled = state.selectedAppProfileTemplateId.isNullOrBlank()
                            )

                            Spacer(Modifier.height(12.dp))

                            // JSON content input
                            MiuixTextInput(
                                value = editingContent,
                                onValueChange = { editingContent = it },
                                label = stringResource(R.string.settings_template_json),
                                singleLine = false,
                                minLines = 8,
                                modifier = Modifier.heightIn(min = 220.dp)
                            )

                            Spacer(Modifier.height(12.dp))

                            // Action buttons row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (state.appProfileTemplateSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        progress = null,
                                        strokeWidth = 2.dp,
                                        size = 22.dp
                                    )
                                }
                                if (!state.selectedAppProfileTemplateId.isNullOrBlank()) {
                                    TextButton(
                                        text = stringResource(R.string.delete),
                                        onClick = { showDeleteDialog = true },
                                        colors = ButtonDefaults.textButtonColors(
                                            textColor = MiuixTheme.colorScheme.error
                                        )
                                    )
                                }
                                Button(
                                    onClick = { vm.saveAppProfileTemplate(editingId, editingContent) },
                                    enabled = !state.appProfileTemplateSaving && editingId.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        color = MiuixTheme.colorScheme.primary,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(stringResource(R.string.save))
                                }
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(Modifier.height(60.dp))
                }
            }
        }
    }

    // ── Delete Confirmation Dialog ──
    if (showDeleteDialog) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.delete),
            onDismissRequest = { showDeleteDialog = false }
        ) {
            Card {
                Text(
                    text = state.selectedAppProfileTemplateId.orEmpty(),
                    style = MiuixTheme.textStyles.body2
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showDeleteDialog = false }
                )
                Button(
                    onClick = {
                        val id = state.selectedAppProfileTemplateId.orEmpty()
                        vm.deleteAppProfileTemplate(id)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

/**
 * Default JSON template skeleton for new profiles.
 * Duplicated from MD3 SettingsScreen.kt (private function there).
 */
private fun defaultAppProfileTemplateJson(): String =
    """
    {
      "uid": 0,
      "gid": 0,
      "groups": [],
      "capabilities": [],
      "context": "u:r:ksu:s0",
      "namespace": 0,
      "rules": ""
    }
    """.trimIndent()

/**
 * Miuix-themed text input field using [BasicTextField] with Miuix colors.
 * Avoids Material3's [OutlinedTextField] which uses MD3 theming and breaks fonts/colors
 * in MIUIX dark mode.
 */
@Composable
private fun MiuixTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MiuixTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(17.dp)
                )
                .border(
                    width = 1.dp,
                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(17.dp)
                )
                .padding(horizontal = 16.dp, vertical = if (singleLine) 14.dp else 12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = MiuixTheme.textStyles.body1.copy(
                    color = if (enabled) {
                        MiuixTheme.colorScheme.onSurface
                    } else {
                        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                ),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            )
        }
    }
}
