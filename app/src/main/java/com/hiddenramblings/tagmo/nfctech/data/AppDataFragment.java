package com.hiddenramblings.tagmo.nfctech.data;

import androidx.fragment.app.Fragment;

public abstract class AppDataFragment extends Fragment implements AppDataInterface {

}

interface AppDataInterface {
    void onAppDataChecked(boolean enabled);

    byte[] onAppDataSaved() throws NumberFormatException;
}
