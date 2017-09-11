package com.hiddenramblings.tagmo;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.os.Build;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.sharedpreferences.Pref;

@EActivity(R.layout.activity_nfc)
public class NfcActivity extends AppCompatActivity {
    private static final String TAG = "NfcActivity";

    public static final String ACTION_SCAN_TAG = "com.hiddenramblings.tagmo.SCAN_TAG";
    public static final String ACTION_WRITE_TAG_FULL = "com.hiddenramblings.tagmo.WRITE_TAG_FULL";
    public static final String ACTION_WRITE_TAG_RAW = "com.hiddenramblings.tagmo.WRITE_TAG_RAW";
    public static final String ACTION_WRITE_TAG_DATA = "com.hiddenramblings.tagmo.WRITE_TAG_DATA";

    public static final String ACTION_NFC_SCANNED = "com.hiddenramblings.tagmo.NFC_SCANNED";

    public static final String EXTRA_TAG_DATA = "com.hiddenramblings.tagmo.EXTRA_TAG_DATA";
    public static final String EXTRA_IGNORE_TAG_ID = "com.hiddenramblings.tagmo.EXTRA_IGNORE_TAG_ID";

    @ViewById(R.id.txtMessage)
    TextView txtMessage;
    @ViewById(R.id.txtError)
    TextView txtError;

    @ViewById(R.id.imgNfcBar)
    ImageView imgNfcBar;
    @ViewById(R.id.imgNfcCircle)
    ImageView imgNfcCircle;

    @Pref
    Preferences_ prefs;

    NfcAdapter nfcAdapter;
    KeyManager keyManager;

    Animation nfcAnimation;

    @AfterViews
    void afterViews() {
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        this.keyManager = new KeyManager(this);
        updateTitle();

        nfcAnimation = AnimationUtils.loadAnimation(this, R.anim.nfc_scanning);
    }

    @Override
    protected void onResume() {
        super.onResume();
        clearError();
        String action = getIntent().getAction();
        if (!this.keyManager.hasBothKeys() && (ACTION_WRITE_TAG_FULL.equals(action) || ACTION_WRITE_TAG_DATA.equals(action))) {
            showError("Keys not loaded");
            this.nfcAdapter = null;
        } else
            startNfcMonitor();
    }

    void updateTitle() {
        Intent commandIntent = this.getIntent();
        String mode = commandIntent.getAction();
        switch (mode) {
            case ACTION_WRITE_TAG_RAW:
                setTitle("Write to Tag (Raw)");
                break;
            case ACTION_WRITE_TAG_FULL:
                setTitle("Write to Tag (Auto)");
                showToast("Done");
                break;
            case ACTION_WRITE_TAG_DATA:
                setTitle("Update Data on Tag");
                break;
            case ACTION_SCAN_TAG:
                setTitle("Scan Tag");
                break;
            default:
                setTitle("Error");
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
            showMessage("Tag detected..");
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
            Log.d(TAG, tag.toString());
            MifareUltralight mifare = MifareUltralight.get(tag);
            if (mifare == null)
                throw new Exception("Error getting tag data. Possibly not a NTAG215");
            mifare.connect();
            Intent result = null;
            int resultCode = Activity.RESULT_CANCELED;
            try {
                Log.d(TAG, mode);
                byte[] data;
                switch (mode) {
                    case ACTION_WRITE_TAG_RAW:
                        data = commandIntent.getByteArrayExtra(EXTRA_TAG_DATA);
                        if (data == null)
                            throw new Exception("No data to write");
                        TagWriter.writeToTagRaw(mifare, data, prefs.enableTagTypeValidation().get());
                        resultCode = Activity.RESULT_OK;
                        showToast("Done");
                        break;
                    case ACTION_WRITE_TAG_FULL:
                        data = commandIntent.getByteArrayExtra(EXTRA_TAG_DATA);
                        if (data == null)
                            throw new Exception("No data to write");
                        TagWriter.writeToTagAuto(mifare, data, this.keyManager, prefs.enableTagTypeValidation().get(), prefs.enablePowerTagSupport().get());
                        resultCode = Activity.RESULT_OK;
                        showToast("Done");
                        break;
                    case ACTION_WRITE_TAG_DATA:
                        data = commandIntent.getByteArrayExtra(EXTRA_TAG_DATA);
                        boolean ignoreUid = commandIntent.getBooleanExtra(EXTRA_IGNORE_TAG_ID, false);
                        if (data == null)
                            throw new Exception("No data to write");
                        TagWriter.restoreTag(mifare, data, ignoreUid, this.keyManager, prefs.enableTagTypeValidation().get());
                        resultCode = Activity.RESULT_OK;
                        showToast("Done");
                        break;
                    case ACTION_SCAN_TAG:
                        data = TagWriter.readFromTag(mifare);
                        resultCode = Activity.RESULT_OK;
                        result = new Intent(ACTION_NFC_SCANNED);
                        result.putExtra(EXTRA_TAG_DATA, data);
                        showToast("Done");
                        break;
                    default:
                        throw new Exception("State error. Invalid action:" + mode);
                }
            } finally {
                try {
                    mifare.close();
                } catch (Exception e) {
                    Log.d(TAG, "Error closing tag", e);
                    throw new Exception("Error closing tag: " + e.getMessage());
                }
            }
            finishActivityWithResult(resultCode, result);
        } catch (Exception e) {
            Log.d(TAG, "Error", e);
            String error = e.getMessage();
            if (e.getCause() != null)
                error = error + "\n" + e.getCause().toString();
            showError(error);
        }
    }

    @UiThread
    void finishActivityWithResult(int resultcode, Intent resultIntent) {
        setResult(resultcode, resultIntent);
        finish();
    }

    @UiThread
    void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    void startNfcMonitor() {
        if (nfcAdapter == null)
        {
            showError("NFC support not detected.");
            return;
        }

        if (!nfcAdapter.isEnabled()) {
            showError("NFC Disabled.");
            new AlertDialog.Builder(this)
                .setMessage("NFC adapter is currently disabled. Enable NFC?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
                            startActivity(intent);
                        } else {
                            Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                            startActivity(intent);
                        }
                    }
                })
                .setNegativeButton("No", null)
                .show();
        }

        //monitor nfc status
        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        this.registerReceiver(mReceiver, filter);
        listenForTags();
    }

    void stopNfcMonitor() {
        if (nfcAdapter == null)
            return;
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
                    showError("NFC Disabled!");
                } else {
                    listenForTags();
                    clearError();
                }
            }
        }
    };


}
