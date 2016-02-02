/*
 * Copyright (C) 2015 Marcos Vives Del Sol
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

#include "util.h"
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <stdint.h>

void aes128ctr(const uint8_t * in, uint8_t * out, size_t size, const uint8_t * key, const uint8_t * iv) {
	EVP_CIPHER_CTX ctx;
	int pos;

	EVP_CIPHER_CTX_init(&ctx);
	EVP_EncryptInit_ex(&ctx, EVP_aes_128_ctr(), NULL, key, iv);
	EVP_EncryptUpdate(&ctx, out, &pos, in, size);
	EVP_EncryptFinal_ex(&ctx, out + pos, &pos);
	EVP_CIPHER_CTX_cleanup(&ctx);
}

void sha256hmac(const uint8_t * key, size_t keySize, const uint8_t * in, size_t inSize, uint8_t * out) {
	unsigned int hmacLen;
	HMAC(EVP_sha256(), key, keySize, in, inSize, out, &hmacLen);
}

void printhex(void * data, size_t size) {
	size_t i;
	uint8_t * bytes = (uint8_t *) data;
	for (i = 0; i < size; i++) {
		if ((i % 16) > 0) {
			printf(" ");
		}
		printf("%02X", bytes[i]);
		if ((i % 16) == 15) {
			printf("\n");
		}
	}
	if ((i % 16) != 15) {
		printf("\n");
	}
}
