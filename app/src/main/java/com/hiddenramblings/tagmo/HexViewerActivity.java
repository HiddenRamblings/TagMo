package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hiddenramblings.tagmo.adapter.HexCodeAdapter;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.nfctech.TagUtils;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_hex_viewer)
public class HexViewerActivity extends AppCompatActivity {

    @ViewById(R.id.gridView)
    RecyclerView listView;

    @AfterViews
    void afterViews() {
        byte[] tagData = getIntent().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
        KeyManager keyManager = new KeyManager(this);
        if (keyManager.isKeyMissing()) {
            showErrorDialog(R.string.no_decrypt_key);
            return;
        }
        HexCodeAdapter adapter;
        try {
            adapter = new HexCodeAdapter(keyManager.decrypt(tagData));
            listView.setLayoutManager(new LinearLayoutManager(this));
            listView.setAdapter(adapter);
        } catch (Exception e) {
            try {
                adapter = new HexCodeAdapter(TagUtils.getValidatedData(keyManager, tagData));
                listView.setLayoutManager(new LinearLayoutManager(this));
                listView.setAdapter(adapter);
            } catch (Exception ex) {
                Debug.Log(e);
                showErrorDialog(R.string.fail_display);
            }
        }
    }

    @UiThread
    void showErrorDialog(int msgRes) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error_caps)
                .setMessage(msgRes)
                .setPositiveButton(R.string.close, (dialog, which) -> finish())
                .show();
        setResult(Activity.RESULT_OK, new Intent(TagMo.ACTION_FIX_BANK_DATA));
    }
}
