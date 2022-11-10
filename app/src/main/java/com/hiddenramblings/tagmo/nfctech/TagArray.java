/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */

package com.hiddenramblings.tagmo.nfctech;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.browser.Preferences;
import com.hiddenramblings.tagmo.eightbit.io.Debug;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;

public class TagArray {

    public static String getTagTechnology(Tag tag) {
        final Context context = TagMo.getContext();
        String type = context.getString(R.string.unknown_type);
        for (String tech : tag.getTechList()) {
            if (MifareClassic.class.getName().equals(tech)) {
                switch (MifareClassic.get(tag).getType()) {
                    default:
                    case MifareClassic.TYPE_CLASSIC:
                        type = context.getString(R.string.mifare_classic);
                        break;
                    case MifareClassic.TYPE_PLUS:
                        type = context.getString(R.string.mifare_plus);
                        break;
                    case MifareClassic.TYPE_PRO:
                        type = context.getString(R.string.mifare_pro);
                        break;
                }
                return type;
            } else if (MifareUltralight.class.getName().equals(tech)) {
                switch (MifareUltralight.get(tag).getType()) {
                    default:
                    case MifareUltralight.TYPE_ULTRALIGHT:
                        type = context.getString(R.string.mifare_ultralight);
                        break;
                    case MifareUltralight.TYPE_ULTRALIGHT_C:
                        type = context.getString(R.string.mifare_ultralight_c);
                        break;
                }
                return type;
            } else if (IsoDep.class.getName().equals(tech)) {
                return context.getString(R.string.isodep);
            } else if (Ndef.class.getName().equals(tech)) {
                return context.getString(R.string.ndef);
            } else if (NdefFormatable.class.getName().equals(tech)) {
                return context.getString(R.string.ndef_formatable);
            }
        }
        return type;
    }

    private static Preferences mPrefs = new Preferences(TagMo.getContext());

    public static boolean isPowerTag(NTAG215 mifare) {
        if (mPrefs.power_tag_support()) {
            byte[] signature = mifare.transceive(NfcByte.POWERTAG_SIG);
            return null != signature && TagArray.compareRange(signature,
                    NfcByte.POWERTAG_SIGNATURE, NfcByte.POWERTAG_SIGNATURE.length);
        }
        return false;
    }

    public static boolean isElite(NTAG215 mifare) {
        if (mPrefs.elite_support()) {
            byte[] signature = mifare.readSignature(false);
            byte[] page10 = TagArray.hexToByteArray("FFFFFFFFFF");
            return null != signature && TagArray.compareRange(signature, page10,
                    32 - page10.length, signature.length);
        }
        return false;
    }

    static boolean compareRange(byte[] data, byte[] data2, int offset, int len) {
        for (int i = 0, j = offset; j < len; i++, j++) {
            if (data[j] != data2[i])
                return false;
        }
        return true;
    }

