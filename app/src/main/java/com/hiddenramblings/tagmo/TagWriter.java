package com.hiddenramblings.tagmo;

import android.util.Log;

import com.hiddenramblings.tagmo.ptag.PTagKeyManager;

import java.io.IOException;

/**
 * Created by MAS on 01/02/2016.
 */
public class TagWriter {
    private static final String TAG = "TagWriter";

    private static final byte[] POWERTAG_SIGNATURE = Util.hexStringToByteArray("213C65444901602985E9F6B50CACB9C8CA3C4BCD13142711FF571CF01E66BD6F");
    private static final byte[] POWERTAG_IDPAGES = Util.hexStringToByteArray("04070883091012131800000000000000");
    private static final String POWERTAG_KEY = "FFFFFFFFFFFFFFFF0000000000000000";
    private static final byte[] COMP_WRITE_CMD = Util.hexStringToByteArray("a000");
    private static final byte[] SIG_CMD = Util.hexStringToByteArray("3c00");

    public static void writeToTagRaw(NTAG215 mifare, byte[] tagData, boolean validateNtag) throws Exception {
        validate(mifare, tagData, validateNtag);

        validateBlankTag(mifare);

        try {
            byte[][] pages = TagUtil.splitPages(tagData);
            writePages(mifare, 3, 129, pages);
            Log.d(TAG, "Wrote main data");
        } catch (Exception e) {
            throw new Exception("Error while writing main data (stage 1)", e);
        }
        try {
            writePassword(mifare);
            Log.d(TAG, "Wrote password");
        } catch (Exception e) {
            throw new Exception("Error while setting password (stage 2)", e);
        }
        try {
            writeLockInfo(mifare);
            Log.d(TAG, "Wrote lock info");
        } catch (Exception e) {
            throw new Exception("Error while setting lock info (stage 3)", e);
        }
    }

    private static void validateBlankTag(NTAG215 mifare) throws Exception {
        byte[] lockPage = mifare.readPages(0x02);
        Log.d(TAG, Util.bytesToHex(lockPage));
        if (lockPage[2] == (byte) 0x0F && lockPage[3] == (byte) 0xE0) {
            Log.d(TAG, "locked");
            throw new Exception("Tag already an amiibo. Use 'Restore Tag' to write data");
        }
        Log.d(TAG, "not locked");
    }

