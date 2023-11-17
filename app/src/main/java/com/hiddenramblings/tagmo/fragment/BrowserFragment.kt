package com.hiddenramblings.tagmo.fragment

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.hiddenramblings.tagmo.*
import com.hiddenramblings.tagmo.BrowserSettings.BrowserSettingsListener
import com.hiddenramblings.tagmo.adapter.BrowserAdapter
import com.hiddenramblings.tagmo.adapter.FoldersAdapter
import com.hiddenramblings.tagmo.adapter.FoomiiboAdapter
import com.hiddenramblings.tagmo.adapter.FoomiiboAdapter.OnFoomiiboClickListener
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.amiibo.Character
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.eightbit.widget.ProgressAlert
import com.hiddenramblings.tagmo.nfctech.Foomiibo
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.hiddenramblings.tagmo.widget.Toasty
import com.robertlevonyan.views.chip.Chip
import com.robertlevonyan.views.chip.OnCloseClickListener
import kotlinx.coroutines.*
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import java.io.File

private val Number.toPx get() = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), Resources.getSystem().displayMetrics
).toInt()

class BrowserFragment : Fragment(), OnFoomiiboClickListener {
    private val prefs: Preferences by lazy { Preferences(TagMo.appContext) }
    private val keyManager: KeyManager by lazy { (requireActivity() as BrowserActivity).keyManager }

    private var chipList: FlexboxLayout? = null
    private var browserWrapper: SwipeRefreshLayout? = null
    var browserContent: RecyclerView? = null
        private set
    var foomiiboContent: RecyclerView? = null
        private set
    private lateinit var settings: BrowserSettings
    var bottomSheet: BottomSheetBehavior<View>? = null
        private set
    private var currentFolderView: TextView? = null
    private val resultData: ArrayList<ByteArray> = arrayListOf()

    private val statsHandler = Handler(Looper.getMainLooper())

