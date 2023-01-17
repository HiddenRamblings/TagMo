package com.hiddenramblings.tagmo.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.*
import android.provider.Settings
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.TranslateAnimation
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.*
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.GravityCompat
import androidx.core.view.MenuCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.hiddenramblings.tagmo.*
import com.hiddenramblings.tagmo.NFCIntent.FilterComponent
import com.hiddenramblings.tagmo.amiibo.*
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.Companion.binFileMatches
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.Companion.getAmiiboManager
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.Companion.hasSpoofData
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.Companion.listAmiiboDocuments
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.Companion.listAmiibos
import com.hiddenramblings.tagmo.amiibo.PowerTagManager.powerTagManager
import com.hiddenramblings.tagmo.amiibo.games.GamesManager
import com.hiddenramblings.tagmo.amiibo.games.GamesManager.Companion.getGamesManager
import com.hiddenramblings.tagmo.amiibo.tagdata.TagDataEditor
import com.hiddenramblings.tagmo.browser.BrowserSettings.*
import com.hiddenramblings.tagmo.browser.UpdateManager.CheckPlayUpdateListener
import com.hiddenramblings.tagmo.browser.UpdateManager.CheckUpdateListener
import com.hiddenramblings.tagmo.browser.adapter.BrowserAdapter
import com.hiddenramblings.tagmo.browser.adapter.FoldersAdapter
import com.hiddenramblings.tagmo.browser.adapter.FoomiiboAdapter
import com.hiddenramblings.tagmo.browser.fragment.BrowserFragment
import com.hiddenramblings.tagmo.browser.fragment.EliteBankFragment
import com.hiddenramblings.tagmo.browser.fragment.JoyConFragment.Companion.newInstance
import com.hiddenramblings.tagmo.browser.fragment.SettingsFragment
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.eightbit.view.AnimatedLinearLayout
import com.hiddenramblings.tagmo.hexcode.HexCodeViewer
import com.hiddenramblings.tagmo.nfctech.NfcActivity
import com.hiddenramblings.tagmo.nfctech.ScanTag
import com.hiddenramblings.tagmo.nfctech.TagArray.decipherFilename
import com.hiddenramblings.tagmo.nfctech.TagArray.getValidatedData
import com.hiddenramblings.tagmo.nfctech.TagArray.getValidatedFile
import com.hiddenramblings.tagmo.nfctech.TagArray.validateData
import com.hiddenramblings.tagmo.nfctech.TagArray.writeBytesToDocument
import com.hiddenramblings.tagmo.nfctech.TagArray.writeBytesToFile
import com.hiddenramblings.tagmo.nfctech.TagReader
import com.hiddenramblings.tagmo.qrcode.QRCodeScanner
import com.hiddenramblings.tagmo.widget.Toasty
import com.wajahatkarim3.easyflipviewpager.CardFlipPageTransformer2
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderEffectBlur
import eightbitlab.com.blurview.RenderScriptBlur
import eightbitlab.com.blurview.SupportRenderScriptBlur
import kotlinx.coroutines.*
import org.json.JSONException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.ParseException
import java.util.*
import java.util.concurrent.Executors

