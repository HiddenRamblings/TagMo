package com.hiddenramblings.tagmo.browser.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.SearchManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import com.hiddenramblings.tagmo.GlideApp
import com.hiddenramblings.tagmo.NFCIntent
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboFile
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.getAmiiboManager
import com.hiddenramblings.tagmo.amiibo.EliteTag
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.amiibo.tagdata.AmiiboData
import com.hiddenramblings.tagmo.amiibo.tagdata.TagDataEditor
import com.hiddenramblings.tagmo.browser.BrowserActivity
import com.hiddenramblings.tagmo.browser.BrowserSettings
import com.hiddenramblings.tagmo.browser.ImageActivity
import com.hiddenramblings.tagmo.browser.adapter.EliteBankAdapter
import com.hiddenramblings.tagmo.browser.adapter.WriteTagAdapter
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.hexcode.HexCodeViewer
import com.hiddenramblings.tagmo.nfctech.NfcActivity
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.hiddenramblings.tagmo.widget.Toasty
import com.shawnlin.numberpicker.NumberPicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import java.text.ParseException

class EliteBankFragment : Fragment(), EliteBankAdapter.OnAmiiboClickListener {
    private lateinit var prefs: Preferences
    private lateinit var rootLayout: CoordinatorLayout
    var eliteContent: RecyclerView? = null
        private set
    private var bankOptionsMenu: LinearLayout? = null
    private var switchMenuOptions: AppCompatToggleButton? = null
    private var writeBankLayout: LinearLayout? = null
    private var bankAdapter: EliteBankAdapter? = null
    private var amiiboFilesView: RecyclerView? = null
    private var writeTagAdapter: WriteTagAdapter? = null
    private var amiiboTile: CardView? = null
    private var amiiboCard: CardView? = null
    private var toolbar: Toolbar? = null
    private lateinit var amiiboTileTarget: CustomTarget<Bitmap?>
    private lateinit var amiiboCardTarget: CustomTarget<Bitmap?>
    private var bankStats: TextView? = null
    private lateinit var eliteBankCount: NumberPicker
    private var writeOpenBanks: AppCompatButton? = null
    private var writeSerials: SwitchCompat? = null
    private var eraseOpenBanks: AppCompatButton? = null
    private var securityOptions: LinearLayout? = null
    private var searchView: SearchView? = null
    private lateinit var settings: BrowserSettings
    var bottomSheet: BottomSheetBehavior<View>? = null
        private set
    private lateinit var keyManager: KeyManager
    private var amiibos: ArrayList<EliteTag?> = arrayListOf()
    private var clickedPosition = 0

    private var listener: RefreshListener? = null

    private enum class CLICKED {
        NOTHING, WRITE_DATA, EDIT_DATA, HEX_CODE, BANK_BACKUP, VERIFY_TAG, ERASE_BANK
    }

    private var status = CLICKED.NOTHING

