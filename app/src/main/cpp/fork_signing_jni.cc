#include <jni.h>

#include <sodium.h>

#include <cstring>
#include <string>
#include <vector>

namespace {

bool ensure_sodium_ready() {
    static const bool ready = sodium_init() >= 0;
    return ready;
}

jstring new_illegal_state(JNIEnv *env, const char *message) {
    jclass exception = env->FindClass("java/lang/IllegalStateException");
    if (exception != nullptr) {
        env->ThrowNew(exception, message);
    }
    return nullptr;
}

}  // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_abk_kernel_utils_AbkKsuNative_encryptGitHubSecretNative(
    JNIEnv *env,
    jobject,
    jstring secret_value,
    jstring public_key_base64
) {
    if (secret_value == nullptr || public_key_base64 == nullptr) {
        return new_illegal_state(env, "Missing GitHub secret input");
    }
    if (!ensure_sodium_ready()) {
        return new_illegal_state(env, "libsodium initialization failed");
    }

    const char *secret_chars = env->GetStringUTFChars(secret_value, nullptr);
    const char *public_key_chars = env->GetStringUTFChars(public_key_base64, nullptr);
    if (secret_chars == nullptr || public_key_chars == nullptr) {
        if (secret_chars != nullptr) {
            env->ReleaseStringUTFChars(secret_value, secret_chars);
        }
        if (public_key_chars != nullptr) {
            env->ReleaseStringUTFChars(public_key_base64, public_key_chars);
        }
        return new_illegal_state(env, "Failed to read GitHub secret input");
    }

    std::string secret(secret_chars);
    std::string public_key_b64(public_key_chars);
    env->ReleaseStringUTFChars(secret_value, secret_chars);
    env->ReleaseStringUTFChars(public_key_base64, public_key_chars);

    std::vector<unsigned char> public_key(crypto_box_PUBLICKEYBYTES);
    size_t public_key_len = 0;
    const int decode_result = sodium_base642bin(
        public_key.data(),
        public_key.size(),
        public_key_b64.c_str(),
        public_key_b64.size(),
        nullptr,
        &public_key_len,
        nullptr,
        sodium_base64_VARIANT_ORIGINAL
    );
    if (decode_result != 0 || public_key_len != crypto_box_PUBLICKEYBYTES) {
        return new_illegal_state(env, "Invalid GitHub repository public key");
    }

    std::vector<unsigned char> cipher(secret.size() + crypto_box_SEALBYTES);
    if (crypto_box_seal(
            cipher.data(),
            reinterpret_cast<const unsigned char *>(secret.data()),
            static_cast<unsigned long long>(secret.size()),
            public_key.data()) != 0) {
        return new_illegal_state(env, "Failed to encrypt secret with repository public key");
    }

    const size_t encoded_len = sodium_base64_encoded_len(
        cipher.size(),
        sodium_base64_VARIANT_ORIGINAL
    );
    std::string encoded(encoded_len, '\0');
    sodium_bin2base64(
        encoded.data(),
        encoded.size(),
        cipher.data(),
        cipher.size(),
        sodium_base64_VARIANT_ORIGINAL
    );
    encoded.resize(std::strlen(encoded.c_str()));
    return env->NewStringUTF(encoded.c_str());
}
