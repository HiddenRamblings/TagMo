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

#ifndef HAVE_NFC3D_KEYGEN_H
#define HAVE_NFC3D_KEYGEN_H

#include <stdint.h>
#include <stdbool.h>

#define NFC3D_KEYGEN_SEED_SIZE 64

#pragma pack(1)
typedef struct {
	uint8_t hmacKey[16];
	char typeString[14];
	uint8_t rfu;
	uint8_t magicBytesSize;
	uint8_t magicBytes[16];
	uint8_t xorPad[32];
} nfc3d_keygen_masterkeys;

typedef struct {
	const uint8_t aesKey[16];
	const uint8_t aesIV[16];
	const uint8_t hmacKey[16];
} nfc3d_keygen_derivedkeys;
#pragma pack()

void nfc3d_keygen(const nfc3d_keygen_masterkeys * baseKeys, const uint8_t * baseSeed, nfc3d_keygen_derivedkeys * derivedKeys);

#endif
