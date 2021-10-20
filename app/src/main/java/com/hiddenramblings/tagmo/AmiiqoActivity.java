package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.hiddenramblings.tagmo.adapter.AmiiqoContentAdapter;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.nfc.TagUtil;
import com.hiddenramblings.tagmo.nfc.TagWriter;
import com.hiddenramblings.tagmo.nfc.Util;
import com.hiddenramblings.tagmo.settings.BrowserSettings;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.sharedpreferences.Pref;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.amiiqo_content)
public class AmiiqoActivity  extends AppCompatActivity implements
        AmiiqoContentAdapter.OnAmiiboClickListener {

    private static final String TAG = AmiiqoActivity.class.getSimpleName();

    @Pref
    Preferences_ prefs;

    @ViewById(R.id.amiibos_list)
    RecyclerView amiibosView;
    @ViewById(R.id.hardware_info)
    TextView hardwareInfo;
    @ViewById(R.id.bottom_sheet)
    ViewGroup bottomSheet;
    @ViewById(R.id.toggle)
    ImageView toggle;
    @ViewById(R.id.bank_count_picker)
    BankNumberPicker amiiqoBankCount;
    @ViewById(R.id.write_all_banks)
    AppCompatButton writeAllBanks;
    @ViewById(R.id.write_bank_count)
    AppCompatButton writeBankCount;

    BottomSheetBehavior<View> bottomSheetBehavior;

    @InstanceState
    BrowserSettings settings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.settings = new BrowserSettings();
        this.settings.setBrowserRootFolder(new File(Util.getSDCardDir(), prefs.browserRootFolder().get()));
        this.settings.setQuery(prefs.query().get());
        this.settings.setSort(prefs.sort().get());
        this.settings.setAmiiboSeriesFilter(prefs.filterAmiiboSeries().get());
        this.settings.setAmiiboTypeFilter(prefs.filterAmiiboType().get());
        this.settings.setCharacterFilter(prefs.filterCharacter().get());
        this.settings.setGameSeriesFilter(prefs.filterGameSeries().get());
        this.settings.setAmiiboView(prefs.browserAmiiboView().get());
        this.settings.setImageNetworkSettings(prefs.imageNetworkSetting().get());
        this.settings.setRecursiveEnabled(prefs.recursiveFolders().get());
        this.settings.setShowMissingFiles(prefs.showMissingFiles().get());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @AfterViews
    void afterViews() {
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        this.bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        this.bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    toggle.setImageResource(R.drawable.ic_expand_less_white_24dp);
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    toggle.setImageResource(R.drawable.ic_expand_more_white_24dp);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {

            }
        });

        hardwareInfo.setText(getString(R.string.amiiqo_sig,
                getIntent().getStringExtra(TagMo.EXTRA_SIGNATURE)));
        amiiqoBankCount.setValue(getIntent().getIntExtra(TagMo.EXTRA_BANK_COUNT, 1));
        amiibosView.setLayoutManager(new LinearLayoutManager(this));
        settings.setAmiiboFiles(getIntent().getParcelableArrayListExtra(TagMo.EXTRA_AMIIBO_FILES));
        updateAmiiqoList(getIntent().getStringArrayListExtra(TagMo.EXTRA_UNIT_DATA));

        showToast(getString(R.string.show_active_bank, getIntent().getIntExtra(
                TagMo.EXTRA_ACTIVE_BANK, prefs.amiiqoActiveBank().get())), Toast.LENGTH_LONG);
    }

    private void updateAmiiqoList(ArrayList<String> tagData) {
        AmiiboManager amiiboManager;
        try {
            amiiboManager = Util.loadAmiiboManager(this);
        } catch (IOException | JSONException | ParseException e) {
            e.printStackTrace();
            amiiboManager = null;
        }
        if (amiiboManager == null) return;
        ArrayList<Amiibo> amiibos = new ArrayList<>();
        for (int x = 0; x < tagData.size(); x++) {
            Amiibo amiibo = amiiboManager.amiibos.get(Util.hex2long(tagData.get(x)));
            if (amiibo != null) TagMo.Debug(TAG, amiibo.getName());
            amiibos.add(amiibo);
        }

        amiibosView.setAdapter(new AmiiqoContentAdapter(settings, this, amiibos));
    }

    ActivityResultLauncher<Intent> onNFCActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null)
            return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction()))
            return;

        byte[] tagData = result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);

        Bundle args = new Bundle();
        args.putByteArray(TagMo.BYTE_TAG_DATA, tagData);

        Intent intent = new Intent(this, AmiiboActivity_.class);
        intent.putExtras(args);

        startActivity(intent);
    });

    ActivityResultLauncher<Intent> onConfigureActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null)
            return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction()))
            return;

        int bank_count = result.getData().getIntExtra(
                TagMo.EXTRA_BANK_COUNT, prefs.amiiqoBankCount().get());
        ArrayList<String> tagData = result.getData().getStringArrayListExtra(TagMo.EXTRA_UNIT_DATA);

        prefs.amiiqoBankCount().put(bank_count);
        amiiqoBankCount.setValue(bank_count);
        updateAmiiqoList(tagData);
    });

    ActivityResultLauncher<Intent> onActivateActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null)
            return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction()))
            return;

        showToast(getString(R.string.show_active_bank, result.getData().getIntExtra(
                TagMo.EXTRA_ACTIVE_BANK, prefs.amiiqoBankCount().get())), Toast.LENGTH_LONG);
    });

    ActivityResultLauncher<Intent> onDeleteActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null)
            return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction()))
            return;

        ArrayList<String> tagData = result.getData().getStringArrayListExtra(TagMo.EXTRA_UNIT_DATA);

        updateAmiiqoList(tagData);
    });

    @Click(R.id.toggle)
    void onConfigExpandClick() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    @Click(R.id.write_all_banks)
    void onWriteAllBanksClick() {

    }

    @Click(R.id.write_bank_count)
    void onWriteBankCountClick() {
        Intent configure = new Intent(this, NfcActivity_.class);
        configure.setAction(TagMo.ACTION_CONFIGURE);
        configure.putExtra(TagMo.EXTRA_BANK_COUNT, amiiqoBankCount.getValue());
        onConfigureActivity.launch(configure);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    @Override
    public void onAmiiboLongClicked(Amiibo amiibo, int position) {
        View view = getLayoutInflater().inflate(R.layout.amiiqo_extended, null);
        AppCompatButton activate_button = view.findViewById(R.id.activate_bank);
        activate_button.setOnClickListener(v -> {
            Intent activate = new Intent(AmiiqoActivity.this, NfcActivity_.class);
            activate.setAction(TagMo.ACTION_ACTIVATE_BANK);
            activate.putExtra(TagMo.EXTRA_ACTIVE_BANK, TagWriter.getDisplayFromIndex(position));
            onActivateActivity.launch(activate);
        });
        AppCompatButton delete_button = view.findViewById(R.id.delete_bank);
        delete_button.setOnClickListener(v -> {
            Intent delete = new Intent(AmiiqoActivity.this, NfcActivity_.class);
            delete.setAction(TagMo.ACTION_DELETE_BANK);
            delete.putExtra(TagMo.EXTRA_ACTIVE_BANK, TagWriter.getDisplayFromIndex(position));
            delete.putExtra(TagMo.EXTRA_BANK_COUNT, amiiqoBankCount.getValue());
            onDeleteActivity.launch(delete);
        });
        AppCompatButton backup_button = view.findViewById(R.id.backup_amiibo);
        backup_button.setOnClickListener(v ->
                showToast(getString(R.string.feature_unavailable), Toast.LENGTH_LONG));

        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setView(view).show();
    }

    @Override
    public void onAmiiboClicked(Amiibo amiibo, int position) {
        boolean isAvailable = false;
        ArrayList<AmiiboFile> files = settings.getAmiiboFiles();
        for (int x = 0; x < files.size(); x ++) {
            if (files.get(x).getId() == amiibo.id) {
                byte[] tagData;
                try {
                    tagData = TagUtil.readTag(getContentResolver().openInputStream(
                            Uri.fromFile(files.get(x).getFilePath())));
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }

                Bundle args = new Bundle();
                args.putByteArray(TagMo.BYTE_TAG_DATA, tagData);

                Intent intent = new Intent(this, AmiiboActivity_.class);
                intent.putExtra(TagMo.EXTRA_ACTIVE_BANK, TagWriter.getDisplayFromIndex(position));
                intent.putExtras(args);

                startActivity(intent);
                isAvailable = true;
                break;
            }
        }

        if (!isAvailable && amiibo != null) {
            Intent amiiboIntent = new Intent(AmiiqoActivity.this, NfcActivity_.class);
            amiiboIntent.putExtra(TagMo.EXTRA_ACTIVE_BANK, TagWriter.getDisplayFromIndex(position));
            amiiboIntent.setAction(TagMo.ACTION_SCAN_TAG);
            onNFCActivity.launch(amiiboIntent);
        }
    }

    @Override
    public void onAmiiboImageClicked(Amiibo amiibo, int position) {
        if (amiibo != null) {
            Bundle bundle = new Bundle();
            bundle.putLong(ImageActivity.INTENT_EXTRA_AMIIBO_ID, amiibo.id);

            Intent intent = new Intent(this, ImageActivity_.class);
            intent.putExtras(bundle);

            this.startActivity(intent);
        }
    }

    @UiThread
    public void showToast(String msg, int length) {
        Toast.makeText(this, msg, length).show();
    }
}
