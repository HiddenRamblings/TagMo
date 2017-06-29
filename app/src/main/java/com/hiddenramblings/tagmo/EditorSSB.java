package com.hiddenramblings.tagmo;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.ViewById;

@EActivity(R.layout.activity_editor_ssb)
@OptionsMenu({R.menu.editor_menu})
public class EditorSSB extends AppCompatActivity {
    private static final String TAG = "EditorSSB";

    @ViewById(R.id.spnAppearance)
    Spinner spnAppearance;
    @ViewById(R.id.spnSpecial1)
    Spinner spnSpecial1;
    @ViewById(R.id.spnSpecial2)
    Spinner spnSpecial2;
    @ViewById(R.id.spnSpecial3)
    Spinner spnSpecial3;
    @ViewById(R.id.spnSpecial4)
    Spinner spnSpecial4;

    @ViewById(R.id.spnEffect1)
    Spinner spnEffect1;
    @ViewById(R.id.spnEffect2)
    Spinner spnEffect2;
    @ViewById(R.id.spnEffect3)
    Spinner spnEffect3;


    @ViewById(R.id.txtStatAttack)
    TextView txtStatAttack;
    @ViewById(R.id.txtStatDefense)
    TextView txtStatDefense;
    @ViewById(R.id.txtStatSpeed)
    TextView txtStatSpeed;

    @ViewById(R.id.spnStatAttack)
    Spinner spnStatAttack;
    @ViewById(R.id.spnStatDefense)
    Spinner spnStatDefense;
    @ViewById(R.id.spnStatSpeed)
    Spinner spnStatSpeed;

    @ViewById(R.id.spnLevel)
    Spinner spnLevel;

    KeyManager keyManager;

    @AfterViews
    void afterViews() {
        setListForSpinners(new Spinner[]{spnAppearance},
                R.array.ssb_appearance_values, android.R.layout.simple_list_item_1);
        setListForSpinners(new Spinner[]{spnSpecial1, spnSpecial2, spnSpecial3, spnSpecial4},
                R.array.ssb_specials_values, android.R.layout.simple_list_item_1);
        setListForSpinners(new Spinner[]{spnEffect1, spnEffect2, spnEffect3},
                R.array.ssb_bonus_effects, android.R.layout.simple_list_item_1);
        setListForSpinners(new Spinner[]{spnStatAttack, spnStatDefense, spnStatSpeed},
                R.array.ssb_stat_values, android.R.layout.simple_list_item_1);
        setListForSpinners(new Spinner[]{spnLevel},
                R.array.ssb_levels, android.R.layout.simple_list_item_1);

        keyManager = new KeyManager(this);

        try {
            byte[] tagData = getIntent().getByteArrayExtra(Actions.EXTRA_TAG_DATA);
            tagData = TagUtil.decrypt(keyManager, tagData);
            this.loadData(tagData);
        } catch (Exception e) {
            Log.d(TAG, "Error decrypting data", e);
            finish();
        }
    }


    //all offsets for decrypted (internal) format of tags
    private static final int OFFSET_APP_DATA = 0xDC;

    private static final int OFFSET_APPEARANCE = OFFSET_APP_DATA + 0x08;

    private static final int OFFSET_SPECIAL_NEUTRAL = OFFSET_APP_DATA + 0x09;
    private static final int OFFSET_SPECIAL_SIDE_TO_SIDE = OFFSET_APP_DATA + 0x0A;
    private static final int OFFSET_SPECIAL_UP = OFFSET_APP_DATA + 0x0B;
    private static final int OFFSET_SPECIAL_DOWN = OFFSET_APP_DATA + 0x0C;

    private static final int OFFSET_STATS_ATTACK = OFFSET_APP_DATA + 0x10;
    private static final int OFFSET_STATS_DEFENSE = OFFSET_APP_DATA + 0x12;
    private static final int OFFSET_STATS_SPEED = OFFSET_APP_DATA + 0x14;

    private static final int OFFSET_BONUS_EFFECT1 = OFFSET_APP_DATA + 0x0D;
    private static final int OFFSET_BONUS_EFFECT2 = OFFSET_APP_DATA + 0x0E;
    private static final int OFFSET_BONUS_EFFECT3 = OFFSET_APP_DATA + 0x0F;

    private static final int OFFSET_LEVEL = OFFSET_APP_DATA + 0x7C;

    int readStat(byte[] data, int offset) {
        short value = (short)((data[offset] & 0xFF) << 8);
        value |= data[offset+1] & 0xFF;
        int res = value;
        res += 200; //shift the value to make it positive value as the seek bar doesnt support negative

        if (res < 0)
            res = 0;
        if (res > 401)
            res = 401;

        return res;
    }

