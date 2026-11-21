package com.abk.kernel.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.xingguangcuican6666.abk.ui.components.AbkOption
import io.github.xingguangcuican6666.abk.ui.components.AbkSegmented
import io.github.xingguangcuican6666.abk.ui.components.AbkTabItem
import io.github.xingguangcuican6666.abk.ui.components.AbkTabs

data class AbkSegmentedButtonOption<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true
)

@Suppress("UNUSED_PARAMETER")
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
    val selectedId = options.indexOfFirst { it.value == selectedValue }
        .takeIf { it >= 0 }?.toString()
    val select: (String) -> Unit = { id ->
        id.toIntOrNull()?.let(options::getOrNull)?.takeIf { it.enabled }?.let { onSelect(it.value) }
    }
    if (options.size in 2..4 && equalWidth) {
        AbkSegmented(
            options = options.mapIndexed { index, option ->
                AbkOption(index.toString(), option.label, enabled = option.enabled)
            },
            selectedId = selectedId,
            onSelect = select,
            modifier = modifier
        )
    } else {
        // Keep every host option reachable; the library deliberately limits segmented controls to four.
        AbkTabs(
            items = options.mapIndexed { index, option ->
                AbkTabItem(index.toString(), option.label, enabled = option.enabled)
            },
            selectedId = selectedId.orEmpty(),
            onSelect = select,
            modifier = modifier,
            scrollable = !equalWidth || options.size > 4
        )
    }
}
