package com.hiddenramblings.tagmo.nfctech

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
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
import com.hiddenramblings.tagmo.NFCIntent
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.amiibo.AmiiboFile
import com.hiddenramblings.tagmo.amiibo.EliteTag
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.amiibo.tagdata.AmiiboData
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.nfctech.TagArray.isElite
import com.hiddenramblings.tagmo.nfctech.TagArray.isPowerTag
import com.hiddenramblings.tagmo.nfctech.TagArray.technology
import com.hiddenramblings.tagmo.parcelable
import com.hiddenramblings.tagmo.parcelableArrayList
import com.shawnlin.numberpicker.NumberPicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.math.abs

class NfcActivity : AppCompatActivity() {

    private val prefs: Preferences by lazy { Preferences(applicationContext) }
    private val keyManager: KeyManager by lazy { KeyManager(this) }

    private lateinit var txtMessage: TextView
    private lateinit var txtError: TextView
    private lateinit var imgNfcBar: AppCompatImageView
    private lateinit var imgNfcCircle: AppCompatImageView
    private lateinit var bankPicker: NumberPicker
    private lateinit var bankTextView: TextView
    private lateinit var nfcAnimation: Animation

    private var nfcAdapter: NfcAdapter? = null

    private var isEliteIntent = false
    private var isEliteDevice = false
    private var ntag215: NTAG215? = null
    private var skylanders: Skylanders? = null
    private var infinity: Infinity? = null
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

        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContentView(R.layout.activity_nfc)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        txtMessage = findViewById(R.id.txtMessage)
        txtError = findViewById(R.id.txtError)
        imgNfcBar = findViewById(R.id.imgNfcBar)
        imgNfcCircle = findViewById(R.id.imgNfcCircle)
        bankPicker = findViewById(R.id.number_picker_nfc)
        bankTextView = findViewById(R.id.bank_number_details)

        configureInterface()

