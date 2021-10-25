package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.nfc.KeyManager;
import com.hiddenramblings.tagmo.nfc.NTAG215;
import com.hiddenramblings.tagmo.nfc.TagUtils;
import com.hiddenramblings.tagmo.nfc.TagWriter;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.sharedpreferences.Pref;

import java.util.ArrayList;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_nfc)
public class NfcActivity extends AppCompatActivity {

    @Pref
    Preferences_ prefs;

    @ViewById(R.id.txtMessage)
    TextView txtMessage;
    @ViewById(R.id.txtError)
    TextView txtError;
    @ViewById(R.id.imgNfcBar)
    ImageView imgNfcBar;
    @ViewById(R.id.imgNfcCircle)
    ImageView imgNfcCircle;
    @ViewById(R.id.bank_number_picker)
    BankNumberPicker bankNumberPicker;
    @ViewById(R.id.bank_number_details)
    TextView bankTextView;

    NfcAdapter nfcAdapter;
    KeyManager keyManager;
    Animation nfcAnimation;

    private NTAG215 mifare;
    private volatile boolean isUnlocking;
    private int write_count;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @AfterViews
    void afterViews() {
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        this.keyManager = new KeyManager(this);
        configureInterface();

        nfcAnimation = AnimationUtils.loadAnimation(this, R.anim.nfc_scanning);
    }

    @Override
    protected void onResume() {
        super.onResume();
        clearError();
        String action = getIntent().getAction();
        switch (action) {
            case TagMo.ACTION_WRITE_TAG_FULL:
            case TagMo.ACTION_WRITE_TAG_DATA:
                if (this.keyManager.isKeyMissing()) {
                    showError("Keys not loaded");
                    this.nfcAdapter = null;
                    break;
                }
            case TagMo.ACTION_WRITE_TAG_RAW:
            case TagMo.ACTION_WRITE_ALL_TAGS:
            case TagMo.ACTION_BACKUP_AMIIBO:
            case TagMo.ACTION_SCAN_TAG:
            case TagMo.ACTION_SCAN_UNIT:
            case TagMo.ACTION_SET_BANK_COUNT:
            case TagMo.ACTION_ACTIVATE_BANK:
            case TagMo.ACTION_DELETE_BANK:
            case TagMo.ACTION_LOCK_AMIIBO:
            case TagMo.ACTION_UNLOCK_UNIT:
                startNfcMonitor();
                break;
        }
    }

    void configureInterface() {
        Intent commandIntent = this.getIntent();
        String mode = commandIntent.getAction();

        if (commandIntent.hasExtra(TagMo.EXTRA_CURRENT_BANK))
            bankNumberPicker.setValue(commandIntent.getIntExtra(
                    TagMo.EXTRA_CURRENT_BANK, bankNumberPicker.getValue()));
        else if (prefs.enableEliteSupport().get()) {
            bankNumberPicker.setValue(prefs.eliteActiveBank().get());
        } else {
            bankTextView.setVisibility(View.GONE);
            bankNumberPicker.setVisibility(View.GONE);
        }
        if (commandIntent.hasExtra(TagMo.EXTRA_BANK_COUNT)) {
            write_count = commandIntent.getIntExtra(TagMo.EXTRA_BANK_COUNT, 1);
        }
        if (mode.equals(TagMo.ACTION_UNLOCK_UNIT)) isUnlocking = true;
        switch (mode) {
            case TagMo.ACTION_WRITE_TAG_RAW:
                bankNumberPicker.setMaxValue(prefs.eliteBankCount().get());
                setTitle(R.string.write_raw);
                break;
            case TagMo.ACTION_WRITE_TAG_FULL:
                bankNumberPicker.setMaxValue(prefs.eliteBankCount().get());
                setTitle(R.string.write_auto);
                break;
            case TagMo.ACTION_WRITE_TAG_DATA:
                bankNumberPicker.setMaxValue(prefs.eliteBankCount().get());
                setTitle(R.string.restore_tag);
                break;
            case TagMo.ACTION_WRITE_ALL_TAGS:
                bankNumberPicker.setVisibility(View.GONE);
                bankNumberPicker.setEnabled(false);
                bankTextView.setVisibility(View.GONE);
                setTitle(R.string.write_collection);
            case TagMo.ACTION_BACKUP_AMIIBO:
                bankNumberPicker.setVisibility(View.GONE);
                bankNumberPicker.setEnabled(false);
                bankTextView.setVisibility(View.GONE);
                setTitle(R.string.backup_amiibo);
                break;
            case TagMo.ACTION_SCAN_TAG:
                setTitle(R.string.scan_tag);
                break;
            case TagMo.ACTION_SCAN_UNIT:
            case TagMo.ACTION_SET_BANK_COUNT:
            case TagMo.ACTION_LOCK_AMIIBO:
            case TagMo.ACTION_UNLOCK_UNIT:
                bankNumberPicker.setVisibility(View.GONE);
            case TagMo.ACTION_ACTIVATE_BANK:
                bankNumberPicker.setEnabled(false);
                bankTextView.setVisibility(View.GONE);
                setTitle(R.string.scan_elite);
                break;
            case TagMo.ACTION_DELETE_BANK:
                setTitle(R.string.delete_bank);
                break;


            default:
                setTitle(R.string.error);
                finish();
        }
    }

