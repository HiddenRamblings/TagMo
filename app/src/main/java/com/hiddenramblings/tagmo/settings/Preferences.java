package com.hiddenramblings.tagmo.settings;

import org.androidannotations.annotations.sharedpreferences.DefaultBoolean;
import org.androidannotations.annotations.sharedpreferences.DefaultInt;
import org.androidannotations.annotations.sharedpreferences.DefaultString;
import org.androidannotations.annotations.sharedpreferences.SharedPref;

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

    @DefaultInt(1/*VIEW.COMPACT*/)
    int browserAmiiboView();

    @DefaultBoolean(true)
    boolean enable_tag_type_validation();

    @DefaultBoolean(true)
    boolean enable_automatic_scan();

    @DefaultString(SettingsFragment.IMAGE_NETWORK_ALWAYS)
    String image_network_settings();

    @DefaultBoolean(false)
    boolean enable_power_tag_support();

    @DefaultBoolean(false)
    boolean enable_elite_support();

    @DefaultString("")
    String settings_elite_signature();

    @DefaultBoolean(false)
    boolean settings_disable_debug();

    @DefaultBoolean(true)
    boolean settings_stable_channel();

    String browserRootFolder();

    @DefaultInt(200)
    int eliteBankCount();

    @DefaultInt(0)
    int eliteActiveBank();

    @DefaultBoolean(true)
    boolean recursiveFolders();

    @DefaultBoolean(false)
    boolean showDownloads();

    @DefaultBoolean(false)
    boolean showMissingFiles();

    @DefaultBoolean(false)
    boolean preferEmulated();

    @DefaultBoolean(false)
    boolean enableScaling();

    String lastUpdated();

    String downloadUrl();
}