    public static void writeToTagAuto(NTAG215 mifare, byte[] tagData, KeyManager keyManager, boolean validateNtag, boolean supportPowerTag) throws Exception {
        byte[] idPages = mifare.readPages(0);
        if (idPages == null || idPages.length != TagUtil.PAGE_SIZE * 4)
            throw new Exception("Read failed! Unexpected read size.");

        boolean isPowerTag = false;
        if (supportPowerTag) {
            byte[] sig = mifare.transceive(SIG_CMD);
            isPowerTag = compareRange(sig, POWERTAG_SIGNATURE, 0, POWERTAG_SIGNATURE.length);
        }

        Log.d(TAG, "is power tag : " + isPowerTag);

        if (isPowerTag) {
            //use a pre-determined static id for powertag
            tagData = TagUtil.patchUid(POWERTAG_IDPAGES, tagData, keyManager, true);
        } else {
            tagData = TagUtil.patchUid(idPages, tagData, keyManager, true);
        }

        Log.d(TAG, Util.bytesToHex(tagData));

        if (!isPowerTag) {
            validate(mifare, tagData, validateNtag);
            validateBlankTag(mifare);
        }

        if (isPowerTag) {
            byte[] oldid = mifare.getTag().getId();
            if (oldid == null || oldid.length != 7)
                throw new Exception("Could not read old UID");

            Log.d(TAG, "Old UID " + Util.bytesToHex(oldid));

            byte[] page10 = mifare.readPages(0x10);
            Log.d(TAG, "page 10 " + Util.bytesToHex(page10));

            String page10bytes = Util.bytesToHex(new byte[]{page10[0], page10[3]});

            byte[] ptagKeySuffix = PTagKeyManager.getKey(oldid, page10bytes);
            byte[] ptagKey = Util.hexStringToByteArray(POWERTAG_KEY);
            System.arraycopy(ptagKeySuffix, 0, ptagKey, 8, 8);

            Log.d(TAG, "ptag key " + Util.bytesToHex(ptagKey));

            mifare.transceive(COMP_WRITE_CMD);
            mifare.transceive(ptagKey);

            if (!(idPages[0] == (byte) 0xFF && idPages[1] == (byte) 0xFF))
                doAuth(mifare);
        }

        byte[][] pages = TagUtil.splitPages(tagData);
        if (isPowerTag) {
            byte[] zeropage = Util.hexStringToByteArray("00000000");
            mifare.writePage(0x86, zeropage); //PACK
            writePages(mifare, 0x01, 0x84, pages);
            mifare.writePage(0x85, zeropage); //PWD
            mifare.writePage(0x00, pages[0]); //UID
            mifare.writePage(0x00, pages[0]); //UID
        } else {
            try {
                writePages(mifare, 3, 129, pages);
                Log.d(TAG, "Wrote main data");
            } catch (Exception e) {
                throw new Exception("Error while writing main data (stage 1)", e);
            }
            try {
                writePassword(mifare);
                Log.d(TAG, "Wrote password");
            } catch (Exception e) {
                throw new Exception("Error while setting password (stage 2)", e);
            }
            try {
                writeLockInfo(mifare);
                Log.d(TAG, "Wrote lock info");
            } catch (Exception e) {
                throw new Exception("Error while setting lock info (stage 3)", e);
            }
        }
    }

    public static void restoreTag(NTAG215 mifare, byte[] tagData, boolean ignoreUid, KeyManager keyManager, boolean validateNtag) throws Exception {
        if (!ignoreUid)
            validate(mifare, tagData, validateNtag);
        else {
            byte[] liveData = readFromTag(mifare);
            if (!compareRange(liveData, tagData, 0, 9)) {
                //restoring to different tag: transplant mii and appdata to livedata and re-encrypt

                liveData = TagUtil.decrypt(keyManager, liveData);
                tagData = TagUtil.decrypt(keyManager, tagData);

                System.arraycopy(tagData, 0x08, liveData, 0x08, 0x1B4 - 0x08);

                tagData = TagUtil.encrypt(keyManager, liveData);
            }
        }

        doAuth(mifare);
        byte[][] pages = TagUtil.splitPages(tagData);
        writePages(mifare, 4, 12, pages);
        writePages(mifare, 32, 129, pages);
    }

    static void validate(NTAG215 mifare, byte[] tagData, boolean validateNtag) throws Exception {
        if (tagData == null)
            throw new Exception("Cannot validate: no source data loaded to compare.");

        if (validateNtag) {
            try {
                byte[] versionInfo = mifare.transceive(new byte[]{(byte) 0x60});
                if (versionInfo == null || versionInfo.length != 8)
                    throw new Exception("Tag version error");
                if (versionInfo[0x02] != (byte) 0x04 || versionInfo[0x06] != (byte) 0x11)
                    throw new Exception("Tag is not an NTAG215");
            } catch (Exception e) {
                Log.e(TAG, "version information error", e);
                throw e;
            }
        }

        byte[] pages = mifare.readPages(0);
        if (pages == null || pages.length != TagUtil.PAGE_SIZE * 4)
            throw new Exception("Read failed! Unexpected read size.");

        if (!compareRange(pages, tagData, 0, 9))
            throw new Exception("Source UID does not match the target!");

        Log.e(TAG, "Validate success");
    }

    static boolean compareRange(byte[] data, byte[] data2, int data2offset, int len) {
        for (int i = data2offset, j = 0; j < len; i++, j++) {
            if (data[j] != data2[i])
                return false;
        }
        return true;
    }

