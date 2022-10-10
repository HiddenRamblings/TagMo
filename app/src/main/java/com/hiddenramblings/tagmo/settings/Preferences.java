package com.hiddenramblings.tagmo.settings;

import org.androidannotations.annotations.sharedpreferences.DefaultBoolean;
import org.androidannotations.annotations.sharedpreferences.DefaultInt;
import org.androidannotations.annotations.sharedpreferences.DefaultString;
import org.androidannotations.annotations.sharedpreferences.SharedPref;

@SuppressWarnings("unused")
@SharedPref(value = SharedPref.Scope.APPLICATION_DEFAULT)
public interface Preferences {
    String query();

    @DefaultInt(1/*SORT.NAME*/)
    int sort();

    String filterGameTitles();

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

    @DefaultBoolean(false)
    boolean disable_foomiibo_browser();

    @DefaultString(SettingsFragment.IMAGE_NETWORK_ALWAYS)
    String image_network_settings();

    @DefaultInt(0)
    int database_source_setting();

    @DefaultBoolean(false)
    boolean enable_power_tag_support();

    @DefaultBoolean(false)
    boolean enable_elite_support();

    @DefaultString("")
    String settings_elite_signature();

    @DefaultBoolean(false)
    boolean enable_flask_support();

    @DefaultBoolean(false)
    boolean settings_disable_debug();

    String browserRootFolder();

    String browserRootDocument();

    @DefaultInt(-1)
    int foomiiboOffset();

    @DefaultInt(200)
    int eliteBankCount();

    @DefaultInt(0)
    int eliteActiveBank();

    @DefaultInt(0)
    int flaskActiveSlot();

    @DefaultBoolean(true)
    boolean recursiveFolders();

    @DefaultBoolean(false)
    boolean preferEmulated();

    @DefaultInt(0)
    int applicationTheme();

    String downloadUrl();

    String lastUpdatedAPI();

    long lastUpdatedGit();
}
