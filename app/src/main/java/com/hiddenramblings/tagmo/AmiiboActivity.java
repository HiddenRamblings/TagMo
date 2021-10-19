package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.nfc.TagUtil;
import com.hiddenramblings.tagmo.nfc.Util;
import com.hiddenramblings.tagmo.settings.SettingsFragment;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.Extra;
import org.androidannotations.annotations.InstanceState;
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
import java.util.Locale;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_amiibo)
public class AmiiboActivity extends AppCompatActivity {
    private static final String TAG = AmiiboActivity.class.getSimpleName();

    public static final String BACKGROUND_AMIIBO_MANAGER = "amiibo_manager";

    @ViewById(R.id.toolbar)
    Toolbar toolbar;
    @ViewById(R.id.amiiboInfo)
    View amiiboInfo;
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

    @Extra(TagMo.BYTE_TAG_DATA)
    byte[] tagData;

    AmiiboManager amiiboManager = null;

    @Pref
    Preferences_ prefs;

    @InstanceState
    boolean ignoreTagTd;

    @AfterViews
    void afterViews() {
        toolbar.inflateMenu(R.menu.tag_menu);
        toolbar.setOnMenuItemClickListener(item -> {
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
                case R.id.mnu_edit:
                    openTagEditor();
                    return true;
            }
            return false;
        });

        loadAmiiboManager();
        updateAmiiboView();
    }

    @Click(R.id.container)
    void onContainerClick() {
        finish();
    }

    ActivityResultLauncher<Intent> onEditTagResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        TagMo.Debug(TAG, R.string.tag_data);
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null)
            return;

        TagMo.Debug(TAG, R.string.tag_data);
        if (!TagMo.ACTION_EDIT_COMPLETE.equals(result.getData().getAction()))
            return;

        TagMo.Debug(TAG, R.string.tag_data);
        this.tagData = result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
        this.runOnUiThread(this::updateAmiiboView);
    });

    void openTagEditor() {
        Intent intent = new Intent(this, TagDataActivity_.class);
        intent.setAction(TagMo.ACTION_EDIT_DATA);
        intent.putExtra(TagMo.EXTRA_TAG_DATA, this.tagData);
        onEditTagResult.launch(intent);
    }

    CustomTarget<Bitmap> amiiboImageTarget = new CustomTarget<Bitmap>() {
        @Override
        public void onLoadStarted(@Nullable Drawable placeholder) {
            imageAmiibo.setVisibility(View.GONE);
        }

        @Override
        public void onLoadFailed(@Nullable Drawable errorDrawable) {
            imageAmiibo.setVisibility(View.GONE);
        }

        @Override
        public void onLoadCleared(@Nullable Drawable placeholder) {

        }

        @Override
        public void onResourceReady(@NonNull Bitmap resource, Transition transition) {
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
            showToast(getString(R.string.amiibo_info_parse_error));
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
            LogError(getString(R.string.tag_invalid));
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
            String fileName = String.format(Locale.ENGLISH,
                    "%1$s [%2$s] %3$ty%3$tm%3$te_%3$tH%3$tM%3$tS%4$s.bin",
                    name, uIds, Calendar.getInstance(), (valid ? "" : "_corrupted_")
            );

            File dir = new File(Util.getSDCardDir(), prefs.browserRootFolder().get());
            if (!dir.isDirectory()) dir.mkdir();

            File file = new File(dir.getAbsolutePath(), fileName);

            TagMo.Debug(TAG, file.toString());
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(tagData);
            }
            try {
                MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
            } catch (Exception e) {
                TagMo.Error(TAG, R.string.media_scan_fail, e);
            }
            LogMessage(getString(R.string.wrote_file, fileName));
        } catch (Exception e) {
            LogError(getString(R.string.write_error, e.getMessage()));
        }
    }

    ActivityResultLauncher<Intent> onNFCResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null)
            return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction()))
            return;

        this.tagData = result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
        this.runOnUiThread(this::updateAmiiboView);
    });

    void writeTag() {
        Intent intent = new Intent(this, NfcActivity_.class);
        intent.setAction(TagMo.ACTION_WRITE_TAG_FULL);
        intent.putExtra(TagMo.EXTRA_TAG_DATA, this.tagData);
        onNFCResult.launch(intent);
    }

    void restoreTag() {
        Intent intent = new Intent(this, NfcActivity_.class);
        intent.setAction(TagMo.ACTION_WRITE_TAG_DATA);
        intent.putExtra(TagMo.EXTRA_TAG_DATA, this.tagData);
        intent.putExtra(TagMo.EXTRA_IGNORE_TAG_ID, ignoreTagTd);
        onNFCResult.launch(intent);
    }

    void viewHex() {
        Intent intent = new Intent(this, HexViewerActivity_.class);
        intent.setAction(TagMo.ACTION_EDIT_DATA);
        intent.putExtra(TagMo.EXTRA_TAG_DATA, this.tagData);
        startActivity(intent);
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
        // String character = "";
        final String amiiboImageUrl;

        if (this.tagData == null) {
            tagInfo = getString(R.string.no_tag_loaded);
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
                tagInfo = getString(R.string.read_error);
                amiiboImageUrl = null;
            } else if (amiiboId == 0) {
                tagInfo = getString(R.string.blank_tag);
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
                    // if (amiibo.getCharacter() != null)
                    //     character = amiibo.getCharacter().name;
                } else {
                    tagInfo = "ID: " + TagUtil.amiiboIdToHex(amiiboId);
                    amiiboImageUrl = Amiibo.getImageUrl(amiiboId);
                }
            }
        }

        if (tagInfo == null) {
            txtError.setVisibility(View.GONE);
            amiiboInfo.setVisibility(View.VISIBLE);
        } else {
            setAmiiboInfoText(txtError, tagInfo, false);
            amiiboInfo.setVisibility(View.GONE);
        }
        setAmiiboInfoText(txtName, amiiboName, tagInfo != null);
        setAmiiboInfoText(txtTagId, amiiboHexId, tagInfo != null);
        setAmiiboInfoText(txtAmiiboSeries, amiiboSeries, tagInfo != null);
        setAmiiboInfoText(txtAmiiboType, amiiboType, tagInfo != null);
        setAmiiboInfoText(txtGameSeries, gameSeries, tagInfo != null);
        // setAmiiboInfoText(txtCharacter, character, tagInfo != null);

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
                textView.setText(getString(R.string.unknown));
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
                .setPositiveButton(R.string.close, null)
                .show();
    }

    @UiThread
    void LogError(String msg, Throwable e) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(msg)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    @UiThread
    void LogError(String msg) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(msg)
                .setPositiveButton(R.string.close, null)
                .show();
    }
}
