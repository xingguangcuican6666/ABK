package com.abk.kernel.utils

import com.abk.kernel.data.model.GitHubSecretPublicKey
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class ForkSigningMaterial(
    val privateKeyPem: String,
    val privateKeyBase64: String,
    val publicKeyPem: String,
    val publicKeyBase64: String
)

enum class ForkSigningImportError {
    EMPTY_PUBLIC_KEY,
    EMPTY_PRIVATE_KEY,
    INVALID_PUBLIC_KEY,
    INVALID_PRIVATE_KEY,
    KEY_MISMATCH
}

class ForkSigningImportException(
    val reason: ForkSigningImportError,
    message: String
) : IllegalArgumentException(message)

object ForkSigningManager {
    fun generateSigningMaterial(): ForkSigningMaterial {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        return ForkSigningMaterial(
            privateKeyPem = pem("PRIVATE KEY", pair.private.encoded),
            privateKeyBase64 = Base64.getEncoder().encodeToString(pair.private.encoded),
            publicKeyPem = pem("PUBLIC KEY", pair.public.encoded),
            publicKeyBase64 = Base64.getEncoder().encodeToString(pair.public.encoded)
        )
    }

    fun importSigningMaterial(
        publicKeyPem: String,
        privateKeyPem: String
    ): ForkSigningMaterial {
        val publicKeyBytes = decodePemBlock(publicKeyPem, ForkSigningImportError.EMPTY_PUBLIC_KEY)
        val privateKeyBytes = decodePemBlock(privateKeyPem, ForkSigningImportError.EMPTY_PRIVATE_KEY)
        val publicKey = decodePublicKey(publicKeyBytes)
        val privateKey = decodePrivateKey(privateKeyBytes)
        verifyKeyPair(publicKey, privateKey)
        return ForkSigningMaterial(
            privateKeyPem = normalizePem(privateKeyPem, "PRIVATE KEY"),
            privateKeyBase64 = Base64.getEncoder().encodeToString(privateKey.encoded),
            publicKeyPem = normalizePem(publicKeyPem, "PUBLIC KEY"),
            publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.encoded)
        )
    }

    fun encryptSecretForGitHub(
        secretValue: String,
        publicKey: GitHubSecretPublicKey
    ): String = AbkKsuNative.encryptGitHubSecret(secretValue, publicKey.key)

    fun publicKeyPemFromBase64(base64: String): String =
        pem("PUBLIC KEY", Base64.getDecoder().decode(base64))

    fun publicKeyPemFromStoredValue(value: String?): String? {
        val normalized = normalizeStoredPublicKeyValue(value) ?: return null
        return if (normalized.contains("-----BEGIN")) {
            normalized
        } else {
            runCatching { publicKeyPemFromBase64(normalized) }.getOrNull()
        }
    }

    private fun normalizeStoredPublicKeyValue(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        if (trimmed.contains("-----BEGIN")) return trimmed
        if (trimmed.startsWith("{")) {
            val json = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
            val extracted = sequenceOf(
                "publicKeyBase64",
                "public_key_base64",
                "publicKey",
                "public_key"
            ).mapNotNull { key ->
                json.optString(key).trim().takeIf { it.isNotBlank() }
            }.firstOrNull()
            if (!extracted.isNullOrBlank()) return extracted
        }
        return trimmed
    }

    private fun decodePemBlock(
        pem: String,
        emptyReason: ForkSigningImportError
    ): ByteArray {
        val lines = pem.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() && !it.startsWith("-----BEGIN") && !it.startsWith("-----END") }
            .joinToString("")
        if (lines.isBlank()) {
            throw ForkSigningImportException(
                emptyReason,
                when (emptyReason) {
                    ForkSigningImportError.EMPTY_PUBLIC_KEY -> "Public key PEM is empty"
                    ForkSigningImportError.EMPTY_PRIVATE_KEY -> "Private key PEM is empty"
                    else -> "PEM block is empty"
                }
            )
        }
        return try {
            Base64.getMimeDecoder().decode(lines)
        } catch (error: Throwable) {
            throw ForkSigningImportException(
                when (emptyReason) {
                    ForkSigningImportError.EMPTY_PUBLIC_KEY -> ForkSigningImportError.INVALID_PUBLIC_KEY
                    ForkSigningImportError.EMPTY_PRIVATE_KEY -> ForkSigningImportError.INVALID_PRIVATE_KEY
                    else -> emptyReason
                },
                when (emptyReason) {
                    ForkSigningImportError.EMPTY_PUBLIC_KEY -> "Invalid public key PEM"
                    ForkSigningImportError.EMPTY_PRIVATE_KEY -> "Invalid private key PEM"
                    else -> "Invalid PEM"
                }
            )
        }
    }

    private fun decodePublicKey(bytes: ByteArray): PublicKey = try {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
    } catch (error: Throwable) {
        throw ForkSigningImportException(ForkSigningImportError.INVALID_PUBLIC_KEY, "Invalid public key PEM")
    }

    private fun decodePrivateKey(bytes: ByteArray): PrivateKey = try {
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes))
    } catch (error: Throwable) {
        throw ForkSigningImportException(ForkSigningImportError.INVALID_PRIVATE_KEY, "Invalid private key PEM")
    }

    private fun verifyKeyPair(publicKey: PublicKey, privateKey: PrivateKey) {
        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(challenge)
            sign()
        }
        val verified = Signature.getInstance("SHA256withRSA").run {
            initVerify(publicKey)
            update(challenge)
            verify(signature)
        }
        if (!verified) {
            throw ForkSigningImportException(
                ForkSigningImportError.KEY_MISMATCH,
                "Public key and private key do not match"
            )
        }
    }

    private fun normalizePem(pem: String, type: String): String {
        val lines = pem.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() && !it.startsWith("-----BEGIN") && !it.startsWith("-----END") }
            .joinToString("\n")
        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(
            Base64.getMimeDecoder().decode(lines)
        )
        return buildString {
            append("-----BEGIN $type-----\n")
            append(encoded)
            append("\n-----END $type-----\n")
        }
    }

    private fun pem(type: String, bytes: ByteArray): String {
        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(bytes)
        return buildString {
            append("-----BEGIN $type-----\n")
            append(encoded)
            append("\n-----END $type-----\n")
        }
    }
}
