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
    fun wantsBackgroundPainterRequiresAllFlags() {
        val base = config()
        assertTrue(base.wantsBackgroundPainter)

        assertFalse(config(blurEnabled = false).wantsBackgroundPainter)
        assertFalse(config(backgroundExpEnabled = false).wantsBackgroundPainter)
        assertFalse(config(backgroundUri = null).wantsBackgroundPainter)
        assertFalse(config(backgroundUri = "").wantsBackgroundPainter)
        assertFalse(config(backgroundUri = "   ").wantsBackgroundPainter)
        assertFalse(config(backgroundImageEnabled = false).wantsBackgroundPainter)
    }

    @Test
    fun masterSwitchOffDisablesNestedBackgroundPainter() {
        // Even if the nested flag is still persisted, the master switch must win.
        assertFalse(config(blurEnabled = false, backgroundExpEnabled = true).wantsBackgroundPainter)
    }
}
