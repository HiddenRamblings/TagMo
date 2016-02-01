package com.hiddenramblings.tagmo;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;

/**
 * Created by MAS on 31/01/2016.
 */
public class KeyManager {
    private static final String TAG = "KeyManager";

    private static final String FIXED_KEY_FILE = "fixed_key.bin";
    private static final String FIXED_KEY_MD5 = "0AD86557C7BA9E75C79A7B43BB466333";

    private static final String UNFIXED_KEY_FILE = "unfixed_key.bin";
    private static final String UNFIXED_KEY_MD5 = "2551AFC7C8813008819836E9B619F7ED";

    private static final int KEY_FILE_SIZE = 80;

    byte[] fixedKey = null;
    byte[] unfixedKey = null;

    Context context;

    public KeyManager(Context context) {
        this.context = context;

        try {
            if (hasLocalFile(FIXED_KEY_FILE))
                fixedKey = loadKeyFromStorage(FIXED_KEY_FILE);

            if (hasLocalFile(UNFIXED_KEY_FILE))
                unfixedKey = loadKeyFromStorage(UNFIXED_KEY_FILE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load keys: ", e);
        }
    }

    boolean hasLocalFile(String file) {
        String[] files = context.fileList();
        for(int i=0; i<files.length; i++)
        {
            if (files[i].equals(file))
                return true;
        }
        return false;
    }

    public boolean hasFixedKey() {
        return fixedKey != null;
    }

    public boolean hasUnFixedKey() {
        return unfixedKey != null;
    }

    private void loadKey(Uri source, String targetfile, String md5) throws Exception {
        try {
            InputStream strm = context.getContentResolver().openInputStream(source);
            try {
                byte[] data = new byte[KEY_FILE_SIZE];
                if (strm.read(data, 0, data.length) != KEY_FILE_SIZE)
                    throw new Exception("Key file size does not match.");

                if (!md5.equals(Util.md5(data)))
                    throw new Exception("Key file signature does not match.");

                FileOutputStream fos = context.openFileOutput(targetfile, context.MODE_PRIVATE);
                try {
                    fos.write(data);
                } finally {
                    fos.close();
                }
            } finally {
                strm.close();
            }

        } catch (Exception e) {
            context.deleteFile(targetfile);
            throw e;
        }
    }

    private byte[] loadKeyFromStorage(String file) throws Exception {
        try {
            FileInputStream fs = context.openFileInput(file);
            try {
                byte[] key = new byte[KEY_FILE_SIZE];
                if (fs.read(key) != KEY_FILE_SIZE) throw new Exception("Invalid file size");
                return key;
            } finally {
                fs.close();
            }
        } catch(Exception e) {
            Log.e(TAG, "Error reading key from local storage", e);
        }
        return null;
    }

    public void loadFixedKey(Uri fileuri) throws Exception {
        loadKey(fileuri, FIXED_KEY_FILE, FIXED_KEY_MD5);
        this.fixedKey = loadKeyFromStorage(FIXED_KEY_FILE);
    }

    public void loadUnfixedKey(Uri fileuri) throws Exception {
        loadKey(fileuri, UNFIXED_KEY_FILE, UNFIXED_KEY_MD5);
        this.unfixedKey = loadKeyFromStorage(UNFIXED_KEY_FILE);
    }

}
