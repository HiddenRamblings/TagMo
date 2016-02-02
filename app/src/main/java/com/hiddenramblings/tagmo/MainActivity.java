package com.hiddenramblings.tagmo;

import android.app.ExpandableListActivity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.NfcA;
import android.os.Environment;
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

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;


@EActivity(R.layout.activity_main)
@OptionsMenu({R.menu.main_menu})
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    public enum NfcMode {
        Idle, Scan, Validate, WriteRaw, WriteAuto
    }

    private static final int FILE_LOAD_DUMP = 0x100;
    private static final int FILE_LOAD_KEYS = 0x101;

    @ViewById(R.id.logMessages)
    EditText logMessages;

    TagFile tagFile;
    KeyManager keyManager;
    NfcAdapter nfcAdapter;
    NfcMode currentMode;

    /*
    void test() {
        Log.d(TAG, "Loading jni3");
        AmiiTool t = new AmiiTool();
        Log.d(TAG, "Loading fixed keys");
        int res = t.setKeysFixed(lockedkey, lockedkey.length);
        Log.d(TAG, "---------result "+ res);
        Log.d(TAG, "Loading unfixed keys");
        res = t.setKeysUnfixed(unfixedkey, unfixedkey.length);
        Log.d(TAG, "---------result "+ res);
        byte[] plainTag = new byte[encryptedTag.length];
        res = t.unpack(encryptedTag, encryptedTag.length, plainTag, plainTag.length);
        Log.d(TAG, "---------result "+ res);
        Log.d(TAG, Util.bytesToHex(plainTag));
        byte[] finalTag = new byte[encryptedTag.length];
        res = t.pack(plainTag, plainTag.length, finalTag, finalTag.length);
        Log.d(TAG, "---------result "+ res);
        Log.d(TAG, Util.bytesToHex(finalTag));
        Log.d(TAG, "Match: " + Arrays.equals(finalTag, encryptedTag));
    }
    */

    @AfterViews
    protected void afterViews() {
        this.tagFile = new TagFile(this);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        currentMode = NfcMode.Idle;
        keyManager = new KeyManager(this);

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
        if (nfcAdapter == null)
        {
            logMessages.append("No NFC support detected!!!!!!!\n");
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
    }

    void stopNfcMonitor() {
        if (nfcAdapter == null)
            return;
        nfcAdapter.disableForegroundDispatch(this);
    }

    @UiThread
    void updateStatus() {
        if (nfcAdapter == null)
        {
            logMessages.append("No NFC support detected!!!!!!!\n");
            return;
        }

        boolean hasfixed = this.keyManager.hasFixedKey();
        boolean hasunfixed = this.keyManager.hasUnFixedKey();

        logMessages.append("Have retail locked keys: " + hasfixed + "\n");
        logMessages.append("Have retail unfixed keys: " + hasunfixed + "\n");

        if (!hasfixed || !hasunfixed) {
            logMessages.append("!!Some keys have not been loaded. Both keys required for the auto write feature to function!!\n");
            return;
        }
    }

    @OptionsItem(R.id.mnu_load_tag_file)
    void loadTagFile() {
        showFileChooser("Load encrypted tag file for writing", "*/*", FILE_LOAD_DUMP);
    }

    @OptionsItem(R.id.mnu_load_keys)
    void loadFixedKeysClicked() {
        showFileChooser("Load the key file", "*/*", FILE_LOAD_KEYS);
    }

    @OptionsItem(R.id.mnu_dump_tag)
    void dumpTag() {
        currentMode = NfcMode.Scan;
        LogMessage("Current mode: Scan tag to file.");
        LogMessage("Place phone on tag.");
    }

    @OptionsItem(R.id.mnu_write_tag_raw)
    void writeToTagRaw() {
        currentMode = NfcMode.Idle;

        if (!this.tagFile.isValid()) {
            LogMessage("No valid tag file loaded!");
            return;
        }

        //todo: double check keys
        currentMode = NfcMode.WriteRaw;

        LogMessage("Current mode: NFC Write Raw");
        if (currentMode != NfcMode.Idle)
            LogMessage("Place phone on the (blank) tag.");
    }

    @OptionsItem(R.id.mnu_write_tag_auto)
    void writeToTagAuto() {
        currentMode = NfcMode.Idle;

        if (!this.keyManager.hasFixedKey() || !this.keyManager.hasUnFixedKey()) {
            LogMessage("Key files not loaded. This functionality is unavailable without them!");
            return;
        }

        if (!this.tagFile.isValid()) {
            LogMessage("No valid tag file loaded!");
            return;
        }

        try {
            this.tagFile.decrypt(this.keyManager);
        } catch (Exception e) {
            LogMessage("Failed to decrypt tag. :" + e.getMessage());
            return;
        }

        currentMode = NfcMode.WriteAuto;

        LogMessage("Current mode: NFC Write Auto");
        if (currentMode != NfcMode.Idle)
            LogMessage("Place phone on the (blank) tag.");
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
                    case WriteRaw:
                        TagWriter.writeToTagRaw(mifare, this.tagFile);
                        LogMessage("Done");
                        break;
                    case WriteAuto:
                        TagWriter.writeToTagAuto(mifare, this.tagFile, this.keyManager);
                        LogMessage("Done");
                        break;
                    case Validate:
                        TagWriter.validate(mifare, this.tagFile.tagData);
                        LogMessage("Successfully validated.");
                        break;
                    case Scan:
                        byte[] data = TagWriter.readFromTag(mifare);
                        writeTagToFile(data);
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

    protected  void writeTagToFile(byte[] tagdata) {
        boolean valid = false;
        try {
            TagFile.validateTag(tagdata);
            valid = true;
        } catch (Exception e) {
            LogMessage("Warning tag not valid");
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
            LogMessage("Error writing to file: " + e.getMessage());
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
    void loadKey(Uri uri) {
        try {
            this.keyManager.loadKey(uri);
            updateStatus();
        } catch (Exception e) {
            LogMessage("Error: " + e.getMessage());
        }
    }
}
