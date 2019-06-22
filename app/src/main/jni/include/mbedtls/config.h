/*
 *  Reduced configuration used by Picocoin.
 *
 *  Copyright (C) 2006-2015, ARM Limited, All Rights Reserved
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may
 *  not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  This file is part of mbed TLS (https://tls.mbed.org)
 */
/*
 * Reduced configuration used by Picocoin.
 *
 * See README.txt for usage instructions.
 *
 * Distinguishing features:
 * - no SSL/TLS;
 * - no X.509;
 * - ECDSA/PK and some other chosen crypto bits.
 */

#ifndef MBEDTLS_CONFIG_H
#define MBEDTLS_CONFIG_H

/* System support */
#define MBEDTLS_PLATFORM_C

/* mbed TLS feature support */
#define MBEDTLS_CIPHER_MODE_CTR

/* mbed TLS modules */
#define MBEDTLS_AES_C
#define MBEDTLS_MD_C
#define MBEDTLS_SHA256_C

#include "check_config.h"

#endif /* MBEDTLS_CONFIG_H */
