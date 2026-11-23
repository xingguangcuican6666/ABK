package io.github.xingguangcuican6666.abk.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

@Composable
public fun AbkDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    dismissDescription: String = "关闭对话框",
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    tone: AbkTone? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AbkTheme.colors
    Dialog(onDismissRequest, properties = DialogProperties(dismissOnBackPress, dismissOnClickOutside, usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(c.scrim.alpha) }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(modifier.widthIn(max = if (wide) 640.dp else 420.dp).fillMaxWidth().heightIn(max = maxHeight * 0.85f)
                .semantics { paneTitle = title }, shape = AbkShapes.ExtraLarge, color = c.raised,
                contentColor = c.ink, border = BorderStroke(1.dp, c.line)) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // The mark sits beside the title, not above it, so long titles wrap under themselves.
                        // It is outlined rather than a filled disc so the glyph, not a coloured badge, names the state.
                        if (tone != null) AbkStatusGlyph(tone, Modifier.padding(end = 10.dp), size = 24.dp)
                        AbkText(title, Modifier.weight(1f).semantics { heading() }, MaterialTheme.typography.titleLarge)
                        AbkIconButton(AbkIcons.Close, dismissDescription, onDismissRequest)
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                    FlowRow(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AbkBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    dismissDescription: String = "关闭弹层",
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AbkTheme.colors
    ModalBottomSheet(onDismissRequest, modifier, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), containerColor = c.raised,
        contentColor = c.ink, scrimColor = c.scrim) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AbkHeading(title)
                if (description != null) AbkText(description, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
            }
            AbkIconButton(AbkIcons.Close, dismissDescription, onDismissRequest)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        AbkDivider()
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

public sealed interface AbkMenuEntry {
    public data class Action(
        val id: String, val label: String, val icon: ImageVector? = null, val shortcut: String? = null,
        val enabled: Boolean = true, val danger: Boolean = false,
    ) : AbkMenuEntry
    public data class Label(val text: String) : AbkMenuEntry
    public data object Divider : AbkMenuEntry
}

/** Place alongside its anchor inside a Box. Selection dismisses first; the host handles the action. */
@Composable
public fun AbkMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    entries: List<AbkMenuEntry>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AbkTheme.colors
    val actions = entries.filterIsInstance<AbkMenuEntry.Action>()
    require(actions.map { it.id }.distinct().size == actions.size) { "Menu action IDs must be unique" }
    DropdownMenu(expanded, onDismissRequest, modifier.widthIn(min = 184.dp), shape = AbkShapes.Medium,
        containerColor = c.raised, border = BorderStroke(1.dp, c.line)) {
        entries.forEach { entry ->
            when (entry) {
                is AbkMenuEntry.Label -> AbkText(entry.text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { heading() },
                    MaterialTheme.typography.labelSmall, c.inkMuted)
                AbkMenuEntry.Divider -> AbkDivider(Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                is AbkMenuEntry.Action -> {
                    val color = if (entry.danger) c.danger.color else c.inkSoft
                    DropdownMenuItem(
                        text = { AbkText(entry.label, style = MaterialTheme.typography.bodyMedium,
                            color = if (entry.enabled) color else c.inkFaint) },
                        onClick = { onDismissRequest(); onSelect(entry.id) },
                        modifier = Modifier.heightIn(min = 48.dp), enabled = entry.enabled,
                        leadingIcon = entry.icon?.let { icon -> ({ AbkIcon(icon, null) }) },
                        trailingIcon = entry.shortcut?.let { shortcut -> ({ AbkKeycap(shortcut) }) },
                        colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color, disabledTextColor = c.inkFaint),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AbkTooltip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = AbkTheme.colors
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(shape = AbkShapes.Small, containerColor = c.ink, contentColor = c.raised) {
                AbkText(text, style = MaterialTheme.typography.bodySmall, color = c.raised)
            }
        }, state = rememberTooltipState(), modifier = modifier, content = content,
    )
}
