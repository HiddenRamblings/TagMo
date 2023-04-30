package com.hiddenramblings.tagmo.browser.fragment

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.snackbar.Snackbar
import com.hiddenramblings.tagmo.*
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.amiibo.games.GamesManager.Companion.getGamesManager
import com.hiddenramblings.tagmo.browser.BrowserActivity
import com.hiddenramblings.tagmo.browser.BrowserSettings
import com.hiddenramblings.tagmo.browser.BrowserSettings.BrowserSettingsListener
import com.hiddenramblings.tagmo.browser.ImageActivity
import com.hiddenramblings.tagmo.browser.adapter.BrowserAdapter
import com.hiddenramblings.tagmo.browser.adapter.FoomiiboAdapter
import com.hiddenramblings.tagmo.browser.adapter.FoomiiboAdapter.OnFoomiiboClickListener
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.nfctech.Foomiibo
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.hiddenramblings.tagmo.widget.ProgressAlert
import com.hiddenramblings.tagmo.widget.Toasty
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.robertlevonyan.views.chip.Chip
import com.robertlevonyan.views.chip.OnCloseClickListener
import kotlinx.coroutines.*
import java.io.File

class BrowserFragment : Fragment(), OnFoomiiboClickListener {
    private val prefs: Preferences by lazy { Preferences(requireContext().applicationContext) }
    private val keyManager: KeyManager by lazy { KeyManager(requireContext()) }

    private var chipList: FlexboxLayout? = null
    private var browserScroller: RecyclerViewFastScroller? = null
    var browserContent: RecyclerView? = null
        private set
    var foomiiboContent: RecyclerView? = null
        private set
    private lateinit var directory: File
    private val foomiibo = Foomiibo()
    private lateinit var settings: BrowserSettings
    private val resultData: ArrayList<ByteArray> = arrayListOf()

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
                run breaking@{
                    resultData.forEach { data ->
                        try {
                            if (data.isNotEmpty() && Amiibo.dataToId(data) == Amiibo.dataToId(tagData)) {
                                updated = true
                                resultData[resultData.indexOf(data)] = tagData
                                return@breaking
                            }
                        } catch (ignored: Exception) { }
                    }
                }
                if (!updated) resultData.add(tagData)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_browser, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as BrowserActivity
        settings = activity.settings ?: BrowserSettings().initialize()
        directory = File(activity.filesDir, "Foomiibo")
        directory.mkdirs()
        chipList = view.findViewById(R.id.chip_list)
        chipList?.isGone = true

        browserContent = view.findViewById(R.id.browser_content)
        if (prefs.softwareLayer())
            // browserContent?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            browserContent?.setLayerType(View.LAYER_TYPE_NONE, null)
        browserContent?.layoutManager = if (settings.amiiboView == BrowserSettings.VIEW.IMAGE.value)
            GridLayoutManager(activity, activity.columnCount)
        else LinearLayoutManager(activity)
        browserContent?.adapter = BrowserAdapter(settings, activity)
        settings.addChangeListener(browserContent?.adapter as BrowserSettingsListener?)

        foomiiboContent = view.findViewById(R.id.foomiibo_list)
        if (prefs.softwareLayer())
            // foomiiboContent?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            foomiiboContent?.setLayerType(View.LAYER_TYPE_NONE, null)
        foomiiboContent?.layoutManager = if (settings.amiiboView == BrowserSettings.VIEW.IMAGE.value)
            GridLayoutManager(activity, activity.columnCount)
        else LinearLayoutManager(activity)
        foomiiboContent?.adapter = FoomiiboAdapter(settings, this)
        settings.addChangeListener(foomiiboContent?.adapter as BrowserSettingsListener?)

