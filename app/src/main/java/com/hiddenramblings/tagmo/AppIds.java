package com.hiddenramblings.tagmo;

import java.util.HashMap;

public class AppIds {
    public static final HashMap<Integer, String> appIds = new HashMap<>();

    static {
        appIds.put(AppDataTPFragment.APP_ID, TagMo.getStringRes(R.string.zelda_twilight));
        appIds.put(AppDataSSBFragment.APP_ID, TagMo.getStringRes(R.string.super_smash));
    }
}
