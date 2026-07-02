package com.abk.kernel.utils

import com.abk.kernel.data.model.RootGrantProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootUtilsRootGrantProfileTest {

    @Test
    fun skipsNativeProfileReadWhenUidIsNotGranted() {
        var readCalls = 0

        val profile = RootUtils.resolveRootGrantProfile(
            packageName = "com.example.app",
            uid = 10123,
            grantedUids = emptySet()
        ) { _, _ ->
            readCalls += 1
            RootGrantProfile(name = "com.example.app", currentUid = 10123, allowSu = true)
        }

        assertEquals(0, readCalls)
        assertFalse(profile.allowSu)
        assertEquals("com.example.app", profile.name)
        assertEquals(10123, profile.currentUid)
    }

    @Test
    fun readsNativeProfileWhenUidIsGranted() {
        var readCalls = 0

        val profile = RootUtils.resolveRootGrantProfile(
            packageName = "com.example.app",
            uid = 10123,
            grantedUids = setOf(10123)
        ) { _, _ ->
            readCalls += 1
            RootGrantProfile(name = "com.example.app", currentUid = 10123, allowSu = true)
        }

        assertEquals(1, readCalls)
        assertTrue(profile.allowSu)
    }

    @Test
    fun fallsBackToDefaultProfileWhenGrantedUidHasNoNativeRecord() {
        val profile = RootUtils.resolveRootGrantProfile(
            packageName = "com.example.app",
            uid = 10123,
            grantedUids = setOf(10123)
        ) { _, _ -> null }

        assertFalse(profile.allowSu)
        assertEquals("com.example.app", profile.name)
        assertEquals(10123, profile.currentUid)
    }

    @Test
    fun sharedUidPackagesRemainEligibleForNativeRead() {
        var readCalls = 0
        val grantedUids = setOf(10123)

        assertTrue(RootUtils.shouldReadRootGrantProfile(10123, grantedUids))

        RootUtils.resolveRootGrantProfile("com.example.first", 10123, grantedUids) { _, _ ->
            readCalls += 1
            RootGrantProfile(name = "com.example.first", currentUid = 10123, allowSu = true)
        }
        RootUtils.resolveRootGrantProfile("com.example.second", 10123, grantedUids) { _, _ ->
            readCalls += 1
            RootGrantProfile(name = "com.example.second", currentUid = 10123, allowSu = true)
        }

        assertEquals(2, readCalls)
    }
}
