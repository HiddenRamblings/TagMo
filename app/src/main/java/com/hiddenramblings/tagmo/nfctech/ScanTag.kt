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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScanTag {
    private var hasTestedElite = false
    private var isEliteDevice = false
    private fun closeTagSilently(mifare: NTAG215?) {
        try {
            mifare?.close()
        } catch (ignored: Exception) { }
    }

    private suspend fun onTagDiscovered(activity: BrowserActivity, intent: Intent) = withContext(Dispatchers.IO) {
        val prefs = Preferences(activity.applicationContext)
        val tag = intent.parcelable<Tag>(NfcAdapter.EXTRA_TAG)
        val tagTech = tag.technology()
        val mifare = NTAG215[tag]
        try {
            mifare?.let { ntag ->
                ntag.connect()
                if (!hasTestedElite) {
                    hasTestedElite = true
                    if (!isPowerTag(ntag) && prefs.eliteEnabled()) isEliteDevice = isElite(ntag)
                }
                try {
                    if (isEliteDevice) {
                        if (TagReader.needsFirmware(ntag)) {
                            if (TagWriter.updateFirmware(ntag)) {
                                Toasty(activity).Short(R.string.firmware_update)
                            }
                            closeTagSilently(ntag)
                            return@withContext
                        }
                        val bankParams = TagReader.getBankParams(ntag)
                        val banksCount = bankParams?.get(1)?.toInt()?.and(0xFF) ?: -1
                        val activeBank = bankParams?.get(0)?.toInt()?.and(0xFF) ?: -1
                        val signature = TagReader.getBankSignature(ntag)
                        prefs.eliteSignature(signature)
                        prefs.eliteActiveBank(activeBank)
                        prefs.eliteBankCount(banksCount)
                        withContext(Dispatchers.Main) {
                            activity.showElitePage(Bundle().apply {
                                val titles = TagReader.readTagTitles(ntag, banksCount)
                                putString(NFCIntent.EXTRA_SIGNATURE, signature)
                                putInt(NFCIntent.EXTRA_BANK_COUNT, banksCount)
                                putInt(NFCIntent.EXTRA_ACTIVE_BANK, activeBank)
                                putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST, titles)
                            })
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            activity.updateAmiiboView(TagReader.readFromTag(ntag))
                        }
                    }
                    hasTestedElite = false
                    isEliteDevice = false
                } catch (ex: Exception) {
                    throw ex
                } finally {
                    closeTagSilently(ntag)
                }
            } ?: if (prefs.eliteEnabled())
                onEliteVerificationFailed(activity)
            else
                throw Exception(activity.getString(R.string.error_tag_protocol, tagTech))
        } catch (e: Exception) {
            Debug.warn(e)
            Debug.getExceptionCause(e)?.let { error ->
                if (prefs.eliteEnabled()) {
                    when {
                        e is TagLostException -> {
                            if (isEliteDevice) {
                                activity.onNFCActivity.launch(Intent(activity, NfcActivity::class.java).setAction(NFCIntent.ACTION_BLIND_SCAN))
                            } else {
                                withContext(Dispatchers.Main) {
                                    IconifiedSnackbar(activity, activity.viewPager)
                                        .buildSnackbar(R.string.speed_scan, Snackbar.LENGTH_SHORT)
                                        .show()
                                }
                            }
                            closeTagSilently(mifare)
                        }
                        isEliteLockedCause(activity, error) -> {
                            withContext(Dispatchers.Main) {
                                getErrorDialog(activity, R.string.possible_lock, R.string.prepare_unlock)
                                    .setPositiveButton(R.string.unlock) { dialog: DialogInterface, _: Int ->
                                        closeTagSilently(mifare)
                                        dialog.dismiss()
                                        activity.onNFCActivity.launch(Intent(activity, NfcActivity::class.java).setAction(NFCIntent.ACTION_UNLOCK_UNIT))
                                    }.setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                                        closeTagSilently(mifare)
                                        dialog.dismiss()
                                    }.show()
                            }
                        }
                        Debug.hasException(e, NTAG215::class.java.name, "connect") -> {
                            withContext(Dispatchers.Main) {
                                onEliteVerificationFailed(activity)
                            }
                        }
                        else -> {}
                    }
                } else {
                    Toasty(activity).Short(
                        when {
                            e is TagLostException -> {
                                closeTagSilently(mifare)
                                "${activity.getString(R.string.tag_disconnect)}\n$error"
                            }
                            Debug.hasException(e, NTAG215::class.java.name, "connect") -> {
                                "${activity.getString(R.string.error_tag_faulty)}\n$error"
                            }
                            else -> { error }
                        }
                    )
                }
            } ?: {
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

    private fun onEliteVerificationFailed(activity: BrowserActivity) {
        getErrorDialog(activity, R.string.possible_blank, R.string.prepare_blank)
            .setPositiveButton(R.string.scan) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                activity.onNFCActivity.launch(Intent(
                    activity, NfcActivity::class.java
                ).setAction(NFCIntent.ACTION_BLIND_SCAN))
            }.setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }.show()
    }

    private fun getErrorDialog(
        activity: BrowserActivity, title: Int, message: Int
    ) : AlertDialog.Builder {
        return AlertDialog.Builder(activity).setTitle(title).setMessage(message)
    }

    fun onNewIntent(activity: BrowserActivity, intent: Intent) {
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action
            || NfcAdapter.ACTION_TECH_DISCOVERED == intent.action
            || NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            if (activity.keyManager.isKeyMissing()) return
            CoroutineScope(Dispatchers.IO).launch { onTagDiscovered(activity, intent) }
        }
    }
}