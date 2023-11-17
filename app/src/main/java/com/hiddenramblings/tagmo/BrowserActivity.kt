package com.hiddenramblings.tagmo

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import android.provider.Settings
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
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.database.getStringOrNull
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
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.hiddenramblings.tagmo.*
import com.hiddenramblings.tagmo.BrowserSettings.*
import com.hiddenramblings.tagmo.NFCIntent.FilterComponent
import com.hiddenramblings.tagmo.adapter.BrowserAdapter
import com.hiddenramblings.tagmo.amiibo.*
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.binFileMatches
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.getAmiiboManager
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.hasSpoofData
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.listAmiiboDocuments
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.listAmiiboFiles
import com.hiddenramblings.tagmo.amiibo.games.GamesManager
import com.hiddenramblings.tagmo.amiibo.games.GamesManager.Companion.getGamesManager
import com.hiddenramblings.tagmo.amiibo.tagdata.TagDataEditor
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.eightbit.request.ImageTarget
import com.hiddenramblings.tagmo.eightbit.util.Zip
import com.hiddenramblings.tagmo.eightbit.view.AnimatedLinearLayout
import com.hiddenramblings.tagmo.eightbit.widget.FABulous
import com.hiddenramblings.tagmo.eightbit.widget.ProgressAlert
import com.hiddenramblings.tagmo.fragment.BrowserFragment
import com.hiddenramblings.tagmo.fragment.SettingsFragment
import com.hiddenramblings.tagmo.hexcode.HexCodeViewer
import com.hiddenramblings.tagmo.nfctech.Flipper.toNFC
import com.hiddenramblings.tagmo.nfctech.Foomiibo
import com.hiddenramblings.tagmo.nfctech.NfcActivity
import com.hiddenramblings.tagmo.nfctech.NfcByte
import com.hiddenramblings.tagmo.nfctech.ScanTag
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.hiddenramblings.tagmo.nfctech.TagArray.withRandomSerials
import com.hiddenramblings.tagmo.nfctech.TagReader
import com.hiddenramblings.tagmo.qrcode.QRCodeScanner
import com.hiddenramblings.tagmo.update.UpdateManager
import com.hiddenramblings.tagmo.viewpager.*
import com.hiddenramblings.tagmo.wave9.DimensionActivity
import com.hiddenramblings.tagmo.widget.Toasty
import com.shawnlin.numberpicker.NumberPicker
import eightbitlab.com.blurview.BlurView
import kotlinx.coroutines.*
import org.json.JSONException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.ParseException
import java.util.*


