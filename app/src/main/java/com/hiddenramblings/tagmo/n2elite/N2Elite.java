package com.hiddenramblings.tagmo.n2elite;

import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.NfcA;
import android.nfc.tech.TagTechnology;

import java.io.IOException;

public class N2Elite implements TagTechnology {
    private final MifareUltralight m_mifare;
    private final NfcA m_nfcA;

    public static final int PAGE_SIZE = 4;

    private static final int NXP_MANUFACTURER_ID = 0x04;
    private static final int MAX_PAGE_COUNT = 256;

    public static final int CMD_GET_VERSION = 0x60;
    public static final int CMD_READ = 0x30;
    public static final int CMD_FAST_READ = 0x3A;
    public static final int CMD_WRITE = 0xA2;
    public static final int CMD_COMP_WRITE = 0xA0;
    public static final int CMD_READ_CNT = 0x39;
    public static final int CMD_PWD_AUTH = 0x1B;
    public static final int CMD_READ_SIG = 0x3C;

    // N2 Elite
    public static final byte N2_SELECT_BANK = (byte) 0xA7;
    public static final byte N2_FAST_READ = 0x3B;
    public static final byte N2_FAST_WRITE = (byte) 0xAE;
    public static final byte AMIIBO_GET_VERSION = 0x55; // N2_GET_INFO
    public static final byte N2_LOCK = 0x46;
    public static final byte AMIIBO_READ_SIG = 0x43;// N2_GET_ID
    public static final byte N2_SET_BANK_CNT = (byte) 0xA9;
    public static final byte N2_UNLOCK1 = 0x44;
    public static final byte N2_UNLOCK2 = 0x45;
    public static final byte N2_WRITE = (byte) 0xA5;

    public N2Elite(MifareUltralight mifare) {
        m_nfcA = null;
        m_mifare = mifare;
    }

    public N2Elite(NfcA nfcA) {
        m_nfcA = nfcA;
        m_mifare = null;
        maxTransceiveLength = m_nfcA.getMaxTransceiveLength();
    }

    public static N2Elite get(Tag tag) {
        MifareUltralight mifare = MifareUltralight.get(tag);
        if (mifare != null)
            return new N2Elite(mifare);
        NfcA nfcA = NfcA.get(tag);
        if (nfcA != null) {
            if (nfcA.getSak() == 0x00 && tag.getId()[0] == NXP_MANUFACTURER_ID)
                return new N2Elite(nfcA);
        }

        return null;
    }

