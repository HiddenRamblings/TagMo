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
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import android.os.Message;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.TranslateAnimation;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchaseHistoryRecord;
import com.android.billingclient.api.PurchaseHistoryResponseListener;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchaseHistoryParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.eightbitlab.blurview.BlurView;
import com.eightbitlab.blurview.BlurViewFacade;
import com.eightbitlab.blurview.RenderScriptBlur;
import com.eightbitlab.blurview.SupportRenderScriptBlur;
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
import com.hiddenramblings.tagmo.browser.adapter.BrowserAdapter;
import com.hiddenramblings.tagmo.browser.adapter.FoldersAdapter;
import com.hiddenramblings.tagmo.browser.adapter.FoomiiboAdapter;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.eightbit.view.AnimatedLinearLayout;
import com.hiddenramblings.tagmo.hexcode.HexCodeViewer;
import com.hiddenramblings.tagmo.nfctech.NTAG215;
import com.hiddenramblings.tagmo.nfctech.TagReader;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.nfctech.TagWriter;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.BrowserSettings.BrowserSettingsListener;
import com.hiddenramblings.tagmo.settings.BrowserSettings.FILTER;
import com.hiddenramblings.tagmo.settings.BrowserSettings.SORT;
import com.hiddenramblings.tagmo.settings.BrowserSettings.VIEW;
import com.hiddenramblings.tagmo.settings.Preferences_;
import com.hiddenramblings.tagmo.settings.SettingsFragment;
import com.hiddenramblings.tagmo.widget.Toasty;
import com.wajahatkarim3.easyflipviewpager.CardFlipPageTransformer2;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Executors;

import myinnos.indexfastscrollrecycler.IndexFastScrollRecyclerView;

