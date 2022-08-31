package com.hiddenramblings.tagmo.nfctech;

import android.content.Context;
import android.net.Uri;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.eightbit.io.Debug;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class TagReader {

    private static final int BULK_READ_PAGE_COUNT = 4;

    static void validateBlankTag(NTAG215 mifare) throws IOException {
        byte[] lockPage = mifare.readPages(0x02);
        Debug.Info(TagWriter.class, TagUtils.bytesToHex(lockPage));
        if (lockPage[2] == (byte) 0x0F && lockPage[3] == (byte) 0xE0) {
            throw new IOException(TagMo.getContext()
                    .getString(R.string.error_tag_rewrite));
        }
        Debug.Info(TagWriter.class, R.string.validation_success);
    }

    private static byte[] getTagData(String path, InputStream inputStream) throws Exception {
        int length = inputStream.available();
        if (length < NfcByte.TAG_FILE_SIZE) {
            if (length == NfcByte.KEY_FILE_SIZE) return null;
            throw new IOException(TagMo.getContext().getString(
                    R.string.invalid_file_size, path, length, NfcByte.TAG_FILE_SIZE));
        }
        byte[] data = new byte[NfcByte.TAG_FILE_SIZE];
        new DataInputStream(inputStream).readFully(data);
        return data;
    }

    static byte[] readTagFile(File file) throws Exception {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return getTagData(file.getPath(), inputStream);
        }
    }

    public static byte[] readTagDocument(Uri uri) throws Exception {
        try (InputStream inputStream = TagMo.getContext()
                .getContentResolver().openInputStream(uri)) {
            return getTagData(uri.getPath(), inputStream);
        }
    }

    public static byte[] readFromTag(NTAG215 tag) throws Exception {
        byte[] tagData = new byte[NfcByte.TAG_FILE_SIZE];
        int pageCount = NfcByte.TAG_FILE_SIZE / NfcByte.PAGE_SIZE;

        for (int i = 0; i < pageCount; i += BULK_READ_PAGE_COUNT) {
            byte[] pages = tag.readPages(i);
            if (null == pages || pages.length != NfcByte.PAGE_SIZE * BULK_READ_PAGE_COUNT)
                throw new IOException(TagMo.getContext()
                        .getString(R.string.fail_invalid_size));

            int dstIndex = i * NfcByte.PAGE_SIZE;
            int dstCount = Math.min(BULK_READ_PAGE_COUNT
                    * NfcByte.PAGE_SIZE, tagData.length - dstIndex);

            System.arraycopy(pages, 0, tagData, dstIndex, dstCount);
        }

        Debug.Info(TagReader.class, TagUtils.bytesToHex(tagData));
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
                if (null == tagData || tagData.length != 8) {
                    throw new NullPointerException();
                }
                tags.add(TagUtils.bytesToHex(tagData));
                i++;
            } catch (Exception e) {
                Debug.Warn(TagReader.class, TagMo.getContext()
                        .getString(R.string.fail_parse_banks));
            }
        }
        return tags;
    }

    public static byte[] getBankDetails(NTAG215 tag) {
        return tag.getVersion(false);
    }

    public static String getTagSignature(NTAG215 tag) {
        byte[] signature = tag.readSignature(false);
        if (null != signature)
            return TagUtils.bytesToHex(signature).substring(0, 22);
        return null;
    }

    public static byte[] scanTagToBytes(NTAG215 tag, int bank)
            throws IllegalStateException, NullPointerException {
        byte[] tagData = new byte[NfcByte.TAG_FILE_SIZE];
        try {
            byte[] data = bank == -1 ? tag.fastRead(0x00, 0x86)
                    : tag.amiiboFastRead(0x00, 0x86, bank);
            if (null == data) {
                throw new NullPointerException(TagMo.getContext()
                        .getString(R.string.fail_read_amiibo));
            }
            System.arraycopy(data, 0, tagData, 0, NfcByte.TAG_FILE_SIZE);
            return tagData;
        } catch (IllegalStateException e) {
            throw new IllegalStateException(TagMo.getContext()
                    .getString(R.string.fail_early_remove));
        } catch (NullPointerException npe) {
            throw new NullPointerException(TagMo.getContext()
                    .getString(R.string.fail_amiibo_null));
        }
    }

    public static byte[] scanBankToBytes(NTAG215 tag, int bank)
            throws IllegalStateException, NullPointerException {
        final Context context = TagMo.getContext();
        byte[] tagData = new byte[NfcByte.TAG_FILE_SIZE];
        try {
            byte[] data = tag.amiiboFastRead(0x00, 0x86, bank);
            if (null == data) {
                throw new NullPointerException(context.getString(R.string.fail_read_amiibo));
            }
            System.arraycopy(data, 0, tagData, 0, NfcByte.TAG_FILE_SIZE);
            Debug.Info(TagReader.class, TagUtils.bytesToHex(tagData));
            return tagData;
        } catch (IllegalStateException e) {
            throw new IllegalStateException(context.getString(R.string.fail_early_remove));
        } catch (NullPointerException npe) {
            throw new NullPointerException(context.getString(R.string.fail_amiibo_null));
        }
    }

    public static boolean needsFirmware(NTAG215 tag) {
        byte[] version = getBankDetails(tag);
        return !((version.length != 4 || version[3] == (byte) 0x03)
                && !(version.length == 2 && version[0] == 100 && version[1] == 0));
    }
}
