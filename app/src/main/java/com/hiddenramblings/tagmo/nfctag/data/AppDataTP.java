package com.hiddenramblings.tagmo.nfctag.data;

public class AppDataTP extends AppData {
    public static final int LEVEL_MIN_VALUE = 0;
    public static final int LEVEL_MAX_VALUE = 40;
    public static final int LEVEL_OFFSET = 0x0;
    public static final int HEARTS_MIN_VALUE = 0;
    public static final int HEARTS_MAX_VALUE = 20 * 4;
    public static final int HEARTS_OFFSET = LEVEL_OFFSET + 0x01;

    public AppDataTP(byte[] appData) throws Exception {
        super(appData);
    }

    public void checkLevel(int value) throws Exception {
        if (value < LEVEL_MIN_VALUE || value > LEVEL_MAX_VALUE)
            throw new Exception();
    }

    public int getLevel() throws Exception {
        int value = appData.get(LEVEL_OFFSET) & 0xFF;
        checkLevel(value);
        return value;
    }

    public void setLevel(int value) throws Exception {
        checkLevel(value);
        appData.put(LEVEL_OFFSET, (byte) value);
    }

    public void checkHearts(int value) throws Exception {
        if (value < HEARTS_MIN_VALUE || value > HEARTS_MAX_VALUE)
            throw new Exception();
    }

    public int getHearts() throws Exception {
        int value = appData.get(HEARTS_OFFSET) & 0xFF;
        checkHearts(value);
        return value;
    }

    public void setHearts(int value) throws Exception {
        checkHearts(value);
        appData.put(HEARTS_OFFSET, (byte) value);
    }
}