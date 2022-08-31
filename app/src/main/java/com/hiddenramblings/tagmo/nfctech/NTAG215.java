package com.hiddenramblings.tagmo.nfctech;

import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.NfcA;
import android.nfc.tech.TagTechnology;

import com.hiddenramblings.tagmo.eightbit.io.Debug;

import java.io.IOException;

public class NTAG215 implements TagTechnology {

    private static final int NXP_MANUFACTURER_ID = 0x04;
    private static final int MAX_PAGE_COUNT = 256;

    private final MifareUltralight m_mifare;
    private final NfcA m_nfcA;
    private final int maxTransceiveLength;

    public NTAG215(NfcA nfcA) {
        m_nfcA = nfcA;
        m_mifare = null;
        maxTransceiveLength = (m_nfcA.getMaxTransceiveLength() / 4) + 1;
    }

    public NTAG215(MifareUltralight mifare) {
        m_nfcA = null;
        m_mifare = mifare;
        maxTransceiveLength = (m_mifare.getMaxTransceiveLength() / 4) + 1;
    }

    public static NTAG215 get(Tag tag) {
        NfcA nfcA = NfcA.get(tag);
        if (null != nfcA) {
            if (nfcA.getSak() == 0x00 && tag.getId()[0] == NXP_MANUFACTURER_ID)
                return new NTAG215(nfcA);
        }
        MifareUltralight mifare = MifareUltralight.get(tag);
        if (null != mifare)
            return new NTAG215(mifare);

        return null;
    }

    public static NTAG215 getBlind(Tag tag) {
        NfcA nfcA = NfcA.get(tag);
        if (null != nfcA) {
            return new NTAG215(nfcA);
        }
        return null;
    }

    @SuppressWarnings("unused")
    public int getTimeout() {
        if (null != m_mifare)
            return m_mifare.getTimeout();
        if (null != m_nfcA) {
            return m_nfcA.getTimeout();
        }
        return 0;
    }

    @SuppressWarnings("unused")
    public void setTimeout(int timeout) {
        if (null != m_mifare)
            m_mifare.setTimeout(timeout);
        if (null != m_nfcA) {
            m_nfcA.setTimeout(timeout);
        }
    }

    public byte[] transceive(byte[] data) {
        try {
            if (null != m_mifare) {
                return m_mifare.transceive(data);
            } else if (null != m_nfcA) {
                return m_nfcA.transceive(data);
            }
        } catch (IOException e) {
            Debug.Warn(e);
        }
        return null;
    }

    public byte[] readPages(int pageOffset) throws IOException {
        if (null != m_mifare)
            return m_mifare.readPages(pageOffset);
        else if (null != m_nfcA) {
            validatePageIndex(pageOffset);
            //checkConnected();

            byte[] cmd = {
                    NfcByte.CMD_READ,
                    (byte) pageOffset
            };
            return m_nfcA.transceive(cmd);
        }
        return null;
    }

    public void writePage(int pageOffset, byte[] data) throws IOException {
        if (null != m_mifare) {
            m_mifare.writePage(pageOffset, data);
        } else if (null != m_nfcA) {
            validatePageIndex(pageOffset);
            //m_nfcA.checkConnected();

            byte[] cmd = new byte[data.length + 2];
            cmd[0] = (byte) NfcByte.CMD_WRITE;
            cmd[1] = (byte) pageOffset;
            System.arraycopy(data, 0, cmd, 2, data.length);

            m_nfcA.transceive(cmd);
        }
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
        if (null != m_mifare) {
            m_mifare.connect();
        } else if (null != m_nfcA) {
            m_nfcA.connect();
        }
    }

    public void close() throws IOException {
        if (null != m_mifare) {
            m_mifare.close();
        } else if (null != m_nfcA) {
            m_nfcA.close();
        }
    }

    public Tag getTag() {
        if (null != m_mifare) {
            return m_mifare.getTag();
        } else if (null != m_nfcA) {
            return m_nfcA.getTag();
        }
        return null;
    }

    /*
     * byte 1: currently active slot
     * byte 2: number of active banks
     * byte 3: button pressed?
     * byte 4: FW version?
     * see: http://wiki.yobi.be/wiki/N2_Elite#0x55:_N2_GET_INFO
     */
    public byte[] getVersion(boolean isGeneric) {
        byte[] command = isGeneric
                ? new byte[]{ NfcByte.CMD_GET_VERSION }
                : new byte[]{ NfcByte.N2_GET_VERSION };
        return this.transceive(command);
    }

