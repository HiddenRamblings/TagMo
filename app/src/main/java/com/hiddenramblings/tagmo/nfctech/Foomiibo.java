/*
 * ====================================================================
 * Copyright (c) 2021-2022 AbandonedCart.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * For the purpose of this license, the phrase "TagMo labels" shall
 * be used to refer to the labels "8-bit Dream", "TwistedUmbrella",
 * "TagMo" and "AbandonedCart" and these labels should be considered
 * the equivalent of any usage of the aforementioned phrase.
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All materials mentioning features or use of this software and
 *    redistributions of any form whatsoever must display the following
 *    acknowledgment unless made available by tagged, public "commits":
 *    "This product includes software developed for TagMo by AbandonedCart"
 *
 * 4. The TagMo labels must not be used in any form to endorse or promote
 *    products derived from this software without prior written permission.
 *    For written permission, please contact enderinexiledc@gmail.com
 *
 * 5. Products derived from this software may not be called by the TagMo labels
 *    nor may these labels appear in their names or product information without
 *    prior written permission of AbandonedCart.
 *
 * THIS SOFTWARE IS PROVIDED BY AbandonedCart AND TagMo ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */

package com.hiddenramblings.tagmo.nfctech;

import static java.lang.Integer.parseInt;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.eightbit.io.Debug;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

public class Foomiibo {

    public Foomiibo() { }

    private byte[] getRandomBytes(int size) {
        Random random = new Random();
        byte[] randBytes = new byte[size];
        random.nextBytes(randBytes);
        return randBytes;
    }

    public byte[] generateRandomUID() {
        byte[] uid = getRandomBytes(9);
        uid[0x0] = 0x04;
        uid[0x3] = (byte) (0x88 ^ uid[0] ^ uid[1] ^ uid[2]);
        uid[0x8] = (byte) (uid[4] ^ uid[5] ^ uid[6] ^ uid[7]);
        return uid;
    }

    @SuppressWarnings("unused")
    private String randomizeSerial(String serial) {
        Random random = new Random();
        String week = new DecimalFormat("00").format(
                random.nextInt(52 - 1 + 1) + 1);
        String year = String.valueOf(random.nextInt(9 + 1));
        String identifier = serial.substring(3, 7);
        String facility = TagMo.getContext().getResources().getStringArray(
                R.array.production_factory)[random.nextInt(3 + 1)];

        return week + year + "000" + identifier + facility;
    }

    public byte[] generateData(String id) {
        byte[] arr = new byte[NfcByte.TAG_DATA_SIZE];

        // Set UID, BCC0
        // 0x04, (byte) 0xC0, 0x0A, 0x46, 0x61, 0x6B, 0x65, 0x0A
        byte[] uid = generateRandomUID();
        System.arraycopy(uid, 0, arr, 0x1D4, uid.length);

        // Set BCC1
        arr[0] = uid[0x8];

        // Set Internal, Static Lock, and CC
        byte[] CC = new byte[]{ 0x48, 0x0F, (byte) 0xE0, (byte) 0xF1, 0x10, (byte) 0xFF, (byte) 0xEE };
        System.arraycopy(CC, 0, arr, 0x1, CC.length);

        // Set 0xA5, Write Counter, and Unknown
        byte[] OxA5 = new byte[]{ (byte) 0xA5, 0x00, 0x00, 0x00 };
        System.arraycopy(OxA5, 0, arr, 0x28, OxA5.length);

        // Set Dynamic Lock, and RFUI
        byte[] RFUI = new byte[]{ 0x01, 0x00, 0x0F, (byte) 0xBD };
        System.arraycopy(RFUI, 0, arr, 0x208, RFUI.length);

        // Set CFG0
        byte[] CFG0 = new byte[]{ 0x00, 0x00, 0x00, 0x04 };
        System.arraycopy(CFG0, 0, arr, 0x20C, CFG0.length);

        // Set CFG1
        byte[] CFG1 = new byte[]{ 0x5F, 0x00, 0x00, 0x00 };
        System.arraycopy(CFG1, 0, arr, 0x210, CFG1.length);

        // Set Keygen Salt
        byte[] salt = getRandomBytes(32);
        System.arraycopy(salt, 0, arr, 0x1E8, salt.length);

        int off1 = 0x54, off2 = 0x1DC;
        // Write Identification Block
        for (int i = 0; i < 16; i += 2, off1 += 1, off2 += 1) {
            byte currByte = (byte) parseInt(id.substring(i, i + 2), 16);
            arr[off1] = currByte;
            arr[off2] = currByte;
        }

        return arr;
    }

    public byte[] generateData(long id) {
        return generateData(String.valueOf(id));
    }

    private static final String hexSingature = "5461674d6f20382d426974204e544147";

    public byte[] getSignedData(byte[] tagData) {
        byte[] signedData = new byte[NfcByte.TAG_FILE_SIZE];
        System.arraycopy(tagData, 0, signedData, 0x0, tagData.length);
        byte[] signature = TagArray.hexToByteArray(hexSingature);
        System.arraycopy(signature, 0, signedData, 0x21C, signature.length);
        return signedData;
    }

    public byte[] getSignedData(String id) {
        return getSignedData(generateData(id));
    }

    public static String getDataSignature(byte[] tagData) {
        if (tagData.length == NfcByte.TAG_FILE_SIZE) {
            String signature = TagArray.bytesToHex(
                    Arrays.copyOfRange(tagData, 540, NfcByte.TAG_FILE_SIZE)
            ).substring(0, 32).toLowerCase(Locale.US);
            Debug.Info(TagMo.class, TagArray.hexToString(signature));
            if (hexSingature.equals(signature)) return signature;
        }
        return null;
    }
}
