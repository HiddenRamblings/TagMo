package com.hiddenramblings.tagmo;

import java.nio.ByteBuffer;

public class AppData {
    public static final int APP_FILE_SIZE = 0xD8;

    ByteBuffer appData;

    public AppData(byte[] appData) throws Exception {
        if (appData.length < APP_FILE_SIZE)
            throw new Exception("Invalid app data");

        this.appData = ByteBuffer.wrap(appData);
    }

    public byte[] array() {
        return appData.array();
    }
}
