package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.eightbit.io.Debug;
import com.eightbit.material.IconifiedSnackbar;
import com.eightbit.os.Storage;
import com.eightbit.provider.DocumentsUri;
import com.eightbit.tagmo.Foomiibo;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.security.ProviderInstaller;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.TagMo.Website;
import com.hiddenramblings.tagmo.adapter.BrowserAmiibosAdapter;
import com.hiddenramblings.tagmo.adapter.BrowserFoldersAdapter;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.AmiiboSeries;
import com.hiddenramblings.tagmo.amiibo.AmiiboType;
import com.hiddenramblings.tagmo.amiibo.Character;
import com.hiddenramblings.tagmo.amiibo.GameSeries;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.github.InstallReceiver;
import com.hiddenramblings.tagmo.github.JSONExecutor;
import com.hiddenramblings.tagmo.nfctech.PowerTagManager;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.BrowserSettings.BrowserSettingsListener;
import com.hiddenramblings.tagmo.settings.BrowserSettings.SORT;
import com.hiddenramblings.tagmo.settings.BrowserSettings.VIEW;
import com.hiddenramblings.tagmo.settings.SettingsFragment;
import com.hiddenramblings.tagmo.settings.SettingsFragment_;
import com.hiddenramblings.tagmo.widget.Toasty;
import com.robertlevonyan.views.chip.Chip;
import com.robertlevonyan.views.chip.OnCloseClickListener;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.LongClick;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.OptionsMenuItem;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.api.BackgroundExecutor;
import org.apmem.tools.layouts.FlowLayout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_browser)
@OptionsMenu({R.menu.browser_menu})
public class BrowserActivity extends AppCompatActivity implements
        SearchView.OnQueryTextListener,
        SwipeRefreshLayout.OnRefreshListener,
        BrowserSettingsListener,
        BrowserAmiibosAdapter.OnAmiiboClickListener {

    @ViewById(R.id.chip_list)
    FlowLayout chipList;
    @ViewById(R.id.amiibos_list)
    RecyclerView amiibosView;
    @ViewById(R.id.swipe_refresh)
    SwipeRefreshLayout swipeRefreshLayout;
    @ViewById(R.id.folders_list)
    RecyclerView foldersView;
    @ViewById(R.id.bottom_sheet)
    ViewGroup bottomSheet;
    @ViewById(R.id.current_folder)
    TextView currentFolderView;
    @ViewById(R.id.toggle)
    ImageView toggle;
    @ViewById(R.id.preferences)
    CoordinatorLayout preferences;
    @ViewById(R.id.switch_storage_root)
    AppCompatButton switchStorageRoot;

    @OptionsMenuItem(R.id.search)
    MenuItem menuSearch;
    @OptionsMenuItem(R.id.sort_id)
    MenuItem menuSortId;
    @OptionsMenuItem(R.id.sort_name)
    MenuItem menuSortName;
    @OptionsMenuItem(R.id.sort_game_series)
    MenuItem menuSortGameSeries;
    @OptionsMenuItem(R.id.sort_character)
    MenuItem menuSortCharacter;
    @OptionsMenuItem(R.id.sort_amiibo_series)
    MenuItem menuSortAmiiboSeries;
    @OptionsMenuItem(R.id.sort_amiibo_type)
    MenuItem menuSortAmiiboType;
    @OptionsMenuItem(R.id.sort_file_path)
    MenuItem menuSortFilePath;
    @OptionsMenuItem(R.id.filter_game_series)
    MenuItem menuFilterGameSeries;
    @OptionsMenuItem(R.id.filter_character)
    MenuItem menuFilterCharacter;
    @OptionsMenuItem(R.id.filter_amiibo_series)
    MenuItem menuFilterAmiiboSeries;
    @OptionsMenuItem(R.id.filter_amiibo_type)
    MenuItem menuFilterAmiiboType;
    @OptionsMenuItem(R.id.view_simple)
    MenuItem menuViewSimple;
    @OptionsMenuItem(R.id.view_compact)
    MenuItem menuViewCompact;
    @OptionsMenuItem(R.id.view_large)
    MenuItem menuViewLarge;
    @OptionsMenuItem(R.id.view_image)
    MenuItem menuViewImage;
    @OptionsMenuItem(R.id.show_downloads)
    MenuItem menuShowDownloads;
    @OptionsMenuItem(R.id.recursive)
    MenuItem menuRecursiveFiles;
    @OptionsMenuItem(R.id.show_missing)
    MenuItem menuShowMissing;
    @OptionsMenuItem(R.id.enable_scale)
    MenuItem menuEnableScale;
    @OptionsMenuItem(R.id.capture_logcat)
    MenuItem menuLogcat;

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private KeyManager keyManager;
    private AmiiboFile clickedAmiibo = null;
    private final Handler handler = new Handler();
    private int filteredCount;

    private Snackbar ongoingSnackbar;

    @InstanceState
    BrowserSettings settings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TagMo.setScaledTheme(this, R.style.AppTheme);
        keyManager = new KeyManager(this);
        callHousekeeping();
    }

    @AfterViews
    void afterViews() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkForUpdate();
        } else {
            ProviderInstaller.installIfNeededAsync(this,
                    new ProviderInstaller.ProviderInstallListener() {
                @Override
                public void onProviderInstalled() {
                    checkForUpdate();
                }

                @Override
                public void onProviderInstallFailed(int errorCode, Intent recoveryIntent) {
                    GoogleApiAvailability availability = GoogleApiAvailability.getInstance();
                    if (availability.isUserResolvableError(errorCode)) {
                        // Recoverable error. Show a dialog prompting the user to
                        // install/update/enable Google Play services.
                        availability.showErrorDialogFragment(
                                BrowserActivity.this, errorCode, 1, dialog -> {
                            // The user chose not to take the recovery action
                        });
                    }
                    // Google Play services is not available.
                }
            });
        }
        
        this.bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        this.bottomSheetBehavior.addBottomSheetCallback(
                new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    preferences.setVisibility(View.GONE);
                    toggle.setImageResource(R.drawable.ic_expand_less_white_24dp);
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    toggle.setImageResource(R.drawable.ic_expand_more_white_24dp);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {

            }
        });

        if (this.settings == null) {
            this.settings = new BrowserSettings().initialize();
        } else {
            this.onFilterGameSeriesChanged();
            this.onFilterCharacterChanged();
            this.onFilterAmiiboSeriesChanged();
            this.onFilterAmiiboTypeChanged();
            this.onAmiiboFilesChanged();
        }
        this.settings.addChangeListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                this.onStorageEnabled();
            } else {
                requestScopedStorage();
            }
        } else {
            int permission = ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            } else {
                this.onStorageEnabled();
            }
        }

        this.swipeRefreshLayout.setOnRefreshListener(this);
        this.swipeRefreshLayout.setProgressViewOffset(false, 0,
                (int) getResources().getDimension(R.dimen.swipe_progress_end));

        if (this.settings.getAmiiboView() == VIEW.IMAGE.getValue())
            this.amiibosView.setLayoutManager(new GridLayoutManager(this, getColumnCount()));
        else
            this.amiibosView.setLayoutManager(new LinearLayoutManager(this));
        this.amiibosView.setAdapter(new BrowserAmiibosAdapter(settings, this));
        this.settings.addChangeListener((BrowserSettingsListener) this.amiibosView.getAdapter());

        this.foldersView.setLayoutManager(new LinearLayoutManager(this));
        this.foldersView.setAdapter(new BrowserFoldersAdapter(settings));
        this.settings.addChangeListener((BrowserSettingsListener) this.foldersView.getAdapter());

        this.loadPTagKeyManager();
    }

    ActivityResultLauncher<Intent> onAmiiboActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        this.onRootFolderChanged(false);

        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
        if (!TagMo.ACTION_DELETE_AMIIBO.equals(result.getData().getAction())) return;

        if (clickedAmiibo.getFilePath() != null) {
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.warn_delete_file, Storage.getRelativePath(
                            clickedAmiibo.getFilePath(), TagMo.getPrefs().preferEmulated().get())))
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        //noinspection ResultOfMethodCallIgnored
                        clickedAmiibo.getFilePath().delete();
                        this.onRootFolderChanged(false);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> {
                        openAmiiboViewer(clickedAmiibo);
                        dialog.dismiss();
                    }).show();
        } else {
            openAmiiboViewer(clickedAmiibo);
            new Toasty(this).Long(getString(R.string.delete_misisng));
        }
    });

    ActivityResultLauncher<Intent> onNFCActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        if (result.getData().hasExtra(TagMo.EXTRA_SIGNATURE)) {
            String signature = result.getData().getStringExtra(TagMo.EXTRA_SIGNATURE);
            int active_bank = result.getData().getIntExtra(
                    TagMo.EXTRA_ACTIVE_BANK, TagMo.getPrefs().eliteActiveBank().get());
            int bank_count = result.getData().getIntExtra(
                    TagMo.EXTRA_BANK_COUNT, TagMo.getPrefs().eliteBankCount().get());

            TagMo.getPrefs().eliteSignature().put(signature);
            TagMo.getPrefs().eliteActiveBank().put(active_bank);
            TagMo.getPrefs().eliteBankCount().put(bank_count);

            Intent eliteIntent = new Intent(this, BankListActivity_.class);
            eliteIntent.putExtras(result.getData());
            eliteIntent.putExtra(TagMo.EXTRA_AMIIBO_FILES, settings.getAmiiboFiles());
            startActivity(eliteIntent);
        } else {
            Bundle args = new Bundle();
            args.putByteArray(TagMo.EXTRA_TAG_DATA,
                    result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA));

            onAmiiboActivity.launch(new Intent(this,
                    AmiiboActivity_.class).putExtras(args));
        }
    });

    ActivityResultLauncher<Intent> onBackupActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        byte[] tagData = result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);

        View view = getLayoutInflater().inflate(R.layout.dialog_backup, amiibosView, false);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        final EditText input = view.findViewById(R.id.backup_entry);
        input.setText(TagUtils.decipherFilename(settings.getAmiiboManager(), tagData));
        Dialog backupDialog = dialog.setView(view).show();
        view.findViewById(R.id.save_backup).setOnClickListener(v -> {
            try {
                File directory = Storage.getDownloadDir("TagMo", "Backups");
                String fileName = TagUtils.writeBytesToFile(directory,
                        input.getText().toString() + ".bin", tagData);
                new Toasty(this).Long(getString(R.string.wrote_file, fileName));
                this.onRootFolderChanged(false);
            } catch (IOException e) {
                new Toasty(this).Short(e.getMessage());
            }
            backupDialog.dismiss();
        });
        view.findViewById(R.id.cancel_backup).setOnClickListener(v -> backupDialog.dismiss());
    });

    ActivityResultLauncher<Intent> onValidateActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        try {
            TagUtils.validateData(result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA));
            new Toasty(this).Dialog(R.string.validation_success);
        } catch (Exception e) {
            new Toasty(this).Dialog(e.getMessage());
        }
    });

    @LongClick(R.id.nfc_fab)
    public void onFabLongClicked() {
        onNFCActivity.launch(new Intent(this,
                NfcActivity_.class).setAction(TagMo.ACTION_SCAN_TAG));
    }

    @Click(R.id.nfc_fab)
    public void onFabClicked() {
        PopupMenu popup;
        View fab = findViewById(R.id.nfc_fab);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
            popup = new PopupMenu(this, fab,
                    Gravity.END, 0, R.style.PopupMenu);
        else
            popup = new PopupMenu(this, fab);
        try {
            for (Field field : popup.getClass().getDeclaredFields()) {
                if ("mPopup".equals(field.getName())) {
                    field.setAccessible(true);
                    Object menuPopupHelper = field.get(popup);
                    if (menuPopupHelper != null) {
                        Method setForceIcons = Class.forName(menuPopupHelper.getClass().getName())
                                .getMethod("setForceShowIcon", boolean.class);
                        setForceIcons.invoke(menuPopupHelper, true);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        popup.getMenuInflater().inflate(R.menu.action_menu, popup.getMenu());
        popup.show();
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.mnu_scan:
                    onNFCActivity.launch(new Intent(this,
                            NfcActivity_.class).setAction(TagMo.ACTION_SCAN_TAG));
                    return true;
                case R.id.mnu_backup:
                    Intent backup = new Intent(this, NfcActivity_.class);
                    backup.setAction(TagMo.ACTION_BACKUP_AMIIBO);
                    onBackupActivity.launch(backup);
                    return true;
                case R.id.mnu_validate:
                    onValidateActivity.launch(new Intent(this,
                            NfcActivity_.class).setAction(TagMo.ACTION_SCAN_TAG));
                    return true;
            }
            return false;
        });
    }

    @Click(R.id.toggle)
    void onBrowserFolderExpandClick() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    @Click(R.id.switch_storage_root)
    void onSwitchStorageClicked() {
        boolean internal = !TagMo.getPrefs().preferEmulated().get();
        TagMo.getPrefs().preferEmulated().put(internal);
        switchStorageRoot.setText(internal
                ? R.string.emulated_storage_root
                : R.string.physical_storage_root);
        this.settings.setBrowserRootFolder(Storage.getFile(internal));
        this.settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_id)
    void onSortIdClick() {
        settings.setSort(SORT.ID.getValue());
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_name)
    void onSortNameClick() {
        settings.setSort(SORT.NAME.getValue());
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_game_series)
    void onSortGameSeriesClick() {
        settings.setSort(SORT.GAME_SERIES.getValue());
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_character)
    void onSortCharacterClick() {
        settings.setSort(SORT.CHARACTER.getValue());
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_amiibo_series)
    void onSortAmiiboSeriesClick() {
        settings.setSort(SORT.AMIIBO_SERIES.getValue());
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_amiibo_type)
    void onSortAmiiboTypeClick() {
        settings.setSort(SORT.AMIIBO_TYPE.getValue());
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_file_path)
    void onSortFilePathClick() {
        settings.setSort(SORT.FILE_PATH.getValue());
        settings.notifyChanges();
    }

    @OptionsItem(R.id.view_simple)
    void onViewSimpleClick() {
        if (this.settings.getAmiiboView() == VIEW.IMAGE.getValue())
            this.amiibosView.setLayoutManager(new LinearLayoutManager(this));
        settings.setAmiiboView(VIEW.SIMPLE.getValue());
        settings.notifyChanges();
    }

    @OptionsItem(R.id.view_compact)
    void onViewCompactClick() {
        if (this.settings.getAmiiboView() == VIEW.IMAGE.getValue())
            this.amiibosView.setLayoutManager(new LinearLayoutManager(this));
        settings.setAmiiboView(VIEW.COMPACT.getValue());
        settings.notifyChanges();
    }

    @OptionsItem(R.id.view_large)
    void onViewLargeClick() {
        if (this.settings.getAmiiboView() == VIEW.IMAGE.getValue())
            this.amiibosView.setLayoutManager(new LinearLayoutManager(this));
        settings.setAmiiboView(VIEW.LARGE.getValue());
        settings.notifyChanges();
    }

    @OptionsItem(R.id.view_image)
    void onViewImageClick() {
        this.amiibosView.setLayoutManager(new GridLayoutManager(this, getColumnCount()));
        settings.setAmiiboView(VIEW.IMAGE.getValue());
        settings.notifyChanges();
    }

    @OptionsItem(R.id.show_downloads)
    void OnShowDownloadsCicked() {
        this.settings.setShowDownloads(!this.settings.isShowingDownloads());
        this.settings.notifyChanges();
    }

    @OptionsItem(R.id.recursive)
    void onRecursiveFilesClicked() {
        this.settings.setRecursiveEnabled(!this.settings.isRecursiveEnabled());
        this.settings.notifyChanges();
    }

    @OptionsItem(R.id.show_missing)
    void OnShowMissingCicked() {
        this.settings.setShowMissingFiles(!this.settings.isShowingMissingFiles());
        this.settings.notifyChanges();
    }

    @OptionsItem(R.id.enable_scale)
    void onEnableScaleClicked() {
        TagMo.getPrefs().enableScaling().put(!menuEnableScale.isChecked());
        this.recreate();
    }

    @OptionsItem(R.id.capture_logcat)
    @Background
    void onCaptureLogcatClicked() {
        try {
            Uri uri = Debug.processLogcat(this, "tagmo_logcat");
            String path = DocumentsUri.getPath(this, uri);
            String output = path != null ? Storage.getRelativePath(new File(path),
                    TagMo.getPrefs().preferEmulated().get()) : uri.getPath();
            new Toasty(this).Long(getString(R.string.wrote_logcat, output));
            startActivity(TagMo.getIntent(new Intent(this,
                    WebActivity_.class)).setData(uri));
        } catch (IOException e) {
            new Toasty(this).Short(e.getMessage());
        }
    }

    @UiThread
    public void showDownloadsSnackbar() {
        Snackbar snackbar = new IconifiedSnackbar(this).buildSnackbar(
                getString(R.string.downloads_hidden), Snackbar.LENGTH_LONG, null);
        snackbar.setAction(R.string.enable, v -> setDownloadResult());
        snackbar.show();
    }

    @OptionsItem(R.id.build_foomiibo)
    @Background
    void onBuildFoomiiboClicked() {
        try {
            Foomiibo.generateDirectory(settings.getAmiiboManager(),
                    Storage.getDownloadDir("TagMo", "Foomiibo"));
        } catch (Exception e) {
            Debug.Log(e);
        } finally {
            if (TagMo.getPrefs().showDownloads().get())
                setRefreshResult();
            else
                showDownloadsSnackbar();
        }
    }

    private int getQueryCount(String queryText) {
        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null)
            return 0;
        Set<Long> items = new HashSet<>();
        for (Amiibo amiibo : amiiboManager.amiibos.values()) {
            if (settings.amiiboContainsQuery(amiibo, queryText))
                items.add(amiibo.id);
        }
        return items.size();
    }

    enum FILTER {
        GAME_SERIES,
        CHARACTER,
        AMIIBO_SERIES,
        AMIIBO_TYPE
    }

    private int getFilteredCount(String filter, FILTER filterType) {
        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null)
            return 0;

        Set<Long> items = new HashSet<>();
        for (Amiibo amiibo : amiiboManager.amiibos.values()) {
            switch (filterType) {
                case GAME_SERIES:
                    GameSeries gameSeries = amiibo.getGameSeries();
                    if (gameSeries != null &&
                            Amiibo.matchesCharacterFilter(amiibo.getCharacter(),
                                    settings.getCharacterFilter()) &&
                            Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(),
                                    settings.getAmiiboSeriesFilter()) &&
                            Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(),
                                    settings.getAmiiboTypeFilter())
                    ) {
                        if (gameSeries.name.equals(filter))
                            items.add(amiibo.id);
                    }
                    break;
                case CHARACTER:
                    Character character = amiibo.getCharacter();
                    if (character != null &&
                            Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(),
                                    settings.getGameSeriesFilter()) &&
                            Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(),
                                    settings.getAmiiboSeriesFilter()) &&
                            Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(),
                                    settings.getAmiiboTypeFilter())
                    ) {
                        if (character.name.equals(filter))
                            items.add(amiibo.id);
                    }
                    break;
                case AMIIBO_SERIES:
                    AmiiboSeries amiiboSeries = amiibo.getAmiiboSeries();
                    if (amiiboSeries != null &&
                            Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(),
                                    settings.getGameSeriesFilter()) &&
                            Amiibo.matchesCharacterFilter(amiibo.getCharacter(),
                                    settings.getCharacterFilter()) &&
                            Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(),
                                    settings.getAmiiboTypeFilter())
                    ) {
                        if (amiiboSeries.name.equals(filter))
                            items.add(amiibo.id);
                    }
                    break;
                case AMIIBO_TYPE:
                    AmiiboType amiiboType = amiibo.getAmiiboType();
                    if (amiiboType != null &&
                            Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(),
                                    settings.getGameSeriesFilter()) &&
                            Amiibo.matchesCharacterFilter(amiibo.getCharacter(),
                                    settings.getCharacterFilter()) &&
                            Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(),
                                    settings.getAmiiboSeriesFilter())
                    ) {
                        if (amiiboType.name.equals(filter))
                            items.add(amiibo.id);
                    }
                    break;
            }
        }
        return items.size();
    }

    @OptionsItem(R.id.filter_game_series)
    boolean onFilterGameSeriesClick() {
        SubMenu subMenu = menuFilterGameSeries.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null)
            return false;

        Set<String> items = new HashSet<>();
        for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
            if (amiibo == null)
                continue;

            GameSeries gameSeries = amiibo.getGameSeries();
            if (gameSeries != null &&
                    Amiibo.matchesCharacterFilter(amiibo.getCharacter(),
                            settings.getCharacterFilter()) &&
                    Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(),
                            settings.getAmiiboSeriesFilter()) &&
                    Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(),
                            settings.getAmiiboTypeFilter())
            ) {
                items.add(gameSeries.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_game_series_group, Menu.NONE, 0, item)
                    .setChecked(item.equals(settings.getGameSeriesFilter()))
                    .setOnMenuItemClickListener(onFilterGameSeriesItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_game_series_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterGameSeriesItemClick =
            new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setGameSeriesFilter(menuItem.getTitle().toString());
            settings.notifyChanges();
            filteredCount = getFilteredCount(menuItem.getTitle().toString(), FILTER.GAME_SERIES);
            return false;
        }
    };

    @OptionsItem(R.id.filter_character)
    boolean onFilterCharacterClick() {
        SubMenu subMenu = menuFilterCharacter.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null)
            return true;

        Set<String> items = new HashSet<>();
        for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
            if (amiibo == null)
                continue;

            Character character = amiibo.getCharacter();
            if (character != null &&
                    Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(),
                            settings.getGameSeriesFilter()) &&
                    Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(),
                            settings.getAmiiboSeriesFilter()) &&
                    Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(),
                            settings.getAmiiboTypeFilter())
            ) {
                items.add(character.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_character_group, Menu.NONE, 0, item)
                    .setChecked(item.equals(settings.getCharacterFilter()))
                    .setOnMenuItemClickListener(onFilterCharacterItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_character_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterCharacterItemClick =
            new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setCharacterFilter(menuItem.getTitle().toString());
            settings.notifyChanges();
            filteredCount = getFilteredCount(menuItem.getTitle().toString(), FILTER.CHARACTER);
            return false;
        }
    };

    @OptionsItem(R.id.filter_amiibo_series)
    boolean onFilterAmiiboSeriesClick() {
        SubMenu subMenu = menuFilterAmiiboSeries.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null)
            return true;

        Set<String> items = new HashSet<>();
        for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
            if (amiibo == null)
                continue;

            AmiiboSeries amiiboSeries = amiibo.getAmiiboSeries();
            if (amiiboSeries != null &&
                    Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(),
                            settings.getGameSeriesFilter()) &&
                    Amiibo.matchesCharacterFilter(amiibo.getCharacter(),
                            settings.getCharacterFilter()) &&
                    Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(),
                            settings.getAmiiboTypeFilter())
            ) {
                items.add(amiiboSeries.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_amiibo_series_group, Menu.NONE, 0, item)
                    .setChecked(item.equals(settings.getAmiiboSeriesFilter()))
                    .setOnMenuItemClickListener(onFilterAmiiboSeriesItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_amiibo_series_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterAmiiboSeriesItemClick
            = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setAmiiboSeriesFilter(menuItem.getTitle().toString());
            settings.notifyChanges();
            filteredCount = getFilteredCount(menuItem.getTitle().toString(), FILTER.AMIIBO_SERIES);
            return false;
        }
    };

    @OptionsItem(R.id.filter_amiibo_type)
    boolean onFilterAmiiboTypeClick() {
        SubMenu subMenu = menuFilterAmiiboType.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null)
            return true;

        Set<AmiiboType> items = new HashSet<>();
        for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
            if (amiibo == null)
                continue;

            AmiiboType amiiboType = amiibo.getAmiiboType();
            if (amiiboType != null &&
                    Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(),
                            settings.getGameSeriesFilter()) &&
                    Amiibo.matchesCharacterFilter(amiibo.getCharacter(),
                            settings.getCharacterFilter()) &&
                    Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(),
                            settings.getAmiiboSeriesFilter())
            ) {
                items.add(amiiboType);
            }
        }

        ArrayList<AmiiboType> list = new ArrayList<>(items);
        Collections.sort(list);
        for (AmiiboType item : list) {
            subMenu.add(R.id.filter_amiibo_type_group, Menu.NONE, 0, item.name)
                    .setChecked(item.name.equals(settings.getAmiiboTypeFilter()))
                    .setOnMenuItemClickListener(onFilterAmiiboTypeItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_amiibo_type_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterAmiiboTypeItemClick
            = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setAmiiboTypeFilter(menuItem.getTitle().toString());
            settings.notifyChanges();
            filteredCount = getFilteredCount(menuItem.getTitle().toString(), FILTER.AMIIBO_TYPE);
            return false;
        }
    };

    public void setDownloadResult() {
        this.settings.setShowDownloads(true);
        this.settings.notifyChanges();
        this.onRefresh();
    }

    public void setPowerTagResult() {
        this.loadPTagKeyManager();
    }

    public void setRefreshResult() {
        this.onRefresh();
    }

    @OptionsItem(R.id.settings)
    void openSettings() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        if (!preferences.isShown()) {
            preferences.setVisibility(View.VISIBLE);
            SettingsFragment fragment = new SettingsFragment_();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.preferences, fragment)
                    .commit();
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    public void onRefresh() {
        this.swipeRefreshLayout.setRefreshing(false);
        this.loadAmiiboManager();
        this.onRootFolderChanged(true);
    }

    private void onStorageEnabled() {
        boolean internal = TagMo.getPrefs().preferEmulated().get();
        if (Storage.getFile(internal).exists() && Storage.hasPhysicalStorage()) {
            switchStorageRoot.setText(internal
                    ? R.string.emulated_storage_root
                    : R.string.physical_storage_root);
        } else {
            switchStorageRoot.setVisibility(View.GONE);
        }
        if (keyManager.isKeyMissing()) {
            showOngoingSnackbar(getString(R.string.locating_keys));
            locateKeyFiles();
        } else {
            this.onRefresh();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuCompat.setGroupDividerEnabled(menu, true);
        boolean result = super.onCreateOptionsMenu(menu);

        this.onSortChanged();
        this.onViewChanged();
        this.onShowDownloadsChanged();
        this.onRecursiveFilesChanged();
        this.onShowMissingChanged();
        this.onEnableScaleChanged();

        // setOnQueryTextListener will clear this, so make a copy
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menuSearch.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setSubmitButtonEnabled(true);
        searchView.setOnQueryTextListener(this);
        menuSearch.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    onBackPressed();
                    return false;
                } else if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    onBackPressed();
                    return false;
                } else {
                    return true;
                }
            }
        });

        //focus the SearchView
        String query = settings.getQuery();
        if (!query.isEmpty()) {
            menuSearch.expandActionView();
            searchView.setQuery(query, true);
            searchView.clearFocus();
        }

        return result;
    }

    private void openAmiiboViewer(AmiiboFile amiiboFile) {
        Bundle args = new Bundle();
        try {
            byte[] data = amiiboFile.getData() != null ? amiiboFile.getData()
                        : TagUtils.getValidatedFile(keyManager, amiiboFile.getFilePath());
            args.putByteArray(TagMo.EXTRA_TAG_DATA, data);
        } catch (Exception e) {
            Debug.Log(e);
            return;
        }

        Intent intent = new Intent(this, AmiiboActivity_.class);
        intent.putExtras(args);
        onAmiiboActivity.launch(intent);
    }

    @Override
    public void onAmiiboClicked(AmiiboFile amiiboFile) {
        if (amiiboFile.getFilePath() == null)
            return;

        clickedAmiibo = amiiboFile;
        openAmiiboViewer(amiiboFile);
    }

    @Override
    public void onAmiiboImageClicked(AmiiboFile amiiboFile) {
        Bundle bundle = new Bundle();
        bundle.putLong(TagMo.EXTRA_AMIIBO_ID, amiiboFile.getId());

        Intent intent = new Intent(this, ImageActivity_.class);
        intent.putExtras(bundle);

        this.startActivity(intent);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        settings.setQuery(query);
        settings.notifyChanges();
        setAmiiboStats();
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        settings.setQuery(newText);
        settings.notifyChanges();
        if (newText.length() == 0)
            setAmiiboStats();
        return true;
    }

    static final String BACKGROUND_UPDATE = "github";

    void checkForUpdate() {
        BackgroundExecutor.cancelAll(BACKGROUND_UPDATE, true);
        checkForUpdateTask();
    }

    @Background
    void installUpdateTask(String apkUrl) {
        File apk = new File(getFilesDir(), apkUrl.substring(
                apkUrl.lastIndexOf(File.separator) + 1));
        try {
            DataInputStream dis = new DataInputStream(new URL(apkUrl).openStream());

            byte[] buffer = new byte[1024];
            int length;
            FileOutputStream fos = new FileOutputStream(apk);
            while ((length = dis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fos.close();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Uri apkUri = Storage.getFileUri(apk);
                PackageInstaller installer = getPackageManager().getPackageInstaller();
                ContentResolver resolver = getContentResolver();
                InputStream apkStream = resolver.openInputStream(apkUri);
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int sessionId = installer.createSession(params);
                PackageInstaller.Session session = installer.openSession(sessionId);
                DocumentFile document = DocumentFile.fromSingleUri(this, apkUri);
                if (document == null)
                    throw new IOException(getString(R.string.fail_invalid_size));
                OutputStream sessionStream = session.openWrite(
                        "NAME", 0, document.length());
                byte[] buf = new byte[8192];
                int size;
                while ((size = apkStream.read(buf)) > 0) {
                    sessionStream.write(buf, 0, size);
                }
                session.fsync(sessionStream);
                apkStream.close();
                sessionStream.close();
                PendingIntent pi = PendingIntent.getBroadcast(this, 8675309,
                        new Intent(this, InstallReceiver.class),
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                                : PendingIntent.FLAG_UPDATE_CURRENT);
                session.commit(pi.getIntentSender());
            } else {
                Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                intent.setDataAndType(Storage.getFileUri(apk),
                        getString(R.string.mimetype_apk));
                intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME,
                        getApplicationInfo().packageName);
                startActivity(TagMo.getIntent(intent));
            }
        } catch (MalformedURLException mue) {
            Debug.Log(mue);
        } catch (IOException ioe) {
            Debug.Log(ioe);
        } catch (SecurityException se) {
            Debug.Log(se);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    ActivityResultLauncher<Intent> onRequestInstall = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (getPackageManager().canRequestPackageInstalls())
            installUpdateTask(TagMo.getPrefs().downloadUrl().get());
        TagMo.getPrefs().downloadUrl().remove();
    });

    public void installUpdateCompat(String apkUrl) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (getPackageManager().canRequestPackageInstalls()) {
                installUpdateTask(apkUrl);
            } else {
                TagMo.getPrefs().downloadUrl().put(apkUrl);
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
                onRequestInstall.launch(intent);
            }
        } else {
            installUpdateTask(apkUrl);
        }
    }

    private void parseUpdateJSON(String result, boolean isMaster) {
        String lastCommit = null, downloadUrl = null;
        try {
            JSONObject jsonObject = (JSONObject) new JSONTokener(result).nextValue();
            lastCommit = ((String) jsonObject.get("name")).substring(6);
            JSONArray assets = (JSONArray) jsonObject.get("assets");
            JSONObject asset = (JSONObject) assets.get(0);
            downloadUrl = (String) asset.get("browser_download_url");
            if (!isMaster && !BuildConfig.COMMIT.equals(lastCommit))
                installUpdateCompat(downloadUrl);
        } catch (JSONException e) {
            Debug.Log(e);
        }

        if (isMaster && lastCommit != null && downloadUrl != null) {
            String finalLastCommit = lastCommit;
            String finalDownloadUrl = downloadUrl;
            new JSONExecutor(Website.TAGMO_GIT_API + "experimental")
                    .setResultListener(experimental -> {
                try {
                    JSONObject jsonObject = (JSONObject) new JSONTokener(experimental).nextValue();
                    String extraCommit = ((String) jsonObject.get("name")).substring(6);
                    if (!BuildConfig.COMMIT.equals(extraCommit)
                            && !BuildConfig.COMMIT.equals(finalLastCommit))
                        installUpdateCompat(finalDownloadUrl);
                } catch (JSONException e) {
                    Debug.Log(e);
                }
            });
        }
    }

    @Background(id = BACKGROUND_UPDATE)
    void checkForUpdateTask() {
        boolean isMaster = TagMo.getPrefs().stableChannel().get();
        new JSONExecutor(Website.TAGMO_GIT_API + (isMaster
                ? "master" : "experimental")).setResultListener(result -> {
            if (result != null) parseUpdateJSON(result, isMaster);
        });
    }

    static final String BACKGROUND_POWERTAG = "powertag";

    void loadPTagKeyManager() {
        if (TagMo.getPrefs().enablePowerTagSupport().get()) {
            BackgroundExecutor.cancelAll(BACKGROUND_POWERTAG, true);
            loadPTagKeyManagerTask();
        }
    }

    @Background(id = BACKGROUND_POWERTAG)
    void loadPTagKeyManagerTask() {
        try {
            PowerTagManager.getPowerTagManager();
        } catch (Exception e) {
            Debug.Log(e);
            new Toasty(this).Short(R.string.fail_powertag_keys);
        }
    }

    static final String BACKGROUND_AMIIBO_MANAGER = "amiibo_manager";

    void loadAmiiboManager() {
        BackgroundExecutor.cancelAll(BACKGROUND_AMIIBO_MANAGER, true);
        loadAmiiboManagerTask();
    }

    @Background(id = BACKGROUND_AMIIBO_MANAGER)
    void loadAmiiboManagerTask() {
        AmiiboManager amiiboManager;
        try {
            amiiboManager = AmiiboManager.getAmiiboManager();
        } catch (IOException | JSONException | ParseException e) {
            Debug.Log(e);
            amiiboManager = null;
            new Toasty(this).Short(R.string.amiibo_info_parse_error);
        }

        if (Thread.currentThread().isInterrupted())
            return;

        final AmiiboManager uiAmiiboManager = amiiboManager;
        this.runOnUiThread(() -> {
            settings.setAmiiboManager(uiAmiiboManager);
            settings.notifyChanges();
        });
    }

    static final String BACKGROUND_FOLDERS = "folders";

    void loadFolders(File rootFolder) {
        BackgroundExecutor.cancelAll(BACKGROUND_FOLDERS, true);
        loadFoldersTask(rootFolder);
    }

    ArrayList<File> listFolders(File rootFolder) {
        ArrayList<File> folders = new ArrayList<>();
        File[] files = rootFolder.listFiles();
        if (files == null)
            return folders;
        for (File file : files) {
            if (file.isDirectory()) {
                folders.add(file);
            }
        }
        return folders;
    }

    @Background(id = BACKGROUND_FOLDERS)
    void loadFoldersTask(File rootFolder) {
        final ArrayList<File> folders = listFolders(rootFolder);
        Collections.sort(folders, (file1, file2) -> file1.getPath().compareToIgnoreCase(file2.getPath()));

        if (Thread.currentThread().isInterrupted())
            return;

        this.runOnUiThread(() -> {
            settings.setFolders(folders);
            settings.notifyChanges();
        });
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
        if (this.settings.isShowingDownloads()) {
            File download = Storage.getDownloadDir("TagMo");
            File[] files = rootFolder.listFiles((dir, name) -> name.equals(download.getName()));
            if (download != rootFolder && (files == null || files.length == 0))
                amiiboFiles.addAll(AmiiboManager.listAmiibos(keyManager, download, recursiveFiles));
        }

        if (Thread.currentThread().isInterrupted())
            return;

        this.runOnUiThread(() -> {
            settings.setAmiiboFiles(amiiboFiles);
            settings.notifyChanges();
        });
    }

    ActivityResultLauncher<Intent> onDocumentTree = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;

        Uri treeUri = result.getData().getData();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);

        // List all existing files inside picked directory
        if (pickedDir != null) {
            final ArrayList<AmiiboFile> amiiboFiles = AmiiboManager
                    .listAmiiboDocuments(keyManager, pickedDir, this.settings.isRecursiveEnabled());
            this.runOnUiThread(() -> {
                settings.setAmiiboFiles(amiiboFiles);
                settings.notifyChanges();
            });
        }

