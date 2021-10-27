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
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
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
import com.hiddenramblings.tagmo.nfctag.TagReader;
import com.hiddenramblings.tagmo.nfctag.TagUtils;
import com.hiddenramblings.tagmo.settings.SettingsFragment;

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

    AmiiboManager amiiboManager = null;
    boolean isResponsive = false;
    int current_bank = -1;

    @InstanceState
    boolean ignoreTagTd;

    @AfterViews
    void afterViews() {
        if (getCallingActivity() != null) {
            isResponsive = getCallingActivity().getClassName().equals(EliteActivity_.class.getName());
        }
        toolbar.inflateMenu(isResponsive ? R.menu.elite_menu : R.menu.amiibo_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.mnu_activate:
                    modifyBank(EliteActivity.ACTIVATE);
                    return true;
                case R.id.mnu_refresh:
                    Intent refresh = new Intent(this, NfcActivity_.class);
                    refresh.setAction(TagMo.ACTION_SCAN_TAG);
                    refresh.putExtra(TagMo.EXTRA_CURRENT_BANK, current_bank);
                    onEditTagResult.launch(refresh);
                    return true;
                case R.id.mnu_replace:
                    modifyBank(EliteActivity.REPLACE);
                    return true;
                case R.id.mnu_write:
                    writeTag();
                    return true;
                case R.id.mnu_restore:
                    restoreTag();
                    return true;
                case R.id.mnu_save:
                    displayBackupDialog();
                    return true;
                case R.id.mnu_backup:
                    modifyBank(EliteActivity.BACKUP);
                    return true;
                case R.id.mnu_edit:
                    openTagEditor();
                    return true;
                case R.id.mnu_view_hex:
                    viewHex();
                    return true;
                case R.id.mnu_wipe_bank:
                    modifyBank(EliteActivity.WIPE_BANK);
                    return true;
                case R.id.mnu_ignore_tag_id:
                    ignoreTagTd = !item.isChecked();
                    item.setChecked(ignoreTagTd);
                    return true;
            }
            return false;
        });
        if (getIntent().hasExtra(TagMo.EXTRA_CURRENT_BANK)) {
            current_bank = getIntent().getIntExtra(TagMo.EXTRA_CURRENT_BANK,
                    TagMo.getPrefs().eliteActiveBank().get());
        }

        loadAmiiboManager();
        updateAmiiboView();
    }

    ActivityResultLauncher<Intent> onEditTagResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null)
            return;

        if (!TagMo.ACTION_EDIT_COMPLETE.equals(result.getData().getAction()))
            return;

        this.tagData = result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
        this.runOnUiThread(this::updateAmiiboView);
    });

    @Click(R.id.container)
    void onContainerClick() {
        finish();
    }

    void openTagEditor() {
        Intent intent = new Intent(this, TagDataActivity_.class);
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

    public static final String BACKGROUND_AMIIBO_MANAGER = "amiibo_manager";

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

    ActivityResultLauncher<Intent> onNFCResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null)
            return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction()))
            return;

        if (TagMo.getPrefs().enableEliteSupport().get()) {
            String signature = result.getData().getStringExtra(TagMo.EXTRA_SIGNATURE);
            int active_bank = result.getData().getIntExtra(
                    TagMo.EXTRA_ACTIVE_BANK, TagMo.getPrefs().eliteActiveBank().get());
            int bank_count = result.getData().getIntExtra(
                    TagMo.EXTRA_BANK_COUNT, TagMo.getPrefs().eliteBankCount().get());

            TagMo.getPrefs().eliteSignature().put(signature);
            TagMo.getPrefs().eliteActiveBank().put(active_bank);
            TagMo.getPrefs().eliteBankCount().put(bank_count);

            Intent eliteIntent = new Intent(this, EliteActivity_.class);
            if (isResponsive) {
                eliteIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            }
            eliteIntent.putExtra(TagMo.EXTRA_SIGNATURE, signature);
            eliteIntent.putExtra(TagMo.EXTRA_ACTIVE_BANK, active_bank);
            eliteIntent.putExtra(TagMo.EXTRA_BANK_COUNT, bank_count);
            eliteIntent.putExtra(TagMo.EXTRA_AMIIBO_DATA,
                    result.getData().getStringArrayListExtra(TagMo.EXTRA_AMIIBO_DATA));
            startActivity(eliteIntent);

            finish(); // Relaunch activity to bring view to front

            Intent amiiboIntent = new Intent(this, AmiiboActivity_.class);
            amiiboIntent.putExtra(TagMo.EXTRA_TAG_DATA, tagData);
            if (active_bank != -1)
                amiiboIntent.putExtra(TagMo.EXTRA_CURRENT_BANK, active_bank);
            amiiboIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(amiiboIntent);
        }

        this.tagData = result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
        this.runOnUiThread(this::updateAmiiboView);
    });


    void modifyBank(int selection) {
        Intent action = new Intent(TagMo.ACTION_NFC_SCANNED);
        action.putExtra(TagMo.EXTRA_BANK_ACTION, selection);
        setResult(Activity.RESULT_OK, action);
        finish();
    }

    void writeTag() {
        Intent intent = new Intent(this, NfcActivity_.class);
        intent.setAction(TagMo.ACTION_WRITE_TAG_FULL);
        if (current_bank != -1)
            intent.putExtra(TagMo.EXTRA_CURRENT_BANK, current_bank);
        intent.putExtra(TagMo.EXTRA_TAG_DATA, this.tagData);
        onNFCResult.launch(intent);
    }

    void restoreTag() {
        Intent intent = new Intent(this, NfcActivity_.class);
        intent.setAction(TagMo.ACTION_WRITE_TAG_DATA);
        if (current_bank != -1)
            intent.putExtra(TagMo.EXTRA_CURRENT_BANK, current_bank);
        intent.putExtra(TagMo.EXTRA_TAG_DATA, this.tagData);
        intent.putExtra(TagMo.EXTRA_IGNORE_TAG_ID, ignoreTagTd);
        onNFCResult.launch(intent);
    }

    private void displayBackupDialog() {
        View view = getLayoutInflater().inflate(R.layout.backup_dialog, null);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        final EditText input = view.findViewById(R.id.backup_entry);
        input.setText(TagReader.generateFileName(this.amiiboManager, tagData));
        Dialog backupDialog = dialog.setView(view).show();
        view.findViewById(R.id.save_backup).setOnClickListener(v -> {
            try {
                File directory = new File(TagMo.getStorage(), TagMo.getPrefs().browserRootFolder().get()
                        + File.separator + TagMo.getStringRes(R.string.tagmo_backup));
                String fileName = TagReader.writeBytesToFile(directory, input.getText().toString(), tagData);
                showToast(getString(R.string.wrote_file, fileName));
            } catch (IOException e) {
                showToast(e.getMessage());
            }
            backupDialog.dismiss();
        });
        view.findViewById(R.id.cancel_backup).setOnClickListener(v -> backupDialog.dismiss());
    }

    void viewHex() {
        Intent intent = new Intent(this, HexViewerActivity_.class);
        intent.putExtra(TagMo.EXTRA_TAG_DATA, this.tagData);
        startActivity(intent);
    }

    @Click(R.id.imageAmiibo)
    void onImageClicked() {
        long amiiboId;
        try {
            amiiboId = TagUtils.amiiboIdFromTag(tagData);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putLong(TagMo.EXTRA_AMIIBO_ID, amiiboId);

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
                amiiboId = TagUtils.amiiboIdFromTag(this.tagData);
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
        String imageNetworkSetting = TagMo.getPrefs().imageNetworkSetting().get();
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
}
