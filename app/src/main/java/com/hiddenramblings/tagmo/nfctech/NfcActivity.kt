package com.hiddenramblings.tagmo.nfctech

import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.hiddenramblings.tagmo.NFCIntent
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.amiibo.AmiiboFile
import com.hiddenramblings.tagmo.amiibo.EliteTag
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.nfctech.*
import com.hiddenramblings.tagmo.widget.Toasty
import com.shawnlin.numberpicker.NumberPicker
import java.io.IOException
import java.util.concurrent.Executors


class NfcActivity : AppCompatActivity() {
    private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
        Debug.isNewer(Build.VERSION_CODES.TIRAMISU) ->
            getParcelableExtra(key, T::class.java)
        else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
    }
    private inline fun <reified T : Parcelable>
            Intent.parcelableArrayList(key: String): ArrayList<T>? = when {
        Debug.isNewer(Build.VERSION_CODES.TIRAMISU) ->
            getParcelableArrayListExtra(key, T::class.java)
        else -> @Suppress("DEPRECATION") getParcelableArrayListExtra(key)
    }

    private var prefs: Preferences? = null
    private lateinit var txtMessage: TextView
    private lateinit var txtError: TextView
    private lateinit var imgNfcBar: AppCompatImageView
    private lateinit var imgNfcCircle: AppCompatImageView
    private lateinit var bankPicker: NumberPicker
    private lateinit var bankTextView: TextView
    private lateinit var nfcAnimation: Animation

    private var nfcAdapter: NfcAdapter? = null
    private var keyManager: KeyManager? = null
    private val foomiibo = Foomiibo()

    private var isEliteIntent = false
    private var isEliteDevice = false
    private var mifare: NTAG215? = null
    private var writeCount = 0
    private var tagTech: String? = null
    private var hasTestedElite = false

    private val loadingExecutor = Executors.newSingleThreadExecutor()

    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED == action) {
                if (!nfcAdapter!!.isEnabled) {
                    showError(getString(R.string.nfc_disabled))
                } else {
                    clearError()
                    listenForTags()
                }
            }
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Preferences(applicationContext)
        setContentView(R.layout.activity_nfc)
        val actionBar = supportActionBar
        if (null != actionBar) {
            actionBar.setHomeButtonEnabled(true)
            actionBar.setDisplayHomeAsUpEnabled(true)
        }
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        keyManager = KeyManager(this)
        txtMessage = findViewById(R.id.txtMessage)
        txtError = findViewById(R.id.txtError)
        imgNfcBar = findViewById(R.id.imgNfcBar)
        imgNfcCircle = findViewById(R.id.imgNfcCircle)
        bankPicker = findViewById(R.id.number_picker)
        bankTextView = findViewById(R.id.bank_number_details)
        configureInterface()
        bankPicker.setBackgroundResource(R.drawable.picker_border)
        nfcAnimation = AnimationUtils.loadAnimation(this, R.anim.nfc_scanning)
    }

    private fun getPosition(picker: NumberPicker): Int {
        return picker.value - picker.minValue
    }

    private fun setPosition(picker: NumberPicker?, position: Int) {
        picker!!.value = position + picker.minValue
    }

    private fun configureInterface() {
        val commandIntent = this.intent
        val mode = commandIntent.action
        isEliteIntent = commandIntent.hasExtra(NFCIntent.EXTRA_SIGNATURE)
        if (commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
            setPosition(
                bankPicker, commandIntent.getIntExtra(
                    NFCIntent.EXTRA_CURRENT_BANK, getPosition(bankPicker)
                )
            )
        } else if (isEliteIntent) {
            setPosition(bankPicker, prefs!!.eliteActiveBank())
        } else {
            bankTextView.isGone = true
            bankPicker.isGone = true
        }
        if (commandIntent.hasExtra(NFCIntent.EXTRA_BANK_COUNT)) {
            writeCount = commandIntent.getIntExtra(NFCIntent.EXTRA_BANK_COUNT, 200)
        }
        when (mode) {
            NFCIntent.ACTION_WRITE_TAG_RAW,
            NFCIntent.ACTION_WRITE_TAG_FULL,
            NFCIntent.ACTION_WRITE_TAG_DATA -> {
                if (!isEliteIntent || !commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
                    bankPicker.isGone = true
                    bankPicker.isEnabled = false
                    bankTextView.isGone = true
                }
                bankPicker.maxValue = prefs!!.eliteBankCount()
            }
            NFCIntent.ACTION_WRITE_ALL_TAGS,
            NFCIntent.ACTION_ERASE_ALL_TAGS,
            NFCIntent.ACTION_BLIND_SCAN,
            NFCIntent.ACTION_SCAN_TAG,
            NFCIntent.ACTION_SET_BANK_COUNT,
            NFCIntent.ACTION_LOCK_AMIIBO,
            NFCIntent.ACTION_UNLOCK_UNIT,
            -> {
                bankPicker.isGone = true
                bankPicker.isEnabled = false
                bankTextView.isGone = true
            }
            NFCIntent.ACTION_BACKUP_AMIIBO,
            NFCIntent.ACTION_ERASE_BANK,
            NFCIntent.ACTION_ACTIVATE_BANK ->
                if (!isEliteIntent || !commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
                    bankPicker.isGone = true
                    bankTextView.isGone = true
                }
        }
        when (mode) {
            NFCIntent.ACTION_WRITE_TAG_RAW -> setTitle(R.string.write_raw)
            NFCIntent.ACTION_WRITE_TAG_FULL -> setTitle(R.string.write_auto)
            NFCIntent.ACTION_WRITE_TAG_DATA -> setTitle(R.string.update_tag)
            NFCIntent.ACTION_WRITE_ALL_TAGS -> setTitle(R.string.write_collection)
            NFCIntent.ACTION_BACKUP_AMIIBO -> setTitle(R.string.amiibo_backup)
            NFCIntent.ACTION_ERASE_ALL_TAGS -> setTitle(R.string.erase_collection)
            NFCIntent.ACTION_BLIND_SCAN,
            NFCIntent.ACTION_SCAN_TAG ->
                if (commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
                    title = getString(R.string.scan_bank_no, bankPicker.value)
                } else if (isEliteIntent) {
                    setTitle(R.string.scan_elite)
                } else {
                    setTitle(R.string.scan_tag)
                }
            NFCIntent.ACTION_ERASE_BANK -> setTitle(R.string.erase_bank)
            NFCIntent.ACTION_ACTIVATE_BANK -> setTitle(R.string.activate_bank)
            NFCIntent.ACTION_SET_BANK_COUNT -> setTitle(R.string.set_bank_count)
            NFCIntent.ACTION_LOCK_AMIIBO -> setTitle(R.string.lock_amiibo)
            NFCIntent.ACTION_UNLOCK_UNIT -> setTitle(R.string.unlock_elite)
            else -> {
                setTitle(R.string.error_caps)
                finish()
            }
        }
    }

    private fun showMessage(msgRes: Int) {
        runOnUiThread { txtMessage.setText(msgRes) }
    }

    private fun showMessage(msgRes: Int, params: String?) {
        runOnUiThread { txtMessage.text = getString(msgRes, params) }
    }

    private fun showMessage(msgRes: Int, params: Int, size: Int) {
        runOnUiThread { txtMessage.text = getString(msgRes, params, size) }
    }

    private fun showError(msg: String) {
        runOnUiThread {
            txtError.text = msg
            txtError.isVisible = true
            txtMessage.isGone = true
            imgNfcCircle.isGone = true
            imgNfcBar.isGone = true
            imgNfcBar.clearAnimation()
        }
    }

    private fun clearError() {
        txtError.isGone = true
        txtMessage.isVisible = true
        imgNfcCircle.isVisible = true
        imgNfcBar.isVisible = true
        imgNfcBar.animation = nfcAnimation
    }

    private fun closeTagSilently(mifare: NTAG215?) {
        try {
            mifare?.close()
        } catch (ignored: Exception) { }
    }

    private fun onTagDiscovered(intent: Intent) {
        val commandIntent = getIntent()
        val mode = commandIntent.action
        setResult(Activity.RESULT_CANCELED)
        val args = Bundle()
        var update: ByteArray? = ByteArray(0)
        try {
            val tag = intent.parcelable<Tag>(NfcAdapter.EXTRA_TAG)
            mifare = if (NFCIntent.ACTION_BLIND_SCAN == mode || isEliteIntent)
                NTAG215.getBlind(tag)
            else NTAG215[tag]
            tagTech = TagArray.getTagTechnology(tag)
            showMessage(R.string.tag_scanning, tagTech)
            mifare?.connect()
            if (!hasTestedElite) {
                hasTestedElite = true
                if (TagArray.isPowerTag(mifare)) {
                    showMessage(R.string.tag_scanning, getString(R.string.power_tag))
                } else if (prefs!!.eliteEnabled()) {
                    isEliteDevice = (TagArray.isElite(mifare)
                            || NFCIntent.ACTION_UNLOCK_UNIT == mode
                            || NFCIntent.ACTION_BLIND_SCAN == mode)
                    if (isEliteDevice) showMessage(
                        R.string.tag_scanning, getString(R.string.elite_n2)
                    )
                }
            }
            var bankNumber = 0
            var banksCount = -1
            var activeBank = -1
            if (isEliteDevice && NFCIntent.ACTION_UNLOCK_UNIT != mode) {
                if (TagReader.needsFirmware(mifare)) {
                    if (TagWriter.updateFirmware(mifare))
                        showMessage(R.string.firmware_update)
                    closeTagSilently(mifare)
                    finish()
                }
                val bankParams = TagReader.getBankParams(mifare)
                banksCount = bankParams?.get(1)?.toInt()?.and(0xFF) ?: banksCount
                activeBank = bankParams?.get(0)?.toInt()?.and(0xFF) ?: activeBank
                if (NFCIntent.ACTION_WRITE_ALL_TAGS != mode
                    && NFCIntent.ACTION_ERASE_ALL_TAGS != mode
                    && NFCIntent.ACTION_SET_BANK_COUNT != mode
                ) {
                    bankNumber = getPosition(bankPicker)
                    if (bankNumber > banksCount)
                        throw Exception(getString(R.string.fail_bank_oob))
                }
            }
            try {
                var data: ByteArray? = ByteArray(0)
                if (commandIntent.hasExtra(NFCIntent.EXTRA_TAG_DATA)) {
                    data = commandIntent.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA)
                    if (null == data || data.size <= 1) throw IOException(getString(R.string.error_no_data))
                }
                when (mode) {
                    NFCIntent.ACTION_WRITE_TAG_RAW -> {
                        update = TagReader.readFromTag(mifare)
                        TagWriter.writeToTagRaw(
                            mifare!!, data!!,
                            prefs!!.tagTypeValidation()
                        )
                        setResult(RESULT_OK)
                    }
                    NFCIntent.ACTION_WRITE_TAG_FULL -> if (isEliteDevice) {
                        if (bankPicker.isGone) {
                            showMessage(R.string.bank_select)
                            runOnUiThread {
                                bankPicker.isVisible = true
                                bankPicker.isEnabled = true
                                bankTextView.isVisible = true
                            }
                            setIntent(commandIntent)
                            hasTestedElite = false
                            return
                        }
                        TagWriter.writeEliteAuto(mifare!!, data, keyManager!!, bankNumber)
                        val write = Intent(NFCIntent.ACTION_NFC_SCANNED)
                        write.putExtra(
                            NFCIntent.EXTRA_SIGNATURE,
                            TagReader.getBankSignature(mifare)
                        )
                        write.putExtra(NFCIntent.EXTRA_BANK_COUNT, banksCount)
                        write.putExtra(NFCIntent.EXTRA_ACTIVE_BANK, activeBank)
                        write.putExtra(NFCIntent.EXTRA_CURRENT_BANK, bankNumber)
                        args.putStringArrayList(
                            NFCIntent.EXTRA_AMIIBO_LIST,
                            TagReader.readTagTitles(mifare, banksCount)
                        )
                        args.putByteArray(NFCIntent.EXTRA_TAG_DATA, data)
                        setResult(RESULT_OK, write.putExtras(args))
                    } else {
                        update = TagReader.readFromTag(mifare)
                        TagWriter.writeToTagAuto(
                            mifare!!, data!!, keyManager!!,
                            prefs!!.tagTypeValidation()
                        )
                        setResult(RESULT_OK)
                    }
                    NFCIntent.ACTION_WRITE_TAG_DATA -> {
                        val ignoreUid = commandIntent.getBooleanExtra(
                            NFCIntent.EXTRA_IGNORE_TAG_ID, false
                        )
                        TagWriter.restoreTag(
                            mifare!!, data!!, ignoreUid, keyManager!!,
                            prefs!!.tagTypeValidation()
                        )
                        setResult(RESULT_OK)
                    }
                    NFCIntent.ACTION_WRITE_ALL_TAGS -> {
                        mifare!!.setBankCount(writeCount)
                        if (activeBank <= writeCount) mifare!!.activateBank(activeBank)
                        if (commandIntent.hasExtra(NFCIntent.EXTRA_AMIIBO_FILES)) {
                            val amiiboList = commandIntent.parcelableArrayList<AmiiboFile>(
                                NFCIntent.EXTRA_AMIIBO_FILES
                            )
                            var x = 0
                            while (x < amiiboList!!.size) {
                                showMessage(R.string.bank_writing, x + 1, amiiboList.size)
                                val tagData = amiiboList[x].data
                                if (null != tagData) {
                                    TagWriter.writeEliteAuto(mifare!!, tagData, keyManager!!, x)
                                } else {
                                    Toasty(this).Long(
                                        getString(
                                            R.string.fail_bank_data, x + 1
                                        )
                                    )
                                }
                                x++
                            }
                        } else if (commandIntent.hasExtra(NFCIntent.EXTRA_AMIIBO_LIST)) {
                            val amiiboList = commandIntent.parcelableArrayList<EliteTag>(
                                NFCIntent.EXTRA_AMIIBO_LIST
                            )
                            var x = 0
                            while (x < amiiboList!!.size) {
                                showMessage(R.string.bank_writing, x + 1, amiiboList.size)
                                var tagData = TagArray.getValidatedData(keyManager, amiiboList[x].data)
                                if (null == tagData) tagData =
                                    foomiibo.generateData(amiiboList[x].id)
                                TagWriter.writeEliteAuto(mifare!!, tagData, keyManager!!, x)
                                x++
                            }
                        }
                        val write = Intent(NFCIntent.ACTION_NFC_SCANNED)
                        write.putExtra(NFCIntent.EXTRA_BANK_COUNT, writeCount)
                        args.putStringArrayList(
                            NFCIntent.EXTRA_AMIIBO_LIST,
                            TagReader.readTagTitles(mifare, banksCount)
                        )
                        setResult(RESULT_OK, write.putExtras(args))
                    }
                    NFCIntent.ACTION_BACKUP_AMIIBO -> {
                        val backup = Intent(NFCIntent.ACTION_NFC_SCANNED)
                        if (commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
                            args.putByteArray(
                                NFCIntent.EXTRA_TAG_DATA,
                                TagReader.scanTagToBytes(mifare, bankNumber)
                            )
                            backup.putExtra(NFCIntent.EXTRA_CURRENT_BANK, bankNumber)
                        } else {
                            args.putByteArray(
                                NFCIntent.EXTRA_TAG_DATA,
                                TagReader.scanTagToBytes(mifare, activeBank)
                            )
                        }
                        setResult(RESULT_OK, backup.putExtras(args))
                    }
                    NFCIntent.ACTION_ERASE_BANK -> {
                        TagWriter.wipeBankData(mifare!!, bankNumber)
                        val format = Intent(NFCIntent.ACTION_NFC_SCANNED)
                        args.putStringArrayList(
                            NFCIntent.EXTRA_AMIIBO_LIST,
                            TagReader.readTagTitles(mifare, banksCount)
                        )
                        setResult(RESULT_OK, format.putExtras(args))
                    }
                    NFCIntent.ACTION_ERASE_ALL_TAGS -> {
                        mifare?.setBankCount(writeCount)
                        mifare?.activateBank(0)
                        var x = 1
                        while (x < writeCount) {
                            showMessage(R.string.bank_erasing, x + 1, writeCount)
                            TagWriter.wipeBankData(mifare!!, x)
                            x++
                        }
                        val erase = Intent(NFCIntent.ACTION_NFC_SCANNED)
                        erase.putExtra(NFCIntent.EXTRA_BANK_COUNT, writeCount)
                        erase.putExtra(NFCIntent.EXTRA_ACTIVE_BANK, 0)
                        erase.putExtra(NFCIntent.EXTRA_CURRENT_BANK, 0)
                        args.putStringArrayList(
                            NFCIntent.EXTRA_AMIIBO_LIST,
                            TagReader.readTagTitles(mifare, banksCount)
                        )
                        setResult(RESULT_OK, erase.putExtras(args))
                    }
                    NFCIntent.ACTION_BLIND_SCAN,
                    NFCIntent.ACTION_SCAN_TAG -> {
                        val result = Intent(NFCIntent.ACTION_NFC_SCANNED)
                        if (isEliteDevice) {
                            if (commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
                                data = TagArray.getValidatedData(keyManager,
                                    TagReader.scanBankToBytes(mifare, bankNumber)
                                )
                                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, data)
                                result.putExtra(NFCIntent.EXTRA_CURRENT_BANK, bankNumber)
                            } else {
                                val titles = TagReader.readTagTitles(mifare, banksCount)
                                result.putExtra(
                                    NFCIntent.EXTRA_SIGNATURE,
                                    TagReader.getBankSignature(mifare)
                                )
                                result.putExtra(NFCIntent.EXTRA_BANK_COUNT, banksCount)
                                result.putExtra(NFCIntent.EXTRA_ACTIVE_BANK, activeBank)
                                args.putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST, titles)
                            }
                        } else {
                            args.putByteArray(
                                NFCIntent.EXTRA_TAG_DATA,
                                TagReader.readFromTag(mifare)
                            )
                        }
                        setResult(RESULT_OK, result.putExtras(args))
                    }
                    NFCIntent.ACTION_ACTIVATE_BANK -> {
                        mifare?.activateBank(bankNumber)
                        val active = Intent(NFCIntent.ACTION_NFC_SCANNED)
                        active.putExtra(
                            NFCIntent.EXTRA_ACTIVE_BANK,
                            TagReader.getBankParams(mifare)?.get(0)?.toInt()?.and(0xFF)
                        )
                        setResult(RESULT_OK, active)
                    }
                    NFCIntent.ACTION_SET_BANK_COUNT -> {
                        mifare!!.setBankCount(writeCount)
                        mifare!!.activateBank(activeBank)
                        val list = TagReader.readTagTitles(mifare, writeCount)
                        val configure = Intent(NFCIntent.ACTION_NFC_SCANNED)
                        configure.putExtra(NFCIntent.EXTRA_BANK_COUNT, writeCount)
                        args.putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST, list)
                        setResult(RESULT_OK, configure.putExtras(args))
                    }
                    NFCIntent.ACTION_LOCK_AMIIBO -> {
                        try {
                            TagArray.getValidatedData(keyManager,
                                TagReader.scanBankToBytes(mifare!!, activeBank)
                            )
                        } catch (ex: Exception) {
                            throw Exception(getString(R.string.fail_lock))
                        }
                        mifare!!.amiiboLock()
                        setResult(RESULT_OK)
                    }
                    NFCIntent.ACTION_UNLOCK_UNIT -> if (null == mifare?.amiiboPrepareUnlock()) {
                        val unlockBar = IconifiedSnackbar(
                            this, findViewById(R.id.coordinator)
                        ).buildTickerBar(R.string.progress_unlock)
                        unlockBar.setAction(R.string.proceed) {
                            mifare?.amiiboUnlock()
                            unlockBar.dismiss()
                        }.show()
                        while (unlockBar.isShown) {
                            setResult(RESULT_OK)
                        }
                    } else {
                        throw Exception(getString(R.string.fail_unlock))
                    }
                    else -> throw Exception(getString(R.string.error_state, mode))
                }
            } finally {
                closeTagSilently(mifare)
            }
            finish()
        } catch (e: Exception) {
            Debug.warn(e)
            val cause: String? = Debug.getExceptionCause(e)
            var error: String? = Debug.getExceptionError(e)
            if (null != error) {
                if (getString(R.string.error_tag_rewrite) == cause) {
                    args.putByteArray(NFCIntent.EXTRA_TAG_DATA, update)
                    setResult(
                        RESULT_OK, Intent(
                            NFCIntent.ACTION_UPDATE_TAG
                        ).putExtras(args)
                    )
                    runOnUiThread {
                        AlertDialog.Builder(this@NfcActivity)
                            .setTitle(R.string.error_tag_rewrite)
                            .setMessage(R.string.tag_update_only)
                            .setPositiveButton(R.string.proceed) { dialog: DialogInterface, _: Int ->
                                closeTagSilently(mifare)
                                dialog.dismiss()
                                finish()
                            }
                            .show()
                    }
                } else if (prefs!!.eliteEnabled()) {
                    if (e is TagLostException) {
                        showMessage(R.string.speed_scan)
                        closeTagSilently(mifare)
                    } else if (getString(R.string.nfc_null_array) == cause) {
                        runOnUiThread {
                            AlertDialog.Builder(this@NfcActivity)
                                .setTitle(R.string.possible_lock)
                                .setMessage(R.string.prepare_unlock)
                                .setPositiveButton(R.string.unlock) { dialog: DialogInterface, _: Int ->
                                    closeTagSilently(mifare)
                                    dialog.dismiss()
                                    getIntent().action = NFCIntent.ACTION_UNLOCK_UNIT
                                    recreate()
                                }
                                .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                                    closeTagSilently(mifare)
                                    dialog.dismiss()
                                    finish()
                                }.show()
                        }
                    } else if (e is NullPointerException && error.contains(NTAG215.CONNECT)) {
                        runOnUiThread {
                            AlertDialog.Builder(this@NfcActivity)
                                .setTitle(R.string.possible_blank)
                                .setMessage(R.string.prepare_blank)
                                .setPositiveButton(R.string.scan) { dialog: DialogInterface, _: Int ->
                                    dialog.dismiss()
                                    getIntent().action = NFCIntent.ACTION_BLIND_SCAN
                                    recreate()
                                }
                                .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                                    dialog.dismiss() }
                                .show()
                        }
                    }
                } else {
                    if (e is NullPointerException && error.contains(NTAG215.CONNECT))
                        error = getString(R.string.error_tag_faulty) + "\n" + error
                    showError(error)
                }
            } else {
                showError(getString(R.string.error_unknown))
                try {
                    Debug.processLogcat(this@NfcActivity)
                } catch (ignored: IOException) { }
            }
        }
    }

    private var onNFCActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        runOnUiThread {
            txtMessage = findViewById(R.id.txtMessage)
            txtMessage.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    txtMessage.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    startNfcMonitor()
                }
            })
        }
    }

    fun startNfcMonitor() {
        if (null == nfcAdapter) {
            showError(getString(R.string.nfc_unsupported))
        } else if (!nfcAdapter!!.isEnabled) {
            showError(getString(R.string.nfc_disabled))
            AlertDialog.Builder(this)
                .setMessage(R.string.nfc_available)
                .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                    if (Debug.isNewer(Build.VERSION_CODES.Q))
                        onNFCActivity.launch(Intent(Settings.Panel.ACTION_NFC)
                    ) else
                        onNFCActivity.launch(Intent(Settings.ACTION_NFC_SETTINGS))
                }
                .setNegativeButton(R.string.no) { _: DialogInterface?, _: Int -> finish() }
                .show()
        } else {
            // monitor nfc status
            if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2)) {
                val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
                this.registerReceiver(mReceiver, filter)
            }
            listenForTags()
        }
    }

    private fun stopNfcMonitor() {
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (ignored: RuntimeException) { }
        if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2)) {
            try {
                unregisterReceiver(mReceiver)
            } catch (ignored: IllegalArgumentException) { }
        }
    }

    private fun listenForTags() {
        val nfcPendingIntent = PendingIntent.getActivity(
            applicationContext,
            0, Intent(applicationContext, this.javaClass),
            if (Debug.isNewer(Build.VERSION_CODES.S))
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nfcTechList = arrayOf<Array<String>>()
        val filter = IntentFilter()
        filter.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED)
        filter.addAction(NfcAdapter.ACTION_TECH_DISCOVERED)
        filter.addAction(NfcAdapter.ACTION_TAG_DISCOVERED)
        try {
            nfcAdapter!!.enableForegroundDispatch(
                this,
                nfcPendingIntent,
                arrayOf(filter),
                nfcTechList
            )
        } catch (ex: RuntimeException) {
            Debug.warn(ex)
            cancelAction()
        }
    }

    private fun cancelAction() {
        closeTagSilently(mifare)
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onPause() {
        super.onPause()
        stopNfcMonitor()
    }

    override fun onResume() {
        super.onResume()
        clearError()
        if (null == keyManager)
            keyManager = KeyManager(this)
        if (null == nfcAdapter)
            nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        when (intent.action) {
            NFCIntent.ACTION_WRITE_TAG_FULL,
            NFCIntent.ACTION_WRITE_TAG_DATA,
            NFCIntent.ACTION_WRITE_ALL_TAGS -> {
                if (keyManager!!.isKeyMissing)
                    showError("Keys not loaded")
                startNfcMonitor()
            }
            NFCIntent.ACTION_WRITE_TAG_RAW,
            NFCIntent.ACTION_BACKUP_AMIIBO,
            NFCIntent.ACTION_ERASE_BANK,
            NFCIntent.ACTION_ERASE_ALL_TAGS,
            NFCIntent.ACTION_BLIND_SCAN,
            NFCIntent.ACTION_SCAN_TAG,
            NFCIntent.ACTION_ACTIVATE_BANK,
            NFCIntent.ACTION_SET_BANK_COUNT,
            NFCIntent.ACTION_LOCK_AMIIBO,
            NFCIntent.ACTION_UNLOCK_UNIT,
            -> startNfcMonitor()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action
            || NfcAdapter.ACTION_TECH_DISCOVERED == intent.action
            || NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            val tech = if (null != tagTech) tagTech!! else getString(R.string.nfc_tag)
            showMessage(R.string.tag_detected, tech)
            loadingExecutor.execute { onTagDiscovered(intent) }
        }
    }

    override fun onStart() {
        super.onStart()
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                cancelAction()
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            cancelAction()
        } else {
            return super.onOptionsItemSelected(item)
        }
        return true
    }
}