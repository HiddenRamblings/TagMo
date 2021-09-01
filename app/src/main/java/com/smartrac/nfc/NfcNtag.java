package com.smartrac.nfc;

/*
 * *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*
 * SMARTRAC SDK for Android NFC NTAG
 * ===============================================================================
 * Copyright (C) 2016 SMARTRAC TECHNOLOGY GROUP
 * ===============================================================================
 * SMARTRAC SDK
 * (C) Copyright 2016, Smartrac Technology Fletcher, Inc.
 * 267 Cane Creek Rd, Fletcher, NC, 28732, USA
 * All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#
 */


import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.nfc.tech.TagTechnology;

import java.io.IOException;

public class NfcNtag implements TagTechnology {

    // Static constructor using the android.nfc.Tag object
    public static NfcNtag get(Tag tag) {
        return new NfcNtag(tag);
    }

    public NfcNtag(Tag tag) {
        nfca = NfcA.get(tag);
        maxTransceiveLength = nfca.getMaxTransceiveLength();
    }

    public void connect() throws IOException {
        nfca.connect();
    }

    public void close() throws IOException {
        nfca.close();
    }
    
    public int getMaxTransceiveLength() {
        return maxTransceiveLength;
    }
    
    public Tag getTag() {
        return nfca.getTag();
    }
    
    public boolean isConnected() {
        return nfca.isConnected();
    }

    // NTAG GET_VERSION
    public byte[] getVersion() {
        byte[] req = new byte [1];
        byte[] resp;

        req[0] = NfcNtagOpcode.GET_VERSION;

        try {
            resp = nfca.transceive(req);
        } catch (IOException ex) {
            resp = null;
        }
        return resp;
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

        req[0] = NfcNtagOpcode.AMIIBO_GET_VERSION;

        try {
            resp = nfca.transceive(req);
        } catch (IOException ex) {
            resp = null;
        }
        return resp;
    }

    // NTAG READ
    public byte[] read(int addr) {
        byte[] req = new byte[2];
        byte[] resp;

        req[0] = NfcNtagOpcode.READ;
        req[1] = (byte)(addr & 0xFF);

        try {
            resp = nfca.transceive(req);
        } catch (IOException ex) {
            resp = null;
        }
        return resp;
    }

    public interface IFastRead {
        byte[] doFastRead(int startAddr, int endAddr, int bank);
    }

    // NTAG FAST_READ
    public byte[] fastRead(int startAddr, int endAddr) {
        return internalFastRead(new IFastRead() {
            @Override
            public byte[] doFastRead(int startAddr, int endAddr, int bank) {
                try {
                    return nfca.transceive(new byte[]{NfcNtagOpcode.FAST_READ, (byte)(startAddr & 0xFF), (byte)(endAddr & 0xFF)});
                } catch (Exception unused) {
                    return null;
                }
            }
        }, startAddr, endAddr, 0);

    }
    
