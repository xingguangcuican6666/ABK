package com.abk.kernel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abk.kernel.ui.theme.uiSurfaceColor

data class AbkSegmentedButtonOption<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true
)

@Composable
fun <T> AbkSingleChoiceSegmentedButtonRow(
    options: List<AbkSegmentedButtonOption<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    showSelectionIcon: Boolean = true,
    equalWidth: Boolean = true
) {
    if (options.isEmpty()) return

    val colors = MaterialTheme.colorScheme
    val outlineColor = colors.outline.copy(alpha = 0.72f)
    val containerShape = RoundedCornerShape(24.dp)

    Surface(
        modifier = modifier,
        shape = containerShape,
        color = uiSurfaceColor(colors.surfaceContainerLowest),
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .selectableGroup()
        ) {
            options.forEachIndexed { index, option ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(outlineColor)
                    )
                }

                val selected = option.value == selectedValue
                val segmentShape = segmentedItemShape(index = index, count = options.size, radius = 24.dp)
                val containerColor by animateColorAsState(
                    targetValue = if (selected) {
                        colors.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    label = "abk-segmented-container"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selected) colors.onSecondaryContainer else colors.onSurface,
                    label = "abk-segmented-content"
                )
                val segmentModifier = if (equalWidth) Modifier.weight(1f) else Modifier

                Box(
                    modifier = segmentModifier
                        .fillMaxHeight()
                        .clip(segmentShape)
                        .background(containerColor)
                        .selectable(
                            selected = selected,
                            enabled = option.enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(option.value) }
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedVisibility(visible = showSelectionIcon && selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = contentColor
                            )
                        }
                        Text(
                            text = option.label,
                            color = contentColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun segmentedItemShape(
    index: Int,
    count: Int,
    radius: Dp
): RoundedCornerShape = when {
    count <= 1 -> RoundedCornerShape(radius)
    index == 0 -> RoundedCornerShape(topStart = radius, bottomStart = radius)
    index == count - 1 -> RoundedCornerShape(topEnd = radius, bottomEnd = radius)
    else -> RoundedCornerShape(0.dp)
}
