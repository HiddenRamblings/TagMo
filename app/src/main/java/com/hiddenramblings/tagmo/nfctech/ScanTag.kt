package com.hiddenramblings.tagmo.nfctech

import android.content.DialogInterface
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.NfcA
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import com.hiddenramblings.tagmo.NFCIntent
import com.hiddenramblings.tagmo.NfcActivity
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.browser.BrowserActivity
import com.hiddenramblings.tagmo.browser.Preferences
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.nfctech.TagArray.getTagTechnology
import com.hiddenramblings.tagmo.nfctech.TagArray.isElite
import com.hiddenramblings.tagmo.nfctech.TagArray.isPowerTag
import com.hiddenramblings.tagmo.widget.Toasty

class ScanTag {
    private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
        Debug.isNewer(Build.VERSION_CODES.TIRAMISU) ->
            getParcelableExtra(key, T::class.java)
        else ->
            @Suppress("DEPRECATION") getParcelableExtra(key) as? T
    }
    private var hasTestedElite = false
    private var isEliteDevice = false
    private fun closeTagSilently(mifare: NTAG215?) {
        if (null != mifare) {
            try {
                mifare.close()
            } catch (ignored: Exception) { }
        }
    }

    fun onTagDiscovered(activity: BrowserActivity, intent: Intent) {
        val prefs = Preferences(activity.applicationContext)
        var mifare: NTAG215? = null
        try {
            val tag = intent.parcelable<Tag>(NfcAdapter.EXTRA_TAG)
            mifare = NTAG215[tag!!]
            val tagTech = getTagTechnology(tag)
            if (mifare == null) {
                if (prefs.eliteEnabled()) {
                    mifare = NTAG215(NfcA.get(tag))
                    try {
                        mifare.connect()
                    } catch (ex: Exception) {
                        Debug.info(ex)
                    }
                    if (TagReader.needsFirmware(mifare)) {
                        if (TagWriter.updateFirmware(mifare)) Toasty(activity).Short(R.string.firmware_update)
                        mifare.close()
                        activity.finish()
                    }
                }
                throw Exception(activity.getString(R.string.error_tag_protocol, tagTech))
            }
            mifare.connect()
            if (!hasTestedElite) {
                hasTestedElite = true
                if (!isPowerTag(mifare)) {
                    isEliteDevice = isElite(mifare)
                }
            }
            val bankParams: ByteArray
            val bankCount: Int
            val activeBank: Int
            if (!isEliteDevice) {
                bankCount = -1
                activeBank = -1
            } else {
                bankParams = TagReader.getBankParams(mifare)!!
                bankCount = bankParams[1].toInt() and 0xFF
                activeBank = bankParams[0].toInt() and 0xFF
            }
            try {
                if (isEliteDevice) {
                    val signature = TagReader.getBankSignature(mifare)
                    prefs.eliteSignature(signature)
                    prefs.eliteActiveBank(activeBank)
                    prefs.eliteBankCount(bankCount)
                    val args = Bundle()
                    val titles = TagReader.readTagTitles(mifare, bankCount)
                    args.putString(NFCIntent.EXTRA_SIGNATURE, signature)
                    args.putInt(NFCIntent.EXTRA_BANK_COUNT, bankCount)
                    args.putInt(NFCIntent.EXTRA_ACTIVE_BANK, activeBank)
                    args.putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST, titles)
                    activity.showElitePage(args)
                } else {
                    activity.updateAmiiboView(TagReader.readFromTag(mifare))
                }
                hasTestedElite = false
                isEliteDevice = false
            } finally {
                mifare.close()
            }
        } catch (e: Exception) {
            Debug.warn(e)
            var error: String? = Debug.getExceptionDetails(e)
            if (null != error) {
                if (prefs.eliteEnabled()) {
                    val finalMifare = mifare
                    if (e is TagLostException) {
                        if (isEliteDevice) {
                            activity.onNFCActivity.launch(
                                Intent(
                                    activity, NfcActivity::class.java
                                ).setAction(NFCIntent.ACTION_BLIND_SCAN)
                            )
                        } else {
                            IconifiedSnackbar(activity, activity.layout).buildSnackbar(
                                R.string.speed_scan, Snackbar.LENGTH_SHORT
                            ).show()
                        }
                        closeTagSilently(finalMifare)
                    } else if (activity.getString(R.string.nfc_null_array) == error) {
                        activity.runOnUiThread {
                            AlertDialog.Builder(activity)
                                .setTitle(R.string.possible_lock)
                                .setMessage(R.string.prepare_unlock)
                                .setPositiveButton(R.string.unlock) { dialog: DialogInterface, _: Int ->
                                    closeTagSilently(finalMifare)
                                    dialog.dismiss()
                                    activity.onNFCActivity.launch(
                                        Intent(
                                            activity, NfcActivity::class.java
                                        ).setAction(NFCIntent.ACTION_UNLOCK_UNIT)
                                    )
                                }
                                .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                                    closeTagSilently(finalMifare)
                                    dialog.dismiss()
                                }.show()
                        }
                    } else if (e is NullPointerException && error.contains(NTAG215.CONNECT)) {
                        activity.runOnUiThread {
                            AlertDialog.Builder(activity)
                                .setTitle(R.string.possible_blank)
                                .setMessage(R.string.prepare_blank)
                                .setPositiveButton(R.string.scan) { dialog: DialogInterface, _: Int ->
                                    dialog.dismiss()
                                    activity.onNFCActivity.launch(
                                        Intent(
                                            activity, NfcActivity::class.java
                                        ).setAction(NFCIntent.ACTION_BLIND_SCAN)
                                    )
                                }
                                .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                                .show()
                        }
                    }
                } else {
                    if (e is NullPointerException && error.contains(NTAG215.CONNECT)) {
                        error = activity.getString(R.string.error_tag_faulty)
                    }
                    Toasty(activity).Short(error)
                }
            } else {
                Toasty(activity).Short(R.string.error_unknown)
            }
        }
    }
}