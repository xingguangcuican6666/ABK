package com.abk.kernel.utils

import com.abk.kernel.data.model.WorkflowStepI18nBundle
import com.abk.kernel.utils.WorkflowStepI18n.RefreshResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkflowStepI18nTest {

    @Before
    fun setUp() {
        WorkflowStepI18n.resetForTest()
    }

    @Test
    fun parseJson_trimsKeys() {
        val json = """{"version":2,"steps":{"  编译内核  ":"Compile kernel"}}"""
        val bundle = WorkflowStepI18n.parseJson(json)
        assertNotNull(bundle)
        assertEquals(2, bundle!!.version)
        assertEquals("Compile kernel", bundle.steps["编译内核"])
    }

    @Test
    fun parseJson_invalidReturnsNull() {
        assertNull(WorkflowStepI18n.parseJson("not json"))
    }

    @Test
    fun translateKey_unknownReturnsOriginal() {
        val bundle = WorkflowStepI18nBundle(1, mapOf("编译内核" to "Compile kernel"))
        assertEquals("unknown", WorkflowStepI18n.translateKey(bundle, "unknown"))
    }

    @Test
    fun translateKey_resolvesFromBundle() {
        val bundle = WorkflowStepI18nBundle(1, mapOf("编译内核" to "Compile kernel"))
        assertEquals("Compile kernel", WorkflowStepI18n.translateKey(bundle, "编译内核"))
    }

    @Test
    fun refreshResult_notifyStaleSnackbar() {
        assertTrue(RefreshResult.Failed.notifyStaleSnackbar())
        assertTrue(RefreshResult.UsedFallbackStaleRemote.notifyStaleSnackbar())
        assertFalse(RefreshResult.UsedFallbackSilent.notifyStaleSnackbar())
        assertFalse(RefreshResult.Updated.notifyStaleSnackbar())
        assertFalse(RefreshResult.UpToDate.notifyStaleSnackbar())
    }
}
