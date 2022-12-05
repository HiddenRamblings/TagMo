package com.hiddenramblings.tagmo.browser

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.hiddenramblings.tagmo.GlideTagModule
import com.hiddenramblings.tagmo.eightbit.io.Debug

class Preferences(context: Context?) {
    private val prefs: SharedPreferences
    private fun getBoolean(pref: String, defValue: Boolean): Boolean {
        return prefs.getBoolean(pref, defValue)
    }

    private fun putBoolean(pref: String, value: Boolean) {
        prefs.edit().putBoolean(pref, value).apply()
    }

    private fun getInt(pref: String, defValue: Int): Int {
        return prefs.getInt(pref, defValue)
    }

    private fun putInt(pref: String, value: Int) {
        prefs.edit().putInt(pref, value).apply()
    }

    private fun getLong(pref: String, defValue: Long): Long {
        return prefs.getLong(pref, defValue)
    }

    private fun putLong(pref: String, value: Long) {
        prefs.edit().putLong(pref, value).apply()
    }

    private fun getString(pref: String, defValue: String?): String? {
        return prefs.getString(pref, defValue)
    }

    private fun putString(pref: String, value: String?) {
        prefs.edit().putString(pref, value).apply()
    }

    fun remove(pref: String?) {
        prefs.edit().remove(pref).apply()
    }

    private val query = "query"
    fun query(): String? {
        return getString(query, null)
    }

    fun query(value: String?) {
        putString(query, value)
    }

    private val sort = "sort"
    fun sort(): Int {
        return getInt(sort, 1 /*SORT.NAME*/)
    }

    fun sort(value: Int) {
        putInt(sort, value)
    }

    private val filterGameTitles = "filterGameTitles"
    fun filterGameTitles(): String? {
        return getString(filterGameTitles, null)
    }

    fun filterGameTitles(value: String?) {
        putString(filterGameTitles, value)
    }

    private val filterGameSeries = "filterGameSeries"
    fun filterGameSeries(): String? {
        return getString(filterGameSeries, null)
    }

    fun filterGameSeries(value: String?) {
        putString(filterGameSeries, value)
    }

    private val filterCharacter = "filterCharacter"
    fun filterCharacter(): String? {
        return getString(filterCharacter, null)
    }

    fun filterCharacter(value: String?) {
        putString(filterCharacter, value)
    }

    private val filterAmiiboSeries = "filterAmiiboSeries"
    fun filterAmiiboSeries(): String? {
        return getString(filterAmiiboSeries, null)
    }

    fun filterAmiiboSeries(value: String?) {
        putString(filterAmiiboSeries, value)
    }

    private val filterAmiiboType = "filterAmiiboType"
    fun filterAmiiboType(): String? {
        return getString(filterAmiiboType, null)
    }

    fun filterAmiiboType(value: String?) {
        putString(filterAmiiboType, value)
    }

    private val browserAmiiboView = "browserAmiiboView"
    fun browserAmiiboView(): Int {
        return getInt(browserAmiiboView, 1 /*VIEW.COMPACT*/)
    }

    fun browserAmiiboView(value: Int) {
        putInt(browserAmiiboView, value)
    }

    private val guides_prompted = "guides_prompted"
    fun guides_prompted(): Boolean {
        return getBoolean(guides_prompted, false)
    }

    fun guides_prompted(value: Boolean) {
        putBoolean(guides_prompted, value)
    }

    private val enable_tag_type_validation = "enable_tag_type_validation"
    fun enable_tag_type_validation(): Boolean {
        return getBoolean(enable_tag_type_validation, true)
    }

    fun enable_tag_type_validation(value: Boolean) {
        putBoolean(enable_tag_type_validation, value)
    }

    private val enable_automatic_scan = "enable_automatic_scan"
    fun enable_automatic_scan(): Boolean {
        return getBoolean(enable_automatic_scan, true)
    }

    fun enable_automatic_scan(value: Boolean) {
        putBoolean(enable_automatic_scan, value)
    }

    private val disable_foomiibo = "disable_foomiibo_browser"
    fun disable_foomiibo(): Boolean {
        return getBoolean(disable_foomiibo, false)
    }

    fun disable_foomiibo(value: Boolean) {
        putBoolean(disable_foomiibo, value)
    }

    private val image_network = "image_network_settings"
    fun image_network(): String? {
        return getString(image_network, GlideTagModule.IMAGE_NETWORK_ALWAYS)
    }

    fun image_network(value: String?) {
        putString(image_network, value)
    }

    private val database_source = "database_source_setting"
    fun database_source(): Int {
        return getInt(database_source, 0)
    }