class BrowserActivity : AppCompatActivity(), BrowserSettingsListener,
    BrowserAdapter.OnAmiiboClickListener {
    private lateinit var prefs: Preferences
    private lateinit var keyManager: KeyManager
    private var filteredCount = 0
    private var clickedAmiibo: AmiiboFile? = null
    var settings: BrowserSettings? = null
        private set
    private var ignoreTagId = false
    private var updateManager: UpdateManager? = null
    private var updateUrl: String? = null
    private var appUpdate: AppUpdateInfo? = null
    private var fragmentSettings: SettingsFragment? = null
    var bottomSheetBehavior: BottomSheetBehavior<View>? = null
        private set
    private var bottomSheet: BottomSheetBehavior<View>? = null
    private var currentFolderView: TextView? = null
    var reloadTabCollection = false
    private var prefsDrawer: DrawerLayout? = null
    private var switchStorageRoot: AppCompatButton? = null
    private var switchStorageType: AppCompatButton? = null
    private var joyConDialog: Dialog? = null
    private var fakeSnackbar: AnimatedLinearLayout? = null
    private var fakeSnackbarText: TextView? = null
    private var fakeSnackbarItem: AppCompatButton? = null
    var layout: ViewPager2? = null
        private set
    private lateinit var nfcFab: FloatingActionButton
    private var amiibosView: RecyclerView? = null
    private var foomiiboView: RecyclerView? = null
    private var fragmentBrowser: BrowserFragment? = null
    private var fragmentElite: EliteBankFragment? = null
    private var menuSortId: MenuItem? = null
    private var menuSortName: MenuItem? = null
    private var menuSortGameSeries: MenuItem? = null
    private var menuSortCharacter: MenuItem? = null
    private var menuSortAmiiboSeries: MenuItem? = null
    private var menuSortAmiiboType: MenuItem? = null
    private var menuSortFilePath: MenuItem? = null
    private var menuFilterGameSeries: MenuItem? = null
    private var menuFilterCharacter: MenuItem? = null
    private var menuFilterAmiiboSeries: MenuItem? = null
    private var menuFilterAmiiboType: MenuItem? = null
    private var menuFilterGameTitles: MenuItem? = null
    private var menuViewSimple: MenuItem? = null
    private var menuViewCompact: MenuItem? = null
    private var menuViewLarge: MenuItem? = null
    private var menuViewImage: MenuItem? = null
    private var menuRecursiveFiles: MenuItem? = null
    private var amiiboContainer: FrameLayout? = null
    private var toolbar: Toolbar? = null
    private var amiiboInfo: View? = null
    private var txtError: TextView? = null
    private var txtTagId: TextView? = null
    private var txtName: TextView? = null
    private var txtGameSeries: TextView? = null

    // private TextView txtCharacter;
    private var txtAmiiboType: TextView? = null
    private var txtAmiiboSeries: TextView? = null
    private var imageAmiibo: AppCompatImageView? = null
    private val pagerAdapter = NavPagerAdapter(this)
    private val tagScanner = ScanTag()
    private val donationManager = DonationManager(this)
    private val statsHandler = Handler(Looper.getMainLooper())
    private val sheetHandler = Handler(Looper.getMainLooper())

    private val loadingScope = CoroutineScope(Dispatchers.Main + Job())
    private val backgroundScope = CoroutineScope(Dispatchers.IO)
    private val loadingExecutor = Executors.newSingleThreadExecutor()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Preferences(applicationContext)
        if (null != supportActionBar) {
            if (BuildConfig.WEAR_OS) {
                supportActionBar?.hide()
            } else {
                supportActionBar?.setDisplayShowHomeEnabled(true)
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_baseline_menu_24)
            }
        }
        setContentView(R.layout.activity_browser)
        keyManager = KeyManager(this)
        fakeSnackbar = findViewById(R.id.fake_snackbar)
        fakeSnackbarText = findViewById(R.id.snackbar_text)
        fakeSnackbarItem = findViewById(R.id.snackbar_item)
        layout = findViewById(R.id.amiibo_pager)
        nfcFab = findViewById(R.id.nfc_fab)
        currentFolderView = findViewById(R.id.current_folder)
        if (!BuildConfig.WEAR_OS) {
            switchStorageRoot = findViewById(R.id.switch_storage_root)
            switchStorageType = findViewById(R.id.switch_storage_type)
        }
        amiiboContainer = findViewById(R.id.amiiboContainer)
        toolbar = findViewById(R.id.toolbar)
        amiiboInfo = findViewById(R.id.amiiboInfo)
        txtError = findViewById(R.id.txtError)
        txtTagId = findViewById(R.id.txtTagId)
        txtName = findViewById(R.id.txtName)
        txtGameSeries = findViewById(R.id.txtGameSeries)
        // txtCharacter = findViewById(R.id.txtCharacter);
        txtAmiiboType = findViewById(R.id.txtAmiiboType)
        txtAmiiboSeries = findViewById(R.id.txtAmiiboSeries)
        imageAmiibo = findViewById(R.id.imageAmiibo)
        if (Debug.isOlder(Build.VERSION_CODES.M)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (null == settings) settings = BrowserSettings().initialize()
        settings?.addChangeListener(this)
        val intent = intent
        if (null != intent && componentName == FilterComponent) {
            val browser = Intent(this, BrowserActivity::class.java)
            intent.action?.let { browser.action = it}
            intent.extras?.let { browser.putExtras(it) }
            intent.data?.let { browser.data = it }
            startActivity(browser)
        }
        layout?.keepScreenOn = BuildConfig.WEAR_OS
        layout?.adapter = pagerAdapter
        val cardFlipPageTransformer = CardFlipPageTransformer2()
        cardFlipPageTransformer.isScalable = true
        layout?.setPageTransformer(cardFlipPageTransformer)
        setViewPagerSensitivity(layout, 4)
        fragmentBrowser = pagerAdapter.browser
        if (BuildConfig.WEAR_OS) fragmentSettings = pagerAdapter.settings
        fragmentElite = pagerAdapter.eliteBanks
        amiibosView = fragmentBrowser?.browserContent
        foomiiboView = fragmentBrowser?.foomiiboView
        bottomSheet = bottomSheetBehavior
        layout?.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            @SuppressLint("NewApi")
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position != 0) {
                    BrowserAdapter.resetVisible()
                    FoomiiboAdapter.resetVisible()
                }
                val hasFlaskEnabled = prefs.flaskEnabled()
                if (BuildConfig.WEAR_OS) {
                    when (position) {
                        1 -> if (hasFlaskEnabled) {
                            showActionButton()
                            hideBottomSheet()
                            val fragmentFlask = pagerAdapter.flaskSlots
                            fragmentFlask.delayedBluetoothEnable()
                            amiibosView = fragmentFlask.flaskContent
                            bottomSheet = fragmentFlask.bottomSheet
                        } else {
                            hideBrowserInterface()
                        }
                        2, 3 -> hideBrowserInterface()
                        else -> {
                            showBrowserInterface()
                            amiibosView = fragmentBrowser!!.browserContent
                            foomiiboView = fragmentBrowser!!.foomiiboView
                            bottomSheet = bottomSheetBehavior
                            if (null != foomiiboView) {
                                foomiiboView?.layoutManager =
                                    if (settings!!.amiiboView == VIEW.IMAGE.value)
                                        GridLayoutManager(this@BrowserActivity, columnCount)
                                    else LinearLayoutManager(this@BrowserActivity)
                            }
                        }
                    }
                } else {
                    val hasEliteEnabled = prefs.eliteEnabled()
                    when (position) {
                        1 -> if (hasEliteEnabled) {
                            showActionButton()
                            hideBottomSheet()
                            setTitle(R.string.elite_n2)
                            amiibosView = fragmentElite!!.eliteContent
                            bottomSheet = fragmentElite!!.bottomSheet
                        } else if (hasFlaskEnabled) {
                            showActionButton()
                            hideBottomSheet()
                            setTitle(R.string.flask_title)
                            val fragmentFlask = pagerAdapter.flaskSlots
                            fragmentFlask.delayedBluetoothEnable()
                            amiibosView = fragmentFlask.flaskContent
                            bottomSheet = fragmentFlask.bottomSheet
                        } else {
                            hideBrowserInterface()
                            setTitle(R.string.guides)
                        }
                        2 -> if (hasEliteEnabled && hasFlaskEnabled) {
                            showActionButton()
                            hideBottomSheet()
                            setTitle(R.string.flask_title)
                            val fragmentFlask = pagerAdapter.flaskSlots
                            fragmentFlask.delayedBluetoothEnable()
                            amiibosView = fragmentFlask.flaskContent
                            bottomSheet = fragmentFlask.bottomSheet
                        } else {
                            hideBrowserInterface()
                            setTitle(R.string.guides)
                        }
                        3 -> {
                            hideBrowserInterface()
                            setTitle(R.string.guides)
                        }
                        else -> {
                            showBrowserInterface()
                            setTitle(R.string.tagmo)
                            amiibosView = fragmentBrowser!!.browserContent
                            foomiiboView = fragmentBrowser!!.foomiiboView
                            bottomSheet = bottomSheetBehavior
                            if (null != foomiiboView) {
                                foomiiboView?.layoutManager =
                                    if (settings!!.amiiboView == VIEW.IMAGE.value)
                                        GridLayoutManager(this@BrowserActivity, columnCount)
                                    else LinearLayoutManager(this@BrowserActivity)
                            }
                        }
                    }
                }
                if (null != amiibosView) {
                    amiibosView?.layoutManager = if (settings!!.amiiboView == VIEW.IMAGE.value)
                        GridLayoutManager(this@BrowserActivity, columnCount)
                    else LinearLayoutManager(this@BrowserActivity)
                }
                if (BuildConfig.WEAR_OS) onCreateWearOptionsMenu() else invalidateOptionsMenu()
            }
        })
        TabLayoutMediator(findViewById(R.id.navigation_tabs), layout!!, true,
            Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2)
        ) { tab: TabLayout.Tab, position: Int ->
            val hasFlaskEnabled = prefs.flaskEnabled()
            if (BuildConfig.WEAR_OS) {
                when (position) {
                    1 -> if (hasFlaskEnabled) {
                        tab.setText(R.string.flask_title)
                    } else {
                        tab.setText(R.string.settings)
                    }
                    2 -> tab.setText(R.string.settings)
                    3 -> tab.setText(R.string.guides)
                    else -> tab.setText(R.string.browser)
                }
            } else {
                val hasEliteEnabled = prefs.eliteEnabled()
                when (position) {
                    1 -> if (hasEliteEnabled) {
                        tab.setText(R.string.elite_n2)
                    } else if (hasFlaskEnabled) {
                        tab.setText(R.string.flask_title)
                    } else {
                        tab.setText(R.string.guides)
                    }
                    2 -> if (hasEliteEnabled && hasFlaskEnabled) {
                        tab.setText(R.string.flask_title)
                    } else {
                        tab.setText(R.string.guides)
                    }
                    3 -> tab.setText(R.string.guides)
                    else -> tab.setText(R.string.browser)
                }
            }
        }.attach()
        val coordinator = findViewById<CoordinatorLayout>(R.id.coordinator)
        if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR1)) {
            (amiiboContainer as BlurView?)!!.setupWith(
                coordinator,
                if (Debug.isNewer(Build.VERSION_CODES.S))
                    RenderEffectBlur()
                else if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR1))
                    @Suppress("DEPRECATION") RenderScriptBlur(this)
                else
                    SupportRenderScriptBlur(this)
            )
                .setFrameClearDrawable(window.decorView.background)
                .setBlurRadius(2f).setBlurAutoUpdate(true)
        }

        if (BuildConfig.WEAR_OS) {
            onRequestStorage.launch(PERMISSIONS_STORAGE)
        } else {
            requestStoragePermission()
            try {
                @Suppress("DEPRECATION") packageManager.getPackageInfo(
                    "com.hiddenramblings.tagmo", PackageManager.GET_META_DATA
                )
                AlertDialog.Builder(this)
                    .setTitle(R.string.conversion_title)
                    .setMessage(R.string.conversion_message)
                    .setPositiveButton(R.string.proceed) { _: DialogInterface?, _: Int ->
                        startActivity(Intent(Intent.ACTION_DELETE)
                            .setData(Uri.parse("package:com.hiddenramblings.tagmo"))
                        )
                    }.show()
            } catch (ignored: PackageManager.NameNotFoundException) { }
        }

        val toggle = findViewById<AppCompatImageView>(R.id.toggle)
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet))
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior?.addBottomSheetCallback(object : BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    toggle.setImageResource(R.drawable.ic_expand_less_white_24dp)
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    setFolderText(settings)
                    toggle.setImageResource(R.drawable.ic_expand_more_white_24dp)
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
        toggle.setOnClickListener {
            if (bottomSheetBehavior?.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_EXPANDED)
            } else {
                bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
        }
        if (BuildConfig.WEAR_OS) {
            onCreateWearOptionsMenu()
        } else {
            onLoadSettingsFragment()
            val buildText = findViewById<TextView>(R.id.build_text)
            buildText.movementMethod = LinkMovementMethod.getInstance()
            buildText.text = TagMo.getVersionLabel(false)
            prefsDrawer = findViewById(R.id.drawer_layout)
            prefsDrawer?.addDrawerListener(object : SimpleDrawerListener() {
                override fun onDrawerOpened(drawerView: View) {
                    findViewById<View>(R.id.donation_banner).isVisible = TagMo.hasSubscription
                    super.onDrawerOpened(drawerView)
                    findViewById<View>(R.id.build_layout).setOnClickListener {
                        closePrefsDrawer()
                        val repository = "https://github.com/HiddenRamblings/TagMo"
                        showWebsite(repository)
                    }
                    if (null != appUpdate) {
                        findViewById<View>(R.id.build_layout).setOnClickListener {
                            closePrefsDrawer()
                            updateManager?.downloadPlayUpdate(appUpdate)
                        }
                    }
                    if (null != updateUrl) {
                        findViewById<View>(R.id.build_layout).setOnClickListener {
                            closePrefsDrawer()
                            updateManager?.installUpdateCompat(updateUrl!!)
                        }
                    }
                }

                override fun onDrawerClosed(drawerView: View) {
                    super.onDrawerClosed(drawerView)
                    if (reloadTabCollection) {
                        reloadTabCollection = false
                        onTabCollectionChanged()
                    }
                }
            })
        }

        val foomiiboOptions = findViewById<LinearLayout>(R.id.foomiibo_options)
        val foomiiboHandler = Handler(Looper.getMainLooper())
        foomiiboOptions.findViewById<View>(R.id.clear_foomiibo_set).setOnClickListener {
            collapseBottomSheet()
            foomiiboHandler.postDelayed(
                { fragmentBrowser?.clearFoomiiboSet() }, TagMo.uiDelay.toLong()
            )
        }
        foomiiboOptions.findViewById<View>(R.id.build_foomiibo_set).setOnClickListener {
            collapseBottomSheet()
            foomiiboHandler.postDelayed(
                { fragmentBrowser?.buildFoomiiboSet() }, TagMo.uiDelay.toLong()
            )
        }

        val foldersView = findViewById<RecyclerView>(R.id.folders_list)
        foldersView.layoutManager = LinearLayoutManager(this)
        foldersView.adapter = FoldersAdapter(settings)
        settings?.addChangeListener(foldersView.adapter as BrowserSettingsListener?)

        val popup = if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP_MR1)) PopupMenu(
            this, nfcFab, Gravity.END, 0, R.style.PopupMenu
        ) else PopupMenu(this, nfcFab)
        try {
            for (field in popup.javaClass.declaredFields) {
                if ("mPopup" == field.name) {
                    field.isAccessible = true
                    val menuPopupHelper = field[popup]
                    if (null != menuPopupHelper) {
                        val setForceIcons = Class.forName(menuPopupHelper.javaClass.name)
                            .getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                        setForceIcons.invoke(menuPopupHelper, true)
                    }
                    break
                }
            }
        } catch (e: Exception) {
            Debug.warn(e)
        }
        popup.menuInflater.inflate(R.menu.action_menu, popup.menu)
        nfcFab.setOnClickListener { showPopupMenu(popup) }
        findViewById<View>(R.id.amiiboContainer).setOnClickListener {
            amiiboContainer?.isGone = true
        }

        if (null != intent && null != intent.action && Intent.ACTION_VIEW == intent.action) {
            try {
                if (null != intent.clipData) {
                    for (i in 0 until intent.clipData!!.itemCount) {
                        val uri = intent.clipData!!.getItemAt(i).uri
                        val data = TagReader.readTagDocument(uri)
                        updateAmiiboView(data, AmiiboFile(
                            uri.path?.let { File(it) }, Amiibo.dataToId(data), data
                        ))
                    }
                } else if (null != intent.data) {
                    val uri = intent.data
                    val data = TagReader.readTagDocument(uri!!)
                    updateAmiiboView(data, AmiiboFile(
                        uri.path?.let { File(it) }, Amiibo.dataToId(data), data
                    ))
                }
            } catch (ignored: Exception) { }
        }

        loadPTagKeyManager()

        if (!BuildConfig.WEAR_OS) {
            donationManager.retrieveDonationMenu()
            findViewById<View>(R.id.donate_layout).setOnClickListener {
                closePrefsDrawer()
                donationManager.onSendDonationClicked()
            }
        }

        if (!prefs.guidesPrompted()) {
            prefs.guidesPrompted(true)
            layout?.setCurrentItem(pagerAdapter.itemCount - 1, false)
        }
    }

    fun onApplicationRecreate() {
        val intent = intent
        overridePendingTransition(0, 0)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        finish()
        overridePendingTransition(0, 0)
        startActivity(intent)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun onTabCollectionChanged() {
        if (layout?.currentItem != 0) layout?.setCurrentItem(0, false)
        if (Debug.isNewer(Build.VERSION_CODES.TIRAMISU))
            onApplicationRecreate()
        else pagerAdapter.notifyDataSetChanged()
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private fun onShowJoyConFragment() {
        if (null != joyConDialog && joyConDialog!!.isShowing) return
        val fragmentJoyCon = newInstance()
        fragmentJoyCon.show(supportFragmentManager, "dialog")
        joyConDialog = fragmentJoyCon.dialog
    }

    private fun requestStoragePermission() {
        if (Debug.isNewer(Build.VERSION_CODES.R)) {
            if (BuildConfig.GOOGLE_PLAY) {
                onDocumentEnabled()
            } else {
                if (null != settings?.browserRootDocument && isDocumentStorage) {
                    onDocumentEnabled()
                } else if (Environment.isExternalStorageManager()) {
                    settings?.browserRootDocument = null
                    settings?.notifyChanges()
                    onStorageEnabled()
                } else {
                    requestScopedStorage()
                }
            }
        } else if (Debug.isNewer(Build.VERSION_CODES.M)) {
            onRequestStorage.launch(PERMISSIONS_STORAGE)
        } else {
            onStorageEnabled()
        }
    }

    val onNFCActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || null == result.data) return@registerForActivityResult
        if (NFCIntent.ACTION_NFC_SCANNED != result.data!!.action) return@registerForActivityResult
        if (result.data!!.hasExtra(NFCIntent.EXTRA_SIGNATURE)) {
            val signature = result.data!!.getStringExtra(NFCIntent.EXTRA_SIGNATURE)
            prefs.eliteSignature(signature)
            val activeBank = result.data!!.getIntExtra(
                NFCIntent.EXTRA_ACTIVE_BANK, prefs.eliteActiveBank()
            )
            prefs.eliteActiveBank(activeBank)
            val bankCount = result.data!!.getIntExtra(
                NFCIntent.EXTRA_BANK_COUNT, prefs.eliteBankCount()
            )
            prefs.eliteBankCount(bankCount)
            showElitePage(result.data!!.extras)
        } else {
            layout!!.setCurrentItem(0, true)
            updateAmiiboView(result.data?.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA))
        }
    }
    private val onBackupActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || null == result.data) return@registerForActivityResult
        if (NFCIntent.ACTION_NFC_SCANNED != result.data!!.action) return@registerForActivityResult
        val tagData = result.data?.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA)
        val view = layoutInflater.inflate(
            R.layout.dialog_save_item, layout, false
        )
        val dialog = AlertDialog.Builder(this)
        val input = view.findViewById<EditText>(R.id.save_item_entry)
        input.setText(decipherFilename(settings?.amiiboManager, tagData, true))
        val backupDialog: Dialog = dialog.setView(view).create()
        view.findViewById<View>(R.id.button_save).setOnClickListener { _: View? ->
            try {
                var fileName: String? = input.text.toString()
                fileName = if (isDocumentStorage) {
                    val rootDocument = settings?.browserRootDocument?.let {
                        DocumentFile.fromTreeUri(this, it)
                    } ?: throw NullPointerException()
                    writeBytesToDocument(
                        this, rootDocument, fileName!!, tagData
                    )
                } else {
                    writeBytesToFile(Storage.getDownloadDir(
                            "TagMo", "Backups"
                    ), fileName!!, tagData)
                }
                IconifiedSnackbar(this, layout).buildSnackbar(
                    getString(R.string.wrote_file, fileName), Snackbar.LENGTH_SHORT
                ).show()
                onRootFolderChanged(true)
            } catch (e: Exception) {
                e.message?.let { Toasty(this).Short(it) }
            }
            backupDialog.dismiss()
        }
        view.findViewById<View>(R.id.button_cancel).setOnClickListener { backupDialog.dismiss() }
        backupDialog.show()
    }
    private val onValidateActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || null == result.data) return@registerForActivityResult
        if (NFCIntent.ACTION_NFC_SCANNED != result.data!!.action) return@registerForActivityResult
        try {
            validateData(result.data?.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA))
            IconifiedSnackbar(this, layout).buildSnackbar(
                R.string.validation_success, Snackbar.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            IconifiedSnackbar(this, layout).buildSnackbar(
                e.message, R.drawable.ic_baseline_bug_report_24dp, Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun showPopupMenu(popup: PopupMenu) {
        popup.menu.findItem(R.id.mnu_scan).isEnabled = false
        popup.menu.findItem(R.id.mnu_backup).isEnabled = false
        popup.menu.findItem(R.id.mnu_validate).isEnabled = false
        popup.menu.findItem(R.id.mnu_lego).isEnabled = false
        popup.menu.findItem(R.id.mnu_qr_code).isEnabled = false
        popup.show()
        val popupHandler: Handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                popup.menu.findItem(msg.what).isEnabled = true
            }
        }
        popupHandler.postDelayed({
            val baseDelay = 75
            popupHandler.sendEmptyMessageDelayed(R.id.mnu_qr_code, baseDelay.toLong())
            popupHandler.sendEmptyMessageDelayed(R.id.mnu_lego, (75 + baseDelay).toLong())
            popupHandler.sendEmptyMessageDelayed(R.id.mnu_validate, (175 + baseDelay).toLong())
            popupHandler.sendEmptyMessageDelayed(R.id.mnu_backup, (275 + baseDelay).toLong())
            popupHandler.sendEmptyMessageDelayed(R.id.mnu_scan, (375 + baseDelay).toLong())
        }, 275)
        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.mnu_scan -> {
                    onNFCActivity.launch(
                        Intent(this, NfcActivity::class.java)
                            .setAction(NFCIntent.ACTION_SCAN_TAG)
                    )
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_backup -> {
                    val backup = Intent(this, NfcActivity::class.java)
                    backup.action = NFCIntent.ACTION_BACKUP_AMIIBO
                    onBackupActivity.launch(backup)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_validate -> {
                    onValidateActivity.launch(
                        Intent(this, NfcActivity::class.java)
                            .setAction(NFCIntent.ACTION_SCAN_TAG)
                    )
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_lego -> {
                    Toasty(this).Short(R.string.notice_incomplete)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_qr_code -> {
                    onQRCodeScanner.launch(Intent(this, QRCodeScanner::class.java))
                    return@setOnMenuItemClickListener true
                }
                else -> false
            }
        }
    }

    fun onCaptureLogcatClick() {
        if (updateManager?.hasPendingUpdate() == true) {
            if (null != appUpdate) updateManager?.downloadPlayUpdate(appUpdate)
            if (null != updateUrl) updateManager?.installUpdateCompat(updateUrl)
            return
        }
        loadingExecutor.execute {
            try {
                if (!Debug.processLogcat(this)) {
                    runOnUiThread { showWebsite(null) }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    IconifiedSnackbar(this, layout).buildSnackbar(
                        e.message, Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun setFoomiiboPanelVisibility() {
        fragmentBrowser!!.setFoomiiboVisibility()
    }

    private fun getQueryCount(queryText: String?): Int {
        if (null == queryText) return 0
        val amiiboManager = settings?.amiiboManager ?: return 0
        val items: MutableSet<Long> = HashSet()
        for (amiibo in amiiboManager.amiibos.values) {
            if (settings?.amiiboContainsQuery(amiibo, queryText) == true) items.add(amiibo.id)
        }
        return items.size
    }

    private fun getFilteredCount(filter: String, filterType: FILTER): Int {
        val amiiboManager = settings?.amiiboManager ?: return 0
        val gamesManager = settings?.gamesManager
        val items: MutableSet<Long> = HashSet()
        for (amiibo in amiiboManager.amiibos.values) {
            when (filterType) {
                FILTER.CHARACTER -> {
                    val character = amiibo.character
                    if (null != character && Amiibo.matchesGameSeriesFilter(
                            amiibo.gameSeries, settings!!.getFilter(FILTER.GAME_SERIES)
                        ) && Amiibo.matchesAmiiboSeriesFilter(
                            amiibo.amiiboSeries, settings!!.getFilter(FILTER.AMIIBO_SERIES)
                        ) && Amiibo.matchesAmiiboTypeFilter(
                            amiibo.amiiboType, settings!!.getFilter(FILTER.AMIIBO_TYPE)
                        )
                    ) {
                        if (character.name == filter) items.add(amiibo.id)
                    }
                }
                FILTER.GAME_SERIES -> {
                    val gameSeries = amiibo.gameSeries
                    if (null != gameSeries && Amiibo.matchesCharacterFilter(
                            amiibo.character, settings!!.getFilter(FILTER.CHARACTER)
                        ) && Amiibo.matchesAmiiboSeriesFilter(
                            amiibo.amiiboSeries, settings!!.getFilter(FILTER.AMIIBO_SERIES)
                        ) && Amiibo.matchesAmiiboTypeFilter(
                            amiibo.amiiboType, settings!!.getFilter(FILTER.AMIIBO_TYPE)
                        )
                    ) {
                        if (gameSeries.name == filter) items.add(amiibo.id)
                    }
                }
                FILTER.AMIIBO_SERIES -> {
                    val amiiboSeries = amiibo.amiiboSeries
                    if (null != amiiboSeries && Amiibo.matchesGameSeriesFilter(
                            amiibo.gameSeries, settings!!.getFilter(FILTER.GAME_SERIES)
                        ) && Amiibo.matchesCharacterFilter(
                            amiibo.character, settings!!.getFilter(FILTER.CHARACTER)
                        ) && Amiibo.matchesAmiiboTypeFilter(
                            amiibo.amiiboType, settings!!.getFilter(FILTER.AMIIBO_TYPE)
                        )
                    ) {
                        if (amiiboSeries.name == filter) items.add(amiibo.id)
                    }
                }
                FILTER.AMIIBO_TYPE -> {
                    val amiiboType = amiibo.amiiboType
                    if (null != amiiboType && Amiibo.matchesGameSeriesFilter(
                            amiibo.gameSeries, settings!!.getFilter(FILTER.GAME_SERIES)
                        ) && Amiibo.matchesCharacterFilter(
                            amiibo.character, settings!!.getFilter(FILTER.CHARACTER)
                        ) && Amiibo.matchesAmiiboSeriesFilter(
                            amiibo.amiiboSeries, settings!!.getFilter(FILTER.AMIIBO_SERIES)
                        )
                    ) {
                        if (amiiboType.name == filter) items.add(amiibo.id)
                    }
                }
                FILTER.GAME_TITLES -> if (Amiibo.matchesGameSeriesFilter(
                        amiibo.gameSeries, settings!!.getFilter(FILTER.GAME_SERIES)
                    ) && Amiibo.matchesCharacterFilter(
                        amiibo.character, settings!!.getFilter(FILTER.CHARACTER)
                    ) && Amiibo.matchesAmiiboSeriesFilter(
                        amiibo.amiiboSeries, settings!!.getFilter(FILTER.AMIIBO_SERIES)
                    ) && Amiibo.matchesAmiiboTypeFilter(
                        amiibo.amiiboType, settings!!.getFilter(FILTER.AMIIBO_TYPE)
                    )
                ) {
                    if (null != gamesManager) items.addAll(
                        gamesManager.getGameAmiiboIds(
                            amiiboManager,
                            filter
                        )
                    )
                }
            }
        }
        return items.size
    }

    private fun onFilterCharacterClick(): Boolean {
        val subMenu = menuFilterCharacter?.subMenu
        subMenu?.clear() ?: return true
        settings?.amiiboManager ?: return true
        val items: MutableSet<String> = HashSet()
        settings!!.amiiboManager!!.amiibos.values.forEach {
            val character = it.character
            if (null != character && Amiibo.matchesGameSeriesFilter(
                    it.gameSeries, settings!!.getFilter(FILTER.GAME_SERIES)
                ) && Amiibo.matchesAmiiboSeriesFilter(
                    it.amiiboSeries, settings!!.getFilter(FILTER.AMIIBO_SERIES)
                ) && Amiibo.matchesAmiiboTypeFilter(
                    it.amiiboType, settings!!.getFilter(FILTER.AMIIBO_TYPE)
                )
            ) {
                items.add(character.name)
            }
        }
        val list = ArrayList(items)
        list.sort()
        for (item in list) {
            subMenu.add(R.id.filter_character_group, Menu.NONE, 0, item)
                .setChecked(item == settings!!.getFilter(FILTER.CHARACTER))
                .setOnMenuItemClickListener(onFilterCharacterItemClick)
        }
        subMenu.setGroupCheckable(R.id.filter_character_group, true, true)
        return true
    }

    private val onFilterCharacterItemClick = MenuItem.OnMenuItemClickListener { menuItem ->
        settings?.setFilter(FILTER.CHARACTER, menuItem.title.toString())
        settings?.notifyChanges()
        filteredCount = getFilteredCount(menuItem.title.toString(), FILTER.CHARACTER)
        false
    }

    private fun onFilterGameSeriesClick(): Boolean {
        val subMenu = menuFilterGameSeries!!.subMenu
        subMenu?.clear() ?: return true
        settings?.amiiboManager ?: return false
        val items: MutableSet<String> = HashSet()
        settings!!.amiiboManager!!.amiibos.values.forEach {
            val gameSeries = it.gameSeries
            if (null != gameSeries && Amiibo.matchesCharacterFilter(
                    it.character, settings!!.getFilter(FILTER.CHARACTER)
                ) && Amiibo.matchesAmiiboSeriesFilter(
                    it.amiiboSeries, settings!!.getFilter(FILTER.AMIIBO_SERIES)
                ) && Amiibo.matchesAmiiboTypeFilter(
                    it.amiiboType, settings!!.getFilter(FILTER.AMIIBO_TYPE)
                )
            ) {
                items.add(gameSeries.name)
            }
        }
        val list = ArrayList(items)
        list.sort()
        for (item in list) {
            subMenu.add(R.id.filter_game_series_group, Menu.NONE, 0, item)
                .setChecked(item == settings!!.getFilter(FILTER.GAME_SERIES))
                .setOnMenuItemClickListener(onFilterGameSeriesItemClick)
        }
        subMenu.setGroupCheckable(R.id.filter_game_series_group, true, true)
        return true
    }

    private val onFilterGameSeriesItemClick = MenuItem.OnMenuItemClickListener { menuItem ->
        settings?.setFilter(FILTER.GAME_SERIES, menuItem.title.toString())
        settings?.notifyChanges()
        filteredCount = getFilteredCount(menuItem.title.toString(), FILTER.GAME_SERIES)
        false
    }

    private fun onFilterAmiiboSeriesClick(): Boolean {
        val subMenu = menuFilterAmiiboSeries!!.subMenu
        subMenu?.clear() ?: return true
        val amiiboManager = settings?.amiiboManager ?: return true
        val items: MutableSet<String> = HashSet()
        amiiboManager.amiibos.values.forEach {
            val amiiboSeries = it.amiiboSeries
            if (null != amiiboSeries && Amiibo.matchesGameSeriesFilter(
                    it.gameSeries, settings!!.getFilter(FILTER.GAME_SERIES)
                ) && Amiibo.matchesCharacterFilter(
                    it.character, settings!!.getFilter(FILTER.CHARACTER)
                ) && Amiibo.matchesAmiiboTypeFilter(
                    it.amiiboType, settings!!.getFilter(FILTER.AMIIBO_TYPE)
                )
            ) {
                items.add(amiiboSeries.name)
            }
        }
        val list = ArrayList(items)
        list.sort()
        for (item in list) {
            subMenu.add(R.id.filter_amiibo_series_group, Menu.NONE, 0, item)
                .setChecked(item == settings!!.getFilter(FILTER.AMIIBO_SERIES))
                .setOnMenuItemClickListener(onFilterAmiiboSeriesItemClick)
        }
        subMenu.setGroupCheckable(R.id.filter_amiibo_series_group, true, true)
        return true
    }

    private val onFilterAmiiboSeriesItemClick = MenuItem.OnMenuItemClickListener { menuItem ->
        settings?.setFilter(FILTER.AMIIBO_SERIES, menuItem.title.toString())
        settings?.notifyChanges()
        filteredCount = getFilteredCount(menuItem.title.toString(), FILTER.AMIIBO_SERIES)
        false
    }

    private fun onFilterAmiiboTypeClick(): Boolean {
        val subMenu = menuFilterAmiiboType!!.subMenu
        subMenu?.clear() ?: return true
        val amiiboManager = settings?.amiiboManager ?: return true
        val items: MutableSet<AmiiboType> = HashSet()
        amiiboManager.amiibos.values.forEach {
            val amiiboType = it.amiiboType
            if (null != amiiboType && Amiibo.matchesGameSeriesFilter(
                    it.gameSeries, settings!!.getFilter(FILTER.GAME_SERIES)
                ) && Amiibo.matchesCharacterFilter(
                    it.character, settings!!.getFilter(FILTER.CHARACTER)
                ) && Amiibo.matchesAmiiboSeriesFilter(
                    it.amiiboSeries, settings!!.getFilter(FILTER.AMIIBO_SERIES)
                )
            ) {
                items.add(amiiboType)
            }
        }
        val list = ArrayList(items)
        list.sort()
        for (item in list) {
            subMenu.add(R.id.filter_amiibo_type_group, Menu.NONE, 0, item.name)
                .setChecked(item.name == settings!!.getFilter(FILTER.AMIIBO_TYPE))
                .setOnMenuItemClickListener(onFilterAmiiboTypeItemClick)
        }
        subMenu.setGroupCheckable(R.id.filter_amiibo_type_group, true, true)
        return true
    }

    private val onFilterAmiiboTypeItemClick = MenuItem.OnMenuItemClickListener { menuItem ->
        settings?.setFilter(FILTER.AMIIBO_TYPE, menuItem.title.toString())
        settings?.notifyChanges()
        filteredCount = getFilteredCount(menuItem.title.toString(), FILTER.AMIIBO_TYPE)
        false
    }

    private fun onFilterGameTitlesClick() {
        val subMenu = menuFilterGameTitles!!.subMenu
        subMenu?.clear() ?: return
        settings?.amiiboManager ?: return
        val gamesManager = settings!!.gamesManager
        val items: MutableSet<String> = HashSet()
        gamesManager?.gameTitles!!.forEach { items.add(it.name) }
        val list = ArrayList(items)
        list.sort()
        list.forEach {
            subMenu.add(R.id.filter_game_titles_group, Menu.NONE, 0, it)
                .setChecked(it == settings!!.getFilter(FILTER.GAME_TITLES))
                .setOnMenuItemClickListener(onFilterGameTitlesItemClick)
        }
        subMenu.setGroupCheckable(R.id.filter_game_titles_group, true, true)
    }

    private val onFilterGameTitlesItemClick = MenuItem.OnMenuItemClickListener { menuItem ->
        settings?.setFilter(FILTER.GAME_TITLES, menuItem.title.toString())
        settings?.notifyChanges()
        filteredCount = getFilteredCount(menuItem.title.toString(), FILTER.GAME_TITLES)
        false
    }

    private fun onCreateToolbarMenu(toolbar: Toolbar?, tagData: ByteArray?, amiiboFile: AmiiboFile?) {
        if (null == toolbar) return
        if (!toolbar.menu.hasVisibleItems()) toolbar.inflateMenu(R.menu.amiibo_menu)
        var available = (null != tagData) && tagData.isNotEmpty()
        if (available) {
            try {
                Amiibo.dataToId(tagData)
            } catch (e: Exception) {
                available = false
                Debug.info(e)
            }
        }
        toolbar.menu.findItem(R.id.mnu_write).isEnabled = available
        toolbar.menu.findItem(R.id.mnu_update).isEnabled = available
        toolbar.menu.findItem(R.id.mnu_edit).isEnabled = available
        toolbar.menu.findItem(R.id.mnu_view_hex).isEnabled = available
        toolbar.menu.findItem(R.id.mnu_share_qr).isEnabled = available
        toolbar.menu.findItem(R.id.mnu_validate).isEnabled = available
        var cached = false
        val backup = toolbar.menu.findItem(R.id.mnu_save)
        backup.isEnabled = available
        val delete = toolbar.menu.findItem(R.id.mnu_delete)
        delete.isVisible = null != amiiboFile
        if (null != amiiboFile) {
            if (null != amiiboFile.docUri) {
                val relativeDocument = Storage.getRelativeDocument(amiiboFile.docUri?.uri)
                cached = relativeDocument.startsWith("/Foomiibo/")
            } else if (null != amiiboFile.filePath) {
                var relativeFile = Storage.getRelativePath(
                    amiiboFile.filePath, prefs.preferEmulated()
                )
                if (null != prefs.browserRootFolder()) {
                    relativeFile = relativeFile.replace(prefs.browserRootFolder()!!, "")
                }
                cached = relativeFile.startsWith("/Foomiibo/")
            }
            if (cached) backup.setTitle(R.string.cache)
        }
        toolbar.setOnMenuItemClickListener { menuItem ->
            clickedAmiibo = amiiboFile
            val args = Bundle()
            val scan = Intent(this, NfcActivity::class.java)
            when (menuItem.itemId) {
                R.id.mnu_scan -> {
                    scan.action = NFCIntent.ACTION_SCAN_TAG
                    onUpdateTagResult.launch(scan)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_write -> {
                    args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData)
                    scan.action = NFCIntent.ACTION_WRITE_TAG_FULL
                    onUpdateTagResult.launch(scan.putExtras(args))
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_update -> {
                    args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData)
                    scan.action = NFCIntent.ACTION_WRITE_TAG_DATA
                    scan.putExtra(NFCIntent.EXTRA_IGNORE_TAG_ID, ignoreTagId)
                    onUpdateTagResult.launch(scan.putExtras(args))
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_save -> {
                    if (cached) {
                        if (tagData != null) {
                            fragmentBrowser?.buildFoomiiboFile(tagData)
                            onRootFolderChanged(true)
                        }
                    } else {
                        val view = layoutInflater.inflate(R.layout.dialog_save_item, null)
                        val dialog = AlertDialog.Builder(this)
                        val input = view.findViewById<EditText>(R.id.save_item_entry)
                        input.setText(decipherFilename(settings!!.amiiboManager, tagData, true))
                        val backupDialog: Dialog = dialog.setView(view).create()
                        view.findViewById<View>(R.id.button_save).setOnClickListener {
                            try {
                                var fileName: String? = input.text.toString()
                                fileName = if (isDocumentStorage) {
                                    val rootDocument = DocumentFile.fromTreeUri(
                                        this, settings!!.browserRootDocument!!
                                    ) ?: throw NullPointerException()
                                    writeBytesToDocument(
                                        this, rootDocument, fileName!!, tagData
                                    )
                                } else {
                                    writeBytesToFile(Storage.getDownloadDir(
                                            "TagMo", "Backups"
                                    ), fileName!!, tagData)
                                }
                                IconifiedSnackbar(this, layout).buildSnackbar(
                                    getString(R.string.wrote_file, fileName), Snackbar.LENGTH_SHORT
                                ).show()
                                onRootFolderChanged(true)
                            } catch (e: Exception) {
                                e.message?.let { Toasty(this).Short(it) }
                            }
                            backupDialog.dismiss()
                        }
                        view.findViewById<View>(R.id.button_cancel).setOnClickListener {
                            backupDialog.dismiss()
                        }
                        backupDialog.show()
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_edit -> {
                    args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData)
                    val tagEdit = Intent(this, TagDataEditor::class.java)
                    onUpdateTagResult.launch(tagEdit.putExtras(args))
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_view_hex -> {
                    val hexView = Intent(this, HexCodeViewer::class.java)
                    hexView.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData)
                    startActivity(hexView)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_share_qr -> {
                    val qrShare = Intent(this, QRCodeScanner::class.java)
                    qrShare.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData)
                    onQRCodeScanner.launch(qrShare)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_validate -> {
                    try {
                        validateData(tagData)
                        IconifiedSnackbar(this, layout).buildSnackbar(
                            R.string.validation_success, Snackbar.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        IconifiedSnackbar(this, layout).buildSnackbar(
                            e.message,
                            R.drawable.ic_baseline_bug_report_24dp, Snackbar.LENGTH_LONG
                        ).show()
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_delete -> {
                    deleteAmiiboDocument(amiiboFile)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_ignore_tag_id -> {
                    ignoreTagId = !menuItem.isChecked
                    menuItem.isChecked = ignoreTagId
                    return@setOnMenuItemClickListener true
                }
                else -> false
            }
        }
    }

    fun onCreateToolbarMenu(
        fragment: BrowserFragment, toolbar: Toolbar, tagData: ByteArray?, itemView: View
    ) {
        if (!toolbar.menu.hasVisibleItems()) toolbar.inflateMenu(R.menu.amiibo_menu)
        toolbar.menu.findItem(R.id.mnu_save).setTitle(R.string.cache)
        toolbar.menu.findItem(R.id.mnu_scan).isVisible = false
        toolbar.setOnMenuItemClickListener {
            val args = Bundle()
            val scan = Intent(this, NfcActivity::class.java)
            when (it.itemId) {
                R.id.mnu_write -> {
                    args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData)
                    scan.action = NFCIntent.ACTION_WRITE_TAG_FULL
                    try {
                        fragment.onUpdateTagResult.launch(scan.putExtras(args))
                    } catch (ex: IllegalStateException) {
                        layout?.adapter = pagerAdapter
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_update -> {
                    args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData)
                    scan.action = NFCIntent.ACTION_WRITE_TAG_DATA
                    scan.putExtra(NFCIntent.EXTRA_IGNORE_TAG_ID, ignoreTagId)
                    try {
                        fragment.onUpdateTagResult.launch(scan.putExtras(args))
                    } catch (ex: IllegalStateException) {
                        layout?.adapter = pagerAdapter
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_save -> {
                    if (tagData != null) {
                        fragment.buildFoomiiboFile(tagData)
                        itemView.callOnClick()
                        onRefresh(true)
                    } else {
                        Toasty(this).Short(R.string.fail_save_data)
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_edit -> {
                    args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData)
                    val tagEdit = Intent(this, TagDataEditor::class.java)
                    try {
                        fragment.onUpdateTagResult.launch(tagEdit.putExtras(args))
                    } catch (ex: IllegalStateException) {
                        layout?.adapter = pagerAdapter
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_view_hex -> {
                    val hexView = Intent(this, HexCodeViewer::class.java)
                    hexView.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData)
                    startActivity(hexView)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_share_qr -> {
                    val qrShare = Intent(this, QRCodeScanner::class.java)
                    qrShare.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData)
                    onQRCodeScanner.launch(qrShare)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_validate -> {
                    try {
                        validateData(tagData)
                        IconifiedSnackbar(this, layout).buildSnackbar(
                            R.string.validation_success, Snackbar.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        IconifiedSnackbar(this, layout).buildSnackbar(
                            e.message, R.drawable.ic_baseline_bug_report_24dp, Snackbar.LENGTH_LONG
                        ).show()
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_delete -> {
                    fragment.deleteFoomiiboFile(tagData)
                    itemView.callOnClick()
                    onRefresh(true)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_ignore_tag_id -> {
                    ignoreTagId = !it.isChecked
                    it.isChecked = ignoreTagId
                    return@setOnMenuItemClickListener true
                }
                else -> false
            }
        }
    }

    private fun getGameCompatibility(txtUsage: TextView, tagData: ByteArray?) {
        val gamesManager = settings?.gamesManager
        val label = findViewById<TextView>(R.id.txtUsageLabel)
        if (null != gamesManager) {
            label.isVisible = true
            loadingScope.launch {
                withContext(Dispatchers.IO) {
                    val usage: String? = try {
                        val amiiboId = Amiibo.dataToId(tagData)
                        gamesManager.getGamesCompatibility(amiiboId)
                    } catch (ex: Exception) {
                        Debug.warn(ex)
                        null
                    }
                    withContext(Dispatchers.Main) {
                        txtUsage.text = usage
                    }
                }
            }
        } else {
            label.isGone = true
        }
    }

    fun onKeysLoaded(indicator: Boolean) {
        hideFakeSnackbar()
        onRefresh(indicator)
    }

    fun onRefresh(indicator: Boolean) {
        loadAmiiboManager()
        onRootFolderChanged(indicator)
    }

    val isDocumentStorage: Boolean
        get() = if (
            Debug.isNewer(Build.VERSION_CODES.LOLLIPOP) && null != settings?.browserRootDocument
        ) {
            try {
                DocumentFile.fromTreeUri(this, settings!!.browserRootDocument!!)
                true
            } catch (iae: IllegalArgumentException) {
                false
            }
        } else false

    @Throws(ActivityNotFoundException::class)
    private fun onDocumentRequested() {
        if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
            intent.putExtra("android.content.extra.FANCY", true)
            onDocumentTree.launch(intent)
        }
    }

    private fun onDocumentEnabled() {
        if (isDocumentStorage) {
            onStorageEnabled()
        } else {
            try {
                onDocumentRequested()
            } catch (anf: ActivityNotFoundException) {
                Toasty(this).Long(R.string.storage_unavailable)
            }
        }
    }

    private fun onStorageEnabled() {
        if (BuildConfig.WEAR_OS) {
            if (keyManager.isKeyMissing)
                onShowSettingsFragment()
            else onRefresh(true)
        } else {
            if (isDocumentStorage) {
                switchStorageRoot?.isVisible = true
                switchStorageRoot?.setText(R.string.document_storage_root)
                switchStorageRoot?.setOnClickListener {
                    try {
                        onDocumentRequested()
                    } catch (anf: ActivityNotFoundException) {
                        Toasty(this).Long(R.string.storage_unavailable)
                    }
                    bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_COLLAPSED)
                }
                if (Debug.isNewer(Build.VERSION_CODES.R) && !BuildConfig.GOOGLE_PLAY) {
                    switchStorageType?.isVisible = true
                    switchStorageType?.setText(R.string.grant_file_permission)
                    switchStorageType?.setOnClickListener {
                        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
                        if (Environment.isExternalStorageManager()) {
                            settings?.browserRootDocument = null
                            settings?.notifyChanges()
                            onStorageEnabled()
                        } else {
                            requestScopedStorage()
                        }
                    }
                } else {
                    switchStorageType?.isGone = true
                }
                if (keyManager.isKeyMissing)
                    onShowSettingsFragment()
                else onRefresh(true)
            } else {
                val internal = prefs.preferEmulated()
                val storage = Storage.getFile(internal)
                if (null != storage && storage.exists() && Storage.hasPhysicalStorage()) {
                    switchStorageRoot?.isVisible = true
                    switchStorageRoot?.setText(if (internal) R.string.emulated_storage_root else R.string.physical_storage_root)
                    switchStorageRoot?.setOnClickListener {
                        val external = !prefs.preferEmulated()
                        switchStorageRoot?.setText(if (external) R.string.emulated_storage_root else R.string.physical_storage_root)
                        settings?.browserRootFolder = Storage.getFile(external)
                        settings?.notifyChanges()
                        prefs.preferEmulated(external)
                    }
                } else {
                    switchStorageRoot?.isGone = true
                }
                if (Debug.isNewer(Build.VERSION_CODES.R)) {
                    switchStorageType?.isVisible = true
                    switchStorageType?.setText(R.string.force_document_storage)
                    switchStorageType?.setOnClickListener {
                        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
                        try {
                            onDocumentRequested()
                        } catch (anf: ActivityNotFoundException) {
                            Toasty(this).Long(R.string.storage_unavailable)
                        }
                    }
                } else {
                    switchStorageType?.isGone = true
                }
                if (keyManager.isKeyMissing) {
                    hideFakeSnackbar()
                    showFakeSnackbar(getString(R.string.locating_keys))
                    locateKeyFiles()
                } else {
                    onRefresh(true)
                }
            }
        }
    }

    private fun onMenuItemClicked(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refresh -> {
                onRefresh(true)
            }
            R.id.sort_id -> {
                settings?.sort = SORT.ID.value
                settings?.notifyChanges()
            }
            R.id.sort_name -> {
                settings?.sort = SORT.NAME.value
                settings?.notifyChanges()
            }
            R.id.sort_character -> {
                settings?.sort = SORT.CHARACTER.value
                settings?.notifyChanges()
            }
            R.id.sort_game_series -> {
                settings?.sort = SORT.GAME_SERIES.value
                settings?.notifyChanges()
            }
            R.id.sort_amiibo_series -> {
                settings?.sort = SORT.AMIIBO_SERIES.value
                settings?.notifyChanges()
            }
            R.id.sort_amiibo_type -> {
                settings?.sort = SORT.AMIIBO_TYPE.value
                settings?.notifyChanges()
            }
            R.id.sort_file_path -> {
                settings?.sort = SORT.FILE_PATH.value
                settings?.notifyChanges()
            }
            R.id.view_simple -> {
                amiibosView?.layoutManager = LinearLayoutManager(this)
                foomiiboView?.layoutManager = LinearLayoutManager(this)
                settings?.amiiboView = VIEW.SIMPLE.value
                settings?.notifyChanges()
            }
            R.id.view_compact -> {
                amiibosView?.layoutManager = LinearLayoutManager(this)
                foomiiboView?.layoutManager = LinearLayoutManager(this)
                settings?.amiiboView = VIEW.COMPACT.value
                settings?.notifyChanges()
            }
            R.id.view_large -> {
                amiibosView?.layoutManager = LinearLayoutManager(this)
                foomiiboView?.layoutManager = LinearLayoutManager(this)
                settings?.amiiboView = VIEW.LARGE.value
                settings?.notifyChanges()
            }
            R.id.view_image -> {
                amiibosView?.layoutManager = GridLayoutManager(this, columnCount)
                foomiiboView?.layoutManager = GridLayoutManager(this, columnCount)
                settings?.amiiboView = VIEW.IMAGE.value
                settings?.notifyChanges()
            }
            R.id.recursive -> {
                settings?.isRecursiveEnabled = !settings!!.isRecursiveEnabled
                settings?.notifyChanges()
            }
            R.id.mnu_joy_con -> {
                Toasty(this).Short(R.string.notice_incomplete)
                if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2)) onShowJoyConFragment()
            }
            R.id.capture_logcat -> {
                onCaptureLogcatClick()
            }
            R.id.filter_character -> {
                return onFilterCharacterClick()
            }
            R.id.filter_game_series -> {
                return onFilterGameSeriesClick()
            }
            R.id.filter_amiibo_series -> {
                return onFilterAmiiboSeriesClick()
            }
            R.id.filter_amiibo_type -> {
                return onFilterAmiiboTypeClick()
            }
            R.id.filter_game_titles -> {
                onFilterGameTitlesClick()
            }
        }
        return BuildConfig.WEAR_OS || super.onOptionsItemSelected(item)
    }

    fun onCreateWearOptionsMenu() {
        val toolbar = findViewById<Toolbar>(R.id.drawer_layout)
        if (!toolbar.menu.hasVisibleItems()) {
            toolbar.inflateMenu(R.menu.browser_menu)
            menuSortId = toolbar.menu.findItem(R.id.sort_id)
            menuSortName = toolbar.menu.findItem(R.id.sort_name)
            menuSortCharacter = toolbar.menu.findItem(R.id.sort_character)
            menuSortGameSeries = toolbar.menu.findItem(R.id.sort_game_series)
            menuSortAmiiboSeries = toolbar.menu.findItem(R.id.sort_amiibo_series)
            menuSortAmiiboType = toolbar.menu.findItem(R.id.sort_amiibo_type)
            menuSortFilePath = toolbar.menu.findItem(R.id.sort_file_path)
            menuFilterGameSeries = toolbar.menu.findItem(R.id.filter_game_series)
            menuFilterCharacter = toolbar.menu.findItem(R.id.filter_character)
            menuFilterAmiiboSeries = toolbar.menu.findItem(R.id.filter_amiibo_series)
            menuFilterAmiiboType = toolbar.menu.findItem(R.id.filter_amiibo_type)
            menuFilterGameTitles = toolbar.menu.findItem(R.id.filter_game_titles)
            menuViewSimple = toolbar.menu.findItem(R.id.view_simple)
            menuViewCompact = toolbar.menu.findItem(R.id.view_compact)
            menuViewLarge = toolbar.menu.findItem(R.id.view_large)
            menuViewImage = toolbar.menu.findItem(R.id.view_image)
            menuRecursiveFiles = toolbar.menu.findItem(R.id.recursive)
            toolbar.menu.findItem(R.id.mnu_joy_con).isVisible =
                Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2)
        }
        onSortChanged()
        onViewChanged()
        onRecursiveFilesChanged()
        toolbar.setOnMenuItemClickListener { item: MenuItem -> onMenuItemClicked(item) }
    }

    @SuppressLint("RestrictedApi")
    private fun setOptionalIconsVisible(menu: Menu) {
        if (menu is MenuBuilder) menu.setOptionalIconsVisible(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (BuildConfig.WEAR_OS) return super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.browser_menu, menu)
        MenuCompat.setGroupDividerEnabled(menu, true)
        setOptionalIconsVisible(menu)
        val menuSearch = menu.findItem(R.id.search)
        val menuUpdate = menu.findItem(R.id.install_update)
        menuSortId = menu.findItem(R.id.sort_id)
        menuSortName = menu.findItem(R.id.sort_name)
        menuSortCharacter = menu.findItem(R.id.sort_character)
        menuSortGameSeries = menu.findItem(R.id.sort_game_series)
        menuSortAmiiboSeries = menu.findItem(R.id.sort_amiibo_series)
        menuSortAmiiboType = menu.findItem(R.id.sort_amiibo_type)
        menuSortFilePath = menu.findItem(R.id.sort_file_path)
        menuFilterGameSeries = menu.findItem(R.id.filter_game_series)
        menuFilterCharacter = menu.findItem(R.id.filter_character)
        menuFilterAmiiboSeries = menu.findItem(R.id.filter_amiibo_series)
        menuFilterAmiiboType = menu.findItem(R.id.filter_amiibo_type)
        menuFilterGameTitles = menu.findItem(R.id.filter_game_titles)
        menuViewSimple = menu.findItem(R.id.view_simple)
        menuViewCompact = menu.findItem(R.id.view_compact)
        menuViewLarge = menu.findItem(R.id.view_large)
        menuViewImage = menu.findItem(R.id.view_image)
        menuRecursiveFiles = menu.findItem(R.id.recursive)
        menu.findItem(R.id.mnu_joy_con).isVisible =
            Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2)
        if (null == settings) return false
        onSortChanged()
        onViewChanged()
        onRecursiveFilesChanged()
        menuUpdate.isVisible = null != appUpdate || null != updateUrl
        val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
        val searchView = menuSearch.actionView as SearchView?
        searchView!!.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        searchView.isSubmitButtonEnabled = false
        menuSearch.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                return if (BottomSheetBehavior.STATE_EXPANDED == bottomSheet?.state
                    || View.VISIBLE == amiiboContainer?.visibility
                    || supportFragmentManager.backStackEntryCount > 0) {
                    onBackPressedDispatcher.onBackPressed()
                    false
                } else {
                    true
                }
            }
        })
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                settings?.query = query
                settings?.notifyChanges()
                if (layout?.currentItem == 0) setAmiiboStats()
                return false
            }

            override fun onQueryTextChange(query: String): Boolean {
                settings?.query = query
                settings?.notifyChanges()
                if (layout?.currentItem == 0 && query.isEmpty()) setAmiiboStats()
                return true
            }
        })
        val query = settings?.query
        if (!TextUtils.isEmpty(query)) {
            menuSearch.expandActionView()
            searchView.setQuery(query, true)
            searchView.clearFocus()
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (!closePrefsDrawer()) {
                prefsDrawer?.openDrawer(GravityCompat.START)
            }
        } else if (item.itemId == R.id.install_update) {
            if (null != appUpdate) updateManager?.downloadPlayUpdate(appUpdate)
            if (null != updateUrl) updateManager?.installUpdateCompat(updateUrl)
        }
        return onMenuItemClicked(item)
    }

    override fun onAmiiboClicked(itemView: View, amiiboFile: AmiiboFile?) {
        if (null == amiiboFile!!.docUri && null == amiiboFile.filePath) return
        try {
            val tagData = getValidatedData(keyManager, amiiboFile)
            if (settings?.amiiboView != VIEW.IMAGE.value) {
                val menuOptions = itemView.findViewById<LinearLayout>(R.id.menu_options)
                val toolbar = menuOptions.findViewById<Toolbar>(R.id.toolbar)
                if (menuOptions.isGone) onCreateToolbarMenu(toolbar, tagData, amiiboFile)
                menuOptions.isGone = menuOptions.isVisible
                val txtUsage = itemView.findViewById<TextView>(R.id.txtUsage)
                if (txtUsage.isGone) getGameCompatibility(txtUsage, tagData)
                txtUsage.isGone = txtUsage.isVisible
            } else {
                updateAmiiboView(tagData, amiiboFile)
            }
        } catch (e: Exception) {
            Debug.warn(e)
        }
    }

    override fun onAmiiboRebind(itemView: View, amiiboFile: AmiiboFile?) {
        if (amiiboFile?.filePath == null) return
        try {
            val tagData = if (null != amiiboFile.data)
                amiiboFile.data
            else
                getValidatedFile(keyManager, amiiboFile.filePath!!)!!
            if (settings?.amiiboView != VIEW.IMAGE.value) {
                onCreateToolbarMenu(
                    itemView.findViewById<View>(R.id.menu_options).findViewById(R.id.toolbar),
                    tagData, amiiboFile
                )
                getGameCompatibility(itemView.findViewById(R.id.txtUsage), tagData)
            } else {
                updateAmiiboView(tagData, amiiboFile)
            }
        } catch (e: Exception) {
            Debug.warn(e)
        }
    }

    override fun onAmiiboImageClicked(amiiboFile: AmiiboFile?) {
        val bundle = Bundle()
        bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiiboFile!!.id)
        val intent = Intent(this, ImageActivity::class.java)
        intent.putExtras(bundle)
        this.startActivity(intent)
    }

    fun loadPTagKeyManager() {
        if (prefs.powerTagEnabled()) {
            backgroundScope.launch(Dispatchers.IO) {
                try {
                    powerTagManager
                } catch (e: Exception) {
                    Debug.warn(e)
                    Toasty(this@BrowserActivity).Short(R.string.fail_powertag_keys)
                }
            }
        }
    }

    private fun loadAmiiboManager() {
        loadingScope.launch {
            withContext(Dispatchers.IO) {
                var amiiboManager: AmiiboManager?
                try {
                    amiiboManager = getAmiiboManager(applicationContext)
                } catch (e: IOException) {
                    Debug.warn(e)
                    amiiboManager = null
                    Toasty(this@BrowserActivity).Short(R.string.amiibo_info_parse_error)
                } catch (e: JSONException) {
                    Debug.warn(e)
                    amiiboManager = null
                    Toasty(this@BrowserActivity).Short(R.string.amiibo_info_parse_error)
                } catch (e: ParseException) {
                    Debug.warn(e)
                    amiiboManager = null
                    Toasty(this@BrowserActivity).Short(R.string.amiibo_info_parse_error)
                }
                val gamesManager: GamesManager? = try {
                    getGamesManager(this@BrowserActivity)
                } catch (e: IOException) {
                    Debug.warn(e)
                    null
                } catch (e: JSONException) {
                    Debug.warn(e)
                    null
                } catch (e: ParseException) {
                    Debug.warn(e)
                    null
                }
                withContext(Dispatchers.Main) {
                    settings!!.amiiboManager = amiiboManager
                    settings!!.gamesManager = gamesManager
                    settings!!.notifyChanges()
                    managerStats
                }
            }
        }
    }

    private fun listFolders(rootFolder: File?): ArrayList<File?> {
        val folders = ArrayList<File?>()
        val files = rootFolder?.listFiles()
        if (files.isNullOrEmpty()) return folders
        for (file in files) {
            if (file.isDirectory) folders.add(file)
        }
        return folders
    }

    private fun loadFolders(rootFolder: File?) {
        loadingScope.launch {
            withContext(Dispatchers.IO) {
                val folders = listFolders(rootFolder)
                folders.sortWith { file1: File?, file2: File? ->
                    file1?.path!!.compareTo(file2?.path!!, ignoreCase = true)
                }
                withContext(Dispatchers.Main) {
                    settings?.folders = folders
                    settings?.notifyChanges()
                }
            }
        }
    }

    private fun isDirectoryHidden(rootFolder: File?, directory: File, recursive: Boolean): Boolean {
        if (null == rootFolder) return false
        return rootFolder.path != directory.path && (!recursive
                || (!rootFolder.path.startsWith(directory.path)
                && !directory.path.startsWith(rootFolder.path)))
    }

    private fun loadAmiiboFiles(rootFolder: File?, recursiveFiles: Boolean) {
        loadingScope.launch {
            withContext(Dispatchers.IO) {
                val amiiboFiles = listAmiibos(keyManager, rootFolder, recursiveFiles)
                val download = Storage.getDownloadDir(null)
                if (isDirectoryHidden(rootFolder, download, recursiveFiles))
                    amiiboFiles.addAll(listAmiibos(keyManager, download, true))
                val foomiibo = File(filesDir, "Foomiibo")
                amiiboFiles.addAll(listAmiibos(keyManager, foomiibo, true))
                withContext(Dispatchers.Main) {
                    hideFakeSnackbar()
                    settings?.amiiboFiles = amiiboFiles
                    settings?.notifyChanges()
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun loadAmiiboDocuments(rootFolder: DocumentFile?, recursiveFiles: Boolean) {
        loadingScope.launch {
            withContext(Dispatchers.IO) {
                val amiiboFiles = listAmiiboDocuments(
                    this@BrowserActivity, keyManager, rootFolder!!, recursiveFiles
                )
                if (amiiboFiles.isEmpty() && null == prefs.browserRootDocument()) {
                    onDocumentRequested()
                    return@withContext
                }
                val foomiibo = File(filesDir, "Foomiibo")
                amiiboFiles.addAll(listAmiibos(keyManager, foomiibo, true))
                withContext(Dispatchers.Main) {
                    hideFakeSnackbar()
                    settings?.amiiboFiles = amiiboFiles
                    settings?.notifyChanges()
                }
            }
        }
    }

    fun loadAmiiboBackground() {
        if (isDocumentStorage) {
            val rootDocument = DocumentFile.fromTreeUri(
                this@BrowserActivity, settings!!.browserRootDocument!!
            )
            loadAmiiboDocuments(rootDocument, settings!!.isRecursiveEnabled)
        } else {
            loadAmiiboFiles(settings!!.browserRootFolder!!, settings!!.isRecursiveEnabled)
        }
    }

    private var onDocumentTree = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
        val treeUri = result.data!!.data
        if (Debug.isNewer(Build.VERSION_CODES.KITKAT))
            contentResolver.takePersistableUriPermission(treeUri!!,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        val pickedDir = DocumentFile.fromTreeUri(this, treeUri!!)

        // List all existing files inside picked directory
        if (null != pickedDir) {
            settings!!.browserRootDocument = treeUri
            onStorageEnabled()
        }
    }

    override fun onBrowserSettingsChanged(
        newBrowserSettings: BrowserSettings?,
        oldBrowserSettings: BrowserSettings?
    ) {
        if (newBrowserSettings == null || oldBrowserSettings == null) return
        var folderChanged = !BrowserSettings.equals(
            newBrowserSettings.browserRootFolder,
            oldBrowserSettings.browserRootFolder
        )
        try {
            folderChanged = folderChanged || !BrowserSettings.equals(
                newBrowserSettings.browserRootDocument,
                oldBrowserSettings.browserRootDocument
            )
        } catch (ignored: Exception) { }
        if (newBrowserSettings.isRecursiveEnabled != oldBrowserSettings.isRecursiveEnabled) {
            oldBrowserSettings.amiiboFiles.clear()
            newBrowserSettings.amiiboFiles.clear()
            folderChanged = true
            onRecursiveFilesChanged()
        }
        if (!BrowserSettings.equals(
                newBrowserSettings.lastUpdatedAPI,
                oldBrowserSettings.lastUpdatedAPI
            )
        ) {
            loadAmiiboManager()
            folderChanged = true
        }
        if (folderChanged) {
            onRootFolderChanged(true)
            setFolderText(newBrowserSettings)
        } else {
            setFolderText(null)
        }
        if (newBrowserSettings.sort != oldBrowserSettings.sort) {
            onSortChanged()
        }
        if (!BrowserSettings.equals(
                newBrowserSettings.getFilter(FILTER.CHARACTER),
                oldBrowserSettings.getFilter(FILTER.CHARACTER)
            )
        ) {
            onFilterContentsChanged(FILTER.CHARACTER)
        }
        if (!BrowserSettings.equals(
                newBrowserSettings.getFilter(FILTER.GAME_SERIES),
                oldBrowserSettings.getFilter(FILTER.GAME_SERIES)
            )
        ) {
            onFilterContentsChanged(FILTER.GAME_SERIES)
        }
        if (!BrowserSettings.equals(
                newBrowserSettings.getFilter(FILTER.AMIIBO_SERIES),
                oldBrowserSettings.getFilter(FILTER.AMIIBO_SERIES)
            )
        ) {
            onFilterContentsChanged(FILTER.AMIIBO_SERIES)
        }
        if (!BrowserSettings.equals(
                newBrowserSettings.getFilter(FILTER.AMIIBO_TYPE),
                oldBrowserSettings.getFilter(FILTER.AMIIBO_TYPE)
            )
        ) {
            onFilterContentsChanged(FILTER.AMIIBO_TYPE)
        }
        if (!BrowserSettings.equals(
                newBrowserSettings.getFilter(FILTER.GAME_TITLES),
                oldBrowserSettings.getFilter(FILTER.GAME_TITLES)
            )
        ) {
            onFilterContentsChanged(FILTER.GAME_TITLES)
        }
        if (newBrowserSettings.amiiboView != oldBrowserSettings.amiiboView) {
            onViewChanged()
        }
        if (System.currentTimeMillis() >= oldBrowserSettings.lastUpdatedGit + 3600000) {
            updateManager = UpdateManager(this)
            if (BuildConfig.GOOGLE_PLAY) {
                updateManager?.setPlayUpdateListener(object : CheckPlayUpdateListener {
                    override fun onPlayUpdateFound(appUpdateInfo: AppUpdateInfo?) {
                        appUpdate = appUpdateInfo
                        if (BuildConfig.WEAR_OS)
                            onCreateWearOptionsMenu()
                        else invalidateOptionsMenu()
                    }
                })
            } else {
                updateManager?.setUpdateListener(object : CheckUpdateListener {
                    override fun onUpdateFound(downloadUrl: String?) {
                        updateUrl = downloadUrl
                        if (BuildConfig.WEAR_OS)
                            onCreateWearOptionsMenu()
                        else invalidateOptionsMenu()
                    }
                })
            }
            newBrowserSettings.lastUpdatedGit = System.currentTimeMillis()
        }
        prefs.browserRootFolder(Storage.getRelativePath(
            newBrowserSettings.browserRootFolder, prefs.preferEmulated()
        ))
        prefs.browserRootDocument(
            if (null != newBrowserSettings.browserRootDocument)
                newBrowserSettings.browserRootDocument.toString()
            else null
        )
        prefs.query(newBrowserSettings.query)
        prefs.sort(newBrowserSettings.sort)
        prefs.filterCharacter(newBrowserSettings.getFilter(FILTER.CHARACTER))
        prefs.filterGameSeries(newBrowserSettings.getFilter(FILTER.GAME_SERIES))
        prefs.filterAmiiboSeries(newBrowserSettings.getFilter(FILTER.AMIIBO_SERIES))
        prefs.filterAmiiboType(newBrowserSettings.getFilter(FILTER.AMIIBO_TYPE))
        prefs.filterGameTitles(newBrowserSettings.getFilter(FILTER.GAME_TITLES))
        prefs.browserAmiiboView(newBrowserSettings.amiiboView)
        prefs.imageNetwork(newBrowserSettings.imageNetworkSettings)
        prefs.recursiveFolders(newBrowserSettings.isRecursiveEnabled)
        prefs.lastUpdatedAPI(newBrowserSettings.lastUpdatedAPI)
        prefs.lastUpdatedGit(newBrowserSettings.lastUpdatedGit)
    }

    private fun onSortChanged() {
        if (null != settings) {
            when (SORT.valueOf(settings!!.sort)) {
                SORT.ID -> menuSortId?.isChecked = true
                SORT.NAME -> menuSortName?.isChecked = true
                SORT.CHARACTER -> menuSortCharacter?.isChecked = true
                SORT.GAME_SERIES -> menuSortGameSeries?.isChecked = true
                SORT.AMIIBO_SERIES -> menuSortAmiiboSeries?.isChecked = true
                SORT.AMIIBO_TYPE -> menuSortAmiiboType?.isChecked = true
                SORT.FILE_PATH -> menuSortFilePath?.isChecked = true
            }
        }
    }

    private fun onViewChanged() {
        if (null == menuViewSimple) return
        when (VIEW.valueOf(settings!!.amiiboView)) {
            VIEW.SIMPLE -> menuViewSimple!!.isChecked = true
            VIEW.COMPACT -> menuViewCompact!!.isChecked = true
            VIEW.LARGE -> menuViewLarge!!.isChecked = true
            VIEW.IMAGE -> menuViewImage!!.isChecked = true
        }
    }

    fun onRootFolderChanged(indicator: Boolean) {
        if (isDocumentStorage) {
            val rootDocument = DocumentFile.fromTreeUri(
                this, settings!!.browserRootDocument!!
            )
            if (!keyManager.isKeyMissing) {
                if (indicator) showFakeSnackbar(getString(R.string.refreshing_list))
                loadAmiiboDocuments(rootDocument, settings!!.isRecursiveEnabled)
            }
        } else {
            val rootFolder = settings!!.browserRootFolder
            if (!keyManager.isKeyMissing) {
                if (indicator) showFakeSnackbar(getString(R.string.refreshing_list))
                loadAmiiboFiles(rootFolder, settings!!.isRecursiveEnabled)
            }
            loadFolders(rootFolder)
        }
    }

    private fun onFilterContentsChanged(filter: FILTER) {
        val filterText = settings!!.getFilter(filter)
        val filterTag = when (filter) {
            FILTER.CHARACTER -> getString(R.string.filter_character)
            FILTER.GAME_SERIES -> getString(R.string.filter_game_series)
            FILTER.AMIIBO_SERIES -> getString(R.string.filter_amiibo_series)
            FILTER.AMIIBO_TYPE -> getString(R.string.filter_amiibo_type)
            FILTER.GAME_TITLES -> getString(R.string.filter_game_titles)
        }
        fragmentBrowser!!.addFilterItemView(filterText, filterTag) {
            settings!!.setFilter(filter, "")
            settings!!.notifyChanges()
            if (layout!!.currentItem == 0) setAmiiboStats()
        }
    }

    fun onFilterContentsLoaded() {
        onFilterContentsChanged(FILTER.CHARACTER)
        onFilterContentsChanged(FILTER.GAME_SERIES)
        onFilterContentsChanged(FILTER.AMIIBO_SERIES)
        onFilterContentsChanged(FILTER.AMIIBO_TYPE)
        onFilterContentsChanged(FILTER.GAME_TITLES)
    }

    private fun onRecursiveFilesChanged() {
        if (null == menuRecursiveFiles) return
        menuRecursiveFiles!!.isChecked = settings!!.isRecursiveEnabled
    }

    private fun launchEliteActivity(resultData: Intent?) {
        if (resultData!!.hasExtra(NFCIntent.EXTRA_SIGNATURE)) {
            showElitePage(resultData.extras)
        }
    }

    private val onQRCodeScanner = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || null == result.data) return@registerForActivityResult
        onRootFolderChanged(true)
    }

    private val onUpdateTagResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || null == result.data) return@registerForActivityResult
        if (NFCIntent.ACTION_NFC_SCANNED != result.data!!.action
            && NFCIntent.ACTION_UPDATE_TAG != result.data!!.action
            && NFCIntent.ACTION_EDIT_COMPLETE != result.data!!.action
        ) return@registerForActivityResult


        // If we're supporting, didn't arrive from, but scanned an N2...
        if (prefs.eliteEnabled() && result.data!!.hasExtra(NFCIntent.EXTRA_SIGNATURE)) {
            launchEliteActivity(result.data)
        } else {
            updateAmiiboView(result.data?.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA))
            // toolbar.getMenu().findItem(R.id.mnu_write).setEnabled(false);
        }
    }

    private fun deleteAmiiboFile(amiiboFile: AmiiboFile?) {
        if (null != amiiboFile?.filePath) {
            val relativeFile = Storage.getRelativePath(
                amiiboFile.filePath, prefs.preferEmulated()
            )
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.warn_delete_file, relativeFile))
                .setPositiveButton(R.string.delete) { dialog: DialogInterface, _: Int ->
                    amiiboContainer?.isGone = true
                    amiiboFile.filePath?.delete()
                    IconifiedSnackbar(this, layout).buildSnackbar(
                        getString(R.string.delete_file, relativeFile), Snackbar.LENGTH_SHORT
                    ).show()
                    onRootFolderChanged(true)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .show()
        } else {
            Toasty(this).Short(R.string.delete_missing)
        }
    }

    private fun deleteAmiiboDocument(amiiboFile: AmiiboFile?) {
        if (null != amiiboFile?.docUri) {
            val relativeDocument = Storage.getRelativeDocument(amiiboFile.docUri!!.uri)
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.warn_delete_file, relativeDocument))
                .setPositiveButton(R.string.delete) { dialog: DialogInterface, _: Int ->
                    amiiboContainer?.isGone = true
                    amiiboFile.docUri?.delete()
                    IconifiedSnackbar(this, layout).buildSnackbar(
                        getString(R.string.delete_file, relativeDocument), Snackbar.LENGTH_SHORT
                    ).show()
                    onRootFolderChanged(true)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .show()
        } else {
            deleteAmiiboFile(amiiboFile)
        }
    }

    private val imageTarget: CustomTarget<Bitmap?> = object : CustomTarget<Bitmap?>() {
        override fun onLoadFailed(errorDrawable: Drawable?) {
            imageAmiibo?.isGone = true
        }

        override fun onLoadCleared(placeholder: Drawable?) {}
        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
            imageAmiibo?.maxHeight = Resources.getSystem().displayMetrics.heightPixels / 3
            imageAmiibo?.requestLayout()
            imageAmiibo?.setImageBitmap(resource)
            imageAmiibo?.isVisible = true
        }
    }

    fun setAmiiboInfoText(textView: TextView?, text: CharSequence?, hasTagInfo: Boolean) {
        textView?.isGone = hasTagInfo
        if (!hasTagInfo) {
            if (!text.isNullOrEmpty()) {
                textView?.text = text
                textView?.isEnabled = true
            } else {
                textView?.text = getString(R.string.unknown)
                textView?.isEnabled = false
            }
        }
    }

    @JvmOverloads
    fun updateAmiiboView(tagData: ByteArray?, amiiboFile: AmiiboFile? = clickedAmiibo) {
        amiiboContainer?.post {
            amiiboContainer!!.alpha = 0f
            amiiboContainer!!.isVisible = true
            amiiboContainer!!.animate().alpha(1f).setDuration(150).setListener(null)
            onCreateToolbarMenu(toolbar, tagData, amiiboFile)
            var amiiboId: Long = -1
            var tagInfo: String? = null
            var amiiboHexId = ""
            var amiiboName = ""
            var amiiboSeries = ""
            var amiiboType = ""
            var gameSeries = ""
            // String character = "";
            val amiiboImageUrl: String?
            if (tagData?.isNotEmpty() == true) {
                try {
                    amiiboId = Amiibo.dataToId(tagData)
                } catch (e: Exception) {
                    Debug.info(e)
                }
            }
            if (amiiboId == -1L) {
                tagInfo = getString(R.string.read_error)
                amiiboImageUrl = null
            } else if (amiiboId == 0L) {
                tagInfo = getString(R.string.blank_tag)
                amiiboImageUrl = null
            } else {
                var amiibo: Amiibo? = null
                val amiiboManager = settings?.amiiboManager
                if (null != amiiboManager) {
                    amiibo = amiiboManager.amiibos[amiiboId]
                    if (null == amiibo)
                        amiibo = Amiibo(amiiboManager, amiiboId, null, null)
                }
                if (null != amiibo) {
                    amiiboHexId = Amiibo.idToHex(amiibo.id)
                    amiiboImageUrl = amiibo.imageUrl
                    if (null != amiibo.name) amiiboName = amiibo.name!!
                    if (null != amiibo.amiiboSeries) amiiboSeries = amiibo.amiiboSeries!!.name
                    if (null != amiibo.amiiboType) amiiboType = amiibo.amiiboType!!.name
                    if (null != amiibo.gameSeries) gameSeries = amiibo.gameSeries!!.name
                } else {
                    amiiboHexId = Amiibo.idToHex(amiiboId)
                    tagInfo = "ID: $amiiboHexId"
                    amiiboImageUrl = Amiibo.getImageUrl(amiiboId)
                }
            }
            val hasTagInfo = null != tagInfo
            amiiboInfo?.isGone = hasTagInfo
            if (hasTagInfo) {
                setAmiiboInfoText(txtError, tagInfo, false)
            } else {
                txtError?.isGone = true
            }
            setAmiiboInfoText(txtName, amiiboName, hasTagInfo)
            setAmiiboInfoText(txtTagId, amiiboHexId, hasTagInfo)
            setAmiiboInfoText(txtAmiiboSeries, amiiboSeries, hasTagInfo)
            setAmiiboInfoText(txtAmiiboType, amiiboType, hasTagInfo)
            setAmiiboInfoText(txtGameSeries, gameSeries, hasTagInfo)
            // setAmiiboInfoText(txtCharacter, character, hasTagInfo);
            try {
                val txtUsage = findViewById<TextView>(R.id.txtUsage)
                getGameCompatibility(txtUsage, tagData)
                txtUsage.isGone = true
                val label = findViewById<TextView>(R.id.txtUsageLabel)
                label.setOnClickListener {
                    if (txtUsage.isVisible) {
                        txtUsage.isGone = true
                        label.setText(R.string.game_titles_view)
                    } else {
                        txtUsage.isVisible = true
                        label.setText(R.string.game_titles_hide)
                    }
                }
            } catch (ex: Exception) {
                Debug.warn(ex)
            }
            if (hasSpoofData(amiiboHexId)) txtTagId?.isEnabled = false
            if (null != imageAmiibo) {
                imageAmiibo!!.isGone = true
                GlideApp.with(imageAmiibo!!).clear(imageAmiibo!!)
                if (!amiiboImageUrl.isNullOrEmpty()) {
                    GlideApp.with(imageAmiibo!!).asBitmap().load(amiiboImageUrl).into(imageTarget)
                }
                val amiiboTagId = amiiboId
                imageAmiibo!!.setOnClickListener {
                    val bundle = Bundle()
                    bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiiboTagId)
                    val intent = Intent(this@BrowserActivity, ImageActivity::class.java)
                    intent.putExtras(bundle)
                    startActivity(intent)
                }
            }
        }
    }

    val columnCount: Int
        get() {
            val metrics = DisplayMetrics()
            val mWindowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val columns = if (Debug.isNewer(Build.VERSION_CODES.S)) {
                val bounds: Rect = mWindowManager.currentWindowMetrics.bounds
                ((bounds.width() / (resources.configuration.densityDpi / 160)) + 0.5) / 112
            } else @Suppress("DEPRECATION") {
                if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR1))
                    mWindowManager.defaultDisplay.getRealMetrics(metrics)
                else
                    mWindowManager.defaultDisplay.getMetrics(metrics)
                ((metrics.widthPixels / metrics.density) + 0.5) / 112
            }
            return if (columns < 1) 3 else columns.toInt()
        }

    @Suppress("SameParameterValue")
    private fun setViewPagerSensitivity(viewPager: ViewPager2?, sensitivity: Int) {
        try {
            val ff = ViewPager2::class.java.getDeclaredField("mRecyclerView")
            ff.isAccessible = true
            val recyclerView = ff[viewPager] as RecyclerView
            val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
            touchSlopField.isAccessible = true
            val touchSlop = touchSlopField[recyclerView] as Int
            touchSlopField[recyclerView] = touchSlop * sensitivity
        } catch (e: NoSuchFieldException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }
    }

    private val managerStats: Unit
        get() {
            val foomiiboSlider = fragmentBrowser!!.view
            val characterStats = findViewById<TextView>(R.id.stats_character)
            val amiiboTypeStats = findViewById<TextView>(R.id.stats_amiibo_type)
            val amiiboTitleStats = findViewById<TextView>(R.id.stats_amiibo_titles)
            val amiiboManager = settings!!.amiiboManager
            val hasAmiibo = null != amiiboManager
            if (null != foomiiboSlider) {
                val foomiiboStats = foomiiboSlider.findViewById<TextView>(R.id.divider_text)
                foomiiboStats.text = getString(
                    R.string.number_foomiibo,
                    if (hasAmiibo) amiiboManager!!.amiibos.size else 0
                )
            }
            characterStats.text = getString(
                R.string.number_character,
                if (hasAmiibo) amiiboManager!!.characters.size else 0
            )
            amiiboTypeStats.text = getString(
                R.string.number_type,
                if (hasAmiibo) amiiboManager!!.amiiboTypes.size else 0
            )
            if (hasAmiibo) {
                characterStats.setOnClickListener {
                    val items = ArrayList<Character>()
                    for (character in amiiboManager!!.characters.values) {
                        if (!items.contains(character)) items.add(character)
                    }
                    items.sort()
                    android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.pref_amiibo_characters)
                        .setAdapter(object : ArrayAdapter<Character>(
                            this, android.R.layout.simple_list_item_2, android.R.id.text1, items
                        ) {
                            override fun getView(
                                position: Int, convertView: View?, parent: ViewGroup
                            ): View {
                                val view = super.getView(position, convertView, parent)
                                val text1 = view.findViewById<TextView>(android.R.id.text1)
                                val text2 = view.findViewById<TextView>(android.R.id.text2)
                                val character = getItem(position)
                                text1.text = character!!.name
                                val gameSeries = character.gameSeries
                                text2.text = gameSeries?.name ?: ""
                                return view
                            }
                        }, null)
                        .setPositiveButton(R.string.close, null)
                        .show()
                }
                amiiboTypeStats.setOnClickListener {
                    val amiiboTypes = ArrayList(
                        amiiboManager!!.amiiboTypes.values
                    )
                    amiiboTypes.sort()
                    val items = ArrayList<String>()
                    for (amiiboType in amiiboTypes) {
                        if (!items.contains(amiiboType.name)) items.add(amiiboType.name)
                    }
                    android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.pref_amiibo_types)
                        .setAdapter(ArrayAdapter(
                                this, android.R.layout.simple_list_item_1, items
                        ), null)
                        .setPositiveButton(R.string.close, null)
                        .show()
                }
            }
            val gamesManager = settings?.gamesManager
            val hasGames = null != amiiboManager
            amiiboTitleStats.text = getString(
                R.string.number_titles, if (hasGames) gamesManager?.gameTitles?.size else 0
            )
            if (hasGames) {
                amiiboTitleStats.setOnClickListener {
                    val amiiboTitles = gamesManager!!.gameTitles
                    val items = ArrayList<String>()
                    for (amiiboTitle in amiiboTitles) {
                        if (!items.contains(amiiboTitle.name)) items.add(amiiboTitle.name)
                    }
                    items.sort()
                    android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.pref_amiibo_titles)
                        .setAdapter(ArrayAdapter(
                                this, android.R.layout.simple_list_item_1, items
                        ), null)
                        .setPositiveButton(R.string.close, null)
                        .show()
                }
            }
        }

    private fun getAdapterStats(amiiboManager: AmiiboManager): IntArray {
        if (null != amiibosView && null != amiibosView!!.adapter
            && amiibosView!!.adapter is BrowserAdapter) {
            val adapter = amiibosView!!.adapter as BrowserAdapter?
            val size = adapter!!.itemCount
            var count = 0
            for (amiibo in amiiboManager.amiibos.values) {
                for (x in 0 until size) {
                    if (amiibo.id == adapter.getItemId(x)) {
                        count += 1
                        break
                    }
                }
            }
            return intArrayOf(size, count)
        }
        return intArrayOf(0, 0)
    }

    private fun setAmiiboStats() {
        statsHandler.removeCallbacksAndMessages(null)
        currentFolderView!!.post {
            val size = settings!!.amiiboFiles.size
            if (size <= 0) return@post
            currentFolderView!!.gravity = Gravity.CENTER
            val amiiboManager = settings!!.amiiboManager
            if (null != amiiboManager) {
                var count = 0
                if (!TextUtils.isEmpty(settings?.query)) {
                    val stats = getAdapterStats(amiiboManager)
                    currentFolderView!!.text = getString(
                        R.string.amiibo_collected,
                        stats[0], stats[1], getQueryCount(settings?.query)
                    )
                } else if (!settings!!.isFilterEmpty) {
                    val stats = getAdapterStats(amiiboManager)
                    currentFolderView!!.text = getString(
                        R.string.amiibo_collected,
                        stats[0], stats[1], filteredCount
                    )
                } else {
                    for (amiibo in amiiboManager.amiibos.values) {
                        for (amiiboFile in settings!!.amiiboFiles) {
                            if (amiibo.id == amiiboFile?.id) {
                                count += 1
                                break
                            }
                        }
                    }
                    currentFolderView!!.text = getString(
                        R.string.amiibo_collected,
                        size, count, amiiboManager.amiibos.size
                    )
                }
            } else {
                currentFolderView!!.text = getString(R.string.files_displayed, size)
            }
        }
    }

    private fun setFolderText(textSettings: BrowserSettings?) {
        if (null != textSettings) {
            val relativePath: String = if (isDocumentStorage) {
                Storage.getRelativeDocument(textSettings.browserRootDocument)
            } else {
                val rootFolder = textSettings.browserRootFolder
                val relativeRoot = Storage.getRelativePath(
                    rootFolder, prefs.preferEmulated()
                )
                if (relativeRoot.length > 1) relativeRoot else rootFolder!!.absolutePath
            }
            currentFolderView?.gravity = Gravity.CENTER_VERTICAL
            currentFolderView?.text = relativePath
            statsHandler.postDelayed({ setAmiiboStats() }, 3000)
        } else {
            setAmiiboStats()
        }
    }

    private fun showFakeSnackbar(msg: String) {
        fakeSnackbar?.post {
            fakeSnackbarItem?.visibility = View.INVISIBLE
            fakeSnackbarText?.text = msg
            fakeSnackbar?.isVisible = true
        }
    }

    private fun hideFakeSnackbar() {
        runOnUiThread {
            if (fakeSnackbar?.isVisible == true) {
                val animate = TranslateAnimation(
                    0f, 0f, 0f, (-fakeSnackbar!!.height).toFloat()
                )
                animate.duration = 150
                animate.fillAfter = false
                fakeSnackbar?.setAnimationListener(object : AnimatedLinearLayout.AnimationListener {
                    override fun onAnimationStart(layout: AnimatedLinearLayout?) {}
                    override fun onAnimationEnd(layout: AnimatedLinearLayout?) {
                        fakeSnackbar!!.clearAnimation()
                        layout!!.setAnimationListener(null)
                        fakeSnackbar!!.isGone = true
                    }
                })
                fakeSnackbar?.startAnimation(animate)
            }
        }
    }

    private fun collapseBottomSheet() {
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun hideBottomSheet() {
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        sheetHandler.postDelayed({
            bottomSheetBehavior?.isHideable = true
            bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_HIDDEN)
        }, TagMo.uiDelay.toLong())
    }

    private fun hideBrowserInterface() {
        val params = nfcFab.layoutParams as CoordinatorLayout.LayoutParams
        val behavior = params.behavior as FloatingActionButton.Behavior?
        if (null != behavior) behavior.isAutoHideEnabled = false
        nfcFab.hide()
        hideBottomSheet()
    }

    private fun showActionButton() {
        nfcFab.show()
        val params = nfcFab.layoutParams as CoordinatorLayout.LayoutParams
        val behavior = params.behavior as FloatingActionButton.Behavior?
        if (null != behavior) behavior.isAutoHideEnabled = true
    }

    fun showDonationPanel() {
        donationManager.onSendDonationClicked()
    }

    private fun showBrowserInterface() {
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
        sheetHandler.postDelayed(
            { bottomSheetBehavior!!.setHideable(false) },
            TagMo.uiDelay.toLong()
        )
        showActionButton()
    }

    fun closePrefsDrawer(): Boolean {
        if (prefsDrawer?.isDrawerOpen(GravityCompat.START) == true) {
            prefsDrawer?.closeDrawer(GravityCompat.START)
            return true
        }
        return false
    }

    private fun onLoadSettingsFragment() {
        if (null == fragmentSettings) fragmentSettings = SettingsFragment()
        if (!fragmentSettings!!.isAdded) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.preferences, fragmentSettings!!)
                .commit()
        }
    }

    private fun onShowSettingsFragment() {
        if (BuildConfig.WEAR_OS) {
            layout?.post {
                layout?.setCurrentItem(if (prefs.flaskEnabled()) 2 else 1, false)
            }
        } else {
            prefsDrawer?.openDrawer(GravityCompat.START)
        }
    }

    fun showElitePage(extras: Bundle?) {
        if (!prefs.eliteEnabled()) return
        layout?.post {
            fragmentElite!!.arguments = extras
            layout!!.setCurrentItem(1, true)
        }
    }

    fun showWebsite(address: String?) {
        layout?.setCurrentItem(pagerAdapter.itemCount - 1, true)
        pagerAdapter.website.loadWebsite(address)
    }

    private fun keyNameMatcher(name: String): Boolean {
        val isValid = binFileMatches(name)
        return name.lowercase().endsWith("retail.bin") ||
                isValid && (name.lowercase().startsWith("locked")
                || name.lowercase().startsWith("unfixed"))
    }

    private suspend fun locateKeyFilesRecursive(rootFolder: File?) {
        withContext(Dispatchers.IO) {
            val files = rootFolder?.listFiles { _: File?, name: String -> keyNameMatcher(name) }
            if (!files.isNullOrEmpty()) {
                for (file in files) {
                    try {
                        FileInputStream(file).use { inputStream ->
                            try {
                                keyManager.evaluateKey(inputStream)
                            } catch (ex: Exception) {
                                withContext(Dispatchers.Main) { onShowSettingsFragment() }
                            }
                            withContext(Dispatchers.Main) { hideFakeSnackbar() }
                        }
                    } catch (e: Exception) {
                        Debug.warn(e)
                    }
                }
            } else {
                val directories = rootFolder?.listFiles()
                if (directories.isNullOrEmpty()) return@withContext
                for (directory in directories) {
                    if (directory.isDirectory) locateKeyFilesRecursive(directory)
                }
            }
        }
    }

    private fun locateKeyFiles() {
        loadingScope.launch {
            withContext(Dispatchers.IO) {
                val files = Storage.getDownloadDir(null)
                    .listFiles { _: File?, name: String -> keyNameMatcher(name) }
                if (!files.isNullOrEmpty()) {
                    for (file in files) {
                        try {
                            FileInputStream(file).use { inputStream ->
                                try {
                                    keyManager.evaluateKey(inputStream)
                                } catch (ex: Exception) {
                                    withContext(Dispatchers.Main) { onShowSettingsFragment() }
                                }
                                withContext(Dispatchers.Main) { hideFakeSnackbar() }
                            }
                        } catch (e: Exception) {
                            Debug.warn(e)
                        }
                    }
                } else {
                    locateKeyFilesRecursive(Storage.getFile(prefs.preferEmulated()))
                }
                if (keyManager.isKeyMissing) {
                    withContext(Dispatchers.Main) {
                        hideFakeSnackbar()
                        onShowSettingsFragment()
                    }
                }
            }
        }
    }

    private var onRequestStorage = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { permissions: Map<String, Boolean> ->
        var isStorageEnabled = true
        if (BuildConfig.WEAR_OS) {
            isStorageEnabled =
                java.lang.Boolean.TRUE == permissions[Manifest.permission.READ_EXTERNAL_STORAGE]
        } else {
            for ((_, value) in permissions) {
                if (!value) isStorageEnabled = false
            }
        }
        if (isStorageEnabled) onStorageEnabled() else onDocumentEnabled()
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    var onRequestScopedStorage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Environment.isExternalStorageManager()) {
            settings!!.browserRootDocument = null
            settings!!.notifyChanges()
            onStorageEnabled()
        } else {
            onDocumentEnabled()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    fun requestScopedStorage() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            try {
                intent.data = Uri.parse(String.format("package:%s", packageName))
            } catch (e: Exception) {
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            }
            onRequestScopedStorage.launch(intent)
        } catch (anf: ActivityNotFoundException) {
            onDocumentEnabled()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    val onRequestInstall = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (null != prefs.downloadUrl()) {
            if (packageManager.canRequestPackageInstalls())
                updateManager?.installUpdateTask(prefs.downloadUrl())
            prefs.remove(prefs.downloadUrl)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action
            || NfcAdapter.ACTION_TECH_DISCOVERED == intent.action
            || NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            if (keyManager.isKeyMissing) return
            loadingExecutor.execute {
                tagScanner.onTagDiscovered(this@BrowserActivity, intent)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (BuildConfig.WEAR_OS) {
                    if (null != bottomSheet && BottomSheetBehavior.STATE_EXPANDED == bottomSheet?.state) {
                        bottomSheet?.setState(BottomSheetBehavior.STATE_COLLAPSED)
                    } else if (amiiboContainer?.isVisible == true) {
                        amiiboContainer?.isGone = true
                    } else if (layout?.currentItem != 0) {
                        if (layout?.currentItem == pagerAdapter.itemCount - 1
                            && pagerAdapter.website.hasGoneBack()) return
                        else layout?.setCurrentItem(0, true)
                    } else {
                        finishAffinity()
                    }
                } else {
                    if (!closePrefsDrawer()) {
                        if (null != bottomSheet && BottomSheetBehavior.STATE_EXPANDED == bottomSheet?.state) {
                            bottomSheet?.setState(BottomSheetBehavior.STATE_COLLAPSED)
                        } else if (amiiboContainer?.isVisible == true) {
                            amiiboContainer?.isGone = true
                        } else if (layout?.currentItem != 0) {
                            if (layout?.currentItem == pagerAdapter.itemCount - 1
                                && pagerAdapter.website.hasGoneBack()) return
                                else layout?.setCurrentItem(0, true)
                        } else {
                            finishAffinity()
                        }
                    }
                }
            }
        })
    }

    companion object {
        private val PERMISSIONS_STORAGE = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}