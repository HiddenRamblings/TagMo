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
import com.hiddenramblings.tagmo.NFCIntent;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.browser.BrowserActivity;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.widget.Toasty;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.util.concurrent.Executors;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String API_URL = "https://tagmoapi.onrender.com/";
    private static final String API_LAST_UPDATED = API_URL + "lastupdated/";

    public static final String IMAGE_NETWORK_NEVER = "NEVER";
    public static final String IMAGE_NETWORK_WIFI = "WIFI_ONLY";
    public static final String IMAGE_NETWORK_ALWAYS = "ALWAYS";

    private static final int RESULT_KEYS = 8000;
    private static final int RESULT_IMPORT_AMIIBO_DATABASE = 8001;

    Preferences_ prefs;

    Preference importKeys;

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
            new JSONExecutor(requireActivity(), API_LAST_UPDATED).setResultListener(result -> {
                if (null != result) parseUpdateJSON(result, false);
            });
        }

        importKeys = findPreference(getString(R.string.settings_import_keys));

        updateKeySummary();

        ListPreference imageNetworkSetting = findPreference(getString(R.string.image_network_settings));
        if (null != imageNetworkSetting) {
            onImageNetworkChange(imageNetworkSetting, prefs.image_network_settings().get());
            imageNetworkSetting.setOnPreferenceChangeListener((preference, newValue) -> {
                onImageNetworkChange((ListPreference) preference, newValue.toString());
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
            enableTagTypeValidation.setChecked(prefs.enable_tag_type_validation().get());
            enableTagTypeValidation.setOnPreferenceClickListener(preference -> {
                prefs.enable_tag_type_validation().put(enableTagTypeValidation.isChecked());
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        CheckBoxPreference enableAutomaticScan = findPreference(
                getString(R.string.settings_enable_automatic_scan)
        );
        if (null != enableAutomaticScan) {
            enableAutomaticScan.setOnPreferenceClickListener(preference -> {
                boolean isChecked = enableAutomaticScan.isChecked();
                prefs.enable_automatic_scan().put(isChecked);
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
            disableFoomiiboPanel.setChecked(prefs.settings_disable_foomiibo().get());
            disableFoomiiboPanel.setOnPreferenceClickListener(preference -> {
                boolean isChecked = disableFoomiiboPanel.isChecked();
                prefs.settings_disable_foomiibo().put(isChecked);
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
                prefs.enable_power_tag_support().put(isEnabled);
                if (isEnabled) {
                    ((BrowserActivity) requireActivity()).loadPTagKeyManager();
                    ((BrowserActivity) requireActivity()).showWebsite(NFCIntent.SITE_POWERTAG_HELP);
                }
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        CheckBoxPreference enableEliteSupport = findPreference(
                getString(R.string.settings_enable_elite_support)
        );
        if (null != enableEliteSupport) {
            boolean isElite = prefs.enable_elite_support().get();
            enableEliteSupport.setChecked(isElite);
            if (isElite && prefs.settings_elite_signature().get().length() > 1) {
                enableEliteSupport.setSummary(getString(
                        R.string.elite_signature, prefs.settings_elite_signature().get()));
            }
            enableEliteSupport.setOnPreferenceClickListener(preference -> {
                boolean isEnabled = enableEliteSupport.isChecked();
                prefs.enable_elite_support().put(enableEliteSupport.isChecked());
                if (isEnabled && prefs.settings_elite_signature().get().length() > 1)
                    enableEliteSupport.setSummary(getString(R.string.elite_signature,
                            prefs.settings_elite_signature().get()));
                else
                    enableEliteSupport.setSummary(getString(R.string.elite_details));
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
            themeSetting.setValueIndex(prefs.applicationTheme().get());
            themeSetting.setOnPreferenceChangeListener((preference, newValue) -> {
                int index = ((ListPreference) preference).findIndexOfValue(newValue.toString());
                prefs.applicationTheme().put(index);
                ((TagMo) requireActivity().getApplication()).setThemePreference();
                requireActivity().recreate();
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        CheckBoxPreference disableDebug = findPreference(getString(R.string.settings_disable_debug));
        if (null != disableDebug) {
            disableDebug.setChecked(prefs.settings_disable_debug().get());
            disableDebug.setOnPreferenceClickListener(preference -> {
                prefs.settings_disable_debug().put(disableDebug.isChecked());
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
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
            prefs.image_network_settings().put(newValue);
            imageNetworkSetting.setValue(newValue);
            imageNetworkSetting.setSummary(imageNetworkSetting.getEntry());
            BrowserActivity activity = (BrowserActivity) requireActivity();
            if (null != activity.getSettings()) activity.getSettings().notifyChanges();
        }
    }

    private void validateKeys(Uri data) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (InputStream strm = requireContext().getContentResolver().openInputStream(data)) {
                this.keyManager.evaluateKey(strm);
            } catch (Exception e) {
                Debug.Log(e);
                requireActivity().runOnUiThread(() ->
                        new IconifiedSnackbar(requireActivity()).buildSnackbar(
                                requireActivity().findViewById(R.id.preferences),
                                e.getMessage(), Snackbar.LENGTH_SHORT
                        ).show());
            }
            if (Thread.currentThread().isInterrupted()) return;

            ((BrowserActivity) requireActivity()).onRefresh(true);
            updateKeySummary();
        });
    }

    private void updateKeySummary() {
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
        new JSONExecutor(requireActivity(), API_LAST_UPDATED).setResultListener(result -> {
            if (null != result) parseUpdateJSON(result, true);
        });
    }

    private void updateAmiiboDatabase(Uri data) {
        resetAmiiboDatabase(false);
        Executors.newSingleThreadExecutor().execute(() -> {
            AmiiboManager amiiboManager;
            try {
                amiiboManager = AmiiboManager.parse(requireContext(), data);
            } catch (JSONException | ParseException e) {
                Debug.Log(e);
                new Toasty(requireActivity()).Short(R.string.amiibo_failure_parse);
                return;
            } catch (IOException e) {
                Debug.Log(e);
                new Toasty(requireActivity()).Short(R.string.amiibo_failure_read);
                return;
            }

            if (Thread.currentThread().isInterrupted()) return;

            try {
                AmiiboManager.saveDatabase(amiiboManager, requireContext().getApplicationContext());
            } catch (JSONException | IOException e) {
                Debug.Log(e);
                new Toasty(requireActivity()).Short(R.string.amiibo_failure_update);
                return;
            }
            requireActivity().runOnUiThread(() -> {
                showSnackbar(R.string.amiibo_info_updated, Snackbar.LENGTH_SHORT);
                ((BrowserActivity) requireActivity()).getSettings().notifyChanges();
            });
        });
    }

    private void resetAmiiboDatabase(boolean notify) {
        Executors.newSingleThreadExecutor().execute(() -> {
            requireContext().deleteFile(AmiiboManager.AMIIBO_DATABASE_FILE);
            BrowserActivity activity = (BrowserActivity) requireActivity();
            activity.runOnUiThread(() -> {
                activity.getSettings().setLastUpdatedAPI(null);
                if (notify) activity.getSettings().notifyChanges();
            });
            try {
                Executors.newSingleThreadExecutor().execute(() ->
                        Glide.get(requireActivity()).clearDiskCache());
                requireActivity().runOnUiThread(() ->
                        Glide.get(requireActivity()).clearMemory());
            } catch (IllegalStateException ignored) { }
            requireActivity().runOnUiThread(() -> showSnackbar(
                    R.string.removing_amiibo_info, Snackbar.LENGTH_SHORT));
        });
    }

    private void downloadAmiiboAPIData(String lastUpdated) {
        showSnackbar(R.string.sync_amiibo_process, Snackbar.LENGTH_INDEFINITE);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                URL url = new URL(API_URL + "api/amiibo/");
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setUseCaches(false);
                urlConnection.setDefaultUseCaches(false);

                int statusCode = urlConnection.getResponseCode();
                if (statusCode == 200) {
                    InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());

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
                                Debug.Log(e);
                            }
                        }
                    }

                    String json = response.toString();
                    AmiiboManager amiiboManager = AmiiboManager.parseAmiiboAPI(new JSONObject(json));
                    if (Thread.currentThread().isInterrupted()) return;

                    AmiiboManager.saveDatabase(amiiboManager, requireContext().getApplicationContext());
                    BrowserActivity activity = (BrowserActivity) requireActivity();
                    activity.runOnUiThread(() -> {
                        showSnackbar(R.string.sync_amiibo_complete, Snackbar.LENGTH_SHORT);
                        activity.getSettings().setLastUpdatedAPI(lastUpdated);
                        activity.getSettings().notifyChanges();
                    });
                } else {
                    throw new Exception(String.valueOf(statusCode));
                }
            } catch (Exception e) {
                Debug.Log(e);
                requireActivity().runOnUiThread(() -> showSnackbar(
                        R.string.sync_amiibo_failed, Snackbar.LENGTH_SHORT));
            }
        });
    }

    private final ActivityResultLauncher<Intent> onLoadKeys = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            ((BrowserActivity) requireActivity()).locateKeyFiles();
            return;
        }
        if (null != result.getData().getClipData()) {
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
        Intent intent = new Intent(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_GET_CONTENT)
                .putExtra("android.content.extra.SHOW_ADVANCED", true)
                .putExtra("android.content.extra.FANCY", true);

        switch(resultCode) {
            case RESULT_KEYS:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                                getResources().getStringArray(R.array.mimetype_bin));
                    }
                    onLoadKeys.launch(Intent.createChooser(
                            NFCIntent.getIntent(intent), title));
                } catch (ActivityNotFoundException ex) {
                    Debug.Log(ex);
                }
                break;
            case RESULT_IMPORT_AMIIBO_DATABASE:
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                                getResources().getStringArray(R.array.mimetype_json));
                    }
                    onImportAmiiboDatabase.launch(Intent.createChooser(
                            NFCIntent.getIntent(intent), title));
                } catch (ActivityNotFoundException ex) {
                    Debug.Log(ex);
                }
                break;
        }
    }

    private void showSnackbar(int msgRes, int length) {
        new IconifiedSnackbar(requireActivity()).buildSnackbar(
                requireActivity().findViewById(R.id.preferences), msgRes, length
        ).show();
    }

    private void parseUpdateJSON(String result, boolean isMenuClicked) {
        try {
            JSONObject jsonObject = new JSONObject(result);
            String lastUpdated = (String) jsonObject.get("lastUpdated");
            BrowserActivity activity = (BrowserActivity) requireActivity();
            if (isMenuClicked) {
                downloadAmiiboAPIData(lastUpdated);
            } else if (null == activity.getSettings().getLastUpdatedAPI()
                    || !activity.getSettings().getLastUpdatedAPI().equals(lastUpdated)) {
                new IconifiedSnackbar(requireActivity()).buildSnackbar(
                        requireActivity().findViewById(R.id.preferences),
                        R.string.update_amiibo_api, Snackbar.LENGTH_LONG
                ).setAction(R.string.sync, v -> downloadAmiiboAPIData(lastUpdated)).show();
            }
        } catch (Exception e) {
            Debug.Log(e);
        }
    }
}