    @SuppressWarnings("unused")
    public byte[] getBankCount() {
        byte[] req = new byte [1];
        byte[] resp;

        req[0] = NfcByte.N2_BANK_COUNT;

        return this.transceive(req);
    }

    public byte[] readSignature(boolean isGeneric) {
        byte[] command = isGeneric
                ? new byte[] { NfcByte.CMD_READ_SIG, (byte) 0x00 }
                : new byte[]{ NfcByte.N2_READ_SIG };
        return this.transceive(command);
    }

    public void setBankCount(int count) {
        this.transceive(new byte[]{
                (byte) NfcByte.N2_SET_BANKCOUNT,
                (byte) (count & 0xFF)
        });
    }

    public void activateBank(int bank) {
        this.transceive(new byte[]{
                (byte) NfcByte.N2_ACTIVATE_BANK,
                (byte) (bank & 0xFF)
        });
    }

    public void initFirmware() {
        this.transceive(new byte[]{
                (byte) 0xFFF4, (byte) 0x49,
                (byte) 0xFF9B, (byte) 0xFF99,
                (byte) 0xFFC3, (byte) 0xFFDA,
                (byte) 0x57, (byte) 0x71,
                (byte) 0x0A, (byte) 0x64,
                (byte) 0x4A, (byte) 0xFF9E,
                (byte) 0xFFF8, (byte) NfcByte.CMD_WRITE,
                (byte) NfcByte.CMD_READ, (byte) 0xFFD9
        });
    }

    private interface IFastRead {
        byte[] doFastRead(int i, int i2, int i3);
    }

    private interface IFastWrite {
        boolean doFastWrite(int i, int i2, byte[] bArr);
    }

    public byte[] fastRead(int startAddr, int endAddr) {
        return internalFastRead((startAddr1, endAddr1, bank) -> this.transceive(new byte[]{
                NfcByte.CMD_FAST_READ,
                (byte) (startAddr1 & 0xFF),
                (byte) (endAddr1 & 0xFF)
        }), startAddr, endAddr, 0);
    }

    public byte[] amiiboFastRead(int startAddr, int endAddr, int bank) {
        return internalFastRead((startAddr1, endAddr1, bank1) -> this.transceive(new byte[]{
                NfcByte.N2_FAST_READ,
                (byte) (startAddr1 & 0xFF),
                (byte) (endAddr1 & 0xFF),
                (byte) (bank1 & 0xFF)
        }), startAddr, endAddr, bank);
    }

    private byte[] internalFastRead(IFastRead method, int startAddr, int endAddr, int bank) {
        if (endAddr < startAddr) {
            return null;
        }
        byte[] resp = new byte[(((endAddr - startAddr) + 1) * 4)];
        int maxReadLength = (maxTransceiveLength / 4) - 1;
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
            if (null == respSnippet) {
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
        if (null != data && data.length % 4 == 0) {
            return internalWrite((startAddr, bank1, data1) -> {
                byte[] req = new byte[7];
                req[0] = (byte) NfcByte.N2_WRITE;
                req[1] = (byte) (startAddr & 0xFF);
                req[2] = (byte) (bank1 & 0xFF);
                try {
                    System.arraycopy(data1, 0, req, 3, 4);
                    this.transceive(req);
                    return true;
                } catch (Exception e) {
                    return false;
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
        if (null == data) {
            return false;
        }
        return internalFastWrite((startAddr, bank1, data1) -> {
            byte[] req = new byte[(data1.length + 4)];
            req[0] = (byte) NfcByte.N2_FAST_WRITE;
            req[1] = (byte) (startAddr & 0xFF);
            req[2] = (byte) (bank1 & 0xFF);
            req[3] = (byte) (data1.length & 0xFF);
            try {
                System.arraycopy(data1, 0, req, 4, data1.length);
                this.transceive(req);
                return true;
            } catch (Exception e) {
                return false;
            }
        }, addr, bank, data);
    }

    public void amiiboLock() {
        this.transceive(new byte[]{
                NfcByte.N2_LOCK
        });
    }

    public byte[] amiiboPrepareUnlock() {
        return this.transceive(new byte[]{
                NfcByte.N2_UNLOCK_1
        });
    }

    public void amiiboUnlock() {
        this.transceive(new byte[]{
                NfcByte.N2_UNLOCK_2
        });
    }

    @Override
    public boolean isConnected() {
        return m_nfcA.isConnected();
    }
}