        nfcAnimation = AnimationUtils.loadAnimation(this, R.anim.nfc_scanning)
    }

    private fun configureInterface() {
        val commandIntent = this.intent
        val mode = commandIntent.action
        isEliteIntent = commandIntent.hasExtra(NFCIntent.EXTRA_SIGNATURE)
        when {
            commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK) -> {
                val position = bankPicker.getPositionByValue(bankPicker.value)
                bankPicker.setPositionByValue(commandIntent.getIntExtra(NFCIntent.EXTRA_CURRENT_BANK, position))
            }
            isEliteIntent -> {
                bankPicker.setPositionByValue(commandIntent.getIntExtra(NFCIntent.EXTRA_CURRENT_BANK, prefs.eliteActiveBank()))
            }
            else -> {
                bankTextView.isGone = true
                bankPicker.isGone = true
            }
        }
        val hideBankPicker = !isEliteIntent || !commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)
        if (commandIntent.hasExtra(NFCIntent.EXTRA_BANK_COUNT))
            writeCount = commandIntent.getIntExtra(NFCIntent.EXTRA_BANK_COUNT, 200)
        when (mode) {
            NFCIntent.ACTION_WRITE_TAG_RAW,
            NFCIntent.ACTION_WRITE_TAG_FULL,
            NFCIntent.ACTION_WRITE_TAG_DATA -> {
                if (hideBankPicker) {
                    bankPicker.isGone = true
                    bankPicker.isEnabled = false
                    bankTextView.isGone = true
                }
                bankPicker.maxValue = abs(prefs.eliteBankCount())
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
                if (hideBankPicker) {
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
                when {
                    commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK) ->
                        title = getString(R.string.scan_bank_no, bankPicker.value)
                    isEliteIntent -> setTitle(R.string.scan_elite)
                    else -> setTitle(R.string.scan_tag)
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
        CoroutineScope(Dispatchers.Main).launch { txtMessage.setText(msgRes) }
    }

    private fun showMessage(msgRes: Int, params: String?) {
        CoroutineScope(Dispatchers.Main).launch { txtMessage.text = getString(msgRes, params) }
    }

    private fun showMessage(msgRes: Int, params: Int, size: Int) {
        CoroutineScope(Dispatchers.Main).launch { txtMessage.text = getString(msgRes, params, size) }
    }

    private fun showError(msg: String) {
        CoroutineScope(Dispatchers.Main).launch {
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

    private fun closeTagSilently(ntag215: NTAG215?) {
        try {
            ntag215?.close()
        } catch (ignored: Exception) { }
    }

    private fun writeCollection(ntag215: NTAG215, command: Intent) {
        when {
            command.hasExtra(NFCIntent.EXTRA_AMIIBO_DATA) -> {
                val amiiboList = command.parcelableArrayList<AmiiboData>(
                    NFCIntent.EXTRA_AMIIBO_DATA
                )
                amiiboList?.forEachIndexed { x, amiiboData ->
                    showMessage(R.string.bank_writing, x + 1, amiiboList.size)
                    TagWriter.writeEliteAuto(ntag215, amiiboData.array, x)
                }
            }
            command.hasExtra(NFCIntent.EXTRA_AMIIBO_BYTES) -> {
                val amiiboList = command.parcelableArrayList<AmiiboFile>(
                    NFCIntent.EXTRA_AMIIBO_BYTES
                )
                amiiboList?.forEachIndexed { x, amiiboFile ->
                    val tagData = amiiboFile.data ?: Foomiibo.getSignedData(amiiboFile.id)
                    showMessage(R.string.bank_writing, x + 1, amiiboList.size)
                    TagWriter.writeEliteAuto(ntag215, tagData, x)
                }
            }
            command.hasExtra(NFCIntent.EXTRA_AMIIBO_LIST) -> {
                val amiiboList = command.parcelableArrayList<EliteTag>(
                    NFCIntent.EXTRA_AMIIBO_LIST
                )
                amiiboList?.forEachIndexed { x, eliteTag ->
                    val tagData = eliteTag.data ?: Foomiibo.getSignedData(eliteTag.id)
                    showMessage(R.string.bank_writing, x + 1, amiiboList.size)
                    TagWriter.writeEliteAuto(ntag215, tagData, x)
                }
            }
        }
    }

    private suspend fun onMifareUltralight(tag: Tag?) = withContext(Dispatchers.IO) {
        val command = intent
        val mode = command.action
        var update: ByteArray? = ByteArray(0)
        try {
            ntag215 = if (NFCIntent.ACTION_BLIND_SCAN == mode || isEliteIntent)
                NTAG215.getBlind(tag) else NTAG215[tag]
            ntag215?.let { mifare ->
                if (!hasTestedElite) {
                    hasTestedElite = true
                    if (mifare.isPowerTag) {
                        showMessage(R.string.tag_scanning, getString(R.string.power_tag))
                    } else if (prefs.eliteEnabled()) {
                        isEliteDevice = (isEliteIntent || mifare.isElite
                                || NFCIntent.ACTION_UNLOCK_UNIT == mode
                                || NFCIntent.ACTION_BLIND_SCAN == mode)
                        if (isEliteDevice) showMessage(R.string.tag_scanning, getString(R.string.elite_n2))
                    }
                }
                var bankNumber = 0
                var bankCount = -1
                var activeBank = -1
                if (isEliteDevice && NFCIntent.ACTION_UNLOCK_UNIT != mode) {
                    if (TagReader.needsFirmware(mifare)) {
                        if (TagWriter.updateFirmware(mifare)) showMessage(R.string.firmware_update)
                        closeTagSilently(mifare)
                        finish()
                    }
                    val bankParams = TagReader.getBankParams(mifare)
                    bankCount = bankParams?.get(1)?.toInt()?.and(0xFF) ?: bankCount
                    activeBank = bankParams?.get(0)?.toInt()?.and(0xFF) ?: activeBank
                    if (NFCIntent.ACTION_WRITE_ALL_TAGS != mode
                            && NFCIntent.ACTION_ERASE_ALL_TAGS != mode
                            && NFCIntent.ACTION_SET_BANK_COUNT != mode
                    ) {
                        bankNumber = bankPicker.getPositionByValue(bankPicker.value)
                        if (bankNumber > bankCount)
                            throw Exception(getString(R.string.fail_bank_oob))
                    }
                }
                try {
                    var data: ByteArray? = ByteArray(0)
                    if (command.hasExtra(NFCIntent.EXTRA_TAG_DATA)) {
                        data = command.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA)
                        if (null == data || data.size <= 1)
                            throw IOException(getString(R.string.error_no_data))
                    }
                    when (mode) {
                        NFCIntent.ACTION_WRITE_TAG_RAW -> {
                            update = TagReader.readFromTag(mifare)
                            TagWriter.writeToTagRaw(mifare, data!!, prefs.tagTypeValidation())
                            setResult(RESULT_OK)
                        }
                        NFCIntent.ACTION_WRITE_TAG_FULL -> {
                            if (isEliteDevice) {
                                if (bankPicker.isGone) {
                                    showMessage(R.string.bank_select)
                                    withContext(Dispatchers.Main) {
                                        bankPicker.isVisible = true
                                        bankPicker.isEnabled = true
                                        bankTextView.isVisible = true
                                    }
                                    intent = command
                                    hasTestedElite = false
                                    return@withContext
                                }
                                TagWriter.writeEliteAuto(mifare, data, bankNumber)
                                setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                    putExtras(Bundle().apply {
                                        putString(
                                                NFCIntent.EXTRA_SIGNATURE,
                                                TagReader.getBankSignature(mifare)
                                        )
                                        putInt(NFCIntent.EXTRA_BANK_COUNT, bankCount)
                                        putInt(NFCIntent.EXTRA_ACTIVE_BANK, activeBank)
                                        putInt(NFCIntent.EXTRA_CURRENT_BANK, bankNumber)
                                        putStringArrayList(
                                                NFCIntent.EXTRA_AMIIBO_LIST,
                                                TagReader.readTagTitles(mifare, bankCount)
                                        )
                                        putByteArray(NFCIntent.EXTRA_TAG_DATA, data)
                                    })
                                })
                            } else {
                                update = TagReader.readFromTag(mifare)
                                TagWriter.writeToTagAuto(mifare, data!!, keyManager, prefs.tagTypeValidation())
                                setResult(RESULT_OK)
                            }
                        }
                        NFCIntent.ACTION_WRITE_TAG_DATA -> {
                            val ignoreUid = command.getBooleanExtra(
                                    NFCIntent.EXTRA_IGNORE_TAG_ID, false
                            )
                            TagWriter.restoreTag(
                                    mifare, data!!, ignoreUid, keyManager, prefs.tagTypeValidation()
                            )
                            setResult(RESULT_OK)
                        }
                        NFCIntent.ACTION_WRITE_ALL_TAGS -> {
                            mifare.setBankCount(writeCount)
                            if (activeBank <= writeCount) mifare.activateBank(activeBank)
                            writeCollection(mifare, intent)
                            setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                putExtras(Bundle().apply {
                                    putInt(NFCIntent.EXTRA_BANK_COUNT, writeCount)
                                    putStringArrayList(
                                            NFCIntent.EXTRA_AMIIBO_LIST,
                                            TagReader.readTagTitles(mifare, bankCount)
                                    )
                                })
                            })
                        }
                        NFCIntent.ACTION_BACKUP_AMIIBO -> {
                            setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                if (command.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
                                    putExtras(Bundle().apply {
                                        putByteArray(
                                                NFCIntent.EXTRA_TAG_DATA,
                                                TagReader.scanTagToBytes(mifare, bankNumber)
                                        )
                                        putInt(NFCIntent.EXTRA_CURRENT_BANK, bankNumber)
                                    })
                                } else {
                                    putExtras(Bundle().apply {
                                        putByteArray(
                                                NFCIntent.EXTRA_TAG_DATA,
                                                TagReader.scanTagToBytes(mifare, activeBank)
                                        )
                                    })
                                }
                            })
                        }
                        NFCIntent.ACTION_ERASE_BANK -> {
                            TagWriter.wipeBankData(mifare, bankNumber)
                            setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                putExtras(Bundle().apply {
                                    putStringArrayList(
                                            NFCIntent.EXTRA_AMIIBO_LIST,
                                            TagReader.readTagTitles(mifare, bankCount)
                                    )
                                })
                            })
                        }
                        NFCIntent.ACTION_ERASE_ALL_TAGS -> {
                            mifare.setBankCount(writeCount)
                            mifare.activateBank(0)
                            var x = 1
                            while (x < writeCount) {
                                showMessage(R.string.bank_erasing, x + 1, writeCount)
                                TagWriter.wipeBankData(mifare, x)
                                x++
                            }
                            setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                putExtras(Bundle().apply {
                                    putInt(NFCIntent.EXTRA_BANK_COUNT, writeCount)
                                    putInt(NFCIntent.EXTRA_ACTIVE_BANK, 0)
                                    putInt(NFCIntent.EXTRA_CURRENT_BANK, 0)
                                    putStringArrayList(
                                            NFCIntent.EXTRA_AMIIBO_LIST,
                                            TagReader.readTagTitles(mifare, bankCount)
                                    )
                                })
                            })
                        }
                        NFCIntent.ACTION_BLIND_SCAN,
                        NFCIntent.ACTION_SCAN_TAG -> {
                            setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                if (isEliteDevice) {
                                    if (intent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
                                        data = TagArray.getValidatedData(
                                                keyManager, TagReader.scanBankToBytes(mifare, bankNumber)
                                        )
                                        putExtras(Bundle().apply {
                                            putByteArray(NFCIntent.EXTRA_TAG_DATA, data)
                                            putInt(NFCIntent.EXTRA_CURRENT_BANK, bankNumber)
                                        })
                                    } else {
                                        val titles = TagReader.readTagTitles(mifare, bankCount)
                                        putExtras(Bundle().apply {
                                            putString(
                                                    NFCIntent.EXTRA_SIGNATURE,
                                                    TagReader.getBankSignature(mifare)
                                            )
                                            putInt(NFCIntent.EXTRA_BANK_COUNT, bankCount)
                                            putInt(NFCIntent.EXTRA_ACTIVE_BANK, activeBank)
                                            putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST, titles)
                                        })
                                    }
                                } else {
                                    putExtras(Bundle().apply {
                                        putByteArray(NFCIntent.EXTRA_TAG_DATA, TagReader.readFromTag(mifare))
                                    })
                                }
                            })
                        }
                        NFCIntent.ACTION_ACTIVATE_BANK -> {
                            mifare.activateBank(bankNumber)
                            setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                putExtra(
                                        NFCIntent.EXTRA_ACTIVE_BANK,
                                        TagReader.getBankParams(mifare)?.get(0)?.toInt()?.and(0xFF)
                                )
                            })
                        }
                        NFCIntent.ACTION_SET_BANK_COUNT -> {
                            mifare.setBankCount(writeCount)
                            mifare.activateBank(activeBank)
                            val list = TagReader.readTagTitles(mifare, writeCount)
                            setResult(RESULT_OK, Intent(NFCIntent.ACTION_NFC_SCANNED).apply {
                                putExtras(Bundle().apply {
                                    putInt(NFCIntent.EXTRA_BANK_COUNT, writeCount)
                                    putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST, list)
                                })
                            })
                        }
                        NFCIntent.ACTION_LOCK_AMIIBO -> {
                            try {
                                TagArray.getValidatedData(keyManager, TagReader.scanBankToBytes(mifare, activeBank))
                            } catch (ex: Exception) {
                                throw Exception(getString(R.string.fail_lock))
                            }
                            mifare.amiiboLock()
                            setResult(RESULT_OK)
                        }
                        NFCIntent.ACTION_UNLOCK_UNIT -> {
                            if (null == mifare.amiiboPrepareUnlock()) {
                                val unlockBar = IconifiedSnackbar(
                                        this@NfcActivity, findViewById(R.id.coordinator)
                                ).buildTickerBar(R.string.progress_unlock)
                                unlockBar.setAction(R.string.proceed) {
                                    mifare.amiiboUnlock()
                                }.show()
                                while (unlockBar.isShown) setResult(RESULT_OK)
                            } else {
                                throw Exception(getString(R.string.fail_unlock))
                            }
                        }
                        else -> {
                            throw Exception(getString(R.string.error_state, mode))
                        }
                    }
                } finally {
                    closeTagSilently(mifare)
                }
                finish()
            } ?: if (prefs.eliteEnabled()) {
                onEliteVerificationFailed(intent)
            } else {
                throw Exception(getString(R.string.error_tag_protocol, tagTech))
            }
        } catch (e: Exception) {
            Debug.warn(e)
            Debug.getExceptionCause(e)?.let { error ->
                when {
                    getString(R.string.error_tag_rewrite) == error -> {
                        withContext(Dispatchers.Main) {
                            getErrorDialog(
                                    this@NfcActivity, R.string.error_tag_rewrite, R.string.tag_update_only
                            ).setPositiveButton(R.string.update) { dialog: DialogInterface, _: Int ->
                                closeTagSilently(ntag215)
                                setResult(RESULT_OK, Intent(NFCIntent.ACTION_UPDATE_TAG).apply {
                                    putExtras(Bundle().apply {
                                        putByteArray(NFCIntent.EXTRA_TAG_DATA, update)
                                    })
                                })
                                dialog.dismiss()
                                finish()
                            }.setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                                closeTagSilently(ntag215)
                                dialog.dismiss()
                                finish()
                            }.show()
                        }
                    }

                    getString(R.string.uid_key_missing) == error -> {
                        withContext(Dispatchers.Main) {
                            getErrorDialog(
                                    this@NfcActivity, R.string.uid_key_missing, R.string.power_tag_reset
                            ).setPositiveButton(R.string.proceed) { dialog: DialogInterface, _: Int ->
                                closeTagSilently(ntag215)
                                dialog.dismiss()
                                finish()
                            }.show()
                        }
                    }

                    getString(R.string.fail_encrypt) == error -> {
                        withContext(Dispatchers.Main) {
                            getErrorDialog(
                                    this@NfcActivity, R.string.fail_encrypt, R.string.encryption_fault
                            ).setPositiveButton(R.string.proceed) { dialog: DialogInterface, _: Int ->
                                closeTagSilently(ntag215)
                                dialog.dismiss()
                                finish()
                            }.show()
                        }
                    }

                    prefs.eliteEnabled() -> {
                        when {
                            e is TagLostException -> {
                                showMessage(R.string.speed_scan)
                                closeTagSilently(ntag215)
                            }

                            isEliteLockedCause(error) -> {
                                withContext(Dispatchers.Main) {
                                    getErrorDialog(
                                            this@NfcActivity, R.string.possible_lock, R.string.prepare_unlock
                                    ).setPositiveButton(R.string.unlock) { dialog: DialogInterface, _: Int ->
                                        closeTagSilently(ntag215)
                                        dialog.dismiss()
                                        intent.action = NFCIntent.ACTION_UNLOCK_UNIT
                                        recreate()
                                    }.setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                                        closeTagSilently(ntag215)
                                        dialog.dismiss()
                                        finish()
                                    }.show()
                                }
                            }

                            Debug.hasException(e, NTAG215::class.java.name, "connect") -> {
                                withContext(Dispatchers.Main) {
                                    onEliteVerificationFailed(intent)
                                }
                            }

                            else -> {}
                        }
                    }

                    else -> {
                        when {
                            e is TagLostException -> {
                                closeTagSilently(ntag215)
                                showError("${getString(R.string.tag_disconnect)}\n\n$error")
                            }

                            Debug.hasException(e, NTAG215::class.java.name, "connect") -> {
                                showError("${getString(R.string.error_tag_faulty)}\n\n$error")
                            }

                            else -> {
                                showError(error)
                                val exception = StringWriter().apply {
                                    e.printStackTrace(PrintWriter(this))
                                }
                                try {
                                    Debug.clipException(this@NfcActivity, exception.toString())
                                } catch (ignored: Exception) {
                                }
                            }
                        }
                    }
                }
            } ?: {
                showError(getString(R.string.error_unknown))
                try {
                    Debug.processLogcat(this@NfcActivity)
                } catch (ignored: IOException) {
                }
            }
        }
    }

    private suspend fun onMifareClassic(tag: Tag?) = withContext(Dispatchers.IO) {
        val command = intent
        val mode = command.action
        try {
            skylanders = Skylanders[tag]
            infinity = Infinity[tag]
            skylanders?.let { mifare ->
                when(mode) {
                    NFCIntent.ACTION_SCAN_TAG -> {
                        mifare.authenticate()
                    }
                }
                mifare.close()
            } ?: infinity?.let { mifare ->
                when(mode) {
                    NFCIntent.ACTION_SCAN_TAG -> {
                        mifare.authenticate()
                    }
                }
                mifare.close()
            } ?: {
                showError(getString(R.string.error_nxp_required))
            }
        } catch (e: Exception) {
            Debug.warn(e)
            Debug.getExceptionCause(e)?.let { error ->

            } ?: {
                showError(getString(R.string.error_unknown))
                try {
                    Debug.processLogcat(this@NfcActivity)
                } catch (ignored: IOException) {
                }
            }
        }
    }

    private suspend fun onTagDiscovered(intent: Intent) = withContext(Dispatchers.IO) {
        setResult(RESULT_CANCELED)
        val tag = intent.parcelable<Tag>(NfcAdapter.EXTRA_TAG)
        tagTech = tag.technology()
        showMessage(R.string.tag_scanning, tagTech)
        when (tagTech) {
            getString(R.string.mifare_ultralight) -> onMifareUltralight(tag)
            getString(R.string.mifare_classic) -> onMifareClassic(tag)
            getString(R.string.nfciso, "A") -> onMifareUltralight(tag)
            else -> { }
        }
    }

    private fun isEliteLockedCause(error: String?) : Boolean {
        return getString(R.string.nfc_null_array) == error ||
                getString(R.string.nfc_read_result) == error ||
                getString(R.string.invalid_read_result) == error
    }

    private fun onEliteVerificationFailed(commandIntent: Intent) {
        CoroutineScope(Dispatchers.Main).launch {
            getErrorDialog(
                this@NfcActivity, R.string.possible_blank, R.string.prepare_blank
            ).setPositiveButton(R.string.scan) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                commandIntent.action = NFCIntent.ACTION_BLIND_SCAN
                intent = commandIntent
                recreate()
            }.setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                finish()
            }.show()
        }
    }

    private fun getErrorDialog(
        activity: AppCompatActivity, title: Int, message: Int
    ) : AlertDialog.Builder {
        return AlertDialog.Builder(activity).setTitle(title).setMessage(message)
    }

    private var onNFCActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        CoroutineScope(Dispatchers.Main).launch {
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
                    else
                        onNFCActivity.launch(Intent(Settings.ACTION_NFC_SETTINGS))
                }
                .setNegativeButton(R.string.no) { _: DialogInterface?, _: Int -> finish() }
                .show()
        } else {
            // monitor nfc status
            this.registerReceiver(mReceiver, IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED))
            listenForTags()
        }
    }

    private fun stopNfcMonitor() {
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (ignored: RuntimeException) { }
        try {
            unregisterReceiver(mReceiver)
        } catch (ignored: IllegalArgumentException) { }
    }

    private fun listenForTags() {
        val nfcPendingIntent = PendingIntent.getActivity(
            applicationContext, 0, Intent(applicationContext, this.javaClass),
            if (Version.isSnowCone)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nfcTechList = arrayOf<Array<String>>()
        val filter = IntentFilter()
        filter.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED)
        filter.addAction(NfcAdapter.ACTION_TECH_DISCOVERED)
        filter.addAction(NfcAdapter.ACTION_TAG_DISCOVERED)
        try {
            nfcAdapter?.enableForegroundDispatch(this, nfcPendingIntent, arrayOf(filter), nfcTechList)
        } catch (ex: RuntimeException) {
            Debug.warn(ex)
            cancelAction()
        }
    }

    private fun cancelAction() {
        closeTagSilently(ntag215)
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
            CoroutineScope(Dispatchers.IO).launch { onTagDiscovered(intent) }
        }
    }

    override fun onStart() {
        super.onStart()
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { cancelAction() }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home)
            cancelAction()
        else
            return super.onOptionsItemSelected(item)
        return true
    }
}