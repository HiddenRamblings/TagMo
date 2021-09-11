#ifndef TAGMO_MAIN_C_H
#define TAGMO_MAIN_C_H

#include <openssl/evp.h>
#include <openssl/hmac.h>

void test() {
    EVP_CIPHER_CTX ctx;
    EVP_CIPHER_CTX_init(&ctx);
}

#endif //TAGMO_MAIN_C_H
