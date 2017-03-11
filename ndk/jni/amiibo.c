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

#include "nfc3d/amiibo.h"
#include "util.h"
#include <openssl/hmac.h>
#include <errno.h>

#define HMAC_POS_DATA 0x008
#define HMAC_POS_TAG 0x1B4

void nfc3d_amiibo_calc_seed(const uint8_t * dump, uint8_t * key) {
	memcpy(key + 0x00, dump + 0x029, 0x02);
	memset(key + 0x02, 0x00, 0x0E);
	memcpy(key + 0x10, dump + 0x1D4, 0x08);
	memcpy(key + 0x18, dump + 0x1D4, 0x08);
	memcpy(key + 0x20, dump + 0x1E8, 0x20);
}

void nfc3d_amiibo_keygen(const nfc3d_keygen_masterkeys * masterKeys, const uint8_t * dump, nfc3d_keygen_derivedkeys * derivedKeys) {
	uint8_t seed[NFC3D_KEYGEN_SEED_SIZE];

	nfc3d_amiibo_calc_seed(dump, seed);
	nfc3d_keygen(masterKeys, seed, derivedKeys);
}

void nfc3d_amiibo_cipher(const nfc3d_keygen_derivedkeys * keys, const uint8_t * in, uint8_t * out) {
	aes128ctr(in + 0x02C, out + 0x02C, 0x188, keys->aesKey, keys->aesIV);

	memcpy(out + 0x000, in + 0x000, 0x008);
	// Data signature NOT copied
	memcpy(out + 0x028, in + 0x028, 0x004);
	// Tag signature NOT copied
	memcpy(out + 0x1D4, in + 0x1D4, 0x034);
}

void nfc3d_amiibo_tag_to_internal(const uint8_t * tag, uint8_t * intl) {
	memcpy(intl + 0x000, tag + 0x008, 0x008);
	memcpy(intl + 0x008, tag + 0x080, 0x020);
	memcpy(intl + 0x028, tag + 0x010, 0x024);
	memcpy(intl + 0x04C, tag + 0x0A0, 0x168);
	memcpy(intl + 0x1B4, tag + 0x034, 0x020);
	memcpy(intl + 0x1D4, tag + 0x000, 0x008);
	memcpy(intl + 0x1DC, tag + 0x054, 0x02C);
}

void nfc3d_amiibo_internal_to_tag(const uint8_t * intl, uint8_t * tag) {
	memcpy(tag + 0x008, intl + 0x000, 0x008);
	memcpy(tag + 0x080, intl + 0x008, 0x020);
	memcpy(tag + 0x010, intl + 0x028, 0x024);
	memcpy(tag + 0x0A0, intl + 0x04C, 0x168);
	memcpy(tag + 0x034, intl + 0x1B4, 0x020);
	memcpy(tag + 0x000, intl + 0x1D4, 0x008);
	memcpy(tag + 0x054, intl + 0x1DC, 0x02C);
}

bool nfc3d_amiibo_unpack(const nfc3d_amiibo_keys * amiiboKeys, const uint8_t * tag, uint8_t * plain) {
	uint8_t internal[NFC3D_AMIIBO_SIZE];
	nfc3d_keygen_derivedkeys dataKeys;
	nfc3d_keygen_derivedkeys tagKeys;

	// Convert format
	nfc3d_amiibo_tag_to_internal(tag, internal);

	// Generate keys
	nfc3d_amiibo_keygen(&amiiboKeys->data, internal, &dataKeys);
	nfc3d_amiibo_keygen(&amiiboKeys->tag, internal, &tagKeys);

	// Decrypt
	nfc3d_amiibo_cipher(&dataKeys, internal, plain);

	// Init OpenSSL HMAC context
	HMAC_CTX hmacCtx;
	HMAC_CTX_init(&hmacCtx);

	// Regenerate tag HMAC. Note: order matters, data HMAC depends on tag HMAC!
	HMAC_Init_ex(&hmacCtx, tagKeys.hmacKey, sizeof(tagKeys.hmacKey), EVP_sha256(), NULL);
	HMAC_Update(&hmacCtx, plain + 0x1D4, 0x34);
	HMAC_Final(&hmacCtx, plain + HMAC_POS_TAG, NULL);

	// Regenerate data HMAC
	HMAC_Init_ex(&hmacCtx, dataKeys.hmacKey, sizeof(dataKeys.hmacKey), EVP_sha256(), NULL);
	HMAC_Update(&hmacCtx, plain + 0x029, 0x1DF);
	HMAC_Final(&hmacCtx, plain + HMAC_POS_DATA, NULL);

	// HMAC cleanup
	HMAC_CTX_cleanup(&hmacCtx);

	return
			memcmp(plain + HMAC_POS_DATA, internal + HMAC_POS_DATA, 32) == 0 &&
			memcmp(plain + HMAC_POS_TAG, internal + HMAC_POS_TAG, 32) == 0;
}

void nfc3d_amiibo_pack(const nfc3d_amiibo_keys * amiiboKeys, const uint8_t * plain, uint8_t * tag) {
	uint8_t cipher[NFC3D_AMIIBO_SIZE];
	nfc3d_keygen_derivedkeys tagKeys;
	nfc3d_keygen_derivedkeys dataKeys;

	// Generate keys
	nfc3d_amiibo_keygen(&amiiboKeys->tag, plain, &tagKeys);
	nfc3d_amiibo_keygen(&amiiboKeys->data, plain, &dataKeys);

	// Init OpenSSL HMAC context
	HMAC_CTX hmacCtx;
	HMAC_CTX_init(&hmacCtx);

	// Generate tag HMAC
	HMAC_Init_ex(&hmacCtx, tagKeys.hmacKey, sizeof(tagKeys.hmacKey), EVP_sha256(), NULL);
	HMAC_Update(&hmacCtx, plain + 0x1D4, 0x34);
	HMAC_Final(&hmacCtx, cipher + HMAC_POS_TAG, NULL);

	// Generate data HMAC
	HMAC_Init_ex(&hmacCtx, dataKeys.hmacKey, sizeof(dataKeys.hmacKey), EVP_sha256(), NULL);
	HMAC_Update(&hmacCtx, plain + 0x029, 0x18B); // Data
	HMAC_Update(&hmacCtx, cipher + HMAC_POS_TAG, 0x20); // Tag HMAC
	HMAC_Update(&hmacCtx, plain + 0x1D4, 0x34); // Here be dragons
	HMAC_Final(&hmacCtx, cipher + HMAC_POS_DATA, NULL);

	// HMAC cleanup
	HMAC_CTX_cleanup(&hmacCtx);

	// Encrypt
	nfc3d_amiibo_cipher(&dataKeys, plain, cipher);

	// Convert back to hardware
	nfc3d_amiibo_internal_to_tag(cipher, tag);
}

bool nfc3d_amiibo_load_keys(nfc3d_amiibo_keys * amiiboKeys, const char * path) {
	FILE * f = fopen(path, "rb");
	if (!f) {
		return false;
	}

	if (!fread(amiiboKeys, sizeof(*amiiboKeys), 1, f)) {
		fclose(f);
		return false;
	}
	fclose(f);

	if (
		(amiiboKeys->data.magicBytesSize > 16) ||
		(amiiboKeys->tag.magicBytesSize > 16)
	) {
		errno = EILSEQ;
		return false;
	}

	return true;
}
