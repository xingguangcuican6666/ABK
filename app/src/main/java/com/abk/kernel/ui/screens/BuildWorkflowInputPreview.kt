package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.viewmodel.toInputMap
import io.github.xingguangcuican6666.abk.ui.components.AbkPanel
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
internal fun BuildWorkflowInputPreview(config: KernelBuildConfig, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val redacted = stringResource(R.string.build_preview_redacted)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BuildActionButton(
            label = stringResource(R.string.build_dispatch_preview),
            icon = AbkIcons.Terminal,
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            Text(
                stringResource(R.string.build_dispatch_preview_hint),
                style = MaterialTheme.typography.bodySmall,
                color = AbkTheme.colors.inkMuted,
            )
            AbkPanel {
                SelectionContainer {
                    Text(
                        config.toInputMap().entries.joinToString("\n\n") { (key, value) ->
                            val displayed =
                                if (key == "kpm_password" && value.isNotEmpty()) {
                                    redacted
                                } else value
                            "$key = $displayed"
                        },
                        fontFamily = AbkTheme.monoFont,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
