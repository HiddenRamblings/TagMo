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
import android.nfc.tech.NfcA;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
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

import com.eightbit.io.Debug;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.nfctech.NTAG215;
import com.hiddenramblings.tagmo.nfctech.TagReader;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.nfctech.TagWriter;
import com.hiddenramblings.tagmo.widget.BankPicker;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.io.IOException;
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
    BankPicker bankPicker;
    @ViewById(R.id.bank_number_details)
    TextView bankTextView;

    private NfcAdapter nfcAdapter;
    private KeyManager keyManager;
    private Animation nfcAnimation;

    private boolean isEliteIntent;
    private boolean isEliteDevice;
    private NTAG215 mifare;
    private volatile boolean isUnlocking;
    private int write_count;
    private String tagTech;
    private boolean hasTestedElite;

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
            case TagMo.ACTION_WRITE_TAG_FULL:
            case TagMo.ACTION_WRITE_TAG_DATA:
                if (this.keyManager.isKeyMissing()) {
                    showError("Keys not loaded");
                    this.nfcAdapter = null;
                    break;
                }
            case TagMo.ACTION_WRITE_TAG_RAW:
            case TagMo.ACTION_WRITE_ALL_TAGS:
            case TagMo.ACTION_FIX_BANK_DATA:
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

        if (getCallingActivity() != null)
            isEliteIntent = BankListActivity_.class.getName().equals(
                    getCallingActivity().getClassName());
        if (commandIntent.hasExtra(TagMo.EXTRA_CURRENT_BANK)) {
            bankPicker.setPosition(commandIntent.getIntExtra(
                    TagMo.EXTRA_CURRENT_BANK, bankPicker.getPosition()));
        } else if (isEliteIntent) {
            bankPicker.setPosition(TagMo.getPrefs().eliteActiveBank().get());
        } else {
            bankTextView.setVisibility(View.GONE);
            bankPicker.setVisibility(View.GONE);
        }
        if (commandIntent.hasExtra(TagMo.EXTRA_BANK_COUNT)) {
            write_count = commandIntent.getIntExtra(TagMo.EXTRA_BANK_COUNT, 200);
        }
        switch (mode) {
            case TagMo.ACTION_WRITE_TAG_FULL:
            case TagMo.ACTION_WRITE_TAG_RAW:
            case TagMo.ACTION_WRITE_TAG_DATA:
                if (!isEliteIntent || !commandIntent.hasExtra(TagMo.EXTRA_CURRENT_BANK)) {
                    bankPicker.setVisibility(View.GONE);
                    bankPicker.setEnabled(false);
                    bankTextView.setVisibility(View.GONE);
                }
                bankPicker.setMaxValue(TagMo.getPrefs().eliteBankCount().get());
                break;
            case TagMo.ACTION_WRITE_ALL_TAGS:
            case TagMo.ACTION_SCAN_TAG:
            case TagMo.ACTION_SET_BANK_COUNT:
            case TagMo.ACTION_LOCK_AMIIBO:
            case TagMo.ACTION_UNLOCK_UNIT:
                bankPicker.setVisibility(View.GONE);
                bankPicker.setEnabled(false);
                bankTextView.setVisibility(View.GONE);
                break;
            case TagMo.ACTION_ACTIVATE_BANK:
            case TagMo.ACTION_FIX_BANK_DATA:
            case TagMo.ACTION_BACKUP_AMIIBO:
            case TagMo.ACTION_FORMAT_BANK:
                if (!isEliteIntent || !commandIntent.hasExtra(TagMo.EXTRA_CURRENT_BANK)) {
                    bankPicker.setVisibility(View.GONE);
                    bankTextView.setVisibility(View.GONE);
                }
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
            case TagMo.ACTION_FIX_BANK_DATA:
                setTitle(R.string.amiibo_repair);
                break;
            case TagMo.ACTION_BACKUP_AMIIBO:
                setTitle(R.string.amiibo_backup);
                break;
            case TagMo.ACTION_SCAN_TAG:
                if (commandIntent.hasExtra(TagMo.EXTRA_CURRENT_BANK)) {
                    setTitle(getString(R.string.scan_bank_no, bankPicker.getValue()));
                } else if (isEliteIntent) {
                    setTitle(R.string.scan_elite);
                } else {
                    setTitle(R.string.scan_tag);
                }
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
                setTitle(R.string.format_bank);
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
            String tech = tagTech != null ? tagTech : getString(R.string.nfc_tag);
            showMessage(getString(R.string.tag_detected, tech));
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
        setResult(Activity.RESULT_CANCELED);
        try {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            mifare = NTAG215.get(tag);
            tagTech = TagUtils.getTagTechnology(tag);
            if (mifare == null) {
                if (TagMo.getPrefs().enableEliteSupport().get()) {
                    mifare = new NTAG215(NfcA.get(tag));
                    try {
                        mifare.connect();
                    } catch (Exception ex) {
                        Debug.Log(ex);
                    }
                    if (TagReader.needsFirmware(mifare)) {
                        if (TagWriter.updateFirmware(mifare))
                            showMessage(getString(R.string.firmware_update));
                        mifare.close();
                        finish();
                    }
                }
                throw new Exception(getString(R.string.error_tag_protocol, tagTech));
            }
            mifare.connect();
            if (!hasTestedElite) {
                hasTestedElite = true;
                if (TagUtils.isPowerTag(mifare)) {
                    showMessage(getString(R.string.tag_scanning, getString(R.string.power_tag)));
                } else {
                    isEliteDevice = TagUtils.isElite(mifare);
                    if (isEliteDevice) {
                        showMessage(getString(R.string.tag_scanning, getString(R.string.elite_device)));
                    } else {
                        showMessage(getString(R.string.tag_scanning, tagTech));
                    }
                }
            }
            int selection;
            byte[] bank_details;
            int bank_count;
            int active_bank;
            if (!isEliteDevice || TagMo.ACTION_UNLOCK_UNIT.equals(mode)) {
                selection = 0;
                bank_count = -1;
                active_bank = -1;
            } else {
                selection = 0;
                bank_details = TagReader.getEliteDetails(mifare);
                bank_count = bank_details[1] & 0xFF;
                active_bank = bank_details[0] & 0xFF;
                if (!TagMo.ACTION_SET_BANK_COUNT.equals(mode)
                        && !TagMo.ACTION_WRITE_ALL_TAGS.equals(mode)) {
                    selection = bankPicker.getPosition();
                    if (selection > bank_count) {
                        throw new Exception(getString(R.string.fail_bank_oob));
                    }
                }
            }
            try {
                byte[] data = new byte[0];
                if (commandIntent.hasExtra(TagMo.EXTRA_TAG_DATA)) {
                    data = commandIntent.getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
                    if (data == null || data.length <= 1)
                        throw new IOException(getString(R.string.error_no_data));
                }
                Bundle args = new Bundle();
                switch (mode) {
                    case TagMo.ACTION_WRITE_TAG_RAW:
                        TagWriter.writeToTagRaw(mifare, data,
                                TagMo.getPrefs().enableTagTypeValidation().get());
                        setResult(Activity.RESULT_OK);
                        break;

                    case TagMo.ACTION_WRITE_TAG_FULL:
                        if (isEliteDevice) {
                            TagWriter.writeEliteAuto(mifare, data, keyManager, selection);
                            Intent write = new Intent(TagMo.ACTION_NFC_SCANNED);
                            write.putExtra(TagMo.EXTRA_SIGNATURE,
                                    TagReader.getEliteSignature(mifare));
                            write.putExtra(TagMo.EXTRA_BANK_COUNT, bank_count);
                            write.putExtra(TagMo.EXTRA_ACTIVE_BANK, active_bank);
                            args.putStringArrayList(TagMo.EXTRA_AMIIBO_LIST,
                                    TagReader.readTagTitles(mifare, bank_count));
                            args.putByteArray(TagMo.EXTRA_TAG_DATA, data);
                            setResult(Activity.RESULT_OK, write.putExtras(args));
                        } else {
                            TagWriter.writeToTagAuto(mifare, data, this.keyManager,
                                    TagMo.getPrefs().enableTagTypeValidation().get());
                            setResult(Activity.RESULT_OK);
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
                        mifare.setBankCount(write_count);
                        if (active_bank <= write_count)
                            mifare.activateBank(active_bank);
                        ArrayList<AmiiboFile> amiiboList = commandIntent
                                .getParcelableArrayListExtra(TagMo.EXTRA_AMIIBO_FILES);
                        for (int x = 0; x < amiiboList.size(); x++) {
                            TagWriter.writeEliteAuto(mifare, amiiboList.get(x).getData() != null
                                    ? amiiboList.get(x).getData()
                                    : TagReader.getValidatedFile(keyManager,
                                    amiiboList.get(x).getFilePath()), keyManager, x);
                        }
                        Intent write = new Intent(TagMo.ACTION_NFC_SCANNED);
                        write.putExtra(TagMo.EXTRA_BANK_COUNT, write_count);
                        args.putStringArrayList(TagMo.EXTRA_AMIIBO_LIST,
                                TagReader.readTagTitles(mifare, bank_count));
                        setResult(Activity.RESULT_OK,  write.putExtras(args));
                        break;

                    case TagMo.ACTION_BACKUP_AMIIBO:
                        Intent backup = new Intent(TagMo.ACTION_NFC_SCANNED);
                        if (commandIntent.hasExtra(TagMo.EXTRA_CURRENT_BANK)) {
                            args.putByteArray(TagMo.EXTRA_TAG_DATA,
                                    TagReader.scanBankToBytes(mifare, selection));
                            backup.putExtra(TagMo.EXTRA_CURRENT_BANK, selection);
                        } else {
                            args.putByteArray(TagMo.EXTRA_TAG_DATA,
                                    TagReader.scanTagToBytes(mifare));
                        }
                        setResult(Activity.RESULT_OK, backup.putExtras(args));
                        break;

                    case TagMo.ACTION_FIX_BANK_DATA:
                        mifare.activateBank(selection);
                        data = TagReader.readFromTag(mifare);
                        mifare.activateBank(active_bank);
                        TagWriter.writeEliteAuto(mifare, data, keyManager, selection);
                        Intent repair = new Intent(TagMo.ACTION_NFC_SCANNED);
                        args.putByteArray(TagMo.EXTRA_TAG_DATA, data);
                        repair.putExtra(TagMo.EXTRA_CURRENT_BANK, selection);
                        setResult(Activity.RESULT_OK, repair.putExtras(args));
                        break;

                    case TagMo.ACTION_SCAN_TAG:
                        Intent result = new Intent(TagMo.ACTION_NFC_SCANNED);
                        if (isEliteDevice) {
                            if (commandIntent.hasExtra(TagMo.EXTRA_CURRENT_BANK)) {
                                try {
                                    data = TagReader.scanBankToBytes(mifare, selection);
                                    data = TagReader.getValidatedData(keyManager, data);
                                    TagUtils.validateNtag(mifare, data, false);
                                } catch (Exception e) {
                                    mifare.activateBank(selection);
                                    data = TagReader.readFromTag(mifare);
                                    mifare.activateBank(active_bank);
                                }
                                args.putByteArray(TagMo.EXTRA_TAG_DATA, data);
                                result.putExtra(TagMo.EXTRA_CURRENT_BANK, selection);
                            } else {
                                ArrayList<String> titles = TagReader.readTagTitles(mifare, bank_count);
                                result.putExtra(TagMo.EXTRA_SIGNATURE,
                                        TagReader.getEliteSignature(mifare));
                                result.putExtra(TagMo.EXTRA_BANK_COUNT, bank_count);
                                result.putExtra(TagMo.EXTRA_ACTIVE_BANK, active_bank);
                                args.putStringArrayList(TagMo.EXTRA_AMIIBO_LIST, titles);
                            }
                        } else {
                            args.putByteArray(TagMo.EXTRA_TAG_DATA,
                                    TagReader.readFromTag(mifare));
                        }
                        setResult(Activity.RESULT_OK, result.putExtras(args));
                        break;

                    case TagMo.ACTION_SET_BANK_COUNT:
                        mifare.setBankCount(write_count);
                        mifare.activateBank(active_bank);
                        ArrayList<String> list = TagReader.readTagTitles(mifare, write_count);
                        Intent configure = new Intent(TagMo.ACTION_NFC_SCANNED);
                        configure.putExtra(TagMo.EXTRA_BANK_COUNT, write_count);
                        args.putStringArrayList(TagMo.EXTRA_AMIIBO_LIST, list);
                        setResult(Activity.RESULT_OK, configure.putExtras(args));
                        break;

                    case TagMo.ACTION_ACTIVATE_BANK:
                        mifare.activateBank(selection);
                        Intent active = new Intent(TagMo.ACTION_NFC_SCANNED);
                        active.putExtra(TagMo.EXTRA_ACTIVE_BANK,
                                TagReader.getEliteDetails(mifare)[0] & 0xFF);
                        setResult(Activity.RESULT_OK, active);
                        break;

                    case TagMo.ACTION_FORMAT_BANK:
                        TagWriter.wipeBankData(mifare, selection);
                        Intent format = new Intent(TagMo.ACTION_NFC_SCANNED);
                        args.putStringArrayList(TagMo.EXTRA_AMIIBO_LIST,
                                TagReader.readTagTitles(mifare, bank_count));
                        setResult(Activity.RESULT_OK, format.putExtras(args));
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
                        throw new Exception(getString(R.string.error_state, mode));
                }
            } finally {
                mifare.close();
            }
            finish();
        } catch (Exception e) {
            Debug.Log(e);
            String error = e.getMessage();
            error = e.getCause() != null ? error + "\n" + e.getCause().toString() : error;
            if (error != null && TagMo.getPrefs().enableEliteSupport().get()) {
                if (e instanceof android.nfc.TagLostException) {
                    txtMessage.setText(R.string.speed_scan);
                    try {
                        mifare.close();
                    } catch (IOException ex) {
                        Debug.Log(ex);
                    }
                    return;
                }
                if (TagMo.getStringRes(R.string.nfc_null_array).equals(error)) {
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
            showError(error);
        }
    }

    ActivityResultLauncher<Intent> onNFCActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> startNfcMonitor()
    );

    void startNfcMonitor() {
        if (nfcAdapter == null) {
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

    void stopNfcMonitor() {
        if (nfcAdapter == null) {
            return;
        }
        nfcAdapter.disableForegroundDispatch(this);
        this.unregisterReceiver(mReceiver);
    }

    void listenForTags() {
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

    @OptionsItem(android.R.id.home)
    void cancelAction() {
        if (mifare != null) {
            try {
                mifare.close();
            } catch (Exception e) {
                Debug.Log(e);
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