    val onUpdateTagResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != AppCompatActivity.RESULT_OK) return@registerForActivityResult
        result.data?.let { intent ->
            if (NFCIntent.ACTION_NFC_SCANNED != intent.action
                && NFCIntent.ACTION_UPDATE_TAG != intent.action
                && NFCIntent.ACTION_EDIT_COMPLETE != intent.action
            ) return@registerForActivityResult
            val tagData = intent.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA)
            if (tagData?.isNotEmpty() == true) {
                var updated = false
                for (data in resultData) {
                    try {
                        if (data.isNotEmpty() && Amiibo.dataToId(data) == Amiibo.dataToId(tagData)) {
                            updated = true
                            resultData[resultData.indexOf(data)] = tagData
                            break
                        }
                    } catch (ignored: Exception) { }
                }
                if (!updated) resultData.add(tagData)
            }
        }
    }

    private val onSelectArchiveFile = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
    ) { uri ->
        bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
        if (null == uri) {
            Toasty(requireContext()).Short(R.string.error_uri_unknown)
            return@registerForActivityResult
        }
        (requireActivity() as BrowserActivity).decompressArchive(uri)
    }

    private var browserActivity: BrowserActivity? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (activity is BrowserActivity) browserActivity = activity as BrowserActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_browser, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        browserActivity?.let { activity ->
            settings = activity.settings ?: BrowserSettings()

            chipList = view.findViewById(R.id.chip_list)
            chipList?.isGone = true

            browserWrapper = view.findViewById<SwipeRefreshLayout>(R.id.browser_wrapper).apply {
                setOnRefreshListener {
                    if (!activity.isRefreshing) activity.onRefresh(true)
                    isRefreshing = false
                }
            }
            browserContent = view.findViewById<RecyclerView>(R.id.browser_content).apply {
                layoutManager = if (settings.amiiboView == BrowserSettings.VIEW.IMAGE.value)
                    GridLayoutManager(activity, activity.columnCount)
                else
                    LinearLayoutManager(activity)
                adapter = BrowserAdapter(settings, activity).also {
                    settings.addChangeListener(it)
                }
                FastScrollerBuilder(this).build().setPadding(0, (-2).toPx, 0, 0)
            }

            foomiiboContent = view.findViewById<RecyclerView>(R.id.foomiibo_list).apply {
                layoutManager = if (settings.amiiboView == BrowserSettings.VIEW.IMAGE.value)
                    GridLayoutManager(activity, activity.columnCount)
                else
                    LinearLayoutManager(activity)
                adapter = FoomiiboAdapter(settings, this@BrowserFragment).also {
                    settings.addChangeListener(it)
                }
                FastScrollerBuilder(this).build()
            }

            currentFolderView = view.findViewById(R.id.current_folder)
            view.findViewById<RecyclerView>(R.id.folders_list).apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = FoldersAdapter(settings)
                settings.addChangeListener(adapter as? BrowserSettingsListener)
            }

            val toggle = view.findViewById<AppCompatImageView>(R.id.toggle)
            bottomSheet = BottomSheetBehavior.from(view.findViewById(R.id.bottom_sheet)).apply {
                state = BottomSheetBehavior.STATE_COLLAPSED
                addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                            toggle.setImageResource(R.drawable.ic_expand_less_white_24dp)
                        } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                            setFolderText(settings)
                            setStorageButtons()
                            toggle.setImageResource(R.drawable.ic_expand_more_white_24dp)
                        }
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {}
                })
            }.also { bottomSheet ->
                toggle.setOnClickListener {
                    if (bottomSheet.state == BottomSheetBehavior.STATE_COLLAPSED) {
                        bottomSheet.setState(BottomSheetBehavior.STATE_EXPANDED)
                    } else {
                        bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
                    }
                }
            }

            val foomiiboOptions = view.findViewById<LinearLayout>(R.id.foomiibo_options)
            foomiiboOptions.findViewById<View>(R.id.clear_foomiibo_set).setOnClickListener {
                clearFoomiiboSet(activity)
            }
            foomiiboOptions.findViewById<View>(R.id.build_foomiibo_set).setOnClickListener {
                buildFoomiiboSet(activity)
            }
            view.findViewById<View>(R.id.select_zip_file).setOnClickListener {
                onSelectArchiveFile.launch(arrayOf(getString(R.string.mimetype_zip)))
            }
            activity.onFilterContentsLoaded()
        }
    }

    fun onConfigurationChanged() {
        browserActivity?.let { activity ->
            browserContent?.layoutManager = GridLayoutManager(activity, activity.columnCount)
            foomiiboContent?.layoutManager = GridLayoutManager(activity, activity.columnCount)
        }
    }

    @SuppressLint("InflateParams")
    fun addFilterItemView(text: String?, tag: String?, listener: OnCloseClickListener?) {
        chipList?.let { flex ->
            var chipContainer = flex.findViewWithTag<FrameLayout>(tag)
            chipContainer?.let { flex.removeView(it) }
            if (!text.isNullOrEmpty()) {
                chipContainer = layoutInflater.inflate(R.layout.chip_view, null) as FrameLayout
                chipContainer.tag = tag
                chipContainer.findViewById<Chip>(R.id.chip).run {
                    setText(text)
                    closable = true
                    onCloseClickListener = listener
                }
                flex.addView(chipContainer)
                flex.isVisible = true
            } else if (chipList?.childCount == 0) {
                flex.isGone = true
            }
        }
    }

    val managerStats: Unit
        get() {
            if (null == view) return
            val characterStats = requireView().findViewById<TextView>(R.id.stats_character)
            val amiiboTypeStats = requireView().findViewById<TextView>(R.id.stats_amiibo_type)
            val amiiboTitleStats = requireView().findViewById<TextView>(R.id.stats_amiibo_titles)
            val amiiboManager = settings.amiiboManager
            val hasAmiibo = null != amiiboManager

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
                    val items: java.util.ArrayList<Character> = arrayListOf()
                    amiiboManager?.characters?.values?.forEach {
                        if (!items.contains(it)) items.add(it)
                    }
                    items.sort()
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.pref_amiibo_characters)
                        .setAdapter(object : ArrayAdapter<Character>(
                            requireContext(), android.R.layout.simple_list_item_2,
                            android.R.id.text1, items
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
                    val amiiboTypes = java.util.ArrayList(
                        amiiboManager!!.amiiboTypes.values
                    )
                    amiiboTypes.sort()
                    val items: java.util.ArrayList<String> = arrayListOf()
                    amiiboTypes.forEach {
                        if (!items.contains(it.name)) items.add(it.name)
                    }
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.pref_amiibo_types)
                        .setAdapter(
                            ArrayAdapter(
                                requireContext(), android.R.layout.simple_list_item_1, items
                            ), null)
                        .setPositiveButton(R.string.close, null)
                        .show()
                }
            }
            val gamesManager = settings.gamesManager
            val hasGames = null != amiiboManager && null != gamesManager
            amiiboTitleStats.text = getString(
                R.string.number_titles, if (hasGames) gamesManager?.gameTitles?.size else 0
            )
            if (hasGames) {
                amiiboTitleStats.setOnClickListener {
                    val items: java.util.ArrayList<String> = arrayListOf()
                    gamesManager?.gameTitles?.forEach {
                        if (!items.contains(it.name)) items.add(it.name)
                    }
                    items.sort()
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.pref_amiibo_titles)
                        .setAdapter(
                            ArrayAdapter(
                                requireContext(), android.R.layout.simple_list_item_1, items
                            ), null)
                        .setPositiveButton(R.string.close, null)
                        .show()
                }
            }
        }

    private fun getAdapterStats(amiiboManager: AmiiboManager): IntArray {
        if (browserContent?.adapter is BrowserAdapter) {
            val adapter = browserContent?.adapter as BrowserAdapter
            val count = amiiboManager.amiibos.values.count { adapter.hasItem(it.id) }
            return intArrayOf(adapter.itemCount, count)
        }
        return intArrayOf(0, 0)
    }

    fun setAmiiboStats() {
        statsHandler.removeCallbacksAndMessages(null)
        if (isDetached) return
        browserActivity?.let { activity ->
            if (activity.viewPager.currentItem != 0) return
            currentFolderView?.run {
                val size = settings.amiiboFiles.size
                if (size <= 0) return@run
                gravity = Gravity.CENTER
                settings.amiiboManager?.let {
                    var count = 0
                    if (!settings.query.isNullOrEmpty()) {
                        val stats = getAdapterStats(it)
                        text = getString(
                            R.string.amiibo_collected,
                            stats[0], stats[1], activity.getQueryCount(settings.query)
                        )
                    } else if (!settings.isFilterEmpty) {
                        val stats = getAdapterStats(it)
                        text = getString(
                            R.string.amiibo_collected,
                            stats[0], stats[1], activity.filteredCount
                        )
                    } else {
                        it.amiibos.values.forEach { amiibo ->
                            settings.amiiboFiles.forEach { amiiboFile ->
                                if (amiibo.id == amiiboFile?.id) count += 1
                            }
                        }
                        text = getString(
                            R.string.amiibo_collected,
                            size, count, it.amiibos.size
                        )
                    }
                } ?: size.let {
                    text = getString(R.string.files_displayed, it)
                }
            }
        }
    }

    fun setFolderText(textSettings: BrowserSettings?) {
        textSettings?.also {
            val relativePath: String = if (prefs.isDocumentStorage) {
                Storage.getRelativeDocument(it.browserRootDocument)
            } else {
                val rootFolder = it.browserRootFolder
                val relativeRoot = Storage.getRelativePath(
                    rootFolder, prefs.preferEmulated()
                )
                relativeRoot.ifEmpty { rootFolder?.absolutePath ?: "" }
            }
            currentFolderView?.gravity = Gravity.CENTER_VERTICAL
            currentFolderView?.text = relativePath
            statsHandler.postDelayed({ setAmiiboStats() }, 3000)
        } ?: setAmiiboStats()
    }

    fun setStorageButtons() {
        if (BuildConfig.WEAR_OS) return
        browserActivity?.let { activity ->
            val switchStorageRoot = requireView().findViewById<AppCompatButton>(R.id.switch_storage_root)
            val switchStorageType = requireView().findViewById<AppCompatButton>(R.id.switch_storage_type)
            if (prefs.isDocumentStorage) {
                switchStorageRoot?.let { button ->
                    button.isVisible = true
                    button.setText(R.string.document_storage_root)
                    button.setOnClickListener {
                        bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
                        try {
                            activity.onDocumentRequested()
                        } catch (anf: ActivityNotFoundException) {
                            Toasty(activity).Long(R.string.storage_unavailable)
                        }
                    }
                }
                switchStorageType?.let { button ->
                    if (Version.isRedVelvet && !BuildConfig.GOOGLE_PLAY) {
                        button.isVisible = true
                        button.setText(R.string.grant_file_permission)
                        button.setOnClickListener {
                            bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
                            if (Environment.isExternalStorageManager()) {
                                settings.browserRootDocument = null
                                settings.notifyChanges()
                                activity.onStorageEnabled()
                            } else {
                                activity.requestScopedStorage()
                            }
                        }
                    } else {
                        button.isGone = true
                    }
                }
            } else {
                val internal = prefs.preferEmulated()
                val storage = Storage.getFile(internal)
                if (storage?.exists() == true && Storage.hasPhysicalStorage()) {
                    switchStorageRoot?.let { button ->
                        button.isVisible = true
                        button.setText(if (internal)
                            R.string.emulated_storage_root
                        else
                            R.string.physical_storage_root
                        )
                        button.setOnClickListener {
                            val external = !prefs.preferEmulated()
                            button.setText(if (external)
                                R.string.emulated_storage_root
                            else
                                R.string.physical_storage_root
                            )
                            settings.browserRootFolder = Storage.getFile(external)
                            settings.notifyChanges()
                            prefs.preferEmulated(external)
                        }
                    }

                } else {
                    switchStorageRoot?.isGone = true
                }
                switchStorageType?.let { button ->
                    if (Version.isRedVelvet) {
                        button.isVisible = true
                        button.setText(R.string.force_document_storage)
                        button.setOnClickListener {
                            bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
                            try {
                                activity.onDocumentRequested()
                            } catch (anf: ActivityNotFoundException) {
                                Toasty(activity).Long(R.string.storage_unavailable)
                            }
                        }
                    } else {
                        button.isGone = true
                    }
                }
            }
        }
    }

    fun setFoomiiboVisibility(isActive: Boolean) {
        browserWrapper?.isVisible = !isActive
        foomiiboContent?.isVisible = isActive
    }

    fun deleteFoomiiboFile(tagData: ByteArray?) {
        try {
            val amiibo = settings.amiiboManager?.amiibos?.get(Amiibo.dataToId(tagData))
                ?: throw Exception()
            val directory = amiibo.amiiboSeries?.let {
                File(Foomiibo.directory, it.name)
            } ?: Foomiibo.directory
            val amiiboFile = File(
                directory, "${TagArray.decipherFilename(amiibo, tagData, false)}.bin"
            )
            AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.warn_delete_file, amiiboFile.name))
                .setPositiveButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .setNegativeButton(R.string.delete) { dialog: DialogInterface, _: Int ->
                    if (amiiboFile.delete()) {
                        IconifiedSnackbar(requireActivity(), browserWrapper).buildSnackbar(
                            getString(R.string.delete_foomiibo, amiibo.name),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    } else {
                        Toasty(requireContext()).Short(R.string.delete_virtual)
                    }
                    dialog.dismiss()
                }
                .show()
        } catch (e: Exception) { Toasty(requireContext()).Short(R.string.delete_virtual) }
    }

    private fun clearFoomiiboSet(activity: AppCompatActivity) {
        val dialog = ProgressAlert.show(activity, "").apply {
            setMessage(getString(R.string.foomiibo_removing, Foomiibo.directory.name))
        }
        CoroutineScope(Dispatchers.IO).launch {
            Foomiibo.directory.deleteRecursively()
            withContext(Dispatchers.Main) {
                dialog.dismiss()
                if (activity is BrowserActivity) activity.onRootFolderChanged(false)
            }
        }
    }

    private fun buildFoomiiboFile(amiibo: Amiibo) {
        try {
            val tagData = Foomiibo.getSignedData(amiibo.id)
            val directory = File(Foomiibo.directory, amiibo.amiiboSeries!!.name)
            directory.mkdirs()
            TagArray.writeBytesToFile(
                directory, TagArray.decipherFilename(amiibo, tagData, false), tagData
            )
        } catch (e: Exception) { Debug.warn(e) }
    }

    fun buildFoomiiboFile(tagData: ByteArray) {
        try {
            val amiibo = settings.amiiboManager?.amiibos?.get(Amiibo.dataToId(tagData)) ?: return
            val directory = amiibo.amiiboSeries?.let {
                File(Foomiibo.directory, it.name)
            } ?: Foomiibo.directory
            directory.mkdirs()
            val foomiiboData = Foomiibo.getSignedData(tagData)
            TagArray.writeBytesToFile(
                directory, TagArray.decipherFilename(amiibo, foomiiboData, false), foomiiboData
            )
            IconifiedSnackbar(requireActivity(), browserWrapper).buildSnackbar(
                getString(R.string.wrote_foomiibo, amiibo.name), Snackbar.LENGTH_SHORT
            ).show()
        } catch (e: Exception) { Debug.warn(e) }
    }

    private fun buildFoomiiboSet(activity: AppCompatActivity) {
        try {
            settings.amiiboManager?.let { amiiboManager ->
                val dialog = ProgressAlert.show(activity, "").apply {
                    setMessage(getString(R.string.foomiibo_removing, Foomiibo.directory.name))
                }
                CoroutineScope(Dispatchers.IO).launch {
                    Foomiibo.directory.deleteRecursively()
                    Foomiibo.directory.mkdirs()
                    amiiboManager.amiibos.values.forEach { amiibo ->
                        buildFoomiiboFile(amiibo)
                        withContext(Dispatchers.Main) {
                            amiibo.character?.let {
                                dialog.setMessage(getString(R.string.foomiibo_progress, it.name))
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        if (activity is BrowserActivity) activity.onRootFolderChanged(false)
                    }
                }
            } ?: Toasty(activity).Short(R.string.amiibo_failure_read)
        } catch (ex: Exception) {
            Toasty(activity).Short(R.string.amiibo_failure_read)
        }
    }

    private fun getGameCompatibility(txtUsage: TextView, tagData: ByteArray) {
        if (tagData.isEmpty()) return
        settings.gamesManager?.let {
            try {
                val amiiboId = Amiibo.dataToId(tagData)
                txtUsage.text = it.getGamesCompatibility(amiiboId)
            } catch (ex: Exception) {
                Debug.warn(ex)
            }
        }
    }

    private fun Amiibo?.toTagData(): ByteArray {
        var tagData = byteArrayOf()
        for (data in resultData) {
            try {
                if (data.isNotEmpty() && Amiibo.dataToId(data) == this?.id) {
                    tagData = data
                    break
                }
            } catch (ignored: Exception) { }
        }
        return try {
            if (tagData.isEmpty() && null != this)
                Foomiibo.getSignedData(Amiibo.idToHex(this.id))
            else
                TagArray.getValidatedData(keyManager, tagData)
        } catch (ignored: Exception) { byteArrayOf() }
    }

    override fun onFoomiiboClicked(itemView: View, amiibo: Amiibo?) {
        browserActivity?.let { activity ->
            val tagData = amiibo.toTagData()
            if (settings.amiiboView != BrowserSettings.VIEW.IMAGE.value) {
                itemView.findViewById<LinearLayout>(R.id.menu_options)?.let {
                    val toolbar = it.findViewById<Toolbar>(R.id.toolbar)
                    if (!it.isVisible)
                        activity.onCreateToolbarMenu(this, toolbar, tagData, itemView)
                    it.isGone = it.isVisible
                    itemView.findViewById<TextView>(R.id.txtUsage).apply {
                        if (isGone) getGameCompatibility(this, tagData)
                        isGone = isVisible
                    }
                }
            } else {
                activity.updateAmiiboView(tagData, null)
                activity.onCreateToolbarMenu(this, null, tagData, itemView)
            }
        }
    }

    override fun onFoomiiboRebind(itemView: View, amiibo: Amiibo?) {
        browserActivity?.let { activity ->
            val tagData = amiibo.toTagData()
            if (settings.amiiboView != BrowserSettings.VIEW.IMAGE.value) {
                itemView.findViewById<LinearLayout>(R.id.menu_options)?.let {
                    val toolbar = it.findViewById<Toolbar>(R.id.toolbar)
                    activity.onCreateToolbarMenu(this, toolbar, tagData, itemView)
                    itemView.findViewById<TextView>(R.id.txtUsage).apply {
                        getGameCompatibility(this, tagData)
                    }
                }
            } else {
                activity.updateAmiiboView(tagData, null)
                activity.onCreateToolbarMenu(this, null, tagData, itemView)
            }
        }
    }

    override fun onFoomiiboImageClicked(amiibo: Amiibo?) {
        if (null == amiibo) return
        this.startActivity(Intent(requireContext(), ImageActivity::class.java)
            .putExtras(Bundle().apply {
                putLong(NFCIntent.EXTRA_AMIIBO_ID, amiibo.id)
            })
        )
    }
}