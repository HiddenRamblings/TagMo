/*
 * ====================================================================
 * libamiibo Copyright (C) 2018 Benjamin Krämer
 * Data/Settings/AppData/Games/MarioLuigiSuperstarSaga.cs
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */

package com.hiddenramblings.tagmo.amiibo.tagdata;

import java.io.IOException;

public class AppDataMLSuperstarSaga extends AppData {

    static final int EXPERIENCE_MIN_VALUE = 0x000001;
    static final int EXPERIENCE_MAX_VALUE = 0x1D892C;
    static final int EXPERIENCE_OFFSET = 0x10;

    public AppDataMLSuperstarSaga(byte[] appData) throws IOException {
        super(appData);
    }

    public void checkExperience(int value) throws NumberFormatException {
        if (value < EXPERIENCE_MIN_VALUE || value > EXPERIENCE_MAX_VALUE)
            throw new NumberFormatException();
    }

    public int getExperience() throws NumberFormatException {
        int value = appData.getShort(EXPERIENCE_OFFSET) & 0xFFFFFF;
        checkExperience(value);
        return value;
    }

    public void setExperience(int value) throws NumberFormatException {
        checkExperience(value);
        appData.putShort(EXPERIENCE_OFFSET, (short) value);
    }

    private static final int[] LEVEL_THRESHOLDS = new int[] {
            0x000001, 0x000002, 0x000004, 0x000006, 0x00000A, 0x000014, 0x000028, 0x000046, 0x00006E,
            0x0000A0, 0x0000DC, 0x000122, 0x000172, 0x0001CC, 0x000230, 0x0002F8, 0x0003E8, 0x00054B,
            0x0006DB, 0x0008BB, 0x000AEB, 0x000D6B, 0x00100E, 0x0012FC, 0x0018D8, 0x0021A2, 0x002D5A,
            0x003C00, 0x004D94, 0x006216, 0x007986, 0x009768, 0x00B89C, 0x00E074, 0x010EF0, 0x014410,
            0x017FD4, 0x01C23C, 0x0211EC, 0x02871C, 0x02FC4C, 0x03717C, 0x03E6AC, 0x045BDC, 0x04D10C,
            0x05463C, 0x05BB6C, 0x06309C, 0x06A5CC, 0x071AFC, 0x07902C, 0x08055C, 0x087A8C, 0x08EFBC,
            0x0964EC, 0x09DA1C, 0x0A4F4C, 0x0AC47C, 0x0B39AC, 0x0BAEDC, 0x0C240C, 0x0C993C, 0x0D0E6C,
            0x0D839C, 0x0DF8CC, 0x0E6DFC, 0x0EE32C, 0x0F585C, 0x0FCD8C, 0x1042BC, 0x10B7EC, 0x112D1C,
            0x11A24C, 0x12177C, 0x128CAC, 0x1301DC, 0x13770C, 0x13EC3C, 0x14616C, 0x14D69C, 0x154BCC,
            0x15C0FC, 0x16362C, 0x16AB5C, 0x17208C, 0x1795BC, 0x180AEC, 0x18801C, 0x18F54C, 0x196A7C,
            0x19DFAC, 0x1A54DC, 0x1ACA0C, 0x1B3F3C, 0x1BB46C, 0x1C299C, 0x1C9ECC, 0x1D13FC, 0x1D892C
    };

    public int getLevel() throws NumberFormatException {
        int value = getExperience();
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
            if (LEVEL_THRESHOLDS[i] <= value)
                return i + 1;
        }
        throw new NumberFormatException();
    }

    public void setLevel(int level) throws NumberFormatException {
        setExperience(LEVEL_THRESHOLDS[level - 1]);
    }
}