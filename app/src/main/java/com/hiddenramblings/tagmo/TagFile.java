package com.hiddenramblings.tagmo;

import android.content.Context;
import android.net.Uri;
import android.nfc.tech.MifareUltralight;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Created by MAS on 31/01/2016.
 */
public class TagFile {
    private static final String TAG = "TagFile";
    private static final int TAG_FILE_SIZE = 532;
    public static final int PAGE_SIZE = 4;

    byte[][] tagData = null;

    Context context;
    public TagFile(Context context) {
        this.context = context;
    }

    public boolean isValid() {
        return tagData != null;
    }

    public synchronized void loadFile(Uri uri) throws Exception {
        InputStream strm = context.getContentResolver().openInputStream(uri);
        byte[] data = new byte[TAG_FILE_SIZE];
        try {
            int len = strm.read(data);
            if (len != TAG_FILE_SIZE)
                throw new Exception("Invalid file size. was expecting " + TAG_FILE_SIZE);
        } finally {
            strm.close();
        }

        byte[][] pages = new byte[data.length / PAGE_SIZE][];
        for (int i = 0, j = 0; i < data.length; i += PAGE_SIZE, j++) {
            pages[j] = Arrays.copyOfRange(data, i, i + PAGE_SIZE);
        }

        if (pages[0][0] != (byte)0x04)
            throw new Exception("Invalid tag file. Tag must start with a 0x04.");

        if (pages[2][2] != (byte)0x0F || pages[2][3] != (byte)0xE0)
            throw new Exception("Invalid tag file. lock signature mismatch.");

        Log.d(TAG, Util.bytesToHex(pages[3]));
        if (pages[3][0] != (byte)0xF1 || pages[3][1] != (byte)0x10 || pages[3][2] != (byte)0xFF || pages[3][3] != (byte)0xEE)
            throw new Exception("Invalid tag file. CC signature mismatch.");

        if (pages[0x82][0] != (byte)0x01 || pages[0x82][1] != (byte)0x0 || pages[0x82][2] != (byte)0x0F)
            throw new Exception("Invalid tag file. dynamic lock signature mismatch.");

        if (pages[0x83][0] != (byte)0x0 || pages[0x83][1] != (byte)0x0 || pages[0x83][2] != (byte)0x0 || pages[0x83][3] != (byte)0x04)
            throw new Exception("Invalid tag file. CFG0 signature mismatch.");

        if (pages[0x84][0] != (byte)0x5F || pages[0x84][1] != (byte)0x0 || pages[0x84][2] != (byte)0x0 || pages[0x84][3] != (byte)0x00)
            throw new Exception("Invalid tag file. CFG1 signature mismatch.");

        this.tagData = pages;
    }

    public void writeToTag(MifareUltralight mifare) throws Exception
    {
        this.validate(mifare);
        try {
            this.writePages(mifare, 3, 129, this.tagData);
            Log.d(TAG, "Wrote main data");
        } catch (Exception e) {
            throw new Exception("Error while writing main data (stage 1)", e);
        }
        try {
            this.writePassword(mifare);
            Log.d(TAG, "Wrote password");
        } catch (Exception e) {
            throw new Exception("Error while setting password (stage 2)", e);
        }
        try {
            this.writeLockInfo(mifare);
            Log.d(TAG, "Wrote lock info");
        } catch (Exception e) {
            throw new Exception("Error while setting lock info (stage 3)", e);
        }
    }

    public void validate(MifareUltralight mifare) throws Exception
    {
        if (this.tagData == null)
            throw new Exception("Cannot validate: no source data loaded to compare.");

        byte[] pages = mifare.readPages(0);
        if (pages.length != TagFile.PAGE_SIZE * 4)
            throw new Exception("Read failed! Unexpected read size.");

        if (!comparePage(pages, 0, 0) || !comparePage(pages, 0, 0))
            throw new Exception("Source UID does not match the target!");

        Log.e(TAG, "Validate success");
    }

    boolean comparePage(byte[] data, int offset, int page) {
        for(int i=offset, j=0; i<offset+PAGE_SIZE; i++, j++) {
            if (data[i] != this.tagData[page][j])
                return false;
        }
        return true;
    }

    public void readFromTag(MifareUltralight mifare) throws Exception
    {

    }

    void writePages(MifareUltralight tag, int pagestart, int pageend, byte[][] data) throws IOException {
        int i = 0;
        for(i=pagestart; i<=pageend; i++) {
            tag.writePage(i, data[i]);
        }
        Log.d(TAG, "Wrote to page " + i);
    }

    void writePassword(MifareUltralight tag) throws IOException {
        byte[] pages0_1 = tag.readPages(0);

        if (pages0_1 == null)
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

    public void writeLockInfo(MifareUltralight tag) throws IOException {
        byte[] pages = tag.readPages(0);

        if (pages == null)
            throw new IOException("Read failed");

        tag.writePage(2, new byte[]{pages[2 * PAGE_SIZE], pages[(2 * PAGE_SIZE) + 1], (byte) 0x0F, (byte) 0xE0}); //lock bits
        tag.writePage(130, new byte[]{(byte)0x01, (byte)0x00, (byte)0x0F, (byte)0x00}); //dynamic lock bits. should the last bit be 0xBD accoridng to the nfc docs though: //Remark: Set all bits marked with RFUI to 0, when writing to the dynamic lock bytes.
        tag.writePage(131, new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x04}); //config
        tag.writePage(132, new byte[]{(byte)0x5F, (byte)0x00, (byte)0x00, (byte)0x00}); //config
    }

}
