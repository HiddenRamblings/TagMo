package com.hiddenramblings.tagmo.settings;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.GlideApp;
import com.hiddenramblings.tagmo.NFCIntent;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.browser.BrowserActivity;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.security.SecurityHandler;
import com.hiddenramblings.tagmo.nfctech.TagArray;
import com.hiddenramblings.tagmo.widget.Toasty;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.ParseException;
import java.util.Scanner;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

public class SettingsFragment extends PreferenceFragmentCompat {

    public static final String IMAGE_NETWORK_NEVER = "NEVER";
    public static final String IMAGE_NETWORK_WIFI = "WIFI_ONLY";
    public static final String IMAGE_NETWORK_ALWAYS = "ALWAYS";

    private static final int RESULT_KEYS = 8000;
    private static final int RESULT_IMPORT_AMIIBO_DATABASE = 8001;

    Preferences prefs;

    Preference importKeys;
    ListPreference imageNetworkSetting;

    private KeyManager keyManager;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preference_screen, rootKey);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = TagMo.getPrefs();

        this.keyManager = new KeyManager(this.getContext());
        if (!keyManager.isKeyMissing()) {
            onUpdateRequested(false);
        }

        importKeys = findPreference(getString(R.string.settings_import_keys));

        updateKeySummary();

        imageNetworkSetting = findPreference(getString(R.string.image_network_settings));
        if (null != imageNetworkSetting) {
            onImageNetworkChange(imageNetworkSetting, prefs.image_network());
            imageNetworkSetting.setOnPreferenceChangeListener((preference, newValue) -> {
                onImageNetworkChange(imageNetworkSetting, newValue.toString());
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        importKeys.setOnPreferenceClickListener(preference -> {
            onImportKeysClicked();
            return SettingsFragment.super.onPreferenceTreeClick(preference);
        });

        CheckBoxPreference enableTagTypeValidation = findPreference(
                getString(R.string.settings_tag_type_validation)
        );
        if (null != enableTagTypeValidation) {
            enableTagTypeValidation.setChecked(prefs.enable_tag_type_validation());
            enableTagTypeValidation.setOnPreferenceClickListener(preference -> {
                prefs.enable_tag_type_validation(enableTagTypeValidation.isChecked());
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        CheckBoxPreference enableAutomaticScan = findPreference(
                getString(R.string.settings_enable_automatic_scan)
        );
        if (null != enableAutomaticScan) {
            enableAutomaticScan.setChecked(
                    requireContext().getPackageManager().getComponentEnabledSetting(
                            NFCIntent.FilterComponent
                    ) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            );
            enableAutomaticScan.setOnPreferenceClickListener(preference -> {
                boolean isChecked = enableAutomaticScan.isChecked();
                prefs.enable_automatic_scan(isChecked);
                if (isChecked) {
                    requireContext().getPackageManager().setComponentEnabledSetting(
                            NFCIntent.FilterComponent,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP);
                } else {
                    requireContext().getPackageManager().setComponentEnabledSetting(
                            NFCIntent.FilterComponent,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP);
                }
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        CheckBoxPreference disableFoomiiboPanel = findPreference(
                getString(R.string.settings_hide_foomiibo_panel)
        );
        if (null != disableFoomiiboPanel && null != getActivity()) {
            disableFoomiiboPanel.setChecked(prefs.disable_foomiibo());
            disableFoomiiboPanel.setOnPreferenceClickListener(preference -> {
                boolean isChecked = disableFoomiiboPanel.isChecked();
                prefs.disable_foomiibo(isChecked);
                ((BrowserActivity) getActivity()).setFoomiiboPanelVisibility();
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        CheckBoxPreference enablePowerTagSupport = findPreference(
                getString(R.string.settings_enable_power_tag_support)
        );
        if (null != enablePowerTagSupport) {
            enablePowerTagSupport.setOnPreferenceClickListener(preference -> {
                boolean isEnabled = enablePowerTagSupport.isChecked();
                prefs.power_tag_support(isEnabled);
                if (isEnabled) {
                    ((BrowserActivity) requireActivity()).loadPTagKeyManager();
                }
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        CheckBoxPreference enableEliteSupport = findPreference(
                getString(R.string.settings_enable_elite_support)
        );
        if (null != enableEliteSupport) {
            boolean isElite = prefs.elite_support();
            enableEliteSupport.setChecked(isElite);
            if (isElite && prefs.elite_signature().length() > 1) {
                enableEliteSupport.setSummary(getString(
                        R.string.elite_signature, prefs.elite_signature()));
            }
            enableEliteSupport.setOnPreferenceClickListener(preference -> {
                boolean isEnabled = enableEliteSupport.isChecked();
                prefs.elite_support(enableEliteSupport.isChecked());
                if (isEnabled && prefs.elite_signature().length() > 1)
                    enableEliteSupport.setSummary(getString(R.string.elite_signature,
                            prefs.elite_signature()));
                else
                    enableEliteSupport.setSummary(getString(R.string.elite_details));
                ((BrowserActivity) requireActivity()).onTabCollectionChanged();
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        CheckBoxPreference enableFlaskSupport = findPreference(
                getString(R.string.settings_enable_flask_support)
        );
        if (null != enableFlaskSupport) {
            enableFlaskSupport.setChecked(prefs.flask_support());
            enableFlaskSupport.setOnPreferenceClickListener(preference -> {
                prefs.flask_support(enableFlaskSupport.isChecked());
                ((BrowserActivity) requireActivity()).onTabCollectionChanged();
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
            enableFlaskSupport.setVisible(Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2));
        }

        ListPreference databaseSourceSetting = findPreference(getString(R.string.setting_database_source));
        if (null != databaseSourceSetting) {
            databaseSourceSetting.setValueIndex(prefs.database_source());
            databaseSourceSetting.setSummary(databaseSourceSetting.getEntry());
            databaseSourceSetting.setOnPreferenceClickListener(preference -> {
                ((ListPreference) preference).setValueIndex(prefs.database_source());
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
            databaseSourceSetting.setOnPreferenceChangeListener((preference, newValue) -> {
                ListPreference databaseSource = ((ListPreference) preference);
                int index = databaseSource.findIndexOfValue(newValue.toString());
                prefs.database_source(index);
                databaseSource.setSummary(databaseSource.getEntries()[index]);
                rebuildAmiiboDatabase();
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        Preference syncInfo = findPreference(getString(R.string.settings_import_info_amiiboapi));
        if (null != syncInfo) {
            syncInfo.setOnPreferenceClickListener(preference -> {
                rebuildAmiiboDatabase();
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        Preference importInfo = findPreference(getString(R.string.settings_import_info));
        if (null != importInfo) {
            importInfo.setOnPreferenceClickListener(preference -> {
                showFileChooser(getString(R.string.import_json_details),
                        RESULT_IMPORT_AMIIBO_DATABASE);
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        Preference resetInfo = findPreference(getString(R.string.settings_reset_info));
        if (null != resetInfo) {
            resetInfo.setOnPreferenceClickListener(preference -> {
                resetAmiiboDatabase(true);
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        ListPreference themeSetting = findPreference(getString(R.string.settings_tagmo_theme));
        if (null != themeSetting) {
            themeSetting.setValueIndex(prefs.applicationTheme());
            themeSetting.setOnPreferenceClickListener(preference -> {
                ((ListPreference) preference).setValueIndex(prefs.applicationTheme());
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
            themeSetting.setOnPreferenceChangeListener((preference, newValue) -> {
                int index = ((ListPreference) preference).findIndexOfValue(newValue.toString());
                prefs.applicationTheme(index);
                ((TagMo) requireActivity().getApplication()).setThemePreference();
                onApplicationThemeChanged();
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        CheckBoxPreference disableDebug = findPreference(getString(R.string.settings_disable_debug));
        if (null != disableDebug) {
            disableDebug.setChecked(prefs.disable_debug());
            disableDebug.setOnPreferenceClickListener(preference -> {
                prefs.disable_debug(disableDebug.isChecked());
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        Preference disclaimerFoomiibo = findPreference(getString(R.string.disclaimer_foomiibo));
        if (null != disclaimerFoomiibo) {
            try (InputStream in = getResources().openRawResource(R.raw.tos_foomiibo);
                 BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
                StringBuilder total = new StringBuilder();
                String line;
                while (null != (line = r.readLine())) {
                    total.append(line).append("\n");
                }
                disclaimerFoomiibo.setSummary(total.toString());
            } catch (Exception e) {
                Debug.Info(e);
            }
        }

        Preference disclaimerTagMo = findPreference(getString(R.string.disclaimer_tagmo));
        if (null != disclaimerTagMo) {
            try (InputStream in = getResources().openRawResource(R.raw.tos_tagmo);
                 BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
                StringBuilder total = new StringBuilder();
                String line;
                while (null != (line = r.readLine())) {
                    total.append(line).append("\n");
                }
                disclaimerTagMo.setSummary(total.toString());
            } catch (Exception e) {
                Debug.Info(e);
            }
        }
    }

    private void onImportKeysClicked() {
        showFileChooser(getString(R.string.decryption_keys), RESULT_KEYS);
    }

    private void onImageNetworkChange(ListPreference imageNetworkSetting, String newValue) {
        int index = imageNetworkSetting.findIndexOfValue(newValue);
        if (index == -1) {
            onImageNetworkChange(imageNetworkSetting, IMAGE_NETWORK_ALWAYS);
        } else {
            prefs.image_network(newValue);
            imageNetworkSetting.setValue(newValue);
            imageNetworkSetting.setSummary(imageNetworkSetting.getEntry());
            BrowserActivity activity = (BrowserActivity) requireActivity();
            if (null != activity.getSettings()) {
                activity.runOnUiThread(() -> activity.getSettings().notifyChanges());
            }
        }
    }

    private void onApplicationThemeChanged() {
        Intent intent = requireActivity().getIntent();
        requireActivity().overridePendingTransition(0, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        requireActivity().finish();
        requireActivity().overridePendingTransition(0, 0);
        startActivity(intent);
    }

    private void validateKeys(Uri data) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (InputStream strm = requireContext().getContentResolver().openInputStream(data)) {
                this.keyManager.evaluateKey(strm);

                if (Thread.currentThread().isInterrupted()) return;

                requireActivity().runOnUiThread(() -> {
                    ((BrowserActivity) requireActivity()).onKeysLoaded(true);
                    updateKeySummary();
                });
            } catch (Exception e) {
                Debug.Info(e);
            }
        });
    }

    public void verifyKeyFiles() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Scanner scanner = new Scanner(new URL(
                        "https://pastebin.com/raw/aV23ha3X").openStream());
                for (int i = 0; i < 4; i++) {
                    if (scanner.hasNextLine()) scanner.nextLine();
                }
                this.keyManager.evaluateKey(new ByteArrayInputStream(TagArray.hexToByteArray(
                        scanner.nextLine().replace(" ", "")
                )));
                scanner.close();
            } catch (IOException e) {
                Debug.Warn(e);
            }

            if (Thread.currentThread().isInterrupted()) return;

            requireActivity().runOnUiThread(() -> {
                ((BrowserActivity) requireActivity()).onKeysLoaded(true);
                updateKeySummary();
            });
        });
    }

    public void updateKeySummary() {
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

        requireActivity().runOnUiThread(() -> importKeys.setSummary(keySummary));
    }

    public void rebuildAmiiboDatabase() {
        resetAmiiboDatabase(false);
        onUpdateRequested(true);
    }

    private void updateAmiiboDatabase(Uri data) {
        resetAmiiboDatabase(false);
        Executors.newSingleThreadExecutor().execute(() -> {
            AmiiboManager amiiboManager;
            try {
                amiiboManager = AmiiboManager.parse(requireContext(), data);
            } catch (JSONException | ParseException | IOException e) {
                Debug.Warn(e);
                new Toasty(requireActivity()).Short(R.string.amiibo_failure_parse);
                return;
            }

            if (Thread.currentThread().isInterrupted()) return;

            try {
                AmiiboManager.saveDatabase(amiiboManager, requireContext().getApplicationContext());
            } catch (JSONException | IOException e) {
                Debug.Warn(e);
                new Toasty(requireActivity()).Short(R.string.amiibo_failure_update);
                return;
            }
            BrowserActivity activity = (BrowserActivity) requireActivity();
            activity.runOnUiThread(() -> {
                buildSnackbar(
                        activity, R.string.amiibo_info_updated, Snackbar.LENGTH_SHORT
                ).show();
                activity.getSettings().notifyChanges();
            });
        });
    }

    private void resetAmiiboDatabase(boolean notify) {
        Executors.newSingleThreadExecutor().execute(() -> {
            requireContext().deleteFile(AmiiboManager.AMIIBO_DATABASE_FILE);
            BrowserActivity activity = (BrowserActivity) requireActivity();
            if (notify) {
                activity.runOnUiThread(() -> {
                    activity.getSettings().setLastUpdatedAPI(null);
                    activity.getSettings().notifyChanges();
                });
            }
            try {
                Executors.newSingleThreadExecutor().execute(() ->
                        GlideApp.get(activity).clearDiskCache());
                requireActivity().runOnUiThread(() ->
                        GlideApp.get(activity).clearMemory());
            } catch (IllegalStateException ignored) { }
            if (notify) activity.runOnUiThread(() -> buildSnackbar(
                    activity, R.string.removing_amiibo_info, Snackbar.LENGTH_SHORT
            ).show());
        });
    }

    private HttpsURLConnection fixServerLocation(URL url) throws IOException {
        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.setUseCaches(false);
        urlConnection.setDefaultUseCaches(false);
        return urlConnection;
    }

    private void downloadAmiiboAPIData(String lastUpdated) {
        BrowserActivity activity = (BrowserActivity) requireActivity();
        final Snackbar syncMessage = buildSnackbar(
                activity, R.string.sync_amiibo_process, Snackbar.LENGTH_INDEFINITE
        );
        activity.runOnUiThread(syncMessage::show);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                URL url;
                if (prefs.database_source() == 0) {
                    url = new URL(AmiiboManager.RENDER_RAW + "database/amiibo.json");
                } else {
                    url = new URL(AmiiboManager.AMIIBO_API + "amiibo/");
                }
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setUseCaches(false);
                conn.setDefaultUseCaches(false);

                int statusCode = conn.getResponseCode();
                if (statusCode == HttpsURLConnection.HTTP_MOVED_PERM) {
                    String address = conn.getHeaderField("Location");
                    conn.disconnect();
                    conn = fixServerLocation(new URL(address));
                    statusCode = conn.getResponseCode();
                } else if (statusCode != HttpsURLConnection.HTTP_OK && isRenderAPI(conn)) {
                    conn.disconnect();
                    conn = fixServerLocation(new URL(AmiiboManager.AMIIBO_API  + "amiibo/"));
                    statusCode = conn.getResponseCode();
                }

                if (statusCode == HttpsURLConnection.HTTP_OK) {
                    InputStream inputStream = new BufferedInputStream(conn.getInputStream());

                    BufferedReader reader = null;
                    StringBuilder response = new StringBuilder();
                    try {
                        reader = new BufferedReader(new InputStreamReader(inputStream));
                        String line;
                        while (null != (line = reader.readLine())) {
                            response.append(line);
                        }
                    } finally {
                        if (null != reader) {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                Debug.Info(e);
                            }
                        }
                        conn.disconnect();
                    }

                    AmiiboManager amiiboManager = isRenderAPI(conn)
                            ? AmiiboManager.parse(response.toString())
                            : AmiiboManager.parseAmiiboAPI(response.toString());

                    if (Thread.currentThread().isInterrupted()) return;

                    AmiiboManager.saveDatabase(amiiboManager, requireContext().getApplicationContext());
                    activity.runOnUiThread(() -> {
                        if (syncMessage.isShown()) syncMessage.dismiss();
                        buildSnackbar(
                                activity, R.string.sync_amiibo_complete, Snackbar.LENGTH_SHORT
                        ).show();
                        activity.getSettings().setLastUpdatedAPI(lastUpdated);
                        activity.getSettings().notifyChanges();
                    });
                } else {
                    conn.disconnect();
                    throw new Exception(String.valueOf(statusCode));
                }
            } catch (Exception e) {
                Debug.Warn(e);
                activity.runOnUiThread(() -> {
                    if (syncMessage.isShown()) syncMessage.dismiss();
                    buildSnackbar(
                            activity, R.string.sync_amiibo_failed, Snackbar.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    private void onDownloadRequested(String lastUpdated) {
        new SecurityHandler(requireActivity(), new SecurityHandler.ProviderInstallListener() {
            @Override
            public void onProviderInstalled() {
                downloadAmiiboAPIData(lastUpdated);
            }

            @Override
            public void onProviderInstallException() { downloadAmiiboAPIData(lastUpdated); }

            @Override
            public void onProviderInstallFailed() {
                onImageNetworkChange(imageNetworkSetting, IMAGE_NETWORK_NEVER);
                new Toasty(requireActivity()).Short(R.string.fail_ssl_update);
            }
        });
    }

    private final ActivityResultLauncher<Intent> onLoadKeys = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            verifyKeyFiles();
        } else if (null != result.getData().getClipData()) {
            for (int i = 0; i < result.getData().getClipData().getItemCount(); i++) {
                validateKeys(result.getData().getClipData().getItemAt(i).getUri());
            }
        } else {
            validateKeys(result.getData().getData());
        }
    });

    private final ActivityResultLauncher<Intent> onImportAmiiboDatabase = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;

        updateAmiiboDatabase(result.getData().getData());
    });

    private void showFileChooser(String title, int resultCode) {
        Intent intent = new Intent(Debug.isNewer(Build.VERSION_CODES.KITKAT)
                ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_GET_CONTENT)
                .putExtra("android.content.extra.SHOW_ADVANCED", true)
                .putExtra("android.content.extra.FANCY", true);

        switch(resultCode) {
            case RESULT_KEYS:
                if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2))
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    if (Debug.isNewer(Build.VERSION_CODES.KITKAT)) {
                        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                                getResources().getStringArray(R.array.mimetype_bin));
                    }
                    onLoadKeys.launch(Intent.createChooser(
                            NFCIntent.getIntent(intent), title));
                } catch (ActivityNotFoundException ex) {
                    Debug.Info(ex);
                }
                break;
            case RESULT_IMPORT_AMIIBO_DATABASE:
                try {
                    if (Debug.isNewer(Build.VERSION_CODES.KITKAT)) {
                        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                                getResources().getStringArray(R.array.mimetype_json));
                    }
                    onImportAmiiboDatabase.launch(Intent.createChooser(
                            NFCIntent.getIntent(intent), title));
                } catch (ActivityNotFoundException ex) {
                    Debug.Info(ex);
                }
                break;
        }
    }

    private Snackbar buildSnackbar(Activity activity, int msgRes, int length) {
        return new IconifiedSnackbar(activity).buildSnackbar(
                requireActivity().findViewById(R.id.preferences), msgRes, length
        );
    }

    private void parseCommitDate(String result, boolean isMenuClicked) {
        try {
            JSONObject jsonObject = new JSONObject(result);
            JSONObject render = (JSONObject) jsonObject.get("commit");
            JSONObject commit = (JSONObject) render.get("commit");
            JSONObject author = (JSONObject) commit.get("author");
            String lastUpdated = (String) author.get("date");
            BrowserActivity activity = (BrowserActivity) requireActivity();
            if (isMenuClicked) {
                onDownloadRequested(lastUpdated);
            } else if (null == activity.getSettings().getLastUpdatedAPI()
                    || !activity.getSettings().getLastUpdatedAPI().equals(lastUpdated)) {
                try {
                    activity.runOnUiThread(() -> buildSnackbar(
                            activity,  R.string.update_amiibo_api, Snackbar.LENGTH_LONG
                    ).setAction(R.string.sync, v -> onDownloadRequested(lastUpdated)).show());
                } catch (IllegalStateException ignored) { }
            }
        } catch (Exception e) {
            Debug.Warn(e);
        }
    }

    private void parseUpdateJSON(String result, boolean isMenuClicked) {
        try {
            JSONObject jsonObject = new JSONObject(result);
            String lastUpdatedAPI = (String) jsonObject.get("lastUpdated");
            String lastUpdated = lastUpdatedAPI.substring(
                    0, lastUpdatedAPI.lastIndexOf(".")
            ) + "Z";
            BrowserActivity activity = (BrowserActivity) requireActivity();
            if (isMenuClicked) {
                onDownloadRequested(lastUpdated);
            } else if (null == activity.getSettings().getLastUpdatedAPI()
                    || !activity.getSettings().getLastUpdatedAPI().equals(lastUpdated)) {
                activity.runOnUiThread(() -> buildSnackbar(
                        activity, R.string.update_amiibo_api, Snackbar.LENGTH_LONG
                ).setAction(R.string.sync, v -> onDownloadRequested(lastUpdated)).show());
            }
        } catch (Exception e) {
            Debug.Warn(e);
        }
    }

    private void onUpdateRequested(boolean isMenuClicked) {
        if (prefs.database_source() == 0) {
            new JSONExecutor(requireActivity(),
                    "https://api.github.com/repos/8BitDream/AmiiboAPI/",
                    "branches/render?path=databaset%2Famiibo.json"
            ).setResultListener(result -> {
                if (null != result) parseCommitDate(result, isMenuClicked);
            });
        } else {
            new JSONExecutor(requireActivity(),
                    AmiiboManager.AMIIBO_API, "lastupdated/"
            ).setResultListener(result -> {
                if (null != result) parseUpdateJSON(result, isMenuClicked);
            });
        }
    }

    private boolean isRenderAPI(HttpsURLConnection conn) {
        String render = AmiiboManager.RENDER_RAW + "database/amiibo.json";
        return render.equals(conn.getURL().toString());
    }
}
