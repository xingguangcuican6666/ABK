package io.github.xingguangcuican6666.abk.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

public enum class AbkStepState { Complete, Current, Pending }
public data class AbkStep(val title: String, val description: String? = null, val state: AbkStepState = AbkStepState.Pending)

@Composable
public fun AbkSteps(
    steps: List<AbkStep>, modifier: Modifier = Modifier, horizontal: Boolean = false,
    completeDescription: String = "已完成", currentDescription: String = "当前步骤", pendingDescription: String = "待处理",
) {
    val labels = mapOf(AbkStepState.Complete to completeDescription, AbkStepState.Current to currentDescription, AbkStepState.Pending to pendingDescription)
    if (horizontal) {
        Row(modifier.horizontalScroll(rememberScrollState())) {
            steps.forEachIndexed { index, step ->
                Column(Modifier.width(160.dp).semantics(mergeDescendants = true) { stateDescription = labels.getValue(step.state) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepMarker(index, step.state)
                        if (index < steps.lastIndex) Box(Modifier.weight(1f).height(1.dp).background(
                            if (step.state == AbkStepState.Complete) AbkTheme.colors.success.color else AbkTheme.colors.line))
                    }
                    StepLabel(step, Modifier.padding(top = 8.dp, end = 16.dp))
                }
            }
        }
    } else {
        Column(modifier) {
            steps.forEachIndexed { index, step ->
                Row(Modifier.height(IntrinsicSize.Min).semantics(mergeDescendants = true) { stateDescription = labels.getValue(step.state) },
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        StepMarker(index, step.state)
                        if (index < steps.lastIndex) Box(Modifier.weight(1f).width(1.dp).background(
                            if (step.state == AbkStepState.Complete) AbkTheme.colors.success.color else AbkTheme.colors.line))
                    }
                    StepLabel(step, Modifier.weight(1f).padding(bottom = if (index < steps.lastIndex) 24.dp else 0.dp))
                }
            }
        }
    }
}

@Composable
private fun StepMarker(index: Int, state: AbkStepState) {
    val c = AbkTheme.colors
    val background = when (state) { AbkStepState.Complete -> c.success.container; AbkStepState.Current -> c.accent.color; AbkStepState.Pending -> c.raised }
    val foreground = when (state) { AbkStepState.Complete -> c.success.onContainer; AbkStepState.Current -> c.accent.onColor; AbkStepState.Pending -> c.inkMuted }
    Surface(Modifier.size(28.dp), shape = CircleShape, color = background,
        border = BorderStroke(2.dp, when (state) { AbkStepState.Complete -> c.success.color; AbkStepState.Current -> c.accent.color; AbkStepState.Pending -> c.lineStrong })) {
        Box(contentAlignment = Alignment.Center) {
            if (state == AbkStepState.Complete) AbkIcon(AbkIcons.Check, null, tint = foreground, size = 16.dp)
            else AbkText((index + 1).toString(), style = MaterialTheme.typography.labelSmall, color = foreground)
        }
    }
}

@Composable
private fun StepLabel(step: AbkStep, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AbkText(step.title, style = MaterialTheme.typography.titleSmall)
        if (step.description != null) AbkText(step.description, style = MaterialTheme.typography.bodyMedium, color = AbkTheme.colors.inkMuted)
    }
}

public data class AbkTimelineItem(val time: String, val title: String, val description: String? = null, val tone: AbkTone = AbkTone.Neutral)

@Composable
public fun AbkTimeline(items: List<AbkTimelineItem>, modifier: Modifier = Modifier) {
    Column(modifier) {
        items.forEachIndexed { index, item ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(16.dp).fillMaxHeight().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val status = AbkTheme.colors.status(item.tone)
                    Surface(Modifier.size(12.dp), shape = CircleShape, color = status.container, border = BorderStroke(2.dp, status.color)) {}
                    if (index < items.lastIndex) Box(Modifier.weight(1f).width(1.dp).background(AbkTheme.colors.line))
                }
                Column(Modifier.weight(1f).padding(bottom = if (index < items.lastIndex) 20.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AbkText(item.time, style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkFaint)
                    AbkText(item.title, style = MaterialTheme.typography.titleSmall)
                    if (item.description != null) AbkText(item.description, style = MaterialTheme.typography.bodyMedium, color = AbkTheme.colors.inkMuted)
                }
            }
        }
    }
}
