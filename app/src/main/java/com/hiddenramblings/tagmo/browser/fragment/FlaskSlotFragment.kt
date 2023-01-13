package com.hiddenramblings.tagmo.browser.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.SearchManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.content.*
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.*
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.snackbar.Snackbar
import com.hiddenramblings.tagmo.*
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboFile
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.Companion.getAmiiboManager
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.Companion.hasSpoofData
import com.hiddenramblings.tagmo.amiibo.FlaskTag
import com.hiddenramblings.tagmo.bluetooth.BluetoothHandler
import com.hiddenramblings.tagmo.bluetooth.BluetoothHandler.BluetoothListener
import com.hiddenramblings.tagmo.bluetooth.FlaskGattService
import com.hiddenramblings.tagmo.bluetooth.PuckGattService
import com.hiddenramblings.tagmo.browser.BrowserActivity
import com.hiddenramblings.tagmo.browser.BrowserSettings
import com.hiddenramblings.tagmo.browser.ImageActivity
import com.hiddenramblings.tagmo.browser.adapter.FlaskSlotAdapter
import com.hiddenramblings.tagmo.browser.adapter.WriteTagAdapter
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.hiddenramblings.tagmo.widget.Toasty
import com.shawnlin.numberpicker.NumberPicker
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.text.ParseException
import java.util.concurrent.Executors

@SuppressLint("NewApi")
open class FlaskSlotFragment : Fragment(), FlaskSlotAdapter.OnAmiiboClickListener, BluetoothListener {
    private lateinit var prefs: Preferences
    private var bluetoothHandler: BluetoothHandler? = null
    private var isFragmentVisible = false
    private lateinit var rootLayout: CoordinatorLayout
    private var amiiboTile: CardView? = null
    private var amiiboCard: CardView? = null
    private var toolbar: Toolbar? = null
    private lateinit var amiiboTileTarget: CustomTarget<Bitmap?>
    private lateinit var amiiboCardTarget: CustomTarget<Bitmap?>
    var flaskContent: RecyclerView? = null
        private set
    private var flaskStats: TextView? = null
    private lateinit var flaskSlotCount: NumberPicker
    private var screenOptions: LinearLayout? = null
    private var writeSlots: AppCompatButton? = null
    private var eraseSlots: AppCompatButton? = null
    private var slotOptionsMenu: LinearLayout? = null
    private var createBlank: AppCompatButton? = null
    private var switchMenuOptions: AppCompatToggleButton? = null
    private var writeSlotsLayout: LinearLayout? = null
    private var writeTagAdapter: WriteTagAdapter? = null
    private var statusBar: Snackbar? = null
    private var processDialog: Dialog? = null
    private lateinit var settings: BrowserSettings
    var bottomSheet: BottomSheetBehavior<View>? = null
        private set
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var scanCallbackFlaskLP: ScanCallback? = null
    private var scanCallbackFlask: LeScanCallback? = null
    private var serviceFlask: FlaskGattService? = null
    private var scanCallbackPuckLP: ScanCallback? = null
    private var scanCallbackPuck: LeScanCallback? = null
    private var servicePuck: PuckGattService? = null
    private var deviceProfile: String? = null
    private var deviceAddress: String? = null
    private var maxSlotCount = 85
    private var currentCount = 0
    private var deviceDialog: AlertDialog? = null

    private enum class STATE {
        NONE, SCANNING, CONNECT, MISSING, PURCHASE
    }

    private var noticeState = STATE.NONE

    private enum class SHEET {
        LOCKED, AMIIBO, MENU, WRITE
    }

