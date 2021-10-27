package com.hiddenramblings.tagmo.nfctag;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;

import java.io.IOException;

public class TagWriter {

    public static void writeToTagRaw(NTAG215 mifare, byte[] tagData, boolean validateNtag) throws Exception {
        TagReader.validate(mifare, tagData, validateNtag);
        TagReader.validateBlankTag(mifare);

        try {
            byte[][] pages = TagUtils.splitPages(tagData);
            writePages(mifare, 3, 129, pages);
            TagMo.Debug(TagWriter.class, R.string.data_write);
        } catch (Exception e) {
            throw new Exception(TagMo.getStringRes(R.string.data_write_error), e);
        }
        try {
            writePassword(mifare);
            TagMo.Debug(TagWriter.class, R.string.password_write);
        } catch (Exception e) {
            throw new Exception(TagMo.getStringRes(R.string.password_write_error), e);
        }
        try {
            writeLockInfo(mifare);
            TagMo.Debug(TagWriter.class, R.string.lock_write);
        } catch (Exception e) {
            throw new Exception(TagMo.getStringRes(R.string.lock_write_error), e);
        }
    }

    static void writePages(NTAG215 tag, int pagestart, int pageend, byte[][] data) throws IOException {
        for (int i = pagestart; i <= pageend; i++) {
            tag.writePage(i, data[i]);
            TagMo.Debug(TagWriter.class, R.string.write_page, String.valueOf(i));
        }
    }

    public static void writeToTagAuto(NTAG215 mifare, byte[] tagData, KeyManager keyManager,
                                      boolean validateNtag) throws Exception {
        byte[] idPages = mifare.readPages(0);
        if (idPages == null || idPages.length != NfcByte.PAGE_SIZE * 4)
            throw new Exception(TagMo.getStringRes(R.string.fail_read_size));

        boolean isPowerTag = TagUtils.isPowerTag(mifare);

        TagMo.Debug(TagWriter.class, R.string.power_tag_verify, String.valueOf(isPowerTag));

        tagData = TagUtils.decrypt(keyManager, tagData);
        if (isPowerTag) {
            // use a pre-determined static id for Power Tag
            tagData = TagUtils.patchUid(NfcByte.POWERTAG_IDPAGES, tagData);
        } else {
            tagData = TagUtils.patchUid(idPages, tagData);
        }
        tagData = TagUtils.encrypt(keyManager, tagData);

        TagMo.Debug(TagWriter.class, TagUtils.bytesToHex(tagData));

        if (!isPowerTag) {
            TagReader.validate(mifare, tagData, validateNtag);
            try {
                TagReader.validateBlankTag(mifare);
            } catch (Exception e) {
                if (TagUtils.isElite(mifare)) {
                    throw new Exception(TagMo.getStringRes(R.string.scan_elite_menu));
                } else {
                    throw new Exception(e.getMessage());
                }
            }
        }

        if (isPowerTag) {
            byte[] oldid = mifare.getTag().getId();
            if (oldid == null || oldid.length != 7)
                throw new Exception(TagMo.getStringRes(R.string.fail_read_uid));

            TagMo.Debug(TagWriter.class, R.string.old_uid, TagUtils.bytesToHex(oldid));

            byte[] page10 = mifare.readPages(0x10);
            TagMo.Debug(TagWriter.class, R.string.page_ten, TagUtils.bytesToHex(page10));

            String page10bytes = TagUtils.bytesToHex(new byte[]{page10[0], page10[3]});

            byte[] ptagKeySuffix = PowerTagManager.getPowerTagKey(oldid, page10bytes);
            byte[] ptagKey = TagUtils.hexToByteArray(NfcByte.POWERTAG_KEY);
            System.arraycopy(ptagKeySuffix, 0, ptagKey, 8, 8);

            TagMo.Debug(TagWriter.class, R.string.ptag_key, TagUtils.bytesToHex(ptagKey));

            mifare.transceive(NfcByte.COMP_WRITE_CMD);
            mifare.transceive(ptagKey);

            if (!(idPages[0] == (byte) 0xFF && idPages[1] == (byte) 0xFF))
                doAuth(mifare);
        }

        byte[][] pages = TagUtils.splitPages(tagData);
        if (isPowerTag) {
            byte[] zeropage = TagUtils.hexToByteArray("00000000");
            mifare.writePage(0x86, zeropage); //PACK
            writePages(mifare, 0x01, 0x84, pages);
            mifare.writePage(0x85, zeropage); //PWD
            mifare.writePage(0x00, pages[0]); //UID
            mifare.writePage(0x00, pages[0]); //UID
        } else {
            try {
                writePages(mifare, 3, 129, pages);
                TagMo.Debug(TagWriter.class, R.string.data_write);
            } catch (Exception e) {
                throw new Exception(TagMo.getStringRes(R.string.data_write_error), e);
            }
            try {
                writePassword(mifare);
                TagMo.Debug(TagWriter.class, R.string.password_write);
            } catch (Exception e) {
                throw new Exception(TagMo.getStringRes(R.string.password_write_error), e);
            }
            try {
                writeLockInfo(mifare);
                TagMo.Debug(TagWriter.class, R.string.lock_write);
            } catch (Exception e) {
                throw new Exception(TagMo.getStringRes(R.string.lock_write_error), e);
            }
        }
    }

    public static byte[] writeEliteAuto(NTAG215 tag, byte[] tagData, int active_bank) throws Exception {
        if (doEliteAuth(tag, tag.fastRead(0, 0))) {
            if (tag.amiiboFastWrite(0, active_bank, tagData)) {
                byte[] result = new byte[8];
                System.arraycopy(tagData, 84, result, 0, result.length);
                return result;
            } else {
                throw new Exception(TagMo.getStringRes(R.string.elite_write_error));
            }
        } else {
            throw new Exception(TagMo.getStringRes(R.string.elite_auth_error));
        }
    }

