package com.hiddenramblings.tagmo.nfctag.data;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;

import java.nio.ByteBuffer;
import java.util.HashMap;

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

    public static final HashMap<Integer, String> appIds = new HashMap<>();

    static {
        appIds.put(AppDataTPFragment.APP_ID, TagMo.getStringRes(R.string.zelda_twilight));
        appIds.put(AppDataSSBFragment.APP_ID, TagMo.getStringRes(R.string.super_smash));
        appIds.put(-1, TagMo.getStringRes(R.string.unspecified));
    }
}
