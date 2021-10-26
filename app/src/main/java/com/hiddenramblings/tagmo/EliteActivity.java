package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.hiddenramblings.tagmo.adapter.EliteBrowserAdapter;
import com.hiddenramblings.tagmo.adapter.EliteWriteBlankAdapter;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.nfctag.TagReader;
import com.hiddenramblings.tagmo.nfctag.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;

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
import java.text.ParseException;
import java.util.ArrayList;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.elite_browser)
public class EliteActivity extends AppCompatActivity implements
        EliteBrowserAdapter.OnAmiiboClickListener {

    public static final int ACTIVATE = 0;
    public static final int BACKUP = 1;
    public static final int WIPE_BANK = 2;

    @ViewById(R.id.amiibos_list)
    RecyclerView amiibosView;
    @ViewById(R.id.hardware_info)
    TextView hardwareInfo;
    @ViewById(R.id.bottom_sheet)
    ViewGroup bottomSheet;
    @ViewById(R.id.toggle)
    ImageView toggle;
    @ViewById(R.id.elite_bank_stats)
    TextView bankStats;
    @ViewById(R.id.bank_count_picker)
    BankNumberPicker eliteBankCount;
    @ViewById(R.id.write_open_banks)
    AppCompatButton writeOpenBanks;
    @ViewById(R.id.write_bank_count)
    AppCompatButton writeBankCount;

    BottomSheetBehavior<View> bottomSheetBehavior;

    ArrayList<AmiiboFile> amiiboFiles;

    @InstanceState
    BrowserSettings settings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.settings = new BrowserSettings();
        this.settings.setBrowserRootFolder(new File(TagMo.getStorage(), TagMo.getPrefs().browserRootFolder().get()));
        this.settings.setQuery(TagMo.getPrefs().query().get());
        this.settings.setSort(TagMo.getPrefs().sort().get());
        this.settings.setAmiiboSeriesFilter(TagMo.getPrefs().filterAmiiboSeries().get());
        this.settings.setAmiiboTypeFilter(TagMo.getPrefs().filterAmiiboType().get());
        this.settings.setCharacterFilter(TagMo.getPrefs().filterCharacter().get());
        this.settings.setGameSeriesFilter(TagMo.getPrefs().filterGameSeries().get());
        this.settings.setAmiiboView(TagMo.getPrefs().browserAmiiboView().get());
        this.settings.setImageNetworkSettings(TagMo.getPrefs().imageNetworkSetting().get());
        this.settings.setRecursiveEnabled(TagMo.getPrefs().recursiveFolders().get());
        this.settings.setShowMissingFiles(TagMo.getPrefs().showMissingFiles().get());
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

        loadAmiiboFiles(settings.getBrowserRootFolder(), settings.isRecursiveEnabled());

        int bank_count = getIntent().getIntExtra(
                TagMo.EXTRA_BANK_COUNT, TagMo.getPrefs().eliteBankCount().get());
        int active_bank = getIntent().getIntExtra(
                TagMo.EXTRA_ACTIVE_BANK, TagMo.getPrefs().eliteActiveBank().get());

        hardwareInfo.setText(getString(R.string.elite_signature,
                getIntent().getStringExtra(TagMo.EXTRA_SIGNATURE)));
        eliteBankCount.setValue(bank_count);
        amiibosView.setLayoutManager(new LinearLayoutManager(this));
        EliteBrowserAdapter adapter = new EliteBrowserAdapter(settings, this);
        amiibosView.setAdapter(adapter);
        this.settings.addChangeListener(adapter);
        updateEliteHardwareAdapter(getIntent().getStringArrayListExtra(TagMo.EXTRA_AMIIBO_DATA));
        bankStats.setText(getString(R.string.elite_bank_stats, active_bank, bank_count));
        writeOpenBanks.setText(getString(R.string.write_open_banks, bank_count));
    }

    private void updateEliteHardwareAdapter(ArrayList<String> tagData) {
        AmiiboManager amiiboManager;
        try {
            amiiboManager = AmiiboManager.getAmiiboManager();
        } catch (IOException | JSONException | ParseException e) {
            e.printStackTrace();
            amiiboManager = null;
        }
        if (amiiboManager == null) return;
        final AmiiboManager uiAmiiboManager = amiiboManager;
        this.runOnUiThread(() -> {
            settings.setAmiiboManager(uiAmiiboManager);
            settings.notifyChanges();
        });
        ArrayList<Amiibo> amiibos = new ArrayList<>();
        for (int x = 0; x < tagData.size(); x++) {
            Amiibo amiibo = amiiboManager.amiibos.get(TagUtils.hexToLong(tagData.get(x)));
            amiibos.add(amiibo);
        }
        if (amiibosView.getAdapter() != null) {
            this.runOnUiThread(() -> {
                ((EliteBrowserAdapter) amiibosView.getAdapter()).setAmiibos(amiibos);
                amiibosView.getAdapter().notifyDataSetChanged();
            });
        }
    }

    @Click(R.id.toggle)
    void onConfigExpandClick() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    ActivityResultLauncher<Intent> onWriteOpenBanksActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null)
            return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction()))
            return;

        updateEliteHardwareAdapter(result.getData().getStringArrayListExtra(TagMo.EXTRA_AMIIBO_DATA));
    });

    private void writeAmiiboCollection(ArrayList<AmiiboFile> amiiboList, Dialog writeDialog) {
        if (amiiboList != null && amiiboList.size() == TagMo.getPrefs().eliteBankCount().get()) {
            new AlertDialog.Builder(EliteActivity.this)
                    .setMessage(R.string.write_confirm)
                    .setNegativeButton(R.string.proceed, (dialog, which) -> {
                        Intent collection = new Intent(this, NfcActivity_.class);
                        collection.setAction(TagMo.ACTION_WRITE_ALL_TAGS);
                        collection.putExtra(TagMo.EXTRA_AMIIBO_FILES, amiiboList);
                        onWriteOpenBanksActivity.launch(collection);
                        dialog.dismiss();
                        writeDialog.dismiss();
                    })
                    .setPositiveButton(R.string.cancel, (dialog, which) -> {
                        amiiboList.clear();
                        dialog.dismiss();
                    })
                    .show();
        }
    }

    @Click(R.id.write_open_banks)
    void onWriteOpenBanksClick() {
        View view = getLayoutInflater().inflate( R.layout.elite_writer, null);

        RecyclerView writerListView = view.findViewById(R.id.amiibos_list);
        writerListView.setLayoutManager(new LinearLayoutManager(this));

        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        Dialog writeDialog = dialog.setView(view).show();

        EliteWriteBlankAdapter.OnHighlightListener itemClick =
                new EliteWriteBlankAdapter.OnHighlightListener() {
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

        writerListView.setAdapter(new EliteWriteBlankAdapter(
                settings, itemClick, amiiboFiles).withHighlight(true));
        this.settings.addChangeListener((BrowserSettings.BrowserSettingsListener) writerListView.getAdapter());
        writeDialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );

        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    ActivityResultLauncher<Intent> onWriteBankCountActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null)
            return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction()))
            return;

        int bank_count = result.getData().getIntExtra(TagMo.EXTRA_BANK_COUNT,
                TagMo.getPrefs().eliteBankCount().get());

        TagMo.getPrefs().eliteBankCount().put(bank_count);

        eliteBankCount.setValue(bank_count);
        updateEliteHardwareAdapter(result.getData().getStringArrayListExtra(TagMo.EXTRA_AMIIBO_DATA));
        bankStats.setText(getString(R.string.elite_bank_stats,
                TagMo.getPrefs().eliteActiveBank().get(), bank_count));
        writeOpenBanks.setText(getString(R.string.write_open_banks, bank_count));
    });

    @Click(R.id.write_bank_count)
    void onWriteBankCountClick() {
        if (TagMo.getPrefs().eliteActiveBank().get() >= eliteBankCount.getValue()) {
            showToast(R.string.fail_active_oob);
            return;
        }
        Intent configure = new Intent(this, NfcActivity_.class);
        configure.setAction(TagMo.ACTION_SET_BANK_COUNT);
        configure.putExtra(TagMo.EXTRA_BANK_COUNT, eliteBankCount.getValue());
        onWriteBankCountActivity.launch(configure);

        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    ActivityResultLauncher<Intent> onActivateActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null)
            return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction()))
            return;

        int active_bank = result.getData().getIntExtra(TagMo.EXTRA_ACTIVE_BANK,
                TagMo.getPrefs().eliteActiveBank().get());

        TagMo.getPrefs().eliteActiveBank().put(active_bank);

        updateEliteHardwareAdapter(result.getData().getStringArrayListExtra(TagMo.EXTRA_AMIIBO_DATA));
        int bank_count = TagMo.getPrefs().eliteBankCount().get();
        bankStats.setText(getString(R.string.elite_bank_stats, active_bank, bank_count));
        writeOpenBanks.setText(getString(R.string.write_open_banks, bank_count));

    });

    ActivityResultLauncher<Intent> onBackupActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        byte[] tagData = result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);

        View view = getLayoutInflater().inflate(R.layout.backup_dialog, null);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        final EditText input = view.findViewById(R.id.backup_entry);
        input.setText(TagReader.generateFileName(settings.getAmiiboManager(), tagData));
        Dialog backupDialog = dialog.setView(view).show();
        view.findViewById(R.id.save_backup).setOnClickListener(v -> {
            try {
                File directory = new File(settings.getBrowserRootFolder(),
                        TagMo.getStringRes(R.string.tagmo_backup));
                String fileName = TagReader.writeBytesToFile(directory,
                        input.getText().toString() + ".bin", tagData);
                showToast(getString(R.string.wrote_file, fileName));
            } catch (IOException e) {
                showToast(e.getMessage());
            }
            backupDialog.dismiss();
        });
        view.findViewById(R.id.cancel_backup).setOnClickListener(v -> backupDialog.dismiss());
    });

    ActivityResultLauncher<Intent> onModifierActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null)
            return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction()))
            return;

        updateEliteHardwareAdapter(result.getData().getStringArrayListExtra(TagMo.EXTRA_AMIIBO_DATA));
    });

    private void writeAmiiboFile(AmiiboFile amiiboFile, int position) {
        Bundle args = new Bundle();
        try {
            args.putByteArray(TagMo.EXTRA_TAG_DATA,
                    TagReader.readTagStream(amiiboFile.getFilePath()));
        } catch (Exception e) {
            e.printStackTrace();
        }

        Intent intent = new Intent(this, NfcActivity_.class);
        intent.setAction(TagMo.ACTION_WRITE_TAG_FULL);
        intent.putExtra(TagMo.EXTRA_CURRENT_BANK, TagUtils.getValueForPosition(position));
        intent.putExtras(args);
        onModifierActivity.launch(intent);

    }

    private void displayWriteDialog(int position) {
        View view = getLayoutInflater().inflate( R.layout.elite_writer, null);

        RecyclerView writerListView = view.findViewById(R.id.amiibos_list);
        writerListView.setLayoutManager(new LinearLayoutManager(this));

        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        Dialog writeDialog = dialog.setView(view).show();

        EliteWriteBlankAdapter.OnAmiiboClickListener itemClick =
                new EliteWriteBlankAdapter.OnAmiiboClickListener() {
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

        writerListView.setAdapter(new EliteWriteBlankAdapter(settings, itemClick, amiiboFiles));
        this.settings.addChangeListener((BrowserSettings.BrowserSettingsListener) writerListView.getAdapter());
        writeDialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private int clickedPosition;

    ActivityResultLauncher<Intent> onViewerActivity = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        switch (result.getData().getIntExtra(TagMo.EXTRA_BANK_ACTION, 0)) {
            case ACTIVATE:
                Intent activate = new Intent(EliteActivity.this, NfcActivity_.class);
                activate.setAction(TagMo.ACTION_ACTIVATE_BANK);
                activate.putExtra(TagMo.EXTRA_CURRENT_BANK,
                        TagUtils.getValueForPosition(clickedPosition));
                onActivateActivity.launch(activate);
                break;
            case BACKUP:
                Intent backup = new Intent(this, NfcActivity_.class);
                backup.setAction(TagMo.ACTION_BACKUP_AMIIBO);
                backup.putExtra(TagMo.EXTRA_CURRENT_BANK,
                        TagUtils.getValueForPosition(clickedPosition));
                onBackupActivity.launch(backup);
                break;
            case WIPE_BANK:
                Intent format = new Intent(EliteActivity.this, NfcActivity_.class);
                format.setAction(TagMo.ACTION_FORMAT_BANK);
                format.putExtra(TagMo.EXTRA_CURRENT_BANK,
                        TagUtils.getValueForPosition(clickedPosition));
                onModifierActivity.launch(format);
                break;
        }
    });

    ActivityResultLauncher<Intent> onNFCActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        byte[] tagData = result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);

        Bundle args = new Bundle();
        args.putByteArray(TagMo.EXTRA_TAG_DATA, tagData);

        Intent intent = new Intent(this, AmiiboActivity_.class);
        intent.putExtras(args);

        onViewerActivity.launch(intent);
    });

    @Override
    public void onAmiiboClicked(Amiibo amiibo, int position) {
        boolean isAvailable = false;
        if (amiibo == null) {
            displayWriteDialog(position);
            return;
        }
        clickedPosition = position;
        for (int x = 0; x < amiiboFiles.size(); x ++) {
            if (amiiboFiles.get(x).getId() == amiibo.id) {
                Bundle args = new Bundle();
                try {
                    args.putByteArray(TagMo.EXTRA_TAG_DATA, TagReader.readTagStream(
                            amiiboFiles.get(x).getFilePath()));
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }

                Intent intent = new Intent(this, AmiiboActivity_.class);
                intent.putExtra(TagMo.EXTRA_CURRENT_BANK, TagUtils.getValueForPosition(position));
                intent.putExtras(args);

                onViewerActivity.launch(intent);
                isAvailable = true;
                break;
            }
        }

        if (!isAvailable) {
            Intent amiiboIntent = new Intent(EliteActivity.this, NfcActivity_.class);
            amiiboIntent.putExtra(TagMo.EXTRA_CURRENT_BANK, TagUtils.getValueForPosition(position));
            amiiboIntent.setAction(TagMo.ACTION_SCAN_TAG);
            onNFCActivity.launch(amiiboIntent);
        }
    }

    @Override
    public void onAmiiboImageClicked(Amiibo amiibo, int position) {
        if (amiibo != null) {
            Bundle bundle = new Bundle();
            bundle.putLong(TagMo.EXTRA_AMIIBO_ID, amiibo.id);

            Intent intent = new Intent(this, ImageActivity_.class);
            intent.putExtras(bundle);

            this.startActivity(intent);
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
                    e.printStackTrace();
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
}
