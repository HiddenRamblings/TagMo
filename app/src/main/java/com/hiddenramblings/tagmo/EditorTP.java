package com.hiddenramblings.tagmo;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.ViewById;

@EActivity(R.layout.activity_editor_tp)
@OptionsMenu({R.menu.editor_menu})
public class EditorTP extends AppCompatActivity {
    public static final int WOLF_LINK_ID = 0x01030000;

    private static final String TAG = "EditorTP";

    @ViewById(R.id.spnShadowCaveLevel)
    Spinner spnShadowCaveLevel;
    @ViewById(R.id.spnHearts)
    Spinner spnHearts;

    KeyManager keyManager;

    @AfterViews
    void afterViews() {
        setListForSpinners(new Spinner[]{spnShadowCaveLevel},
                R.array.editor_tp_levels, android.R.layout.simple_list_item_1);
        setListForSpinners(new Spinner[]{spnHearts},
                R.array.editor_tp_hearts, android.R.layout.simple_list_item_1);

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
    private static final int OFFSET_APP_DATA = 0xED;

    private static final int OFFSET_LEVEL = OFFSET_APP_DATA;
    private static final int OFFSET_HEARTS = OFFSET_APP_DATA + 0x01;


    void loadData(final byte[] data) {
        try {
            if (data[OFFSET_LEVEL] < 0 || data[OFFSET_LEVEL] > spnShadowCaveLevel.getAdapter().getCount()) {
                throw new IndexOutOfBoundsException("OFFSET_LEVEL Value invalid: "+data[OFFSET_LEVEL]);
            }
            if (data[OFFSET_HEARTS] < 0 || data[OFFSET_HEARTS] > spnHearts.getAdapter().getCount()) {
                throw new IndexOutOfBoundsException("OFFSET_HEARTS Value invalid: "+data[OFFSET_HEARTS]);
            }

            spnShadowCaveLevel.setSelection(data[OFFSET_LEVEL]);
            spnHearts.setSelection(data[OFFSET_HEARTS]);
        } catch (IndexOutOfBoundsException e) {
            Log.d(TAG, "Error loading data", e);
            Toast.makeText(this, R.string.editor_error_outofbounds, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    void updateData(byte[] data) {
        data[OFFSET_LEVEL] = (byte) spnShadowCaveLevel.getSelectedItemPosition();
        data[OFFSET_HEARTS] = (byte) spnHearts.getSelectedItemPosition();
    }

    void setListForSpinners(Spinner[] controls, int list, int layout) {
        for (Spinner control : controls) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, list, layout);
            control.setAdapter(adapter);
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
