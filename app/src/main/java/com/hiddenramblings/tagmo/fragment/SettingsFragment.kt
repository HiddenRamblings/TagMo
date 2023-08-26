package com.hiddenramblings.tagmo.fragment

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
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
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.snackbar.Snackbar
import com.hiddenramblings.tagmo.BrowserActivity
import com.hiddenramblings.tagmo.GlideApp
import com.hiddenramblings.tagmo.GlideTagModule
import com.hiddenramblings.tagmo.NFCIntent.FilterComponent
import com.hiddenramblings.tagmo.NFCIntent.getIntent
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.parse
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.parseAmiiboAPI
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.saveDatabase
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.eightbit.net.JSONExecutor
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.hiddenramblings.tagmo.nfctech.TagArray.toByteArray
import com.hiddenramblings.tagmo.security.SecurityHandler
import com.hiddenramblings.tagmo.widget.Toasty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.text.ParseException

class SettingsFragment : PreferenceFragmentCompat() {
    private val prefs: Preferences by lazy { Preferences(TagMo.appContext) }
    private val keyManager: KeyManager by lazy { (requireActivity() as BrowserActivity).keyManager }

    private var importKeys: Preference? = null
    var imageNetworkSetting: ListPreference? = null

    private val onLoadKeys = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != AppCompatActivity.RESULT_OK || null == result.data) {
            return@registerForActivityResult
        } else if (null != result.data?.clipData) {
            result.data?.clipData?.let {
                for (i in 0 until it.itemCount) { validateKeys(it.getItemAt(i).uri) }
            }
        } else {
            result.data?.let { validateKeys(it.data) }
        }
    }

    private val onLoadKeyDocuments = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { documents: List<Uri> ->
        if (documents.isNotEmpty()) { documents.forEach { validateKeys(it) } }
    }

    private val onImportAmiiboDB = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != AppCompatActivity.RESULT_OK) return@registerForActivityResult
        result.data?.let { intent -> updateAmiiboDatabase(intent.data) }
    }

    private val onImportAmiiboDBDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { document: Uri? -> document?.let { updateAmiiboDatabase(it) } }

    private var browserActivity: BrowserActivity? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_screen, rootKey)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (activity is BrowserActivity) browserActivity = activity as BrowserActivity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageNetworkSetting = findPreference(getString(R.string.image_network_settings))
        imageNetworkSetting?.apply {
            onImageNetworkChange(imageNetworkSetting, prefs.imageNetwork())
            onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any ->
                    onImageNetworkChange(this, newValue.toString())
                    preference?.let { super@SettingsFragment.onPreferenceTreeClick(it) } ?: false
                }
        }
        importKeys = findPreference<Preference>(getString(R.string.settings_import_keys))?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                keyEntryMethod()
                super@SettingsFragment.onPreferenceTreeClick(it)
            }
        }
        updateKeySummary()
        findPreference<Preference>(getString(R.string.settings_menu_return))?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                browserActivity?.restoreMenuLayout()
                super@SettingsFragment.onPreferenceTreeClick(it)
            }
        }
        findPreference<CheckBoxPreference>(getString(R.string.settings_tag_type_validation))?.apply {
            isChecked = prefs.tagTypeValidation()
            onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    prefs.tagTypeValidation(isChecked)
                    super@SettingsFragment.onPreferenceTreeClick(it)
                }
        }
        findPreference<CheckBoxPreference>(getString(R.string.settings_automatic_scan))?.apply {
            isChecked = requireContext().packageManager.getComponentEnabledSetting(
                FilterComponent
            ) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val isChecked = isChecked
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
                    super@SettingsFragment.onPreferenceTreeClick(it)
                }
        }
        findPreference<SwitchPreferenceCompat>(getString(R.string.settings_enable_power_tag_support))?.apply {
            isChecked = prefs.powerTagEnabled()
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val isEnabled = isChecked
                prefs.powerTagEnabled(isEnabled)
                if (isEnabled) browserActivity?.loadPTagKeyManager()
                super@SettingsFragment.onPreferenceTreeClick(it)
            }
        }
        findPreference<SwitchPreferenceCompat>(getString(R.string.settings_enable_elite_support))?.apply {
            val isElite = prefs.eliteEnabled()
            isChecked = isElite
            if (isElite && prefs.eliteSignature()?.isNotEmpty() == true) {
                summary = getString(R.string.elite_signature, prefs.eliteSignature())
            }
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val isEnabled = isChecked
                prefs.eliteEnabled(isChecked)
                summary = if (isEnabled && !prefs.eliteSignature().isNullOrEmpty())
                    getString(R.string.elite_signature, prefs.eliteSignature())
                else
                    getString(R.string.elite_details)
                browserActivity?.reloadTabCollection = true
                super@SettingsFragment.onPreferenceTreeClick(it)
            }
        }
        findPreference<ListPreference>(getString(R.string.setting_database_source))?.apply {
            setValueIndex(prefs.databaseSource())
            summary = entry
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                (it as ListPreference).setValueIndex(prefs.databaseSource())
                super@SettingsFragment.onPreferenceTreeClick(it)
            }
            onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference: Preference, newValue: Any ->
                    val databaseSource = preference as ListPreference
                    val index = databaseSource.findIndexOfValue(newValue.toString())
                    prefs.databaseSource(index)
                    databaseSource.summary = databaseSource.entries[index]
                    rebuildAmiiboDatabase()
                    super@SettingsFragment.onPreferenceTreeClick(preference)
                }
        }
        findPreference<Preference>(getString(R.string.settings_import_info_amiiboapi))?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                rebuildAmiiboDatabase()
                super@SettingsFragment.onPreferenceTreeClick(it)
            }
        }
        findPreference<Preference>(getString(R.string.settings_import_info))?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                showFileChooser(
                    getString(R.string.import_json_details),
                    RESULT_IMPORT_AMIIBO_DATABASE
                )
                super@SettingsFragment.onPreferenceTreeClick(it)
            }
        }
        findPreference<Preference>(getString(R.string.settings_reset_info))?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                resetAmiiboDatabase(true)
                super@SettingsFragment.onPreferenceTreeClick(it)
            }
        }
        findPreference<ListPreference>(getString(R.string.settings_tagmo_theme))?.apply {
            setValueIndex(prefs.applicationTheme())
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                (it as ListPreference).setValueIndex(prefs.applicationTheme())
                super@SettingsFragment.onPreferenceTreeClick(it)
            }
            onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference: Preference, newValue: Any ->
                    val index = (preference as ListPreference).findIndexOfValue(newValue.toString())
                    prefs.applicationTheme(index)
                    (requireActivity().application as TagMo).setThemePreference()
                    browserActivity?.onApplicationRecreate()
                    super@SettingsFragment.onPreferenceTreeClick(preference)
                }
        }
        findPreference<CheckBoxPreference>(getString(R.string.settings_disable_debug))?.apply {
            isChecked = prefs.disableDebug()
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                prefs.disableDebug(isChecked)
                super@SettingsFragment.onPreferenceTreeClick(it)
            }
        }
        findPreference<Preference>(getString(R.string.disclaimer_foomiibo))?.apply {
            try {
                resources.openRawResource(R.raw.tos_foomiibo).use { tos ->
                    BufferedReader(InputStreamReader(tos)).use { r ->
                        val total = StringBuilder()
                        var line: String?
                        while (null != r.readLine().also { line = it }) {
                            total.append(line).append("\n")
                        }
                        summary = total.toString()
                    }
                }
            } catch (e: Exception) { Debug.info(e) }
        }
        findPreference<Preference>(getString(R.string.disclaimer_tagmo))?.apply {
            try {
                resources.openRawResource(R.raw.tos_tagmo).use { tos ->
                    BufferedReader(InputStreamReader(tos)).use { r ->
                        val total = StringBuilder()
                        var line: String?
                        while (null != r.readLine().also { line = it }) {
                            total.append(line).append("\n")
                        }
                        summary = total.toString()
                    }
                }
            } catch (e: Exception) { Debug.info(e) }
        }
    }

    private fun onImportKeysClicked() { showFileChooser(getString(R.string.decryption_keys), RESULT_KEYS) }

    private fun onImageNetworkChange(imageNetworkSetting: ListPreference?, newValue: String?) {
        val index = imageNetworkSetting?.findIndexOfValue(newValue)
        if (index == -1) {
            onImageNetworkChange(imageNetworkSetting, GlideTagModule.IMAGE_NETWORK_ALWAYS)
        } else {
            prefs.imageNetwork(newValue)
            imageNetworkSetting?.value = newValue
            imageNetworkSetting?.summary = imageNetworkSetting?.entry
            browserActivity?.let { activity ->
                CoroutineScope(Dispatchers.Main).launch {
                    activity.settings?.notifyChanges()
                }
            }
        }
    }

    private fun validateKeys(data: Uri?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                data?.let {
                    requireContext().contentResolver.openInputStream(it)?.use { strm ->
                        keyManager.evaluateKey(strm)
                        withContext(Dispatchers.Main) {
                            browserActivity?.onKeysLoaded(true)
                            updateKeySummary()
                        }
                    }
                }
            } catch (e: Exception) { Debug.warn(e) }
        }
    }

    private fun keyEntryDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_save_item, null)
        val dialog = AlertDialog.Builder(requireContext())
        view.findViewById<TextView>(R.id.save_item_label).setText(R.string.key_hex_entry)
        val input = view.findViewById<EditText>(R.id.save_item_entry)
        val scannerDialog: Dialog = dialog.setView(view).create()
        scannerDialog.setCancelable(false)
        view.findViewById<View>(R.id.button_save).setOnClickListener {
            scannerDialog.dismiss()
            if (input.text.isNullOrEmpty()) return@setOnClickListener
            try {
                keyManager.evaluateKey(ByteArrayInputStream(
                    input.text.toString().toByteArray()
                ))
                browserActivity?.onKeysLoaded(true)
                updateKeySummary()
            } catch (e: Exception) { Toasty(requireActivity()).Short(e.message) }
        }
        view.findViewById<View>(R.id.button_cancel).setOnClickListener { scannerDialog.dismiss() }
        scannerDialog.show()
    }

    private fun keyEntryMethod() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.key_input_method)
            .setPositiveButton(R.string.key_input_bin) { _: DialogInterface?, _: Int ->
                onImportKeysClicked()
            }
            .setNegativeButton(R.string.key_input_hex) { _: DialogInterface?, _: Int ->
                keyEntryDialog()
            }
            .show()
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
        keySummary.append(getString(R.string.key_download))
        keySummary.append("\n")
        keySummary.append(unfixedBuilder)
        keySummary.append("\n")
        keySummary.append(fixedBuilder)
        requireActivity().runOnUiThread { importKeys?.summary = keySummary }
        if (!keyManager.isKeyMissing) onSyncRequested(false)
    }

    private fun rebuildAmiiboDatabase() {
        if (keyManager.isKeyMissing) return
        resetAmiiboDatabase(false)
        onSyncRequested(true)
    }

    private fun updateAmiiboDatabase(data: Uri?) {
        resetAmiiboDatabase(false)
        CoroutineScope(Dispatchers.IO).launch {
            val amiiboManager: AmiiboManager? = try {
                parse(requireContext(), data)
            } catch (e: JSONException) {
                Debug.warn(e)
                withContext(Dispatchers.Main) {
                    Toasty(requireContext()).Short(R.string.amiibo_failure_parse)
                }
                return@launch
            } catch (e: ParseException) {
                Debug.warn(e)
                withContext(Dispatchers.Main) {
                    Toasty(requireContext()).Short(R.string.amiibo_failure_parse)
                }
                return@launch
            } catch (e: IOException) {
                Debug.warn(e)
                withContext(Dispatchers.Main) {
                    Toasty(requireContext()).Short(R.string.amiibo_failure_parse)
                }
                return@launch
            }
            try {
                amiiboManager?.let {
                    saveDatabase(it, requireContext().applicationContext)
                } ?: withContext(Dispatchers.Main) {
                    Toasty(requireContext()).Short(R.string.amiibo_failure_update)
                }
            } catch (e: JSONException) {
                Debug.warn(e)
                withContext(Dispatchers.Main) {
                    Toasty(requireContext()).Short(R.string.amiibo_failure_update)
                }
                return@launch
            } catch (e: IOException) {
                Debug.warn(e)
                withContext(Dispatchers.Main) {
                    Toasty(requireContext()).Short(R.string.amiibo_failure_update)
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                browserActivity?.let { activity ->
                    buildSnackbar(activity, R.string.amiibo_info_updated, Snackbar.LENGTH_SHORT).show()
                    activity.settings?.notifyChanges()
                }
            }
        }
    }

    private fun resetAmiiboDatabase(notify: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            requireContext().deleteFile(AmiiboManager.AMIIBO_DATABASE_FILE)
            browserActivity?.let { activity ->
                if (notify) {
                    withContext(Dispatchers.Main) {
                        activity.settings?.run {
                            lastUpdatedAPI = null
                            notifyChanges()
                        }
                    }
                }
                try {
                    withContext(Dispatchers.IO) { GlideApp.get(activity).clearDiskCache() }
                    withContext(Dispatchers.Main) { GlideApp.get(activity).clearMemory() }
                } catch (ignored: IllegalStateException) { }
                if (notify) withContext(Dispatchers.Main) {
                    buildSnackbar(
                        activity, R.string.removing_amiibo_info, Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun downloadAmiiboAPIData(lastUpdated: String) {
        browserActivity?.let { activity ->
            val syncMessage = buildSnackbar(
                activity, R.string.sync_amiibo_process, Snackbar.LENGTH_INDEFINITE
            )
            CoroutineScope(Dispatchers.IO).launch {
                withContext(Dispatchers.Main) {
                    syncMessage.show()
                }
                JSONExecutor(
                    activity, if (prefs.databaseSource() == 0)
                        "${AmiiboManager.RENDER_RAW}/database/amiibo.json"
                    else "${AmiiboManager.AMIIBO_API}/amiibo/"
                ).setDatabaseListener(object : JSONExecutor.DatabaseListener {
                    override fun onResults(result: String?, isRawJSON: Boolean) {
                        result?.let {
                            val amiiboManager = if (isRawJSON) parse(it) else parseAmiiboAPI(it)
                            saveDatabase(amiiboManager, requireContext().applicationContext)
                            CoroutineScope(Dispatchers.Main).launch {
                                if (syncMessage.isShown) syncMessage.dismiss()
                                buildSnackbar(
                                    activity, R.string.sync_amiibo_complete, Snackbar.LENGTH_SHORT
                                ).show()
                                activity.settings?.run {
                                    lastUpdatedAPI = lastUpdated
                                    notifyChanges()
                                }
                            }
                        }
                    }

                    override fun onException(e: Exception) {
                        CoroutineScope(Dispatchers.Main).launch {
                            if (syncMessage.isShown) syncMessage.dismiss()
                            buildSnackbar(
                                activity, R.string.amiibo_failure_server, Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                })
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
                Toasty(requireContext()).Short(R.string.fail_ssl_update)
            }
        })
    }

    private fun showFileChooser(title: String, resultCode: Int) {
        if (Version.isKitKat) {
            try {
                when (resultCode) {
                    RESULT_KEYS -> {
                        onLoadKeyDocuments.launch(resources.getStringArray(R.array.mimetype_bin))
                    }
                    RESULT_IMPORT_AMIIBO_DATABASE -> {
                        onImportAmiiboDBDocument.launch(resources.getStringArray(R.array.mimetype_json))
                    }
                }
            } catch (ex: ActivityNotFoundException) {
                Debug.verbose(ex)
            }
        } else {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                putExtra("android.content.extra.SHOW_ADVANCED", true)
                putExtra("android.content.extra.FANCY", true)
                try {
                    when (resultCode) {
                        RESULT_KEYS -> {
                            if (Version.isJellyBeanMR2) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            onLoadKeys.launch(Intent.createChooser(getIntent(this), title))
                        }
                        RESULT_IMPORT_AMIIBO_DATABASE -> {
                            onImportAmiiboDB.launch(Intent.createChooser(getIntent(this), title))
                        }
                    }
                } catch (ex: ActivityNotFoundException) {
                    Debug.verbose(ex)
                }
            }
        }
    }

    private fun buildSnackbar(activity: AppCompatActivity, msgRes: Int, length: Int): Snackbar {
        return IconifiedSnackbar(activity).buildSnackbar(
            activity.findViewById(R.id.preferences), msgRes, length
        )
    }

    private fun parseCommitDate(result: String, isMenuClicked: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonObject = JSONObject(result)
                val render = jsonObject["commit"] as JSONObject
                val commit = render["commit"] as JSONObject
                // JSONObject author = (JSONObject) commit.get("committer");
                val author = commit["author"] as JSONObject
                val lastUpdated = author["date"] as String
                browserActivity?.let { activity ->
                    if (isMenuClicked) {
                        onDownloadRequested(lastUpdated)
                    } else if (null == activity.settings?.lastUpdatedAPI
                        || activity.settings?.lastUpdatedAPI != lastUpdated
                    ) {
                        withContext(Dispatchers.Main) {
                            try {
                                buildSnackbar(
                                    activity, R.string.update_amiibo_api, Snackbar.LENGTH_LONG
                                ).setAction(R.string.sync) { onDownloadRequested(lastUpdated) }.show()
                            } catch (ignored: IllegalStateException) { }
                        }
                    }
                }
            } catch (e: Exception) { Debug.warn(e) }
        }
    }

    private fun parseUpdateJSON(result: String, isMenuClicked: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            browserActivity?.let { activity ->
                try {
                    val jsonObject = JSONObject(result)
                    val lastUpdatedAPI = jsonObject["lastUpdated"] as String
                    val lastUpdated = lastUpdatedAPI.substringBeforeLast(".") + "Z"
                    if (isMenuClicked) {
                        onDownloadRequested(lastUpdated)
                    } else if (activity.settings?.lastUpdatedAPI != lastUpdated) {
                        withContext(Dispatchers.Main) {
                            buildSnackbar(
                                activity, R.string.update_amiibo_api, Snackbar.LENGTH_LONG
                            ).setAction(R.string.sync) { onDownloadRequested(lastUpdated) }.show()
                        }
                    }
                } catch (e: Exception) { Debug.warn(e) }
            }
        }
    }

    private fun onSyncRequested(isMenuClicked: Boolean) {
        browserActivity?.let { activity ->
            if (prefs.databaseSource() == 0) {
                JSONExecutor(
                    activity,
                    "https://api.github.com/repos/8bitDream/AmiiboAPI",
                    "branches/render?path=database/amiibo.json"
                ).setResultListener(object : JSONExecutor.ResultListener {
                    override fun onResults(result: String?) {
                        result?.let { parseCommitDate(it, isMenuClicked) }
                    }

                    override fun onException(e: Exception) {
                        CoroutineScope(Dispatchers.Main).launch {
                            buildSnackbar(
                                activity, R.string.amiibo_failure_server, Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                })
            } else {
                JSONExecutor(
                    activity, AmiiboManager.AMIIBO_API, "lastupdated/"
                ).setResultListener(object : JSONExecutor.ResultListener {
                    override fun onResults(result: String?) {
                        result?.let { parseUpdateJSON(it, isMenuClicked) }
                    }

                    override fun onException(e: Exception) {
                        CoroutineScope(Dispatchers.Main).launch {
                            buildSnackbar(
                                activity, R.string.amiibo_failure_server, Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                })
            }
        }
    }

    companion object {
        private const val RESULT_KEYS = 8000
        private const val RESULT_IMPORT_AMIIBO_DATABASE = 8001
    }
}