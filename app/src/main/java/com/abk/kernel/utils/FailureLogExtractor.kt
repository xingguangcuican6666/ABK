package com.abk.kernel.utils

object FailureLogExtractor {
    private val ansiEscape = Regex("\u001B\\[[;\\d]*[ -/]*[@-~]")
    private val oscEscape = Regex("\u001B\\][^\u0007]*(\u0007|\u001B\\\\)")
    private val loneEscape = Regex("\u001B[@-_]")

    private val errorMarkers = listOf(
        "##[error]",
        "::error::",
        "error:",
        "failed",
        "fatal:",
        "exception:",
        "build failed",
    )

    fun sanitizeForDisplay(raw: String): String {
        if (raw.isEmpty()) return ""
        val withoutEscapes = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(ansiEscape, "")
            .replace(oscEscape, "")
            .replace(loneEscape, "")
        return buildString(withoutEscapes.length) {
            for (ch in withoutEscapes) {
                when {
                    ch == '\n' || ch == '\t' -> append(ch)
                    ch.isISOControl() -> Unit
                    ch == '\uFFFD' -> Unit
                    Character.getType(ch) == Character.FORMAT.toInt() -> Unit
                    else -> append(ch)
                }
            }
        }
    }

    fun extract(raw: String, maxChars: Int = 2_250): String {
        if (raw.isBlank()) return ""
        val lines = sanitizeForDisplay(raw).lines()
        val hitIndexes = lines.indices.filter { index ->
            val lower = lines[index].lowercase()
            errorMarkers.any { marker -> lower.contains(marker) }
        }
        val excerpt = if (hitIndexes.isEmpty()) {
            lines.takeLast(24).joinToString("\n")
        } else {
            val start = (hitIndexes.min() - 5).coerceAtLeast(0)
            val end = (hitIndexes.max() + 8).coerceAtMost(lines.lastIndex)
            lines.subList(start, end + 1).joinToString("\n")
        }
        return excerpt.take(maxChars)
    }
}