    fun database_source(value: Int) {
        putInt(database_source, value)
    }

    private val power_tag_support = "enable_power_tag_support"
    fun power_tag_support(): Boolean {
        return getBoolean(power_tag_support, false)
    }

    fun power_tag_support(value: Boolean) {
        putBoolean(power_tag_support, value)
    }

    private val elite_support = "enable_elite_support"
    fun elite_support(): Boolean {
        return getBoolean(elite_support, false)
    }

    fun elite_support(value: Boolean) {
        putBoolean(elite_support, value)
    }

    private val elite_signature = "settings_elite_signature"
    fun elite_signature(): String? {
        return getString(elite_signature, "")
    }

    fun elite_signature(value: String?) {
        putString(elite_signature, value)
    }

    private val flask_support = "enable_flask_support"
    fun flask_support(): Boolean {
        return getBoolean(flask_support, false)
    }

    fun flask_support(value: Boolean) {
        putBoolean(flask_support, value)
    }

    private val software_layer = "settings_software_layer"
    fun software_layer(): Boolean {
        return getBoolean(software_layer, Debug.isOxygenOS)
    }

    fun software_layer(value: Boolean) {
        putBoolean(software_layer, value)
    }

    private val disable_debug = "settings_disable_debug"
    fun disable_debug(): Boolean {
        return getBoolean(disable_debug, false)
    }

    fun disable_debug(value: Boolean) {
        putBoolean(disable_debug, value)
    }

    private val browserRootFolder = "browserRootFolder"
    fun browserRootFolder(): String? {
        return getString(browserRootFolder, null)
    }

    fun browserRootFolder(value: String?) {
        putString(browserRootFolder, value)
    }

    private val browserRootDocument = "browserRootDocument"
    fun browserRootDocument(): String? {
        return getString(browserRootDocument, null)
    }

    fun browserRootDocument(value: String?) {
        putString(browserRootDocument, value)
    }

    private val foomiiboOffset = "foomiiboOffset"
    fun foomiiboOffset(): Int {
        return getInt(foomiiboOffset, -1)
    }

    fun foomiiboOffset(value: Int) {
        putInt(foomiiboOffset, value)
    }

    private val eliteBankCount = "eliteBankCount"
    fun eliteBankCount(): Int {
        return getInt(eliteBankCount, 200)
    }

    fun eliteBankCount(value: Int) {
        putInt(eliteBankCount, value)
    }

    private val eliteActiveBank = "eliteActiveBank"
    fun eliteActiveBank(): Int {
        return getInt(eliteActiveBank, 0)
    }

    fun eliteActiveBank(value: Int) {
        putInt(eliteActiveBank, value)
    }

    private val flaskActiveSlot = "flaskActiveSlot"
    fun flaskActiveSlot(): Int {
        return getInt(flaskActiveSlot, 0)
    }

    fun flaskActiveSlot(value: Int) {
        putInt(flaskActiveSlot, value)
    }

    private val recursiveFolders = "recursiveFolders"
    fun recursiveFolders(): Boolean {
        return getBoolean(recursiveFolders, true)
    }

    fun recursiveFolders(value: Boolean) {
        putBoolean(recursiveFolders, value)
    }

    private val preferEmulated = "preferEmulated"
    fun preferEmulated(): Boolean {
        return getBoolean(preferEmulated, false)
    }

    fun preferEmulated(value: Boolean) {
        putBoolean(preferEmulated, value)
    }

    private val applicationTheme = "applicationTheme"
    fun applicationTheme(): Int {
        return getInt(applicationTheme, 0)
    }

    fun applicationTheme(value: Int) {
        putInt(applicationTheme, value)
    }

    val downloadUrl = "downloadUrl"
    fun downloadUrl(): String? {
        return getString(downloadUrl, null)
    }

    fun downloadUrl(value: String?) {
        putString(downloadUrl, value)
    }

    private val lastUpdatedAPI = "lastUpdatedAPI"
    fun lastUpdatedAPI(): String? {
        return getString(lastUpdatedAPI, null)
    }

    fun lastUpdatedAPI(value: String?) {
        putString(lastUpdatedAPI, value)
    }

    private val lastUpdatedGit = "lastUpdatedGit"

    init {
        prefs = PreferenceManager.getDefaultSharedPreferences(context!!)
    }

    fun lastUpdatedGit(): Long {
        return getLong(lastUpdatedGit, 0)
    }

    fun lastUpdatedGit(value: Long) {
        putLong(lastUpdatedGit, value)
    }
}