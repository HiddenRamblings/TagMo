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
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.hiddenramblings.tagmo.adapter.BankListBrowserAdapter;
import com.hiddenramblings.tagmo.adapter.WriteBanksAdapter;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.BrowserSettings.VIEW;
import com.hiddenramblings.tagmo.settings.SettingsFragment;
import com.hiddenramblings.tagmo.widget.BankPicker;
import com.hiddenramblings.tagmo.widget.Toasty;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.api.BackgroundExecutor;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_bank_list)
public class BankListActivity extends AppCompatActivity implements
        BankListBrowserAdapter.OnAmiiboClickListener {

    @ViewById(R.id.amiibos_list)
    RecyclerView amiibosView;
    @ViewById(R.id.hardware_info)
    TextView hardwareInfo;
    @ViewById(R.id.bottom_sheet)
    ViewGroup bottomSheet;
    @ViewById(R.id.toggle)
    ImageView toggle;

    @ViewById(R.id.write_banks_layout)
    RelativeLayout writeBankLayout;
    @ViewById(R.id.amiibo_files_list)
    RecyclerView amiiboFilesView;
    @ViewById(R.id.amiibo_search)
    SearchView searchView;

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
    BankPicker eliteBankCount;
    @ViewById(R.id.write_open_banks)
    AppCompatButton writeOpenBanks;
    @ViewById(R.id.erase_open_banks)
    AppCompatButton eraseOpenBanks;
    @ViewById(R.id.write_bank_count)
    AppCompatButton writeBankCount;

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private KeyManager keyManager;

    private final ArrayList<Amiibo> amiibos = new ArrayList<>();
    private WriteBanksAdapter writeFileAdapter;
    private WriteBanksAdapter writeListAdapter;

    private int clickedPosition;
    private enum CLICKED {
        NOTHING,
        WRITE_DATA,
        EDIT_DATA,
        HEX_CODE,
        BANK_BACKUP,
        VERIFY_TAG,
        ERASE_BANK
    }
    private CLICKED status = CLICKED.NOTHING;

    @InstanceState
    BrowserSettings settings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keyManager = new KeyManager(this);
    }

    @AfterViews
    void afterViews() {
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        this.settings = new BrowserSettings().initialize();

        this.bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        this.bottomSheetBehavior.addBottomSheetCallback(
                new BottomSheetBehavior.BottomSheetCallback() {
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
                int bottomHeight = bottomSheet.getMeasuredHeight()
                        - bottomSheetBehavior.getPeekHeight();
                ViewGroup mainLayout = findViewById(R.id.main_layout);
                mainLayout.setPadding(0, 0, 0, slideOffset > 0
                        ? (int) (bottomHeight * slideOffset) : 0);
                if (slideOffset > 0)
                    amiibosView.smoothScrollToPosition(clickedPosition);
            }
        });
        toolbar.inflateMenu(R.menu.bank_menu);

        int bank_count = getIntent().getIntExtra(TagMo.EXTRA_BANK_COUNT,
                TagMo.getPrefs().eliteBankCount().get());
        int active_bank = getIntent().getIntExtra(TagMo.EXTRA_ACTIVE_BANK,
                TagMo.getPrefs().eliteActiveBank().get());

        hardwareInfo.setText(getString(R.string.elite_signature,
                getIntent().getStringExtra(TagMo.EXTRA_SIGNATURE)));
        eliteBankCount.setValue(bank_count);
        if (settings.getAmiiboView() == VIEW.IMAGE.getValue())
            amiibosView.setLayoutManager(new GridLayoutManager(this, getColumnCount()));
        else
            amiibosView.setLayoutManager(new LinearLayoutManager(this));
        BankListBrowserAdapter adapter = new BankListBrowserAdapter(settings, this);
        amiibosView.setAdapter(adapter);
        this.settings.addChangeListener(adapter);
        updateEliteHardwareAdapter(getIntent().getStringArrayListExtra(TagMo.EXTRA_AMIIBO_LIST));
        bankStats.setText(getString(R.string.elite_bank_stats,
                eliteBankCount.getValueForPosition(active_bank), bank_count));
        writeOpenBanks.setText(getString(R.string.write_open_banks, bank_count));
        eraseOpenBanks.setText(getString(R.string.erase_open_banks, bank_count));

        eliteBankCount.setOnValueChangedListener((numberPicker, valueOld, valueNew) -> {
            writeOpenBanks.setText(getString(R.string.write_open_banks, valueNew));
            eraseOpenBanks.setText(getString(R.string.erase_open_banks, valueNew));
        });

        this.loadAmiiboFiles(settings.getBrowserRootFolder(), settings.isRecursiveEnabled());

        if (settings.getAmiiboView() == VIEW.IMAGE.getValue())
            amiiboFilesView.setLayoutManager(new GridLayoutManager(this, getColumnCount()));
        else
            amiiboFilesView.setLayoutManager(new LinearLayoutManager(this));

        writeFileAdapter = new WriteBanksAdapter(settings,
                new WriteBanksAdapter.OnAmiiboClickListener() {
            @Override
            public void onAmiiboClicked(AmiiboFile amiiboFile) {
                if (null != amiiboFile) {
                    writeAmiiboFile(amiiboFile, clickedPosition);
                }
            }

            @Override
            public void onAmiiboImageClicked(AmiiboFile amiiboFile) {
                if (null != amiiboFile) {
                    writeAmiiboFile(amiiboFile, clickedPosition);
                }
            }
        });
        this.settings.addChangeListener(writeFileAdapter);

        writeListAdapter = new WriteBanksAdapter(settings, this::writeAmiiboCollection);
        this.settings.addChangeListener(writeListAdapter);

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
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
    }

    private void updateEliteHardwareAdapter(ArrayList<String> amiiboList) {
        AmiiboManager amiiboManager;
        try {
            amiiboManager = AmiiboManager.getAmiiboManager();
        } catch (IOException | JSONException | ParseException e) {
            Debug.Log(e);
            amiiboManager = null;
        }
        if (null == amiiboManager) return;
        final AmiiboManager uiAmiiboManager = amiiboManager;
        this.runOnUiThread(() -> {
            settings.setAmiiboManager(uiAmiiboManager);
            settings.notifyChanges();
        });

        if (amiibos.isEmpty()) {
            if (null != amiibosView.getAdapter())
                ((BankListBrowserAdapter) amiibosView.getAdapter()).setAmiibos(amiibos);
            for (int x = 0; x < amiiboList.size(); x++) {
                amiibos.add(amiiboManager.amiibos.get(TagUtils.hexToLong(amiiboList.get(x))));
                if (null != amiibosView.getAdapter())
                    amiibosView.getAdapter().notifyItemInserted(x);
            }
        } else {
            for (int x = 0; x < amiiboList.size(); x++) {
                long amiiboId = TagUtils.hexToLong(amiiboList.get(x));
                if (x >= amiibos.size()) {
                    amiibos.add(amiiboManager.amiibos.get(TagUtils.hexToLong(amiiboList.get(x))));
                    if (null != amiibosView.getAdapter())
                        amiibosView.getAdapter().notifyItemInserted(x);
                } else if (null == amiibos.get(x) || amiibos.get(x).bank != x
                        || amiiboId != amiibos.get(x).id) {
                    amiibos.set(x, amiiboManager.amiibos.get(amiiboId));
                    if (null != amiibosView.getAdapter())
                        amiibosView.getAdapter().notifyItemChanged(x);
                }
            }
            if (amiibos.size() > amiiboList.size()) {
                int count = amiibos.size();
                int size = amiiboList.size();
                ArrayList<Amiibo> shortList = new ArrayList<>();
                for (int x = 0; x < size; x++) {
                    shortList.add(amiibos.get(x));
                }
                amiibos.clear();
                amiibos.addAll(shortList);
                if (null != amiibosView.getAdapter()) {
                    amiibosView.getAdapter().notifyItemRangeChanged(0, size);
                    amiibosView.getAdapter().notifyItemRangeRemoved(size, count - size);
                }
            }
        }
    }

    private void onBottomSheetChanged(boolean isMenu, boolean hasAmiibo) {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        amiiboCard.setVisibility(isMenu && hasAmiibo ? View.VISIBLE : View.GONE);
        if (isMenu) writeListAdapter.resetSelections();
        writeBankLayout.setVisibility(isMenu ? View.GONE : View.VISIBLE);
        writeOpenBanks.setVisibility(isMenu ? View.VISIBLE : View.GONE);
        eraseOpenBanks.setVisibility(isMenu ? View.VISIBLE : View.GONE);
        eliteBankCount.setVisibility(isMenu ? View.VISIBLE : View.GONE);
        writeBankCount.setVisibility(isMenu ? View.VISIBLE : View.GONE);
    }

    @Click(R.id.toggle)
    void onConfigExpandClick() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    ActivityResultLauncher<Intent> onOpenBanksActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || null == result.getData()) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        int bank_count = result.getData().getIntExtra(TagMo.EXTRA_BANK_COUNT,
                TagMo.getPrefs().eliteBankCount().get());

        TagMo.getPrefs().eliteBankCount().put(bank_count);

        eliteBankCount.setValue(bank_count);
        updateEliteHardwareAdapter(result.getData().getStringArrayListExtra(TagMo.EXTRA_AMIIBO_LIST));
        bankStats.setText(getString(R.string.elite_bank_stats, eliteBankCount.getValueForPosition(
                TagMo.getPrefs().eliteActiveBank().get()), bank_count));
        writeOpenBanks.setText(getString(R.string.write_open_banks, bank_count));
        eraseOpenBanks.setText(getString(R.string.erase_open_banks, bank_count));
    });

    private void writeAmiiboCollection(ArrayList<AmiiboFile> amiiboList) {
        if (null != amiiboList && amiiboList.size() == eliteBankCount.getValue()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.write_confirm)
                    .setPositiveButton(R.string.proceed, (dialog, which) -> {
                        Intent collection = new Intent(this, NfcActivity_.class);
                        collection.setAction(TagMo.ACTION_WRITE_ALL_TAGS);
                        collection.putExtra(TagMo.EXTRA_BANK_COUNT, eliteBankCount.getValue());
                        collection.putExtra(TagMo.EXTRA_AMIIBO_FILES, amiiboList);
                        onOpenBanksActivity.launch(collection);
                        onBottomSheetChanged(true, false);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> {
                        amiiboList.clear();
                        onBottomSheetChanged(true, false);
                        dialog.dismiss();
                    })
                    .show();
        }
    }

    @Click(R.id.write_open_banks)
    void onWriteOpenBanksClick() {
        onBottomSheetChanged(false, false);
        amiiboFilesView.setAdapter(writeListAdapter);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Click(R.id.erase_open_banks)
    void onEraseOpenBanksClick() {
        Intent collection = new Intent(this, NfcActivity_.class);
        collection.setAction(TagMo.ACTION_WRITE_ALL_TAGS);
        collection.putExtra(TagMo.EXTRA_BANK_COUNT, eliteBankCount.getValue());
        onOpenBanksActivity.launch(collection);
    }

    @Click(R.id.write_bank_count)
    void onWriteBankCountClick() {
        if (TagMo.getPrefs().eliteActiveBank().get() >= eliteBankCount.getValue()) {
            new Toasty(this).Short(R.string.fail_active_oob);
            onBottomSheetChanged(true, false);
            return;
        }
        Intent configure = new Intent(this, NfcActivity_.class);
        configure.setAction(TagMo.ACTION_SET_BANK_COUNT);
        configure.putExtra(TagMo.EXTRA_BANK_COUNT, eliteBankCount.getValue());
        onOpenBanksActivity.launch(configure);

        onBottomSheetChanged(true, false);
    }

    private void displayBackupDialog(byte[] tagData) {
        View view = getLayoutInflater().inflate(R.layout.dialog_backup, null);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        final EditText input = view.findViewById(R.id.backup_entry);
        input.setText(TagUtils.decipherFilename(settings.getAmiiboManager(), tagData));
        Dialog backupDialog = dialog.setView(view).show();
        view.findViewById(R.id.save_backup).setOnClickListener(v -> {
            try {
                File directory = Storage.getDownloadDir("TagMo", "Backups");
                String fileName = TagUtils.writeBytesToFile(directory,
                        input.getText().toString(), tagData);
                new Toasty(this).Long(getString(R.string.wrote_file, fileName));
                this.loadAmiiboFiles(settings.getBrowserRootFolder(),
                        settings.isRecursiveEnabled());
            } catch (IOException e) {
                new Toasty(this).Short(e.getMessage());
            }
            backupDialog.dismiss();
        });
        view.findViewById(R.id.cancel_backup).setOnClickListener(v -> backupDialog.dismiss());
    }

    ActivityResultLauncher<Intent> onUpdateTagResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || null == result.getData()) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())
                && !TagMo.ACTION_EDIT_COMPLETE.equals(result.getData().getAction())) return;

        if (result.getData().hasExtra(TagMo.EXTRA_TAG_DATA)) {
            byte[] tagData = result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
            if (null != amiibos.get(clickedPosition))
                amiibos.get(clickedPosition).data = tagData;
            updateAmiiboView(tagData, -1, clickedPosition);
        }

        if (result.getData().hasExtra(TagMo.EXTRA_AMIIBO_LIST)) {
            updateEliteHardwareAdapter(result.getData().getStringArrayListExtra(
                    TagMo.EXTRA_AMIIBO_LIST));
        }

        if (status == CLICKED.ERASE_BANK) {
            status = CLICKED.NOTHING;
            onBottomSheetChanged(true, false);
            amiibos.set(clickedPosition, null);
        }
    });

    private void writeAmiiboFile(AmiiboFile amiiboFile, int position) {
        Bundle args = new Bundle();
        try {
            byte[] data = null != amiiboFile.getData() ? amiiboFile.getData()
                    : TagUtils.getValidatedFile(keyManager, amiiboFile.getFilePath());
            args.putByteArray(TagMo.EXTRA_TAG_DATA, data);
        } catch (Exception e) {
            Debug.Log(e);
        }

        Intent intent = new Intent(this, NfcActivity_.class);
        intent.setAction(TagMo.ACTION_WRITE_TAG_FULL);
        intent.putExtra(TagMo.EXTRA_CURRENT_BANK, position);
        intent.putExtras(args);
        onUpdateTagResult.launch(intent);
        onBottomSheetChanged(true, true);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void displayWriteDialog(int position) {
        onBottomSheetChanged(false, false);
        writeFileAdapter.setListener(new WriteBanksAdapter.OnAmiiboClickListener() {
            @Override
            public void onAmiiboClicked(AmiiboFile amiiboFile) {
                if (null != amiiboFile) {
                    writeAmiiboFile(amiiboFile, position);
                }
            }

            @Override
            public void onAmiiboImageClicked(AmiiboFile amiiboFile) {
                if (null != amiiboFile) {
                    writeAmiiboFile(amiiboFile, position);
                }
            }
        });
        amiiboFilesView.setAdapter(writeFileAdapter);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    ActivityResultLauncher<Intent> onActivateActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || null == result.getData()) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        int active_bank = result.getData().getIntExtra(TagMo.EXTRA_ACTIVE_BANK,
                TagMo.getPrefs().eliteActiveBank().get());

        if (null != amiibosView.getAdapter()) {
            amiibosView.getAdapter().notifyItemChanged(TagMo.getPrefs().eliteActiveBank().get());
            amiibosView.getAdapter().notifyItemChanged(active_bank);
        }

        TagMo.getPrefs().eliteActiveBank().put(active_bank);

        int bank_count = TagMo.getPrefs().eliteBankCount().get();
        bankStats.setText(getString(R.string.elite_bank_stats,
                eliteBankCount.getValueForPosition(active_bank), bank_count));
        writeOpenBanks.setText(getString(R.string.write_open_banks, bank_count));
        eraseOpenBanks.setText(getString(R.string.erase_open_banks, bank_count));
    });

    ActivityResultLauncher<Intent> onScanTagResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || null == result.getData()) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        byte[] tagData = result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);
        int current_bank = result.getData().getIntExtra(TagMo.EXTRA_CURRENT_BANK, clickedPosition);

        Bundle args = new Bundle();
        args.putByteArray(TagMo.EXTRA_TAG_DATA, tagData);
        if (null != amiibos.get(current_bank))
            amiibos.get(current_bank).data = tagData;
        switch (status) {
            case NOTHING:
                break;
            case WRITE_DATA:
                Intent modify = new Intent(this, NfcActivity_.class);
                modify.setAction(TagMo.ACTION_WRITE_TAG_FULL);
                modify.putExtra(TagMo.EXTRA_CURRENT_BANK, current_bank);
                onUpdateTagResult.launch(modify.putExtras(args));
                break;
            case EDIT_DATA:
                onUpdateTagResult.launch(new Intent(this,
                        TagDataActivity_.class).putExtras(args));
                break;
            case HEX_CODE:
                onUpdateTagResult.launch(new Intent(this,
                        HexViewerActivity_.class).putExtras(args));
                break;
            case BANK_BACKUP:
                displayBackupDialog(tagData);
                break;
            case VERIFY_TAG:
                try {
                    TagUtils.validateData(tagData);
                    new Toasty(this).Dialog(R.string.validation_success);
                } catch (Exception e) {
                    new Toasty(this).Dialog(e.getMessage());
                }
                break;

        }
        status = CLICKED.NOTHING;
        updateAmiiboView(tagData, -1, current_bank);
        if (null != amiibosView.getAdapter())
            amiibosView.getAdapter().notifyItemChanged(current_bank);
    });

    private void scanAmiiboBank(int current_bank) {
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
            Toasty notice = new Toasty(this);
            Intent scan = new Intent(this, NfcActivity_.class);
            scan.putExtra(TagMo.EXTRA_CURRENT_BANK, current_bank);
            switch (item.getItemId()) {
                case R.id.mnu_activate:
                    scan.setAction(TagMo.ACTION_ACTIVATE_BANK);
                    onActivateActivity.launch(scan);
                    return true;
                case R.id.mnu_replace:
                    displayWriteDialog(current_bank);
                    return true;
                case R.id.mnu_write:
                    if (null != tagData && tagData.length > 0) {
                        scan.setAction(TagMo.ACTION_WRITE_TAG_FULL);
                        scan.putExtra(TagMo.EXTRA_TAG_DATA, tagData);
                        onUpdateTagResult.launch(scan);
                    } else {
                        status = CLICKED.WRITE_DATA;
                        scanAmiiboBank(current_bank);
                    }
                    return true;
                case R.id.mnu_erase_bank:
                    if (TagMo.getPrefs().eliteActiveBank().get() == current_bank) {
                        notice.Short(R.string.erase_active);
                    } else {
                        scan.setAction(TagMo.ACTION_ERASE_BANK);
                        onUpdateTagResult.launch(scan);
                        status = CLICKED.ERASE_BANK;
                    }
                    return true;
                case R.id.mnu_edit:
                    if (null != tagData && tagData.length > 0) {
                        Intent editor = new Intent(this, TagDataActivity_.class);
                        editor.putExtra(TagMo.EXTRA_TAG_DATA, tagData);
                        onUpdateTagResult.launch(editor);
                    } else {
                        status = CLICKED.EDIT_DATA;
                        scanAmiiboBank(current_bank);
                    }
                    return true;
                case R.id.mnu_view_hex:
                    if (null != tagData && tagData.length > 0) {
                        Intent viewhex = new Intent(this, HexViewerActivity_.class);
                        viewhex.putExtra(TagMo.EXTRA_TAG_DATA, tagData);
                        startActivity(viewhex);
                    } else {
                        status = CLICKED.HEX_CODE;
                        scanAmiiboBank(current_bank);
                    }
                    return true;
                case R.id.mnu_backup:
                    if (null != tagData && tagData.length > 0) {
                        displayBackupDialog(tagData);
                    } else {
                        status = CLICKED.BANK_BACKUP;
                        scanAmiiboBank(current_bank);
                    }
                    return true;
                case R.id.mnu_validate:
                    if (null != tagData && tagData.length > 0) {
                        try {
                            TagUtils.validateData(tagData);
                            notice.Dialog(R.string.validation_success);
                        } catch (Exception e) {
                            notice.Dialog(e.getMessage());
                        }
                    } else {
                        status = CLICKED.VERIFY_TAG;
                        scanAmiiboBank(current_bank);
                    }
                    return true;
            }
            return false;
        });

        String tagInfo = null;
        String amiiboHexId = "";
        String amiiboName = "";
        String amiiboSeries = "";
        String amiiboType = "";
        String gameSeries = "";
        // String character = "";
        String amiiboImageUrl;

        Amiibo amiibo = amiibos.get(current_bank);
        if (null == amiibo) {
            if (null != tagData && tagData.length > 0) {
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
                if (null != settings.getAmiiboManager()) {
                    amiibo = settings.getAmiiboManager().amiibos.get(amiiboId);
                    if (null == amiibo)
                        amiibo = new Amiibo(settings.getAmiiboManager(),
                                amiiboId, null, null);
                }
            }
        }

        if (null != amiibo) {
            amiiboImageUrl = amiibo.getImageUrl();
            amiiboHexId = TagUtils.amiiboIdToHex(amiibo.id);
            if (null != amiibo.name)
                amiiboName = amiibo.name;
            if (null != amiibo.getAmiiboSeries())
                amiiboSeries = amiibo.getAmiiboSeries().name;
            if (null != amiibo.getAmiiboType())
                amiiboType = amiibo.getAmiiboType().name;
            if (null != amiibo.getGameSeries())
                gameSeries = amiibo.getGameSeries().name;
            // if (null != amiibo.getCharacter())
            //     character = amiibo.getCharacter().name;
        } else {
            tagInfo = "ID: " + TagUtils.amiiboIdToHex(amiiboId);
            amiiboImageUrl = Amiibo.getImageUrl(amiiboId);
        }

        boolean hasTagInfo = null != tagInfo;

        if (hasTagInfo) {
            setAmiiboInfoText(txtError, tagInfo, false);
            amiiboInfo.setVisibility(View.GONE);
        } else {
            txtError.setVisibility(View.GONE);
            amiiboInfo.setVisibility(View.VISIBLE);
        }
        setAmiiboInfoText(txtBank, getString(R.string.bank_number,
                eliteBankCount.getValueForPosition(current_bank)), hasTagInfo);
        setAmiiboInfoText(txtName, amiiboName, hasTagInfo);
        setAmiiboInfoText(txtTagId, amiiboHexId, hasTagInfo);
        setAmiiboInfoText(txtAmiiboSeries, amiiboSeries, hasTagInfo);
        setAmiiboInfoText(txtAmiiboType, amiiboType, hasTagInfo);
        setAmiiboInfoText(txtGameSeries, gameSeries, hasTagInfo);
        // setAmiiboInfoText(txtCharacter, character, hasTagInfo);

        if (null != imageAmiibo) {
            imageAmiibo.setVisibility(View.GONE);
            Glide.with(this).clear(amiiboImageTarget);
            if (null != amiiboImageUrl) {
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
            return new RequestOptions().onlyRetrieveFromCache(null == activeNetwork
                    || activeNetwork.getType() != ConnectivityManager.TYPE_WIFI);
        } else {
            return new RequestOptions().onlyRetrieveFromCache(false);
        }
    }

    private int getColumnCount() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
        else
            mWindowManager.getDefaultDisplay().getMetrics(metrics);
        return (int) ((metrics.widthPixels / metrics.density) / 112 + 0.5);
    }

    private void scanAmiiboTag(int position) {
        Intent amiiboIntent = new Intent(this, NfcActivity_.class);
        amiiboIntent.putExtra(TagMo.EXTRA_CURRENT_BANK, position);
        amiiboIntent.setAction(TagMo.ACTION_SCAN_TAG);
        onUpdateTagResult.launch(amiiboIntent);
    }

    @Override
    public void onAmiiboClicked(Amiibo amiibo, int position) {
        if (null == amiibo) {
            displayWriteDialog(position);
            return;
        }
        clickedPosition = position;
        status = CLICKED.NOTHING;
        onBottomSheetChanged(true, true);
        if (null != amiibo.data  && amiibo.bank == position) {
            updateAmiiboView(amiibo.data, -1, position);
        } else if (amiibo.id != 0) {
            updateAmiiboView(null, amiibo.id, position);
        } else {
            scanAmiiboTag(position);
        }
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onAmiiboImageClicked(Amiibo amiibo, int position) {
        if (null != amiibo ) {
            Bundle bundle = new Bundle();
            bundle.putLong(TagMo.EXTRA_AMIIBO_ID, amiibo.id);

            Intent intent = new Intent(this, ImageActivity_.class);
            intent.putExtras(bundle);

            startActivity(intent);
        }
    }

    @Override
    public boolean onAmiiboLongClicked(Amiibo amiibo, int position) {
        if (null != amiibo )
            scanAmiiboTag(position);
        else
            displayWriteDialog(position);
        return true;
    }

    static final String BACKGROUND_AMIIBO_FILES = "amiibo_files";

    void loadAmiiboFiles(File rootFolder, boolean recursiveFiles) {
        BackgroundExecutor.cancelAll(BACKGROUND_AMIIBO_FILES, true);
        loadAmiiboFilesTask(rootFolder, recursiveFiles);
    }

    @Background(id = BACKGROUND_AMIIBO_FILES)
    void loadAmiiboFilesTask(File rootFolder, boolean recursiveFiles) {
        final ArrayList<AmiiboFile> amiiboFiles = AmiiboManager
                .listAmiibos(keyManager, rootFolder, recursiveFiles);
        if (settings.isShowingDownloads()) {
            amiiboFiles.addAll(AmiiboManager.listAmiibos(keyManager,
                    Storage.getDownloadDir(null), recursiveFiles));
        }

        if (Thread.currentThread().isInterrupted())
            return;

        this.runOnUiThread(() -> {
            settings.setAmiiboFiles(amiiboFiles);
            settings.notifyChanges();
        });
    }

    @Override
    public void onBackPressed() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            onBottomSheetChanged(true, false);
        } else {
            super.onBackPressed();
        }
    }
}
