package com.hiddenramblings.tagmo.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
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
import androidx.appcompat.widget.Toolbar
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
import com.hiddenramblings.tagmo.browser.BrowserSettings.BrowserSettingsListener
import com.hiddenramblings.tagmo.browser.adapter.BrowserAdapter
import com.hiddenramblings.tagmo.browser.adapter.FoomiiboAdapter
import com.hiddenramblings.tagmo.browser.adapter.FoomiiboAdapter.OnFoomiiboClickListener
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.nfctech.Foomiibo
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.hiddenramblings.tagmo.widget.Toasty
import com.robertlevonyan.views.chip.Chip
import com.robertlevonyan.views.chip.OnCloseClickListener
import myinnos.indexfastscrollrecycler.IndexFastScrollRecyclerView
import java.io.File
import java.util.concurrent.Executors

class BrowserFragment : Fragment(), OnFoomiiboClickListener {
    private lateinit var prefs: Preferences
    private var chipList: FlexboxLayout? = null
    var browserContent: RecyclerView? = null
        private set
    var foomiiboView: RecyclerView? = null
        private set
    private lateinit var directory: File
    private lateinit var keyManager: KeyManager
    private val foomiibo = Foomiibo()
    private lateinit var settings: BrowserSettings
    private val resultData = ArrayList<ByteArray>()
    val onUpdateTagResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != Activity.RESULT_OK || null == result.data)
            return@registerForActivityResult
        if (NFCIntent.ACTION_NFC_SCANNED != result.data!!.action
            && NFCIntent.ACTION_UPDATE_TAG != result.data!!.action
            && NFCIntent.ACTION_EDIT_COMPLETE != result.data!!.action
        ) return@registerForActivityResult
        val tagData = result.data?.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA)
        if (null != tagData && tagData.isNotEmpty()) {
            var updated = false
            for (data in resultData) {
                try {
                    if (data.isNotEmpty() && Amiibo.dataToId(data) == Amiibo.dataToId(tagData)) {
                        updated = true
                        resultData[resultData.indexOf(data)] = tagData
                        break
                    }
                } catch (ignored: Exception) {
                }
            }
            if (!updated) resultData.add(tagData)
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
        prefs = Preferences(activity.applicationContext)
        keyManager = KeyManager(activity)
        settings = activity.settings ?: BrowserSettings().initialize()
        directory = File(requireActivity().filesDir, "Foomiibo")
        directory.mkdirs()
        chipList = view.findViewById(R.id.chip_list)
        chipList?.visibility = View.GONE
        browserContent = view.findViewById(R.id.browser_content)
        if (browserContent is IndexFastScrollRecyclerView)
                (browserContent as IndexFastScrollRecyclerView)
                    .setTransientIndexBar(!BuildConfig.WEAR_OS)
        if (prefs.softwareLayer())
            browserContent?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        foomiiboView = view.findViewById(R.id.foomiibo_list)
        if (foomiiboView is IndexFastScrollRecyclerView)
                (foomiiboView as IndexFastScrollRecyclerView)
                    .setTransientIndexBar(!BuildConfig.WEAR_OS)
        if (prefs.softwareLayer())
            foomiiboView?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        browserContent?.layoutManager = if (settings.amiiboView == BrowserSettings.VIEW.IMAGE.value)
            GridLayoutManager(activity, activity.columnCount)
        else LinearLayoutManager(activity)
        browserContent?.adapter = BrowserAdapter(settings, activity)
        settings.addChangeListener(browserContent?.adapter as BrowserSettingsListener?)
        foomiiboView?.layoutManager = if (settings.amiiboView == BrowserSettings.VIEW.IMAGE.value)
            GridLayoutManager(activity, activity.columnCount)
        else LinearLayoutManager(activity)
        foomiiboView?.adapter = FoomiiboAdapter(settings, this)
        settings.addChangeListener(foomiiboView?.adapter as BrowserSettingsListener?)
        view.findViewById<View>(R.id.list_divider).visibility = View.GONE
        if (BuildConfig.WEAR_OS && null != browserContent?.layoutParams)
            browserContent!!.layoutParams.height = browserContent!!.layoutParams.height / 3
        view.findViewById<View>(R.id.list_divider)
            .setOnTouchListener { v: View, event: MotionEvent ->
                if (browserContent is IndexFastScrollRecyclerView) {
                    (browserContent as IndexFastScrollRecyclerView).setIndexBarVisibility(false)
                }
                if (foomiiboView is IndexFastScrollRecyclerView) {
                    (foomiiboView as IndexFastScrollRecyclerView).setIndexBarVisibility(false)
                }
                val srcHeight = browserContent!!.layoutParams.height
                val y = event.y.toInt()
                if (browserContent!!.layoutParams.height + y >= -0.5f) {
                    if (event.action == MotionEvent.ACTION_MOVE) {
                        browserContent!!.layoutParams.height += y
                        if (srcHeight != browserContent!!.layoutParams.height)
                            browserContent!!.requestLayout()
                    } else if (event.action == MotionEvent.ACTION_UP) {
                        if (browserContent!!.layoutParams.height + y < 0f) {
                            browserContent!!.layoutParams.height = 0
                        } else {
                            val minHeight = activity.bottomSheetBehavior!!.peekHeight + v.height + requireContext().resources.getDimension(R.dimen.sliding_bar_margin)
                            if (browserContent!!.layoutParams.height > view.height - minHeight.toInt())
                                browserContent!!.layoutParams.height = view.height - minHeight.toInt()
                        }
                        if (srcHeight != browserContent!!.layoutParams.height)
                            browserContent!!.requestLayout()
                    }
                    prefs.foomiiboOffset(browserContent!!.layoutParams.height)
                }
                true
            }
        activity.onFilterContentsLoaded()
    }

    @SuppressLint("InflateParams")
    fun addFilterItemView(text: String?, tag: String?, listener: OnCloseClickListener?) {
        if (null == chipList) return
        var chipContainer = chipList?.findViewWithTag<FrameLayout>(tag)
        if (null != chipContainer) chipList!!.removeView(chipContainer)
        if (!TextUtils.isEmpty(text)) {
            chipContainer = layoutInflater.inflate(R.layout.chip_view, null) as FrameLayout
            chipContainer.tag = tag
            val chip = chipContainer.findViewById<Chip>(R.id.chip)
            chip.setText(text)
            chip.closable = true
            chip.onCloseClickListener = listener
            chipList!!.addView(chipContainer)
            chipList!!.visibility = View.VISIBLE
        } else if (chipList!!.childCount == 0) {
            chipList!!.visibility = View.GONE
        }
    }

    fun setFoomiiboVisibility() {
        if (null == view) return
        val activity = requireActivity() as BrowserActivity
        val divider = requireView().findViewById<View>(R.id.list_divider)
        val minHeight = (activity.bottomSheetBehavior!!.peekHeight + divider.height + requireContext()
            .resources.getDimension(R.dimen.sliding_bar_margin))
        if (browserContent!!.layoutParams.height > requireView().height - minHeight.toInt()) {
            browserContent!!.layoutParams.height = requireView().height - minHeight.toInt()
        } else {
            val valueY = prefs.foomiiboOffset()
            browserContent!!.layoutParams.height =
                if (valueY != -1) valueY else browserContent!!.layoutParams.height
        }
        if (prefs.foomiiboDisabled()) {
            divider.visibility = View.GONE
            browserContent!!.layoutParams.height = requireView().height
        } else {
            divider.visibility = View.VISIBLE
        }
        browserContent!!.requestLayout()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as BrowserActivity).onRootFolderChanged(false)
        browserContent!!.postDelayed({ setFoomiiboVisibility() }, TagMo.uiDelay.toLong())
    }

    private fun deleteDir(handler: Handler?, dialog: ProgressDialog?, dir: File?) {
        if (!directory.exists()) return
        val files = dir?.listFiles()
        if (null != files && files.isNotEmpty()) {
            files.forEach {
                if (it.isDirectory) {
                    handler?.post {
                        dialog!!.setMessage(getString(R.string.foomiibo_removing, it.name))
                    }
                    deleteDir(handler, dialog, it)
                } else {
                    it.delete()
                }
            }
        }
        dir?.delete()
    }

    fun deleteFoomiiboFile(tagData: ByteArray?) {
        try {
            val amiibo = settings.amiiboManager!!.amiibos[Amiibo.dataToId(tagData)]
                ?: throw Exception()
            val directory = File(directory, amiibo.amiiboSeries!!.name)
            val amiiboFile = File(
                directory, TagArray.decipherFilename(amiibo, tagData, false)
            )
            AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.warn_delete_file, amiiboFile.name))
                .setPositiveButton(R.string.delete) { dialog: DialogInterface, _: Int ->
                    if (amiiboFile.delete()) {
                        IconifiedSnackbar(requireActivity(), browserContent).buildSnackbar(
                            getString(R.string.delete_foomiibo, amiibo.name),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    } else {
                        Toasty(requireActivity()).Short(R.string.delete_virtual)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .show()
        } catch (e: Exception) {
            Toasty(requireActivity()).Short(R.string.delete_virtual)
        }
    }

    fun clearFoomiiboSet(handler: Handler) {
        val dialog = ProgressDialog.show(
            requireActivity(), "", "", true
        )
        Executors.newSingleThreadExecutor().execute {
            deleteDir(handler, dialog, directory)
            handler.post {
                dialog.dismiss()
                (requireActivity() as BrowserActivity).onRefresh(false)
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
        } catch (e: Exception) {
            Debug.warn(e)
        }
    }

    fun buildFoomiiboFile(tagData: ByteArray?) {
        try {
            val amiibo = settings.amiiboManager!!.amiibos[Amiibo.dataToId(tagData)] ?: return
            val directory = File(directory, amiibo.amiiboSeries!!.name)
            directory.mkdirs()
            val foomiiboData = foomiibo.getSignedData(tagData!!)
            TagArray.writeBytesToFile(
                directory, TagArray.decipherFilename(
                    amiibo, foomiiboData, false
                ), foomiiboData
            )
            IconifiedSnackbar(requireActivity(), browserContent).buildSnackbar(
                getString(R.string.wrote_foomiibo, amiibo.name), Snackbar.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Debug.warn(e)
        }
    }

    fun buildFoomiiboSet(handler: Handler) {
        val amiiboManager = if (null != settings.amiiboManager)
            settings.amiiboManager
        else null
        if (null == amiiboManager) {
            Toasty(requireActivity()).Short(R.string.amiibo_failure_read)
            return
        }
        val dialog = ProgressDialog.show(
            requireActivity(), "", "", true
        )
        Executors.newSingleThreadExecutor().execute {
            deleteDir(null, null, directory)
            directory.mkdirs()
            amiiboManager.amiibos.values.forEach {
                buildFoomiiboFile(it)
                handler.post {
                    dialog.setMessage(getString(R.string.foomiibo_progress, it.character!!.name))
                }
            }
            handler.post {
                dialog.dismiss()
                (requireActivity() as BrowserActivity).onRefresh(false)
            }
        }
    }

    private fun getGameCompatibility(txtUsage: TextView, tagData: ByteArray) {
        Executors.newSingleThreadExecutor().execute {
            try {
                val amiiboId = Amiibo.dataToId(tagData)
                val gamesManager = getGamesManager(requireContext())
                val usage = gamesManager.getGamesCompatibility(amiiboId)
                txtUsage.post { txtUsage.text = usage }
            } catch (ex: Exception) {
                Debug.warn(ex)
            }
        }
    }

    override fun onFoomiiboClicked(itemView: View?, amiibo: Amiibo?) {
        var tagData = ByteArray(0)
        for (data in resultData) {
            try {
                if (data.isNotEmpty() && Amiibo.dataToId(data) == amiibo!!.id) {
                    tagData = data
                    break
                }
            } catch (ignored: Exception) {
            }
        }
        if (tagData.isEmpty())
            tagData = foomiibo.generateData(Amiibo.idToHex(amiibo!!.id))
        try {
            tagData = TagArray.getValidatedData(keyManager, tagData)!!
        } catch (ignored: Exception) { }
        val activity = requireActivity() as BrowserActivity
        val menuOptions = itemView?.findViewById<LinearLayout>(R.id.menu_options)
        if (null != menuOptions) {
            val toolbar = menuOptions.findViewById<Toolbar>(R.id.toolbar)
            if (settings.amiiboView != BrowserSettings.VIEW.IMAGE.value) {
                if (menuOptions.visibility == View.VISIBLE) {
                    menuOptions.visibility = View.GONE
                } else {
                    menuOptions.visibility = View.VISIBLE
                    activity.onCreateToolbarMenu(this, toolbar, tagData, itemView)
                }
                val txtUsage = itemView.findViewById<TextView>(R.id.txtUsage)
                if (txtUsage.visibility == View.VISIBLE) {
                    txtUsage.visibility = View.GONE
                } else {
                    txtUsage.visibility = View.VISIBLE
                    getGameCompatibility(txtUsage, tagData)
                }
            } else {
                activity.onCreateToolbarMenu(this, toolbar, tagData, itemView)
                activity.updateAmiiboView(tagData, null)
            }
        }
    }

    override fun onFoomiiboRebind(itemView: View?, amiibo: Amiibo?) {
        var tagData = ByteArray(0)
        for (data in resultData) {
            try {
                if (data.isNotEmpty() && Amiibo.dataToId(data) == amiibo!!.id) {
                    tagData = data
                    break
                }
            } catch (ignored: Exception) {
            }
        }
        if (tagData.isEmpty()) tagData = foomiibo.generateData(Amiibo.idToHex(amiibo!!.id))
        try {
            tagData = TagArray.getValidatedData(keyManager, tagData)!!
        } catch (ignored: Exception) { }
        if (settings.amiiboView != BrowserSettings.VIEW.IMAGE.value) {
            val activity = requireActivity() as BrowserActivity
            val toolbar =
                itemView!!.findViewById<View>(R.id.menu_options).findViewById<Toolbar>(R.id.toolbar)
            activity.onCreateToolbarMenu(this, toolbar, tagData, itemView)
            getGameCompatibility(itemView.findViewById(R.id.txtUsage), tagData)
        }
    }

    override fun onFoomiiboImageClicked(amiibo: Amiibo?) {
        if (null == amiibo) return
        val bundle = Bundle()
        bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiibo.id)
        val intent = Intent(requireContext(), ImageActivity::class.java)
        intent.putExtras(bundle)
        this.startActivity(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (null == view) return
        browserContent!!.postDelayed({ setFoomiiboVisibility() }, TagMo.uiDelay.toLong())
    }
}