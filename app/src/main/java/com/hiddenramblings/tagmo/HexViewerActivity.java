package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.eightbit.io.Debug;
import com.hiddenramblings.tagmo.adapter.HexCodeAdapter;
import com.hiddenramblings.tagmo.nfctech.KeyManager;
import com.hiddenramblings.tagmo.nfctech.TagUtils;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
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
        try {
            setAdapterTagData(TagUtils.decrypt(keyManager, tagData));
        } catch (Exception e) {
            Debug.Error(e);
            Debug.Error(getClass(), R.string.fail_decrypt);
            finish();
        }
    }

    void setAdapterTagData(byte[] tagData) {
        adapter = new HexCodeAdapter(tagData);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.setAdapter(adapter);
    }
}
