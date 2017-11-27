package com.hiddenramblings.tagmo;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.nio.ByteBuffer;

@EActivity(R.layout.activity_editor_tp)
@OptionsMenu({R.menu.editor_menu})
public class EditorTP extends AppCompatActivity {
    public static final long WOLF_LINK_ID = 0x01030000024F0902L;
    public static final int APP_ID = 0x1019C800;

    private static final String TAG = "EditorTP";

    @ViewById(R.id.spnShadowCaveLevel)
    Spinner spnShadowCaveLevel;
    @ViewById(R.id.spnHearts)
    Spinner spnHearts;

    KeyManager keyManager;

    byte[] tagData;

    @AfterViews
    void afterViews() {
        setListForSpinners(new Spinner[]{spnShadowCaveLevel},
            R.array.editor_tp_levels, android.R.layout.simple_list_item_1);
        setListForSpinners(new Spinner[]{spnHearts},
            R.array.editor_tp_hearts, android.R.layout.simple_list_item_1);

        keyManager = new KeyManager(this);

        try {
            this.tagData = getIntent().getByteArrayExtra(Actions.EXTRA_TAG_DATA);

            long amiiboId;
            try {
                amiiboId = TagUtil.amiiboIdFromTag(tagData);
            } catch (Exception e) {
                e.printStackTrace();
                LogError("Unable read Amiibo ID");
                return;
            }

            if (!canEditAmiibo(amiiboId)) {
                LogError("This Amiibo is not compatible with this editor");
                return;
            }

            this.tagData = TagUtil.decrypt(keyManager, tagData);
            this.loadData(tagData);
        } catch (Exception e) {
            e.printStackTrace();

            LogError("Error decyrpting data");
        }
    }

    public static boolean canEditAmiibo(long amiiboId) {
        return amiiboId == WOLF_LINK_ID;
    }

    //all offsets for decrypted (internal) format of tags
    private static final int OFFSET_APP_DATA = 0xED;
    private static final int OFFSET_LEVEL = OFFSET_APP_DATA;
    private static final int OFFSET_HEARTS = OFFSET_APP_DATA + 0x01;

    void loadData(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        int appId = bb.getInt(TagUtil.APP_ID_OFFSET);
        bb.putInt(TagUtil.APP_ID_OFFSET, APP_ID);
        bb.put(OFFSET_LEVEL, (byte)0);
        bb.put(OFFSET_HEARTS, (byte)0);
        bb.putInt(0xAC, 0x00050000);
        bb.putInt(0xB0, 0x1019e600);
        bb.putInt(0xB4, 0x00031019);
        bb.putInt(0xB8, 0xc80045c9);

        bb.putInt(0xDC, 0x00000000);
        bb.putInt(0xE0, 0x00000000);
        bb.putInt(0xE4, 0x00000000);
        bb.putInt(0xE8, 0x00000000);
        bb.putInt(0xEC, 0xff285064);
        bb.putInt(0xF0, 0x00000000);
        bb.putInt(0xF4, 0x00000000);
        bb.putInt(0xF8, 0x00000000);
        bb.putInt(0xFC, 0x00000000);
        data = bb.array();
        appId = bb.getInt(TagUtil.APP_ID_OFFSET);
        if (appId != APP_ID) {
            LogError("This Amiibo's app data is not formatted for The Legend of Zelda: Twilight Princess HD HD Cave of Shadows.");
            return;
        }

        try {
            if (data[OFFSET_LEVEL] < 0 || data[OFFSET_LEVEL] > spnShadowCaveLevel.getAdapter().getCount()) {
                throw new IndexOutOfBoundsException("OFFSET_LEVEL Value invalid: " + data[OFFSET_LEVEL]);
            }
            if (data[OFFSET_HEARTS] < 0 || data[OFFSET_HEARTS] > spnHearts.getAdapter().getCount()) {
                throw new IndexOutOfBoundsException("OFFSET_HEARTS Value invalid: " + data[OFFSET_HEARTS]);
            }

            spnShadowCaveLevel.setSelection(data[OFFSET_LEVEL]);
            spnHearts.setSelection(data[OFFSET_HEARTS]);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();

            LogError("Error parsing data");
        }
        this.tagData = data;
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
            this.updateData(this.tagData);
            byte[] tagData = TagUtil.encrypt(keyManager, this.tagData);
            Intent intent = new Intent(Actions.ACTION_EDIT_COMPLETE);
            intent.putExtra(Actions.EXTRA_TAG_DATA, tagData);
            setResult(Activity.RESULT_OK, intent);
        } catch (Exception e) {
            Log.d(TAG, "Error encrypting data", e);
        }
        finish();
    }

    @UiThread
    void LogError(String msg) {
        new AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(msg)
            .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    EditorTP.this.finish();
                }
            })
            .show();
    }
}
