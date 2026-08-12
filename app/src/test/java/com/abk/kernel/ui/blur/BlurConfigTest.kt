package com.abk.kernel.ui.blur

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurConfigTest {

    private fun config(
        blurEnabled: Boolean = true,
        backgroundExpEnabled: Boolean = true,
        backgroundUri: String? = "content://custom-background",
        backgroundImageEnabled: Boolean = true,
    ) = BlurConfig(
        blurEnabled = blurEnabled,
        backgroundExpEnabled = backgroundExpEnabled,
        backgroundUri = backgroundUri,
        backgroundImageEnabled = backgroundImageEnabled,
    )

    @Test
    fun wantsBackgroundPainterRequiresBackgroundFlags() {
        val base = config()
        assertTrue(base.wantsBackgroundPainter)

        // The master AGSL bar-blur switch must NOT gate the software card-blur path.
        assertTrue(config(blurEnabled = false).wantsBackgroundPainter)
        assertFalse(config(backgroundExpEnabled = false).wantsBackgroundPainter)
        assertFalse(config(backgroundUri = null).wantsBackgroundPainter)
        assertFalse(config(backgroundUri = "").wantsBackgroundPainter)
        assertFalse(config(backgroundUri = "   ").wantsBackgroundPainter)
        assertFalse(config(backgroundImageEnabled = false).wantsBackgroundPainter)
    }

    @Test
    fun masterSwitchDoesNotGateSoftwareCardBlur() {
        // The software StackBlur path is independent of the AGSL bar-blur master switch,
        // so a disabled master switch must not disable the card blur.
        assertTrue(config(blurEnabled = false, backgroundExpEnabled = true).wantsBackgroundPainter)
    }
}
