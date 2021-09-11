package com.hiddenramblings.tagmo.data;

interface AppDataInterface {
    void onAppDataChecked(boolean enabled);

    byte[] onAppDataSaved() throws Exception;
}