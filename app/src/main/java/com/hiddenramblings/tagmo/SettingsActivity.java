package com.hiddenramblings.tagmo;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.Toast;

import com.hiddenramblings.tagmo.amiibo.AmiiboManager;

import org.androidannotations.annotations.AfterPreferences;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.PreferenceByKey;
import org.androidannotations.annotations.PreferenceClick;
import org.androidannotations.annotations.PreferenceScreen;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.sharedpreferences.Pref;
import org.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;

@PreferenceScreen(R.xml.settings)
@EActivity
public class SettingsActivity extends PreferenceActivity {
    private static final int RESULT_KEYS = 0;
    private static final int RESULT_IMPORT_AMIIBO_DATABASE = 1;

    @Pref
    Preferences_ prefs;

    @PreferenceByKey(R.string.settings_import_keys)
    Preference key;
    @PreferenceByKey(R.string.settings_enable_amiibo_browser)
    CheckBoxPreference enableAmiiboBrowser;
    @PreferenceByKey(R.string.settings_database_amiibos)
    Preference amiiboStats;
    @PreferenceByKey(R.string.settings_database_game_series)
    Preference gameSeriesStats;
    @PreferenceByKey(R.string.settings_database_characters)
    Preference characterStats;
    @PreferenceByKey(R.string.settings_database_amiibo_series)
    Preference amiiboSeriesStats;
    @PreferenceByKey(R.string.settings_database_amiibo_types)
    Preference amiiboTypeStats;

    KeyManager keyManager;
    AmiiboManager amiiboManager = null;

    @AfterPreferences
    protected void afterViews() {
        this.keyManager = new KeyManager(this);
        loadAmiiboManager();
        updateKeySummary();
        updateAmiiboStats();
    }

    @Background
    void loadAmiiboManager() {
        try {
            AmiiboManager amiiboManager = Util.loadAmiiboManager(this);
            setAmiiboManager(amiiboManager);
        } catch (IOException | JSONException | ParseException e) {
            e.printStackTrace();
            showToast("Unable to parse amiibo database");
        }
    }

    @Background
    void updateAmiiboManager(Uri data) {
        try {
            AmiiboManager amiiboManager = AmiiboManager.parse(this, data);
            Util.saveLocalAmiiboDatabase(this, amiiboManager);
            setAmiiboManager(amiiboManager);

            showToast("Updated amiibo database");
        } catch (IOException | ParseException | JSONException e) {
            e.printStackTrace();
            showToast("Unable to parse amiibo database");
        } catch (Util.AmiiboDatabaseException e) {
            e.printStackTrace();
            showToast(e.getMessage());
        }
    }

    @Background
    void resetAmiiboManager() {
        this.deleteFile(Util.AMIIBO_DATABASE_FILE);
        AmiiboManager amiiboManager = null;
        try {
            amiiboManager = Util.loadAmiiboManager(this);
        } catch (IOException | JSONException | ParseException e) {
            e.printStackTrace();
            showToast("Unable to parse amiibo database");
        }

        setAmiiboManager(amiiboManager);
        showToast("Reset amiibo database");
    }

    @UiThread
    void setAmiiboManager(AmiiboManager amiiboManager) {
        this.amiiboManager = amiiboManager;
        this.updateAmiiboStats();
    }

    void updateAmiiboStats() {
        String amiiboCount = "0";
        String gameSeriesCount = "0";
        String characterCount = "0";
        String amiiboSeriesCount = "0";
        String amiiboTypeCount = "0";
        if (amiiboManager != null) {
            amiiboCount = String.valueOf(amiiboManager.amiibos.size());
            gameSeriesCount = String.valueOf(amiiboManager.gameSeries.size());
            characterCount = String.valueOf(amiiboManager.characters.size());
            amiiboSeriesCount = String.valueOf(amiiboManager.amiiboSeries.size());
            amiiboTypeCount = String.valueOf(amiiboManager.amiiboTypes.size());
        }
        this.amiiboStats.setSummary(amiiboCount);
        this.gameSeriesStats.setSummary(gameSeriesCount);
        this.characterStats.setSummary(characterCount);
        this.amiiboSeriesStats.setSummary(amiiboSeriesCount);
        this.amiiboTypeStats.setSummary(amiiboTypeCount);
    }

    @PreferenceClick(R.string.settings_import_keys)
    void onKeysClicked() {
        showFileChooser("Fixed Key", "*/*", RESULT_KEYS);
    }

    @UiThread
    void updateKeySummary() {
        String unfixedText;
        ForegroundColorSpan unfixedSpan;
        if (this.keyManager.hasUnFixedKey()) {
            unfixedText = getString(R.string.unfixed_key_found);
            unfixedSpan = new ForegroundColorSpan(Color.rgb(0x00, 0xAf, 0x00));
        } else {
            unfixedText = getString(R.string.unfixed_key_missing);
            unfixedSpan = new ForegroundColorSpan(Color.RED);
        }
        SpannableStringBuilder unfixedBuilder = new SpannableStringBuilder(unfixedText);
        unfixedBuilder.setSpan(unfixedSpan, 0, unfixedText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        String fixedText;
        ForegroundColorSpan fixedSpan;
        if (this.keyManager.hasFixedKey()) {
            fixedText = getString(R.string.fixed_key_found);
            fixedSpan = new ForegroundColorSpan(Color.rgb(0x00, 0xAf, 0x00));
        } else {
            fixedText = getString(R.string.fixed_key_missing);
            fixedSpan = new ForegroundColorSpan(Color.RED);
        }
        SpannableStringBuilder fixedBuilder = new SpannableStringBuilder(fixedText);
        fixedBuilder.setSpan(fixedSpan, 0, fixedText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        SpannableStringBuilder keySummary = new SpannableStringBuilder();
        keySummary.append(unfixedBuilder);
        keySummary.append("\n");
        keySummary.append(fixedBuilder);

        key.setSummary(keySummary);
    }

    @Background
    void saveKeys(Uri data) {
        try {
            this.keyManager.loadKey(data);
        } catch (Exception e) {
            e.printStackTrace();
            showToast(e.getMessage());
        }
        updateKeySummary();
    }

    @PreferenceClick(R.string.settings_import_database)
    void onImportDatabaseClicked() {
        showFileChooser("Fixed Key", "*/*", RESULT_IMPORT_AMIIBO_DATABASE);
    }

    @PreferenceClick(R.string.settings_export_database)
    void onExportDatabaseClicked() {
        File file = new File(Util.getDataDir(), Util.AMIIBO_DATABASE_FILE);
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file);
            Util.saveAmiiboDatabase(this.amiiboManager, fileOutputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (Util.AmiiboDatabaseException e) {
            e.printStackTrace();
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        showToast("Exported amiibo database to " + Util.friendlyPath(file.getAbsolutePath()));
    }

    @PreferenceClick(R.string.settings_reset_database)
    void onResetDatabaseClicked() {
        resetAmiiboManager();
    }

    @PreferenceClick(R.string.settings_enable_amiibo_browser)
    void onEnableAmiiboBrowserClicked() {
        prefs.enableAmiiboBrowser().put(enableAmiiboBrowser.isChecked());
    }

    private void showFileChooser(String title, String mimeType, int resultCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent, title), resultCode);
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e("", ex.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK)
            return;

        switch (requestCode) {
            case RESULT_KEYS:
                saveKeys(data.getData());
                break;
            case RESULT_IMPORT_AMIIBO_DATABASE:
                updateAmiiboManager(data.getData());
                break;
        }
    }

    @UiThread
    public void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}