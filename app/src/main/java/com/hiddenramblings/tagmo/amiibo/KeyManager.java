package com.hiddenramblings.tagmo.amiibo;

import android.content.Context;
import android.net.Uri;

import com.hiddenramblings.tagmo.AmiiTool;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.nfctech.NfcByte;
import com.hiddenramblings.tagmo.nfctech.TagUtils;

import java.io.ByteArrayInputStream;
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

    private static final String JaviMaD = /* https://pastebin.com/aV23ha3X */
            "1D 16 4B 37 5B 72 A5 57 28 B9 1D 64 B6 A3 C2 05" +
            "75 6E 66 69 78 65 64 20 69 6E 66 6F 73 00 00 0E" +
            "DB 4B 9E 3F 45 27 8F 39 7E FF 9B 4F B9 93 00 00" +
            "04 49 17 DC 76 B4 96 40 D6 F8 39 39 96 0F AE D4" +
            "EF 39 2F AA B2 14 28 AA 21 FB 54 E5 45 05 47 66" +
            "7F 75 2D 28 73 A2 00 17 FE F8 5C 05 75 90 4B 6D" +
            "6C 6F 63 6B 65 64 20 73 65 63 72 65 74 00 00 10" +
            "FD C8 A0 76 94 B8 9E 4C 47 D3 7D E8 CE 5C 74 C1" +
            "04 49 17 DC 76 B4 96 40 D6 F8 39 39 96 0F AE D4" +
            "EF 39 2F AA B2 14 28 AA 21 FB 54 E5 45 05 47 66";

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

    private void evaluateKey(InputStream strm) throws IOException {
        byte[] data = new byte[NfcByte.KEY_FILE_SIZE * 2];
        int rlen = strm.read(data, 0, NfcByte.KEY_FILE_SIZE * 2);
        if (rlen <= 0) {
            throw new IOException(context.getString(R.string.invalid_key_error));
        } else if (rlen == NfcByte.KEY_FILE_SIZE * 2) {
            byte[] key2 = new byte[NfcByte.KEY_FILE_SIZE];
            System.arraycopy(data, NfcByte.KEY_FILE_SIZE, key2, 0, NfcByte.KEY_FILE_SIZE);
            readKey(key2);
            byte[] key1 = new byte[NfcByte.KEY_FILE_SIZE];
            System.arraycopy(data, 0, key1, 0, NfcByte.KEY_FILE_SIZE);
            readKey(key1);
        } else if (rlen == NfcByte.KEY_FILE_SIZE) {
            readKey(data);
        } else {
            throw new IOException(context.getString(R.string.key_size_error));
        }
    }

    private void readKey(byte[] data) throws IOException {
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
        } else {
            throw new IOException(context.getString(R.string.key_signature_error));
        }
    }

    public void decipherKey() throws IOException {
        evaluateKey(new ByteArrayInputStream(TagUtils.hexToByteArray(
                JaviMaD.replace(" ", ""))));
    }

    public void loadKey(Uri uri) throws IOException {
        try (InputStream strm = context.getContentResolver().openInputStream(uri)) {
            evaluateKey(strm);
        }
    }

    @SuppressWarnings("unused")
    public void loadKey(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            evaluateKey(inputStream);
        }
    }

    public byte[] decrypt(byte[] tagData) throws Exception {
        if (!hasFixedKey() || !hasUnFixedKey())
            throw new Exception(context.getString(R.string.key_not_present));

        AmiiTool tool = new AmiiTool();
        if (tool.setKeysFixed(fixedKey, fixedKey.length) == 0)
            throw new Exception(context.getString(R.string.error_amiitool_init));
        if (tool.setKeysUnfixed(unfixedKey, unfixedKey.length) == 0)
            throw new Exception(context.getString(R.string.error_amiitool_init));
        byte[] decrypted = new byte[NfcByte.TAG_FILE_SIZE];
        if (tool.unpack(tagData, tagData.length, decrypted, decrypted.length) == 0)
            throw new Exception(context.getString(R.string.fail_decrypt));

        return decrypted;
    }

    public byte[] encrypt(byte[] tagData) throws RuntimeException {
        if (!hasFixedKey() || !hasUnFixedKey())
            throw new RuntimeException(context.getString(R.string.key_not_present));

        AmiiTool tool = new AmiiTool();
        if (tool.setKeysFixed(fixedKey, fixedKey.length) == 0)
            throw new RuntimeException(context.getString(R.string.error_amiitool_init));
        if (tool.setKeysUnfixed(unfixedKey, unfixedKey.length) == 0)
            throw new RuntimeException(context.getString(R.string.error_amiitool_init));
        byte[] encrypted = new byte[NfcByte.TAG_FILE_SIZE];
        if (tool.pack(tagData, tagData.length, encrypted, encrypted.length) == 0)
            throw new RuntimeException(context.getString(R.string.fail_encrypt));

        return encrypted;
    }
}
