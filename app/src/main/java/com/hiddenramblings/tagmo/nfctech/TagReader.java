package com.hiddenramblings.tagmo.nfctech;

import android.media.MediaScannerConnection;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.eightbit.io.Debug;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.KeyManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class TagReader {

    private static final int BULK_READ_PAGE_COUNT = 4;

    static void validateBlankTag(NTAG215 mifare) throws Exception {
        byte[] lockPage = mifare.readPages(0x02);
        Debug.Log(TagWriter.class, TagUtils.bytesToHex(lockPage));
        if (lockPage[2] == (byte) 0x0F && lockPage[3] == (byte) 0xE0) {
            throw new IOException(TagMo.getStringRes(R.string.error_tag_rewrite));
        }
        Debug.Log(TagWriter.class, R.string.validation_success);
    }

    public static byte[] getValidatedData(KeyManager keyManager, byte[] data) throws Exception {
        if (data == null) return null;
        try {
            TagUtils.validateData(data);
        } catch (Exception e) {
            data = keyManager.encrypt(data);
            TagUtils.validateData(data);
        }
        return data;
    }

    public static byte[] getValidatedFile(KeyManager keyManager, File file) throws Exception {
        return getValidatedData(keyManager, TagReader.readTagFile(file));
    }

    public static byte[] getValidatedDocument(
            KeyManager keyManager, DocumentFile file) throws Exception {
        return getValidatedData(keyManager, TagReader.readTagDocument(file.getUri()));
    }

    private static byte[] getTagData(String path, InputStream inputStream) throws Exception {
        byte[] data = new byte[NfcByte.TAG_FILE_SIZE];
        int len = inputStream.read(data);
        if (len == NfcByte.KEY_FILE_SIZE) return null;
        if (len != NfcByte.TAG_FILE_SIZE)
            throw new IOException(TagMo.getStringRes(R.string.invalid_file_size,
                    path, NfcByte.TAG_FILE_SIZE));
        return data;
    }

    static byte[] readTagFile(File file) throws Exception {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return getTagData(file.getPath(), inputStream);
        }
    }

    static byte[] readTagDocument(Uri file) throws Exception {
        try (InputStream inputStream = TagMo.getContext()
                .getContentResolver().openInputStream(file)) {
            return getTagData(file.getPath(), inputStream);
        }
    }

    public static byte[] readFromTag(NTAG215 tag) throws Exception {
        byte[] tagData = new byte[NfcByte.TAG_FILE_SIZE];
        int pageCount = NfcByte.TAG_FILE_SIZE / NfcByte.PAGE_SIZE;

        for (int i = 0; i < pageCount; i += BULK_READ_PAGE_COUNT) {
            byte[] pages = tag.readPages(i);
            if (pages == null || pages.length != NfcByte.PAGE_SIZE * BULK_READ_PAGE_COUNT)
                throw new IOException(TagMo.getStringRes(R.string.fail_invalid_size));

            int dstIndex = i * NfcByte.PAGE_SIZE;
            int dstCount = Math.min(BULK_READ_PAGE_COUNT * NfcByte.PAGE_SIZE, tagData.length - dstIndex);

            System.arraycopy(pages, 0, tagData, dstIndex, dstCount);
        }

        Debug.Log(TagReader.class, TagUtils.bytesToHex(tagData));
        return tagData;
    }

    static byte[] readBankTitle(NTAG215 tag, int bank) {
        return tag.amiiboFastRead(0x15, 0x16, bank);
    }

    public static ArrayList<String> readTagTitles(NTAG215 tag, int numBanks) {
        ArrayList<String> tags = new ArrayList<>();
        int i = 0;
        while (i < (numBanks & 0xFF)) {
            try {
                byte[] tagData = readBankTitle(tag, i);
                if (tagData == null || tagData.length != 8) {
                    throw new NullPointerException();
                }
                tags.add(TagUtils.bytesToHex(tagData));
                i++;
            } catch (Exception e) {
                Debug.Log(TagReader.class, TagMo.getStringRes(R.string.fail_parse_banks));
            }
        }
        return tags;
    }

    public static byte[] getEliteDetails(NTAG215 tag) {
        return tag.amiiboGetVersion();
    }

    public static String getEliteSignature(NTAG215 tag) {
        byte[] signature = tag.readEliteSingature();
        if (signature != null)
            return TagUtils.bytesToHex(signature).substring(0, 22);
        return null;
    }

    public static String writeBytesToFile(File backupDir, String name, byte[] tagData)
            throws IOException {
        //noinspection ResultOfMethodCallIgnored
        backupDir.mkdirs();
        File binFile = new File(backupDir, name);
        try (FileOutputStream fos = new FileOutputStream(binFile)) {
            fos.write(tagData);
        }
        try {
            MediaScannerConnection.scanFile(TagMo.getContext(),
                    new String[] { binFile.getAbsolutePath() }, null, null);
        } catch (Exception e) {
            Debug.Log(R.string.fail_media_scan, e);
        }
        return binFile.getAbsolutePath();
    }

    public static String decipherBinName(
            AmiiboManager amiiboManager, byte[] tagData, boolean decrypted) {
        String status = "";
        if (decrypted) {
            status = "(Decrypted)";
        } else {
            try {
                TagUtils.validateData(tagData);
            } catch (Exception e) {
                status = "(Invalid)";
            }
        }
        try {
            long amiiboId = TagUtils.amiiboIdFromTag(tagData);
            String name = TagUtils.amiiboIdToHex(amiiboId);
            if (amiiboManager != null) {
                Amiibo amiibo = amiiboManager.amiibos.get(amiiboId);
                if (amiibo != null && amiibo.name != null) {
                    name = amiibo.name.replace("/", "-");
                }
            }

            byte[] uId = Arrays.copyOfRange(tagData, 0, 9);
            String uIds = TagUtils.bytesToHex(uId);
            return String.format(Locale.ROOT, "%1$s[%2$s]%3$s.bin", name, uIds, status);
        } catch (Exception e) {
            Debug.Log(TagReader.class, e.getMessage());
        }
        return "";
    }

    public static String decipherFilename(AmiiboManager amiiboManager, byte[] tagData) {
        return decipherBinName(amiiboManager, tagData, false);
    }

    public static byte[] scanTagToBytes(NTAG215 tag) throws Exception {
        byte[] output = new byte[572];
        try {
            byte[] data = tag.fastRead(0x00, 0x86);
            if (data == null) {
                throw new Exception(TagMo.getStringRes(R.string.fail_read_amiibo));
            }
            System.arraycopy(data, 0, output, 0, 540);
            data = tag.readSignature();
            System.arraycopy(data, 0, output, 540, data.length);
            Debug.Log(TagReader.class, TagUtils.bytesToHex(output));
            return output;
        } catch (IllegalStateException e) {
            throw new IllegalStateException(TagMo.getStringRes(R.string.fail_early_remove));
        } catch (NullPointerException e2) {
            throw new NullPointerException(TagMo.getStringRes(R.string.fail_amiibo_null));
        }
    }

    public static byte[] scanBankToBytes(NTAG215 tag, int bank) throws Exception {
        byte[] output = new byte[572];
        try {
            byte[] data = tag.amiiboFastRead(0x00, 0x86, bank);
            if (data == null) {
                throw new NullPointerException(TagMo.getStringRes(R.string.fail_read_amiibo));
            }
            System.arraycopy(data, 0, output, 0, 540);
            data = tag.readSignature();
            System.arraycopy(data, 0, output, 540, data.length);
            Debug.Log(TagReader.class, TagUtils.bytesToHex(output));
            return output;
        } catch (IllegalStateException e) {
            throw new IllegalStateException(TagMo.getStringRes(R.string.fail_early_remove));
        } catch (NullPointerException npe) {
            throw new NullPointerException(TagMo.getStringRes(R.string.fail_amiibo_null));
        }
    }

    public static boolean needsFirmware(NTAG215 tag) {
        byte[] version = getEliteDetails(tag);
        return !((version.length != 4 || version[3] == (byte) 0x03)
                && !(version.length == 2 && version[0] == 100 && version[1] == 0));
    }
}
