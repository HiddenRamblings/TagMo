package com.hiddenramblings.tagmo.browser.fragment

import android.app.Dialog
import android.app.SearchManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatToggleButton
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
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
import com.hiddenramblings.tagmo.TagMo
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
    private val prefs: Preferences by lazy { Preferences(TagMo.appContext) }
    private val keyManager: KeyManager by lazy { KeyManager(TagMo.appContext) }

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
    private var amiibos: ArrayList<EliteTag?> = arrayListOf()
    private var clickedPosition = 0

    private var refreshListener: RefreshListener? = null

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

        val activity = requireActivity() as BrowserActivity

        settings = activity.settings ?: BrowserSettings().initialize()

        eliteContent = view.findViewById<RecyclerView>(R.id.elite_content).apply {
            if (prefs.softwareLayer()) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            // setHasFixedSize(true)
            layoutManager = if (settings.amiiboView == BrowserSettings.VIEW.IMAGE.value)
                GridLayoutManager(activity, activity.columnCount)
            else
                LinearLayoutManager(activity)
            bankAdapter = EliteBankAdapter(settings, this@EliteBankFragment).also {
                settings.addChangeListener(it)
                adapter = it
            }
        }

        switchMenuOptions = view.findViewById(R.id.switch_menu_btn)
        bankOptionsMenu = view.findViewById(R.id.bank_options_menu)

        writeBankLayout = view.findViewById<LinearLayout>(R.id.write_list_banks).apply {
            if (prefs.softwareLayer()) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        amiiboFilesView = view.findViewById<RecyclerView>(R.id.amiibo_files_list).apply {
            if (prefs.softwareLayer()) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            setHasFixedSize(true)
            layoutManager = if (settings.amiiboView == BrowserSettings.VIEW.IMAGE.value)
                GridLayoutManager(activity, activity.columnCount)
            else
                LinearLayoutManager(activity)
            writeTagAdapter = WriteTagAdapter(settings).also { adapter = it }
        }

        securityOptions = view.findViewById(R.id.security_options)
        val bitmapHeight = Resources.getSystem().displayMetrics.heightPixels / 4
        amiiboTile = view.findViewById<CardView>(R.id.active_tile_layout).apply {
            with (findViewById<AppCompatImageView>(R.id.imageAmiibo)) {
                amiiboTileTarget = object : CustomTarget<Bitmap?>() {
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        setImageResource(R.drawable.ic_no_image_60)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        setImageResource(R.drawable.ic_no_image_60)
                    }

                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                        maxHeight = bitmapHeight
                        requestLayout()
                        setImageBitmap(resource)
                    }
                }
            }
        }
        amiiboCard = view.findViewById<CardView>(R.id.active_card_layout).apply {
            findViewById<View>(R.id.txtError)?.isGone = true
            findViewById<View>(R.id.txtPath)?.isGone = true
            with (findViewById<AppCompatImageView>(R.id.imageAmiibo)) {
                amiiboCardTarget = object : CustomTarget<Bitmap?>() {
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        setImageResource(0)
                        isGone = true
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        setImageResource(0)
                        isGone = true
                    }

                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                        maxHeight = bitmapHeight
                        requestLayout()
                        setImageBitmap(resource)
                        isVisible = true
                    }
                }
            }
        }
        toolbar = view.findViewById(R.id.toolbar)

        bankStats = view.findViewById(R.id.bank_stats)

        writeOpenBanks = view.findViewById<AppCompatButton>(R.id.write_open_banks).apply {
            setOnClickListener {
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
                        override fun onAmiiboDataClicked(amiiboFile: AmiiboFile?, count: Int) {
                            amiiboFile?.let {
                                writeAmiiboCollection(it.withRandomSerials(keyManager, count))
                            }
                        }
                    }, count, writeSerials?.isChecked ?: false)
                }
                bottomSheet?.setState(BottomSheetBehavior.STATE_EXPANDED)
            }
        }

        writeSerials = view.findViewById(R.id.write_serial_fill)

        eraseOpenBanks = view.findViewById<AppCompatButton>(R.id.erase_open_banks).apply {
            setOnClickListener {
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
        }

        eliteBankCount = view.findViewById<NumberPicker>(R.id.number_picker_bank).apply {
            if (prefs.softwareLayer()) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            setOnValueChangedListener { _, _, newVal ->
                updateNumberedText(newVal)
            }
        }

        val toggle = view.findViewById<AppCompatImageView>(R.id.toggle)
        bottomSheet = BottomSheetBehavior.from(view.findViewById(R.id.bottom_sheet_bank)).apply {
            state = BottomSheetBehavior.STATE_COLLAPSED
            val mainLayout = view.findViewById<ViewGroup>(R.id.main_layout)
            var slideHeight = 0F
            addBottomSheetCallback(object : BottomSheetCallback() {
                override fun onStateChanged(view: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        if (writeBankLayout?.visibility == View.VISIBLE)
                            onBottomSheetChanged(SHEET.MENU)
                        toggle.setImageResource(R.drawable.ic_expand_less_24dp)
                        mainLayout.setPadding(0, 0, 0, 0)
                    } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                        toggle.setImageResource(R.drawable.ic_expand_more_24dp)
                        eliteContent?.smoothScrollToPosition(clickedPosition)
                        val bottomHeight: Int = (view.measuredHeight - peekHeight)
                        mainLayout.setPadding(0, 0, 0, if (slideHeight > 0)
                            (bottomHeight * slideHeight).toInt() else 0
                        )
                    }
                }

                override fun onSlide(view: View, slideOffset: Float) { slideHeight = slideOffset }
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
        toggle.setImageResource(R.drawable.ic_expand_more_24dp)
        toolbar?.inflateMenu(R.menu.bank_menu)

        switchMenuOptions?.setOnClickListener {
            if (bankOptionsMenu?.isShown == true) {
                onBottomSheetChanged(SHEET.AMIIBO)
            } else {
                onBottomSheetChanged(SHEET.MENU)
            }
            bottomSheet?.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
        searchView = view.findViewById<SearchView>(R.id.amiibo_search).apply {
            with (activity.getSystemService(Context.SEARCH_SERVICE) as SearchManager) {
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

        view.findViewById<View>(R.id.edit_bank_count).setOnClickListener {
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
        onBottomSheetChanged(SHEET.LOCKED)
    }

    private fun updateNumberedText(number: Int) {
        writeOpenBanks?.text = getString(R.string.write_banks, number)
        eraseOpenBanks?.text = getString(R.string.erase_banks, number)
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
            if (amiibos.isEmpty()) {
                bankAdapter?.setAmiibos(amiibos)
                amiiboList?.forEachIndexed { i, amiibo ->
                    amiibos.add(EliteTag(amiiboManager.amiibos[TagArray.hexToLong(amiibo)]))
                    bankAdapter?.notifyItemInserted(i)
                }
            } else {
                amiiboList?.forEachIndexed { i, amiibo ->
                    val amiiboId = TagArray.hexToLong(amiibo)
                    if (i >= amiibos.size) {
                        amiibos.add(EliteTag(amiiboManager.amiibos[TagArray.hexToLong(amiibo)]))
                        bankAdapter?.notifyItemInserted(i)
                    } else if (null == amiibos[i] || i != amiibos[i]?.index || amiiboId != amiibos[i]?.id) {
                        amiibos[i] = EliteTag(amiiboManager.amiibos[amiiboId])
                        bankAdapter?.notifyItemChanged(i)
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
            withContext(Dispatchers.Main) {
                refreshListener?.onListRefreshed(amiibos)
            }
        }
    }

    private fun onBottomSheetChanged(sheet: SHEET) {
        bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheet?.isFitToContents = true
        when (sheet) {
            SHEET.LOCKED -> {
                amiiboCard?.isGone = true
                switchMenuOptions?.isGone = true
                bankOptionsMenu?.isGone = true
                securityOptions?.isVisible = true
                writeBankLayout?.isGone = true
            }
            SHEET.AMIIBO -> {
                amiiboCard?.isVisible = true
                switchMenuOptions?.isVisible = true
                bankOptionsMenu?.isGone = true
                securityOptions?.isGone = true
                writeBankLayout?.isGone = true
            }
            SHEET.MENU -> {
                amiiboCard?.isGone = true
                switchMenuOptions?.isVisible = true
                bankOptionsMenu?.isVisible = true
                securityOptions?.isVisible = true
                writeBankLayout?.isGone = true
            }
            SHEET.WRITE -> {
                bottomSheet?.isFitToContents = false
                amiiboCard?.isGone = true
                switchMenuOptions?.isGone = true
                bankOptionsMenu?.isGone = true
                securityOptions?.isGone = true
                writeBankLayout?.isVisible = true
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
        bankAdapter?.let {
            it.notifyItemChanged(prefs.eliteActiveBank())
            it.notifyItemChanged(activeBank)
        }
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
            if (intent.hasExtra(NFCIntent.EXTRA_CURRENT_BANK))
                clickedPosition = intent.getIntExtra(NFCIntent.EXTRA_CURRENT_BANK, clickedPosition)
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
                    removeRefreshListener()
                }
            })
            if (intent.hasExtra(NFCIntent.EXTRA_AMIIBO_LIST)) {
                updateEliteAdapter(intent.getStringArrayListExtra(NFCIntent.EXTRA_AMIIBO_LIST))
            }
            updateAmiiboView(amiiboCard, tagData, -1, clickedPosition)
            if (status == CLICKED.ERASE_BANK) {
                status = CLICKED.NOTHING
                onBottomSheetChanged(SHEET.MENU)
                amiibos[clickedPosition] = null
            } else {
                bottomSheet?.state = BottomSheetBehavior.STATE_EXPANDED
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
        if (amiibos.size > clickedPosition) amiibos[clickedPosition]?.let { it.data = tagData }
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
            } catch (e: Exception) { Toasty(requireContext()).Dialog(e.message) }
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
            } catch (e: Exception) { Debug.warn(e) }
        } else {
            try {
                val data = amiiboFile.data ?: amiiboFile.filePath?.let {
                    TagArray.getValidatedFile(keyManager, it)
                }
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, data)
            } catch (e: Exception) { Debug.warn(e) }
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
            override fun onAmiiboDataClicked(amiiboFile: AmiiboFile?, count: Int) {}
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
                fileName?.let { name ->
                    Toasty(requireContext()).Long(
                        getString(R.string.wrote_file, name)
                    )
                    activity.loadAmiiboBackground()
                } ?: Toasty(requireContext()).Long(getString(R.string.fail_save_file))
            } catch (e: Exception) { Toasty(requireActivity()).Short(e.message) }
            backupDialog.dismiss()
        }
        view.findViewById<View>(R.id.button_cancel).setOnClickListener { backupDialog.dismiss() }
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
        toolbar?.setOnMenuItemClickListener { item: MenuItem ->
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
                        } catch (e: Exception) { notice.Dialog(e.message) }
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
        amiiboView: View?, tagData: ByteArray?, amiiboId: Long, current_bank: Int
    ) {
        if (null == amiiboView) return
        val amiiboInfo = requireView().findViewById<View>(R.id.amiiboInfo)
        val txtError = requireView().findViewById<TextView>(R.id.txtError)
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
                    removeRefreshListener()
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
        settings.removeChangeListener(writeTagAdapter)
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
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                onBottomSheetChanged(SHEET.MENU)
                dialog.dismiss()
            }
            .show()
    }

    private fun writeAmiiboFileCollection(amiiboList: ArrayList<AmiiboFile?>) {
        val bytesList: ArrayList<AmiiboData?> = arrayListOf()
        amiiboList.forEach {
            it?.let { amiiboFile ->
                if (prefs.isDocumentStorage) {
                    try {
                        val data = amiiboFile.data ?: amiiboFile.docUri?.let { doc ->
                            TagArray.getValidatedDocument(keyManager, doc)
                        }
                        data?.let { bytesList.add(AmiiboData(data)) }
                    } catch (e: Exception) { Debug.warn(e) }
                } else {
                    try {
                        val data = amiiboFile.data ?: amiiboFile.filePath?.let { file ->
                            TagArray.getValidatedFile(keyManager, file)
                        }
                        data?.let { bytesList.add(AmiiboData(data)) }
                    } catch (e: Exception) { Debug.warn(e) }
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
            requireView().findViewById<TextView>(R.id.hardware_info).text = getString(
                R.string.elite_signature, extras.getString(NFCIntent.EXTRA_SIGNATURE)
            )
            eliteBankCount.value = bankCount
            setRefreshListener(object : RefreshListener {
                override fun onListRefreshed(amiibos: ArrayList<EliteTag?>) {
                    amiibos[activeBank]?.id?.let {
                        onBottomSheetChanged(SHEET.AMIIBO)
                        updateAmiiboView(amiiboTile, null, it, activeBank)
                        updateAmiiboView(amiiboCard, null, it, activeBank)
                        Handler(Looper.getMainLooper()).postDelayed({
                            bottomSheet?.state = BottomSheetBehavior.STATE_EXPANDED
                        }, 125)
                    } ?: onBottomSheetChanged(SHEET.MENU)
                    removeRefreshListener()
                }
            })
            updateEliteAdapter(extras.getStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST))
            bankStats?.text = getString(
                R.string.bank_stats, getValueForPosition(eliteBankCount, activeBank), bankCount
            )
            updateNumberedText(bankCount)
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
        this.refreshListener = listener
    }

    private fun removeRefreshListener() {
        this.refreshListener = null
    }

    interface RefreshListener {
        fun onListRefreshed(amiibos: ArrayList<EliteTag?>)
    }
}