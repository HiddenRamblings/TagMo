package com.hiddenramblings.tagmo.nfctech;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.NFCIntent;
import com.hiddenramblings.tagmo.NfcActivity;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.browser.BrowserActivity;
import com.hiddenramblings.tagmo.browser.Preferences;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.widget.Toasty;

import java.util.ArrayList;

public class ScanTag {

    private boolean hasTestedElite;
    private boolean isEliteDevice;

    private void closeTagSilently(NTAG215 mifare) {
        if (null != mifare) {
            try {
                mifare.close();
            } catch (Exception ignored) { }
        }
    }

    public void onTagDiscovered(BrowserActivity activity, Intent intent) {
        Preferences prefs = new Preferences(activity.getApplicationContext());
        NTAG215 mifare = null;
        try {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            mifare = NTAG215.get(tag);
            String tagTech = TagArray.getTagTechnology(tag);
            if (mifare == null) {
                if (prefs.elite_support()) {
                    mifare = new NTAG215(NfcA.get(tag));
                    try {
                        mifare.connect();
                    } catch (Exception ex) {
                        Debug.Info(ex);
                    }
                    if (TagReader.needsFirmware(mifare)) {
                        if (TagWriter.updateFirmware(mifare))
                            new Toasty(activity).Short(R.string.firmware_update);
                        mifare.close();
                        activity.finish();
                    }
                }
                throw new Exception(activity.getString(R.string.error_tag_protocol, tagTech));
            }
            mifare.connect();
            if (!hasTestedElite) {
                hasTestedElite = true;
                if (!TagArray.isPowerTag(mifare)) {
                    isEliteDevice = TagArray.isElite(mifare);
                }
            }
            byte[] bank_details;
            int bank_count;
            int active_bank;
            if (!isEliteDevice) {
                bank_count = -1;
                active_bank = -1;
            } else {
                bank_details = TagReader.getBankDetails(mifare);
                bank_count = bank_details[1] & 0xFF;
                active_bank = bank_details[0] & 0xFF;
            }
            try {
                if (isEliteDevice) {
                    String signature = TagReader.getBankSignature(mifare);
                    prefs.elite_signature(signature);
                    prefs.eliteActiveBank(active_bank);
                    prefs.eliteBankCount(bank_count);

                    Bundle args = new Bundle();
                    ArrayList<String> titles = TagReader.readTagTitles(mifare, bank_count);
                    args.putString(NFCIntent.EXTRA_SIGNATURE, signature);
                    args.putInt(NFCIntent.EXTRA_BANK_COUNT, bank_count);
                    args.putInt(NFCIntent.EXTRA_ACTIVE_BANK, active_bank);
                    args.putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST, titles);
                    activity.showElitePage(args);

                } else {
                    activity.updateAmiiboView(TagReader.readFromTag(mifare));
                }
                hasTestedElite = false;
                isEliteDevice = false;
            } finally {
                mifare.close();
            }
        } catch (Exception e) {
            Debug.Warn(e);
            String error = e.getMessage();
            error = null != e.getCause() ? error + "\n" + e.getCause().toString() : error;
            if (null != error) {
                if (prefs.elite_support()) {
                    NTAG215 finalMifare = mifare;
                    if (e instanceof android.nfc.TagLostException) {
                        new IconifiedSnackbar(activity, activity.getLayout()).buildSnackbar(
                                R.string.speed_scan, Snackbar.LENGTH_SHORT
                        ).show();
                        closeTagSilently(finalMifare);
                    } else if (activity.getString(R.string.nfc_null_array).equals(error)) {
                        activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                                .setTitle(R.string.possible_lock)
                                .setMessage(R.string.prepare_unlock)
                                .setPositiveButton(R.string.unlock, (dialog, which) -> {
                                    closeTagSilently(finalMifare);
                                    dialog.dismiss();
                                    activity.onNFCActivity.launch(new Intent(
                                            activity, NfcActivity.class
                                    ).setAction(NFCIntent.ACTION_UNLOCK_UNIT));
                                })
                                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                                    closeTagSilently(finalMifare);
                                    dialog.dismiss();
                                }).show());
                    } else if (e instanceof NullPointerException && error.contains(NTAG215.CONNECT)) {
                        activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                                .setTitle(R.string.possible_blank)
                                .setMessage(R.string.prepare_blank)
                                .setPositiveButton(R.string.scan, (dialog, which) -> {
                                    dialog.dismiss();
                                    activity.onNFCActivity.launch(new Intent(
                                            activity, NfcActivity.class
                                    ).setAction(NFCIntent.ACTION_BLIND_SCAN));
                                })
                                .setNegativeButton(R.string.cancel, (dialog, which) ->
                                        dialog.dismiss()).show());
                    }
                } else {
                    if (e instanceof NullPointerException && error.contains(NTAG215.CONNECT)) {
                        error = activity.getString(R.string.error_tag_faulty);
                    }
                    new Toasty(activity).Short(error);
                }
            } else {
                new Toasty(activity).Short(R.string.error_unknown);
            }
        }
    }
}