    public static boolean compareRange(byte[] data, byte[] data2, int len) {
        return compareRange(data, data2, 0, len);
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

    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? Long.BYTES : 8
        );
        buffer.putLong(x);
        return buffer.array();
    }

    public static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? Long.BYTES : 8
        );
        buffer.put(bytes);
        buffer.flip(); // need flip
        try {
            return buffer.getLong();
        } catch (BufferUnderflowException bue) {
            return (long) buffer.getInt();
        }
    }

    public static long hexToLong(String s) {
        long result = 0;
        for (int i = 0; i < s.length(); i++) {
            result = (result << 4) + ((long) Character.digit(s.charAt(i), 16));
        }
        return result;
    }

    static String hexToString(String hex) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 2) {
            output.append((char) Integer.parseInt(hex.substring(i, i + 2), 16));
        }
        return output.toString();
    }

    @Nullable
    public static void validateData(byte[] data) throws Exception {
        final Context context = TagMo.getContext();
        if (null == data)
            throw new IOException(context.getString(R.string.invalid_data_null));
        /* TagWriter.splitPages(data) */
        if (data.length == NfcByte.KEY_FILE_SIZE || data.length == NfcByte.KEY_FILE_SIZE * 2)
            throw new IOException(context.getString(R.string.invalid_tag_key));
        else if (data.length < NfcByte.TAG_DATA_SIZE)
            throw new IOException(context.getString(
                    R.string.invalid_data_size, data.length, NfcByte.TAG_DATA_SIZE
            ));

        byte[][] pages = new byte[data.length / NfcByte.PAGE_SIZE][];
        for (int i = 0, j = 0; i < data.length; i += NfcByte.PAGE_SIZE, j++) {
            pages[j] = Arrays.copyOfRange(data, i, i + NfcByte.PAGE_SIZE);
        }

        if (pages[0][0] != (byte) 0x04)
            throw new Exception(context.getString(R.string.invalid_tag_prefix));

        if (pages[2][2] != (byte) 0x0F || pages[2][3] != (byte) 0xE0)
            throw new Exception(context.getString(R.string.invalid_tag_lock));

        if (pages[3][0] != (byte) 0xF1 || pages[3][1] != (byte) 0x10
                || pages[3][2] != (byte) 0xFF || pages[3][3] != (byte) 0xEE)
            throw new Exception(context.getString(R.string.invalid_tag_cc));

        if (pages[0x82][0] != (byte) 0x01 || pages[0x82][1] != (byte) 0x0 || pages[0x82][2] != (byte) 0x0F)
            throw new Exception(context.getString(R.string.invalid_tag_dynamic));

        if (pages[0x83][0] != (byte) 0x0 || pages[0x83][1] != (byte) 0x0
                || pages[0x83][2] != (byte) 0x0 || pages[0x83][3] != (byte) 0x04)
            throw new Exception(context.getString(R.string.invalid_tag_cfg_zero));

        if (pages[0x84][0] != (byte) 0x5F || pages[0x84][1] != (byte) 0x0
                || pages[0x84][2] != (byte) 0x0 || pages[0x84][3] != (byte) 0x00)
            throw new Exception(context.getString(R.string.invalid_tag_cfg_one));
    }

    public static void validateNtag(NTAG215 mifare, byte[] tagData, boolean validateNtag)
            throws Exception {
        final Context context = TagMo.getContext();
        if (null == tagData) throw new IOException(context.getString(R.string.no_source_data));

        if (validateNtag) {
            try {
                byte[] versionInfo = mifare.transceive(new byte[]{(byte) 0x60});
                if (null == versionInfo  || versionInfo.length != 8)
                    throw new Exception(context.getString(R.string.error_tag_version));
                if (versionInfo[0x02] != (byte) 0x04 || versionInfo[0x06] != (byte) 0x11)
                    throw new FormatException(context.getString(R.string.error_tag_specs));
            } catch (Exception e) {
                Debug.Warn(R.string.error_version, e);
                throw e;
            }
        }

        byte[] pages = mifare.readPages(0);
        if (null == pages  || pages.length != NfcByte.PAGE_SIZE * 4)
            throw new Exception(context.getString(R.string.fail_read_size));

        if (!TagArray.compareRange(pages, tagData, 9))
            throw new Exception(context.getString(R.string.fail_mismatch_uid));

        Debug.Info(TagWriter.class, R.string.validation_success);
    }

    public static String decipherFilename(AmiiboManager amiiboManager, byte[] tagData, boolean verified) {
        String status = "";
        if (verified) {
            try {
                validateData(tagData);
                status = "Validated";
            } catch (Exception e) {
                Debug.Warn(e);
                status = e.getMessage();
            }
        }
        try {
            long amiiboId = Amiibo.dataToId(tagData);
            String name = Amiibo.idToHex(amiiboId);
            if (null != amiiboManager) {
                Amiibo amiibo = amiiboManager.amiibos.get(amiiboId);
                if (null != amiibo && null != amiibo.name) {
                    name = amiibo.name.replace(File.separatorChar, '-');
                }
            }
            
            String uidHex = bytesToHex(Arrays.copyOfRange(tagData, 0, 9));
            if (verified)
                return String.format(Locale.ROOT, "%1$s[%2$s]-%3$s.bin", name, uidHex, status);
            else
                return String.format(Locale.ROOT, "%1$s[%2$s].bin", name, uidHex);
        } catch (Exception ex) {
            Debug.Warn(ex);
        }
        return "";
    }

    public static byte[] getValidatedData(KeyManager keyManager, byte[] data) throws Exception {
        if (null == data ) return null;
        try {
            TagArray.validateData(data);
            data = keyManager.decrypt(data);
        } catch (Exception e) {
            data = keyManager.encrypt(data);
            TagArray.validateData(data);
            data = keyManager.decrypt(data);
        }
        return keyManager.encrypt(data);
    }

    public static byte[] getValidatedFile(KeyManager keyManager, File file) throws Exception {
        return getValidatedData(keyManager, TagReader.readTagFile(file));
    }

    public static byte[] getValidatedDocument(
            KeyManager keyManager, Uri fileUri) throws Exception {
        return getValidatedData(keyManager, TagReader.readTagDocument(fileUri));
    }

    public static byte[] getValidatedDocument(
            KeyManager keyManager, DocumentFile file) throws Exception {
        return getValidatedData(keyManager, TagReader.readTagDocument(file.getUri()));
    }

    public static byte[] getValidatedData(KeyManager keyManager, AmiiboFile file) throws Exception {
        return null != file.getData() ? file.getData() : null != file.getDocUri()
                ? TagArray.getValidatedDocument(keyManager, file.getDocUri())
                : TagArray.getValidatedFile(keyManager, file.getFilePath());
    }

    public static String writeBytesToFile(File directory, String name, byte[] tagData)
            throws IOException {
        File binFile = new File(directory, name);
        new FileOutputStream(binFile).write(tagData);
        try {
            MediaScannerConnection.scanFile(TagMo.getContext(),
                    new String[] { binFile.getAbsolutePath() },
                    null, null);
        } catch (Exception e) {
            Debug.Info(e);
        }
        return binFile.getAbsolutePath();
    }

    public static String writeBytesToDocument(
            Context context, DocumentFile directory, String name, byte[] tagData
    ) throws IOException {
        DocumentFile newFile = directory.createFile(context.getResources()
                .getStringArray(R.array.mimetype_bin)[0], name + ".bin");
        if (null != newFile) {
            context.getContentResolver().openOutputStream(newFile.getUri()).write(tagData);
        }
        return null != newFile ? newFile.getName() : null;
    }
}
