package com.hiddenramblings.tagmo.amiibo;

import android.content.Context;
import android.net.Uri;

import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.AmiiTool;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.nfctech.NfcByte;
import com.hiddenramblings.tagmo.nfctech.TagUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class KeyManager {

    private static final String FIXED_KEY_MD5 = "0AD86557C7BA9E75C79A7B43BB466333";
    private static final String UNFIXED_KEY_MD5 = "2551AFC7C8813008819836E9B619F7ED";

    byte[] fixedKey = null;
    byte[] unfixedKey = null;

    Context context;

    public KeyManager(Context context) {
        this.context = context;
        isKeyMissing();
    }

    boolean hasLocalFile(String file) {
        String[] files = context.fileList();
        for (String file1 : files) {
            if (file1.equals(file))
                return true;
        }
        return false;
    }

    private byte[] loadKeyFromStorage(String file) {
        try (FileInputStream fs = context.openFileInput(file)) {
            byte[] key = new byte[NfcByte.KEY_FILE_SIZE];
            if (fs.read(key) != NfcByte.KEY_FILE_SIZE)
                throw new IOException(context.getString(R.string.key_size_invalid));
            return key;
        } catch (Exception e) {
            Debug.Log(R.string.key_read_error, e);
        }
        return null;
    }

    public boolean hasFixedKey() {
        if (hasLocalFile(FIXED_KEY_MD5))
            fixedKey = loadKeyFromStorage(FIXED_KEY_MD5);
        return null != fixedKey ;
    }

    public boolean hasUnFixedKey() {
        if (hasLocalFile(UNFIXED_KEY_MD5))
            unfixedKey = loadKeyFromStorage(UNFIXED_KEY_MD5);
        return null != unfixedKey ;
    }

    public boolean isKeyMissing() {
        return !hasFixedKey() || !hasUnFixedKey();
    }

    void saveKeyFile(String file, byte[] key) throws IOException {
        try (FileOutputStream fos = context.openFileOutput(file, Context.MODE_PRIVATE)) {
            fos.write(key);
        }
    }

    private void readKey(InputStream strm) throws IOException {
        byte[] data = new byte[NfcByte.KEY_FILE_SIZE];
        int rlen = strm.read(data, 0, data.length);
        if (rlen <= 0)
            throw new IOException(context.getString(R.string.invalid_key_error));

        if (rlen < NfcByte.KEY_FILE_SIZE)
            throw new IOException(context.getString(R.string.key_size_error));

        String md5 = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] result = digest.digest(data);
            md5 = TagUtils.bytesToHex(result);
        } catch (NoSuchAlgorithmException e) {
            Debug.Log(e);
        }
        if (FIXED_KEY_MD5.equals(md5)) {
            saveKeyFile(FIXED_KEY_MD5, data);
            this.fixedKey = loadKeyFromStorage(FIXED_KEY_MD5);
        } else if (UNFIXED_KEY_MD5.equals(md5)) {
            saveKeyFile(UNFIXED_KEY_MD5, data);
            this.unfixedKey = loadKeyFromStorage(UNFIXED_KEY_MD5);
            if (!hasFixedKey()) readKey(strm);
        } else {
            throw new IOException(context.getString(R.string.key_signature_error));
        }
    }

    public void loadKey(Uri file) throws IOException {
        try (InputStream strm = context.getContentResolver().openInputStream(file)) {
            readKey(strm);
        }
    }

    public void loadKey(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            readKey(inputStream);
        }
    }

    public byte[] decrypt(byte[] tagData) throws Exception {
        if (!hasFixedKey() || !hasUnFixedKey())
            throw new Exception(TagMo.getStringRes(R.string.key_not_present));

        AmiiTool tool = new AmiiTool();
        if (tool.setKeysFixed(fixedKey, fixedKey.length) == 0)
            throw new Exception(TagMo.getStringRes(R.string.error_amiitool_init));
        if (tool.setKeysUnfixed(unfixedKey, unfixedKey.length) == 0)
            throw new Exception(TagMo.getStringRes(R.string.error_amiitool_init));
        byte[] decrypted = new byte[NfcByte.TAG_FILE_SIZE];
        if (tool.unpack(tagData, tagData.length, decrypted, decrypted.length) == 0)
            throw new Exception(TagMo.getStringRes(R.string.fail_decrypt));

        return decrypted;
    }

    public byte[] encrypt(byte[] tagData) throws RuntimeException {
        if (!hasFixedKey() || !hasUnFixedKey())
            throw new RuntimeException(TagMo.getStringRes(R.string.key_not_present));

        AmiiTool tool = new AmiiTool();
        if (tool.setKeysFixed(fixedKey, fixedKey.length) == 0)
            throw new RuntimeException(TagMo.getStringRes(R.string.error_amiitool_init));
        if (tool.setKeysUnfixed(unfixedKey, unfixedKey.length) == 0)
            throw new RuntimeException(TagMo.getStringRes(R.string.error_amiitool_init));
        byte[] encrypted = new byte[NfcByte.TAG_FILE_SIZE];
        if (tool.pack(tagData, tagData.length, encrypted, encrypted.length) == 0)
            throw new RuntimeException(TagMo.getStringRes(R.string.fail_encrypt));

        return encrypted;
    }
}
