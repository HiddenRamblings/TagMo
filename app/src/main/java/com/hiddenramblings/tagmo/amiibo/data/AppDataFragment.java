package com.hiddenramblings.tagmo.amiibo.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.hiddenramblings.tagmo.TagDataActivity;

public abstract class AppDataFragment extends Fragment implements AppDataInterface {

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        onAppDataChecked(((TagDataActivity) context).isAppDataInitialized);
    }
}

interface AppDataInterface {
    void onAppDataChecked(boolean enabled);

    byte[] onAppDataSaved() throws NumberFormatException;
}