//            // Create a new file and write into it
//            DocumentFile newFile = pickedDir.createFile(getResources().getStringArray(
//                    R.array.mimetype_bin)[0], fileName + ".bin");
//            if (newFile != null) {
//                try (OutputStream outputStream = getContentResolver()
//                        .openOutputStream(newFile.getUri())) {
//                    outputStream.write(tagData);
//                } catch (FileNotFoundException e) {
//                    e.printStackTrace();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }

    });

    @Override
    public void onBrowserSettingsChanged(BrowserSettings newBrowserSettings,
                                         BrowserSettings oldBrowserSettings) {
        if (newBrowserSettings == null || oldBrowserSettings == null) return;
        boolean folderChanged = false;
        if (!BrowserSettings.equals(newBrowserSettings.getBrowserRootFolder(),
                oldBrowserSettings.getBrowserRootFolder())) {
            folderChanged = true;
        }
        if (newBrowserSettings.isShowingDownloads() != oldBrowserSettings.isShowingDownloads()) {
            folderChanged = true;
            onShowDownloadsChanged();
        }
        if (newBrowserSettings.isRecursiveEnabled() != oldBrowserSettings.isRecursiveEnabled()) {
            folderChanged = true;
            onRecursiveFilesChanged();
        }
        if (newBrowserSettings.isShowingMissingFiles() != oldBrowserSettings.isShowingMissingFiles()) {
            folderChanged = true;
            onShowMissingChanged();
        }
        if (folderChanged) {
            onRootFolderChanged(true);
        }

        if (newBrowserSettings.getSort() != oldBrowserSettings.getSort()) {
            onSortChanged();
        }
        if (!BrowserSettings.equals(newBrowserSettings.getGameSeriesFilter(),
                oldBrowserSettings.getGameSeriesFilter())) {
            onFilterGameSeriesChanged();
        }
        if (!BrowserSettings.equals(newBrowserSettings.getCharacterFilter(),
                oldBrowserSettings.getCharacterFilter())) {
            onFilterCharacterChanged();
        }
        if (!BrowserSettings.equals(newBrowserSettings.getAmiiboSeriesFilter(),
                oldBrowserSettings.getAmiiboSeriesFilter())) {
            onFilterAmiiboSeriesChanged();
        }
        if (!BrowserSettings.equals(newBrowserSettings.getAmiiboTypeFilter(),
                oldBrowserSettings.getAmiiboTypeFilter())) {
            onFilterAmiiboTypeChanged();
        }
        if (newBrowserSettings.getAmiiboView() != oldBrowserSettings.getAmiiboView()) {
            onViewChanged();
        }
        if (!BrowserSettings.equals(newBrowserSettings.getAmiiboFiles(),
                oldBrowserSettings.getAmiiboFiles())) {
            onAmiiboFilesChanged();
        }

        TagMo.getPrefs().edit()
                .browserRootFolder().put(Storage.getRelativePath(
                        newBrowserSettings.getBrowserRootFolder(),
                TagMo.getPrefs().preferEmulated().get()))
                .query().put(newBrowserSettings.getQuery())
                .sort().put(newBrowserSettings.getSort())
                .filterGameSeries().put(newBrowserSettings.getGameSeriesFilter())
                .filterCharacter().put(newBrowserSettings.getCharacterFilter())
                .filterAmiiboSeries().put(newBrowserSettings.getAmiiboSeriesFilter())
                .filterAmiiboType().put(newBrowserSettings.getAmiiboTypeFilter())
                .browserAmiiboView().put(newBrowserSettings.getAmiiboView())
                .imageNetworkSetting().put(newBrowserSettings.getImageNetworkSettings())
                .showDownloads().put(newBrowserSettings.isShowingDownloads())
                .recursiveFolders().put(newBrowserSettings.isRecursiveEnabled())
                .showMissingFiles().put(newBrowserSettings.isShowingMissingFiles())
                .apply();

        File rootFolder = newBrowserSettings.getBrowserRootFolder();
        String relativeRoot = Storage.getRelativePath(rootFolder,
                TagMo.getPrefs().preferEmulated().get());
        setFolderText(relativeRoot.length() > 1 ? relativeRoot : rootFolder.getAbsolutePath(),
                folderChanged ? 3000 : 1500);
    }

    private void onAmiiboFilesChanged() {
        if (settings.getAmiiboFiles() == null || settings.getAmiiboFiles().size() == 0) {
            showOngoingSnackbar(getString(R.string.amiibo_not_found));
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            if (ongoingSnackbar != null && ongoingSnackbar.isShown())
                ongoingSnackbar.dismiss();
        }
    }

    void onSortChanged() {
        if (menuSortId == null)
            return;
        switch (SORT.valueOf(settings.getSort())) {
            case ID:
                menuSortId.setChecked(true);
                break;
            case NAME:
                menuSortName.setChecked(true);
                break;
            case GAME_SERIES:
                menuSortGameSeries.setChecked(true);
                break;
            case CHARACTER:
                menuSortCharacter.setChecked(true);
                break;
            case AMIIBO_SERIES:
                menuSortAmiiboSeries.setChecked(true);
                break;
            case AMIIBO_TYPE:
                menuSortAmiiboType.setChecked(true);
                break;
            case FILE_PATH:
                menuSortFilePath.setChecked(true);
                break;
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

    void onViewChanged() {
        if (menuViewSimple == null)
            return;
        switch(VIEW.valueOf(settings.getAmiiboView())) {
            case SIMPLE:
                menuViewSimple.setChecked(true);
                break;
            case COMPACT:
                menuViewCompact.setChecked(true);
                break;
            case LARGE:
                menuViewLarge.setChecked(true);
                break;
            case IMAGE:
                menuViewImage.setChecked(true);
                break;
        }
    }

    void onRootFolderChanged(boolean indicator) {
        if (this.settings != null) {
            File rootFolder = this.settings.getBrowserRootFolder();
            if (!keyManager.isKeyMissing()) {
                if (indicator) showOngoingSnackbar(getString(R.string.refreshing_list));
                this.loadAmiiboFiles(rootFolder, this.settings.isRecursiveEnabled());
            }
            this.loadFolders(rootFolder);
        }
    }

    void onFilterGameSeriesChanged() {
        addFilterItemView(settings.getGameSeriesFilter(),
                "filter_game_series", onFilterGameSeriesChipCloseClick);
    }

    OnCloseClickListener onFilterGameSeriesChipCloseClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(@NonNull View v) {
            settings.setGameSeriesFilter("");
            settings.notifyChanges();
            setAmiiboStats();
        }
    };

    void onFilterCharacterChanged() {
        addFilterItemView(settings.getCharacterFilter(), "filter_character",
                onFilterCharacterChipCloseClick);
    }

    OnCloseClickListener onFilterCharacterChipCloseClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(@NonNull View v) {
            settings.setCharacterFilter("");
            settings.notifyChanges();
            setAmiiboStats();
        }
    };

    void onFilterAmiiboSeriesChanged() {
        addFilterItemView(settings.getAmiiboSeriesFilter(), "filter_amiibo_series",
                onFilterAmiiboSeriesChipCloseClick);
    }

    OnCloseClickListener onFilterAmiiboSeriesChipCloseClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(@NonNull View v) {
            settings.setAmiiboSeriesFilter("");
            settings.notifyChanges();
            setAmiiboStats();
        }
    };

    void onFilterAmiiboTypeChanged() {
        addFilterItemView(settings.getAmiiboTypeFilter(),
                "filter_amiibo_type", onAmiiboTypeChipCloseClick);
    }

    OnCloseClickListener onAmiiboTypeChipCloseClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(@NonNull View v) {
            settings.setAmiiboTypeFilter("");
            settings.notifyChanges();
            setAmiiboStats();
        }
    };

    void onShowDownloadsChanged() {
        if (menuShowDownloads == null)
            return;

        menuShowDownloads.setChecked(settings.isShowingDownloads());
    }

    void onRecursiveFilesChanged() {
        if (menuRecursiveFiles == null)
            return;

        menuRecursiveFiles.setChecked(settings.isRecursiveEnabled());
    }

    void onShowMissingChanged() {
        if (menuShowMissing == null)
            return;

        menuShowMissing.setChecked(settings.isShowingMissingFiles());
    }

    void onEnableScaleChanged() {
        if (menuEnableScale == null)
            return;

        menuEnableScale.setChecked(TagMo.getPrefs().enableScaling().get());
    }

    @SuppressLint("InflateParams")
    public void addFilterItemView(String text, String tag, OnCloseClickListener listener) {
        FrameLayout chipContainer = chipList.findViewWithTag(tag);
        chipList.removeView(chipContainer);
        if (!text.isEmpty()) {
            chipContainer = (FrameLayout) getLayoutInflater().inflate(R.layout.chip_view, null);
            chipContainer.setTag(tag);
            Chip chip = chipContainer.findViewById(R.id.chip);
            chip.setText(text);
            chip.setClosable(true);
            chip.setOnCloseClickListener(listener);
            chipList.addView(chipContainer);
            chipList.setVisibility(View.VISIBLE);
        } else if (chipList.getChildCount() == 0) {
            chipList.setVisibility(View.GONE);
        }
    }

    private int[] getAdapterStats() {
        BrowserAmiibosAdapter adapter = (BrowserAmiibosAdapter) amiibosView.getAdapter();
        if (adapter == null) return new int[] { 0, 0 };
        int size = adapter.getItemCount();
        int count = 0;
        for (Amiibo amiibo : settings.getAmiiboManager().amiibos.values()) {
            for (int x = 0; x < size; x++) {
                if (amiibo.id == adapter.getItemId(x)) {
                    count += 1;
                    break;
                }
            }
        }
        return new int[] { size, count };
    }

    private void setAmiiboStatsText() {
        int size = settings.getAmiiboFiles().size();
        if (size <= 0) return;
        currentFolderView.setGravity(Gravity.CENTER);
        if (settings.getAmiiboManager() != null) {
            int count = 0;
            if (!settings.getQuery().isEmpty()) {
                int[] stats = getAdapterStats();
                currentFolderView.setText(getString(R.string.amiibo_collected,
                        stats[0], stats[1], getQueryCount(settings.getQuery())));
            } else if (settings.hasFilteredData()) {
                int[] stats = getAdapterStats();
                currentFolderView.setText(getString(R.string.amiibo_collected,
                        stats[0], stats[1], filteredCount));
            } else {
                for (Amiibo amiibo : settings.getAmiiboManager().amiibos.values()) {
                    for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
                        if (amiibo.id == amiiboFile.getId()) {
                            count += 1;
                            break;
                        }
                    }
                }
                currentFolderView.setText(getString(R.string.amiibo_collected,
                        size, count, settings.getAmiiboManager().amiibos.size()));
            }
        } else {
            currentFolderView.setText(getString(R.string.files_displayed, size));
        }
    }

    private void setFolderText(String text, int delay) {
        this.currentFolderView.setGravity(Gravity.NO_GRAVITY);
        this.currentFolderView.setText(text);
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::setAmiiboStatsText, delay);
    }

    private void setAmiiboStats() {
        handler.removeCallbacksAndMessages(null);
        setAmiiboStatsText();
    }

    @UiThread
    void showOngoingSnackbar(String msg) {
        if (ongoingSnackbar != null && ongoingSnackbar.isShown())
            ongoingSnackbar.dismiss();
        ongoingSnackbar = new IconifiedSnackbar(this, findViewById(R.id.main_layout))
                .buildTickerBar(msg, Snackbar.LENGTH_INDEFINITE);
        ongoingSnackbar.show();
    }

    @UiThread
    public void showSetupSnackbar() {
        if (ongoingSnackbar != null && ongoingSnackbar.isShown())
            ongoingSnackbar.dismiss();
        ongoingSnackbar = new IconifiedSnackbar(this, findViewById(R.id.main_layout))
                .buildTickerBar(getString(R.string.keys_not_found), Snackbar.LENGTH_INDEFINITE);
        ongoingSnackbar.setAction(R.string.setup, v -> {
            openSettings();
            ongoingSnackbar.dismiss();
        });
        ongoingSnackbar.show();
    }

    private static final String BACKGROUND_LOAD_KEYS = "load_keys";

    private boolean keyNameMatcher(String name) {
        boolean isValid = name.toLowerCase(Locale.ROOT).endsWith(".bin");
        return name.toLowerCase(Locale.ROOT).endsWith("retail.bin") ||
                (isValid && (name.toLowerCase(Locale.ROOT).startsWith("locked")
                        || name.toLowerCase(Locale.ROOT).startsWith("unfixed")));
    }

    void locateKeyFiles() {
        BackgroundExecutor.cancelAll(BACKGROUND_LOAD_KEYS, true);
        locateKeyFilesTask();
    }

    void locateKeyFilesRecursive(File[] files) {
        if (files == null || files.length == 0)
            return;
        for (File file : files) {
            if (file.isDirectory()) {
                locateKeyFilesRecursive(file.listFiles());
            } else {
                if (keyNameMatcher(file.getName())) {
                    try {
                        this.keyManager.loadKey(file);
                    } catch (Exception e) {
                        Debug.Log(e);
                    }
                }
            }
        }
    }

    @Background(id = BACKGROUND_LOAD_KEYS)
    void locateKeyFilesTask() {
        File[] files = Storage.getDownloadDir(null)
                .listFiles((dir, name) -> keyNameMatcher(name));
        if (files != null && files.length > 0) {
            for (File file : files) {
                try {
                    this.keyManager.loadKey(file);
                } catch (Exception e) {
                    Debug.Log(e);
                }
            }
        } else {
            locateKeyFilesRecursive(settings.getBrowserRootFolder().listFiles());
        }

        if (Thread.currentThread().isInterrupted())
            return;

        if (keyManager.isKeyMissing()) {
            showSetupSnackbar();
        } else {
            this.onRefresh();
        }
    }

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";
    private static final String WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE";
    private static final String[] PERMISSIONS_STORAGE = {
            READ_EXTERNAL_STORAGE,
            WRITE_EXTERNAL_STORAGE
    };

    @RequiresApi(api = Build.VERSION_CODES.R)
    ActivityResultLauncher<Intent> onRequestScopedStorage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (Environment.isExternalStorageManager()) {
            this.onStorageEnabled();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
                intent.putExtra("android.content.extra.FANCY", true);
                onDocumentTree.launch(TagMo.getIntent(intent));
            }
        }
    });

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        int permission = ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            Snackbar storageBar = Snackbar.make(findViewById(R.id.coordinator),
                    R.string.permission_required, Snackbar.LENGTH_LONG);
            storageBar.setAction(R.string.allow, v -> ActivityCompat.requestPermissions(
                    BrowserActivity.this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE));
            storageBar.show();
        } else {
            this.onStorageEnabled();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    void requestScopedStorage() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        try {
            intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
        } catch (Exception e) {
            intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        }
        onRequestScopedStorage.launch(intent);
    }

