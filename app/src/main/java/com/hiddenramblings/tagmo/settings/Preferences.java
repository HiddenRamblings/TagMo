package com.hiddenramblings.tagmo.settings;

import android.annotation.SuppressLint;

import com.hiddenramblings.tagmo.R;

import org.androidannotations.annotations.sharedpreferences.DefaultBoolean;
import org.androidannotations.annotations.sharedpreferences.DefaultInt;
import org.androidannotations.annotations.sharedpreferences.DefaultString;
import org.androidannotations.annotations.sharedpreferences.SharedPref;

@SuppressLint("NonConstantResourceId")
@SuppressWarnings("unused")
@SharedPref(value = SharedPref.Scope.UNIQUE)
public interface Preferences {
    String query();

    @DefaultInt(1/*SORT.NAME*/)
    int sort();

    String filterGameSeries();

    String filterCharacter();

    String filterAmiiboSeries();

    String filterAmiiboType();

    @DefaultBoolean(keyRes = R.string.settings_tag_type_validation, value = true)
    boolean enableTagTypeValidation();

    @DefaultBoolean(keyRes = R.string.settings_enable_automatic_scan, value = true)
    boolean enableAutomaticScan();

    @DefaultBoolean(keyRes = R.string.settings_enable_power_tag_support, value = false)
    boolean enablePowerTagSupport();

    @DefaultBoolean(keyRes = R.string.settings_enable_elite_support, value = false)
    boolean enableEliteSupport();

    @DefaultString(keyRes = R.string.settings_elite_signature, value = "")
    String eliteSignature();

    @DefaultInt(200)
    int eliteBankCount();

    @DefaultInt(0)
    int eliteActiveBank();

    @DefaultInt(1/*VIEW.COMPACT*/)
    int browserAmiiboView();

    @DefaultString(keyRes = R.string.image_network_settings,
            value = SettingsFragment.IMAGE_NETWORK_ALWAYS)
    String imageNetworkSetting();

    String browserRootFolder();

    @DefaultBoolean(true)
    boolean recursiveFolders();

    @DefaultBoolean(false)
    boolean showDownloads();

    @DefaultBoolean(false)
    boolean showMissingFiles();

    @DefaultBoolean(keyRes = R.string.settings_disable_debug, value = false)
    boolean disableDebug();

    @DefaultBoolean(false)
    boolean preferEmulated();

    @DefaultBoolean(keyRes = R.string.settings_stable_channel, value = true)
    boolean stableChannel();

    @DefaultBoolean(false)
    boolean enableScaling();

    String lastUpdated();

    String downloadUrl();
}
