package io.github.xingguangcuican6666.abk.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme

@Composable
public fun AbkField(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    optionalLabel: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = AbkTheme.colors.inkSoft)
            if (optionalLabel != null) Text(optionalLabel, style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkFaint)
        }
        content()
        if (error != null) Text(error, Modifier.semantics { this.error(error) }, color = AbkTheme.colors.danger.color, style = MaterialTheme.typography.bodySmall)
        else if (hint != null) Text(hint, color = AbkTheme.colors.inkMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
public fun AbkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    hint: String? = null,
    error: String? = null,
    optionalLabel: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
) {
    val c = AbkTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    AbkField(label, modifier, hint, error, optionalLabel) {
        BasicTextField(
            value, onValueChange, Modifier.fillMaxWidth().semantics {
                contentDescription = label
                if (error != null) this.error(error)
            },
            enabled = enabled, readOnly = readOnly, singleLine = singleLine, interactionSource = interactions,
            minLines = minLines, maxLines = maxLines,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (enabled) c.ink else c.inkFaint),
            cursorBrush = SolidColor(c.accent.color),
            decorationBox = { inner ->
                Surface(
                    color = if (error != null) c.danger.container else c.input,
                    contentColor = c.inkSoft, shape = AbkShapes.Medium,
                    border = BorderStroke(if (focused) 2.dp else 1.dp,
                        if (error != null) c.danger.color else if (focused) c.accent.color else c.lineStrong),
                ) {
                    Row(Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        prefix?.invoke()
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty()) Text(placeholder, color = c.inkFaint, style = MaterialTheme.typography.bodyLarge)
                            inner()
                        }
                        suffix?.invoke()
                    }
                }
            },
        )
    }
}

public data class AbkOption(val id: String, val label: String, val description: String? = null, val enabled: Boolean = true)

@Composable
public fun AbkSelect(
    options: List<AbkOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "请选择",
    enabled: Boolean = true,
    hint: String? = null,
    error: String? = null,
) {
    require(options.map { it.id }.distinct().size == options.size) { "Option IDs must be unique" }
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
    AbkField(label, modifier, hint, error) {
        Box {
            Surface(
                shape = AbkShapes.Medium, color = AbkTheme.colors.input,
                border = BorderStroke(1.dp, if (error != null) AbkTheme.colors.danger.color else AbkTheme.colors.lineStrong),
            ) {
                Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                    .clickable(enabled = enabled, role = Role.Button) { expanded = true }
                    .semantics { contentDescription = label; stateDescription = selected?.label ?: placeholder }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AbkText(selected?.label ?: placeholder, Modifier.weight(1f), color = if (enabled) AbkTheme.colors.ink else AbkTheme.colors.inkFaint)
                    AbkIcon(AbkIcons.Forward, null, Modifier.rotate(90f))
                }
            }
            DropdownMenu(expanded && enabled, onDismissRequest = { expanded = false }, containerColor = AbkTheme.colors.raised) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { AbkText(option.label) },
                        onClick = { expanded = false; onSelect(option.id) },
                        enabled = option.enabled,
                        trailingIcon = if (option.id == selectedId) ({ AbkIcon(AbkIcons.Check, null) }) else null,
                    )
                }
            }
        }
    }
}

@Composable
public fun AbkCheckbox(
    state: ToggleableState,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
        .triStateToggleable(state, enabled = enabled, role = Role.Checkbox, onClick = onClick)
        .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AbkControl {
            TriStateCheckbox(state, null, enabled = enabled, colors = CheckboxDefaults.colors(
                checkedColor = AbkTheme.colors.accent.color, checkmarkColor = AbkTheme.colors.accent.onColor,
                uncheckedColor = AbkTheme.colors.lineStrong,
            ))
        }
        ChoiceLabel(label, description, enabled, Modifier.weight(1f))
    }
}

@Composable
public fun AbkRadioOption(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    card: Boolean = false,
) {
    val c = AbkTheme.colors
    Surface(modifier, color = if (card && selected) c.accent.container else if (card) c.raised else androidx.compose.ui.graphics.Color.Transparent,
        shape = AbkShapes.Large, border = if (card) BorderStroke(1.dp, if (selected) c.accent.color else c.line) else null) {
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = if (card) 16.dp else 4.dp, vertical = if (card) 14.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The row is the touch target, so the control keeps its 20dp visual size instead of Material's
            // 48dp touch box; that box was what shoved the label against the ring in the first place.
            AbkControl {
                RadioButton(selected, null, enabled = enabled, colors = RadioButtonDefaults.colors(
                    selectedColor = c.accent.color, unselectedColor = c.lineStrong,
                ))
            }
            ChoiceLabel(label, description, enabled, Modifier.weight(1f))
        }
    }
}

