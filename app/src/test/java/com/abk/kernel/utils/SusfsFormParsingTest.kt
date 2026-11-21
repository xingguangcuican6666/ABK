package com.abk.kernel.utils

import com.abk.kernel.data.model.SusfsConfig
import com.abk.kernel.data.model.SusfsKstatEntry
import com.abk.kernel.data.model.SusfsOpenRedirectRule
import com.abk.kernel.data.model.SusfsPathRule
import com.abk.kernel.data.model.SusfsPresetOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SusfsFormParsingTest {
    @Test
    fun pathRulesKeepOptionalRetryValuesAndIgnoreComments() {
        val expected =
            listOf(
                SusfsPathRule("/fixture/first", 3),
                SusfsPathRule("/fixture/second"),
                SusfsPathRule("/fixture/first", 3),
            )
        assertEquals(
            expected,
            parseSusfsPathRules(
                "# fixture comment\n /fixture/first 3 \n\n/fixture/second\n/fixture/first 3"
            ),
        )
        assertEquals(expected, parseSusfsPathRules(renderSusfsPathRules(expected)))
        assertTrue(runCatching { parseSusfsPathRules("/fixture/path invalid") }.isFailure)
    }

    @Test
    fun stringListsTrimLinesWithoutIntroducingDeduplication() {
        val expected = listOf("/fixture/first", "/fixture/second", "/fixture/first")
        assertEquals(
            expected,
            parseSusfsStringList(" /fixture/first \n# comment\n\n/fixture/second\n/fixture/first"),
        )
        assertEquals(expected, parseSusfsStringList(renderSusfsStringList(expected)))
    }

    @Test
    fun openRedirectsRetainStageFallbacksAndUidClamping() {
        val expected =
            listOf(
                SusfsOpenRedirectRule("/fixture/a", "/fixture/b", "service", 4),
                SusfsOpenRedirectRule("/fixture/c", "/fixture/d", "boot_completed", 0),
                SusfsOpenRedirectRule("/fixture/e", "/fixture/f", "boot_completed"),
            )
        assertEquals(
            expected,
            parseSusfsOpenRedirects(
                "# fixture comment\n/fixture/a /fixture/b service 9\n/fixture/c /fixture/d unknown -1\n/fixture/e /fixture/f boot_completed"
            ),
        )
        assertEquals(expected, parseSusfsOpenRedirects(renderSusfsOpenRedirects(expected)))
        assertTrue(runCatching { parseSusfsOpenRedirects("/fixture/a /fixture/b") }.isFailure)
        assertTrue(
            runCatching { parseSusfsOpenRedirects("/fixture/a /fixture/b service invalid") }
                .isFailure
        )
    }

    @Test
    fun kstatJsonPreservesDefaultsAndRoundTripsWithoutExecutingAnything() {
        val expected = listOf(SusfsKstatEntry("/fixture/stat", ino = "123"))
        assertEquals(
            expected,
            parseSusfsKstatJson("[{\"path\":\" /fixture/stat \",\"ino\":\"123\"}]"),
        )
        assertEquals(expected, parseSusfsKstatJson(renderSusfsKstatJson(expected)))
        assertTrue(parseSusfsKstatJson(" ").isEmpty())
        assertTrue(runCatching { parseSusfsKstatJson("[{\"ino\":\"123\"}]") }.isFailure)
        assertTrue(runCatching { parseSusfsKstatJson("not-json") }.isFailure)
    }

    @Test
    fun normalizationKeepsExistingFallbacksAndPresetRanges() {
        val normalized =
            normalizeSusfsConfig(
                SusfsConfig(
                    schemaVersion = 0,
                    hideSusMountsMode = "unknown",
                    spoofUnameStage = "unknown",
                    unameValue = " ",
                    buildTimeValue = "",
                    sdcardRootPath = "",
                    androidDataRootPath = " ",
                    maps = listOf(" /fixture/map ", ""),
                    legitMounts = emptyList(),
                    presets =
                        SusfsPresetOptions(hideCustomRomLevel = 99, emulateVoldAppDataMode = -1),
                )
            )
        assertEquals(1, normalized.schemaVersion)
        assertEquals(SUSFS_HIDE_MOUNTS_OFF, normalized.hideSusMountsMode)
        assertEquals(SUSFS_SPOOF_UNAME_OFF, normalized.spoofUnameStage)
        assertEquals("default", normalized.unameValue)
        assertEquals("default", normalized.buildTimeValue)
        assertEquals("/sdcard", normalized.sdcardRootPath)
        assertEquals("/sdcard/Android/data", normalized.androidDataRootPath)
        assertEquals(listOf("/fixture/map"), normalized.maps)
        assertEquals(defaultSusfsLegitMounts(), normalized.legitMounts)
        assertEquals(5, normalized.presets.hideCustomRomLevel)
        assertEquals(0, normalized.presets.emulateVoldAppDataMode)
    }
}
