/*
 * ====================================================================
 * SSBU_Amiibo Copyright (c) 2021 odwdinc
 * src/ssbu_amiibo/amiibo_class.py
 * smash-amiibo-editor Copyright (c) 2021 jozz024
 * utils/ssbu_amiibo.py
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */

package com.hiddenramblings.tagmo.amiibo.tagdata;

public class Checksum {

    private final byte[] u0;

    public Checksum() {
        int p0 = 0xEDB88320 | 0x80000000;

        u0 = new byte[0x100];
        int i = 0x1;
        while ((i & 0xFF) != 0) {
            int t0 = i;
            for (int x = 0; x < 0x8; x++) {
                boolean b = (t0 & 0x1) != 0;
                t0 >>= 0x1;
                if (b) t0 ^= p0;
            }
            u0[i] = (byte) (t0);
            i += 0x1;
        }
    }

    public int generate(byte[] appData) {
        byte[] checksum = new byte[0xD4];
        System.arraycopy(appData, 0x04, checksum, 0, checksum.length);

        int t = 0x0;
        for (byte k : checksum) {
            t = ((t >> 0x8) ^ u0[(k ^ t) & 0xFF]);
        }
        return t ^ 0xFFFFFFFF;
    }
}
