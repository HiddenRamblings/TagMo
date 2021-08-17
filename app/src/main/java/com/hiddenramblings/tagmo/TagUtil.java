package com.hiddenramblings.tagmo;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Random;

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
        return new TagData(data).getAmiiboID();
    }

    public static String amiiboIdToHex(long amiiboId) {
        return String.format("%016X", amiiboId);
    }

    public static byte[][] splitPages(byte[] data) throws Exception {
        if (data.length < TAG_FILE_SIZE)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_data));

        byte[][] pages = new byte[data.length / TagUtil.PAGE_SIZE][];
        for (int i = 0, j = 0; i < data.length; i += TagUtil.PAGE_SIZE, j++) {
            pages[j] = Arrays.copyOfRange(data, i, i + TagUtil.PAGE_SIZE);
        }
        return pages;
    }

    public static void validateTag(byte[] data) throws Exception {
        byte[][] pages = TagUtil.splitPages(data);

        if (pages[0][0] != (byte) 0x04)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_prefix));

        if (pages[2][2] != (byte) 0x0F || pages[2][3] != (byte) 0xE0)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_lock));

        if (pages[3][0] != (byte) 0xF1 || pages[3][1] != (byte) 0x10 || pages[3][2] != (byte) 0xFF || pages[3][3] != (byte) 0xEE)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_cc));

        if (pages[0x82][0] != (byte) 0x01 || pages[0x82][1] != (byte) 0x0 || pages[0x82][2] != (byte) 0x0F)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_dynamic));

        if (pages[0x83][0] != (byte) 0x0 || pages[0x83][1] != (byte) 0x0 || pages[0x83][2] != (byte) 0x0 || pages[0x83][3] != (byte) 0x04)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_cfg_zero));

        if (pages[0x84][0] != (byte) 0x5F || pages[0x84][1] != (byte) 0x0 || pages[0x84][2] != (byte) 0x0 || pages[0x84][3] != (byte) 0x00)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_cfg_one));
    }

    public static byte[] decrypt(KeyManager keyManager, byte[] tagData) throws Exception {
        if (!keyManager.hasFixedKey() || !keyManager.hasUnFixedKey())
            throw new Exception(TagMo.getStringRes(R.string.key_not_present));

        AmiiTool tool = new AmiiTool();
        if (tool.setKeysFixed(keyManager.fixedKey, keyManager.fixedKey.length) == 0)
            throw new Exception(TagMo.getStringRes(R.string.amiitool_init_error));
        if (tool.setKeysUnfixed(keyManager.unfixedKey, keyManager.unfixedKey.length) == 0)
            throw new Exception(TagMo.getStringRes(R.string.amiitool_init_error));
        byte[] decrypted = new byte[TagUtil.TAG_FILE_SIZE];
        if (tool.unpack(tagData, tagData.length, decrypted, decrypted.length) == 0)
            throw new Exception(TagMo.getStringRes(R.string.failed_decrypt));

        return decrypted;
    }

    public static byte[] encrypt(KeyManager keyManager, byte[] tagData) throws Exception {
        if (!keyManager.hasFixedKey() || !keyManager.hasUnFixedKey())
            throw new Exception(TagMo.getStringRes(R.string.key_not_present));

        AmiiTool tool = new AmiiTool();
        if (tool.setKeysFixed(keyManager.fixedKey, keyManager.fixedKey.length) == 0)
            throw new Exception(TagMo.getStringRes(R.string.amiitool_init_error));
        if (tool.setKeysUnfixed(keyManager.unfixedKey, keyManager.unfixedKey.length) == 0)
            throw new Exception(TagMo.getStringRes(R.string.amiitool_init_error));
        byte[] encrypted = new byte[TagUtil.TAG_FILE_SIZE];
        if (tool.pack(tagData, tagData.length, encrypted, encrypted.length) == 0)
            throw new Exception(TagMo.getStringRes(R.string.failed_encrypt));

        return encrypted;
    }


    public static byte[] patchUid(byte[] uid, byte[] tagData) throws Exception {
        if (uid.length < 9) throw new Exception(TagMo.getStringRes(R.string.invalid_uid_length));

        byte[] patched = Arrays.copyOf(tagData, tagData.length);

        System.arraycopy(uid, 0, patched, 0x1d4, 8);
        patched[0] = uid[8];

        return patched;
    }

    public static boolean isEncrypted(byte[] tagData) {
        return tagData[10] == 0x0F && tagData[11] == 0xE0;
    }

    public static byte[] readTag(InputStream inputStream) throws Exception {
        byte[] data = new byte[TAG_FILE_SIZE];
        try {
            int len = inputStream.read(data);
            if (len != TAG_FILE_SIZE)
                throw new Exception(TagMo.getStringRes(R.string.invalid_file_size) + TAG_FILE_SIZE);

            return data;
        } finally {
            inputStream.close();
        }
    }

    static byte[] toAmiiboDate(Date date) {
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);

        int year = calendar.get(Calendar.YEAR) - 2000;
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.putShort((short) ((year << 9) | (month << 5) | day));

        return bb.array();
    }

    static Date fromAmiiboDate(byte[] bytes) throws IllegalArgumentException {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        int date = bb.getShort();

        int year = ((date & 0xFE00) >> 9) + 2000;
        int month = ((date & 0x01E0) >> 5) - 1;
        int day = date & 0x1F;

        Calendar calendar = Calendar.getInstance();
        calendar.setLenient(false);
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        return calendar.getTime();
    }

    public static byte[] generateRandomUID() {
        byte[] uid = new byte[9];
        Random Random = new Random();
        Random.nextBytes(uid);

        uid[3] = (byte) (0x88 ^ uid[0] ^ uid[1] ^ uid[2]);
        uid[8] = (byte) (uid[3] ^ uid[4] ^ uid[5] ^ uid[6]);

        return uid;
    }

    public static byte[] getBytes(ByteBuffer bb, int offset, int length) {
        byte[] bytes = new byte[length];
        bb.position(offset);
        bb.get(bytes);
        return bytes;
    }

    public static void putBytes(ByteBuffer bb, int offset, byte[] bytes) {
        bb.position(offset);
        bb.put(bytes);
    }

    public static ByteBuffer getByteBuffer(ByteBuffer bb, int offset, int length) {
        return ByteBuffer.wrap(getBytes(bb, offset, length));
    }

    public static Date getDate(ByteBuffer bb, int offset) {
        return TagUtil.fromAmiiboDate(getBytes(bb, offset, 0x2));
    }

    public static void putDate(ByteBuffer bb, int offset, Date date) {
        putBytes(bb, offset, TagUtil.toAmiiboDate(date));
    }

    public static String getString(ByteBuffer bb, int offset, int length, Charset charset) throws UnsupportedEncodingException {
        return charset.decode(getByteBuffer(bb, offset, length)).toString();
    }

    public static void putString(ByteBuffer bb, int offset, int length, Charset charset, String text) {
        byte[] bytes = new byte[length];
        byte[] bytes2 = charset.encode(text).array();
        System.arraycopy(bytes2, 0, bytes, 0, bytes2.length);

        putBytes(bb, offset, bytes);
    }

    public static BitSet getBitSet(ByteBuffer bb, int offset, int length) {
        BitSet bitSet = new BitSet(length * 8);
        byte[] bytes = getBytes(bb, offset, length);
        for (int i = 0; i < bytes.length; i++) {
            for (int j = 0; j < 8; j++) {
                bitSet.set((i * 8) + j, ((bytes[i] >> j) & 1) == 1);
            }
        }
        return bitSet;
    }

    public static void putBitSet(ByteBuffer bb, int offset, int length, BitSet bitSet) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < bytes.length; i++) {
            for (int j = 0; j < 8; j++) {
                boolean set = bitSet.get((i * 8) + j);
                bytes[i] = (byte) (set ? bytes[i] | (1 << j) : bytes[i] & ~(1 << j));
            }
        }
        putBytes(bb, offset, bytes);
    }
}
