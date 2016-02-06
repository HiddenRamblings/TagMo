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

    byte[] tagData = null;
    byte[] decryptedData = null;

    Context context;
    public TagFile(Context context) {
        this.context = context;
    }

    public boolean isValid() {
        return tagData != null;
    }

    public synchronized void loadFile(Uri uri) throws Exception {
        InputStream strm = context.getContentResolver().openInputStream(uri);
        byte[] data = new byte[TagUtil.TAG_FILE_SIZE];
        try {
            int len = strm.read(data);
            if (len != TagUtil.TAG_FILE_SIZE)
                throw new Exception("Invalid file size. was expecting " + TagUtil.TAG_FILE_SIZE);
        } finally {
            strm.close();
        }

        validateTag(data);

        this.tagData = data;
    }

    public static void validateTag(byte[] data) throws Exception {
        byte[][] pages = TagUtil.splitPages(data);

        if (pages[0][0] != (byte) 0x04)
            throw new Exception("Invalid tag file. Tag must start with a 0x04.");

        if (pages[2][2] != (byte) 0x0F || pages[2][3] != (byte) 0xE0)
            throw new Exception("Invalid tag file. lock signature mismatch.");

        Log.d(TAG, Util.bytesToHex(pages[3]));
        if (pages[3][0] != (byte) 0xF1 || pages[3][1] != (byte) 0x10 || pages[3][2] != (byte) 0xFF || pages[3][3] != (byte) 0xEE)
            throw new Exception("Invalid tag file. CC signature mismatch.");

        if (pages[0x82][0] != (byte) 0x01 || pages[0x82][1] != (byte) 0x0 || pages[0x82][2] != (byte) 0x0F)
            throw new Exception("Invalid tag file. dynamic lock signature mismatch.");

        if (pages[0x83][0] != (byte) 0x0 || pages[0x83][1] != (byte) 0x0 || pages[0x83][2] != (byte) 0x0 || pages[0x83][3] != (byte) 0x04)
            throw new Exception("Invalid tag file. CFG0 signature mismatch.");

        if (pages[0x84][0] != (byte) 0x5F || pages[0x84][1] != (byte) 0x0 || pages[0x84][2] != (byte) 0x0 || pages[0x84][3] != (byte) 0x00)
            throw new Exception("Invalid tag file. CFG1 signature mismatch.");
    }

    public void decrypt(KeyManager keyManager) throws Exception {
        if (!keyManager.hasFixedKey() || !keyManager.hasUnFixedKey())
            throw new Exception("Key files not loaded!");

        Log.d(TAG, "decrypting tag");

        AmiiTool tool = new AmiiTool();
        if (tool.setKeysFixed(keyManager.fixedKey, keyManager.fixedKey.length) == 0)
            throw new Exception("Failed to initialise amiitool");
        if (tool.setKeysUnfixed(keyManager.unfixedKey, keyManager.unfixedKey.length)== 0)
            throw new Exception("Failed to initialise amiitool");
        byte[] decrypted = new byte[TagUtil.TAG_FILE_SIZE];
        if (tool.unpack(this.tagData, this.tagData.length, decrypted, decrypted.length) == 0)
            throw new Exception("Failed to decrypt tag");

        this.decryptedData = decrypted;
    }

    public byte[] encryptToUid(byte[] uid, KeyManager keyManager) throws Exception {
        if (this.decryptedData == null)
            this.decrypt(keyManager);

        if (uid.length < 9) throw new Exception("Invalid uid length");

        byte[] patched = Arrays.copyOf(this.decryptedData, this.decryptedData.length);

        System.arraycopy(uid, 0, patched, 0x1d4, 8);
        patched[0] = uid[8];

        AmiiTool tool = new AmiiTool();
        byte[] encrypted = new byte[TagUtil.TAG_FILE_SIZE];
        if (tool.pack(patched, patched.length, encrypted, encrypted.length) == 0)
            throw new Exception("Failed to encrypt tag");

        return encrypted;
    }

    /*
    Does nto work
    public void buildTag(byte[] parameters, KeyManager keyManager) throws Exception {
        if (parameters == null || parameters.length!=8)
            throw new Exception("Invalid parameter size");
        byte[] internal = new byte[TagUtil.TAG_FILE_SIZE];
        System.arraycopy(parameters, 0, internal, 0x1DC, 8); //parameters
        System.arraycopy(Util.hexStringToByteArray("0512941E"), 0, internal, 0x1E4, 4); //page of unknown significance
        System.arraycopy(Util.getRandom(8 * 4), 0, internal, 0x1E8, 8 * 4); //keygen salt
        System.arraycopy(Util.hexStringToByteArray("F110FFEE"), 0, internal, 0x4, 4); //cc
        System.arraycopy(Util.hexStringToByteArray("0FE0"), 0, internal, 0x2, 2); //lock bytes
        System.arraycopy(Util.hexStringToByteArray("01000FBD000000045F000000"), 0, internal, 0x208, 12); //config and dynamic locks
        internal[0x1D4] = (byte)0x0f; //UID 1st byte

        try {
            this.decryptedData = internal;

            this.tagData = this.encryptToUid(Util.hexStringToByteArray("040000000000000000"), keyManager);
            validateTag(this.tagData);
            Log.d(TAG, Util.bytesToHex(tagData));
        } catch (Exception e) {
            this.tagData = null;
            this.decryptedData = null;
            throw e;
        }
    }
    */

}
