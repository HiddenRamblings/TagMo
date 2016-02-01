package com.hiddenramblings.tagmo;

import java.util.Arrays;

public class TagUtil {
    public static final int TAG_FILE_SIZE = 532;
    public static final int PAGE_SIZE = 4;

    public static byte[] keygen(byte[] uuid) {
        //from AmiiManage (GPL)
        byte[] key = new byte[4];
        int[] uuid_to_ints = new int[uuid.length];

        for (int i = 0; i < uuid.length; i++)
            uuid_to_ints[i] = (0xFF & uuid[i]);

        if (uuid.length == 7) {
            key[0] = ((byte) (0xFF & (0xAA ^ (uuid_to_ints[1] ^ uuid_to_ints[3]))));
            key[1] = ((byte) (0xFF & (0x55 ^ (uuid_to_ints[2] ^ uuid_to_ints[4]))));
            key[2] = ((byte) (0xFF & (0xAA ^ (uuid_to_ints[3] ^ uuid_to_ints[5]))));
            key[3] = ((byte) (0xFF & (0x55 ^ (uuid_to_ints[4] ^ uuid_to_ints[6]))));
            return key;
        }

        return null;
    }

    public static byte[] uidFromPages(byte[] pages0_1) {
        //removes the checksum bytes from the first two pages of a tag to get the actual uid
        if (pages0_1.length < 8) return null;

        byte[] key = new byte[7];
        key[0] = pages0_1[0];
        key[1] = pages0_1[1];
        key[2] = pages0_1[2];
        key[3] = pages0_1[4];
        key[4] = pages0_1[5];
        key[5] = pages0_1[6];
        key[6] = pages0_1[7];
        return key;
    }

    public static byte[][] splitPages(byte[] data) { //assume correct sizes
        byte[][] pages = new byte[data.length / TagUtil.PAGE_SIZE][];
        for (int i = 0, j = 0; i < data.length; i += TagUtil.PAGE_SIZE, j++) {
            pages[j] = Arrays.copyOfRange(data, i, i + TagUtil.PAGE_SIZE);
        }
        return pages;
    }

}
