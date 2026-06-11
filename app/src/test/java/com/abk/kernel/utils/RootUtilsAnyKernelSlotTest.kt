package com.abk.kernel.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RootUtilsAnyKernelSlotTest {

    @Test
    fun normalizesBootSlotSuffixVariants() {
        assertEquals("_a", RootUtils.normalizeBootSlotSuffix("_A"))
        assertEquals("_a", RootUtils.normalizeBootSlotSuffix("a"))
        assertEquals("_b", RootUtils.normalizeBootSlotSuffix("_b"))
        assertNull(RootUtils.normalizeBootSlotSuffix("normal"))
    }

    @Test
    fun resolvesCurrentAk3TargetSlotSuffix() {
        assertEquals(
            "_a",
            RootUtils.resolveAk3TargetSlotSuffix("_a", RootUtils.Ak3SlotTarget.CURRENT)
        )
        assertEquals(
            "_b",
            RootUtils.resolveAk3TargetSlotSuffix("b", RootUtils.Ak3SlotTarget.CURRENT)
        )
    }

    @Test
    fun resolvesInactiveAk3TargetSlotSuffix() {
        assertEquals(
            "_b",
            RootUtils.resolveAk3TargetSlotSuffix("_a", RootUtils.Ak3SlotTarget.INACTIVE)
        )
        assertEquals(
            "_a",
            RootUtils.resolveAk3TargetSlotSuffix("_b", RootUtils.Ak3SlotTarget.INACTIVE)
        )
    }

    @Test
    fun returnsNullWhenCurrentSlotIsUnknown() {
        assertNull(RootUtils.resolveAk3TargetSlotSuffix(null, RootUtils.Ak3SlotTarget.INACTIVE))
        assertNull(RootUtils.slotNameFromSuffix("normal"))
    }
}
