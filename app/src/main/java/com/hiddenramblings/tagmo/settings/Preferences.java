package com.hiddenramblings.tagmo.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class Preferences {

    private final SharedPreferences prefs;

    public Preferences(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    private boolean getBoolean(String pref, boolean defValue) {
        return prefs.getBoolean(pref, defValue);
    }
    private void putBoolean(String pref, boolean value) {
        prefs.edit().putBoolean(pref, value).apply();
    }

    private int getInt(String pref, int defValue) {
        return prefs.getInt(pref, defValue);
    }
    private void putInt(String pref, int value) {
        prefs.edit().putInt(pref, value).apply();
    }

    private long getLong(String pref, long defValue) {
        return prefs.getLong(pref, defValue);
    }
    private void putLong(String pref, long value) {
        prefs.edit().putLong(pref, value).apply();
    }

    private String getString(String pref, String defValue) {
        return prefs.getString(pref, defValue);
    }
    private void putString(String pref, String value) {
        prefs.edit().putString(pref, value).apply();
    }

    public void remove(String pref) {
        prefs.edit().remove(pref).apply();
    }

    public String query() {
        return getString("query", null);
    }
    public void query(String value) {
        putString("query", value);
    }

    public int sort() {
        return getInt("sort", 1/*SORT.NAME*/);
    }
    public void sort(int value) {
        putInt("sort", value);
    }

    public String filterGameTitles() {
        return getString("query", null);
    }
    public void filterGameTitles(String value) {
        putString("filterGameTitles", value);
    }

    public String filterGameSeries() {
        return getString("query", null);
    }
    public void filterGameSeries(String value) {
        putString("filterGameSeries", value);
    }

    public String filterCharacter() {
        return getString("filterCharacter", null);
    }
    public void filterCharacter(String value) {
        putString("filterCharacter", value);
    }

    public String filterAmiiboSeries() {
        return getString("filterAmiiboSeries", null);
    }
    public void filterAmiiboSeries(String value) {
        putString("filterAmiiboSeries", value);
    }

    public String filterAmiiboType() {
        return getString("filterAmiiboType", null);
    }
    public void filterAmiiboType(String value) {
        putString("filterAmiiboType", value);
    }

    public int browserAmiiboView() {
        return getInt("browserAmiiboView", 1/*VIEW.COMPACT*/);
    }
    public void browserAmiiboView(int value) {
        putInt("browserAmiiboView", value);
    }

    public boolean enable_tag_type_validation() {
        return getBoolean("enable_tag_type_validation", true);
    }
    public void enable_tag_type_validation(boolean value) {
        putBoolean("enable_tag_type_validation", value);
    }

    public boolean enable_automatic_scan() {
        return getBoolean("enable_automatic_scan", true);
    }
    public void enable_automatic_scan(boolean value) {
        putBoolean("enable_automatic_scan", value);
    }

    public boolean disable_foomiibo_browser() {
        return getBoolean("disable_foomiibo_browser", false);
    }
    public void disable_foomiibo_browser(boolean value) {
        putBoolean("disable_foomiibo_browser", value);
    }

    public String image_network_settings() {
        return getString("image_network_settings", SettingsFragment.IMAGE_NETWORK_ALWAYS);
    }
    public void image_network_settings(String value) {
        putString("image_network_settings", value);
    }

    public int database_source_setting() {
        return getInt("database_source_setting", 0);
    }
    public void database_source_setting(int value) {
        putInt("database_source_setting", value);
    }

    public boolean enable_power_tag_support() {
        return getBoolean("enable_power_tag_support", false);
    }
    public void enable_power_tag_support(boolean value) {
        putBoolean("enable_power_tag_support", value);
    }

    public boolean enable_elite_support() {
        return getBoolean("enable_elite_support", false);
    }
    public void enable_elite_support(boolean value) {
        putBoolean("enable_elite_support", value);
    }

    public String settings_elite_signature() {
        return getString("settings_elite_signature", "");
    }
    public void settings_elite_signature(String value) {
        putString("settings_elite_signature", value);
    }

    public boolean enable_flask_support() {
        return getBoolean("enable_flask_support", false);
    }
    public void enable_flask_support(boolean value) {
        putBoolean("enable_flask_support", value);
    }

    public boolean settings_disable_debug() {
        return getBoolean("settings_disable_debug", false);
    }
    public void settings_disable_debug(boolean value) {
        putBoolean("settings_disable_debug", value);
    }

    public String browserRootFolder() {
        return getString("browserRootFolder", null);
    }
    public void browserRootFolder(String value) {
        putString("browserRootFolder", value);
    }

    public String browserRootDocument() {
        return getString("browserRootDocument", null);
    }
    public void browserRootDocument(String value) {
        putString("browserRootDocument", value);
    }

    public int foomiiboOffset() {
        return getInt("foomiiboOffset", -1);
    }
    public void foomiiboOffset(int value) {
        putInt("foomiiboOffset", value);
    }

    public int eliteBankCount() {
        return getInt("eliteBankCount", 200);
    }
    public void eliteBankCount(int value) {
        putInt("eliteBankCount", value);
    }

    public int eliteActiveBank() {
        return getInt("eliteActiveBank", 0);
    }
    public void eliteActiveBank(int value) {
        putInt("eliteActiveBank", value);
    }

    public int flaskActiveSlot() {
        return getInt("flaskActiveSlot", 0);
    }
    public void flaskActiveSlot(int value) {
        putInt("flaskActiveSlot", value);
    }

    public boolean recursiveFolders() {
        return getBoolean("recursiveFolders", true);
    }
    public void recursiveFolders(boolean value) {
        putBoolean("recursiveFolders", value);
    }

    public boolean preferEmulated() {
        return getBoolean("preferEmulated", false);
    }
    public void preferEmulated(boolean value) {
        putBoolean("preferEmulated", value);
    }

    public int applicationTheme() {
        return getInt("applicationTheme", 0);
    }
    public void applicationTheme(int value) {
        putInt("applicationTheme", value);
    }

    public String downloadUrl() {
        return getString("downloadUrl", null);
    }
    public void downloadUrl(String value) {
        putString("downloadUrl", value);
    }

    public String lastUpdatedAPI() {
        return getString("lastUpdatedAPI", null);
    }
    public void lastUpdatedAPI(String value) {
        putString("lastUpdatedAPI", value);
    }

    public long lastUpdatedGit() {
        return getLong("lastUpdatedGit", 0);
    }
    public void lastUpdatedGit(long value) {
        putLong("lastUpdatedGit", value);
    }
}
