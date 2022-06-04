package com.hiddenramblings.tagmo;

import android.app.Activity;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.hiddenramblings.tagmo.adapter.BankBrowserAdapter;
import com.hiddenramblings.tagmo.adapter.WriteBanksAdapter;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.BrowserSettings.VIEW;
import com.hiddenramblings.tagmo.settings.Preferences_;
import com.hiddenramblings.tagmo.widget.Toasty;
import com.shawnlin.numberpicker.NumberPicker;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.concurrent.Executors;


public class BankListActivity extends AppCompatActivity implements
        BankBrowserAdapter.OnAmiiboClickListener {

    private final Preferences_ prefs = TagMo.getPrefs();

    private RecyclerView amiibosView;

    private LinearLayout bankOptionsMenu;
    private ToggleButton switchMenuOptions;
    private RelativeLayout writeBankLayout;
    private RecyclerView amiiboFilesView;

    private CardView amiiboCard;
    private Toolbar toolbar;
    private View amiiboInfo;
    private TextView txtError;
    private TextView txtTagId;
    private TextView txtName;
    private TextView txtBank;
    private TextView txtGameSeries;
    // private TextView txtCharacter;
    private TextView txtAmiiboType;
    private TextView txtAmiiboSeries;
    private AppCompatImageView imageAmiibo;

    private TextView bankStats;
    private NumberPicker eliteBankCount;
    private AppCompatButton writeOpenBanks;
    private AppCompatButton eraseOpenBanks;
    private AppCompatButton writeBankCount;

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

    private BrowserSettings settings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keyManager = new KeyManager(this);

        setContentView(R.layout.activity_bank_list);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        amiibosView = findViewById(R.id.amiibos_list);
        amiibosView.setHasFixedSize(true);

        switchMenuOptions = findViewById(R.id.switch_menu_options);
        bankOptionsMenu = findViewById(R.id.bank_options_menu);
        writeBankLayout = findViewById(R.id.write_banks_layout);
        amiiboFilesView = findViewById(R.id.amiibo_files_list);

        amiiboCard = findViewById(R.id.active_card_layout);
        toolbar = findViewById(R.id.toolbar);
        amiiboInfo = findViewById(R.id.amiiboInfo);
        txtError = findViewById(R.id.txtError);
        txtTagId = findViewById(R.id.txtTagId);
        txtName = findViewById(R.id.txtName);
        txtBank = findViewById(R.id.txtBank);
        txtGameSeries = findViewById(R.id.txtGameSeries);
        // txtCharacter = findViewById(R.id.txtCharacter);
        txtAmiiboType = findViewById(R.id.txtAmiiboType);
        txtAmiiboSeries = findViewById(R.id.txtAmiiboSeries);
        imageAmiibo = findViewById(R.id.imageAmiibo);

        bankStats = findViewById(R.id.bank_stats);
        eliteBankCount = findViewById(R.id.bank_number_picker);
        writeOpenBanks = findViewById(R.id.write_open_banks);
        eraseOpenBanks = findViewById(R.id.erase_open_banks);
        writeBankCount = findViewById(R.id.write_bank_count);

        this.settings = new BrowserSettings().initialize();

        AppCompatImageView toggle = findViewById(R.id.toggle);
        this.bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet));
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        this.bottomSheetBehavior.addBottomSheetCallback(
                new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    if (writeBankLayout.getVisibility() == View.VISIBLE)
                        onBottomSheetChanged(true, false);
                    toggle.setImageResource(R.drawable.ic_expand_less_white_24dp);
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    toggle.setImageResource(R.drawable.ic_expand_more_white_24dp);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                ViewGroup mainLayout = findViewById(R.id.main_layout);
                if (mainLayout.getBottom() >= bottomSheet.getTop()) {
                    int bottomHeight = bottomSheet.getMeasuredHeight()
                            - bottomSheetBehavior.getPeekHeight();
                    mainLayout.setPadding(0, 0, 0, slideOffset > 0
                            ? (int) (bottomHeight * slideOffset) : 0);
                }
                if (slideOffset > 0)
                    amiibosView.smoothScrollToPosition(clickedPosition);
            }
        });

        toggle.setOnClickListener(view -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        toolbar.inflateMenu(R.menu.bank_menu);

        int bank_count = getIntent().getIntExtra(NFCIntent.EXTRA_BANK_COUNT,
                prefs.eliteBankCount().get());
        int active_bank = getIntent().getIntExtra(NFCIntent.EXTRA_ACTIVE_BANK,
                prefs.eliteActiveBank().get());

        ((TextView) findViewById(R.id.hardware_info)).setText(getString(R.string.elite_signature,
                getIntent().getStringExtra(NFCIntent.EXTRA_SIGNATURE)));
        eliteBankCount.setValue(bank_count);
        if (settings.getAmiiboView() == VIEW.IMAGE.getValue())
            amiibosView.setLayoutManager(new GridLayoutManager(this, getColumnCount()));
        else
            amiibosView.setLayoutManager(new LinearLayoutManager(this));
        BankBrowserAdapter adapter = new BankBrowserAdapter(settings, this);
        amiibosView.setAdapter(adapter);
        this.settings.addChangeListener(adapter);
        updateEliteHardwareAdapter(getIntent().getStringArrayListExtra(NFCIntent.EXTRA_AMIIBO_LIST));
        bankStats.setText(getString(R.string.bank_stats,
                getValueForPosition(eliteBankCount, active_bank), bank_count));
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

        switchMenuOptions.setOnClickListener(view -> {
            if (bankOptionsMenu.isShown()) {
                amiiboCard.setVisibility(View.VISIBLE);
                bankOptionsMenu.setVisibility(View.GONE);
            } else {
                bankOptionsMenu.setVisibility(View.VISIBLE);
                amiiboCard.setVisibility(View.GONE);
            }
            amiibosView.requestLayout();
        });

        writeListAdapter = new WriteBanksAdapter(settings, this::writeAmiiboCollection);
        this.settings.addChangeListener(writeListAdapter);

        SearchView searchView = findViewById(R.id.amiibo_search);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setSubmitButtonEnabled(false);
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

        writeOpenBanks.setOnClickListener(view -> {
            onBottomSheetChanged(false, false);
            amiiboFilesView.setAdapter(writeListAdapter);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        eraseOpenBanks.setOnClickListener(view -> {
            Intent collection = new Intent(this, NfcActivity.class);
            collection.setAction(NFCIntent.ACTION_ERASE_ALL_TAGS);
            collection.putExtra(NFCIntent.EXTRA_BANK_COUNT, eliteBankCount.getValue());
            onOpenBanksActivity.launch(collection);
        });

        writeBankCount.setOnClickListener(view -> {
            if (prefs.eliteActiveBank().get() >= eliteBankCount.getValue()) {
                new Toasty(this).Short(R.string.fail_active_oob);
                onBottomSheetChanged(true, false);
                return;
            }
            Intent configure = new Intent(this, NfcActivity.class);
            configure.setAction(NFCIntent.ACTION_SET_BANK_COUNT);
            configure.putExtra(NFCIntent.EXTRA_BANK_COUNT, eliteBankCount.getValue());
            onOpenBanksActivity.launch(configure);

            onBottomSheetChanged(true, false);
        });

        if (null == amiibos.get(active_bank)) {
            onBottomSheetChanged(true, false);
        } else {
            updateAmiiboView(null, amiibos.get(active_bank).id, active_bank);
            onBottomSheetChanged(true, true);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void updateEliteHardwareAdapter(ArrayList<String> amiiboList) {
        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (null == amiiboManager) {
            try {
                amiiboManager = AmiiboManager.getAmiiboManager(getApplicationContext());
            } catch (IOException | JSONException | ParseException e) {
                Debug.Log(e);
                amiiboManager = null;
                new Toasty(this).Short(R.string.amiibo_info_parse_error);
            }

            final AmiiboManager uiAmiiboManager = amiiboManager;
            this.runOnUiThread(() -> {
                settings.setAmiiboManager(uiAmiiboManager);
                settings.notifyChanges();
            });
        }

        if (amiibos.isEmpty()) {
            if (null != amiibosView.getAdapter())
                ((BankBrowserAdapter) amiibosView.getAdapter()).setAmiibos(amiibos);
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
        switchMenuOptions.setVisibility(isMenu && hasAmiibo ? View.VISIBLE : View.GONE);
        bankOptionsMenu.setVisibility(isMenu && !hasAmiibo ? View.VISIBLE : View.GONE);
        if (isMenu) writeListAdapter.resetSelections();
        writeBankLayout.setVisibility(isMenu || hasAmiibo ? View.GONE : View.VISIBLE);
    }

    private final CustomTarget<Bitmap> amiiboImageTarget = new CustomTarget<>() {
        @Override
        public void onLoadStarted(@Nullable Drawable placeholder) { }

        @Override
        public void onLoadFailed(@Nullable Drawable errorDrawable) {
            imageAmiibo.setVisibility(View.GONE);
        }

        @Override
        public void onLoadCleared(@Nullable Drawable placeholder) { }

        @Override
        public void onResourceReady(@NonNull Bitmap resource, Transition transition) {
            imageAmiibo.setMaxHeight(Resources.getSystem().getDisplayMetrics().heightPixels / 3);
            imageAmiibo.requestLayout();
            imageAmiibo.setImageBitmap(resource);
            imageAmiibo.setVisibility(View.VISIBLE);
        }
    };

    private final ActivityResultLauncher<Intent> onActivateActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || null == result.getData()) return;

        if (!NFCIntent.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        int active_bank = result.getData().getIntExtra(NFCIntent.EXTRA_ACTIVE_BANK,
                prefs.eliteActiveBank().get());

        if (null != amiibosView.getAdapter()) {
            amiibosView.getAdapter().notifyItemChanged(prefs.eliteActiveBank().get());
            amiibosView.getAdapter().notifyItemChanged(active_bank);
        }

        prefs.eliteActiveBank().put(active_bank);

        int bank_count = prefs.eliteBankCount().get();
        bankStats.setText(getString(R.string.bank_stats,
                getValueForPosition(eliteBankCount, active_bank), bank_count));
        writeOpenBanks.setText(getString(R.string.write_open_banks, bank_count));
        eraseOpenBanks.setText(getString(R.string.erase_open_banks, bank_count));
    });

    private final ActivityResultLauncher<Intent> onUpdateTagResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || null == result.getData()) return;

        if (!NFCIntent.ACTION_NFC_SCANNED.equals(result.getData().getAction())
                && !NFCIntent.ACTION_EDIT_COMPLETE.equals(result.getData().getAction())) return;

        if (result.getData().hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
            clickedPosition = result.getData().getIntExtra(
                    NFCIntent.EXTRA_CURRENT_BANK, clickedPosition);
        }

        byte[] tagData = null != amiibos.get(clickedPosition)
                ? amiibos.get(clickedPosition).data : null;

        if (result.getData().hasExtra(NFCIntent.EXTRA_TAG_DATA)) {
            tagData = result.getData().getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA);
            if (null != amiibos.get(clickedPosition))
                amiibos.get(clickedPosition).data = tagData;
        }

        if (result.getData().hasExtra(NFCIntent.EXTRA_AMIIBO_LIST)) {
            updateEliteHardwareAdapter(result.getData().getStringArrayListExtra(
                    NFCIntent.EXTRA_AMIIBO_LIST));
        }

        updateAmiiboView(tagData, -1, clickedPosition);

        if (status == CLICKED.ERASE_BANK) {
            status = CLICKED.NOTHING;
            onBottomSheetChanged(true, false);
            amiibos.set(clickedPosition, null);
        }
    });

    private final ActivityResultLauncher<Intent> onScanTagResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || null == result.getData()) return;

        if (!NFCIntent.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        byte[] tagData = result.getData().getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA);
        int current_bank = result.getData().getIntExtra(NFCIntent.EXTRA_CURRENT_BANK, clickedPosition);

        Bundle args = new Bundle();
        args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData);
        if (null != amiibos.get(current_bank))
            amiibos.get(current_bank).data = tagData;
        switch (status) {
            case NOTHING:
                break;
            case WRITE_DATA:
                Intent modify = new Intent(this, NfcActivity.class);
                modify.setAction(NFCIntent.ACTION_WRITE_TAG_FULL);
                modify.putExtra(NFCIntent.EXTRA_CURRENT_BANK, current_bank);
                onUpdateTagResult.launch(modify.putExtras(args));
                break;
            case EDIT_DATA:
                onUpdateTagResult.launch(new Intent(this,
                        TagDataActivity.class).putExtras(args));
                break;
            case HEX_CODE:
                onUpdateTagResult.launch(new Intent(this,
                        HexViewerActivity.class).putExtras(args));
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
        Intent scan = new Intent(this, NfcActivity.class);
        scan.setAction(NFCIntent.ACTION_SCAN_TAG);
        scan.putExtra(NFCIntent.EXTRA_CURRENT_BANK, current_bank);
        onScanTagResult.launch(scan);
    }

    private void scanAmiiboTag(int position) {
        Intent amiiboIntent = new Intent(this, NfcActivity.class);
        amiiboIntent.putExtra(NFCIntent.EXTRA_CURRENT_BANK, position);
        amiiboIntent.setAction(NFCIntent.ACTION_SCAN_TAG);
        onUpdateTagResult.launch(amiiboIntent);
    }

    private void writeAmiiboFile(AmiiboFile amiiboFile, int position) {
        Bundle args = new Bundle();
        try {
            byte[] data = null != amiiboFile.getData() ? amiiboFile.getData()
                    : TagUtils.getValidatedFile(keyManager, amiiboFile.getFilePath());
            args.putByteArray(NFCIntent.EXTRA_TAG_DATA, data);
        } catch (Exception e) {
            Debug.Log(e);
        }

        Intent intent = new Intent(this, NfcActivity.class);
        intent.setAction(NFCIntent.ACTION_WRITE_TAG_FULL);
        intent.putExtra(NFCIntent.EXTRA_CURRENT_BANK, position);
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

    private void displayBackupDialog(byte[] tagData) {
        View view = getLayoutInflater().inflate(R.layout.dialog_backup, null);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        final EditText input = view.findViewById(R.id.backup_entry);
        input.setText(TagUtils.decipherFilename(settings.getAmiiboManager(), tagData));
        Dialog backupDialog = dialog.setView(view).create();
        view.findViewById(R.id.save_backup).setOnClickListener(v -> {
            try {
                String fileName = TagUtils.writeBytesToFile(
                        Storage.getDownloadDir("TagMo", "Backups"),
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
        backupDialog.show();
    }

    private void setAmiiboInfoText(TextView textView, CharSequence text, boolean hasTagInfo) {
        if (hasTagInfo) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            if (text.length() == 0) {
                textView.setText(getString(R.string.unknown));
                textView.setEnabled(false);
            } else {
                textView.setText(text);
                textView.setText(text);
                textView.setEnabled(true);
            }
        }
    }

    private void updateAmiiboView(byte[] tagData, long amiiboId, int current_bank) {
        toolbar.setOnMenuItemClickListener(item -> {
            Toasty notice = new Toasty(this);
            Intent scan = new Intent(this, NfcActivity.class);
            scan.putExtra(NFCIntent.EXTRA_CURRENT_BANK, current_bank);
            if (item.getItemId() == R.id.mnu_activate) {
                scan.setAction(NFCIntent.ACTION_ACTIVATE_BANK);
                onActivateActivity.launch(scan);
                return true;
            } else if (item.getItemId() == R.id.mnu_replace) {
                displayWriteDialog(current_bank);
                return true;
            } else if (item.getItemId() == R.id.mnu_write) {
                if (null != tagData && tagData.length > 0) {
                    scan.setAction(NFCIntent.ACTION_WRITE_TAG_FULL);
                    scan.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData);
                    onUpdateTagResult.launch(scan);
                } else {
                    status = CLICKED.WRITE_DATA;
                    scanAmiiboBank(current_bank);
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_erase_bank) {
                scan.setAction(NFCIntent.ACTION_ERASE_BANK);
                onUpdateTagResult.launch(scan);
                status = CLICKED.ERASE_BANK;
                return true;
            } else if (item.getItemId() == R.id.mnu_edit) {
                if (null != tagData && tagData.length > 0) {
                    Intent editor = new Intent(this, TagDataActivity.class);
                    editor.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData);
                    onUpdateTagResult.launch(editor);
                } else {
                    status = CLICKED.EDIT_DATA;
                    scanAmiiboBank(current_bank);
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_view_hex) {
                if (null != tagData && tagData.length > 0) {
                    Intent viewhex = new Intent(this, HexViewerActivity.class);
                    viewhex.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData);
                    startActivity(viewhex);
                } else {
                    status = CLICKED.HEX_CODE;
                    scanAmiiboBank(current_bank);
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_backup) {
                if (null != tagData && tagData.length > 0) {
                    displayBackupDialog(tagData);
                } else {
                    status = CLICKED.BANK_BACKUP;
                    scanAmiiboBank(current_bank);
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_validate) {
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
            } else if (null != settings.getAmiiboManager()) {
                amiibo = settings.getAmiiboManager().amiibos.get(amiiboId);
                if (null == amiibo)
                    amiibo = new Amiibo(settings.getAmiiboManager(),
                            amiiboId, null, null);
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
                getValueForPosition(eliteBankCount, current_bank)), hasTagInfo);
        setAmiiboInfoText(txtName, amiiboName, hasTagInfo);
        setAmiiboInfoText(txtTagId, amiiboHexId, hasTagInfo);
        setAmiiboInfoText(txtAmiiboSeries, amiiboSeries, hasTagInfo);
        setAmiiboInfoText(txtAmiiboType, amiiboType, hasTagInfo);
        setAmiiboInfoText(txtGameSeries, gameSeries, hasTagInfo);
        // setAmiiboInfoText(txtCharacter, character, hasTagInfo);

        if (null != imageAmiibo) {
            imageAmiibo.setVisibility(View.GONE);
            GlideApp.with(this).clear(amiiboImageTarget);
            if (null != amiiboImageUrl) {
                GlideApp.with(this).asBitmap().load(amiiboImageUrl).into(amiiboImageTarget);
                final long amiiboTagId = amiiboId;
                imageAmiibo.setOnClickListener(view -> {
                    if (amiiboTagId == -1) {
                        return;
                    }

                    Bundle bundle = new Bundle();
                    bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiiboTagId);

                    Intent intent = new Intent(this, ImageActivity.class);
                    intent.putExtras(bundle);

                    startActivity(intent);
                });
            }
        }
    }

    private final ActivityResultLauncher<Intent> onOpenBanksActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || null == result.getData()) return;

        if (!NFCIntent.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        int bank_count = result.getData().getIntExtra(NFCIntent.EXTRA_BANK_COUNT,
                prefs.eliteBankCount().get());

        prefs.eliteBankCount().put(bank_count);

        eliteBankCount.setValue(bank_count);
        updateEliteHardwareAdapter(result.getData().getStringArrayListExtra(NFCIntent.EXTRA_AMIIBO_LIST));
        bankStats.setText(getString(R.string.bank_stats,
                getValueForPosition(eliteBankCount, prefs.eliteActiveBank().get()), bank_count));
        writeOpenBanks.setText(getString(R.string.write_open_banks, bank_count));
        eraseOpenBanks.setText(getString(R.string.erase_open_banks, bank_count));
    });

    private void writeAmiiboCollection(ArrayList<AmiiboFile> amiiboList) {
        if (null != amiiboList && amiiboList.size() == eliteBankCount.getValue()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.write_confirm)
                    .setPositiveButton(R.string.proceed, (dialog, which) -> {
                        Intent collection = new Intent(this, NfcActivity.class);
                        collection.setAction(NFCIntent.ACTION_WRITE_ALL_TAGS);
                        collection.putExtra(NFCIntent.EXTRA_BANK_COUNT, eliteBankCount.getValue());
                        collection.putExtra(NFCIntent.EXTRA_AMIIBO_FILES, amiiboList);
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

    private int getColumnCount() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
        else
            mWindowManager.getDefaultDisplay().getMetrics(metrics);
        return (int) ((metrics.widthPixels / metrics.density) / 112 + 0.5);
    }

    public int getValueForPosition(NumberPicker picker, int value) {
        return value + picker.getMinValue();
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
        if (null != amiibo) {
            Bundle bundle = new Bundle();
            bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiibo.id);

            Intent intent = new Intent(this, ImageActivity.class);
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

    private boolean isDirectoryHidden(File rootFolder, File directory, boolean recursive) {
        return !rootFolder.getPath().equals(directory.getPath()) && (!recursive
                || (!rootFolder.getPath().startsWith(directory.getPath())
                && !directory.getPath().startsWith(rootFolder.getPath())));
    }

    private void loadAmiiboFiles(File rootFolder, boolean recursiveFiles) {
        Executors.newSingleThreadExecutor().execute(() -> {
            final ArrayList<AmiiboFile> amiiboFiles = AmiiboManager
                    .listAmiibos(keyManager, rootFolder, recursiveFiles);
            if (this.settings.isShowingDownloads()) {
                File download = Storage.getDownloadDir(null);
                if (isDirectoryHidden(rootFolder, download, recursiveFiles))
                    amiiboFiles.addAll(AmiiboManager
                            .listAmiibos(keyManager, download, true));
            } else {
                File foomiibo = Storage.getDownloadDir("TagMo", "Foomiibo");
                if (isDirectoryHidden(rootFolder, foomiibo, recursiveFiles))
                    amiiboFiles.addAll(AmiiboManager
                            .listAmiibos(keyManager, foomiibo, true));
            }

            if (Thread.currentThread().isInterrupted()) return;

            this.runOnUiThread(() -> {
                settings.setAmiiboFiles(amiiboFiles);
                settings.notifyChanges();
            });
        });
    }

    @Override
    public void onBackPressed() {
        if (BottomSheetBehavior.STATE_EXPANDED == bottomSheetBehavior.getState())
            onBottomSheetChanged(true, false);
        else
            super.onBackPressed();
    }
}
