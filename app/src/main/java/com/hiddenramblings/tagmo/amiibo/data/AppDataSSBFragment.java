package com.hiddenramblings.tagmo.amiibo.data;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.Nullable;

import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagDataActivity_;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.TextChange;
import org.androidannotations.annotations.ViewById;

import java.io.IOException;

@SuppressLint("NonConstantResourceId")
@EFragment(R.layout.fragment_app_data_ssb)
public class AppDataSSBFragment extends AppDataFragment {

    public static final int APP_ID = 0x10110E00;

    @ViewById(R.id.spnAppearance)
    Spinner spnAppearance;
    @ViewById(R.id.txtLevel)
    EditText txtLevel;

    @ViewById(R.id.spnSpecial1)
    Spinner spnSpecialNeutral;
    @ViewById(R.id.spnSpecial2)
    Spinner spnSpecialSide;
    @ViewById(R.id.spnSpecial3)
    Spinner spnSpecialUp;
    @ViewById(R.id.spnSpecial4)
    Spinner spnSpecialDown;

    @ViewById(R.id.spnEffect1)
    Spinner spnEffect1;
    @ViewById(R.id.spnEffect2)
    Spinner spnEffect2;
    @ViewById(R.id.spnEffect3)
    Spinner spnEffect3;

    @ViewById(R.id.txtStatAttack)
    EditText txtStatAttack;
    @ViewById(R.id.txtStatDefense)
    EditText txtStatDefense;
    @ViewById(R.id.txtStatSpeed)
    EditText txtStatSpeed;

    AppDataSSB appData;
    boolean initialAppDataInitialized;

