package com.hiddenramblings.tagmo.nfctech;

import android.nfc.FormatException;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;

import com.eightbit.io.Debug;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.data.AmiiboData;

import java.io.IOException;
import java.util.Arrays;

public class TagUtils {

    public static String getTagTechnology(Tag tag) {
        String type = TagMo.getStringRes(R.string.unknown_type);
        for (String tech : tag.getTechList()) {
            if (MifareClassic.class.getName().equals(tech)) {
                switch (MifareClassic.get(tag).getType()) {
                    default:
                    case MifareClassic.TYPE_CLASSIC:
                        type = TagMo.getStringRes(R.string.mifare_classic);
                        break;
                    case MifareClassic.TYPE_PLUS:
                        type = TagMo.getStringRes(R.string.mifare_plus);
                        break;
                    case MifareClassic.TYPE_PRO:
                        type = TagMo.getStringRes(R.string.mifare_pro);
                        break;
                }
                return type;
            } else if (MifareUltralight.class.getName().equals(tech)) {
                switch (MifareUltralight.get(tag).getType()) {
                    default:
                    case MifareUltralight.TYPE_ULTRALIGHT:
                        type = TagMo.getStringRes(R.string.mifare_ultralight);
                        break;
                    case MifareUltralight.TYPE_ULTRALIGHT_C:
                        type = TagMo.getStringRes(R.string.mifare_ultralight_c);
                        break;
                }
                return type;
            } else if (IsoDep.class.getName().equals(tech)) {
                return TagMo.getStringRes(R.string.isodep);
            } else if (Ndef.class.getName().equals(tech)) {
                return TagMo.getStringRes(R.string.ndef);
            } else if (NdefFormatable.class.getName().equals(tech)) {
                return TagMo.getStringRes(R.string.ndef_formatable);
            }
        }
        return type;
    }

    public static boolean isPowerTag(NTAG215 mifare) {
        if (TagMo.getPrefs().enablePowerTagSupport().get()) {
            try {
                if (TagUtils.compareRange(mifare.transceive(NfcByte.POWERTAG_SIG), NfcByte.POWERTAG_SIGNATURE,
                        0, NfcByte.POWERTAG_SIGNATURE.length))
                    return true;
            } catch (IOException e) {
                Debug.Log(e);
            }
        }
        return false;
    }

    public static boolean isElite(NTAG215 mifare) {
        if (TagMo.getPrefs().enableEliteSupport().get()) {
            byte[] signature = mifare.readEliteSingature();
            return signature != null && TagUtils.bytesToHex(signature).endsWith("FFFFFFFFFF");
        }
        return false;
    }

    static boolean compareRange(byte[] data, byte[] data2, int data2offset, int len) {
        for (int i = data2offset, j = 0; j < len; i++, j++) {
            if (data[j] != data2[i])
                return false;
        }
        return true;
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

    public static long hexToLong(String s) {
        long result = 0;
        for (int i = 0; i < s.length(); i++) {
            result = (result << 4) + ((long) Character.digit(s.charAt(i), 16));
        }
        return result;
    }

    static byte hexToByte(String hex) {
        byte ret = (byte) 0;
        byte hi = (byte) hex.charAt(0);
        byte lo = (byte) hex.charAt(1);
        if (hi >= NfcByte.CMD_READ && hi <= NfcByte.CMD_READ_CNT) {
            ret = (byte) (((hi - 0x30) << 4));
        } else if (hi >= (byte) 0x41 && hi <= NfcByte.N2_LOCK) {
            ret = (byte) ((((hi - 0x41) + 0x0A) << 4));
        } else if (hi >= (byte) 0x61 && hi <= (byte) 0x66) {
            ret = (byte) ((((hi - 0x61) + 0x0A) << 4));
        }
        if (lo >= NfcByte.CMD_READ && lo <= NfcByte.CMD_READ_CNT) {
            return (byte) ((lo - 0x30) | ret);
        }
        if (lo >= (byte) 0x41 && lo <= NfcByte.N2_LOCK) {
            return (byte) (((lo - 0x41) + 0x0A) | ret);
        }
        if (lo < (byte) 0x61 || lo > (byte) 0x66) {
            return ret;
        }
        return (byte) (((lo - 0x61) + 0x0A) | ret);
    }

    public static long amiiboIdFromTag(byte[] data) throws NumberFormatException, IOException {
        return new AmiiboData(data).getAmiiboID();
    }

    public static String amiiboIdToHex(long amiiboId) {
        return String.format("%016X", amiiboId);
    }

    static byte[][] splitPages(byte[] data) throws Exception {
        if (data.length < NfcByte.TAG_FILE_SIZE)
            throw new IOException(TagMo.getStringRes(R.string.invalid_tag_data));

        byte[][] pages = new byte[data.length / NfcByte.PAGE_SIZE][];
        for (int i = 0, j = 0; i < data.length; i += NfcByte.PAGE_SIZE, j++) {
            pages[j] = Arrays.copyOfRange(data, i, i + NfcByte.PAGE_SIZE);
        }
        return pages;
    }

    public static void validateData(byte[] data) throws Exception {
        byte[][] pages = TagUtils.splitPages(data);

        if (pages[0][0] != (byte) 0x04)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_prefix));

        if (pages[2][2] != (byte) 0x0F || pages[2][3] != (byte) 0xE0)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_lock));

        if (pages[3][0] != (byte) 0xF1 || pages[3][1] != (byte) 0x10
                || pages[3][2] != (byte) 0xFF || pages[3][3] != (byte) 0xEE)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_cc));

        if (pages[0x82][0] != (byte) 0x01 || pages[0x82][1] != (byte) 0x0 || pages[0x82][2] != (byte) 0x0F)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_dynamic));

        if (pages[0x83][0] != (byte) 0x0 || pages[0x83][1] != (byte) 0x0
                || pages[0x83][2] != (byte) 0x0 || pages[0x83][3] != (byte) 0x04)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_cfg_zero));

        if (pages[0x84][0] != (byte) 0x5F || pages[0x84][1] != (byte) 0x0
                || pages[0x84][2] != (byte) 0x0 || pages[0x84][3] != (byte) 0x00)
            throw new Exception(TagMo.getStringRes(R.string.invalid_tag_file, R.string.invalid_tag_cfg_one));
    }

    public static void validateNtag(NTAG215 mifare, byte[] tagData, boolean validateNtag) throws Exception {
        if (tagData == null)
            throw new IOException(TagMo.getStringRes(R.string.no_source_data));

        if (validateNtag) {
            try {
                byte[] versionInfo = mifare.transceive(new byte[]{(byte) 0x60});
                if (versionInfo == null || versionInfo.length != 8)
                    throw new Exception(TagMo.getStringRes(R.string.error_tag_version));
                if (versionInfo[0x02] != (byte) 0x04 || versionInfo[0x06] != (byte) 0x11)
                    throw new FormatException(TagMo.getStringRes(R.string.error_tag_format));
            } catch (Exception e) {
                Debug.Log(R.string.error_version, e);
                throw e;
            }
        }

        byte[] pages = mifare.readPages(0);
        if (pages == null || pages.length != NfcByte.PAGE_SIZE * 4)
            throw new Exception(TagMo.getStringRes(R.string.fail_read_size));

        if (!TagUtils.compareRange(pages, tagData, 0, 9))
            throw new Exception(TagMo.getStringRes(R.string.fail_mismatch_uid));

        Debug.Log(TagWriter.class, R.string.validation_success);
    }
}
