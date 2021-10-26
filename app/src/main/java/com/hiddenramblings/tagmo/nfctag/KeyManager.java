package com.hiddenramblings.tagmo.nfctag;

import android.content.Context;
import android.net.Uri;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class KeyManager {

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
            TagMo.Error(getClass(), R.string.key_load_error, e);
        }
    }

    boolean hasLocalFile(String file) {
        String[] files = context.fileList();
        for (String file1 : files) {
            if (file1.equals(file))
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

    public boolean isKeyMissing() {
        return fixedKey == null || unfixedKey == null;
    }

    private byte[] loadKeyFromStorage(String file) throws Exception {
        try {
            try (FileInputStream fs = context.openFileInput(file)) {
                byte[] key = new byte[KEY_FILE_SIZE];
                if (fs.read(key) != KEY_FILE_SIZE) throw new Exception(TagMo.getStringRes(R.string.key_size_invalid));
                return key;
            }
        } catch (Exception e) {
            TagMo.Error(getClass(), R.string.key_read_error, e);
        }
        return null;
    }

    void saveKeyFile(String file, byte[] key) throws IOException {
        try (FileOutputStream fos = context.openFileOutput(file, Context.MODE_PRIVATE)) {
            fos.write(key);
        }
    }

    boolean readKey(InputStream strm) throws Exception {
        byte[] data = new byte[KEY_FILE_SIZE];
        int rlen = strm.read(data, 0, data.length);
        if (rlen <= 0)
            return false;

        if (rlen < KEY_FILE_SIZE)
            throw new Exception(TagMo.getStringRes(R.string.key_size_error));

        String md5 = TagUtils.md5(data);
        if (FIXED_KEY_MD5.equals(md5)) {
            saveKeyFile(FIXED_KEY_FILE, data);
            this.fixedKey = loadKeyFromStorage(FIXED_KEY_FILE);
        } else if (UNFIXED_KEY_MD5.equals(md5)) {
            saveKeyFile(UNFIXED_KEY_FILE, data);
            this.unfixedKey = loadKeyFromStorage(UNFIXED_KEY_FILE);
        } else
            throw new Exception(TagMo.getStringRes(R.string.key_signature_error));
        return true;
    }

    public void loadKey(Uri file) throws Exception {
        try (InputStream strm = context.getContentResolver().openInputStream(file)) {
            if (!readKey(strm))
                throw new Exception(TagMo.getStringRes(R.string.invalid_key_error)); //if we can't even read one key then it's completely wrong
            readKey(strm);
        }
    }
}
