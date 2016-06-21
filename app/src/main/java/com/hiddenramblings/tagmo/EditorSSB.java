package com.hiddenramblings.tagmo;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

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

        keyManager = new KeyManager(this);

        try {
            byte[] tagData = getIntent().getByteArrayExtra(Actions.EXTRA_TAG_DATA);
            tagData = TagUtil.decrypt(keyManager, tagData);
            this.loadData(tagData);
        } catch (Exception e) {
            Log.d(TAG, "Error decyrpting data", e);
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

    void loadData(final byte[] data) {
        spnAppearance.setSelection(data[OFFSET_APPEARANCE] & 0xFF);

        setSpecialValue(spnSpecial1, data[OFFSET_SPECIAL_NEUTRAL] & 0xFF);
        setSpecialValue(spnSpecial2, data[OFFSET_SPECIAL_SIDE_TO_SIDE] & 0xFF);
        setSpecialValue(spnSpecial3, data[OFFSET_SPECIAL_UP] & 0xFF);
        setSpecialValue(spnSpecial4, data[OFFSET_SPECIAL_DOWN] & 0xFF);

        spnStatAttack.setSelection(readStat(data, OFFSET_STATS_ATTACK));
        spnStatDefense.setSelection(readStat(data, OFFSET_STATS_DEFENSE));
        spnStatSpeed.setSelection(readStat(data, OFFSET_STATS_SPEED));

        setEffectValue(spnEffect1, data[OFFSET_BONUS_EFFECT1] & 0xFF);
        setEffectValue(spnEffect2, data[OFFSET_BONUS_EFFECT2] & 0xFF);
        setEffectValue(spnEffect3, data[OFFSET_BONUS_EFFECT3] & 0xFF);
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
