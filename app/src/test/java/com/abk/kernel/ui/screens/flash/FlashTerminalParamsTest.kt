package com.abk.kernel.ui.screens.flash

import android.os.Parcel
import com.abk.kernel.miuix.ui.screens.flash.common.FlashTerminalParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FlashTerminalParamsTest {

    @Test
    fun parcelableRoundTrip_allFieldsMatch() {
        val original = FlashTerminalParams(
            artifactPath = "/test/boot.img",
            artifactName = "boot.img",
            artifactType = "KERNEL_IMG",
            ak3SlotTarget = "INACTIVE",
            allowHighRiskFallback = true,
            operationTitle = "Flash Kernel"
        )
        val parcel = Parcel.obtain()
        parcel.writeParcelable(original, 0)
        parcel.setDataPosition(0)
        val restored = parcel.readParcelable<FlashTerminalParams>(
            FlashTerminalParams::class.java.classLoader
        )!!
        assertEquals(original.artifactPath, restored.artifactPath)
        assertEquals(original.artifactName, restored.artifactName)
        assertEquals(original.artifactType, restored.artifactType)
        assertEquals(original.ak3SlotTarget, restored.ak3SlotTarget)
        assertEquals(original.allowHighRiskFallback, restored.allowHighRiskFallback)
        assertEquals(original.operationTitle, restored.operationTitle)
        parcel.recycle()
    }

    @Test
    fun nullAk3SlotTarget_survivesSerialization() {
        val original = FlashTerminalParams(
            artifactPath = "/test/boot.img",
            artifactName = "boot.img",
            artifactType = "KERNEL_IMG",
            ak3SlotTarget = null,
            allowHighRiskFallback = true,
            operationTitle = "Flash Kernel"
        )
        val parcel = Parcel.obtain()
        parcel.writeParcelable(original, 0)
        parcel.setDataPosition(0)
        val restored = parcel.readParcelable<FlashTerminalParams>(
            FlashTerminalParams::class.java.classLoader
        )!!
        assertNull(restored.ak3SlotTarget)
        parcel.recycle()
    }

    @Test
    fun allowHighRiskFallback_true_survivesSerialization() {
        val original = FlashTerminalParams(
            artifactPath = "/test/boot.img",
            artifactName = "boot.img",
            artifactType = "ANYKERNEL3",
            ak3SlotTarget = "CURRENT",
            allowHighRiskFallback = true,
            operationTitle = "Flash AnyKernel3"
        )
        val parcel = Parcel.obtain()
        parcel.writeParcelable(original, 0)
        parcel.setDataPosition(0)
        val restored = parcel.readParcelable<FlashTerminalParams>(
            FlashTerminalParams::class.java.classLoader
        )!!
        assertEquals(true, restored.allowHighRiskFallback)
        parcel.recycle()
    }

    @Test
    fun allowHighRiskFallback_false_survivesSerialization() {
        val original = FlashTerminalParams(
            artifactPath = "/test/boot.img",
            artifactName = "boot.img",
            artifactType = "ANYKERNEL3",
            ak3SlotTarget = "CURRENT",
            allowHighRiskFallback = false,
            operationTitle = "Flash AnyKernel3"
        )
        val parcel = Parcel.obtain()
        parcel.writeParcelable(original, 0)
        parcel.setDataPosition(0)
        val restored = parcel.readParcelable<FlashTerminalParams>(
            FlashTerminalParams::class.java.classLoader
        )!!
        assertEquals(false, restored.allowHighRiskFallback)
        parcel.recycle()
    }

    @Test
    fun emptyArtifactPath_survivesSerialization() {
        val original = FlashTerminalParams(
            artifactPath = "",
            artifactName = "boot.img",
            artifactType = "KERNEL_IMG",
            ak3SlotTarget = null,
            allowHighRiskFallback = false,
            operationTitle = "Flash Image"
        )
        val parcel = Parcel.obtain()
        parcel.writeParcelable(original, 0)
        parcel.setDataPosition(0)
        val restored = parcel.readParcelable<FlashTerminalParams>(
            FlashTerminalParams::class.java.classLoader
        )!!
        assertEquals("", restored.artifactPath)
        parcel.recycle()
    }

    @Test
    fun artifactType_KERNEL_IMG_survivesSerialization() {
        assertArtifactTypeRoundTrip("KERNEL_IMG")
    }

    @Test
    fun artifactType_ANYKERNEL3_survivesSerialization() {
        assertArtifactTypeRoundTrip("ANYKERNEL3")
    }

    @Test
    fun artifactType_SUSFS_MODULE_survivesSerialization() {
        assertArtifactTypeRoundTrip("SUSFS_MODULE")
    }

    @Test
    fun artifactType_KSU_MANAGER_survivesSerialization() {
        assertArtifactTypeRoundTrip("KSU_MANAGER")
    }

    private fun assertArtifactTypeRoundTrip(artifactType: String) {
        val original = FlashTerminalParams(
            artifactPath = "/test/boot.img",
            artifactName = "boot.img",
            artifactType = artifactType,
            ak3SlotTarget = null,
            allowHighRiskFallback = false,
            operationTitle = "Flash $artifactType"
        )
        val parcel = Parcel.obtain()
        parcel.writeParcelable(original, 0)
        parcel.setDataPosition(0)
        val restored = parcel.readParcelable<FlashTerminalParams>(
            FlashTerminalParams::class.java.classLoader
        )!!
        assertEquals(artifactType, restored.artifactType)
        parcel.recycle()
    }
}
