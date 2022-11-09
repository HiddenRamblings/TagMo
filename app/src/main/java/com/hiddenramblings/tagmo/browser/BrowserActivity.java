package com.hiddenramblings.tagmo.browser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.TranslateAnimation;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.hiddenramblings.tagmo.BuildConfig;
import com.hiddenramblings.tagmo.GlideApp;
import com.hiddenramblings.tagmo.ImageActivity;
import com.hiddenramblings.tagmo.NFCIntent;
import com.hiddenramblings.tagmo.NfcActivity;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.AmiiboSeries;
import com.hiddenramblings.tagmo.amiibo.AmiiboType;
import com.hiddenramblings.tagmo.amiibo.Character;
import com.hiddenramblings.tagmo.amiibo.GameSeries;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.amiibo.PowerTagManager;
import com.hiddenramblings.tagmo.amiibo.games.GameTitles;
import com.hiddenramblings.tagmo.amiibo.games.GamesManager;
import com.hiddenramblings.tagmo.amiibo.tagdata.TagDataEditor;
import com.hiddenramblings.tagmo.browser.BrowserSettings.BrowserSettingsListener;
import com.hiddenramblings.tagmo.browser.BrowserSettings.FILTER;
import com.hiddenramblings.tagmo.browser.BrowserSettings.SORT;
import com.hiddenramblings.tagmo.browser.BrowserSettings.VIEW;
import com.hiddenramblings.tagmo.browser.adapter.BrowserAdapter;
import com.hiddenramblings.tagmo.browser.adapter.FoldersAdapter;
import com.hiddenramblings.tagmo.browser.adapter.FoomiiboAdapter;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.eightbit.view.AnimatedLinearLayout;
import com.hiddenramblings.tagmo.hexcode.HexCodeViewer;
import com.hiddenramblings.tagmo.nfctech.ScanTag;
import com.hiddenramblings.tagmo.nfctech.TagArray;
import com.hiddenramblings.tagmo.nfctech.TagReader;
import com.hiddenramblings.tagmo.widget.Toasty;
import com.wajahatkarim3.easyflipviewpager.CardFlipPageTransformer2;

import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;
import eightbitlab.com.blurview.SupportRenderScriptBlur;
import myinnos.indexfastscrollrecycler.IndexFastScrollRecyclerView;

