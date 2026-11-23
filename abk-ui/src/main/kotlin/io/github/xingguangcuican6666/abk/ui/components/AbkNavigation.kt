package io.github.xingguangcuican6666.abk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
public fun AbkScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(modifier.imePadding(), topBar = topBar, bottomBar = bottomBar, snackbarHost = snackbarHost,
        containerColor = AbkTheme.colors.background, contentColor = AbkTheme.colors.ink,
        contentWindowInsets = contentWindowInsets, content = content)
}

@Composable
public fun AbkTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scrolled: Boolean = false,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
) {
    Column(modifier.background(AbkTheme.colors.background).windowInsetsPadding(windowInsets)) {
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            navigationIcon?.invoke()
            Column(Modifier.weight(1f).semantics { heading() }) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = AbkTheme.colors.ink)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
            }
            actions()
        }
        if (scrolled) AbkDivider()
    }
}

@Composable
public fun AbkHero(
    title: String, modifier: Modifier = Modifier, eyebrow: String? = null, description: String? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (eyebrow != null) AbkEyebrow(eyebrow)
        AbkHeading(title, large = true)
        if (description != null) AbkText(description, color = AbkTheme.colors.inkMuted)
        actions?.invoke()
    }
}

public data class AbkNavigationItem(val id: String, val label: String, val icon: ImageVector, val enabled: Boolean = true)

/** Equal-weight destinations: selection never changes the icon, font, color or hit-box. */
@Composable
public fun AbkBottomNavigation(
    items: List<AbkNavigationItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
) {
    require(items.size in 2..5) { "Bottom navigation supports two to five destinations" }
    require(items.map { it.id }.distinct().size == items.size) { "Destination IDs must be unique" }
    val c = AbkTheme.colors
    Column(modifier.background(c.raised).windowInsetsPadding(windowInsets)) {
        AbkDivider()
        Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 8.dp, vertical = 4.dp)) {
            items.forEach { item ->
                val interactions = remember(item.id) { MutableInteractionSource() }
                val focused by interactions.collectIsFocusedAsState()
                val selected = item.id == selectedId
                val foreground = if (item.enabled) c.inkMuted else c.inkFaint
                Box(
                    Modifier.weight(1f).abkFocusBorder(focused, foreground)
                        .selectable(selected, interactions, indication = null, enabled = item.enabled, role = Role.Tab) { onSelect(item.id) },
                ) {
                    Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                        AbkIcon(item.icon, null, tint = foreground, size = 22.dp)
                        Text(item.label, style = MaterialTheme.typography.labelSmall, color = foreground)
                    }
                    if (selected) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                        .size(12.dp, 2.dp).background(foreground, AbkShapes.Pill))
                }
            }
        }
    }
}

public data class AbkTabItem(val id: String, val label: String, val count: Int? = null, val enabled: Boolean = true)

@Composable
public fun AbkTabs(
    items: List<AbkTabItem>, selectedId: String, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, scrollable: Boolean = false,
) {
    require(items.map { it.id }.distinct().size == items.size) { "Tab IDs must be unique" }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier).selectableGroup()) {
            items.forEach { item ->
                val selected = item.id == selectedId
                Column(Modifier.then(if (scrollable) Modifier.width(IntrinsicSize.Max) else Modifier.weight(1f))
                    .selectable(selected, enabled = item.enabled, role = Role.Tab) { onSelect(item.id) },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.label, style = MaterialTheme.typography.labelMedium,
                            color = if (!item.enabled) AbkTheme.colors.inkFaint else if (selected) AbkTheme.colors.accent.color else AbkTheme.colors.inkMuted)
                        if (item.count != null) AbkBadge(item.count.toString(), dot = false)
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) AbkTheme.colors.accent.color else Color.Transparent))
                }
            }
        }
        AbkDivider()
    }
}

public data class AbkBreadcrumbItem(val label: String, val onClick: (() -> Unit)? = null)

@Composable
public fun AbkBreadcrumb(items: List<AbkBreadcrumbItem>, modifier: Modifier = Modifier) {
    AbkCluster(modifier) {
        items.forEachIndexed { index, item ->
            if (index > 0) Text("/", color = AbkTheme.colors.inkFaint)
            if (item.onClick != null) AbkButton(item.onClick, style = AbkButtonStyle.Ghost, size = AbkSize.Small) { Text(item.label) }
            else Text(item.label, Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.labelMedium, color = AbkTheme.colors.ink)
        }
    }
}
