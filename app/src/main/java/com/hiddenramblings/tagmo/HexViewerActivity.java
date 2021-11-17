package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hiddenramblings.tagmo.adapter.HexCodeAdapter;
import com.hiddenramblings.tagmo.amiibo.KeyManager;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_hex_viewer)
public class HexViewerActivity extends AppCompatActivity {

    @ViewById(R.id.gridView)
    RecyclerView listView;
    HexCodeAdapter adapter;

    @AfterViews
    void afterViews() {
        decryptTagData(getIntent().getByteArrayExtra(TagMo.EXTRA_TAG_DATA));
    }

    void decryptTagData(byte[] tagData) {
        KeyManager keyManager = new KeyManager(this);
        if (keyManager.isKeyMissing()) {
            showErrorDialog(R.string.no_decrypt_key);
            return;
        }
        try {
            tagData = keyManager.decrypt(tagData);
        } catch (Exception e) {
            // Decryption is not a necessary step
        }
        setAdapterTagData(tagData);
    }

    void setAdapterTagData(byte[] tagData) {
        adapter = new HexCodeAdapter(tagData);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.setAdapter(adapter);
    }

    @UiThread
    void showErrorDialog(int msgRes) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error_caps)
                .setMessage(msgRes)
                .setPositiveButton(R.string.close, (dialog, which) -> finish())
                .show();
    }
}
