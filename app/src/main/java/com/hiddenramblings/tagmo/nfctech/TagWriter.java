package com.hiddenramblings.tagmo.nfctech;

import android.content.Context;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.amiibo.PowerTagManager;
import com.hiddenramblings.tagmo.eightbit.io.Debug;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

public class TagWriter {

    public static void writeToTagRaw(
            NTAG215 mifare, byte[] tagData, boolean validateNtag
    ) throws Exception {
        final Context context = TagMo.getContext();
        TagUtils.validateNtag(mifare, tagData, validateNtag);
        TagReader.validateBlankTag(mifare);

        try {
            byte[][] pages = TagUtils.splitPages(tagData);
            writePages(mifare, 3, 129, pages);
            Debug.Info(TagWriter.class, R.string.data_write);
        } catch (Exception e) {
            throw new Exception(context.getString(R.string.error_data_write), e);
        }
        try {
            writePassword(mifare);
            Debug.Info(TagWriter.class, R.string.password_write);
        } catch (Exception e) {
            throw new Exception(context.getString(R.string.error_password_write), e);
        }
        try {
            writeLockInfo(mifare);
            Debug.Info(TagWriter.class, R.string.lock_write);
        } catch (Exception e) {
            throw new Exception(context.getString(R.string.error_lock_write), e);
        }
    }

    private static void writePages(
            NTAG215 tag, int pagestart, int pageend, byte[][] data
    ) throws IOException {
        for (int i = pagestart; i <= pageend; i++) {
            tag.writePage(i, data[i]);
            Debug.Info(TagWriter.class, R.string.write_page, String.valueOf(i));
        }
    }

    private static byte[] patchUid(byte[] uid, byte[] tagData) throws Exception {
        if (uid.length < 9) throw new IOException(TagMo.getContext()
                .getString(R.string.invalid_uid_length));

        byte[] patched = Arrays.copyOf(tagData, tagData.length);
        System.arraycopy(uid, 0, patched, 0x1d4, 8);
        patched[0] = uid[8];
        return patched;
    }

    private static void writePasswordLockInfo(NTAG215 mifare) throws Exception {
        try {
            writePassword(mifare);
            Debug.Info(TagWriter.class, R.string.password_write);
        } catch (Exception e) {
            throw new Exception(TagMo.getContext()
                    .getString(R.string.error_password_write), e);
        }
        try {
            writeLockInfo(mifare);
            Debug.Info(TagWriter.class, R.string.lock_write);
        } catch (Exception e) {
            throw new Exception(TagMo.getContext()
                    .getString(R.string.error_lock_write), e);
        }
    }