public class BrowserActivity extends AppCompatActivity implements
        BrowserSettingsListener,
        BrowserAdapter.OnAmiiboClickListener {

    private Preferences prefs;
    private KeyManager keyManager;
    private int filteredCount;
    private AmiiboFile clickedAmiibo = null;

    private BrowserSettings settings;
    private boolean ignoreTagId;
    private CheckUpdatesTask updates;
    private String updateUrl;
    private AppUpdateInfo appUpdate;

    private SettingsFragment fragmentSettings;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private BottomSheetBehavior<View> bottomSheet;
    private TextView currentFolderView;
    private DrawerLayout prefsDrawer;
    private AppCompatButton switchStorageRoot;
    private AppCompatButton switchStorageType;
    private Dialog joyConDialog;

    private AnimatedLinearLayout fakeSnackbar;
    private TextView fakeSnackbarText;
    private AppCompatButton fakeSnackbarItem;
    private ViewPager2 mainLayout;
    private FloatingActionButton nfcFab;
    private RecyclerView amiibosView;
    private RecyclerView foomiiboView;
    private BrowserFragment fragmentBrowser;
    private EliteBankFragment fragmentElite;

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
    private MenuItem menuFilterGameTitles;
    private MenuItem menuViewSimple;
    private MenuItem menuViewCompact;
    private MenuItem menuViewLarge;
    private MenuItem menuViewImage;
    private MenuItem menuRecursiveFiles;

    private FrameLayout amiiboContainer;
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

    NavPagerAdapter pagerAdapter = new NavPagerAdapter(this);

    private final ScanTag tagScanner = new ScanTag();
    private final DonationHandler donations = new DonationHandler(this);

    private final Handler statsHandler = new Handler(Looper.getMainLooper());
    private final Handler sheetHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Preferences(getApplicationContext());
        keyManager = new KeyManager(this);

        if (null != getSupportActionBar()) {
            if (BuildConfig.WEAR_OS) {
                getSupportActionBar().hide();
            } else {
                getSupportActionBar().setDisplayShowHomeEnabled(true);
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_baseline_menu_24);
            }
        }

        setContentView(R.layout.activity_browser);

        fakeSnackbar = findViewById(R.id.fake_snackbar);
        fakeSnackbarText = findViewById(R.id.snackbar_text);
        fakeSnackbarItem = findViewById(R.id.snackbar_item);
        mainLayout = findViewById(R.id.amiibo_pager);
        nfcFab = findViewById(R.id.nfc_fab);
        currentFolderView = findViewById(R.id.current_folder);
        if (!BuildConfig.WEAR_OS) {
            switchStorageRoot = findViewById(R.id.switch_storage_root);
            switchStorageType = findViewById(R.id.switch_storage_type);
        }
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

        if (Debug.isOlder(Build.VERSION_CODES.M)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }

        if (null == this.settings) this.settings = new BrowserSettings().initialize();
        this.settings.addChangeListener(this);

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

        mainLayout.setKeepScreenOn(BuildConfig.WEAR_OS);
        mainLayout.setAdapter(pagerAdapter);
        CardFlipPageTransformer2 cardFlipPageTransformer = new CardFlipPageTransformer2();
        cardFlipPageTransformer.setScalable(true);
        mainLayout.setPageTransformer(cardFlipPageTransformer);
        setViewPagerSensitivity(mainLayout, 4);
        fragmentBrowser = pagerAdapter.getBrowser();
        if (BuildConfig.WEAR_OS) fragmentSettings = pagerAdapter.getSettings();
        fragmentElite = pagerAdapter.getEliteBanks();

        amiibosView = fragmentBrowser.getBrowserContent();
        foomiiboView = fragmentBrowser.getFoomiiboView();
        bottomSheet = bottomSheetBehavior;
        mainLayout.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @SuppressLint("NewApi")
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (position != 0) {
                    BrowserAdapter.resetVisible();
                    FoomiiboAdapter.resetVisible();
                }
                boolean hasFlaskEnabled = prefs.flask_support();
                if (BuildConfig.WEAR_OS) {
                    switch (position) {
                        case 1:
                            if (hasFlaskEnabled) {
                                showActionButton();
                                hideBottomSheet();
                                FlaskSlotFragment fragmentFlask = pagerAdapter.getFlaskSlots();
                                fragmentFlask.delayedBluetoothEnable();
                                amiibosView = fragmentFlask.getFlaskContent();
                                bottomSheet = fragmentFlask.getBottomSheet();
                            } else {
                                hideBrowserInterface();
                            }
                            break;
                        case 2:
                        case 3:
                            hideBrowserInterface();
                            break;
                        default:
                            showBrowserInterface();
                            amiibosView = fragmentBrowser.getBrowserContent();
                            foomiiboView = fragmentBrowser.getFoomiiboView();
                            bottomSheet = bottomSheetBehavior;
                            if (null == foomiiboView) break;
                            foomiiboView.setLayoutManager(settings.getAmiiboView()
                                    == BrowserSettings.VIEW.IMAGE.getValue()
                                    ? new GridLayoutManager(BrowserActivity.this, getColumnCount())
                                    : new LinearLayoutManager(BrowserActivity.this));
                            break;
                    }
                } else {
                    boolean hasEliteEnabled = prefs.elite_support();
                    switch (position) {
                        case 1:
                            if (hasEliteEnabled) {
                                showActionButton();
                                hideBottomSheet();
                                setTitle(R.string.elite_n2);
                                amiibosView = fragmentElite.getEliteContent();
                                bottomSheet = fragmentElite.getBottomSheet();
                            } else if (hasFlaskEnabled) {
                                showActionButton();
                                hideBottomSheet();
                                setTitle(R.string.flask_title);
                                FlaskSlotFragment fragmentFlask = pagerAdapter.getFlaskSlots();
                                fragmentFlask.delayedBluetoothEnable();
                                amiibosView = fragmentFlask.getFlaskContent();
                                bottomSheet = fragmentFlask.getBottomSheet();
                            } else {
                                hideBrowserInterface();
                                setTitle(R.string.guides);
                            }
                            break;
                        case 2:
                            if (hasEliteEnabled && hasFlaskEnabled) {
                                showActionButton();
                                hideBottomSheet();
                                setTitle(R.string.flask_title);
                                FlaskSlotFragment fragmentFlask = pagerAdapter.getFlaskSlots();
                                fragmentFlask.delayedBluetoothEnable();
                                amiibosView = fragmentFlask.getFlaskContent();
                                bottomSheet = fragmentFlask.getBottomSheet();
                            } else {
                                hideBrowserInterface();
                                setTitle(R.string.guides);
                            }
                            break;
                        case 3:
                            hideBrowserInterface();
                            setTitle(R.string.guides);
                            break;
                        default:
                            showBrowserInterface();
                            setTitle(R.string.tagmo);
                            amiibosView = fragmentBrowser.getBrowserContent();
                            foomiiboView = fragmentBrowser.getFoomiiboView();
                            bottomSheet = bottomSheetBehavior;
                            if (null == foomiiboView) break;
                            foomiiboView.setLayoutManager(settings.getAmiiboView()
                                    == BrowserSettings.VIEW.IMAGE.getValue()
                                    ? new GridLayoutManager(BrowserActivity.this, getColumnCount())
                                    : new LinearLayoutManager(BrowserActivity.this));
                            break;
                    }
                }
                if (null != amiibosView) {
                    amiibosView.setLayoutManager(settings.getAmiiboView()
                            == BrowserSettings.VIEW.IMAGE.getValue()
                            ? new GridLayoutManager(BrowserActivity.this, getColumnCount())
                            : new LinearLayoutManager(BrowserActivity.this));
                }
                if (BuildConfig.WEAR_OS)
                    onCreateWearOptionsMenu();
                else
                    invalidateOptionsMenu();
            }
        });

        new TabLayoutMediator(findViewById(R.id.navigation_tabs), mainLayout, true,
                Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2), (tab, position) -> {
            boolean hasFlaskEnabled = prefs.flask_support();
            if (BuildConfig.WEAR_OS) {
                switch (position) {
                    case 1:
                        if (hasFlaskEnabled) {
                            tab.setText(R.string.flask_title);
                        } else {
                            tab.setText(R.string.settings);
                        }
                        break;
                    case 2:
                        tab.setText(R.string.settings);
                        break;
                    case 3:
                        tab.setText(R.string.guides);
                        break;
                    default:
                        tab.setText(R.string.browser);
                        break;
                }
            } else {
                boolean hasEliteEnabled = prefs.elite_support();
                switch (position) {
                    case 1:
                        if (hasEliteEnabled) {
                            tab.setText(R.string.elite_n2);
                        } else if (hasFlaskEnabled) {
                            tab.setText(R.string.flask_title);
                        } else {
                            tab.setText(R.string.guides);
                        }
                        break;
                    case 2:
                        if (hasEliteEnabled && hasFlaskEnabled) {
                            tab.setText(R.string.flask_title);
                        } else {
                            tab.setText(R.string.guides);
                        }
                        break;
                    case 3:
                        tab.setText(R.string.guides);
                        break;
                    default:
                        tab.setText(R.string.browser);
                        break;
                }
            }
        }).attach();

        if (!BuildConfig.WEAR_OS) onLoadSettingsFragment();

        CoordinatorLayout coordinator = findViewById(R.id.coordinator);
        if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR1)) {
            //noinspection deprecation
            ((BlurView) amiiboContainer).setupWith(coordinator,
                    Debug.isNewer(Build.VERSION_CODES.S)
                            ? new RenderEffectBlur()
                            : Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR1)
                            ? new RenderScriptBlur(this)
                            : new SupportRenderScriptBlur(this))
                    .setFrameClearDrawable(getWindow().getDecorView().getBackground())
                    .setBlurRadius(2f).setBlurAutoUpdate(true);
        }

        AppCompatImageView toggle = findViewById(R.id.toggle);
        this.bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet));
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        this.bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    toggle.setImageResource(R.drawable.ic_expand_less_white_24dp);
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    setFolderText(settings);
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

        LinearLayout foomiiboOptions = findViewById(R.id.foomiibo_options);
        final Handler foomiiboHandler = new Handler(Looper.getMainLooper());
        foomiiboOptions.findViewById(R.id.clear_foomiibo_set).setOnClickListener(clickView -> {
            collapseBottomSheet();
            foomiiboHandler.postDelayed(() ->
                    fragmentBrowser.clearFoomiiboSet(this.statsHandler), TagMo.uiDelay);
        });

        foomiiboOptions.findViewById(R.id.build_foomiibo_set).setOnClickListener(clickView -> {
            collapseBottomSheet();
            foomiiboHandler.postDelayed(() ->
                    fragmentBrowser.buildFoomiiboSet(this.statsHandler), TagMo.uiDelay);
        });

        if (BuildConfig.WEAR_OS) {
            onRequestStorage.launch(PERMISSIONS_STORAGE);
        } else {
            requestStoragePermission();
            try {
                getPackageManager().getPackageInfo(
                        "com.hiddenramblings.tagmo", PackageManager.GET_META_DATA
                );
                new AlertDialog.Builder(this)
                        .setTitle(R.string.conversion_title)
                        .setMessage(R.string.conversion_message)
                        .setPositiveButton(R.string.proceed, (dialogInterface, i) -> startActivity(
                                new Intent(Intent.ACTION_DELETE).setData(
                                        Uri.parse("package:com.hiddenramblings.tagmo")
                                )
                        )).show();

            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        RecyclerView foldersView = findViewById(R.id.folders_list);
        foldersView.setLayoutManager(new LinearLayoutManager(this));
        foldersView.setAdapter(new FoldersAdapter(settings));
        this.settings.addChangeListener((BrowserSettingsListener) foldersView.getAdapter());

        this.loadPTagKeyManager();

        PopupMenu popup = Debug.isNewer(Build.VERSION_CODES.LOLLIPOP_MR1)
                    ? new PopupMenu(this, nfcFab, Gravity.END, 0, R.style.PopupMenu)
                    : new PopupMenu(this, nfcFab);
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
            Debug.Warn(e);
        }
        popup.getMenuInflater().inflate(R.menu.action_menu, popup.getMenu());
        nfcFab.setOnClickListener(view -> showPopupMenu(popup));

        findViewById(R.id.amiiboContainer).setOnClickListener(view ->
                amiiboContainer.setVisibility(View.GONE));

        if (null != intent && null != intent.getAction()
                && Intent.ACTION_VIEW.equals(intent.getAction())) {
            try {
                if (null != intent.getClipData()) {
                    for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
                        Uri uri = intent.getClipData().getItemAt(i).getUri();
                        byte[] data = TagReader.readTagDocument(uri);
                        updateAmiiboView(data, new AmiiboFile(new File(uri.getPath()),
                                Amiibo.dataToId(data), data));
                    }
                } else if (null != intent.getData()) {
                    Uri uri = intent.getData();
                    byte[] data = TagReader.readTagDocument(uri);
                    updateAmiiboView(data, new AmiiboFile(new File(uri.getPath()),
                            Amiibo.dataToId(data), data));
                }
            } catch (Exception ignored) {}
        }

        if (BuildConfig.WEAR_OS) {
            onCreateWearOptionsMenu();
        } else {
            TextView buildText = findViewById(R.id.build_text);
            buildText.setMovementMethod(LinkMovementMethod.getInstance());
            buildText.setText(TagMo.getVersionLabel(false));

            prefsDrawer = findViewById(R.id.drawer_layout);
            prefsDrawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
                @Override
                public void onDrawerOpened(View drawerView) {
                    super.onDrawerOpened(drawerView);
                    findViewById(R.id.build_layout).setOnClickListener(view -> {
                        closePrefsDrawer();
                        String repository = "https://github.com/HiddenRamblings/TagMo";
                        showWebsite(repository);
                    });
                    if (null != appUpdate) {
                        findViewById(R.id.build_layout).setOnClickListener(view -> {
                            closePrefsDrawer();
                            updates.downloadPlayUpdate(appUpdate);
                        });
                    }
                    if (null != updateUrl) {
                        findViewById(R.id.build_layout).setOnClickListener(view -> {
                            closePrefsDrawer();
                            updates.installUpdateCompat(updateUrl);
                        });
                    }
                }
            });
        }

        donations.retrieveDonationMenu();
        findViewById(R.id.donate_layout).setOnClickListener(view -> {
            if (!BuildConfig.WEAR_OS) closePrefsDrawer();
            donations.onSendDonationClicked();
        });

        if (!prefs.guides_prompted()) {
            prefs.guides_prompted(true);
            mainLayout.setCurrentItem(pagerAdapter.getItemCount() - 1, false);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void onTabCollectionChanged() {
        mainLayout.setCurrentItem(0, false);
        pagerAdapter.notifyDataSetChanged();
    }

    private void onLoadSettingsFragment() {
        if (null == fragmentSettings) fragmentSettings = new SettingsFragment();
        if (!fragmentSettings.isAdded()) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.preferences, fragmentSettings)
                    .commit();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void onShowJoyConFragment() {
        if (null != joyConDialog && joyConDialog.isShowing()) return;
        JoyConFragment fragmentJoyCon = JoyConFragment.newInstance();
        fragmentJoyCon.show(getSupportFragmentManager(), "dialog");
        joyConDialog = fragmentJoyCon.getDialog();
    }

    private void requestStoragePermission() {
        if (Debug.isNewer(Build.VERSION_CODES.R)) {
            if (BuildConfig.GOOGLE_PLAY) {
                this.onDocumentEnabled();
            } else {
                if (null != settings.getBrowserRootDocument() && isDocumentStorage()) {
                    this.onDocumentEnabled();
                } else if (Environment.isExternalStorageManager()) {
                    settings.setBrowserRootDocument(null);
                    settings.notifyChanges();
                    this.onStorageEnabled();
                } else {
                    requestScopedStorage();
                }
            }
        } else if (Debug.isNewer(Build.VERSION_CODES.M)) {
            onRequestStorage.launch(PERMISSIONS_STORAGE);
        } else {
            this.onStorageEnabled();
        }
    }

    public final ActivityResultLauncher<Intent> onNFCActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || null == result.getData()) return;

        if (!NFCIntent.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        if (result.getData().hasExtra(NFCIntent.EXTRA_SIGNATURE)) {
            String signature = result.getData().getStringExtra(NFCIntent.EXTRA_SIGNATURE);
            prefs.elite_signature(signature);
            int active_bank = result.getData().getIntExtra(
                    NFCIntent.EXTRA_ACTIVE_BANK, prefs.eliteActiveBank());
            prefs.eliteActiveBank(active_bank);
            int bank_count = result.getData().getIntExtra(
                    NFCIntent.EXTRA_BANK_COUNT, prefs.eliteBankCount());
            prefs.eliteBankCount(bank_count);
            showElitePage(result.getData().getExtras());
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

        View view = getLayoutInflater().inflate(
                R.layout.dialog_backup, mainLayout, false
        );
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        final EditText input = view.findViewById(R.id.backup_entry);
        input.setText(TagArray.decipherFilename(settings.getAmiiboManager(), tagData, true));
        Dialog backupDialog = dialog.setView(view).create();
        view.findViewById(R.id.save_backup).setOnClickListener(v -> {
            try {
                String fileName = input.getText().toString() + ".bin";
                if (isDocumentStorage()) {
                    DocumentFile rootDocument = DocumentFile.fromTreeUri(this,
                            this.settings.getBrowserRootDocument());
                    if (null == rootDocument) throw new NullPointerException();
                    fileName = TagArray.writeBytesToDocument(this, rootDocument,
                            fileName, tagData);
                } else {
                    fileName = TagArray.writeBytesToFile(Storage.getDownloadDir(
                            "TagMo", "Backups"
                    ), fileName, tagData);
                }
                new IconifiedSnackbar(this, mainLayout).buildSnackbar(
                        getString(R.string.wrote_file, fileName), Snackbar.LENGTH_SHORT
                ).show();
                this.onRootFolderChanged(true);
            } catch (IOException | NullPointerException e) {
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
            TagArray.validateData(result.getData().getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA));
            new IconifiedSnackbar(this, mainLayout).buildSnackbar(
                    R.string.validation_success, Snackbar.LENGTH_SHORT).show();
        } catch (Exception e) {
            new IconifiedSnackbar(this, mainLayout).buildSnackbar(e.getMessage(),
                    R.drawable.ic_baseline_bug_report_24dp, Snackbar.LENGTH_LONG).show();
        }
    });

    private void showPopupMenu(PopupMenu popup) {
        MenuItem scanItem = popup.getMenu().findItem(R.id.mnu_scan);
        MenuItem backupItem = popup.getMenu().findItem(R.id.mnu_backup);
        MenuItem validateItem = popup.getMenu().findItem(R.id.mnu_validate);
        MenuItem legoItem = popup.getMenu().findItem(R.id.mnu_lego);
        MenuItem joyConItem = popup.getMenu().findItem(R.id.mnu_joy_con);

        scanItem.setEnabled(false);
        backupItem.setEnabled(false);
        validateItem.setEnabled(false);
        legoItem.setEnabled(false);
        joyConItem.setEnabled(false);
        joyConItem.setVisible(Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2));

        popup.show();
        Handler popupHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                popup.getMenu().findItem(msg.what).setEnabled(true);
            }
        };
        popupHandler.postDelayed(() -> {
            int baseDelay = 0;
            if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2)) {
                baseDelay = 75;
                popupHandler.sendEmptyMessageDelayed(R.id.mnu_joy_con, baseDelay);
            }
            popupHandler.sendEmptyMessageDelayed(R.id.mnu_lego, 75 + baseDelay);
            popupHandler.sendEmptyMessageDelayed(R.id.mnu_validate, 175 + baseDelay);
            popupHandler.sendEmptyMessageDelayed(R.id.mnu_backup, 275 + baseDelay);
            popupHandler.sendEmptyMessageDelayed(R.id.mnu_scan, 375 + baseDelay);
        }, 275);

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.mnu_scan) {
                onNFCActivity.launch(new Intent(this,
                        NfcActivity.class).setAction(NFCIntent.ACTION_SCAN_TAG));
                return true;
            } else if (item.getItemId() == R.id.mnu_backup) {
                Intent backup = new Intent(this, NfcActivity.class);
                backup.setAction(NFCIntent.ACTION_BACKUP_AMIIBO);
                onBackupActivity.launch(backup);
                return true;
            } else if (item.getItemId() == R.id.mnu_validate) {
                onValidateActivity.launch(new Intent(
                        this, NfcActivity.class
                ).setAction(NFCIntent.ACTION_SCAN_TAG));
                return true;
            } else if (item.getItemId() == R.id.mnu_lego) {
                new Toasty(this).Short(R.string.notice_incomplete);
                return true;
            } else if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2)
                    && item.getItemId() == R.id.mnu_joy_con) {
                new Toasty(this).Short(R.string.notice_incomplete);
                onShowJoyConFragment();
                return true;
            }
            return false;
        });
    }

    private void onCaptureLogcatClicked() {
        if (updates.hasPendingUpdate()) {
            if (null != appUpdate) updates.downloadPlayUpdate(appUpdate);
            if (null != updateUrl) updates.installUpdateCompat(updateUrl);
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (!Debug.processLogcat(this )) {
                    runOnUiThread(() -> showWebsite(null));
                }
            } catch (IOException e) {
                runOnUiThread(() -> new IconifiedSnackbar(this, mainLayout).buildSnackbar(
                        e.getMessage(), Snackbar.LENGTH_SHORT
                ).show());
            }
        });
    }

    public void setFoomiiboPanelVisibility() {
        fragmentBrowser.setFoomiiboVisibility();
    }

    private int getQueryCount(String queryText) {
        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (null == amiiboManager) return 0;
        Set<Long> items = new HashSet<>();
        for (Amiibo amiibo : amiiboManager.amiibos.values()) {
            if (settings.amiiboContainsQuery(amiibo, queryText))
                items.add(amiibo.id);
        }
        return items.size();
    }

    private int getFilteredCount(String filter, FILTER filterType) {
        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null) return 0;
        GamesManager gamesManager = settings.getGamesManager();

        Set<Long> items = new HashSet<>();
        for (Amiibo amiibo : amiiboManager.amiibos.values()) {
            switch (filterType) {
                case CHARACTER:
                    Character character = amiibo.getCharacter();
                    if (null != character &&
                            Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(),
                                    settings.getFilter(FILTER.GAME_SERIES)) &&
                            Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(),
                                    settings.getFilter(FILTER.AMIIBO_SERIES)) &&
                            Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(),
                                    settings.getFilter(FILTER.AMIIBO_TYPE))
                    ) {
                        if (character.name.equals(filter))
                            items.add(amiibo.id);
                    }
                    break;
                case GAME_SERIES:
                    GameSeries gameSeries = amiibo.getGameSeries();
                    if (null != gameSeries &&
                            Amiibo.matchesCharacterFilter(amiibo.getCharacter(),
                                    settings.getFilter(FILTER.CHARACTER)) &&
                            Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(),
                                    settings.getFilter(FILTER.AMIIBO_SERIES)) &&
                            Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(),
                                    settings.getFilter(FILTER.AMIIBO_TYPE))
                    ) {
                        if (gameSeries.name.equals(filter))
                            items.add(amiibo.id);
                    }
                    break;
                case AMIIBO_SERIES:
                    AmiiboSeries amiiboSeries = amiibo.getAmiiboSeries();
                    if (null != amiiboSeries &&
                            Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(),
                                    settings.getFilter(FILTER.GAME_SERIES)) &&
                            Amiibo.matchesCharacterFilter(amiibo.getCharacter(),
                                    settings.getFilter(FILTER.CHARACTER)) &&
                            Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(),
                                    settings.getFilter(FILTER.AMIIBO_TYPE))
                    ) {
                        if (amiiboSeries.name.equals(filter))
                            items.add(amiibo.id);
                    }
                    break;
                case AMIIBO_TYPE:
                    AmiiboType amiiboType = amiibo.getAmiiboType();
                    if (null != amiiboType &&
                            Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(),
                                    settings.getFilter(FILTER.GAME_SERIES)) &&
                            Amiibo.matchesCharacterFilter(amiibo.getCharacter(),
                                    settings.getFilter(FILTER.CHARACTER)) &&
                            Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(),
                                    settings.getFilter(FILTER.AMIIBO_SERIES))
                    ) {
                        if (amiiboType.name.equals(filter))
                            items.add(amiibo.id);
                    }
                    break;
                case GAME_TITLES:
                    if (Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(),
                            settings.getFilter(FILTER.GAME_SERIES)) &&
                            Amiibo.matchesCharacterFilter(amiibo.getCharacter(),
                                    settings.getFilter(FILTER.CHARACTER)) &&
                            Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(),
                                    settings.getFilter(FILTER.AMIIBO_SERIES)) &&
                            Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(),
                                    settings.getFilter(FILTER.AMIIBO_TYPE))
                    ) {
                        if (null != gamesManager)
                            items.addAll(gamesManager.getGameAmiiboIds(amiiboManager, filter));
                    }
                    break;
            }
        }
        return items.size();
    }

    private boolean onFilterCharacterClick() {
        SubMenu subMenu = menuFilterCharacter.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null) return true;

        Set<String> items = new HashSet<>();
        for (Amiibo amiibo : settings.getAmiiboManager().amiibos.values()) {

            Character character = amiibo.getCharacter();
            if (null != character &&
                    Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(),
                            settings.getFilter(FILTER.GAME_SERIES)) &&
                    Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(),
                            settings.getFilter(FILTER.AMIIBO_SERIES)) &&
                    Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(),
                            settings.getFilter(FILTER.AMIIBO_TYPE))
            ) {
                items.add(character.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_character_group, Menu.NONE, 0, item)
                    .setChecked(item.equals(settings.getFilter(FILTER.CHARACTER)))
                    .setOnMenuItemClickListener(onFilterCharacterItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_character_group, true, true);

        return true;
    }

    private final MenuItem.OnMenuItemClickListener onFilterCharacterItemClick =
            new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    settings.setFilter(FILTER.CHARACTER, menuItem.getTitle().toString());
                    settings.notifyChanges();
                    filteredCount = getFilteredCount(menuItem.getTitle().toString(), FILTER.CHARACTER);
                    return false;
                }
            };

    private boolean onFilterGameSeriesClick() {
        SubMenu subMenu = menuFilterGameSeries.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null) return false;

        Set<String> items = new HashSet<>();
        for (Amiibo amiibo : settings.getAmiiboManager().amiibos.values()) {

            GameSeries gameSeries = amiibo.getGameSeries();
            if (null != gameSeries &&
                    Amiibo.matchesCharacterFilter(amiibo.getCharacter(),
                            settings.getFilter(FILTER.CHARACTER)) &&
                    Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(),
                            settings.getFilter(FILTER.AMIIBO_SERIES)) &&
                    Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(),
                            settings.getFilter(FILTER.AMIIBO_TYPE))
            ) {
                items.add(gameSeries.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_game_series_group, Menu.NONE, 0, item)
                    .setChecked(item.equals(settings.getFilter(FILTER.GAME_SERIES)))
                    .setOnMenuItemClickListener(onFilterGameSeriesItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_game_series_group, true, true);

        return true;
    }

    private final MenuItem.OnMenuItemClickListener onFilterGameSeriesItemClick
            = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setFilter(FILTER.GAME_SERIES, menuItem.getTitle().toString());
            settings.notifyChanges();
            filteredCount = getFilteredCount(menuItem.getTitle().toString(), FILTER.GAME_SERIES);
            return false;
        }
    };

    private boolean onFilterAmiiboSeriesClick() {
        SubMenu subMenu = menuFilterAmiiboSeries.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null) return true;

        Set<String> items = new HashSet<>();
        for (Amiibo amiibo : amiiboManager.amiibos.values()) {

            AmiiboSeries amiiboSeries = amiibo.getAmiiboSeries();
            if (null != amiiboSeries &&
                    Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(),
                            settings.getFilter(FILTER.GAME_SERIES)) &&
                    Amiibo.matchesCharacterFilter(amiibo.getCharacter(),
                            settings.getFilter(FILTER.CHARACTER)) &&
                    Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(),
                            settings.getFilter(FILTER.AMIIBO_TYPE))
            ) {
                items.add(amiiboSeries.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_amiibo_series_group, Menu.NONE, 0, item)
                    .setChecked(item.equals(settings.getFilter(FILTER.AMIIBO_SERIES)))
                    .setOnMenuItemClickListener(onFilterAmiiboSeriesItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_amiibo_series_group, true, true);

        return true;
    }

    private final MenuItem.OnMenuItemClickListener onFilterAmiiboSeriesItemClick
            = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setFilter(FILTER.AMIIBO_SERIES, menuItem.getTitle().toString());
            settings.notifyChanges();
            filteredCount = getFilteredCount(menuItem.getTitle().toString(), FILTER.AMIIBO_SERIES);
            return false;
        }
    };

    private boolean onFilterAmiiboTypeClick() {
        SubMenu subMenu = menuFilterAmiiboType.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null) return true;

        Set<AmiiboType> items = new HashSet<>();
        for (Amiibo amiibo : amiiboManager.amiibos.values()) {

            AmiiboType amiiboType = amiibo.getAmiiboType();
            if (null != amiiboType &&
                    Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(),
                            settings.getFilter(FILTER.GAME_SERIES)) &&
                    Amiibo.matchesCharacterFilter(amiibo.getCharacter(),
                            settings.getFilter(FILTER.CHARACTER)) &&
                    Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(),
                            settings.getFilter(FILTER.AMIIBO_SERIES))
            ) {
                items.add(amiiboType);
            }
        }

        ArrayList<AmiiboType> list = new ArrayList<>(items);
        Collections.sort(list);
        for (AmiiboType item : list) {
            subMenu.add(R.id.filter_amiibo_type_group, Menu.NONE, 0, item.name)
                    .setChecked(item.name.equals(settings.getFilter(FILTER.AMIIBO_TYPE)))
                    .setOnMenuItemClickListener(onFilterAmiiboTypeItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_amiibo_type_group, true, true);

        return true;
    }

    private final MenuItem.OnMenuItemClickListener onFilterAmiiboTypeItemClick
            = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setFilter(FILTER.AMIIBO_TYPE, menuItem.getTitle().toString());
            settings.notifyChanges();
            filteredCount = getFilteredCount(menuItem.getTitle().toString(), FILTER.AMIIBO_TYPE);
            return false;
        }
    };

    private void onFilterGameTitlesClick() {
        SubMenu subMenu = menuFilterGameTitles.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null) return;
        GamesManager gamesManager = settings.getGamesManager();

        Set<String> items = new HashSet<>();
        if (null != gamesManager) {
            for (GameTitles gameTitle : gamesManager.getGameTitles()) {
                items.add(gameTitle.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_game_titles_group, Menu.NONE, 0, item)
                    .setChecked(item.equals(settings.getFilter(FILTER.GAME_TITLES)))
                    .setOnMenuItemClickListener(onFilterGameTitlesItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_game_titles_group, true, true);

    }

    private final MenuItem.OnMenuItemClickListener onFilterGameTitlesItemClick =
            new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setFilter(FILTER.GAME_TITLES, menuItem.getTitle().toString());
            settings.notifyChanges();
            filteredCount = getFilteredCount(menuItem.getTitle().toString(), FILTER.GAME_TITLES);
            return false;
        }
    };

    private void getToolbarOptions(Toolbar toolbar, byte[] tagData, AmiiboFile amiiboFile) {
        if (!toolbar.getMenu().hasVisibleItems())
            toolbar.inflateMenu(R.menu.amiibo_menu);
        boolean available = null != tagData  && tagData.length > 0;
        if (available) {
            try {
                Amiibo.dataToId(tagData);
            } catch (Exception e) {
                available = false;
                Debug.Info(e);
            }
        }
        toolbar.getMenu().findItem(R.id.mnu_write).setEnabled(available);
        toolbar.getMenu().findItem(R.id.mnu_update).setEnabled(available);
        toolbar.getMenu().findItem(R.id.mnu_save).setEnabled(available);
        toolbar.getMenu().findItem(R.id.mnu_edit).setEnabled(available);
        toolbar.getMenu().findItem(R.id.mnu_view_hex).setEnabled(available);
        toolbar.getMenu().findItem(R.id.mnu_validate).setEnabled(available);

        MenuItem backup = toolbar.getMenu().findItem(R.id.mnu_save);
        MenuItem delete = toolbar.getMenu().findItem(R.id.mnu_delete);
        if (null != amiiboFile) {
            if (null != amiiboFile.getDocUri()) {
                String relativeDocument = Storage.getRelativeDocument(
                        amiiboFile.getDocUri().getUri()
                );
                backup.setVisible(!relativeDocument.startsWith("/Foomiibo/"));
            } else if (null != amiiboFile.getFilePath()) {
                String relativeFile = Storage.getRelativePath(amiiboFile.getFilePath(),
                        prefs.preferEmulated()).replace(prefs.browserRootFolder(), "");
                backup.setVisible(!relativeFile.startsWith("/Foomiibo/"));
            }
            delete.setVisible(true);
        } else {
            delete.setVisible(false);
        }

        toolbar.setOnMenuItemClickListener(item -> {
            clickedAmiibo = amiiboFile;
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
                scan.putExtra(NFCIntent.EXTRA_IGNORE_TAG_ID, ignoreTagId);
                onUpdateTagResult.launch(scan.putExtras(args));
                return true;
            } else if (item.getItemId() == R.id.mnu_save) {
                View view = getLayoutInflater().inflate(R.layout.dialog_backup, null);
                AlertDialog.Builder dialog = new AlertDialog.Builder(this);
                final EditText input = view.findViewById(R.id.backup_entry);
                input.setText(TagArray.decipherFilename(settings.getAmiiboManager(), tagData, true));
                Dialog backupDialog = dialog.setView(view).create();
                view.findViewById(R.id.save_backup).setOnClickListener(v -> {
                    try {
                        String fileName = input.getText().toString() + ".bin";
                        if (isDocumentStorage()) {
                            DocumentFile rootDocument = DocumentFile.fromTreeUri(this,
                                    this.settings.getBrowserRootDocument());
                            if (null == rootDocument) throw new NullPointerException();
                            fileName = TagArray.writeBytesToDocument(this, rootDocument,
                                    fileName, tagData);
                        } else {
                            fileName = TagArray.writeBytesToFile(Storage.getDownloadDir(
                                    "TagMo", "Backups"
                            ), fileName, tagData);
                        }
                        new IconifiedSnackbar(this, mainLayout).buildSnackbar(
                                getString(R.string.wrote_file, fileName), Snackbar.LENGTH_SHORT
                        ).show();
                        this.onRootFolderChanged(true);
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
                Intent tagEdit = new Intent(this, TagDataEditor.class);
                onUpdateTagResult.launch(tagEdit.putExtras(args));
                return true;
            } else if (item.getItemId() == R.id.mnu_view_hex) {
                Intent hexView = new Intent(this, HexCodeViewer.class);
                hexView.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData);
                startActivity(hexView);
                return true;
            } else if (item.getItemId() == R.id.mnu_validate) {
                try {
                    TagArray.validateData(tagData);
                    new IconifiedSnackbar(this, mainLayout).buildSnackbar(
                            R.string.validation_success, Snackbar.LENGTH_SHORT).show();
                } catch (Exception e) {
                    new IconifiedSnackbar(this, mainLayout).buildSnackbar(e.getMessage(),
                            R.drawable.ic_baseline_bug_report_24dp, Snackbar.LENGTH_LONG).show();
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_delete) {
                deleteAmiiboDocument(amiiboFile);
                return true;
            } else if (item.getItemId() == R.id.mnu_ignore_tag_id) {
                ignoreTagId = !item.isChecked();
                item.setChecked(ignoreTagId);
                return true;
            }
            return false;
        });
    }

    void getToolbarOptions(BrowserFragment fragment, Toolbar toolbar, byte[] tagData, View itemView) {
        if (!toolbar.getMenu().hasVisibleItems())
            toolbar.inflateMenu(R.menu.amiibo_menu);
        toolbar.getMenu().findItem(R.id.mnu_save).setTitle(R.string.cache);
        toolbar.getMenu().findItem(R.id.mnu_scan).setVisible(false);
        toolbar.setOnMenuItemClickListener(item -> {
            Bundle args = new Bundle();
            Intent scan = new Intent(this, NfcActivity.class);
            if (item.getItemId() == R.id.mnu_write) {
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData);
                scan.setAction(NFCIntent.ACTION_WRITE_TAG_FULL);
                try {
                    fragment.onUpdateTagResult.launch(scan.putExtras(args));
                } catch (IllegalStateException ex) {
                    mainLayout.setAdapter(pagerAdapter);
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_update) {
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData);
                scan.setAction(NFCIntent.ACTION_WRITE_TAG_DATA);
                scan.putExtra(NFCIntent.EXTRA_IGNORE_TAG_ID, ignoreTagId);
                try {
                    fragment.onUpdateTagResult.launch(scan.putExtras(args));
                } catch (IllegalStateException ex) {
                    mainLayout.setAdapter(pagerAdapter);
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_save) {
                fragment.buildFoomiiboFile(tagData);
                itemView.callOnClick();
                this.onRefresh(true);
                return true;
            } else if (item.getItemId() == R.id.mnu_edit) {
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData);
                Intent tagEdit = new Intent(this, TagDataEditor.class);
                try {
                    fragment.onUpdateTagResult.launch(tagEdit.putExtras(args));
                } catch (IllegalStateException ex) {
                    mainLayout.setAdapter(pagerAdapter);
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_view_hex) {
                Intent hexView = new Intent(this, HexCodeViewer.class);
                hexView.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData);
                startActivity(hexView);
                return true;
            } else if (item.getItemId() == R.id.mnu_validate) {
                try {
                    TagArray.validateData(tagData);
                    new IconifiedSnackbar(this, mainLayout).buildSnackbar(
                            R.string.validation_success, Snackbar.LENGTH_SHORT).show();
                } catch (Exception e) {
                    new IconifiedSnackbar(this, mainLayout).buildSnackbar(e.getMessage(),
                            R.drawable.ic_baseline_bug_report_24dp, Snackbar.LENGTH_LONG).show();
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_delete) {
                fragment.deleteFoomiiboFile(tagData);
                itemView.callOnClick();
                this.onRefresh(true);
                return true;
            } else if (item.getItemId() == R.id.mnu_ignore_tag_id) {
                ignoreTagId = !item.isChecked();
                item.setChecked(ignoreTagId);
                return true;
            }
            return false;
        });
    }

    private void getGameCompatibility(TextView txtUsage, byte[] tagData) {
        GamesManager gamesManager = settings.getGamesManager();
        TextView label = findViewById(R.id.txtUsageLabel);
        if (null != gamesManager) {
            label.setVisibility(View.VISIBLE);
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    long amiiboId = Amiibo.dataToId(tagData);
                    String usage = gamesManager.getGamesCompatibility(amiiboId);
                    txtUsage.post(() -> txtUsage.setText(usage));
                } catch (Exception ex) {
                    Debug.Warn(ex);
                }
            });
        } else {
            label.setVisibility(View.GONE);
        }
    }

    public void onKeysLoaded(boolean indicator) {
        hideFakeSnackbar();
        this.onRefresh(indicator);
    }

    public void onRefresh(boolean indicator) {
        this.loadAmiiboManager();
        this.onRootFolderChanged(indicator);
    }

    public boolean isDocumentStorage() {
        if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)
                && null != this.settings.getBrowserRootDocument()) {
            try {
                DocumentFile.fromTreeUri(this, this.settings.getBrowserRootDocument());
                return true;
            } catch (IllegalArgumentException iae) {
                return false;
            }
        }
        return false;
    }

    private void onDocumentRequested() throws ActivityNotFoundException {
        if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
            intent.putExtra("android.content.extra.FANCY", true);
            onDocumentTree.launch(intent);
        }
    }

    private void onDocumentEnabled() {
        if (isDocumentStorage()) {
            this.onStorageEnabled();
        } else {
            try {
                onDocumentRequested();
            } catch (ActivityNotFoundException anf) {
                new Toasty(this).Long(R.string.storage_unavailable);
            }
        }
    }

    private void onStorageEnabled() {
        if (BuildConfig.WEAR_OS) {
            if (keyManager.isKeyMissing()) {
                showSettingsPage();
            } else {
                this.onRefresh(true);
            }
        } else {
            if (isDocumentStorage()) {
                switchStorageRoot.setVisibility(View.VISIBLE);
                switchStorageRoot.setText(R.string.document_storage_root);
                switchStorageRoot.setOnClickListener(view -> {
                    try {
                        onDocumentRequested();
                    } catch (ActivityNotFoundException anf) {
                        new Toasty(this).Long(R.string.storage_unavailable);
                    }
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                });
                if (Debug.isNewer(Build.VERSION_CODES.R) && !BuildConfig.GOOGLE_PLAY) {
                    switchStorageType.setVisibility(View.VISIBLE);
                    switchStorageType.setText(R.string.grant_file_permission);
                    switchStorageType.setOnClickListener(view -> {
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        if (Environment.isExternalStorageManager()) {
                            settings.setBrowserRootDocument(null);
                            settings.notifyChanges();
                            this.onStorageEnabled();
                        } else {
                            requestScopedStorage();
                        }
                    });
                } else {
                    switchStorageType.setVisibility(View.GONE);
                }
                if (keyManager.isKeyMissing()) {
                    showSettingsPage();
                } else {
                    this.onRefresh(true);
                }
            } else {
                boolean internal = prefs.preferEmulated();
                if (Storage.getFile(internal).exists() && Storage.hasPhysicalStorage()) {
                    switchStorageRoot.setVisibility(View.VISIBLE);
                    switchStorageRoot.setText(internal
                            ? R.string.emulated_storage_root
                            : R.string.physical_storage_root);
                    switchStorageRoot.setOnClickListener(view -> {
                        boolean external = !prefs.preferEmulated();
                        switchStorageRoot.setText(external
                                ? R.string.emulated_storage_root
                                : R.string.physical_storage_root);
                        this.settings.setBrowserRootFolder(Storage.getFile(external));
                        this.settings.notifyChanges();
                        prefs.preferEmulated(external);
                    });
                } else {
                    switchStorageRoot.setVisibility(View.GONE);
                }
                if (Debug.isNewer(Build.VERSION_CODES.R)) {
                    switchStorageType.setVisibility(View.VISIBLE);
                    switchStorageType.setText(R.string.force_document_storage);
                    switchStorageType.setOnClickListener(view -> {
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        try {
                            onDocumentRequested();
                        } catch (ActivityNotFoundException anf) {
                            new Toasty(this).Long(R.string.storage_unavailable);
                        }
                    });
                } else {
                    switchStorageType.setVisibility(View.GONE);
                }
                if (keyManager.isKeyMissing()) {
                    hideFakeSnackbar();
                    showFakeSnackbar(getString(R.string.locating_keys));
                    locateKeyFiles();
                } else {
                    this.onRefresh(true);
                }
            }
        }
    }

    private boolean onMenuItemClicked(MenuItem item) {
        if (item.getItemId() == R.id.refresh) {
            onRefresh(true);
        } else if (item.getItemId() == R.id.sort_id) {
            settings.setSort(SORT.ID.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.sort_name) {
            settings.setSort(SORT.NAME.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.sort_character) {
            settings.setSort(SORT.CHARACTER.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.sort_game_series) {
            settings.setSort(SORT.GAME_SERIES.getValue());
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
            if (null != amiibosView)
                amiibosView.setLayoutManager(new LinearLayoutManager(this));
            foomiiboView.setLayoutManager(new LinearLayoutManager(this));
            settings.setAmiiboView(VIEW.SIMPLE.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.view_compact) {
            if (null != amiibosView)
                amiibosView.setLayoutManager(new LinearLayoutManager(this));
            foomiiboView.setLayoutManager(new LinearLayoutManager(this));
            settings.setAmiiboView(VIEW.COMPACT.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.view_large) {
            if (null != amiibosView)
                amiibosView.setLayoutManager(new LinearLayoutManager(this));
            foomiiboView.setLayoutManager(new LinearLayoutManager(this));
            settings.setAmiiboView(VIEW.LARGE.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.view_image) {
            if (null != amiibosView)
                amiibosView.setLayoutManager(new GridLayoutManager(this, getColumnCount()));
            foomiiboView.setLayoutManager(new GridLayoutManager(this, getColumnCount()));
            settings.setAmiiboView(VIEW.IMAGE.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.recursive) {
            this.settings.setRecursiveEnabled(!this.settings.isRecursiveEnabled());
            this.settings.notifyChanges();
        } else if (item.getItemId() == R.id.capture_logcat) {
            onCaptureLogcatClicked();
        } else if (item.getItemId() == R.id.filter_character) {
            return onFilterCharacterClick();
        } else if (item.getItemId() == R.id.filter_game_series) {
            return onFilterGameSeriesClick();
        } else if (item.getItemId() == R.id.filter_amiibo_series) {
            return onFilterAmiiboSeriesClick();
        } else if (item.getItemId() == R.id.filter_amiibo_type) {
            return onFilterAmiiboTypeClick();
        } else if (item.getItemId() == R.id.filter_game_titles) {
            onFilterGameTitlesClick();
        }
        return BuildConfig.WEAR_OS || super.onOptionsItemSelected(item);
    }

    public void onCreateWearOptionsMenu() {
        Toolbar toolbar = findViewById(R.id.drawer_layout);
        if (!toolbar.getMenu().hasVisibleItems()) {
            toolbar.inflateMenu(R.menu.browser_menu);
            menuSortId = toolbar.getMenu().findItem(R.id.sort_id);
            menuSortName = toolbar.getMenu().findItem(R.id.sort_name);
            menuSortCharacter = toolbar.getMenu().findItem(R.id.sort_character);
            menuSortGameSeries = toolbar.getMenu().findItem(R.id.sort_game_series);
            menuSortAmiiboSeries = toolbar.getMenu().findItem(R.id.sort_amiibo_series);
            menuSortAmiiboType = toolbar.getMenu().findItem(R.id.sort_amiibo_type);
            menuSortFilePath = toolbar.getMenu().findItem(R.id.sort_file_path);
            menuFilterGameSeries = toolbar.getMenu().findItem(R.id.filter_game_series);
            menuFilterCharacter = toolbar.getMenu().findItem(R.id.filter_character);
            menuFilterAmiiboSeries = toolbar.getMenu().findItem(R.id.filter_amiibo_series);
            menuFilterAmiiboType = toolbar.getMenu().findItem(R.id.filter_amiibo_type);
            menuFilterGameTitles = toolbar.getMenu().findItem(R.id.filter_game_titles);
            menuViewSimple = toolbar.getMenu().findItem(R.id.view_simple);
            menuViewCompact = toolbar.getMenu().findItem(R.id.view_compact);
            menuViewLarge = toolbar.getMenu().findItem(R.id.view_large);
            menuViewImage = toolbar.getMenu().findItem(R.id.view_image);
            menuRecursiveFiles = toolbar.getMenu().findItem(R.id.recursive);
        }

        this.onSortChanged();
        this.onViewChanged();
        this.onRecursiveFilesChanged();

        toolbar.setOnMenuItemClickListener(this::onMenuItemClicked);
    }

    @SuppressLint("RestrictedApi")
    private void setOptionalIconsVisible(Menu menu) {
        if (menu instanceof MenuBuilder)
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (BuildConfig.WEAR_OS) return super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.browser_menu, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        setOptionalIconsVisible(menu);

        MenuItem menuSearch = menu.findItem(R.id.search);
        MenuItem menuUpdate = menu.findItem(R.id.install_update);
        menuSortId = menu.findItem(R.id.sort_id);
        menuSortName = menu.findItem(R.id.sort_name);
        menuSortCharacter = menu.findItem(R.id.sort_character);
        menuSortGameSeries = menu.findItem(R.id.sort_game_series);
        menuSortAmiiboSeries = menu.findItem(R.id.sort_amiibo_series);
        menuSortAmiiboType = menu.findItem(R.id.sort_amiibo_type);
        menuSortFilePath = menu.findItem(R.id.sort_file_path);
        menuFilterGameSeries = menu.findItem(R.id.filter_game_series);
        menuFilterCharacter = menu.findItem(R.id.filter_character);
        menuFilterAmiiboSeries = menu.findItem(R.id.filter_amiibo_series);
        menuFilterAmiiboType = menu.findItem(R.id.filter_amiibo_type);
        menuFilterGameTitles = menu.findItem(R.id.filter_game_titles);
        menuViewSimple = menu.findItem(R.id.view_simple);
        menuViewCompact = menu.findItem(R.id.view_compact);
        menuViewLarge = menu.findItem(R.id.view_large);
        menuViewImage = menu.findItem(R.id.view_image);
        menuRecursiveFiles = menu.findItem(R.id.recursive);

        if (null == this.settings) return false;

        this.onSortChanged();
        this.onViewChanged();
        this.onRecursiveFilesChanged();

        menuUpdate.setVisible(null != appUpdate || null != updateUrl);

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menuSearch.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setSubmitButtonEnabled(false);
        menuSearch.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                if (BottomSheetBehavior.STATE_EXPANDED == bottomSheet.getState()
                        || View.VISIBLE == amiiboContainer.getVisibility()
                        || getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    onBackPressed();
                    return false;
                } else {
                    return true;
                }
            }
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                settings.setQuery(query);
                settings.notifyChanges();
                if (mainLayout.getCurrentItem() == 0) setAmiiboStats();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                settings.setQuery(query);
                settings.notifyChanges();
                if (mainLayout.getCurrentItem() == 0 && query.length() == 0)
                    setAmiiboStats();
                return true;
            }
        });

        String query = settings.getQuery();
        if (!TextUtils.isEmpty(query)) {
            menuSearch.expandActionView();
            searchView.setQuery(query, true);
            searchView.clearFocus();
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (!closePrefsDrawer()) {
                prefsDrawer.openDrawer(GravityCompat.START);
            }
        } else if (item.getItemId() == R.id.install_update) {
            if (null != appUpdate) updates.downloadPlayUpdate(appUpdate);
            if (null != updateUrl) updates.installUpdateCompat(updateUrl);
        }
        return onMenuItemClicked(item);
    }

    @Override
    public void onAmiiboClicked(View itemView, AmiiboFile amiiboFile) {
        if (null == amiiboFile.getDocUri() && null == amiiboFile.getFilePath()) return;
        try {
            byte[] tagData = TagArray.getValidatedData(keyManager, amiiboFile);
            if (settings.getAmiiboView() != VIEW.IMAGE.getValue()) {
                LinearLayout menuOptions = itemView.findViewById(R.id.menu_options);
                Toolbar toolbar = menuOptions.findViewById(R.id.toolbar);
                if (menuOptions.getVisibility() == View.VISIBLE) {
                    menuOptions.setVisibility(View.GONE);
                } else {
                    menuOptions.setVisibility(View.VISIBLE);
                    getToolbarOptions(toolbar, tagData, amiiboFile);
                }
                TextView txtUsage = itemView.findViewById(R.id.txtUsage);
                if (txtUsage.getVisibility() == View.VISIBLE) {
                    txtUsage.setVisibility(View.GONE);
                } else {
                    txtUsage.setVisibility(View.VISIBLE);
                    getGameCompatibility(txtUsage, tagData);
                }
            } else {
                updateAmiiboView(tagData, amiiboFile);
            }
        } catch (Exception e) {
            Debug.Warn(e);
        }
    }

    @Override
    public void onAmiiboRebind(View itemView, AmiiboFile amiiboFile) {
        if (amiiboFile.getFilePath() == null)
            return;
        try {
            byte[] tagData = null != amiiboFile.getData() ? amiiboFile.getData()
                    : TagArray.getValidatedFile(keyManager, amiiboFile.getFilePath());

            if (settings.getAmiiboView() != VIEW.IMAGE.getValue()) {
                getToolbarOptions(itemView.findViewById(R.id.menu_options)
                        .findViewById(R.id.toolbar), tagData, amiiboFile);
                getGameCompatibility(itemView.findViewById(R.id.txtUsage), tagData);
            } else {
                updateAmiiboView(tagData, amiiboFile);
            }
        } catch (Exception e) {
            Debug.Warn(e);
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

    public void loadPTagKeyManager() {
        if (prefs.power_tag_support()) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    PowerTagManager.getPowerTagManager();
                } catch (Exception e) {
                    Debug.Warn(e);
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
                Debug.Warn(e);
                amiiboManager = null;
                new Toasty(this).Short(R.string.amiibo_info_parse_error);
            }

            GamesManager gamesManager;
            try {
                gamesManager = GamesManager.getGamesManager(this);
            } catch (IOException | JSONException | ParseException e) {
                Debug.Warn(e);
                gamesManager = null;
            }

            if (Thread.currentThread().isInterrupted()) return;

            final AmiiboManager uiAmiiboManager = amiiboManager;
            final GamesManager uiGamesManager = gamesManager;
            this.runOnUiThread(() -> {
                settings.setAmiiboManager(uiAmiiboManager);
                settings.setGamesManager(uiGamesManager);
                settings.notifyChanges();
                getManagerStats();
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

                File download = Storage.getDownloadDir(null);
                if (isDirectoryHidden(rootFolder, download, recursiveFiles))
                    amiiboFiles.addAll(AmiiboManager
                            .listAmiibos(keyManager, download, true));

            File foomiibo = new File(getFilesDir(), "Foomiibo");
            amiiboFiles.addAll(AmiiboManager.listAmiibos(keyManager, foomiibo, true));

            if (Thread.currentThread().isInterrupted()) return;

            this.runOnUiThread(() -> {
                hideFakeSnackbar();
                settings.setAmiiboFiles(amiiboFiles);
                settings.notifyChanges();
            });
        });
    }

    @SuppressLint("NewApi")
    private void loadAmiiboDocuments(DocumentFile rootFolder, boolean recursiveFiles)
            throws SecurityException {
        Executors.newSingleThreadExecutor().execute(() -> {
            final ArrayList<AmiiboFile> amiiboFiles = AmiiboManager
                    .listAmiiboDocuments(this, keyManager, rootFolder, recursiveFiles);
            File foomiibo = new File(getFilesDir(), "Foomiibo");
            amiiboFiles.addAll(AmiiboManager.listAmiibos(keyManager, foomiibo, true));

            if (Thread.currentThread().isInterrupted()) return;

            this.runOnUiThread(() -> {
                hideFakeSnackbar();
                settings.setAmiiboFiles(amiiboFiles);
                settings.notifyChanges();
            });
        });
    }

    void loadAmiiboBackground() {
        if (isDocumentStorage()) {
            DocumentFile rootDocument = DocumentFile.fromTreeUri(
                    BrowserActivity.this, settings.getBrowserRootDocument()
            );
            try {
                loadAmiiboDocuments(rootDocument, settings.isRecursiveEnabled());
            } catch (SecurityException ignored) { }
        } else {
            loadAmiiboFiles(settings.getBrowserRootFolder(), settings.isRecursiveEnabled());
        }
    }

    ActivityResultLauncher<Intent> onDocumentTree = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null)
            return;

        Uri treeUri = result.getData().getData();
        if (Debug.isNewer(Build.VERSION_CODES.KITKAT))
            getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);

        // List all existing files inside picked directory
        if (null != pickedDir) {
            this.settings.setBrowserRootDocument(treeUri);
            this.onStorageEnabled();
        }
    });

    @Override
    public void onBrowserSettingsChanged(
            BrowserSettings newBrowserSettings,
            BrowserSettings oldBrowserSettings) {
        if (newBrowserSettings == null || oldBrowserSettings == null) return;
        boolean folderChanged = !BrowserSettings.equals(
                newBrowserSettings.getBrowserRootFolder(),
                oldBrowserSettings.getBrowserRootFolder()
        );
        try {
            folderChanged = folderChanged || !BrowserSettings.equals(
                    newBrowserSettings.getBrowserRootDocument(),
                    oldBrowserSettings.getBrowserRootDocument()
            );
        } catch (Exception ignored) { }
        if (newBrowserSettings.isRecursiveEnabled() != oldBrowserSettings.isRecursiveEnabled()) {
            settings.getAmiiboFiles().clear();
            folderChanged = true;
            onRecursiveFilesChanged();
        }
        if (!BrowserSettings.equals(newBrowserSettings.getLastUpdatedAPI(),
                oldBrowserSettings.getLastUpdatedAPI())) {
            this.loadAmiiboManager();
            folderChanged = true;
        }
        if (folderChanged) {
            onRootFolderChanged(true);
            setFolderText(newBrowserSettings);
        } else {
            setFolderText(null);
        }

        if (newBrowserSettings.getSort() != oldBrowserSettings.getSort()) {
            onSortChanged();
        }
        if (!BrowserSettings.equals(newBrowserSettings.getFilter(FILTER.CHARACTER),
                oldBrowserSettings.getFilter(FILTER.CHARACTER))) {
            onFilterContentsChanged(FILTER.CHARACTER);
        }
        if (!BrowserSettings.equals(newBrowserSettings.getFilter(FILTER.GAME_SERIES),
                oldBrowserSettings.getFilter(FILTER.GAME_SERIES))) {
            onFilterContentsChanged(FILTER.GAME_SERIES);
        }
        if (!BrowserSettings.equals(newBrowserSettings.getFilter(FILTER.AMIIBO_SERIES),
                oldBrowserSettings.getFilter(FILTER.AMIIBO_SERIES))) {
            onFilterContentsChanged(FILTER.AMIIBO_SERIES);
        }
        if (!BrowserSettings.equals(newBrowserSettings.getFilter(FILTER.AMIIBO_TYPE),
                oldBrowserSettings.getFilter(FILTER.AMIIBO_TYPE))) {
            onFilterContentsChanged(FILTER.AMIIBO_TYPE);
        }
        if (!BrowserSettings.equals(newBrowserSettings.getFilter(FILTER.GAME_TITLES),
                oldBrowserSettings.getFilter(FILTER.GAME_TITLES))) {
            onFilterContentsChanged(FILTER.GAME_TITLES);
        }
        if (newBrowserSettings.getAmiiboView() != oldBrowserSettings.getAmiiboView()) {
            onViewChanged();
        }
        if (System.currentTimeMillis() >= oldBrowserSettings.getLastUpdatedGit() + 3600000) {
            updates = new CheckUpdatesTask(this);
            if (BuildConfig.GOOGLE_PLAY) {
                updates.setPlayUpdateListener(appUpdateInfo -> {
                    appUpdate = appUpdateInfo;
                    if (BuildConfig.WEAR_OS)
                        onCreateWearOptionsMenu();
                    else
                        invalidateOptionsMenu();
                });
            } else {
                updates.setUpdateListener(downloadUrl -> {
                    updateUrl = downloadUrl;
                    if (BuildConfig.WEAR_OS)
                        onCreateWearOptionsMenu();
                    else
                        invalidateOptionsMenu();
                });
            }
            newBrowserSettings.setLastUpdatedGit(System.currentTimeMillis());
        }

        prefs.browserRootFolder(Storage.getRelativePath(
                newBrowserSettings.getBrowserRootFolder(), prefs.preferEmulated()
        ));
        prefs.browserRootDocument(null != newBrowserSettings.getBrowserRootDocument()
                ? newBrowserSettings.getBrowserRootDocument().toString() : null
        );
        prefs.query(newBrowserSettings.getQuery());
        prefs.sort(newBrowserSettings.getSort());
        prefs.filterCharacter(newBrowserSettings.getFilter(FILTER.CHARACTER));
        prefs.filterGameSeries(newBrowserSettings.getFilter(FILTER.GAME_SERIES));
        prefs.filterAmiiboSeries(newBrowserSettings.getFilter(FILTER.AMIIBO_SERIES));
        prefs.filterAmiiboType(newBrowserSettings.getFilter(FILTER.AMIIBO_TYPE));
        prefs.filterGameTitles(newBrowserSettings.getFilter(FILTER.GAME_TITLES));
        prefs.browserAmiiboView(newBrowserSettings.getAmiiboView());
        prefs.image_network(newBrowserSettings.getImageNetworkSettings());
        prefs.recursiveFolders(newBrowserSettings.isRecursiveEnabled());
        prefs.lastUpdatedAPI(newBrowserSettings.getLastUpdatedAPI());
        prefs.lastUpdatedGit(newBrowserSettings.getLastUpdatedGit());
    }

    private void setIndexFastScrollRecyclerListener(RecyclerView amiibosView) {
        if (BuildConfig.WEAR_OS) return;
        if (amiibosView instanceof IndexFastScrollRecyclerView) {
            IndexFastScrollRecyclerView indexView = (IndexFastScrollRecyclerView) amiibosView;
            //noinspection deprecation
            indexView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        indexView.setIndexBarVisibility(true);
                    } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        indexView.setIndexBarVisibility(false);
                    }
                }
            });
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
                setIndexFastScrollRecyclerListener(amiibosView);
                setIndexFastScrollRecyclerListener(foomiiboView);
                break;
            case CHARACTER:
                menuSortCharacter.setChecked(true);
                setIndexFastScrollRecyclerListener(amiibosView);
                setIndexFastScrollRecyclerListener(foomiiboView);
                break;
            case GAME_SERIES:
                menuSortGameSeries.setChecked(true);
                setIndexFastScrollRecyclerListener(amiibosView);
                setIndexFastScrollRecyclerListener(foomiiboView);
                break;
            case AMIIBO_SERIES:
                menuSortAmiiboSeries.setChecked(true);
                setIndexFastScrollRecyclerListener(amiibosView);
                setIndexFastScrollRecyclerListener(foomiiboView);
                break;
            case AMIIBO_TYPE:
                menuSortAmiiboType.setChecked(true);
                setIndexFastScrollRecyclerListener(amiibosView);
                setIndexFastScrollRecyclerListener(foomiiboView);
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
        if (isDocumentStorage()) {
            DocumentFile rootDocument = DocumentFile.fromTreeUri(this,
                    this.settings.getBrowserRootDocument());
            if (!keyManager.isKeyMissing()) {
                if (indicator) showFakeSnackbar(getString(R.string.refreshing_list));
                try {
                    this.loadAmiiboDocuments(rootDocument, settings.isRecursiveEnabled());
                } catch (SecurityException sx) {
                    try {
                        onDocumentRequested();
                    } catch (ActivityNotFoundException ignored) { }
                }
            }
        } else {
            File rootFolder = this.settings.getBrowserRootFolder();
            if (!keyManager.isKeyMissing()) {
                if (indicator) showFakeSnackbar(getString(R.string.refreshing_list));
                this.loadAmiiboFiles(rootFolder, this.settings.isRecursiveEnabled());
            }
            this.loadFolders(rootFolder);
        }
    }

    private void onFilterContentsChanged(FILTER filter) {
        String filterText = settings.getFilter(filter);
        String filterTag = "";
        switch (filter) {
            case CHARACTER:
                filterTag = getString(R.string.filter_character);
                break;
            case GAME_SERIES:
                filterTag = getString(R.string.filter_game_series);
                break;
            case AMIIBO_SERIES:
                filterTag = getString(R.string.filter_amiibo_series);
                break;
            case AMIIBO_TYPE:
                filterTag = getString(R.string.filter_amiibo_type);
                break;
            case GAME_TITLES:
                filterTag = getString(R.string.filter_game_titles);
                break;
        }
        fragmentBrowser.addFilterItemView(filterText, filterTag, v -> {
            settings.setFilter(filter, "");
            settings.notifyChanges();
            setAmiiboStats();
        });
    }

    void onFilterContentsLoaded() {
        onFilterContentsChanged(FILTER.CHARACTER);
        onFilterContentsChanged(FILTER.GAME_SERIES);
        onFilterContentsChanged(FILTER.AMIIBO_SERIES);
        onFilterContentsChanged(FILTER.AMIIBO_TYPE);
        onFilterContentsChanged(FILTER.GAME_TITLES);
    }

    private void onRecursiveFilesChanged() {
        if (null == menuRecursiveFiles) return;
        menuRecursiveFiles.setChecked(settings.isRecursiveEnabled());
    }

    private void launchEliteActivity(Intent resultData) {
        if (TagMo.getPrefs().elite_support()
                && resultData.hasExtra(NFCIntent.EXTRA_SIGNATURE)) {
            showElitePage(resultData.getExtras());
        }
    }

    private final ActivityResultLauncher<Intent> onUpdateTagResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || null == result.getData()) return;

        if (!NFCIntent.ACTION_NFC_SCANNED.equals(result.getData().getAction())
                && !NFCIntent.ACTION_UPDATE_TAG.equals(result.getData().getAction())
                && !NFCIntent.ACTION_EDIT_COMPLETE.equals(result.getData().getAction())) return;


        // If we're supporting, didn't arrive from, but scanned an N2...
        if (prefs.elite_support() && result.getData().hasExtra(NFCIntent.EXTRA_SIGNATURE)) {
            launchEliteActivity(result.getData());
        } else {
            updateAmiiboView(result.getData().getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA));
            // toolbar.getMenu().findItem(R.id.mnu_write).setEnabled(false);
        }
    });

    private void deleteAmiiboFile(AmiiboFile amiiboFile) {
        if (null != amiiboFile && null != amiiboFile.getFilePath()) {
            String relativeFile = Storage.getRelativePath(
                    amiiboFile.getFilePath(), prefs.preferEmulated());
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.warn_delete_file, relativeFile))
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        amiiboContainer.setVisibility(View.GONE);
                        //noinspection ResultOfMethodCallIgnored
                        amiiboFile.getFilePath().delete();
                        new IconifiedSnackbar(this, mainLayout).buildSnackbar(
                                getString(R.string.delete_file, relativeFile),
                                Snackbar.LENGTH_SHORT
                        ).show();
                        this.onRootFolderChanged(true);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss()).show();
        } else {
            new Toasty(this).Short(R.string.delete_missing);
        }
    }

    private void deleteAmiiboDocument(AmiiboFile amiiboFile) {
        if (null != amiiboFile && null != amiiboFile.getDocUri()) {
            String relativeDocument = Storage.getRelativeDocument(
                    amiiboFile.getDocUri().getUri()
            );
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.warn_delete_file, relativeDocument))
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        amiiboContainer.setVisibility(View.GONE);
                        amiiboFile.getDocUri().delete();
                        new IconifiedSnackbar(this, mainLayout).buildSnackbar(
                                getString(R.string.delete_file, relativeDocument),
                                Snackbar.LENGTH_SHORT
                        ).show();
                        this.onRootFolderChanged(true);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss()).show();
        } else {
            deleteAmiiboFile(amiiboFile);
        }
    }

    final CustomTarget<Bitmap> imageTarget = new CustomTarget<>() {

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

    public void updateAmiiboView(byte[] tagData, AmiiboFile amiiboFile) {
        amiiboContainer.post(() -> {
            amiiboContainer.setAlpha(0f);
            amiiboContainer.setVisibility(View.VISIBLE);
            amiiboContainer.animate().alpha(1f).setDuration(150).setListener(null);

            getToolbarOptions(toolbar, tagData, amiiboFile);

            long amiiboId = -1;
            String tagInfo = null;
            String amiiboHexId = "";
            String amiiboName = "";
            String amiiboSeries = "";
            String amiiboType = "";
            String gameSeries = "";
            // String character = "";
            String amiiboImageUrl;

            if (null != tagData  && tagData.length > 0) {
                try {
                    amiiboId = Amiibo.dataToId(tagData);
                } catch (Exception e) {
                    Debug.Info(e);
                }
            }

            if (amiiboId == -1) {
                tagInfo = getString(R.string.read_error);
                amiiboImageUrl = null;
            } else if (amiiboId == 0) {
                tagInfo = getString(R.string.blank_tag);
                amiiboImageUrl = null;
            } else {
                Amiibo amiibo = null;
                AmiiboManager amiiboManager = settings.getAmiiboManager();
                if (null != amiiboManager) {
                    amiibo = amiiboManager.amiibos.get(amiiboId);
                    if (null == amiibo)
                        amiibo = new Amiibo(amiiboManager, amiiboId, null, null);
                }
                if (null != amiibo) {
                    amiiboHexId = Amiibo.idToHex(amiibo.id);
                    amiiboImageUrl = amiibo.getImageUrl();
                    if (null != amiibo.name )
                        amiiboName = amiibo.name;
                    if (null != amiibo.getAmiiboSeries() )
                        amiiboSeries = amiibo.getAmiiboSeries().name;
                    if (null != amiibo.getAmiiboType() )
                        amiiboType = amiibo.getAmiiboType().name;
                    if (null != amiibo.getGameSeries() )
                        gameSeries = amiibo.getGameSeries().name;
                } else {
                    amiiboHexId = Amiibo.idToHex(amiiboId);
                    tagInfo = "ID: " + amiiboHexId;
                    amiiboImageUrl = Amiibo.getImageUrl(amiiboId);
                }
            }

            boolean hasTagInfo = null != tagInfo;
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
                TextView txtUsage = findViewById(R.id.txtUsage);
                getGameCompatibility(txtUsage, tagData);
                txtUsage.setVisibility(View.GONE);
                TextView label = findViewById(R.id.txtUsageLabel);
                label.setOnClickListener(view -> {
                    if (txtUsage.getVisibility() == View.VISIBLE) {
                        txtUsage.setVisibility(View.GONE);
                        label.setText(R.string.game_titles_view);
                    } else {
                        txtUsage.setVisibility(View.VISIBLE);
                        label.setText(R.string.game_titles_hide);
                    }
                });
            } catch (Exception ex) {
                Debug.Warn(ex);
            }

            if (null != imageAmiibo) {
                imageAmiibo.setVisibility(View.GONE);
                GlideApp.with(imageAmiibo).clear(imageAmiibo);
                if (null != amiiboImageUrl) {
                    GlideApp.with(imageAmiibo).asBitmap()
                            .load(amiiboImageUrl).into(imageTarget);
                    final long amiiboTagId = amiiboId;
                    imageAmiibo.setOnClickListener(view -> {
                        Bundle bundle = new Bundle();
                        bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiiboTagId);

                        Intent intent = new Intent(BrowserActivity.this, ImageActivity.class);
                        intent.putExtras(bundle);

                        startActivity(intent);
                    });
                }
            }
            if (amiiboHexId.endsWith("00000002") && !amiiboHexId.startsWith("00000000"))
                txtTagId.setEnabled(false);
        });
    }

    public void updateAmiiboView(byte[] tagData) {
        updateAmiiboView(tagData, clickedAmiibo);
    }

    public int getColumnCount() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR1))
            mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
        else
            mWindowManager.getDefaultDisplay().getMetrics(metrics);
        return (int) ((metrics.widthPixels / metrics.density) / 112 + 0.5);
    }

    @SuppressWarnings("ConstantConditions")
    private void setViewPagerSensitivity(ViewPager2 viewPager, int sensitivity) {
        try {
            Field ff = ViewPager2.class.getDeclaredField("mRecyclerView") ;
            ff.setAccessible(true);
            RecyclerView recyclerView =  (RecyclerView) ff.get(viewPager);
            Field touchSlopField = RecyclerView.class.getDeclaredField("mTouchSlop") ;
            touchSlopField.setAccessible(true);
            int touchSlop = (int) touchSlopField.get(recyclerView);
            touchSlopField.set(recyclerView,touchSlop * sensitivity);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void getManagerStats() {
        View foomiiboSlider = fragmentBrowser.getView();
        TextView characterStats = findViewById(R.id.stats_character);
        TextView amiiboTypeStats = findViewById(R.id.stats_amiibo_type);
        TextView amiiboTitleStats = findViewById(R.id.stats_amiibo_titles);

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        boolean hasAmiibo = null != amiiboManager;
        if (null != foomiiboSlider) {
            TextView foomiiboStats = foomiiboSlider.findViewById(R.id.divider_text);
            foomiiboStats.setText(getString(R.string.number_foomiibo, hasAmiibo
                    ? amiiboManager.amiibos.size() : 0));
        }
        characterStats.setText(getString(R.string.number_character, hasAmiibo
                ? amiiboManager.characters.size() : 0));
        amiiboTypeStats.setText(getString(R.string.number_type, hasAmiibo
                ? amiiboManager.amiiboTypes.size() : 0));

        if (hasAmiibo) {
            characterStats.setOnClickListener(view1 -> {
                final ArrayList<Character> items = new ArrayList<>();
                for (Character character : amiiboManager.characters.values()) {
                    if (!items.contains(character))
                        items.add(character);
                }
                Collections.sort(items);

                new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.pref_amiibo_characters)
                        .setAdapter(new ArrayAdapter<>(this,
                                android.R.layout.simple_list_item_2, android.R.id.text1, items) {
                            @NonNull
                            @Override
                            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                                View view = super.getView(position, convertView, parent);
                                TextView text1 = view.findViewById(android.R.id.text1);
                                TextView text2 = view.findViewById(android.R.id.text2);

                                Character character = getItem(position);
                                text1.setText(character.name);

                                GameSeries gameSeries = character.getGameSeries();
                                text2.setText(null == gameSeries ? "" : gameSeries.name);

                                return view;
                            }
                        }, null)
                        .setPositiveButton(R.string.close, null)
                        .show();
            });

            amiiboTypeStats.setOnClickListener(view1 -> {
                final ArrayList<AmiiboType> amiiboTypes =
                        new ArrayList<>(amiiboManager.amiiboTypes.values());
                Collections.sort(amiiboTypes);

                final ArrayList<String> items = new ArrayList<>();
                for (AmiiboType amiiboType : amiiboTypes) {
                    if (!items.contains(amiiboType.name))
                        items.add(amiiboType.name);
                }

                new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.pref_amiibo_types)
                        .setAdapter(new ArrayAdapter<>(this,
                                android.R.layout.simple_list_item_1, items), null)
                        .setPositiveButton(R.string.close, null)
                        .show();
            });
        }

        GamesManager gamesManager = settings.getGamesManager();
        boolean hasGames = null != amiiboManager;
        amiiboTitleStats.setText(getString(R.string.number_titles, hasGames
                ? gamesManager.getGameTitles().size() : 0));

        if (hasGames) {
            amiiboTitleStats.setOnClickListener(view1 -> {
                final Collection<GameTitles> amiiboTitles = gamesManager.getGameTitles();
                final ArrayList<String> items = new ArrayList<>();
                for (GameTitles amiiboTitle : amiiboTitles) {
                    if (!items.contains(amiiboTitle.name))
                        items.add(amiiboTitle.name);
                }
                Collections.sort(items);

                new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.pref_amiibo_titles)
                        .setAdapter(new ArrayAdapter<>(this,
                                android.R.layout.simple_list_item_1, items), null)
                        .setPositiveButton(R.string.close, null)
                        .show();
            });
        }
    }

    private int[] getAdapterStats(AmiiboManager amiiboManager) {
        BrowserAdapter adapter = null != amiibosView
                ? (BrowserAdapter) amiibosView.getAdapter() : null;
        if (null == adapter) return new int[]{0, 0};
        int size = adapter.getItemCount();
        int count = 0;
        for (Amiibo amiibo : amiiboManager.amiibos.values()) {
            for (int x = 0; x < size; x++) {
                if (amiibo.id == adapter.getItemId(x)) {
                    count += 1;
                    break;
                }
            }
        }
        return new int[]{size, count};
    }

    private void setAmiiboStats() {
        statsHandler.removeCallbacksAndMessages(null);
        currentFolderView.post(() -> {
            int size = settings.getAmiiboFiles().size();
            if (size <= 0) return;
            currentFolderView.setGravity(Gravity.CENTER);
            AmiiboManager amiiboManager = settings.getAmiiboManager();
            if (null != amiiboManager) {
                int count = 0;
                if (!TextUtils.isEmpty(settings.getQuery())) {
                    int[] stats = getAdapterStats(amiiboManager);
                    currentFolderView.setText(getString(R.string.amiibo_collected,
                            stats[0], stats[1], getQueryCount(settings.getQuery())));
                } else if (!settings.isFilterEmpty()) {
                    int[] stats = getAdapterStats(amiiboManager);
                    currentFolderView.setText(getString(R.string.amiibo_collected,
                            stats[0], stats[1], filteredCount));
                } else {
                    for (Amiibo amiibo : amiiboManager.amiibos.values()) {
                        for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
                            if (amiibo.id == amiiboFile.getId()) {
                                count += 1;
                                break;
                            }
                        }
                    }
                    currentFolderView.setText(getString(R.string.amiibo_collected,
                            size, count, amiiboManager.amiibos.size()));
                }
            } else {
                currentFolderView.setText(getString(R.string.files_displayed, size));
            }
        });
    }

    private void setFolderText(BrowserSettings textSettings) {
        if (null != textSettings) {
            String relativePath;
            if (isDocumentStorage()) {
                relativePath = Storage.getRelativeDocument(textSettings.getBrowserRootDocument());
            } else {
                File rootFolder = textSettings.getBrowserRootFolder();
                String relativeRoot = Storage.getRelativePath(
                        rootFolder, prefs.preferEmulated()
                );
                relativePath = relativeRoot.length() > 1
                        ? relativeRoot : rootFolder.getAbsolutePath();
            }
            this.currentFolderView.setGravity(Gravity.CENTER_VERTICAL);
            this.currentFolderView.setText(relativePath);
            statsHandler.postDelayed(this::setAmiiboStats, 3000);
        } else {
            setAmiiboStats();
        }
    }

    private void showFakeSnackbar(String msg) {
        fakeSnackbar.post(() -> {
            fakeSnackbarItem.setVisibility(View.INVISIBLE);
            fakeSnackbarText.setText(msg);
            fakeSnackbar.setVisibility(View.VISIBLE);
        });
    }

    private void hideFakeSnackbar() {
        if (fakeSnackbar.getVisibility() == View.VISIBLE) {
            TranslateAnimation animate = new TranslateAnimation(
                    0, 0, 0, -fakeSnackbar.getHeight());
            animate.setDuration(150);
            animate.setFillAfter(false);
            fakeSnackbar.setAnimationListener(new AnimatedLinearLayout.AnimationListener() {
                @Override
                public void onAnimationStart(AnimatedLinearLayout layout) { }

                @Override
                public void onAnimationEnd(AnimatedLinearLayout layout) {
                    fakeSnackbar.clearAnimation();
                    layout.setAnimationListener(null);
                    fakeSnackbar.setVisibility(View.GONE);
                }
            });
            fakeSnackbar.startAnimation(animate);
        }
    }

    public BottomSheetBehavior<View> getBottomSheetBehavior() {
        return bottomSheetBehavior;
    }

    public void collapseBottomSheet() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    private void hideBottomSheet() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        sheetHandler.postDelayed(() -> {
            bottomSheetBehavior.setHideable(true);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }, TagMo.uiDelay);
    }

    private void hideBrowserInterface() {
        CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) nfcFab.getLayoutParams();
        FloatingActionButton.Behavior behavior =
                (FloatingActionButton.Behavior) params.getBehavior();
        if (null != behavior) behavior.setAutoHideEnabled(false);
        nfcFab.hide();
        hideBottomSheet();
    }

    private void showActionButton() {
        nfcFab.show();
        CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) nfcFab.getLayoutParams();
        FloatingActionButton.Behavior behavior =
                (FloatingActionButton.Behavior) params.getBehavior();
        if (null != behavior) behavior.setAutoHideEnabled(true);
    }

    private void showBrowserInterface() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        sheetHandler.postDelayed(() ->
                bottomSheetBehavior.setHideable(false), TagMo.uiDelay);
        showActionButton();
    }

    public boolean closePrefsDrawer() {
        if (prefsDrawer.isDrawerOpen(GravityCompat.START)) {
            prefsDrawer.closeDrawer(GravityCompat.START);
            return true;
        }
        return false;
    }

    public void showSettingsPage() {
        if (BuildConfig.WEAR_OS) {
            mainLayout.post(() -> mainLayout.setCurrentItem(
                    prefs.flask_support() ? 2 : 1, false
            ));
        } else {
            prefsDrawer.openDrawer(GravityCompat.START);
        }
    }

    public void showElitePage(Bundle extras) {
        mainLayout.post(() -> {
            fragmentElite.setArguments(extras);
            mainLayout.setCurrentItem(1, true);
        });
    }

    public void showWebsite(String address) {
        mainLayout.setCurrentItem(pagerAdapter.getItemCount() - 1, true);
        pagerAdapter.getWebsite().loadWebsite(address);
    }

    public ViewPager2 getLayout() {
        return this.mainLayout;
    }

    public BrowserSettings getSettings() {
        return this.settings;
    }

    private boolean keyNameMatcher(String name) {
        boolean isValid = AmiiboManager.binFileMatcher(name);
        return name.toLowerCase(Locale.ROOT).endsWith("retail.bin") ||
                (isValid && (name.toLowerCase(Locale.ROOT).startsWith("locked")
                        || name.toLowerCase(Locale.ROOT).startsWith("unfixed")));
    }

    public void locateKeyFilesRecursive(File rootFolder) {
        Executors.newSingleThreadExecutor().execute(() -> {
            File[] files = rootFolder.listFiles((dir, name) -> keyNameMatcher(name));
            if (null != files && files.length > 0) {
                for (File file : files) {
                    try (FileInputStream inputStream = new FileInputStream(file)) {
                        this.keyManager.evaluateKey(inputStream);
                        hideFakeSnackbar();
                    } catch (Exception e) {
                        Debug.Warn(e);
                    }
                }
            } else {
                File[] directories = rootFolder.listFiles();
                if (directories == null || directories.length == 0) return;
                for (File directory : directories) {
                    if (directory.isDirectory()) locateKeyFilesRecursive(directory);
                }
            }
        });
    }

    public void locateKeyFiles() {
        Executors.newSingleThreadExecutor().execute(() -> {
            File[] files = Storage.getDownloadDir(null)
                    .listFiles((dir, name) -> keyNameMatcher(name));
            if (null != files && files.length > 0) {
                for (File file : files) {
                    try (FileInputStream inputStream = new FileInputStream(file)) {
                        this.keyManager.evaluateKey(inputStream);
                        hideFakeSnackbar();
                    } catch (Exception e) {
                        Debug.Warn(e);
                    }
                }
            } else {
                locateKeyFilesRecursive(Storage.getFile(prefs.preferEmulated()));
            }
        });
    }

    private static final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    ActivityResultLauncher<String[]> onRequestStorage = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
        boolean isStorageEnabled = true;
        if (BuildConfig.WEAR_OS) {
            isStorageEnabled = Boolean.TRUE.equals(permissions.get(
                    Manifest.permission.READ_EXTERNAL_STORAGE
            ));
        } else {
            for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
                if (!entry.getValue()) isStorageEnabled = false;
            }
        }
        if (isStorageEnabled)
            this.onStorageEnabled();
        else
            this.onDocumentEnabled();
    });

    @RequiresApi(api = Build.VERSION_CODES.R)
    ActivityResultLauncher<Intent> onRequestScopedStorage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (Environment.isExternalStorageManager()) {
            settings.setBrowserRootDocument(null);
            settings.notifyChanges();
            this.onStorageEnabled();
        } else {
            this.onDocumentEnabled();
        }
    });

    @RequiresApi(api = Build.VERSION_CODES.R)
    void requestScopedStorage() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            try {
                intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
            } catch (Exception e) {
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            }
            onRequestScopedStorage.launch(intent);
        } catch (ActivityNotFoundException anf) {
            this.onDocumentEnabled();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    final ActivityResultLauncher<Intent> onRequestInstall = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (getPackageManager().canRequestPackageInstalls())
            updates.installUpdateTask(prefs.downloadUrl());
        prefs.remove(prefs.downloadUrl);
    });

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())
                || NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            if (keyManager.isKeyMissing()) return;
            Executors.newSingleThreadExecutor().execute(() ->
                    tagScanner.onTagDiscovered(BrowserActivity.this, intent));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (BuildConfig.WEAR_OS) {
                    if (null != bottomSheet && BottomSheetBehavior
                            .STATE_EXPANDED == bottomSheet.getState()) {
                        bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    } else if (View.VISIBLE == amiiboContainer.getVisibility()) {
                        amiiboContainer.setVisibility(View.GONE);
                    } else if (mainLayout.getCurrentItem() != 0) {
                        mainLayout.setCurrentItem(0, true);
                    } else {
                        finishAffinity();
                    }
                } else {
                    if (!closePrefsDrawer()) {
                        if (null != bottomSheet && BottomSheetBehavior
                                .STATE_EXPANDED == bottomSheet.getState()) {
                            bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        } else if (View.VISIBLE == amiiboContainer.getVisibility()) {
                            amiiboContainer.setVisibility(View.GONE);
                        } else if (mainLayout.getCurrentItem() != 0) {
                            mainLayout.setCurrentItem(0, true);
                        } else {
                            finishAffinity();
                        }
                    }
                }
            }
        });
    }
}