//    @Override
//    protected void onNewIntent(Intent intent) {
//        super.onNewIntent(intent);
//
//        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())
//                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())
//                || NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
//            Intent reader = new Intent(this, NfcActivity_.class);
//            reader.setAction(intent.getAction());
//            reader.putExtras(intent.getExtras());
//            startActivity(reader);
//        }
//    }

    @Override
    public void onBackPressed() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void moveFile(File inputFile, String fileName, String outputPath) {
        InputStream in;
        OutputStream out;
        try {
            new File (outputPath).mkdirs();
            in = new FileInputStream(inputFile);
            out = new FileOutputStream(new File(outputPath, fileName));
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.flush();
            out.close();
            inputFile.delete();
        }
        catch (FileNotFoundException fnfe) {
            Debug.Log(fnfe);
        }
        catch (Exception e) {
            Debug.Log(e);
        }
    }

    private void moveDir(File directory, String destination) {
        File output = Storage.getDownloadDir("TagMo", destination);
        File[] files = directory.listFiles();
        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file.isDirectory()) {
                    new File(output, file.getName());
                    moveDir(file, destination + File.separator + file.getName());
                } else {
                    moveFile(file, file.getName(), output.getPath());
                }
            }
        }
    }

    private void callHousekeeping() {
        File[] logs = Storage.getDownloadDir("TagMo",
                "Logcat").listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".txt"));
        if (logs != null && logs.length > 0) {
            for (File file : logs) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        File[] files = getFilesDir().listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".apk"));
        if (files != null && files.length > 0) {
            for (File file : files) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        File logcat = Storage.getDownloadDir("TagMo(Logcat)");
        if (logcat.exists()) {
            moveDir(logcat, "Logcat");
        }
        //noinspection ResultOfMethodCallIgnored
        logcat.delete();
        File backup = Storage.getDownloadDir("TagMo(Backup)");
        if (backup.exists()) {
            moveDir(backup, "Backups");
        }
        //noinspection ResultOfMethodCallIgnored
        backup.delete();
    }
}
