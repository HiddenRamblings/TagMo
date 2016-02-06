package com.hiddenramblings.tagmo;

import android.app.DialogFragment;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.IdentityHashMap;


@EActivity(R.layout.activity_main)
@OptionsMenu({R.menu.main_menu})
public class MainActivity extends AppCompatActivity /* implements TagCreateDialog.TagCreateListener */ {
    private static final String TAG = "MainActivity";

    public enum NfcMode {
        Idle, SaveTag, RestoreTag , WriteRaw, WriteAuto, CreateTag
    }

    private static final int FILE_LOAD_DUMP = 0x100;
    private static final int FILE_LOAD_KEYS = 0x101;

    @ViewById(R.id.webView)
    WebView webView;
    @ViewById(R.id.txtLockedKey)
    TextView txtLockedKey;
    @ViewById(R.id.txtUnfixedKey)
    TextView txtUnfixedKey;
    @ViewById(R.id.txtNFC)
    TextView txtNFC;
    @ViewById(R.id.btnSaveTag)
    Button btnSaveTag;
    /*
    @ViewById(R.id.btnCreateTag)
    Button btnCreateTag; */
    @ViewById(R.id.btnLoadTag)
    Button btnLoadTag;
    @ViewById(R.id.btnWriteTagAuto)
    Button btnWriteTagAuto;
    @ViewById(R.id.btnWriteTagRaw)
    Button btnWriteTagRaw;
    @ViewById(R.id.btnRestoreTag)
    Button btnRestoreTag;
    @ViewById(R.id.txtStatus)
    TextView txtStatus;

    TagFile tagFile;
    KeyManager keyManager;
    NfcAdapter nfcAdapter;
    NfcMode currentMode;

