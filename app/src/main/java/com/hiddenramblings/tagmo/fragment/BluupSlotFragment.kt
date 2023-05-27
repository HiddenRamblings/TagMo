package com.hiddenramblings.tagmo.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.SearchManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.*
import androidx.cardview.widget.CardView
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.target.CustomTarget
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.snackbar.Snackbar
import com.hiddenramblings.tagmo.*
import com.hiddenramblings.tagmo.adapter.BluupSlotAdapter
import com.hiddenramblings.tagmo.adapter.WriteTagAdapter
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboFile
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.getAmiiboManager
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.hasSpoofData
import com.hiddenramblings.tagmo.amiibo.BluupTag
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.amiibo.tagdata.AmiiboData
import com.hiddenramblings.tagmo.bluetooth.BluetoothHandler
import com.hiddenramblings.tagmo.bluetooth.BluetoothHandler.BluetoothListener
import com.hiddenramblings.tagmo.bluetooth.BluupGattService
import com.hiddenramblings.tagmo.bluetooth.PuckGattService
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.eightbit.request.ImageTarget
import com.hiddenramblings.tagmo.eightbit.widget.Toasty
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.shawnlin.numberpicker.NumberPicker
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.text.ParseException

@SuppressLint("NewApi")
open class BluupSlotFragment : Fragment(), BluupSlotAdapter.OnAmiiboClickListener, BluetoothListener {
    private val prefs: Preferences by lazy { Preferences(TagMo.appContext) }
    private val keyManager: KeyManager by lazy { (requireActivity() as BrowserActivity).keyManager }

    private var bluetoothHandler: BluetoothHandler? = null
    private var isFragmentVisible = false
    private var amiiboTile: CardView? = null
    private var amiiboCard: CardView? = null
    private var toolbar: Toolbar? = null
    private lateinit var amiiboTileTarget: CustomTarget<Bitmap?>
    var bluupContent: RecyclerView? = null
        private set
    var bluupAdapter: BluupSlotAdapter? = null
    private var bluupStats: TextView? = null
    private lateinit var bluupSlotCount: NumberPicker
    private var screenOptions: LinearLayout? = null
    private var writeSlots: AppCompatButton? = null
    private var writeSerials: SwitchCompat? = null
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
    private var scanCallbackBluupLP: ScanCallback? = null
    private var scanCallbackBluup: LeScanCallback? = null
    private var serviceBluup: BluupGattService? = null
    private var scanCallbackPuckLP: ScanCallback? = null
    private var scanCallbackPuck: LeScanCallback? = null
    private var servicePuck: PuckGattService? = null
    private var deviceProfile: String? = null
    private var deviceAddress: String? = null
    private var maxSlotCount = 85
    private var currentCount = 0
    private var deviceDialog: AlertDialog? = null

    private enum class DEVICE {
        FLASK, SLIDE, PUCK, GATT
    }

    private var deviceType = DEVICE.GATT

    private enum class STATE {
        NONE, SCANNING, CONNECT, MISSING, TIMEOUT
    }

    private var noticeState = STATE.NONE

    private enum class SHEET {
        LOCKED, AMIIBO, MENU, WRITE
    }

