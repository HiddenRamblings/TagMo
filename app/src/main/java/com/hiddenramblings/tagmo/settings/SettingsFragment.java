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
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.BrowserActivity;
import com.hiddenramblings.tagmo.GlideApp;
import com.hiddenramblings.tagmo.NFCIntent;
import com.hiddenramblings.tagmo.NfcActivity;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.WebActivity;
import com.hiddenramblings.tagmo.adapter.SettingsAmiiboAdapter;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.AmiiboSeries;
import com.hiddenramblings.tagmo.amiibo.AmiiboType;
import com.hiddenramblings.tagmo.amiibo.Character;
import com.hiddenramblings.tagmo.amiibo.GameSeries;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.github.JSONExecutor;
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
    CheckBoxPreference enableTagTypeValidation;
    CheckBoxPreference enableAutomaticScan;
    CheckBoxPreference enablePowerTagSupport;
    CheckBoxPreference enableEliteSupport;
    Preference lockEliteHardware;
    Preference unlockEliteHardware;
    Preference launchFlaskEditor;
    Preference amiiboStats;
    Preference gameSeriesStats;
    Preference characterStats;
    Preference amiiboSeriesStats;
    Preference amiiboTypeStats;
    ListPreference imageNetworkSetting;
    CheckBoxPreference disableDebug;
    CheckBoxPreference stableChannel;

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
        enableTagTypeValidation = findPreference(getString(R.string.settings_tag_type_validation));
        enableAutomaticScan = findPreference(getString(R.string.settings_enable_automatic_scan));
        enablePowerTagSupport = findPreference(getString(R.string.settings_enable_power_tag_support));
        enableEliteSupport = findPreference(getString(R.string.settings_enable_elite_support));
        lockEliteHardware = findPreference(getString(R.string.lock_elite_hardware));
        unlockEliteHardware = findPreference(getString(R.string.unlock_elite_hardware));
        launchFlaskEditor = findPreference(getString(R.string.settings_open_flask_editor));
        amiiboStats = findPreference(getString(R.string.settings_info_amiibo));
        gameSeriesStats = findPreference(getString(R.string.settings_info_game_series));
        characterStats = findPreference(getString(R.string.settings_info_characters));
        amiiboSeriesStats = findPreference(getString(R.string.settings_info_amiibo_series));
        amiiboTypeStats = findPreference(getString(R.string.settings_info_amiibo_types));
        imageNetworkSetting = findPreference(getString(R.string.image_network_settings));
        disableDebug = findPreference(getString(R.string.settings_disable_debug));
        stableChannel = findPreference(getString(R.string.settings_stable_channel));

        this.enableTagTypeValidation.setChecked(prefs.enable_tag_type_validation().get());
        this.disableDebug.setChecked(prefs.settings_disable_debug().get());
        this.stableChannel.setChecked(prefs.settings_stable_channel().get());

        loadAmiiboManager();
        updateKeySummary();
        updateAmiiboStats();
        onImageNetworkChange(prefs.image_network_settings().get());

        boolean isElite = prefs.enable_elite_support().get();
        this.enableEliteSupport.setChecked(isElite);
        if (isElite && prefs.settings_elite_signature().get().length() > 1) {
            this.enableEliteSupport.setSummary(getString(
                    R.string.elite_signature, prefs.settings_elite_signature().get()));
        }
        lockEliteHardware.setVisible(isElite);
        unlockEliteHardware.setVisible(isElite);

        importKeys.setOnPreferenceClickListener(preference -> {
            onImportKeysClicked();
            return SettingsFragment.super.onPreferenceTreeClick(preference);
        });

        enableTagTypeValidation.setOnPreferenceClickListener(preference -> {
            prefs.enable_tag_type_validation().put(enableTagTypeValidation.isChecked());
            return SettingsFragment.super.onPreferenceTreeClick(preference);
        });

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

        enableEliteSupport.setOnPreferenceClickListener(preference -> {
            boolean isEnabled = enableEliteSupport.isChecked();
            prefs.enable_elite_support().put(enableEliteSupport.isChecked());
            if (isEnabled && prefs.settings_elite_signature().get().length() > 1)
                enableEliteSupport.setSummary(getString(R.string.elite_signature,
                        prefs.settings_elite_signature().get()));
            else
                enableEliteSupport.setSummary(getString(R.string.elite_details));
            lockEliteHardware.setVisible(isEnabled);
            unlockEliteHardware.setVisible(isEnabled);
            return SettingsFragment.super.onPreferenceTreeClick(preference);
        });

        lockEliteHardware.setOnPreferenceClickListener(preference -> {
            new AlertDialog.Builder(requireContext())
                    .setMessage(R.string.lock_elite_warning)
                    .setPositiveButton(R.string.write, (dialog, which) -> {
                        Intent lock = new Intent(requireContext(), NfcActivity.class);
                        lock.setAction(NFCIntent.ACTION_LOCK_AMIIBO);
                        startActivity(lock);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null).show();
            return SettingsFragment.super.onPreferenceTreeClick(preference);
        });

        unlockEliteHardware.setOnPreferenceClickListener(preference -> {
            new AlertDialog.Builder(requireContext())
                    .setMessage(R.string.prepare_unlock)
                    .setPositiveButton(R.string.start, (dialog, which) -> {
                        Intent unlock = new Intent(requireContext(), NfcActivity.class);
                        unlock.setAction(NFCIntent.ACTION_UNLOCK_UNIT);
                        startActivity(unlock);
                        dialog.dismiss();
                    }).show();
            return SettingsFragment.super.onPreferenceTreeClick(preference);
        });

        launchFlaskEditor.setOnPreferenceClickListener(preference -> {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            CustomTabsIntent customTabsIntent = builder.build();
            // builder.setActionButton(icon, description, pendingIntent, tint); // action button
            // builder.addMenuItem(menuItemTitle, menuItemPendingIntent); // menu item
            customTabsIntent.launchUrl(requireActivity(), Uri.parse("https://flask.run/"));
            return SettingsFragment.super.onPreferenceTreeClick(preference);
        });

        Preference syncInfo = findPreference(getString(R.string.settings_import_info_amiiboapi));
        if (null != syncInfo) {
            syncInfo.setOnPreferenceClickListener(preference -> {
                new JSONExecutor(API_LAST_UPDATED).setResultListener(result -> {
                    if (null != result) parseUpdateJSON(result, true);
                });
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
                    .setAdapter(new SettingsAmiiboAdapter(new ArrayList<>(
                            amiiboManager.amiibos.values())), null)
                    .setPositiveButton(R.string.close, null)
                    .show();
            return SettingsFragment.super.onPreferenceTreeClick(preference);
        });

        imageNetworkSetting.setOnPreferenceChangeListener((preference, newValue) -> {
            onImageNetworkChange(newValue.toString());
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

        disableDebug.setOnPreferenceClickListener(preference -> {
            prefs.settings_disable_debug().put(disableDebug.isChecked());
            return SettingsFragment.super.onPreferenceTreeClick(preference);
        });
        stableChannel.setOnPreferenceClickListener(preference -> {
            prefs.settings_stable_channel().put(stableChannel.isChecked());
            return SettingsFragment.super.onPreferenceTreeClick(preference);
        });

        Preference viewGuides = findPreference(getString(R.string.settings_view_guides));
        if (null != viewGuides) {
            viewGuides.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireActivity(), WebActivity.class)
                        .setAction(NFCIntent.SITE_GITLAB_README));
                return SettingsFragment.super.onPreferenceTreeClick(preference);
            });
        }
    }

    private void onImportKeysClicked() {
        showFileChooser(getString(R.string.decryption_keys), RESULT_KEYS);
    }

    private void onImageNetworkChange(String newValue) {
        int index = imageNetworkSetting.findIndexOfValue(newValue);
        if (index == -1) {
            onImageNetworkChange(IMAGE_NETWORK_ALWAYS);
        } else {
            prefs.image_network_settings().put(newValue);
            imageNetworkSetting.setValue(newValue);
            imageNetworkSetting.setSummary(imageNetworkSetting.getEntry());
        }
    }

    private void validateKeys(Uri data) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                this.keyManager.loadKey(data);
            } catch (Exception e) {
                Debug.Log(e);
                requireActivity().runOnUiThread(() ->
                        showSnackbar(e.getMessage(), Snackbar.LENGTH_SHORT));
            }
            if (Thread.currentThread().isInterrupted())
                return;

            ((BrowserActivity) requireActivity()).onRefresh();
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

        importKeys.setSummary(keySummary);
    }

    private void loadAmiiboManager() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AmiiboManager amiiboManager;
            try {
                amiiboManager = AmiiboManager.getAmiiboManager();
            } catch (IOException | JSONException | ParseException e) {
                Debug.Log(e);
                new Toasty(requireActivity()).Short(R.string.amiibo_failure_load);
                return;
            }
            if (Thread.currentThread().isInterrupted())
                return;

            setAmiiboManager(amiiboManager);
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

            if (Thread.currentThread().isInterrupted())
                return;

            try {
                AmiiboManager.saveDatabase(amiiboManager);
            } catch (JSONException | IOException e) {
                Debug.Log(e);
                new Toasty(requireActivity()).Short(R.string.amiibo_failure_update);
                return;
            }

            setAmiiboManager(amiiboManager);
            requireActivity().runOnUiThread(() -> showSnackbar(
                    getString(R.string.amiibo_info_updated), Snackbar.LENGTH_SHORT));
        });
    }

    private void resetAmiiboManager() {
        Executors.newSingleThreadExecutor().execute(() -> {
            requireContext().deleteFile(AmiiboManager.AMIIBO_DATABASE_FILE);

            AmiiboManager amiiboManager = null;
            try {
                amiiboManager = AmiiboManager.getDefaultAmiiboManager();
            } catch (IOException | JSONException | ParseException e) {
                Debug.Log(e);
                new Toasty(requireActivity()).Short(R.string.amiibo_failure_parse_default);
            }
            if (Thread.currentThread().isInterrupted())
                return;

            setAmiiboManager(amiiboManager);
            requireActivity().runOnUiThread(() -> showSnackbar(
                    getString(R.string.removing_amiibo_info), Snackbar.LENGTH_SHORT));
        });
    }

    private void setAmiiboManager(AmiiboManager amiiboManager) {
        this.amiiboManager = amiiboManager;
        new Thread(() -> GlideApp.get(TagMo.getContext()).clearDiskCache());
        requireActivity().runOnUiThread(() -> {
            GlideApp.get(requireContext()).clearMemory();
            updateAmiiboStats();
        });
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
        showSnackbar(getString(R.string.sync_amiibo_process), Snackbar.LENGTH_INDEFINITE);
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
                    if (Thread.currentThread().isInterrupted())
                        return;

                    AmiiboManager.saveDatabase(amiiboManager);
                    setAmiiboManager(amiiboManager);
                    requireActivity().runOnUiThread(() -> showSnackbar(
                            getString(R.string.sync_amiibo_complete), Snackbar.LENGTH_SHORT));
                } else {
                    throw new Exception(String.valueOf(statusCode));
                }
                prefs.lastUpdated().put(lastUpdated);
            } catch (Exception e) {
                Debug.Log(e);
                requireActivity().runOnUiThread(() ->
                        showSnackbar(getString(R.string.sync_amiibo_failed), Snackbar.LENGTH_SHORT));
            }
        });
    }

    private final ActivityResultLauncher<Intent> onLoadKeys = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;

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
                        ? Intent.ACTION_OPEN_DOCUMENT
                        : Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        intent.putExtra("android.content.extra.FANCY", true);

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

    private void showSnackbar(String msg, int length) {
        new IconifiedSnackbar(requireActivity()).buildSnackbar(msg, length, null).show();
    }

    private void parseUpdateJSON(String result, boolean isMenuClicked) {
        try {
            JSONObject jsonObject = new JSONObject(result);
            String lastUpdated = (String) jsonObject.get("lastUpdated");
            if (isMenuClicked) {
                downloadAmiiboAPIData(lastUpdated);
            } else if (!prefs.lastUpdated().get().equals(lastUpdated)) {
                new IconifiedSnackbar(requireActivity()).buildSnackbar(
                        getString(R.string.update_amiibo_api), Snackbar.LENGTH_LONG, null)
                        .setAction(R.string.sync, v -> downloadAmiiboAPIData(lastUpdated)).show();
            }
        } catch (Exception e) {
            Debug.Log(e);
        }
    }
}
