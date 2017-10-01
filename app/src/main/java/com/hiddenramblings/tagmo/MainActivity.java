package com.hiddenramblings.tagmo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;

import com.hiddenramblings.tagmo.ptag.PTagKeyManager;


import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.OptionsMenuItem;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.sharedpreferences.Pref;
import org.androidannotations.api.BackgroundExecutor;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

@EActivity(R.layout.activity_main)
@OptionsMenu({R.menu.main_menu})
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    public static final String BACKGROUND_AMIIBO_MANAGER = "amiibo_manager";

    private static final int FILE_LOAD_TAG = 0x100;
    private static final int NFC_ACTIVITY = 0x102;
    private static final int EDIT_TAG = 0x103;
    private static final int SCAN_QR_CODE = 0x104;

    public static final int VIEW_TYPE_SIMPLE = 0;
    public static final int VIEW_TYPE_COMPACT = 1;
    public static final int VIEW_TYPE_LARGE = 2;

    TextView txtTagId;
    TextView txtName;
    TextView txtGameSeries;
    TextView txtCharacter;
    TextView txtAmiiboType;
    TextView txtAmiiboSeries;
    ImageView imageAmiibo;

    @OptionsMenuItem(R.id.view_simple)
    MenuItem menuViewSimple;
    @OptionsMenuItem(R.id.view_compact)
    MenuItem menuViewCompact;
    @OptionsMenuItem(R.id.view_large)
    MenuItem menuViewLarge;

    @ViewById(R.id.btnScanTag)
    Button btnScanTag;
    @ViewById(R.id.btnSaveTag)
    Button btnSaveTag;
    @ViewById(R.id.btnLoadTag)
    Button btnLoadTag;
    @ViewById(R.id.btnWriteTagAuto)
    Button btnWriteTagAuto;
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
    @ViewById(R.id.coordinator)
    View snackBarContainer;

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

    ArrayList<Snackbar> snackbarQueue = new ArrayList<>();
    Snackbar keysNotFoundSnackbar;
    Snackbar nfcNotSupportedSnackbar;
    Snackbar nfcNotEnabledSnackbar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.verifyStoragePermissions();
    }

    @AfterViews
    protected void afterViews() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        setAmiiboView();
    }

    @Override
    protected void onResume() {
        super.onResume();

        startNfcMonitor();
        keyManager = new KeyManager(this);
        this.loadAmiiboManager();
        this.loadPTagKeyManager();
    }

    @Override
    protected void onPause() {
        stopNfcMonitor();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);

        setView(getView());

        return result;
    }

    @Background
    void loadPTagKeyManager() {
        if (!prefs.enablePowerTagSupport().get()) {
            Log.d(TAG, "PowerTag support not enabled.");
            return;
        }
        try {
            Log.d(TAG, "Loading PowerTag keyset.");
            PTagKeyManager.load(this);
        } catch (Exception e) {
            e.printStackTrace();
            showToast("Failed to load PowerTag keys");
        }
    }

    void loadAmiiboManager() {
        BackgroundExecutor.cancelAll(BACKGROUND_AMIIBO_MANAGER, true);
        loadAmiiboManagerTask();
    }

    @Background(id=BACKGROUND_AMIIBO_MANAGER)
    void loadAmiiboManagerTask() {
        AmiiboManager amiiboManager = null;
        try {
            amiiboManager = Util.loadAmiiboManager(this);
        } catch (IOException | JSONException | ParseException e) {
            e.printStackTrace();
            showToast("Unable to parse amiibo info");
        }

        if (Thread.currentThread().isInterrupted())
            return;
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
                Log.d(TAG, "ACTION_ADAPTER_STATE_CHANGED");
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

    Snackbar.Callback snackbarCallback = new Snackbar.Callback() {
        @Override
        public void onDismissed(Snackbar snackbar, int event) {
            super.onDismissed(snackbar, event);

            switch (event) {
                case Snackbar.Callback.DISMISS_EVENT_ACTION:
                case Snackbar.Callback.DISMISS_EVENT_TIMEOUT:
                case Snackbar.Callback.DISMISS_EVENT_SWIPE:
                case Snackbar.Callback.DISMISS_EVENT_MANUAL:
                    snackbarQueue.remove(snackbar);
                    if (!snackbarQueue.isEmpty()) {
                        displaySnackbar(snackbarQueue);
                    }
                    break;
            }
        }
    };

    public void displaySnackbar(ArrayList<Snackbar> queue) {
        if (queue.isEmpty())
            return;

        queue.get(0)
            .removeCallback(snackbarCallback)
            .addCallback(snackbarCallback)
            .show();
    }

    public int getView() {
        return this.prefs.mainAmiiboView().get();
    }

    public void setView(int view) {
        this.prefs.mainAmiiboView().put(view);
        if (view == VIEW_TYPE_SIMPLE) {
            menuViewSimple.setChecked(true);
        } else if (view == VIEW_TYPE_COMPACT) {
            menuViewCompact.setChecked(true);
        } else if (view == VIEW_TYPE_LARGE) {
            menuViewLarge.setChecked(true);
        }
    }

    @UiThread
    void updateStatus() {
        boolean hasNfc = (nfcAdapter != null);
        boolean nfcEnabled = !hasNfc || nfcAdapter.isEnabled();
        boolean hasFixed = this.keyManager.hasFixedKey();
        boolean hasUnfixed = this.keyManager.hasUnFixedKey();
        boolean hasKeys = hasFixed && hasUnfixed;
        boolean hasTag = currentTagData != null;

        if (!hasKeys) {
            if (keysNotFoundSnackbar == null) {
                keysNotFoundSnackbar = Snackbar
                    .make(snackBarContainer, R.string.keys_missing_warning, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.open_settings_action, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            openSettings();
                        }
                    })
                    .addCallback(new Snackbar.Callback() {
                        @Override
                        public void onDismissed(Snackbar snackbar, int event) {
                            super.onDismissed(snackbar, event);

                            if (keysNotFoundSnackbar == snackbar) {
                                keysNotFoundSnackbar = null;
                            }
                        }
                    });
            }
            if (!snackbarQueue.contains(keysNotFoundSnackbar)) {
                snackbarQueue.add(keysNotFoundSnackbar);
            }
        } else if (snackbarQueue.indexOf(keysNotFoundSnackbar) == 0) {
            keysNotFoundSnackbar.dismiss();
        } else {
            snackbarQueue.remove(keysNotFoundSnackbar);
        }
        if (!hasNfc) {
            if (nfcNotSupportedSnackbar == null) {
                nfcNotSupportedSnackbar = Snackbar
                    .make(snackBarContainer, R.string.nfc_unsupported, Snackbar.LENGTH_INDEFINITE)
                    .addCallback(new Snackbar.Callback() {
                        @Override
                        public void onDismissed(Snackbar snackbar, int event) {
                            super.onDismissed(snackbar, event);

                            if (nfcNotSupportedSnackbar == snackbar) {
                                nfcNotSupportedSnackbar = null;
                            }
                        }
                    });
            }
            if (!snackbarQueue.contains(nfcNotSupportedSnackbar)) {
                snackbarQueue.add(nfcNotSupportedSnackbar);
            }
        } else if (snackbarQueue.indexOf(nfcNotSupportedSnackbar) == 0) {
            nfcNotSupportedSnackbar.dismiss();
        } else {
            snackbarQueue.remove(nfcNotSupportedSnackbar);
        }
        if (!nfcEnabled) {
            if (nfcNotEnabledSnackbar == null) {
                nfcNotEnabledSnackbar = Snackbar
                    .make(snackBarContainer, R.string.nfc_disabled, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.nfc_enable_action, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
                                startActivity(intent);
                            } else {
                                Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                                startActivity(intent);
                            }
                        }
                    })
                    .addCallback(new Snackbar.Callback() {
                        @Override
                        public void onDismissed(Snackbar snackbar, int event) {
                            super.onDismissed(snackbar, event);

                            if (nfcNotEnabledSnackbar == snackbar) {
                                nfcNotEnabledSnackbar = null;
                            }
                        }
                    });
            }
            if (!snackbarQueue.contains(nfcNotEnabledSnackbar)) {
                snackbarQueue.add(nfcNotEnabledSnackbar);
            }
        } else if (snackbarQueue.indexOf(nfcNotEnabledSnackbar) == 0) {
            nfcNotEnabledSnackbar.dismiss();
        } else {
            snackbarQueue.remove(nfcNotEnabledSnackbar);
        }
        displaySnackbar(snackbarQueue);

        btnScanTag.setEnabled(nfcEnabled);
        btnWriteTagAuto.setEnabled(nfcEnabled && hasKeys && hasTag);
        btnRestoreTag.setEnabled(nfcEnabled && hasTag);
        btnSaveTag.setEnabled(hasTag);
        btnShowQRCode.setEnabled(hasTag);
        btnEditDataSSB.setEnabled(hasKeys && hasTag);
        btnEditDataTP.setEnabled(hasKeys && hasTag);
        btnViewHex.setEnabled(hasKeys && hasTag);

        int ssbVisibility = View.GONE;
        int tpVisibility = View.GONE;

        if (currentTagData != null) {
            try {
                long amiiboId = TagUtil.amiiboIdFromTag(currentTagData);
                if (EditorTP.canEditAmiibo(amiiboId)) {
                    tpVisibility = View.VISIBLE;
                }
                if (EditorSSB.canEditAmiibo(amiiboId)) {
                    ssbVisibility = View.VISIBLE;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        updateAmiiboView();

        btnEditDataSSB.setVisibility(ssbVisibility);
        btnEditDataTP.setVisibility(tpVisibility);
    }

    void setAmiiboView() {
        ViewGroup amiiboInfoView = this.findViewById(R.id.amiiboInfoView);
        Glide.with(this).clear(amiiboImageTarget);
        amiiboInfoView.removeAllViews();

        View amiiboView;
        switch (getView()) {
            case VIEW_TYPE_COMPACT:
                amiiboView = this.getLayoutInflater().inflate(R.layout.amiibo_compact_card, amiiboInfoView, false);
                break;
            case VIEW_TYPE_LARGE:
                amiiboView = this.getLayoutInflater().inflate(R.layout.amiibo_large_card, amiiboInfoView, false);
                break;
            case VIEW_TYPE_SIMPLE:
            default:
                amiiboView = this.getLayoutInflater().inflate(R.layout.amiibo_simple_card, amiiboInfoView, false);
                break;
        }

        amiiboInfoView.addView(amiiboView);
        txtTagId = amiiboView.findViewById(R.id.txtTagId);
        txtName = amiiboView.findViewById(R.id.txtName);
        txtGameSeries = amiiboView.findViewById(R.id.txtGameSeries);
        txtCharacter = amiiboView.findViewById(R.id.txtCharacter);
        txtAmiiboType = amiiboView.findViewById(R.id.txtAmiiboType);
        txtAmiiboSeries = amiiboView.findViewById(R.id.txtAmiiboSeries);
        imageAmiibo = amiiboView.findViewById(R.id.imageAmiibo);
        if (imageAmiibo != null) {
            imageAmiibo.setOnClickListener(this.onAmiiboImageClick);
        }

        updateAmiiboView();
    }

    View.OnClickListener onAmiiboImageClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            long amiiboId;
            try {
                amiiboId = TagUtil.amiiboIdFromTag(currentTagData);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            Bundle bundle = new Bundle();
            bundle.putLong(ImageActivity.INTENT_EXTRA_AMIIBO_ID, amiiboId);

            Intent intent = new Intent(MainActivity.this, ImageActivity_.class);
            intent.putExtras(bundle);

            startActivity(intent);
        }
    };

    SimpleTarget<Bitmap> amiiboImageTarget = new SimpleTarget<Bitmap>() {
        @Override
        public void onLoadStarted(@Nullable Drawable placeholder) {
            imageAmiibo.setVisibility(View.GONE);
        }

        @Override
        public void onLoadFailed(@Nullable Drawable errorDrawable) {
            imageAmiibo.setVisibility(View.GONE);
        }

        @Override
        public void onResourceReady(Bitmap resource, Transition transition) {
            imageAmiibo.setImageBitmap(resource);
            imageAmiibo.setVisibility(View.VISIBLE);
        }
    };


    public void updateAmiiboView() {
        String tagInfo = null;
        String amiiboHexId = "";
        String amiiboName = "";
        String amiiboSeries = "";
        String amiiboType = "";
        String gameSeries = "";
        String character = "";
        final String amiiboImageUrl;

        if (this.currentTagData == null) {
            tagInfo = "<No Tag Loaded>";
            amiiboImageUrl = null;
        } else {
            long amiiboId;
            try {
                amiiboId = TagUtil.amiiboIdFromTag(this.currentTagData);
            } catch (Exception e) {
                e.printStackTrace();
                amiiboId = -1;
            }
            if (amiiboId == -1) {
                tagInfo = "<Error Reading Tag>";
                amiiboImageUrl = null;
            } else if (amiiboId == 0) {
                tagInfo = "<Blank Tag>";
                amiiboImageUrl = null;
            } else {
                Amiibo amiibo = null;
                if (this.amiiboManager != null) {
                    amiibo = amiiboManager.amiibos.get(amiiboId);
                    if (amiibo == null)
                        amiibo = new Amiibo(amiiboManager, amiiboId, null, null);
                }
                if (amiibo != null) {
                    amiiboHexId = TagUtil.amiiboIdToHex(amiibo.id);
                    amiiboImageUrl = amiibo.getImageUrl();
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
                } else {
                    tagInfo = "ID: " + TagUtil.amiiboIdToHex(amiiboId);
                    amiiboImageUrl = Amiibo.getImageUrl(amiiboId);
                }
            }
        }

        if (tagInfo == null) {
            setAmiiboInfoText(txtName, amiiboName, false);
        } else {
            setAmiiboInfoText(txtName, tagInfo, false);
        }
        setAmiiboInfoText(txtTagId, amiiboHexId, tagInfo != null);
        setAmiiboInfoText(txtAmiiboSeries, amiiboSeries, tagInfo != null);
        setAmiiboInfoText(txtAmiiboType, amiiboType, tagInfo != null);
        setAmiiboInfoText(txtGameSeries, gameSeries, tagInfo != null);
        //setAmiiboInfoText(txtCharacter, character, tagInfo != null);

        if (imageAmiibo != null) {
            imageAmiibo.setVisibility(View.GONE);
            Glide.with(this).clear(amiiboImageTarget);
            if (amiiboImageUrl != null) {
                Glide.with(this)
                    .setDefaultRequestOptions(new RequestOptions().onlyRetrieveFromCache(onlyRetrieveFromCache()))
                    .asBitmap()
                    .load(amiiboImageUrl)
                    .into(amiiboImageTarget);
            }
        }
    }

    void setAmiiboInfoText(TextView textView, CharSequence text, boolean hasTagInfo) {
        if (hasTagInfo) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            if (text.length() == 0) {
                textView.setText("Unknown");
                textView.setEnabled(false);
            } else {
                textView.setText(text);
                textView.setEnabled(true);
            }
        }
    }

    boolean onlyRetrieveFromCache() {
        String imageNetworkSetting = prefs.imageNetworkSetting().get();
        if (SettingsFragment.IMAGE_NETWORK_NEVER.equals(imageNetworkSetting)) {
            return true;
        } else if (SettingsFragment.IMAGE_NETWORK_WIFI.equals(imageNetworkSetting)) {
            ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork == null || activeNetwork.getType() != ConnectivityManager.TYPE_WIFI;
        } else {
            return false;
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

    @OptionsItem(R.id.mnu_write_raw)
    void writeToTagRaw() {
        if (this.currentTagData == null) {
            LogError("No tag loaded");
            return;
        }
        if (!this.keyManager.hasBothKeys()) {
            LogError("Keys not loaded");
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

        String content = Base64.encodeToString(this.currentTagData, Base64.DEFAULT);

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
            data = Base64.decode(content, Base64.DEFAULT);
            TagUtil.validateTag(data);
        } catch (Exception e) {
            Log.e(TAG, "QR Code base64 decode failure", e);
            data = null;
        }
        if (data == null) {
            try {
                data = content.getBytes("ISO-8859-1");
                TagUtil.validateTag(data);
            } catch (Exception e) {
                Log.e(TAG, "QR Code legacy decode failure", e);
                data = null;
            }
        }

        if (data == null) {
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

    @OptionsItem(R.id.view_simple)
    void onViewSimpleClick() {
        this.setView(VIEW_TYPE_SIMPLE);
        this.setAmiiboView();
    }

    @OptionsItem(R.id.view_compact)
    void onViewCompactClick() {
        this.setView(VIEW_TYPE_COMPACT);
        this.setAmiiboView();
    }

    @OptionsItem(R.id.view_large)
    void onViewLargeClick() {
        this.setView(VIEW_TYPE_LARGE);
        this.setAmiiboView();
    }

    @OnActivityResult(EDIT_TAG)
    void onEditTagResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null)
            return;

        if (!Actions.ACTION_EDIT_COMPLETE.equals(data.getAction()))
            return;

        this.currentTagData = data.getByteArrayExtra(Actions.EXTRA_TAG_DATA);
        this.updateStatus();

        if (this.currentTagData == null) {
            LogError("Tag data is empty");
        }
    }

    @OnActivityResult(NFC_ACTIVITY)
    void onNFCResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null)
            return;

        if (!NfcActivity.ACTION_NFC_SCANNED.equals(data.getAction()))
            return;

        this.currentTagData = data.getByteArrayExtra(NfcActivity.EXTRA_TAG_DATA);
        updateStatus();

        if (this.currentTagData == null) {
            LogError("Tag data is empty");
        } else if (cbAutoSaveOnScan.isChecked()) {
            writeTagToFile(this.currentTagData);
        }
    }

    @OnActivityResult(FILE_LOAD_TAG)
    void onFileLoadResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null)
            return;

        loadTagFile(data.getData());
    }

    @OnActivityResult(SCAN_QR_CODE)
    void onScanQRCodeResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null)
            return;

        loadQRCode(data.getStringExtra("SCAN_RESULT"));
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
        Log.d(TAG, "loadTagFile");
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
