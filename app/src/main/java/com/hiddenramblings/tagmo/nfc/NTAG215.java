package com.hiddenramblings.tagmo.nfc;

import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.NfcA;

import java.io.IOException;

public class NTAG215 {
    private final MifareUltralight m_mifare;
    private final NfcA m_nfcA;

    public NTAG215(MifareUltralight mifare) {
        m_nfcA = null;
        m_mifare = mifare;
    }

    public NTAG215(NfcA nfcA) {
        m_nfcA = nfcA;
        m_mifare = null;
    }

    public static NTAG215 get(Tag tag) {
        MifareUltralight mifare = MifareUltralight.get(tag);
        if (mifare != null)
            return new NTAG215(mifare);
        NfcA nfcA = NfcA.get(tag);
        if (nfcA != null) {
            if (nfcA.getSak() == 0x00 && tag.getId()[0] == NfcCmd.NXP_MANUFACTURER_ID)
                return new NTAG215(nfcA);
        }

        return null;
    }

    public byte[] readPages(int pageOffset) throws IOException {
        if (m_mifare != null)
            return m_mifare.readPages(pageOffset);
        else if (m_nfcA != null) {
            validatePageIndex(pageOffset);
            //checkConnected();

            byte[] cmd = {
                    NfcCmd.CMD_READ,
                    (byte) pageOffset
            };
            return m_nfcA.transceive(cmd);
        }
        return null;
    }

    public void writePage(int pageOffset, byte[] data) throws IOException {
        if (m_mifare != null) {
            m_mifare.writePage(pageOffset, data);
        } else if (m_nfcA != null) {
            validatePageIndex(pageOffset);
            //m_nfcA.checkConnected();

            byte[] cmd = new byte[data.length + 2];
            cmd[0] = (byte) NfcCmd.CMD_WRITE;
            cmd[1] = (byte) pageOffset;
            System.arraycopy(data, 0, cmd, 2, data.length);

            m_nfcA.transceive(cmd);
        }
    }

    public byte[] transceive(byte[] data) throws IOException {
        if (m_mifare != null) {
            return m_mifare.transceive(data);
        } else if (m_nfcA != null) {
            return m_nfcA.transceive(data);
        }
        return null;
    }

    private static void validatePageIndex(int pageIndex) {
        // Do not be too strict on upper bounds checking, since some cards
        // may have more addressable memory than they report.
        // Note that issuing a command to an out-of-bounds block is safe - the
        // tag will wrap the read to an addressable area. This validation is a
        // helper to guard against obvious programming mistakes.
        if (pageIndex < 0 || pageIndex >= NfcCmd.MAX_PAGE_COUNT) {
            throw new IndexOutOfBoundsException("page out of bounds: " + pageIndex);
        }
    }

    public void connect() throws IOException {
        if (m_mifare != null) {
            m_mifare.connect();
        } else if (m_nfcA != null) {
            m_nfcA.connect();
        }
    }

    public void close() throws IOException {
        if (m_mifare != null) {
            m_mifare.close();
        } else if (m_nfcA != null) {
            m_nfcA.close();
        }
    }

    public Tag getTag() {
        if (m_mifare != null) {
            return m_mifare.getTag();
        } else if (m_nfcA != null) {
            return m_nfcA.getTag();
        }
        return null;
    }
}