class BrowserActivity : AppCompatActivity(), BrowserSettingsListener,
    BrowserAdapter.OnAmiiboClickListener {
    private val prefs: Preferences by lazy { Preferences(applicationContext) }
    val keyManager: KeyManager by lazy { KeyManager(this) }

    var filteredCount = 0
    private var clickedAmiibo: AmiiboFile? = null
    var settings: BrowserSettings? = null
        private set
    private var ignoreTagId = false
    private var updateManager: UpdateManager? = null
    private var fragmentSettings: SettingsFragment? = null
    private var browserSheet: BottomSheetBehavior<View>? = null
    var reloadTabCollection = false
    private var prefsDrawer: DrawerLayout? = null
    private var settingsPage: CoordinatorLayout? = null
    private var animationArray: ArrayList<ValueAnimator>? = null
    private var fakeSnackbar: AnimatedLinearLayout? = null
    private var fakeSnackbarText: TextView? = null
    private var fakeSnackbarItem: AppCompatButton? = null
    lateinit var viewPager: ViewPager2
        private set
    private var scrollListener: ScrollListener? = null
    private var snackbarListener: SnackbarListener? = null
    private val pagerAdapter = NavPagerAdapter(this)
    private lateinit var nfcFab: FABulous
    private var amiibosView: RecyclerView? = null
    private var foomiiboView: RecyclerView? = null
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
    // private TextView txtCharacter? = null
    private var txtAmiiboType: TextView? = null
    private var txtAmiiboSeries: TextView? = null
    private var imageAmiibo: AppCompatImageView? = null

    private val tagScanner = ScanTag()
    private val donationManager = DonationManager(this)

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.WEAR_OS) {
            supportActionBar?.hide()
        } else {
            supportActionBar?.let {
                it.setDisplayShowHomeEnabled(true)
                it.setDisplayHomeAsUpEnabled(true)
                it.setHomeAsUpIndicator(R.drawable.ic_menu_24)
            }
        }
        setContentView(R.layout.activity_browser)

        fakeSnackbar = findViewById(R.id.fake_snackbar)
        fakeSnackbarText = findViewById(R.id.snackbar_text)
        fakeSnackbarItem = findViewById(R.id.snackbar_item)
        viewPager = findViewById(R.id.browser_pager)
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
        if (Version.isLowerThan(Build.VERSION_CODES.M)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        Foomiibo.directory.mkdirs()
        if (null == settings) settings = BrowserSettings()
        settings?.addChangeListener(this)

        if (!BuildConfig.WEAR_OS) {
            updateManager = UpdateManager(this)
            settings?.lastUpdatedGit = System.currentTimeMillis()
            updateManager?.setUpdateListener(object : UpdateManager.UpdateListener {
                override fun onUpdateFound() {
                    if (BuildConfig.WEAR_OS) onCreateWearOptionsMenu() else invalidateOptionsMenu()
                }
            })
        }
        val intent = intent
        intent?.let {
            if (componentName == FilterComponent) {
                startActivity(Intent(this, BrowserActivity::class.java).apply {
                    it.action?.let { action = it}
                    it.extras?.let { putExtras(it) }
                    it.data?.let { data = it }
                })
            }
        }

        nfcFab = findViewById<FABulous>(R.id.nfc_fab).apply {
            (behavior as FloatingActionButton.Behavior).isAutoHideEnabled = false
            loadSavedPosition(prefs)
            setOnMoveListener(object : FABulous.OnViewMovedListener {
                override fun onActionMove(x: Float, y: Float) {
                    prefs.fabulousX(x)
                    prefs.fabulousY(y)
                }
            })
        }

        viewPager.keepScreenOn = BuildConfig.WEAR_OS
        viewPager.adapter = pagerAdapter
        viewPager.isUserInputEnabled = TagMo.isUserInputEnabled
        if (TagMo.isUserInputEnabled) {
            viewPager.setPageTransformer(DepthTransformer())
            setViewPagerSensitivity(viewPager, 4)
        }
        amiibosView = pagerAdapter.browser.browserContent
        foomiiboView = pagerAdapter.browser.foomiiboContent
        browserSheet = pagerAdapter.browser.bottomSheet

        viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            @SuppressLint("NewApi")
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                nfcFab.hide()
                val isEliteEnabled = prefs.eliteEnabled()
                if (BuildConfig.WEAR_OS) {
                    when (position) {
                        0 -> {
                            nfcFab.show()
                            pagerAdapter.browser.run {
                                amiibosView = browserContent
                                foomiiboView = foomiiboContent?.apply {
                                    layoutManager = getFileLayoutManager()
                                }
                                browserSheet = bottomSheet
                            }
                        }
                        2 -> {
                            if (isEliteEnabled) {
                                nfcFab.show()
                            } else {
                                pagerAdapter.gattSlots.run {
                                    delayedBluetoothEnable()
                                    amiibosView = gattContent
                                    browserSheet = bottomSheet
                                }
                            }
                        }
                        3 -> {
                            pagerAdapter.gattSlots.run {
                                delayedBluetoothEnable()
                                amiibosView = gattContent
                                browserSheet = bottomSheet
                            }
                        }
                        else -> {}
                    }
                } else {
                    when (position) {
                        0 -> {
                            nfcFab.show()
                            setTitle(R.string.tagmo)
                            pagerAdapter.browser.run {
                                amiibosView = browserContent
                                foomiiboView = foomiiboContent?.apply {
                                    layoutManager = getFileLayoutManager()
                                }
                                browserSheet = bottomSheet
                            }
                        }
                        1 -> {
                            setTitle(R.string.guides)
                        }
                        2 -> {
                            if (isEliteEnabled) {
                                nfcFab.show()
                                setTitle(R.string.elite_n2)
                                pagerAdapter.eliteBanks.run {
                                    amiibosView = eliteContent
                                    browserSheet = bottomSheet
                                }
                            } else {
                                setTitle(R.string.gatt_title)
                                pagerAdapter.gattSlots.run {
                                    delayedBluetoothEnable()
                                    amiibosView = gattContent
                                    browserSheet = bottomSheet
                                }
                            }
                        }
                        3 -> {
                            setTitle(R.string.gatt_title)
                            pagerAdapter.gattSlots.run {
                                delayedBluetoothEnable()
                                amiibosView = gattContent
                                browserSheet = bottomSheet
                            }
                        }
                        else -> {}
                    }
                }
                amiibosView?.layoutManager = getFileLayoutManager()
                if (BuildConfig.WEAR_OS) onCreateWearOptionsMenu() else invalidateOptionsMenu()
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) scrollListener?.onScrollComplete()
                super.onPageScrollStateChanged(state)
            }
        })

        onLoadSettingsFragment()
        findViewById<TextView>(R.id.build_text).apply {
            movementMethod = LinkMovementMethod.getInstance()
            text = TagMo.versionLabelLinked
        }

        findViewById<CoordinatorLayout>(R.id.coordinator).apply {
            if (amiiboContainer is BlurView) {
                (amiiboContainer as BlurView).setupWith(this)
                    .setFrameClearDrawable(window.decorView.background)
                    .setBlurRadius(2f).setBlurAutoUpdate(true)
            }
        }

        if (BuildConfig.WEAR_OS) {
            onRequestStorage.launch(PERMISSIONS_STORAGE)
        } else {
            requestStoragePermission()
            try {
                packageManager.getPackageInfo("com.hiddenramblings.tagmo", PackageManager.GET_META_DATA)
                AlertDialog.Builder(this)
                    .setTitle(R.string.conversion_title)
                    .setMessage(R.string.conversion_message)
                    .setPositiveButton(R.string.proceed) { _: DialogInterface?, _: Int ->
                        startActivity(Intent(Intent.ACTION_DELETE).setData(
                            Uri.parse("package:com.hiddenramblings.tagmo")
                        ))
                    }.show()
            } catch (ignored: PackageManager.NameNotFoundException) { }
        }

        onCreateMainMenuLayout()

        if (BuildConfig.WEAR_OS) {
            onCreateWearOptionsMenu()
        } else {
            prefsDrawer = findViewById(R.id.drawer_layout)
            val settingsBanner = findViewById<TextView>(R.id.donation_banner)
            prefsDrawer?.addDrawerListener(object : SimpleDrawerListener() {
                override fun onDrawerOpened(drawerView: View) {
                    settingsBanner.isVisible = TagMo.hasSubscription
                    onTextColorAnimation(settingsBanner, 0)
                    super.onDrawerOpened(drawerView)
                    if (updateManager?.hasPendingUpdate() == true) {
                        findViewById<View>(R.id.build_layout).setOnClickListener {
                            closePrefsDrawer()
                            updateManager?.onUpdateRequested()
                        }
                    } else {
                        findViewById<View>(R.id.build_layout).setOnClickListener {
                            closePrefsDrawer()
                            showWebsite("https://github.com/HiddenRamblings/TagMo")
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

        val popup = if (Version.isLollipopMR)
            PopupMenu(this, nfcFab, Gravity.END, 0, R.style.PopupMenu)
        else
            PopupMenu(this, nfcFab)
        try {
            for (field in popup.javaClass.declaredFields) {
                if ("mPopup" == field.name) {
                    field.isAccessible = true
                    field[popup]?.let {
                        val setForceIcons = Class.forName(it.javaClass.name)
                            .getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                        setForceIcons.invoke(it, true)
                    }
                    break
                }
            }
        } catch (e: Exception) { Debug.warn(e) }
        popup.menuInflater.inflate(R.menu.action_menu, popup.menu)
        nfcFab.setOnClickListener { showPopupMenu(popup) }

        findViewById<View>(R.id.amiiboContainer).setOnClickListener {
            amiiboContainer?.isGone = true
        }

        if (null != intent && null != intent.action) {
            if (intent.action == Intent.ACTION_SEND) {
                processIncomingUri(intent, intent.parcelable(Intent.EXTRA_STREAM) as Uri?)
            } else if (Intent.ACTION_VIEW == intent.action) {
                try {
                    if (null != intent.clipData) {
                        intent.clipData?.run {
                            for (i in 0 until this.itemCount) {
                                val uri = this.getItemAt(i).uri
                                processIncomingUri(intent, uri)
                            }
                        }
                    } else {
                        intent.data?.let { uri ->
                            processIncomingUri(intent, uri)
                        }
                    }
                } catch (ignored: Exception) { }
            }
        }

        loadPTagKeyManager()

        if (!BuildConfig.WEAR_OS) {
            donationManager.retrieveDonationMenu()
            findViewById<View>(R.id.donate_layout).setOnClickListener {
                closePrefsDrawer()
                donationManager.onSendDonationClicked()
            }
        }
    }

    private fun getFileLayoutManager() : RecyclerView.LayoutManager {
        return if (settings?.amiiboView == VIEW.IMAGE.value)
            GridLayoutManager(this@BrowserActivity, columnCount)
        else
            LinearLayoutManager(this@BrowserActivity)
    }

    private fun onTextColorAnimation(textView: TextView?, index: Int) {
        if (textView?.isVisible == true && !animationArray.isNullOrEmpty()) {
            val colorAnimation: ValueAnimator? = animationArray?.get(index)
            colorAnimation?.addUpdateListener { animator ->
                textView.setTextColor(animator.animatedValue as Int)
            }
            colorAnimation?.duration = 250
            colorAnimation?.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animation.removeAllListeners()
                    var nextIndex = index + 1
                    if (nextIndex >= animationArray!!.size) nextIndex = 0
                    onTextColorAnimation(textView, nextIndex)
                }
            })
            colorAnimation?.start()
        }
    }

    fun onApplicationRecreate() {
        this.recreate()
//        val intent = intent
//        overridePendingTransition(0, 0)
//        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
//        finish()
//        overridePendingTransition(0, 0)
//        startActivity(intent)
    }

    private fun onCreateMainMenuLayout() {
        findViewById<CardView>(R.id.menu_amiibo).setOnClickListener {
            closePrefsDrawer()
            showBrowserPage()
        }

        findViewById<CardView>(R.id.menu_foomiibo).setOnClickListener {
            closePrefsDrawer()
            if (viewPager.currentItem != 0) viewPager.setCurrentItem(0, false)
            pagerAdapter.browser.setFoomiiboVisibility(true)
        }

        findViewById<CardView>(R.id.menu_elite).apply {
            setOnClickListener {
                closePrefsDrawer()
                if (viewPager.currentItem != 2) viewPager.setCurrentItem(2, false)
            }
            isVisible = prefs.eliteEnabled()
        }


        findViewById<CardView>(R.id.menu_gatt).apply {
            setOnClickListener {
                closePrefsDrawer()
                if (!prefs.eliteEnabled()) {
                    if (viewPager.currentItem != 2) viewPager.setCurrentItem(2, false)
                } else {
                    if (viewPager.currentItem != 3) viewPager.setCurrentItem(3, false)
                }

            }
        }

        findViewById<CardView>(R.id.menu_qr_code).setOnClickListener {
            closePrefsDrawer()
            onReturnableIntent.launch(Intent(this, QRCodeScanner::class.java))
        }

        findViewById<CardView>(R.id.menu_lego).setOnClickListener {
            closePrefsDrawer()
            if (BuildConfig.WEAR_OS)
                Toasty(this).Short(R.string.feature_unavailable)
            else
                onReturnableIntent.launch(Intent(this, DimensionActivity::class.java))
        }

        findViewById<CardView>(R.id.menu_guides).setOnClickListener {
            closePrefsDrawer()
            if (viewPager.currentItem != 1) viewPager.setCurrentItem(1, false)
        }

        settingsPage = findViewById(R.id.preferences)
        findViewById<CardView>(R.id.menu_settings).setOnClickListener {
            settingsPage?.let { it.isVisible = it.isGone }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun onTabCollectionChanged() {
        showBrowserPage()
        if (Version.isTiramisu) onApplicationRecreate() else pagerAdapter.notifyDataSetChanged()
        onCreateMainMenuLayout()
    }

    private fun requestStoragePermission() {
        if (Version.isRedVelvet) {
            if (BuildConfig.GOOGLE_PLAY) {
                onDocumentEnabled()
            } else {
                if (Environment.isExternalStorageManager()) {
                    onStorageEnabled()
                } else {
                    onDocumentEnabled()
                }
            }
        } else if (Version.isMarshmallow) {
            onRequestStorage.launch(PERMISSIONS_STORAGE)
        } else {
            onStorageEnabled()
        }
    }

    val onNFCActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        result.data?.let { intent ->
            if (NFCIntent.ACTION_NFC_SCANNED != intent.action) return@registerForActivityResult
            if (prefs.eliteEnabled() && intent.hasExtra(NFCIntent.EXTRA_SIGNATURE)) {
                val signature = intent.getStringExtra(NFCIntent.EXTRA_SIGNATURE)
                prefs.eliteSignature(signature)
                val activeBank = intent.getIntExtra(
                    NFCIntent.EXTRA_ACTIVE_BANK, prefs.eliteActiveBank()
                )
                prefs.eliteActiveBank(activeBank)
                val bankCount = intent.getIntExtra(
                    NFCIntent.EXTRA_BANK_COUNT, prefs.eliteBankCount()
                )
                prefs.eliteBankCount(bankCount)
                intent.extras?.let { showElitePage(it) }
            } else {
                viewPager.setCurrentItem(0, false)
                updateAmiiboView(intent.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA))
            }
        }
    }
    private val onBackupActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || null == result.data) return@registerForActivityResult
        if (NFCIntent.ACTION_NFC_SCANNED != result.data!!.action) return@registerForActivityResult
        val tagData = result.data?.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA)
        layoutInflater.inflate(
            R.layout.dialog_save_item, viewPager, false
        ).run {
            val input = findViewById<EditText>(R.id.save_item_entry).apply {
                setText(TagArray.decipherFilename(settings?.amiiboManager, tagData, true))
            }
            AlertDialog.Builder(this@BrowserActivity).setView(this).create().also { dialog ->
                findViewById<View>(R.id.button_save).setOnClickListener { _: View? ->
                    try {
                        val outputData = TagArray.getValidatedData(keyManager, tagData)
                        val fileName = input.text?.toString()?.let { name ->
                            TagArray.writeBytesWithName(
                                    this@BrowserActivity, name, "Backups", outputData
                            )
                        }
                        IconifiedSnackbar(this@BrowserActivity, viewPager).buildSnackbar(
                            getString(R.string.wrote_file, fileName), Snackbar.LENGTH_SHORT
                        ).show()
                        onRootFolderChanged(true)
                    } catch (e: Exception) { Toasty(this@BrowserActivity).Short(e.message) }
                    dialog.dismiss()
                }
                findViewById<View>(R.id.button_cancel).setOnClickListener { dialog.dismiss() }
                dialog.show()
            }
        }
    }
    private val onValidateActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || null == result.data) return@registerForActivityResult
        if (NFCIntent.ACTION_NFC_SCANNED != result.data?.action) return@registerForActivityResult
        try {
            TagArray.validateData(result.data?.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA))
            IconifiedSnackbar(this, viewPager).buildSnackbar(
                R.string.validation_success, Snackbar.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            IconifiedSnackbar(this, viewPager).buildSnackbar(
                e.message, R.drawable.ic_bug_report_24dp, Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun showPopupMenu(popup: PopupMenu) {
        popup.menu.findItem(R.id.mnu_scan).isEnabled = false
        popup.menu.findItem(R.id.mnu_backup).isEnabled = false
        popup.menu.findItem(R.id.mnu_validate).isEnabled = false
        popup.show()
        val popupHandler: Handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                popup.menu.findItem(msg.what).isEnabled = true
            }
        }
        popupHandler.postDelayed({
            val baseDelay = 75
            popupHandler.sendEmptyMessageDelayed(R.id.mnu_validate, (baseDelay).toLong())
            popupHandler.sendEmptyMessageDelayed(R.id.mnu_backup, (75 + baseDelay).toLong())
            popupHandler.sendEmptyMessageDelayed(R.id.mnu_scan, (175 + baseDelay).toLong())
        }, 275)
        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.mnu_scan -> {
                    if (BuildConfig.WEAR_OS) {
                        Toasty(this).Short(R.string.feature_unavailable)
                        return@setOnMenuItemClickListener true
                    }
                    onNFCActivity.launch(
                        Intent(this, NfcActivity::class.java)
                            .setAction(NFCIntent.ACTION_SCAN_TAG)
                    )
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_backup -> {
                    if (BuildConfig.WEAR_OS) {
                        Toasty(this).Short(R.string.feature_unavailable)
                        return@setOnMenuItemClickListener true
                    }
                    val backup = Intent(this, NfcActivity::class.java)
                    backup.action = NFCIntent.ACTION_BACKUP_AMIIBO
                    onBackupActivity.launch(backup)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_validate -> {
                    if (BuildConfig.WEAR_OS) {
                        Toasty(this).Short(R.string.feature_unavailable)
                        return@setOnMenuItemClickListener true
                    }
                    onValidateActivity.launch(
                        Intent(this, NfcActivity::class.java)
                            .setAction(NFCIntent.ACTION_SCAN_TAG)
                    )
                    return@setOnMenuItemClickListener true
                }
                else -> false
            }
        }
    }

    fun onReportProblemClick() {
        if (updateManager?.hasPendingUpdate() == true) {
            updateManager?.onUpdateRequested()
            return
        }
        try {
            Debug.processLogcat(this@BrowserActivity)
        } catch (e: IOException) {
            IconifiedSnackbar(this@BrowserActivity, viewPager)
                .buildSnackbar(e.message, Snackbar.LENGTH_SHORT).show()
        }
    }

    fun getQueryCount(queryText: String?): Int {
        if (null == queryText) return 0
        val amiiboManager = settings?.amiiboManager ?: return 0
        val items: HashSet<Long> = hashSetOf()
        amiiboManager.amiibos.values.forEach {
            if (settings?.amiiboContainsQuery(it, queryText) == true) items.add(it.id)
        }
        return items.size
    }

    private fun getFilteredCount(filter: String, filterType: FILTER): Int {
        val amiiboManager = settings?.amiiboManager ?: return 0
        val items: HashSet<Long> = hashSetOf()
        amiiboManager.amiibos.values.forEach { amiibo ->
            when (filterType) {
                FILTER.CHARACTER -> {
                    amiibo.character?.let {
                        if (Amiibo.matchesGameSeriesFilter(
                                amiibo.gameSeries, settings!!.getFilter(FILTER.GAME_SERIES)
                            ) && Amiibo.matchesAmiiboSeriesFilter(
                                amiibo.amiiboSeries, settings!!.getFilter(FILTER.AMIIBO_SERIES)
                            ) && Amiibo.matchesAmiiboTypeFilter(
                                amiibo.amiiboType, settings!!.getFilter(FILTER.AMIIBO_TYPE)
                            )
                        ) {
                            if (it.name == filter) items.add(amiibo.id)
                        }
                    }
                }
                FILTER.GAME_SERIES -> {
                    amiibo.gameSeries?.let {
                        if (Amiibo.matchesCharacterFilter(
                                amiibo.character, settings!!.getFilter(FILTER.CHARACTER)
                            ) && Amiibo.matchesAmiiboSeriesFilter(
                                amiibo.amiiboSeries, settings!!.getFilter(FILTER.AMIIBO_SERIES)
                            ) && Amiibo.matchesAmiiboTypeFilter(
                                amiibo.amiiboType, settings!!.getFilter(FILTER.AMIIBO_TYPE)
                            )
                        ) {
                            if (it.name == filter) items.add(amiibo.id)
                        }
                    }
                }
                FILTER.AMIIBO_SERIES -> {
                    amiibo.amiiboSeries?.let {
                        if (Amiibo.matchesGameSeriesFilter(
                                amiibo.gameSeries, settings!!.getFilter(FILTER.GAME_SERIES)
                            ) && Amiibo.matchesCharacterFilter(
                                amiibo.character, settings!!.getFilter(FILTER.CHARACTER)
                            ) && Amiibo.matchesAmiiboTypeFilter(
                                amiibo.amiiboType, settings!!.getFilter(FILTER.AMIIBO_TYPE)
                            )
                        ) {
                            if (it.name == filter) items.add(amiibo.id)
                        }
                    }
                }
                FILTER.AMIIBO_TYPE -> {
                    amiibo.amiiboType?.let {
                        if (Amiibo.matchesGameSeriesFilter(
                                amiibo.gameSeries, settings!!.getFilter(FILTER.GAME_SERIES)
                            ) && Amiibo.matchesCharacterFilter(
                                amiibo.character, settings!!.getFilter(FILTER.CHARACTER)
                            ) && Amiibo.matchesAmiiboSeriesFilter(
                                amiibo.amiiboSeries, settings!!.getFilter(FILTER.AMIIBO_SERIES)
                            )
                        ) {
                            if (it.name == filter) items.add(amiibo.id)
                        }
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
                    settings?.gamesManager?.let {
                        items.addAll(it.getGameAmiiboIds(amiiboManager, filter))
                    }
                }
            }
        }
        return items.size
    }

    private fun onFilterCharacterClick(): Boolean {
        val subMenu = menuFilterCharacter?.subMenu
        subMenu?.clear() ?: return true
        val amiiboManager = settings?.amiiboManager ?: return true
        val items: ArrayList<String> = arrayListOf()
        amiiboManager.amiibos.values.forEach {
            it.character?.let { character ->
                if (Amiibo.matchesGameSeriesFilter(
                        it.gameSeries, settings!!.getFilter(FILTER.GAME_SERIES)
                    ) && Amiibo.matchesAmiiboSeriesFilter(
                        it.amiiboSeries, settings!!.getFilter(FILTER.AMIIBO_SERIES)
                    ) && Amiibo.matchesAmiiboTypeFilter(
                        it.amiiboType, settings!!.getFilter(FILTER.AMIIBO_TYPE)
                    )
                ) {
                    if (!items.contains(character.name)) items.add(character.name)
                }
            }
        }
        items.sorted().forEach {
            subMenu.add(R.id.filter_character_group, Menu.NONE, 0, it)
                .setChecked(it == settings!!.getFilter(FILTER.CHARACTER))
                .setOnMenuItemClickListener(onFilterCharacterItemClick)
        }
        subMenu.setGroupCheckable(R.id.filter_character_group, true, true)
        return true
    }

    private val onFilterCharacterItemClick = MenuItem.OnMenuItemClickListener { menuItem ->
        settings?.let {
            it.setFilter(FILTER.CHARACTER, menuItem.title.toString())
            it.notifyChanges()
        }
        filteredCount = getFilteredCount(menuItem.title.toString(), FILTER.CHARACTER)
        false
    }

    private fun onFilterGameSeriesClick(): Boolean {
        val subMenu = menuFilterGameSeries?.subMenu
        subMenu?.clear() ?: return true
        val amiiboManager = settings?.amiiboManager ?: return true
        val items: ArrayList<String> = arrayListOf()
        amiiboManager.amiibos.values.forEach {
            it.gameSeries?.let { gameSeries ->
                if (Amiibo.matchesCharacterFilter(
                        it.character, settings!!.getFilter(FILTER.CHARACTER)
                    ) && Amiibo.matchesAmiiboSeriesFilter(
                        it.amiiboSeries, settings!!.getFilter(FILTER.AMIIBO_SERIES)
                    ) && Amiibo.matchesAmiiboTypeFilter(
                        it.amiiboType, settings!!.getFilter(FILTER.AMIIBO_TYPE)
                    )
                ) {
                    if (!items.contains(gameSeries.name)) items.add(gameSeries.name)
                }
            }
        }
        items.sorted().forEach {
            subMenu.add(R.id.filter_game_series_group, Menu.NONE, 0, it)
                .setChecked(it == settings!!.getFilter(FILTER.GAME_SERIES))
                .setOnMenuItemClickListener(onFilterGameSeriesItemClick)
        }
        subMenu.setGroupCheckable(R.id.filter_game_series_group, true, true)
        return true
    }

    private val onFilterGameSeriesItemClick = MenuItem.OnMenuItemClickListener { menuItem ->
        settings?.let {
            it.setFilter(FILTER.GAME_SERIES, menuItem.title.toString())
            it.notifyChanges()
        }
        filteredCount = getFilteredCount(menuItem.title.toString(), FILTER.GAME_SERIES)
        false
    }

    private fun onFilterAmiiboSeriesClick(): Boolean {
        val subMenu = menuFilterAmiiboSeries?.subMenu
        subMenu?.clear() ?: return true
        val amiiboManager = settings?.amiiboManager ?: return true
        val items: ArrayList<String> = arrayListOf()
        amiiboManager.amiibos.values.forEach {
            it.amiiboSeries?.let { amiiboSeries ->
                if (Amiibo.matchesGameSeriesFilter(
                        it.gameSeries, settings!!.getFilter(FILTER.GAME_SERIES)
                    ) && Amiibo.matchesCharacterFilter(
                        it.character, settings!!.getFilter(FILTER.CHARACTER)
                    ) && Amiibo.matchesAmiiboTypeFilter(
                        it.amiiboType, settings!!.getFilter(FILTER.AMIIBO_TYPE)
                    )
                ) {
                    if (!items.contains(amiiboSeries.name)) items.add(amiiboSeries.name)
                }
            }
        }
        items.sorted().forEach {
            subMenu.add(R.id.filter_amiibo_series_group, Menu.NONE, 0, it)
                .setChecked(it == settings?.getFilter(FILTER.AMIIBO_SERIES))
                .setOnMenuItemClickListener(onFilterAmiiboSeriesItemClick)
        }
        subMenu.setGroupCheckable(R.id.filter_amiibo_series_group, true, true)
        return true
    }

    private val onFilterAmiiboSeriesItemClick = MenuItem.OnMenuItemClickListener { menuItem ->
        settings?.let{
            it.setFilter(FILTER.AMIIBO_SERIES, menuItem.title.toString())
            it.notifyChanges()
        }
        filteredCount = getFilteredCount(menuItem.title.toString(), FILTER.AMIIBO_SERIES)
        false
    }

    private fun onFilterAmiiboTypeClick(): Boolean {
        val subMenu = menuFilterAmiiboType?.subMenu
        subMenu?.clear() ?: return true
        val amiiboManager = settings?.amiiboManager ?: return true
        val items: ArrayList<AmiiboType> = arrayListOf()
        amiiboManager.amiibos.values.forEach {
            it.amiiboType?.let { amiiboType ->
                if (Amiibo.matchesGameSeriesFilter(
                        it.gameSeries, settings!!.getFilter(FILTER.GAME_SERIES)
                    ) && Amiibo.matchesCharacterFilter(
                        it.character, settings!!.getFilter(FILTER.CHARACTER)
                    ) && Amiibo.matchesAmiiboSeriesFilter(
                        it.amiiboSeries, settings!!.getFilter(FILTER.AMIIBO_SERIES)
                    )
                ) {
                    if (!items.contains(amiiboType)) items.add(amiiboType)
                }
            }
        }
        items.sorted().forEach {
            subMenu.add(R.id.filter_amiibo_type_group, Menu.NONE, 0, it.name)
                .setChecked(it.name == settings!!.getFilter(FILTER.AMIIBO_TYPE))
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
        val subMenu = menuFilterGameTitles?.subMenu
        subMenu?.clear() ?: return
        settings?.amiiboManager ?: return
        val items: ArrayList<String> = arrayListOf()
        settings?.gamesManager?.gameTitles?.forEach { if (!items.contains(it.name))  items.add(it.name) }
        items.sorted().forEach {
            subMenu.add(R.id.filter_game_titles_group, Menu.NONE, 0, it)
                .setChecked(it == settings!!.getFilter(FILTER.GAME_TITLES))
                .setOnMenuItemClickListener(onFilterGameTitlesItemClick)
        }
        subMenu.setGroupCheckable(R.id.filter_game_titles_group, true, true)
    }

    private val onFilterGameTitlesItemClick = MenuItem.OnMenuItemClickListener { menuItem ->
        settings?.let {
            it.setFilter(FILTER.GAME_TITLES, menuItem.title.toString())
            it.notifyChanges()
        }
        filteredCount = getFilteredCount(menuItem.title.toString(), FILTER.GAME_TITLES)
        false
    }

    private fun exportWithRandomSerial(amiiboFile: AmiiboFile?, tagData: ByteArray?, count: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val amiiboList = amiiboFile?.let {
                TagArray.getValidatedAmiibo(keyManager, it).withRandomSerials(count, keyManager)
            } ?: tagData?.withRandomSerials(count, keyManager)
            amiiboList?.forEachIndexed { index, amiiboData ->
                try {
                    val outputData = keyManager.encrypt(amiiboData.array)
                    writeFlipperFile(index, outputData)
                } catch (ex: Exception) {
                    Debug.warn(ex)
                }
            } ?: withContext(Dispatchers.Main) {
                IconifiedSnackbar(this@BrowserActivity, viewPager).buildSnackbar(
                        getString(R.string.fail_randomize), Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun cloneWithRandomSerial(amiiboFile: AmiiboFile?, tagData: ByteArray?, count: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val cached = amiiboFile?.let {
                it.filePath?.let { path ->
                    var relativeFile = Storage.getRelativePath(path, prefs.preferEmulated())
                    prefs.browserRootFolder()?.let { root ->
                        relativeFile = relativeFile.replace(root, "")
                    }
                    relativeFile.startsWith("/Foomiibo/")
                } ?: it.docUri?.let { doc ->
                    Storage.getRelativeDocument(doc.uri).startsWith("/Foomiibo/")
                } ?: false
            } ?: false

            val directory = File(Foomiibo.directory, "Duplicates")
            if (cached) directory.mkdirs()

            val fileName = TagArray.decipherFilename(settings?.amiiboManager, tagData, true)
            val amiiboList = amiiboFile?.let {
                TagArray.getValidatedAmiibo(keyManager, it).withRandomSerials(count, keyManager)
            } ?: tagData?.withRandomSerials(count, keyManager)
            amiiboList?.forEachIndexed { index, amiiboData ->
                val name = "${fileName}_$index"
                try {
                    val outputData = keyManager.encrypt(amiiboData.array)
                    if (cached) {
                        val path = TagArray.writeBytesToFile(directory, name, outputData)
                        AmiiboFile(File(path), amiiboData.amiiboID, outputData)
                    } else {
                        TagArray.writeBytesWithName(this@BrowserActivity, name, "Duplicates", outputData)?.let {
                            if (prefs.isDocumentStorage)
                                AmiiboFile(DocumentFile.fromTreeUri(
                                    this@BrowserActivity, Uri.parse(it)
                                ), amiiboData.amiiboID, outputData)
                            else
                                AmiiboFile(File(it), amiiboData.amiiboID, amiiboData.array)
                        }
                    }
                } catch (ex: Exception) {
                    Debug.warn(ex)
                }
            } ?:  withContext(Dispatchers.Main) {
                IconifiedSnackbar(this@BrowserActivity, viewPager).buildSnackbar(
                        getString(R.string.fail_randomize), Snackbar.LENGTH_SHORT
                ).show()
            }
            withContext(Dispatchers.Main) {
                onRootFolderChanged(true)
            }
        }
    }

    private fun showDuplicatorDialog(amiiboFile: AmiiboFile?, tagData: ByteArray?) {
        val view = layoutInflater.inflate(R.layout.dialog_duplicator, null)
        val dialog = AlertDialog.Builder(this)
        val copierDialog: Dialog = dialog.setView(view).create()
        val count = view.findViewById<NumberPicker>(R.id.number_picker_bin)
        view.findViewById<View>(R.id.button_export).setOnClickListener {
            exportWithRandomSerial(amiiboFile, tagData, count.value)
            copierDialog.dismiss()
        }
        view.findViewById<View>(R.id.button_save).setOnClickListener {
            cloneWithRandomSerial(amiiboFile, tagData, count.value)
            copierDialog.dismiss()
        }
        view.findViewById<View>(R.id.button_cancel).setOnClickListener {
            copierDialog.dismiss()
        }
        copierDialog.show()
    }

    private fun writeFlipperFile(index: Int?, tagData: ByteArray?) {
        val fileName = TagArray.decipherFilename(settings?.amiiboManager, tagData, false)
        tagData?.toNFC(if (null != index) "${fileName}_$index" else fileName)
                ?: IconifiedSnackbar(this, viewPager).buildSnackbar(
                        getString(R.string.fail_save_file), Snackbar.LENGTH_SHORT
                ).show()
    }

    private fun onCreateToolbarMenu(itemView: View?, tagData: ByteArray?, amiiboFile: AmiiboFile?) {
        val toolbar = when (itemView) {
            is Toolbar -> itemView
            else -> itemView?.findViewById<View>(R.id.menu_options)
                ?.findViewById(R.id.toolbar) ?: return
        }
        if (!toolbar.menu.hasVisibleItems()) toolbar.inflateMenu(R.menu.amiibo_menu)
        var available = (null != tagData) && tagData.isNotEmpty()
        if (available) {
            try {
                Amiibo.dataToId(tagData)
            } catch (e: Exception) {
                available = false
                Debug.verbose(e)
            }
        }
        toolbar.menu.findItem(R.id.mnu_write).isVisible = available
        toolbar.menu.findItem(R.id.mnu_update).isVisible = available
        toolbar.menu.findItem(R.id.mnu_edit).isVisible = available
        toolbar.menu.findItem(R.id.mnu_gatt).isVisible = available
        toolbar.menu.findItem(R.id.mnu_view_hex).isVisible = available
        toolbar.menu.findItem(R.id.mnu_share_qr).isVisible = available
        toolbar.menu.findItem(R.id.mnu_flipper).isVisible = available
        toolbar.menu.findItem(R.id.mnu_validate).isVisible = available
        toolbar.menu.findItem(R.id.mnu_ignore_tag_id).isVisible = available
        toolbar.menu.findItem(R.id.mnu_random).apply {
            isVisible = null != amiiboFile
        }
        toolbar.menu.findItem(R.id.mnu_delete).apply {
            isVisible = null != amiiboFile
        }
        val backup = toolbar.menu.findItem(R.id.mnu_save).apply {
            isVisible = available
        }
        val cached = amiiboFile?.let {
            it.docUri?.let { doc ->
                Storage.getRelativeDocument(doc.uri).startsWith("/Foomiibo/")
            } ?: it.filePath?.let { path ->
                var relativeFile = Storage.getRelativePath(path, prefs.preferEmulated())
                prefs.browserRootFolder()?.let { root ->
                    relativeFile = relativeFile.replace(root, "")
                }
                relativeFile.startsWith("/Foomiibo/")
            }
        } ?: false
        if (cached) backup.setTitle(R.string.cache)
        toolbar.setOnMenuItemClickListener { menuItem ->
            clickedAmiibo = amiiboFile
            if (menuItem.itemId != R.id.mnu_ignore_tag_id
                && menuItem.itemId != R.id.mnu_validate
                && menuItem.itemId != R.id.share_export) {
                itemView.performClick()
            }
            val scan = Intent(this, NfcActivity::class.java)
            when (menuItem.itemId) {
                R.id.mnu_scan -> {
                    scan.action = NFCIntent.ACTION_SCAN_TAG
                    onUpdateTagResult.launch(scan)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_write -> {
                    onUpdateTagResult.launch(scan.apply {
                        action = NFCIntent.ACTION_WRITE_TAG_FULL
                        putExtras(Bundle().apply {
                            putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData)
                        })
                    })
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_update -> {
                    onUpdateTagResult.launch(scan.apply {
                        action = NFCIntent.ACTION_WRITE_TAG_DATA
                        putExtras(Bundle().apply {
                            putBoolean(NFCIntent.EXTRA_IGNORE_TAG_ID, ignoreTagId)
                            putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData)
                        })
                    })
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_edit -> {
                    onUpdateTagResult.launch(Intent(this, TagDataEditor::class.java)
                            .putExtras(Bundle().apply {
                                putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData)
                            })
                    )
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_gatt -> {
                    showGattPage(Bundle().apply {
                        putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData)
                    })
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_save -> {
                    if (cached) {
                        if (tagData != null) {
                            pagerAdapter.browser.buildFoomiiboFile(tagData)
                            onRootFolderChanged(true)
                        }
                    } else {
                        val view = layoutInflater.inflate(R.layout.dialog_save_item, null)
                        val dialog = AlertDialog.Builder(this)
                        val input = view.findViewById<EditText>(R.id.save_item_entry)
                        input.setText(
                            TagArray.decipherFilename(settings?.amiiboManager, tagData, true)
                        )
                        val backupDialog: Dialog = dialog.setView(view).create()
                        view.findViewById<View>(R.id.button_save).setOnClickListener {
                            try {
                                val fileName = TagArray.writeBytesWithName(
                                    this, input.text, tagData
                                )
                                fileName?.let { name ->
                                    IconifiedSnackbar(this, viewPager).buildSnackbar(
                                        getString(R.string.wrote_file, name), Snackbar.LENGTH_SHORT
                                    ).show()
                                    onRootFolderChanged(true)
                                } ?: IconifiedSnackbar(this, viewPager).buildSnackbar(
                                    getString(R.string.fail_save_file), Snackbar.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) { Toasty(this).Short(e.message) }
                            backupDialog.dismiss()
                        }
                        view.findViewById<View>(R.id.button_cancel).setOnClickListener {
                            backupDialog.dismiss()
                        }
                        backupDialog.show()
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_random -> {
                    showDuplicatorDialog(amiiboFile, tagData)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_view_hex -> {
                    onUpdateTagResult.launch(Intent(this, HexCodeViewer::class.java)
                        .putExtra(NFCIntent.EXTRA_TAG_DATA, tagData)
                    )
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_share_qr -> {
                    onReturnableIntent.launch(Intent(this, QRCodeScanner::class.java)
                        .putExtra(NFCIntent.EXTRA_TAG_DATA, tagData)
                    )
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_flipper -> {
                    writeFlipperFile(null, tagData)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_validate -> {
                    try {
                        TagArray.validateData(tagData)
                        IconifiedSnackbar(this, viewPager).buildSnackbar(
                            R.string.validation_success, Snackbar.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        IconifiedSnackbar(this, viewPager).buildSnackbar(
                            e.message,
                            R.drawable.ic_bug_report_24dp, Snackbar.LENGTH_LONG
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
        fragment: BrowserFragment, menubar: Toolbar?, tagData: ByteArray, itemView: View
    ) {
        val toolbar = menubar ?: toolbar ?: return
        if (!toolbar.menu.hasVisibleItems()) toolbar.inflateMenu(R.menu.amiibo_menu)
        toolbar.menu.findItem(R.id.mnu_save).setTitle(R.string.cache)
        toolbar.menu.findItem(R.id.mnu_scan).isVisible = false
        toolbar.menu.findItem(R.id.mnu_random).isVisible = false

        val available = tagData.isNotEmpty()
        toolbar.menu.findItem(R.id.mnu_write).isVisible = available
        toolbar.menu.findItem(R.id.mnu_update).isVisible = available
        toolbar.menu.findItem(R.id.mnu_edit).isVisible = available
        toolbar.menu.findItem(R.id.mnu_gatt).isVisible = available
        toolbar.menu.findItem(R.id.mnu_view_hex).isVisible = available
        toolbar.menu.findItem(R.id.mnu_share_qr).isVisible = available
        toolbar.menu.findItem(R.id.mnu_flipper).isVisible = available
        toolbar.menu.findItem(R.id.mnu_validate).isVisible = available
        toolbar.menu.findItem(R.id.mnu_delete).isVisible = available
        toolbar.menu.findItem(R.id.mnu_ignore_tag_id).isVisible = available

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
                        viewPager.adapter = pagerAdapter
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
                        viewPager.adapter = pagerAdapter
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_edit -> {
                    args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData)
                    val tagEdit = Intent(this, TagDataEditor::class.java)
                    try {
                        fragment.onUpdateTagResult.launch(tagEdit.putExtras(args))
                    } catch (ex: IllegalStateException) {
                        viewPager.adapter = pagerAdapter
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_gatt -> {
                    args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData)
                    showGattPage(args)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_save -> {
                    if (tagData.isNotEmpty()) {
                        fragment.buildFoomiiboFile(tagData)
                        itemView.callOnClick()
                        onRootFolderChanged(true)
                    } else {
                        Toasty(this).Short(R.string.fail_save_data)
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_view_hex -> {
                    onUpdateTagResult.launch(Intent(this, HexCodeViewer::class.java)
                        .putExtra(NFCIntent.EXTRA_TAG_DATA, tagData)
                    )
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_share_qr -> {
                    onReturnableIntent.launch(Intent(this, QRCodeScanner::class.java)
                        .putExtra(NFCIntent.EXTRA_TAG_DATA, tagData)
                    )
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_flipper -> {
                    writeFlipperFile(null, tagData)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_validate -> {
                    try {
                        TagArray.validateData(tagData)
                        IconifiedSnackbar(this, viewPager).buildSnackbar(
                            R.string.validation_success, Snackbar.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        IconifiedSnackbar(this, viewPager).buildSnackbar(
                            e.message, R.drawable.ic_bug_report_24dp, Snackbar.LENGTH_LONG
                        ).show()
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_delete -> {
                    fragment.deleteFoomiiboFile(tagData)
                    itemView.callOnClick()
                    onRootFolderChanged(true)
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

    private fun getGameCompatibility(txtUsage: TextView, tagData: ByteArray?) : Boolean {
        return settings?.gamesManager?.let {
            try {
                val amiiboId = Amiibo.dataToId(tagData)
                txtUsage.text = it.getGamesCompatibility(amiiboId)
                true
            } catch (ex: Exception) {
                Debug.warn(ex)
                false
            }
        } ?: false
    }

    fun onKeysLoaded(indicator: Boolean) {
        hideFakeSnackbar()
        onRefresh(indicator)
    }

    fun onRefresh(indicator: Boolean) {
        loadAmiiboManager()
        onRootFolderChanged(indicator)
    }

    val isRefreshing : Boolean get() = fakeSnackbar?.isVisible == true

    @Throws(ActivityNotFoundException::class)
    fun onDocumentRequested() {
        if (Version.isLollipop) {
            onDocumentTree.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .putExtra("android.content.extra.SHOW_ADVANCED", true)
                .putExtra("android.content.extra.FANCY", true)
            )
        }
    }

    private fun onDocumentEnabled() {
        if (prefs.isDocumentStorage) {
            onStorageEnabled()
        } else {
            try {
                onDocumentRequested()
            } catch (anf: ActivityNotFoundException) {
                Toasty(this).Long(R.string.storage_unavailable)
            }
        }
    }

    fun onStorageEnabled() {
        if (keyManager.isKeyMissing) {
            hideFakeSnackbar()
            showFakeSnackbar(getString(R.string.locating_keys))
            locateKeyFiles()
        } else {
            onRefresh(true)
        }
    }

    private fun onMenuItemClicked(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refresh -> {
                onRefresh(true)
            }
            R.id.sort_id -> {
                settings?.let {
                    it.sort = SORT.ID.value
                    it.notifyChanges()
                }
            }
            R.id.sort_name -> {
                settings?.let {
                    it.sort = SORT.NAME.value
                    it.notifyChanges()
                }
            }
            R.id.sort_character -> {
                settings?.let {
                    it.sort = SORT.CHARACTER.value
                    it.notifyChanges()
                }
            }
            R.id.sort_game_series -> {
                settings?.let {
                    it.sort = SORT.GAME_SERIES.value
                    it.notifyChanges()
                }
            }
            R.id.sort_amiibo_series -> {
                settings?.let {
                    it.sort = SORT.AMIIBO_SERIES.value
                    it.notifyChanges()
                }
            }
            R.id.sort_amiibo_type -> {
                settings?.let {
                    it.sort = SORT.AMIIBO_TYPE.value
                    it.notifyChanges()
                }
            }
            R.id.sort_file_path -> {
                settings?.let {
                    it.sort = SORT.FILE_PATH.value
                    it.notifyChanges()
                }
            }
            R.id.view_compact -> {
                amiibosView?.layoutManager = LinearLayoutManager(this)
                foomiiboView?.layoutManager = LinearLayoutManager(this)
                settings?.let {
                    it.amiiboView = VIEW.COMPACT.value
                    it.notifyChanges()
                }
            }
            R.id.view_large -> {
                amiibosView?.layoutManager = LinearLayoutManager(this)
                foomiiboView?.layoutManager = LinearLayoutManager(this)
                settings?.let {
                    it.amiiboView = VIEW.LARGE.value
                    it.notifyChanges()
                }
            }
            R.id.view_image -> {
                amiibosView?.layoutManager = GridLayoutManager(this, columnCount)
                amiibosView?.invalidate()
                foomiiboView?.layoutManager = GridLayoutManager(this, columnCount)
                foomiiboView?.invalidate()
                settings?.let {
                    it.amiiboView = VIEW.IMAGE.value
                    it.notifyChanges()
                }
            }
            R.id.recursive -> {
                settings?.let {
                    it.isRecursiveEnabled = !it.isRecursiveEnabled
                    it.notifyChanges()
                }
            }
            R.id.send_donation -> {
                showDonationPanel()
            }
            R.id.report_problem -> {
                onReportProblemClick()
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
        onSortChanged()
        onViewChanged()
        onRecursiveFilesChanged()
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
        menuViewCompact = menu.findItem(R.id.view_compact)
        menuViewLarge = menu.findItem(R.id.view_large)
        menuViewImage = menu.findItem(R.id.view_image)
        menuRecursiveFiles = menu.findItem(R.id.recursive)
        if (null == settings) return false
        onSortChanged()
        onViewChanged()
        onRecursiveFilesChanged()
        menuUpdate.isVisible = updateManager?.hasPendingUpdate() == true
        val searchView = menuSearch.actionView as? SearchView
        searchView?.let { search ->
            with (getSystemService(SEARCH_SERVICE) as SearchManager) {
                search.setSearchableInfo(getSearchableInfo(componentName))
            }
            search.isSubmitButtonEnabled = false
            menuSearch.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                    return true
                }

                override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                    return if (BottomSheetBehavior.STATE_EXPANDED == browserSheet?.state
                        || amiiboContainer?.isVisible == true
                        || supportFragmentManager.backStackEntryCount > 0
                    ) {
                        onBackPressedDispatcher.onBackPressed()
                        false
                    } else {
                        true
                    }
                }
            })
            search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    settings?.let {
                        it.query = query
                        it.notifyChanges()
                    }
                    pagerAdapter.browser.setAmiiboStats()
                    return false
                }

                override fun onQueryTextChange(query: String): Boolean {
                    settings?.let {
                        it.query = query
                        it.notifyChanges()
                    }
                    if (query.isEmpty()) pagerAdapter.browser.setAmiiboStats()
                    return true
                }
            })
            settings?.query?.let {
                if (it.isNotBlank()) {
                    menuSearch.expandActionView()
                    search.setQuery(it, true)
                    search.clearFocus()
                }
            }
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (!closePrefsDrawer()) {
                prefsDrawer?.openDrawer(GravityCompat.START)
            }
        } else if (item.itemId == R.id.install_update) {
            updateManager?.onUpdateRequested()
        }
        return onMenuItemClicked(item)
    }

    override fun onAmiiboClicked(itemView: View, amiiboFile: AmiiboFile?) {
        if (null == amiiboFile?.docUri && null == amiiboFile?.filePath) return
        try {
            val tagData = TagArray.getValidatedAmiibo(keyManager, amiiboFile)
            if (settings?.amiiboView != VIEW.IMAGE.value) {
                val menuOptions = itemView.findViewById<LinearLayout>(R.id.menu_options)
                if (menuOptions.isGone) onCreateToolbarMenu(itemView, tagData, amiiboFile)
                menuOptions.isGone = menuOptions.isVisible
                val txtUsage = itemView.findViewById<TextView>(R.id.txtUsage)
                if (txtUsage.isGone) getGameCompatibility(txtUsage, tagData)
                txtUsage.isGone = txtUsage.isVisible
            } else {
                updateAmiiboView(tagData, amiiboFile)
            }
        } catch (e: Exception) { Debug.warn(e) }
    }

    override fun onAmiiboRebind(itemView: View, amiiboFile: AmiiboFile?) {
        if (amiiboFile?.filePath == null) return
        try {
            val tagData = amiiboFile.data ?: amiiboFile.filePath?.let {
                TagArray.getValidatedFile(keyManager, it)
            } ?: amiiboFile.docUri?.let {
                TagArray.getValidatedDocument(keyManager, it)
            }
            if (settings?.amiiboView != VIEW.IMAGE.value) {
                onCreateToolbarMenu(itemView, tagData, amiiboFile)
                getGameCompatibility(itemView.findViewById(R.id.txtUsage), tagData)
            } else {
                updateAmiiboView(tagData, amiiboFile)
            }
        } catch (e: Exception) { Debug.warn(e) }
    }

    override fun onAmiiboImageClicked(amiiboFile: AmiiboFile?) {
        this.startActivity(Intent(this, ImageActivity::class.java)
            .putExtras(Bundle().apply {
                amiiboFile?.let { putLong(NFCIntent.EXTRA_AMIIBO_ID, it.id) }
            })
        )
    }

    fun loadPTagKeyManager() {
        if (prefs.powerTagEnabled()) {
            try {
                PowerTagManager.setPowerTagManager()
            } catch (e: Exception) {
                Debug.warn(e)
                Toasty(this@BrowserActivity).Short(R.string.fail_powertag_keys)
            }
        }
    }

    private fun loadAmiiboManager() {
        CoroutineScope(Dispatchers.IO).launch {
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
                settings?.let {
                    it.amiiboManager = amiiboManager
                    it.gamesManager = gamesManager
                    it.notifyChanges()
                    pagerAdapter.browser.managerStats
                }
            }
        }
    }

    private fun listFolders(rootFolder: File?): ArrayList<File?> {
        val folders: ArrayList<File?> = arrayListOf()
        rootFolder?.listFiles().also { files ->
            if (files.isNullOrEmpty()) return folders
            files.forEach { file -> if (file.isDirectory) folders.add(file) }
        }
        return folders
    }

    private fun loadFolders(rootFolder: File?) {
        CoroutineScope(Dispatchers.IO).launch {
            val folders = listFolders(rootFolder)
            folders.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it?.path ?: "" })
            withContext(Dispatchers.Main) {
                settings?.folders = folders
                settings?.notifyChanges()
            }
        }
    }

    private suspend fun ArrayList<AmiiboFile?>.appendStorageFiles(): ArrayList<AmiiboFile?> {
        listAmiiboFiles(keyManager, TagMo.downloadDir, true).forEach { file ->
            file?.let { amiiboFile ->
                if (!any { it?.id == amiiboFile.id }) add(amiiboFile)
            }
        }
        listAmiiboFiles(keyManager, File(filesDir, "Foomiibo"), true).also {
            addAll(it)
        }
        return this
    }

    private fun loadAmiiboFiles(rootFolder: File?, recursiveFiles: Boolean) {
        setSnackbarListener(object: SnackbarListener {
            override fun onSnackbarHidden(fakeSnackbar: AnimatedLinearLayout) {
                nfcFab.loadSavedPosition(prefs)
                snackbarListener = null
            }
        })
        CoroutineScope(Dispatchers.IO).launch {
            val amiiboFiles = listAmiiboFiles(
                keyManager, rootFolder, recursiveFiles
            ).appendStorageFiles()
            withContext(Dispatchers.Main) {
                hideFakeSnackbar()
                settings?.let {
                    it.amiiboFiles = amiiboFiles
                    it.notifyChanges()
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun loadAmiiboDocuments(rootFolder: DocumentFile?, recursiveFiles: Boolean) {
        setSnackbarListener(object: SnackbarListener {
            override fun onSnackbarHidden(fakeSnackbar: AnimatedLinearLayout) {
                nfcFab.loadSavedPosition(prefs)
                snackbarListener = null
            }
        })
        CoroutineScope(Dispatchers.IO).launch {
            val amiiboFiles = try {
                rootFolder?.let {
                    listAmiiboDocuments(this@BrowserActivity, keyManager, it, recursiveFiles)
                } ?: arrayListOf()
            } catch (ex: Exception) {
                arrayListOf()
            }
            if (amiiboFiles.isEmpty() && null == settings?.browserRootDocument) {
                onDocumentRequested()
            }
            settings?.amiiboFiles = amiiboFiles.appendStorageFiles()
            withContext(Dispatchers.Main) {
                hideFakeSnackbar()
                settings?.notifyChanges()
            }
        }
    }

    fun loadAmiiboBackground() {
        if (prefs.isDocumentStorage) {
            val rootDocument = settings?.browserRootDocument?.let {
                DocumentFile.fromTreeUri(this@BrowserActivity, it)
            }
            loadAmiiboDocuments(rootDocument, settings?.isRecursiveEnabled == true)
        } else {
            loadAmiiboFiles(settings?.browserRootFolder, settings?.isRecursiveEnabled == true)
        }
    }

    private fun getUriFIleSize(uri: Uri): Long {
        return if (uri.scheme.equals("file")) {
            uri.path?.let { File(it).length() } ?: 0
        } else if (uri.scheme.equals("content")) {
            this.contentResolver.query(uri, null, null, null, null)?.use {
                it.moveToFirst()
                it.getStringOrNull(it.getColumnIndex(OpenableColumns.SIZE))?.toLong() ?: 0
            } ?: 0
        } else {
            0
        }
    }

    fun decompressArchive(uri: Uri?) {
        val zipFile = File(externalCacheDir, "archive.zip")
        zipFile.outputStream().use { fileOut ->
            contentResolver.openInputStream(uri)?.use {
                it.copyTo(fileOut)
            }
        }
        val folder = Storage.getDownloadDir("TagMo", "Archive")
        val processDialog = ProgressAlert.show(
            this, getString(R.string.unzip_item, zipFile.nameWithoutExtension)
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Zip.extract(zipFile, folder.absolutePath) { progress ->
                    CoroutineScope(Dispatchers.Main).launch {
                        val nameProgress = "${zipFile.nameWithoutExtension} (${progress}%) "
                        processDialog.setMessage(getString(R.string.unzip_item, nameProgress))
                    }
                    if (progress == 100) {
                        zipFile.delete()
                        CoroutineScope(Dispatchers.Main).launch {
                            processDialog.dismiss()
                        }
                       requestStoragePermission()
                    }
                }
            } catch (iae: IllegalArgumentException) {
                Debug.error(iae)
                Toasty(this@BrowserActivity).Short(R.string.error_archive_format)
                if (zipFile.exists()) zipFile.delete()
            }
        }
    }

    private fun processIncomingUri(intent: Intent, uri: Uri?) {
        if (null == uri) {
            Toasty(this@BrowserActivity).Short(R.string.error_uri_unknown)
            return
        }
        val binFile = resources.getStringArray(R.array.mimetype_bin)
        if (binFile.contains(intent.type)) {
            val length = getUriFIleSize(uri)
            when {
                length >= NfcByte.TAG_DATA_SIZE -> {
                    val data = TagReader.readTagDocument(uri)
                    updateAmiiboView(data, AmiiboFile(
                        uri.path?.let { File(it) }, Amiibo.dataToId(data), data
                    ))
                }
                length >= NfcByte.KEY_FILE_SIZE -> {
                    onLoadSettingsFragment()
                    fragmentSettings?.validateKeys(intent.parcelable(Intent.EXTRA_STREAM) as Uri?)
                }
                else -> {
                    Toasty(this@BrowserActivity).Short(R.string.error_uri_size)
                }
            }
        } else if (intent.type == getString(R.string.mimetype_zip)) {
            decompressArchive(uri)
        }
    }

    private var onDocumentTree = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
        val treeUri = result.data?.data
        contentResolver.takePersistableUriPermission(treeUri!!,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val pickedDir = DocumentFile.fromTreeUri(this, treeUri!!)

        // List all existing files inside picked directory
        if (null != pickedDir) {
            settings?.let {
                it.browserRootDocument = treeUri
                it.notifyChanges()
            }
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
            pagerAdapter.browser.setFolderText(newBrowserSettings)
        } else {
            pagerAdapter.browser.setFolderText(null)
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
            updateManager?.refreshUpdateStatus()
            newBrowserSettings.lastUpdatedGit = System.currentTimeMillis()
        }
        prefs.browserRootFolder(Storage.getRelativePath(
            newBrowserSettings.browserRootFolder, prefs.preferEmulated()
        ))
        prefs.browserRootDocument(newBrowserSettings.browserRootDocument?.toString())

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
        settings?.let {
            when (SORT.valueOf(it.sort)) {
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
        if (null == menuViewCompact) return
        settings?.let {
            when (VIEW.valueOf(it.amiiboView)) {
                VIEW.COMPACT -> menuViewCompact?.isChecked = true
                VIEW.LARGE -> menuViewLarge?.isChecked = true
                VIEW.IMAGE -> menuViewImage?.isChecked = true
            }
        }
    }

    fun onRootFolderChanged(indicator: Boolean) {
        if (prefs.isDocumentStorage) {
            val rootDocument = settings?.browserRootDocument?.let {
                DocumentFile.fromTreeUri(this, it)
            }
            if (!keyManager.isKeyMissing) {
                if (indicator) showFakeSnackbar(getString(R.string.refreshing_list))
                loadAmiiboDocuments(rootDocument, settings?.isRecursiveEnabled == true)
            }
        } else {
            val rootFolder = settings?.browserRootFolder
            if (!keyManager.isKeyMissing) {
                if (indicator) showFakeSnackbar(getString(R.string.refreshing_list))
                loadAmiiboFiles(rootFolder, settings?.isRecursiveEnabled == true)
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
        pagerAdapter.browser.addFilterItemView(filterText, filterTag) {
            settings?.let {
                it.setFilter(filter, "")
                it.notifyChanges()
            }
            pagerAdapter.browser.setAmiiboStats()
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
        menuRecursiveFiles?.isChecked = settings?.isRecursiveEnabled == true
    }

    private val onReturnableIntent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        showBrowserPage()
        if (result.resultCode != RESULT_OK || null == result.data) return@registerForActivityResult
        onRootFolderChanged(true)
    }

    private val onUpdateTagResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        result.data?.let { intent ->
            if (NFCIntent.ACTION_NFC_SCANNED != intent.action
                && NFCIntent.ACTION_UPDATE_TAG != intent.action
                && NFCIntent.ACTION_EDIT_COMPLETE != intent.action
            ) return@registerForActivityResult

            // If we're supporting, didn't arrive from, but scanned an N2...
            if (prefs.eliteEnabled() && intent.hasExtra(NFCIntent.EXTRA_SIGNATURE)) {
                intent.extras?.let { showElitePage(it) }
            } else {
                updateAmiiboView(intent.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA))
                // toolbar.getMenu().findItem(R.id.mnu_write).setEnabled(false);
            }
        }
    }

    private fun deleteAmiiboFile(amiiboFile: AmiiboFile?) {
        amiiboFile?.filePath?.let { file ->
            val relativeFile = Storage.getRelativePath(file, prefs.preferEmulated())
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.warn_delete_file, relativeFile))
                .setPositiveButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .setNegativeButton(R.string.delete) { dialog: DialogInterface, _: Int ->
                    amiiboContainer?.isGone = true
                    file.delete()
                    IconifiedSnackbar(this, viewPager).buildSnackbar(
                        getString(R.string.delete_file, relativeFile), Snackbar.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
//                    settings?.let {
//                        it.amiiboFiles.remove(amiiboFile)
//                        it.notifyChanges()
//                    } ?: onRootFolderChanged(true)
                    onRootFolderChanged(true)
                }
                .show()
        } ?: Toasty(this).Short(R.string.delete_missing)
    }

    private fun deleteAmiiboDocument(amiiboFile: AmiiboFile?) {
        amiiboFile?.docUri?.let { docUri ->
            val relativeDocument = Storage.getRelativeDocument(docUri.uri)
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.warn_delete_file, relativeDocument))
                .setPositiveButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .setNegativeButton(R.string.delete) { dialog: DialogInterface, _: Int ->
                    amiiboContainer?.isGone = true
                    docUri.delete()
                    IconifiedSnackbar(this, viewPager).buildSnackbar(
                        getString(R.string.delete_file, relativeDocument), Snackbar.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
//                    settings?.let {
//                        it.amiiboFiles.remove(amiiboFile)
//                        it.notifyChanges()
//                    } ?: onRootFolderChanged(true)
                    onRootFolderChanged(true)
                }
                .show()
        } ?: deleteAmiiboFile(amiiboFile)
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
        amiiboContainer?.let {
            it.alpha = 0f
            it.isVisible = true
            it.animate().alpha(1f).setDuration(150).setListener(null)
        }
        onCreateToolbarMenu(toolbar, tagData, amiiboFile)
        var amiiboId: Long = -1
        var tagInfo: String? = null
        var amiiboHexId = ""
        var amiiboName = ""
        var amiiboSeries = ""
        var amiiboType = ""
        var gameSeries = ""
        // String character = "";
        var amiiboImageUrl: String? = null
        if (tagData?.isNotEmpty() == true) {
            try {
                amiiboId = Amiibo.dataToId(tagData)
            } catch (e: Exception) { Debug.verbose(e) }
        }
        when (amiiboId) {
            -1L -> {
                tagInfo = getString(R.string.read_error)
                amiiboImageUrl = null
            }
            0L -> {
                tagInfo = getString(R.string.blank_tag)
                amiiboImageUrl = null
            }
            else -> {
                var amiibo: Amiibo? = null
                settings?.amiiboManager?.let {
                    amiibo = it.amiibos[amiiboId]
                    if (null == amiibo)
                        amiibo = Amiibo(it, amiiboId, null, null)
                }
                amiibo?.let {
                    amiiboHexId = Amiibo.idToHex(it.id)
                    amiiboImageUrl = it.imageUrl
                    it.name?.let { name -> amiiboName = name }
                    it.amiiboSeries?.let { series -> amiiboSeries = series.name }
                    it.amiiboType?.let { type -> amiiboType = type.name }
                    it.gameSeries?.let { series -> gameSeries = series.name }
                } ?: amiiboId.let {
                    amiiboHexId = Amiibo.idToHex(it)
                    tagInfo = "ID: $amiiboHexId"
                    amiiboImageUrl = Amiibo.getImageUrl(it)
                }
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
            val label = findViewById<TextView>(R.id.txtUsageLabel)
            if (getGameCompatibility(txtUsage, tagData)) {
                label.setOnClickListener {
                    label.setText(
                        if (txtUsage.isVisible)
                            R.string.game_titles_view
                        else
                            R.string.game_titles_hide
                    )
                    txtUsage.isGone = txtUsage.isVisible
                }
                label.isVisible = true
            } else {
                label.isVisible = false
            }
            txtUsage.isGone = true

        } catch (ex: Exception) { Debug.warn(ex) }
        if (hasSpoofData(amiiboHexId)) txtTagId?.isEnabled = false
        imageAmiibo?.let {
            it.setImageResource(0)
            it.isGone = true
            if (!amiiboImageUrl.isNullOrEmpty()) {
                val imageTarget = ImageTarget.getTargetHR(it)
                Glide.with(it).clear(it)
                Glide.with(it).asBitmap().load(amiiboImageUrl).into(imageTarget)
            }
            val amiiboTagId = amiiboId
            it.setOnClickListener {
                startActivity(Intent(this@BrowserActivity, ImageActivity::class.java)
                    .putExtras(Bundle().apply {
                        putLong(NFCIntent.EXTRA_AMIIBO_ID, amiiboTagId)
                    })
                )
            }
        }
    }

    val columnCount: Int
        get() {
            val metrics = DisplayMetrics()
            val columns = (getSystemService(WINDOW_SERVICE) as WindowManager).run {
                if (Version.isSnowCone) {
                    val bounds: Rect = currentWindowMetrics.bounds
                    ((bounds.width() / (resources.configuration.densityDpi / 160)) + 0.5) / 120
                } else @Suppress("deprecation") {
                    defaultDisplay.getRealMetrics(metrics)
                    ((metrics.widthPixels / metrics.density) + 0.5) / 120
                }
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



    private fun showFakeSnackbar(msg: String) {
        fakeSnackbar?.post {
            fakeSnackbarItem?.visibility = View.INVISIBLE
            fakeSnackbarText?.text = msg
            fakeSnackbar?.isVisible = true
        }
    }

    private fun hideFakeSnackbar() {
        CoroutineScope(Dispatchers.Main).launch {
            fakeSnackbar?.let {
                if (it.isVisible) {
                    val animate = TranslateAnimation(
                        0f, 0f, 0f, (-it.height).toFloat()
                    ).apply {
                        duration = 150
                        fillAfter = false
                    }
                    it.setAnimationListener(object :
                        AnimatedLinearLayout.AnimationListener {
                        override fun onAnimationStart(layout: AnimatedLinearLayout?) {}
                        override fun onAnimationEnd(layout: AnimatedLinearLayout?) {
                            it.clearAnimation()
                            layout?.setAnimationListener(null)
                            it.isGone = true
                            snackbarListener?.onSnackbarHidden(it)
                        }
                    })
                    it.startAnimation(animate)
                }
            }
        }
    }

    fun showDonationPanel() {
        donationManager.onSendDonationClicked()
    }

    fun restoreMenuLayout() {
        settingsPage?.isGone = true
        if (reloadTabCollection) {
            reloadTabCollection = false
            onTabCollectionChanged()
        }
    }
    fun closePrefsDrawer(): Boolean {
        prefsDrawer?.let {
            if (it.isDrawerOpen(GravityCompat.START)) {
                it.closeDrawer(GravityCompat.START)
                settingsPage?.isGone = true
                return true
            }
        }
        return false
    }

    private fun onLoadSettingsFragment() {
        if (null == fragmentSettings) fragmentSettings = SettingsFragment()
        fragmentSettings?.let {
            if (!it.isAdded) supportFragmentManager.beginTransaction().replace(R.id.preferences, it).commit()
        }
    }

    private fun onShowSettingsFragment() {
        CoroutineScope(Dispatchers.Main).launch {
            if (BuildConfig.WEAR_OS) {
                settingsPage?.isVisible = true
            } else {
                prefsDrawer?.openDrawer(GravityCompat.START)
                settingsPage?.isVisible = true
            }
        }
    }

    fun showBrowserPage() {
        if (viewPager.currentItem != 0) viewPager.setCurrentItem(0, false)
        pagerAdapter.browser.setFoomiiboVisibility(false)
    }

    private fun showGattPage(extras: Bundle) {
        val index = if (prefs.eliteEnabled()) 3 else 2
        pagerAdapter.gattSlots.arguments = extras
        if (viewPager.currentItem != index) {
            viewPager.setCurrentItem(index, false)
        } else {
            pagerAdapter.gattSlots.processArguments()
        }
    }

    fun showElitePage(extras: Bundle) {
        pagerAdapter.eliteBanks.arguments = extras
        if (viewPager.currentItem == 2) {
            pagerAdapter.eliteBanks.processArguments()
            return
        }
        if (TagMo.isUserInputEnabled) {
            setScrollListener(object : ScrollListener {
                override fun onScrollComplete() {
                    pagerAdapter.eliteBanks.processArguments()
                    scrollListener = null
                }
            })
        } else {
            viewPager.postDelayed({
                pagerAdapter.eliteBanks.processArguments()
            }, TagMo.uiDelay.toLong())
        }
        viewPager.setCurrentItem(2, false)
    }

    fun showWebsite(address: String?) {
        if (viewPager.currentItem == pagerAdapter.itemCount - 1) {
            pagerAdapter.website.loadWebsite(address)
            return
        }
        if (TagMo.isUserInputEnabled) {
            setScrollListener(object : ScrollListener {
                override fun onScrollComplete() {
                    pagerAdapter.website.loadWebsite(address)
                    scrollListener = null
                }
            })
        } else {
            viewPager.postDelayed({
                pagerAdapter.website.loadWebsite(address)
            }, TagMo.uiDelay.toLong())
        }
        viewPager.setCurrentItem(1, false)
    }

    private fun keyNameMatcher(name: String): Boolean {
        val isValid = binFileMatches(name)
        return name.lowercase().endsWith("retail.bin") ||
                isValid && (name.lowercase().startsWith("locked")
                || name.lowercase().startsWith("unfixed"))
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private suspend fun locateKeyDocumentsRecursive() = withContext(Dispatchers.IO) {
        settings?.browserRootDocument?.let {
            AmiiboDocument(this@BrowserActivity).listFiles(it, true)
        }.also { uris ->
            if (uris.isNullOrEmpty()) {
                hideFakeSnackbar()
                onShowSettingsFragment()
            } else {
                coroutineScope { uris.map { uri -> async(Dispatchers.IO) {
                    try {
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            try {
                                keyManager.evaluateKey(inputStream)
                            } catch (ex: Exception) {
                                onShowSettingsFragment()
                            }
                            hideFakeSnackbar()
                        }
                    } catch (e: Exception) { Debug.warn(e) }
                } }.awaitAll() }
                if (keyManager.isKeyMissing) {
                    onShowSettingsFragment()
                    hideFakeSnackbar()
                }
            }
        } ?: {
            hideFakeSnackbar()
            onShowSettingsFragment()
        }

    }

    private suspend fun locateKeyFilesRecursive(rootFolder: File?, isRoot: Boolean) {
        withContext(Dispatchers.IO) {
            rootFolder?.listFiles { _: File?, name: String -> keyNameMatcher(name) }.also { files ->
                if (files.isNullOrEmpty()) {
                    rootFolder?.listFiles().also { directories ->
                        if (directories.isNullOrEmpty()){
                            if (isRoot) {
                                hideFakeSnackbar()
                                onShowSettingsFragment()
                            }
                        } else {
                            coroutineScope { directories.map { file -> async(Dispatchers.IO) {
                                if (file.isDirectory) locateKeyFilesRecursive(file, false)
                            } }.awaitAll() }
                        }
                    }
                } else {
                    coroutineScope { files.map { file -> async(Dispatchers.IO) {
                        try {
                            FileInputStream(file).use { inputStream ->
                                try {
                                    keyManager.evaluateKey(inputStream)
                                } catch (ignored: Exception) { }
                            }
                        } catch (e: Exception) { Debug.warn(e) }
                    }
                    } }.awaitAll() }
                if (keyManager.isKeyMissing) {
                    onShowSettingsFragment()
                    hideFakeSnackbar()
                }
            } ?: {
                if (isRoot) {
                    hideFakeSnackbar()
                    onShowSettingsFragment()
                }
            }
        }
    }

    private fun locateKeyFiles() {
        CoroutineScope(Dispatchers.IO).launch {
            Storage.getDownloadDir(null).listFiles {
                    _: File?, name: String -> keyNameMatcher(name)
            }.also { files ->
                if (files.isNullOrEmpty()) {
                    if (Version.isLollipop && prefs.isDocumentStorage)
                        locateKeyDocumentsRecursive()
                    else
                        locateKeyFilesRecursive(Storage.getFile(prefs.preferEmulated()), true)
                } else {
                    coroutineScope { files.map { file -> async(Dispatchers.IO) {
                        try {
                            FileInputStream(file).use { inputStream ->
                                try {
                                    keyManager.evaluateKey(inputStream)
                                } catch (ignored: Exception) { }
                            }
                        } catch (e: Exception) {
                            Debug.warn(e)
                        }
                    } }.awaitAll() }
                    if (keyManager.isKeyMissing) {
                        onShowSettingsFragment()
                        hideFakeSnackbar()
                    }
                }
            }
        }
    }

    private var onRequestStorage = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { permissions: Map<String, Boolean> ->
        var isStorageEnabled = true
        if (BuildConfig.WEAR_OS) {
            isStorageEnabled = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        } else {
            permissions.entries.forEach {
                if (!it.value) isStorageEnabled = false
            }
        }
        if (isStorageEnabled) onStorageEnabled() else onDocumentEnabled()
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    val onRequestScopedStorage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Environment.isExternalStorageManager()) {
            settings?.let {
                it.browserRootDocument = null
                it.notifyChanges()
            }
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
        prefs.downloadUrl()?.let {
            if (packageManager.canRequestPackageInstalls())
                updateManager?.installDownload(it)
            prefs.remove(prefs.downloadUrl)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when(viewPager.currentItem) {
            0 -> { pagerAdapter.browser.onConfigurationChanged() }
            2 -> {
                if (prefs.eliteEnabled()) {
                    pagerAdapter.eliteBanks.onConfigurationChanged()
                } else {
                    pagerAdapter.gattSlots.onConfigurationChanged()
                }
            }
            3 -> { pagerAdapter.gattSlots.onConfigurationChanged() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tagScanner.onNewIntent(this@BrowserActivity, intent)
    }

    override fun onStart() {
        super.onStart()
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (BuildConfig.WEAR_OS) {
                    when {
                        browserSheet?.state == BottomSheetBehavior.STATE_EXPANDED -> {
                            browserSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
                        }
                        amiiboContainer?.isVisible == true -> {
                            amiiboContainer?.isGone = true
                        }
                        viewPager.currentItem != 0 -> {
                            if (viewPager.currentItem == 1 && pagerAdapter.website.hasGoneBack())
                                return
                            else
                                showBrowserPage()
                        }
                        else -> {
                            finishAffinity()
                        }
                    }
                } else {
                    when {
                        !closePrefsDrawer() -> {
                            if (browserSheet?.state == BottomSheetBehavior.STATE_EXPANDED) {
                                browserSheet?.setState(BottomSheetBehavior.STATE_COLLAPSED)
                            } else if (amiiboContainer?.isVisible == true) {
                                amiiboContainer?.isGone = true
                            } else if (viewPager.currentItem != 0) {
                                if (viewPager.currentItem == 1 && pagerAdapter.website.hasGoneBack())
                                    return
                                else
                                    showBrowserPage()
                            } else {
                                finishAffinity()
                            }
                        }
                    }
                }
            }
        })
        if (animationArray.isNullOrEmpty()) {
            val colorRed = ContextCompat.getColor(this, android.R.color.holo_red_light)
            val colorGreen = ContextCompat.getColor(this, android.R.color.holo_green_light)
            val colorBlue = ContextCompat.getColor(this, android.R.color.holo_blue_light)
            animationArray = arrayListOf(
                ValueAnimator.ofObject(ArgbEvaluator(), colorRed, colorGreen),
                ValueAnimator.ofObject(ArgbEvaluator(), colorGreen, colorBlue),
                ValueAnimator.ofObject(ArgbEvaluator(), colorBlue, colorRed)
            )
        }
    }

    override fun onStop() {
        super.onStop()
        animationArray?.clear()
    }

    companion object {
        private val PERMISSIONS_STORAGE = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    private fun setScrollListener(listener: ScrollListener) {
        scrollListener = listener
    }

    interface ScrollListener {
        fun onScrollComplete()
    }

    private fun setSnackbarListener(listener: SnackbarListener) {
        snackbarListener = listener
    }

    interface SnackbarListener {
        fun onSnackbarHidden(fakeSnackbar: AnimatedLinearLayout)
    }
}