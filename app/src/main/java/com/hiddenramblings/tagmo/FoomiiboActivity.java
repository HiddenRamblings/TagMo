package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.api.BackgroundExecutor;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Locale;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_foomiibo)
public class FoomiiboActivity extends AppCompatActivity implements
        FoomiiboAdapter.OnAmiiboClickListener {

    @ViewById(R.id.amiibos_list)
    RecyclerView amiibosView;

    private final Foomiibo foomiibo = new Foomiibo();
    private final File directory = Storage.getDownloadDir("TagMo", "Foomiibo");

    @InstanceState
    BrowserSettings settings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @AfterViews
    void afterViews() {
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        this.settings = new BrowserSettings().initialize();
        this.loadAmiiboManager();

        if (this.settings.getAmiiboView() == BrowserSettings.VIEW.IMAGE.getValue())
            this.amiibosView.setLayoutManager(new GridLayoutManager(this, getColumnCount()));
        else
            this.amiibosView.setLayoutManager(new LinearLayoutManager(this));
        this.amiibosView.setAdapter(new FoomiiboAdapter(settings, this));
        this.settings.addChangeListener((BrowserSettingsListener) this.amiibosView.getAdapter());

        setResult(RESULT_CANCELED);
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
    private static void deleteDir(File dir) {
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

    private static final String BACKGROUND_AMIIBO_MANAGER = "amiibo_manager";
    private void loadAmiiboManager() {
        BackgroundExecutor.cancelAll(BACKGROUND_AMIIBO_MANAGER, true);
        loadAmiiboManagerTask();
    }

    @Background(id = BACKGROUND_AMIIBO_MANAGER)
    void loadAmiiboManagerTask() {
        AmiiboManager amiiboManager;
        try {
            amiiboManager = AmiiboManager.getAmiiboManager();
        } catch (IOException | JSONException | ParseException e) {
            Debug.Log(e);
            amiiboManager = null;
            new Toasty(this).Short(R.string.amiibo_info_parse_error);
        }

        if (Thread.currentThread().isInterrupted())
            return;

        final AmiiboManager uiAmiiboManager = amiiboManager;
        this.runOnUiThread(() -> {
            settings.setAmiiboManager(uiAmiiboManager);
            settings.notifyChanges();
        });
    }

    @Click(R.id.build_foomiibo_series)
    @Background
    void onBuildFoomiiboClicked() {
        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (null == amiiboManager) return;

        if (directory.exists())
            deleteDir(directory);
        //noinspection ResultOfMethodCallIgnored
        directory.mkdirs();

        for (Amiibo amiibo : amiiboManager.amiibos.values()) {
            try {
                byte[] tagData = foomiibo.generateData(TagUtils.amiiboIdToHex(amiibo.id));
                File directory = new File(this.directory, amiibo.getAmiiboSeries().name);
                //noinspection ResultOfMethodCallIgnored
                directory.mkdirs();
                TagUtils.writeBytesToFile(directory, decipherFilename(
                        amiiboManager, tagData), tagData);
            } catch (Exception e) {
                Debug.Log(e);
            }
        }
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onAmiiboClicked(Amiibo amiibo) {
        try {
            byte[] tagData = foomiibo.generateData(TagUtils.amiiboIdToHex(amiibo.id));
            File directory = new File(this.directory, amiibo.getAmiiboSeries().name);
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
            TagUtils.writeBytesToFile(directory, decipherFilename(
                    settings.getAmiiboManager(), tagData), tagData);
            setResult(RESULT_OK);
        } catch (Exception e) {
            Debug.Log(e);
        }
    }

    @Override
    public void onAmiiboImageClicked(Amiibo amiibo) {
        try {
            byte[] tagData = foomiibo.generateData(TagUtils.amiiboIdToHex(amiibo.id));
            File directory = new File(this.directory, amiibo.getAmiiboSeries().name);
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
            TagUtils.writeBytesToFile(directory, decipherFilename(
                    settings.getAmiiboManager(), tagData), tagData);
            setResult(RESULT_OK);
        } catch (Exception e) {
            Debug.Log(e);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