    void writeStat(byte[] data, int value, int offset) {
        value -= 200;

        data[offset] = (byte)(((short)value) >> 8);
        data[offset+1] = (byte)(((short)value) & 0xFF);
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

    void setSpecialValue(Spinner spinner, int value) {
        if (value > 2)
            value = 2;
        if (value < 0)
            value = 0;
        spinner.setSelection(value);
    }

    void setSpinnerValue(Spinner spinner, int value) {
        if (value >= spinner.getAdapter().getCount())
            value = 0;
        if (value < 0)
            value = 0;
        spinner.setSelection(value);
    }

    private static final int[] LEVEL_THRESHOLDS = new int[]{ 0x00, 0x08, 0x010, 0x01D, 0x02D, 0x048,
            0x05B, 0x075, 0x08D, 0x0AF, 0x0E1, 0x0103, 0x0126, 0x0149, 0x0172, 0x0196, 0x01BE, 0x01F7,
            0x0216, 0x0240, 0x0278, 0x02A4, 0x02D6, 0x030E, 0x034C, 0x037C, 0x03BB, 0x03F4, 0x042A, 0x0440,
            0x048A, 0x04B6,0x04E3, 0x053F, 0x056D, 0x059C, 0x0606, 0x0641, 0x0670, 0x069E, 0x06FC, 0x072E,
            0x075D, 0x07B9, 0x07E7, 0x0844, 0x0875, 0x08D3, 0x0902, 0x093E
    };

    int readLevel(byte[] data) {
        int value = (data[OFFSET_LEVEL] & 0xFF) << 8;
        value |= (data[OFFSET_LEVEL+1] & 0xFF);

        for (int i= LEVEL_THRESHOLDS.length-1; i>=0; i--) {
            if (LEVEL_THRESHOLDS[i] <= value)
                return i+1;
        }
        return 1;
    }

    void writeLevel(byte[] data, int level) {
        int oldLevel = readLevel(data);
        if (oldLevel == level)
            return; //level is a granular value as such we don't want to overwrite it in case its halfway through a level

        int value = LEVEL_THRESHOLDS[level - 1];

        data[OFFSET_LEVEL] = (byte) ((value >> 8) & 0xFF);
        data[OFFSET_LEVEL + 1] = (byte) (value & 0xFF);
    }

    void loadData(final byte[] data) {
        try {
            setSpinnerValue(spnAppearance, data[OFFSET_APPEARANCE] & 0xFF);

            setSpecialValue(spnSpecial1, data[OFFSET_SPECIAL_NEUTRAL] & 0xFF);
            setSpecialValue(spnSpecial2, data[OFFSET_SPECIAL_SIDE_TO_SIDE] & 0xFF);
            setSpecialValue(spnSpecial3, data[OFFSET_SPECIAL_UP] & 0xFF);
            setSpecialValue(spnSpecial4, data[OFFSET_SPECIAL_DOWN] & 0xFF);

            setSpinnerValue(spnStatAttack, readStat(data, OFFSET_STATS_ATTACK));
            setSpinnerValue(spnStatDefense, readStat(data, OFFSET_STATS_DEFENSE));
            setSpinnerValue(spnStatSpeed, readStat(data, OFFSET_STATS_SPEED));

            setEffectValue(spnEffect1, data[OFFSET_BONUS_EFFECT1] & 0xFF);
            setEffectValue(spnEffect2, data[OFFSET_BONUS_EFFECT2] & 0xFF);
            setEffectValue(spnEffect3, data[OFFSET_BONUS_EFFECT3] & 0xFF);

            setSpinnerValue(spnLevel, readLevel(data) - 1);
        } catch (Exception ex) {
            Log.e(TAG, "Error loading SSB data", ex);
            Toast.makeText(this, "Error loading data. Tag may not be a SSB", Toast.LENGTH_LONG);
        }
    }

    void updateData(byte[] data) {
        data[OFFSET_APPEARANCE] = (byte)spnAppearance.getSelectedItemPosition();
        data[OFFSET_SPECIAL_NEUTRAL] = (byte)spnSpecial1.getSelectedItemPosition();
        data[OFFSET_SPECIAL_SIDE_TO_SIDE] = (byte)spnSpecial2.getSelectedItemPosition();
        data[OFFSET_SPECIAL_UP] = (byte)spnSpecial3.getSelectedItemPosition();
        data[OFFSET_SPECIAL_DOWN] = (byte)spnSpecial4.getSelectedItemPosition();

        writeStat(data, (short)spnStatAttack.getSelectedItemPosition(), OFFSET_STATS_ATTACK);
        writeStat(data, (short)spnStatDefense.getSelectedItemPosition(), OFFSET_STATS_DEFENSE);
        writeStat(data, (short)spnStatSpeed.getSelectedItemPosition(), OFFSET_STATS_SPEED);

        data[OFFSET_BONUS_EFFECT1] = (byte)getEffectValue(spnEffect1);
        data[OFFSET_BONUS_EFFECT2] = (byte)getEffectValue(spnEffect2);
        data[OFFSET_BONUS_EFFECT3] = (byte)getEffectValue(spnEffect3);

        writeLevel(data, spnLevel.getSelectedItemPosition()+1);
    }

    void setListForSpinners(Spinner[] controls, int list, int layout) {
        for(int i=0; i<controls.length; i++) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, list, layout);
            controls[i].setAdapter(adapter);
        }
    }

    @OptionsItem(R.id.mnu_save)
    void save() {
        try {
            byte[] tagData = getIntent().getByteArrayExtra(Actions.EXTRA_TAG_DATA);
            tagData = TagUtil.decrypt(keyManager, tagData);
            this.updateData(tagData);
            tagData = TagUtil.encrypt(keyManager, tagData);
            Intent intent = new Intent(Actions.ACTION_EDIT_COMPLETE);
            intent.putExtra(Actions.EXTRA_TAG_DATA, tagData);
            setResult(Activity.RESULT_OK, intent);
        } catch (Exception e) {
            Log.d(TAG, "Error encrypting data", e);
        }
        finish();
    }


}
