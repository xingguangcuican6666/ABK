package com.abk.kernel.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomFontStorageTest {
    @Test
    fun sanitizeDisplayNameKeepsOnlySafeBasenameText() {
        assertEquals("reader.otf", CustomFontStorage.sanitizeDisplayName("folder/reader.otf"))
        assertEquals("Custom font", CustomFontStorage.sanitizeDisplayName("  \u0000  "))
        assertEquals("font-name.ttf", CustomFontStorage.sanitizeDisplayName("..\\font-name.ttf"))
    }
}
