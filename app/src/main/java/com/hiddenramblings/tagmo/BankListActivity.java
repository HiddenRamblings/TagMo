package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.eightbit.io.Debug;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.hiddenramblings.tagmo.adapter.BankBrowserAdapter;
import com.hiddenramblings.tagmo.adapter.WriteBlankAdapter;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.nfctech.TagReader;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.BrowserSettings.BrowserSettingsListener;
import com.hiddenramblings.tagmo.settings.SettingsFragment;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.api.BackgroundExecutor;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_bank_list)
public class BankListActivity extends AppCompatActivity implements
        BankBrowserAdapter.OnAmiiboClickListener {

    @ViewById(R.id.amiibos_list)
    RecyclerView amiibosView;
    @ViewById(R.id.hardware_info)
    TextView hardwareInfo;
    @ViewById(R.id.bottom_sheet)
    ViewGroup bottomSheet;
    @ViewById(R.id.toggle)
    ImageView toggle;

    @ViewById(R.id.amiiboCard)
    CardView amiiboCard;
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
    @ViewById(R.id.txtBank)
    TextView txtBank;
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

    @ViewById(R.id.elite_bank_stats)
    TextView bankStats;
    @ViewById(R.id.bank_count_picker)
    BankNumberPicker eliteBankCount;
    @ViewById(R.id.write_open_banks)
    AppCompatButton writeOpenBanks;
    @ViewById(R.id.write_bank_count)
    AppCompatButton writeBankCount;

    BottomSheetBehavior<View> bottomSheetBehavior;

    ArrayList<Amiibo> amiibos = new ArrayList<>();
    ArrayList<AmiiboFile> amiiboFiles;

    private int clickedPosition;
    private enum CLICKED {
        NOTHING,
        WRITER,
        EDITOR,
        HEXCODE,
        BACKUP,
        FORMAT
    }
    private CLICKED status = CLICKED.NOTHING;

    @InstanceState
    BrowserSettings settings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.settings = new BrowserSettings().initialize();
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
                    amiiboCard.setVisibility(View.GONE);
                    toggle.setImageResource(R.drawable.ic_expand_less_white_24dp);
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    toggle.setImageResource(R.drawable.ic_expand_more_white_24dp);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                int bottomHeight = bottomSheet.getMeasuredHeight()
                        - bottomSheetBehavior.getPeekHeight();
                View mainLayout = findViewById(R.id.main_layout);
                mainLayout.setPadding(0, 0, 0, slideOffset > 0
                        ? (int) (bottomHeight * slideOffset) : 0);
                amiibosView.smoothScrollToPosition(clickedPosition);
            }
        });
        toolbar.inflateMenu(R.menu.elite_menu);

        loadAmiiboFiles(settings.getBrowserRootFolder(), settings.isRecursiveEnabled());

        int bank_count = getIntent().getIntExtra(TagMo.EXTRA_BANK_COUNT,
                TagMo.getPrefs().eliteBankCount().get());
        int active_bank = getIntent().getIntExtra(TagMo.EXTRA_ACTIVE_BANK,
                TagMo.getPrefs().eliteActiveBank().get());

        hardwareInfo.setText(getString(R.string.elite_signature,
                getIntent().getStringExtra(TagMo.EXTRA_SIGNATURE)));
        eliteBankCount.setValue(bank_count);
        amiibosView.setLayoutManager(new LinearLayoutManager(this));
        BankBrowserAdapter adapter = new BankBrowserAdapter(settings, this);
        amiibosView.setAdapter(adapter);
        this.settings.addChangeListener(adapter);
        updateEliteHardwareAdapter(getIntent().getStringArrayListExtra(TagMo.EXTRA_AMIIBO_DATA));
        bankStats.setText(getString(R.string.elite_bank_stats,
                eliteBankCount.getValueForPosition(active_bank), bank_count));
        writeOpenBanks.setText(getString(R.string.write_open_banks, bank_count));

        eliteBankCount.setOnValueChangedListener((numberPicker, valueOld, valueNew)
                -> writeOpenBanks.setText(getString(R.string.write_open_banks, valueNew)));
    }

    @SuppressLint("NotifyDataSetChanged")
    private void refreshEliteHardwareAdapter() {
        if (amiibosView.getAdapter() != null) {
            this.runOnUiThread(() -> {
                ((BankBrowserAdapter) amiibosView.getAdapter()).setAmiibos(amiibos);
                amiibosView.getAdapter().notifyDataSetChanged();
            });
        }
    }

    private void updateEliteHardwareAdapter(ArrayList<String> amiiboList) {
        AmiiboManager amiiboManager;
        try {
            amiiboManager = AmiiboManager.getAmiiboManager();
        } catch (IOException | JSONException | ParseException e) {
            Debug.Error(e);
            amiiboManager = null;
        }
        if (amiiboManager == null) return;
        final AmiiboManager uiAmiiboManager = amiiboManager;
        this.runOnUiThread(() -> {
            settings.setAmiiboManager(uiAmiiboManager);
            settings.notifyChanges();
        });

        if (amiibos.isEmpty()) {
            for (int x = 0; x < amiiboList.size(); x++) {
                amiibos.add(amiiboManager.amiibos.get(TagUtils.hexToLong(amiiboList.get(x))));
            }
        } else {
            for (int x = 0; x < amiiboList.size(); x++) {
                if (amiibos.get(x) == null || amiibos.get(x).bank != x) {
                    amiibos.set(x, amiiboManager.amiibos.get(TagUtils.hexToLong(amiiboList.get(x))));
                }
            }
        }
        refreshEliteHardwareAdapter();
    }

    @Click(R.id.toggle)
    void onConfigExpandClick() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    ActivityResultLauncher<Intent> onWriteBanksActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        int bank_count = result.getData().getIntExtra(TagMo.EXTRA_BANK_COUNT,
                TagMo.getPrefs().eliteBankCount().get());

        TagMo.getPrefs().eliteBankCount().put(bank_count);

        eliteBankCount.setValue(bank_count);
        updateEliteHardwareAdapter(result.getData().getStringArrayListExtra(TagMo.EXTRA_AMIIBO_DATA));
        bankStats.setText(getString(R.string.elite_bank_stats, eliteBankCount.getValueForPosition(
                TagMo.getPrefs().eliteActiveBank().get()), bank_count));
        writeOpenBanks.setText(getString(R.string.write_open_banks, bank_count));
    });

    private void writeAmiiboCollection(ArrayList<AmiiboFile> amiiboList, Dialog writeDialog) {
        if (amiiboList != null && amiiboList.size() == eliteBankCount.getValue()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.write_confirm)
                    .setPositiveButton(R.string.proceed, (dialog, which) -> {
                        Intent collection = new Intent(this, NfcActivity_.class);
                        collection.setAction(TagMo.ACTION_WRITE_ALL_TAGS);
                        collection.putExtra(TagMo.EXTRA_BANK_COUNT, eliteBankCount.getValue());
                        collection.putExtra(TagMo.EXTRA_AMIIBO_FILES, amiiboList);
                        onWriteBanksActivity.launch(collection);
                        dialog.dismiss();
                        writeDialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> {
                        amiiboList.clear();
                        dialog.dismiss();
                    })
                    .show();
        }
    }

    @Click(R.id.write_open_banks)
    void onWriteOpenBanksClick() {
        View view = getLayoutInflater().inflate( R.layout.dialog_write_banks, null);

        RecyclerView writerListView = view.findViewById(R.id.amiibos_list);
        writerListView.setLayoutManager(new LinearLayoutManager(this));

        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        Dialog writeDialog = dialog.setView(view).show();

        WriteBlankAdapter.OnHighlightListener itemClick =
                new WriteBlankAdapter.OnHighlightListener() {
            @Override
            public void onAmiiboClicked(ArrayList<AmiiboFile> amiiboList) {
                writeAmiiboCollection(amiiboList, writeDialog);
            }

            @Override
            public void onAmiiboImageClicked(ArrayList<AmiiboFile> amiiboList) {
                writeAmiiboCollection(amiiboList, writeDialog);
            }
        };

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = view.findViewById(R.id.amiibo_search);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setSubmitButtonEnabled(true);
        searchView.setIconifiedByDefault(false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                settings.setQuery(query);
                settings.notifyChanges();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                settings.setQuery(query);
                settings.notifyChanges();
                return true;
            }
        });

        writerListView.setAdapter(new WriteBlankAdapter(settings, itemClick, amiiboFiles));
        this.settings.addChangeListener((BrowserSettingsListener) writerListView.getAdapter());
        writeDialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );

        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    @Click(R.id.write_bank_count)
    void onWriteBankCountClick() {
        if (TagMo.getPrefs().eliteActiveBank().get() >= eliteBankCount.getValue()) {
            showToast(R.string.fail_active_oob);
            return;
        }
        Intent configure = new Intent(this, NfcActivity_.class);
        configure.setAction(TagMo.ACTION_SET_BANK_COUNT);
        configure.putExtra(TagMo.EXTRA_BANK_COUNT, eliteBankCount.getValue());
        onWriteBanksActivity.launch(configure);

        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    private void displayBackupDialog(byte[] tagData) {
        View view = getLayoutInflater().inflate(R.layout.dialog_backup, null);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        final EditText input = view.findViewById(R.id.backup_entry);
        input.setText(TagReader.generateFileName(settings.getAmiiboManager(), tagData));
        Dialog backupDialog = dialog.setView(view).show();
        view.findViewById(R.id.save_backup).setOnClickListener(v -> {
            try {
                File directory = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                String fileName = TagReader.writeBytesToFile(directory,
                        input.getText().toString(), tagData);
                showToast(getString(R.string.wrote_file, fileName));
            } catch (IOException e) {
                showToast(e.getMessage());
            }
            backupDialog.dismiss();
        });
        view.findViewById(R.id.cancel_backup).setOnClickListener(v -> backupDialog.dismiss());
    }

    ActivityResultLauncher<Intent> onUpdateTagResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())
                && !TagMo.ACTION_EDIT_COMPLETE.equals(result.getData().getAction())) return;

        if (result.getData().hasExtra(TagMo.EXTRA_TAG_DATA)) {
            byte[] tagData = result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
            if (amiibos.get(clickedPosition) != null)
                amiibos.get(clickedPosition).data = tagData;
            updateAmiiboView(tagData, clickedPosition);
        }

        if (result.getData().hasExtra(TagMo.EXTRA_AMIIBO_DATA))
            updateEliteHardwareAdapter(result.getData().getStringArrayListExtra(
                    TagMo.EXTRA_AMIIBO_DATA));

        if (status == CLICKED.FORMAT) {
            status = CLICKED.NOTHING;
            this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            amiibos.set(clickedPosition, null);
        }
    });

    private void writeAmiiboFile(AmiiboFile amiiboFile, int position) {
        Bundle args = new Bundle();
        try {
            args.putByteArray(TagMo.EXTRA_TAG_DATA,
                    TagReader.readTagStream(amiiboFile.getFilePath()));
        } catch (Exception e) {
            Debug.Error(e);
        }

        Intent intent = new Intent(this, NfcActivity_.class);
        intent.setAction(TagMo.ACTION_WRITE_TAG_FULL);
        intent.putExtra(TagMo.EXTRA_CURRENT_BANK, position);
        intent.putExtras(args);
        onUpdateTagResult.launch(intent);
    }

    private void displayWriteDialog(int position) {
        View view = getLayoutInflater().inflate( R.layout.dialog_write_banks, null);

        RecyclerView writerListView = view.findViewById(R.id.amiibos_list);
        writerListView.setLayoutManager(new LinearLayoutManager(this));

        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        Dialog writeDialog = dialog.setView(view).show();

        WriteBlankAdapter.OnAmiiboClickListener itemClick =
                new WriteBlankAdapter.OnAmiiboClickListener() {
                    @Override
                    public void onAmiiboClicked(AmiiboFile amiiboFile) {
                        if (amiiboFile != null) {
                            writeAmiiboFile(amiiboFile, position);
                            writeDialog.dismiss();
                        }
                    }

                    @Override
                    public void onAmiiboImageClicked(AmiiboFile amiiboFile) {
                        if (amiiboFile != null) {
                            writeAmiiboFile(amiiboFile, position);
                            writeDialog.dismiss();
                        }
                    }
                };

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = view.findViewById(R.id.amiibo_search);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setSubmitButtonEnabled(true);
        searchView.setIconifiedByDefault(false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                settings.setQuery(query);
                settings.notifyChanges();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                settings.setQuery(query);
                settings.notifyChanges();
                return true;
            }
        });

        writerListView.setAdapter(new WriteBlankAdapter(settings, itemClick, amiiboFiles));
        this.settings.addChangeListener((BrowserSettingsListener) writerListView.getAdapter());
        writeDialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    ActivityResultLauncher<Intent> onActivateActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        int active_bank = result.getData().getIntExtra(TagMo.EXTRA_ACTIVE_BANK,
                TagMo.getPrefs().eliteActiveBank().get());

        TagMo.getPrefs().eliteActiveBank().put(active_bank);

        refreshEliteHardwareAdapter();
        int bank_count = TagMo.getPrefs().eliteBankCount().get();
        bankStats.setText(getString(R.string.elite_bank_stats,
                eliteBankCount.getValueForPosition(active_bank), bank_count));
        writeOpenBanks.setText(getString(R.string.write_open_banks, bank_count));
    });

    ActivityResultLauncher<Intent> onScanTagResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        byte[] tagData = result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
        Bundle args = new Bundle();
        args.putByteArray(TagMo.EXTRA_TAG_DATA, tagData);
        if (amiibos.get(clickedPosition) != null)
            amiibos.get(clickedPosition).data = tagData;
        switch (status) {
            case NOTHING:
                break;
            case WRITER:
                Intent modify = new Intent(this, NfcActivity_.class);
                modify.setAction(TagMo.ACTION_WRITE_TAG_FULL);
                modify.putExtra(TagMo.EXTRA_CURRENT_BANK, clickedPosition);
                onUpdateTagResult.launch(modify.putExtras(args));
                break;
            case EDITOR:
                onUpdateTagResult.launch(new Intent(this,
                        TagDataActivity_.class).putExtras(args));
                break;
            case HEXCODE:
                startActivity(new Intent(this,
                        HexViewerActivity_.class).putExtras(args));
                break;
            case BACKUP:
                displayBackupDialog(tagData);
                break;

        }
        status = CLICKED.NOTHING;
        updateAmiiboView(tagData, clickedPosition);
        refreshEliteHardwareAdapter();
    });

    private void scanAmiiboData(int current_bank) {
        Intent scan = new Intent(this, NfcActivity_.class);
        scan.setAction(TagMo.ACTION_SCAN_TAG);
        scan.putExtra(TagMo.EXTRA_CURRENT_BANK, current_bank);
        onScanTagResult.launch(scan);
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

    public void updateAmiiboView(byte[] tagData, long amiiboId, int current_bank) {
        toolbar.setOnMenuItemClickListener(item -> {
            Intent modify = new Intent(this, NfcActivity_.class);
            modify.putExtra(TagMo.EXTRA_CURRENT_BANK, current_bank);
            switch (item.getItemId()) {
                case R.id.mnu_activate:
                    modify.setAction(TagMo.ACTION_ACTIVATE_BANK);
                    onActivateActivity.launch(modify);
                    return true;
                case R.id.mnu_write:
                    if (tagData != null) {
                        modify.setAction(TagMo.ACTION_WRITE_TAG_FULL);
                        modify.putExtra(TagMo.EXTRA_TAG_DATA, tagData);
                        onUpdateTagResult.launch(modify);
                    } else {
                        status = CLICKED.WRITER;
                        scanAmiiboData(current_bank);
                    }
                    return true;
                case R.id.mnu_replace:
                    displayWriteDialog(current_bank);
                    return true;
                case R.id.mnu_format_bank:
                    if (TagMo.getPrefs().eliteActiveBank().get() == current_bank) {
                        showToast(R.string.delete_active);
                    } else {
                        modify.setAction(TagMo.ACTION_FORMAT_BANK);
                        onUpdateTagResult.launch(modify);
                        status = CLICKED.FORMAT;
                    }
                    return true;
                case R.id.mnu_edit:
                    if (tagData != null) {
                        Intent editor = new Intent(this, TagDataActivity_.class);
                        editor.putExtra(TagMo.EXTRA_TAG_DATA, tagData);
                        onUpdateTagResult.launch(editor);
                    } else {
                        status = CLICKED.EDITOR;
                        scanAmiiboData(current_bank);
                    }
                    return true;
                case R.id.mnu_view_hex:
                    if (tagData != null) {
                        Intent viewhex = new Intent(this, HexViewerActivity_.class);
                        viewhex.putExtra(TagMo.EXTRA_TAG_DATA, tagData);
                        startActivity(viewhex);
                    } else {
                        status = CLICKED.HEXCODE;
                        scanAmiiboData(current_bank);
                    }
                    return true;
                case R.id.mnu_backup:
                    if (tagData != null) {
                    displayBackupDialog(tagData);
                    } else {
                        status = CLICKED.BACKUP;
                        scanAmiiboData(current_bank);
                    }
                    return true;
                case R.id.mnu_scan:
                    scanAmiiboData(current_bank);
                    return true;

            }
            return false;
        });

        int selected_item = eliteBankCount.getValueForPosition(current_bank);
        toolbar.getMenu().findItem(R.id.mnu_scan).setTitle(getString(R.string.scan_bank,
                new DecimalFormat("000").format(selected_item)));
        String amiiboBank = getString(R.string.bank_number, selected_item);

        String tagInfo = null;
        String amiiboHexId = "";
        String amiiboName = "";
        String amiiboSeries = "";
        String amiiboType = "";
        String gameSeries = "";
        // String character = "";
        String amiiboImageUrl;

        Amiibo amiibo = amiibos.get(current_bank);
        if (amiibo == null) {
            if (tagData != null) {
                try {
                    amiiboId = TagUtils.amiiboIdFromTag(tagData);
                } catch (Exception e) {
                    Debug.Log(e);
                }
            }
            if (amiiboId == -1) {
                tagInfo = getString(R.string.read_error);
            } else if (amiiboId == 0) {
                tagInfo = getString(R.string.blank_tag);
            } else {
                if (settings.getAmiiboManager() != null) {
                    amiibo = settings.getAmiiboManager().amiibos.get(amiiboId);
                    if (amiibo == null)
                        amiibo = new Amiibo(settings.getAmiiboManager(), amiiboId, null, null);
                }
            }
        }

        if (amiibo != null) {
            amiiboImageUrl = amiibo.getImageUrl();
            amiiboHexId = TagUtils.amiiboIdToHex(amiibo.id);
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

        boolean hasTagInfo = tagInfo != null;

        if (hasTagInfo) {
            setAmiiboInfoText(txtError, tagInfo, false);
            amiiboInfo.setVisibility(View.GONE);
        } else {
            txtError.setVisibility(View.GONE);
            amiiboInfo.setVisibility(View.VISIBLE);
        }
        setAmiiboInfoText(txtBank, amiiboBank, hasTagInfo);
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
            final long amiiboTagId = amiiboId;
            imageAmiibo.setOnClickListener(view -> {
                if (amiiboTagId == -1) {
                    return;
                }

                Bundle bundle = new Bundle();
                bundle.putLong(TagMo.EXTRA_AMIIBO_ID, amiiboTagId);

                Intent intent = new Intent(this, ImageActivity_.class);
                intent.putExtras(bundle);

                startActivity(intent);
            });
        }
    }

    public void updateAmiiboView(long amiiboId, int current_bank) {
        updateAmiiboView(null, amiiboId, current_bank);
    }

    public void updateAmiiboView(byte[] tagData, int current_bank) {
        updateAmiiboView(tagData, -1, current_bank);
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
        String imageNetworkSetting = settings.getImageNetworkSettings();
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
    public void onAmiiboClicked(Amiibo amiibo, int position) {
        if (amiibo == null) {
            displayWriteDialog(position);
            return;
        }
        clickedPosition = position;
        status = CLICKED.NOTHING;
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        amiiboCard.setVisibility(View.VISIBLE);
        if (amiibo.data != null && amiibo.bank == position) {
            updateAmiiboView(amiibo.data, position);
        } else if (amiibo.id != 0) {
            updateAmiiboView(amiibo.id, position);
        } else {
            Intent amiiboIntent = new Intent(this, NfcActivity_.class);
            amiiboIntent.putExtra(TagMo.EXTRA_CURRENT_BANK, position);
            amiiboIntent.setAction(TagMo.ACTION_SCAN_TAG);
            onUpdateTagResult.launch(amiiboIntent);
        }
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onAmiiboImageClicked(Amiibo amiibo, int position) {
        if (amiibo != null) {
            Bundle bundle = new Bundle();
            bundle.putLong(TagMo.EXTRA_AMIIBO_ID, amiibo.id);

            Intent intent = new Intent(this, ImageActivity_.class);
            intent.putExtras(bundle);

            startActivity(intent);
        }
    }

    public static final String BACKGROUND_AMIIBO_FILES = "amiibo_files";

    void loadAmiiboFiles(File rootFolder, boolean recursiveFiles) {
        BackgroundExecutor.cancelAll(BACKGROUND_AMIIBO_FILES, true);
        loadAmiiboFilesTask(rootFolder, recursiveFiles);
    }

    @Background(id = BACKGROUND_AMIIBO_FILES)
    void loadAmiiboFilesTask(File rootFolder, boolean recursiveFiles) {
        amiiboFiles = listAmiibos(rootFolder, recursiveFiles);
    }

    ArrayList<AmiiboFile> listAmiibos(File rootFolder, boolean recursiveFiles) {
        ArrayList<AmiiboFile> amiiboFiles = new ArrayList<>();

        File[] files = rootFolder.listFiles();
        if (files == null)
            return amiiboFiles;

        for (File file : files) {
            if (file.isDirectory() && recursiveFiles) {
                amiiboFiles.addAll(listAmiibos(file, true));
            } else {
                try {
                    byte[] data = TagReader.readTagFile(file);
                    TagReader.validateTag(data);
                    amiiboFiles.add(new AmiiboFile(file, TagUtils.amiiboIdFromTag(data)));
                } catch (Exception e) {
                    Debug.Log(e);
                }
            }
        }
        return amiiboFiles;
    }

    @UiThread
    public void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @UiThread
    public void showToast(int msgRes) {
        Toast.makeText(this, msgRes, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            super.onBackPressed();
        }
    }
}
