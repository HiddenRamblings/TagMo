package com.hiddenramblings.tagmo.browser

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import com.hiddenramblings.tagmo.GlideApp
import com.hiddenramblings.tagmo.GlideTagModule
import com.hiddenramblings.tagmo.NFCIntent.FilterComponent
import com.hiddenramblings.tagmo.NFCIntent.getIntent
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.Companion.parse
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.Companion.parseAmiiboAPI
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.Companion.saveDatabase
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.eightbit.net.JSONExecutor
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.hiddenramblings.tagmo.security.SecurityHandler
import com.hiddenramblings.tagmo.widget.Toasty
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.URL
import java.text.ParseException
import java.util.*
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var prefs: Preferences
    private var importKeys: Preference? = null
    var imageNetworkSetting: ListPreference? = null
    private lateinit var keyManager: KeyManager
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_screen, rootKey)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Preferences(requireContext().applicationContext)
        keyManager = KeyManager(requireContext())
        if (!keyManager.isKeyMissing) {
            onUpdateRequested(false)
        }
        importKeys = findPreference(getString(R.string.settings_import_keys))
        updateKeySummary()
        imageNetworkSetting = findPreference(getString(R.string.image_network_settings))
        if (null != imageNetworkSetting) {
            onImageNetworkChange(imageNetworkSetting, prefs.imageNetwork())
            imageNetworkSetting!!.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any ->
                    onImageNetworkChange(imageNetworkSetting, newValue.toString())
                    super@SettingsFragment.onPreferenceTreeClick(preference!!)
                }
        }
        importKeys!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                onImportKeysClicked()
                super@SettingsFragment.onPreferenceTreeClick(preference!!)
            }
        val tagTypeValidation = findPreference<CheckBoxPreference>(
            getString(R.string.settings_tag_type_validation)
        )
        if (null != tagTypeValidation) {
            tagTypeValidation.isChecked = prefs.tagTypeValidation()
            tagTypeValidation.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    prefs.tagTypeValidation(tagTypeValidation.isChecked)
                    super@SettingsFragment.onPreferenceTreeClick(preference!!)
                }
        }
        val automaticScan = findPreference<CheckBoxPreference>(
            getString(R.string.settings_automatic_scan)
        )
        if (null != automaticScan) {
            automaticScan.isChecked =
                requireContext().packageManager.getComponentEnabledSetting(
                    FilterComponent
                ) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            automaticScan.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    val isChecked = automaticScan.isChecked
                    prefs.automaticScan(isChecked)
                    if (isChecked) {
                        requireContext().packageManager.setComponentEnabledSetting(
                            FilterComponent,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    } else {
                        requireContext().packageManager.setComponentEnabledSetting(
                            FilterComponent,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                    super@SettingsFragment.onPreferenceTreeClick(preference!!)
                }
        }
        val disableFoomiiboPanel = findPreference<CheckBoxPreference>(
            getString(R.string.settings_hide_foomiibo_panel)
        )
        if (null != disableFoomiiboPanel && null != activity) {
            disableFoomiiboPanel.isChecked = prefs.foomiiboDisabled()
            disableFoomiiboPanel.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    val isChecked = disableFoomiiboPanel.isChecked
                    prefs.foomiiboDisabled(isChecked)
                    (activity as BrowserActivity?)!!.setFoomiiboPanelVisibility()
                    super@SettingsFragment.onPreferenceTreeClick(preference!!)
                }
        }
        val enablePowerTagSupport = findPreference<CheckBoxPreference>(
            getString(R.string.settings_enable_power_tag_support)
        )
        if (null != enablePowerTagSupport) {
            enablePowerTagSupport.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    val isEnabled = enablePowerTagSupport.isChecked
                    prefs.powerTagEnabled(isEnabled)
                    if (isEnabled) {
                        (requireActivity() as BrowserActivity).loadPTagKeyManager()
                    }
                    super@SettingsFragment.onPreferenceTreeClick(preference!!)
                }
        }
        val enableEliteSupport = findPreference<CheckBoxPreference>(
            getString(R.string.settings_enable_elite_support)
        )
        if (null != enableEliteSupport) {
            val isElite = prefs.eliteEnabled()
            enableEliteSupport.isChecked = isElite
            if (isElite && null != prefs.eliteSignature() && prefs.eliteSignature()!!.isNotEmpty()) {
                enableEliteSupport.summary = getString(R.string.elite_signature, prefs.eliteSignature())
            }
            enableEliteSupport.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    val isEnabled = enableEliteSupport.isChecked
                    prefs.eliteEnabled(enableEliteSupport.isChecked)
                    if (isEnabled && !prefs.eliteSignature().isNullOrEmpty())
                        enableEliteSupport.summary = getString(
                            R.string.elite_signature, prefs.eliteSignature()
                        ) 
                    else 
                        enableEliteSupport.summary = getString(R.string.elite_details)
                    (requireActivity() as BrowserActivity).reloadTabCollection = true
                    super@SettingsFragment.onPreferenceTreeClick(preference!!)
                }
        }
        val enableFlaskSupport = findPreference<CheckBoxPreference>(
            getString(R.string.settings_enable_flask_support)
        )
        if (null != enableFlaskSupport) {
            enableFlaskSupport.isChecked = prefs.flaskEnabled()
            enableFlaskSupport.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    prefs.flaskEnabled(enableFlaskSupport.isChecked)
                    (requireActivity() as BrowserActivity).reloadTabCollection = true
                    super@SettingsFragment.onPreferenceTreeClick(preference!!)
                }
            enableFlaskSupport.isVisible = Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2)
        }
        val databaseSourceSetting = findPreference<ListPreference>(getString(R.string.setting_database_source))
        if (null != databaseSourceSetting) {
            databaseSourceSetting.setValueIndex(prefs.databaseSource())
            databaseSourceSetting.summary = databaseSourceSetting.entry
            databaseSourceSetting.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference ->
                    (preference as ListPreference).setValueIndex(
                        prefs.databaseSource()
                    )
                    super@SettingsFragment.onPreferenceTreeClick(preference)
                }
            databaseSourceSetting.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference: Preference, newValue: Any ->
                    val databaseSource = preference as ListPreference
                    val index = databaseSource.findIndexOfValue(newValue.toString())
                    prefs.databaseSource(index)
                    databaseSource.summary = databaseSource.entries[index]
                    rebuildAmiiboDatabase()
                    super@SettingsFragment.onPreferenceTreeClick(preference)
                }
        }
        val syncInfo = findPreference<Preference>(getString(R.string.settings_import_info_amiiboapi))
        if (null != syncInfo) {
            syncInfo.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    rebuildAmiiboDatabase()
                    super@SettingsFragment.onPreferenceTreeClick(preference!!)
                }
        }
        val importInfo = findPreference<Preference>(getString(R.string.settings_import_info))
        if (null != importInfo) {
            importInfo.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    showFileChooser(
                        getString(R.string.import_json_details),
                        RESULT_IMPORT_AMIIBO_DATABASE
                    )
                    super@SettingsFragment.onPreferenceTreeClick(preference!!)
                }
        }
        val resetInfo = findPreference<Preference>(getString(R.string.settings_reset_info))
        if (null != resetInfo) {
            resetInfo.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    resetAmiiboDatabase(true)
                    super@SettingsFragment.onPreferenceTreeClick(preference!!)
                }
        }
        val softwareLayer =
            findPreference<CheckBoxPreference>(getString(R.string.settings_software_layer))
        if (null != softwareLayer) {
            softwareLayer.isChecked = prefs.softwareLayer()
            softwareLayer.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    prefs.softwareLayer(softwareLayer.isChecked)
                    (requireActivity() as BrowserActivity).onApplicationRecreate()
                    super@SettingsFragment.onPreferenceTreeClick(preference!!)
                }
        }
        val themeSetting = findPreference<ListPreference>(getString(R.string.settings_tagmo_theme))
        if (null != themeSetting) {
            themeSetting.setValueIndex(prefs.applicationTheme())
            themeSetting.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference ->
                    (preference as ListPreference).setValueIndex(
                        prefs.applicationTheme()
                    )
                    super@SettingsFragment.onPreferenceTreeClick(preference)
                }
            themeSetting.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference: Preference, newValue: Any ->
                    val index = (preference as ListPreference).findIndexOfValue(newValue.toString())
                    prefs.applicationTheme(index)
                    (requireActivity().application as TagMo).setThemePreference()
                    (requireActivity() as BrowserActivity).onApplicationRecreate()
                    super@SettingsFragment.onPreferenceTreeClick(preference)
                }
        }
        val disableDebug = findPreference<CheckBoxPreference>(getString(R.string.settings_disable_debug))
        if (null != disableDebug) {
            disableDebug.isChecked = prefs.disableDebug()
            disableDebug.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference? ->
                    prefs.disableDebug(disableDebug.isChecked)
                    super@SettingsFragment.onPreferenceTreeClick(preference!!)
                }
        }
        val disclaimerFoomiibo = findPreference<Preference>(getString(R.string.disclaimer_foomiibo))
        if (null != disclaimerFoomiibo) {
            try {
                resources.openRawResource(R.raw.tos_foomiibo).use { `in` ->
                    BufferedReader(InputStreamReader(`in`)).use { r ->
                        val total = StringBuilder()
                        var line: String?
                        while (null != r.readLine().also { line = it }) {
                            total.append(line).append("\n")
                        }
                        disclaimerFoomiibo.summary = total.toString()
                    }
                }
            } catch (e: Exception) {
                Debug.info(e)
            }
        }
        val disclaimerTagMo = findPreference<Preference>(getString(R.string.disclaimer_tagmo))
        if (null != disclaimerTagMo) {
            try {
                resources.openRawResource(R.raw.tos_tagmo).use { `in` ->
                    BufferedReader(InputStreamReader(`in`)).use { r ->
                        val total = StringBuilder()
                        var line: String?
                        while (null != r.readLine().also { line = it }) {
                            total.append(line).append("\n")
                        }
                        disclaimerTagMo.summary = total.toString()
                    }
                }
            } catch (e: Exception) {
                Debug.info(e)
            }
        }
    }

    private fun onImportKeysClicked() {
        showFileChooser(getString(R.string.decryption_keys), RESULT_KEYS)
    }

    private fun onImageNetworkChange(imageNetworkSetting: ListPreference?, newValue: String?) {
        val index = imageNetworkSetting!!.findIndexOfValue(newValue)
        if (index == -1) {
            onImageNetworkChange(imageNetworkSetting, GlideTagModule.IMAGE_NETWORK_ALWAYS)
        } else {
            prefs.imageNetwork(newValue)
            imageNetworkSetting.value = newValue
            imageNetworkSetting.summary = imageNetworkSetting.entry
            val activity = requireActivity() as BrowserActivity
            if (null != activity.settings) {
                activity.runOnUiThread { activity.settings!!.notifyChanges() }
            }
        }
    }

    private fun validateKeys(data: Uri?) {
        Executors.newSingleThreadExecutor().execute {
            try {
                requireContext().contentResolver.openInputStream(data!!).use { strm ->
                    keyManager.evaluateKey(strm!!)
                    if (Thread.currentThread().isInterrupted) return@execute
                    requireActivity().runOnUiThread {
                        (requireActivity() as BrowserActivity).onKeysLoaded(true)
                        updateKeySummary()
                    }
                }
            } catch (e: Exception) {
                Debug.info(e)
            }
        }
    }

    private fun keyEntryDialog(hexString: String) {
        val view = layoutInflater.inflate(R.layout.dialog_save_item, null)
        val dialog = AlertDialog.Builder(requireContext())
        (view.findViewById<View>(R.id.save_item_label) as TextView).setText(R.string.key_hex_entry)
        val input = view.findViewById<EditText>(R.id.save_item_entry)
        input.setText(hexString)
        val scannerDialog: Dialog = dialog.setView(view).create()
        scannerDialog.setCancelable(false)
        view.findViewById<View>(R.id.button_save).setOnClickListener {
            try {
                keyManager.evaluateKey(ByteArrayInputStream(TagArray.hexToByteArray(
                    input.text.toString().filter { !it.isWhitespace() }
                )))
                (requireActivity() as BrowserActivity).onKeysLoaded(true)
                updateKeySummary()
            } catch (e: Exception) {
                e.message?.let { Toasty(requireActivity()).Short(it) }
            }
            scannerDialog.dismiss()
        }
        view.findViewById<View>(R.id.button_cancel).setOnClickListener { scannerDialog.dismiss() }
        scannerDialog.show()
    }

    private fun updateKeySummary() {
        val unfixedText: String
        val unfixedSpan: ForegroundColorSpan
        if (keyManager.hasUnFixedKey()) {
            unfixedText = getString(R.string.unfixed_key_found)
            unfixedSpan = ForegroundColorSpan(Color.rgb(0x00, 0xAf, 0x00))
        } else {
            unfixedText = getString(R.string.unfixed_key_missing)
            unfixedSpan = ForegroundColorSpan(Color.RED)
        }
        val unfixedBuilder = SpannableStringBuilder(unfixedText)
        if (unfixedBuilder.isNotEmpty()) {
            unfixedBuilder.setSpan(
                unfixedSpan, 0, unfixedText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        val fixedText: String
        val fixedSpan: ForegroundColorSpan
        if (keyManager.hasFixedKey()) {
            fixedText = getString(R.string.fixed_key_found)
            fixedSpan = ForegroundColorSpan(Color.rgb(0x00, 0xAf, 0x00))
        } else {
            fixedText = getString(R.string.fixed_key_missing)
            fixedSpan = ForegroundColorSpan(Color.RED)
        }
        val fixedBuilder = SpannableStringBuilder(fixedText)
        if (fixedBuilder.isNotEmpty()) {
            fixedBuilder.setSpan(
                fixedSpan, 0, fixedText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        val keySummary = SpannableStringBuilder()
        keySummary.append(unfixedBuilder)
        keySummary.append("\n")
        keySummary.append(fixedBuilder)
        requireActivity().runOnUiThread { importKeys!!.summary = keySummary }
    }

    private fun rebuildAmiiboDatabase() {
        resetAmiiboDatabase(false)
        onUpdateRequested(true)
    }

    private fun updateAmiiboDatabase(data: Uri?) {
        resetAmiiboDatabase(false)
        Executors.newSingleThreadExecutor().execute {
            val amiiboManager: AmiiboManager = try {
                parse(requireContext(), data)
            } catch (e: JSONException) {
                Debug.warn(e)
                Toasty(requireActivity()).Short(R.string.amiibo_failure_parse)
                return@execute
            } catch (e: ParseException) {
                Debug.warn(e)
                Toasty(requireActivity()).Short(R.string.amiibo_failure_parse)
                return@execute
            } catch (e: IOException) {
                Debug.warn(e)
                Toasty(requireActivity()).Short(R.string.amiibo_failure_parse)
                return@execute
            }
            if (Thread.currentThread().isInterrupted) return@execute
            try {
                saveDatabase(amiiboManager, requireContext().applicationContext)
            } catch (e: JSONException) {
                Debug.warn(e)
                Toasty(requireActivity()).Short(R.string.amiibo_failure_update)
                return@execute
            } catch (e: IOException) {
                Debug.warn(e)
                Toasty(requireActivity()).Short(R.string.amiibo_failure_update)
                return@execute
            }
            val activity = requireActivity() as BrowserActivity
            activity.runOnUiThread {
                buildSnackbar(
                    activity, R.string.amiibo_info_updated, Snackbar.LENGTH_SHORT
                ).show()
                activity.settings!!.notifyChanges()
            }
        }
    }

    private fun resetAmiiboDatabase(notify: Boolean) {
        Executors.newSingleThreadExecutor().execute {
            requireContext().deleteFile(AmiiboManager.AMIIBO_DATABASE_FILE)
            val activity = requireActivity() as BrowserActivity
            if (notify) {
                activity.runOnUiThread {
                    activity.settings!!.lastUpdatedAPI = null
                    activity.settings!!.notifyChanges()
                }
            }
            try {
                Executors.newSingleThreadExecutor()
                    .execute { GlideApp.get(activity).clearDiskCache() }
                requireActivity().runOnUiThread { GlideApp.get(activity).clearMemory() }
            } catch (ignored: IllegalStateException) {
            }
            if (notify) activity.runOnUiThread {
                buildSnackbar(
                    activity, R.string.removing_amiibo_info, Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    @Throws(IOException::class)
    private fun fixServerLocation(url: URL): HttpsURLConnection {
        val urlConnection = url.openConnection() as HttpsURLConnection
        urlConnection.requestMethod = "GET"
        urlConnection.useCaches = false
        urlConnection.defaultUseCaches = false
        return urlConnection
    }

    private fun downloadAmiiboAPIData(lastUpdated: String) {
        val activity = requireActivity() as BrowserActivity
        val syncMessage = buildSnackbar(
            activity, R.string.sync_amiibo_process, Snackbar.LENGTH_INDEFINITE
        )
        activity.runOnUiThread { syncMessage.show() }
        Executors.newSingleThreadExecutor().execute {
            try {
                val url: URL = if (prefs.databaseSource() == 0) {
                    URL(AmiiboManager.RENDER_RAW + "database/amiibo.json")
                } else {
                    URL(AmiiboManager.AMIIBO_API + "amiibo/")
                }
                var conn = url.openConnection() as HttpsURLConnection
                conn.requestMethod = "GET"
                conn.useCaches = false
                conn.defaultUseCaches = false
                var statusCode = conn.responseCode
                if (statusCode == HttpsURLConnection.HTTP_MOVED_PERM) {
                    val address = conn.getHeaderField("Location")
                    conn.disconnect()
                    conn = fixServerLocation(URL(address))
                    statusCode = conn.responseCode
                } else if (statusCode != HttpsURLConnection.HTTP_OK && isRenderAPI(conn)) {
                    conn.disconnect()
                    conn = fixServerLocation(URL(AmiiboManager.AMIIBO_API + "amiibo/"))
                    statusCode = conn.responseCode
                }
                if (statusCode == HttpsURLConnection.HTTP_OK) {
                    val inputStream: InputStream = BufferedInputStream(conn.inputStream)
                    var reader: BufferedReader? = null
                    val response = StringBuilder()
                    try {
                        reader = BufferedReader(InputStreamReader(inputStream))
                        var line: String?
                        while (null != reader.readLine().also { line = it }) {
                            response.append(line)
                        }
                    } finally {
                        if (null != reader) {
                            try {
                                reader.close()
                            } catch (e: IOException) {
                                Debug.info(e)
                            }
                        }
                        conn.disconnect()
                    }
                    val amiiboManager = if (isRenderAPI(conn))
                        parse(response.toString())
                    else
                        parseAmiiboAPI(response.toString())
                    if (Thread.currentThread().isInterrupted) return@execute
                    saveDatabase(amiiboManager, requireContext().applicationContext)
                    activity.runOnUiThread {
                        if (syncMessage.isShown) syncMessage.dismiss()
                        buildSnackbar(
                            activity, R.string.sync_amiibo_complete, Snackbar.LENGTH_SHORT
                        ).show()
                        activity.settings!!.lastUpdatedAPI = lastUpdated
                        activity.settings!!.notifyChanges()
                    }
                } else {
                    conn.disconnect()
                    throw Exception(statusCode.toString())
                }
            } catch (e: Exception) {
                Debug.warn(e)
                activity.runOnUiThread {
                    if (syncMessage.isShown) syncMessage.dismiss()
                    buildSnackbar(
                        activity, R.string.sync_amiibo_failed, Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun onDownloadRequested(lastUpdated: String) {
        SecurityHandler(requireActivity(), object : SecurityHandler.ProviderInstallListener {
            override fun onProviderInstalled() {
                downloadAmiiboAPIData(lastUpdated)
            }

            override fun onProviderInstallException() {
                downloadAmiiboAPIData(lastUpdated)
            }

            override fun onProviderInstallFailed() {
                onImageNetworkChange(imageNetworkSetting, GlideTagModule.IMAGE_NETWORK_NEVER)
                Toasty(requireActivity()).Short(R.string.fail_ssl_update)
            }
        })
    }

    private val onLoadKeys = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            Executors.newSingleThreadExecutor().execute {
                try {
                    val scanner = Scanner(
                        URL(
                            "https://pastebin.com/raw/aV23ha3X"
                        ).openStream()
                    )
                    for (i in 0..3) {
                        if (scanner.hasNextLine()) scanner.nextLine()
                    }
                    val hexString = scanner.nextLine()
                    requireActivity().runOnUiThread { keyEntryDialog(hexString) }
                    scanner.close()
                } catch (e: IOException) {
                    Debug.warn(e)
                }
            }
        } else if (null != result.data!!.clipData) {
            for (i in 0 until result.data!!.clipData!!.itemCount) {
                validateKeys(result.data!!.clipData!!.getItemAt(i).uri)
            }
        } else {
            validateKeys(result.data!!.data)
        }
    }
    private val onImportAmiiboDatabase = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) 
            return@registerForActivityResult
        updateAmiiboDatabase(result.data!!.data)
    }

    private fun showFileChooser(title: String, resultCode: Int) {
        val intent =
            Intent(if (Debug.isNewer(Build.VERSION_CODES.KITKAT)) 
                Intent.ACTION_OPEN_DOCUMENT 
            else 
                Intent.ACTION_GET_CONTENT)
                .putExtra("android.content.extra.SHOW_ADVANCED", true)
                .putExtra("android.content.extra.FANCY", true)
        when (resultCode) {
            RESULT_KEYS -> {
                if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2)) intent.putExtra(
                    Intent.EXTRA_ALLOW_MULTIPLE,
                    true
                )
                try {
                    if (Debug.isNewer(Build.VERSION_CODES.KITKAT)) {
                        intent.putExtra(
                            Intent.EXTRA_MIME_TYPES,
                            resources.getStringArray(R.array.mimetype_bin)
                        )
                    }
                    onLoadKeys.launch(
                        Intent.createChooser(
                            getIntent(intent), title
                        )
                    )
                } catch (ex: ActivityNotFoundException) {
                    Debug.info(ex)
                }
            }
            RESULT_IMPORT_AMIIBO_DATABASE -> try {
                if (Debug.isNewer(Build.VERSION_CODES.KITKAT)) {
                    intent.putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        resources.getStringArray(R.array.mimetype_json)
                    )
                }
                onImportAmiiboDatabase.launch(
                    Intent.createChooser(
                        getIntent(intent), title
                    )
                )
            } catch (ex: ActivityNotFoundException) {
                Debug.info(ex)
            }
        }
    }

    private fun buildSnackbar(activity: Activity, msgRes: Int, length: Int): Snackbar {
        return IconifiedSnackbar(activity).buildSnackbar(
            requireActivity().findViewById(R.id.preferences), msgRes, length
        )
    }

    private fun parseCommitDate(result: String, isMenuClicked: Boolean) {
        try {
            val jsonObject = JSONObject(result)
            val render = jsonObject["commit"] as JSONObject
            val commit = render["commit"] as JSONObject
            // JSONObject author = (JSONObject) commit.get("committer");
            val author = commit["author"] as JSONObject
            val lastUpdated = author["date"] as String
            val activity = requireActivity() as BrowserActivity
            if (isMenuClicked) {
                onDownloadRequested(lastUpdated)
            } else if (null == activity.settings!!.lastUpdatedAPI
                || activity.settings!!.lastUpdatedAPI != lastUpdated
            ) {
                activity.runOnUiThread {
                    try {
                        buildSnackbar(
                            activity, R.string.update_amiibo_api, Snackbar.LENGTH_LONG
                        ).setAction(R.string.sync) { onDownloadRequested(lastUpdated) }.show()
                    } catch (ignored: IllegalStateException) { }
                }
            }
        } catch (e: Exception) {
            Debug.warn(e)
        }
    }

    private fun parseUpdateJSON(result: String, isMenuClicked: Boolean) {
        try {
            val jsonObject = JSONObject(result)
            val lastUpdatedAPI = jsonObject["lastUpdated"] as String
            val lastUpdated = lastUpdatedAPI.substring(
                0, lastUpdatedAPI.lastIndexOf(".")
            ) + "Z"
            val activity = requireActivity() as BrowserActivity
            if (isMenuClicked) {
                onDownloadRequested(lastUpdated)
            } else if (null == activity.settings!!.lastUpdatedAPI
                || activity.settings!!.lastUpdatedAPI != lastUpdated
            ) {
                activity.runOnUiThread {
                    buildSnackbar(
                        activity, R.string.update_amiibo_api, Snackbar.LENGTH_LONG
                    ).setAction(R.string.sync) { onDownloadRequested(lastUpdated) }.show()
                }
            }
        } catch (e: Exception) {
            Debug.warn(e)
        }
    }

    private fun onUpdateRequested(isMenuClicked: Boolean) {
        if (prefs.databaseSource() == 0) {
            JSONExecutor(
                requireActivity(),
                "https://api.github.com/repos/8bitDream/AmiiboAPI/",
                "branches/render?path=databaset%2Famiibo.json"
            ).setResultListener(object : JSONExecutor.ResultListener {
                override fun onResults(result: String?) {
                    result?.let { parseCommitDate(it, isMenuClicked) }
                }
            })
        } else {
            JSONExecutor(
                requireActivity(), AmiiboManager.AMIIBO_API, "lastupdated/"
            ).setResultListener(object : JSONExecutor.ResultListener {
                override fun onResults(result: String?) {
                    result?.let { parseUpdateJSON(it, isMenuClicked) }
                }
            })
        }
    }

    private fun isRenderAPI(conn: HttpsURLConnection): Boolean {
        val render = AmiiboManager.RENDER_RAW + "database/amiibo.json"
        return render == conn.url.toString()
    }

    companion object {
        private const val RESULT_KEYS = 8000
        private const val RESULT_IMPORT_AMIIBO_DATABASE = 8001
    }
}