        view.findViewById<View>(R.id.list_divider).isGone = true
        browserScroller = view.findViewById(R.id.browser_scroller)
        val browserParams = browserScroller?.layoutParams
        if (BuildConfig.WEAR_OS && null != browserParams)
            browserParams.height = browserParams.height / 3
        view.findViewById<View>(R.id.list_divider).setOnTouchListener { v: View, event: MotionEvent ->
            browserScroller?.layoutParams?.let { layoutParams ->
                val srcHeight = layoutParams.height
                val y = event.y.toInt()
                if (layoutParams.height + y >= -0.5f) {
                    if (event.action == MotionEvent.ACTION_MOVE) {
                        layoutParams.height += y
                    } else if (event.action == MotionEvent.ACTION_UP) {
                        if (layoutParams.height + y < 0f) {
                            layoutParams.height = 0
                        } else {
                            val peekHeight = activity.bottomSheetBehavior?.peekHeight ?: 0
                            val minHeight = peekHeight + v.height + requireContext().resources
                                .getDimension(R.dimen.sliding_bar_margin)
                            if (layoutParams.height > view.height - minHeight.toInt())
                                layoutParams.height = view.height - minHeight.toInt()
                        }
                    }
                    browserScroller?.let {
                        if (srcHeight != layoutParams.height) it.requestLayout()
                        prefs.foomiiboOffset(it.layoutParams.height) }
                }
            }
            true
        }
        activity.onFilterContentsLoaded()
    }

    @SuppressLint("InflateParams")
    fun addFilterItemView(text: String?, tag: String?, listener: OnCloseClickListener?) {
        if (null == chipList) return
        var chipContainer = chipList?.findViewWithTag<FrameLayout>(tag)
        chipContainer?.let { chipList?.removeView(it) }
        if (!text.isNullOrEmpty()) {
            chipContainer = layoutInflater.inflate(R.layout.chip_view, null) as FrameLayout
            chipContainer.tag = tag
            chipContainer.findViewById<Chip>(R.id.chip).run {
                setText(text)
                closable = true
                onCloseClickListener = listener
            }
            chipList?.addView(chipContainer)
            chipList?.isVisible = true
        } else if (chipList?.childCount == 0) {
            chipList?.isGone = true
        }
    }

    fun setFoomiiboVisibility() {
        if (null == view) return
        val activity = requireActivity() as BrowserActivity
        val divider = requireView().findViewById<View>(R.id.list_divider)
        val peekHeight = activity.bottomSheetBehavior?.peekHeight ?: 0
        val minHeight = (peekHeight + divider.height + requireContext().resources
            .getDimension(R.dimen.sliding_bar_margin))
        val layoutParams = browserScroller?.layoutParams
        val srcHeight = layoutParams?.height
        layoutParams?.let {
            if (it.height > requireView().height - minHeight.toInt()) {
                it.height = requireView().height - minHeight.toInt()
            } else {
                val valueY = prefs.foomiiboOffset()
                it.height = if (valueY != -1) valueY else it.height
            }
        }
        if (prefs.foomiiboDisabled()) {
            divider.isGone = true
            layoutParams?.height = requireView().height
        } else {
            divider.isVisible = true
        }
        if (srcHeight != layoutParams?.height) browserScroller?.requestLayout()
    }

    override fun onResume() {
        super.onResume()
        browserScroller?.postDelayed({ setFoomiiboVisibility() }, TagMo.uiDelay.toLong())
    }

    private suspend fun deleteDir(dialog: ProgressAlert?, dir: File?) {
        if (!directory.exists()) return
        withContext(Dispatchers.IO) {
            dir?.listFiles().also { files ->
                if (!files.isNullOrEmpty()) {
                    files.forEach {
                        if (it.isDirectory) {
                            withContext(Dispatchers.Main) {
                                dialog?.setMessage(getString(R.string.foomiibo_removing, it.name))
                            }
                            deleteDir(dialog, it)
                        } else {
                            it.delete()
                        }
                    }
                }
            }
            dir?.delete()
        }
    }

    fun deleteFoomiiboFile(tagData: ByteArray?) {
        try {
            val amiibo = settings.amiiboManager?.amiibos?.get(Amiibo.dataToId(tagData))
                ?: throw Exception()
            val directory = amiibo.amiiboSeries?.let { File(directory, it.name) } ?: directory
            val amiiboFile = File(
                directory, TagArray.decipherFilename(amiibo, tagData, false)
            )
            AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.warn_delete_file, amiiboFile.name))
                .setPositiveButton(R.string.delete) { dialog: DialogInterface, _: Int ->
                    if (amiiboFile.delete()) {
                        IconifiedSnackbar(requireActivity(), browserScroller).buildSnackbar(
                            getString(R.string.delete_foomiibo, amiibo.name),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    } else {
                        Toasty(requireContext()).Short(R.string.delete_virtual)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .show()
        } catch (e: Exception) { Toasty(requireContext()).Short(R.string.delete_virtual) }
    }

    fun clearFoomiiboSet(activity: AppCompatActivity) {
        val dialog = ProgressAlert.show(activity, "")
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            deleteDir(dialog, directory)
            withContext(Dispatchers.Main) {
                dialog.dismiss()
                if (activity is BrowserActivity) activity.onRootFolderChanged(false)
            }
        }
    }

    private fun buildFoomiiboFile(amiibo: Amiibo) {
        try {
            val tagData = foomiibo.getSignedData(Amiibo.idToHex(amiibo.id))
            val directory = File(directory, amiibo.amiiboSeries!!.name)
            directory.mkdirs()
            TagArray.writeBytesToFile(
                directory, TagArray.decipherFilename(amiibo, tagData, false), tagData
            )
        } catch (e: Exception) { Debug.warn(e) }
    }

    fun buildFoomiiboFile(tagData: ByteArray) {
        try {
            val amiibo = settings.amiiboManager?.amiibos?.get(Amiibo.dataToId(tagData)) ?: return
            val directory = amiibo.amiiboSeries?.let { File(directory, it.name) } ?: directory
            directory.mkdirs()
            val foomiiboData = foomiibo.getSignedData(tagData)
            TagArray.writeBytesToFile(
                directory, TagArray.decipherFilename(
                    amiibo, foomiiboData, false
                ), foomiiboData
            )
            IconifiedSnackbar(requireActivity(), browserScroller).buildSnackbar(
                getString(R.string.wrote_foomiibo, amiibo.name), Snackbar.LENGTH_SHORT
            ).show()
        } catch (e: Exception) { Debug.warn(e) }
    }

    fun buildFoomiiboSet(activity: AppCompatActivity) {
        try {
            settings.amiiboManager?.let { amiiboManager ->
                val dialog = ProgressAlert.show(activity, "")
                CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
                    deleteDir(null, directory)
                    directory.mkdirs()
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
        CoroutineScope(Dispatchers.Default).launch {
            val usage: String? = try {
                val amiiboId = Amiibo.dataToId(tagData)
                val gamesManager = getGamesManager(requireContext())
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

    override fun onFoomiiboClicked(itemView: View?, amiibo: Amiibo?) {
        var tagData = byteArrayOf()
        run breaking@{
            resultData.forEach { data ->
                try {
                    if (data.isNotEmpty() && Amiibo.dataToId(data) == amiibo?.id) {
                        tagData = data
                        return@breaking
                    }
                } catch (ignored: Exception) { }
            }
        }
        if (tagData.isEmpty() && null != amiibo)
            tagData = foomiibo.generateData(Amiibo.idToHex(amiibo.id))
        try {
            tagData = TagArray.getValidatedData(keyManager, tagData)!!
        } catch (ignored: Exception) { }
        val activity = requireActivity() as BrowserActivity
        val menuOptions = itemView?.findViewById<LinearLayout>(R.id.menu_options)
        menuOptions?.let {
            val toolbar = it.findViewById<Toolbar>(R.id.toolbar)
            if (settings.amiiboView != BrowserSettings.VIEW.IMAGE.value) {
                if (!it.isVisible)
                    activity.onCreateToolbarMenu(this, toolbar, tagData, itemView)
                it.isGone = it.isVisible
                val txtUsage = itemView.findViewById<TextView>(R.id.txtUsage)
                if (!txtUsage.isVisible) getGameCompatibility(txtUsage, tagData)
                txtUsage.isGone = txtUsage.isVisible
            } else {
                activity.onCreateToolbarMenu(this, toolbar, tagData, itemView)
                activity.updateAmiiboView(tagData, null)
            }
        }
    }

    override fun onFoomiiboRebind(itemView: View?, amiibo: Amiibo?) {
        var tagData = byteArrayOf()
        run breaking@{
            resultData.forEach { data ->
                try {
                    if (data.isNotEmpty() && Amiibo.dataToId(data) == amiibo?.id) {
                        tagData = data
                        return@breaking
                    }
                } catch (ignored: Exception) { }
            }
        }
        if (tagData.isEmpty() && null != amiibo)
            tagData = foomiibo.generateData(Amiibo.idToHex(amiibo.id))
        try {
            tagData = TagArray.getValidatedData(keyManager, tagData)!!
        } catch (ignored: Exception) { }
        if (settings.amiiboView != BrowserSettings.VIEW.IMAGE.value) {
            val activity = requireActivity() as BrowserActivity
            itemView?.let { view ->
                view.findViewById<View>(R.id.menu_options).findViewById<Toolbar>(R.id.toolbar).also {
                    activity.onCreateToolbarMenu(this, it, tagData, view)
                }
                getGameCompatibility(view.findViewById(R.id.txtUsage), tagData)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (null == view) return
        browserScroller?.postDelayed({ setFoomiiboVisibility() }, TagMo.uiDelay.toLong())
    }
}