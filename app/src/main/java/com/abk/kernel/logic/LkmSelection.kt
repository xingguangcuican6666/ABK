package com.abk.kernel.logic

/**
 * Picks the LKM KMI to show when the recommendation may arrive after the
 * option list: a manual choice wins; otherwise the recommendation, then the
 * current value, then the first option.
 */
fun preferredLkmKmiSelection(
    currentSelection: String,
    options: List<String>,
    recommendedKmi: String?,
    hasCustomSelection: Boolean
): String {
    if (options.isEmpty()) return ""

    val current = currentSelection.takeIf { it in options }
    val recommended = recommendedKmi?.takeIf { it in options }
    return when {
        hasCustomSelection && current != null -> current
        recommended != null -> recommended
        current != null -> current
        else -> options.first()
    }
}
