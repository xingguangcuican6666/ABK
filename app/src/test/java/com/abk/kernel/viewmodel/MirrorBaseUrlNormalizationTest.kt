package com.abk.kernel.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorBaseUrlNormalizationTest {

    @Test
    fun appendsTrailingSlashWhenMissing() {
        assertEquals(
            "https://hk.gh-proxy.org/",
            normalizeMirrorBaseUrl("https://hk.gh-proxy.org")
        )
    }

    @Test
    fun trimsSurroundingWhitespaceAndKeepsExistingSlash() {
        assertEquals(
            "https://hk.gh-proxy.org/",
            normalizeMirrorBaseUrl(" https://hk.gh-proxy.org/ ")
        )
    }

    @Test
    fun returnsEmptyStringForBlankInput() {
        assertEquals("", normalizeMirrorBaseUrl("   "))
    }
}
