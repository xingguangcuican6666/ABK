package com.abk.kernel.miuix.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class FloatingTabItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun MiuixFloatingBottomBar(
    items: List<FloatingTabItem>,
    selectedIndex: Int,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isBlurEnabled: Boolean = true,
    isLiquidGlassEnabled: Boolean = true,
) {
    val selectedIndexState by rememberUpdatedState(selectedIndex)
    FloatingBottomBar(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(bottom = 17.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        selectedIndex = { selectedIndexState },
        onSelected = { items.getOrNull(it)?.onClick?.invoke() },
        backdrop = backdrop,
        tabsCount = items.size,
        isBlurEnabled = isBlurEnabled,
        isLiquidGlassEnabled = isLiquidGlassEnabled,
    ) {
        items.forEachIndexed { _, item ->
            FloatingBottomBarItem(
                onClick = item.onClick,
                modifier = Modifier.defaultMinSize(minWidth = 64.dp)
            ) {
                // Always onSurface: the glass indicator lifts the selected tab out of the
                // accent-tinted duplicate layer, so the colour switch happens in the shader.
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = item.label,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}
