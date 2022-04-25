package com.hiddenramblings.tagmo;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.TranslateAnimation;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.Preference;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.eightbitlab.blurview.BlurView;
import com.eightbitlab.blurview.BlurViewFacade;
import com.eightbitlab.blurview.RenderScriptBlur;
import com.eightbitlab.blurview.SupportRenderScriptBlur;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.security.ProviderInstaller;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.adapter.BrowserAmiibosAdapter;
import com.hiddenramblings.tagmo.adapter.BrowserFoldersAdapter;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.AmiiboSeries;
import com.hiddenramblings.tagmo.amiibo.AmiiboType;
import com.hiddenramblings.tagmo.amiibo.Character;
import com.hiddenramblings.tagmo.amiibo.GameSeries;
import com.hiddenramblings.tagmo.amiibo.GamesManager;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.eightbit.view.AnimatedLinearLayout;
import com.hiddenramblings.tagmo.nfctech.NTAG215;
import com.hiddenramblings.tagmo.nfctech.PowerTagManager;
import com.hiddenramblings.tagmo.nfctech.TagReader;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.nfctech.TagWriter;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.BrowserSettings.BrowserSettingsListener;
import com.hiddenramblings.tagmo.settings.BrowserSettings.SORT;
import com.hiddenramblings.tagmo.settings.BrowserSettings.VIEW;
import com.hiddenramblings.tagmo.settings.Preferences_;
import com.hiddenramblings.tagmo.settings.SettingsFragment;
import com.hiddenramblings.tagmo.widget.Toasty;
import com.robertlevonyan.views.chip.OnCloseClickListener;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Executors;