    public static final int BULK_READ_PAGE_COUNT = 4;

    public static byte[] readFromTag(NTAG215 tag) throws Exception {
        byte[] tagData = new byte[TagUtil.TAG_FILE_SIZE];
        int pageCount = TagUtil.TAG_FILE_SIZE / TagUtil.PAGE_SIZE;

        for (int i = 0; i < pageCount; i += BULK_READ_PAGE_COUNT) {
            byte[] pages = tag.readPages(i);
            if (pages == null || pages.length != TagUtil.PAGE_SIZE * BULK_READ_PAGE_COUNT)
                throw new Exception("Invalid read result size");

            int dstIndex = i * TagUtil.PAGE_SIZE;
            int dstCount = Math.min(BULK_READ_PAGE_COUNT * TagUtil.PAGE_SIZE, tagData.length - dstIndex);

            System.arraycopy(pages, 0, tagData, dstIndex, dstCount);
        }

        Log.d(TAG, Util.bytesToHex(tagData));
        return tagData;
    }

    static void writePages(NTAG215 tag, int pagestart, int pageend, byte[][] data) throws IOException {
        for (int i = pagestart; i <= pageend; i++) {
            tag.writePage(i, data[i]);
            Log.d(TAG, "Wrote to page " + i);
        }
    }

    static void writePassword(NTAG215 tag) throws IOException {
        byte[] pages0_1 = tag.readPages(0);

        if (pages0_1 == null || pages0_1.length != TagUtil.PAGE_SIZE * 4)
            throw new IOException("Read failed");

        byte[] uid = TagUtil.uidFromPages(pages0_1);
        byte[] password = TagUtil.keygen(uid);

        Log.d(TAG, "Password: " + Util.bytesToHex(password));

        Log.d(TAG, "Writing PACK");
        tag.writePage(0x86, new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0, (byte) 0});

        Log.d(TAG, "Writing PWD");
        tag.writePage(0x85, password);
        Log.d(TAG, "pwd done");
    }

    static void writeLockInfo(NTAG215 tag) throws IOException {
        byte[] pages = tag.readPages(0);

        if (pages == null || pages.length != TagUtil.PAGE_SIZE * 4)
            throw new IOException("Read failed");

        tag.writePage(2, new byte[]{pages[2 * TagUtil.PAGE_SIZE], pages[(2 * TagUtil.PAGE_SIZE) + 1], (byte) 0x0F, (byte) 0xE0}); //lock bits
        tag.writePage(130, new byte[]{(byte) 0x01, (byte) 0x00, (byte) 0x0F, (byte) 0x00}); //dynamic lock bits. should the last bit be 0xBD accoridng to the nfc docs though: //Remark: Set all bits marked with RFUI to 0, when writing to the dynamic lock bytes.
        tag.writePage(131, new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x04}); //config
        tag.writePage(132, new byte[]{(byte) 0x5F, (byte) 0x00, (byte) 0x00, (byte) 0x00}); //config
    }

    static void doAuth(NTAG215 tag) throws Exception {
        byte[] pages0_1 = tag.readPages(0);

        if (pages0_1 == null || pages0_1.length != TagUtil.PAGE_SIZE * 4)
            throw new Exception("Read failed");

        byte[] uid = TagUtil.uidFromPages(pages0_1);
        byte[] password = TagUtil.keygen(uid);

        Log.d(TAG, "Password: " + Util.bytesToHex(password));

        byte[] auth = new byte[]{
                (byte) 0x1B,
                password[0],
                password[1],
                password[2],
                password[3]
        };
        byte[] response = tag.transceive(auth);
        if (response == null)
            throw new Exception("Auth result was null");
        String respStr = Util.bytesToHex(response);
        Log.e(TAG, "Auth response " + respStr);
        if (!"8080".equals(respStr)) {
            throw new Exception("Authenticaiton failed");
        }
    }

}
