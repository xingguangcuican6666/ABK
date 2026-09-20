package com.abk.kernel.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class AbkRootPatchScreenSelectionTest {

    @Test
    fun prefersRecommendedKmiWhenItArrivesAfterFallbackSelection() {
        val options = listOf("android14-6.1.162", "android14-6.6.10")

        val selected = preferredLkmKmiSelection(
            currentSelection = "android14-6.1.162",
            options = options,
            recommendedKmi = "android14-6.6.10",
            hasCustomSelection = false
        )

        assertEquals("android14-6.6.10", selected)
    }

    @Test
    fun keepsManualSelectionWhenUserAlreadyChoseOne() {
        val options = listOf("android14-6.1.162", "android14-6.6.10")

        val selected = preferredLkmKmiSelection(
            currentSelection = "android14-6.1.162",
            options = options,
            recommendedKmi = "android14-6.6.10",
            hasCustomSelection = true
        )

        assertEquals("android14-6.1.162", selected)
    }

    @Test
    fun fallsBackToFirstOptionWhenRecommendedKmiIsUnavailable() {
        val options = listOf("android14-6.1.162", "android14-6.6.10")

        val selected = preferredLkmKmiSelection(
            currentSelection = "",
            options = options,
            recommendedKmi = "android15-6.6.10",
            hasCustomSelection = false
        )

        assertEquals("android14-6.1.162", selected)
    }

    @Test
    fun returnsEmptyStringWhenNoKmiOptionsExist() {
        val selected = preferredLkmKmiSelection(
            currentSelection = "android14-6.1.162",
            options = emptyList(),
            recommendedKmi = "android14-6.1.162",
            hasCustomSelection = false
        )

        assertEquals("", selected)
    }
}
