package com.hiddenramblings.tagmo.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.security.ProviderInstaller;
import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.NfcActivity_;
import com.hiddenramblings.tagmo.Preferences_;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.adapter.SettingsAmiiboAdapter;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.AmiiboSeries;
import com.hiddenramblings.tagmo.amiibo.AmiiboType;
import com.hiddenramblings.tagmo.amiibo.Character;
import com.hiddenramblings.tagmo.amiibo.GameSeries;
import com.hiddenramblings.tagmo.github.RequestStamp;
import com.hiddenramblings.tagmo.nfc.KeyManager;

import org.androidannotations.annotations.AfterPreferences;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.PreferenceByKey;
import org.androidannotations.annotations.PreferenceChange;
import org.androidannotations.annotations.PreferenceClick;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.sharedpreferences.Pref;
import org.androidannotations.api.BackgroundExecutor;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;

@SuppressLint("NonConstantResourceId")
@EFragment
public class SettingsFragment extends PreferenceFragmentCompat {

    public static final String IMAGE_NETWORK_NEVER = "NEVER";
    public static final String IMAGE_NETWORK_WIFI = "WIFI_ONLY";
    public static final String IMAGE_NETWORK_ALWAYS = "ALWAYS";

    private static final int RESULT_KEYS = 0;
    private static final int RESULT_IMPORT_AMIIBO_DATABASE = 1;

    @Pref
    Preferences_ prefs;

    @PreferenceByKey(R.string.settings_import_keys)
    Preference key;
    @PreferenceByKey(R.string.settings_enable_tag_type_validation)
    CheckBoxPreference enableTagTypeValidation;
    @PreferenceByKey(R.string.settings_enable_power_tag_support)
    CheckBoxPreference enablePowerTagSupport;
    @PreferenceByKey(R.string.settings_enable_elite_support)
    CheckBoxPreference enableEliteSupport;
    @PreferenceByKey(R.string.lock_elite_hardware)
    Preference lockEliteHardware;
    @PreferenceByKey(R.string.settings_info_amiibos)
    Preference amiiboStats;
    @PreferenceByKey(R.string.settings_info_game_series)
    Preference gameSeriesStats;
    @PreferenceByKey(R.string.settings_info_characters)
    Preference characterStats;
    @PreferenceByKey(R.string.settings_info_amiibo_series)
    Preference amiiboSeriesStats;
    @PreferenceByKey(R.string.settings_info_amiibo_types)
    Preference amiiboTypeStats;
    @PreferenceByKey(R.string.image_network_settings)
    ListPreference imageNetworkSetting;
    @PreferenceByKey(R.string.settings_disable_debug)
    CheckBoxPreference disableDebug;
    @PreferenceByKey(R.string.settings_ignore_sdcard)
    CheckBoxPreference ignoreSdcard;
    @PreferenceByKey(R.string.settings_stable_channel)
    CheckBoxPreference stableChannel;

