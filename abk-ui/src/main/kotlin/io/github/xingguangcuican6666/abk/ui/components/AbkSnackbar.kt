package io.github.xingguangcuican6666.abk.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

private data class AbkSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    override val withDismissAction: Boolean,
    override val duration: SnackbarDuration,
    val tone: AbkTone,
) : SnackbarVisuals

/** Material's mutex queues requests, cancellation removes them, and each request returns one result. */
@Stable
public class AbkSnackbarHostState {
    internal val material: SnackbarHostState = SnackbarHostState()

    public suspend fun show(
        message: String,
        tone: AbkTone = AbkTone.Neutral,
        actionLabel: String? = null,
        withDismissAction: Boolean = false,
        duration: SnackbarDuration = if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
    ): SnackbarResult = material.showSnackbar(AbkSnackbarVisuals(message, actionLabel, withDismissAction, duration, tone))

    public fun dismissCurrent() { material.currentSnackbarData?.dismiss() }
}

@Composable
public fun rememberAbkSnackbarHostState(): AbkSnackbarHostState = remember { AbkSnackbarHostState() }

@Composable
public fun AbkSnackbarHost(
    state: AbkSnackbarHostState,
    modifier: Modifier = Modifier,
    dismissDescription: String = "关闭提示",
) {
    val c = AbkTheme.colors
    // SnackbarHost retains accessibility-aware timeout, live-region and dismiss semantics.
    SnackbarHost(state.material, modifier) { data ->
        val tone = (data.visuals as? AbkSnackbarVisuals)?.tone ?: AbkTone.Neutral
        Surface(Modifier.padding(12.dp).fillMaxWidth(), shape = AbkShapes.Large,
            color = c.raised, contentColor = c.ink, border = BorderStroke(1.dp, c.line)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Same outlined glyph as alerts and dialogs; neutral falls back to a subdued dot.
                    AbkStatusGlyph(tone, size = 22.dp)
                    AbkText(data.visuals.message, Modifier.weight(1f), MaterialTheme.typography.bodyMedium)
                    if (data.visuals.withDismissAction) AbkIconButton(AbkIcons.Close, dismissDescription, data::dismiss, size = AbkSize.Small)
                }
                if (data.visuals.actionLabel != null) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AbkButton(data::performAction, style = AbkButtonStyle.Ghost, size = AbkSize.Small) {
                        AbkText(data.visuals.actionLabel!!, color = c.accent.color, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
