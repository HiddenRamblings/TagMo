package com.hiddenramblings.tagmo;

interface AppDataInterface {
    void onAppDataChecked(boolean enabled);

    byte[] onAppDataSaved() throws Exception;
}