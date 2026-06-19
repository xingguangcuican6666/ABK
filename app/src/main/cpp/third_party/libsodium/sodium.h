#pragma once

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

#define sodium_base64_VARIANT_ORIGINAL 1

#define crypto_box_PUBLICKEYBYTES 32U
#define crypto_box_MACBYTES 16U
#define crypto_box_SEALBYTES (crypto_box_PUBLICKEYBYTES + crypto_box_MACBYTES)

int sodium_init(void);

size_t sodium_base64_encoded_len(size_t bin_len, int variant);

char *sodium_bin2base64(char *b64, size_t b64_maxlen,
                        const unsigned char *bin, size_t bin_len,
                        int variant);

int sodium_base642bin(unsigned char *bin, size_t bin_maxlen,
                      const char *b64, size_t b64_len,
                      const char *ignore, size_t *bin_len,
                      const char **b64_end, int variant);

int crypto_box_seal(unsigned char *c, const unsigned char *m,
                    unsigned long long mlen, const unsigned char *pk);

#ifdef __cplusplus
}
#endif
