/* ====================================================================
 * Copyright (c) 2012-2022 AbandonedCart.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * For the purpose of this license, the phrase "TagMo labels" shall
 * be used to refer to the labels "8-Bit Dream", "TwistedUmbrella",
 * "TagMo" and "AbandonedCart" and these labels should be considered
 * the equivalent of any usage of the aforementioned phrase.
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All materials mentioning features or use of this software and
 *    redistributions of any form whatsoever must display the following
 *    acknowledgment unless made available by tagged, public "commits":
 *    "This product includes software developed for TagMo by AbandonedCart"
 *
 * 4. The TagMo labels must not be used in any form to endorse or promote
 *    products derived from this software without prior written permission.
 *    For written permission, please contact enderinexiledc@gmail.com
 *
 * 5. Products derived from this software may not be called by the TagMo labels
 *    nor may these labels appear in their names or product information without
 *    prior written permission of AbandonedCart.
 *
 * THIS SOFTWARE IS PROVIDED BY AbandonedCart AND TagMo ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
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

import androidx.documentfile.provider.DocumentFile;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.amiibo.tagdata.AmiiboData;
import com.hiddenramblings.tagmo.eightbit.io.Debug;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

public class TagUtils {

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

    public static boolean isPowerTag(NTAG215 mifare) {
        if (TagMo.getPrefs().enable_power_tag_support().get()) {
            byte[] signature = mifare.transceive(NfcByte.POWERTAG_SIG);
            return null != signature && TagUtils.compareRange(signature,
                    NfcByte.POWERTAG_SIGNATURE, NfcByte.POWERTAG_SIGNATURE.length);
        }
        return false;
    }

    public static boolean isElite(NTAG215 mifare) {
        if (TagMo.getPrefs().enable_elite_support().get()) {
            byte[] signature = mifare.readSignature(false);
            byte[] page10 = TagUtils.hexToByteArray("FFFFFFFFFF");
            return null != signature && TagUtils.compareRange(signature, page10,
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

    static boolean compareRange(byte[] data, byte[] data2, int len) {
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
            throw new IOException(TagMo.getContext().getString(
                    R.string.invalid_data_size, data.length, NfcByte.TAG_FILE_SIZE));

        byte[][] pages = new byte[data.length / NfcByte.PAGE_SIZE][];
        for (int i = 0, j = 0; i < data.length; i += NfcByte.PAGE_SIZE, j++) {
            pages[j] = Arrays.copyOfRange(data, i, i + NfcByte.PAGE_SIZE);
        }
        return pages;
    }

    public static void validateData(byte[] data) throws Exception {
        final Context context = TagMo.getContext();
        byte[][] pages = TagUtils.splitPages(data);

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
                    throw new FormatException(context.getString(R.string.error_tag_format));
            } catch (Exception e) {
                Debug.Warn(R.string.error_version, e);
                throw e;
            }
        }

        byte[] pages = mifare.readPages(0);
        if (null == pages  || pages.length != NfcByte.PAGE_SIZE * 4)
            throw new Exception(context.getString(R.string.fail_read_size));

        if (!TagUtils.compareRange(pages, tagData, 9))
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
            long amiiboId = amiiboIdFromTag(tagData);
            String name = amiiboIdToHex(amiiboId);
            if (null != amiiboManager) {
                Amiibo amiibo = amiiboManager.amiibos.get(amiiboId);
                if (null != amiibo && null != amiibo.name) {
                    name = amiibo.name.replace(File.separatorChar, '-');
                }
            }

            byte[] uid = Arrays.copyOfRange(tagData, 0, 9);
            String uidHex = bytesToHex(uid);
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
            TagUtils.validateData(data);
            data = keyManager.decrypt(data);
        } catch (Exception e) {
            data = keyManager.encrypt(data);
            TagUtils.validateData(data);
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

    public static String writeBytesToFile(File destination, String name, byte[] tagData)
            throws IOException {
        File binFile = new File(destination, name);
        new FileOutputStream(binFile).write(tagData);
        try {
            MediaScannerConnection.scanFile(TagMo.getContext(),
                    new String[] { binFile.getAbsolutePath() }, null, null);
        } catch (Exception e) {
            Debug.Info(R.string.fail_media_scan, e);
        }
        return binFile.getAbsolutePath();
    }

    public static String writeBytesToDocument(
            Context context, DocumentFile destination, String name, byte[] tagData
    ) throws IOException {
        DocumentFile newFile = destination.createFile(context.getResources()
                .getStringArray(R.array.mimetype_bin)[0], name + ".bin");
        if (null != newFile) {
            context.getContentResolver().openOutputStream(newFile.getUri()).write(tagData);
        }
        return null != newFile ? newFile.getName() : null;
    }
}