    public static AppDataSSBFragment newInstance(byte[] appData, boolean initialAppDataInitialized) {
        Bundle args = new Bundle();
        args.putByteArray("app_data", appData);
        args.putBoolean("app_data_init", initialAppDataInitialized);

        AppDataSSBFragment fragment = new AppDataSSBFragment_();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            try {
                appData = new AppDataSSB(getArguments().getByteArray("app_data"));
            } catch (IOException e) {
                Debug.Log(e);
                return;
            }
            if (savedInstanceState == null) {
                initialAppDataInitialized = getArguments().getBoolean("app_data_init");
            }
        }
    }

    @AfterViews
    void afterViews() {
        setListForSpinners(new Spinner[]{spnAppearance},
                R.array.ssb_appearance_values);
        setListForSpinners(new Spinner[]{spnSpecialNeutral, spnSpecialSide, spnSpecialUp, spnSpecialDown},
                R.array.ssb_specials_values);
        setListForSpinners(new Spinner[]{spnEffect1, spnEffect2, spnEffect3},
                R.array.ssb_bonus_effects);

        this.loadData();

        onAppDataChecked(((TagDataActivity_) requireContext()).isAppDataInitialized);
    }

    void loadData() {
        loadAppearance();
        loadLevel();

        loadSpecialNeutral();
        loadSpecialSide();
        loadSpecialUp();
        loadSpecialDown();

        loadStatAttack();
        loadStatDefense();
        loadStatSpeed();

        loadBonusEffect1();
        loadBonusEffect2();
        loadBonusEffect3();
    }

    void loadAppearance() {
        int appearance;
        if (initialAppDataInitialized) {
            try {
                appearance = appData.getAppearence();
            } catch (Exception e) {
                appearance = 0;
            }
        } else {
            appearance = 0;
        }
        spnAppearance.setSelection(appearance);
    }

    void loadLevel() {
        int level;
        if (initialAppDataInitialized) {
            try {
                level = appData.getLevel();
            } catch (Exception e) {
                level = 50;
            }
        } else {
            level = 50;
        }
        txtLevel.setText(String.valueOf(level));
    }

    @TextChange(R.id.txtLevel)
    void onLevelUpdate() {
        try {
            int level = Integer.parseInt(txtLevel.getText().toString());
            if (level < 1 || level > 50) {
                txtLevel.setError(getString(R.string.error_min_max, 1, 50));
            } else {
                txtLevel.setError(null);
            }
        } catch (NumberFormatException e) {
            txtLevel.setError(getString(R.string.error_min_max, 1, 50));
        }
    }

    void loadSpecialNeutral() {
        int specialNeutral;
        if (initialAppDataInitialized) {
            try {
                specialNeutral = appData.getSpecialNeutral();
            } catch (Exception e) {
                specialNeutral = 0;
            }
        } else {
            specialNeutral = 0;
        }
        spnSpecialNeutral.setSelection(specialNeutral);
    }

    void loadSpecialSide() {
        int specialSide;
        if (initialAppDataInitialized) {
            try {
                specialSide = appData.getSpecialSide();
            } catch (Exception e) {
                specialSide = 0;
            }
        } else {
            specialSide = 0;
        }
        spnSpecialSide.setSelection(specialSide);
    }

    void loadSpecialUp() {
        int specialUp;
        if (initialAppDataInitialized) {
            try {
                specialUp = appData.getSpecialUp();
            } catch (Exception e) {
                specialUp = 0;
            }
        } else {
            specialUp = 0;
        }
        spnSpecialUp.setSelection(specialUp);
    }

    void loadSpecialDown() {
        int specialDown;
        if (initialAppDataInitialized) {
            try {
                specialDown = appData.getSpecialDown();
            } catch (Exception e) {
                specialDown = 0;
            }
        } else {
            specialDown = 0;
        }
        spnSpecialDown.setSelection(specialDown);
    }

    void loadStatAttack() {
        int statAttack;
        if (initialAppDataInitialized) {
            try {
                statAttack = appData.getStatAttack();
            } catch (Exception e) {
                statAttack = 200;
            }
        } else {
            statAttack = 200;
        }
        txtStatAttack.setText(String.valueOf(statAttack));
    }

    @TextChange(R.id.txtStatAttack)
    void onStatAttackUpdate() {
        try {
            int level = Integer.parseInt(txtStatAttack.getText().toString());
            try {
                appData.checkStat(level);
                txtStatAttack.setError(null);
            } catch (Exception e) {
                txtStatAttack.setError(getString(R.string.error_min_max, -200, 200));
            }
        } catch (NumberFormatException e) {
            txtStatAttack.setError(getString(R.string.error_min_max, -200, 200));
        }
    }

    void loadStatDefense() {
        int statDefense;
        if (initialAppDataInitialized) {
            try {
                statDefense = appData.getStatDefense();
            } catch (Exception e) {
                statDefense = 200;
            }
        } else {
            statDefense = 200;
        }
        txtStatDefense.setText(String.valueOf(statDefense));
    }

    @TextChange(R.id.txtStatDefense)
    void onStatDefenseUpdate() {
        try {
            int level = Integer.parseInt(txtStatDefense.getText().toString());
            try {
                appData.checkStat(level);
                txtStatDefense.setError(null);
            } catch (Exception e) {
                txtStatDefense.setError(getString(R.string.error_min_max, -200, 200));
            }
        } catch (NumberFormatException e) {
            txtStatDefense.setError(getString(R.string.error_min_max, -200, 200));
        }
    }

    void loadStatSpeed() {
        int statSpeed;
        if (initialAppDataInitialized) {
            try {
                statSpeed = appData.getStatSpeed();
            } catch (Exception e) {
                statSpeed = 200;
            }
        } else {
            statSpeed = 200;
        }
        txtStatSpeed.setText(String.valueOf(statSpeed));
    }

    @TextChange(R.id.txtStatSpeed)
    void onStatSpeedUpdate() {
        try {
            int level = Integer.parseInt(txtStatSpeed.getText().toString());
            try {
                appData.checkStat(level);
                txtStatSpeed.setError(null);
            } catch (Exception e) {
                txtStatSpeed.setError(getString(R.string.error_min_max, -200, 200));
            }
        } catch (NumberFormatException e) {
            txtStatSpeed.setError(getString(R.string.error_min_max, -200, 200));
        }
    }

    void setEffectValue(Spinner spinner, int value) {
        if (value == 0xFF)
            value = 0;
        else
            value++;

        if (value > spinner.getAdapter().getCount())
            value = 0;

        spinner.setSelection(value);
    }

    int getEffectValue(Spinner spinner) {
        int value = spinner.getSelectedItemPosition();
        if (value == 0)
            value = 0xFF;
        else
            value--;

        return value;
    }

    void loadBonusEffect1() {
        int bonusEffect1;
        if (initialAppDataInitialized) {
            try {
                bonusEffect1 = appData.getBonusEffect1();
            } catch (Exception e) {
                bonusEffect1 = 0xFF;
            }
        } else {
            bonusEffect1 = 0xFF;
        }
        setEffectValue(spnEffect1, bonusEffect1);
    }

    void loadBonusEffect2() {
        int bonusEffect2;
        if (initialAppDataInitialized) {
            try {
                bonusEffect2 = appData.getBonusEffect2();
            } catch (Exception e) {
                bonusEffect2 = 0xFF;
            }
        } else {
            bonusEffect2 = 0xFF;
        }
        setEffectValue(spnEffect2, bonusEffect2);
    }

    void loadBonusEffect3() {
        int bonusEffect3;
        if (initialAppDataInitialized) {
            try {
                bonusEffect3 = appData.getBonusEffect3();
            } catch (Exception e) {
                bonusEffect3 = 0xFF;
            }
        } else {
            bonusEffect3 = 0xFF;
        }
        setEffectValue(spnEffect3, bonusEffect3);
    }

    void setListForSpinners(Spinner[] controls, int list) {
        for (Spinner control : controls) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                    this.getContext(), list, R.layout.spinner_text);
            adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
            control.setAdapter(adapter);
        }
    }

    @Override
    public void onAppDataChecked(boolean enabled) {
        if (spnAppearance == null)
            return;

        spnAppearance.setEnabled(enabled);
        txtLevel.setEnabled(enabled);
        spnSpecialNeutral.setEnabled(enabled);
        spnSpecialSide.setEnabled(enabled);
        spnSpecialUp.setEnabled(enabled);
        spnSpecialDown.setEnabled(enabled);
        txtStatAttack.setEnabled(enabled);
        txtStatDefense.setEnabled(enabled);
        txtStatSpeed.setEnabled(enabled);
        spnEffect1.setEnabled(enabled);
        spnEffect2.setEnabled(enabled);
        spnEffect3.setEnabled(enabled);
    }

    @Override
    public byte[] onAppDataSaved() {
        try {
            int appearance = spnAppearance.getSelectedItemPosition();
            appData.setAppearence(appearance);
        } catch (NumberFormatException e) {
            spnAppearance.requestFocus();
            throw e;
        }
        try {
            int level = Integer.parseInt(txtLevel.getText().toString());
            Integer oldLevel;
            try {
                oldLevel = appData.getLevel();
            } catch (Exception e) {
                oldLevel = null;
            }

            //level is a granular value as such we don't want to overwrite it in case its halfway through a level
            if (oldLevel == null || level != oldLevel) {
                appData.setLevel(level);
            }
        } catch (NumberFormatException e) {
            txtLevel.requestFocus();
            throw e;
        }

        try {
            int specialNeutral = spnSpecialNeutral.getSelectedItemPosition();
            appData.setSpecialNeutral(specialNeutral);
        } catch (NumberFormatException e) {
            spnSpecialNeutral.requestFocus();
            throw e;
        }
        try {
            int specialSide = spnSpecialSide.getSelectedItemPosition();
            appData.setSpecialSide(specialSide);
        } catch (NumberFormatException e) {
            spnSpecialSide.requestFocus();
            throw e;
        }
        try {
            int specialUp = spnSpecialUp.getSelectedItemPosition();
            appData.setSpecialUp(specialUp);
        } catch (NumberFormatException e) {
            spnSpecialUp.requestFocus();
            throw e;
        }
        try {
            int specialDown = spnSpecialDown.getSelectedItemPosition();
            appData.setSpecialDown(specialDown);
        } catch (NumberFormatException e) {
            spnSpecialDown.requestFocus();
            throw e;
        }

        try {
            int statAttack = Integer.parseInt(txtStatAttack.getText().toString());
            appData.setStatAttack(statAttack);
        } catch (NumberFormatException e) {
            txtStatAttack.requestFocus();
            throw e;
        }
        try {
            int statDefense = Integer.parseInt(txtStatDefense.getText().toString());
            appData.setStatDefense(statDefense);
        } catch (NumberFormatException e) {
            txtStatDefense.requestFocus();
            throw e;
        }
        try {
            int statSpeed = Integer.parseInt(txtStatSpeed.getText().toString());
            appData.setStatSpeed(statSpeed);
        } catch (NumberFormatException e) {
            txtStatSpeed.requestFocus();
            throw e;
        }

        try {
            int bonusEffect1 = getEffectValue(spnEffect1);
            appData.setBonusEffect1(bonusEffect1);
        } catch (NumberFormatException e) {
            spnEffect1.requestFocus();
            throw e;
        }
        try {
            int bonusEffect2 = getEffectValue(spnEffect2);
            appData.setBonusEffect2(bonusEffect2);
        } catch (NumberFormatException e) {
            spnEffect2.requestFocus();
            throw e;
        }
        try {
            int bonusEffect3 = getEffectValue(spnEffect3);
            appData.setBonusEffect3(bonusEffect3);
        } catch (NumberFormatException e) {
            spnEffect3.requestFocus();
            throw e;
        }

        return appData.array();
    }

}
