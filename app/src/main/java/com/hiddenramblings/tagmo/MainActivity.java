package com.hiddenramblings.tagmo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.sharedpreferences.Pref;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;


@EActivity(R.layout.activity_main)
@OptionsMenu({R.menu.main_menu})
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final int FILE_LOAD_TAG = 0x100;
    private static final int NFC_ACTIVITY = 0x102;
    private static final int EDIT_TAG = 0x103;
    private static final int SCAN_QR_CODE = 0x104;

    @ViewById(R.id.txtLockedKey)
    TextView txtLockedKey;
    @ViewById(R.id.txtUnfixedKey)
    TextView txtUnfixedKey;
    @ViewById(R.id.txtNFC)
    TextView txtNFC;

    @ViewById(R.id.txtTagInfo)
    TextView txtTagInfo;
    @ViewById(R.id.txtTagId)
    TextView txtTagId;
    @ViewById(R.id.txtName)
    TextView txtName;
    @ViewById(R.id.txtGameSeries)
    TextView txtGameSeries;
    @ViewById(R.id.txtCharacter)
    TextView txtCharacter;
    @ViewById(R.id.txtAmiiboType)
    TextView txtAmiiboType;
    @ViewById(R.id.txtAmiiboSeries)
    TextView txtAmiiboSeries;

    @ViewById(R.id.btnSaveTag)
    Button btnSaveTag;
    @ViewById(R.id.btnLoadTag)
    Button btnLoadTag;
    @ViewById(R.id.btnWriteTagAuto)
    Button btnWriteTagAuto;
    @ViewById(R.id.btnWriteTagRaw)
    Button btnWriteTagRaw;
    @ViewById(R.id.btnRestoreTag)
    Button btnRestoreTag;
    @ViewById(R.id.btnScanQRCode)
    Button btnScanQRCode;
    @ViewById(R.id.btnShowQRCode)
    Button btnShowQRCode;
    @ViewById(R.id.btnEditDataSSB)
    Button btnEditDataSSB;
    @ViewById(R.id.btnEditDataTP)
    Button btnEditDataTP;
    @ViewById(R.id.btnViewHex)
    Button btnViewHex;

    @ViewById(R.id.cbAutoSaveOnScan)
    CheckBox cbAutoSaveOnScan;
    @ViewById(R.id.cbNoIDValidate)
    CheckBox cbNoIDValidate;

    @InstanceState
    byte[] currentTagData;
    KeyManager keyManager;
    NfcAdapter nfcAdapter;

    AmiiboManager amiiboManager = null;

    @Pref
    Preferences_ prefs;

    boolean keyWarningShown;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.verifyStoragePermissions();
    }

    @AfterViews
    protected void afterViews() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        startNfcMonitor();
        keyManager = new KeyManager(this);
        this.loadAmiiboManager();
        updateStatus();
    }

    @Override
    protected void onPause() {
        stopNfcMonitor();
        super.onPause();
    }

    @Background
    void loadAmiiboManager() {
        AmiiboManager amiiboManager = null;
        try {
            amiiboManager = Util.loadAmiiboManager(this);
        } catch (IOException | JSONException | ParseException e) {
            e.printStackTrace();
            showToast("Unable to parse amiibo database");
        }

        setAmiiboManager(amiiboManager);
    }

    @UiThread
    void setAmiiboManager(AmiiboManager amiiboManager) {
        this.amiiboManager = amiiboManager;
        this.updateStatus();
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
            return;
        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        this.registerReceiver(mReceiver, filter);
    }

    void stopNfcMonitor() {
        if (nfcAdapter == null)
            return;
        this.unregisterReceiver(mReceiver);
    }

    @UiThread
    void updateStatus() {
        boolean hasNfc = (nfcAdapter != null);
        boolean nfcEnabled = hasNfc && nfcAdapter.isEnabled();
        boolean hasFixed = this.keyManager.hasFixedKey();
        boolean hasUnfixed = this.keyManager.hasUnFixedKey();
        boolean hasKeys = hasFixed && hasUnfixed;
        boolean hasTag = currentTagData != null;

        if (!hasNfc) {
            txtNFC.setTextColor(Color.RED);
            txtNFC.setText(R.string.nfc_unsupported);
        } else if (!nfcEnabled) {
            txtNFC.setTextColor(Color.RED);
            txtNFC.setText(R.string.nfc_disabled);
        } else {
            txtNFC.setTextColor(Color.rgb(0x00, 0xAf, 0x00));
            txtNFC.setText(R.string.nfc_enabled);
        }

        if (hasFixed) {
            txtLockedKey.setTextColor(Color.rgb(0x00, 0xAf, 0x00));
            txtLockedKey.setText(R.string.fixed_key_found);
        } else {
            txtLockedKey.setTextColor(Color.RED);
            txtLockedKey.setText(R.string.fixed_key_missing);
        }

        if (hasUnfixed) {
            txtUnfixedKey.setTextColor(Color.rgb(0x00, 0xAf, 0x00));
            txtUnfixedKey.setText(R.string.unfixed_key_found);
        } else {
            txtUnfixedKey.setTextColor(Color.RED);
            txtUnfixedKey.setText(R.string.unfixed_key_missing);
        }

        btnWriteTagAuto.setEnabled(nfcEnabled && hasKeys && hasTag);
        btnWriteTagRaw.setEnabled(nfcEnabled && hasTag);
        btnRestoreTag.setEnabled(nfcEnabled && hasTag);
        btnSaveTag.setEnabled(nfcEnabled && hasTag);
        btnShowQRCode.setEnabled(hasTag);
        btnEditDataSSB.setEnabled(hasKeys && hasTag);
        btnViewHex.setEnabled(hasKeys && hasTag);

        if (!hasKeys && !keyWarningShown) {
            new AlertDialog.Builder(this)
                .setMessage("To read and write Amiibo tags, encryption keys are required. Would you like to open settings to import them?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openSettings();
                    }
                })
                .setNegativeButton("No", null)
                .show();
            keyWarningShown = true;
        }

        String tagInfo = "";
        String amiiboHexId = "";
        String amiiboName = "";
        String amiiboSeries = "";
        String amiiboType = "";
        String gameSeries = "";
        String character = "";
        int ssbVisibility = View.INVISIBLE;
        int tpVisibility = View.INVISIBLE;

        if (this.currentTagData == null) {
            tagInfo = "<No tag loaded>";
        } else {
            try {
                long amiiboId = TagUtil.amiiboIdFromTag(this.currentTagData);
                Amiibo amiibo = null;
                if (this.amiiboManager != null) {
                    amiibo = amiiboManager.amiibos.get(amiiboId);
                    if (amiibo == null)
                        amiibo = new Amiibo(amiiboManager, amiiboId, null, null);
                }
                if (amiibo != null) {
                    amiiboHexId = TagUtil.amiiboIdToHex(amiibo.id);
                    if (amiibo.name != null)
                        amiiboName = amiibo.name;
                    if (amiibo.getAmiiboSeries() != null)
                        amiiboSeries = amiibo.getAmiiboSeries().name;
                    if (amiibo.getAmiiboType() != null)
                        amiiboType = amiibo.getAmiiboType().name;
                    if (amiibo.getGameSeries() != null)
                        gameSeries = amiibo.getGameSeries().name;
                    if (amiibo.getCharacter() != null)
                        character = amiibo.getCharacter().name;

                    switch (amiibo.getHead()) {
                        case EditorTP.WOLF_LINK_ID:
                            tpVisibility = View.VISIBLE;
                            break;
                        default:
                            ssbVisibility = View.VISIBLE;
                            break;
                    }
                } else {
                    tagInfo = "<Unknown amiibo id: " + TagUtil.amiiboIdToHex(amiiboId) + ">";
                }
            } catch (Exception e) {
                e.printStackTrace();
                tagInfo = "<Error reading tag>";
            }
        }

        txtTagInfo.setText(tagInfo);
        setAmiiboInfoText(txtName, amiiboName, !tagInfo.isEmpty());
        setAmiiboInfoText(txtTagId, amiiboHexId, !tagInfo.isEmpty());
        setAmiiboInfoText(txtAmiiboSeries, amiiboSeries, !tagInfo.isEmpty());
        setAmiiboInfoText(txtAmiiboType, amiiboType, !tagInfo.isEmpty());
        setAmiiboInfoText(txtGameSeries, gameSeries, !tagInfo.isEmpty());
        setAmiiboInfoText(txtCharacter, character, !tagInfo.isEmpty());
        btnEditDataSSB.setVisibility(ssbVisibility);
        btnEditDataTP.setVisibility(tpVisibility);
    }

    void setAmiiboInfoText(TextView textView, CharSequence text, boolean hasTagInfo) {
        if (hasTagInfo) {
            textView.setText("");
        } else if (text.length() == 0) {
            textView.setText("Unknown");
            textView.setTextColor(Color.RED);
        } else {
            textView.setText(text);
            textView.setTextColor(textView.getTextColors().getDefaultColor());
        }
    }

    @Click(R.id.btnLoadTag)
    void loadTagFile() {
        if (prefs.enableAmiiboBrowser().get()) {
            Intent intent = new Intent(this, BrowserActivity_.class);
            startActivityForResult(intent, FILE_LOAD_TAG);
        } else {
            showFileChooser("Load encrypted tag file for writing", "*/*", FILE_LOAD_TAG);
        }
    }

    @OptionsItem(R.id.mnu_dump_logcat)
    void dumpLogcatClicked() {
        dumpLogCat();
    }

    @OptionsItem(R.id.settings)
    void openSettings() {
        Intent i = new Intent(this, SettingsActivity_.class);
        startActivity(i);
    }

    @Click(R.id.btnScanTag)
    void scanTag() {
        Intent intent = new Intent(this, NfcActivity_.class);
        intent.setAction(NfcActivity.ACTION_SCAN_TAG);
        startActivityForResult(intent, NFC_ACTIVITY);
    }

    @Click(R.id.btnWriteTagRaw)
    void writeToTagRaw() {
        if (this.currentTagData == null) {
            LogError("No tag loaded");
            return;
        }
        Intent intent = new Intent(this, NfcActivity_.class);
        intent.setAction(NfcActivity.ACTION_WRITE_TAG_RAW);
        intent.putExtra(NfcActivity.EXTRA_TAG_DATA, this.currentTagData);
        startActivityForResult(intent, NFC_ACTIVITY);
    }

    @Click(R.id.btnRestoreTag)
    void restoreTag() {
        if (this.currentTagData == null) {
            LogError("No tag loaded");
            return;
        }
        Intent intent = new Intent(this, NfcActivity_.class);
        intent.setAction(NfcActivity.ACTION_WRITE_TAG_DATA);
        intent.putExtra(NfcActivity.EXTRA_TAG_DATA, this.currentTagData);
        intent.putExtra(NfcActivity.EXTRA_IGNORE_TAG_ID, this.cbNoIDValidate.isChecked());
        startActivityForResult(intent, NFC_ACTIVITY);
    }

    @Click(R.id.btnWriteTagAuto)
    void writeToTagAuto() {
        if (this.currentTagData == null) {
            LogError("No tag loaded");
            return;
        }
        Intent intent = new Intent(this, NfcActivity_.class);
        intent.setAction(NfcActivity.ACTION_WRITE_TAG_FULL);
        intent.putExtra(NfcActivity.EXTRA_TAG_DATA, this.currentTagData);
        startActivityForResult(intent, NFC_ACTIVITY);
    }

    @Click(R.id.btnSaveTag)
    void saveTag() {
        if (this.currentTagData == null) {
            LogError("No tag loaded");
            return;
        }
        writeTagToFile(this.currentTagData);
    }

    @Click(R.id.btnShowQRCode)
    void showQRCode() {
        if (this.currentTagData == null) {
            LogError("No tag loaded");
            return;
        }

        String content;
        try {
            content = new String(this.currentTagData, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            showToast("Failed to encode QR Code");
            return;
        }

        Intent intent = new Intent("com.google.zxing.client.android.ENCODE");
        intent.putExtra("ENCODE_TYPE", "TEXT_TYPE");
        intent.putExtra("ENCODE_SHOW_CONTENTS", false);
        intent.putExtra("ENCODE_DATA", content);
        startQRActivity(intent, -1);
    }

    @Click(R.id.btnScanQRCode)
    void scanQRCode() {
        Intent intent = new Intent("com.google.zxing.client.android.SCAN");
        intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
        intent.putExtra("CHARACTER_SET", "ISO-8859-1");
        startQRActivity(intent, SCAN_QR_CODE);
    }

    void startQRActivity(Intent intent, int resultCode) {
        if (intent.resolveActivity(getPackageManager()) != null) {
            this.startActivityForResult(intent, resultCode);
        } else {
            new AlertDialog.Builder(this)
                .setMessage("Barcode Scanner is required to use QR Codes. Would you like to install it from the Play Store?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Uri marketUri = Uri.parse("market://details?id=com.google.zxing.client.android");
                        Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
                        startActivity(marketIntent);
                    }
                })
                .setNegativeButton("No", null)
                .show();
        }
    }

    void loadQRCode(String content) {
        byte[] data;
        try {
            data = content.getBytes("ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            showToast("Failed to decode QR Code");
            return;
        }
        this.currentTagData = data;
        this.updateStatus();
    }

    @Click(R.id.btnEditDataSSB)
    void editSSBData() {
        if (this.currentTagData == null) {
            LogError("No tag loaded");
            return;
        }
        Intent intent = new Intent(this, EditorSSB_.class);
        intent.setAction(Actions.ACTION_EDIT_DATA);
        intent.putExtra(Actions.EXTRA_TAG_DATA, this.currentTagData);
        startActivityForResult(intent, EDIT_TAG);
    }

    @Click(R.id.btnEditDataTP)
    void editTPData() {
        if (this.currentTagData == null) {
            LogError("No tag loaded");
            return;
        }
        Intent intent = new Intent(this, EditorTP_.class);
        intent.setAction(Actions.ACTION_EDIT_DATA);
        intent.putExtra(Actions.EXTRA_TAG_DATA, this.currentTagData);
        startActivityForResult(intent, EDIT_TAG);
    }

    @Click(R.id.btnViewHex)
    void viewHex() {
        if (this.currentTagData == null) {
            LogError("No tag loaded");
            return;
        }
        Intent intent = new Intent(this, HexViewerActivity_.class);
        intent.setAction(Actions.ACTION_EDIT_DATA);
        intent.putExtra(Actions.EXTRA_TAG_DATA, this.currentTagData);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK)
            return;

        Log.d(TAG, "onActivityResult");

        String action;
        switch (requestCode) {
            case EDIT_TAG:
                if (data == null) return;
                action = data.getAction();
                if (!Actions.ACTION_EDIT_COMPLETE.equals(action))
                    return;
                this.currentTagData = data.getByteArrayExtra(Actions.EXTRA_TAG_DATA);
                this.updateStatus();
                if (this.currentTagData == null) {
                    LogError("Tag data is empty");
                    return;
                }
            case NFC_ACTIVITY:
                if (data == null) return;
                action = data.getAction();
                if (!NfcActivity.ACTION_NFC_SCANNED.equals(action))
                    return;
                this.currentTagData = data.getByteArrayExtra(NfcActivity.EXTRA_TAG_DATA);
                updateStatus();
                if (this.currentTagData == null) {
                    LogError("Tag data is empty");
                    return;
                }
                if (cbAutoSaveOnScan.isChecked())
                    writeTagToFile(this.currentTagData);
                break;
            case FILE_LOAD_TAG:
                loadTagFile(data.getData());
                break;
            case SCAN_QR_CODE:
                loadQRCode(data.getStringExtra("SCAN_RESULT"));
                break;
        }
    }

    private void showFileChooser(String title, String mimeType, int resultCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent, title), resultCode);
        } catch (android.content.ActivityNotFoundException ex) {
            LogError("Failed to show file open dialog. Please install a file manager app.");
            Log.e(TAG, ex.getMessage());
        }
    }

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";
    private static final String WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE";
    private static String[] PERMISSIONS_STORAGE = {
        READ_EXTERNAL_STORAGE,
        WRITE_EXTERNAL_STORAGE
    };

    void verifyStoragePermissions() {
        int permission = ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
        }
    }

    @Background
    void loadTagFile(Uri uri) {
        try {
            this.currentTagData = TagUtil.readTag(getContentResolver().openInputStream(uri));
            showToast("Loaded tag file.");
        } catch (Exception e) {
            LogError("Error: " + e.getMessage());
        }
        updateStatus();
    }

    @Background
    protected void writeTagToFile(byte[] tagdata) {
        boolean valid = false;
        try {
            TagUtil.validateTag(tagdata);
            valid = true;
        } catch (Exception e) {
            LogError("Warning tag is not valid");
        }

        try {
            long amiiboId = TagUtil.amiiboIdFromTag(this.currentTagData);
            String name = null;
            if (this.amiiboManager != null) {
                Amiibo amiibo = this.amiiboManager.amiibos.get(amiiboId);
                if (amiibo != null && amiibo.name != null) {
                    name = amiibo.name.replace("/", "-");
                }
            }
            if (name == null)
                name = TagUtil.amiiboIdToHex(amiiboId);

            byte[] uId = Arrays.copyOfRange(tagdata, 0, 9);
            String uIds = Util.bytesToHex(uId);
            String fileName = String.format("%1$s [%2$s] %3$ty%3$tm%3$te_%3$tH%3$tM%3$tS%4$s.bin", name, uIds, Calendar.getInstance(), (valid ? "" : "_corrupted_"));

            File dir = Util.getDataDir();
            if (!dir.isDirectory())
                dir.mkdir();

            File file = new File(dir.getAbsolutePath(), fileName);

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
            LogMessage("Wrote to file " + fileName + " in tagmo directory.");
        } catch (Exception e) {
            LogError("Error writing to file: " + e.getMessage());
        }
    }

    @Background
    void dumpLogCat() {
        try {
            File dir = Util.getDataDir();
            if (!dir.isDirectory())
                dir.mkdir();

            String fName = "tagmo_logcat.txt";

            File file = new File(dir.getAbsolutePath(), fName);

            Log.d(TAG, file.toString());
            Util.dumpLogcat(file.getAbsolutePath());
            try {
                MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
            } catch (Exception e) {
                Log.e(TAG, "Failed to refresh media scanner", e);
            }
            LogMessage("Wrote to file " + fName + " in tagmo directory.");
        } catch (Exception e) {
            LogError("Error writing to file: " + e.getMessage());
        }
    }

    @UiThread
    public void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @UiThread
    void LogMessage(String msg) {
        new AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("Close", null)
            .show();
    }

    @UiThread
    void LogError(String msg, Throwable e) {
        new AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(msg)
            .setPositiveButton("Close", null)
            .show();
    }

    @UiThread
    void LogError(String msg) {
        new AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(msg)
            .setPositiveButton("Close", null)
            .show();
    }
}