    KeyManager keyManager;
    AmiiboManager amiiboManager = null;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preference_screen);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new RequestStamp().setListener(result -> {
            if (result != null && prefs.lastModified().get() < result) {
                showInstallSnackbar();
            }
        }).execute(TagMo.getStringRes(R.string.amiibo_api_raw, R.string.api_database));
    }

    @AfterPreferences
    protected void afterViews() {
        this.enableTagTypeValidation.setChecked(prefs.enableTagTypeValidation().get());
        this.disableDebug.setChecked(prefs.disableDebug().get());
        this.ignoreSdcard.setChecked(prefs.ignoreSdcard().get());
        this.stableChannel.setChecked(prefs.stableChannel().get());

        this.keyManager = new KeyManager(this.getContext());

        loadAmiiboManager();
        updateKeySummary();
        updateAmiiboStats();
        onImageNetworkChange(prefs.imageNetworkSetting().get());

        boolean isElite = prefs.enableEliteSupport().get();
        this.enableEliteSupport.setChecked(isElite);
        if (isElite && prefs.eliteSignature().get().length() > 1) {
            this.enableEliteSupport.setSummary(getString(
                    R.string.elite_details_enabled, prefs.eliteSignature().get()));
        }
        lockEliteHardware.setVisible(isElite);
    }

    @PreferenceClick(R.string.settings_import_keys)
    void onKeysClicked() {
        showFileChooser("Fixed Key", "*/*", RESULT_KEYS);
    }

    @PreferenceClick(R.string.settings_enable_tag_type_validation)
    void onEnableTagTypeValidationClicked() {
        prefs.enableTagTypeValidation().put(enableTagTypeValidation.isChecked());
    }

    @PreferenceClick(R.string.settings_enable_power_tag_support)
    void onEnablePowerTagSupportClicked() {
        if (enablePowerTagSupport.isChecked() && enableEliteSupport.isChecked()) {
            showToast(R.string.hardware_conflict, Toast.LENGTH_LONG);
            return;
        }
        prefs.enablePowerTagSupport().put(enablePowerTagSupport.isChecked());
    }

    @PreferenceClick(R.string.settings_enable_elite_support)
    void onEnableEliteSupportClicked() {
        boolean isEnabled = enableEliteSupport.isChecked();
        if (enableEliteSupport.isChecked() && enablePowerTagSupport.isChecked()) {
            showToast(R.string.hardware_conflict, Toast.LENGTH_LONG);
            return;
        }
        prefs.enableEliteSupport().put(enableEliteSupport.isChecked());
        if (isEnabled && prefs.eliteSignature().get().length() > 1)
            enableEliteSupport.setSummary(getString(
                    R.string.elite_details_enabled, prefs.eliteSignature().get()));
        else
            enableEliteSupport.setSummary(getString(R.string.elite_details));
        lockEliteHardware.setVisible(isEnabled);
    }

    @PreferenceClick(R.string.lock_elite_hardware)
    void onLockEliteHardwareClicked() {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.lock_elite_warning)
                .setPositiveButton(R.string.proceed, (dialog, which) -> {
                    Intent lock = new Intent(requireContext(), NfcActivity_.class);
                    lock.setAction(TagMo.ACTION_LOCK_AMIIBO);
                    startActivity(lock);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    @PreferenceClick(R.string.settings_import_info_amiiboapi)
    void onSyncAmiiboAPIClicked() {
        downloadAmiiboAPIData();
    }

    @PreferenceClick(R.string.settings_import_info)
    void onImportInfoClicked() {
        showFileChooser(getString(R.string.fixed_key), "*/*", RESULT_IMPORT_AMIIBO_DATABASE);
    }

    @PreferenceClick(R.string.settings_export_info)
    void onExportInfoClicked() {
        if (this.amiiboManager == null) {
            showToast(R.string.amiibo_info_not_loaded, Toast.LENGTH_SHORT);
            return;
        }

        File file = new File(TagMo.getTagMoFiles(), AmiiboManager.AMIIBO_DATABASE_FILE);
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file);
            AmiiboManager.saveDatabase(this.amiiboManager, fileOutputStream);
        } catch (JSONException | IOException e) {
            e.printStackTrace();
            showToast(R.string.amiibo_info_export_fail, TagMo.friendlyPath(file), Toast.LENGTH_SHORT);
            return;
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        showSnackbar(getString(R.string.amiibo_info_exported,
                TagMo.friendlyPath(file)), Snackbar.LENGTH_LONG);
    }

    @PreferenceClick(R.string.settings_reset_info)
    void onResetInfoClicked() {
        resetAmiiboManager();
    }

    @PreferenceClick(R.string.settings_info_amiibos)
    void onAmiiboStatsClicked() {
        new AlertDialog.Builder(this.getContext())
                .setTitle(R.string.pref_amiibos)
                .setAdapter(new SettingsAmiiboAdapter(prefs, new ArrayList<>(
                        amiiboManager.amiibos.values())), null)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    @PreferenceClick(R.string.settings_info_game_series)
    void onGameSeriesStatsClicked() {
        final ArrayList<String> items = new ArrayList<>();
        for (GameSeries gameSeries : amiiboManager.gameSeries.values()) {
            if (!items.contains(gameSeries.name))
                items.add(gameSeries.name);
        }
        Collections.sort(items);

        new AlertDialog.Builder(this.getContext())
                .setTitle(R.string.pref_amiibo_game)
                .setAdapter(new ArrayAdapter<>(this.getContext(), android.R.layout.simple_list_item_1, items), null)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    @PreferenceChange(R.string.image_network_settings)
    void onImageNetworkChange(String newValue) {
        int index = imageNetworkSetting.findIndexOfValue(newValue);
        if (index == -1) {
            onImageNetworkChange(IMAGE_NETWORK_ALWAYS);
        } else {
            prefs.imageNetworkSetting().put(newValue);
            imageNetworkSetting.setValue(newValue);
            imageNetworkSetting.setSummary(imageNetworkSetting.getEntries()[index]);
        }
    }

    @PreferenceClick(R.string.settings_info_characters)
    void onCharacterStatsClicked() {
        final ArrayList<Character> items = new ArrayList<>();
        for (Character character : amiiboManager.characters.values()) {
            if (!items.contains(character))
                items.add(character);
        }
        Collections.sort(items);

        new AlertDialog.Builder(this.getContext())
                .setTitle(R.string.pref_amiibo_characters)
                .setAdapter(new ArrayAdapter<Character>(this.getContext(), android.R.layout.simple_list_item_2, android.R.id.text1, items) {
                    @NonNull
                    @Override
                    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        TextView text1 = view.findViewById(android.R.id.text1);
                        TextView text2 = view.findViewById(android.R.id.text2);

                        Character character = getItem(position);
                        text1.setText(character.name);

                        GameSeries gameSeries = character.getGameSeries();
                        text2.setText(gameSeries == null ? "" : gameSeries.name);

                        return view;
                    }
                }, null)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    @PreferenceClick(R.string.settings_info_amiibo_series)
    void onAmiiboSeriesStatsClicked() {
        final ArrayList<String> items = new ArrayList<>();
        for (AmiiboSeries amiiboSeries : amiiboManager.amiiboSeries.values()) {
            if (!items.contains(amiiboSeries.name))
                items.add(amiiboSeries.name);
        }
        Collections.sort(items);

        new AlertDialog.Builder(this.getContext())
                .setTitle(R.string.pref_amiibo_series)
                .setAdapter(new ArrayAdapter<>(this.getContext(), android.R.layout.simple_list_item_1, items), null)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    @PreferenceClick(R.string.settings_info_amiibo_types)
    void onAmiiboTypesStatsClicked() {
        final ArrayList<AmiiboType> amiiboTypes = new ArrayList<>(amiiboManager.amiiboTypes.values());
        Collections.sort(amiiboTypes);

        final ArrayList<String> items = new ArrayList<>();
        for (AmiiboType amiiboType : amiiboTypes) {
            if (!items.contains(amiiboType.name))
                items.add(amiiboType.name);
        }

        new AlertDialog.Builder(this.getContext())
                .setTitle(R.string.pref_amiibo_types)
                .setAdapter(new ArrayAdapter<>(this.getContext(), android.R.layout.simple_list_item_1, items), null)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    @PreferenceClick(R.string.settings_disable_debug)
    void onDisableDebugClicked() {
        prefs.disableDebug().put(disableDebug.isChecked());
    }

    @PreferenceClick(R.string.settings_ignore_sdcard)
    void onIgnoreSdcardClicked() {
        if (getActivity() != null) getActivity().setResult(Activity.RESULT_OK,
                new Intent().putExtra("REFRESH", true));
        prefs.ignoreSdcard().put(ignoreSdcard.isChecked());
    }

    @PreferenceClick(R.string.settings_stable_channel)
    void onStableChannelClicked() {
        prefs.stableChannel().put(stableChannel.isChecked());
    }

    private static final String BACKGROUND_LOAD_KEYS = "load_keys";

    void updateKeys(Uri data) {
        BackgroundExecutor.cancelAll(BACKGROUND_LOAD_KEYS, true);
        updateKeysTask(data);
    }

    @Background(id = BACKGROUND_LOAD_KEYS)
    void updateKeysTask(Uri data) {
        try {
            this.keyManager.loadKey(data);
        } catch (Exception e) {
            e.printStackTrace();
            showSnackbar(e.getMessage(), Snackbar.LENGTH_SHORT);
        }
        if (Thread.currentThread().isInterrupted())
            return;

        updateKeySummary();
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

    private static final String BACKGROUND_AMIIBO_MANAGER = "amiibo_manager";

    void loadAmiiboManager() {
        BackgroundExecutor.cancelAll(BACKGROUND_AMIIBO_MANAGER, true);
        loadAmiiboManagerTask();
    }

    @Background(id = BACKGROUND_AMIIBO_MANAGER)
    void loadAmiiboManagerTask() {
        AmiiboManager amiiboManager;
        try {
            amiiboManager = AmiiboManager.getAmiiboManager();
        } catch (IOException | JSONException | ParseException e) {
            e.printStackTrace();
            showToast(R.string.amiibo_failure_generic,
                    getString(R.string.amiibo_failure_load), Toast.LENGTH_SHORT);
            return;
        }
        if (Thread.currentThread().isInterrupted())
            return;

        setAmiiboManager(amiiboManager);
    }

    void updateAmiiboManager(Uri data) {
        BackgroundExecutor.cancelAll(BACKGROUND_AMIIBO_MANAGER, true);
        updateAmiiboManagerTask(data);
    }

    @Background(id = BACKGROUND_AMIIBO_MANAGER)
    void updateAmiiboManagerTask(Uri data) {
        AmiiboManager amiiboManager;
        try {
            amiiboManager = AmiiboManager.parse(requireContext(), data);
        } catch (JSONException | ParseException e) {
            e.printStackTrace();
            showToast(R.string.amiibo_failure_generic,
                    getString(R.string.amiibo_failure_parse), Toast.LENGTH_SHORT);
            return;
        } catch (IOException e) {
            e.printStackTrace();
            showToast(R.string.amiibo_failure_generic,
                    getString(R.string.amiibo_failure_read), Toast.LENGTH_SHORT);
            return;
        }
        if (Thread.currentThread().isInterrupted())
            return;

        try {
            AmiiboManager.saveDatabase(amiiboManager);
        } catch (JSONException | IOException e) {
            e.printStackTrace();
            showToast(R.string.amiibo_failure_generic,
                    getString(R.string.amiibo_failure_update), Toast.LENGTH_SHORT);
            return;
        }
        if (Thread.currentThread().isInterrupted())
            return;

        setAmiiboManager(amiiboManager);
        showSnackbar(getString(R.string.amiibo_info_updated), Snackbar.LENGTH_SHORT);
    }

    void resetAmiiboManager() {
        BackgroundExecutor.cancelAll(BACKGROUND_AMIIBO_MANAGER, true);
        resetAmiiboManagerTask();
    }

    @Background(id = BACKGROUND_AMIIBO_MANAGER)
    void resetAmiiboManagerTask() {
        requireContext().deleteFile(AmiiboManager.AMIIBO_DATABASE_FILE);

        AmiiboManager amiiboManager = null;
        try {
            amiiboManager = AmiiboManager.getDefaultAmiiboManager();
        } catch (IOException | JSONException | ParseException e) {
            e.printStackTrace();
            showToast(R.string.amiibo_failure_generic,
                    getString(R.string.amiibo_failure_parse_default), Snackbar.LENGTH_SHORT);
        }
        if (Thread.currentThread().isInterrupted())
            return;

        setAmiiboManager(amiiboManager);
        showSnackbar(getString(R.string.reset_amiibo_info), Snackbar.LENGTH_SHORT);
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
        this.amiiboStats.setSummary(getString(R.string.total, amiiboCount));
        this.gameSeriesStats.setSummary(getString(R.string.total, gameSeriesCount));
        this.characterStats.setSummary(getString(R.string.total, characterCount));
        this.amiiboSeriesStats.setSummary(getString(R.string.total, amiiboSeriesCount));
        this.amiiboTypeStats.setSummary(getString(R.string.total, amiiboTypeCount));
    }

    private static final String BACKGROUND_SYNC_AMIIBO_MANAGER = "sync_amiibo_manager";

    void downloadAmiiboAPIDataCompat() {
        ProviderInstaller.installIfNeededAsync(requireContext(), new ProviderInstaller.ProviderInstallListener() {
            @Override
            public void onProviderInstalled() {
                BackgroundExecutor.cancelAll(BACKGROUND_SYNC_AMIIBO_MANAGER, true);
                downloadAmiiboAPIDataTask();
            }

            @Override
            public void onProviderInstallFailed(int errorCode, Intent recoveryIntent) {
                GoogleApiAvailability availability = GoogleApiAvailability.getInstance();
                if (availability.isUserResolvableError(errorCode)) {
                    // Recoverable error. Show a dialog prompting the user to
                    // install/update/enable Google Play services.
                    availability.showErrorDialogFragment(requireActivity(), errorCode, 1, dialog -> {
                        // The user chose not to take the recovery action
                        requireActivity().finish();
                    });
                } else {
                    // Google Play services is not available.
                    requireActivity().finish();
                }
            }
        });
    }

    void downloadAmiiboAPIData() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BackgroundExecutor.cancelAll(BACKGROUND_SYNC_AMIIBO_MANAGER, true);
            downloadAmiiboAPIDataTask();
        } else {
            downloadAmiiboAPIDataCompat();
        }
    }

    @Background(id = BACKGROUND_SYNC_AMIIBO_MANAGER)
    void downloadAmiiboAPIDataTask() {
        showSnackbar(getString(R.string.sync_amiibo_status,
                getString(R.string.sync_processing)), Snackbar.LENGTH_INDEFINITE);
        try {
            URL url = new URL(getString(R.string.amiibo_api_url));
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");

            int statusCode = urlConnection.getResponseCode();
            if (statusCode == 200) {
                InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());

                BufferedReader reader = null;
                StringBuilder response = new StringBuilder();
                try {
                    reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                String json = response.toString();
                AmiiboManager amiiboManager = AmiiboManager.parseAmiiboAPI(new JSONObject(json));
                if (Thread.currentThread().isInterrupted())
                    return;

                AmiiboManager.saveDatabase(amiiboManager);
                setAmiiboManager(amiiboManager);
                showSnackbar(getString(R.string.sync_amiibo_status,
                        getString(R.string.sync_complete)), Snackbar.LENGTH_SHORT);
                prefs.lastModified().put(System.currentTimeMillis());
            } else {
                throw new Exception(String.valueOf(statusCode));
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (Thread.currentThread().isInterrupted())
                return;
            showSnackbar(getString(R.string.sync_amiibo_status,
                    getString(R.string.sync_failed)), Snackbar.LENGTH_SHORT);
        }
    }

    ActivityResultLauncher<Intent> onLoadKeys = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK
                || result.getData() == null)
            return;
        updateKeys(result.getData().getData());
    });

    ActivityResultLauncher<Intent> onImportAmiiboDatabase = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK
                || result.getData() == null)
            return;
        updateAmiiboManager(result.getData().getData());
    });

    private void showFileChooser(String title, String mimeType, int resultCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        switch(resultCode) {
            case RESULT_KEYS:
                try {
                    onLoadKeys.launch(Intent.createChooser(intent, title));
                } catch (android.content.ActivityNotFoundException ex) {
                    TagMo.Error(getClass(), ex.getMessage());
                }
                break;
            case RESULT_IMPORT_AMIIBO_DATABASE:
                try {
                    onImportAmiiboDatabase.launch(Intent.createChooser(intent, title));
                } catch (android.content.ActivityNotFoundException ex) {
                    TagMo.Error(getClass(), ex.getMessage());
                }
                break;
        }
    }

    public Snackbar buildSnackbar(String msg, int length) {
        Snackbar snackbar = Snackbar.make(requireActivity().findViewById(
                R.id.coordinator), msg, length);
        View snackbarLayout = snackbar.getView();
        TextView textView = snackbarLayout.findViewById(
                com.google.android.material.R.id.snackbar_text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_stat_notice, 0, 0, 0);
        } else {
            textView.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_stat_notice, 0, 0, 0);
        }
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setCompoundDrawablePadding(getResources().getDimensionPixelOffset(R.dimen.snackbar_icon_padding));
        return snackbar;
    }

    @UiThread
    public void showSnackbar(String msg, int length) {
        buildSnackbar(msg, length).show();
    }

    @UiThread
    public void showInstallSnackbar() {
        Snackbar snackbar = buildSnackbar(getString(
                R.string.update_amiibo_api), Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.sync, v -> downloadAmiiboAPIData());
        snackbar.show();
    }

    @UiThread
    public void showToast(int msgRes, int length) {
        Toast.makeText(this.getContext(), getString(msgRes), length).show();
    }

    @UiThread
    public void showToast(int msgRes, String params, int length) {
        Toast.makeText(this.getContext(), getString(msgRes, params), length).show();
    }
}
