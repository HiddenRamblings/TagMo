package com.hiddenramblings.tagmo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hiddenramblings.tagmo.adapter.HexCodeAdapter;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.nfctech.TagUtils;

public class HexViewerActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_hex_viewer);

        byte[] tagData = getIntent().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
        KeyManager keyManager = new KeyManager(this);
        if (keyManager.isKeyMissing()) {
            showErrorDialog(R.string.no_decrypt_key);
            return;
        }

        RecyclerView listView = findViewById(R.id.gridView);
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

    void showErrorDialog(int msgRes) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error_caps)
                .setMessage(msgRes)
                .setPositiveButton(R.string.close, (dialog, which) -> finish())
                .show();
        setResult(Activity.RESULT_OK, new Intent(TagMo.ACTION_FIX_BANK_DATA));
    }
}