    public byte[] readPages(int pageOffset) throws IOException {
        if (m_mifare != null)
            return m_mifare.readPages(pageOffset);
        else if (m_nfcA != null) {
            validatePageIndex(pageOffset);
            //checkConnected();

            byte[] cmd = {CMD_READ, (byte) pageOffset};
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
            cmd[0] = (byte) CMD_WRITE;
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
        if (pageIndex < 0 || pageIndex >= MAX_PAGE_COUNT) {
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

    @Override
    public boolean isConnected() {
        return m_nfcA.isConnected();
    }

    public Tag getTag() {
        if (m_mifare != null) {
            return m_mifare.getTag();
        } else if (m_nfcA != null) {
            return m_nfcA.getTag();
        }
        return null;
    }

    /* byte 1: currently active slot
    /* byte 2: number of active banks
    /* byte 3: button pressed?
    /* byte 4: FW version?
    // see: http://wiki.yobi.be/wiki/N2_Elite#0x55:_N2_GET_INFO
    */
    public byte[] amiiboGetVersion() {
        byte[] req = new byte [1];
        byte[] resp;

        req[0] = AMIIBO_GET_VERSION;

        try {
            resp = transceive(req);
        } catch (IOException ex) {
            resp = null;
        }
        return resp;
    }

    public byte[] amiiboReadSig() {
        try {
            return this.transceive(new byte[]{AMIIBO_READ_SIG});
        } catch (Exception unused) {
            return null;
        }
    }

    public byte[] amiiboSetBankcount(int i) {
        try {
            return this.transceive(new byte[]{N2_SET_BANK_CNT, (byte) (i & 0xFF)});
        } catch (Exception unused) {
            return null;
        }
    }

    public byte[] amiiboActivateBank(int i) {
        try {
            return this.transceive(new byte[]{N2_SELECT_BANK, (byte) (i & 0xFF)});
        } catch (Exception unused) {
            return null;
        }
    }

    public interface IFastRead {
        byte[] doFastRead(int startAddr, int endAddr, int bank);
    }

    public byte[] amiiboFastRead(int startAddr, int endAddr, int bank) {
        return internalFastRead(new IFastRead() {
            @Override
            public byte[] doFastRead(int startAddr, int endAddr, int bank) {
                try {
                    return transceive(new byte[] {
                            N2_FAST_READ,
                            (byte)(startAddr & 0xFF),
                            (byte)(endAddr & 0xFF),
                            (byte) (bank & 0xFF)
                    });
                } catch (Exception unused) {
                    return null;
                }
            }
        }, startAddr, endAddr, bank);
    }

    private byte[] internalFastRead(IFastRead iFastRead, int startAddr, int endAddr, int bank) {
        if (endAddr < startAddr)
            return null;

        boolean bOk = true;
        byte[] resp = new byte[4 * (endAddr - startAddr + 1)];
        int maxReadLength = (maxTransceiveLength / 4) - 1;
        if (maxReadLength < 1)
            return null;
        int iNumReads = 1 + (endAddr - startAddr + 1) / maxReadLength;
        int i = 0;

        while ((i < iNumReads) && bOk)
        {
            int endSnippet;
            int startSnippet = startAddr + i * maxReadLength;
            if (i == iNumReads - 1)
                endSnippet = endAddr;
            else
                endSnippet = startSnippet + maxReadLength - 1;

            byte[] respSnippet = iFastRead.doFastRead(startSnippet, endSnippet, bank);
            if (respSnippet == null)
            {
                bOk = false;
            }
            else
            {
                if (respSnippet.length != 4 * (endSnippet - startSnippet + 1))
                {
                    bOk = false;
                }
                else
                {
                    System.arraycopy(respSnippet, 0, resp, i * maxReadLength, respSnippet.length);
                }
            }
            i++;
        }
        if (bOk)
        {
            return resp;
        }
        return null;
    }

    public interface IFastWrite {
        boolean doFastWrite(int i, int i2, byte[] bArr);
    }

    private boolean internalWrite(IFastWrite iFastWrite, int i, int i2, byte[] bArr) {
        byte[] bArr2 = new byte[4];
        int length = bArr.length / 4;
        for (int i3 = 0; i3 < length; i3++) {
            System.arraycopy(bArr, i3 * 4, bArr2, 0, 4);
            if (!iFastWrite.doFastWrite(i + i3, i2, bArr2)) {
                return false;
            }
        }
        return true;
    }

    public boolean amiiboWrite(int i, int i2, byte[] bArr) {
        if (bArr != null && bArr.length % 4 == 0) {
            return internalWrite(new IFastWrite() {
                @Override
                public boolean doFastWrite(int i, int i2, byte[] bArr) {
                    byte[] bArr2 = new byte[7];
                    bArr2[0] = N2_WRITE;
                    bArr2[1] = (byte) (i & 0xFF);
                    bArr2[2] = (byte) (i2 & 0xFF);
                    try {
                        System.arraycopy(bArr, 0, bArr2, 3, 4);
                        transceive(bArr2);
                        return true;
                    } catch (Exception unused) {
                        return false;
                    }
                }
            }, i, i2, bArr);
        }
        return false;
    }

    private boolean internalFastWrite(IFastWrite iFastWrite, int i, int i2, byte[] bArr) {
        int length = (bArr.length / 4) + i;
        int i3 = 16;
        int i4 = 0;
        while (i <= length) {
            int i5 = i + 4;
            if (i5 >= length) {
                i3 = bArr.length % i3;
            }
            if (i3 == 0) {
                return true;
            }
            byte[] bArr2 = new byte[i3];
            System.arraycopy(bArr, i4, bArr2, 0, i3);
            if (!iFastWrite.doFastWrite(i, i2, bArr2)) {
                return false;
            }
            i4 += i3;
            i = i5;
        }
        return true;
    }

    public boolean amiiboFastWrite(int i, int i2, byte[] bArr) {
        if (bArr == null) {
            return false;
        }
        return internalFastWrite(new IFastWrite() {
            @Override
            public boolean doFastWrite(int i, int i2, byte[] bArr) {
                byte[] bArr2 = new byte[(bArr.length + 4)];
                bArr2[0] = N2_FAST_WRITE;
                bArr2[1] = (byte) (i & 0xFF);
                bArr2[2] = (byte) (i2 & 0xFF);
                bArr2[3] = (byte) (bArr.length & 0xFF);
                try {
                    System.arraycopy(bArr, 0, bArr2, 4, bArr.length);
                    transceive(bArr2);
                    return true;
                } catch (Exception unused) {
                    return false;
                }
            }
        }, i, i2, bArr);
    }

    private int maxTransceiveLength;
}