    private val fragmentHandler = Handler(Looper.getMainLooper())
    private var flaskServerConn: ServiceConnection = object : ServiceConnection {
        var isServiceDiscovered = false
        override fun onServiceConnected(component: ComponentName, binder: IBinder) {
            val localBinder = binder as FlaskGattService.LocalBinder
            serviceFlask = localBinder.service
            if (serviceFlask!!.initialize()) {
                if (serviceFlask!!.connect(deviceAddress)) {
                    serviceFlask!!.setListener(object : FlaskGattService.BluetoothGattListener {
                        override fun onServicesDiscovered() {
                            isServiceDiscovered = true
                            onBottomSheetChanged(SHEET.MENU)
                            maxSlotCount = 85
                            rootLayout.post {
                                flaskSlotCount.maxValue = maxSlotCount
                                screenOptions?.isVisible = true
                                createBlank?.isVisible = true
                                (rootLayout.findViewById<View>(R.id.hardware_info) as TextView).text =
                                    deviceProfile
                            }
                            try {
                                serviceFlask!!.setFlaskCharacteristicRX()
                                serviceFlask!!.deviceAmiibo
                            } catch (uoe: UnsupportedOperationException) {
                                disconnectService()
                                Toasty(requireActivity()).Short(R.string.device_invalid)
                            }
                        }

                        override fun onFlaskStatusChanged(jsonObject: JSONObject?) {
                            if (null != processDialog && processDialog!!.isShowing)
                                processDialog!!.dismiss()
                            serviceFlask!!.deviceAmiibo
                        }

                        override fun onFlaskListRetrieved(jsonArray: JSONArray) {
                            Executors.newSingleThreadExecutor().execute {
                                currentCount = jsonArray.length()
                                val flaskAmiibos = ArrayList<Amiibo?>()
                                for (i in 0 until currentCount) {
                                    try {
                                        val amiibo = getAmiiboFromTail(
                                            jsonArray.getString(i).split("|")
                                        )
                                        flaskAmiibos.add(amiibo)
                                    } catch (ex: JSONException) {
                                        Debug.warn(ex)
                                    } catch (ex: NullPointerException) {
                                        Debug.warn(ex)
                                    }
                                }
                                val adapter = FlaskSlotAdapter(
                                    settings, this@FlaskSlotFragment
                                )
                                adapter.setFlaskAmiibo(flaskAmiibos)
                                flaskContent?.post {
                                    dismissSnackbarNotice(true)
                                    flaskContent?.adapter = adapter
                                    if (currentCount > 0) {
                                        serviceFlask?.activeAmiibo
                                        adapter.notifyItemRangeInserted(0, currentCount)
                                    } else {
                                        amiiboTile?.visibility = View.INVISIBLE
                                        flaskButtonState
                                    }
                                }
                            }
                        }

                        override fun onFlaskRangeRetrieved(jsonArray: JSONArray) {
                            Executors.newSingleThreadExecutor().execute {
                                val flaskAmiibos = ArrayList<Amiibo?>()
                                for (i in 0 until jsonArray.length()) {
                                    try {
                                        val amiibo = getAmiiboFromTail(
                                            jsonArray.getString(i).split("|")
                                        )
                                        flaskAmiibos.add(amiibo)
                                    } catch (ex: JSONException) {
                                        Debug.warn(ex)
                                    } catch (ex: NullPointerException) {
                                        Debug.warn(ex)
                                    }
                                }
                                val adapter = flaskContent?.adapter as FlaskSlotAdapter?
                                if (null != adapter) {
                                    adapter.addFlaskAmiibo(flaskAmiibos)
                                    flaskContent?.post {
                                        adapter.notifyItemRangeInserted(
                                            currentCount, flaskAmiibos.size
                                        )
                                        currentCount = adapter.itemCount
                                    }
                                }
                            }
                        }

                        override fun onFlaskActiveChanged(jsonObject: JSONObject?) {
                            if (null == jsonObject) return
                            try {
                                val name = jsonObject.getString("name")
                                if ("undefined" == name) {
                                    resetActiveSlot()
                                    return
                                }
                                val amiibo = getAmiiboFromTail(name.split("|"))
                                val index = jsonObject.getString("index")
                                getActiveAmiibo(amiibo, amiiboTile)
                                if (bottomSheet?.state ==
                                    BottomSheetBehavior.STATE_COLLAPSED
                                ) getActiveAmiibo(amiibo, amiiboCard)
                                prefs.flaskActiveSlot(index.toInt())
                                flaskContent?.post {
                                    flaskStats?.text =
                                        getString(R.string.flask_count, index, currentCount)
                                }
                                flaskButtonState
                            } catch (ex: JSONException) {
                                Debug.warn(ex)
                            } catch (ex: NullPointerException) {
                                Debug.warn(ex)
                            }
                        }

                        override fun onFlaskFilesDownload(dataString: String) {
                            try {
                                val tagData = dataString.toByteArray()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        override fun onFlaskProcessFinish() {
                            requireActivity().runOnUiThread {
                                if (null != processDialog && processDialog!!.isShowing)
                                    processDialog!!.dismiss()
                            }
                        }

                        override fun onGattConnectionLost() {
                            fragmentHandler.postDelayed(
                                { showDisconnectNotice() }, TagMo.uiDelay.toLong()
                            )
                            requireActivity().runOnUiThread {
                                bottomSheet?.setState(BottomSheetBehavior.STATE_COLLAPSED)
                            }
                            stopGattService()
                        }
                    })
                } else {
                    stopGattService()
                    Toasty(requireActivity()).Short(R.string.device_invalid)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            stopGattService()
            if (!isServiceDiscovered) {
                showPurchaseNotice()
            }
        }
    }
    private var puckServerConn: ServiceConnection = object : ServiceConnection {
        var isServiceDiscovered = false
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val localBinder = binder as PuckGattService.LocalBinder
            servicePuck = localBinder.service
            if (servicePuck!!.initialize()) {
                if (servicePuck!!.connect(deviceAddress)) {
                    servicePuck!!.setListener(object : PuckGattService.BluetoothGattListener {
                        override fun onServicesDiscovered() {
                            isServiceDiscovered = true
                            onBottomSheetChanged(SHEET.MENU)
                            maxSlotCount = 32
                            rootLayout.post {
                                flaskSlotCount.maxValue = maxSlotCount
                                screenOptions?.isGone = true
                                createBlank?.isGone = true
                                (rootLayout.findViewById<View>(R.id.hardware_info) as TextView)
                                    .text = deviceProfile
                                flaskSlotCount.maxValue = maxSlotCount
                            }
                            try {
                                servicePuck!!.setPuckCharacteristicRX()
                                servicePuck!!.deviceAmiibo
                                // servicePuck!!.getDeviceSlots(32);
                            } catch (uoe: UnsupportedOperationException) {
                                disconnectService()
                                Toasty(requireActivity()).Short(R.string.device_invalid)
                            }
                        }

                        override fun onPuckActiveChanged(slot: Int) {
                            val adapter = flaskContent?.adapter as FlaskSlotAdapter?
                            if (null != adapter) {
                                val amiibo = adapter.getItem(slot)
                                getActiveAmiibo(amiibo, amiiboTile)
                                if (bottomSheet?.state == BottomSheetBehavior.STATE_COLLAPSED)
                                    getActiveAmiibo(amiibo, amiiboCard)
                                prefs.flaskActiveSlot(slot)
                                flaskButtonState
                                flaskContent?.post {
                                    flaskStats?.text = getString(
                                        R.string.flask_count, slot.toString(), currentCount
                                    )
                                }
                            }
                        }

                        override fun onPuckListRetrieved(
                            slotData: ArrayList<ByteArray?>, active: Int
                        ) {
                            Executors.newSingleThreadExecutor().execute {
                                currentCount = slotData.size
                                val flaskAmiibos = ArrayList<Amiibo?>()
                                for (i in 0 until currentCount) {
                                    try {
                                        val amiibo = getAmiiboFromHead(slotData[i])
                                        flaskAmiibos.add(amiibo)
                                    } catch (ex: NullPointerException) {
                                        Debug.warn(ex)
                                        flaskAmiibos.add(null)
                                    }
                                }
                                val adapter = FlaskSlotAdapter(
                                    settings, this@FlaskSlotFragment
                                )
                                adapter.setFlaskAmiibo(flaskAmiibos)
                                flaskContent?.post {
                                    dismissSnackbarNotice(true)
                                    flaskContent?.adapter = adapter
                                    if (currentCount > 0) {
                                        adapter.notifyItemRangeInserted(0, currentCount)
                                        onPuckActiveChanged(active)
                                    } else {
                                        amiiboTile?.visibility = View.INVISIBLE
                                        flaskButtonState
                                    }
                                }
                            }
                        }

                        override fun onPuckFilesDownload(tagData: ByteArray) {}
                        override fun onPuckProcessFinish() {
                            requireActivity().runOnUiThread {
                                if (null != processDialog && processDialog!!.isShowing)
                                    processDialog!!.dismiss()
                            }
                        }

                        override fun onGattConnectionLost() {
                            fragmentHandler.postDelayed(
                                { showDisconnectNotice() }, TagMo.uiDelay.toLong()
                            )
                            requireActivity().runOnUiThread {
                                bottomSheet?.setState(BottomSheetBehavior.STATE_COLLAPSED)
                            }
                            stopGattService()
                        }
                    })
                } else {
                    stopGattService()
                    Toasty(requireActivity()).Short(R.string.device_invalid)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            stopGattService()
            if (!isServiceDiscovered) {
                showPurchaseNotice()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_flask_slot, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) return
        rootLayout = view as CoordinatorLayout
        val activity = requireActivity() as BrowserActivity
        prefs = Preferences(activity.applicationContext)
        amiiboTile = rootLayout.findViewById(R.id.active_tile_layout)
        amiiboCard = rootLayout.findViewById(R.id.active_card_layout)
        amiiboCard?.findViewById<View>(R.id.txtError)?.isGone = true
        amiiboCard?.findViewById<View>(R.id.txtPath)?.isGone = true
        toolbar = rootLayout.findViewById(R.id.toolbar)
        amiiboTileTarget = object : CustomTarget<Bitmap?>() {
            val imageAmiibo = amiiboTile?.findViewById<AppCompatImageView>(R.id.imageAmiibo)
            override fun onLoadFailed(errorDrawable: Drawable?) {
                imageAmiibo?.visibility = View.INVISIBLE
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                imageAmiibo?.setImageResource(0)
            }

            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                imageAmiibo?.maxHeight = Resources.getSystem().displayMetrics.heightPixels / 4
                imageAmiibo?.requestLayout()
                imageAmiibo?.setImageBitmap(resource)
                imageAmiibo?.visibility = View.VISIBLE
            }
        }
        amiiboCardTarget = object : CustomTarget<Bitmap?>() {
            val imageAmiibo = amiiboCard?.findViewById<AppCompatImageView>(R.id.imageAmiibo)
            override fun onLoadFailed(errorDrawable: Drawable?) {
                imageAmiibo?.visibility = View.INVISIBLE
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                imageAmiibo?.setImageResource(0)
            }

            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                imageAmiibo?.maxHeight = Resources.getSystem().displayMetrics.heightPixels / 4
                imageAmiibo?.requestLayout()
                imageAmiibo?.setImageBitmap(resource)
                imageAmiibo?.visibility = View.VISIBLE
            }
        }
        settings = activity.settings ?: BrowserSettings().initialize()
        flaskContent = rootLayout.findViewById(R.id.flask_content)
        if (prefs.softwareLayer())
            flaskContent?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        // flaskContent!!.setHasFixedSize(true);
        if (settings.amiiboView == BrowserSettings.VIEW.IMAGE.value)
            flaskContent?.layoutManager = GridLayoutManager(activity, activity.columnCount)
        else
            flaskContent?.layoutManager = LinearLayoutManager(activity)
        flaskStats = rootLayout.findViewById(R.id.flask_stats)
        switchMenuOptions = rootLayout.findViewById(R.id.switch_menu_btn)
        slotOptionsMenu = rootLayout.findViewById(R.id.slot_options_menu)
        val writeFile = rootLayout.findViewById<AppCompatButton>(R.id.write_slot_file)
        createBlank = rootLayout.findViewById(R.id.create_blank)
        flaskSlotCount = rootLayout.findViewById(R.id.number_picker)
        flaskSlotCount.maxValue = maxSlotCount
        screenOptions = rootLayout.findViewById(R.id.screen_options)
        writeSlots = rootLayout.findViewById(R.id.write_slot_count)
        writeSlots?.text = getString(R.string.write_slots, 1)
        eraseSlots = rootLayout.findViewById(R.id.erase_slot_count)
        eraseSlots?.text = getString(R.string.erase_slots, 0)
        writeSlotsLayout = rootLayout.findViewById(R.id.write_list_layout)
        if (prefs.softwareLayer())
            writeSlotsLayout!!.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        val amiiboFilesView = rootLayout.findViewById<RecyclerView>(R.id.amiibo_files_list)
        if (prefs.softwareLayer())
            amiiboFilesView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        // amiiboFilesView.setHasFixedSize(true);
        val toggle = rootLayout.findViewById<AppCompatImageView>(R.id.toggle)
        bottomSheet = BottomSheetBehavior.from(rootLayout.findViewById(R.id.bottom_sheet))
        setBottomSheetHidden(false)
        bottomSheet?.addBottomSheetCallback(object : BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    if (writeSlotsLayout?.visibility == View.VISIBLE)
                        onBottomSheetChanged(SHEET.MENU)
                    toggle.setImageResource(R.drawable.ic_expand_less_white_24dp)
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    toggle.setImageResource(R.drawable.ic_expand_more_white_24dp)
                }
            }

            override fun onSlide(view: View, slideOffset: Float) {
                val mainLayout = rootLayout.findViewById<ViewGroup>(R.id.flask_content)
                if (mainLayout.bottom >= view.top) {
                    val bottomHeight: Int = (view.measuredHeight - bottomSheet!!.peekHeight)
                    mainLayout.setPadding(
                        0,
                        0,
                        0,
                        if (slideOffset > 0) (bottomHeight * slideOffset).toInt() else 0
                    )
                }
            }
        })
        toggle.setOnClickListener {
            if (bottomSheet?.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheet?.setState(BottomSheetBehavior.STATE_EXPANDED)
            } else {
                bottomSheet?.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
        }
        toolbar?.inflateMenu(R.menu.flask_menu)
        if (settings.amiiboView == BrowserSettings.VIEW.IMAGE.value)
            amiiboFilesView.layoutManager = GridLayoutManager(activity, activity.columnCount)
        else amiiboFilesView.layoutManager = LinearLayoutManager(activity)
        writeTagAdapter = WriteTagAdapter(settings)
        amiiboFilesView.adapter = writeTagAdapter
        settings.addChangeListener(writeTagAdapter)
        view.findViewById<View>(R.id.switch_devices).setOnClickListener {
            bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
            disconnectService()
            if (isBluetoothEnabled) selectBluetoothDevice()
        }
        switchMenuOptions?.setOnClickListener {
            if (slotOptionsMenu?.isShown == true) {
                onBottomSheetChanged(SHEET.AMIIBO)
            } else {
                onBottomSheetChanged(SHEET.MENU)
            }
            bottomSheet?.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
        val searchView = rootLayout.findViewById<SearchView>(R.id.amiibo_search)
        if (BuildConfig.WEAR_OS) {
            searchView.isGone = true
        } else {
            val searchManager = activity
                .getSystemService(Context.SEARCH_SERVICE) as SearchManager
            searchView.setSearchableInfo(
                searchManager
                    .getSearchableInfo(activity.componentName)
            )
            searchView.isSubmitButtonEnabled = false
            searchView.setIconifiedByDefault(false)
            val searchBar = searchView.findViewById<LinearLayout>(R.id.search_bar)
            searchBar.layoutParams.height = resources
                .getDimension(R.dimen.button_height_min).toInt()
            searchBar.gravity = Gravity.CENTER_VERTICAL
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    settings.query = query
                    settings.notifyChanges()
                    return false
                }

                override fun onQueryTextChange(query: String): Boolean {
                    settings.query = query
                    settings.notifyChanges()
                    return true
                }
            })
        }
        writeFile.setOnClickListener {
            onBottomSheetChanged(SHEET.WRITE)
            searchView.setQuery(settings.query, true)
            searchView.clearFocus()
            writeTagAdapter!!.setListener(object : WriteTagAdapter.OnAmiiboClickListener {
                override fun onAmiiboClicked(amiiboFile: AmiiboFile?) {
                    onBottomSheetChanged(SHEET.AMIIBO)
                    showProcessingNotice(true)
                    uploadAmiiboFile(amiiboFile)
                }

                override fun onAmiiboImageClicked(amiiboFile: AmiiboFile?) {
                    handleImageClicked(amiiboFile)
                }

                override fun onAmiiboListClicked(amiiboList: ArrayList<AmiiboFile?>?) {}
            }, 1)
            bottomSheet?.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
        flaskSlotCount.setOnValueChangedListener { _: NumberPicker?, _: Int, valueNew: Int ->
            if (maxSlotCount - currentCount > 0) writeSlots!!.text =
                getString(R.string.write_slots, valueNew)
        }
        writeSlots?.setOnClickListener {
            onBottomSheetChanged(SHEET.WRITE)
            searchView.setQuery(settings.query, true)
            searchView.clearFocus()
            flaskSlotCount.value.let { count ->
                writeTagAdapter?.setListener(object : WriteTagAdapter.OnAmiiboClickListener {
                    override fun onAmiiboClicked(amiiboFile: AmiiboFile?) {}
                    override fun onAmiiboImageClicked(amiiboFile: AmiiboFile?) {}
                    override fun onAmiiboListClicked(amiiboList: ArrayList<AmiiboFile?>?) {
                        if (!amiiboList.isNullOrEmpty()) writeAmiiboCollection(amiiboList)
                    }
                }, count)
            }
            bottomSheet?.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
        eraseSlots?.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.flask_erase_confirm)
                .setPositiveButton(R.string.proceed) { _: DialogInterface?, _: Int ->
                    showProcessingNotice(false)
                    serviceFlask?.clearStorage(currentCount)
                }
                .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .show()
        }
        createBlank?.setOnClickListener { serviceFlask!!.createBlankTag() }
        rootLayout.findViewById<View>(R.id.screen_layered)
            .setOnClickListener { serviceFlask?.setFlaskFace(false) }
        rootLayout.findViewById<View>(R.id.screen_stacked)
            .setOnClickListener { serviceFlask?.setFlaskFace(true) }
        flaskButtonState
    }

