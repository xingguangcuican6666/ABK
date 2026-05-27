package com.abk.kernel.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RootUtilsAnyKernelSlotTest {

    @Test
    fun rewritesSlotSelectToInactive() {
        val original = """
            do_device_check=1
            slot_select=active
            block=/dev/block/bootdevice/by-name/boot
        """.trimIndent()

        val rewritten = RootUtils.rewriteAnyKernelSlotSelect(
            original,
            RootUtils.Ak3SlotTarget.INACTIVE
        )

        assertEquals(
            """
                do_device_check=1
                slot_select=inactive
                block=/dev/block/bootdevice/by-name/boot
            """.trimIndent(),
            rewritten
        )
    }

    @Test
    fun rewritesAutoSelectionToCurrentSlot() {
        val original = """
            do.devicecheck=1
            slot_select=auto
        """.trimIndent()

        val rewritten = RootUtils.rewriteAnyKernelSlotSelect(
            original,
            RootUtils.Ak3SlotTarget.CURRENT
        )

        assertEquals(
            """
                do.devicecheck=1
                slot_select=active
            """.trimIndent(),
            rewritten
        )
    }

    @Test
    fun returnsNullWhenSlotSelectIsMissing() {
        assertNull(
            RootUtils.rewriteAnyKernelSlotSelect(
                "do.devicecheck=1\nblock=boot",
                RootUtils.Ak3SlotTarget.INACTIVE
            )
        )
    }
}
