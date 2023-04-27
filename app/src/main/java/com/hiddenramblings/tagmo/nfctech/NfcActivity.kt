package com.hiddenramblings.tagmo.nfctech

import android.app.PendingIntent
import android.content.*
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
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
import com.hiddenramblings.tagmo.*
import com.hiddenramblings.tagmo.amiibo.AmiiboFile
import com.hiddenramblings.tagmo.amiibo.EliteTag
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.amiibo.tagdata.AmiiboData
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.nfctech.TagArray.technology
import com.hiddenramblings.tagmo.widget.Toasty
import com.shawnlin.numberpicker.NumberPicker
import java.io.IOException
import java.util.concurrent.Executors

class NfcActivity : AppCompatActivity() {

    private lateinit var prefs: Preferences
    private lateinit var txtMessage: TextView
    private lateinit var txtError: TextView
    private lateinit var imgNfcBar: AppCompatImageView
    private lateinit var imgNfcCircle: AppCompatImageView
    private lateinit var bankPicker: NumberPicker
    private lateinit var bankTextView: TextView
    private lateinit var nfcAnimation: Animation

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var keyManager: KeyManager
    private val foomiibo = Foomiibo()

    private var isEliteIntent = false
    private var isEliteDevice = false
    private var mifare: NTAG215? = null
    private var writeCount = 0
    private var tagTech: String? = null
    private var hasTestedElite = false

    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED == action) {
                if (nfcAdapter?.isEnabled != true) {
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

        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContentView(R.layout.activity_nfc)

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
            setPosition(bankPicker, prefs.eliteActiveBank())
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
                bankPicker.maxValue = prefs.eliteBankCount()
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
                if (commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK))
                    title = getString(R.string.scan_bank_no, bankPicker.value)
                else if (isEliteIntent) setTitle(R.string.scan_elite)
                else setTitle(R.string.scan_tag)
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
        setResult(RESULT_CANCELED)
        var update: ByteArray? = ByteArray(0)
        val tag = intent.parcelable<Tag>(NfcAdapter.EXTRA_TAG)
        tagTech = tag.technology()
        showMessage(R.string.tag_scanning, tagTech)
        mifare = if (NFCIntent.ACTION_BLIND_SCAN == mode || isEliteIntent)
            NTAG215.getBlind(tag) else NTAG215[tag]
        try {
            mifare?.let { ntag ->
                ntag.connect()
                if (!hasTestedElite) {
                    hasTestedElite = true
                    if (TagArray.isPowerTag(ntag)) {
                        showMessage(R.string.tag_scanning, getString(R.string.power_tag))
                    } else if (prefs.eliteEnabled()) {
                        isEliteDevice = (TagArray.isElite(ntag)
                                || NFCIntent.ACTION_UNLOCK_UNIT == mode
                                || NFCIntent.ACTION_BLIND_SCAN == mode)
                        if (isEliteDevice)
                            showMessage(R.string.tag_scanning, getString(R.string.elite_n2))
                    }
                }
                var bankNumber = 0
                var banksCount = -1
                var activeBank = -1
                if (isEliteDevice && NFCIntent.ACTION_UNLOCK_UNIT != mode) {
                    if (TagReader.needsFirmware(ntag)) {
                        if (TagWriter.updateFirmware(ntag)) showMessage(R.string.firmware_update)
                        closeTagSilently(ntag)
                        finish()
                    }
                    val bankParams = TagReader.getBankParams(ntag)
                    banksCount = bankParams?.get(1)?.toInt()?.and(0xFF) ?: banksCount
                    activeBank = bankParams?.get(0)?.toInt()?.and(0xFF) ?: activeBank
                    if (NFCIntent.ACTION_WRITE_ALL_TAGS != mode
                        && NFCIntent.ACTION_ERASE_ALL_TAGS != mode
                        && NFCIntent.ACTION_SET_BANK_COUNT != mode
                    ) {
                        bankNumber = getPosition(bankPicker)
                        if (bankNumber > banksCount) throw Exception(getString(R.string.fail_bank_oob))
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
                            update = TagReader.readFromTag(ntag)
                            TagWriter.writeToTagRaw(ntag, data!!, prefs.tagTypeValidation())
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
                            TagWriter.writeEliteAuto(ntag, data, keyManager, bankNumber)
                            setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                putExtra(
                                    NFCIntent.EXTRA_SIGNATURE,
                                    TagReader.getBankSignature(ntag)
                                )
                                putExtra(NFCIntent.EXTRA_BANK_COUNT, banksCount)
                                putExtra(NFCIntent.EXTRA_ACTIVE_BANK, activeBank)
                                putExtra(NFCIntent.EXTRA_CURRENT_BANK, bankNumber)
                                putExtras(Bundle().apply {
                                    putStringArrayList(
                                        NFCIntent.EXTRA_AMIIBO_LIST,
                                        TagReader.readTagTitles(ntag, banksCount)
                                    )
                                    putByteArray(NFCIntent.EXTRA_TAG_DATA, data)
                                })
                            })
                        } else {
                            update = TagReader.readFromTag(ntag)
                            TagWriter.writeToTagAuto(
                                ntag, data!!, keyManager, prefs.tagTypeValidation()
                            )
                            setResult(RESULT_OK)
                        }
                        NFCIntent.ACTION_WRITE_TAG_DATA -> {
                            val ignoreUid = commandIntent.getBooleanExtra(
                                NFCIntent.EXTRA_IGNORE_TAG_ID, false
                            )
                            TagWriter.restoreTag(
                                ntag, data!!, ignoreUid, keyManager, prefs.tagTypeValidation()
                            )
                            setResult(RESULT_OK)
                        }
                        NFCIntent.ACTION_WRITE_ALL_TAGS -> {
                            ntag.setBankCount(writeCount)
                            if (activeBank <= writeCount) ntag.activateBank(activeBank)
                            if (commandIntent.hasExtra(NFCIntent.EXTRA_AMIIBO_BYTES)) {
                                val amiiboList = commandIntent.parcelableArrayList<AmiiboData>(
                                    NFCIntent.EXTRA_AMIIBO_BYTES
                                )
                                amiiboList?.indices?.forEach { x ->
                                    showMessage(R.string.bank_writing, x + 1, amiiboList.size)
                                    keyManager.encrypt(amiiboList[x].array).run {
                                        TagWriter.writeEliteAuto(ntag, this, keyManager, x)
                                    }
                                }
                            } else if (commandIntent.hasExtra(NFCIntent.EXTRA_AMIIBO_FILES)) {
                                val amiiboList = commandIntent.parcelableArrayList<AmiiboFile>(
                                    NFCIntent.EXTRA_AMIIBO_FILES
                                )
                                amiiboList?.indices?.forEach { x ->
                                    showMessage(R.string.bank_writing, x + 1, amiiboList.size)
                                    val tagData = amiiboList[x].data
                                    if (null != tagData)
                                        TagWriter.writeEliteAuto(ntag, tagData, keyManager, x)
                                    else
                                        Toasty(this).Long(getString(
                                            R.string.fail_bank_data, x + 1
                                        ))
                                }
                            } else if (commandIntent.hasExtra(NFCIntent.EXTRA_AMIIBO_LIST)) {
                                val amiiboList = commandIntent.parcelableArrayList<EliteTag>(
                                    NFCIntent.EXTRA_AMIIBO_LIST
                                )
                                amiiboList?.indices?.forEach { x ->
                                    showMessage(R.string.bank_writing, x + 1, amiiboList.size)
                                    var tagData = TagArray.getValidatedData(keyManager, amiiboList[x].data)
                                    if (null == tagData) tagData = foomiibo.generateData(amiiboList[x].id)
                                    TagWriter.writeEliteAuto(ntag, tagData, keyManager, x)
                                }
                            }
                            setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                putExtra(NFCIntent.EXTRA_BANK_COUNT, writeCount)
                                putExtras(Bundle().apply {
                                    putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST,
                                        TagReader.readTagTitles(ntag, banksCount)
                                    )
                                })
                            })
                        }
                        NFCIntent.ACTION_BACKUP_AMIIBO -> {
                            setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                if (commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
                                    putExtras(Bundle().apply {
                                        putByteArray(
                                            NFCIntent.EXTRA_TAG_DATA,
                                            TagReader.scanTagToBytes(ntag, bankNumber)
                                        )
                                    })
                                    putExtra(NFCIntent.EXTRA_CURRENT_BANK, bankNumber)
                                } else {
                                    putExtras(Bundle().apply {
                                        putByteArray(
                                            NFCIntent.EXTRA_TAG_DATA,
                                            TagReader.scanTagToBytes(ntag, activeBank)
                                        )
                                    })
                                }
                            })
                        }
                        NFCIntent.ACTION_ERASE_BANK -> {
                            TagWriter.wipeBankData(ntag, bankNumber)
                            setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                putExtras(Bundle().apply {
                                    putStringArrayList(
                                        NFCIntent.EXTRA_AMIIBO_LIST,
                                        TagReader.readTagTitles(ntag, banksCount)
                                    )
                                })
                            })
                        }
                        NFCIntent.ACTION_ERASE_ALL_TAGS -> {
                            ntag.setBankCount(writeCount)
                            ntag.activateBank(0)
                            var x = 1
                            while (x < writeCount) {
                                showMessage(R.string.bank_erasing, x + 1, writeCount)
                                TagWriter.wipeBankData(ntag, x)
                                x++
                            }
                            setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                putExtra(NFCIntent.EXTRA_BANK_COUNT, writeCount)
                                putExtra(NFCIntent.EXTRA_ACTIVE_BANK, 0)
                                putExtra(NFCIntent.EXTRA_CURRENT_BANK, 0)
                                putExtras(Bundle().apply {
                                    putStringArrayList(
                                        NFCIntent.EXTRA_AMIIBO_LIST,
                                        TagReader.readTagTitles(ntag, banksCount)
                                    )
                                })
                            })
                        }
                        NFCIntent.ACTION_BLIND_SCAN,
                        NFCIntent.ACTION_SCAN_TAG -> {
                            setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                if (isEliteDevice) {
                                    if (commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
                                        data = TagArray.getValidatedData(keyManager,
                                            TagReader.scanBankToBytes(ntag, bankNumber)
                                        )
                                        putExtras(Bundle().apply {
                                            putByteArray(NFCIntent.EXTRA_TAG_DATA, data)
                                        })
                                        putExtra(NFCIntent.EXTRA_CURRENT_BANK, bankNumber)
                                    } else {
                                        val titles = TagReader.readTagTitles(ntag, banksCount)
                                        putExtra(NFCIntent.EXTRA_SIGNATURE,
                                            TagReader.getBankSignature(ntag)
                                        )
                                        putExtra(NFCIntent.EXTRA_BANK_COUNT, banksCount)
                                        putExtra(NFCIntent.EXTRA_ACTIVE_BANK, activeBank)
                                        putExtras(Bundle().apply {
                                            putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST, titles)
                                        })
                                    }
                                } else {
                                    putExtras(Bundle().apply {
                                        putByteArray(
                                            NFCIntent.EXTRA_TAG_DATA, TagReader.readFromTag(ntag)
                                        )
                                    })
                                }
                            })
                        }
                        NFCIntent.ACTION_ACTIVATE_BANK -> {
                            ntag.activateBank(bankNumber)
                            setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                putExtra(NFCIntent.EXTRA_ACTIVE_BANK,
                                    TagReader.getBankParams(ntag)?.get(0)?.toInt()?.and(0xFF)
                                )
                            })
                        }
                        NFCIntent.ACTION_SET_BANK_COUNT -> {
                            ntag.setBankCount(writeCount)
                            ntag.activateBank(activeBank)
                            val list = TagReader.readTagTitles(ntag, writeCount)
                            setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                putExtra(NFCIntent.EXTRA_BANK_COUNT, writeCount)
                                putExtras(Bundle().apply {
                                    putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST, list)
                                })
                            })
                        }
                        NFCIntent.ACTION_LOCK_AMIIBO -> {
                            try {
                                TagArray.getValidatedData(
                                    keyManager, TagReader.scanBankToBytes(ntag, activeBank)
                                )
                            } catch (ex: Exception) {
                                throw Exception(getString(R.string.fail_lock))
                            }
                            ntag.amiiboLock()
                            setResult(RESULT_OK)
                        }
                        NFCIntent.ACTION_UNLOCK_UNIT ->
                            if (null == ntag.amiiboPrepareUnlock()) {
                                val unlockBar = IconifiedSnackbar(
                                    this, findViewById(R.id.coordinator)
                                ).buildTickerBar(R.string.progress_unlock)
                                unlockBar.setAction(R.string.proceed) {
                                    ntag.amiiboUnlock()
                                    unlockBar.dismiss()
                                }.show()
                                while (unlockBar.isShown) setResult(RESULT_OK)
                            } else {
                                throw Exception(getString(R.string.fail_unlock))
                            }
                        else -> throw Exception(getString(R.string.error_state, mode))
                    }
                } catch (ex: Exception) {
                    throw ex
                } finally {
                    closeTagSilently(ntag)
                }
            } ?: throw Exception(getString(R.string.error_tag_protocol, tagTech))
            finish()
        } catch (e: Exception) {
            Debug.warn(e)
            var error: String? = Debug.getExceptionCause(e)
            if (null != error) {
                when {
                    getString(R.string.error_tag_rewrite) == error -> {
                        setResult(RESULT_OK, Intent(NFCIntent.ACTION_UPDATE_TAG).apply {
                            putExtras(Bundle().apply {
                                putByteArray(NFCIntent.EXTRA_TAG_DATA, update)
                            })
                        })
                        runOnUiThread {
                            getErrorDialog(this@NfcActivity,
                                R.string.error_tag_rewrite, R.string.tag_update_only
                            ).setPositiveButton(R.string.proceed) { dialog: DialogInterface, _: Int ->
                                closeTagSilently(mifare)
                                dialog.dismiss()
                                finish()
                            }.show()
                        }
                    }
                    prefs.eliteEnabled() -> {
                        when {
                            e is TagLostException -> {
                                showMessage(R.string.speed_scan)
                                closeTagSilently(mifare)
                            }
                            isEliteLockedCause(error) -> {
                                runOnUiThread {
                                    getErrorDialog(this@NfcActivity,
                                        R.string.possible_lock, R.string.prepare_unlock
                                    ).setPositiveButton(R.string.unlock) { dialog: DialogInterface, _: Int ->
                                        closeTagSilently(mifare)
                                        dialog.dismiss()
                                        getIntent().action = NFCIntent.ACTION_UNLOCK_UNIT
                                        recreate()
                                    }.setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                                        closeTagSilently(mifare)
                                        dialog.dismiss()
                                        finish()
                                    }.show()
                                }
                            }
                            Debug.hasException(e, NTAG215::class.java.name, "connect") -> {
                                runOnUiThread {
                                    getErrorDialog(this@NfcActivity,
                                        R.string.possible_blank, R.string.prepare_blank
                                    ).setPositiveButton(R.string.scan) { dialog: DialogInterface, _: Int ->
                                        dialog.dismiss()
                                        getIntent().action = NFCIntent.ACTION_BLIND_SCAN
                                        recreate()
                                    }.setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                                        dialog.dismiss()
                                    }.show()
                                }
                            }
                        }
                    }
                    else -> {
                        if (Debug.hasException(e, NTAG215::class.java.name, "connect"))
                            error = getString(R.string.error_tag_faulty) + "\n" + error
                        showError(error)
                    }
                }
            } else {
                showError(getString(R.string.error_unknown))
                try {
                    Debug.processLogcat(this@NfcActivity)
                } catch (ignored: IOException) { }
            }
        }
    }

    private fun isEliteLockedCause(error: String?) : Boolean {
        return getString(R.string.nfc_null_array) == error ||
                getString(R.string.nfc_read_result) == error ||
                getString(R.string.invalid_read_result) == error
    }

    private fun getErrorDialog(
        activity: AppCompatActivity, title: Int, message: Int
    ) : AlertDialog.Builder {
        return AlertDialog.Builder(activity).setTitle(title).setMessage(message)
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
        } else if (nfcAdapter?.isEnabled != true) {
            showError(getString(R.string.nfc_disabled))
            AlertDialog.Builder(this)
                .setMessage(R.string.nfc_available)
                .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                    if (Version.isQuinceTart)
                        onNFCActivity.launch(Intent(Settings.Panel.ACTION_NFC))
                    else onNFCActivity.launch(Intent(Settings.ACTION_NFC_SETTINGS))
                }
                .setNegativeButton(R.string.no) { _: DialogInterface?, _: Int -> finish() }
                .show()
        } else {
            // monitor nfc status
            if (Version.isJellyBeanMR2) {
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
        if (Version.isJellyBeanMR2) {
            try {
                unregisterReceiver(mReceiver)
            } catch (ignored: IllegalArgumentException) { }
        }
    }

    private fun listenForTags() {
        val nfcPendingIntent = PendingIntent.getActivity(
            applicationContext,
            0, Intent(applicationContext, this.javaClass),
            if (Version.isSnowCone)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nfcTechList = arrayOf<Array<String>>()
        val filter = IntentFilter()
        filter.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED)
        filter.addAction(NfcAdapter.ACTION_TECH_DISCOVERED)
        filter.addAction(NfcAdapter.ACTION_TAG_DISCOVERED)
        try {
            nfcAdapter?.enableForegroundDispatch(
                this, nfcPendingIntent, arrayOf(filter), nfcTechList
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
        if (null == nfcAdapter) nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        when (intent.action) {
            NFCIntent.ACTION_WRITE_TAG_FULL,
            NFCIntent.ACTION_WRITE_TAG_DATA,
            NFCIntent.ACTION_WRITE_ALL_TAGS -> {
                if (keyManager.isKeyMissing) showError("Keys not loaded")
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
            val tech = tagTech ?: getString(R.string.nfc_tag)
            showMessage(R.string.tag_detected, tech)
            Executors.newSingleThreadExecutor().execute { onTagDiscovered(intent) }
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
        if (item.itemId == android.R.id.home)
            cancelAction()
        else return super.onOptionsItemSelected(item)
        return true
    }
}