    private fun onBottomSheetChanged(sheet: SHEET) {
        bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
        requireActivity().runOnUiThread {
            when (sheet) {
                SHEET.LOCKED -> {
                    amiiboCard?.isGone = true
                    switchMenuOptions?.isGone = true
                    slotOptionsMenu?.isGone = true
                    writeSlotsLayout?.isGone = true
                }
                SHEET.AMIIBO -> {
                    amiiboCard?.isVisible = true
                    switchMenuOptions?.isVisible = true
                    slotOptionsMenu?.isGone = true
                    writeSlotsLayout?.isGone = true
                }
                SHEET.MENU -> {
                    amiiboCard?.isGone = true
                    switchMenuOptions?.isVisible = true
                    slotOptionsMenu?.isVisible = true
                    writeSlotsLayout?.isGone = true
                }
                SHEET.WRITE -> {
                    amiiboCard?.isGone = true
                    switchMenuOptions?.isGone = true
                    slotOptionsMenu?.isGone = true
                    writeSlotsLayout?.isVisible = true
                }
            }
            flaskContent?.requestLayout()
        }
    }

    fun setAmiiboInfoText(textView: TextView?, text: CharSequence?) {
        textView?.isVisible = true
        if (!text.isNullOrEmpty()) {
            textView?.text = text
            textView?.isEnabled = true
        } else {
            textView?.setText(R.string.unknown)
            textView?.isEnabled = false
        }
    }

