package com.abk.kernel.utils

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
}