    @Override
    protected void onPause() {
        stopNfcMonitor();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.getAction().equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
            showMessage(getString(R.string.tag_detected));
            this.onTagDiscovered(intent);
        }
    }

    @UiThread
    void showMessage(String msg) {
        txtMessage.setText(msg);
    }

    @UiThread
    void showError(String msg) {
        txtError.setText(msg);
        txtError.setVisibility(View.VISIBLE);
        txtMessage.setVisibility(View.GONE);
        imgNfcCircle.setVisibility(View.GONE);
        imgNfcBar.setVisibility(View.GONE);
        imgNfcBar.clearAnimation();
    }

    @UiThread
    void clearError() {
        txtError.setVisibility(View.GONE);
        txtMessage.setVisibility(View.VISIBLE);
        imgNfcCircle.setVisibility(View.VISIBLE);
        imgNfcBar.setVisibility(View.VISIBLE);
        imgNfcBar.setAnimation(nfcAnimation);
    }

    @Background
    void onTagDiscovered(Intent intent) {
        Intent commandIntent = this.getIntent();
        String mode = commandIntent.getAction();
        try {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            TagMo.Debug(getClass(), tag.toString());
            mifare = NTAG215.get(tag);
            if (mifare == null) {
                throw new Exception(getString(R.string.tag_type_error));
            }
            mifare.connect();
            int selection;
            byte[] bank_details;
            int bank_count;
            int active_bank;
            if (!prefs.enableEliteSupport().get()
                    || mode.equals(TagMo.ACTION_BACKUP_AMIIBO)
                    || mode.equals(TagMo.ACTION_UNLOCK_UNIT)) {
                selection = 0;
                bank_count = -1;
                active_bank = -1;
            } else {
                selection = 0;
                bank_details = TagWriter.getBankDetails(mifare);
                bank_count = bank_details[1] & 0xFF;
                active_bank = TagWriter.getValueFromPosition(bank_details[0] & 0xFF);
                if (!mode.equals(TagMo.ACTION_SET_BANK_COUNT)
                        && !mode.equals(TagMo.ACTION_WRITE_ALL_TAGS)
                        && !mode.equals(TagMo.ACTION_SCAN_UNIT)) {
                    selection = TagWriter.getPositionFromValue(bankNumberPicker.getValue());
                    if (selection > bank_count) {
                        throw new Exception(getString(R.string.fail_bank_oob));
                    }
                }
            }
            setResult(Activity.RESULT_CANCELED);
            try {
                TagMo.Debug(getClass(), mode);
                byte[] data;
                switch (mode) {
                    case TagMo.ACTION_WRITE_TAG_RAW:
                        data = commandIntent.getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
                        if (data == null) {
                            throw new Exception(getString(R.string.no_data));
                        }
                        TagWriter.writeToTagRaw(mifare, data, prefs.enableTagTypeValidation().get());
                        setResult(Activity.RESULT_OK);
                        showToast(getString(R.string.done));
                        break;

                    case TagMo.ACTION_WRITE_TAG_FULL:
                        data = commandIntent.getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
                        if (data == null) {
                            throw new Exception(getString(R.string.no_data));
                        }
                        if (prefs.enableEliteSupport().get()) {
                            TagWriter.writeEliteAuto(mifare, data, selection);
                            Intent write = new Intent(TagMo.ACTION_NFC_SCANNED);
                            write.putExtra(TagMo.EXTRA_SIGNATURE,
                                    TagWriter.getEliteSignature(mifare));
                            write.putExtra(TagMo.EXTRA_BANK_COUNT, bank_count);
                            write.putExtra(TagMo.EXTRA_ACTIVE_BANK, active_bank);
                            write.putExtra(TagMo.EXTRA_UNIT_DATA,
                                    TagWriter.readFromTags(mifare, bank_count));
                            write.putExtra(TagMo.EXTRA_TAG_DATA, data);
                            setResult(Activity.RESULT_OK, write);
                        } else {
                            TagWriter.writeToTagAuto(mifare, data, this.keyManager,
                                    prefs.enableTagTypeValidation().get(),
                                    prefs.enablePowerTagSupport().get());
                            setResult(Activity.RESULT_OK);
                        }
                        showToast(getString(R.string.done));
                        break;

                    case TagMo.ACTION_WRITE_TAG_DATA:
                        data = commandIntent.getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
                        boolean ignoreUid = commandIntent.getBooleanExtra(
                                TagMo.EXTRA_IGNORE_TAG_ID, false);
                        if (data == null) {
                            throw new Exception(getString(R.string.no_data));
                        }
                        TagWriter.restoreTag(mifare, data, ignoreUid,
                                this.keyManager, prefs.enableTagTypeValidation().get());
                        setResult(Activity.RESULT_OK);
                        showToast(getString(R.string.done));
                        break;

                    case TagMo.ACTION_WRITE_ALL_TAGS:
                        ArrayList<AmiiboFile> amiiboList =
                                commandIntent.getParcelableArrayListExtra(TagMo.EXTRA_AMIIBO_FILES);
                        for (int x = 0; x < amiiboList.size(); x++) {
                            Uri fileUri;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                fileUri = FileProvider.getUriForFile(this,
                                        TagMo.PROVIDER, amiiboList.get(x).getFilePath());
                            } else {
                                fileUri = Uri.fromFile(amiiboList.get(x).getFilePath());
                            }
                            data = TagUtils.readTag(getContentResolver().openInputStream(fileUri));
                            TagWriter.writeEliteAuto(mifare, data, x);
                        }
                        Intent write = new Intent(TagMo.ACTION_NFC_SCANNED);
                        write.putExtra(TagMo.EXTRA_UNIT_DATA,
                                TagWriter.readFromTags(mifare, bank_count));
                        setResult(Activity.RESULT_OK, write);
                        break;

                    case TagMo.ACTION_BACKUP_AMIIBO:
                        if (prefs.enableEliteSupport().get()) {
                            mifare.activateBank(TagWriter.getPositionFromValue(
                                    bankNumberPicker.getValue()));
                        }
                        data = TagWriter.scanTagToBytes(mifare);
                        if (prefs.enableEliteSupport().get()) {
                            mifare.activateBank(prefs.eliteActiveBank().get());
                        }
                        Intent backup = new Intent(TagMo.ACTION_NFC_SCANNED);
                        backup.putExtra(TagMo.EXTRA_TAG_DATA, data);
                        setResult(Activity.RESULT_OK, backup);
                        break;

                    case TagMo.ACTION_SCAN_TAG:
                        data = TagWriter.readFromTag(mifare);
                        Intent result = new Intent(TagMo.ACTION_NFC_SCANNED);
                        result.putExtra(TagMo.EXTRA_TAG_DATA, data);
                        setResult(Activity.RESULT_OK, result);
                        showToast(getString(R.string.done));
                        break;

                    case TagMo.ACTION_SCAN_UNIT:
                        ArrayList<String> tags = TagWriter.readFromTags(mifare, bank_count);
                        if (TagWriter.needsFirmware(mifare)) {
                            if (TagWriter.flashFirmware(mifare))
                                showToast(getString(R.string.firmware_update));
                        }
                        Intent results = new Intent(TagMo.ACTION_NFC_SCANNED);
                        results.putExtra(TagMo.EXTRA_SIGNATURE,
                                TagWriter.getEliteSignature(mifare));
                        results.putExtra(TagMo.EXTRA_BANK_COUNT, bank_count);
                        results.putExtra(TagMo.EXTRA_ACTIVE_BANK, active_bank);
                        results.putExtra(TagMo.EXTRA_UNIT_DATA, tags);
                        setResult(Activity.RESULT_OK, results);
                        break;

                    case TagMo.ACTION_SET_BANK_COUNT:
                        mifare.setBankCount(write_count);
                        mifare.activateBank(TagWriter.getPositionFromValue(active_bank));
                        ArrayList<String> list = TagWriter.readFromTags(mifare, write_count);
                        Intent configure = new Intent(TagMo.ACTION_NFC_SCANNED);
                        configure.putExtra(TagMo.EXTRA_BANK_COUNT, write_count);
                        configure.putExtra(TagMo.EXTRA_UNIT_DATA, list);
                        setResult(Activity.RESULT_OK, configure);
                        break;

                    case TagMo.ACTION_ACTIVATE_BANK:
                        mifare.activateBank(selection);
                        Intent active = new Intent(TagMo.ACTION_NFC_SCANNED);
                        active.putExtra(TagMo.EXTRA_ACTIVE_BANK,
                                TagWriter.getValueFromPosition(selection));
                        active.putExtra(TagMo.EXTRA_UNIT_DATA,
                                TagWriter.readFromTags(mifare, bank_count));
                        setResult(Activity.RESULT_OK, active);
                        break;

                    case TagMo.ACTION_DELETE_BANK:
                        TagWriter.deleteBank(mifare, selection);
                        Intent delete = new Intent(TagMo.ACTION_NFC_SCANNED);
                        delete.putExtra(TagMo.EXTRA_BANK_COUNT, bank_count);
                        delete.putExtra(TagMo.EXTRA_UNIT_DATA,
                                TagWriter.readFromTags(mifare, bank_count));
                        setResult(Activity.RESULT_OK, delete);
                        break;

                    case TagMo.ACTION_LOCK_AMIIBO:
                        mifare.amiiboLock();
                        setResult(Activity.RESULT_OK);
                        break;

                    case TagMo.ACTION_UNLOCK_UNIT:
                        if (isUnlocking) {
                            mifare.amiiboPrepareUnlock();
                            runOnUiThread(() -> new AlertDialog.Builder(NfcActivity.this)
                                    .setMessage(R.string.progress_unlock)
                                    .setPositiveButton(R.string.proceed, (dialog, which) -> {
                                        mifare.amiiboUnlock();
                                        isUnlocking = false;
                                        dialog.dismiss();
                                    }).show());
                            while (isUnlocking) {
                                setResult(Activity.RESULT_OK);
                            }
                        }
                       break;

                    default:
                        throw new Exception(getString(R.string.state_error, mode));
                }
            } finally {
                mifare.close();
            }
            finish();
        } catch (Exception e) {
            TagMo.Error(getClass(), R.string.error, e);
            String error = e.getMessage();
            if (e.getCause() != null) {
                error = error + "\n" + e.getCause().toString();
            }
            showError(error);
            if (prefs.enableEliteSupport().get() && error != null
                    && error.equals("Attempt to read from null array")) {
                runOnUiThread(() -> new AlertDialog.Builder(NfcActivity.this)
                        .setTitle(R.string.possible_lock)
                        .setMessage(R.string.prepare_unlock)
                        .setPositiveButton(R.string.unlock, (dialog, which) -> {
                            dialog.dismiss();
                            finish();
                            Intent unlock = new Intent(this, NfcActivity_.class);
                            unlock.setAction(TagMo.ACTION_UNLOCK_UNIT);
                            startActivity(unlock);
                        })
                        .setNegativeButton(R.string.cancel, null).show());
            }
        }
    }

    @UiThread
    void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    void startNfcMonitor() {
        if (nfcAdapter == null) {
            showError(getString(R.string.nfc_unsupported));
            return;
        }

        if (!nfcAdapter.isEnabled()) {
            showError(getString(R.string.nfc_disabled));
            new AlertDialog.Builder(this)
                    .setMessage(R.string.nfc_query)
                    .setPositiveButton(R.string.yes, (dialog, which) ->
                            startActivity(new Intent(Build.VERSION.SDK_INT
                                    >= Build.VERSION_CODES.JELLY_BEAN
                                    ? Settings.ACTION_NFC_SETTINGS
                                    : Settings.ACTION_WIRELESS_SETTINGS)
                    ))
                    .setNegativeButton(R.string.no, null)
                    .show();
        }

        // monitor nfc status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
            this.registerReceiver(mReceiver, filter);
        }
        listenForTags();
    }

    void stopNfcMonitor() {
        if (nfcAdapter == null) {
            return;
        }
        nfcAdapter.disableForegroundDispatch(this);
        this.unregisterReceiver(mReceiver);
    }

    void listenForTags() {
        Intent intent = new Intent(this.getApplicationContext(), this.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent nfcPendingIntent = PendingIntent.getActivity(this.getApplicationContext(), 0, intent, 0);

        String[][] nfcTechList = new String[][]{};

        IntentFilter filter1 = new IntentFilter();
        filter1.addAction(NfcAdapter.ACTION_TAG_DISCOVERED);

        IntentFilter[] nfcIntentFilter = new IntentFilter[]{filter1};

        nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, nfcIntentFilter, nfcTechList);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)) {
                if (!nfcAdapter.isEnabled()) {
                    showError(getString(R.string.nfc_disabled));
                } else {
                    listenForTags();
                    clearError();
                }
            }
        }
    };

    @OptionsItem(android.R.id.home)
    void cancelAction() {
        if (mifare != null) {
            try {
                mifare.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    @Override
    public void onBackPressed() {
        cancelAction();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }
}
