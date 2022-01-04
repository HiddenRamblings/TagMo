package com.hiddenramblings.tagmo;

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
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.nfctech.NTAG215;
import com.hiddenramblings.tagmo.nfctech.TagReader;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.nfctech.TagWriter;
import com.hiddenramblings.tagmo.settings.Preferences_;
import com.hiddenramblings.tagmo.widget.BankPicker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executors;

public class NfcActivity extends AppCompatActivity {

    private final Preferences_ prefs = TagMo.getPrefs();

    TextView txtMessage;
    TextView txtError;
    ImageView imgNfcBar;
    ImageView imgNfcCircle;
    BankPicker bankPicker;
    TextView bankTextView;

    private NfcAdapter nfcAdapter;
    private KeyManager keyManager;
    private Animation nfcAnimation;

    private boolean isEliteIntent;
    private boolean isEliteDevice;
    private NTAG215 mifare;
    private int write_count;
    private String tagTech;
    private boolean hasTestedElite;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_nfc);

        ActionBar actionBar = getSupportActionBar();
        if (null != actionBar) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        txtMessage = findViewById(R.id.txtMessage);
        txtError = findViewById(R.id.txtError);
        imgNfcBar = findViewById(R.id.imgNfcBar);
        imgNfcCircle = findViewById(R.id.imgNfcCircle);
        bankPicker = findViewById(R.id.bank_number_picker);
        bankTextView = findViewById(R.id.bank_number_details);

        this.nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        this.keyManager = new KeyManager(this);
        configureInterface();

        bankPicker.setBackgroundResource(TagMo.isDarkTheme()
                ? R.drawable.picker_border_dark : R.drawable.picker_border_light);

        nfcAnimation = AnimationUtils.loadAnimation(this, R.anim.nfc_scanning);
    }

    @Override
    protected void onResume() {
        super.onResume();
        clearError();
        String action = getIntent().getAction();
        switch (action) {
            case NFCIntent.ACTION_WRITE_TAG_FULL:
            case NFCIntent.ACTION_WRITE_TAG_DATA:
                if (this.keyManager.isKeyMissing()) {
                    showError("Keys not loaded");
                    this.nfcAdapter = null;
                    break;
                }
            case NFCIntent.ACTION_WRITE_TAG_RAW:
            case NFCIntent.ACTION_WRITE_ALL_TAGS:
            case NFCIntent.ACTION_BACKUP_AMIIBO:
            case NFCIntent.ACTION_SCAN_TAG:
            case NFCIntent.ACTION_SET_BANK_COUNT:
            case NFCIntent.ACTION_ACTIVATE_BANK:
            case NFCIntent.ACTION_ERASE_BANK:
            case NFCIntent.ACTION_LOCK_AMIIBO:
            case NFCIntent.ACTION_UNLOCK_UNIT:
                startNfcMonitor();
                break;
        }
    }

    private void configureInterface() {
        Intent commandIntent = this.getIntent();
        String mode = commandIntent.getAction();

        if (null != getCallingActivity())
            isEliteIntent = BankListActivity.class.getName().equals(
                    getCallingActivity().getClassName());
        if (commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
            bankPicker.setPosition(commandIntent.getIntExtra(
                    NFCIntent.EXTRA_CURRENT_BANK, bankPicker.getPosition()));
        } else if (isEliteIntent) {
            bankPicker.setPosition(prefs.eliteActiveBank().get());
        } else {
            bankTextView.setVisibility(View.GONE);
            bankPicker.setVisibility(View.GONE);
        }
        if (commandIntent.hasExtra(NFCIntent.EXTRA_BANK_COUNT)) {
            write_count = commandIntent.getIntExtra(NFCIntent.EXTRA_BANK_COUNT, 200);
        }
        switch (mode) {
            case NFCIntent.ACTION_WRITE_TAG_FULL:
            case NFCIntent.ACTION_WRITE_TAG_RAW:
            case NFCIntent.ACTION_WRITE_TAG_DATA:
                if (!isEliteIntent || !commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
                    bankPicker.setVisibility(View.GONE);
                    bankPicker.setEnabled(false);
                    bankTextView.setVisibility(View.GONE);
                }
                bankPicker.setMaxValue(prefs.eliteBankCount().get());
                break;
            case NFCIntent.ACTION_WRITE_ALL_TAGS:
            case NFCIntent.ACTION_SCAN_TAG:
            case NFCIntent.ACTION_SET_BANK_COUNT:
            case NFCIntent.ACTION_LOCK_AMIIBO:
            case NFCIntent.ACTION_UNLOCK_UNIT:
                bankPicker.setVisibility(View.GONE);
                bankPicker.setEnabled(false);
                bankTextView.setVisibility(View.GONE);
                break;
            case NFCIntent.ACTION_ACTIVATE_BANK:
            case NFCIntent.ACTION_BACKUP_AMIIBO:
            case NFCIntent.ACTION_ERASE_BANK:
                if (!isEliteIntent || !commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
                    bankPicker.setVisibility(View.GONE);
                    bankTextView.setVisibility(View.GONE);
                }
                break;
        }
        switch (mode) {
            case NFCIntent.ACTION_WRITE_TAG_RAW:
                setTitle(R.string.write_raw);
                break;
            case NFCIntent.ACTION_WRITE_TAG_FULL:
                setTitle(R.string.write_auto);
                break;
            case NFCIntent.ACTION_WRITE_TAG_DATA:
                setTitle(R.string.update_tag);
                break;
            case NFCIntent.ACTION_WRITE_ALL_TAGS:
                setTitle(R.string.write_collection);
                break;
            case NFCIntent.ACTION_BACKUP_AMIIBO:
                setTitle(R.string.amiibo_backup);
                break;
            case NFCIntent.ACTION_SCAN_TAG:
                if (commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
                    setTitle(getString(R.string.scan_bank_no, bankPicker.getValue()));
                } else if (isEliteIntent) {
                    setTitle(R.string.scan_elite);
                } else {
                    setTitle(R.string.scan_tag);
                }
                break;
            case NFCIntent.ACTION_SET_BANK_COUNT:
                setTitle(R.string.set_bank_count);
                break;
            case NFCIntent.ACTION_LOCK_AMIIBO:
                setTitle(R.string.lock_amiibo);
                break;
            case NFCIntent.ACTION_UNLOCK_UNIT:
                setTitle(R.string.unlock_elite);
                break;
            case NFCIntent.ACTION_ACTIVATE_BANK:
                setTitle(R.string.activate_bank);
                break;
            case NFCIntent.ACTION_ERASE_BANK:
                setTitle(R.string.erase_bank);
                break;

            default:
                setTitle(R.string.error_caps);
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
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())
                || NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            String tech = null != tagTech ? tagTech : getString(R.string.nfc_tag);
            showMessage(R.string.tag_detected, tech);
            Executors.newSingleThreadExecutor().execute(() -> this.onTagDiscovered(intent));
        }
    }

    private void showMessage(int msgRes) {
        this.runOnUiThread(() -> txtMessage.setText(msgRes));
    }

    private void showMessage(int msgRes, String params) {
        this.runOnUiThread(() -> txtMessage.setText(getString(msgRes, params)));
    }

    private void showError(String msg) {
        this.runOnUiThread(() -> {
            txtError.setText(msg);
            txtError.setVisibility(View.VISIBLE);
            txtMessage.setVisibility(View.GONE);
            imgNfcCircle.setVisibility(View.GONE);
            imgNfcBar.setVisibility(View.GONE);
            imgNfcBar.clearAnimation();
        });
    }

    private void clearError() {
        txtError.setVisibility(View.GONE);
        txtMessage.setVisibility(View.VISIBLE);
        imgNfcCircle.setVisibility(View.VISIBLE);
        imgNfcBar.setVisibility(View.VISIBLE);
        imgNfcBar.setAnimation(nfcAnimation);
    }

    private void onTagDiscovered(Intent intent) {
        Intent commandIntent = this.getIntent();
        String mode = commandIntent.getAction();
        setResult(Activity.RESULT_CANCELED);
        Bundle args = new Bundle();
        byte[] update = new byte[0];
        try {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            mifare = NTAG215.get(tag);
            tagTech = TagUtils.getTagTechnology(tag);
            mifare.connect();
            if (!hasTestedElite) {
                hasTestedElite = true;
                if (TagUtils.isPowerTag(mifare)) {
                    showMessage(R.string.tag_scanning, getString(R.string.power_tag));
                } else if (prefs.enable_elite_support().get()) {
                    isEliteDevice = TagUtils.isElite(mifare)
                            || NFCIntent.ACTION_UNLOCK_UNIT.equals(mode);
                    if (isEliteDevice) {
                        showMessage(R.string.tag_scanning, getString(R.string.elite_device));
                    } else {
                        showMessage(R.string.tag_scanning, tagTech);
                    }
                } else {
                    showMessage(R.string.tag_scanning, tagTech);
                }
            }
            int selection;
            byte[] bank_details;
            int bank_count;
            int active_bank;
            if (!isEliteDevice || NFCIntent.ACTION_UNLOCK_UNIT.equals(mode)) {
                selection = 0;
                bank_count = -1;
                active_bank = -1;
            } else {
                if (TagReader.needsFirmware(mifare)) {
                    if (TagWriter.updateFirmware(mifare))
                        showMessage(R.string.firmware_update);
                    mifare.close();
                    finish();
                }
                selection = 0;
                bank_details = TagReader.getBankDetails(mifare);
                bank_count = bank_details[1] & 0xFF;
                active_bank = bank_details[0] & 0xFF;
                if (!NFCIntent.ACTION_SET_BANK_COUNT.equals(mode)
                        && !NFCIntent.ACTION_WRITE_ALL_TAGS.equals(mode)) {
                    selection = bankPicker.getPosition();
                    if (selection > bank_count) {
                        throw new Exception(getString(R.string.fail_bank_oob));
                    }
                }
            }
            try {
                byte[] data = new byte[0];
                if (commandIntent.hasExtra(NFCIntent.EXTRA_TAG_DATA)) {
                    data = commandIntent.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA);
                    if (null == data || data.length <= 1)
                        throw new IOException(getString(R.string.error_no_data));
                }
                switch (mode) {
                    case NFCIntent.ACTION_WRITE_TAG_RAW:
                        update = TagReader.readFromTag(mifare);
                        TagWriter.writeToTagRaw(mifare, data,
                                prefs.enable_tag_type_validation().get());
                        setResult(Activity.RESULT_OK);
                        break;

                    case NFCIntent.ACTION_WRITE_TAG_FULL:
                        if (isEliteDevice) {
                            if (bankPicker.getVisibility() == View.GONE) {
                                showMessage(R.string.bank_select);
                                this.runOnUiThread(() -> {
                                    bankPicker.setVisibility(View.VISIBLE);
                                    bankPicker.setEnabled(true);
                                    bankTextView.setVisibility(View.VISIBLE);
                                });
                                setIntent(commandIntent);
                                hasTestedElite = false;
                                return;
                            }

                            TagWriter.writeEliteAuto(mifare, data, keyManager, selection);
                            Intent write = new Intent(NFCIntent.ACTION_NFC_SCANNED);
                            write.putExtra(NFCIntent.EXTRA_SIGNATURE,
                                    TagReader.getTagSignature(mifare));
                            write.putExtra(NFCIntent.EXTRA_BANK_COUNT, bank_count);
                            write.putExtra(NFCIntent.EXTRA_ACTIVE_BANK, active_bank);
                            write.putExtra(NFCIntent.EXTRA_CURRENT_BANK, selection);
                            args.putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST,
                                    TagReader.readTagTitles(mifare, bank_count));
                            args.putByteArray(NFCIntent.EXTRA_TAG_DATA, data);
                            setResult(Activity.RESULT_OK, write.putExtras(args));
                        } else {
                            update = TagReader.readFromTag(mifare);
                            TagWriter.writeToTagAuto(mifare, data, this.keyManager,
                                    prefs.enable_tag_type_validation().get());
                            setResult(Activity.RESULT_OK);
                        }
                        break;

                    case NFCIntent.ACTION_WRITE_TAG_DATA:
                        boolean ignoreUid = commandIntent.getBooleanExtra(
                                NFCIntent.EXTRA_IGNORE_TAG_ID, false);
                        TagWriter.restoreTag(mifare, data, ignoreUid, this.keyManager,
                                prefs.enable_tag_type_validation().get());
                        setResult(Activity.RESULT_OK);
                        break;

                    case NFCIntent.ACTION_WRITE_ALL_TAGS:
                        mifare.setBankCount(write_count);
                        if (active_bank <= write_count)
                            mifare.activateBank(active_bank);
                        ArrayList<AmiiboFile> amiiboList = commandIntent
                                .getParcelableArrayListExtra(NFCIntent.EXTRA_AMIIBO_FILES);
                        for (int x = 0; x < amiiboList.size(); x++) {
                            txtMessage.setText(getString(R.string.bank_writing,
                                    x + 1, amiiboList.size()));
                            byte[] tagData = amiiboList.get(x).getData();
                            if (null == tagData)
                                tagData = TagUtils.getValidatedFile(keyManager,
                                        amiiboList.get(x).getFilePath());
                            TagWriter.writeEliteAuto(mifare, tagData, keyManager, x);
                        }
                        Intent write = new Intent(NFCIntent.ACTION_NFC_SCANNED);
                        write.putExtra(NFCIntent.EXTRA_BANK_COUNT, write_count);
                        args.putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST,
                                TagReader.readTagTitles(mifare, bank_count));
                        setResult(Activity.RESULT_OK,  write.putExtras(args));
                        break;

                    case NFCIntent.ACTION_ERASE_ALL_TAGS:
                        mifare.setBankCount(write_count);
                        if (active_bank <= write_count)
                            mifare.activateBank(active_bank);
                        for (int x = 0; x < write_count; x++) {
                            txtMessage.setText(getString(R.string.bank_erasing,
                                    x + 1, write_count));
                            TagWriter.wipeBankData(mifare, x);
                        }
                        Intent erase = new Intent(NFCIntent.ACTION_NFC_SCANNED);
                        erase.putExtra(NFCIntent.EXTRA_BANK_COUNT, write_count);
                        args.putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST,
                                TagReader.readTagTitles(mifare, bank_count));
                        setResult(Activity.RESULT_OK,  erase.putExtras(args));
                        break;

                    case NFCIntent.ACTION_BACKUP_AMIIBO:
                        Intent backup = new Intent(NFCIntent.ACTION_NFC_SCANNED);
                        if (commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
                            args.putByteArray(NFCIntent.EXTRA_TAG_DATA,
                                    TagReader.scanTagToBytes(mifare, selection));
                            backup.putExtra(NFCIntent.EXTRA_CURRENT_BANK, selection);
                        } else {
                            args.putByteArray(NFCIntent.EXTRA_TAG_DATA,
                                    TagReader.scanTagToBytes(mifare, active_bank));
                        }
                        setResult(Activity.RESULT_OK, backup.putExtras(args));
                        break;

                    case NFCIntent.ACTION_SCAN_TAG:
                        Intent result = new Intent(NFCIntent.ACTION_NFC_SCANNED);
                        if (isEliteDevice) {
                            if (commandIntent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
                                data = TagUtils.getValidatedData(keyManager,
                                        TagReader.scanBankToBytes(mifare, selection));
                                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, data);
                                result.putExtra(NFCIntent.EXTRA_CURRENT_BANK, selection);
                            } else {
                                ArrayList<String> titles = TagReader.readTagTitles(mifare, bank_count);
                                result.putExtra(NFCIntent.EXTRA_SIGNATURE,
                                        TagReader.getTagSignature(mifare));
                                result.putExtra(NFCIntent.EXTRA_BANK_COUNT, bank_count);
                                result.putExtra(NFCIntent.EXTRA_ACTIVE_BANK, active_bank);
                                args.putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST, titles);
                            }
                        } else {
                            args.putByteArray(NFCIntent.EXTRA_TAG_DATA,
                                    TagReader.readFromTag(mifare));
                        }
                        setResult(Activity.RESULT_OK, result.putExtras(args));
                        break;

                    case NFCIntent.ACTION_SET_BANK_COUNT:
                        mifare.setBankCount(write_count);
                        mifare.activateBank(active_bank);
                        ArrayList<String> list = TagReader.readTagTitles(mifare, write_count);
                        Intent configure = new Intent(NFCIntent.ACTION_NFC_SCANNED);
                        configure.putExtra(NFCIntent.EXTRA_BANK_COUNT, write_count);
                        args.putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST, list);
                        setResult(Activity.RESULT_OK, configure.putExtras(args));
                        break;

                    case NFCIntent.ACTION_ACTIVATE_BANK:
                        mifare.activateBank(selection);
                        Intent active = new Intent(NFCIntent.ACTION_NFC_SCANNED);
                        active.putExtra(NFCIntent.EXTRA_ACTIVE_BANK,
                                TagReader.getBankDetails(mifare)[0] & 0xFF);
                        setResult(Activity.RESULT_OK, active);
                        break;

                    case NFCIntent.ACTION_ERASE_BANK:
                        TagWriter.wipeBankData(mifare, selection);
                        Intent format = new Intent(NFCIntent.ACTION_NFC_SCANNED);
                        args.putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST,
                                TagReader.readTagTitles(mifare, bank_count));
                        setResult(Activity.RESULT_OK, format.putExtras(args));
                        break;

                    case NFCIntent.ACTION_LOCK_AMIIBO:
                        mifare.amiiboLock();
                        setResult(Activity.RESULT_OK);
                        break;

                    case NFCIntent.ACTION_UNLOCK_UNIT:
                        if (null == mifare.amiiboPrepareUnlock()) {
                            Snackbar unlockBar = new IconifiedSnackbar(this,
                                    findViewById(R.id.coordinator))
                                    .buildTickerBar(getString(R.string.progress_unlock));
                            unlockBar.setAction(R.string.proceed, v -> {
                                mifare.amiiboUnlock();
                                unlockBar.dismiss();
                            }).show();
                            while (unlockBar.isShown()) {
                                setResult(Activity.RESULT_OK);
                            }
                        } else {
                            throw new Exception(getString(R.string.fail_unlock));
                        }
                        break;

                    default:
                        throw new Exception(getString(R.string.error_state, mode));
                }
            } finally {
                mifare.close();
            }
            finish();
        } catch (Exception e) {
            Debug.Log(e);
            String error = e.getMessage();
            error = null != e.getCause() ? error + "\n" + e.getCause().toString() : error;
            if (null != error && prefs.enable_elite_support().get()) {
                if (e instanceof android.nfc.TagLostException) {
                    txtMessage.setText(R.string.speed_scan);
                    try {
                        mifare.close();
                    } catch (IOException ignored) { }
                    return;
                } else if (getString(R.string.error_tag_rewrite).equals(error)) {
                    args.putByteArray(NFCIntent.EXTRA_TAG_DATA, update);
                    setResult(Activity.RESULT_OK, new Intent(NFCIntent.ACTION_UPDATE_TAG).putExtras(args));
                    runOnUiThread(() -> new AlertDialog.Builder(NfcActivity.this)
                            .setTitle(R.string.error_tag_rewrite)
                            .setMessage(R.string.tag_update_only)
                            .setPositiveButton(R.string.proceed, (dialog, which) -> {
                                try {
                                    mifare.close();
                                } catch (IOException ignored) { }
                                dialog.dismiss();
                                finish();
                            })
                           .show());
                    return;
                } else if (getString(R.string.nfc_null_array).equals(error)) {
                    runOnUiThread(() -> new AlertDialog.Builder(NfcActivity.this)
                            .setTitle(R.string.possible_lock)
                            .setMessage(R.string.prepare_unlock)
                            .setPositiveButton(R.string.unlock, (dialog, which) -> {
                                try {
                                    mifare.close();
                                } catch (IOException ignored) { }
                                dialog.dismiss();
                                finish();
                                Intent unlock = new Intent(this, NfcActivity.class);
                                unlock.setAction(NFCIntent.ACTION_UNLOCK_UNIT);
                                startActivity(unlock);
                            })
                            .setNegativeButton(R.string.cancel,  (dialog, which) -> {
                                try {
                                    mifare.close();
                                } catch (IOException ignored) { }
                                dialog.dismiss();
                                finish();
                            }).show());
                    return;
                }
            }
            showError(error);
        }
    }

    ActivityResultLauncher<Intent> onNFCActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> startNfcMonitor()
    );

    void startNfcMonitor() {
        if (null == nfcAdapter) {
            showError(getString(R.string.nfc_unsupported));
        } else if (!nfcAdapter.isEnabled()) {
            showError(getString(R.string.nfc_disabled));
            new AlertDialog.Builder(this)
                    .setMessage(R.string.nfc_query)
                    .setPositiveButton(R.string.yes, (dialog, which) ->
                            onNFCActivity.launch(new Intent(Settings.ACTION_NFC_SETTINGS))
                    )
                    .setNegativeButton(R.string.no, (dialog, which) ->
                            finish()
                    )
                    .show();
        } else {
            // monitor nfc status
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
                this.registerReceiver(mReceiver, filter);
            }
            listenForTags();
        }
    }

    private void stopNfcMonitor() {
        if (null == nfcAdapter) {
            return;
        }
        nfcAdapter.disableForegroundDispatch(this);
        this.unregisterReceiver(mReceiver);
    }

    private void listenForTags() {
        PendingIntent nfcPendingIntent = PendingIntent.getActivity(TagMo.getContext(), 0,
                new Intent(TagMo.getContext(), this.getClass()),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);

        String[][] nfcTechList = new String[][]{};

        IntentFilter filter = new IntentFilter();
        filter.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filter.addAction(NfcAdapter.ACTION_TECH_DISCOVERED);
        filter.addAction(NfcAdapter.ACTION_TAG_DISCOVERED);

        nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent,
                new IntentFilter[]{filter}, nfcTechList);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED.equals(action)) {
                if (!nfcAdapter.isEnabled()) {
                    showError(getString(R.string.nfc_disabled));
                } else {
                    listenForTags();
                    clearError();
                }
            }
        }
    };

    void cancelAction() {
        if (null != mifare) {
            try {
                mifare.close();
            } catch (Exception ignored) { }
        }
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            cancelAction();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        cancelAction();
    }
}