public class BrowserActivity extends AppCompatActivity implements
        BrowserSettingsListener,
        BrowserAdapter.OnAmiiboClickListener {

    private Preferences_ prefs;
    private KeyManager keyManager;
    private int filteredCount;
    private AmiiboFile clickedAmiibo = null;

    private BrowserSettings settings;
    private boolean ignoreTagId;
    private CheckUpdatesTask updates;
    private String updateUrl;
    private AppUpdateInfo appUpdate;

    private SettingsFragment fragmentSettings;
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private JoyConFragment fragmentJoyCon;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private TextView currentFolderView;
    private DrawerLayout prefsDrawer;
    private AppCompatButton switchStorageRoot;
    private AppCompatButton switchStorageType;

    private AnimatedLinearLayout fakeSnackbar;
    private TextView fakeSnackbarText;
    private AppCompatButton fakeSnackbarItem;
    private ViewPager2 mainLayout;
    private FloatingActionButton nfcFab;
    private RecyclerView amiibosView;
    private RecyclerView foomiiboView;
    private BottomSheetBehavior<View> bottomSheet;
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
    private MenuItem menuHideDownloads;

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

    private final Handler handler = new Handler(Looper.getMainLooper());
    NavPagerAdapter pagerAdapter = new NavPagerAdapter(this);

    private BillingClient billingClient;
    private final ArrayList<ProductDetails> iapSkuDetails = new ArrayList<>();
    private final ArrayList<ProductDetails> subSkuDetails = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = TagMo.getPrefs();
        setTheme(R.style.AppTheme);
        keyManager = new KeyManager(this);

        if (null != getSupportActionBar()) {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_baseline_menu_24);
        }

        setLoadCompleted();

        setContentView(R.layout.activity_browser);

        fakeSnackbar = findViewById(R.id.fake_snackbar);
        fakeSnackbarText = findViewById(R.id.snackbar_text);
        fakeSnackbarItem = findViewById(R.id.snackbar_item);
        mainLayout = findViewById(R.id.amiibo_pager);
        nfcFab = findViewById(R.id.nfc_fab);
        currentFolderView = findViewById(R.id.current_folder);
        prefsDrawer = findViewById(R.id.drawer_layout);
        switchStorageRoot = findViewById(R.id.switch_storage_root);
        switchStorageType = findViewById(R.id.switch_storage_type);
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

        mainLayout.setAdapter(pagerAdapter);
        CardFlipPageTransformer2 cardFlipPageTransformer = new CardFlipPageTransformer2();
        cardFlipPageTransformer.setScalable(true);
        mainLayout.setPageTransformer(cardFlipPageTransformer);
        setViewPagerSensitivity(mainLayout, 4);
        fragmentBrowser = pagerAdapter.getBrowser();
        fragmentElite = pagerAdapter.getEliteBanks();

        amiibosView = fragmentBrowser.getAmiibosView();
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
                switch (position) {
                    case 1:
                        showActionButton();
                        hideBottomSheet();
                        setTitle(R.string.elite_n2);
                        amiibosView = fragmentElite.getAmiibosView();
                        bottomSheet = fragmentElite.getBottomSheet();
                        break;
                    case 2:
                        hideBrowserInterface();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                            setTitle(R.string.flask_ble);
                            FlaskSlotFragment fragmentFlask = pagerAdapter.getFlaskSlots();
                            fragmentFlask.delayedBluetoothEnable();
                            amiibosView = fragmentFlask.getAmiibosView();
                            bottomSheet = fragmentFlask.getBottomSheet();
                        } else {
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
                        amiibosView = fragmentBrowser.getAmiibosView();
                        foomiiboView = fragmentBrowser.getFoomiiboView();
                        bottomSheet = bottomSheetBehavior;
                        if (null == foomiiboView) break;
                        foomiiboView.setLayoutManager(settings.getAmiiboView()
                                == BrowserSettings.VIEW.IMAGE.getValue()
                                ? new GridLayoutManager(BrowserActivity.this, getColumnCount())
                                : new LinearLayoutManager(BrowserActivity.this));
                        break;
                }
                if (null != amiibosView) {
                    amiibosView.setLayoutManager(settings.getAmiiboView()
                            == BrowserSettings.VIEW.IMAGE.getValue()
                            ? new GridLayoutManager(BrowserActivity.this, getColumnCount())
                            : new LinearLayoutManager(BrowserActivity.this));
                }
                invalidateOptionsMenu();
            }
        });

        new TabLayoutMediator(findViewById(R.id.navigation_tabs), mainLayout, (tab, position) -> {
            switch (position) {
                case 1:
                    tab.setText(R.string.elite_n2);
                    break;
                case 2:
                    tab.setText(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
                            ? R.string.flask_ble : R.string.guides);
                    break;
                case 3:
                    tab.setText(R.string.guides);
                    break;
                default:
                    tab.setText(R.string.browser);
                    break;
            }
        }).attach();

        onLoadSettingsFragment();

        CoordinatorLayout coordinator = findViewById(R.id.coordinator);
        BlurViewFacade blurView = amiiboContainer.setupWith(coordinator)
                .setFrameClearDrawable(getWindow().getDecorView().getBackground())
                .setBlurRadius(2f).setBlurAutoUpdate(true)
                .setHasFixedTransformationMatrix(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            blurView.setBlurAlgorithm(new RenderScriptBlur(this));
        else
            blurView.setBlurAlgorithm(new SupportRenderScriptBlur(this));

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
        foomiiboOptions.findViewById(R.id.clear_foomiibo_set).setOnClickListener(
                clickView -> fragmentBrowser.clearFoomiiboSet()
        );

        foomiiboOptions.findViewById(R.id.build_foomiibo_set).setOnClickListener(
                clickView -> fragmentBrowser.buildFoomiiboSet()
        );

        if (prefs.hasAcceptedTOS().get()) {
            requestStoragePermission();
        } else {
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
                            requestStoragePermission();
                        })
                        .show().getWindow()
                        .setLayout(LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
            } catch (Exception e) {
                Debug.Log(e);
            }
        }

        RecyclerView foldersView = findViewById(R.id.folders_list);
        foldersView.setLayoutManager(new LinearLayoutManager(this));
        foldersView.setAdapter(new FoldersAdapter(settings));
        this.settings.addChangeListener((BrowserSettingsListener) foldersView.getAdapter());

        this.loadPTagKeyManager();

        PopupMenu popup = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
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
            Debug.Log(e);
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

        if (TagMo.isCompatBuild()) retrieveDonationMenu();

        ((TextView) findViewById(R.id.build_text)).setText(getString(
                R.string.build_details, getBuildTypeName(), BuildConfig.COMMIT
        ));
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

        try {
            getPackageManager().getPackageInfo(
                    "com.hiddenramblings.tagmo", PackageManager.GET_META_DATA
            );
            this.runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle(R.string.conversion_title)
                    .setMessage(R.string.conversion_message)
                    .setPositiveButton(R.string.proceed, (dialogInterface, i) ->
                            startActivity(new Intent(Intent.ACTION_DELETE).setData(
                                    Uri.parse("package:com.hiddenramblings.tagmo")
                            ))
                    ).show());
        } catch (PackageManager.NameNotFoundException ignored) { }
    }

    private void onLoadSettingsFragment() {
        if (null == fragmentSettings) fragmentSettings = new SettingsFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.preferences, fragmentSettings)
                .commit();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void onShowJoyConFragment() {
        if (null == fragmentJoyCon) fragmentJoyCon = new JoyConFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.preferences, fragmentJoyCon)
                .commit();
        prefsDrawer.openDrawer(GravityCompat.START);
        fragmentJoyCon.delayedBluetoothEnable();
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (TagMo.isGooglePlay()) {
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
        } else {
            onRequestStorage.launch(PERMISSIONS_STORAGE);
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
            showEliteWindow(result.getData().getExtras());
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
                mainLayout, false);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        final EditText input = view.findViewById(R.id.backup_entry);
        input.setText(TagUtils.decipherFilename(settings.getAmiiboManager(), tagData, true));
        Dialog backupDialog = dialog.setView(view).create();
        view.findViewById(R.id.save_backup).setOnClickListener(v -> {
            try {
                String fileName;
                if (isDocumentStorage()) {
                    DocumentFile rootDocument = DocumentFile.fromTreeUri(this,
                            this.settings.getBrowserRootDocument());
                    if (null == rootDocument) throw new NullPointerException();
                    fileName = TagUtils.writeBytesToDocument(this, rootDocument,
                            input.getText().toString() + ".bin", tagData);
                } else {
                    fileName = TagUtils.writeBytesToFile(
                            Storage.getDownloadDir("TagMo", "Backups"),
                            input.getText().toString() + ".bin", tagData);
                }
                new IconifiedSnackbar(this, mainLayout).buildSnackbar(
                        getString(R.string.wrote_file, fileName), Snackbar.LENGTH_SHORT
                ).show();
                this.onRootFolderChanged(false);
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
            TagUtils.validateData(result.getData().getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA));
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
        MenuItem flaskItem = popup.getMenu().findItem(R.id.mnu_flask);

        scanItem.setEnabled(false);
        backupItem.setEnabled(false);
        validateItem.setEnabled(false);
        flaskItem.setEnabled(false);
        flaskItem.setVisible(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2);

        popup.show();
        Handler popupHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                popup.getMenu().findItem(msg.what).setEnabled(true);
            }
        };
        popupHandler.postDelayed(() -> {
            int baseDelay = 0;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                baseDelay = 75;
                popupHandler.sendEmptyMessageDelayed(R.id.mnu_flask,  baseDelay);
            }
            popupHandler.sendEmptyMessageDelayed(R.id.mnu_validate, 75 + baseDelay);
            popupHandler.sendEmptyMessageDelayed(R.id.mnu_backup, 175 + baseDelay);
            popupHandler.sendEmptyMessageDelayed(R.id.mnu_scan, 275 + baseDelay);
        }, 325);

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.mnu_scan) {
                onNFCActivity.launch(new Intent(this,
                        NfcActivity.class).setAction(NFCIntent.ACTION_SCAN_TAG));
                return true;
            } else if (item.getItemId() == R.id.mnu_flask) {
                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                CustomTabsIntent customTabsIntent = builder.build();
                builder.setCloseButtonIcon(BitmapFactory.decodeResource(getResources(),
                        R.drawable.ic_stat_notice_24dp));
                customTabsIntent.launchUrl(this, Uri.parse("https://flask.run/"));
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

    private void onSendDonationClicked() {
        if (TagMo.isCompatBuild()) {
            LinearLayout layout = (LinearLayout) getLayoutInflater()
                    .inflate(R.layout.donation_layout, null);
            AlertDialog.Builder dialog = new AlertDialog.Builder(new ContextThemeWrapper(
                    BrowserActivity.this, R.style.DialogTheme_NoActionBar
            ));
            LinearLayout donations = layout.findViewById(R.id.donation_layout);
            Collections.sort(iapSkuDetails, (obj1, obj2) ->
                    obj1.getProductId().compareToIgnoreCase(obj2.getProductId()));
            for (ProductDetails skuDetail : iapSkuDetails) {
                if (null == skuDetail.getOneTimePurchaseOfferDetails()) continue;
                donations.addView(getDonationButton(skuDetail));
            }
            LinearLayout subscriptions = layout.findViewById(R.id.subscription_layout);
            Collections.sort(subSkuDetails, (obj1, obj2) ->
                    obj1.getProductId().compareToIgnoreCase(obj2.getProductId()));
            for (ProductDetails skuDetail : subSkuDetails) {
                if (null == skuDetail.getSubscriptionOfferDetails()) continue;
                subscriptions.addView(getSubscriptionButton(skuDetail));
            }
            dialog.setOnCancelListener(dialogInterface -> {
                donations.removeAllViewsInLayout();
                subscriptions.removeAllViewsInLayout();
            });
            Dialog donateDialog = dialog.setView(layout).show();
            if (!TagMo.isGooglePlay()) {
                @SuppressLint("InflateParams")
                View paypal = getLayoutInflater().inflate(R.layout.button_paypal, null);
                paypal.setOnClickListener(view -> {
                    closePrefsDrawer();
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://www.paypal.com/donate/?hosted_button_id=Q2LFH2SC8RHRN"
                    )));
                    donateDialog.cancel();
                });
                layout.addView(paypal);
            }
            donateDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        } else {
            closePrefsDrawer();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://www.paypal.com/donate/?hosted_button_id=Q2LFH2SC8RHRN"
            )));
        }
    }

    public void setFoomiiboPanelVisibility() {
        fragmentBrowser.setFoomiiboVisibility();
    }

    private void onShowDonationNotice() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Snackbar donorNotice = new IconifiedSnackbar(
                    BrowserActivity.this, mainLayout
            ).buildSnackbar(
                    R.string.donation_notice,
                    R.drawable.ic_github_octocat_24dp, Snackbar.LENGTH_LONG
            );
            donorNotice.setAction(R.string.donate, v -> onSendDonationClicked());
            donorNotice.show();
        }, TagMo.uiDelay);
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

    private final MenuItem.OnMenuItemClickListener onFilterGameSeriesItemClick =
            new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setFilter(FILTER.GAME_SERIES, menuItem.getTitle().toString());
            settings.notifyChanges();
            filteredCount = getFilteredCount(menuItem.getTitle().toString(), FILTER.GAME_SERIES);
            return false;
        }
    };

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
                TagUtils.amiiboIdFromTag(tagData);
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
                        TagMo.getPrefs().preferEmulated().get()).replace(
                        TagMo.getPrefs().browserRootFolder().get(), "");
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
                input.setText(TagUtils.decipherFilename(settings.getAmiiboManager(), tagData, true));
                Dialog backupDialog = dialog.setView(view).create();
                view.findViewById(R.id.save_backup).setOnClickListener(v -> {
                    try {
                        String fileName;
                        if (isDocumentStorage()) {
                            DocumentFile rootDocument = DocumentFile.fromTreeUri(this,
                                    this.settings.getBrowserRootDocument());
                            if (null == rootDocument) throw new NullPointerException();
                            fileName = TagUtils.writeBytesToDocument(this, rootDocument,
                                    input.getText().toString() + ".bin", tagData);
                        } else {
                            fileName = TagUtils.writeBytesToFile(
                                    Storage.getDownloadDir("TagMo", "Backups"),
                                    input.getText().toString(), tagData);
                        }
                        new IconifiedSnackbar(this, mainLayout).buildSnackbar(
                                getString(R.string.wrote_file, fileName), Snackbar.LENGTH_SHORT
                        ).show();
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
                    TagUtils.validateData(tagData);
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

    void getToolbarOptions(Toolbar toolbar, byte[] tagData, View itemView) {
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
                fragmentBrowser.onUpdateTagResult.launch(scan.putExtras(args));
                return true;
            } else if (item.getItemId() == R.id.mnu_update) {
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData);
                scan.setAction(NFCIntent.ACTION_WRITE_TAG_DATA);
                scan.putExtra(NFCIntent.EXTRA_IGNORE_TAG_ID, ignoreTagId);
                fragmentBrowser.onUpdateTagResult.launch(scan.putExtras(args));
                return true;
            } else if (item.getItemId() == R.id.mnu_save) {
                fragmentBrowser.buildFoomiiboFile(tagData);
                itemView.callOnClick();
                onRefresh(false);
                return true;
            } else if (item.getItemId() == R.id.mnu_edit) {
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData);
                Intent tagEdit = new Intent(this, TagDataEditor.class);
                fragmentBrowser.onUpdateTagResult.launch(tagEdit.putExtras(args));
                return true;
            } else if (item.getItemId() == R.id.mnu_view_hex) {
                Intent hexView = new Intent(this, HexCodeViewer.class);
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
                fragmentBrowser.deleteFoomiiboFile(tagData);
                itemView.callOnClick();
                onRefresh(false);
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
                    long amiiboId = TagUtils.amiiboIdFromTag(tagData);
                    String usage = gamesManager.getGamesCompatibility(amiiboId);
                    txtUsage.post(() -> txtUsage.setText(usage));
                } catch (Exception ex) {
                    Debug.Log(ex);
                }
            });
        } else {
            label.setVisibility(View.GONE);
        }
    }

    public void onRefresh(boolean indicator) {
        this.loadAmiiboManager();
        this.onRootFolderChanged(indicator);
    }

    public boolean isDocumentStorage() {
        if (null != this.settings.getBrowserRootDocument()) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
            } catch (ActivityNotFoundException anfex) {
                new Toasty(this).Long(R.string.storage_unavailable);
                finish();
            }
        }
    }

    private void onStorageEnabled() {
        if (isDocumentStorage()) {
            switchStorageRoot.setVisibility(View.VISIBLE);
            switchStorageRoot.setText(R.string.document_storage_root);
            switchStorageRoot.setOnClickListener(view -> {
                try {
                    onDocumentRequested();
                } catch (ActivityNotFoundException ignored) { }
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            });
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !TagMo.isGooglePlay()) {
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
                verifyKeyFiles();
            } else {
                this.onRefresh(true);
            }
        } else {
            boolean internal = prefs.preferEmulated().get();
            if (Storage.getFile(internal).exists() && Storage.hasPhysicalStorage()) {
                switchStorageRoot.setVisibility(View.VISIBLE);
                switchStorageRoot.setText(internal
                        ? R.string.emulated_storage_root
                        : R.string.physical_storage_root);
                switchStorageRoot.setOnClickListener(view -> {
                    boolean external = !prefs.preferEmulated().get();
                    switchStorageRoot.setText(external
                            ? R.string.emulated_storage_root
                            : R.string.physical_storage_root);
                    this.settings.setBrowserRootFolder(Storage.getFile(external));
                    this.settings.notifyChanges();
                    prefs.preferEmulated().put(external);
                });
            } else {
                switchStorageRoot.setVisibility(View.GONE);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                switchStorageType.setVisibility(View.VISIBLE);
                switchStorageType.setText(R.string.force_document_storage);
                switchStorageType.setOnClickListener(view -> {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    try {
                        onDocumentRequested();
                    } catch (ActivityNotFoundException anfex) {
                        new Toasty(this).Long(R.string.storage_unavailable);
                        finish();
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
        menuFilterGameTitles = menu.findItem(R.id.filter_game_titles);
        menuViewSimple = menu.findItem(R.id.view_simple);
        menuViewCompact = menu.findItem(R.id.view_compact);
        menuViewLarge = menu.findItem(R.id.view_large);
        menuViewImage = menu.findItem(R.id.view_image);
        menuRecursiveFiles = menu.findItem(R.id.recursive);
        menuHideDownloads = menu.findItem(R.id.hide_downloads);

        menu.findItem(R.id.connect_joy_con).setVisible(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
        );

        if (null == this.settings) return false;

        this.onSortChanged();
        this.onViewChanged();
        this.onRecursiveFilesChanged();

        if (isDocumentStorage()) {
            menuHideDownloads.setVisible(false);
        } else {
            menuHideDownloads.setVisible(true);
            menuHideDownloads.setTitle(getString(
                    R.string.hide_downloads, Storage.getDownloadDir(null).getName()
            ));
            this.onHideDownloadsChanged();
        }

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
        if (!query.isEmpty()) {
            menuSearch.expandActionView();
            searchView.setQuery(query, true);
            searchView.clearFocus();
        }

        menuUpdate.setVisible(null != appUpdate || null != updateUrl);

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
        } else if (item.getItemId() == R.id.refresh) {
            onRefresh(true);
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
            amiibosView.setLayoutManager(new LinearLayoutManager(this));
            foomiiboView.setLayoutManager(new LinearLayoutManager(this));
            settings.setAmiiboView(VIEW.SIMPLE.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.view_compact) {
            amiibosView.setLayoutManager(new LinearLayoutManager(this));
            foomiiboView.setLayoutManager(new LinearLayoutManager(this));
            settings.setAmiiboView(VIEW.COMPACT.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.view_large) {
            amiibosView.setLayoutManager(new LinearLayoutManager(this));
            foomiiboView.setLayoutManager(new LinearLayoutManager(this));
            settings.setAmiiboView(VIEW.LARGE.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.view_image) {
            amiibosView.setLayoutManager(new GridLayoutManager(this, getColumnCount()));
            foomiiboView.setLayoutManager(new GridLayoutManager(this, getColumnCount()));
            settings.setAmiiboView(VIEW.IMAGE.getValue());
            settings.notifyChanges();
        } else if (item.getItemId() == R.id.recursive) {
            this.settings.setRecursiveEnabled(!this.settings.isRecursiveEnabled());
            this.settings.notifyChanges();
        } else if (item.getItemId() == R.id.hide_downloads) {
            this.settings.setHideDownloads(!this.settings.isHidingDownloads());
            this.settings.notifyChanges();
        } else if (item.getItemId() == R.id.connect_joy_con
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            onShowJoyConFragment();
        } else if (item.getItemId() == R.id.capture_logcat) {
            onCaptureLogcatClicked();
        } else if (item.getItemId() == R.id.send_donation) {
            onSendDonationClicked();
        } else if (item.getItemId() == R.id.filter_game_series) {
            return onFilterGameSeriesClick();
        } else if (item.getItemId() == R.id.filter_character) {
            return onFilterCharacterClick();
        } else if (item.getItemId() == R.id.filter_amiibo_series) {
            return onFilterAmiiboSeriesClick();
        } else if (item.getItemId() == R.id.filter_amiibo_type) {
            return onFilterAmiiboTypeClick();
        } else if (item.getItemId() == R.id.filter_game_titles) {
            onFilterGameTitlesClick();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onAmiiboClicked(View itemView, AmiiboFile amiiboFile) {
        if (null == amiiboFile.getDocUri() && null == amiiboFile.getFilePath())
            return;
        try {
            byte[] tagData = null != amiiboFile.getData() ? amiiboFile.getData()
                    : null != amiiboFile.getDocUri()
                    ? TagUtils.getValidatedDocument(keyManager, amiiboFile.getDocUri())
                    : TagUtils.getValidatedFile(keyManager, amiiboFile.getFilePath());

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
            Debug.Log(e);
        }
    }

    @Override
    public void onAmiiboRebind(View itemView, AmiiboFile amiiboFile) {
        if (amiiboFile.getFilePath() == null)
            return;
        try {
            byte[] tagData = null != amiiboFile.getData() ? amiiboFile.getData()
                    : TagUtils.getValidatedFile(keyManager, amiiboFile.getFilePath());

            if (settings.getAmiiboView() != VIEW.IMAGE.getValue()) {
                getToolbarOptions(itemView.findViewById(R.id.menu_options)
                        .findViewById(R.id.toolbar), tagData, amiiboFile);
                getGameCompatibility(itemView.findViewById(R.id.txtUsage), tagData);
            } else {
                updateAmiiboView(tagData, amiiboFile);
            }
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

            GamesManager gamesManager;
            try {
                gamesManager = GamesManager.getGamesManager(this);
            } catch (IOException | JSONException | ParseException e) {
                Debug.Log(e);
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
            if (!this.settings.isHidingDownloads()) {
                File download = Storage.getDownloadDir(null);
                if (isDirectoryHidden(rootFolder, download, recursiveFiles))
                    amiiboFiles.addAll(AmiiboManager
                            .listAmiibos(keyManager, download, true));
            }
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

    private void loadAmiiboDocuments(DocumentFile rootFolder, boolean recursiveFiles) {
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

    void loadStoredAmiibo() {
        if (isDocumentStorage()) {
            DocumentFile rootDocument = DocumentFile.fromTreeUri(
                    BrowserActivity.this, settings.getBrowserRootDocument()
            );
            loadAmiiboDocuments(rootDocument, settings.isRecursiveEnabled());
        } else {
            loadAmiiboFiles(settings.getBrowserRootFolder(), settings.isRecursiveEnabled());
        }
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
        if (newBrowserSettings.isHidingDownloads() != oldBrowserSettings.isHidingDownloads()) {
            folderChanged = true;
            onHideDownloadsChanged();
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
        if (!BrowserSettings.equals(newBrowserSettings.getFilter(FILTER.GAME_SERIES),
                oldBrowserSettings.getFilter(FILTER.GAME_SERIES))) {
            onFilterContentsChanged(FILTER.GAME_SERIES);
        }
        if (!BrowserSettings.equals(newBrowserSettings.getFilter(FILTER.CHARACTER),
                oldBrowserSettings.getFilter(FILTER.CHARACTER))) {
            onFilterContentsChanged(FILTER.CHARACTER);
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
            if (TagMo.isGooglePlay()) {
                updates.setPlayUpdateListener(appUpdateInfo -> {
                    appUpdate = appUpdateInfo;
                    invalidateOptionsMenu();
                });
            } else {
                updates.setUpdateListener(downloadUrl -> {
                    updateUrl = downloadUrl;
                    invalidateOptionsMenu();
                });
            }
            newBrowserSettings.setLastUpdatedGit(System.currentTimeMillis());
        }

        prefs.edit().browserRootFolder().put(Storage.getRelativePath(
                newBrowserSettings.getBrowserRootFolder(), prefs.preferEmulated().get()))
                .browserRootDocument().put(null != newBrowserSettings.getBrowserRootDocument()
                        ? newBrowserSettings.getBrowserRootDocument().toString() : null)
                .query().put(newBrowserSettings.getQuery())
                .sort().put(newBrowserSettings.getSort())
                .filterGameSeries().put(newBrowserSettings.getFilter(FILTER.GAME_SERIES))
                .filterCharacter().put(newBrowserSettings.getFilter(FILTER.CHARACTER))
                .filterAmiiboSeries().put(newBrowserSettings.getFilter(FILTER.AMIIBO_SERIES))
                .filterAmiiboType().put(newBrowserSettings.getFilter(FILTER.AMIIBO_TYPE))
                .filterGameTitles().put(newBrowserSettings.getFilter(FILTER.GAME_TITLES))
                .browserAmiiboView().put(newBrowserSettings.getAmiiboView())
                .image_network_settings().put(newBrowserSettings.getImageNetworkSettings())
                .recursiveFolders().put(newBrowserSettings.isRecursiveEnabled())
                .hideDownloads().put(newBrowserSettings.isHidingDownloads())
                .lastUpdatedAPI().put(newBrowserSettings.getLastUpdatedAPI())
                .lastUpdatedGit().put(newBrowserSettings.getLastUpdatedGit())
                .apply();
    }

    private void setIndexFastScrollRecyclerListener(RecyclerView amiibosView) {
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
            case GAME_SERIES:
                menuSortGameSeries.setChecked(true);
                setIndexFastScrollRecyclerListener(amiibosView);
                setIndexFastScrollRecyclerListener(foomiiboView);
                break;
            case CHARACTER:
                menuSortCharacter.setChecked(true);
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
                this.loadAmiiboDocuments(rootDocument, settings.isRecursiveEnabled());
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
            case GAME_SERIES:
                filterTag = getString(R.string.filter_game_series);
                break;
            case CHARACTER:
                filterTag = getString(R.string.filter_character);
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
        onFilterContentsChanged(FILTER.GAME_SERIES);
        onFilterContentsChanged(FILTER.CHARACTER);
        onFilterContentsChanged(FILTER.AMIIBO_SERIES);
        onFilterContentsChanged(FILTER.AMIIBO_TYPE);
        onFilterContentsChanged(FILTER.GAME_TITLES);
    }

    private void onRecursiveFilesChanged() {
        if (null == menuRecursiveFiles) return;
        menuRecursiveFiles.setChecked(settings.isRecursiveEnabled());
    }

    private void onHideDownloadsChanged() {
        if (null == menuHideDownloads) return;
        menuHideDownloads.setChecked(settings.isHidingDownloads());
    }

    private void launchEliteActivity(Intent resultData) {
        if (TagMo.getPrefs().enable_elite_support().get()
                && resultData.hasExtra(NFCIntent.EXTRA_SIGNATURE)) {
            showEliteWindow(resultData.getExtras());
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
            // toolbar.getMenu().findItem(R.id.mnu_write).setEnabled(false);
        }
    });

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

    private void deleteAmiiboFile(AmiiboFile amiiboFile) {
        if (null != amiiboFile && null != amiiboFile.getFilePath()) {
            String relativeFile = Storage.getRelativePath(
                    amiiboFile.getFilePath(), prefs.preferEmulated().get());
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
                amiiboId = TagUtils.amiiboIdFromTag(tagData);
            } catch (Exception e) {
                Debug.Log(e);
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
            } else {
                amiiboHexId = TagUtils.amiiboIdToHex(amiiboId);
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
            Debug.Log(ex);
        }

        if (null != imageAmiibo) {
            imageAmiibo.setVisibility(View.GONE);
            GlideApp.with(this).clear(imageTarget);
            if (null != amiiboImageUrl) {
                GlideApp.with(this).asBitmap().load(amiiboImageUrl).into(imageTarget);
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
        if (amiiboHexId.endsWith("00000002") && !amiiboHexId.startsWith("00000000"))
            txtTagId.setEnabled(false);
    }

    private void updateAmiiboView(byte[] tagData) {
        updateAmiiboView(tagData, clickedAmiibo);
    }

    public int getColumnCount() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
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
        TextView gameSeriesStats = findViewById(R.id.stats_game_series);
        TextView characterStats = findViewById(R.id.stats_character);
        TextView amiiboSeriesStats = findViewById(R.id.stats_amiibo_series);
        TextView amiiboTypeStats = findViewById(R.id.stats_amiibo_type);

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        boolean hasAmiibo = null != amiiboManager;
        if (null != foomiiboSlider) {
            TextView foomiiboStats = foomiiboSlider.findViewById(R.id.divider_text);
            foomiiboStats.setText(getString(R.string.number_foomiibo, hasAmiibo
                    ? amiiboManager.amiibos.size() : 0));
        }
        gameSeriesStats.setText(getString(R.string.number_game, hasAmiibo
                ? amiiboManager.gameSeries.size() : 0));
        characterStats.setText(getString(R.string.number_character, hasAmiibo
                ? amiiboManager.characters.size() : 0));
        amiiboSeriesStats.setText(getString(R.string.number_series, hasAmiibo
                ? amiiboManager.amiiboSeries.size() : 0));
        amiiboTypeStats.setText(getString(R.string.number_type, hasAmiibo
                ? amiiboManager.amiiboTypes.size() : 0));

        if (hasAmiibo) {
            gameSeriesStats.setOnClickListener(view1 -> {
                final ArrayList<String> items = new ArrayList<>();
                for (GameSeries gameSeries : amiiboManager.gameSeries.values()) {
                    if (!items.contains(gameSeries.name))
                        items.add(gameSeries.name);
                }
                Collections.sort(items);

                new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.amiibo_game)
                        .setAdapter(new ArrayAdapter<>(this,
                                android.R.layout.simple_list_item_1, items), null)
                        .setPositiveButton(R.string.close, null)
                        .show();
            });

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

            amiiboSeriesStats.setOnClickListener(view1 -> {
                final ArrayList<String> items = new ArrayList<>();
                for (AmiiboSeries amiiboSeries : amiiboManager.amiiboSeries.values()) {
                    if (!items.contains(amiiboSeries.name))
                        items.add(amiiboSeries.name);
                }
                Collections.sort(items);

                new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.amiibo_series)
                        .setAdapter(new ArrayAdapter<>(this,
                                android.R.layout.simple_list_item_1, items), null)
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
    }

    private int[] getAdapterStats(AmiiboManager amiiboManager) {
        BrowserAdapter adapter = (BrowserAdapter) fragmentBrowser.getAmiibosView().getAdapter();
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
        handler.removeCallbacksAndMessages(null);
        currentFolderView.post(() -> {
            int size = settings.getAmiiboFiles().size();
            if (size <= 0) return;
            currentFolderView.setGravity(Gravity.CENTER);
            AmiiboManager amiiboManager = settings.getAmiiboManager();
            if (null != amiiboManager) {
                int count = 0;
                if (!settings.getQuery().isEmpty()) {
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
                        rootFolder, prefs.preferEmulated().get()
                );
                relativePath = relativeRoot.length() > 1
                        ? relativeRoot : rootFolder.getAbsolutePath();
            }
            this.currentFolderView.setGravity(Gravity.CENTER_VERTICAL);
            this.currentFolderView.setText(relativePath);
            handler.postDelayed(this::setAmiiboStats, 3000);
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
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
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
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                bottomSheetBehavior.setHideable(false), TagMo.uiDelay);
        showActionButton();
    }

    public boolean closePrefsDrawer() {
        if (prefsDrawer.isDrawerOpen(GravityCompat.START)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                if (null != fragmentJoyCon) fragmentJoyCon.disconnectJoyCon();
            }
            prefsDrawer.closeDrawer(GravityCompat.START);
            onLoadSettingsFragment();
            return true;
        }
        return false;
    }

    public void showEliteWindow(Bundle extras) {
        fragmentElite.setArguments(extras);
        mainLayout.setCurrentItem(1, true);
    }

    public void showWebsite(String address) {
        mainLayout.setCurrentItem(pagerAdapter.getItemCount() - 1, true);
        pagerAdapter.getWebsite().loadWebsite(address);
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

    private void locateKeyFilesRecursive(File rootFolder) {
        Executors.newSingleThreadExecutor().execute(() -> {
            File[] files = rootFolder.listFiles();
            if (files == null || files.length == 0)
                return;
            for (File file : files) {
                if (file.isDirectory() && file != Storage.getDownloadDir(null)) {
                    locateKeyFilesRecursive(file);
                } else if (keyNameMatcher(file.getName())) {
                    try (FileInputStream inputStream = new FileInputStream(file)) {
                        this.keyManager.evaluateKey(inputStream);
                        hideFakeSnackbar();
                    } catch (Exception e) {
                        Debug.Log(e);
                    }
                }
            }
        });
    }

    public void verifyKeyFiles() {
        if (keyManager.isKeyMissing()) {
            this.runOnUiThread(() -> Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    Scanner scanner = new Scanner(new URL(
                            "https://pastebin.com/raw/aV23ha3X").openStream());
                    for (int i = 0; i < 4; i++) {
                        if (scanner.hasNextLine()) scanner.nextLine();
                    }
                    this.keyManager.evaluateKey(new ByteArrayInputStream(
                            TagUtils.hexToByteArray(scanner.nextLine()
                                    .replace(" ", ""))));
                    hideFakeSnackbar();
                    scanner.close();
                } catch (IOException e) {
                    Debug.Log(e);
                }
                if (Thread.currentThread().isInterrupted()) return;
                this.onRefresh(true);
            }));
        } else {
            this.onRefresh(true);
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
                        hideFakeSnackbar();
                    } catch (Exception e) {
                        Debug.Log(e);
                    }
                }
            } else {
                locateKeyFilesRecursive(Storage.getFile(prefs.preferEmulated().get()));
            }
            verifyKeyFiles();
        });
    }

    private static final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private void showStoragePrompt() {
        this.runOnUiThread(() -> {
            Snackbar storageBar = Snackbar.make(findViewById(R.id.coordinator),
                    R.string.permission_required, Snackbar.LENGTH_LONG);
            storageBar.setAction(R.string.allow, v -> onRequestStorage.launch(PERMISSIONS_STORAGE));
            storageBar.show();
        });
    }

    ActivityResultLauncher<String[]> onRequestStorage = registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
    permissions -> { boolean isStorageEnabled = true;
        for (Map.Entry<String,Boolean> entry : permissions.entrySet()) {
            if (!entry.getValue()) isStorageEnabled = false;
        }
        if (isStorageEnabled) this.onStorageEnabled(); else showStoragePrompt();
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
        if (!closePrefsDrawer()) {
            if (null != bottomSheet && BottomSheetBehavior
                    .STATE_EXPANDED == bottomSheet.getState()) {
                bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED);
            } else if (View.VISIBLE == amiiboContainer.getVisibility()) {
                amiiboContainer.setVisibility(View.GONE);
            } else if (mainLayout.getCurrentItem() != 0) {
                mainLayout.setCurrentItem(0, true);
            } else {
                super.onBackPressed();
                finishAffinity();
            }
        }
    }

    private boolean hasTestedElite;
    private boolean isEliteDevice;

    @RequiresApi(api = Build.VERSION_CODES.O)
    final ActivityResultLauncher<Intent> onRequestInstall = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (getPackageManager().canRequestPackageInstalls())
            updates.installUpdateTask(prefs.downloadUrl().get());
        prefs.downloadUrl().remove();
    });

    private void closeTagSilently(NTAG215 mifare) {
        if (null != mifare) {
            try {
                mifare.close();
            } catch (Exception ignored) { }
        }
    }

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

                        Bundle args = new Bundle();
                        ArrayList<String> titles = TagReader.readTagTitles(mifare, bank_count);
                        args.putString(NFCIntent.EXTRA_SIGNATURE, signature);
                        args.putInt(NFCIntent.EXTRA_BANK_COUNT, bank_count);
                        args.putInt(NFCIntent.EXTRA_ACTIVE_BANK, active_bank);
                        args.putStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST, titles);
                        showEliteWindow(args);

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
                    NTAG215 finalMifare = mifare;
                    if (e instanceof android.nfc.TagLostException) {
                        new IconifiedSnackbar(this, mainLayout).buildSnackbar(
                                R.string.speed_scan, Snackbar.LENGTH_SHORT
                        ).show();
                        closeTagSilently(finalMifare);
                        return;
                    } else if (getString(R.string.nfc_null_array).equals(error)) {
                        new AlertDialog.Builder(BrowserActivity.this)
                                .setTitle(R.string.possible_lock)
                                .setMessage(R.string.prepare_unlock)
                                .setPositiveButton(R.string.unlock, (dialog, which) -> {
                                    closeTagSilently(finalMifare);
                                    dialog.dismiss();
                                    onNFCActivity.launch(new Intent(
                                            this, NfcActivity.class
                                    ).setAction(NFCIntent.ACTION_UNLOCK_UNIT));
                                })
                                .setNegativeButton(R.string.cancel,  (dialog, which) -> {
                                    closeTagSilently(finalMifare);
                                    dialog.dismiss();
                                }).show();
                        return;
                    } else if (e instanceof NullPointerException
                            && error.contains("nfctech.NTAG215.connect()")) {
                        new AlertDialog.Builder(BrowserActivity.this)
                                .setTitle(R.string.possible_blank)
                                .setMessage(R.string.prepare_blank)
                                .setPositiveButton(R.string.scan, (dialog, which) -> {
                                    dialog.dismiss();
                                    onNFCActivity.launch(new Intent(
                                            this, NfcActivity.class
                                    ).setAction(NFCIntent.ACTION_BLIND_SCAN));
                                })
                                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss()).show();
                    }
                }
                if (null != error) {
                    if (e instanceof NullPointerException
                            && error.contains("nfctech.NTAG215.connect()")) {
                        error = getString(R.string.error_tag_faulty);
                    }
                    new Toasty(this).Short(error);
                } else {
                    new Toasty(this).Short(R.string.error_unknown);
                }
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    private String getBuildTypeName() {
        if (TagMo.isGooglePlay()) {
            return "Google Play";
        } else {
            if (TagMo.isCompatBuild()) {
                if (Objects.equals(BuildConfig.BUILD_TYPE, "debug")) {
                    return "GitHub Testing";
                } else if (Objects.equals(BuildConfig.BUILD_TYPE, "release")) {
                    return "GitHub Release";
                }
            } else {
                return "GitHub Archive";
            }
        }
        return "";
    }

    private String getIAP(int amount) {
        return String.format(Locale.ROOT, "subscription_%02d", amount);
    }

    private String getSub(int amount) {
        return String.format(Locale.ROOT, "monthly_%02d", amount);
    }

    private final ArrayList<String> iapList = new ArrayList<>();
    private final ArrayList<String> subList = new ArrayList<>();

    private final ConsumeResponseListener consumeResponseListener = (billingResult, s)
            -> new IconifiedSnackbar(this).buildTickerBar(R.string.donation_thanks).show();

    private void handlePurchaseIAP(Purchase purchase) {
        ConsumeParams.Builder consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken());
        billingClient.consumeAsync(consumeParams.build(), consumeResponseListener);
    }

    private final AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener = billingResult
            -> new IconifiedSnackbar(this).buildTickerBar(R.string.donation_thanks).show();

    private void handlePurchaseSub(Purchase purchase) {
        AcknowledgePurchaseParams.Builder acknowledgePurchaseParams = AcknowledgePurchaseParams
                .newBuilder().setPurchaseToken(purchase.getPurchaseToken());
        billingClient.acknowledgePurchase(acknowledgePurchaseParams.build(),
                acknowledgePurchaseResponseListener);
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged()) {
                for (String iap : iapList) {
                    if (purchase.getProducts().contains(iap))
                        handlePurchaseIAP(purchase);
                }
                for (String sub : subList) {
                    if (purchase.getProducts().contains(sub))
                        handlePurchaseSub(purchase);
                }
            }
        }
    }

    private final PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, purchases) -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && null != purchases) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        }
    };

    private final ArrayList<String> subsPurchased = new ArrayList<>();

    private final PurchasesResponseListener subsOwnedListener = (billingResult, purchases) -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            for (Purchase purchase : purchases) {
                for (String sku : purchase.getProducts()) {
                    if (subsPurchased.contains(sku)) {
                        break;
                    }
                }
            }
        }
    };

    private final PurchaseHistoryResponseListener subHistoryListener = (billingResult, purchases) -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && null != purchases) {
            for (PurchaseHistoryRecord purchase : purchases)
                subsPurchased.addAll(purchase.getProducts());
            billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS).build(), subsOwnedListener);
        }
    };

    private final PurchaseHistoryResponseListener iapHistoryListener = (billingResult, purchases) -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && null != purchases) {
            for (PurchaseHistoryRecord purchase : purchases) {
                for (String sku : purchase.getProducts()) {
                    if (Integer.parseInt(sku.split("_")[1]) >= 10) {
                        break;
                    }
                }
            }
        }
    };

    private void retrieveDonationMenu() {
        billingClient = BillingClient.newBuilder(this)
                .setListener(purchasesUpdatedListener).enablePendingPurchases().build();

        iapSkuDetails.clear();
        subSkuDetails.clear();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingServiceDisconnected() { }

            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() ==  BillingClient.BillingResponseCode.OK) {
                    iapList.add(getIAP(1));
                    iapList.add(getIAP(5));
                    iapList.add(getIAP(10));
                    iapList.add(getIAP(25));
                    iapList.add(getIAP(50));
                    iapList.add(getIAP(75));
                    iapList.add(getIAP(99));
                    for (String productId : iapList) {
                        QueryProductDetailsParams.Product productList = QueryProductDetailsParams
                                .Product.newBuilder().setProductId(productId)
                                .setProductType(BillingClient.ProductType.INAPP).build();
                        QueryProductDetailsParams.Builder params = QueryProductDetailsParams
                                .newBuilder().setProductList(List.of(productList));
                        billingClient.queryProductDetailsAsync(params.build(),
                                (billingResult1, productDetailsList) -> {
                            iapSkuDetails.addAll(productDetailsList);
                            billingClient.queryPurchaseHistoryAsync(
                                    QueryPurchaseHistoryParams.newBuilder().setProductType(
                                            BillingClient.ProductType.INAPP
                                    ).build(), iapHistoryListener
                            );
                        });

                    }
                }
                subList.add(getSub(1));
                subList.add(getSub(5));
                subList.add(getSub(10));
                subList.add(getSub(25));
                subList.add(getSub(50));
                subList.add(getSub(75));
                subList.add(getSub(99));
                for (String productId : subList) {
                    QueryProductDetailsParams.Product productList = QueryProductDetailsParams
                            .Product.newBuilder().setProductId(productId)
                            .setProductType(BillingClient.ProductType.SUBS).build();
                    QueryProductDetailsParams.Builder params = QueryProductDetailsParams
                            .newBuilder().setProductList(List.of(productList));
                    billingClient.queryProductDetailsAsync(params.build(),
                            (billingResult1, productDetailsList) -> {
                        subSkuDetails.addAll(productDetailsList);
                        billingClient.queryPurchaseHistoryAsync(
                                QueryPurchaseHistoryParams.newBuilder().setProductType(
                                        BillingClient.ProductType.SUBS
                                ).build(), subHistoryListener
                        );
                    });
                }
            }
        });
    }

    @SuppressWarnings("ConstantConditions")
    private Button getDonationButton(ProductDetails skuDetail) {
        Button button = new Button(getApplicationContext());
        button.setBackgroundResource(R.drawable.rounded_view);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    10f,
                    Resources.getSystem().getDisplayMetrics()
            ));
        }
        int padding = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                4f,
                Resources.getSystem().getDisplayMetrics()
        );
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, padding, 0, padding);
        button.setLayoutParams(params);
        button.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        button.setText(getString(R.string.iap_button, skuDetail
                .getOneTimePurchaseOfferDetails().getFormattedPrice()));
        button.setOnClickListener(view1 -> {
            BillingFlowParams.ProductDetailsParams productDetailsParamsList
                    = BillingFlowParams.ProductDetailsParams
                    .newBuilder().setProductDetails(skuDetail).build();
            billingClient.launchBillingFlow(
                    BrowserActivity.this, BillingFlowParams.newBuilder()
                            .setProductDetailsParamsList(List.of(productDetailsParamsList)).build()
            );
        });
        return button;
    }

    @SuppressWarnings("ConstantConditions")
    private Button getSubscriptionButton(ProductDetails skuDetail) {
        Button button = new Button(getApplicationContext());
        button.setBackgroundResource(R.drawable.rounded_view);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    10f,
                    Resources.getSystem().getDisplayMetrics()
            ));
        }
        int padding = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                4f,
                Resources.getSystem().getDisplayMetrics()
        );
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, padding, 0, padding);
        button.setLayoutParams(params);
        button.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        button.setText(getString(R.string.sub_button, skuDetail
                .getSubscriptionOfferDetails().get(0).getPricingPhases()
                .getPricingPhaseList().get(0).getFormattedPrice()));
        button.setOnClickListener(view1 -> {
            BillingFlowParams.ProductDetailsParams productDetailsParamsList
                    = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setOfferToken(skuDetail.getSubscriptionOfferDetails().get(0).getOfferToken())
                    .setProductDetails(skuDetail).build();
            billingClient.launchBillingFlow(
                    BrowserActivity.this, BillingFlowParams.newBuilder()
                            .setProductDetailsParamsList(List.of(productDetailsParamsList)).build()
            );
        });
        return button;
    }

    private void setLoadCompleted() {
        int loadCount = prefs.refreshCount().get();
        if (prefs.refreshCount().get() == 0) onShowDonationNotice();
        prefs.refreshCount().put(loadCount <= 8 ? loadCount + 1 : 0);
    }

    @Override
    protected void onRestart() {
        setLoadCompleted();
        super.onRestart();
    }
}
