package com.hiddenramblings.tagmo.data;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;

import java.nio.ByteBuffer;

public class AppData {

    private static final int APP_FILE_SIZE = 0xD8;

    ByteBuffer appData;

    public AppData(byte[] appData) throws Exception {
        if (appData.length < APP_FILE_SIZE)
            throw new Exception(TagMo.getStringRes(R.string.invalid_app_data));

        this.appData = ByteBuffer.wrap(appData);
    }

    public byte[] array() {
        return appData.array();
    }
}
