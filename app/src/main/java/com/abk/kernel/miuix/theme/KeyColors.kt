package com.abk.kernel.miuix.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 16 accent color ARGB values (default + 15 named swatches). Matches KernelSU's
 * `keyColorOptions`. ARGB value `0` is reserved for '默认' (default) — caller
 * treats `0` or `null` as "use ABK green seed".
 */
val miuixKeyColorOptions: List<Int> = listOf(
    Color(0xFFF44336).toArgb(), // Red
    Color(0xFFE91E63).toArgb(), // Pink
    Color(0xFF9C27B0).toArgb(), // Purple
    Color(0xFF673AB7).toArgb(), // Deep Purple
    Color(0xFF3F51B5).toArgb(), // Indigo
    Color(0xFF2196F3).toArgb(), // Blue
    Color(0xFF00BCD4).toArgb(), // Cyan
    Color(0xFF009688).toArgb(), // Teal
    Color(0xFF4FAF50).toArgb(), // Green
    Color(0xFFFFEB3B).toArgb(), // Yellow
    Color(0xFFFFC107).toArgb(), // Amber
    Color(0xFFFF9800).toArgb(), // Orange
    Color(0xFF795548).toArgb(), // Brown
    Color(0xFF607D8F).toArgb(), // Blue Grey
    Color(0xFFFF9CA8).toArgb(), // Sakura
)
