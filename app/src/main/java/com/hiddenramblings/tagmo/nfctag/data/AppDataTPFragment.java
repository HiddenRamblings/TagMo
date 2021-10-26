package com.hiddenramblings.tagmo.nfctag.data;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagDataActivity_;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.TextChange;
import org.androidannotations.annotations.ViewById;

@SuppressLint("NonConstantResourceId")
@EFragment(R.layout.fragment_app_data_tp)
public class AppDataTPFragment extends AppDataFragment {
    public static final int APP_ID = 0x1019C800;

    @ViewById(R.id.txtHearts1)
    EditText txtHearts1;
    @ViewById(R.id.txtHearts2)
    Spinner txtHearts2;
    @ViewById(R.id.txtLevel)
    EditText txtLevel;

    AppDataTP appData;
    @InstanceState
    boolean initialAppDataInitialized;

    public static AppDataTPFragment newInstance(byte[] appData, boolean initialAppDataInitialized) {
        Bundle args = new Bundle();
        args.putByteArray("app_data", appData);
        args.putBoolean("app_data_init", initialAppDataInitialized);

        AppDataTPFragment fragment = new AppDataTPFragment_();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            appData = new AppDataTP(getArguments().getByteArray("app_data"));
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (savedInstanceState == null) {
            initialAppDataInitialized = getArguments().getBoolean("app_data_init");
        }
    }

    @AfterViews
    void afterViews() {
        setListForSpinners(txtHearts2, R.array.editor_tp_hearts);

        loadLevel();
        loadHearts();

        onAppDataChecked(((TagDataActivity_) requireContext()).isAppDataInitialized());
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        onAppDataChecked(((TagDataActivity_) context).isAppDataInitialized());
    }

    void loadLevel() {
        int level;
        if (initialAppDataInitialized) {
            try {
                level = appData.getLevel();
            } catch (Exception e) {
                level = 40;
            }
        } else {
            level = 40;
        }
        txtLevel.setText(String.valueOf(level));
    }

    @TextChange(R.id.txtLevel)
    void onLevelUpdate() {
        try {
            int level = Integer.parseInt(txtLevel.getText().toString());
            try {
                appData.checkLevel(level);
                txtLevel.setError(null);
            } catch (Exception e) {
                txtLevel.setError(getString(R.string.min_max_error, 0, 40));
            }
        } catch (NumberFormatException e) {
            txtLevel.setError(getString(R.string.min_max_error, 0, 40));
        }
    }

    void loadHearts() {
        int hearts;
        if (initialAppDataInitialized) {
            try {
                hearts = appData.getHearts();
            } catch (Exception e) {
                hearts = AppDataTP.HEARTS_MAX_VALUE;
            }
        } else {
            hearts = AppDataTP.HEARTS_MAX_VALUE;
        }
        txtHearts1.setText(String.valueOf((hearts / 4)));
        txtHearts2.setSelection(hearts % 4);
        txtHearts2.setEnabled((hearts / 4) < 20);
    }

    @TextChange(R.id.txtHearts1)
    void onHeartsUpdate() {
        try {
            int hearts = Integer.parseInt(txtHearts1.getText().toString());
            txtHearts2.setEnabled(hearts < 20);
            if (!txtHearts2.isEnabled()) {
                txtHearts2.setSelection(0);
            }
            try {
                appData.checkHearts(hearts * 4);
                txtHearts1.setError(null);
            } catch (Exception e) {
                txtHearts1.setError(getString(R.string.min_max_error, 0, 20));
            }
        } catch (NumberFormatException e) {
            txtHearts1.setError(getString(R.string.min_max_error, 0, 20));
            txtHearts2.setEnabled(txtHearts1.isEnabled());
        }
    }

    void setListForSpinners(Spinner control, int list) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this.getContext(), list, R.layout.spinner_text);
        adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
        control.setAdapter(adapter);
    }

    @Override
    public void onAppDataChecked(boolean enabled) {
        if (txtHearts2 == null)
            return;

        txtHearts1.setEnabled(enabled);
        onHeartsUpdate();
        txtLevel.setEnabled(enabled);
    }

    @Override
    public byte[] onAppDataSaved() throws Exception {
        try {
            int level = Integer.parseInt(txtLevel.getText().toString());
            appData.setLevel(level);
        } catch (Exception e) {
            txtLevel.requestFocus();
            throw e;
        }
        try {
            int hearts1 = Integer.parseInt(txtHearts1.getText().toString());
            int hearts2 = txtHearts2.getSelectedItemPosition();
            appData.setHearts((hearts1 * 4) + hearts2);
        } catch (Exception e) {
            txtHearts1.requestFocus();
            throw e;
        }


        return appData.array();
    }
}