    private enum class SHEET {
        LOCKED, AMIIBO, MENU, WRITE
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_elite_bank, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rootLayout = view as CoordinatorLayout
        val activity = requireActivity() as BrowserActivity
        prefs = Preferences(activity.applicationContext)
        keyManager = KeyManager(activity)
        eliteContent = rootLayout.findViewById(R.id.elite_content)
        if (prefs.softwareLayer())
            eliteContent?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        eliteContent?.setHasFixedSize(true)
        switchMenuOptions = rootLayout.findViewById(R.id.switch_menu_btn)
        bankOptionsMenu = rootLayout.findViewById(R.id.bank_options_menu)
        writeBankLayout = rootLayout.findViewById(R.id.write_list_layout)
        if (prefs.softwareLayer())
            writeBankLayout?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        amiiboFilesView = rootLayout.findViewById(R.id.amiibo_files_list)
        if (prefs.softwareLayer())
            amiiboFilesView?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        // amiiboFilesView!!.setHasFixedSize(true);
        securityOptions = rootLayout.findViewById(R.id.security_options)
        amiiboTile = rootLayout.findViewById(R.id.active_tile_layout)
        amiiboCard = rootLayout.findViewById(R.id.active_card_layout)
        toolbar = rootLayout.findViewById(R.id.toolbar)
        amiiboTileTarget = object : CustomTarget<Bitmap?>() {
            val imageAmiibo = amiiboTile?.findViewById<AppCompatImageView>(R.id.imageAmiibo)
            override fun onLoadFailed(errorDrawable: Drawable?) {
                imageAmiibo?.setImageResource(R.drawable.ic_no_image_60)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                imageAmiibo?.setImageResource(R.drawable.ic_no_image_60)
            }

            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                imageAmiibo?.maxHeight = Resources.getSystem().displayMetrics.heightPixels / 4
                imageAmiibo?.requestLayout()
                imageAmiibo?.setImageBitmap(resource)
            }
        }
        amiiboCardTarget = object : CustomTarget<Bitmap?>() {
            val imageAmiibo = amiiboCard?.findViewById<AppCompatImageView>(R.id.imageAmiibo)
            override fun onLoadFailed(errorDrawable: Drawable?) {
                imageAmiibo?.setImageResource(0)
                imageAmiibo?.isGone = true
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                imageAmiibo?.setImageResource(0)
                imageAmiibo?.isGone = true
            }

            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                imageAmiibo?.maxHeight = Resources.getSystem().displayMetrics.heightPixels / 4
                imageAmiibo?.requestLayout()
                imageAmiibo?.setImageBitmap(resource)
                imageAmiibo?.isVisible = true
            }
        }
        bankStats = rootLayout.findViewById(R.id.bank_stats)
        eliteBankCount = rootLayout.findViewById(R.id.number_picker)
        writeOpenBanks = rootLayout.findViewById(R.id.write_open_banks)
        writeSerials = rootLayout.findViewById(R.id.write_serial_fill)
        eraseOpenBanks = rootLayout.findViewById(R.id.erase_open_banks)
        settings = activity.settings ?: BrowserSettings().initialize()
        val toggle = rootLayout.findViewById<AppCompatImageView>(R.id.toggle)
        bottomSheet = BottomSheetBehavior.from(rootLayout.findViewById(R.id.bottom_sheet))
        bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheet?.addBottomSheetCallback(object : BottomSheetCallback() {
            override fun onStateChanged(view: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    if (writeBankLayout?.visibility == View.VISIBLE)
                        onBottomSheetChanged(SHEET.MENU)
                    toggle.setImageResource(R.drawable.ic_expand_less_white_24dp)
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    toggle.setImageResource(R.drawable.ic_expand_more_white_24dp)
                }
            }

            override fun onSlide(view: View, slideOffset: Float) {
                val mainLayout = rootLayout.findViewById<ViewGroup>(R.id.main_layout)
                if (mainLayout.bottom >= view.top) {
                    val bottomHeight: Int = (view.measuredHeight - bottomSheet!!.peekHeight)
                    mainLayout.setPadding(
                        0, 0, 0,
                        if (slideOffset > 0) (bottomHeight * slideOffset).toInt() else 0
                    )
                }
                if (slideOffset > 0) eliteContent!!.smoothScrollToPosition(clickedPosition)
            }
        })
        toggle.setOnClickListener {
            if (bottomSheet?.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheet?.setState(BottomSheetBehavior.STATE_EXPANDED)
            } else {
                bottomSheet?.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
        }
        toolbar?.inflateMenu(R.menu.bank_menu)
        if (settings.amiiboView == BrowserSettings.VIEW.IMAGE.value)
            eliteContent?.layoutManager = GridLayoutManager(activity, activity.columnCount)
        else eliteContent?.layoutManager = LinearLayoutManager(activity)
        bankAdapter = EliteBankAdapter(settings, this)
        eliteContent?.adapter = bankAdapter
        settings.addChangeListener(bankAdapter)
        eliteBankCount.setOnValueChangedListener { _, _, newVal ->
            writeOpenBanks?.text = getString(R.string.write_banks, newVal)
            eraseOpenBanks?.text = getString(R.string.erase_banks, newVal)
        }
        if (settings.amiiboView == BrowserSettings.VIEW.IMAGE.value)
            amiiboFilesView?.layoutManager = GridLayoutManager(activity, activity.columnCount)
        else
            amiiboFilesView?.layoutManager = LinearLayoutManager(activity)
        writeTagAdapter = WriteTagAdapter(settings)
        amiiboFilesView?.adapter = writeTagAdapter
        switchMenuOptions?.setOnClickListener {
            if (bankOptionsMenu?.isShown == true) {
                onBottomSheetChanged(SHEET.AMIIBO)
            } else {
                onBottomSheetChanged(SHEET.MENU)
            }
            bottomSheet?.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
        searchView = rootLayout.findViewById<SearchView>(R.id.amiibo_search).apply {
            (activity.getSystemService(Context.SEARCH_SERVICE) as SearchManager).run {
                setSearchableInfo(getSearchableInfo(activity.componentName))
            }
            isSubmitButtonEnabled = false
            setIconifiedByDefault(false)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
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
        writeOpenBanks?.setOnClickListener {
            settings.addChangeListener(writeTagAdapter)
            onBottomSheetChanged(SHEET.WRITE)
            searchView?.setQuery(settings.query, true)
            searchView?.clearFocus()
            eliteBankCount.value.let { count ->
                writeTagAdapter?.setListener(object : WriteTagAdapter.OnAmiiboClickListener {
                    override fun onAmiiboClicked(amiiboFile: AmiiboFile?) {}
                    override fun onAmiiboImageClicked(amiiboFile: AmiiboFile?) {}
                    override fun onAmiiboListClicked(amiiboList: ArrayList<AmiiboFile?>?) {
                        if (!amiiboList.isNullOrEmpty()) writeAmiiboFileCollection(amiiboList)
                    }
                    override fun onAmiiboDataClicked(serialList: ArrayList<AmiiboData?>?) {
                        if (!serialList.isNullOrEmpty()) writeAmiiboCollection(serialList)
                    }
                }, count, writeSerials?.isChecked ?: false)
            }
            bottomSheet?.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
        eraseOpenBanks?.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.elite_erase_confirm)
                .setPositiveButton(R.string.proceed) { _: DialogInterface?, _: Int ->
                    onOpenBanksActivity.launch(Intent(
                        requireActivity(), NfcActivity::class.java
                    ).apply {
                        putExtra(NFCIntent.EXTRA_SIGNATURE, prefs.eliteSignature())
                        action = NFCIntent.ACTION_ERASE_ALL_TAGS
                        putExtra(NFCIntent.EXTRA_BANK_COUNT, eliteBankCount.value)
                    })
                }
                .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .show()
        }
        rootLayout.findViewById<View>(R.id.edit_bank_count).setOnClickListener {
            if (prefs.eliteActiveBank() >= eliteBankCount.value) {
                Toasty(activity).Short(R.string.fail_active_oob)
                onBottomSheetChanged(SHEET.MENU)
                return@setOnClickListener
            }
            onOpenBanksActivity.launch(Intent(activity, NfcActivity::class.java).apply {
                putExtra(NFCIntent.EXTRA_SIGNATURE, prefs.eliteSignature())
                action = NFCIntent.ACTION_SET_BANK_COUNT
                putExtra(NFCIntent.EXTRA_BANK_COUNT, eliteBankCount.value)
            })
        }
        view.findViewById<View>(R.id.lock_elite).setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.lock_elite_warning)
                .setMessage(R.string.lock_elite_details)
                .setPositiveButton(R.string.accept) { dialog: DialogInterface, _: Int ->
                    val lock = Intent(requireContext(), NfcActivity::class.java)
                    lock.action = NFCIntent.ACTION_LOCK_AMIIBO
                    startActivity(lock)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null).show()
        }
        view.findViewById<View>(R.id.unlock_elite).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.unlock_elite_warning)
                .setMessage(R.string.prepare_unlock)
                .setPositiveButton(R.string.start) { dialog: DialogInterface, _: Int ->
                    startActivity(
                        Intent(requireContext(), NfcActivity::class.java)
                            .setAction(NFCIntent.ACTION_UNLOCK_UNIT)
                    )
                    dialog.dismiss()
                }.show()
        }
    }

    private fun updateEliteAdapter(amiiboList: ArrayList<String>?) {
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            var amiiboManager = settings.amiiboManager
            if (null == amiiboManager) {
                try {
                    amiiboManager = getAmiiboManager(requireContext().applicationContext)
                } catch (e: IOException) {
                    Debug.warn(e)
                    withContext(Dispatchers.Main) {
                        Toasty(requireContext()).Short(R.string.amiibo_info_parse_error)
                    }
                } catch (e: JSONException) {
                    Debug.warn(e)
                    withContext(Dispatchers.Main) {
                        Toasty(requireContext()).Short(R.string.amiibo_info_parse_error)
                    }
                } catch (e: ParseException) {
                    Debug.warn(e)
                    withContext(Dispatchers.Main) {
                        Toasty(requireContext()).Short(R.string.amiibo_info_parse_error)
                    }
                }
                val uiAmiiboManager = amiiboManager
                withContext(Dispatchers.Main) {
                    settings.amiiboManager = uiAmiiboManager
                    settings.notifyChanges()
                }
            }
            if (null == amiiboManager) return@launch
            withContext(Dispatchers.Main) {
                if (amiibos.isEmpty()) {
                    bankAdapter?.setAmiibos(amiibos)
                    amiiboList?.indices?.forEach {
                        amiibos.add(EliteTag(amiiboManager.amiibos[TagArray.hexToLong(amiiboList[it])]))
                        bankAdapter?.notifyItemInserted(it)
                    }
                } else {
                    amiiboList?.indices?.forEach {
                        val amiiboId = TagArray.hexToLong(amiiboList[it])
                        if (it >= amiibos.size) {
                            amiibos.add(EliteTag(amiiboManager.amiibos[TagArray.hexToLong(amiiboList[it])]))
                            bankAdapter?.notifyItemInserted(it)
                        } else if (null == amiibos[it] || amiibos[it]!!.index != it || amiiboId != amiibos[it]!!.id) {
                            amiibos[it] = EliteTag(amiiboManager.amiibos[amiiboId])
                            bankAdapter?.notifyItemChanged(it)
                        }
                    }
                    if (null != amiiboList && amiibos.size > amiiboList.size) {
                        val count = amiibos.size
                        val size = amiiboList.size
                        val shortList: ArrayList<EliteTag?> = arrayListOf()
                        for (x in 0 until size) {
                            shortList.add(amiibos[x])
                        }
                        amiibos = shortList
                        bankAdapter?.notifyItemRangeChanged(0, size)
                        bankAdapter?.notifyItemRangeRemoved(size, count - size)
                    }
                }
                listener?.onListRefreshed(amiibos)
            }
        }
    }

    private fun onBottomSheetChanged(sheet: SHEET) {
        bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
        when (sheet) {
            SHEET.LOCKED -> {
                amiiboCard?.isGone = true
                switchMenuOptions?.isGone = true
                bankOptionsMenu?.isGone = true
                securityOptions?.isVisible = true
                writeBankLayout?.isGone = true
                eliteContent?.requestLayout()
            }
            SHEET.AMIIBO -> {
                amiiboCard?.isVisible = true
                switchMenuOptions?.isVisible = true
                bankOptionsMenu?.isGone = true
                securityOptions?.isGone = true
                writeBankLayout?.isGone = true
                eliteContent?.requestLayout()
            }
            SHEET.MENU -> {
                amiiboCard?.isGone = true
                switchMenuOptions?.isVisible = true
                bankOptionsMenu?.isVisible = true
                securityOptions?.isVisible = true
                writeBankLayout?.isGone = true
                eliteContent?.requestLayout()
            }
            SHEET.WRITE -> {
                amiiboCard?.isGone = true
                switchMenuOptions?.isGone = true
                bankOptionsMenu?.isGone = true
                securityOptions?.isGone = true
                writeBankLayout?.isVisible = true
                eliteContent?.requestLayout()
            }
        }
    }

    private val onActivateActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != AppCompatActivity.RESULT_OK || null == result.data)
            return@registerForActivityResult
        if (NFCIntent.ACTION_NFC_SCANNED != result.data?.action)
            return@registerForActivityResult
        val activeBank = result.data?.getIntExtra(
            NFCIntent.EXTRA_ACTIVE_BANK, prefs.eliteActiveBank()
        ) ?: prefs.eliteActiveBank()
        bankAdapter?.notifyItemChanged(prefs.eliteActiveBank())
        bankAdapter?.notifyItemChanged(activeBank)
        prefs.eliteActiveBank(activeBank)
        amiibos[activeBank]?.let {
            updateAmiiboView(amiiboTile, null, it.id, activeBank)
        }
        val bankCount = prefs.eliteBankCount()
        bankStats?.text = getString(
            R.string.bank_stats, getValueForPosition(eliteBankCount, activeBank), bankCount
        )
        writeOpenBanks?.text = getString(R.string.write_banks, bankCount)
        eraseOpenBanks?.text = getString(R.string.erase_banks, bankCount)
    }
    private val onUpdateTagResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != AppCompatActivity.RESULT_OK) return@registerForActivityResult
        result.data?.let { intent ->
        if (NFCIntent.ACTION_NFC_SCANNED != intent.action
            && NFCIntent.ACTION_EDIT_COMPLETE != intent.action) return@registerForActivityResult
            if (intent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
                clickedPosition = intent.getIntExtra(
                    NFCIntent.EXTRA_CURRENT_BANK, clickedPosition
                )
            }
            var tagData = if (amiibos.size > clickedPosition)
                amiibos[clickedPosition]?.data else null
            if (intent.hasExtra(NFCIntent.EXTRA_TAG_DATA)) {
                tagData = intent.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA)
                if (amiibos.size > clickedPosition)
                    amiibos[clickedPosition]?.let { it.data = tagData }
            }
            var activeBank = prefs.eliteActiveBank()
            if (intent.hasExtra(NFCIntent.EXTRA_ACTIVE_BANK)) {
                activeBank = intent.getIntExtra(NFCIntent.EXTRA_ACTIVE_BANK, activeBank)
                prefs.eliteActiveBank(activeBank)
            }
            setRefreshListener(object : RefreshListener {
                override fun onListRefreshed(amiibos: ArrayList<EliteTag?>) {
                    amiibos[activeBank]?.let {
                        updateAmiiboView(amiiboTile, null, it.id, activeBank)
                    }
                    listener = null
                }
            })
            if (intent.hasExtra(NFCIntent.EXTRA_AMIIBO_LIST)) {
                updateEliteAdapter(intent.getStringArrayListExtra(NFCIntent.EXTRA_AMIIBO_LIST))
            }
            updateAmiiboView(amiiboCard, tagData, -1, clickedPosition)
            bottomSheet?.state = BottomSheetBehavior.STATE_EXPANDED
            if (status == CLICKED.ERASE_BANK) {
                status = CLICKED.NOTHING
                onBottomSheetChanged(SHEET.MENU)
                amiibos[clickedPosition] = null
            }
        }
    }
    private val onScanTagResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != AppCompatActivity.RESULT_OK || null == result.data) return@registerForActivityResult
        if (NFCIntent.ACTION_NFC_SCANNED != result.data!!.action) return@registerForActivityResult
        val tagData = result.data?.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA)
        clickedPosition = result.data!!.getIntExtra(NFCIntent.EXTRA_CURRENT_BANK, clickedPosition)
        val args = Bundle()
        args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData)
        if (amiibos.size > clickedPosition && null != amiibos[clickedPosition])
            amiibos[clickedPosition]!!.data = tagData
        when (status) {
            CLICKED.NOTHING -> {}
            CLICKED.WRITE_DATA -> {
                onUpdateTagResult.launch(Intent(requireContext(), NfcActivity::class.java).apply {
                    putExtra(NFCIntent.EXTRA_SIGNATURE, prefs.eliteSignature())
                    action = NFCIntent.ACTION_WRITE_TAG_FULL
                    putExtra(NFCIntent.EXTRA_CURRENT_BANK, clickedPosition)
                    putExtras(args)
                })
            }
            CLICKED.EDIT_DATA -> onUpdateTagResult.launch(
                Intent(requireContext(), TagDataEditor::class.java).putExtras(args)
            )
            CLICKED.HEX_CODE -> onUpdateTagResult.launch(
                Intent(requireContext(), HexCodeViewer::class.java).putExtras(args)
            )
            CLICKED.BANK_BACKUP -> displayBackupDialog(tagData)
            CLICKED.VERIFY_TAG -> try {
                TagArray.validateData(tagData)
                Toasty(requireContext()).Dialog(R.string.validation_success)
            } catch (e: Exception) {
                Toasty(requireContext()).Dialog(e.message)
            }
            else -> {}
        }
        status = CLICKED.NOTHING
        updateAmiiboView(amiiboCard, tagData, -1, clickedPosition)
        bankAdapter?.notifyItemChanged(clickedPosition)
    }

    private fun scanAmiiboBank(current_bank: Int) {
        onScanTagResult.launch(Intent(requireContext(), NfcActivity::class.java).apply {
            putExtra(NFCIntent.EXTRA_SIGNATURE, prefs.eliteSignature())
            action = NFCIntent.ACTION_SCAN_TAG
            putExtra(NFCIntent.EXTRA_CURRENT_BANK, current_bank)
        })
    }

    private fun scanAmiiboTag(position: Int) {
        onUpdateTagResult.launch(Intent(requireContext(), NfcActivity::class.java).apply {
            putExtra(NFCIntent.EXTRA_SIGNATURE, prefs.eliteSignature())
            action = NFCIntent.ACTION_SCAN_TAG
            putExtra(NFCIntent.EXTRA_CURRENT_BANK, position)
        })
    }

    private fun writeAmiiboFile(amiiboFile: AmiiboFile, position: Int) {
        val args = Bundle()
        if (prefs.isDocumentStorage) {
            try {
                val data = amiiboFile.data ?: amiiboFile.docUri?.let {
                    TagArray.getValidatedDocument(keyManager, it)
                }
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, data)
            } catch (e: Exception) {
                Debug.warn(e)
            }
        } else {
            try {
                val data = amiiboFile.data ?: amiiboFile.filePath?.let {
                    TagArray.getValidatedFile(keyManager, it)
                }
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, data)
            } catch (e: Exception) {
                Debug.warn(e)
            }
        }
        onUpdateTagResult.launch(Intent(requireContext(), NfcActivity::class.java).apply {
            putExtra(NFCIntent.EXTRA_SIGNATURE, prefs.eliteSignature())
            action = NFCIntent.ACTION_WRITE_TAG_FULL
            putExtra(NFCIntent.EXTRA_CURRENT_BANK, position)
            putExtras(args)
        })
    }

    private fun displayWriteDialog(position: Int) {
        settings.addChangeListener(writeTagAdapter)
        onBottomSheetChanged(SHEET.WRITE)
        searchView?.setQuery(settings.query, true)
        searchView?.clearFocus()
        writeTagAdapter?.setListener(object : WriteTagAdapter.OnAmiiboClickListener {
            override fun onAmiiboClicked(amiiboFile: AmiiboFile?) {
                amiiboFile?.let {
                    onBottomSheetChanged(SHEET.AMIIBO)
                    writeAmiiboFile(it, position)
                    settings.removeChangeListener(writeTagAdapter)
                }
            }

            override fun onAmiiboImageClicked(amiiboFile: AmiiboFile?) {
                handleImageClicked(amiiboFile)
            }

            override fun onAmiiboListClicked(amiiboList: ArrayList<AmiiboFile?>?) {}
            override fun onAmiiboDataClicked(serialList: ArrayList<AmiiboData?>?) {}
        })
        bottomSheet?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun displayBackupDialog(tagData: ByteArray?) {
        val view = layoutInflater.inflate(R.layout.dialog_save_item, null)
        val dialog = AlertDialog.Builder(requireContext())
        val input = view.findViewById<EditText>(R.id.save_item_entry)
        input.setText(TagArray.decipherFilename(settings.amiiboManager, tagData, true))
        val backupDialog: Dialog = dialog.setView(view).create()
        view.findViewById<View>(R.id.button_save).setOnClickListener {
            try {
                val activity = requireActivity() as BrowserActivity
                val fileName = TagArray.writeBytesWithName(
                    activity, input.text, tagData
                )
                fileName?.let {
                    Toasty(requireContext()).Long(
                        getString(R.string.wrote_file, it)
                    )
                    activity.loadAmiiboBackground()
                } ?: Toasty(requireContext()).Long(
                    getString(R.string.fail_save_file)
                )
            } catch (e: Exception) {
                e.message?.let { Toasty(requireActivity()).Short(it) }
            }
            backupDialog.dismiss()
        }
        view.findViewById<View>(R.id.button_cancel)
            .setOnClickListener { backupDialog.dismiss() }
        backupDialog.show()
    }

    private fun setAmiiboInfoText(textView: TextView, text: CharSequence?, hasTagInfo: Boolean) {
        textView.isGone = hasTagInfo
        if (!hasTagInfo) {
            if (!text.isNullOrEmpty()) {
                textView.text = text
                textView.isEnabled = true
            } else {
                textView.text = getString(R.string.unknown)
                textView.isEnabled = false
            }
        }
    }

    private fun getAmiiboToolbar(tagData: ByteArray?, current_bank: Int) {
        toolbar!!.setOnMenuItemClickListener { item: MenuItem ->
            val notice = Toasty(requireActivity())
            val scan = Intent(requireContext(), NfcActivity::class.java)
                .putExtra(NFCIntent.EXTRA_SIGNATURE, prefs.eliteSignature())
                .putExtra(NFCIntent.EXTRA_CURRENT_BANK, current_bank)
            when (item.itemId) {
                R.id.mnu_activate -> {
                    scan.action = NFCIntent.ACTION_ACTIVATE_BANK
                    onActivateActivity.launch(scan)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_replace -> {
                    displayWriteDialog(current_bank)
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_write -> {
                    if (tagData?.isNotEmpty() == true) {
                        scan.action = NFCIntent.ACTION_WRITE_TAG_FULL
                        scan.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData)
                        onUpdateTagResult.launch(scan)
                    } else {
                        status = CLICKED.WRITE_DATA
                        scanAmiiboBank(current_bank)
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_erase_bank -> {
                    if (prefs.eliteActiveBank() == current_bank) {
                        notice.Short(R.string.erase_active)
                    } else {
                        scan.action = NFCIntent.ACTION_ERASE_BANK
                        onUpdateTagResult.launch(scan)
                        status = CLICKED.ERASE_BANK
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_edit -> {
                    if (tagData?.isNotEmpty() == true) {
                        onUpdateTagResult.launch(Intent(
                            requireContext(), TagDataEditor::class.java
                        ).putExtra(NFCIntent.EXTRA_TAG_DATA, tagData))
                    } else {
                        status = CLICKED.EDIT_DATA
                        scanAmiiboBank(current_bank)
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_view_hex -> {
                    if (tagData?.isNotEmpty() == true) {
                        startActivity(Intent(requireContext(), HexCodeViewer::class.java)
                            .putExtra(NFCIntent.EXTRA_TAG_DATA, tagData)
                        )
                    } else {
                        status = CLICKED.HEX_CODE
                        scanAmiiboBank(current_bank)
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_backup -> {
                    if (tagData?.isNotEmpty() == true) {
                        displayBackupDialog(tagData)
                    } else {
                        status = CLICKED.BANK_BACKUP
                        scanAmiiboBank(current_bank)
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_validate -> {
                    if (tagData?.isNotEmpty() == true) {
                        try {
                            TagArray.validateData(tagData)
                            notice.Dialog(R.string.validation_success)
                        } catch (e: Exception) {
                            notice.Dialog(e.message)
                        }
                    } else {
                        status = CLICKED.VERIFY_TAG
                        scanAmiiboBank(current_bank)
                    }
                    return@setOnMenuItemClickListener true
                }
                else -> false
            }
        }
    }

    private fun updateAmiiboView(
        amiiboView: View?,
        tagData: ByteArray?,
        amiiboId: Long,
        current_bank: Int
    ) {
        if (null == amiiboView) return
        val amiiboInfo = rootLayout.findViewById<View>(R.id.amiiboInfo)
        val txtError = rootLayout.findViewById<TextView>(R.id.txtError)
        val txtName = amiiboView.findViewById<TextView>(R.id.txtName)
        val txtBank = amiiboView.findViewById<TextView>(R.id.txtBank)
        val txtTagId = amiiboView.findViewById<TextView>(R.id.txtTagId)
        val txtAmiiboSeries = amiiboView.findViewById<TextView>(R.id.txtAmiiboSeries)
        val txtAmiiboType = amiiboView.findViewById<TextView>(R.id.txtAmiiboType)
        val txtGameSeries = amiiboView.findViewById<TextView>(R.id.txtGameSeries)
        val imageAmiibo = amiiboView.findViewById<AppCompatImageView>(R.id.imageAmiibo)
        if (amiiboView === amiiboCard) getAmiiboToolbar(tagData, current_bank)
        var tagInfo: String? = null
        var amiiboHexId: String? = ""
        var amiiboName = ""
        var amiiboSeries = ""
        var amiiboType = ""
        var gameSeries = ""
        var amiiboImageUrl: String? = null
        var amiibo = if (amiibos.size > current_bank) amiibos[current_bank] else null
        val amiiboLongId = when {
            null != amiibo && amiibo.id > 0L -> {
                amiibo.id
            }
            null != tagData && tagData.isNotEmpty() -> {
                try {
                    Amiibo.dataToId(tagData)
                } catch (e: Exception) {
                    Debug.info(e)
                    amiiboId
                }
            }
            else -> {
                amiiboId
            }
        }
        val amiiboManager = settings.amiiboManager
        when (amiiboLongId) {
            -1L -> { tagInfo = getString(R.string.read_error) }
            0L -> { tagInfo = getString(R.string.blank_tag) }
            else -> {
                amiiboManager?.let {
                    amiibo = EliteTag(
                        it.amiibos[amiiboLongId]
                            ?: Amiibo(it, amiiboLongId, null, null)
                    )
                }
            }
        }
        amiibo?.let {
            amiiboView.isVisible = true
            amiiboHexId = Amiibo.idToHex(it.id)
            amiiboImageUrl = it.imageUrl
            it.name?.let { name -> amiiboName = name }
            it.amiiboSeries?.let { series -> amiiboSeries = series.name }
            it.amiiboType?.let { type -> amiiboType = type.name }
            it.gameSeries?.let { series -> gameSeries = series.name }
        } ?: tagInfo ?: amiiboLongId.let {
            tagInfo = "ID: " + Amiibo.idToHex(it)
            amiiboImageUrl = Amiibo.getImageUrl(it)
        }
        val hasTagInfo = null != tagInfo
        amiiboInfo.isGone = hasTagInfo
        if (hasTagInfo) {
            setAmiiboInfoText(txtError, tagInfo, false)
        } else {
            txtError.isGone = true
        }
        txtBank?.let {
            setAmiiboInfoText(it, getString(
                R.string.bank_number, getValueForPosition(eliteBankCount, current_bank)
            ), hasTagInfo)
        }
        setAmiiboInfoText(txtName, amiiboName, hasTagInfo)
        setAmiiboInfoText(txtTagId, amiiboHexId, hasTagInfo)
        setAmiiboInfoText(txtAmiiboSeries, amiiboSeries, hasTagInfo)
        setAmiiboInfoText(txtAmiiboType, amiiboType, hasTagInfo)
        setAmiiboInfoText(txtGameSeries, gameSeries, hasTagInfo)
        if (amiiboView !== amiiboTile || null != amiiboImageUrl) {
            imageAmiibo?.let {
                if (!amiiboImageUrl.isNullOrEmpty()) {
                    if (amiiboView === amiiboCard) {
                        it.setImageResource(0)
                        it.isGone = true
                    }
                    GlideApp.with(it).clear(it)
                    GlideApp.with(it).asBitmap().load(amiiboImageUrl).into(
                        if (amiiboView === amiiboCard) amiiboCardTarget else amiiboTileTarget
                    )
                }
                it.setOnClickListener {
                    if (amiiboLongId == -1L) return@setOnClickListener
                    startActivity(Intent(requireContext(), ImageActivity::class.java)
                        .putExtras(Bundle().apply { putLong(NFCIntent.EXTRA_AMIIBO_ID, amiiboLongId) })
                    )
                }
            }
        }
    }

    private val onOpenBanksActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != AppCompatActivity.RESULT_OK) return@registerForActivityResult
        result.data?.let { intent ->
            if (NFCIntent.ACTION_NFC_SCANNED != intent.action) return@registerForActivityResult
            val bankCount = intent.getIntExtra(NFCIntent.EXTRA_BANK_COUNT, prefs.eliteBankCount())
            prefs.eliteBankCount(bankCount)
            eliteBankCount.value = bankCount
            val activeBank = prefs.eliteActiveBank()
            setRefreshListener(object : RefreshListener {
                override fun onListRefreshed(amiibos: ArrayList<EliteTag?>) {
                    amiibos[activeBank]?.let {
                        updateAmiiboView(amiiboTile, null, it.id, activeBank)
                    }
                    listener = null
                }
            })
            updateEliteAdapter(intent.getStringArrayListExtra(NFCIntent.EXTRA_AMIIBO_LIST))

            bankStats?.text = getString(R.string.bank_stats, getValueForPosition(
                eliteBankCount, prefs.eliteActiveBank()
            ), bankCount)
            writeOpenBanks?.text = getString(R.string.write_banks, bankCount)
            eraseOpenBanks?.text = getString(R.string.erase_banks, bankCount)
        }
    }

    private fun writeAmiiboCollection(bytesList: ArrayList<AmiiboData?>) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.elite_write_confirm)
            .setPositiveButton(R.string.proceed) { dialog: DialogInterface, _: Int ->
                onOpenBanksActivity.launch(Intent(requireContext(), NfcActivity::class.java).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(NFCIntent.EXTRA_SIGNATURE, prefs.eliteSignature())
                    action = NFCIntent.ACTION_WRITE_ALL_TAGS
                    putExtra(NFCIntent.EXTRA_BANK_COUNT, eliteBankCount.value)
                    putExtra(NFCIntent.EXTRA_AMIIBO_BYTES, bytesList)
                })
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

    private fun writeAmiiboFileCollection(amiiboList: ArrayList<AmiiboFile?>) {
        val bytesList: ArrayList<AmiiboData?> = arrayListOf()
        amiiboList.indices.forEach {
            amiiboList[it]?.let { amiiboFile ->
                if (prefs.isDocumentStorage) {
                    try {
                        val data = amiiboFile.data ?: amiiboFile.docUri?.let { doc ->
                            TagArray.getValidatedDocument(keyManager, doc)
                        }
                        data?.let { bytesList.add(AmiiboData(data)) }
                    } catch (e: Exception) {
                        Debug.warn(e)
                    }
                } else {
                    try {
                        val data = amiiboFile.data ?: amiiboFile.filePath?.let { file ->
                            TagArray.getValidatedFile(keyManager, file)
                        }
                        data?.let { bytesList.add(AmiiboData(data)) }
                    } catch (e: Exception) {
                        Debug.warn(e)
                    }
                }
            }
        }
        writeAmiiboCollection(bytesList)
    }

    private fun getValueForPosition(picker: NumberPicker, value: Int): Int {
        return value + picker.minValue
    }

    fun onHardwareLoaded(extras: Bundle) {
        try {
            val bankCount = extras.getInt(
                NFCIntent.EXTRA_BANK_COUNT, prefs.eliteBankCount()
            )
            val activeBank = extras.getInt(
                NFCIntent.EXTRA_ACTIVE_BANK, prefs.eliteActiveBank()
            )
            (rootLayout.findViewById<View>(R.id.hardware_info) as TextView).text = getString(
                R.string.elite_signature, extras.getString(NFCIntent.EXTRA_SIGNATURE)
            )
            eliteBankCount.value = bankCount
            setRefreshListener(object : RefreshListener {
                override fun onListRefreshed(amiibos: ArrayList<EliteTag?>) {
                    amiibos[activeBank]?.id?.let {
                        onBottomSheetChanged(SHEET.AMIIBO)
                        updateAmiiboView(amiiboTile, null, it, activeBank)
                        updateAmiiboView(amiiboCard, null, it, activeBank)
                        bottomSheet?.state = BottomSheetBehavior.STATE_EXPANDED
                    } ?: onBottomSheetChanged(SHEET.MENU)
                    listener = null
                }
            })
            updateEliteAdapter(extras.getStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST))
            bankStats?.text = getString(
                R.string.bank_stats,
                getValueForPosition(eliteBankCount, activeBank), bankCount
            )
            writeOpenBanks?.text = getString(R.string.write_banks, bankCount)
            eraseOpenBanks?.text = getString(R.string.erase_banks, bankCount)

        } catch (ignored: Exception) {
            if (amiibos.isEmpty()) onBottomSheetChanged(SHEET.LOCKED)
        }
    }

    private fun handleImageClicked(amiibo: Amiibo?) {
        amiibo?.let {
            this.startActivity(Intent(requireContext(), ImageActivity::class.java)
                .putExtras(Bundle().apply {
                    putLong(NFCIntent.EXTRA_AMIIBO_ID, it.id)
                })
            )
        }
    }

    private fun handleImageClicked(amiiboFile: AmiiboFile?) {
        amiiboFile?.let {
            this.startActivity(Intent(requireContext(), ImageActivity::class.java)
                .putExtras(Bundle().apply {
                    putLong(NFCIntent.EXTRA_AMIIBO_ID, it.id)
                })
            )
        }
    }

    override fun onAmiiboClicked(amiibo: EliteTag?, position: Int) {
        if (null == amiibo?.manager) {
            displayWriteDialog(position)
            return
        }
        clickedPosition = position
        status = CLICKED.NOTHING
        onBottomSheetChanged(SHEET.AMIIBO)
        when {
            null != amiibo.data && amiibo.index == position -> {
                updateAmiiboView(amiiboCard, amiibo.data, -1, position)
            }
            amiibo.id != 0L -> {
                updateAmiiboView(amiiboCard, null, amiibo.id, position)
            }
            else -> { scanAmiiboTag(position) }
        }
        bottomSheet?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onAmiiboImageClicked(amiibo: EliteTag?, position: Int) {
        handleImageClicked(amiibo)
    }

    override fun onAmiiboLongClicked(amiibo: EliteTag?, position: Int): Boolean {
        if (null != amiibo) scanAmiiboTag(position) else displayWriteDialog(position)
        return true
    }

    private fun setRefreshListener(listener: RefreshListener?) {
        this.listener = listener
    }

    interface RefreshListener {
        fun onListRefreshed(amiibos: ArrayList<EliteTag?>)
    }
}