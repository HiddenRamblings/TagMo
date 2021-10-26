package com.hiddenramblings.tagmo.settings;

import android.annotation.SuppressLint;

import com.hiddenramblings.tagmo.BrowserActivity;
import com.hiddenramblings.tagmo.R;

import org.androidannotations.annotations.sharedpreferences.DefaultBoolean;
import org.androidannotations.annotations.sharedpreferences.DefaultInt;
import org.androidannotations.annotations.sharedpreferences.DefaultLong;
import org.androidannotations.annotations.sharedpreferences.DefaultString;
import org.androidannotations.annotations.sharedpreferences.SharedPref;

@SuppressLint("NonConstantResourceId")
@SuppressWarnings("unused")
@SharedPref(value = SharedPref.Scope.UNIQUE)
public interface Preferences {
    String query();

    @DefaultInt(BrowserActivity.SORT_NAME)
    int sort();

    String filterGameSeries();

    String filterCharacter();

    String filterAmiiboSeries();

    String filterAmiiboType();

    @DefaultBoolean(keyRes = R.string.settings_enable_tag_type_validation, value = true)
    boolean enableTagTypeValidation();

    @DefaultBoolean(keyRes = R.string.settings_enable_power_tag_support, value = false)
    boolean enablePowerTagSupport();

    @DefaultBoolean(keyRes = R.string.settings_enable_elite_support, value = false)
    boolean enableEliteSupport();

    @DefaultString(keyRes = R.string.settings_elite_signature, value = "")
    String eliteSignature();

    @DefaultInt(keyRes = R.string.settings_elite_bank_count, value = 1)
    int eliteBankCount();

    @DefaultInt(0)
    int eliteActiveBank();

    @DefaultInt(BrowserActivity.VIEW_TYPE_COMPACT)
    int browserAmiiboView();

    @DefaultString(keyRes = R.string.image_network_settings, value = SettingsFragment.IMAGE_NETWORK_ALWAYS)
    String imageNetworkSetting();

    String browserRootFolder();

    @DefaultBoolean(true)
    boolean recursiveFolders();

    @DefaultBoolean(false)
    boolean showMissingFiles();

    @DefaultBoolean(keyRes = R.string.settings_disable_debug, value = false)
    boolean disableDebug();

    @DefaultBoolean(keyRes = R.string.settings_ignore_sdcard, value = false)
    boolean ignoreSdcard();

    @DefaultBoolean(keyRes = R.string.settings_stable_channel, value = true)
    boolean stableChannel();

    @DefaultLong(-1)
    long lastModified();

    String downloadUrl();
}
