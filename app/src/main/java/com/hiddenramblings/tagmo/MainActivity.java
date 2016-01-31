package com.hiddenramblings.tagmo;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.NfcA;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.androidannotations.annotations.AfterInject;
import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;


@EActivity(R.layout.activity_main)
@OptionsMenu({R.menu.main_menu})
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    public enum NfcMode {
        Idle, Scan, Validate, Write
    }

    private static final int FILE_LOAD_DUMP = 0x100;
    private static final int FILE_LOAD_FIXED_KEYS = 0x101;
    private static final int FILE_LOAD_UNFIXED_KEYS = 0x102;

    @ViewById(R.id.logMessages)
    EditText logMessages;

    TagFile tagFile;
    NfcAdapter nfcAdapter;
    NfcMode currentMode;

    @AfterViews
    protected void afterViews() {
        this.tagFile = new TagFile(this);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        currentMode = NfcMode.Idle;

        updateStatus();
        logMessages.append("Ready! Choose an action from menu.\n");
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

    void startNfcMonitor() {
        Intent intent = new Intent(this.getApplicationContext(), this.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent nfcPendingIntent = PendingIntent.getActivity(this.getApplicationContext(), 0, intent, 0);

        String[][] nfcTechList = new String[][]{};

        IntentFilter filter1 = new IntentFilter();
        filter1.addAction(NfcAdapter.ACTION_TAG_DISCOVERED);

        IntentFilter[] nfcIntentFilter = new IntentFilter[]{filter1};

        nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, nfcIntentFilter, nfcTechList);
    }

    void stopNfcMonitor() {
        nfcAdapter.disableForegroundDispatch(this);
    }

    void updateStatus() {
        if (nfcAdapter == null)
        {
            logMessages.append("No NFC support detected!!!!!!!\n");
            return;
        }

        KeyManager km = new KeyManager(this);
        boolean hasfixed = km.hasFixedKey();
        boolean hasunfixed = km.hasUnFixedKey();
        /*

        logMessages.append("Have retail locked keys: " + hasfixed + "\n");
        logMessages.append("Have retail unfixed keys: " + hasunfixed + "\n");

        if (!hasfixed || !hasunfixed) {
            logMessages.append("!!Some keys have not been loaded. Both keys required for the application to function!!\n");
            return;
        }
        */
    }

    @OptionsItem(R.id.mnu_load_tag_file)
    void loadTagFile() {
        showFileChooser("Load encrypted tag file for writing", "*/*", FILE_LOAD_DUMP);
    }

    @OptionsItem(R.id.mnu_load_fixed_keys)
    void loadFixedKeysClicked() {
        showFileChooser("Load the fixed key file", "*/*", FILE_LOAD_FIXED_KEYS);
    }

    @OptionsItem(R.id.mnu_load_unfixed_keys)
    void loadUnFixedKeysClicked() {
        showFileChooser("Load the unfixed key file", "*/*", FILE_LOAD_UNFIXED_KEYS);
    }

    @OptionsItem(R.id.mnu_write_tag)
    void writeToTag() {
        currentMode = NfcMode.Idle;

        if (!this.tagFile.isValid()) {
            LogMessage("No valid tag file loaded!");
            return;
        }

        //todo: double check keys
        currentMode = NfcMode.Write;

        LogMessage("Current mode: NFC Write");
        if (currentMode != NfcMode.Idle)
            LogMessage("Place phone on the tage");
    }

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
                    case Write:
                        this.tagFile.writeToTag(mifare);
                        LogMessage("Done");
                        break;
                    case Validate:
                        this.tagFile.validate(mifare);
                        LogMessage("Successfully validated.");
                        break;
                    case Scan:
                        this.tagFile.readFromTag(mifare);
                        break;
                    default:
                        LogMessage("No action selected. Select an action from menu.");
                }
            } finally {
                try {
                    mifare.close();
                } catch (Exception e) {
                    LogMessage("Error closing tag: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LogMessage("Error: " + e.getMessage());
            if (e.getCause() != null)
                LogMessage(e.getCause().toString());
            currentMode = NfcMode.Idle;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK)
            return;

        Uri uri = data.getData();
        switch (requestCode) {
            case FILE_LOAD_FIXED_KEYS:
            case FILE_LOAD_UNFIXED_KEYS:
                loadKey(requestCode, uri);
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
            logMessages.append("Failed to show file open dialog. Please install a File Manager.\n");
            Log.e(TAG, ex.getMessage());
        }
    }

    @Background
    void loadDumpFile(Uri uri) {
        try {
            this.tagFile.loadFile(uri);
            LogMessage("Loaded tag file.");
        } catch (Exception e) {
            LogMessage("Error: " + e.getMessage());
        }
    }

    @UiThread void LogMessage(String msg) {
        logMessages.append(msg);
        logMessages.append("\n");
    }

    @Background
    void loadKey(int type, Uri uri) {
        KeyManager km = new KeyManager(this);
        boolean success = false;
        switch (type) {
            case FILE_LOAD_FIXED_KEYS:
                success = km.loadFixedKey(uri);
                afterKeyLoad(success);
                break;
            case FILE_LOAD_UNFIXED_KEYS:
                success = km.loadUnfixedKey(uri);
                afterKeyLoad(success);
                break;
        }
    }

    @UiThread
    void afterKeyLoad(boolean success) {
        if (!success)
            logMessages.append("Failed to load key file. Make sure the file is valid.\n");
        else
            updateStatus();
    }
}
