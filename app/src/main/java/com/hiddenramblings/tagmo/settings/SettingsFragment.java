package com.hiddenramblings.tagmo.settings;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.NFCIntent;
import com.hiddenramblings.tagmo.NfcActivity;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.WebActivity;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.AmiiboSeries;
import com.hiddenramblings.tagmo.amiibo.AmiiboType;
import com.hiddenramblings.tagmo.amiibo.Character;
import com.hiddenramblings.tagmo.amiibo.GameSeries;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Executors;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String API_LAST_UPDATED = "https://www.amiiboapi.com/api/lastupdated/";

    public static final String IMAGE_NETWORK_NEVER = "NEVER";
    public static final String IMAGE_NETWORK_WIFI = "WIFI_ONLY";
    public static final String IMAGE_NETWORK_ALWAYS = "ALWAYS";

    private static final int RESULT_KEYS = 8000;
    private static final int RESULT_IMPORT_AMIIBO_DATABASE = 8001;

    Preferences_ prefs;

    Preference importKeys;
    Preference amiiboStats;
    Preference gameSeriesStats;
    Preference characterStats;
    Preference amiiboSeriesStats;
    Preference amiiboTypeStats;

    private KeyManager keyManager;
    private AmiiboManager amiiboManager = null;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preference_screen, rootKey);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = TagMo.getPrefs();

        this.keyManager = new KeyManager(this.getContext());
        if (!keyManager.isKeyMissing()) {
            new JSONExecutor(API_LAST_UPDATED).setResultListener(result -> {
                if (null != result) parseUpdateJSON(result, false);
            });
        }

        importKeys = findPreference(getString(R.string.settings_import_keys));

        amiiboStats = findPreference(getString(R.string.settings_info_amiibo));
        gameSeriesStats = findPreference(getString(R.string.settings_info_game_series));
        characterStats = findPreference(getString(R.string.settings_info_characters));
        amiiboSeriesStats = findPreference(getString(R.string.settings_info_amiibo_series));
        amiiboTypeStats = findPreference(getString(R.string.settings_info_amiibo_types));

        loadAmiiboManager();
        updateKeySummary();
        updateAmiiboStats();

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

        CheckBoxPreference enableTagTypeValidation = findPreference(getString(R.string.settings_tag_type_validation));
        if (null != enableTagTypeValidation) {
            enableTagTypeValidation.setChecked(prefs.enable_tag_type_validation().get());
            enableTagTypeValidation.setOnPreferenceClickListener(preference -> {
                prefs.enable_tag_type_validation().put(enableTagTypeValidation.isChecked());
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        CheckBoxPreference enableAutomaticScan = findPreference(getString(R.string.settings_enable_automatic_scan));
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

        CheckBoxPreference enablePowerTagSupport = findPreference(getString(R.string.settings_enable_power_tag_support));
        if (null != enablePowerTagSupport) {
            enablePowerTagSupport.setOnPreferenceClickListener(preference -> {
                boolean isEnabled = enablePowerTagSupport.isChecked();
                prefs.enable_power_tag_support().put(isEnabled);
                if (isEnabled) {
                    ((BrowserActivity) requireActivity()).loadPTagKeyManager();
                    startActivity(new Intent(requireActivity(), WebActivity.class)
                            .setAction(NFCIntent.SITE_POWERTAG_HELP));
                }
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        Preference lockEliteHardware = findPreference(getString(R.string.lock_elite_hardware));
        if (null != lockEliteHardware) {
            lockEliteHardware.setOnPreferenceClickListener(preference -> {
                new AlertDialog.Builder(requireContext())
                        .setMessage(R.string.lock_elite_warning)
                        .setPositiveButton(R.string.accept, (dialog, which) -> {
                            Intent lock = new Intent(requireContext(), NfcActivity.class);
                            lock.setAction(NFCIntent.ACTION_LOCK_AMIIBO);
                            startActivity(lock);
                            dialog.dismiss();
                        })
                        .setNegativeButton(R.string.cancel, null).show();
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        Preference unlockEliteHardware = findPreference(getString(R.string.unlock_elite_hardware));
        if (null != unlockEliteHardware) {
            unlockEliteHardware.setOnPreferenceClickListener(preference -> {
                new AlertDialog.Builder(requireContext())
                        .setMessage(R.string.prepare_unlock)
                        .setPositiveButton(R.string.start, (dialog, which) -> {
                            startActivity(new Intent(requireContext(), NfcActivity.class)
                                    .setAction(NFCIntent.ACTION_UNLOCK_UNIT));
                            dialog.dismiss();
                        }).show();
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        CheckBoxPreference enableEliteSupport = findPreference(getString(R.string.settings_enable_elite_support));
        if (null != enableEliteSupport) {
            boolean isElite = prefs.enable_elite_support().get();
            enableEliteSupport.setChecked(isElite);
            if (isElite && prefs.settings_elite_signature().get().length() > 1) {
                enableEliteSupport.setSummary(getString(
                        R.string.elite_signature, prefs.settings_elite_signature().get()));
            }
            if (null != lockEliteHardware)
                lockEliteHardware.setVisible(isElite);
            if (null != unlockEliteHardware)
                unlockEliteHardware.setVisible(isElite);
            enableEliteSupport.setOnPreferenceClickListener(preference -> {
                boolean isEnabled = enableEliteSupport.isChecked();
                prefs.enable_elite_support().put(enableEliteSupport.isChecked());
                if (isEnabled && prefs.settings_elite_signature().get().length() > 1)
                    enableEliteSupport.setSummary(getString(R.string.elite_signature,
                            prefs.settings_elite_signature().get()));
                else
                    enableEliteSupport.setSummary(getString(R.string.elite_details));
                if (null != lockEliteHardware)
                    lockEliteHardware.setVisible(isEnabled);
                if (null != unlockEliteHardware)
                    unlockEliteHardware.setVisible(isEnabled);
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
                resetAmiiboManager();
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        amiiboStats.setOnPreferenceClickListener(preference -> {
            new AlertDialog.Builder(this.getContext())
                    .setTitle(R.string.amiibo)
                    .setAdapter(new AmiiboAdapter(new ArrayList<>(
                            amiiboManager.amiibos.values())), null)
                    .setPositiveButton(R.string.close, null)
                    .show();
            return SettingsFragment.super.onPreferenceTreeClick(preference);
        });

        gameSeriesStats.setOnPreferenceClickListener(preference -> {
            final ArrayList<String> items = new ArrayList<>();
            for (GameSeries gameSeries : amiiboManager.gameSeries.values()) {
                if (!items.contains(gameSeries.name))
                    items.add(gameSeries.name);
            }
            Collections.sort(items);

            new AlertDialog.Builder(this.getContext())
                    .setTitle(R.string.amiibo_game)
                    .setAdapter(new ArrayAdapter<>(this.getContext(),
                            android.R.layout.simple_list_item_1, items), null)
                    .setPositiveButton(R.string.close, null)
                    .show();
            return SettingsFragment.super.onPreferenceTreeClick(preference);
        });

        characterStats.setOnPreferenceClickListener(preference -> {
            final ArrayList<Character> items = new ArrayList<>();
            for (Character character : amiiboManager.characters.values()) {
                if (!items.contains(character))
                    items.add(character);
            }
            Collections.sort(items);

            new AlertDialog.Builder(this.getContext())
                    .setTitle(R.string.pref_amiibo_characters)
                    .setAdapter(new ArrayAdapter<>(this.getContext(),
                            android.R.layout.simple_list_item_2, android.R.id.text1, items) {
                        @NonNull
                        @Override
                        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                            View view = super.getView(position, convertView, parent);
                            TextView text1 = view.findViewById(android.R.id.text1);
                            TextView text2 = view.findViewById(android.R.id.text2);

                            Character character = getItem(position);
                            text1.setText(character.name);

                            GameSeries gameSeries = character.getGameSeries();
                            text2.setText(null == gameSeries ? "" : gameSeries.name);

                            return view;
                        }
                    }, null)
                    .setPositiveButton(R.string.close, null)
                    .show();
            return SettingsFragment.super.onPreferenceTreeClick(preference);
        });

        amiiboSeriesStats.setOnPreferenceClickListener(preference -> {
            final ArrayList<String> items = new ArrayList<>();
            for (AmiiboSeries amiiboSeries : amiiboManager.amiiboSeries.values()) {
                if (!items.contains(amiiboSeries.name))
                    items.add(amiiboSeries.name);
            }
            Collections.sort(items);

            new AlertDialog.Builder(this.getContext())
                    .setTitle(R.string.amiibo_series)
                    .setAdapter(new ArrayAdapter<>(this.getContext(),
                            android.R.layout.simple_list_item_1, items), null)
                    .setPositiveButton(R.string.close, null)
                    .show();
            return SettingsFragment.super.onPreferenceTreeClick(preference);
        });

        amiiboTypeStats.setOnPreferenceClickListener(preference -> {
            final ArrayList<AmiiboType> amiiboTypes =
                    new ArrayList<>(amiiboManager.amiiboTypes.values());
            Collections.sort(amiiboTypes);

            final ArrayList<String> items = new ArrayList<>();
            for (AmiiboType amiiboType : amiiboTypes) {
                if (!items.contains(amiiboType.name))
                    items.add(amiiboType.name);
            }

            new AlertDialog.Builder(this.getContext())
                    .setTitle(R.string.pref_amiibo_types)
                    .setAdapter(new ArrayAdapter<>(this.getContext(),
                            android.R.layout.simple_list_item_1, items), null)
                    .setPositiveButton(R.string.close, null)
                    .show();
            return SettingsFragment.super.onPreferenceTreeClick(preference);
        });

        CheckBoxPreference disableDebug = findPreference(getString(R.string.settings_disable_debug));
        if (null != disableDebug) {
            disableDebug.setChecked(prefs.settings_disable_debug().get());
            disableDebug.setOnPreferenceClickListener(preference -> {
                prefs.settings_disable_debug().put(disableDebug.isChecked());
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }

        CheckBoxPreference stableChannel = findPreference(getString(R.string.settings_stable_channel));
        if (null != stableChannel) {
            stableChannel.setChecked(prefs.settings_stable_channel().get());
            stableChannel.setOnPreferenceClickListener(preference -> {
                prefs.settings_stable_channel().put(stableChannel.isChecked());
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

    private void loadAmiiboManager() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AmiiboManager amiiboManager;
            try {
                amiiboManager = AmiiboManager.getAmiiboManager(requireContext().getApplicationContext());
            } catch (IOException | JSONException | ParseException e) {
                Debug.Log(e);
                new Toasty(requireActivity()).Short(R.string.amiibo_failure_load);
                return;
            }
            if (Thread.currentThread().isInterrupted()) return;

            setAmiiboManager(amiiboManager);
        });
    }

    public void rebuildAmiiboDatabase() {
        new JSONExecutor(API_LAST_UPDATED).setResultListener(result -> {
            if (null != result) parseUpdateJSON(result, true);
        });
    }

    private void updateAmiiboManager(Uri data) {
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

            setAmiiboManager(amiiboManager);
            requireActivity().runOnUiThread(() -> showSnackbar(
                    R.string.amiibo_info_updated, Snackbar.LENGTH_SHORT));
        });
    }

    private void resetAmiiboManager() {
        Executors.newSingleThreadExecutor().execute(() -> {
            requireContext().deleteFile(AmiiboManager.AMIIBO_DATABASE_FILE);

            AmiiboManager amiiboManager = null;
            try {
                amiiboManager = AmiiboManager.getDefaultAmiiboManager(requireContext().getApplicationContext());
            } catch (IOException | JSONException | ParseException e) {
                Debug.Log(e);
                new Toasty(requireActivity()).Short(R.string.amiibo_failure_parse_default);
            }
            if (Thread.currentThread().isInterrupted()) return;

            setAmiiboManager(amiiboManager);
            requireActivity().runOnUiThread(() -> showSnackbar(
                    R.string.removing_amiibo_info, Snackbar.LENGTH_SHORT));
        });
    }

    private void setAmiiboManager(AmiiboManager amiiboManager) {
        try {
            Executors.newSingleThreadExecutor().execute(() ->
                    Glide.get(requireActivity()).clearDiskCache());
            requireActivity().runOnUiThread(() ->
                    Glide.get(requireActivity()).clearMemory());
            this.amiiboManager = amiiboManager;
            requireActivity().runOnUiThread(this::updateAmiiboStats);
        } catch (IllegalStateException ignored) { }
    }

    void updateAmiiboStats() {
        boolean hasAmiibo = null != amiiboManager;
        this.amiiboStats.setTitle(getString(R.string.number_amiibo,
                hasAmiibo ? amiiboManager.amiibos.size() : 0));
        this.gameSeriesStats.setTitle(getString(R.string.number_game,
                hasAmiibo ? amiiboManager.gameSeries.size() : 0));
        this.characterStats.setTitle(getString(R.string.number_character,
                hasAmiibo ? amiiboManager.characters.size() : 0));
        this.amiiboSeriesStats.setTitle(getString(R.string.number_series,
                hasAmiibo ? amiiboManager.amiiboSeries.size() : 0));
        this.amiiboTypeStats.setTitle(getString(R.string.number_type,
                hasAmiibo ? amiiboManager.amiiboTypes.size() : 0));
    }

    private void downloadAmiiboAPIData(String lastUpdated) {
        showSnackbar(R.string.sync_amiibo_process, Snackbar.LENGTH_INDEFINITE);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                URL url = new URL("https://www.amiiboapi.com/api/amiibo/");
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
                    setAmiiboManager(amiiboManager);
                    requireActivity().runOnUiThread(() -> showSnackbar(
                            R.string.sync_amiibo_complete, Snackbar.LENGTH_SHORT));
                } else {
                    throw new Exception(String.valueOf(statusCode));
                }
                prefs.lastUpdated().put(lastUpdated);
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

        updateAmiiboManager(result.getData().getData());
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
            if (isMenuClicked) {
                downloadAmiiboAPIData(lastUpdated);
            } else if (!prefs.lastUpdated().get().equals(lastUpdated)) {
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
