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

#include "nfc3d/drbg.h"
#include <assert.h>
#include <string.h>
#include <openssl/evp.h>

void nfc3d_drbg_init(nfc3d_drbg_ctx * ctx, const uint8_t * hmacKey, size_t hmacKeySize, const uint8_t * seed, size_t seedSize) {
	assert(ctx != NULL);
	assert(hmacKey != NULL);
	assert(seed != NULL);
	assert(seedSize <= NFC3D_DRBG_MAX_SEED_SIZE);

	// Initialize primitives
	ctx->used = false;
	ctx->iteration = 0;
	ctx->bufferSize = sizeof(ctx->iteration) + seedSize;

	// The 16-bit counter is prepended to the seed when hashing, so we'll leave 2 bytes at the start
	memcpy(ctx->buffer + sizeof(uint16_t), seed, seedSize);

	// Initialize underlying HMAC context
	HMAC_CTX_init(&ctx->hmacCtx);
	HMAC_Init_ex(&ctx->hmacCtx, hmacKey, hmacKeySize, EVP_sha256(), NULL);
}

void nfc3d_drbg_step(nfc3d_drbg_ctx * ctx, uint8_t * output) {
	assert(ctx != NULL);
	assert(output != NULL);

	if (ctx->used) {
		// If used at least once, reinitialize the HMAC
		HMAC_Init_ex(&ctx->hmacCtx, NULL, 0, NULL, NULL);
	} else {
		ctx->used = true;
	}

	// Store counter in big endian, and increment it
	ctx->buffer[0] = ctx->iteration >> 8;
	ctx->buffer[1] = ctx->iteration >> 0;
	ctx->iteration++;

	// Do HMAC magic
	HMAC_Update(&ctx->hmacCtx, ctx->buffer, ctx->bufferSize);
	HMAC_Final(&ctx->hmacCtx, output, NULL);
}

void nfc3d_drbg_cleanup(nfc3d_drbg_ctx * ctx) {
	assert(ctx != NULL);
	HMAC_CTX_cleanup(&ctx->hmacCtx);
}

void nfc3d_drbg_generate_bytes(const uint8_t * hmacKey, size_t hmacKeySize, const uint8_t * seed, size_t seedSize, uint8_t * output, size_t outputSize) {
	uint8_t temp[NFC3D_DRBG_OUTPUT_SIZE];

	nfc3d_drbg_ctx rngCtx;
	nfc3d_drbg_init(&rngCtx, hmacKey, hmacKeySize, seed, seedSize);

	while (outputSize > 0) {
		if (outputSize < NFC3D_DRBG_OUTPUT_SIZE) {
			nfc3d_drbg_step(&rngCtx, temp);
			memcpy(output, temp, outputSize);
			break;
		}

		nfc3d_drbg_step(&rngCtx, output);
		output += NFC3D_DRBG_OUTPUT_SIZE;
		outputSize -= NFC3D_DRBG_OUTPUT_SIZE;
	}

	nfc3d_drbg_cleanup(&rngCtx);
}
