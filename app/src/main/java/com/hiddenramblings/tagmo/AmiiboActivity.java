package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

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
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.eightbit.Foomiibo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.SettingsFragment;
import com.hiddenramblings.tagmo.widget.Toasty;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.Extra;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.api.BackgroundExecutor;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_amiibo)
public class AmiiboActivity extends AppCompatActivity {

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

    @Extra(TagMo.EXTRA_TAG_DATA)
    byte[] tagData;

    @Extra(TagMo.EXTRA_AMIIBO_ID)
    long amiiboId = -1;

    private AmiiboManager amiiboManager = null;

    @InstanceState
    boolean ignoreTagTd;

    @AfterViews
    void afterViews() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M){
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }

        toolbar.inflateMenu(R.menu.amiibo_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            Bundle args = new Bundle();
            Intent scan = new Intent(this, NfcActivity_.class);
            switch (item.getItemId()) {
                case R.id.mnu_scan:
                    scan.setAction(TagMo.ACTION_SCAN_TAG);
                    onUpdateTagResult.launch(scan);
                    return true;
                case R.id.mnu_write:
                    args.putByteArray(TagMo.EXTRA_TAG_DATA, this.tagData);
                    scan.setAction(TagMo.ACTION_WRITE_TAG_FULL);
                    onUpdateTagResult.launch(scan.putExtras(args));
                    return true;
                case R.id.mnu_update:
                    args.putByteArray(TagMo.EXTRA_TAG_DATA, this.tagData);
                    scan.setAction(TagMo.ACTION_WRITE_TAG_DATA);
                    scan.putExtra(TagMo.EXTRA_IGNORE_TAG_ID, ignoreTagTd);
                    onUpdateTagResult.launch(scan.putExtras(args));
                    return true;
                case R.id.mnu_save:
                    if (tagData != null)
                        displayBackupDialog(this.tagData, false);
                    else
                        displayBackupDialog(Foomiibo.generateData(
                                txtTagId.getText().toString()), true);
                    return true;
                case R.id.mnu_edit:
                    args.putByteArray(TagMo.EXTRA_TAG_DATA, this.tagData);
                    Intent tagEdit = new Intent(this, TagDataActivity_.class);
                    onUpdateTagResult.launch(tagEdit.putExtras(args));
                    return true;
                case R.id.mnu_view_hex:
                    Intent hexView = new Intent(this, HexViewerActivity_.class);
                    hexView.putExtra(TagMo.EXTRA_TAG_DATA, this.tagData);
                    startActivity(hexView);
                    return true;
                case R.id.mnu_validate:
                    try {
                        TagUtils.validateData(tagData);
                        new Toasty(this).Dialog(R.string.validation_success);
                    } catch (Exception e) {
                        new Toasty(this).Dialog(e.getMessage());
                    }
                    return true;
                case R.id.mnu_delete:
                    setResult(Activity.RESULT_OK, new Intent(TagMo.ACTION_DELETE_AMIIBO));
                    finish();
                    return true;
                case R.id.mnu_ignore_tag_id:
                    ignoreTagTd = !item.isChecked();
                    item.setChecked(ignoreTagTd);
                    return true;
            }
            return false;
        });

        loadAmiiboManager();
        updateAmiiboView();
    }

    private void launchEliteActivity(Intent resultData) {
        if (TagMo.getPrefs().enableEliteSupport().get()
                && resultData.hasExtra(TagMo.EXTRA_SIGNATURE)) {
            Intent eliteIntent = new Intent(this, BankListActivity_.class);
            eliteIntent.putExtras(resultData.getExtras());
            startActivity(eliteIntent);
            finish(); // Relaunch activity to bring view to front
        }
    }

    ActivityResultLauncher<Intent> onUpdateTagResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;

        if (TagMo.ACTION_UPDATE_TAG.equals(result.getData().getAction())) {
            this.tagData = result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
            this.runOnUiThread(this::updateAmiiboView);
            toolbar.getMenu().findItem(R.id.mnu_write).setEnabled(false);
            return;
        }

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())
                && !TagMo.ACTION_EDIT_COMPLETE.equals(result.getData().getAction())) return;


        // If we're supporting, didn't arrive from, but scanned an N2...
        if (TagMo.getPrefs().enableEliteSupport().get()
                && result.getData().hasExtra(TagMo.EXTRA_SIGNATURE)) {
            launchEliteActivity(result.getData());
        } else {
            this.tagData = result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
            this.runOnUiThread(this::updateAmiiboView);
        }
    });

    @Click(R.id.container)
    void onContainerClick() {
        Bundle args = new Bundle();
        args.putByteArray(TagMo.EXTRA_TAG_DATA, this.tagData);
        Intent intent = new Intent(TagMo.ACTION_NFC_SCANNED);
        setResult(Activity.RESULT_OK, intent.putExtras(args));
        TagMo.setIntentFilterEnabled(true);
        finish();
    }

    private void displayBackupDialog(byte[] tagData, boolean foo) {
        View view = getLayoutInflater().inflate(R.layout.dialog_backup, null);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        final EditText input = view.findViewById(R.id.backup_entry);
        input.setText(TagUtils.decipherFilename(this.amiiboManager, tagData, foo));
        Dialog backupDialog = dialog.setView(view).show();
        view.findViewById(R.id.save_backup).setOnClickListener(v -> {
            try {
                File directory = Storage.getDownloadDir("TagMo",
                        foo ? "Foomiibo" : "Backups");
                String fileName = TagUtils.writeBytesToFile(directory,
                        input.getText().toString(), tagData);
                new Toasty(this).Long(getString(R.string.wrote_file, fileName));
            } catch (IOException e) {
                new Toasty(this).Short(e.getMessage());
            }
            backupDialog.dismiss();
        });
        view.findViewById(R.id.cancel_backup).setOnClickListener(v -> backupDialog.dismiss());
    }

    @Click(R.id.imageAmiibo)
    void onImageClicked() {
        if (this.tagData != null && this.tagData.length > 0) {
            try {
                amiiboId = TagUtils.amiiboIdFromTag(tagData);
            } catch (Exception e) {
                Debug.Log(e);
            }
        }
        if (amiiboId == -1) {
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putLong(TagMo.EXTRA_AMIIBO_ID, amiiboId);

        Intent intent = new Intent(this, ImageActivity_.class);
        intent.putExtras(bundle);

        startActivity(intent);
    }

    static final String BACKGROUND_AMIIBO_MANAGER = "amiibo_manager";

    void loadAmiiboManager() {
        BackgroundExecutor.cancelAll(BACKGROUND_AMIIBO_MANAGER, true);
        loadAmiiboManagerTask();
    }

    @Background(id = BACKGROUND_AMIIBO_MANAGER)
    void loadAmiiboManagerTask() {
        AmiiboManager amiiboManager = null;
        try {
            amiiboManager = AmiiboManager.getAmiiboManager();
        } catch (IOException | JSONException | ParseException e) {
            Debug.Log(e);
            new Toasty(this).Short(getString(R.string.amiibo_info_parse_error));
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

    public void updateAmiiboView() {
        String tagInfo = null;
        String amiiboHexId = "";
        String amiiboName = "";
        String amiiboSeries = "";
        String amiiboType = "";
        String gameSeries = "";
        // String character = "";
        String amiiboImageUrl;

        boolean available = this.tagData != null && this.tagData.length > 0;
        if (available) {
            try {
                amiiboId = TagUtils.amiiboIdFromTag(this.tagData);
            } catch (Exception e) {
                available = false;
                Debug.Log(e);
            }
        }
        toolbar.getMenu().findItem(R.id.mnu_write).setEnabled(available);
        toolbar.getMenu().findItem(R.id.mnu_update).setEnabled(available);
        toolbar.getMenu().findItem(R.id.mnu_edit).setEnabled(available);
        toolbar.getMenu().findItem(R.id.mnu_view_hex).setEnabled(available);
        toolbar.getMenu().findItem(R.id.mnu_validate).setEnabled(available);

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
                amiiboHexId = TagUtils.amiiboIdToHex(amiibo.id);
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
                tagInfo = "ID: " + TagUtils.amiiboIdToHex(amiiboId);
                amiiboImageUrl = Amiibo.getImageUrl(amiiboId);
            }
        }

        boolean hasTagInfo = tagInfo != null;
        if (hasTagInfo) {
            setAmiiboInfoText(txtError, tagInfo, false);
            amiiboInfo.setVisibility(View.GONE);
        } else {
            txtError.setVisibility(View.GONE);
            amiiboInfo.setVisibility(View.VISIBLE);
        }
        setAmiiboInfoText(txtName, amiiboName, hasTagInfo);
        setAmiiboInfoText(txtTagId, amiiboHexId, hasTagInfo);
        setAmiiboInfoText(txtAmiiboSeries, amiiboSeries, hasTagInfo);
        setAmiiboInfoText(txtAmiiboType, amiiboType, hasTagInfo);
        setAmiiboInfoText(txtGameSeries, gameSeries, hasTagInfo);
        // setAmiiboInfoText(txtCharacter, character, hasTagInfo);

        if (imageAmiibo != null) {
            imageAmiibo.setVisibility(View.GONE);
            Glide.with(this).clear(amiiboImageTarget);
            if (amiiboImageUrl != null) {
                Glide.with(this)
                        .setDefaultRequestOptions(onlyRetrieveFromCache())
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

    private RequestOptions onlyRetrieveFromCache() {
        String imageNetworkSetting = TagMo.getPrefs().imageNetworkSetting().get();
        if (SettingsFragment.IMAGE_NETWORK_NEVER.equals(imageNetworkSetting)) {
            return new RequestOptions().onlyRetrieveFromCache(true);
        } else if (SettingsFragment.IMAGE_NETWORK_WIFI.equals(imageNetworkSetting)) {
            ConnectivityManager cm = (ConnectivityManager)
                    TagMo.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return new RequestOptions().onlyRetrieveFromCache(activeNetwork == null
                    || activeNetwork.getType() != ConnectivityManager.TYPE_WIFI);
        } else {
            return new RequestOptions().onlyRetrieveFromCache(false);
        }
    }

    @Override
    public void onBackPressed() {
        onContainerClick();
    }
}
