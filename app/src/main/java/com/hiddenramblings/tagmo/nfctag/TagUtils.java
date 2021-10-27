package com.hiddenramblings.tagmo.nfctag;

import android.util.Log;

import com.hiddenramblings.tagmo.AmiiTool;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.nfctag.data.AmiiboData;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Random;

public class TagUtils {

    public static boolean isPowerTag(NTAG215 mifare) {
        if (TagMo.getPrefs().enablePowerTagSupport().get()) {
            try {
                if (TagUtils.compareRange(mifare.transceive(NfcByte.SIG_CMD), NfcByte.POWERTAG_SIGNATURE,
                        0, NfcByte.POWERTAG_SIGNATURE.length))
                    return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean isElite(NTAG215 mifare) {
        if (TagMo.getPrefs().enableEliteSupport().get()) {
            try {
                if (TagUtils.bytesToHex(mifare.readEliteSingature()).endsWith("FFFFFFFFFF"))
                    return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static int getPositionForValue(int value) {
        return value - 1;
    }

    public static int getValueForPosition(int value) {
        return value + 1;
    }

    static boolean compareRange(byte[] data, byte[] data2, int data2offset, int len) {
        for (int i = data2offset, j = 0; j < len; i++, j++) {
            if (data[j] != data2[i])
                return false;
        }
        return true;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static byte[] hexToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static long hexToLong(String s) {
        long result = 0;
        for (int i = 0; i < s.length(); i++) {
            result = (result << 4) + ((long) Character.digit(s.charAt(i), 16));
        }
        return result;
    }

    public static byte hexToByte(String hex) {
        byte ret = (byte) 0;
        byte hi = (byte) hex.charAt(0);
        byte lo = (byte) hex.charAt(1);
        if (hi >= NfcByte.CMD_READ && hi <= NfcByte.CMD_READ_CNT) {
            ret = (byte) (((hi - 48) << 4) | 0);
        } else if (hi >= (byte) 65 && hi <= NfcByte.N2_LOCK) {
            ret = (byte) ((((hi - 65) + 10) << 4) | 0);
        } else if (hi >= (byte) 97 && hi <= (byte) 102) {
            ret = (byte) ((((hi - 97) + 10) << 4) | 0);
        }
        if (lo >= NfcByte.CMD_READ && lo <= NfcByte.CMD_READ_CNT) {
            return (byte) ((lo - 48) | ret);
        }
        if (lo >= (byte) 65 && lo <= NfcByte.N2_LOCK) {
            return (byte) (((lo - 65) + 10) | ret);
        }
        if (lo < (byte) 97 || lo > (byte) 102) {
            return ret;
        }
        return (byte) (((lo - 97) + 10) | ret);
    }

    public static String md5(byte[] data) {
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            byte[] result = digest.digest(data);
            return bytesToHex(result);
        } catch (NoSuchAlgorithmException e) {
            TagMo.Error(TagUtils.class, e.getMessage());
        }
        return null;
    }

    public static byte[] keygen(byte[] uuid) {
        // from AmiiManage (GPL)
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

    public static long amiiboIdFromTag(byte[] data) throws Exception {
        return new AmiiboData(data).getAmiiboID();
    }

    public static String amiiboIdToHex(long amiiboId) {
        return String.format("%016X", amiiboId);
    }

    public static byte[][] splitPages(byte[] data) throws Exception {
        if (data.length < NfcByte.TAG_FILE_SIZE)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_data));

        byte[][] pages = new byte[data.length / NfcByte.PAGE_SIZE][];
        for (int i = 0, j = 0; i < data.length; i += NfcByte.PAGE_SIZE, j++) {
            pages[j] = Arrays.copyOfRange(data, i, i + NfcByte.PAGE_SIZE);
        }
        return pages;
    }

    public static byte[] decrypt(KeyManager keyManager, byte[] tagData) throws Exception {
        if (!keyManager.hasFixedKey() || !keyManager.hasUnFixedKey())
            throw new Exception(TagMo.getStringRes(R.string.key_not_present));

        AmiiTool tool = new AmiiTool();
        if (tool.setKeysFixed(keyManager.fixedKey, keyManager.fixedKey.length) == 0)
            throw new Exception(TagMo.getStringRes(R.string.amiitool_init_error));
        if (tool.setKeysUnfixed(keyManager.unfixedKey, keyManager.unfixedKey.length) == 0)
            throw new Exception(TagMo.getStringRes(R.string.amiitool_init_error));
        byte[] decrypted = new byte[NfcByte.TAG_FILE_SIZE];
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
        byte[] encrypted = new byte[NfcByte.TAG_FILE_SIZE];
        if (tool.pack(tagData, tagData.length, encrypted, encrypted.length) == 0)
            throw new Exception(TagMo.getStringRes(R.string.failed_encrypt));

        return encrypted;
    }

    public static boolean isEncrypted(byte[] tagData) {
        return tagData[10] == 0x0F && tagData[11] == 0xE0;
    }

    public static byte[] patchUid(byte[] uid, byte[] tagData) throws Exception {
        if (uid.length < 9) throw new Exception(TagMo.getStringRes(R.string.invalid_uid_length));

        Log.d("UID", bytesToHex(uid));

        byte[] patched = Arrays.copyOf(tagData, tagData.length);

        System.arraycopy(uid, 0, patched, 0x1d4, 8);
        patched[0] = uid[8];

        return patched;
    }

    public static byte[] generateRandomUID() {
        byte[] uid = new byte[9];
        Random Random = new Random();
        Random.nextBytes(uid);

        uid[3] = (byte) (0x88 ^ uid[0] ^ uid[1] ^ uid[2]);
        uid[8] = (byte) (uid[3] ^ uid[4] ^ uid[5] ^ uid[6]);

        return uid;
    }

    public static String randomizeSerial(String serial) {
        Random random = new Random();
        String week = new DecimalFormat("00").format(
                random.nextInt(52 - 1 + 1) + 1);
        String year = String.valueOf(random.nextInt(9 + 1));
        String identifier = serial.substring(3, 7);
        String facility = TagMo.getContext().getResources().getStringArray(
                R.array.production_factory)[random.nextInt(3 + 1)];

        return week + year + "000" + identifier + facility;
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
        return TagUtils.fromAmiiboDate(getBytes(bb, offset, 0x2));
    }

    public static void putDate(ByteBuffer bb, int offset, Date date) {
        putBytes(bb, offset, TagUtils.toAmiiboDate(date));
    }

    public static String getString(ByteBuffer bb, int offset, int length, Charset charset)
            throws UnsupportedEncodingException {
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
