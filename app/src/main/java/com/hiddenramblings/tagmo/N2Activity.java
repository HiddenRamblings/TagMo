package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.smartrac.nfc.NfcNtag;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.sharedpreferences.Pref;

import java.io.IOException;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_nfc)
public class N2Activity extends AppCompatActivity {
    private static final String TAG = N2Activity.class.getSimpleName();

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
        } else {
            startNfcMonitor();
        }
    }

    void updateTitle() {
        Intent commandIntent = this.getIntent();
        String mode = commandIntent.getAction();
        switch (mode) {
            case ACTION_WRITE_TAG_RAW:
                setTitle(R.string.write_raw);
                break;
            case ACTION_WRITE_TAG_FULL:
                setTitle(R.string.write_auto);
                break;
            case ACTION_WRITE_TAG_DATA:
                setTitle(R.string.restore_tag);
                break;
            case ACTION_SCAN_TAG:
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

        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction()) ||
                NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction()) ||
                NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
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

        // Retrieve Tag ID
        byte[] uid = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
        // Retrieve Tag object
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag != null) {
            // Check if the tag supports NfcA/ISO14443A
//                boolean bIso14443A = false;
            String[] sTech = tag.getTechList();
//                while (int i<sTech.length && ! bIso14443A) {
//                    if (sTech[i].equals("android.nfc.tech.NfcA")) {
//                        bIso14443A = true;
//                    }
//                }
//                if (bIso14443A)
//                {
//                    // Do actions on the NTAG
//                    try {
//                        NfcNtag ntag = NfcNtag.get(tag);
//                        ntag.connect();
//                        // read user memory blocks 4 to 15
//                        byte[] data = ntag.fastRead(0x04, 0x0F);
//                        ntag.close();
//                    }
//                    catch (IOException e) {
//                        // handle NFC comm error...
//                    }
//                }
            for (String tech : sTech) {
                if (tech.equals("android.nfc.tech.NfcA")) {
                    // Do actions on the NTAG
                    try {
                        NfcNtag ntag = NfcNtag.get(tag);
                        ntag.connect();
                        // read user memory blocks 4 to 15
                        byte[] data = ntag.fastRead(0x04, 0x0F);
                        ntag.close();
                    }
                    catch (IOException e) {
                        // handle NFC comm error...
                    }
                    break;
                }
            }

            try {
                TagMo.Debug(TAG, tag.toString());
                NfcNtag mifare = NfcNtag.get(tag);
                mifare.connect();
                setResult(Activity.RESULT_CANCELED);
                try {
                    TagMo.Debug(TAG, mode);
                    byte[] data;
                    switch (mode) {
                        case ACTION_WRITE_TAG_RAW:
                            data = commandIntent.getByteArrayExtra(EXTRA_TAG_DATA);
                            if (data == null) {
                                throw new Exception(getString(R.string.no_data));
                            }
                            mifare.amiiboWrite(0, 0, data);
                            setResult(Activity.RESULT_OK);
                            showToast(getString(R.string.done));
                            break;
                        case ACTION_WRITE_TAG_FULL:
                            data = commandIntent.getByteArrayExtra(EXTRA_TAG_DATA);
                            if (data == null) {
                                throw new Exception(getString(R.string.no_data));
                            }
                            mifare.amiiboFastWrite(0, 0, data);
                            setResult(Activity.RESULT_OK);
                            showToast(getString(R.string.done));
                            break;
                        case ACTION_WRITE_TAG_DATA:
                            data = commandIntent.getByteArrayExtra(EXTRA_TAG_DATA);
                            boolean ignoreUid = commandIntent.getBooleanExtra(EXTRA_IGNORE_TAG_ID, false);
                            if (data == null) {
                                throw new Exception(getString(R.string.no_data));
                            }
                            mifare.amiiboFastWrite(0, 0, data);
                            setResult(Activity.RESULT_OK);
                            showToast("Done");
                            break;
                        case ACTION_SCAN_TAG:
                            data = mifare.amiiboFastRead(0,0,0);
                            Intent result = new Intent(ACTION_NFC_SCANNED);
                            result.putExtra(EXTRA_TAG_DATA, data);
                            setResult(Activity.RESULT_OK, result);
                            showToast(getString(R.string.done));
                            break;
                        default:
                            throw new Exception(getString(R.string.state_error, mode));
                    }
                } finally {
                    try {
                        mifare.close();
                    } catch (Exception e) {
                        TagMo.Error(TAG, R.string.tag_close_error, e);
                        throw new Exception(getString(R.string.tag_close_error, ":" + e.getMessage()));
                    }
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
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
//                            Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
//                            startActivity(intent);
//                        } else {
                            Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                            startActivity(intent);
//                        }
                        }
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();
        }

        //monitor nfc status
        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        this.registerReceiver(mReceiver, filter);
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