/**
 * Drops Material's minimum interactive size for one control. It is only correct when the enclosing row
 * already provides the 48dp target, which every ABK choice row does.
 */
@Composable
private fun AbkControl(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified, content = content)
}

@Composable
public fun AbkRadioGroup(
    options: List<AbkOption>, selectedId: String?, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, cards: Boolean = false,
) {
    require(options.map { it.id }.distinct().size == options.size) { "Option IDs must be unique" }
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(if (cards) 8.dp else 0.dp)) {
        options.forEach { option ->
            AbkRadioOption(option.id == selectedId, { onSelect(option.id) }, option.label,
                description = option.description, enabled = option.enabled, card = cards)
        }
    }
}

@Composable
public fun AbkSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: AbkSize = AbkSize.Medium,
) {
    val c = AbkTheme.colors
    Row(modifier.defaultMinSize(minHeight = 48.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SwitchTrack(checked, enabled, size)
        AbkText(label, color = if (enabled) c.ink else c.inkFaint)
    }
}

@Composable
internal fun SwitchTrack(checked: Boolean, enabled: Boolean, size: AbkSize) {
    val c = AbkTheme.colors
    val small = size == AbkSize.Small
    Box(Modifier.size(if (small) 40.dp else 48.dp, if (small) 24.dp else 28.dp)
        .background(if (!enabled) c.sunken else if (checked) c.accent.color else c.lineStrong, AbkShapes.Pill)
        .padding(4.dp), contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(if (small) 16.dp else 20.dp).background(if (checked && enabled) c.accent.onColor else c.raised, CircleShape))
    }
}

@Composable
public fun AbkSettingSwitch(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ChoiceLabel(title, description, enabled, Modifier.weight(1f))
        SwitchTrack(checked, enabled, AbkSize.Medium)
    }
}

@Composable
public fun AbkSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    valueLabel: String = value.toString(),
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AbkText(label, Modifier.weight(1f), MaterialTheme.typography.bodyMedium, AbkTheme.colors.inkMuted)
            AbkText(valueLabel, style = MaterialTheme.typography.bodyMedium, color = AbkTheme.colors.inkSoft)
        }
        Slider(value, onValueChange, Modifier.fillMaxWidth().semantics { contentDescription = label }, enabled = enabled,
            valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(thumbColor = AbkTheme.colors.accent.color, activeTrackColor = AbkTheme.colors.accent.color,
                inactiveTrackColor = AbkTheme.colors.line))
    }
}

@Composable
public fun AbkSegmented(
    options: List<AbkOption>, selectedId: String?, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, size: AbkSize = AbkSize.Medium,
) {
    require(options.map { it.id }.distinct().size == options.size) { "Option IDs must be unique" }
    require(options.size in 2..4) { "Use tabs or select for more than four options" }
    Surface(modifier, shape = AbkShapes.Pill, color = AbkTheme.colors.sunken, border = BorderStroke(1.dp, AbkTheme.colors.line)) {
        Row(Modifier.padding(4.dp).selectableGroup()) {
            options.forEach { option ->
                val selected = option.id == selectedId
                Surface(Modifier.weight(1f), shape = AbkShapes.Pill, color = if (selected) AbkTheme.colors.raised else androidx.compose.ui.graphics.Color.Transparent) {
                    Box(Modifier.defaultMinSize(minHeight = 48.dp)
                        .selectable(selected, enabled = option.enabled, role = Role.RadioButton) { onSelect(option.id) }
                        .padding(horizontal = 8.dp, vertical = if (size == AbkSize.Small) 6.dp else 10.dp), contentAlignment = Alignment.Center) {
                        Text(option.label, style = if (size == AbkSize.Small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                            color = if (!option.enabled) AbkTheme.colors.inkFaint else if (selected) AbkTheme.colors.ink else AbkTheme.colors.inkMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceLabel(label: String, description: String?, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AbkText(label, color = if (enabled) AbkTheme.colors.ink else AbkTheme.colors.inkFaint)
        if (description != null) AbkText(description, style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
    }
}
