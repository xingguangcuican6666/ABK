package com.abk.kernel.utils

import com.abk.kernel.data.model.GitHubSecretPublicKey
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.util.Base64

data class ForkSigningMaterial(
    val privateKeyPem: String,
    val privateKeyBase64: String,
    val publicKeyPem: String,
    val publicKeyBase64: String
)

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

    private fun pem(type: String, bytes: ByteArray): String {
        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(bytes)
        return buildString {
            append("-----BEGIN $type-----\n")
            append(encoded)
            append("\n-----END $type-----\n")
        }
    }
}
