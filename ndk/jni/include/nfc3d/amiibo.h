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

#ifndef HAVE_NFC3D_AMIIBO_H
#define HAVE_NFC3D_AMIIBO_H

#include <stdint.h>
#include <stdbool.h>
#include "keygen.h"

#define NFC3D_AMIIBO_SIZE 520

#pragma pack(1)
typedef struct {
	nfc3d_keygen_masterkeys data;
	nfc3d_keygen_masterkeys tag;
} nfc3d_amiibo_keys;
#pragma pack()

bool nfc3d_amiibo_unpack(const nfc3d_amiibo_keys * amiiboKeys, const uint8_t * tag, uint8_t * plain);
void nfc3d_amiibo_pack(const nfc3d_amiibo_keys * amiiboKeys, const uint8_t * plain, uint8_t * tag);
#endif
