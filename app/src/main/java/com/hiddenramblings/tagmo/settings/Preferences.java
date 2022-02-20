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

    @DefaultBoolean(false)
    boolean hasAcceptedTOS();

    @DefaultBoolean(true)
    boolean recursiveFolders();

    @DefaultBoolean(false)
    boolean showDownloads();

    @DefaultBoolean(false)
    boolean preferEmulated();

    String lastUpdated();

    String downloadUrl();

//    SharedPreferences preferences = TagMo.getContext()
//            .getSharedPreferences("Preferences", Context.MODE_MULTI_PROCESS);
//
//    public class query {
//        public String get() {
//            return preferences.getString("query", null);
//        }
//
//        public void put(String value) {
//            preferences.edit().putString("query", value).apply();
//        }
//    }
//
//    public class sort {
//        public int get() {
//            return preferences.getInt("sort", 1/*SORT.NAME*/);
//        }
//
//        public void put(int value) {
//            preferences.edit().putInt("sort", value).apply();
//        }
//    }
//
//    public class filterGameSeries {
//        public String get() {
//            return preferences.getString("filterGameSeries", null);
//        }
//
//        public void put(String value) {
//            preferences.edit().putString("filterGameSeries", value).apply();
//        }
//    }
//
//    public class filterCharacter {
//        public String get() {
//            return preferences.getString("filterCharacter", null);
//        }
//
//        public void put(String value) {
//            preferences.edit().putString("filterCharacter", value).apply();
//        }
//    }
//
//    public class filterAmiiboSeries {
//        public String get() {
//            return preferences.getString("filterAmiiboSeries", null);
//        }
//
//        public void put(String value) {
//            preferences.edit().putString("filterAmiiboSeries", value).apply();
//        }
//    }
//
//    public class filterAmiiboType {
//        public String get() {
//            return preferences.getString("filterAmiiboType", null);
//        }
//
//        public void put(String value) {
//            preferences.edit().putString("filterAmiiboType", value).apply();
//        }
//    }
//
//    public class browserAmiiboView {
//        public int get() {
//            return preferences.getInt("browserAmiiboView", 1/*VIEW.COMPACT*/);
//        }
//
//        public void put(int value) {
//            preferences.edit().putInt("browserAmiiboView", value).apply();
//        }
//    }
//
//    public class enable_tag_type_validation {
//        public boolean get() {
//            return preferences.getBoolean("enable_tag_type_validation", true);
//        }
//
//        public void put(boolean value) {
//            preferences.edit().putBoolean("enable_tag_type_validation", value).apply();
//        }
//    }
//
//    public class enable_automatic_scan {
//        public boolean get() {
//            return preferences.getBoolean("enable_automatic_scan", true);
//        }
//
//        public void put(boolean value) {
//            preferences.edit().putBoolean("enable_automatic_scan", value).apply();
//        }
//    }
//
//    public class image_network_settings {
//        public String get() {
//            return preferences.getString("image_network_settings",
//                    SettingsFragment.IMAGE_NETWORK_ALWAYS);
//        }
//
//        public void put(String value) {
//            preferences.edit().putString("image_network_settings", value).apply();
//        }
//    }
//
//    public class enable_power_tag_support {
//        public boolean get() {
//            return preferences.getBoolean("enable_power_tag_support", false);
//        }
//
//        public void put(boolean value) {
//            preferences.edit().putBoolean("enable_power_tag_support", value).apply();
//        }
//    }
//
//    public class enable_elite_support {
//        public boolean get() {
//            return preferences.getBoolean("enable_elite_support", false);
//        }
//
//        public void put(boolean value) {
//            preferences.edit().putBoolean("enable_elite_support", value).apply();
//        }
//    }
//
//    public class settings_elite_signature {
//        public String get() {
//            return preferences.getString("settings_elite_signature", "");
//        }
//
//        public void put(String value) {
//            preferences.edit().putString("settings_elite_signature", value).apply();
//        }
//    }
//
//    public class settings_disable_debug {
//        public boolean get() {
//            return preferences.getBoolean("settings_disable_debug", false);
//        }
//
//        public void put(boolean value) {
//            preferences.edit().putBoolean("settings_disable_debug", value).apply();
//        }
//    }
//
//    public class settings_stable_channel {
//        public boolean get() {
//            return preferences.getBoolean("settings_stable_channel", true);
//        }
//
//        public void put(boolean value) {
//            preferences.edit().putBoolean("settings_stable_channel", value).apply();
//        }
//    }
//
//    public class browserRootFolder {
//        public String get() {
//            return preferences.getString("browserRootFolder", null);
//        }
//
//        public void put(String value) {
//            preferences.edit().putString("browserRootFolder", value).apply();
//        }
//    }
//
//    public class eliteBankCount {
//        public int get() {
//            return preferences.getInt("eliteBankCount", 200);
//        }
//
//        public void put(int value) {
//            preferences.edit().putInt("eliteBankCount", value).apply();
//        }
//    }
//
//    public class eliteActiveBank {
//        public int get() {
//            return preferences.getInt("eliteActiveBank", 0);
//        }
//
//        public void put(int value) {
//            preferences.edit().putInt("eliteActiveBank", value).apply();
//        }
//    }
//
//    public class recursiveFolders {
//        public boolean get() {
//            return preferences.getBoolean("recursiveFolders", true);
//        }
//
//        public void put(boolean value) {
//            preferences.edit().putBoolean("recursiveFolders", value).apply();
//        }
//    }
//
//    public class showMissingFiles {
//        public boolean get() {
//            return preferences.getBoolean("showMissingFiles", false);
//        }
//
//        public void put(boolean value) {
//            preferences.edit().putBoolean("showMissingFiles", value).apply();
//        }
//    }
//
//    public class showDownloads {
//        public boolean get() {
//            return preferences.getBoolean("showDownloads", false);
//        }
//
//        public void put(boolean value) {
//            preferences.edit().putBoolean("showDownloads", value).apply();
//        }
//    }
//
//    public class preferEmulated {
//        public boolean get() {
//            return preferences.getBoolean("preferEmulated", false);
//        }
//
//        public void put(boolean value) {
//            preferences.edit().putBoolean("preferEmulated", value).apply();
//        }
//    }
//
//    public class lastUpdated {
//        public String get() {
//            return preferences.getString("lastUpdated", null);
//        }
//
//        public void put(String value) {
//            preferences.edit().putString("lastUpdated", value).apply();
//        }
//    }
//
//    public class downloadUrl {
//        public String get() {
//            return preferences.getString("downloadUrl", null);
//        }
//
//        public void put(String value) {
//            preferences.edit().putString("downloadUrl", value).apply();
//        }
//    }
}
