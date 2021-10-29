package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hiddenramblings.tagmo.adapter.HexDumpAdapter;
import com.hiddenramblings.tagmo.nfctech.KeyManager;
import com.hiddenramblings.tagmo.nfctech.TagUtils;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_hex_viewer)
public class HexViewerActivity extends AppCompatActivity {

    boolean isEncryptionMissing;

    @ViewById(R.id.gridView)
    RecyclerView listView;
    HexDumpAdapter adapter;

    @AfterViews
    void afterViews() {
        decryptTagData(getIntent().getByteArrayExtra(TagMo.EXTRA_TAG_DATA));
    }

    void decryptTagData(byte[] tagData) {
        KeyManager keyManager = new KeyManager(this);
        try {
            setAdapterTagData(TagUtils.decrypt(keyManager, tagData));
        } catch (Exception e) {
            isEncryptionMissing = !TagUtils.isEncrypted(tagData);
            if (isEncryptionMissing) {
                try {
                    setAdapterTagData(tagData);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    TagMo.Error(getClass(), R.string.invalid_tag_data);
                    finish();
                }
            } else {
                e.printStackTrace();
                TagMo.Error(getClass(), R.string.failed_decrypt);
                finish();
            }
        }
    }

    void setAdapterTagData(byte[] tagData) {
        adapter = new HexDumpAdapter(tagData);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.setAdapter(adapter);
    }
}
