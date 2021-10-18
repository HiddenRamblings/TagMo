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
import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hiddenramblings.tagmo.nfc.KeyManager;
import com.hiddenramblings.tagmo.nfc.NTAG215;
import com.hiddenramblings.tagmo.nfc.TagWriter;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.sharedpreferences.Pref;

import java.util.ArrayList;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_nfc)
public class NfcActivity extends AppCompatActivity {
    private static final String TAG = NfcActivity.class.getSimpleName();



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

    @Pref
    Preferences_ prefs;

    NfcAdapter nfcAdapter;
    KeyManager keyManager;

    Animation nfcAnimation;

    private int bank_count;

    @AfterViews
    void afterViews() {
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        this.keyManager = new KeyManager(this);
        if (prefs.enableAmiiqoSupport().get()) {
            bankNumberPicker.setValue(prefs.amiiqoActiveBank().get());
        } else {
            bankTextView.setVisibility(View.GONE);
            bankNumberPicker.setVisibility(View.GONE);
        }
        updateLayout();

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
                if (!this.keyManager.hasBothKeys()) {
                    showError("Keys not loaded");
                    this.nfcAdapter = null;
                    break;
                }
            case TagMo.ACTION_WRITE_TAG_RAW:
            case TagMo.ACTION_CONFIGURE:
            case TagMo.ACTION_GET_DETAILS:
            case TagMo.ACTION_SCAN_TAG:
            case TagMo.ACTION_SCAN_UNIT:
                startNfcMonitor();
                break;
        }
    }

    void updateLayout() {
        Intent commandIntent = this.getIntent();
        String mode = commandIntent.getAction();
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
            case TagMo.ACTION_CONFIGURE:
                bank_count = getIntent().getIntExtra(TagMo.EXTRA_BANK_COUNT, 1);
            case TagMo.ACTION_GET_DETAILS:
                bankTextView.setVisibility(View.GONE);
                bankNumberPicker.setEnabled(false);
                bankNumberPicker.setVisibility(View.GONE);
            case TagMo.ACTION_SCAN_UNIT:
                setTitle(R.string.scan_amiiqo);
                break;
            case TagMo.ACTION_SCAN_TAG:
                setTitle(R.string.scan_tag);
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
            if (prefs.enableAmiiqoSupport().get()) {
                onAmiiqoDiscovered(intent);
            } else {
                this.onTagDiscovered(intent);
            }
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
            TagMo.Debug(TAG, tag.toString());
            NTAG215 mifare = NTAG215.get(tag);
            if (mifare == null) {
                throw new Exception(getString(R.string.tag_type_error));
            }
            mifare.connect();
            setResult(Activity.RESULT_CANCELED);
            try {
                TagMo.Debug(TAG, mode);
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
                        TagWriter.writeToTagAuto(mifare, data, this.keyManager,
                                prefs.enableTagTypeValidation().get(), prefs.enablePowerTagSupport().get());
                        setResult(Activity.RESULT_OK);
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
                        showToast("Done");
                        break;
                    case TagMo.ACTION_SCAN_TAG:
                        data = TagWriter.readFromTag(mifare);
                        Intent result = new Intent(TagMo.ACTION_NFC_SCANNED);
                        result.putExtra(TagMo.EXTRA_TAG_DATA, data);
                        setResult(Activity.RESULT_OK, result);
                        showToast(getString(R.string.done));
                        break;
                    default:
                        throw new Exception(getString(R.string.state_error, mode));
                }
            } finally {
                mifare.close();
            }
            finish();
        } catch (Exception e) {
            TagMo.Error(TAG, R.string.error, e);
            String error = e.getMessage();
            if (e.getCause() != null) {
                error = error + "\n" + e.getCause().toString();
            }
            showError(error);
        }
    }

    @Background
    void onAmiiqoDiscovered(Intent intent) {
        Intent commandIntent = this.getIntent();
        String mode = commandIntent.getAction();
        try {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            TagMo.Debug(TAG, tag.toString());
            NTAG215 mifare = NTAG215.get(tag);
            if (mifare == null) {
                throw new Exception(getString(R.string.tag_type_error));
            }
            mifare.connect();
            if (!mode.equals(TagMo.ACTION_GET_DETAILS) && !mode.equals(TagMo.ACTION_CONFIGURE)) {
                int selection = bankNumberPicker.getValue() - 1;
                if (selection > TagWriter.getAmiiqoBankCount(mifare)) {
                    throw new Exception(getString(R.string.fail_amiiqo_outofbounds));
                }
                mifare.activateAmiiqoBank(selection);
                prefs.amiiqoActiveBank().put(bankNumberPicker.getValue());
            }
            setResult(Activity.RESULT_CANCELED);
            try {
                TagMo.Debug(TAG, mode);
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
                        TagWriter.writeToTagAuto(mifare, data, this.keyManager,
                                prefs.enableTagTypeValidation().get(), prefs.enablePowerTagSupport().get());
                        setResult(Activity.RESULT_OK);
                        showToast(getString(R.string.done));
                        break;
                    case TagMo.ACTION_WRITE_TAG_DATA:
                        data = commandIntent.getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
                        boolean ignoreUid = commandIntent.getBooleanExtra(TagMo.EXTRA_IGNORE_TAG_ID, false);
                        if (data == null) {
                            throw new Exception(getString(R.string.no_data));
                        }
                        TagWriter.restoreTag(mifare, data, ignoreUid,
                                this.keyManager, prefs.enableTagTypeValidation().get());
                        setResult(Activity.RESULT_OK);
                        showToast(getString(R.string.done));
                        break;
                    case TagMo.ACTION_GET_DETAILS:
                        String signature = TagWriter.getAmiiqoSignature(mifare);
                        int bank_details = TagWriter.getAmiiqoBankCount(mifare);
                        Intent details = new Intent(TagMo.ACTION_NFC_SCANNED);
                        details.putExtra(TagMo.EXTRA_SIGNATURE, signature);
                        details.putExtra(TagMo.EXTRA_BANK_COUNT, bank_details);
                        setResult(Activity.RESULT_OK, details);
                        break;
                    case TagMo.ACTION_CONFIGURE:
                        mifare.setAmiiqoBankCount(bank_count);
                        Intent configure = new Intent(TagMo.ACTION_NFC_SCANNED);
                        configure.putExtra(TagMo.EXTRA_BANK_COUNT, bank_count);
                        setResult(Activity.RESULT_OK, configure);
                        break;
                    case TagMo.ACTION_SCAN_TAG:
                        data = TagWriter.readFromTag(mifare);
                        Intent result = new Intent(TagMo.ACTION_NFC_SCANNED);
                        result.putExtra(TagMo.EXTRA_TAG_DATA, data);
                        setResult(Activity.RESULT_OK, result);
                        break;
                    case TagMo.ACTION_SCAN_UNIT:
                        int count = TagWriter.getAmiiqoBankCount(mifare);
                        ArrayList<byte[]> tags = TagWriter.readFromTags(mifare, count);
                        Intent results = new Intent(TagMo.ACTION_NFC_SCANNED);
                        results.putExtra(TagMo.EXTRA_UNIT_DATA, tags);
                        setResult(Activity.RESULT_OK, results);
                        break;
                    default:
                        throw new Exception(getString(R.string.state_error, mode));
                }
            } finally {
                mifare.close();
            }
            finish();
        } catch (Exception e) {
            TagMo.Error(TAG, R.string.error, e);
            String error = e.getMessage();
            if (e.getCause() != null) {
                error = error + "\n" + e.getCause().toString();
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
}
