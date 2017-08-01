package com.hiddenramblings.tagmo;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.annotation.NonNull;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.AmiiboSeries;
import com.hiddenramblings.tagmo.amiibo.AmiiboType;
import com.hiddenramblings.tagmo.amiibo.Character;
import com.hiddenramblings.tagmo.amiibo.GameSeries;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

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

    @PreferenceClick(R.string.settings_database_amiibos)
    void onAmiiboStatsClicked() {
        final ArrayList<Amiibo> items = new ArrayList<>(amiiboManager.amiibos.values());
        Collections.sort(items);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Amiibos")
            .setAdapter(new BaseAdapter() {
                @Override
                public int getCount() {
                    return items.size();
                }

                @Override
                public long getItemId(int i) {
                    return items.get(i).id;
                }

                @Override
                public Amiibo getItem(int i) {
                    return items.get(i);
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    ViewHolder holder;
                    if (convertView == null) {
                        convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.amiibo_item_view, parent, false);
                        holder = new ViewHolder(convertView);
                    } else {
                        holder = (ViewHolder) convertView.getTag();
                    }

                    String tagInfo = "";
                    String amiiboHexId = "";
                    String amiiboName = "";
                    String amiiboSeries = "";
                    String amiiboType = "";
                    String gameSeries = "";
                    String character = "";

                    Amiibo amiibo = getItem(position);
                    amiiboHexId = TagUtil.amiiboIdToHex(amiibo.id);
                    if (amiibo.name != null)
                        amiiboName = amiibo.name;
                    if (amiibo.getAmiiboSeries() != null)
                        amiiboSeries = amiibo.getAmiiboSeries().name;
                    if (amiibo.getAmiiboType() != null)
                        amiiboType = amiibo.getAmiiboType().name;
                    if (amiibo.getGameSeries() != null)
                        gameSeries = amiibo.getGameSeries().name;
                    if (amiibo.getCharacter() != null)
                        character = amiibo.getCharacter().name;

                    holder.txtTagInfo.setText(tagInfo);
                    setAmiiboInfoText(holder.txtName, amiiboName, !tagInfo.isEmpty());
                    setAmiiboInfoText(holder.txtTagId, amiiboHexId, !tagInfo.isEmpty());
                    setAmiiboInfoText(holder.txtAmiiboSeries, amiiboSeries, !tagInfo.isEmpty());
                    setAmiiboInfoText(holder.txtAmiiboType, amiiboType, !tagInfo.isEmpty());
                    setAmiiboInfoText(holder.txtGameSeries, gameSeries, !tagInfo.isEmpty());
                    setAmiiboInfoText(holder.txtCharacter, character, !tagInfo.isEmpty());

                    return convertView;
                }

                void setAmiiboInfoText(TextView textView, CharSequence text, boolean hasTagInfo) {
                    if (hasTagInfo) {
                        textView.setText("");
                    } else if (text.length() == 0) {
                        textView.setText("Unknown");
                        textView.setTextColor(Color.RED);
                    } else {
                        textView.setText(text);
                        textView.setTextColor(textView.getTextColors().getDefaultColor());
                    }
                }

                class ViewHolder {
                    TextView txtTagInfo;
                    TextView txtName;
                    TextView txtTagId;
                    TextView txtAmiiboSeries;
                    TextView txtAmiiboType;
                    TextView txtGameSeries;
                    TextView txtCharacter;
                    TextView txtPath;

                    public ViewHolder(View view) {
                        this.txtTagInfo = ((TextView) view.findViewById(R.id.txtTagInfo));
                        this.txtName = ((TextView) view.findViewById(R.id.txtName));
                        this.txtTagId = ((TextView) view.findViewById(R.id.txtTagId));
                        this.txtAmiiboSeries = ((TextView) view.findViewById(R.id.txtAmiiboSeries));
                        this.txtAmiiboType = ((TextView) view.findViewById(R.id.txtAmiiboType));
                        this.txtGameSeries = ((TextView) view.findViewById(R.id.txtGameSeries));
                        this.txtCharacter = ((TextView) view.findViewById(R.id.txtCharacter));
                        this.txtPath = ((TextView) view.findViewById(R.id.txtPath));

                        view.setTag(this);
                    }
                }
            }, null)
            .setPositiveButton("Close", null)
            .show();
    }

    @PreferenceClick(R.string.settings_database_game_series)
    void onGameSeriesStatsClicked() {
        final ArrayList<String> items = new ArrayList<>();
        for (GameSeries gameSeries : amiiboManager.gameSeries.values()) {
            if (!items.contains(gameSeries.name))
                items.add(gameSeries.name);
        }
        Collections.sort(items);

        new AlertDialog.Builder(this)
            .setTitle("Game Series")
            .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items), null)
            .setPositiveButton("Close", null)
            .show();
    }

    @PreferenceClick(R.string.settings_database_characters)
    void onCharacterStatsClicked() {
        final ArrayList<Character> items = new ArrayList<>();
        for (Character character : amiiboManager.characters.values()) {
            if (!items.contains(character))
                items.add(character);
        }
        Collections.sort(items);

        new AlertDialog.Builder(this)
            .setTitle("Characters")
            .setAdapter(new ArrayAdapter<Character>(this, android.R.layout.simple_list_item_2, android.R.id.text1, items) {
                @NonNull
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text1 = (TextView) view.findViewById(android.R.id.text1);
                    TextView text2 = (TextView) view.findViewById(android.R.id.text2);

                    Character character = getItem(position);
                    text1.setText(character.name);

                    GameSeries gameSeries = character.getGameSeries();
                    text2.setText(gameSeries == null ? "" : gameSeries.name);

                    return view;
                }
            }, null)
            .setPositiveButton("Close", null)
            .show();
    }

    @PreferenceClick(R.string.settings_database_amiibo_series)
    void onAmiiboSeriesStatsClicked() {
        final ArrayList<String> items = new ArrayList<>();
        for (AmiiboSeries amiiboSeries : amiiboManager.amiiboSeries.values()) {
            if (!items.contains(amiiboSeries.name))
                items.add(amiiboSeries.name);
        }
        Collections.sort(items);

        new AlertDialog.Builder(this)
            .setTitle("Amiibo Series")
            .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items), null)
            .setPositiveButton("Close", null)
            .show();
    }

    @PreferenceClick(R.string.settings_database_amiibo_types)
    void onAmiiboTypesStatsClicked() {
        final ArrayList<AmiiboType> amiiboTypes = new ArrayList<>(amiiboManager.amiiboTypes.values());
        Collections.sort(amiiboTypes);

        final ArrayList<String> items = new ArrayList<>();
        for (AmiiboType amiiboType : amiiboTypes) {
            if (!items.contains(amiiboType.name))
                items.add(amiiboType.name);
        }

        new AlertDialog.Builder(this)
            .setTitle("Amiibo Types")
            .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items), null)
            .setPositiveButton("Close", null)
            .show();
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