    private val fragmentHandler = Handler(Looper.getMainLooper())
    private var bluupServerConn: ServiceConnection = object : ServiceConnection {
        var isServiceDiscovered = false
        override fun onServiceConnected(component: ComponentName, binder: IBinder) {
            val localBinder = binder as BluupGattService.LocalBinder
            serviceBluup = localBinder.service.apply {
                if (initialize() && connect(deviceAddress)) {
                    setListener(object : BluupGattService.BluupBluetoothListener {
                        override fun onBluupServicesDiscovered() {
                            isServiceDiscovered = true
                            onBottomSheetChanged(SHEET.MENU)
                            maxSlotCount = if (deviceType == DEVICE.SLIDE) 40 else 85
                            requireView().post {
                                bluupSlotCount.maxValue = maxSlotCount
                                screenOptions?.isVisible = true
                                createBlank?.isVisible = true
                                requireView().findViewById<TextView>(R.id.hardware_info).text = deviceProfile
                            }
                            try {
                                setBluupCharacteristicRX()
                                deviceAmiibo
                            } catch (uoe: UnsupportedOperationException) {
                                disconnectService()
                                Toasty(requireContext()).Short(R.string.device_invalid)
                            }
                        }

                        override fun onBluupStatusChanged(jsonObject: JSONObject?) {
                            processDialog?.let {
                                if (it.isShowing) it.dismiss()
                            }
                            deviceAmiibo
                        }

                        override fun onBluupListRetrieved(jsonArray: JSONArray) {
                            currentCount = jsonArray.length()
                            val bluupAmiibos: ArrayList<Amiibo?> = arrayListOf()
                            for (i in 0 until currentCount) {
                                try {
                                    val amiibo = getAmiiboFromTail(
                                        jsonArray.getString(i).split("|")
                                    )
                                    bluupAmiibos.add(amiibo)
                                } catch (ex: JSONException) {
                                    Debug.warn(ex)
                                } catch (ex: NullPointerException) {
                                    Debug.warn(ex)
                                }
                            }
                            settings.removeChangeListener(bluupAdapter)
                            bluupAdapter = BluupSlotAdapter(
                                settings, this@BluupSlotFragment
                            ).also {
                                it.setBluupAmiibo(bluupAmiibos)
                                dismissSnackbarNotice(true)
                                requireView().post {
                                    bluupContent?.adapter = it
                                    settings.addChangeListener(it)
                                }
                                if (currentCount > 0) {
                                    activeAmiibo
                                    requireView().post {
                                        it.notifyItemRangeInserted(0, currentCount)
                                    }
                                } else {
                                    amiiboTile?.isInvisible = true
                                    bluupButtonState
                                }
                            }
                        }

                        override fun onBluupRangeRetrieved(jsonArray: JSONArray) {
                            val bluupAmiibos: ArrayList<Amiibo?> = arrayListOf()
                            for (i in 0 until jsonArray.length()) {
                                try {
                                    val amiibo = getAmiiboFromTail(
                                        jsonArray.getString(i).split("|")
                                    )
                                    bluupAmiibos.add(amiibo)
                                } catch (ex: JSONException) {
                                    Debug.warn(ex)
                                } catch (ex: NullPointerException) {
                                    Debug.warn(ex)
                                }
                            }
                            bluupAdapter?.run {
                                addBluupAmiibo(bluupAmiibos)
                                requireView().post {
                                    notifyItemRangeInserted(currentCount, bluupAmiibos.size)
                                }
                                currentCount = itemCount
                            }
                        }

                        override fun onBluupActiveChanged(jsonObject: JSONObject?) {
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
                                if (bottomSheet?.state == BottomSheetBehavior.STATE_COLLAPSED)
                                    getActiveAmiibo(amiibo, amiiboCard)
                                prefs.bluupActiveSlot(index.toInt())
                                bluupButtonState
                                requireView().post {
                                    bluupStats?.text =
                                        getString(R.string.gatt_count, index, currentCount)
                                }
                            } catch (ex: JSONException) {
                                Debug.warn(ex)
                            } catch (ex: NullPointerException) {
                                Debug.warn(ex)
                            }
                        }

                        override fun onBluupFilesDownload(dataString: String) {
                            try {
                                val tagData = dataString.toByteArray()
                            } catch (e: Exception) { e.printStackTrace() }
                        }

                        override fun onBluupProcessFinish() {
                            processDialog?.let {
                                if (it.isShowing) it.dismiss()
                            }
                        }

                        override fun onBluupConnectionLost() {
                            fragmentHandler.postDelayed(
                                { showDisconnectNotice() }, TagMo.uiDelay.toLong()
                            )
                            bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
                            stopGattService()
                        }
                    })
                } else {
                    stopGattService()
                    Toasty(requireContext()).Short(R.string.device_invalid)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            stopGattService()
            if (!isServiceDiscovered) {
                showTimeoutNotice()
            }
        }
    }
    private var puckServerConn: ServiceConnection = object : ServiceConnection {
        var isServiceDiscovered = false
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val localBinder = binder as PuckGattService.LocalBinder
            servicePuck = localBinder.service.apply {
                if (initialize() && connect(deviceAddress)) {
                    setListener(object : PuckGattService.BluetoothGattListener {
                        override fun onPuckServicesDiscovered() {
                            isServiceDiscovered = true
                            onBottomSheetChanged(SHEET.MENU)
                            maxSlotCount = 32
                            requireView().post {
                                bluupSlotCount.maxValue = maxSlotCount
                                screenOptions?.isGone = true
                                createBlank?.isGone = true
                                requireView().findViewById<TextView>(R.id.hardware_info).text = deviceProfile
                                bluupSlotCount.maxValue = maxSlotCount
                            }
                            try {
                                setPuckCharacteristicRX()
                                deviceAmiibo
                                // getDeviceSlots(32);
                            } catch (uoe: UnsupportedOperationException) {
                                disconnectService()
                                Toasty(requireContext()).Short(R.string.device_invalid)
                            }
                        }

                        override fun onPuckActiveChanged(slot: Int) {
                            bluupAdapter?.run {
                                val amiibo = getItem(slot)
                                getActiveAmiibo(amiibo, amiiboTile)
                                if (bottomSheet?.state == BottomSheetBehavior.STATE_COLLAPSED)
                                    getActiveAmiibo(amiibo, amiiboCard)
                                prefs.bluupActiveSlot(slot)
                                bluupButtonState
                                requireView().post {
                                    bluupStats?.text = getString(
                                        R.string.gatt_count, slot.toString(), currentCount
                                    )
                                }
                            }
                        }

                        override fun onPuckListRetrieved(
                            slotData: ArrayList<ByteArray?>, active: Int
                        ) {
                            currentCount = slotData.size
                            val bluupAmiibos: ArrayList<Amiibo?> = arrayListOf()
                            for (i in 0 until currentCount) {
                                if (slotData[i]?.isNotEmpty() == true) {
                                    val amiibo = getAmiiboFromHead(slotData[i])
                                    bluupAmiibos.add(amiibo)
                                } else {
                                    bluupAmiibos.add(null)
                                }
                            }
                            settings.removeChangeListener(bluupAdapter)
                            bluupAdapter = BluupSlotAdapter(
                                settings, this@BluupSlotFragment
                            ).also {
                                it.setBluupAmiibo(bluupAmiibos)
                                dismissSnackbarNotice(true)
                                requireView().post {
                                    bluupContent?.adapter = it
                                    settings.addChangeListener(it)
                                }
                                if (currentCount > 0) {
                                    requireView().post {
                                        it.notifyItemRangeInserted(0, currentCount)
                                    }
                                    onPuckActiveChanged(active)
                                } else {
                                    amiiboTile?.isInvisible = true
                                    bluupButtonState
                                }
                            }
                        }

                        override fun onPuckFilesDownload(tagData: ByteArray) {}
                        override fun onPuckProcessFinish() {
                            processDialog?.let {
                                if (it.isShowing) it.dismiss()
                            }
                        }

                        override fun onPuckConnectionLost() {
                            fragmentHandler.postDelayed(
                                { showDisconnectNotice() }, TagMo.uiDelay.toLong()
                            )
                            bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
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
                showTimeoutNotice()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_bluup_slot, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) return

        val activity = requireActivity() as BrowserActivity

        amiiboTile = view.findViewById<CardView>(R.id.active_tile_layout)
        amiiboCard = view.findViewById<CardView>(R.id.active_card_layout).apply {
            findViewById<View>(R.id.txtError)?.isGone = true
            findViewById<View>(R.id.txtPath)?.isGone = true
        }

        toolbar = view.findViewById(R.id.toolbar)

        settings = activity.settings ?: BrowserSettings().initialize()

        bluupContent = view.findViewById<RecyclerView>(R.id.bluup_content).apply {
            if (TagMo.forceSoftwareLayer) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            setItemViewCacheSize(40)
            layoutManager = if (settings.amiiboView == BrowserSettings.VIEW.IMAGE.value)
                GridLayoutManager(activity, activity.columnCount).apply {
                    initialPrefetchItemCount = 10
                }
            else
                LinearLayoutManager(activity).apply {
                    initialPrefetchItemCount = 10
                }
        }

        bluupStats = view.findViewById(R.id.bluup_stats)
        switchMenuOptions = view.findViewById(R.id.switch_menu_btn)
        slotOptionsMenu = view.findViewById(R.id.slot_options_menu)

        createBlank = view.findViewById<AppCompatButton>(R.id.create_blank).apply {
            setOnClickListener { serviceBluup?.createBlankTag() }
        }

        screenOptions = view.findViewById(R.id.screen_options)

        val searchView = view.findViewById<SearchView>(R.id.amiibo_search)
        if (BuildConfig.WEAR_OS) {
            searchView.isGone = true
        } else {
            with (activity.getSystemService(Context.SEARCH_SERVICE) as SearchManager) {
                searchView.setSearchableInfo(getSearchableInfo(activity.componentName))
            }
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

        view.findViewById<AppCompatButton>(R.id.write_slot_file).apply {
            setOnClickListener {
                settings.addChangeListener(writeTagAdapter)
                onBottomSheetChanged(SHEET.WRITE)
                searchView.setQuery(settings.query, true)
                searchView.clearFocus()
                writeTagAdapter?.setListener(object : WriteTagAdapter.OnAmiiboClickListener {
                    override fun onAmiiboClicked(amiiboFile: AmiiboFile?) {
                        onBottomSheetChanged(SHEET.AMIIBO)
                        showProcessingNotice(true)
                        uploadAmiiboFile(amiiboFile)
                        settings.removeChangeListener(writeTagAdapter)
                    }

                    override fun onAmiiboImageClicked(amiiboFile: AmiiboFile?) {
                        handleImageClicked(amiiboFile)
                    }

                    override fun onAmiiboListClicked(amiiboList: ArrayList<AmiiboFile?>?) {}
                    override fun onAmiiboDataClicked(amiiboFile: AmiiboFile?, count: Int) {}
                })
                bottomSheet?.setState(BottomSheetBehavior.STATE_EXPANDED)
            }
        }

        writeSlots = view.findViewById<AppCompatButton>(R.id.write_slot_count).apply {
            text = getString(R.string.write_slots, 1)
            setOnClickListener {
                settings.addChangeListener(writeTagAdapter)
                onBottomSheetChanged(SHEET.WRITE)
                searchView.setQuery(settings.query, true)
                searchView.clearFocus()
                bluupSlotCount.value.let { count ->
                    writeTagAdapter?.setListener(object : WriteTagAdapter.OnAmiiboClickListener {
                        override fun onAmiiboClicked(amiiboFile: AmiiboFile?) {}
                        override fun onAmiiboImageClicked(amiiboFile: AmiiboFile?) {}
                        override fun onAmiiboListClicked(amiiboList: ArrayList<AmiiboFile?>?) {
                            if (!amiiboList.isNullOrEmpty()) writeAmiiboFileCollection(amiiboList)
                        }
                        override fun onAmiiboDataClicked(amiiboFile: AmiiboFile?, count: Int) {
                            amiiboFile?.let {
                                writeAmiiboDataCollection(it.withRandomSerials(keyManager, count))
                            }
                        }
                    }, count, writeSerials?.isChecked ?: false)
                }
                bottomSheet?.setState(BottomSheetBehavior.STATE_EXPANDED)
            }
        }

        writeSerials = view.findViewById(R.id.write_serial_fill)

        eraseSlots = view.findViewById<AppCompatButton>(R.id.erase_slot_count).apply {
            text = getString(R.string.erase_slots, 0)
            setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setMessage(R.string.gatt_erase_confirm)
                    .setPositiveButton(R.string.proceed) { _: DialogInterface?, _: Int ->
                        showProcessingNotice(false)
                        serviceBluup?.clearStorage(currentCount)
                    }
                    .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }

        bluupSlotCount = view.findViewById<NumberPicker>(R.id.number_picker_slot).apply {
            if (TagMo.forceSoftwareLayer) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            maxValue = maxSlotCount
            setOnValueChangedListener { _, _, newVal ->
                if (maxSlotCount - currentCount > 0)
                    writeSlots?.text = getString(R.string.write_slots, newVal)
            }
        }

        writeSlotsLayout = view.findViewById<LinearLayout>(R.id.write_list_slots).apply {
            if (TagMo.forceSoftwareLayer) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        view.findViewById<RecyclerView>(R.id.amiibo_files_list).apply {
            if (TagMo.forceSoftwareLayer) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            setHasFixedSize(true)
            setItemViewCacheSize(40)
            layoutManager = if (settings.amiiboView == BrowserSettings.VIEW.IMAGE.value)
                GridLayoutManager(activity, activity.columnCount).apply {
                    initialPrefetchItemCount = 10
                }
            else
                LinearLayoutManager(activity).apply {
                    initialPrefetchItemCount = 10
                }
            writeTagAdapter = WriteTagAdapter(settings).also { adapter = it }
        }

        val toggle = view.findViewById<AppCompatImageView>(R.id.toggle)
        bottomSheet = BottomSheetBehavior.from(view.findViewById(R.id.bottom_sheet_slot)).apply {
            state = BottomSheetBehavior.STATE_COLLAPSED
            var slideHeight = 0F
            addBottomSheetCallback(object : BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        if (writeSlotsLayout?.visibility == View.VISIBLE)
                            onBottomSheetChanged(SHEET.MENU)
                        toggle.setImageResource(R.drawable.ic_expand_less_24dp)
                        bluupContent?.setPadding(0, 0, 0, 0)
                    } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                        toggle.setImageResource(R.drawable.ic_expand_more_24dp)
                        bluupContent?.let {
                            val bottomHeight: Int = (view.measuredHeight - peekHeight)
                            it.setPadding(0, 0, 0, if (slideHeight > 0)
                                (bottomHeight * slideHeight).toInt() else 0
                            )
                        }

                    }
                }

                override fun onSlide(view: View, slideOffset: Float) { slideHeight = slideOffset }
            })
        }.also { bottomSheet ->
            setBottomSheetHidden(false)
            toggle.setOnClickListener {
                if (bottomSheet.state == BottomSheetBehavior.STATE_COLLAPSED) {
                    bottomSheet.setState(BottomSheetBehavior.STATE_EXPANDED)
                } else {
                    bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
                }
            }
        }
        toggle.setImageResource(R.drawable.ic_expand_more_24dp)
        toolbar?.inflateMenu(R.menu.bluup_menu)

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

        view.findViewById<View>(R.id.screen_layered)
            .setOnClickListener { serviceBluup?.setFlaskFace(false) }
        view.findViewById<View>(R.id.screen_stacked)
            .setOnClickListener { serviceBluup?.setFlaskFace(true) }
        bluupButtonState
    }

    private fun onBottomSheetChanged(sheet: SHEET) {
        bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheet?.isFitToContents = true
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
                    bottomSheet?.isFitToContents = false
                    amiiboCard?.isGone = true
                    switchMenuOptions?.isGone = true
                    slotOptionsMenu?.isGone = true
                    writeSlotsLayout?.isVisible = true
                }
            }
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

    private val bluupButtonState: Unit
        get() {
            bluupContent?.post {
                val openSlots = maxSlotCount - currentCount
                bluupSlotCount.value = openSlots
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
        bluupAdapter?.getItem(0).run {
            if (this is BluupTag) {
                serviceBluup?.setActiveAmiibo(name, String(TagArray.longToBytes(id)))
            } else {
                this?.let { serviceBluup?.setActiveAmiibo(it.name, it.bluupTail) }
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
        requireView().post {
            val amiiboHexId: String
            val amiiboName: String?
            var amiiboSeries = ""
            var amiiboType = ""
            var gameSeries = ""
            var amiiboImageUrl: String? = null
            if (amiiboView === amiiboTile) amiiboView.isVisible = true
            if (null == active) {
                txtName.setText(R.string.no_tag_loaded)
                txtTagId?.isInvisible = true
                txtAmiiboSeries.isInvisible = true
                txtAmiiboType.isInvisible = true
                txtGameSeries.isInvisible = true
                if (amiiboView === amiiboCard) txtUsageLabel.isInvisible = true
            } else if (active is BluupTag) {
                txtName.setText(R.string.blank_tag)
                txtTagId?.isInvisible = true
                txtAmiiboSeries.isInvisible = true
                txtAmiiboType.isInvisible = true
                txtGameSeries.isInvisible = true
                if (amiiboView === amiiboCard) txtUsageLabel.isInvisible = true
            } else {
                txtTagId?.isInvisible = false
                txtAmiiboSeries.isInvisible = false
                txtAmiiboType.isInvisible = false
                txtGameSeries.isInvisible = false
                if (amiiboView === amiiboCard) txtUsageLabel.isInvisible = false
                amiiboHexId = Amiibo.idToHex(active.id)
                amiiboName = active.name
                amiiboImageUrl = active.imageUrl
                active.amiiboSeries?.let { amiiboSeries = it.name }
                active.amiiboType?.let { amiiboType = it.name }
                active.gameSeries?.let { gameSeries = it.name }
                setAmiiboInfoText(txtName, amiiboName)
                setAmiiboInfoText(txtTagId, amiiboHexId)
                setAmiiboInfoText(txtAmiiboSeries, amiiboSeries)
                setAmiiboInfoText(txtAmiiboType, amiiboType)
                setAmiiboInfoText(txtGameSeries, gameSeries)
                if (hasSpoofData(amiiboHexId)) txtTagId.isEnabled = false
            }

            imageAmiibo?.let {
                if (amiiboView === amiiboCard && null == amiiboImageUrl) {
                    it.setImageResource(0)
                    it.isInvisible = true
                } else if (amiiboView !== amiiboTile || null != amiiboImageUrl) {
                    if (amiiboView === amiiboCard) {
                        it.setImageResource(0)
                        it.isGone = true
                    }
                    if (!amiiboImageUrl.isNullOrEmpty()) {
                        val imageTarget: CustomTarget<Bitmap?> = if (amiiboView === amiiboCard) {
                            ImageTarget.getTargetHR(it)
                        } else {
                            ImageTarget.getTargetR(it)
                        }
                        GlideApp.with(it).clear(it)
                        GlideApp.with(it).asBitmap().load(amiiboImageUrl).into(imageTarget)
                    }
                    it.setOnClickListener {
                        startActivity(Intent(requireContext(), ImageActivity::class.java)
                            .putExtras(Bundle().apply {
                                putLong(NFCIntent.EXTRA_AMIIBO_ID, active!!.id)
                            })
                        )
                    }
                }
            }
        }
    }

    private fun getAmiiboFromTail(name: List<String>): Amiibo? {
        when {
            name.size < 2 -> return null
            name[0].startsWith("New Tag") || name[1].isEmpty() -> return BluupTag(name)
            else -> {
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
                var selectedAmiibo: Amiibo? = null
                amiiboManager?.let {
                    val matches : ArrayList<Amiibo> = arrayListOf()
                    run breaking@{
                        it.amiibos.values.forEach { amiibo ->
                            if (name[1] == amiibo.bluupTail) {
                                if (amiibo.bluupName == name[0]) {
                                    selectedAmiibo = amiibo
                                    matches.clear()
                                    return@breaking
                                } else {
                                    matches.add(amiibo)
                                }
                            }
                        }
                        selectedAmiibo = matches.find {
                            null == it.bluupName
                        }?.apply { bluupName = name[0] }
                    }
                    if (null == selectedAmiibo && matches.isNotEmpty())
                        selectedAmiibo = matches[0]
                }
                return selectedAmiibo
            }
        }
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
        amiiboManager?.let {
            try {
                val headData = ByteBuffer.wrap(tagData!!)
                val amiiboId = headData.getLong(0x28)
                selectedAmiibo = it.amiibos[amiiboId]
            } catch (e: Exception) { Debug.info(e) }
        }
        return selectedAmiibo
    }

    @SuppressLint("InflateParams")
    private fun displayScanResult(
        deviceDialog: AlertDialog, device: BluetoothDevice, detectedType: DEVICE
    ) : View {
        val item = this.layoutInflater.inflate(R.layout.device_bluetooth, null)
        item.findViewById<TextView>(R.id.device_name).text = device.name
        item.findViewById<TextView>(R.id.device_address).text =
            requireActivity().getString(R.string.device_address, device.address)

        item.findViewById<View>(R.id.connect_flask).run {
            setOnClickListener {
                deviceDialog.dismiss()
                deviceProfile = device.name
                deviceAddress = device.address
                deviceType = DEVICE.FLASK
                dismissGattDiscovery()
                showConnectionNotice()
                startBluupService()
            }
            isEnabled = detectedType != DEVICE.PUCK
        }

        item.findViewById<View>(R.id.connect_slide).run {
            setOnClickListener {
                deviceDialog.dismiss()
                deviceProfile = device.name
                deviceAddress = device.address
                deviceType = DEVICE.SLIDE
                dismissGattDiscovery()
                showConnectionNotice()
                startBluupService()
            }
           isEnabled = detectedType != DEVICE.PUCK
        }

        item.findViewById<View>(R.id.connect_puck).run {
            setOnClickListener {
                deviceDialog.dismiss()
                deviceProfile = device.name
                deviceAddress = device.address
                deviceType = DEVICE.PUCK
                dismissGattDiscovery()
                showConnectionNotice()
                startPuckService()
            }
            isEnabled = detectedType != DEVICE.FLASK && detectedType != DEVICE.SLIDE
        }
        return item
    }

    @SuppressLint("MissingPermission", "NewApi")
    private fun scanBluetoothServices(deviceDialog: AlertDialog) {
        mBluetoothAdapter = mBluetoothAdapter
            ?: bluetoothHandler?.getBluetoothAdapter(requireContext())
        if (null == mBluetoothAdapter) {
            setBottomSheetHidden(true)
            Toasty(requireActivity()).Long(R.string.fail_bluetooth_adapter)
            return
        }
        showScanningNotice()
        deviceProfile = null
        val devices: ArrayList<BluetoothDevice> = arrayListOf()
        if (Version.isLollipop) {
            val scanner = mBluetoothAdapter?.bluetoothLeScanner
            val settings = ScanSettings.Builder().setScanMode(
                ScanSettings.SCAN_MODE_LOW_LATENCY
            ).build()
            val filterBluup = ScanFilter.Builder().setServiceUuid(
                ParcelUuid(BluupGattService.BluupNUS)
            ).build()
            scanCallbackBluupLP = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    super.onScanResult(callbackType, result)
                    if (!devices.contains(result.device)) {
                        devices.add(result.device)
                        deviceDialog.findViewById<LinearLayout>(R.id.bluetooth_result)?.addView(
                            displayScanResult(deviceDialog, result.device, DEVICE.GATT)
                        )
                    }
                }
            }
            scanner?.startScan(listOf(filterBluup), settings, scanCallbackBluupLP)
            val filterPuck = ScanFilter.Builder().setServiceUuid(
                ParcelUuid(PuckGattService.PuckNUS)
            ).build()
            scanCallbackPuckLP = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    super.onScanResult(callbackType, result)
                    if (!devices.contains(result.device)) {
                        devices.add(result.device)
                        deviceDialog.findViewById<LinearLayout>(R.id.bluetooth_result)?.addView(
                            displayScanResult(deviceDialog, result.device, DEVICE.PUCK)
                        )
                    }
                }
            }
            scanner?.startScan(listOf(filterPuck), settings, scanCallbackPuckLP)
        } else @Suppress("DEPRECATION") {
            scanCallbackBluup =
                LeScanCallback { bluetoothDevice: BluetoothDevice, _: Int, _: ByteArray? ->
                    if (!devices.contains(bluetoothDevice)) {
                        devices.add(bluetoothDevice)
                        deviceDialog.findViewById<LinearLayout>(R.id.bluetooth_result)?.addView(
                            displayScanResult(deviceDialog, bluetoothDevice, DEVICE.GATT)
                        )
                    }
                }
            mBluetoothAdapter?.startLeScan(arrayOf(BluupGattService.BluupNUS), scanCallbackBluup)
            scanCallbackPuck =
                LeScanCallback { bluetoothDevice: BluetoothDevice, _: Int, _: ByteArray? ->
                    if (!devices.contains(bluetoothDevice)) {
                        devices.add(bluetoothDevice)
                        deviceDialog.findViewById<LinearLayout>(R.id.bluetooth_result)?.addView(
                            displayScanResult(deviceDialog, bluetoothDevice, DEVICE.PUCK)
                        )
                    }
                }
            mBluetoothAdapter?.startLeScan(arrayOf(PuckGattService.PuckNUS), scanCallbackPuck)
        }
        fragmentHandler.postDelayed({
            if (null == deviceProfile) {
                dismissGattDiscovery()
                showTimeoutNotice()
            }
        }, 30000)
    }

    @SuppressLint("MissingPermission")
    private fun selectBluetoothDevice() {
        if (mBluetoothAdapter?.isEnabled != true) {
            Toasty(requireActivity()).Long(R.string.fail_bluetooth_adapter)
            return
        }
        if (deviceDialog?.isShowing == true) return
        val view = this.layoutInflater.inflate(R.layout.dialog_devices, null) as LinearLayout
        view.findViewById<AppCompatButton>(R.id.purchase_bluup).setOnClickListener {
            startActivity(Intent(
                Intent.ACTION_VIEW, Uri.parse("https://www.bluuplabs.com/")
            ))
        }
        deviceDialog = AlertDialog.Builder(requireActivity()).setView(view).show().apply {
            mBluetoothAdapter?.bondedDevices?.forEach { device ->
                deviceType = when {
                    device.name.lowercase().startsWith("flask") -> DEVICE.FLASK
                    device.name.lowercase().startsWith("slide") -> DEVICE.SLIDE
                    else -> DEVICE.GATT
                }
                view.findViewById<LinearLayout>(R.id.bluetooth_paired)?.addView(
                    displayScanResult(this, device, deviceType)
                )
            }
            scanBluetoothServices(this)
        }
    }

    private fun writeAmiiboDataCollection(bytesList: ArrayList<AmiiboData?>) {
        settings.removeChangeListener(writeTagAdapter)
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.gatt_write_confirm)
            .setPositiveButton(R.string.proceed) { dialog: DialogInterface, _: Int ->
                showProcessingNotice(true)
                bytesList.forEachIndexed { i, byte ->
                    fragmentHandler.postDelayed({
                        uploadAmiiboData(byte, i == bytesList.size - 1)
                    }, 30L * i)
                }
                onBottomSheetChanged(SHEET.MENU)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                onBottomSheetChanged(SHEET.MENU)
                dialog.dismiss()
            }
            .show()
    }