    public static void restoreTag(NTAG215 mifare, byte[] tagData, boolean ignoreUid, KeyManager keyManager, boolean validateNtag) throws Exception {
        if (!ignoreUid)
            TagReader.validate(mifare, tagData, validateNtag);
        else {
            byte[] liveData = TagReader.readFromTag(mifare);
            if (!TagUtils.compareRange(liveData, tagData, 0, 9)) {
                // restoring to different tag: transplant mii and appdata to livedata and re-encrypt

                liveData = TagUtils.decrypt(keyManager, liveData);
                tagData = TagUtils.decrypt(keyManager, tagData);

                System.arraycopy(tagData, 0x08, liveData, 0x08, 0x1B4 - 0x08);

                tagData = TagUtils.encrypt(keyManager, liveData);
            }
        }

        doAuth(mifare);
        byte[][] pages = TagUtils.splitPages(tagData);
        writePages(mifare, 4, 12, pages);
        writePages(mifare, 32, 129, pages);
    }

    /**
     * Returns the UID of a tag from first two pages of data (TagFormat)
     */
    public static byte[] uidFromPages(byte[] pages0_1) {
        //removes the checksum bytes from the first two pages of a tag to get the actual uid
        if (pages0_1.length < 8) return null;

        byte[] key = new byte[7];
        key[0] = pages0_1[0];
        key[1] = pages0_1[1];
        key[2] = pages0_1[2];
        key[3] = pages0_1[4];
        key[4] = pages0_1[5];
        key[5] = pages0_1[6];
        key[6] = pages0_1[7];
        return key;
    }

    @SuppressWarnings("ConstantConditions")
    static void doAuth(NTAG215 tag) throws Exception {
        byte[] pages0_1 = tag.readPages(0);

        if (pages0_1 == null || pages0_1.length != NfcByte.PAGE_SIZE * 4)
            throw new Exception(TagMo.getStringRes(R.string.read_failed));

        byte[] uid = uidFromPages(pages0_1);
        byte[] password = TagUtils.keygen(uid);

        TagMo.Debug(TagWriter.class, R.string.password, TagUtils.bytesToHex(password));

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
        String respStr = TagUtils.bytesToHex(response);
        TagMo.Error(TagWriter.class, R.string.auth_response, respStr);
        if (!"8080".equals(respStr)) {
            throw new Exception(TagMo.getStringRes(R.string.auth_failed));
        }
    }

    private static boolean doEliteAuth(NTAG215 tag, byte[] password) {
        if (password == null || password.length != 4) {
            return false;
        }
        byte[] req = new byte[5];
        req[0] = NfcByte.CMD_PWD_AUTH;
        try {
            System.arraycopy(password, 0, req, 1, 4);
            password = tag.transceive(req);
        } catch (Exception e) {
            return false;
        }
        if (password == null || password.length != 2) {
            return false;
        }
        return password[0] == Byte.MIN_VALUE && password[1] == Byte.MIN_VALUE;
    }

    @SuppressWarnings("ConstantConditions")
    static void writePassword(NTAG215 tag) throws IOException {
        byte[] pages0_1 = tag.readPages(0);

        if (pages0_1 == null || pages0_1.length != NfcByte.PAGE_SIZE * 4)
            throw new IOException(TagMo.getStringRes(R.string.read_failed));

        byte[] uid = uidFromPages(pages0_1);
        byte[] password = TagUtils.keygen(uid);

        TagMo.Debug(TagWriter.class, R.string.password, TagUtils.bytesToHex(password));

        TagMo.Debug(TagWriter.class, R.string.write_pack);
        tag.writePage(0x86, new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0, (byte) 0});

        TagMo.Debug(TagWriter.class, R.string.write_pwd);
        tag.writePage(0x85, password);
    }

    static void writeLockInfo(NTAG215 tag) throws IOException {
        byte[] pages = tag.readPages(0);

        if (pages == null || pages.length != NfcByte.PAGE_SIZE * 4)
            throw new IOException(TagMo.getStringRes(R.string.read_failed));

        tag.writePage(2, new byte[]{pages[2 * NfcByte.PAGE_SIZE],
                pages[(2 * NfcByte.PAGE_SIZE) + 1], (byte) 0x0F, (byte) 0xE0}); // lock bits
        tag.writePage(130, new byte[]{(byte) 0x01, (byte) 0x00, (byte) 0x0F, (byte) 0x00});
        // dynamic lock bits. should the last bit be 0xBD according to the nfc docs though:
        // Remark: Set all bits marked with RFUI to 0, when writing to the dynamic lock bytes.
        tag.writePage(131, new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x04}); // config
        tag.writePage(132, new byte[]{(byte) 0x5F, (byte) 0x00, (byte) 0x00, (byte) 0x00}); // config
    }

    public static byte[] wipeBankData(NTAG215 tag, int active_bank)  throws Exception {
        if (doEliteAuth(tag, tag.fastRead(0, 0))) {
            byte[] tagData = TagUtils.hexToByteArray(new String(
                    new char[1080]).replace("\u0000", "F"));
            if (tag.amiiboFastWrite(0, active_bank, tagData)) {
                byte[] result = new byte[8];
                System.arraycopy(tagData, 84, result, 0, result.length);
                return result;
            } else {
                throw new Exception(TagMo.getStringRes(R.string.elite_write_error));
            }
        } else {
            throw new Exception(TagMo.getStringRes(R.string.elite_write_error));
        }
    }
}
