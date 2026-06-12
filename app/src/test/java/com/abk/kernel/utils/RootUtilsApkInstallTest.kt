package com.abk.kernel.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootUtilsApkInstallTest {

    @Test
    fun recoversWhenPmReportedSuccessAndInstalledVersionMatches() {
        assertTrue(
            RootUtils.shouldRecoverSuccessfulApkInstall(
                output = listOf("Success"),
                expectedVersionCode = 42L,
                installedVersionCode = 42L
            )
        )
    }

    @Test
    fun doesNotRecoverWhenInstalledVersionDoesNotMatch() {
        assertFalse(
            RootUtils.shouldRecoverSuccessfulApkInstall(
                output = listOf("Success"),
                expectedVersionCode = 42L,
                installedVersionCode = 41L
            )
        )
    }

    @Test
    fun doesNotRecoverWithoutSuccessMarker() {
        assertFalse(
            RootUtils.shouldRecoverSuccessfulApkInstall(
                output = listOf("Failure [INSTALL_FAILED_INVALID_APK]"),
                expectedVersionCode = 42L,
                installedVersionCode = 42L
            )
        )
    }
}
