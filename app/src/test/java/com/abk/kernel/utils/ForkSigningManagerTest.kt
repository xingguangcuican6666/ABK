package com.abk.kernel.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForkSigningManagerTest {

    @Test
    fun importSigningMaterial_roundTripsGeneratedPem() {
        val material = ForkSigningManager.generateSigningMaterial()

        val imported = ForkSigningManager.importSigningMaterial(
            publicKeyPem = material.publicKeyPem,
            privateKeyPem = material.privateKeyPem
        )

        assertEquals(material.publicKeyBase64, imported.publicKeyBase64)
        assertEquals(material.privateKeyBase64, imported.privateKeyBase64)
        assertTrue(imported.publicKeyPem.contains("-----BEGIN PUBLIC KEY-----"))
        assertTrue(imported.privateKeyPem.contains("-----BEGIN PRIVATE KEY-----"))
    }

    @Test
    fun importSigningMaterial_rejectsMismatchedPair() {
        val first = ForkSigningManager.generateSigningMaterial()
        val second = ForkSigningManager.generateSigningMaterial()

        val error = runCatching {
            ForkSigningManager.importSigningMaterial(
                publicKeyPem = first.publicKeyPem,
                privateKeyPem = second.privateKeyPem
            )
        }.exceptionOrNull()

        assertTrue(error is ForkSigningImportException)
        assertEquals(ForkSigningImportError.KEY_MISMATCH, (error as ForkSigningImportException).reason)
    }

    @Test
    fun importSigningMaterial_rejectsBlankPublicKey() {
        val material = ForkSigningManager.generateSigningMaterial()

        val error = runCatching {
            ForkSigningManager.importSigningMaterial(
                publicKeyPem = "   ",
                privateKeyPem = material.privateKeyPem
            )
        }.exceptionOrNull()

        assertTrue(error is ForkSigningImportException)
        assertEquals(ForkSigningImportError.EMPTY_PUBLIC_KEY, (error as ForkSigningImportException).reason)
    }
}
