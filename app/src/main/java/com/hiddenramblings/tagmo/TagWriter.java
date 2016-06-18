package com.hiddenramblings.tagmo;

import android.nfc.tech.MifareUltralight;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;

/**
 * Created by MAS on 01/02/2016.
 */
public class TagWriter {
    private static final String TAG = "TagWriter";

    public static void writeToTagRaw(MifareUltralight mifare, byte[] tagData) throws Exception
    {
        validate(mifare, tagData);
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

    public static void writeToTagAuto(MifareUltralight mifare, byte[] tagData, KeyManager keyManager) throws Exception
    {
        tagData = adjustTag(keyManager, tagData, mifare);

        Log.d(TAG, Util.bytesToHex(tagData));
        validate(mifare, tagData);
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

    public static void restoreTag(MifareUltralight mifare, byte[] tagData, boolean ignoreUid, KeyManager keyManager) throws Exception {
        if (ignoreUid)
            tagData = adjustTag(keyManager, tagData, mifare);
        validate(mifare, tagData);
        doAuth(mifare);
        byte[][] pages = TagUtil.splitPages(tagData);
        writePages(mifare, 4, 12, pages);
        writePages(mifare, 32, 129, pages);
    }

    static byte[] adjustTag(KeyManager keyManager, byte[] tagData, MifareUltralight mifare) throws Exception {
        byte[] pages = mifare.readPages(0);
        if (pages == null || pages.length != TagUtil.PAGE_SIZE * 4)
            throw new Exception("Read failed! Unexpected read size.");

        return TagUtil.patchUid(pages, tagData, keyManager, true);
    }

    static void validate(MifareUltralight mifare, byte[] tagData) throws Exception
    {
        if (tagData == null)
            throw new Exception("Cannot validate: no source data loaded to compare.");

        byte[] pages = mifare.readPages(0);
        if (pages == null || pages.length != TagUtil.PAGE_SIZE * 4)
            throw new Exception("Read failed! Unexpected read size.");

        if (!compareRange(pages, tagData, 0, 9))
            throw new Exception("Source UID does not match the target!");

        Log.e(TAG, "Validate success");
    }

    static boolean compareRange(byte[] data, byte[] data2, int data2offset, int len) {
        for(int i=data2offset, j=0; j<len; i++, j++) {
            if (data[j] != data2[i])
                return false;
        }
        return true;
    }

    public static final int BULK_READ_PAGE_COUNT = 4;
    public static byte[] readFromTag(MifareUltralight tag) throws Exception
    {
        byte[] tagdata = new byte[TagUtil.TAG_FILE_SIZE];

        int pagecount = TagUtil.TAG_FILE_SIZE / TagUtil.PAGE_SIZE;
        int readcount = (int)Math.ceil((double)TagUtil.TAG_FILE_SIZE / (pagecount * BULK_READ_PAGE_COUNT));

        for(int i=0; i<pagecount; i+=BULK_READ_PAGE_COUNT) {
            byte[] pages = tag.readPages(i);
            if (pages == null || pages.length != TagUtil.PAGE_SIZE * BULK_READ_PAGE_COUNT)
                throw new Exception("Invalid read result size");

            int dstIndex = i*TagUtil.PAGE_SIZE;
            int dstCount = Math.min(BULK_READ_PAGE_COUNT * TagUtil.PAGE_SIZE, tagdata.length - dstIndex);

            System.arraycopy(pages, 0, tagdata, dstIndex, dstCount);
        }

        Log.d(TAG, Util.bytesToHex(tagdata));
        return tagdata;
    }

    static void writePages(MifareUltralight tag, int pagestart, int pageend, byte[][] data) throws IOException {
        int i = 0;
        for(i=pagestart; i<=pageend; i++) {
            tag.writePage(i, data[i]);
        }
        Log.d(TAG, "Wrote to page " + i);
    }

    static void writePassword(MifareUltralight tag) throws IOException {
        byte[] pages0_1 = tag.readPages(0);

        if (pages0_1 == null || pages0_1.length != TagUtil.PAGE_SIZE * 4)
            throw new IOException("Read failed");

        byte[] uid = TagUtil.uidFromPages(pages0_1);
        byte[] password = TagUtil.keygen(uid);

        Log.d(TAG, "Password: " + Util.bytesToHex(password));

        Log.d(TAG, "Writing PACK");
        tag.writePage(0x86, new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0, (byte)0});

        Log.d(TAG, "Writing PWD");
        byte[] auth = new byte[] {
                password[0],
                password[1],
                password[2],
                password[3]
        };
        tag.writePage(0x85, password);
        Log.d(TAG, "pwd done");
    }

    static void writeLockInfo(MifareUltralight tag) throws IOException {
        byte[] pages = tag.readPages(0);

        if (pages == null || pages.length != TagUtil.PAGE_SIZE * 4)
            throw new IOException("Read failed");

        tag.writePage(2, new byte[]{pages[2 * TagUtil.PAGE_SIZE], pages[(2 * TagUtil.PAGE_SIZE) + 1], (byte) 0x0F, (byte) 0xE0}); //lock bits
        tag.writePage(130, new byte[]{(byte) 0x01, (byte) 0x00, (byte) 0x0F, (byte) 0x00}); //dynamic lock bits. should the last bit be 0xBD accoridng to the nfc docs though: //Remark: Set all bits marked with RFUI to 0, when writing to the dynamic lock bytes.
        tag.writePage(131, new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x04}); //config
        tag.writePage(132, new byte[]{(byte) 0x5F, (byte) 0x00, (byte) 0x00, (byte) 0x00}); //config
    }

    static void doAuth(MifareUltralight tag) throws Exception {
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