    void initWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl("file:///android_asset/log.html");
    }

    @AfterViews
    protected void afterViews() {
        initWebView();

        this.tagFile = new TagFile(this);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        keyManager = new KeyManager(this);
        currentMode = NfcMode.Idle;

        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startNfcMonitor();
    }

    @Override
    protected void onPause() {
        stopNfcMonitor();
        super.onPause();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)) {
                updateStatus();
            }
        }
    };

    void startNfcMonitor() {
        if (nfcAdapter == null)
        {
            return;
        }

        Intent intent = new Intent(this.getApplicationContext(), this.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent nfcPendingIntent = PendingIntent.getActivity(this.getApplicationContext(), 0, intent, 0);

        String[][] nfcTechList = new String[][]{};

        IntentFilter filter1 = new IntentFilter();
        filter1.addAction(NfcAdapter.ACTION_TAG_DISCOVERED);

        IntentFilter[] nfcIntentFilter = new IntentFilter[]{filter1};

        nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, nfcIntentFilter, nfcTechList);

        //monitor nfc status
        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        this.registerReceiver(mReceiver, filter);
    }

    void stopNfcMonitor() {
        if (nfcAdapter == null)
            return;
        nfcAdapter.disableForegroundDispatch(this);
        this.unregisterReceiver(mReceiver);
    }

    @UiThread
    void updateStatus() {
        boolean hasNfc = (nfcAdapter != null);
        boolean nfcEnabled = hasNfc && nfcAdapter.isEnabled();
        boolean hasfixed = this.keyManager.hasFixedKey();
        boolean hasunfixed = this.keyManager.hasUnFixedKey();
        boolean hasKeys = hasfixed && hasunfixed;
        boolean hasTag = this.tagFile.isValid();

        if (!hasNfc) {
            txtNFC.setTextColor(Color.RED);
            txtNFC.setText("NFC not supported!");
        } else if (!nfcEnabled) {
            txtNFC.setTextColor(Color.RED);
            txtNFC.setText("NFC not enabled!");
        } else {
            txtNFC.setTextColor(Color.rgb(0x00, 0xAf, 0x00));
            txtNFC.setText("NFC enabled!");
        }

        if (!hasfixed) {
            txtLockedKey.setTextColor(Color.RED);
            txtLockedKey.setText("No Locked key!");
        } else {
            txtLockedKey.setTextColor(Color.rgb(0x00, 0xAf, 0x00));
            txtLockedKey.setText("Locked key OK.");
        }

        if (!hasunfixed) {
            txtUnfixedKey.setTextColor(Color.RED);
            txtUnfixedKey.setText("No Unfixed key!");
        } else {
            txtUnfixedKey.setTextColor(Color.rgb(0x00, 0xAf, 0x00));
            txtUnfixedKey.setText("Unfixed key OK.");
        }

        switch (currentMode) {
            case Idle: txtStatus.setText("Mode: Idle"); break;
            case CreateTag: txtStatus.setText("Mode: Create"); break;
            case WriteRaw: txtStatus.setText("Mode: Write (Raw)"); break;
            case WriteAuto: txtStatus.setText("Mode: Write (Auto)"); break;
            case SaveTag: txtStatus.setText("Mode: Save"); break;
            case RestoreTag: txtStatus.setText("Mode: Restore"); break;
        }

        btnSaveTag.setEnabled(nfcEnabled);
        btnWriteTagAuto.setEnabled(nfcEnabled && hasKeys && hasTag);
        btnWriteTagRaw.setEnabled(nfcEnabled && hasTag);
        //btnCreateTag.setEnabled(nfcEnabled && hasKeys);
        btnRestoreTag.setEnabled(nfcEnabled && hasTag);

        if (!hasKeys) {
            LogError("Not all keys loaded. Load keys using the menu.");
        }
    }

    @Click(R.id.btnLoadTag)
    void loadTagFile() {
        currentMode = NfcMode.Idle;
        updateStatus();
        showFileChooser("Load encrypted tag file for writing", "*/*", FILE_LOAD_DUMP);
    }

    @OptionsItem(R.id.mnu_load_keys)
    void loadKeysClicked() {
        currentMode = NfcMode.Idle;
        updateStatus();
        showFileChooser("Load key file", "*/*", FILE_LOAD_KEYS);
    }

    @Click(R.id.btnSaveTag)
    void dumpTag() {
        currentMode = NfcMode.SaveTag;
        updateStatus();
        LogMessage("Place phone on valid tag.");
    }

    @Click(R.id.btnWriteTagRaw)
    void writeToTagRaw() {
        currentMode = NfcMode.Idle;

        if (!this.tagFile.isValid()) {
            LogError("No valid tag file loaded!");
        } else {
            currentMode = NfcMode.WriteRaw;
            LogMessage("Place phone on (blank) tag.");
        }
        updateStatus();
    }

    @Click(R.id.btnRestoreTag)
    void restoreTag() {
        currentMode = NfcMode.Idle;

        if (!this.tagFile.isValid()) {
            LogError("No valid tag file loaded!");
        } else {
            currentMode = NfcMode.RestoreTag;
            LogMessage("Place phone on valid tag. (Caution this option may brick your tag).");
        }
        updateStatus();
    }

    @Click(R.id.btnWriteTagAuto)
    void writeToTagAuto() {
        currentMode = NfcMode.Idle;

        try {

            if (!this.keyManager.hasFixedKey() || !this.keyManager.hasUnFixedKey()) {
                LogError("Key files not loaded. This functionality is unavailable without them!");
                return;
            }

            if (!this.tagFile.isValid()) {
                LogError("No valid tag file loaded!");
                return;
            }

            try {
                this.tagFile.decrypt(this.keyManager);
            } catch (Exception e) {
                LogError("Failed to decrypt tag. :" + e.getMessage());
                return;
            }

            LogMessage("Place phone on the (blank) tag.");
            currentMode = NfcMode.WriteAuto;
        } finally {
            updateStatus();
        }
    }

    /*
    Does not work.
    @Click(R.id.btnCreateTag)
    void createTag() {
        currentMode = NfcMode.Idle;
        updateStatus();

        DialogFragment dialog = new TagCreateDialog();
        dialog.show(getFragmentManager(), "TagCreateDialog");
    }

    @Override
    public void onTagCreateDialogConfirm(DialogFragment dialog, byte[] parameters) {
        Log.d(TAG, Util.bytesToHex(parameters));
        this.buildTag(parameters);
    }

    @Background
    void buildTag(byte[] parameters) {
        try {
            this.tagFile.buildTag(parameters, keyManager);
            this.currentMode = NfcMode.CreateTag;
            updateStatus();
            LogMessage("Place phone on valid tag. (Caution this option may brick your tag).");
        } catch (Exception e) {
            LogError("Error:", e);
        }
    }
    */


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.getAction().equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
            LogMessage("Tag detected..");
            this.onTagDiscovered(intent);
        }
    }

    @Background
    void onTagDiscovered(Intent intent) {
        NfcMode mode = this.currentMode;

        try {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            Log.d(TAG, tag.toString());
            if (tag == null)
                throw new Exception("Error getting tag data");
            MifareUltralight mifare = MifareUltralight.get(tag);
            if (mifare == null)
                throw new Exception("Error getting tag data. Possibly not a NTAG215");
            mifare.connect();
            try {
                Log.d(TAG, mode.toString());
                switch (mode) {
                    case WriteRaw:
                        TagWriter.writeToTagRaw(mifare, this.tagFile);
                        LogMessage("Done");
                        break;
                    case WriteAuto:
                        TagWriter.writeToTagAuto(mifare, this.tagFile, this.keyManager);
                        LogMessage("Done");
                        break;
                    case RestoreTag:
                        //TagWriter.validate(mifare, this.tagFile.tagData);
                        TagWriter.restoreTag(mifare, this.tagFile.tagData);
                        LogMessage("Done");
                        break;
                    case CreateTag:
                        TagWriter.writeToTagAuto(mifare, this.tagFile, this.keyManager);
                        LogMessage("Done");
                        break;
                    case SaveTag:
                        byte[] data = TagWriter.readFromTag(mifare);
                        writeTagToFile(data);
                        break;
                    default:
                        LogMessage("No action selected. Select an action.");
                }
            } finally {
                try {
                    mifare.close();
                } catch (Exception e) {
                    LogError("Error closing tag: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LogError("Error: " + e.getMessage());
            if (e.getCause() != null)
                LogError(e.getCause().toString());
        }
        currentMode = NfcMode.Idle;
        updateStatus();
    }

    protected  void writeTagToFile(byte[] tagdata) {
        boolean valid = false;
        try {
            TagFile.validateTag(tagdata);
            valid = true;
        } catch (Exception e) {
            LogError("Warning tag not valid");
        }

        try {
            byte[] uid = Arrays.copyOfRange(tagdata, 0, 9);
            String uids = Util.bytesToHex(uid);
            String fname = "tagmo_" + uids + (valid ? "" : "_corrupted_") + ".bin";
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fname);
            Log.d(TAG, file.toString());
            FileOutputStream fos = new FileOutputStream(file);
            try {
                fos.write(tagdata);
            } finally {
                fos.close();
            }
            try {
                MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
            } catch (Exception e) {
                Log.e(TAG, "Failed to refresh media scanner", e);
            }
            LogMessage("Wrote to file " + fname + " in downloads.");
        } catch (Exception e) {
            LogError("Error writing to file: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK)
            return;

        Uri uri = data.getData();
        switch (requestCode) {
            case FILE_LOAD_KEYS:
                loadKey(uri);
                break;
            case FILE_LOAD_DUMP:
                loadDumpFile(uri);
        }
    }

    private void showFileChooser(String title, String mimeType, int resultCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");      //all files
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent, title), resultCode);
        } catch (android.content.ActivityNotFoundException ex) {
            LogError("Failed to show file open dialog. Please install a file manager app.");
            Log.e(TAG, ex.getMessage());
        }
    }

    @Background
    void loadKey(Uri uri) {
        try {
            this.keyManager.loadKey(uri);
        } catch (Exception e) {
            LogMessage("Error: " + e.getMessage());
        }
        updateStatus();
    }

    @Background
    void loadDumpFile(Uri uri) {
        try {
            this.tagFile.loadFile(uri);
            LogMessage("Loaded tag file.");
        } catch (Exception e) {
            LogError("Error: " + e.getMessage());
        }
        updateStatus();
    }

    @UiThread
    void LogMessage(String msg) {
        webView.loadUrl("javascript:logMsg('" + URLEncoder.encode(msg).replace("+", "%20") + "');");
    }
    @UiThread
    void LogError(String msg, Throwable e) {
        webView.loadUrl("javascript:logErr('" + URLEncoder.encode(msg).replace("+", "%20") + "');");
        if (e != null) {
            webView.loadUrl("javascript:logErr('" + URLEncoder.encode(e.getMessage()).replace("+", "%20") + "');");
        }
    }
    @UiThread
    void LogError(String msg) {
        webView.loadUrl("javascript:logErr('" + URLEncoder.encode(msg).replace("+", "%20") + "');");
    }

}
