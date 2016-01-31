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

    Context context;

    public KeyManager(Context context) {
        this.context = context;
    }

    public boolean hasFixedKey() {
        String[] files = context.fileList();
        for(int i=0; i<files.length; i++)
        {
            if (files[i].equals(FIXED_KEY_FILE))
                return true;
        }
        return false;
    }

    public boolean hasUnFixedKey() {
        String[] files = context.fileList();
        for(int i=0; i<files.length; i++)
        {
            if (files[i].equals(UNFIXED_KEY_FILE))
                return true;
        }
        return false;
    }

    private boolean loadKey(Uri source, String targetfile, String md5) {
        try {
            InputStream strm = context.getContentResolver().openInputStream(source);
            try {
                byte[] data = new byte[KEY_FILE_SIZE];
                if (strm.read(data, 0, data.length) != KEY_FILE_SIZE)
                    return false;

                if (!md5.equals(Util.md5(data)))
                    return false;

                FileOutputStream fos = context.openFileOutput(targetfile, context.MODE_PRIVATE);
                try {
                    fos.write(data);
                } finally {
                    fos.close();
                }

                return true;

            } finally {
                strm.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "Loading key failed", e);
            context.deleteFile(targetfile);
        }
        return false;
    }

    public boolean loadFixedKey(Uri fileuri) {
        return loadKey(fileuri, FIXED_KEY_FILE, FIXED_KEY_MD5);
    }

    public boolean loadUnfixedKey(Uri fileuri) {
        return loadKey(fileuri, UNFIXED_KEY_FILE, UNFIXED_KEY_MD5);
    }

}
