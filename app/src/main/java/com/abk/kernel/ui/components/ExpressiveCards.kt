package com.abk.kernel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.abk.kernel.ui.blur.blurredCardBackground
import com.abk.kernel.ui.blur.blurredCardSurfaceColor
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkEmptyState
import io.github.xingguangcuican6666.abk.ui.components.AbkListItem
import io.github.xingguangcuican6666.abk.ui.components.AbkSwitch
import io.github.xingguangcuican6666.abk.ui.components.AbkSettingSwitch
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

/** Host adapters keep backgrounds and business slots outside the reusable library. */
@Composable
fun ExpressiveHeroCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = AbkTheme.colors.raised,
    contentColor: Color = AbkTheme.colors.ink,
    badge: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    AbkCard(
        modifier = modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
        containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised),
        contentColor = AbkTheme.colors.ink,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = AbkTheme.colors.inkMuted)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AbkTheme.colors.inkMuted)
            }
        }
        if (badge != null) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = badge,
        )
        content()
    }
}

@Composable
fun ExpressiveSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    containerColor: Color = AbkTheme.colors.raised,
    content: @Composable ColumnScope.() -> Unit
) {
    AbkCard(
        modifier = modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
        containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = AbkTheme.colors.inkMuted)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
            }
            trailingContent?.invoke()
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun ExpressiveListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    AbkListItem(
        title = title,
        description = subtitle,
        modifier = modifier.semantics { this.selected = selected },
        onClick = onClick,
        enabled = enabled,
        leading = leadingContent ?: leadingIcon?.let { vector ->
            { Icon(vector, null, Modifier.size(22.dp), tint = AbkTheme.colors.inkMuted) }
        },
        trailing = trailingContent,
    )
}

@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String
) {
    AbkSwitch(checked, onCheckedChange, label = label, modifier = modifier, enabled = enabled, showLabel = false)
}

@Composable
fun ExpressiveSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = AbkTheme.colors.inkMuted)
        AbkSettingSwitch(title, subtitle, checked, onCheckedChange, Modifier.weight(1f), enabled)
    }
}

@Composable
fun ExpressiveStatusChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = AbkTheme.colors.inkMuted
) {
    Surface(modifier, shape = AbkShapes.Pill, color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
fun ExpressiveEmptyState(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    AbkEmptyState(title = title, description = subtitle, icon = icon, modifier = modifier.fillMaxWidth())
}
