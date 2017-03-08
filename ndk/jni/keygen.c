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
#include "nfc3d/keygen.h"
#include "util.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>

void nfc3d_keygen_prepare_seed(const nfc3d_keygen_masterkeys * baseKeys, const uint8_t * baseSeed, uint8_t * output, size_t * outputSize) {
	assert(baseKeys != NULL);
	assert(baseSeed != NULL);
	assert(output != NULL);
	assert(outputSize != NULL);

	uint8_t * start = output;

	// 1: Copy whole type string
	output = memccpy(output, baseKeys->typeString, '\0', sizeof(baseKeys->typeString));

	// 2: Append (16 - magicBytesSize) from the input seed
	size_t leadingSeedBytes = 16 - baseKeys->magicBytesSize;
	memcpy(output, baseSeed, leadingSeedBytes);
	output += leadingSeedBytes;

	// 3: Append all bytes from magicBytes
	memcpy(output, baseKeys->magicBytes, baseKeys->magicBytesSize);
	output += baseKeys->magicBytesSize;

	// 4: Append bytes 0x10-0x1F from input seed
	memcpy(output, baseSeed + 0x10, 16);
	output += 16;

	// 5: Xor last bytes 0x20-0x3F of input seed with AES XOR pad and append them
	unsigned int i;
	for (i = 0; i < 32; i++) {
		output[i] = baseSeed[i + 32] ^ baseKeys->xorPad[i];
	}
	output += 32;

	*outputSize = output - start;
}

void nfc3d_keygen(const nfc3d_keygen_masterkeys * baseKeys, const uint8_t * baseSeed, nfc3d_keygen_derivedkeys * derivedKeys) {
	uint8_t preparedSeed[NFC3D_DRBG_MAX_SEED_SIZE];
	size_t preparedSeedSize;

	nfc3d_keygen_prepare_seed(baseKeys, baseSeed, preparedSeed, &preparedSeedSize);
	nfc3d_drbg_generate_bytes(baseKeys->hmacKey, sizeof(baseKeys->hmacKey), preparedSeed, preparedSeedSize, (uint8_t *) derivedKeys, sizeof(*derivedKeys));
}
