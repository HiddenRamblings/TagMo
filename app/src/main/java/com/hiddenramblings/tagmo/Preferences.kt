package com.hiddenramblings.tagmo

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

    @Suppress("SameParameterValue")
    private fun getLong(pref: String, defValue: Long): Long {
        return prefs.getLong(pref, defValue)
    }

    @Suppress("SameParameterValue")
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

    private val guidesPrompted = "guides_prompted"
    fun guidesPrompted(): Boolean {
        return getBoolean(guidesPrompted, false)
    }

    fun guidesPrompted(value: Boolean) {
        putBoolean(guidesPrompted, value)
    }

    private val tagTypeValidation = "enable_tag_type_validation"
    fun tagTypeValidation(): Boolean {
        return getBoolean(tagTypeValidation, true)
    }

    fun tagTypeValidation(value: Boolean) {
        putBoolean(tagTypeValidation, value)
    }

    private val automaticScan = "enable_automatic_scan"
    fun automaticScan(value: Boolean) {
        putBoolean(automaticScan, value)
    }

    private val foomiiboDisabled = "disable_foomiibo_browser"
    fun foomiiboDisabled(): Boolean {
        return getBoolean(foomiiboDisabled, false)
    }

    fun foomiiboDisabled(value: Boolean) {
        putBoolean(foomiiboDisabled, value)
    }

    private val imageNetwork = "image_network_settings"
    fun imageNetwork(): String? {
        return getString(imageNetwork, GlideTagModule.IMAGE_NETWORK_ALWAYS)
    }

    fun imageNetwork(value: String?) {
        putString(imageNetwork, value)
    }

    private val databaseSource = "database_source_setting"
    fun databaseSource(): Int {
        return getInt(databaseSource, 0)
    }

    fun databaseSource(value: Int) {
        putInt(databaseSource, value)
    }

    private val powerTagEnabled = "enable_power_tag_support"
    fun powerTagEnabled(): Boolean {
        return getBoolean(powerTagEnabled, false)
    }

    fun powerTagEnabled(value: Boolean) {
        putBoolean(powerTagEnabled, value)
    }

    private val eliteEnabled = "enable_elite_support"
    fun eliteEnabled(): Boolean {
        return getBoolean(eliteEnabled, false)
    }

    fun eliteEnabled(value: Boolean) {
        putBoolean(eliteEnabled, value)
    }

    private val eliteSignature = "settings_elite_signature"
    fun eliteSignature(): String? {
        return getString(eliteSignature, "")
    }

    fun eliteSignature(value: String?) {
        putString(eliteSignature, value)
    }

    private val flaskEnabled = "enable_flask_support"
    fun flaskEnabled(): Boolean {
        return getBoolean(flaskEnabled, false)
    }

    fun flaskEnabled(value: Boolean) {
        putBoolean(flaskEnabled, value)
    }

    private val softwareLayer = "settings_software_layer"
    fun softwareLayer(): Boolean {
        return getBoolean(softwareLayer, Debug.isOxygenOS)
    }

    fun softwareLayer(value: Boolean) {
        putBoolean(softwareLayer, value)
    }

    private val disableDebug = "settings_disable_debug"
    fun disableDebug(): Boolean {
        return getBoolean(disableDebug, false)
    }

    fun disableDebug(value: Boolean) {
        putBoolean(disableDebug, value)
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