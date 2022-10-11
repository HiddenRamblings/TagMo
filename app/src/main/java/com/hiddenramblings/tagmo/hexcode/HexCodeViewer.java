package com.hiddenramblings.tagmo.hexcode;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.collection.LruCache;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hiddenramblings.tagmo.NFCIntent;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.nfctech.TagArray;
import com.hiddenramblings.tagmo.widget.Toasty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

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
                adapter = new HexAdapter(TagArray.getValidatedData(keyManager, tagData));
                listView.setLayoutManager(new LinearLayoutManager(this));
                listView.setAdapter(adapter);
            } catch (Exception ex) {
                Debug.Warn(e);
                showErrorDialog(R.string.fail_display);
            }
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.hex_code);
        toolbar.inflateMenu(R.menu.save_menu);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.mnu_save) {
                try {
                    saveHexViewToFile(listView, Amiibo.idToHex(Amiibo.dataToId(tagData)));
                } catch (IOException e) {
                    saveHexViewToFile(listView, TagArray.bytesToHex(
                            Arrays.copyOfRange(tagData, 0, 9))
                    );
                }
                return true;
            }
            return false;
        });
    }

    private void saveHexViewToFile(@NonNull RecyclerView view, String filename) {
        HexAdapter adapter = (HexAdapter) view.getAdapter();
        Bitmap viewBitmap = null;
        if (adapter != null) {
            int size = adapter.getItemCount();
            int height = 0;
            Paint paint = new Paint();
            int iHeight = 0;
            final int cacheSize = (int) (Runtime.getRuntime().maxMemory() / 1024) / 16;
            LruCache<String, Bitmap> bitmaCache = new LruCache<>(cacheSize);
            for (int i = 0; i < size; i++) {
                HexAdapter.ViewHolder holder = adapter.createViewHolder(
                        view, adapter.getItemViewType(i)
                );
                adapter.onBindViewHolder(holder, i);
                holder.itemView.measure(View.MeasureSpec.makeMeasureSpec(
                        view.getWidth(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );
                holder.itemView.layout(
                        0, 0,
                        holder.itemView.getMeasuredWidth(),
                        holder.itemView.getMeasuredHeight()
                );
                holder.itemView.setDrawingCacheEnabled(true);
                holder.itemView.buildDrawingCache();
                Bitmap drawingCache = holder.itemView.getDrawingCache();
                if (drawingCache != null) {

                    bitmaCache.put(String.valueOf(i), drawingCache);
                }
                height += holder.itemView.getMeasuredHeight();
            }

            viewBitmap = Bitmap.createBitmap(
                    view.getMeasuredWidth(), height, Bitmap.Config.ARGB_8888
            );
            Canvas bigCanvas = new Canvas(viewBitmap);
            bigCanvas.drawColor(Color.BLACK);

            for (int i = 0; i < size; i++) {
                Bitmap bitmap = bitmaCache.get(String.valueOf(i));
                bigCanvas.drawBitmap(bitmap, 0f, iHeight, paint);
                if (null != bitmap) {
                    iHeight += bitmap.getHeight();
                    bitmap.recycle();
                }
            }
        }

        if (null == viewBitmap) {
            new Toasty(HexCodeViewer.this).Short(getString(R.string.fail_bitmap));
            return;
        }
        File dir = new File(Storage.getDownloadDir("TagMo"), "HexCode");
        if (!dir.exists() && !dir.mkdirs()) {
            new Toasty(HexCodeViewer.this).Short(
                    getString(R.string.mkdir_failed, dir.getName())
            );
            return;
        }
        File file = new File(dir, filename + "-" + System.currentTimeMillis() + ".png");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            viewBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            new Toasty(HexCodeViewer.this).Short(
                    getString(R.string.wrote_file, Storage.getRelativePath(
                            file, TagMo.getPrefs().preferEmulated().get()
                    ))
            );
        } catch (IOException e) {
            Debug.Warn(e);
        }
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