    private val flaskButtonState: Unit
        get() {
            flaskContent?.post {
                val openSlots = maxSlotCount - currentCount
                flaskSlotCount.value = openSlots
                if (openSlots > 0) {
                    writeSlots?.isEnabled = true
                    writeSlots?.text = getString(R.string.write_slots, openSlots)
                } else {
                    writeSlots?.isEnabled = false
                    writeSlots?.text = getString(R.string.slots_full)
                }
                if (currentCount > 0) {
                    eraseSlots?.isEnabled = true
                    eraseSlots?.text = getString(R.string.erase_slots, currentCount)
                } else {
                    eraseSlots?.isEnabled = false
                    eraseSlots?.text = getString(R.string.slots_empty)
                }
            }
        }

    private fun resetActiveSlot() {
        val adapter = flaskContent?.adapter as FlaskSlotAdapter?
        if (null != adapter) {
            val amiibo = adapter.getItem(0)
            if (amiibo is FlaskTag) {
                serviceFlask?.setActiveAmiibo(
                    amiibo.name, String(TagArray.longToBytes(amiibo.id))
                )
            } else {
                serviceFlask?.setActiveAmiibo(amiibo!!.name, amiibo.flaskTail)
            }
        }
    }

    private fun getActiveAmiibo(active: Amiibo?, amiiboView: View?) {
        if (null == amiiboView) return
        val txtName = amiiboView.findViewById<TextView>(R.id.txtName)
        val txtTagId = amiiboView.findViewById<TextView>(R.id.txtTagId)
        val txtAmiiboSeries = amiiboView.findViewById<TextView>(R.id.txtAmiiboSeries)
        val txtAmiiboType = amiiboView.findViewById<TextView>(R.id.txtAmiiboType)
        val txtGameSeries = amiiboView.findViewById<TextView>(R.id.txtGameSeries)
        val imageAmiibo = amiiboView.findViewById<AppCompatImageView>(R.id.imageAmiibo)
        val txtUsageLabel = amiiboView.findViewById<TextView>(R.id.txtUsageLabel)
        amiiboView.post {
            val amiiboHexId: String
            val amiiboName: String?
            var amiiboSeries = ""
            var amiiboType = ""
            var gameSeries = ""
            var amiiboImageUrl: String? = null
            if (amiiboView === amiiboTile) amiiboView.isVisible = true
            if (null == active) {
                txtName.setText(R.string.no_tag_loaded)
                txtTagId!!.visibility = View.INVISIBLE
                txtAmiiboSeries.visibility = View.INVISIBLE
                txtAmiiboType.visibility = View.INVISIBLE
                txtGameSeries.visibility = View.INVISIBLE
                if (amiiboView === amiiboCard) txtUsageLabel.visibility = View.INVISIBLE
            } else if (active is FlaskTag) {
                txtName.setText(R.string.blank_tag)
                txtTagId!!.visibility = View.INVISIBLE
                txtAmiiboSeries.visibility = View.INVISIBLE
                txtAmiiboType.visibility = View.INVISIBLE
                txtGameSeries.visibility = View.INVISIBLE
                if (amiiboView === amiiboCard) txtUsageLabel.visibility = View.INVISIBLE
            } else {
                txtTagId!!.visibility = View.VISIBLE
                txtAmiiboSeries.visibility = View.VISIBLE
                txtAmiiboType.visibility = View.VISIBLE
                txtGameSeries.visibility = View.VISIBLE
                if (amiiboView === amiiboCard) txtUsageLabel.visibility = View.VISIBLE
                amiiboHexId = Amiibo.idToHex(active.id)
                amiiboName = active.name
                amiiboImageUrl = active.imageUrl
                if (null != active.amiiboSeries) amiiboSeries = active.amiiboSeries!!.name
                if (null != active.amiiboType) amiiboType = active.amiiboType!!.name
                if (null != active.gameSeries) gameSeries = active.gameSeries!!.name
                setAmiiboInfoText(txtName, amiiboName)
                setAmiiboInfoText(txtTagId, amiiboHexId)
                setAmiiboInfoText(txtAmiiboSeries, amiiboSeries)
                setAmiiboInfoText(txtAmiiboType, amiiboType)
                setAmiiboInfoText(txtGameSeries, gameSeries)
                if (hasSpoofData(amiiboHexId)) txtTagId.isEnabled = false
            }
            if (amiiboView === amiiboTile && null == amiiboImageUrl) {
                imageAmiibo!!.setImageResource(R.mipmap.ic_launcher_round)
                imageAmiibo.visibility = View.VISIBLE
            } else if (amiiboView === amiiboCard && null == amiiboImageUrl) {
                imageAmiibo!!.setImageResource(0)
                imageAmiibo.visibility = View.INVISIBLE
            } else if (null != imageAmiibo) {
                GlideApp.with(imageAmiibo).clear(imageAmiibo)
                if (!amiiboImageUrl.isNullOrEmpty()) {
                    GlideApp.with(imageAmiibo).asBitmap().load(amiiboImageUrl).into(
                        if (amiiboView === amiiboCard) amiiboCardTarget else amiiboTileTarget
                    )
                }
                imageAmiibo.setOnClickListener {
                    val bundle = Bundle()
                    bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, active!!.id)
                    val intent = Intent(requireContext(), ImageActivity::class.java)
                    intent.putExtras(bundle)
                    startActivity(intent)
                }
            }
        }
    }

    private fun getAmiiboFromTail(name: List<String>): Amiibo? {
        if (name.size < 2) return null
        if (name[1].isEmpty()) return FlaskTag(name)
        var amiiboManager: AmiiboManager?
        try {
            amiiboManager = getAmiiboManager(requireContext().applicationContext)
        } catch (e: IOException) {
            Debug.warn(e)
            amiiboManager = null
            Toasty(requireActivity()).Short(R.string.amiibo_info_parse_error)
        } catch (e: JSONException) {
            Debug.warn(e)
            amiiboManager = null
            Toasty(requireActivity()).Short(R.string.amiibo_info_parse_error)
        } catch (e: ParseException) {
            Debug.warn(e)
            amiiboManager = null
            Toasty(requireActivity()).Short(R.string.amiibo_info_parse_error)
        }
        if (Thread.currentThread().isInterrupted) return null
        var selectedAmiibo: Amiibo? = null
        if (null != amiiboManager) {
            for (amiibo in amiiboManager.amiibos.values) {
                val flaskTail = Amiibo.idToHex(amiibo.id).substring(8, 16).toInt(16).toString(36)
                if (name[1] == flaskTail) {
                    selectedAmiibo = amiibo
                    break
                }
            }
        }
        return selectedAmiibo
    }

    private fun getAmiiboFromHead(tagData: ByteArray?): Amiibo? {
        var amiiboManager: AmiiboManager?
        try {
            amiiboManager = getAmiiboManager(requireContext().applicationContext)
        } catch (e: IOException) {
            Debug.warn(e)
            amiiboManager = null
            Toasty(requireActivity()).Short(R.string.amiibo_info_parse_error)
        } catch (e: JSONException) {
            Debug.warn(e)
            amiiboManager = null
            Toasty(requireActivity()).Short(R.string.amiibo_info_parse_error)
        } catch (e: ParseException) {
            Debug.warn(e)
            amiiboManager = null
            Toasty(requireActivity()).Short(R.string.amiibo_info_parse_error)
        }
        if (Thread.currentThread().isInterrupted) return null
        var selectedAmiibo: Amiibo? = null
        if (null != amiiboManager) {
            try {
                val headData = ByteBuffer.wrap(tagData!!)
                val amiiboId = headData.getLong(0x28)
                selectedAmiibo = amiiboManager.amiibos[amiiboId]
            } catch (e: Exception) {
                Debug.info(e)
            }
        }
        return selectedAmiibo
    }

    @SuppressLint("InflateParams")
    private fun displayScanResult(
        deviceDialog: AlertDialog, device: BluetoothDevice, deviceType: Int
    ) : View {
        val item = this.layoutInflater.inflate(R.layout.device_bluetooth, null)
        (item.findViewById<View>(R.id.device_name) as TextView).text = device.name
        (item.findViewById<View>(R.id.device_address) as TextView).text =
            requireActivity().getString(R.string.device_address, device.address)
        item.findViewById<View>(R.id.connect_flask).setOnClickListener {
            deviceDialog.dismiss()
            deviceProfile = device.name
            deviceAddress = device.address
            dismissGattDiscovery()
            showConnectionNotice()
            startFlaskService()
        }
        item.findViewById<View>(R.id.connect_flask).isEnabled = deviceType != 2
        item.findViewById<View>(R.id.connect_puck).setOnClickListener {
            deviceDialog.dismiss()
            deviceProfile = device.name
            deviceAddress = device.address
            dismissGattDiscovery()
            showConnectionNotice()
            startPuckService()
        }
        item.findViewById<View>(R.id.connect_puck).isEnabled = deviceType != 1
        return item
    }

    @SuppressLint("MissingPermission", "NewApi")
    private fun scanBluetoothServices(deviceDialog: AlertDialog) {
        mBluetoothAdapter =
            if (null != mBluetoothAdapter)
                mBluetoothAdapter
            else bluetoothHandler?.getBluetoothAdapter(requireContext())
        if (null == mBluetoothAdapter) {
            setBottomSheetHidden(true)
            Toasty(requireActivity()).Long(R.string.fail_bluetooth_adapter)
            return
        }
        showScanningNotice()
        deviceProfile = null
        val devices = ArrayList<BluetoothDevice>()
        if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) {
            val scanner = mBluetoothAdapter?.bluetoothLeScanner
            val settings = ScanSettings.Builder().setScanMode(
                ScanSettings.SCAN_MODE_LOW_LATENCY
            ).build()
            val filterFlask = ScanFilter.Builder().setServiceUuid(
                ParcelUuid(FlaskGattService.FlaskNUS)
            ).build()
            scanCallbackFlaskLP = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    super.onScanResult(callbackType, result)
                    if (!devices.contains(result.device)) {
                        devices.add(result.device)
                        deviceDialog.findViewById<LinearLayout>(R.id.bluetooth_result)?.addView(
                            displayScanResult(deviceDialog, result.device, 1)
                        )
                    }
                }
            }
            scanner?.startScan(listOf(filterFlask), settings, scanCallbackFlaskLP)
            val filterPuck = ScanFilter.Builder().setServiceUuid(
                ParcelUuid(PuckGattService.PuckNUS)
            ).build()
            scanCallbackPuckLP = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    super.onScanResult(callbackType, result)
                    if (!devices.contains(result.device)) {
                        devices.add(result.device)
                        deviceDialog.findViewById<LinearLayout>(R.id.bluetooth_result)?.addView(
                            displayScanResult(deviceDialog, result.device, 2)
                        )
                    }
                }
            }
            scanner?.startScan(listOf(filterPuck), settings, scanCallbackPuckLP)
        } else @Suppress("DEPRECATION") {
            scanCallbackFlask =
                LeScanCallback { bluetoothDevice: BluetoothDevice, _: Int, _: ByteArray? ->
                    if (!devices.contains(bluetoothDevice)) {
                        devices.add(bluetoothDevice)
                        deviceDialog.findViewById<LinearLayout>(R.id.bluetooth_result)?.addView(
                            displayScanResult(deviceDialog, bluetoothDevice, 1)
                        )
                    }
                }
            mBluetoothAdapter?.startLeScan(arrayOf(FlaskGattService.FlaskNUS), scanCallbackFlask)
            scanCallbackPuck =
                LeScanCallback { bluetoothDevice: BluetoothDevice, _: Int, _: ByteArray? ->
                    if (!devices.contains(bluetoothDevice)) {
                        devices.add(bluetoothDevice)
                        deviceDialog.findViewById<LinearLayout>(R.id.bluetooth_result)?.addView(
                            displayScanResult(deviceDialog, bluetoothDevice, 2)
                        )
                    }
                }
            mBluetoothAdapter?.startLeScan(arrayOf(PuckGattService.PuckNUS), scanCallbackPuck)
        }
        fragmentHandler.postDelayed({
            if (null == deviceProfile) {
                dismissGattDiscovery()
                showPurchaseNotice()
            }
        }, 30000)
    }

    @SuppressLint("MissingPermission")
    private fun selectBluetoothDevice() {
        if (mBluetoothAdapter == null) {
            Toasty(requireActivity()).Long(R.string.fail_bluetooth_adapter)
            return
        }
        if (null != deviceDialog && deviceDialog!!.isShowing) return
        val view = this.layoutInflater.inflate(R.layout.dialog_devices, null) as LinearLayout
        deviceDialog = AlertDialog.Builder(requireActivity()).setView(view).show()
        for (device in mBluetoothAdapter!!.bondedDevices) {
            val deviceType = if (device.name.lowercase().startsWith("flask")) 1 else 0
            view.findViewById<LinearLayout>(R.id.bluetooth_paired)?.addView(
                displayScanResult(deviceDialog!!, device, deviceType)
            )
        }
        scanBluetoothServices(deviceDialog!!)
    }

    private fun writeAmiiboCollection(amiiboList: ArrayList<AmiiboFile?>) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.flask_write_confirm)
            .setPositiveButton(R.string.proceed) { dialog: DialogInterface, _: Int ->
                onBottomSheetChanged(SHEET.MENU)
                showProcessingNotice(true)
                for (i in amiiboList.indices) {
                    fragmentHandler.postDelayed({
                        uploadAmiiboFile(
                            amiiboList[i], i == amiiboList.size - 1
                        )
                    }, 30L * i)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                amiiboList.clear()
                onBottomSheetChanged(SHEET.MENU)
                dialog.dismiss()
            }
            .show()
    }

    private fun uploadAmiiboFile(amiiboFile: AmiiboFile?, complete: Boolean = true) {
        if (null != amiiboFile) {
            var amiibo: Amiibo? = null
            val amiiboManager = settings.amiiboManager
            if (null != amiiboManager) {
                try {
                    val amiiboId = Amiibo.dataToId(amiiboFile.data)
                    amiibo = amiiboManager.amiibos[amiiboId]
                    if (null == amiibo) amiibo = Amiibo(amiiboManager, amiiboId, null, null)
                } catch (e: Exception) {
                    Debug.warn(e)
                }
            }
            if (null != amiibo && null != amiiboFile.data) {
                if (null != serviceFlask) serviceFlask!!.uploadAmiiboFile(
                    amiiboFile.data!!, amiibo, complete
                )
                if (null != servicePuck) servicePuck!!.uploadSlotAmiibo(
                    amiiboFile.data!!, flaskSlotCount.value - 1
                )
            }
        }
    }

    private fun setBottomSheetHidden(hidden: Boolean) {
        bottomSheet!!.isHideable = hidden
        if (hidden) bottomSheet!!.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun dismissSnackbarNotice(finite: Boolean = false) {
        if (finite) noticeState = STATE.NONE
        if (null != statusBar && statusBar!!.isShown) statusBar!!.dismiss()
    }

    private fun showScanningNotice() {
        dismissSnackbarNotice()
        noticeState = STATE.SCANNING
        if (isFragmentVisible) {
            statusBar = IconifiedSnackbar(requireActivity()).buildSnackbar(
                R.string.flask_scanning,
                R.drawable.ic_baseline_bluetooth_searching_24dp,
                Snackbar.LENGTH_INDEFINITE
            )
            statusBar!!.show()
            statusBar!!.view.keepScreenOn = true
        }
    }

    private fun showConnectionNotice() {
        dismissSnackbarNotice()
        noticeState = STATE.CONNECT
        if (isFragmentVisible) {
            statusBar = IconifiedSnackbar(requireActivity()).buildSnackbar(
                R.string.flask_located,
                R.drawable.ic_bluup_flask_24dp,
                Snackbar.LENGTH_INDEFINITE
            )
            statusBar!!.show()
            statusBar!!.view.keepScreenOn = true
        }
    }

    private fun showDisconnectNotice() {
        dismissSnackbarNotice()
        noticeState = STATE.MISSING
        if (isFragmentVisible) {
            statusBar = IconifiedSnackbar(requireActivity()).buildSnackbar(
                R.string.flask_disconnect,
                R.drawable.ic_baseline_bluetooth_searching_24dp,
                Snackbar.LENGTH_INDEFINITE
            )
            statusBar!!.setAction(R.string.scan) { selectBluetoothDevice() }
            statusBar!!.show()
        }
    }

    private fun showProcessingNotice(upload: Boolean) {
        val builder = AlertDialog.Builder(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_process, null)
        (view.findViewById<View>(R.id.process_text) as TextView).setText(
            if (upload) R.string.flask_upload else R.string.flask_remove
        )
        builder.setView(view)
        processDialog = builder.create()
        processDialog?.show()
        processDialog?.window?.decorView?.keepScreenOn = true
    }

    private fun showPurchaseNotice() {
        dismissSnackbarNotice()
        noticeState = STATE.PURCHASE
        if (isFragmentVisible) {
            statusBar = IconifiedSnackbar(requireActivity()).buildSnackbar(
                R.string.flask_missing,
                R.drawable.ic_bluup_flask_24dp,
                Snackbar.LENGTH_INDEFINITE
            )
            statusBar!!.setAction(R.string.purchase) {
                startActivity(Intent(
                    Intent.ACTION_VIEW, Uri.parse("https://www.bluuplabs.com/flask/")
                ))
                statusBar!!.dismiss()
            }
            statusBar!!.show()
        }
    }

    private fun startFlaskService() {
        val service = Intent(requireContext(), FlaskGattService::class.java)
        requireContext().startService(service)
        requireContext().bindService(service, flaskServerConn, Context.BIND_AUTO_CREATE)
    }

    private fun startPuckService() {
        val service = Intent(requireContext(), PuckGattService::class.java)
        requireContext().startService(service)
        requireContext().bindService(service, puckServerConn, Context.BIND_AUTO_CREATE)
    }

    fun disconnectService() {
        dismissSnackbarNotice(true)
        if (null != serviceFlask) serviceFlask!!.disconnect()
        if (null != servicePuck) servicePuck!!.disconnect() else stopGattService()
    }

    fun stopGattService() {
        onBottomSheetChanged(SHEET.LOCKED)
        deviceAddress = null
        try {
            requireContext().unbindService(flaskServerConn)
            requireContext().stopService(Intent(requireContext(), FlaskGattService::class.java))
        } catch (ignored: IllegalArgumentException) {
        }
        try {
            requireContext().unbindService(flaskServerConn)
            requireContext().stopService(Intent(requireContext(), FlaskGattService::class.java))
        } catch (ignored: IllegalArgumentException) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun dismissGattDiscovery() {
        mBluetoothAdapter =
            if (null != mBluetoothAdapter)
                mBluetoothAdapter
            else bluetoothHandler?.getBluetoothAdapter(requireContext())
        if (null != mBluetoothAdapter) {
            if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) {
                if (null != scanCallbackFlaskLP) mBluetoothAdapter!!.bluetoothLeScanner.stopScan(
                    scanCallbackFlaskLP
                )
                if (null != scanCallbackPuckLP) mBluetoothAdapter!!.bluetoothLeScanner.stopScan(
                    scanCallbackPuckLP
                )
            } else @Suppress("DEPRECATION") {
                if (null != scanCallbackFlask) mBluetoothAdapter!!.stopLeScan(scanCallbackFlask)
                if (null != scanCallbackPuck) mBluetoothAdapter!!.stopLeScan(scanCallbackPuck)
            }
        }
    }

    private fun handleImageClicked(amiiboFile: AmiiboFile?) {
        if (null != amiiboFile) {
            val bundle = Bundle()
            bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiiboFile.id)
            val intent = Intent(requireContext(), ImageActivity::class.java)
            intent.putExtras(bundle)
            startActivity(intent)
        }
    }

    private val isBluetoothEnabled: Boolean
        get() {
            if (null != mBluetoothAdapter && mBluetoothAdapter?.isEnabled == true) return true
            if (null != context) {
                bluetoothHandler = if (null != bluetoothHandler)
                    bluetoothHandler
                else BluetoothHandler(
                    requireContext(),
                    requireActivity().activityResultRegistry,
                    this@FlaskSlotFragment
                )
                bluetoothHandler?.requestPermissions(requireActivity())
            } else {
                fragmentHandler.postDelayed({ isBluetoothEnabled }, 125)
            }
            return false
        }

    fun delayedBluetoothEnable() {
        fragmentHandler.postDelayed({ isBluetoothEnabled }, 125)
    }

    override fun onPause() {
        isFragmentVisible = false
        dismissSnackbarNotice()
        if (noticeState == STATE.SCANNING) dismissGattDiscovery()
        super.onPause()
    }

    override fun onDestroy() {
        try {
            dismissGattDiscovery()
        } catch (ignored: NullPointerException) {
        }
        disconnectService()
        super.onDestroy()
    }

    override fun onResume() {
        isFragmentVisible = true
        super.onResume()
        if (null != statusBar && statusBar!!.isShown) return
        fragmentHandler.postDelayed({
            when (noticeState) {
                STATE.SCANNING, STATE.PURCHASE -> {
                    showScanningNotice()
                    selectBluetoothDevice()
                }
                STATE.CONNECT -> showConnectionNotice()
                STATE.MISSING -> showDisconnectNotice()
                else -> {}
            }
        }, TagMo.uiDelay.toLong())
        if (null == deviceAddress)
            onBottomSheetChanged(SHEET.LOCKED)
        else onBottomSheetChanged(SHEET.MENU)
    }

    override fun onAmiiboClicked(amiibo: Amiibo?, position: Int) {
        getActiveAmiibo(amiibo, amiiboCard)
        onBottomSheetChanged(SHEET.AMIIBO)
        bottomSheet?.state = BottomSheetBehavior.STATE_EXPANDED
        if (amiibo is FlaskTag) {
            toolbar?.menu?.findItem(R.id.mnu_backup)?.isVisible = false
            toolbar?.setOnMenuItemClickListener { item: MenuItem ->
                if (item.itemId == R.id.mnu_activate) {
                    if (null != serviceFlask) {
                        serviceFlask!!.setActiveAmiibo(
                            amiibo.name, String(TagArray.longToBytes(amiibo.id))
                        )
                    }
                    if (null != servicePuck) {
                        servicePuck!!.setActiveSlot(position)
                    }
                    return@setOnMenuItemClickListener true
                } else if (item.itemId == R.id.mnu_delete) {
                    serviceFlask!!.deleteAmiibo(
                        amiibo.name, String(TagArray.longToBytes(amiibo.id))
                    )
                    bottomSheet!!.state = BottomSheetBehavior.STATE_COLLAPSED
                    return@setOnMenuItemClickListener true
                }
                false
            }
        } else if (null != amiibo) {
            toolbar?.menu?.findItem(R.id.mnu_backup)?.isVisible = true
            toolbar?.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.mnu_activate -> {
                        if (null != serviceFlask) {
                            serviceFlask!!.setActiveAmiibo(
                                amiibo.name, amiibo.flaskTail
                            )
                        }
                        if (null != servicePuck) {
                            servicePuck!!.setActiveSlot(position)
                        }
                        return@setOnMenuItemClickListener true
                    }
                    R.id.mnu_delete -> {
                        serviceFlask!!.deleteAmiibo(
                            amiibo.name, amiibo.flaskTail
                        )
                        bottomSheet!!.state = BottomSheetBehavior.STATE_COLLAPSED
                        return@setOnMenuItemClickListener true
                    }
                    R.id.mnu_backup -> {
                        serviceFlask!!.downloadAmiibo(
                            amiibo.name, amiibo.flaskTail
                        )
                        bottomSheet!!.state = BottomSheetBehavior.STATE_COLLAPSED
                        return@setOnMenuItemClickListener true
                    }
                    else -> false
                }
            }
        }
    }

    override fun onAmiiboImageClicked(amiibo: Amiibo?) {
        if (null != amiibo) {
            val bundle = Bundle()
            bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiibo.id)
            val intent = Intent(requireContext(), ImageActivity::class.java)
            intent.putExtras(bundle)
            startActivity(intent)
        }
    }

    override fun onPermissionsFailed() {
        setBottomSheetHidden(true)
        Toasty(requireActivity()).Long(R.string.fail_permissions)
    }

    override fun onAdapterMissing() {
        setBottomSheetHidden(true)
        Toasty(requireActivity()).Long(R.string.fail_bluetooth_adapter)
    }

    override fun onAdapterEnabled(adapter: BluetoothAdapter?) {
        this.mBluetoothAdapter = adapter
        selectBluetoothDevice()
    }
}