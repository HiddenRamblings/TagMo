package com.hiddenramblings.tagmo.nfctag;

import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

public class TagReader {

    static void validateBlankTag(NTAG215 mifare) throws Exception {
        byte[] lockPage = mifare.readPages(0x02);
        TagMo.Debug(TagWriter.class, TagUtils.bytesToHex(lockPage));
        if (lockPage[2] == (byte) 0x0F && lockPage[3] == (byte) 0xE0) {
            TagMo.Debug(TagWriter.class, R.string.locked);
            throw new Exception(TagMo.getStringRes(R.string.tag_already_written));
        }
        TagMo.Debug(TagWriter.class, R.string.unlocked);
    }

    public static void validateTag(byte[] data) throws Exception {
        byte[][] pages = TagUtils.splitPages(data);

        if (pages[0][0] != (byte) 0x04)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_prefix));

        if (pages[2][2] != (byte) 0x0F || pages[2][3] != (byte) 0xE0)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_lock));

        if (pages[3][0] != (byte) 0xF1 || pages[3][1] != (byte) 0x10 || pages[3][2] != (byte) 0xFF || pages[3][3] != (byte) 0xEE)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_cc));

        if (pages[0x82][0] != (byte) 0x01 || pages[0x82][1] != (byte) 0x0 || pages[0x82][2] != (byte) 0x0F)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_dynamic));

        if (pages[0x83][0] != (byte) 0x0 || pages[0x83][1] != (byte) 0x0 || pages[0x83][2] != (byte) 0x0 || pages[0x83][3] != (byte) 0x04)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_cfg_zero));

        if (pages[0x84][0] != (byte) 0x5F || pages[0x84][1] != (byte) 0x0 || pages[0x84][2] != (byte) 0x0 || pages[0x84][3] != (byte) 0x00)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_cfg_one));
    }

    static void validate(NTAG215 mifare, byte[] tagData, boolean validateNtag) throws Exception {
        if (tagData == null)
            throw new Exception(TagMo.getStringRes(R.string.no_source_data));

        if (validateNtag) {
            try {
                byte[] versionInfo = mifare.transceive(new byte[]{(byte) 0x60});
                if (versionInfo == null || versionInfo.length != 8)
                    throw new Exception(TagMo.getStringRes(R.string.tag_version_error));
                if (versionInfo[0x02] != (byte) 0x04 || versionInfo[0x06] != (byte) 0x11)
                    throw new Exception(TagMo.getStringRes(R.string.tag_format_error));
            } catch (Exception e) {
                TagMo.Error(TagWriter.class, R.string.version_error, e);
                throw e;
            }
        }

        byte[] pages = mifare.readPages(0);
        if (pages == null || pages.length != NfcByte.PAGE_SIZE * 4)
            throw new Exception(TagMo.getStringRes(R.string.fail_read_size));

        if (!TagUtils.compareRange(pages, tagData, 0, 9))
            throw new Exception(TagMo.getStringRes(R.string.fail_mismatch_uid));

        TagMo.Error(TagWriter.class, R.string.validation_success);
    }

    public static byte[] readTagFile(File file) throws Exception {
        byte[] data = new byte[NfcByte.TAG_FILE_SIZE];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int len = inputStream.read(data);
            if (len != NfcByte.TAG_FILE_SIZE)
                throw new Exception(TagMo.getStringRes(R.string.invalid_file_size,
                        String.valueOf(NfcByte.TAG_FILE_SIZE)));
            return data;
        }
    }

    public static byte[] readTagStream(File file) throws Exception {
        Uri fileUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fileUri = FileProvider.getUriForFile(TagMo.getContext(),
                    TagMo.PROVIDER, file);
        } else {
            fileUri = Uri.fromFile(file);
        }
        try (InputStream inputStream = TagMo.getContext().getContentResolver().openInputStream(fileUri)) {
            byte[] data = new byte[NfcByte.TAG_FILE_SIZE];
            int len = inputStream.read(data);
            if (len != NfcByte.TAG_FILE_SIZE)
                throw new Exception(TagMo.getStringRes(R.string.invalid_file_size,
                        String.valueOf(NfcByte.TAG_FILE_SIZE)));
            return data;
        }
    }

    public static byte[] readFromTag(NTAG215 tag) throws Exception {
        byte[] tagData = new byte[NfcByte.TAG_FILE_SIZE];
        int pageCount = NfcByte.TAG_FILE_SIZE / NfcByte.PAGE_SIZE;

        for (int i = 0; i < pageCount; i += NfcByte.BULK_READ_PAGE_COUNT) {
            byte[] pages = tag.readPages(i);
            if (pages == null || pages.length != NfcByte.PAGE_SIZE * NfcByte.BULK_READ_PAGE_COUNT)
                throw new Exception(TagMo.getStringRes(R.string.fail_invalid_size));

            int dstIndex = i * NfcByte.PAGE_SIZE;
            int dstCount = Math.min(NfcByte.BULK_READ_PAGE_COUNT * NfcByte.PAGE_SIZE, tagData.length - dstIndex);

            System.arraycopy(pages, 0, tagData, dstIndex, dstCount);
        }

        TagMo.Debug(TagReader.class, TagUtils.bytesToHex(tagData));
        return tagData;
    }

    public static ArrayList<String> readTagTitles(NTAG215 tag, int numBanks) throws Exception {
        ArrayList<String> tags = new ArrayList<>();
        int i = 0;
        while (i < (numBanks & 0xFF)) {
            try {
                byte[] tagData = tag.amiiboFastRead(0x15, 0x16, i);
                if (tagData == null || tagData.length != 8) {
                    throw new Exception();
                }
                tags.add(TagUtils.bytesToHex(tagData));
                i++;
            } catch (Exception e) {
                TagMo.Debug(TagReader.class, TagMo.getStringRes(R.string.fail_elite_bank_parse));
            }
        }
        return tags;
    }

    public static byte[] getBankDetails(NTAG215 tag) {
        return tag.amiiboGetVersion();
    }

    public static String getEliteSignature(NTAG215 tag) {
        byte[] signature = tag.readEliteSingature();
        if (signature != null)
            return TagUtils.bytesToHex(tag.readEliteSingature()).substring(0, 22);
        return null;
    }

    public static String writeBytesToFile(
            File directory, String name, byte[] tagData) throws IOException {
        directory.mkdirs();
        File binFile = new File(directory, name);
        try (FileOutputStream fos = new FileOutputStream(binFile)) {
            fos.write(tagData);
        }
        TagMo.scanFile(binFile);
        return binFile.getAbsolutePath();
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
            return output;
        } catch (IllegalStateException e) {
            throw new Exception(TagMo.getStringRes(R.string.fail_early_remove));
        } catch (NullPointerException e2) {
            throw new Exception(TagMo.getStringRes(R.string.fail_amiibo_npe));
        }
    }

    public static String scanAmiiboToFile(AmiiboManager amiiboManager, byte[] tagData,
                                          String browserRoot) throws Exception {
        String status = "";
        try {
            validateTag(tagData);
        } catch (Exception e) {
            status = "_corrupted_";
            throw new Exception(e.getMessage());
        }

        try {
            long amiiboId = TagUtils.amiiboIdFromTag(tagData);
            String name = null;
            if (amiiboManager != null) {
                Amiibo amiibo = amiiboManager.amiibos.get(amiiboId);
                if (amiibo != null && amiibo.name != null) {
                    name = amiibo.name.replace("/", "-");
                }
            }
            if (name == null)
                name = TagUtils.amiiboIdToHex(amiiboId);

            byte[] uId = Arrays.copyOfRange(tagData, 0, 9);
            String uIds = TagUtils.bytesToHex(uId);
            String fileName = String.format(Locale.ENGLISH,
                    "%1$s [%2$s] %3$ty%3$tm%3$te_%3$tH%3$tM%3$tS%4$s.bin",
                    name, uIds, Calendar.getInstance(), status
            );

            File directory = new File(TagMo.getStorage(), browserRoot
                    + File.pathSeparator + TagMo.getStringRes(R.string.tagmo_export));
            return writeBytesToFile(directory, fileName, tagData);
        } catch (Exception e) {
            throw new Exception(TagMo.getStringRes(R.string.write_error, e.getMessage()));
        }
    }

    public static byte[] scanBankToBytes(NTAG215 tag, int bank) throws Exception {
        byte[] output = new byte[572];
        try {
            byte[] data = tag.amiiboFastRead(0x00, 0x86, bank);
            if (data == null) {
                throw new Exception(TagMo.getStringRes(R.string.fail_read_amiibo));
            }
            System.arraycopy(data, 0, output, 0, 540);
            data = tag.readSignature();
            System.arraycopy(data, 0, output, 540, data.length);
            return output;
        } catch (IllegalStateException e) {
            throw new Exception(TagMo.getStringRes(R.string.fail_early_remove));
        } catch (NullPointerException e2) {
            throw new Exception(TagMo.getStringRes(R.string.fail_amiibo_npe));
        }
    }

    public static boolean needsFirmware(NTAG215 tag) {
        byte[] version = TagReader.getBankDetails(tag);
        return !((version.length != 4 || version[3] == (byte) 0x03)
                && !(version.length == 2 && version[0] == 100 && version[1] == 0));
    }

    public static boolean updateFirmware(NTAG215 tag) throws Exception {
        byte[] response = new byte[1];
        response[0] = (byte) 0xFFFF;
        tag.initFirmware();
        tag.getVersion();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    TagMo.getContext().getResources().openRawResource(R.raw.firmware)));
            while (true) {
                String strLine = br.readLine();
                if (strLine == null) {
                    break;
                }
                String[] parts = strLine.replaceAll("\\s+", " ").split(" ");
                int i;
                if (parts.length < 1) {
                    break;
                } else if (parts[0].equals("C-APDU")) {
                    byte[] apdu_buf = new byte[(parts.length - 1)];
                    for (i = 1; i < parts.length; i++) {
                        apdu_buf[i - 1] = TagUtils.hex2byte(parts[i]);
                    }
                    int sz = apdu_buf[4] & 0xFF;
                    byte[] iso_cmd = new byte[sz];
                    if (apdu_buf[4] + 5 <= apdu_buf.length && apdu_buf[4] <= iso_cmd.length) {
                        for (i = 0; i < sz; i++) {
                            iso_cmd[i] = apdu_buf[i + 5];
                        }
                        boolean done = false;
                        for (i = 0; i < 10; i++) {
                            response = tag.transceive(iso_cmd);
                            if (response != null) {
                                done = true;
                                break;
                            }
                        }
                        if (!done) {
                            throw new Exception(TagMo.getStringRes(R.string.firmware_failed, 1));
                        }
                    }
                    return false;
                } else if (parts[0].equals("C-RPDU")) {
                    byte[] rpdu_buf = new byte[(parts.length - 1)];
                    if (response.length != parts.length - 3) {
                        throw new Exception(TagMo.getStringRes(R.string.firmware_failed, 2));
                    }
                    for (i = 1; i < parts.length; i++) {
                        rpdu_buf[i - 1] = TagUtils.hex2byte(parts[i]);
                    }
                    for (i = 0; i < rpdu_buf.length - 2; i++) {
                        if (rpdu_buf[i] != response[i]) {
                            throw new Exception(TagMo.getStringRes(R.string.firmware_failed, 3));
                        }
                    }
                } else if (!parts[0].equals("RESET") && parts[0].equals("LOGIN")) {

                }
            }
            br.close();
            return true;
        } catch (IOException e) {
            throw new Exception(TagMo.getStringRes(R.string.firmware_failed, 4));
        }
    }
}