    private byte[] amiiboFastRead(int startAddr, int endAddr, int bank) {
        return internalFastRead(new IFastRead() {
            @Override
            public byte[] doFastRead(int startAddr, int endAddr, int bank) {
                try {
                    return nfca.transceive(new byte[]{NfcNtagOpcode.N2_FAST_READ, (byte)(startAddr & 0xFF), (byte)(endAddr & 0xFF), (byte) (bank & 0xFF)});
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

    // NTAG WRITE
    public boolean write(int addr, byte[] data) {

        if (data == null) {
            return false;
        }
        if (data.length != 4) {
            return false;
        }

        byte[] req = new byte[6];
        req[0] = NfcNtagOpcode.WRITE;
        req[1] = (byte)(addr & 0xFF);

        try {
            System.arraycopy(data, 0, req, 2, 4);
            nfca.transceive(req);
        } catch (IOException ex) {
            return false;
        }
        return true;
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
                @Override // com.smartrac.nfc.NfcNtag.IFastWrite
                public boolean doFastWrite(int i, int i2, byte[] bArr) {
                    byte[] bArr2 = new byte[7];
                    bArr2[0] = NfcNtagOpcode.N2_WRITE;
                    bArr2[1] = (byte) (i & 0xFF);
                    bArr2[2] = (byte) (i2 & 0xFF);
                    try {
                        System.arraycopy(bArr, 0, bArr2, 3, 4);
                        NfcNtag.this.nfca.transceive(bArr2);
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
            @Override // com.smartrac.nfc.NfcNtag.IFastWrite
            public boolean doFastWrite(int i, int i2, byte[] bArr) {
                byte[] bArr2 = new byte[(bArr.length + 4)];
                bArr2[0] = NfcNtagOpcode.N2_FAST_WRITE;
                bArr2[1] = (byte) (i & 0xFF);
                bArr2[2] = (byte) (i2 & 0xFF);
                bArr2[3] = (byte) (bArr.length & 0xFF);
                try {
                    System.arraycopy(bArr, 0, bArr2, 4, bArr.length);
                    NfcNtag.this.nfca.transceive(bArr2);
                    return true;
                } catch (Exception unused) {
                    return false;
                }
            }
        }, i, i2, bArr);
    }

    // NTAG READ_CNT
    public int readCnt() {
        byte[] req = new byte[2];
        byte[] resp;
        int cnt;

        req[0] = NfcNtagOpcode.READ_CNT;
        req[1] = 0x02;

        try {
            resp = nfca.transceive(req);
            cnt = resp[0] + resp[1] * 256 + resp[2] * 65536;
        } catch (IOException ex) {
            return -1;
        }
        return cnt;
    }

    // NTAG PWD_AUTH
    public byte[] pwdAuth(byte[] password) {

        if (password == null) {
            return null;
        }
        if (password.length != 4) {
            return null;
        }

        byte[] req = new byte[5];
        byte[] resp;

        req[0] = NfcNtagOpcode.PWD_AUTH;

        try {
            System.arraycopy(password, 0, req, 1, 4);
            resp = nfca.transceive(req);
        } catch (IOException ex) {
            resp = null;
        }
        return resp;	// result will be the PACK
    }

    // NTAG READ_SIG
    public byte[] readSig() {
        byte[] req = new byte[2];
        byte[] resp;

        req[0] = NfcNtagOpcode.READ_SIG;
        req[1] = 0x00;

        try {
            resp = nfca.transceive(req);
        } catch (IOException ex) {
            resp = null;
        }
        return resp;	// result will be the NTAG signature
    }

    public byte[] amiiboReadSig() {
        try {
            return this.nfca.transceive(new byte[]{NfcNtagOpcode.AMIIBO_READ_SIG});
        } catch (Exception unused) {
            return null;
        }
    }

    // NTAG SECTOR_SELECT
    public boolean sectorSelect(byte sector) {
        byte[] req1 = new byte[2];
        byte[] req2 = new byte[4];

        req1[0] = NfcNtagOpcode.SECTOR_SELECT;
        req1[1] = (byte)0xFF;

        try {
            nfca.transceive(req1);
        } catch (IOException ex) {
            return false;
        }

        req2[0] = sector;
        req2[1] = 0x00;

        // The second part of this command works with negative acknowledge:
        // If the tag does not respond, the command worked OK.
        try {
            nfca.transceive(req2);
        } catch (IOException ex) {
            return true;
        }
        return false;
    }

    // NTAG READ_TT_STATUS
    public NfcNtagTtStatus readTtStatus() {
        byte[] req = new byte[2];
        byte[] resp;

        req[0] = NfcNtagOpcode.READ_TT_STATUS;
        req[1] = 0x00;

        try {
            resp = nfca.transceive(req);
        } catch (IOException ex) {
            return null;
        }

        NfcNtagTtStatus ttStatus;
        try {
            ttStatus = new NfcNtagTtStatus(resp);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        return ttStatus;	// result will be the NTAG signature
    }


    // MF UL-C AUTHENTICATE part 1
    public byte[] mfulcAuth1() {
        byte[] req = new byte[2];
        byte[] resp;

        req[0] = NfcNtagOpcode.MFULC_AUTH1;
        req[1] = 0x00;

        try {
            resp = nfca.transceive(req);
        } catch (IOException ex) {
            resp = null;
        }
        return resp;	// result will be "AFh" + ekRndB
    }

    // MF UL-C AUTHENTICATE part 2
    public byte[] mfulcAuth2(byte[] ekRndAB) {
        if (ekRndAB == null) {
            return null;
        }
        if (ekRndAB.length != 16) {
            return null;
        }

        byte[] req = new byte[17];
        byte[] resp;

        req[0] = NfcNtagOpcode.MFULC_AUTH2;

        try {
            System.arraycopy(ekRndAB, 0, req, 1, 16);
            resp = nfca.transceive(req);
        } catch (IOException ex) {
            return null;
        }
        return resp;	// result will be ekRndA'
    }

    public byte[] amiiboSetBankcount(int i) {
        try {
            return this.nfca.transceive(new byte[]{NfcNtagOpcode.N2_SET_BANK_CNT, (byte) (i & 0xFF)});
        } catch (Exception unused) {
            return null;
        }
    }

    public byte[] amiiboActivateBank(int i) {
        try {
            return this.nfca.transceive(new byte[]{NfcNtagOpcode.N2_SELECT_BANK, (byte) (i & 0xFF)});
        } catch (Exception unused) {
            return null;
        }
    }

    private NfcA nfca;
    private int maxTransceiveLength;
}
