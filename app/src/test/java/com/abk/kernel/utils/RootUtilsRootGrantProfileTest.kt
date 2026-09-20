package com.abk.kernel.utils

import com.abk.kernel.data.model.ROOT_PROFILE_FLAG_NO_NEW_PRIVS
import com.abk.kernel.data.model.RootGrantApp
import com.abk.kernel.data.model.RootGrantProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootUtilsRootGrantProfileTest {

    @Test
    fun buildRootGrantListProfileMarksGrantedUidAsAllowed() {
        val profile = RootUtils.buildRootGrantListProfile(
            packageName = "com.example.app",
            uid = 10123,
            grantedUids = setOf(10123)
        )

        assertTrue(profile.allowSu)
        assertEquals("com.example.app", profile.name)
        assertEquals(10123, profile.currentUid)
        assertEquals(ROOT_PROFILE_FLAG_NO_NEW_PRIVS, profile.flags)
        assertTrue(profile.rootUseDefault)
    }

    @Test
    fun buildRootGrantListProfileLeavesUngrantUidOnSafeDefaults() {
        val profile = RootUtils.buildRootGrantListProfile(
            packageName = "com.example.app",
            uid = 10123,
            grantedUids = emptySet()
        )

        assertFalse(profile.allowSu)
        assertEquals("com.example.app", profile.name)
        assertEquals(10123, profile.currentUid)
        assertEquals(ROOT_PROFILE_FLAG_NO_NEW_PRIVS, profile.flags)
        assertTrue(profile.nonRootUseDefault)
        assertTrue(profile.umountModules)
    }

    @Test
    fun buildRootGrantListProfileTreatsSharedUidPackagesConsistently() {
        val grantedUids = setOf(10123)

        val first = RootUtils.buildRootGrantListProfile(
            packageName = "com.example.first",
            uid = 10123,
            grantedUids = grantedUids
        )
        val second = RootUtils.buildRootGrantListProfile(
            packageName = "com.example.second",
            uid = 10123,
            grantedUids = grantedUids
        )

        assertTrue(first.allowSu)
        assertTrue(second.allowSu)
        assertEquals(10123, first.currentUid)
        assertEquals(10123, second.currentUid)
    }

    @Test
    fun prepareRootGrantAppsForDisplayExcludesSelfAndKeepsDisplaySorting() {
        val displayed = RootUtils.prepareRootGrantAppsForDisplay(
            apps = listOf(
                RootGrantApp(
                    packageName = "com.abk.kernel",
                    label = "ABK",
                    uid = 1000,
                    profile = RootGrantProfile(allowSu = true)
                ),
                RootGrantApp(
                    packageName = "com.example.beta",
                    label = "Beta",
                    uid = 1002,
                    profile = RootGrantProfile(allowSu = false)
                ),
                RootGrantApp(
                    packageName = "com.example.alpha",
                    label = "Alpha",
                    uid = 1001,
                    profile = RootGrantProfile(allowSu = true)
                )
            ),
            selfPackageName = "com.abk.kernel"
        )

        assertEquals(listOf("com.example.alpha", "com.example.beta"), displayed.map { it.packageName })
        assertTrue(displayed.none { it.packageName == "com.abk.kernel" })
    }
}
