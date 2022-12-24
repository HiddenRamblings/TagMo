package com.hiddenramblings.tagmo.browser

import android.app.Activity
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
import androidx.appcompat.widget.*
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.hiddenramblings.tagmo.*
import com.hiddenramblings.tagmo.amiibo.*
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.Companion.getAmiiboManager
import com.hiddenramblings.tagmo.amiibo.tagdata.TagDataEditor
import com.hiddenramblings.tagmo.browser.adapter.EliteBankAdapter
import com.hiddenramblings.tagmo.browser.adapter.WriteTagAdapter
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.hexcode.HexCodeViewer
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.hiddenramblings.tagmo.widget.Toasty
import com.shawnlin.numberpicker.NumberPicker
import org.json.JSONException
import java.io.IOException
import java.text.ParseException
import java.util.*

class EliteBankFragment : Fragment(), EliteBankAdapter.OnAmiiboClickListener {
    private var prefs: Preferences? = null
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
    private var eliteBankCount: NumberPicker? = null
    private var writeOpenBanks: AppCompatButton? = null
    private var eraseOpenBanks: AppCompatButton? = null
    private var securityOptions: LinearLayout? = null
    private var searchView: SearchView? = null
    private var settings: BrowserSettings? = null
    var bottomSheet: BottomSheetBehavior<View>? = null
        private set
    private var keyManager: KeyManager? = null
    private var amiibos: ArrayList<EliteTag?> = ArrayList()
    private var clickedPosition = 0

    private enum class CLICKED {
        NOTHING, WRITE_DATA, EDIT_DATA, HEX_CODE, BANK_BACKUP, VERIFY_TAG, ERASE_BANK
    }

    private var status = CLICKED.NOTHING

    private enum class SHEET {
        LOCKED, AMIIBO, MENU, WRITE
    }

