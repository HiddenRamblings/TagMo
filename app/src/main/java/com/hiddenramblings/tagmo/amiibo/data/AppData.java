package com.hiddenramblings.tagmo.amiibo.data;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class AppData {

    private static final int APP_FILE_SIZE = 0xD8;

    ByteBuffer appData;

    public AppData(byte[] appData) throws IOException {
        if (appData.length < APP_FILE_SIZE)
            throw new IOException(TagMo.getContext().getString(R.string.invalid_app_data));

        this.appData = ByteBuffer.wrap(appData);
    }

    public byte[] array() {
        return appData.array();
    }

    public static final HashMap<Integer, String> appIds = new HashMap<>();

    static {
        appIds.put(AppDataTPFragment.APP_ID,
                TagMo.getContext().getString(R.string.zelda_twilight));
        appIds.put(AppDataSSBFragment.APP_ID,
                TagMo.getContext().getString(R.string.super_smash));
        appIds.put(-1, TagMo.getContext().getString(R.string.unspecified));
    }
}
