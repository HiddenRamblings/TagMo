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
import java.util.Date;

@EActivity(R.layout.activity_editor_tp)
@OptionsMenu({R.menu.editor_menu})
public class EditorTP extends AppCompatActivity {
    public static final long WOLF_LINK_ID = 0x01030000024F0902L;
    public static final int APP_ID = 0x1019C800;

    //all offsets for decrypted (internal) format of tags
    private static final int OFFSET_LEVEL = TagUtil.OFFSET_APP_DATA;
    private static final int OFFSET_HEARTS = OFFSET_LEVEL + 0x01;

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

        byte[] tagData = getIntent().getByteArrayExtra(Actions.EXTRA_TAG_DATA);

        long amiiboId;
        try {
            amiiboId = TagUtil.amiiboIdFromTag(tagData);
        } catch (Exception e) {
            e.printStackTrace();
            LogError("Unable to read Amiibo ID");
            return;
        }

        if (!canEditAmiibo(amiiboId)) {
            LogError("This Amiibo is not compatible with this editor");
            return;
        }

        try {
            tagData = TagUtil.decrypt(keyManager, tagData);
        } catch (Exception e) {
            e.printStackTrace();

            LogError("Error decyrpting data");
        }

        try {
            this.tagData = loadData(tagData, true);
        } catch (Exception e) {
            e.printStackTrace();

            LogError("Error loading data");
        }
    }

    public static boolean canEditAmiibo(long amiiboId) {
        return amiiboId == WOLF_LINK_ID;
    }

    byte[] loadData(byte[] data, boolean setDefaults) throws Exception {
        ByteBuffer bb = ByteBuffer.wrap(data);

        Date today = new Date();

        Date createDate;
        try {
            createDate = TagUtil.fromAmiiboDate(bb.getShort(TagUtil.OFFSET_CREATE_DATE));
        } catch (Exception e) {
            createDate = null;
        }
        if (createDate == null || createDate.after(today)) {
            if (setDefaults) {
                bb.putShort(TagUtil.OFFSET_CREATE_DATE, TagUtil.toAmiiboDate(today));
            } else {
                throw new Exception("OFFSET_CREATE_DATE Value invalid" + createDate);
            }
        }

        Date modifiedDate;
        try {
            modifiedDate = TagUtil.fromAmiiboDate(bb.getShort(TagUtil.OFFSET_MODIFIED_DATE));
        } catch (Exception e) {
            modifiedDate = null;
        }
        if (modifiedDate == null || modifiedDate.after(today)) {
            if (setDefaults) {
                bb.putShort(TagUtil.OFFSET_MODIFIED_DATE, TagUtil.toAmiiboDate(today));
            } else {
                throw new Exception("OFFSET_MODIFIED_DATE Value invalid" + createDate);
            }
        }

        int appId = bb.getInt(TagUtil.APP_ID_OFFSET);
        if (appId != APP_ID) {
            if (setDefaults) {
                bb.putInt(TagUtil.APP_ID_OFFSET, APP_ID);
            } else {
                throw new Exception("APP_ID_OFFSET Value invalid: " + appId);
            }
        }

        byte level = bb.get(OFFSET_LEVEL);
        if (level < 0 || level > spnShadowCaveLevel.getAdapter().getCount()) {
            if (setDefaults) {
                level = 0;
                bb.put(OFFSET_LEVEL, level);
            } else {
                throw new IndexOutOfBoundsException("OFFSET_LEVEL Value invalid: " + level);
            }
        }

        byte hearts = bb.get(OFFSET_HEARTS);
        if (hearts < 0 || hearts > spnHearts.getAdapter().getCount()) {
            if (setDefaults) {
                hearts = 0;
                bb.put(OFFSET_HEARTS, hearts);
            } else {
                throw new IndexOutOfBoundsException("OFFSET_HEARTS Value invalid: " + hearts);
            }
        }

        spnShadowCaveLevel.setSelection(level);
        spnHearts.setSelection(hearts);

        return bb.array();
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