    private val eliteHandler = Handler(Looper.getMainLooper())
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
        if (prefs!!.softwareLayer())
            eliteContent!!.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        eliteContent!!.setHasFixedSize(true)
        switchMenuOptions = rootLayout.findViewById(R.id.switch_menu_btn)
        bankOptionsMenu = rootLayout.findViewById(R.id.bank_options_menu)
        writeBankLayout = rootLayout.findViewById(R.id.write_list_layout)
        if (prefs!!.softwareLayer())
            writeBankLayout!!.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        amiiboFilesView = rootLayout.findViewById(R.id.amiibo_files_list)
        if (prefs!!.softwareLayer())
            amiiboFilesView!!.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        // amiiboFilesView!!.setHasFixedSize(true);
        securityOptions = rootLayout.findViewById(R.id.security_options)
        amiiboTile = rootLayout.findViewById(R.id.active_tile_layout)
        amiiboCard = rootLayout.findViewById(R.id.active_card_layout)
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
        bankStats = rootLayout.findViewById(R.id.bank_stats)
        eliteBankCount = rootLayout.findViewById(R.id.number_picker)
        writeOpenBanks = rootLayout.findViewById(R.id.write_open_banks)
        eraseOpenBanks = rootLayout.findViewById(R.id.erase_open_banks)
        settings = activity.settings
        val toggle = rootLayout.findViewById<AppCompatImageView>(R.id.toggle)
        bottomSheet = BottomSheetBehavior.from(rootLayout.findViewById(R.id.bottom_sheet))
        bottomSheet!!.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheet!!.addBottomSheetCallback(object : BottomSheetCallback() {
            override fun onStateChanged(view: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    if (writeBankLayout!!.visibility == View.VISIBLE) onBottomSheetChanged(SHEET.MENU)
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
                        0,
                        0,
                        0,
                        if (slideOffset > 0) (bottomHeight * slideOffset).toInt() else 0
                    )
                }
                if (slideOffset > 0) eliteContent!!.smoothScrollToPosition(clickedPosition)
            }
        })
        toggle.setOnClickListener {
            if (bottomSheet!!.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheet!!.setState(BottomSheetBehavior.STATE_EXPANDED)
            } else {
                bottomSheet!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
        }
        toolbar!!.inflateMenu(R.menu.bank_menu)
        if (settings?.amiiboView == BrowserSettings.VIEW.IMAGE.value)
            eliteContent!!.layoutManager = GridLayoutManager(activity, activity.columnCount)
        else eliteContent!!.layoutManager = LinearLayoutManager(activity)
        bankAdapter = EliteBankAdapter(settings!!, this)
        eliteContent!!.adapter = bankAdapter
        settings?.addChangeListener(bankAdapter)
        eliteBankCount!!.setOnValueChangedListener { _: NumberPicker?, _: Int, valueNew: Int ->
            writeOpenBanks!!.text = getString(R.string.write_open_banks, valueNew)
            eraseOpenBanks!!.text = getString(R.string.erase_open_banks, valueNew)
        }
        if (settings?.amiiboView == BrowserSettings.VIEW.IMAGE.value)
            amiiboFilesView!!.layoutManager = GridLayoutManager(activity, activity.columnCount)
        else
            amiiboFilesView!!.layoutManager = LinearLayoutManager(activity)
        writeTagAdapter = WriteTagAdapter(settings)
        amiiboFilesView!!.adapter = writeTagAdapter
        settings?.addChangeListener(writeTagAdapter)
        switchMenuOptions?.setOnClickListener {
            if (bankOptionsMenu!!.isShown) {
                onBottomSheetChanged(SHEET.AMIIBO)
            } else {
                onBottomSheetChanged(SHEET.MENU)
            }
            bottomSheet!!.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
        searchView = rootLayout.findViewById(R.id.amiibo_search)
        val searchManager = activity.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchView!!.setSearchableInfo(
            searchManager.getSearchableInfo(activity.componentName)
        )
        searchView!!.isSubmitButtonEnabled = false
        searchView!!.setIconifiedByDefault(false)
        searchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                settings?.query = query
                settings?.notifyChanges()
                return false
            }

            override fun onQueryTextChange(query: String): Boolean {
                settings?.query = query
                settings?.notifyChanges()
                return true
            }
        })
        writeOpenBanks!!.setOnClickListener {
            onBottomSheetChanged(SHEET.WRITE)
            searchView!!.setQuery(settings?.query, true)
            searchView!!.clearFocus()
            writeTagAdapter!!.setListener(object : WriteTagAdapter.OnAmiiboClickListener {
                override fun onAmiiboClicked(amiiboFile: AmiiboFile?) {}
                override fun onAmiiboImageClicked(amiiboFile: AmiiboFile?) {}
                override fun onAmiiboListClicked(amiiboList: ArrayList<AmiiboFile?>?) {
                    writeAmiiboCollection(amiiboList)
                }
            }, eliteBankCount!!.value)
            bottomSheet!!.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
        eraseOpenBanks!!.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.elite_erase_confirm)
                .setPositiveButton(R.string.proceed) { _: DialogInterface?, _: Int ->
                    val collection = Intent(requireActivity(), NfcActivity::class.java)
                    collection.putExtra(
                        NFCIntent.EXTRA_SIGNATURE,
                        prefs!!.eliteSignature()
                    )
                    collection.action = NFCIntent.ACTION_ERASE_ALL_TAGS
                    collection.putExtra(NFCIntent.EXTRA_BANK_COUNT, eliteBankCount!!.value)
                    onOpenBanksActivity.launch(collection)
                }
                .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .show()
        }
        rootLayout.findViewById<View>(R.id.edit_bank_count).setOnClickListener {
            if (prefs!!.eliteActiveBank() >= eliteBankCount!!.value) {
                Toasty(activity).Short(R.string.fail_active_oob)
                onBottomSheetChanged(SHEET.MENU)
                return@setOnClickListener
            }
            val configure = Intent(activity, NfcActivity::class.java)
            configure.putExtra(
                NFCIntent.EXTRA_SIGNATURE,
                prefs!!.eliteSignature()
            )
            configure.action = NFCIntent.ACTION_SET_BANK_COUNT
            configure.putExtra(NFCIntent.EXTRA_BANK_COUNT, eliteBankCount!!.value)
            onOpenBanksActivity.launch(configure)
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
            android.app.AlertDialog.Builder(requireContext())
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
        var amiiboManager = settings?.amiiboManager
        if (null == amiiboManager) {
            try {
                amiiboManager = getAmiiboManager(requireContext().applicationContext)
            } catch (e: IOException) {
                Debug.warn(e)
                Toasty(requireActivity()).Short(R.string.amiibo_info_parse_error)
            } catch (e: JSONException) {
                Debug.warn(e)
                Toasty(requireActivity()).Short(R.string.amiibo_info_parse_error)
            } catch (e: ParseException) {
                Debug.warn(e)
                Toasty(requireActivity()).Short(R.string.amiibo_info_parse_error)
            }
            val uiAmiiboManager = amiiboManager
            requireActivity().runOnUiThread {
                settings!!.amiiboManager = uiAmiiboManager
                settings!!.notifyChanges()
            }
        }
        if (null == amiiboManager) {
            return
        }
        if (amiibos.isEmpty()) {
            bankAdapter!!.setAmiibos(amiibos)
            for (x in amiiboList!!.indices) {
                amiibos.add(EliteTag(amiiboManager.amiibos[TagArray.hexToLong(amiiboList[x])]))
                bankAdapter!!.notifyItemInserted(x)
            }
        } else {
            for (x in amiiboList!!.indices) {
                val amiiboId = TagArray.hexToLong(amiiboList[x])
                if (x >= amiibos.size) {
                    amiibos.add(EliteTag(amiiboManager.amiibos[TagArray.hexToLong(amiiboList[x])]))
                    bankAdapter!!.notifyItemInserted(x)
                } else if (null == amiibos[x] || amiibos[x]!!.index != x || amiiboId != amiibos[x]!!.id) {
                    amiibos[x] = EliteTag(amiiboManager.amiibos[amiiboId])
                    bankAdapter!!.notifyItemChanged(x)
                }
            }
            if (amiibos.size > amiiboList.size) {
                val count = amiibos.size
                val size = amiiboList.size
                val shortList = ArrayList<EliteTag?>()
                for (x in 0 until size) {
                    shortList.add(amiibos[x])
                }
                amiibos = ArrayList(shortList)
                bankAdapter!!.notifyItemRangeChanged(0, size)
                bankAdapter!!.notifyItemRangeRemoved(size, count - size)
            }
        }
    }

    private fun onBottomSheetChanged(sheet: SHEET) {
        bottomSheet!!.state = BottomSheetBehavior.STATE_COLLAPSED
        when (sheet) {
            SHEET.LOCKED -> {
                amiiboCard!!.visibility = View.GONE
                switchMenuOptions!!.visibility = View.GONE
                bankOptionsMenu!!.visibility = View.GONE
                securityOptions!!.visibility = View.VISIBLE
                writeBankLayout!!.visibility = View.GONE
            }
            SHEET.AMIIBO -> {
                amiiboCard!!.visibility = View.VISIBLE
                switchMenuOptions!!.visibility = View.VISIBLE
                bankOptionsMenu!!.visibility = View.GONE
                securityOptions!!.visibility = View.GONE
                writeBankLayout!!.visibility = View.GONE
            }
            SHEET.MENU -> {
                amiiboCard!!.visibility = View.GONE
                switchMenuOptions!!.visibility = View.VISIBLE
                bankOptionsMenu!!.visibility = View.VISIBLE
                securityOptions!!.visibility = View.VISIBLE
                writeBankLayout!!.visibility = View.GONE
            }
            SHEET.WRITE -> {
                amiiboCard!!.visibility = View.GONE
                switchMenuOptions!!.visibility = View.GONE
                bankOptionsMenu!!.visibility = View.GONE
                securityOptions!!.visibility = View.GONE
                writeBankLayout!!.visibility = View.VISIBLE
            }
        }
        eliteContent!!.requestLayout()
    }

    private val onActivateActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != Activity.RESULT_OK || null == result.data)
            return@registerForActivityResult
        if (NFCIntent.ACTION_NFC_SCANNED != result.data!!.action)
            return@registerForActivityResult
        val activeBank = result.data!!.getIntExtra(
            NFCIntent.EXTRA_ACTIVE_BANK,
            prefs!!.eliteActiveBank()
        )
        bankAdapter!!.notifyItemChanged(prefs!!.eliteActiveBank())
        bankAdapter!!.notifyItemChanged(activeBank)
        prefs!!.eliteActiveBank(activeBank)
        updateAmiiboView(amiiboTile, null, amiibos[activeBank]!!.id, activeBank)
        val bankCount = prefs!!.eliteBankCount()
        bankStats!!.text = getString(
            R.string.bank_stats, getValueForPosition(eliteBankCount!!, activeBank), bankCount
        )
        writeOpenBanks!!.text = getString(R.string.write_open_banks, bankCount)
        eraseOpenBanks!!.text = getString(R.string.erase_open_banks, bankCount)
    }
    private val onUpdateTagResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != Activity.RESULT_OK || null == result.data) return@registerForActivityResult
        if (NFCIntent.ACTION_NFC_SCANNED != result.data!!.action
            && NFCIntent.ACTION_EDIT_COMPLETE != result.data!!.action
        ) return@registerForActivityResult
        if (result.data!!.hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
            clickedPosition = result.data!!.getIntExtra(
                NFCIntent.EXTRA_CURRENT_BANK, clickedPosition
            )
        }
        var tagData = if (amiibos.size > clickedPosition && null != amiibos[clickedPosition])
            amiibos[clickedPosition]!!.data else null
        if (result.data!!.hasExtra(NFCIntent.EXTRA_TAG_DATA)) {
            tagData = result.data?.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA)
            if (amiibos.size > clickedPosition && null != amiibos[clickedPosition])
                amiibos[clickedPosition]!!.data = tagData
        }
        if (result.data!!.hasExtra(NFCIntent.EXTRA_AMIIBO_LIST)) {
            updateEliteAdapter(result.data!!.getStringArrayListExtra(NFCIntent.EXTRA_AMIIBO_LIST))
        }
        updateAmiiboView(amiiboCard, tagData, -1, clickedPosition)
        bottomSheet!!.state = BottomSheetBehavior.STATE_EXPANDED
        var activeBank = prefs!!.eliteActiveBank()
        if (result.data!!.hasExtra(NFCIntent.EXTRA_ACTIVE_BANK)) {
            activeBank = result.data!!.getIntExtra(NFCIntent.EXTRA_ACTIVE_BANK, activeBank)
            prefs!!.eliteActiveBank(activeBank)
        }
        updateAmiiboView(amiiboTile, null, amiibos[activeBank]!!.id, activeBank)
        if (status == CLICKED.ERASE_BANK) {
            status = CLICKED.NOTHING
            onBottomSheetChanged(SHEET.MENU)
            amiibos[clickedPosition] = null
        }
    }
    private val onScanTagResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != Activity.RESULT_OK || null == result.data) return@registerForActivityResult
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
                val modify = Intent(requireContext(), NfcActivity::class.java)
                modify.putExtra(
                    NFCIntent.EXTRA_SIGNATURE,
                    prefs!!.eliteSignature()
                )
                modify.action = NFCIntent.ACTION_WRITE_TAG_FULL
                modify.putExtra(NFCIntent.EXTRA_CURRENT_BANK, clickedPosition)
                onUpdateTagResult.launch(modify.putExtras(args))
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
                Toasty(requireActivity()).Dialog(R.string.validation_success)
            } catch (e: Exception) {
                Toasty(requireActivity()).Dialog(e.message)
            }
            else -> {}
        }
        status = CLICKED.NOTHING
        updateAmiiboView(amiiboCard, tagData, -1, clickedPosition)
        bankAdapter!!.notifyItemChanged(clickedPosition)
    }

    private fun scanAmiiboBank(current_bank: Int) {
        val scan = Intent(requireContext(), NfcActivity::class.java)
        scan.putExtra(
            NFCIntent.EXTRA_SIGNATURE,
            prefs!!.eliteSignature()
        )
        scan.action = NFCIntent.ACTION_SCAN_TAG
        scan.putExtra(NFCIntent.EXTRA_CURRENT_BANK, current_bank)
        onScanTagResult.launch(scan)
    }

    private fun scanAmiiboTag(position: Int) {
        val amiiboIntent = Intent(requireContext(), NfcActivity::class.java)
        amiiboIntent.putExtra(
            NFCIntent.EXTRA_SIGNATURE,
            prefs!!.eliteSignature()
        )
        amiiboIntent.putExtra(NFCIntent.EXTRA_CURRENT_BANK, position)
        amiiboIntent.action = NFCIntent.ACTION_SCAN_TAG
        onUpdateTagResult.launch(amiiboIntent)
    }

    private fun writeAmiiboFile(amiiboFile: AmiiboFile, position: Int) {
        val args = Bundle()
        if ((requireActivity() as BrowserActivity).isDocumentStorage) {
            try {
                val data = if (null != amiiboFile.data)
                    amiiboFile.data
                else if (null != amiiboFile.docUri)
                    TagArray.getValidatedDocument(keyManager, amiiboFile.docUri!!)
                else null
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, data)
            } catch (e: Exception) {
                Debug.warn(e)
            }
        } else {
            try {
                val data = if (null != amiiboFile.data)
                    amiiboFile.data
                else if (null != amiiboFile.filePath)
                    TagArray.getValidatedFile(keyManager, amiiboFile.filePath!!)
                else null
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, data)
            } catch (e: Exception) {
                Debug.warn(e)
            }
        }
        val intent = Intent(requireContext(), NfcActivity::class.java)
        intent.putExtra(
            NFCIntent.EXTRA_SIGNATURE,
            prefs!!.eliteSignature()
        )
        intent.action = NFCIntent.ACTION_WRITE_TAG_FULL
        intent.putExtra(NFCIntent.EXTRA_CURRENT_BANK, position)
        intent.putExtras(args)
        onUpdateTagResult.launch(intent)
    }

    private fun displayWriteDialog(position: Int) {
        onBottomSheetChanged(SHEET.WRITE)
        searchView!!.setQuery(settings?.query, true)
        searchView!!.clearFocus()
        writeTagAdapter!!.setListener(object : WriteTagAdapter.OnAmiiboClickListener {
            override fun onAmiiboClicked(amiiboFile: AmiiboFile?) {
                if (null != amiiboFile) {
                    onBottomSheetChanged(SHEET.AMIIBO)
                    writeAmiiboFile(amiiboFile, position)
                }
            }

            override fun onAmiiboImageClicked(amiiboFile: AmiiboFile?) {
                handleImageClicked(amiiboFile)
            }

            override fun onAmiiboListClicked(amiiboList: ArrayList<AmiiboFile?>?) {}
        }, 1)
        bottomSheet!!.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun displayBackupDialog(tagData: ByteArray?) {
        val view = layoutInflater.inflate(R.layout.dialog_save_item, null)
        val dialog = AlertDialog.Builder(requireContext())
        val input = view.findViewById<EditText>(R.id.save_item_entry)
        input.setText(TagArray.decipherFilename(settings!!.amiiboManager, tagData, true))
        val backupDialog: Dialog = dialog.setView(view).create()
        view.findViewById<View>(R.id.button_save).setOnClickListener {
            try {
                val fileName: String
                val activity = requireActivity() as BrowserActivity
                fileName = if (activity.isDocumentStorage) ({
                    val rootDocument = DocumentFile.fromTreeUri(
                        requireContext(),
                        settings!!.browserRootDocument!!
                    ) ?: throw NullPointerException()
                    TagArray.writeBytesToDocument(
                        requireContext(), rootDocument, input.text.toString(), tagData
                    )
                }).toString() else {
                    TagArray.writeBytesToFile(Storage.getDownloadDir(
                            "TagMo", "Backups"
                    ), input.text.toString(), tagData)
                }
                Toasty(requireActivity()).Long(getString(R.string.wrote_file, fileName))
                activity.loadAmiiboBackground()
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
        if (hasTagInfo) {
            textView.visibility = View.GONE
        } else {
            textView.visibility = View.VISIBLE
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
            scan.putExtra(
                NFCIntent.EXTRA_SIGNATURE, prefs!!.eliteSignature()
            )
            scan.putExtra(NFCIntent.EXTRA_CURRENT_BANK, current_bank)
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
                    if (null != tagData && tagData.isNotEmpty()) {
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
                    if (prefs!!.eliteActiveBank() == current_bank) {
                        notice.Short(R.string.erase_active)
                    } else {
                        scan.action = NFCIntent.ACTION_ERASE_BANK
                        onUpdateTagResult.launch(scan)
                        status = CLICKED.ERASE_BANK
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_edit -> {
                    if (null != tagData && tagData.isNotEmpty()) {
                        val editor = Intent(requireContext(), TagDataEditor::class.java)
                        editor.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData)
                        onUpdateTagResult.launch(editor)
                    } else {
                        status = CLICKED.EDIT_DATA
                        scanAmiiboBank(current_bank)
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_view_hex -> {
                    if (null != tagData && tagData.isNotEmpty()) {
                        val viewhex = Intent(requireContext(), HexCodeViewer::class.java)
                        viewhex.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData)
                        startActivity(viewhex)
                    } else {
                        status = CLICKED.HEX_CODE
                        scanAmiiboBank(current_bank)
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_backup -> {
                    if (null != tagData && tagData.isNotEmpty()) {
                        displayBackupDialog(tagData)
                    } else {
                        status = CLICKED.BANK_BACKUP
                        scanAmiiboBank(current_bank)
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.mnu_validate -> {
                    if (null != tagData && tagData.isNotEmpty()) {
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
        val amiiboInfo = rootLayout.findViewById<View>(R.id.amiiboInfo)
        val txtError = rootLayout.findViewById<TextView>(R.id.txtError)
        val txtName = amiiboView!!.findViewById<TextView>(R.id.txtName)
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
        val amiiboManager = settings?.amiiboManager
        if (amiiboLongId == -1L) {
            tagInfo = getString(R.string.read_error)
        } else if (amiiboLongId == 0L) {
            tagInfo = getString(R.string.blank_tag)
        } else if (null != amiiboManager) {
            val generic = amiiboManager.amiibos[amiiboLongId]
            amiibo = EliteTag(
                generic ?: Amiibo(amiiboManager, amiiboLongId, null, null)
            )
        }
        if (null != amiibo) {
            amiiboView.visibility = View.VISIBLE
            amiiboImageUrl = amiibo.imageUrl
            amiiboHexId = Amiibo.idToHex(amiibo.id)
            if (null != amiibo.name) amiiboName = amiibo.name!!
            if (null != amiibo.amiiboSeries) amiiboSeries = amiibo.amiiboSeries!!.name
            if (null != amiibo.amiiboType) amiiboType = amiibo.amiiboType!!.name
            if (null != amiibo.gameSeries) gameSeries = amiibo.gameSeries!!.name
        } else if (null == tagInfo) {
            tagInfo = "ID: " + Amiibo.idToHex(amiiboLongId)
            amiiboImageUrl = Amiibo.getImageUrl(amiiboLongId)
        }
        val hasTagInfo = null != tagInfo
        if (hasTagInfo) {
            setAmiiboInfoText(txtError, tagInfo, false)
            amiiboInfo.visibility = View.GONE
        } else {
            txtError.visibility = View.GONE
            amiiboInfo.visibility = View.VISIBLE
        }
        if (null != txtBank)
            setAmiiboInfoText(txtBank, getString(
                R.string.bank_number, getValueForPosition(eliteBankCount!!, current_bank)
            ), hasTagInfo)
        setAmiiboInfoText(txtName, amiiboName, hasTagInfo)
        setAmiiboInfoText(txtTagId, amiiboHexId, hasTagInfo)
        setAmiiboInfoText(txtAmiiboSeries, amiiboSeries, hasTagInfo)
        setAmiiboInfoText(txtAmiiboType, amiiboType, hasTagInfo)
        setAmiiboInfoText(txtGameSeries, gameSeries, hasTagInfo)
        if (amiiboView === amiiboTile && null == amiiboImageUrl) {
            imageAmiibo!!.setImageResource(R.mipmap.ic_launcher_round)
            imageAmiibo.visibility = View.VISIBLE
        } else if (null != imageAmiibo) {
            GlideApp.with(imageAmiibo).clear(imageAmiibo)
            if (!amiiboImageUrl.isNullOrEmpty()) {
                GlideApp.with(imageAmiibo).asBitmap().load(amiiboImageUrl).into(
                    if (amiiboView === amiiboCard) amiiboCardTarget else amiiboTileTarget
                )
            }
            imageAmiibo.setOnClickListener {
                if (amiiboLongId == -1L) {
                    return@setOnClickListener
                }
                val bundle = Bundle()
                bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiiboLongId)
                val intent = Intent(requireContext(), ImageActivity::class.java)
                intent.putExtras(bundle)
                startActivity(intent)
            }
        }
    }

    private val onOpenBanksActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != Activity.RESULT_OK || null == result.data) return@registerForActivityResult
        if (NFCIntent.ACTION_NFC_SCANNED != result.data!!.action) return@registerForActivityResult
        val bankCount = result.data!!.getIntExtra(
            NFCIntent.EXTRA_BANK_COUNT,
            prefs!!.eliteBankCount()
        )
        prefs!!.eliteBankCount(bankCount)
        eliteBankCount!!.value = bankCount
        updateEliteAdapter(result.data!!.getStringArrayListExtra(NFCIntent.EXTRA_AMIIBO_LIST))
        bankStats!!.text = getString(
            R.string.bank_stats, getValueForPosition(
                eliteBankCount!!, prefs!!.eliteActiveBank()
            ), bankCount
        )
        writeOpenBanks!!.text = getString(R.string.write_open_banks, bankCount)
        eraseOpenBanks!!.text = getString(R.string.erase_open_banks, bankCount)
        val activeBank = prefs!!.eliteActiveBank()
        updateAmiiboView(amiiboTile, null, amiibos[activeBank]!!.id, activeBank)
    }

    private fun writeAmiiboCollection(amiiboList: ArrayList<AmiiboFile?>?) {
        for (i in amiiboList!!.indices) {
            try {
                val amiiboFile = amiiboList[i]
                amiiboFile!!.data = TagArray.getValidatedData(keyManager, amiiboFile)
                amiiboList[i] = amiiboFile
            } catch (ignored: Exception) {
            }
        }
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.elite_write_confirm)
            .setPositiveButton(R.string.proceed) { dialog: DialogInterface, _: Int ->
                val collection = Intent(requireContext(), NfcActivity::class.java)
                collection.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                collection.putExtra(NFCIntent.EXTRA_SIGNATURE, prefs!!.eliteSignature())
                collection.action = NFCIntent.ACTION_WRITE_ALL_TAGS
                collection.putExtra(NFCIntent.EXTRA_BANK_COUNT, eliteBankCount!!.value)
                collection.putExtra(NFCIntent.EXTRA_AMIIBO_FILES, amiiboList)
                onOpenBanksActivity.launch(collection)
                onBottomSheetChanged(SHEET.MENU)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                amiiboList.clear()
                onBottomSheetChanged(SHEET.MENU)
                dialog.dismiss()
            }
            .show()
    }

    private fun getValueForPosition(picker: NumberPicker, value: Int): Int {
        return value + picker.minValue
    }

    private fun onHardwareLoaded() {
        try {
            val bankCount = requireArguments().getInt(
                NFCIntent.EXTRA_BANK_COUNT,
                prefs!!.eliteBankCount()
            )
            val activeBank = requireArguments().getInt(
                NFCIntent.EXTRA_ACTIVE_BANK,
                prefs!!.eliteActiveBank()
            )
            (rootLayout.findViewById<View>(R.id.hardware_info) as TextView).text = getString(
                R.string.elite_signature, requireArguments().getString(NFCIntent.EXTRA_SIGNATURE)
            )
            eliteBankCount!!.value = bankCount
            updateEliteAdapter(requireArguments().getStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST))
            bankStats!!.text = getString(
                R.string.bank_stats,
                getValueForPosition(eliteBankCount!!, activeBank), bankCount
            )
            writeOpenBanks!!.text = getString(R.string.write_open_banks, bankCount)
            eraseOpenBanks!!.text = getString(R.string.erase_open_banks, bankCount)
            if (null == amiibos[activeBank]) {
                onBottomSheetChanged(SHEET.MENU)
            } else {
                updateAmiiboView(amiiboCard, null, amiibos[activeBank]!!.id, activeBank)
                updateAmiiboView(amiiboTile, null, amiibos[activeBank]!!.id, activeBank)
                onBottomSheetChanged(SHEET.AMIIBO)
                eliteHandler.postDelayed({
                    bottomSheet!!.setState(BottomSheetBehavior.STATE_EXPANDED)
                }, TagMo.uiDelay.toLong())
            }
            arguments = null
        } catch (ignored: Exception) {
            if (amiibos.isEmpty()) onBottomSheetChanged(SHEET.LOCKED)
        }
    }

    override fun onResume() {
        super.onResume()
        onHardwareLoaded()
    }

    private fun handleImageClicked(amiibo: Amiibo?) {
        if (null != amiibo) {
            val bundle = Bundle()
            bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiibo.id)
            val intent = Intent(requireContext(), ImageActivity::class.java)
            intent.putExtras(bundle)
            startActivity(intent)
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

    override fun onAmiiboClicked(amiibo: EliteTag?, position: Int) {
        if (null == amiibo?.manager) {
            displayWriteDialog(position)
            return
        }
        clickedPosition = position
        status = CLICKED.NOTHING
        onBottomSheetChanged(SHEET.AMIIBO)
        if (null != amiibo.data && amiibo.index == position) {
            updateAmiiboView(amiiboCard, amiibo.data, -1, position)
        } else if (amiibo.id != 0L) {
            updateAmiiboView(amiiboCard, null, amiibo.id, position)
        } else {
            scanAmiiboTag(position)
        }
        bottomSheet!!.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onAmiiboImageClicked(amiibo: EliteTag?, position: Int) {
        handleImageClicked(amiibo)
    }

    override fun onAmiiboLongClicked(amiibo: EliteTag?, position: Int): Boolean {
        if (null != amiibo) scanAmiiboTag(position) else displayWriteDialog(position)
        return true
    }
}