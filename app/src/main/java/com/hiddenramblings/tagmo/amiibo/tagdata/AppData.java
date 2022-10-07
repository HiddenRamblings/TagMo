package com.hiddenramblings.tagmo.amiibo.tagdata;

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
        appIds.put(TagDataEditor.AppId_ChibiRobo,
                TagMo.getContext().getString(R.string.chibi_robo));
        appIds.put(TagDataEditor.AppId_ZeldaTP,
                TagMo.getContext().getString(R.string.zelda_twilight));
        appIds.put(TagDataEditor.AppId_MHStories,
                TagMo.getContext().getString(R.string.mh_stories));
        appIds.put(TagDataEditor.AppId_MLPaperJam,
                TagMo.getContext().getString(R.string.ml_paper_jam));
        appIds.put(TagDataEditor.AppId_MLSuperstar,
                TagMo.getContext().getString(R.string.ml_superstar));
        appIds.put(TagDataEditor.AppId_MarioTennis,
                TagMo.getContext().getString(R.string.mario_tennis));
        appIds.put(TagDataEditor.AppId_Pikmin,
                TagMo.getContext().getString(R.string.pikmin));
        appIds.put(TagDataEditor.AppId_Splatoon,
                TagMo.getContext().getString(R.string.splatoon));
        appIds.put(TagDataEditor.AppId_SSB,
                TagMo.getContext().getString(R.string.super_smash));
        appIds.put(TagDataEditor.AppId_SSBU,
                TagMo.getContext().getString(R.string.smash_ultimate));
        appIds.put(-1, TagMo.getContext().getString(R.string.unspecified));
    }
}