public class BrowserActivity extends AppCompatActivity implements
        SearchView.OnQueryTextListener,
        BrowserSettingsListener,
        BrowserAmiibosAdapter.OnAmiiboClickListener {

    private final Preferences_ prefs = TagMo.getPrefs();
    private CheckUpdatesTask updates;

    private AnimatedLinearLayout fakeSnackbar;
    private AppCompatImageView fakeSnackbarIcon;
    private TextView fakeSnackbarText;
    private ViewPager2 mainLayout;
    private FloatingActionButton nfcFab;
    private BrowserFragment browserFragment;
    private TextView currentFolderView;
    private DrawerLayout prefsDrawer;
    private AppCompatButton switchStorageRoot;

    private MenuItem menuSortId;
    private MenuItem menuSortName;
    private MenuItem menuSortGameSeries;
    private MenuItem menuSortCharacter;
    private MenuItem menuSortAmiiboSeries;
    private MenuItem menuSortAmiiboType;
    private MenuItem menuSortFilePath;
    private MenuItem menuFilterGameSeries;
    private MenuItem menuFilterCharacter;
    private MenuItem menuFilterAmiiboSeries;
    private MenuItem menuFilterAmiiboType;
    private MenuItem menuViewSimple;
    private MenuItem menuViewCompact;
    private MenuItem menuViewLarge;
    private MenuItem menuViewImage;
    private MenuItem menuRecursiveFiles;
    private MenuItem menuShowDownloads;
    private boolean isSearchVisible;

    private Snackbar fooSnackbar;

    private BlurView amiiboContainer;
    private Toolbar toolbar;
    private View amiiboInfo;
    private TextView txtError;
    private TextView txtTagId;
    private TextView txtName;
    private TextView txtGameSeries;
    // private TextView txtCharacter;
    private TextView txtAmiiboType;
    private TextView txtAmiiboSeries;
    private AppCompatImageView imageAmiibo;

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private SettingsFragment settingsFragment;
    private KeyManager keyManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int filteredCount;
    private AmiiboFile clickedAmiibo = null;

    private boolean ignoreTagTd;
    private BrowserSettings settings;
    private String updateUrl;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme);
        keyManager = new KeyManager(this);

        setContentView(R.layout.activity_browser);

        fakeSnackbar = findViewById(R.id.fake_snackbar);
        fakeSnackbarIcon = findViewById(R.id.snackbar_icon);
        fakeSnackbarText = findViewById(R.id.snackbar_text);
        mainLayout = findViewById(R.id.amiibo_pager);
        nfcFab = findViewById(R.id.nfc_fab);
        currentFolderView = findViewById(R.id.current_folder);
        prefsDrawer = findViewById(R.id.drawer_layout);
        switchStorageRoot = findViewById(R.id.switch_storage_root);
        amiiboContainer = findViewById(R.id.amiiboContainer);
        toolbar = findViewById(R.id.toolbar);
        amiiboInfo = findViewById(R.id.amiiboInfo);
        txtError = findViewById(R.id.txtError);
        txtTagId = findViewById(R.id.txtTagId);
        txtName = findViewById(R.id.txtName);
        txtGameSeries = findViewById(R.id.txtGameSeries);
        // txtCharacter = findViewById(R.id.txtCharacter);
        txtAmiiboType = findViewById(R.id.txtAmiiboType);
        txtAmiiboSeries = findViewById(R.id.txtAmiiboSeries);
        imageAmiibo = findViewById(R.id.imageAmiibo);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }

        NavPagerAdapter pagerAdapter = new NavPagerAdapter(this);
        mainLayout.setAdapter(pagerAdapter);
        browserFragment = pagerAdapter.getBrowser();

        mainLayout.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (position != 0) {
                    isSearchVisible = false;
                    if (null != fooSnackbar && fooSnackbar.isShown()) {
                        fooSnackbar.dismiss();
                    }
                }
                switch (position) {
                    case 0:
                        isSearchVisible = true;
                        setTitle(R.string.tagmo_browser);
                        break;
                    case 1:
                        setTitle(R.string.foomiibo);
                        break;
                    case 2:
                        setTitle(R.string.bluup_flask_ble);
                        break;
                }
                invalidateOptionsMenu();
            }
        });

        Intent intent = getIntent();
        if (null != intent) {
            if (getComponentName().equals(NFCIntent.FilterComponent)) {
                Intent browser = new Intent(this, BrowserActivity.class);
                browser.setAction(intent.getAction());
                browser.putExtras(intent.getExtras());
                browser.setData(intent.getData());
                startActivity(browser);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                this.onStorageEnabled();
            } else {
                requestScopedStorage();
            }
        } else {
            onRequestStorage.launch(PERMISSIONS_STORAGE);
        }

        if (null == this.settings) {
            this.settings = new BrowserSettings().initialize();
        } else {
            this.onFilterGameSeriesChanged();
            this.onFilterCharacterChanged();
            this.onFilterAmiiboSeriesChanged();
            this.onFilterAmiiboTypeChanged();
            this.onAmiiboFilesChanged();
        }
        this.settings.addChangeListener(this);

        AppCompatImageView toggle = findViewById(R.id.toggle);
        this.bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet));
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
            public void onSlide(@NonNull View bottomSheet, float slideOffset) { }
        });

        toggle.setOnClickListener(view -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        CoordinatorLayout coordinator = findViewById(R.id.coordinator);
        BlurViewFacade blurView = amiiboContainer.setupWith(coordinator)
                .setFrameClearDrawable(getWindow().getDecorView().getBackground())
                .setBlurRadius(2f).setBlurAutoUpdate(true)
                .setHasFixedTransformationMatrix(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            blurView.setBlurAlgorithm(new RenderScriptBlur(this));
        else
            blurView.setBlurAlgorithm(new SupportRenderScriptBlur(this));

        RecyclerView foldersView = findViewById(R.id.folders_list);
        foldersView.setLayoutManager(new LinearLayoutManager(this));
        foldersView.setAdapter(new BrowserFoldersAdapter(settings));
        this.settings.addChangeListener((BrowserSettingsListener) foldersView.getAdapter());

        this.loadPTagKeyManager();

        nfcFab.setOnClickListener(view -> {
            PopupMenu popup;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
                popup = new PopupMenu(this, nfcFab,
                        Gravity.END, 0, R.style.PopupMenu);
            else
                popup = new PopupMenu(this, nfcFab);
            try {
                for (Field field : popup.getClass().getDeclaredFields()) {
                    if ("mPopup".equals(field.getName())) {
                        field.setAccessible(true);
                        Object menuPopupHelper = field.get(popup);
                        if (null != menuPopupHelper) {
                            Method setForceIcons = Class.forName(menuPopupHelper.getClass().getName())
                                    .getMethod("setForceShowIcon", boolean.class);
                            setForceIcons.invoke(menuPopupHelper, true);
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                Debug.Log(e);
            }
            popup.getMenuInflater().inflate(R.menu.action_menu, popup.getMenu());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                popup.getMenu().findItem(R.id.mnu_flask).setTitle(R.string.bluup_flask_ble);
            else
                popup.getMenu().findItem(R.id.mnu_flask).setTitle(R.string.bluup_flask_web);
            popup.show();
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.mnu_scan) {
                    onNFCActivity.launch(new Intent(this,
                            NfcActivity.class).setAction(NFCIntent.ACTION_SCAN_TAG));
                    return true;
                } else if (item.getItemId() == R.id.mnu_flask) {
                    launchFlaskEditor();
                    return true;
                } else if (item.getItemId() == R.id.mnu_backup) {
                    Intent backup = new Intent(this, NfcActivity.class);
                    backup.setAction(NFCIntent.ACTION_BACKUP_AMIIBO);
                    onBackupActivity.launch(backup);
                    return true;
                } else if (item.getItemId() == R.id.mnu_validate) {
                    onValidateActivity.launch(new Intent(this,
                            NfcActivity.class).setAction(NFCIntent.ACTION_SCAN_TAG));
                    return true;
                }
                return false;
            });
        });

        findViewById(R.id.switch_storage_root).setOnClickListener(view -> {
            boolean external = !prefs.preferEmulated().get();
            switchStorageRoot.setText(external
                    ? R.string.emulated_storage_root
                    : R.string.physical_storage_root);
            this.settings.setBrowserRootFolder(Storage.getFile(external));
            this.settings.notifyChanges();
            prefs.preferEmulated().put(external);
        });

        findViewById(R.id.amiiboContainer).setOnClickListener(view ->
                amiiboContainer.setVisibility(View.GONE));

        if (!prefs.hasAcceptedTOS().get()) {
            try (InputStream in = getResources().openRawResource(R.raw.disclaimer);
                 BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
                StringBuilder total = new StringBuilder();
                String line;
                while (null != (line = r.readLine())) {
                    total.append(line).append("\n");
                }
                new AlertDialog.Builder(this)
                        .setMessage(total.toString())
                        .setCancelable(false)
                        .setPositiveButton(R.string.accept, (dialog, which) -> {
                            prefs.hasAcceptedTOS().put(true);
                            dialog.dismiss();
                        }).show().getWindow()
                        .setLayout(LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
            } catch (Exception e) {
                Debug.Log(e);
            }
        }

        if (null != intent && null != intent.getAction()
                && Intent.ACTION_VIEW.equals(intent.getAction())) {
            try {
                if (null != intent.getClipData()) {
                    for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
                        Uri uri = intent.getClipData().getItemAt(i).getUri();
                        byte[] data = TagReader.readTagDocument(uri);
                        updateAmiiboView(data, new AmiiboFile(new File(uri.getPath()),
                                TagUtils.amiiboIdFromTag(data), data));
                    }
                } else if (null != intent.getData()) {
                    Uri uri = intent.getData();
                    byte[] data = TagReader.readTagDocument(uri);
                    updateAmiiboView(data, new AmiiboFile(new File(uri.getPath()),
                            TagUtils.amiiboIdFromTag(data), data));
                }
            } catch (Exception ignored) {}
        }

        prefsDrawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                if (null == settingsFragment) settingsFragment = new SettingsFragment();
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.preferences, settingsFragment)
                        .commit();
                ((TextView) findViewById(R.id.build_text)).setText(
                        getString(R.string.build_hash, BuildConfig.COMMIT));
                findViewById(R.id.build_layout).setOnClickListener(view -> {
                    closePrefsDrawer();
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://github.com/HiddenRamblings/TagMo"
                    )));
                });
                findViewById(R.id.guide_layout).setOnClickListener(view -> {
                    closePrefsDrawer();
                    startActivity(new Intent(BrowserActivity.this, WebActivity.class)
                            .setAction(NFCIntent.SITE_GITLAB_README));
                });
                findViewById(R.id.donate_layout).setOnClickListener(view -> {
                    closePrefsDrawer();
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://www.paypal.com/donate/?hosted_button_id=Q2LFH2SC8RHRN"
                    )));
                });
                if (null != updateUrl) {
                    findViewById(R.id.build_layout).setOnClickListener(view -> {
                        closePrefsDrawer();
                        updates.installUpdateCompat(updateUrl);
                    });
                }
            }
        });
    }

    private void onProviderInstallerNotAvailable() {
        new Toasty(BrowserActivity.this).Long(R.string.fail_ssl_update);
        finish();
    }

    private void launchFlaskEditor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mainLayout.setCurrentItem(2, true);
        } else {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            CustomTabsIntent customTabsIntent = builder.build();
            builder.setCloseButtonIcon(BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_stat_notice_24dp));
            customTabsIntent.launchUrl(this, Uri.parse("https://flask.run/"));
        }
    }

    private final ActivityResultLauncher<Intent> onNFCActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || null == result.getData()) return;

        if (!NFCIntent.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        if (result.getData().hasExtra(NFCIntent.EXTRA_SIGNATURE)) {
            String signature = result.getData().getStringExtra(NFCIntent.EXTRA_SIGNATURE);
            prefs.settings_elite_signature().put(signature);
            int active_bank = result.getData().getIntExtra(
                    NFCIntent.EXTRA_ACTIVE_BANK, prefs.eliteActiveBank().get());
            prefs.eliteActiveBank().put(active_bank);
            int bank_count = result.getData().getIntExtra(
                    NFCIntent.EXTRA_BANK_COUNT, prefs.eliteBankCount().get());
            prefs.eliteBankCount().put(bank_count);

            Intent eliteIntent = new Intent(this, BankListActivity.class);
            eliteIntent.putExtras(result.getData());
            startActivity(eliteIntent);
        } else {
            mainLayout.setCurrentItem(0, true);
            updateAmiiboView(result.getData().getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA));
        }
    });

    private final ActivityResultLauncher<Intent> onBackupActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || null == result.getData()) return;

        if (!NFCIntent.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        byte[] tagData = result.getData().getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA);

        View view = getLayoutInflater().inflate(R.layout.dialog_backup,
                browserFragment.getAmiibosView(), false);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        final EditText input = view.findViewById(R.id.backup_entry);
        input.setText(TagUtils.decipherFilename(settings.getAmiiboManager(), tagData));
        Dialog backupDialog = dialog.setView(view).create();
        view.findViewById(R.id.save_backup).setOnClickListener(v -> {
            try {
                String fileName = TagUtils.writeBytesToFile(
                        Storage.getDownloadDir("TagMo", "Backups"),
                        input.getText().toString() + ".bin", tagData);
                new Toasty(this).Long(getString(R.string.wrote_file, fileName));
                this.onRootFolderChanged(false);
            } catch (IOException e) {
                new Toasty(this).Short(e.getMessage());
            }
            backupDialog.dismiss();
        });
        view.findViewById(R.id.cancel_backup).setOnClickListener(v -> backupDialog.dismiss());
        backupDialog.show();
    });

    private final ActivityResultLauncher<Intent> onValidateActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || null == result.getData()) return;

        if (!NFCIntent.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        try {
            TagUtils.validateData(result.getData().getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA));
            new IconifiedSnackbar(this, mainLayout).buildSnackbar(
                    R.string.validation_success, Snackbar.LENGTH_SHORT).show();
        } catch (Exception e) {
            new IconifiedSnackbar(this, mainLayout).buildSnackbar(e.getMessage(),
                    R.drawable.ic_baseline_bug_report_24dp, Snackbar.LENGTH_LONG).show();
        }
    });

    private void onRebuildDatabaseClicked() {
        if (null == settingsFragment || settingsFragment.isDetached())
            settingsFragment = new SettingsFragment();
        settingsFragment.rebuildAmiiboDatabase();
        this.recreate();
    }

    private void onCaptureLogcatClicked() {
        if (updates.hasPendingUpdate()) {
            updates.installUpdateCompat(updateUrl);
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (!Debug.processLogcat(this )) {
                    new Toasty(this).Long(getString(R.string.logcat_failed));
                }
            } catch (IOException e) {
                new Toasty(this).Short(e.getMessage());
            }
        });
    }

    private int getQueryCount(String queryText) {
        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (null == amiiboManager)
            return 0;
        Set<Long> items = new HashSet<>();
        for (Amiibo amiibo : amiiboManager.amiibos.values()) {
            if (settings.amiiboContainsQuery(amiibo, queryText))
                items.add(amiibo.id);
        }
        return items.size();
    }

    private enum FILTER {
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
                    if (null != gameSeries &&
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
                    if (null != character &&
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
                    if (null != amiiboSeries &&
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
                    if (null != amiiboType &&
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

    private boolean onFilterGameSeriesClick() {
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
            if (null != gameSeries &&
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

    private final MenuItem.OnMenuItemClickListener onFilterGameSeriesItemClick =
            new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setGameSeriesFilter(menuItem.getTitle().toString());
            settings.notifyChanges();
            filteredCount = getFilteredCount(menuItem.getTitle().toString(), FILTER.GAME_SERIES);
            return false;
        }
    };

    private boolean onFilterCharacterClick() {
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
            if (null != character &&
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

    private final MenuItem.OnMenuItemClickListener onFilterCharacterItemClick =
            new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    settings.setCharacterFilter(menuItem.getTitle().toString());
                    settings.notifyChanges();
                    filteredCount = getFilteredCount(menuItem.getTitle().toString(), FILTER.CHARACTER);
                    return false;
                }
            };

    private boolean onFilterAmiiboSeriesClick() {
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
            if (null != amiiboSeries &&
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

    private final MenuItem.OnMenuItemClickListener onFilterAmiiboSeriesItemClick
            = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setAmiiboSeriesFilter(menuItem.getTitle().toString());
            settings.notifyChanges();
            filteredCount = getFilteredCount(menuItem.getTitle().toString(), FILTER.AMIIBO_SERIES);
            return false;
        }
    };

    private boolean onFilterAmiiboTypeClick() {
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
            if (null != amiiboType &&
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

    private final MenuItem.OnMenuItemClickListener onFilterAmiiboTypeItemClick
            = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setAmiiboTypeFilter(menuItem.getTitle().toString());
            settings.notifyChanges();
            filteredCount = getFilteredCount(menuItem.getTitle().toString(), FILTER.AMIIBO_TYPE);
            return false;
        }
    };

    void checkForUpdates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            updates = new CheckUpdatesTask(this);
            updates.setUpdateListener(downloadUrl -> {
                updateUrl = downloadUrl;
                invalidateOptionsMenu();
            });

        } else {
            this.runOnUiThread(() -> ProviderInstaller.installIfNeededAsync(this,
                    new ProviderInstaller.ProviderInstallListener() {
                @Override
                public void onProviderInstalled() {
                    updates = new CheckUpdatesTask(BrowserActivity.this);
                    updates.setUpdateListener(downloadUrl -> {
                        updateUrl = downloadUrl;
                        invalidateOptionsMenu();
                    });
                }

                @Override
                public void onProviderInstallFailed(int errorCode, Intent recoveryIntent) {
                    GoogleApiAvailability availability = GoogleApiAvailability.getInstance();
                    if (availability.isUserResolvableError(errorCode)) {
                        availability.showErrorDialogFragment(
                                BrowserActivity.this, errorCode, 7000,
                                dialog -> onProviderInstallerNotAvailable());
                    } else {
                        onProviderInstallerNotAvailable();
                    }
                }
            }));
        }
    }

    public void onRefresh() {
        this.loadAmiiboManager();
        this.onRootFolderChanged(true);
    }

    private void onStorageEnabled() {
        boolean internal = prefs.preferEmulated().get();
        if (Storage.getFile(internal).exists() && Storage.hasPhysicalStorage()) {
            switchStorageRoot.setText(internal
                    ? R.string.emulated_storage_root
                    : R.string.physical_storage_root);
        } else {
            switchStorageRoot.setVisibility(View.GONE);
        }
        if (keyManager.isKeyMissing()) {
            showFakeSnackbar(getString(R.string.locating_keys));
            locateKeyFiles();
        } else {
            this.onRefresh();
            checkForUpdates();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.browser_menu, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);

        MenuItem menuSearch = menu.findItem(R.id.search);
        MenuItem menuUpdate = menu.findItem(R.id.install_update);
        menuSortId = menu.findItem(R.id.sort_id);
        menuSortName = menu.findItem(R.id.sort_name);
        menuSortGameSeries = menu.findItem(R.id.sort_game_series);
        menuSortCharacter = menu.findItem(R.id.sort_character);
        menuSortAmiiboSeries = menu.findItem(R.id.sort_amiibo_series);
        menuSortAmiiboType = menu.findItem(R.id.sort_amiibo_type);
        menuSortFilePath = menu.findItem(R.id.sort_file_path);
        menuFilterGameSeries = menu.findItem(R.id.filter_game_series);
        menuFilterCharacter = menu.findItem(R.id.filter_character);
        menuFilterAmiiboSeries = menu.findItem(R.id.filter_amiibo_series);
        menuFilterAmiiboType = menu.findItem(R.id.filter_amiibo_type);
        menuViewSimple = menu.findItem(R.id.view_simple);
        menuViewCompact = menu.findItem(R.id.view_compact);
        menuViewLarge = menu.findItem(R.id.view_large);
        menuViewImage = menu.findItem(R.id.view_image);
        menuRecursiveFiles = menu.findItem(R.id.recursive);
        menuShowDownloads = menu.findItem(R.id.show_downloads);

        if (null == this.settings) return false;

        this.onSortChanged();
        this.onViewChanged();
        this.onRecursiveFilesChanged();
        this.onShowDownloadsChanged();

        if (isSearchVisible) {
            menuSearch.setVisible(true);
            // setOnQueryTextListener will clear this, so make a copy
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            SearchView searchView = (SearchView) menuSearch.getActionView();
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            searchView.setSubmitButtonEnabled(false);
            searchView.setOnQueryTextListener(this);
            menuSearch.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem menuItem) {
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                    if (BottomSheetBehavior.STATE_EXPANDED == bottomSheetBehavior.getState()
                            || View.VISIBLE == amiiboContainer.getVisibility()
                            || getSupportFragmentManager().getBackStackEntryCount() > 0) {
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
        } else {
            menuSearch.setVisible(false);
        }

        menuUpdate.setVisible(null != updateUrl);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        RecyclerView amiibosView = browserFragment.getAmiibosView();
        if (item.getItemId() == R.id.install_update) {
            updates.installUpdateCompat(updateUrl);
        } else if (item.getItemId() == R.id.sort_id) {
            settings.setSort(SORT.ID.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.sort_name) {
            settings.setSort(SORT.NAME.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.sort_game_series) {
            settings.setSort(SORT.GAME_SERIES.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.sort_character) {
            settings.setSort(SORT.CHARACTER.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.sort_amiibo_series) {
            settings.setSort(SORT.AMIIBO_SERIES.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.sort_amiibo_type) {
            settings.setSort(SORT.AMIIBO_TYPE.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.sort_file_path) {
            settings.setSort(SORT.FILE_PATH.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.view_simple) {
            if (this.settings.getAmiiboView() == VIEW.IMAGE.getValue())
                amiibosView.setLayoutManager(new LinearLayoutManager(this));
            settings.setAmiiboView(VIEW.SIMPLE.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.view_compact) {
            if (this.settings.getAmiiboView() == VIEW.IMAGE.getValue())
                amiibosView.setLayoutManager(new LinearLayoutManager(this));
            settings.setAmiiboView(VIEW.COMPACT.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.view_large) {
            if (this.settings.getAmiiboView() == VIEW.IMAGE.getValue())
                amiibosView.setLayoutManager(new LinearLayoutManager(this));
            settings.setAmiiboView(VIEW.LARGE.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.view_image) {
            amiibosView.setLayoutManager(new GridLayoutManager(this, getColumnCount()));
            settings.setAmiiboView(VIEW.IMAGE.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.recursive) {
            this.settings.setRecursiveEnabled(!this.settings.isRecursiveEnabled());
            this.settings.notifyChanges();
        } else if (item.getItemId() == R.id.show_downloads) {
            this.settings.setShowDownloads(!this.settings.isShowingDownloads());
            this.settings.notifyChanges();
        } else if (item.getItemId() == R.id.tagmo_settings) {
            if (prefsDrawer.isDrawerOpen(GravityCompat.START))
                prefsDrawer.closeDrawer(GravityCompat.START);
            else
                prefsDrawer.openDrawer(GravityCompat.START);
        } else if (item.getItemId() == R.id.rebuild_database) {
            onRebuildDatabaseClicked();
        } else if (item.getItemId() == R.id.capture_logcat) {
            onCaptureLogcatClicked();
        } else if (item.getItemId() == R.id.filter_game_series) {
            return onFilterGameSeriesClick();
        } else if (item.getItemId() == R.id.filter_character) {
            return onFilterCharacterClick();
        } else if (item.getItemId() == R.id.filter_amiibo_series) {
            return onFilterAmiiboSeriesClick();
        } else if (item.getItemId() == R.id.filter_amiibo_type) {
            return onFilterAmiiboTypeClick();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onAmiiboClicked(AmiiboFile amiiboFile) {
        if (amiiboFile.getFilePath() == null)
            return;

        clickedAmiibo = amiiboFile;
        try {
            byte[] data = null != amiiboFile.getData() ? amiiboFile.getData()
                    : TagUtils.getValidatedFile(keyManager, amiiboFile.getFilePath());
            updateAmiiboView(data, amiiboFile);
        } catch (Exception e) {
            Debug.Log(e);
        }
    }

    @Override
    public void onAmiiboImageClicked(AmiiboFile amiiboFile) {
        Bundle bundle = new Bundle();
        bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiiboFile.getId());

        Intent intent = new Intent(this, ImageActivity.class);
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

    public void loadPTagKeyManager() {
        if (prefs.enable_power_tag_support().get()) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    PowerTagManager.getPowerTagManager();
                } catch (Exception e) {
                    Debug.Log(e);
                    new Toasty(this).Short(R.string.fail_powertag_keys);
                }
            });
        }
    }

    private void loadAmiiboManager() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AmiiboManager amiiboManager;
            try {
                amiiboManager = AmiiboManager.getAmiiboManager(getApplicationContext());
            } catch (IOException | JSONException | ParseException e) {
                Debug.Log(e);
                amiiboManager = null;
                new Toasty(this).Short(R.string.amiibo_info_parse_error);
            }

            if (Thread.currentThread().isInterrupted()) return;

            final AmiiboManager uiAmiiboManager = amiiboManager;
            this.runOnUiThread(() -> {
                settings.setAmiiboManager(uiAmiiboManager);
                settings.notifyChanges();
            });
        });
    }

    private ArrayList<File> listFolders(File rootFolder) {
        ArrayList<File> folders = new ArrayList<>();
        File[] files = rootFolder.listFiles();
        if (files == null || files.length == 0)
            return folders;
        for (File file : files) {
            if (file.isDirectory()) {
                folders.add(file);
            }
        }
        return folders;
    }

    private void loadFolders(File rootFolder) {
        Executors.newSingleThreadExecutor().execute(() -> {
            final ArrayList<File> folders = listFolders(rootFolder);
            Collections.sort(folders, (file1, file2) ->
                    file1.getPath().compareToIgnoreCase(file2.getPath()));

            if (Thread.currentThread().isInterrupted()) return;

            this.runOnUiThread(() -> {
                settings.setFolders(folders);
                settings.notifyChanges();
            });
        });
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
                if (settings.getAmiiboFiles().isEmpty()) {
                    onAmiiboFilesChanged();
                }
            });
        });
    }

    ActivityResultLauncher<Intent> onDocumentTree = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null)
            return;

        Uri treeUri = result.getData().getData();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);

        // List all existing files inside picked directory
        if (null != pickedDir) {
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
//            if (null != newFile) {
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
    public void onBrowserSettingsChanged(
            BrowserSettings newBrowserSettings,
            BrowserSettings oldBrowserSettings) {
        if (newBrowserSettings == null || oldBrowserSettings == null) return;
        boolean folderChanged = !BrowserSettings.equals(newBrowserSettings.getBrowserRootFolder(),
                oldBrowserSettings.getBrowserRootFolder());
        if (newBrowserSettings.isRecursiveEnabled() != oldBrowserSettings.isRecursiveEnabled()) {
            settings.getAmiiboFiles().clear();
            folderChanged = true;
            onRecursiveFilesChanged();
        }
        if (newBrowserSettings.isShowingDownloads() != oldBrowserSettings.isShowingDownloads()) {
            folderChanged = true;
            onShowDownloadsChanged();
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

        prefs.edit().browserRootFolder().put(Storage.getRelativePath(
                newBrowserSettings.getBrowserRootFolder(), prefs.preferEmulated().get()))
                .query().put(newBrowserSettings.getQuery())
                .sort().put(newBrowserSettings.getSort())
                .filterGameSeries().put(newBrowserSettings.getGameSeriesFilter())
                .filterCharacter().put(newBrowserSettings.getCharacterFilter())
                .filterAmiiboSeries().put(newBrowserSettings.getAmiiboSeriesFilter())
                .filterAmiiboType().put(newBrowserSettings.getAmiiboTypeFilter())
                .browserAmiiboView().put(newBrowserSettings.getAmiiboView())
                .image_network_settings().put(newBrowserSettings.getImageNetworkSettings())
                .recursiveFolders().put(newBrowserSettings.isRecursiveEnabled())
                .showDownloads().put(newBrowserSettings.isShowingDownloads())
                .apply();

        File rootFolder = newBrowserSettings.getBrowserRootFolder();
        String relativeRoot = Storage.getRelativePath(rootFolder, prefs.preferEmulated().get());
        setFolderText(relativeRoot.length() > 1 ? relativeRoot : rootFolder.getAbsolutePath(),
                folderChanged ? 3000 : 1500);
    }

    private void onAmiiboFilesChanged() {
        if (fakeSnackbar.getVisibility() == View.VISIBLE) {
            AutoTransition autoTransition = new AutoTransition();
            autoTransition.setDuration(250);

            TranslateAnimation animate = new TranslateAnimation(
                    0, 0, 0, -fakeSnackbar.getHeight());
            animate.setDuration(150);
            animate.setFillAfter(false);
            fakeSnackbar.setAnimationListener(new AnimatedLinearLayout.AnimationListener() {
                @Override
                public void onAnimationStart(AnimatedLinearLayout layout) { }

                @Override
                public void onAnimationEnd(AnimatedLinearLayout layout) {
                    layout.setAnimationListener(null);
                    fakeSnackbar.setVisibility(View.GONE);
                    if (null != browserFragment.getAmiibosView())
                        browserFragment.getAmiibosView().smoothScrollToPosition(0);
                }
            });
            fakeSnackbar.startAnimation(animate);

            TransitionManager.beginDelayedTransition(mainLayout, autoTransition);
            mainLayout.setPadding(0, 0, 0, 0);
        }
        if (settings.getAmiiboFiles().isEmpty()) { ;
            fakeSnackbar.setAnimationListener(null);
            fakeSnackbar.setVisibility(View.GONE);
            handler.postDelayed(() -> {
                fooSnackbar = new IconifiedSnackbar(this, mainLayout)
                        .buildSnackbar(R.string.amiibo_not_found, Snackbar.LENGTH_INDEFINITE);
                fooSnackbar.setAction(R.string.search, v -> {
                    mainLayout.setCurrentItem(1, true);
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }).show();
            }, 200);
        } else if (null != fooSnackbar && fooSnackbar.isShown()) {
            fooSnackbar.dismiss();
        }
    }

    private void onSortChanged() {
        if (null == menuSortId)
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

    private void onViewChanged() {
        if (null == menuViewSimple)
            return;
        switch (VIEW.valueOf(settings.getAmiiboView())) {
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
        if (null != this.settings && fakeSnackbar.getVisibility() != View.VISIBLE) {
            File rootFolder = this.settings.getBrowserRootFolder();
            if (!keyManager.isKeyMissing()) {
                if (indicator) showFakeSnackbar(getString(R.string.refreshing_list));
                this.loadAmiiboFiles(rootFolder, this.settings.isRecursiveEnabled());
            }
            this.loadFolders(rootFolder);
        }
    }

    private void onFilterGameSeriesChanged() {
        browserFragment.addFilterItemView(settings.getGameSeriesFilter(),
                "filter_game_series", onFilterGameSeriesChipCloseClick);
    }

    private final OnCloseClickListener onFilterGameSeriesChipCloseClick =
            new OnCloseClickListener() {
                @Override
                public void onCloseClick(@NonNull View v) {
                    settings.setGameSeriesFilter("");
                    settings.notifyChanges();
                    setAmiiboStats();
                }
            };

    private void onFilterCharacterChanged() {
        browserFragment.addFilterItemView(settings.getCharacterFilter(), "filter_character",
                onFilterCharacterChipCloseClick);
    }

    private final OnCloseClickListener onFilterCharacterChipCloseClick =
            new OnCloseClickListener() {
                @Override
                public void onCloseClick(@NonNull View v) {
                    settings.setCharacterFilter("");
                    settings.notifyChanges();
                    setAmiiboStats();
                }
            };

    private void onFilterAmiiboSeriesChanged() {
        browserFragment.addFilterItemView(settings.getAmiiboSeriesFilter(), "filter_amiibo_series",
                onFilterAmiiboSeriesChipCloseClick);
    }

    private final OnCloseClickListener onFilterAmiiboSeriesChipCloseClick =
            new OnCloseClickListener() {
                @Override
                public void onCloseClick(@NonNull View v) {
                    settings.setAmiiboSeriesFilter("");
                    settings.notifyChanges();
                    setAmiiboStats();
                }
            };

    private void onFilterAmiiboTypeChanged() {
        browserFragment.addFilterItemView(settings.getAmiiboTypeFilter(),
                "filter_amiibo_type", onAmiiboTypeChipCloseClick);
    }

    private final OnCloseClickListener onAmiiboTypeChipCloseClick =
            new OnCloseClickListener() {
                @Override
                public void onCloseClick(@NonNull View v) {
                    settings.setAmiiboTypeFilter("");
                    settings.notifyChanges();
                    setAmiiboStats();
                }
            };

    private void onRecursiveFilesChanged() {
        if (null == menuRecursiveFiles)
            return;

        menuRecursiveFiles.setChecked(settings.isRecursiveEnabled());
    }

    private void onShowDownloadsChanged() {
        if (null == menuShowDownloads)
            return;

        menuShowDownloads.setChecked(settings.isShowingDownloads());
    }

    private void launchEliteActivity(Intent resultData) {
        if (TagMo.getPrefs().enable_elite_support().get()
                && resultData.hasExtra(NFCIntent.EXTRA_SIGNATURE)) {
            Intent eliteIntent = new Intent(this, BankListActivity.class);
            eliteIntent.putExtras(resultData.getExtras());
            startActivity(eliteIntent);
            finish(); // Relaunch activity to bring view to front
        }
    }

    private final ActivityResultLauncher<Intent> onUpdateTagResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || null == result.getData()) return;

        if (!NFCIntent.ACTION_NFC_SCANNED.equals(result.getData().getAction())
                && !NFCIntent.ACTION_UPDATE_TAG.equals(result.getData().getAction())
                && !NFCIntent.ACTION_EDIT_COMPLETE.equals(result.getData().getAction())) return;


        // If we're supporting, didn't arrive from, but scanned an N2...
        if (TagMo.getPrefs().enable_elite_support().get()
                && result.getData().hasExtra(NFCIntent.EXTRA_SIGNATURE)) {
            launchEliteActivity(result.getData());
        } else {
            updateAmiiboView(result.getData().getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA));
            toolbar.getMenu().findItem(R.id.mnu_write).setEnabled(false);
        }
    });

    private void deleteAmiiboFile(AmiiboFile amiiboFile) {
        if (null != amiiboFile && null != amiiboFile.getFilePath()) {
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.warn_delete_file, Storage.getRelativePath(
                            amiiboFile.getFilePath(), prefs.preferEmulated().get())))
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        amiiboContainer.setVisibility(View.GONE);
                        //noinspection ResultOfMethodCallIgnored
                        amiiboFile.getFilePath().delete();
                        this.onRootFolderChanged(true);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss()).show();
        } else {
            new Toasty(this).Short(R.string.delete_misisng);
        }
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
                textView.setEnabled(true);
            }
        }
    }

    private void updateAmiiboView(byte[] tagData, AmiiboFile amiiboFile) {
        amiiboContainer.setVisibility(View.VISIBLE);
        if (!toolbar.getMenu().hasVisibleItems())
            toolbar.inflateMenu(R.menu.amiibo_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            Bundle args = new Bundle();
            Intent scan = new Intent(this, NfcActivity.class);
            if (item.getItemId() == R.id.mnu_scan) {
                scan.setAction(NFCIntent.ACTION_SCAN_TAG);
                onUpdateTagResult.launch(scan);
                return true;
            } else if (item.getItemId() == R.id.mnu_write) {
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData);
                scan.setAction(NFCIntent.ACTION_WRITE_TAG_FULL);
                onUpdateTagResult.launch(scan.putExtras(args));
                return true;
            } else if (item.getItemId() == R.id.mnu_update) {
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData);
                scan.setAction(NFCIntent.ACTION_WRITE_TAG_DATA);
                scan.putExtra(NFCIntent.EXTRA_IGNORE_TAG_ID, ignoreTagTd);
                onUpdateTagResult.launch(scan.putExtras(args));
                return true;
            } else if (item.getItemId() == R.id.mnu_save) {
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
                    } catch (IOException e) {
                        new Toasty(this).Short(e.getMessage());
                    }
                    backupDialog.dismiss();
                });
                view.findViewById(R.id.cancel_backup).setOnClickListener(v ->
                        backupDialog.dismiss());
                backupDialog.show();
                return true;
            } else if (item.getItemId() == R.id.mnu_edit) {
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData);
                Intent tagEdit = new Intent(this, TagDataActivity.class);
                onUpdateTagResult.launch(tagEdit.putExtras(args));
                return true;
            } else if (item.getItemId() == R.id.mnu_view_hex) {
                Intent hexView = new Intent(this, HexViewerActivity.class);
                hexView.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData);
                startActivity(hexView);
                return true;
            } else if (item.getItemId() == R.id.mnu_validate) {
                try {
                    TagUtils.validateData(tagData);
                    new IconifiedSnackbar(this, mainLayout).buildSnackbar(
                            R.string.validation_success, Snackbar.LENGTH_SHORT).show();
                } catch (Exception e) {
                    new IconifiedSnackbar(this, mainLayout).buildSnackbar(e.getMessage(),
                            R.drawable.ic_baseline_bug_report_24dp, Snackbar.LENGTH_LONG).show();
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_delete) {
                deleteAmiiboFile(amiiboFile);
                return true;
            } else if (item.getItemId() == R.id.mnu_ignore_tag_id) {
                ignoreTagTd = !item.isChecked();
                item.setChecked(ignoreTagTd);
                return true;
            }
            return false;
        });

        long amiiboId = -1;
        String tagInfo = null;
        String amiiboHexId = "";
        String amiiboName = "";
        String amiiboSeries = "";
        String amiiboType = "";
        String gameSeries = "";
        // String character = "";
        String amiiboImageUrl;

        boolean available = null != tagData  && tagData.length > 0;
        if (available) {
            try {
                amiiboId = TagUtils.amiiboIdFromTag(tagData);
            } catch (Exception e) {
                available = false;
                Debug.Log(e);
            }
        }
        toolbar.getMenu().findItem(R.id.mnu_write).setEnabled(available);
        toolbar.getMenu().findItem(R.id.mnu_update).setEnabled(available);
        toolbar.getMenu().findItem(R.id.mnu_save).setEnabled(available);
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
            if (null != settings.getAmiiboManager()) {
                amiibo = settings.getAmiiboManager().amiibos.get(amiiboId);
                if (null == amiibo)
                    amiibo = new Amiibo(settings.getAmiiboManager(),
                            amiiboId, null, null);
            }
            if (null != amiibo) {
                amiiboHexId = TagUtils.amiiboIdToHex(amiibo.id);
                amiiboImageUrl = amiibo.getImageUrl();
                if (null != amiibo.name )
                    amiiboName = amiibo.name;
                if (null != amiibo.getAmiiboSeries() )
                    amiiboSeries = amiibo.getAmiiboSeries().name;
                if (null != amiibo.getAmiiboType() )
                    amiiboType = amiibo.getAmiiboType().name;
                if (null != amiibo.getGameSeries() )
                    gameSeries = amiibo.getGameSeries().name;
                // if (null != amiibo.getCharacter() )
                //     character = amiibo.getCharacter().name;
            } else {
                amiiboHexId = TagUtils.amiiboIdToHex(amiiboId);
                tagInfo = "ID: " + amiiboHexId;
                amiiboImageUrl = Amiibo.getImageUrl(amiiboId);
            }
        }

        boolean hasTagInfo = null != tagInfo ;
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

        try {
            GamesManager gamesManager = GamesManager.getGamesManager(this);

            TextView txtUsage = findViewById(R.id.txtUsage);

            StringBuilder usage = new StringBuilder();
            usage.append("\n3DS:");
            for (String game : gamesManager.get3DSGames(amiiboId)) {
                if (usage.toString().endsWith(":"))
                    usage.append("  ");
                else
                    usage.append(", ");
                usage.append(game);
            }
            usage.append("\n\nWiiU:");
            for (String game : gamesManager.getWiiUGames(amiiboId)) {
                if (usage.toString().endsWith(":"))
                    usage.append("  ");
                else
                    usage.append(", ");
                usage.append(game);
            }
            usage.append("\n\nSwitch:");
            for (String game : gamesManager.getSwitchGames(amiiboId)) {
                if (usage.toString().endsWith(":"))
                    usage.append("  ");
                else
                    usage.append(", ");
                usage.append(game);
            }
            txtUsage.setText(usage);
            txtUsage.setVisibility(View.GONE);
            TextView label = findViewById(R.id.txtUsageLabel);
            label.setOnClickListener(view -> {
                if (txtUsage.getVisibility() == View.VISIBLE) {
                    txtUsage.setVisibility(View.GONE);
                    label.setText(R.string.game_usage_view);
                } else {
                    txtUsage.setVisibility(View.VISIBLE);
                    label.setText(R.string.game_usage_hide);
                }
            });
        } catch (Exception ex) {
            Debug.Log(ex);
        }

        if (null != imageAmiibo) {
            imageAmiibo.setVisibility(View.GONE);
            GlideApp.with(this).clear(amiiboImageTarget);
            if (null != amiiboImageUrl) {
                GlideApp.with(this).asBitmap().load(amiiboImageUrl).into(amiiboImageTarget);
                final long amiiboTagId = amiiboId;
                imageAmiibo.setOnClickListener(view -> {
                    Bundle bundle = new Bundle();
                    bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiiboTagId);

                    Intent intent = new Intent(this, ImageActivity.class);
                    intent.putExtras(bundle);

                    startActivity(intent);
                });
            }
        }
        if (amiiboHexId.endsWith("00000002") && !amiiboHexId.startsWith("00000000")) {
            txtTagId.setEnabled(false);
            toolbar.getMenu().findItem(R.id.mnu_write).setEnabled(false);
            toolbar.getMenu().findItem(R.id.mnu_update).setEnabled(false);
            toolbar.getMenu().findItem(R.id.mnu_save).setEnabled(false);
            toolbar.getMenu().findItem(R.id.mnu_edit).setEnabled(false);
            toolbar.getMenu().findItem(R.id.mnu_validate).setEnabled(false);
        }
    }

    private void updateAmiiboView(byte[] tagData) {
        updateAmiiboView(tagData, clickedAmiibo);
    }

    int getColumnCount() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
        else
            mWindowManager.getDefaultDisplay().getMetrics(metrics);
        return (int) ((metrics.widthPixels / metrics.density) / 112 + 0.5);
    }

    private int[] getAdapterStats() {
        BrowserAmiibosAdapter adapter = (BrowserAmiibosAdapter)
                browserFragment.getAmiibosView().getAdapter();
        if (adapter == null) return new int[]{0, 0};
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
        return new int[]{size, count};
    }

    private void setAmiiboStatsText() {
        int size = settings.getAmiiboFiles().size();
        if (size <= 0) return;
        currentFolderView.setGravity(Gravity.CENTER);
        if (null != settings.getAmiiboManager()) {
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
        this.currentFolderView.setGravity(Gravity.CENTER_VERTICAL);
        this.currentFolderView.setText(text);
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::setAmiiboStatsText, delay);
    }

    private void setAmiiboStats() {
        handler.removeCallbacksAndMessages(null);
        this.runOnUiThread(this::setAmiiboStatsText);
    }

    private void showFakeSnackbar(String msg) {
        this.runOnUiThread(() -> {
            mainLayout.setPadding(0, fakeSnackbarIcon.getHeight(), 0, 0);
            fakeSnackbarText.setText(msg);
            fakeSnackbar.setVisibility(View.VISIBLE);
        });
    }

    void showBrowserPage() {
        mainLayout.setCurrentItem(0, true);
    }

    public void closePrefsDrawer() {
        if (prefsDrawer.isDrawerOpen(GravityCompat.START))
            prefsDrawer.closeDrawer(GravityCompat.START);
    }

    BrowserSettings getSettings() {
        return this.settings;
    }

    ArrayList<Long> getMissingIds() {
        ArrayList<Long> missingIds = new ArrayList<>();
        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (null != amiiboManager) {
            HashSet<Long> amiiboIds = new HashSet<>();
            for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
                amiiboIds.add(amiiboFile.getId());
            }
            for (Amiibo amiibo : amiiboManager.amiibos.values()) {
                if (!amiiboIds.contains(amiibo.id)) {
                    missingIds.add(amiibo.id);
                }
            }
        }
        return missingIds;
    }

    private boolean keyNameMatcher(String name) {
        boolean isValid = name.toLowerCase(Locale.ROOT).endsWith(".bin");
        return name.toLowerCase(Locale.ROOT).endsWith("retail.bin") ||
                (isValid && (name.toLowerCase(Locale.ROOT).startsWith("locked")
                        || name.toLowerCase(Locale.ROOT).startsWith("unfixed")));
    }

    private void locateKeyFilesRecursive(File rootFolder) {
        File[] files = rootFolder.listFiles();
        if (files == null || files.length == 0)
            return;
        for (File file : files) {
            if (file.isDirectory() && file != Storage.getDownloadDir(null)) {
                locateKeyFilesRecursive(file);
            } else if (keyNameMatcher(file.getName())) {
                try (FileInputStream inputStream = new FileInputStream(file)) {
                    this.keyManager.evaluateKey(inputStream);
                } catch (Exception e) {
                    Debug.Log(e);
                }
            }
        }
    }

    public void locateKeyFiles() {
        Executors.newSingleThreadExecutor().execute(() -> {
            File[] files = Storage.getDownloadDir(null)
                    .listFiles((dir, name) -> keyNameMatcher(name));
            if (null != files && files.length > 0) {
                for (File file : files) {
                    try (FileInputStream inputStream = new FileInputStream(file)) {
                        this.keyManager.evaluateKey(inputStream);
                    } catch (Exception e) {
                        Debug.Log(e);
                    }
                }
            } else {
                locateKeyFilesRecursive(Storage.getFile(prefs.preferEmulated().get()));
            }

            if (keyManager.isKeyMissing()) {
                this.runOnUiThread(() -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        try {
                            Scanner scanner = new Scanner(new URL(
                                    "https://pastebin.com/raw/aV23ha3X").openStream());
                            for (int i = 0; i < 4; i++) {
                                if (scanner.hasNextLine()) scanner.nextLine();
                            }
                            this.keyManager.evaluateKey(new ByteArrayInputStream(
                                    TagUtils.hexToByteArray(scanner.nextLine()
                                            .replace(" ", ""))));
                            scanner.close();
                        } catch (IOException e) {
                            Debug.Log(e);
                        }
                        if (Thread.currentThread().isInterrupted()) return;
                        onRefresh();
                    });
                    mainLayout.setPadding(0, 0, 0, 0);
                    fakeSnackbar.setVisibility(View.GONE);
                });
            } else {
                if (null != fooSnackbar && fooSnackbar.isShown())
                    this.runOnUiThread(() -> fooSnackbar.dismiss());
                this.onRefresh();
            }
        });
    }

    private static final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private void showStoragePrompt() {
        Snackbar storageBar = Snackbar.make(findViewById(R.id.coordinator),
                R.string.permission_required, Snackbar.LENGTH_LONG);
        storageBar.setAction(R.string.allow, v -> onRequestStorage.launch(PERMISSIONS_STORAGE));
        storageBar.show();
    }

    ActivityResultLauncher<String[]> onRequestStorage = registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
    permissions -> { boolean isStorageEnabled = true;
        for (Map.Entry<String,Boolean> entry : permissions.entrySet()) {
            if (!entry.getValue()) isStorageEnabled = false;
        }
        if (isStorageEnabled)
            this.onStorageEnabled();
        else
            showStoragePrompt();
    });

    @RequiresApi(api = Build.VERSION_CODES.R)
    ActivityResultLauncher<Intent> onRequestScopedStorage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (Environment.isExternalStorageManager()) {
            this.onStorageEnabled();
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
                    intent.putExtra("android.content.extra.FANCY", true);
                    onDocumentTree.launch(NFCIntent.getIntent(intent));
                }
            }  catch (ActivityNotFoundException anfex) {
                new Toasty(this).Long(R.string.storage_unavailable);
                finish();
            }
        }
    });

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

    @Override
    public void onBackPressed() {
        if (prefsDrawer.isDrawerOpen(GravityCompat.START))
            prefsDrawer.closeDrawer(GravityCompat.START);
        else if (BottomSheetBehavior.STATE_EXPANDED == bottomSheetBehavior.getState())
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        else if (mainLayout.getCurrentItem() != 0)
            mainLayout.setCurrentItem(0, true);
        else if (View.VISIBLE == amiiboContainer.getVisibility())
            amiiboContainer.setVisibility(View.GONE);
        else
            super.onBackPressed();
    }

    private boolean hasTestedElite;
    private boolean isEliteDevice;

    private final ActivityResultLauncher<Intent> onTagLaunchActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (settings.getAmiiboFiles().isEmpty()) this.onRefresh();
    });

    @RequiresApi(api = Build.VERSION_CODES.O)
    final ActivityResultLauncher<Intent> onRequestInstall = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (getPackageManager().canRequestPackageInstalls())
            updates.installUpdateTask(prefs.downloadUrl().get());
        prefs.downloadUrl().remove();
    });

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())
                || NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            if (keyManager.isKeyMissing()) return;
            NTAG215 mifare = null;
            try {
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                mifare = NTAG215.get(tag);
                String tagTech = TagUtils.getTagTechnology(tag);
                if (mifare == null) {
                    if (prefs.enable_elite_support().get()) {
                        mifare = new NTAG215(NfcA.get(tag));
                        try {
                            mifare.connect();
                        } catch (Exception ex) {
                            Debug.Log(ex);
                        }
                        if (TagReader.needsFirmware(mifare)) {
                            if (TagWriter.updateFirmware(mifare))
                                new Toasty(this).Short(R.string.firmware_update);
                            mifare.close();
                            finish();
                        }
                    }
                    throw new Exception(getString(R.string.error_tag_protocol, tagTech));
                }
                mifare.connect();
                if (!hasTestedElite) {
                    hasTestedElite = true;
                    if (!TagUtils.isPowerTag(mifare)) {
                        isEliteDevice = TagUtils.isElite(mifare);
                    }
                }
                byte[] bank_details;
                int bank_count;
                int active_bank;
                if (!isEliteDevice) {
                    bank_count = -1;
                    active_bank = -1;
                } else {
                    bank_details = TagReader.getBankDetails(mifare);
                    bank_count = bank_details[1] & 0xFF;
                    active_bank = bank_details[0] & 0xFF;
                }
                try {
                    if (isEliteDevice) {
                        String signature = TagReader.getTagSignature(mifare);
                        prefs.settings_elite_signature().put(signature);
                        prefs.eliteActiveBank().put(active_bank);
                        prefs.eliteBankCount().put(bank_count);

                        Intent eliteIntent = new Intent(this, BankListActivity.class);
                        Bundle args = new Bundle();
                        ArrayList<String> titles = TagReader.readTagTitles(mifare, bank_count);
                        eliteIntent.putExtra(NFCIntent.EXTRA_SIGNATURE, signature);
                        eliteIntent.putExtra(NFCIntent.EXTRA_BANK_COUNT, bank_count);
                        eliteIntent.putExtra(NFCIntent.EXTRA_ACTIVE_BANK, active_bank);
                        args.putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST, titles);
                        onTagLaunchActivity.launch(eliteIntent.putExtras(args));
                    } else {
                        updateAmiiboView(TagReader.readFromTag(mifare));
                    }
                    hasTestedElite = false;
                    isEliteDevice = false;
                } finally {
                    mifare.close();
                }
            } catch (Exception e) {
                Debug.Log(e);
                String error = e.getMessage();
                error = null != e.getCause() ? error + "\n" + e.getCause().toString() : error;
                if (null != error && prefs.enable_elite_support().get()) {
                    if (e instanceof android.nfc.TagLostException) {
                        new Toasty(this).Short(R.string.speed_scan);
                        try {
                            if (null != mifare) mifare.close();
                        } catch (IOException ex) {
                            Debug.Log(ex);
                        }
                    }
                } else {
                    new Toasty(this).Short(error);
                }
            }
        }
    }
}
