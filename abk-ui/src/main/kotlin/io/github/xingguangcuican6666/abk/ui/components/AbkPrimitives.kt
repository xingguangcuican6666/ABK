package io.github.xingguangcuican6666.abk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

public enum class AbkButtonStyle { Primary, Secondary, Soft, Outline, Ghost, Danger, Sketch }
public enum class AbkSize { Small, Medium, Large }

@Composable
public fun AbkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = AbkTheme.colors.ink,
) {
    Text(text, modifier, color = color, style = style)
}

@Composable
public fun AbkHeading(text: String, modifier: Modifier = Modifier, large: Boolean = false) {
    AbkText(text, modifier.semantics { heading() }, if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge)
}

@Composable
public fun AbkEyebrow(text: String, modifier: Modifier = Modifier) {
    AbkText(text, modifier, MaterialTheme.typography.labelMedium, AbkTheme.colors.inkMuted)
}

@Composable
public fun AbkDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, color = AbkTheme.colors.line)
}

@Composable
public fun AbkPanel(
    modifier: Modifier = Modifier,
    grain: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AbkTheme.colors
    Surface(modifier, shape = AbkShapes.Large, color = colors.raised, border = BorderStroke(1.dp, colors.line)) {
        Column(
            Modifier.then(if (grain) Modifier.abkPaperGrain(colors.inkFaint) else Modifier).padding(AbkSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(AbkSpacing.Md), content = content,
        )
    }
}

@Composable
public fun AbkCluster(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm), verticalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) { content() }
}

@Composable
public fun AbkInlineCode(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.background(AbkTheme.colors.sunken, AbkShapes.Small).padding(horizontal = 5.dp, vertical = 2.dp),
        color = AbkTheme.colors.inkSoft, fontFamily = AbkTheme.monoFont, style = MaterialTheme.typography.bodyMedium)
}

@Composable
public fun AbkKeycap(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.border(1.dp, AbkTheme.colors.lineStrong, AbkShapes.Small).padding(horizontal = 6.dp, vertical = 2.dp),
        color = AbkTheme.colors.inkMuted, fontFamily = AbkTheme.monoFont, style = MaterialTheme.typography.bodySmall)
}

@Composable
public fun AbkHighlight(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.background(AbkTheme.colors.accent.container, AbkShapes.Small).padding(horizontal = 3.dp),
        color = AbkTheme.colors.accent.onContainer, style = MaterialTheme.typography.bodyLarge)
}

@Composable
public fun AbkButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AbkButtonStyle = AbkButtonStyle.Secondary,
    size: AbkSize = AbkSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingDescription: String = "加载中",
    content: @Composable RowScope.() -> Unit,
) {
    val c = AbkTheme.colors
    val background = when (style) {
        AbkButtonStyle.Primary -> c.accent.color
        AbkButtonStyle.Danger -> c.danger.color
        AbkButtonStyle.Soft -> c.accent.container
        AbkButtonStyle.Secondary -> c.raised
        else -> Color.Transparent
    }
    val foreground = when (style) {
        AbkButtonStyle.Primary -> c.accent.onColor
        AbkButtonStyle.Danger -> c.danger.onColor
        AbkButtonStyle.Soft -> c.accent.onContainer
        else -> c.ink
    }
    val shape = if (style == AbkButtonStyle.Sketch) RoundedCornerShape(14.dp, 10.dp, 15.dp, 11.dp) else AbkShapes.Pill
    val padding = when (size) {
        AbkSize.Small -> PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        AbkSize.Medium -> PaddingValues(horizontal = 18.dp, vertical = 10.dp)
        AbkSize.Large -> PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    }
    Button(
        onClick, modifier.defaultMinSize(minHeight = 48.dp).then(if (loading) Modifier.semantics { stateDescription = loadingDescription } else Modifier),
        enabled = enabled && !loading, shape = shape, contentPadding = padding,
        colors = ButtonDefaults.buttonColors(
            containerColor = background, contentColor = foreground,
            disabledContainerColor = if (loading) background else c.sunken,
            disabledContentColor = if (loading) foreground else c.inkFaint,
        ),
        border = when (style) {
            AbkButtonStyle.Outline -> BorderStroke(1.dp, c.lineStrong)
            AbkButtonStyle.Sketch -> BorderStroke(1.dp, c.inkSoft)
            AbkButtonStyle.Secondary -> BorderStroke(1.dp, c.line)
            else -> null
        },
    ) {
        if (loading) AbkSpinner(Modifier.padding(end = 8.dp), size = AbkSize.Small, label = loadingDescription)
        content()
    }
}

@Composable
public fun AbkIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: AbkSize = AbkSize.Medium,
) {
    IconButton(onClick, modifier.defaultMinSize(48.dp, 48.dp), enabled = enabled) {
        AbkIcon(icon, contentDescription, size = if (size == AbkSize.Small) 18.dp else 22.dp,
            tint = if (enabled) AbkTheme.colors.inkSoft else AbkTheme.colors.inkFaint)
    }
}

@Composable
public fun AbkAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: AbkSize = AbkSize.Medium,
    online: Boolean? = null,
    statusDescription: String? = null,
) {
    val diameter = when (size) { AbkSize.Small -> 30.dp; AbkSize.Medium -> 40.dp; AbkSize.Large -> 56.dp }
    Box(modifier.then(if (statusDescription != null) Modifier.semantics { stateDescription = statusDescription } else Modifier)) {
        Box(Modifier.size(diameter).clip(CircleShape).background(AbkTheme.colors.accent.container), contentAlignment = Alignment.Center) {
            AbkText(initials, style = MaterialTheme.typography.titleMedium, color = AbkTheme.colors.accent.onContainer)
        }
        if (online != null) Box(Modifier.align(Alignment.BottomEnd).size(10.dp).border(2.dp, AbkTheme.colors.raised, CircleShape)
            .background(if (online) AbkTheme.colors.success.color else AbkTheme.colors.inkFaint, CircleShape))
    }
}
