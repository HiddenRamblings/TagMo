package com.hiddenramblings.tagmo.browser;

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

    private final String query = "query";
    public String query() {
        return getString(query, null);
    }
    public void query(String value) {
        putString(query, value);
    }

    private final String sort = "sort";
    public int sort() {
        return getInt(sort, 1/*SORT.NAME*/);
    }
    public void sort(int value) {
        putInt(sort, value);
    }

    private final String filterGameTitles = "filterGameTitles";
    public String filterGameTitles() {
        return getString(filterGameTitles, null);
    }
    public void filterGameTitles(String value) {
        putString(filterGameTitles, value);
    }

    private final String filterGameSeries = "filterGameSeries";
    public String filterGameSeries() {
        return getString(filterGameSeries, null);
    }
    public void filterGameSeries(String value) {
        putString(filterGameSeries, value);
    }

    private final String filterCharacter = "filterCharacter";
    public String filterCharacter() {
        return getString(filterCharacter, null);
    }
    public void filterCharacter(String value) {
        putString(filterCharacter, value);
    }

    private final String filterAmiiboSeries = "filterAmiiboSeries";
    public String filterAmiiboSeries() {
        return getString(filterAmiiboSeries, null);
    }
    public void filterAmiiboSeries(String value) {
        putString(filterAmiiboSeries, value);
    }

    private final String filterAmiiboType = "filterAmiiboType";
    public String filterAmiiboType() {
        return getString(filterAmiiboType, null);
    }
    public void filterAmiiboType(String value) {
        putString(filterAmiiboType, value);
    }

    private final String browserAmiiboView = "browserAmiiboView";
    public int browserAmiiboView() {
        return getInt(browserAmiiboView, 1/*VIEW.COMPACT*/);
    }
    public void browserAmiiboView(int value) {
        putInt(browserAmiiboView, value);
    }

    private final String guides_prompted = "guides_prompted";
    public boolean guides_prompted() {
        return getBoolean(guides_prompted, false);
    }
    public void guides_prompted(boolean value) {
        putBoolean(guides_prompted, value);
    }

    private final String enable_tag_type_validation = "enable_tag_type_validation";
    public boolean enable_tag_type_validation() {
        return getBoolean(enable_tag_type_validation, true);
    }
    public void enable_tag_type_validation(boolean value) {
        putBoolean(enable_tag_type_validation, value);
    }

    private final String enable_automatic_scan = "enable_automatic_scan";
    public boolean enable_automatic_scan() {
        return getBoolean(enable_automatic_scan, true);
    }
    public void enable_automatic_scan(boolean value) {
        putBoolean(enable_automatic_scan, value);
    }

    private final String disable_foomiibo = "disable_foomiibo_browser";
    public boolean disable_foomiibo() {
        return getBoolean(disable_foomiibo, false);
    }
    public void disable_foomiibo(boolean value) {
        putBoolean(disable_foomiibo, value);
    }

    private final String image_network = "image_network_settings";
    public String image_network() {
        return getString(image_network, SettingsFragment.IMAGE_NETWORK_ALWAYS);
    }
    public void image_network(String value) {
        putString(image_network, value);
    }

    private final String database_source = "database_source_setting";
    public int database_source() {
        return getInt(database_source, 0);
    }
    public void database_source(int value) {
        putInt(database_source, value);
    }

    private final String power_tag_support = "enable_power_tag_support";
    public boolean power_tag_support() {
        return getBoolean(power_tag_support, false);
    }
    public void power_tag_support(boolean value) {
        putBoolean(power_tag_support, value);
    }

    private final String elite_support = "enable_elite_support";
    public boolean elite_support() {
        return getBoolean(elite_support, false);
    }
    public void elite_support(boolean value) {
        putBoolean(elite_support, value);
    }

    private final String elite_signature = "settings_elite_signature";
    public String elite_signature() {
        return getString(elite_signature, "");
    }
    public void elite_signature(String value) {
        putString(elite_signature, value);
    }

    private final String flask_support = "enable_flask_support";
    public boolean flask_support() {
        return getBoolean(flask_support, false);
    }
    public void flask_support(boolean value) {
        putBoolean(flask_support, value);
    }

    private final String disable_debug = "settings_disable_debug";
    public boolean disable_debug() {
        return getBoolean(disable_debug, false);
    }
    public void disable_debug(boolean value) {
        putBoolean(disable_debug, value);
    }

    private final String browserRootFolder = "browserRootFolder";
    public String browserRootFolder() {
        return getString(browserRootFolder, null);
    }
    public void browserRootFolder(String value) {
        putString(browserRootFolder, value);
    }

    private final String browserRootDocument = "browserRootDocument";
    public String browserRootDocument() {
        return getString(browserRootDocument, null);
    }
    public void browserRootDocument(String value) {
        putString(browserRootDocument, value);
    }

    private final String foomiiboOffset = "foomiiboOffset";
    public int foomiiboOffset() {
        return getInt(foomiiboOffset, -1);
    }
    public void foomiiboOffset(int value) {
        putInt(foomiiboOffset, value);
    }

    private final String eliteBankCount = "eliteBankCount";
    public int eliteBankCount() {
        return getInt(eliteBankCount, 200);
    }
    public void eliteBankCount(int value) {
        putInt(eliteBankCount, value);
    }

    private final String eliteActiveBank = "eliteActiveBank";
    public int eliteActiveBank() {
        return getInt(eliteActiveBank, 0);
    }
    public void eliteActiveBank(int value) {
        putInt(eliteActiveBank, value);
    }

    private final String flaskActiveSlot = "flaskActiveSlot";
    public int flaskActiveSlot() {
        return getInt(flaskActiveSlot, 0);
    }
    public void flaskActiveSlot(int value) {
        putInt(flaskActiveSlot, value);
    }

    private final String recursiveFolders = "recursiveFolders";
    public boolean recursiveFolders() {
        return getBoolean(recursiveFolders, true);
    }
    public void recursiveFolders(boolean value) {
        putBoolean(recursiveFolders, value);
    }

    private final String preferEmulated = "preferEmulated";
    public boolean preferEmulated() {
        return getBoolean(preferEmulated, false);
    }
    public void preferEmulated(boolean value) {
        putBoolean(preferEmulated, value);
    }

    private final String applicationTheme = "applicationTheme";
    public int applicationTheme() {
        return getInt(applicationTheme, 0);
    }
    public void applicationTheme(int value) {
        putInt(applicationTheme, value);
    }

    public final String downloadUrl = "downloadUrl";
    public String downloadUrl() {
        return getString(downloadUrl, null);
    }
    public void downloadUrl(String value) {
        putString(downloadUrl, value);
    }

    private final String lastUpdatedAPI = "lastUpdatedAPI";
    public String lastUpdatedAPI() {
        return getString(lastUpdatedAPI, null);
    }
    public void lastUpdatedAPI(String value) {
        putString(lastUpdatedAPI, value);
    }

    private final String lastUpdatedGit = "lastUpdatedGit";
    public long lastUpdatedGit() {
        return getLong(lastUpdatedGit, 0);
    }
    public void lastUpdatedGit(long value) {
        putLong(lastUpdatedGit, value);
    }
}
