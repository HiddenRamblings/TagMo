package com.hiddenramblings.tagmo.amiibo.tagdata;

import java.io.IOException;

public class AppDataTP extends AppData {
    static final int LEVEL_MIN_VALUE = 0;
    static final int LEVEL_MAX_VALUE = 40;
    static final int LEVEL_OFFSET = 0x0;
    static final int HEARTS_MIN_VALUE = 0;
    public static final int HEARTS_MAX_VALUE = 20 * 4;
    static final int HEARTS_OFFSET = LEVEL_OFFSET + 0x01;

    public AppDataTP(byte[] appData) throws IOException {
        super(appData);
    }

    public void checkLevel(int value) throws NumberFormatException {
        if (value < LEVEL_MIN_VALUE || value > LEVEL_MAX_VALUE)
            throw new NumberFormatException();
    }

    public int getLevel() throws NumberFormatException {
        int value = appData.get(LEVEL_OFFSET) & 0xFF;
        checkLevel(value);
        return value;
    }

    public void setLevel(int value) throws NumberFormatException {
        checkLevel(value);
        appData.put(LEVEL_OFFSET, (byte) value);
    }

    public void checkHearts(int value) throws NumberFormatException {
        if (value < HEARTS_MIN_VALUE || value > HEARTS_MAX_VALUE)
            throw new NumberFormatException();
    }

    public int getHearts() throws NumberFormatException {
        int value = appData.get(HEARTS_OFFSET) & 0xFF;
        checkHearts(value);
        return value;
    }

    public void setHearts(int value) throws NumberFormatException {
        checkHearts(value);
        appData.put(HEARTS_OFFSET, (byte) value);
    }
}