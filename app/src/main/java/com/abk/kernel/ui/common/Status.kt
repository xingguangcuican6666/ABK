package com.abk.kernel.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.WorkflowRun
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkText
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Tone that names a run's state; text and glyph always accompany the colour. */
fun WorkflowRun.tone(): AbkTone = when {
    status in setOf("queued", "waiting", "requested", "pending", "in_progress") -> AbkTone.Accent
    status == "completed" && conclusion == "success" -> AbkTone.Success
    status == "completed" && conclusion == "failure" -> AbkTone.Danger
    status == "completed" && conclusion == "cancelled" -> AbkTone.Warning
    else -> AbkTone.Neutral
}

fun BuildStatus.tone(): AbkTone = when (this) {
    BuildStatus.SUCCESS -> AbkTone.Success
    BuildStatus.FAILURE -> AbkTone.Danger
    BuildStatus.CANCELLED -> AbkTone.Warning
    BuildStatus.IN_PROGRESS, BuildStatus.QUEUED -> AbkTone.Accent
    BuildStatus.IDLE -> AbkTone.Neutral
}

/** Short state word shown beside the tone glyph, so colour is never the only signal. */
fun WorkflowRun.statusLabel(): String = when {
    status in setOf("queued", "waiting", "requested", "pending") -> "排队"
    status == "in_progress" -> "进行中"
    status == "completed" && conclusion == "success" -> "成功"
    status == "completed" && conclusion == "failure" -> "失败"
    status == "completed" && conclusion == "cancelled" -> "已取消"
    status == "completed" -> "已结束"
    else -> status
}

@Composable
fun StatusBadge(tone: AbkTone, label: String) {
    AbkBadge(text = label, tone = tone)
}

/** ISO-8601 → local "MM-dd HH:mm"; the raw string when parsing fails, "—" when absent. */
fun formatRunTimestamp(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return runCatching {
        DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(iso))
    }.getOrDefault(iso)
}

@Composable
fun MutedText(text: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    AbkText(text, modifier, MaterialTheme.typography.bodySmall, AbkTheme.colors.inkMuted)
}
