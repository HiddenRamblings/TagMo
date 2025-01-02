package com.hiddenramblings.tagmo

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.eightbit.widget.FABulous

class Preferences(context: Context) {

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
    private fun getFloat(pref: String, defValue: Float): Float {
        return prefs.getFloat(pref, defValue)
    }

    @Suppress("SameParameterValue")
    private fun putFloat(pref: String, value: Float) {
        prefs.edit().putFloat(pref, value).apply()
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

    private val fabulousX = "fabulousX"
    fun fabulousX(fab: FABulous): Float {
        return getFloat(fabulousX, fab.x)
    }

    fun fabulousX(value: Float) {
        putFloat(fabulousX, value)
    }

    private val fabulousY = "fabulousY"
    fun fabulousY(fab: FABulous): Float {
        return getFloat(fabulousY, fab.y)
    }

    fun fabulousY(value: Float) {
        putFloat(fabulousY, value)
    }

    private val fabulousHorzX = "fabulousHorzX"
    fun fabulousHorzX(fab: FABulous): Float {
        return getFloat(fabulousHorzX, fab.x)
    }

    fun fabulousHorzX(value: Float) {
        putFloat(fabulousHorzX, value)
    }

    private val fabulousHorzY = "fabulousHorzY"
    fun fabulousHorzY(fab: FABulous): Float {
        return getFloat(fabulousHorzY, fab.y)
    }

    fun fabulousHorzY(value: Float) {
        putFloat(fabulousHorzY, value)
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

    private val showCompat3DS = "settings_show_games_ds"
    fun showCompat3DS(): Boolean {
        return getBoolean(showCompat3DS, true)
    }

    fun showCompat3DS(value: Boolean) {
        putBoolean(showCompat3DS, value)
    }

    private val showCompatWiiU = "settings_show_games_wii"
    fun showCompatWiiU(): Boolean {
        return getBoolean(showCompatWiiU, true)
    }

    fun showCompatWiiU(value: Boolean) {
        putBoolean(showCompatWiiU, value)
    }

    private val showCompatSwitch = "settings_show_games_nx"
    fun showCompatSwitch(): Boolean {
        return getBoolean(showCompatSwitch, true)
    }

    fun showCompatSwitch(value: Boolean) {
        putBoolean(showCompatSwitch, value)
    }

    private val persistSkipLockInfo = "persist_skip_lock_info"
    fun persistSkipLockInfo(): Boolean {
        return getBoolean(persistSkipLockInfo, false)
    }

    fun persistSkipLockInfo(value: Boolean) {
        putBoolean(persistSkipLockInfo, value)
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

    private val gattActiveSlot = "gattActiveSlot"
    fun gattActiveSlot(): Int {
        return getInt(gattActiveSlot, 0)
    }

    fun gattActiveSlot(value: Int) {
        putInt(gattActiveSlot, value)
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

    fun lastUpdatedGit(): Long {
        return getLong(lastUpdatedGit, 0)
    }

    fun lastUpdatedGit(value: Long) {
        putLong(lastUpdatedGit, value)
    }

    private val lastBugReport = "lastBugReport"

    fun lastBugReport(): Long {
        return getLong(lastBugReport, 0)
    }

    fun lastBugReport(value: Long) {
        putLong(lastBugReport, value)
    }

    val isDocumentStorage : Boolean get() = run {
        Version.isLollipop && browserRootDocument()?.let {
            try {
                DocumentFile.fromTreeUri(TagMo.appContext, Uri.parse(it))
                true
            } catch (iae: IllegalArgumentException) {
                false
            }
        } ?: false
    }

    init {
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
    }
}