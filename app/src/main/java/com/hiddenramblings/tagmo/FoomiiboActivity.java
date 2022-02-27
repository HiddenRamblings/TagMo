package com.hiddenramblings.tagmo;

import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.hiddenramblings.tagmo.adapter.FoomiiboAdapter;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.eightbit.Foomiibo;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.BrowserSettings.BrowserSettingsListener;
import com.hiddenramblings.tagmo.widget.Toasty;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;

public class FoomiiboActivity extends AppCompatActivity implements
        FoomiiboAdapter.OnFoomiiboClickListener {

    private final Foomiibo foomiibo = new Foomiibo();
    private final File directory = Storage.getDownloadDir("TagMo", "Foomiibo");
    private ProgressDialog dialog;

    private BrowserSettings settings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_foomiibo);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        setResult(RESULT_CANCELED);

        this.settings = new BrowserSettings().initialize();
        Executors.newSingleThreadExecutor().execute(() -> {
            AmiiboManager amiiboManager;
            try {
                amiiboManager = AmiiboManager.getAmiiboManager(getApplicationContext());
            } catch (IOException | JSONException | ParseException e) {
                Debug.Log(e);
                amiiboManager = null;
                new Toasty(this).Short(R.string.amiibo_info_parse_error);
            }

            if (Thread.currentThread().isInterrupted()) return;

            final AmiiboManager uiAmiiboManager = amiiboManager;
            this.runOnUiThread(() -> {
                settings.setAmiiboManager(uiAmiiboManager);
                settings.notifyChanges();
            });
        });

        ArrayList<Long> missingIds = new ArrayList<>();
        if (getIntent().hasExtra(NFCIntent.EXTRA_AMIIBO_LIST)) {
            ArrayList<String> missing = getIntent()
                    .getStringArrayListExtra(NFCIntent.EXTRA_AMIIBO_LIST);
            for (String amiiboId : missing) {
                missingIds.add(Long.parseLong(amiiboId));
            }
        }

        AppCompatImageView toggle = findViewById(R.id.toggle);
        BottomSheetBehavior<View> bottomSheetBehavior =
                BottomSheetBehavior.from(findViewById(R.id.bottom_sheet));
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        bottomSheetBehavior.addBottomSheetCallback(
                new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    toggle.setImageResource(R.drawable.ic_expand_less_white_24dp);
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    toggle.setImageResource(R.drawable.ic_expand_more_white_24dp);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) { }
        });

        toggle.setOnClickListener(view -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        RecyclerView amiibosView = findViewById(R.id.amiibos_list);

        if (this.settings.getAmiiboView() == BrowserSettings.VIEW.IMAGE.getValue())
            amiibosView.setLayoutManager(new GridLayoutManager(this, getColumnCount()));
        else
            amiibosView.setLayoutManager(new LinearLayoutManager(this));
        amiibosView.setAdapter(new FoomiiboAdapter(settings, missingIds, this));
        this.settings.addChangeListener((BrowserSettingsListener) amiibosView.getAdapter());

        SearchView searchView = findViewById(R.id.amiibo_search);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setSubmitButtonEnabled(false);
        searchView.setIconifiedByDefault(false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                settings.setQuery(query);
                settings.notifyChanges();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                settings.setQuery(query);
                settings.notifyChanges();
                return true;
            }
        });

        findViewById(R.id.clear_foomiibo_set).setOnClickListener(view -> {
            deleteDir(directory);
            setResult(RESULT_OK);
            finish();
        });

        findViewById(R.id.build_foomiibo_set).setOnClickListener(view -> buildFoomiiboSet());
    }

    private String decipherFilename(AmiiboManager amiiboManager, byte[] tagData) {
        try {
            long amiiboId = TagUtils.amiiboIdFromTag(tagData);
            String name = TagUtils.amiiboIdToHex(amiiboId);
            if (null != amiiboManager) {
                Amiibo amiibo = amiiboManager.amiibos.get(amiiboId);
                if (null != amiibo && null != amiibo.name) {
                    name = amiibo.name.replace(File.separatorChar, '-');
                }
            }

            byte[] uid = Arrays.copyOfRange(tagData, 0, 9);
            String uidHex = TagUtils.bytesToHex(uid);
            return String.format(Locale.ROOT, "%1$s[%2$s]-Foomiibo.bin", name, uidHex);
        } catch (Exception e) {
            Debug.Log(TagUtils.class, e.getMessage());
        }
        return "";
    }

    private int getColumnCount() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
        else
            mWindowManager.getDefaultDisplay().getMetrics(metrics);
        return (int) ((metrics.widthPixels / metrics.density) / 112 + 0.5);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void deleteDir(File dir) {
        if (!directory.exists()) return;
        File[] files = dir.listFiles();
        if (null != files && files.length > 0) {
            for (File file : files) {
                if (file.isDirectory())
                    deleteDir(file);
                else
                    file.delete();
            }
        }
        dir.delete();
    }

    private void buildFoomiiboFile(Amiibo amiibo) {
        try {
            byte[] tagData = foomiibo.generateData(TagUtils.amiiboIdToHex(amiibo.id));
            File directory = new File(this.directory, amiibo.getAmiiboSeries().name);
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
            TagUtils.writeBytesToFile(directory, decipherFilename(
                    settings.getAmiiboManager(), tagData), tagData);
        } catch (Exception e) {
            Debug.Log(e);
        }
    }

    private void buildFoomiiboSet() {
        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (null == amiiboManager) return;
        Handler handler = new Handler(Looper.getMainLooper());

        Executors.newSingleThreadExecutor().execute(() -> {
            handler.post(() -> dialog = ProgressDialog.show(FoomiiboActivity.this,
                    "", "", true));

            deleteDir(directory);
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();

            for (Amiibo amiibo : amiiboManager.amiibos.values()) {
                buildFoomiiboFile(amiibo);
                handler.post(() -> dialog.setMessage(getString(
                        R.string.foomiibo_progress, amiibo.getCharacter().name)));
            }
            handler.post(() -> dialog.dismiss());

            setResult(RESULT_OK);
            finish();
        });
    }

    private void onBuildFoomiibo(Amiibo amiibo) {
        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (null == amiiboManager) return;

        buildFoomiiboFile(amiibo);
        setResult(RESULT_OK);
    }

    @Override
    public void onFoomiiboClicked(Amiibo foomiibo) {
        onBuildFoomiibo(foomiibo);
    }

    @Override
    public void onFoomiiboImageClicked(Amiibo foomiibo) {
        onBuildFoomiibo(foomiibo);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
