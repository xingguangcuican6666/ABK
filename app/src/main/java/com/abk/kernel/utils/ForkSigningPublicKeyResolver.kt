package com.abk.kernel.utils

import com.abk.kernel.data.repository.Result

data class ForkSigningPublicKeyValue(
    val base64: String,
    val pem: String,
)

internal const val INVALID_FORK_SIGNING_PUBLIC_KEY_MESSAGE =
    "Fork signing public key is unavailable or invalid"

class ForkSigningPublicKeyResolver(
    private val assetName: String,
    private val downloadReleaseAssetText: suspend (
        owner: String,
        repo: String,
        releaseId: Long,
        assetName: String,
    ) -> Result<String>,
) {
    fun parse(value: String?): ForkSigningPublicKeyValue? {
        val base64 = ForkSigningManager.publicKeyBase64FromStoredValue(value) ?: return null
        val pem = runCatching { ForkSigningManager.publicKeyPemFromBase64(base64) }.getOrNull()
            ?: return null
        return ForkSigningPublicKeyValue(base64 = base64, pem = pem)
    }

    suspend fun download(owner: String, repo: String, releaseId: Long): Result<ForkSigningPublicKeyValue> =
        when (val result = downloadReleaseAssetText(owner, repo, releaseId, assetName)) {
            is Result.Success -> parse(result.data)?.let { Result.Success(it) }
                ?: Result.Error(INVALID_FORK_SIGNING_PUBLIC_KEY_MESSAGE)
            is Result.Error -> Result.Error(result.message, result.code)
            Result.Loading -> Result.Loading
        }
}