    private fun writeAmiiboFileCollection(amiiboList: ArrayList<AmiiboFile?>) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.gatt_write_confirm)
            .setPositiveButton(R.string.proceed) { dialog: DialogInterface, _: Int ->
                showProcessingNotice(true)
                amiiboList.forEachIndexed { i, file ->
                    fragmentHandler.postDelayed({
                        uploadAmiiboFile(file, i == amiiboList.size - 1)
                    }, 30L * i)
                }
                onBottomSheetChanged(SHEET.MENU)
                settings.removeChangeListener(writeTagAdapter)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                onBottomSheetChanged(SHEET.MENU)
                settings.removeChangeListener(writeTagAdapter)
                dialog.dismiss()
            }
            .show()
    }

    private fun uploadAmiiboData(amiiboData: AmiiboData?, complete: Boolean = true) {
        var amiibo: Amiibo? = null
        settings.amiiboManager?.let {
            try {
                val amiiboId = Amiibo.dataToId(amiiboData?.array)
                amiibo = it.amiibos[amiiboId]
                if (null == amiibo) amiibo = Amiibo(it, amiiboId, null, null)
            } catch (e: Exception) { Debug.warn(e) }
        }
        amiibo?.let {
            amiiboData?.array?.let { data ->
                serviceBluup?.uploadAmiiboFile(
                    data, it, bluupAdapter?.getDuplicates(it) ?: 0, complete
                )
                servicePuck?.uploadSlotAmiibo(data, bluupSlotCount.value - 1)
            }
        }
    }

    private fun uploadAmiiboFile(amiiboFile: AmiiboFile?, complete: Boolean = true) {
        amiiboFile?.let { file ->
            var amiibo: Amiibo? = null
            settings.amiiboManager?.let {
                try {
                    val amiiboId = Amiibo.dataToId(file.data)
                    amiibo = it.amiibos[amiiboId]
                    if (null == amiibo) amiibo = Amiibo(it, amiiboId, null, null)
                } catch (e: Exception) { Debug.warn(e) }
            }
            amiibo?.let {
                file.data?.let { data ->
                    serviceBluup?.uploadAmiiboFile(
                        data, it, bluupAdapter?.getDuplicates(it) ?: 0, complete
                    )
                    servicePuck?.uploadSlotAmiibo(data, bluupSlotCount.value - 1)
                }
            }
        }
    }

    private fun setBottomSheetHidden(hidden: Boolean) {
        bottomSheet?.isHideable = hidden
        if (hidden)
            bottomSheet?.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun dismissSnackbarNotice(finite: Boolean = false) {
        if (finite) noticeState = STATE.NONE
        if (statusBar?.isShown == true) statusBar?.dismiss()
    }

    private fun showScanningNotice() {
        dismissSnackbarNotice()
        noticeState = STATE.SCANNING
        if (isFragmentVisible) {
            statusBar = IconifiedSnackbar(requireActivity()).buildSnackbar(
                R.string.gatt_scanning,
                R.drawable.ic_bluetooth_searching_24dp, Snackbar.LENGTH_INDEFINITE
            ).also {
                it.show()
                it.view.keepScreenOn = true
            }
        }
    }

    private fun showConnectionNotice() {
        dismissSnackbarNotice()
        noticeState = STATE.CONNECT
        if (isFragmentVisible) {
            statusBar = IconifiedSnackbar(requireActivity()).buildSnackbar(
                R.string.gatt_located,
                R.drawable.ic_bluup_labs_24dp, Snackbar.LENGTH_INDEFINITE
            ).also {
                it.show()
                it.view.keepScreenOn = true
            }
        }
    }

    private fun showDisconnectNotice() {
        dismissSnackbarNotice()
        noticeState = STATE.MISSING
        if (isFragmentVisible) {
            statusBar = IconifiedSnackbar(requireActivity()).buildSnackbar(
                R.string.gatt_disconnect,
                R.drawable.ic_bluetooth_searching_24dp, Snackbar.LENGTH_INDEFINITE
            ).also { status ->
                status.setAction(R.string.scan) {
                    selectBluetoothDevice()
                    status.dismiss()
                }
                status.show()
            }
        }
    }

    private fun showProcessingNotice(upload: Boolean) {
        val builder = AlertDialog.Builder(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_process, null)
        view.findViewById<TextView>(R.id.process_text).setText(
            if (upload) R.string.gatt_upload else R.string.gatt_remove
        )
        builder.setView(view)
        processDialog = builder.create().also {
            it.show()
            it.window?.decorView?.keepScreenOn = true
        }
    }

    private fun showTimeoutNotice() {
        dismissSnackbarNotice()
        noticeState = STATE.TIMEOUT
        if (isFragmentVisible) {
            statusBar = IconifiedSnackbar(requireActivity()).buildSnackbar(
                R.string.gatt_missing,
                R.drawable.ic_bluup_labs_24dp,
                Snackbar.LENGTH_INDEFINITE
            ).also { status ->
                status.setAction(R.string.retry) {
                    selectBluetoothDevice()
                    status.dismiss()
                }
                status.show()
            }
        }
    }

    private fun startBluupService() {
        val service = Intent(requireContext(), BluupGattService::class.java)
        requireContext().startService(service)
        requireContext().bindService(service, bluupServerConn, Context.BIND_AUTO_CREATE)
    }

    private fun startPuckService() {
        val service = Intent(requireContext(), PuckGattService::class.java)
        requireContext().startService(service)
        requireContext().bindService(service, puckServerConn, Context.BIND_AUTO_CREATE)
    }

    fun disconnectService() {
        dismissSnackbarNotice(true)
        serviceBluup?.disconnect() ?: servicePuck?.disconnect() ?: stopGattService()
    }

    fun stopGattService() {
        onBottomSheetChanged(SHEET.LOCKED)
        deviceAddress = null
        try {
            requireContext().unbindService(bluupServerConn)
            requireContext().stopService(Intent(requireContext(), BluupGattService::class.java))
        } catch (ignored: IllegalArgumentException) {
        }
        try {
            requireContext().unbindService(bluupServerConn)
            requireContext().stopService(Intent(requireContext(), BluupGattService::class.java))
        } catch (ignored: IllegalArgumentException) { }
    }

    @SuppressLint("MissingPermission")
    private fun dismissGattDiscovery() {
        mBluetoothAdapter = mBluetoothAdapter
            ?: bluetoothHandler?.getBluetoothAdapter(requireContext())
        mBluetoothAdapter?.let { adapter ->
            if (!adapter.isEnabled) return
            if (Version.isLollipop) {
                scanCallbackBluupLP?.let {
                    adapter.bluetoothLeScanner.stopScan(it)
                    adapter.bluetoothLeScanner.flushPendingScanResults(it)
                }
                scanCallbackPuckLP?.let {
                    adapter.bluetoothLeScanner.stopScan(it)
                    adapter.bluetoothLeScanner.flushPendingScanResults(it)
                }
            } else @Suppress("DEPRECATION") {
                scanCallbackBluup?.let { adapter.stopLeScan(it) }
                scanCallbackPuck?.let { adapter.stopLeScan(it) }
            }
        }
    }

    private fun handleImageClicked(amiiboFile: AmiiboFile?) {
        amiiboFile?.let {
            this.startActivity(Intent(requireContext(), ImageActivity::class.java).apply {
                putExtras(Bundle().apply { putLong(NFCIntent.EXTRA_AMIIBO_ID, it.id) })
            })
        }
    }

    private val isBluetoothEnabled: Boolean
        get() {
            if (mBluetoothAdapter?.isEnabled == true) return true
            context?.run {
                bluetoothHandler = bluetoothHandler ?: BluetoothHandler(
                    this, requireActivity().activityResultRegistry,
                    this@BluupSlotFragment
                )
                bluetoothHandler?.requestPermissions(requireActivity())
            } ?: fragmentHandler.postDelayed({ isBluetoothEnabled }, 125)
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
        } catch (ignored: NullPointerException) { }
        disconnectService()
        bluetoothHandler?.unregisterResultContracts()
        super.onDestroy()
    }

    private fun onFragmentLoaded() {
        if (statusBar?.isShown != true) {
            fragmentHandler.postDelayed({
                when (noticeState) {
                    STATE.SCANNING, STATE.TIMEOUT -> {
                        if (isBluetoothEnabled) {
                            showScanningNotice()
                            selectBluetoothDevice()
                        }
                    }
                    STATE.CONNECT -> showConnectionNotice()
                    STATE.MISSING -> showDisconnectNotice()
                    else -> {}
                }
            }, TagMo.uiDelay.toLong())
            setBottomSheetHidden(false)
            onBottomSheetChanged(if (null == deviceAddress) SHEET.LOCKED else SHEET.MENU)
        }
    }

    override fun onResume() {
        isFragmentVisible = true
        super.onResume()
        onFragmentLoaded()
    }

    override fun onAmiiboClicked(amiibo: Amiibo?, position: Int) {
        getActiveAmiibo(amiibo, amiiboCard)
        onBottomSheetChanged(SHEET.AMIIBO)
        bottomSheet?.state = BottomSheetBehavior.STATE_EXPANDED
        if (amiibo is BluupTag) {
            val amiiboName = amiibo.bluupName ?: amiibo.name
            toolbar?.menu?.findItem(R.id.mnu_backup)?.isVisible = false
            toolbar?.setOnMenuItemClickListener { item: MenuItem ->
                if (item.itemId == R.id.mnu_activate) {
                    serviceBluup?.setActiveAmiibo(amiiboName, amiibo.bluupTail)
                    servicePuck?.setActiveSlot(position)
                    return@setOnMenuItemClickListener true
                } else if (item.itemId == R.id.mnu_delete) {
                    serviceBluup?.deleteAmiibo(amiiboName, amiibo.bluupTail)
                    bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
                    return@setOnMenuItemClickListener true
                }
                false
            }
        } else amiibo?.let {
            val amiiboName = it.bluupName ?: it.name
            toolbar?.menu?.findItem(R.id.mnu_backup)?.isVisible = true
            toolbar?.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.mnu_activate -> {
                        serviceBluup?.setActiveAmiibo(amiiboName, it.bluupTail)
                        servicePuck?.setActiveSlot(position)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.mnu_delete -> {
                        serviceBluup?.deleteAmiibo(amiiboName, it.bluupTail)
                        bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
                        return@setOnMenuItemClickListener true
                    }
                    R.id.mnu_backup -> {
                        serviceBluup?.downloadAmiibo(amiiboName, it.bluupTail)
                        bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
                        return@setOnMenuItemClickListener true
                    }
                    else -> false
                }
            }
        }
    }

    override fun onAmiiboImageClicked(amiibo: Amiibo?) {
        amiibo?.let {
            this.startActivity(Intent(requireContext(), ImageActivity::class.java)
                .putExtras(Bundle().apply { putLong(NFCIntent.EXTRA_AMIIBO_ID, it.id) })
            )
        }
    }

    override fun onPermissionsFailed() {
        this.mBluetoothAdapter = null
        setBottomSheetHidden(true)
        Toasty(requireActivity()).Long(R.string.fail_permissions)
    }

    override fun onAdapterMissing() {
        this.mBluetoothAdapter = null
        noticeState = STATE.MISSING
        setBottomSheetHidden(true)
        Toasty(requireActivity()).Long(R.string.fail_bluetooth_adapter)
    }

    override fun onAdapterRestricted() {
        delayedBluetoothEnable()
    }

    override fun onAdapterEnabled(adapter: BluetoothAdapter?) {
        this.mBluetoothAdapter = adapter
        setBottomSheetHidden(false)
        selectBluetoothDevice()
    }
}