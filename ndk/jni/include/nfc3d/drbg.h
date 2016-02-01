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

#ifndef HAVE_NFC3D_DRBG_H
#define HAVE_NFC3D_DRBG_H

#include <stdbool.h>
#include <stdint.h>
#include <openssl/hmac.h>

#define NFC3D_DRBG_MAX_SEED_SIZE	480	/* Hardcoded max size in 3DS NFC module */
#define NFC3D_DRBG_OUTPUT_SIZE		32	/* Every iteration generates 32 bytes */

typedef struct {
	HMAC_CTX hmacCtx;
	bool used;
	uint16_t iteration;

	uint8_t buffer[sizeof(uint16_t) + NFC3D_DRBG_MAX_SEED_SIZE];
	size_t bufferSize;
} nfc3d_drbg_ctx;

void nfc3d_drbg_init(nfc3d_drbg_ctx * ctx, const uint8_t * hmacKey, size_t hmacKeySize, const uint8_t * seed, size_t seedSize);
void nfc3d_drbg_step(nfc3d_drbg_ctx * ctx, uint8_t * output);
void nfc3d_drbg_cleanup(nfc3d_drbg_ctx * ctx);
void nfc3d_drbg_generate_bytes(const uint8_t * hmacKey, size_t hmacKeySize, const uint8_t * seed, size_t seedSize, uint8_t * output, size_t outputSize);

#endif

