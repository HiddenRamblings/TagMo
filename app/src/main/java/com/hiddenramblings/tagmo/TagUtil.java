package com.hiddenramblings.tagmo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TagUtil {
    public static final int TAG_FILE_SIZE = 532;
    public static final int PAGE_SIZE = 4;
    public static final int AMIIBO_ID_OFFSET = 0x54;

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

    /**
     * Returns the UID of a tag from first two pages of data (TagFormat)
     */
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

    public static long amiiboIdFromTag(byte[] data) throws Exception {
        if (data.length < TAG_FILE_SIZE)
            throw new Exception("Invalid tag data");

        byte[] amiiboId = new byte[4*2];
        System.arraycopy(data, AMIIBO_ID_OFFSET, amiiboId, 0, amiiboId.length);
        return ByteBuffer.wrap(amiiboId).getLong();
    }

    public static String amiiboIdToHex(long amiiboId) {
        return String.format("%016X", amiiboId);
    }

    public static byte[][] splitPages(byte[] data) throws Exception {
        if (data.length < TAG_FILE_SIZE)
            throw new Exception("Invalid tag data");

        byte[][] pages = new byte[data.length / TagUtil.PAGE_SIZE][];
        for (int i = 0, j = 0; i < data.length; i += TagUtil.PAGE_SIZE, j++) {
            pages[j] = Arrays.copyOfRange(data, i, i + TagUtil.PAGE_SIZE);
        }
        return pages;
    }

    public static void validateTag(byte[] data) throws Exception {
        byte[][] pages = TagUtil.splitPages(data);

        if (pages[0][0] != (byte) 0x04)
            throw new Exception("Invalid tag file. Tag must start with a 0x04.");

        if (pages[2][2] != (byte) 0x0F || pages[2][3] != (byte) 0xE0)
            throw new Exception("Invalid tag file. lock signature mismatch.");

        if (pages[3][0] != (byte) 0xF1 || pages[3][1] != (byte) 0x10 || pages[3][2] != (byte) 0xFF || pages[3][3] != (byte) 0xEE)
            throw new Exception("Invalid tag file. CC signature mismatch.");

        if (pages[0x82][0] != (byte) 0x01 || pages[0x82][1] != (byte) 0x0 || pages[0x82][2] != (byte) 0x0F)
            throw new Exception("Invalid tag file. dynamic lock signature mismatch.");

        if (pages[0x83][0] != (byte) 0x0 || pages[0x83][1] != (byte) 0x0 || pages[0x83][2] != (byte) 0x0 || pages[0x83][3] != (byte) 0x04)
            throw new Exception("Invalid tag file. CFG0 signature mismatch.");

        if (pages[0x84][0] != (byte) 0x5F || pages[0x84][1] != (byte) 0x0 || pages[0x84][2] != (byte) 0x0 || pages[0x84][3] != (byte) 0x00)
            throw new Exception("Invalid tag file. CFG1 signature mismatch.");
    }

    public static byte[] decrypt(KeyManager keyManager, byte[] tagData) throws Exception {
        if (!keyManager.hasFixedKey() || !keyManager.hasUnFixedKey())
            throw new Exception("Key files not loaded!");

        AmiiTool tool = new AmiiTool();
        if (tool.setKeysFixed(keyManager.fixedKey, keyManager.fixedKey.length) == 0)
            throw new Exception("Failed to initialise amiitool");
        if (tool.setKeysUnfixed(keyManager.unfixedKey, keyManager.unfixedKey.length)== 0)
            throw new Exception("Failed to initialise amiitool");
        byte[] decrypted = new byte[TagUtil.TAG_FILE_SIZE];
        if (tool.unpack(tagData, tagData.length, decrypted, decrypted.length) == 0)
            throw new Exception("Failed to decrypt tag");

        return decrypted;
    }

    public static byte[] encrypt(KeyManager keyManager, byte[] tagData) throws Exception {
        if (!keyManager.hasFixedKey() || !keyManager.hasUnFixedKey())
            throw new Exception("Key files not loaded!");

        AmiiTool tool = new AmiiTool();
        if (tool.setKeysFixed(keyManager.fixedKey, keyManager.fixedKey.length) == 0)
            throw new Exception("Failed to initialise amiitool");
        if (tool.setKeysUnfixed(keyManager.unfixedKey, keyManager.unfixedKey.length)== 0)
            throw new Exception("Failed to initialise amiitool");
        byte[] encrypted = new byte[TagUtil.TAG_FILE_SIZE];
        if (tool.pack(tagData, tagData.length, encrypted, encrypted.length) == 0)
            throw new Exception("Failed to decrypt tag");

        return encrypted;
    }


    public static byte[] patchUid(byte[] uid, byte[] tagData, KeyManager keyManager, boolean encrypted) throws Exception {
        if (encrypted)
            tagData = decrypt(keyManager, tagData);

        if (uid.length < 9) throw new Exception("Invalid uid length");

        byte[] patched = Arrays.copyOf(tagData, tagData.length);

        System.arraycopy(uid, 0, patched, 0x1d4, 8);
        patched[0] = uid[8];

        AmiiTool tool = new AmiiTool();
        byte[] result = new byte[TagUtil.TAG_FILE_SIZE];
        if (tool.pack(patched, patched.length, result, result.length) == 0)
            throw new Exception("Failed to encrypt tag");

        return result;
    }

    public static byte[] readTag(InputStream inputStream) throws Exception {
        byte[] data = new byte[TAG_FILE_SIZE];
        try {
            int len = inputStream.read(data);
            if (len != TAG_FILE_SIZE)
                throw new Exception("Invalid file size. was expecting " + TAG_FILE_SIZE);

            return data;
        } finally {
            inputStream.close();
        }
    }
}
