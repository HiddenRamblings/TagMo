package com.hiddenramblings.tagmo.hexcode;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hiddenramblings.tagmo.NFCIntent;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.nfctech.TagUtils;

public class HexCodeViewer extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_hex_viewer);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        byte[] tagData = getIntent().getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA);
        KeyManager keyManager = new KeyManager(this);
        if (keyManager.isKeyMissing()) {
            showErrorDialog(R.string.no_decrypt_key);
            return;
        }

        RecyclerView listView = findViewById(R.id.gridView);
        HexAdapter adapter;
        try {
            adapter = new HexAdapter(keyManager.decrypt(tagData));
            listView.setLayoutManager(new LinearLayoutManager(this));
            listView.setAdapter(adapter);
        } catch (Exception e) {
            try {
                adapter = new HexAdapter(TagUtils.getValidatedData(keyManager, tagData));
                listView.setLayoutManager(new LinearLayoutManager(this));
                listView.setAdapter(adapter);
            } catch (Exception ex) {
                Debug.Warn(e);
                showErrorDialog(R.string.fail_display);
            }
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.hex_code);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    void showErrorDialog(int msgRes) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error_caps)
                .setMessage(msgRes)
                .setPositiveButton(R.string.close, (dialog, which) -> finish())
                .show();
        setResult(Activity.RESULT_OK, new Intent(NFCIntent.ACTION_FIX_BANK_DATA));
    }
}
