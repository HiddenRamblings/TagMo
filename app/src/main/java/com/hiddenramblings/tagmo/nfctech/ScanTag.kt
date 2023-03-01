package com.hiddenramblings.tagmo.nfctech

import android.content.DialogInterface
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import com.hiddenramblings.tagmo.NFCIntent
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.browser.BrowserActivity
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.nfctech.TagArray.isElite
import com.hiddenramblings.tagmo.nfctech.TagArray.isPowerTag
import com.hiddenramblings.tagmo.nfctech.TagArray.technology
import com.hiddenramblings.tagmo.parcelable
import com.hiddenramblings.tagmo.widget.Toasty
import java.io.IOException

class ScanTag {
    private var hasTestedElite = false
    private var isEliteDevice = false
    private fun closeTagSilently(mifare: NTAG215?) {
        try {
            mifare?.close()
        } catch (ignored: Exception) { }
    }

    fun onTagDiscovered(activity: BrowserActivity, intent: Intent) {
        val prefs = Preferences(activity.applicationContext)
        var mifare: NTAG215? = null
        try {
            val tag = intent.parcelable<Tag>(NfcAdapter.EXTRA_TAG)
            val tagTech = tag.technology()
            mifare = NTAG215[tag]
            mifare?.connect()
                ?: throw Exception(activity.getString(R.string.error_tag_protocol, tagTech))
            if (!hasTestedElite) {
                hasTestedElite = true
                if (!isPowerTag(mifare)) {
                    isEliteDevice = isElite(mifare)
                }
            }
            var banksCount = -1
            var activeBank = -1
            if (isEliteDevice) {
                if (TagReader.needsFirmware(mifare)) {
                    if (TagWriter.updateFirmware(mifare))
                        Toasty(activity).Short(R.string.firmware_update)
                    closeTagSilently(mifare)
                    return
                }
                val bankParams = TagReader.getBankParams(mifare)
                banksCount = bankParams?.get(1)?.toInt()?.and(0xFF) ?: banksCount
                activeBank = bankParams?.get(0)?.toInt()?.and(0xFF) ?: activeBank
            }
            try {
                if (isEliteDevice) {
                    val signature = TagReader.getBankSignature(mifare)
                    prefs.eliteSignature(signature)
                    prefs.eliteActiveBank(activeBank)
                    prefs.eliteBankCount(banksCount)
                    activity.showElitePage(Bundle().apply {
                        val titles = TagReader.readTagTitles(mifare, banksCount)
                        putString(NFCIntent.EXTRA_SIGNATURE, signature)
                        putInt(NFCIntent.EXTRA_BANK_COUNT, banksCount)
                        putInt(NFCIntent.EXTRA_ACTIVE_BANK, activeBank)
                        putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST, titles)
                    })
                } else {
                    activity.updateAmiiboView(TagReader.readFromTag(mifare))
                }
                hasTestedElite = false
                isEliteDevice = false
            } catch (ex: Exception) {
                throw ex
            } finally {
                closeTagSilently(mifare)
            }
        } catch (e: Exception) {
            Debug.warn(e)
            var error: String? = Debug.getExceptionCause(e)
            if (null != error) {
                if (prefs.eliteEnabled()) {
                    when {
                        e is TagLostException -> {
                            if (isEliteDevice) {
                                activity.onNFCActivity.launch(Intent(
                                    activity, NfcActivity::class.java
                                ).setAction(NFCIntent.ACTION_BLIND_SCAN))
                            } else {
                                IconifiedSnackbar(activity, activity.viewPager).buildSnackbar(
                                    R.string.speed_scan, Snackbar.LENGTH_SHORT
                                ).show()
                            }
                            closeTagSilently(mifare)
                        }
                        isEliteLockedCause(activity, error) -> {
                            activity.runOnUiThread {
                                getErrorDialog(activity,
                                    R.string.possible_lock, R.string.prepare_unlock
                                ).setPositiveButton(R.string.unlock) { dialog: DialogInterface, _: Int ->
                                    closeTagSilently(mifare)
                                    dialog.dismiss()
                                    activity.onNFCActivity.launch(Intent(
                                        activity, NfcActivity::class.java
                                    ).setAction(NFCIntent.ACTION_UNLOCK_UNIT))
                                }.setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                                    closeTagSilently(mifare)
                                    dialog.dismiss()
                                }.show()
                            }
                        }
                        Debug.hasException(e, NTAG215::class.java.name, "connect") -> {
                            activity.runOnUiThread {
                                getErrorDialog(activity,
                                    R.string.possible_blank, R.string.prepare_blank
                                ).setPositiveButton(R.string.scan) { dialog: DialogInterface, _: Int ->
                                    dialog.dismiss()
                                    activity.onNFCActivity.launch(Intent(
                                        activity, NfcActivity::class.java
                                    ).setAction(NFCIntent.ACTION_BLIND_SCAN))
                                }.setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                                    dialog.dismiss()
                                }.show()
                            }
                        }
                    }
                } else {
                    if (Debug.hasException(e, NTAG215::class.java.name, "connect"))
                        error = activity.getString(R.string.error_tag_faulty) + "\n" + error
                    Toasty(activity).Short(error)
                }
            } else {
                Toasty(activity).Short(R.string.error_unknown)
                activity.onReportProblemClick()
            }
        }
    }

    private fun isEliteLockedCause(activity: BrowserActivity, error: String?) : Boolean {
        return activity.getString(R.string.nfc_null_array) == error ||
                activity.getString(R.string.nfc_read_result) == error ||
                activity.getString(R.string.invalid_read_result) == error
    }

    private fun getErrorDialog(
        activity: BrowserActivity, title: Int, message: Int
    ) : AlertDialog.Builder {
        return AlertDialog.Builder(activity).setTitle(title).setMessage(message)
    }
}