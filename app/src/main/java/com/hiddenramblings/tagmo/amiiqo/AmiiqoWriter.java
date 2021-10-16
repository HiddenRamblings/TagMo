package com.hiddenramblings.tagmo.amiiqo;

import com.hiddenramblings.tagmo.KeyManager;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.TagUtil;
import com.hiddenramblings.tagmo.Util;
import com.hiddenramblings.tagmo.ptag.PTagKeyManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class AmiiqoWriter {
    private static final String TAG = AmiiqoWriter.class.getSimpleName();

    private static final byte[] POWERTAG_SIGNATURE = Util.hexStringToByteArray("213C65444901602985E9F6B50CACB9C8CA3C4BCD13142711FF571CF01E66BD6F");
    private static final byte[] POWERTAG_IDPAGES = Util.hexStringToByteArray("04070883091012131800000000000000");
    private static final String POWERTAG_KEY = "FFFFFFFFFFFFFFFF0000000000000000";
    private static final byte[] COMP_WRITE_CMD = Util.hexStringToByteArray("a000");
    private static final byte[] SIG_CMD = Util.hexStringToByteArray("3c00");

    public static void writeToTagRaw(N2Elite mifare, byte[] tagData, boolean validateNtag) throws Exception {
        validate(mifare, tagData, validateNtag);

        validateBlankTag(mifare);

        try {
            byte[][] pages = TagUtil.splitPages(tagData);
            writePages(mifare, 3, 129, pages);
            TagMo.Debug(TAG, R.string.data_write);
        } catch (Exception e) {
            throw new Exception(TagMo.getStringRes(R.string.data_write_error), e);
        }
        try {
            writePassword(mifare);
            TagMo.Debug(TAG, R.string.password_write);
        } catch (Exception e) {
            throw new Exception(TagMo.getStringRes(R.string.password_write_error), e);
        }
        try {
            writeLockInfo(mifare);
            TagMo.Debug(TAG, R.string.lock_write);
        } catch (Exception e) {
            throw new Exception(TagMo.getStringRes(R.string.lock_write_error), e);
        }
    }

    private static void validateBlankTag(N2Elite mifare) throws Exception {
        byte[] lockPage = mifare.readPages(0x02);
        TagMo.Debug(TAG, Util.bytesToHex(lockPage));
        if (lockPage[2] == (byte) 0x0F && lockPage[3] == (byte) 0xE0) {
            TagMo.Debug(TAG, R.string.locked);
            throw new Exception(TagMo.getStringRes(R.string.tag_already_written));
        }
        TagMo.Debug(TAG, R.string.unlocked);
    }

    public static void writeToTagAuto(N2Elite mifare, byte[] tagData, KeyManager keyManager, boolean validateNtag, boolean supportPowerTag) throws Exception {
        byte[] idPages = mifare.readPages(0);
        if (idPages == null || idPages.length != TagUtil.PAGE_SIZE * 4)
            throw new Exception(TagMo.getStringRes(R.string.fail_read_size));

        boolean isPowerTag = false;
        if (supportPowerTag) {
            byte[] sig = mifare.transceive(SIG_CMD);
            isPowerTag = compareRange(sig, POWERTAG_SIGNATURE, 0, POWERTAG_SIGNATURE.length);
        }

        TagMo.Debug(TAG, R.string.power_tag_exists, String.valueOf(isPowerTag));

        tagData = TagUtil.decrypt(keyManager, tagData);
        if (isPowerTag) {
            //use a pre-determined static id for powertag
            tagData = TagUtil.patchUid(POWERTAG_IDPAGES, tagData);
        } else {
            tagData = TagUtil.patchUid(idPages, tagData);
        }
        tagData = TagUtil.encrypt(keyManager, tagData);

        TagMo.Debug(TAG, Util.bytesToHex(tagData));

        if (!isPowerTag) {
            validate(mifare, tagData, validateNtag);
            validateBlankTag(mifare);
        }

        if (isPowerTag) {
            byte[] oldid = mifare.getTag().getId();
            if (oldid == null || oldid.length != 7)
                throw new Exception(TagMo.getStringRes(R.string.fail_read_uid));

            TagMo.Debug(TAG, R.string.old_uid, Util.bytesToHex(oldid));

            byte[] page10 = mifare.readPages(0x10);
            TagMo.Debug(TAG, R.string.page_ten, Util.bytesToHex(page10));

            String page10bytes = Util.bytesToHex(new byte[]{page10[0], page10[3]});

            byte[] ptagKeySuffix = PTagKeyManager.getKey(oldid, page10bytes);
            byte[] ptagKey = Util.hexStringToByteArray(POWERTAG_KEY);
            System.arraycopy(ptagKeySuffix, 0, ptagKey, 8, 8);

            TagMo.Debug(TAG, R.string.ptag_key, Util.bytesToHex(ptagKey));

            mifare.transceive(COMP_WRITE_CMD);
            mifare.transceive(ptagKey);

            if (!(idPages[0] == (byte) 0xFF && idPages[1] == (byte) 0xFF))
                doAuth(mifare);
        }

        byte[][] pages = TagUtil.splitPages(tagData);
        if (isPowerTag) {
            byte[] zeropage = Util.hexStringToByteArray("00000000");
            mifare.writePage(0x86, zeropage); //PACK
            writePages(mifare, 0x01, 0x84, pages);
            mifare.writePage(0x85, zeropage); //PWD
            mifare.writePage(0x00, pages[0]); //UID
            mifare.writePage(0x00, pages[0]); //UID
        } else {
            try {
                writePages(mifare, 3, 129, pages);
                TagMo.Debug(TAG, R.string.data_write);
            } catch (Exception e) {
                throw new Exception(TagMo.getStringRes(R.string.data_write_error), e);
            }
            try {
                writePassword(mifare);
                TagMo.Debug(TAG, R.string.password_write);
            } catch (Exception e) {
                throw new Exception(TagMo.getStringRes(R.string.password_write_error), e);
            }
            try {
                writeLockInfo(mifare);
                TagMo.Debug(TAG, R.string.lock_write);
            } catch (Exception e) {
                throw new Exception(TagMo.getStringRes(R.string.lock_write_error), e);
            }
        }
    }

    public static void restoreTag(N2Elite mifare, byte[] tagData, boolean ignoreUid, KeyManager keyManager, boolean validateNtag) throws Exception {
        if (!ignoreUid)
            validate(mifare, tagData, validateNtag);
        else {
            byte[] liveData = readFromTag(mifare);
            if (!compareRange(liveData, tagData, 0, 9)) {
                //restoring to different tag: transplant mii and appdata to livedata and re-encrypt

                liveData = TagUtil.decrypt(keyManager, liveData);
                tagData = TagUtil.decrypt(keyManager, tagData);

                System.arraycopy(tagData, 0x08, liveData, 0x08, 0x1B4 - 0x08);

                tagData = TagUtil.encrypt(keyManager, liveData);
            }
        }

        doAuth(mifare);
        byte[][] pages = TagUtil.splitPages(tagData);
        writePages(mifare, 4, 12, pages);
        writePages(mifare, 32, 129, pages);
    }

    static void validate(N2Elite mifare, byte[] tagData, boolean validateNtag) throws Exception {
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
                TagMo.Error(TAG, R.string.version_error, e);
                throw e;
            }
        }

        byte[] pages = mifare.readPages(0);
        if (pages == null || pages.length != TagUtil.PAGE_SIZE * 4)
            throw new Exception(TagMo.getStringRes(R.string.fail_read_size));

        if (!compareRange(pages, tagData, 0, 9))
            throw new Exception(TagMo.getStringRes(R.string.fail_mismatch_uid));

        TagMo.Error(TAG, R.string.validation_success);
    }

    static boolean compareRange(byte[] data, byte[] data2, int data2offset, int len) {
        for (int i = data2offset, j = 0; j < len; i++, j++) {
            if (data[j] != data2[i])
                return false;
        }
        return true;
    }

    public static final int BULK_READ_PAGE_COUNT = 4;

    public static byte[] readFromTag(N2Elite tag) throws Exception {
        byte[] tagData = new byte[TagUtil.TAG_FILE_SIZE];
        int pageCount = TagUtil.TAG_FILE_SIZE / TagUtil.PAGE_SIZE;

        for (int i = 0; i < pageCount; i += BULK_READ_PAGE_COUNT) {
            byte[] pages = tag.readPages(i);
            if (pages == null || pages.length != TagUtil.PAGE_SIZE * BULK_READ_PAGE_COUNT)
                throw new Exception(TagMo.getStringRes(R.string.fail_invalid_size));

            int dstIndex = i * TagUtil.PAGE_SIZE;
            int dstCount = Math.min(BULK_READ_PAGE_COUNT * TagUtil.PAGE_SIZE, tagData.length - dstIndex);

            System.arraycopy(pages, 0, tagData, dstIndex, dstCount);
        }

        TagMo.Debug(TAG, Util.bytesToHex(tagData));
        return tagData;
    }

    public static ArrayList<byte[]> readFromTags(N2Elite tag, int numBanks) throws Exception {
        ArrayList<byte[]> tags = new ArrayList<>();
        int i = 0;
        while (i < (numBanks & 255)) {
            try {
                byte[] tagData = tag.amiiboFastRead(0x15, 0x16, i);
                if (tagData == null || tagData.length != 8) {
                    throw new Exception();
                }
                tags.add(tagData);
                i++;
            } catch (Exception e) {
                TagMo.Debug(TAG, TagMo.getStringRes(R.string.fail_amiiqo_bank_parse));
            }
        }
        return tags;
    }

    public static int getAmiiqoBankCount(N2Elite tag) {
        return ByteBuffer.wrap(tag.getAmiiqoBankCount()).getShort();
    }

    public static String getAmiiqoSignature(N2Elite tag) {
        return Util.bytesToHex(tag.readAmiiqoSignature()).substring(0, 22);
    }

    static void writePages(N2Elite tag, int pagestart, int pageend, byte[][] data) throws IOException {
        for (int i = pagestart; i <= pageend; i++) {
            tag.writePage(i, data[i]);
            TagMo.Debug(TAG, R.string.write_page, String.valueOf(i));
        }
    }

    static void writePassword(N2Elite tag) throws IOException {
        byte[] pages0_1 = tag.readPages(0);

        if (pages0_1 == null || pages0_1.length != TagUtil.PAGE_SIZE * 4)
            throw new IOException(TagMo.getStringRes(R.string.read_failed));

        byte[] uid = TagUtil.uidFromPages(pages0_1);
        byte[] password = TagUtil.keygen(uid);

        TagMo.Debug(TAG, R.string.password, Util.bytesToHex(password));

        TagMo.Debug(TAG, R.string.write_pack);
        tag.writePage(0x86, new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0, (byte) 0});

        TagMo.Debug(TAG, R.string.write_pwd);
        tag.writePage(0x85, password);
    }

    static void writeLockInfo(N2Elite tag) throws IOException {
        byte[] pages = tag.readPages(0);

        if (pages == null || pages.length != TagUtil.PAGE_SIZE * 4)
            throw new IOException(TagMo.getStringRes(R.string.read_failed));

        tag.writePage(2, new byte[]{pages[2 * TagUtil.PAGE_SIZE],
                pages[(2 * TagUtil.PAGE_SIZE) + 1], (byte) 0x0F, (byte) 0xE0}); // lock bits
        tag.writePage(130, new byte[]{(byte) 0x01, (byte) 0x00, (byte) 0x0F, (byte) 0x00});
        // dynamic lock bits. should the last bit be 0xBD according to the nfc docs though:
        // Remark: Set all bits marked with RFUI to 0, when writing to the dynamic lock bytes.
        tag.writePage(131, new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x04}); // config
        tag.writePage(132, new byte[]{(byte) 0x5F, (byte) 0x00, (byte) 0x00, (byte) 0x00}); // config
    }

    static void doAuth(N2Elite tag) throws Exception {
        byte[] pages0_1 = tag.readPages(0);

        if (pages0_1 == null || pages0_1.length != TagUtil.PAGE_SIZE * 4)
            throw new Exception(TagMo.getStringRes(R.string.read_failed));

        byte[] uid = TagUtil.uidFromPages(pages0_1);
        byte[] password = TagUtil.keygen(uid);

        TagMo.Debug(TAG, R.string.password, Util.bytesToHex(password));

        byte[] auth = new byte[]{
                (byte) 0x1B,
                password[0],
                password[1],
                password[2],
                password[3]
        };
        byte[] response = tag.transceive(auth);
        if (response == null)
            throw new Exception(TagMo.getStringRes(R.string.auth_null));
        String respStr = Util.bytesToHex(response);
        TagMo.Error(TAG, R.string.auth_response, respStr);
        if (!"8080".equals(respStr)) {
            throw new Exception(TagMo.getStringRes(R.string.auth_failed));
        }
    }

}
