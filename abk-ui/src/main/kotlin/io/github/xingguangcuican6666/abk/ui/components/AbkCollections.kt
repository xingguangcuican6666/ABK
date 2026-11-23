package io.github.xingguangcuican6666.abk.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

public enum class AbkCardStyle { Default, Flat, Sunken }

@Composable
public fun AbkCard(
    modifier: Modifier = Modifier,
    style: AbkCardStyle = AbkCardStyle.Default,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    media: (@Composable () -> Unit)? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AbkTheme.colors
    val color = when (style) { AbkCardStyle.Default -> c.raised; AbkCardStyle.Flat -> Color.Transparent; AbkCardStyle.Sunken -> c.sunken }
    val body: @Composable () -> Unit = {
        Column {
            media?.invoke()
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                header?.invoke(this)
                content()
                if (footer != null) {
                    AbkDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = footer)
                }
            }
        }
    }
    if (onClick == null) {
        Surface(modifier, shape = AbkShapes.Large, color = color, contentColor = c.ink, border = BorderStroke(1.dp, c.line), content = body)
    } else {
        Surface(onClick, modifier, enabled = enabled, shape = AbkShapes.Large, color = color, contentColor = c.ink,
            border = BorderStroke(1.dp, c.line), content = body)
    }
}

@Composable
public fun AbkList(modifier: Modifier = Modifier, plain: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = if (plain) AbkShapes.Small else AbkShapes.Large,
        color = if (plain) Color.Transparent else AbkTheme.colors.raised, contentColor = AbkTheme.colors.ink,
        border = if (plain) null else BorderStroke(1.dp, AbkTheme.colors.line)) {
        Column(content = content)
    }
}

@Composable
public fun AbkListItem(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
        .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
        .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AbkText(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) AbkTheme.colors.ink else AbkTheme.colors.inkFaint)
            if (description != null) AbkText(description, style = MaterialTheme.typography.bodyMedium, color = AbkTheme.colors.inkMuted)
        }
        trailing?.invoke()
    }
}

@Composable
public fun AbkListSectionTitle(title: String, modifier: Modifier = Modifier) {
    AbkText(title, modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
        MaterialTheme.typography.labelMedium, AbkTheme.colors.inkMuted)
}

@Composable
public fun AbkAccordion(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expandedDescription: String = "已展开",
    collapsedDescription: String = "已收起",
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier, shape = AbkShapes.Large, color = AbkTheme.colors.raised, border = BorderStroke(1.dp, AbkTheme.colors.line)) {
        Column {
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
                .clickable(enabled = enabled, role = Role.Button) { onExpandedChange(!expanded) }
                .semantics { stateDescription = if (expanded) expandedDescription else collapsedDescription }
                .padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                AbkText(title, Modifier.weight(1f), MaterialTheme.typography.titleSmall,
                    if (enabled) AbkTheme.colors.ink else AbkTheme.colors.inkFaint)
                AbkIcon(AbkIcons.Plus, null, Modifier.rotate(if (expanded) 45f else 0f),
                    tint = if (expanded) AbkTheme.colors.accent.color else AbkTheme.colors.inkMuted)
            }
            if (expanded) Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
public fun AbkBadge(text: String, modifier: Modifier = Modifier, tone: AbkTone = AbkTone.Neutral, dot: Boolean = true) {
    val colors = AbkTheme.colors.status(tone)
    Row(modifier.background(colors.container, AbkShapes.Pill).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot) Box(Modifier.size(6.dp).background(colors.onContainer, CircleShape))
        Text(text, color = colors.onContainer, style = MaterialTheme.typography.labelSmall)
    }
}

/** A selectable tag has one toggle target; removable tags use a separate, non-overlapping target. */
@Composable
public fun AbkChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = AbkTheme.colors
    Surface(modifier, shape = AbkShapes.Pill, color = if (selected) c.accent.container else c.raised,
        border = BorderStroke(1.dp, if (selected) c.accent.color else c.line)) {
        Row(Modifier.defaultMinSize(minHeight = 48.dp)
            .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = onSelectedChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val foreground = if (!enabled) c.inkFaint else if (selected) c.accent.onContainer else c.inkSoft
            if (icon != null) AbkIcon(icon, null, tint = foreground, size = 18.dp)
            AbkText(label, style = MaterialTheme.typography.bodyMedium, color = foreground)
        }
    }
}

@Composable
public fun AbkRemovableChip(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    removeDescription: String = "移除 $label",
) {
    Surface(modifier, shape = AbkShapes.Pill, color = AbkTheme.colors.raised, border = BorderStroke(1.dp, AbkTheme.colors.line)) {
        Row(Modifier.defaultMinSize(minHeight = 48.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AbkText(label, Modifier.weight(1f, fill = false), MaterialTheme.typography.bodyMedium)
            AbkIconButton(AbkIcons.Close, removeDescription, onRemove, enabled = enabled, size = AbkSize.Small)
        }
    }
}