    public static void writeToTagAuto(
            NTAG215 mifare, byte[] tagData, KeyManager keyManager, boolean validateNtag
    ) throws Exception {
        byte[] idPages = mifare.readPages(0);
        if (null == idPages  || idPages.length != NfcByte.PAGE_SIZE * 4)
            throw new IOException(TagMo.getContext()
                    .getString(R.string.fail_read_size));

        boolean isPowerTag = TagUtils.isPowerTag(mifare);
        Debug.Info(TagWriter.class, R.string.power_tag_verify, String.valueOf(isPowerTag));

        try {
            tagData = keyManager.decrypt(tagData);
        } catch (Exception e) {
            throw new Exception(e);
        }

        if (isPowerTag) {
            // use a pre-determined static id for Power Tag
            tagData = patchUid(NfcByte.POWERTAG_IDPAGES, tagData);
        } else {
            tagData = patchUid(idPages, tagData);
        }
        tagData = keyManager.encrypt(tagData);

        Debug.Info(TagWriter.class, TagUtils.bytesToHex(tagData));

        if (!isPowerTag) {
            TagUtils.validateNtag(mifare, tagData, validateNtag);
            try {
                TagReader.validateBlankTag(mifare);
            } catch (IOException e) {
                throw new IOException(e);
            }
        }

        if (isPowerTag) {
            byte[] oldid = mifare.getTag().getId();
            if (null == oldid  || oldid.length != 7)
                throw new Exception(TagMo.getContext()
                        .getString(R.string.fail_read_uid));

            Debug.Info(TagWriter.class, R.string.old_uid, TagUtils.bytesToHex(oldid));

            byte[] page10 = mifare.readPages(0x10);
            Debug.Info(TagWriter.class, R.string.page_ten, TagUtils.bytesToHex(page10));

            String page10bytes = TagUtils.bytesToHex(new byte[]{page10[0], page10[3]});

            byte[] ptagKeySuffix = PowerTagManager.getPowerTagKey(oldid, page10bytes);
            byte[] ptagKey = TagUtils.hexToByteArray(NfcByte.POWERTAG_KEY);
            System.arraycopy(ptagKeySuffix, 0, ptagKey, 8, 8);

            Debug.Info(TagWriter.class, R.string.ptag_key, TagUtils.bytesToHex(ptagKey));

            mifare.transceive(NfcByte.POWERTAG_WRITE);
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
                Debug.Info(TagWriter.class, R.string.data_write);
            } catch (Exception e) {
                throw new Exception(TagMo.getContext().getString(R.string.error_data_write), e);
            }
            writePasswordLockInfo(mifare);
        }
    }

    public static void writeEliteAuto(
            NTAG215 mifare, byte[] tagData, KeyManager keyManager, int active_bank
    ) throws Exception {
        if (doEliteAuth(mifare, mifare.fastRead(0, 0))) {
            tagData = keyManager.decrypt(tagData);
            // tagData = patchUid(mifare.readPages(0), tagData);
            tagData = keyManager.encrypt(tagData);
            boolean write = mifare.amiiboFastWrite(0, active_bank, tagData);
            if (!write) write = mifare.amiiboWrite(0, active_bank, tagData);
            if (!write) throw new IOException(TagMo.getContext()
                    .getString(R.string.error_elite_write));
        } else {
            throw new Exception(TagMo.getContext()
                    .getString(R.string.error_elite_auth));
        }
    }

    public static void restoreTag(
            NTAG215 mifare, byte[] tagData, boolean ignoreUid,
            KeyManager keyManager, boolean validateNtag
    ) throws Exception {
        if (!ignoreUid)
            TagUtils.validateNtag(mifare, tagData, validateNtag);
        else {
            byte[] liveData = TagReader.readFromTag(mifare);
            if (!TagUtils.compareRange(liveData, tagData, 9)) {
                // restoring to different tag: transplant mii and appdata to livedata and re-encrypt
                liveData = keyManager.decrypt(liveData);
                tagData = keyManager.decrypt(tagData);
                System.arraycopy(tagData, 0x08, liveData, 0x08, 0x1B4 - 0x08);
                /* TODO: Verify that 0x1B4 should not be 0x1D4 */
                tagData = keyManager.encrypt(liveData);
            }
        }

        doAuth(mifare);
        byte[][] pages = TagUtils.splitPages(tagData);
        writePages(mifare, 4, 12, pages);
        writePages(mifare, 32, 129, pages);
    }

    /**
     * Remove the checksum bytes from the first two pages to get the actual uid
     */
    private static byte[] uidFromPages(byte[] pages0_1) {
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

    private static byte[] keygen(byte[] uuid) {
        // from AmiiManage (GPL)
        byte[] key = new byte[4];
        int[] uuid_to_ints = new int[uuid.length];

        for (int i = 0; i < uuid.length; i++)
            uuid_to_ints[i] = (0xFF & uuid[i]);

        if (uuid.length == 7) {
            key[0] = ((byte) (0xFF & (0xAA ^ (uuid_to_ints[1] ^ uuid_to_ints[3]))));
            key[1] = ((byte) (0xFF & (0x55 ^ (uuid_to_ints[2] ^ uuid_to_ints[4]))));
            key[2] = ((byte) (0xFF & (0xAA ^ (uuid_to_ints[3] ^ uuid_to_ints[5]))));
            key[3] = ((byte) (0xFF & (0x55 ^ (uuid_to_ints[4] ^ uuid_to_ints[6]))));
            return key;
        }

        return null;
    }

    @SuppressWarnings("ConstantConditions")
    private static void doAuth(NTAG215 tag) throws Exception {
        byte[] pages0_1 = tag.readPages(0);

        if (null == pages0_1  || pages0_1.length != NfcByte.PAGE_SIZE * 4)
            throw new IOException(TagMo.getContext().getString(R.string.fail_read));

        byte[] uid = uidFromPages(pages0_1);
        byte[] password = keygen(uid);

        Debug.Info(TagWriter.class, R.string.password, TagUtils.bytesToHex(password));

        byte[] auth = new byte[]{
                (byte) 0x1B,
                password[0],
                password[1],
                password[2],
                password[3]
        };
        byte[] response = tag.transceive(auth);
        if (null == response )
            throw new Exception(TagMo.getContext().getString(R.string.error_auth_null));
        String respStr = TagUtils.bytesToHex(response);
        Debug.Info(TagWriter.class, R.string.auth_response, respStr);
        if (!"8080".equals(respStr)) {
            throw new Exception(TagMo.getContext().getString(R.string.fail_auth));
        }
    }

    private static boolean doEliteAuth(NTAG215 tag, byte[] password) {
        if (null == password  || password.length != 4) {
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
        if (null == password  || password.length != 2) {
            return false;
        }
        return password[0] == Byte.MIN_VALUE && password[1] == Byte.MIN_VALUE;
    }

    @SuppressWarnings("ConstantConditions")
    private static void writePassword(NTAG215 tag) throws IOException {
        byte[] pages0_1 = tag.readPages(0);

        if (null == pages0_1  || pages0_1.length != NfcByte.PAGE_SIZE * 4)
            throw new IOException(TagMo.getContext().getString(R.string.fail_read));

        byte[] uid = uidFromPages(pages0_1);
        byte[] password = keygen(uid);

        Debug.Info(TagWriter.class, R.string.password, TagUtils.bytesToHex(password));

        Debug.Info(TagWriter.class, R.string.write_pack);
        tag.writePage(0x86, new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0, (byte) 0});

        Debug.Info(TagWriter.class, R.string.write_pwd);
        tag.writePage(0x85, password);
    }

    private static void writeLockInfo(NTAG215 tag) throws IOException {
        byte[] pages = tag.readPages(0);

        if (null == pages  || pages.length != NfcByte.PAGE_SIZE * 4)
            throw new IOException(TagMo.getContext().getString(R.string.fail_read));

        tag.writePage(2, new byte[]{pages[2 * NfcByte.PAGE_SIZE],
                pages[(2 * NfcByte.PAGE_SIZE) + 1], (byte) 0x0F, (byte) 0xE0}); // lock bits
        tag.writePage(130, new byte[]{(byte) 0x01, (byte) 0x00, (byte) 0x0F, (byte) 0x00});
        // dynamic lock bits. should the last bit be 0xBD according to the nfc docs though:
        // Remark: Set all bits marked with RFUI to 0, when writing to the dynamic lock bytes.
        tag.writePage(131, new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x04}); // config
        tag.writePage(132, new byte[]{(byte) 0x5F, (byte) 0x00, (byte) 0x00, (byte) 0x00}); // config
    }

    public static void wipeBankData(NTAG215 mifare, int active_bank)  throws Exception {
        if (doEliteAuth(mifare, mifare.fastRead(0, 0))) {
            byte[] tagData = TagUtils.hexToByteArray(new String(
                    new char[1080]).replace("\u0000", "F"));
            boolean write = mifare.amiiboFastWrite(0, active_bank, tagData);
            if (!write) write = mifare.amiiboWrite(0, active_bank, tagData);
            if (write) {
                byte[] result = new byte[8];
                System.arraycopy(tagData, 84, result, 0, result.length);
                Debug.Info(TagWriter.class, TagUtils.bytesToHex(result));
            } else {
                throw new Exception(TagMo.getContext()
                        .getString(R.string.error_elite_write));
            }
        } else {
            throw new Exception(TagMo.getContext()
                    .getString(R.string.error_elite_write));
        }
    }

    public static boolean updateFirmware(NTAG215 tag) throws Exception {
        final Context context = TagMo.getContext();
        byte[] response = new byte[1];
        response[0] = (byte) 0xFFFF;
        tag.initFirmware();
        tag.getVersion(true);
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    context.getResources().openRawResource(R.raw.firmware)));
            while (true) {
                String strLine = br.readLine();
                if (null == strLine) {
                    break;
                }
                String[] parts = strLine.replaceAll("\\s+", " ").split(" ");
                int i;
                if (parts.length < 1) {
                    break;
                } else if (parts[0].equals("C-APDU")) {
                    byte[] apdu_buf = new byte[(parts.length - 1)];
                    for (i = 1; i < parts.length; i++) {
                        apdu_buf[i - 1] = TagUtils.hexToByte(parts[i]);
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
                            if (null != response) {
                                done = true;
                                break;
                            }
                        }
                        if (!done) {
                            throw new Exception(context.getString(R.string.firmware_failed, 1));
                        }
                    }
                    return false;
                } else if (parts[0].equals("C-RPDU")) {
                    byte[] rpdu_buf = new byte[(parts.length - 1)];
                    if (response.length != parts.length - 3) {
                        throw new Exception(context.getString(R.string.firmware_failed, 2));
                    }
                    for (i = 1; i < parts.length; i++) {
                        rpdu_buf[i - 1] = TagUtils.hexToByte(parts[i]);
                    }
                    for (i = 0; i < rpdu_buf.length - 2; i++) {
                        if (rpdu_buf[i] != response[i]) {
                            throw new Exception(context.getString(R.string.firmware_failed, 3));
                        }
                    }
                } /* else if (!parts[0].equals("RESET") && parts[0].equals("LOGIN")) { } */
            }
            br.close();
            return true;
        } catch (IOException e) {
            throw new Exception(context.getString(R.string.firmware_failed, 4));
        }
    }
}
