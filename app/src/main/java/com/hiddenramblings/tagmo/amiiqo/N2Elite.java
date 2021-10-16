package com.hiddenramblings.tagmo.amiiqo;

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
    public static final byte N2_BANK_COUNT = 0x55;
    public static final byte N2_LOCK = 0x46;
    public static final byte N2_READ_SIG = 0x43;// N2_GET_ID
    public static final byte N2_SET_BANK_CNT = (byte) 0xA9;
    public static final byte N2_UNLOCK_1 = 0x44;
    public static final byte N2_UNLOCK_2 = 0x45;
    public static final byte N2_WRITE = (byte) 0xA5;

    public N2Elite(MifareUltralight mifare) {
        m_nfcA = null;
        m_mifare = mifare;
        maxTranscieveLength = m_mifare.getMaxTransceiveLength();
    }

    public N2Elite(NfcA nfcA) {
        m_nfcA = nfcA;
        m_mifare = null;
        maxTranscieveLength = m_nfcA.getMaxTransceiveLength();
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
    public byte[] getAmiiqoBankCount() {
        byte[] req = new byte [1];
        byte[] resp;

        req[0] = N2_BANK_COUNT;

        try {
            resp = transceive(req);
        } catch (IOException ex) {
            resp = null;
        }
        return resp;
    }

    public byte[] readAmiiqoSignature() {
        try {
            return this.transceive(new byte[]{
                    N2_READ_SIG
            });
        } catch (Exception unused) {
            return null;
        }
    }

    public byte[] setAmiiqoBankCount(int i) {
        try {
            return transceive(new byte[]{
                    N2_SET_BANK_CNT,
                    (byte) (i & 0xFF)
            });
        } catch (Exception unused) {
            return null;
        }
    }

    public byte[] activateAmiiqoBank(int i) {
        try {
            return transceive(new byte[]{
                    N2_SELECT_BANK,
                    (byte) (i & 0xFF)
            });
        } catch (Exception unused) {
            return null;
        }
    }

    public byte[] initAmiiqoApdu() {
        try {
            return transceive(new byte[]{
                    (byte) -12, (byte) 73, (byte) -101, (byte) -103,
                    (byte) -61, (byte) -38, (byte) 87, (byte) 113,
                    (byte) 10, (byte) 100, (byte) 74, (byte) -98,
                    (byte) -8, (byte) CMD_WRITE, (byte) CMD_READ, (byte) -39
            });
        } catch (Exception e) {
            return null;
        }
    }

    private interface IFastRead {
        byte[] doFastRead(int i, int i2, int i3);
    }

    private interface IFastWrite {
        boolean doFastWrite(int i, int i2, byte[] bArr);
    }

    public byte[] amiiboFastRead(int startAddr, int endAddr, int bank) {
        return internalFastRead(new IFastRead() {
            @Override
            public byte[] doFastRead(int startAddr, int endAddr, int bank) {
                try {
                    return transceive(new byte[]{
                            N2_FAST_READ,
                            (byte) (startAddr & 255),
                            (byte) (endAddr & 255),
                            (byte) (bank & 255)
                    });
                } catch (Exception e) {
                    return null;
                }
            }
        }, startAddr, endAddr, bank);
    }

    private byte[] internalFastRead(IFastRead method, int startAddr, int endAddr, int bank) {
        if (endAddr < startAddr) {
            return null;
        }
        byte[] resp = new byte[(((endAddr - startAddr) + 1) * 4)];
        int maxReadLength = (this.maxTranscieveLength / 4) - 1;
        if (maxReadLength < 1) {
            return null;
        }
        int snippetByteSize = maxReadLength * 4;
        int startSnippet = startAddr;
        int i = 0;
        while (startSnippet <= endAddr) {
            int endSnippet = (startSnippet + maxReadLength) - 1;
            if (endSnippet > endAddr) {
                endSnippet = endAddr;
            }
            byte[] respSnippet = method.doFastRead(startSnippet, endSnippet, bank);
            if (respSnippet == null) {
                return null;
            }
            if (respSnippet.length != ((endSnippet - startSnippet) + 1) * 4) {
                return null;
            }
            if (respSnippet.length == resp.length) {
                return respSnippet;
            }
            System.arraycopy(respSnippet, 0, resp, i * snippetByteSize, respSnippet.length);
            startSnippet += maxReadLength;
            i++;
        }
        return resp;
    }

    private boolean internalWrite(IFastWrite method, int addr, int bank, byte[] data) {
        byte[] query = new byte[4];
        int pages = data.length / 4;
        for (int i = 0; i < pages; i++) {
            System.arraycopy(data, i * 4, query, 0, 4);
            if (!method.doFastWrite(addr + i, bank, query)) {
                return false;
            }
        }
        return true;
    }

    public boolean amiiboWrite(int addr, int bank, byte[] data) {
        if (data != null && data.length % 4 == 0) {
            return internalWrite(new IFastWrite() {
                @Override
                public boolean doFastWrite(int startAddr, int bank, byte[] data) {
                    byte[] req = new byte[7];
                    req[0] = N2_WRITE;
                    req[1] = (byte) (startAddr & 255);
                    req[2] = (byte) (bank & 255);
                    try {
                        System.arraycopy(data, 0, req, 3, 4);
                        transceive(req);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
            }, addr, bank, data);
        }
        return false;
    }

    private boolean internalFastWrite(IFastWrite method, int startAddr, int bank, byte[] data) {
        int snippetByteSize = 16;
        int endAddr = startAddr + (data.length / 4);
        int startSnippet = startAddr;
        int i = 0;
        while (startSnippet <= endAddr) {
            if (startSnippet + 4 >= endAddr) {
                snippetByteSize = data.length % snippetByteSize;
            }
            if (snippetByteSize == 0) {
                return true;
            }
            byte[] query = new byte[snippetByteSize];
            System.arraycopy(data, i, query, 0, snippetByteSize);
            if (!method.doFastWrite(startSnippet, bank, query)) {
                return false;
            }
            startSnippet += 4;
            i += snippetByteSize;
        }
        return true;
    }

    public boolean amiiboFastWrite(int addr, int bank, byte[] data) {
        if (data == null) {
            return false;
        }
        return internalFastWrite(new IFastWrite() {
            @Override
            public boolean doFastWrite(int startAddr, int bank, byte[] data) {
                byte[] req = new byte[(data.length + 4)];
                req[0] = N2_FAST_WRITE;
                req[1] = (byte) (startAddr & 255);
                req[2] = (byte) (bank & 255);
                req[3] = (byte) (data.length & 255);
                try {
                    System.arraycopy(data, 0, req, 4, data.length);
                    transceive(req);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        }, addr, bank, data);
    }

    public byte[] amiiboLock() {
        try {
            return transceive(new byte[]{N2_LOCK});
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] amiiboPrepareUnlock() {
        try {
            return transceive(new byte[]{N2_UNLOCK_1});
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] amiiboUnlock() {
        try {
            return transceive(new byte[]{N2_UNLOCK_2});
        } catch (Exception e) {
            return null;
        }
    }

    private int maxTranscieveLength;
}
