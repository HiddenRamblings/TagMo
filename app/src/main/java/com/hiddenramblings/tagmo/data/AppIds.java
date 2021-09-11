package com.hiddenramblings.tagmo.data;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;

import java.util.HashMap;

public class AppIds {
    public static final HashMap<Integer, String> appIds = new HashMap<>();

    static {
        appIds.put(AppDataTPFragment.APP_ID, TagMo.getStringRes(R.string.zelda_twilight));
        appIds.put(AppDataSSBFragment.APP_ID, TagMo.getStringRes(R.string.super_smash));
        appIds.put(-1, TagMo.getStringRes(R.string.unknown));
    }
}
