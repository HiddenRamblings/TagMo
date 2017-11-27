package com.hiddenramblings.tagmo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.Extra;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.sharedpreferences.Pref;
import org.androidannotations.api.BackgroundExecutor;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;


@EActivity(R.layout.activity_amiibo)
public class AmiiboActivity extends AppCompatActivity {
    private static final String TAG = "AmiiboActivity";

    public static final String BACKGROUND_AMIIBO_MANAGER = "amiibo_manager";

    public static final String ARG_TAG_DATA = "tag_data";

    private static final int NFC_ACTIVITY = 0x102;
    private static final int EDIT_TAG = 0x103;

    @ViewById(R.id.toolbar)
    Toolbar toolbar;
    @ViewById(R.id.txtError)
    TextView txtError;
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
    @ViewById(R.id.imageAmiibo)
    ImageView imageAmiibo;

    @Extra(ARG_TAG_DATA)
    byte[] tagData;

    AmiiboManager amiiboManager = null;

    @Pref
    Preferences_ prefs;

    @InstanceState
    boolean ignoreTagTd;

    @AfterViews
    void afterViews() {
        toolbar.inflateMenu(R.menu.tag_menu);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.mnu_save:
                        saveTag();
                        return true;
                    case R.id.mnu_write:
                        writeTag();
                        return true;
                    case R.id.mnu_restore:
                        restoreTag();
                        return true;
                    case R.id.mnu_view_hex:
                        viewHex();
                        return true;
                    case R.id.mnu_ignore_tag_id:
                        ignoreTagTd = !item.isChecked();
                        item.setChecked(ignoreTagTd);
                        return true;
                    case R.id.mnu_edit_ssb:
                        openSSBEditor();
                        return true;
                    case R.id.mnu_edit_tp:
                        openTPEditor();
                        return true;
                }
                return false;
            }
        });

        loadAmiiboManager();
        updateAmiiboView();
    }

    @Click(R.id.container)
    void onContainerClick() {
        finish();
    }

    void openSSBEditor() {
        Intent intent = new Intent(this, EditorSSB_.class);
        intent.setAction(Actions.ACTION_EDIT_DATA);
        intent.putExtra(Actions.EXTRA_TAG_DATA, this.tagData);
        startActivityForResult(intent, EDIT_TAG);
    }

    void openTPEditor() {
        Intent intent = new Intent(this, EditorTP_.class);
        intent.setAction(Actions.ACTION_EDIT_DATA);
        intent.putExtra(Actions.EXTRA_TAG_DATA, this.tagData);
        startActivityForResult(intent, EDIT_TAG);
    }

    @OnActivityResult(EDIT_TAG)
    void onEditTagResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null)
            return;

        if (!Actions.ACTION_EDIT_COMPLETE.equals(data.getAction()))
            return;

        this.tagData = data.getByteArrayExtra(Actions.EXTRA_TAG_DATA);
        this.updateAmiiboView();
    }

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

    void loadAmiiboManager() {
        BackgroundExecutor.cancelAll(BACKGROUND_AMIIBO_MANAGER, true);
        loadAmiiboManagerTask();
    }

    @Background(id = BACKGROUND_AMIIBO_MANAGER)
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
        this.updateAmiiboView();
    }

    void saveTag() {
        boolean valid = false;
        try {
            TagUtil.validateTag(tagData);
            valid = true;
        } catch (Exception e) {
            LogError("Warning tag is not valid");
        }

        try {
            long amiiboId = TagUtil.amiiboIdFromTag(tagData);
            String name = null;
            if (this.amiiboManager != null) {
                Amiibo amiibo = this.amiiboManager.amiibos.get(amiiboId);
                if (amiibo != null && amiibo.name != null) {
                    name = amiibo.name.replace("/", "-");
                }
            }
            if (name == null)
                name = TagUtil.amiiboIdToHex(amiiboId);

            byte[] uId = Arrays.copyOfRange(tagData, 0, 9);
            String uIds = Util.bytesToHex(uId);
            String fileName = String.format("%1$s [%2$s] %3$ty%3$tm%3$te_%3$tH%3$tM%3$tS%4$s.bin", name, uIds, Calendar.getInstance(), (valid ? "" : "_corrupted_"));

            File dir = new File(Util.getSDCardDir(), prefs.browserRootFolder().get());
            if (!dir.isDirectory())
                dir.mkdir();

            File file = new File(dir.getAbsolutePath(), fileName);

            Log.d(TAG, file.toString());
            FileOutputStream fos = new FileOutputStream(file);
            try {
                fos.write(tagData);
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

    void writeTag() {
        Intent intent = new Intent(this, NfcActivity_.class);
        intent.setAction(NfcActivity.ACTION_WRITE_TAG_FULL);
        intent.putExtra(NfcActivity.EXTRA_TAG_DATA, this.tagData);
        startActivityForResult(intent, NFC_ACTIVITY);
    }

    void restoreTag() {
        Intent intent = new Intent(this, NfcActivity_.class);
        intent.setAction(NfcActivity.ACTION_WRITE_TAG_DATA);
        intent.putExtra(NfcActivity.EXTRA_TAG_DATA, this.tagData);
        intent.putExtra(NfcActivity.EXTRA_IGNORE_TAG_ID, ignoreTagTd);
        startActivityForResult(intent, NFC_ACTIVITY);
    }

    void viewHex() {
        Intent intent = new Intent(this, HexViewerActivity_.class);
        intent.setAction(Actions.ACTION_EDIT_DATA);
        intent.putExtra(Actions.EXTRA_TAG_DATA, this.tagData);
        startActivity(intent);
    }

    @OnActivityResult(NFC_ACTIVITY)
    void onNFCResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null)
            return;

        if (!NfcActivity.ACTION_NFC_SCANNED.equals(data.getAction()))
            return;

        this.tagData = data.getByteArrayExtra(NfcActivity.EXTRA_TAG_DATA);
        updateAmiiboView();
    }

    @Click(R.id.imageAmiibo)
    void onImageClicked() {
        long amiiboId;
        try {
            amiiboId = TagUtil.amiiboIdFromTag(tagData);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putLong(ImageActivity.INTENT_EXTRA_AMIIBO_ID, amiiboId);

        Intent intent = new Intent(this, ImageActivity_.class);
        intent.putExtras(bundle);

        startActivity(intent);
    }

    public void updateAmiiboView() {
        String tagInfo = null;
        String amiiboHexId = "";
        String amiiboName = "";
        String amiiboSeries = "";
        String amiiboType = "";
        String gameSeries = "";
        String character = "";
        final String amiiboImageUrl;

        boolean ssbVisibility = false;
        boolean tpVisibility = false;

        if (this.tagData == null) {
            tagInfo = "<No Tag Loaded>";
            amiiboImageUrl = null;
        } else {
            long amiiboId;
            try {
                amiiboId = TagUtil.amiiboIdFromTag(this.tagData);
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
                if (EditorTP.canEditAmiibo(amiiboId)) {
                    tpVisibility = true;
                }
                if (EditorSSB.canEditAmiibo(amiiboId)) {
                    ssbVisibility = true;
                }
            }
        }
        for (int i = 0; i < toolbar.getMenu().size(); i++) {
            MenuItem menuItem = toolbar.getMenu().getItem(i);
            if (menuItem.getItemId() == R.id.mnu_edit_ssb) {
                menuItem.setVisible(ssbVisibility);
            } else if (menuItem.getItemId() == R.id.mnu_edit_tp) {
                menuItem.setVisible(tpVisibility);
            }
        }

        if (tagInfo == null) {
            txtError.setVisibility(View.GONE);
        } else {
            setAmiiboInfoText(txtError, tagInfo, false);
        }
        setAmiiboInfoText(txtName, amiiboName, tagInfo != null);
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
