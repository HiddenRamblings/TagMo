package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.nfctag.KeyManager;
import com.hiddenramblings.tagmo.nfctag.NTAG215;
import com.hiddenramblings.tagmo.nfctag.TagReader;
import com.hiddenramblings.tagmo.nfctag.TagUtils;
import com.hiddenramblings.tagmo.nfctag.TagWriter;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.util.ArrayList;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_nfc)
public class NfcActivity extends AppCompatActivity {

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
            case TagMo.ACTION_SET_BANK_COUNT:
            case TagMo.ACTION_ACTIVATE_BANK:
            case TagMo.ACTION_FORMAT_BANK:
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
        else if (TagMo.getPrefs().enableEliteSupport().get()) {
            bankNumberPicker.setValue(TagMo.getPrefs().eliteActiveBank().get());
        } else {
            bankTextView.setVisibility(View.GONE);
            bankNumberPicker.setVisibility(View.GONE);
        }
        if (commandIntent.hasExtra(TagMo.EXTRA_BANK_COUNT)) {
            write_count = commandIntent.getIntExtra(TagMo.EXTRA_BANK_COUNT, 1);
        }
        switch (mode) {
            case TagMo.ACTION_WRITE_TAG_FULL:
                if (!commandIntent.hasExtra(TagMo.EXTRA_CURRENT_BANK)) {
                    bankNumberPicker.setVisibility(View.GONE);
                    bankNumberPicker.setEnabled(false);
                    bankTextView.setVisibility(View.GONE);
                }
            case TagMo.ACTION_WRITE_TAG_RAW:
            case TagMo.ACTION_WRITE_TAG_DATA:
                bankNumberPicker.setMaxValue(TagMo.getPrefs().eliteBankCount().get());
                break;
            case TagMo.ACTION_WRITE_ALL_TAGS:
            case TagMo.ACTION_SCAN_TAG:
            case TagMo.ACTION_SET_BANK_COUNT:
            case TagMo.ACTION_LOCK_AMIIBO:
            case TagMo.ACTION_UNLOCK_UNIT:
                bankNumberPicker.setVisibility(View.GONE);
            case TagMo.ACTION_ACTIVATE_BANK:
                bankNumberPicker.setEnabled(false);
                bankTextView.setVisibility(View.GONE);
                break;
            case TagMo.ACTION_BACKUP_AMIIBO:
                if (commandIntent.hasExtra(TagMo.EXTRA_CURRENT_BANK)
                        || !TagMo.getPrefs().enableEliteSupport().get()) {
                    bankNumberPicker.setVisibility(View.GONE);
                    bankNumberPicker.setEnabled(false);
                    bankTextView.setVisibility(View.GONE);
                }
                break;
            case TagMo.ACTION_FORMAT_BANK:
                break;
        }
        switch (mode) {
            case TagMo.ACTION_WRITE_TAG_RAW:
                setTitle(R.string.write_raw);
                break;
            case TagMo.ACTION_WRITE_TAG_FULL:
                setTitle(R.string.write_auto);
                break;
            case TagMo.ACTION_WRITE_TAG_DATA:
                setTitle(R.string.restore_tag);
                break;
            case TagMo.ACTION_WRITE_ALL_TAGS:
                setTitle(R.string.write_collection);
                break;
            case TagMo.ACTION_BACKUP_AMIIBO:
                setTitle(R.string.backup_amiibo);
                break;
            case TagMo.ACTION_SCAN_TAG:
                setTitle(R.string.scan_tag);
                break;
            case TagMo.ACTION_SET_BANK_COUNT:
                setTitle(R.string.set_bank_count);
                break;
            case TagMo.ACTION_LOCK_AMIIBO:
                setTitle(R.string.lock_amiibo);
                break;
            case TagMo.ACTION_UNLOCK_UNIT:
                isUnlocking = true;
                setTitle(R.string.unlock_elite);
                break;
            case TagMo.ACTION_ACTIVATE_BANK:
                setTitle(R.string.activate_bank);
                break;
            case TagMo.ACTION_FORMAT_BANK:
                setTitle(R.string.wipe_bank);
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
    
    private byte[] cheapTagQuickTest(Intent commandIntent) throws Exception {
        byte[] data = new byte[0];
        if (commandIntent.hasExtra(TagMo.EXTRA_TAG_DATA)) {
            data = commandIntent.getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
            if (data == null) throw new Exception(getString(R.string.no_data));
        }
        if (commandIntent.getAction().equals(TagMo.ACTION_WRITE_TAG_FULL)
                && !commandIntent.hasExtra(TagMo.EXTRA_CURRENT_BANK)) {
            TagWriter.writeToTagAuto(mifare, data, this.keyManager,
                    TagMo.getPrefs().enableTagTypeValidation().get());
            setResult(Activity.RESULT_OK);
        } else if (commandIntent.getAction().equals(TagMo.ACTION_SCAN_TAG)) {
            data = TagReader.readFromTag(mifare);
        }
        return data;
    }

    @Background
    void onTagDiscovered(Intent intent) {
        Intent commandIntent = this.getIntent();
        String mode = commandIntent.getAction();
        setResult(Activity.RESULT_CANCELED);
        try {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            TagMo.Debug(getClass(), tag.toString());
            mifare = NTAG215.get(tag);
            if (mifare == null) {
                throw new Exception(getString(R.string.tag_type_error));
            }
            mifare.connect();
            byte[] data = cheapTagQuickTest(commandIntent);
            boolean isElite = TagUtils.isElite(mifare);
            int selection;
            byte[] bank_details;
            int bank_count;
            int active_bank;
            if (!isElite || mode.equals(TagMo.ACTION_UNLOCK_UNIT)
                    || mode.equals(TagMo.ACTION_BACKUP_AMIIBO)) {
                selection = 0;
                bank_count = -1;
                active_bank = -1;
            } else {
                selection = 0;
                bank_details = TagReader.getEliteDetails(mifare);
                bank_count = bank_details[1] & 0xFF;
                active_bank = TagUtils.getValueForPosition(bank_details[0] & 0xFF);
                if (!mode.equals(TagMo.ACTION_SET_BANK_COUNT)
                        && !mode.equals(TagMo.ACTION_WRITE_ALL_TAGS)) {
                    selection = TagUtils.getPositionForValue(bankNumberPicker.getValue());
                    if (selection > bank_count) {
                        throw new Exception(getString(R.string.fail_bank_oob));
                    }
                }
            }
            try {
                switch (mode) {
                    case TagMo.ACTION_WRITE_TAG_RAW:
                        TagWriter.writeToTagRaw(mifare, data, TagMo.getPrefs().enableTagTypeValidation().get());
                        setResult(Activity.RESULT_OK);
                        break;

                    case TagMo.ACTION_WRITE_TAG_FULL:
                        if (isElite) {
                            TagWriter.writeEliteAuto(mifare, data, selection);
                            Intent write = new Intent(TagMo.ACTION_NFC_SCANNED);
                            write.putExtra(TagMo.EXTRA_SIGNATURE,
                                    TagReader.getEliteSignature(mifare));
                            write.putExtra(TagMo.EXTRA_BANK_COUNT, bank_count);
                            write.putExtra(TagMo.EXTRA_ACTIVE_BANK, active_bank);
                            write.putExtra(TagMo.EXTRA_AMIIBO_DATA,
                                    TagReader.readTagTitles(mifare, bank_count));
                            write.putExtra(TagMo.EXTRA_TAG_DATA, data);
                            setResult(Activity.RESULT_OK, write);
                        }
                        break;

                    case TagMo.ACTION_WRITE_TAG_DATA:
                        boolean ignoreUid = commandIntent.getBooleanExtra(
                                TagMo.EXTRA_IGNORE_TAG_ID, false);
                        TagWriter.restoreTag(mifare, data, ignoreUid, this.keyManager,
                                TagMo.getPrefs().enableTagTypeValidation().get());
                        setResult(Activity.RESULT_OK);
                        break;

                    case TagMo.ACTION_WRITE_ALL_TAGS:
                        ArrayList<AmiiboFile> amiiboList =
                                commandIntent.getParcelableArrayListExtra(TagMo.EXTRA_AMIIBO_FILES);
                        for (int x = 0; x < amiiboList.size(); x++) {
                            data = TagReader.readTagStream(amiiboList.get(x).getFilePath());
                            TagWriter.writeEliteAuto(mifare, data, x);
                        }
                        Intent write = new Intent(TagMo.ACTION_NFC_SCANNED);
                        write.putExtra(TagMo.EXTRA_AMIIBO_DATA,
                                TagReader.readTagTitles(mifare, bank_count));
                        setResult(Activity.RESULT_OK, write);
                        break;

                    case TagMo.ACTION_BACKUP_AMIIBO:
                        if (isElite) {
                            data = TagReader.scanBankToBytes(mifare,
                                    TagUtils.getPositionForValue(bankNumberPicker.getValue()));
                        } else {
                            data = TagReader.scanTagToBytes(mifare);
                        }
                        Intent backup = new Intent(TagMo.ACTION_NFC_SCANNED);
                        backup.putExtra(TagMo.EXTRA_TAG_DATA, data);
                        setResult(Activity.RESULT_OK, backup);
                        break;

                    case TagMo.ACTION_SCAN_TAG:
                        if (isElite && !commandIntent.hasExtra(TagMo.EXTRA_CURRENT_BANK)) {
                            if (TagReader.needsFirmware(mifare)) {
                                if (TagReader.updateFirmware(mifare))
                                    showToast(getString(R.string.firmware_update));
                            }
                            ArrayList<String> titles = TagReader.readTagTitles(mifare, bank_count);
                            Intent results = new Intent(TagMo.ACTION_NFC_SCANNED);
                            results.putExtra(TagMo.EXTRA_SIGNATURE,
                                    TagReader.getEliteSignature(mifare));
                            results.putExtra(TagMo.EXTRA_BANK_COUNT, bank_count);
                            results.putExtra(TagMo.EXTRA_ACTIVE_BANK, active_bank);
                            results.putExtra(TagMo.EXTRA_AMIIBO_DATA, titles);
                            setResult(Activity.RESULT_OK, results);
                        } else {
                            Intent result = new Intent(TagMo.ACTION_NFC_SCANNED);
                            result.putExtra(TagMo.EXTRA_TAG_DATA, data);
                            setResult(Activity.RESULT_OK, result);
                        }
                        break;

                    case TagMo.ACTION_SET_BANK_COUNT:
                        mifare.setBankCount(write_count);
                        mifare.activateBank(TagUtils.getPositionForValue(active_bank));
                        ArrayList<String> list = TagReader.readTagTitles(mifare, write_count);
                        Intent configure = new Intent(TagMo.ACTION_NFC_SCANNED);
                        configure.putExtra(TagMo.EXTRA_BANK_COUNT, write_count);
                        configure.putExtra(TagMo.EXTRA_AMIIBO_DATA, list);
                        setResult(Activity.RESULT_OK, configure);
                        break;

                    case TagMo.ACTION_ACTIVATE_BANK:
                        mifare.activateBank(selection);
                        Intent active = new Intent(TagMo.ACTION_NFC_SCANNED);
                        active.putExtra(TagMo.EXTRA_ACTIVE_BANK,
                                TagUtils.getValueForPosition(selection));
                        active.putExtra(TagMo.EXTRA_AMIIBO_DATA,
                                TagReader.readTagTitles(mifare, bank_count));
                        setResult(Activity.RESULT_OK, active);
                        break;

                    case TagMo.ACTION_FORMAT_BANK:
                        TagWriter.wipeBankData(mifare, selection);
                        Intent format = new Intent(TagMo.ACTION_NFC_SCANNED);
                        format.putExtra(TagMo.EXTRA_BANK_COUNT, bank_count);
                        format.putExtra(TagMo.EXTRA_AMIIBO_DATA,
                                TagReader.readTagTitles(mifare, bank_count));
                        setResult(Activity.RESULT_OK, format);
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
            if (error != null && TagMo.getPrefs().enableEliteSupport().get()) {
                if (error.equals(TagMo.getStringRes(R.string.null_array_tag))) {
                    runOnUiThread(() -> new AlertDialog.Builder(NfcActivity.this)
                            .setTitle(R.string.possible_lock)
                            .setMessage(R.string.prepare_unlock)
                            .setNegativeButton(R.string.unlock, (dialog, which) -> {
                                dialog.dismiss();
                                finish();
                                Intent unlock = new Intent(this, NfcActivity_.class);
                                unlock.setAction(TagMo.ACTION_UNLOCK_UNIT);
                                startActivity(unlock);
                            })
                            .setPositiveButton(R.string.cancel, null).show());
                } else if (error.equals(TagMo.getStringRes(R.string.scan_elite_menu))) {
                    showToast(TagMo.getStringRes(R.string.scan_elite_menu));
                    finish();
                }
            }
            showError(error);
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
                    .setNegativeButton(R.string.yes, (dialog, which) ->
                            startActivity(new Intent(Build.VERSION.SDK_INT
                                    >= Build.VERSION_CODES.JELLY_BEAN
                                    ? Settings.ACTION_NFC_SETTINGS
                                    : Settings.ACTION_WIRELESS_SETTINGS)
                    ))
                    .setPositiveButton(R.string.no, null)
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
