package com.hiddenramblings.tagmo.amiibo.tagdata

import android.app.DatePickerDialog
import android.app.DatePickerDialog.OnDateSetListener
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.hiddenramblings.tagmo.GlideApp
import com.hiddenramblings.tagmo.NFCIntent
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.nfctech.Foomiibo
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.hiddenramblings.tagmo.widget.Toasty
import com.vicmikhailau.maskededittext.MaskedEditText
import kotlinx.coroutines.*
import org.json.JSONException
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class TagDataEditor : AppCompatActivity() {

    private val keyManager: KeyManager by lazy { KeyManager(this) }

    private lateinit var txtError: TextView
    private lateinit var txtTagId: TextView
    private lateinit var txtName: TextView
    private lateinit var txtGameSeries: TextView
    // private TextView txtCharacter;
    private lateinit var txtAmiiboType: TextView
    private lateinit var txtAmiiboSeries: TextView
    private lateinit var imageAmiibo: AppCompatImageView
    private lateinit var txtUID: MaskedEditText
    private lateinit var txtCountryCode: Spinner
    private lateinit var txtInitDate: EditText
    private lateinit var txtModifiedDate: EditText
    private lateinit var txtNickname: EditText
    private lateinit var txtMiiName: EditText
    private lateinit var txtMiiAuthor: EditText
    private lateinit var txtWriteCounter: EditText
    private lateinit var txtSerialNumber: EditText
    private lateinit var txtAppName: Spinner
    private lateinit var txtAppId: MaskedEditText
    private lateinit var appDataSwitch: SwitchCompat
    private lateinit var userDataSwitch: SwitchCompat
    private lateinit var generateSerial: AppCompatButton

    private lateinit var appDataViewChibiRobo: LinearLayout
    private lateinit var appDataViewZeldaTP: LinearLayout
    private lateinit var appDataViewMHStories: LinearLayout
    private lateinit var appDataViewMLPaperJam: LinearLayout
    private lateinit var appDataViewMLSuperstarSaga: LinearLayout
    private lateinit var appDataViewMSSuperstars: LinearLayout
    private lateinit var appDataViewMarioTennis: LinearLayout
    private lateinit var appDataViewPikmin: LinearLayout
    private lateinit var appDataViewSplatoon: LinearLayout
    private lateinit var appDataViewSplatoon3: LinearLayout
    private lateinit var appDataViewSSB: LinearLayout
    private lateinit var appDataViewSSBU: LinearLayout
    private lateinit var appDataFormat: AppCompatButton
    private lateinit var appDataTransfer: AppCompatButton
    private var countryCodeAdapter: CountryCodesAdapter? = null
    private var appIdAdapter: AppIdAdapter? = null
    private var ignoreAppNameSelected = false

    private var tagData: ByteArray? = null
    private var amiiboManager: AmiiboManager? = null
    private lateinit var amiiboData: AmiiboData

    private var initialUserDataInitialized = false
    private var isAppDataInitialized = false
    private var initialAppDataInitialized = false
    private var isUserDataInitialized = false
    private var initializedDate: Date? = null
    private var modifiedDate: Date? = null
    private var appId: Int? = null
    private var appDataChibiRobo: AppDataChibiRobo? = null
    private var txtHearts1: EditText? = null
    private var txtHearts2: Spinner? = null
    private var txtLevelZeldaTP: EditText? = null
    private var appDataZeldaTP: AppDataZeldaTP? = null
    private var appDataMHStories: AppDataMHStories? = null
    private var appDataMLPaperJam: AppDataMLPaperJam? = null
    private var buttonUnlock: AppCompatButton? = null
    private var appDataMLSuperstarSaga: AppDataMLSuperstarSaga? = null
    private var appDataMSSuperstars: AppDataMSSuperstars? = null
    private var appDataMarioTennis: AppDataMarioTennis? = null
    private var appDataPikmin: AppDataPikmin? = null
    private var appDataSplatoon: AppDataSplatoon? = null
    private var buttonInject: AppCompatButton? = null
    private var appDataSplatoon3: AppDataSplatoon3? = null
    private var buttonInject3: AppCompatButton? = null
    private var spnAppearance: Spinner? = null
    private var txtLevelSSB: EditText? = null
    private var spnSpecialNeutral: Spinner? = null
    private var spnSpecialSide: Spinner? = null
    private var spnSpecialUp: Spinner? = null
    private var spnSpecialDown: Spinner? = null
    private var spnEffect1: Spinner? = null
    private var spnEffect2: Spinner? = null
    private var spnEffect3: Spinner? = null
    private var txtStatAttack: EditText? = null
    private var txtStatDefense: EditText? = null
    private var txtStatSpeed: EditText? = null
    private var appDataSSB: AppDataSSB? = null
    private var spnAppearanceU: Spinner? = null
    private var txtLevelSSBU: EditText? = null
    private var txtGiftCount: EditText? = null
    private var txtLevelCPU: EditText? = null
    private var txtStatAttackU: EditText? = null
    private var txtStatDefenseU: EditText? = null
    private var txtStatSpeedU: EditText? = null
    private var appDataSSBU: AppDataSSBU? = null

    private fun getDateString(date: Date): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date)
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tag_data)
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        if (keyManager.isKeyMissing()) {
            showErrorDialog(R.string.no_decrypt_key)
            return
        }
        txtError = findViewById(R.id.txtError)
        txtTagId = findViewById(R.id.txtTagId)
        txtName = findViewById(R.id.txtName)
        txtGameSeries = findViewById(R.id.txtGameSeries)
        // txtCharacter = findViewById(R.id.txtCharacter);
        txtAmiiboType = findViewById(R.id.txtAmiiboType)
        txtAmiiboSeries = findViewById(R.id.txtAmiiboSeries)
        imageAmiibo = findViewById(R.id.imageAmiibo)
        txtUID = findViewById(R.id.txtUID)
        txtNickname = findViewById(R.id.txtNickname)
        txtMiiName = findViewById(R.id.txtMiiName)
        txtMiiAuthor = findViewById(R.id.txtMiiAuthor)

        appDataViewChibiRobo = findViewById(R.id.appDataChibiRobo)
        appDataViewZeldaTP = findViewById(R.id.appDataZeldaTP)
        appDataViewMHStories = findViewById(R.id.appDataMHStories)
        appDataViewMLPaperJam = findViewById(R.id.appDataMLPaperJam)
        appDataViewMLSuperstarSaga = findViewById(R.id.appDataMLSuperstarSaga)
        appDataViewMSSuperstars = findViewById(R.id.appDataMSSuperstars)
        appDataViewMarioTennis = findViewById(R.id.appDataMarioTennis)
        appDataViewPikmin = findViewById(R.id.appDataPikmin)
        appDataViewSplatoon = findViewById(R.id.appDataSplatoon)
        appDataViewSplatoon3 = findViewById(R.id.appDataSplatoon3)
        appDataViewSSB = findViewById(R.id.appDataSSB)
        appDataViewSSBU = findViewById(R.id.appDataSSBU)

        tagData = intent.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA)
        try {
            amiiboData = AmiiboData(keyManager.decrypt(tagData))
        } catch (e: Exception) {
            try {
                tagData = TagArray.getValidatedData(keyManager, tagData)
                amiiboData = AmiiboData(tagData as ByteArray)
            } catch (ex: Exception) {
                Debug.warn(e)
                showErrorDialog(R.string.fail_display)
                return
            }
        }
        findViewById<Toolbar>(R.id.toolbar).apply {
            setTitle(R.string.edit_props)
            inflateMenu(R.menu.save_menu)
            setNavigationIcon(android.R.drawable.ic_menu_revert)
            setNavigationOnClickListener { finish() }
            setOnMenuItemClickListener { item: MenuItem ->
                if (item.itemId == R.id.mnu_save) {
                    onSaveClicked()
                    return@setOnMenuItemClickListener true
                }
                false
            }
        }

        userDataSwitch = findViewById<SwitchCompat>(R.id.userDataSwitch).apply {
            setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->
                onUserDataSwitchClicked(checked)
            }
        }
        appDataSwitch = findViewById<SwitchCompat>(R.id.appDataSwitch).apply {
            setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->
                onAppDataSwitchClicked(checked)
            }
        }
        txtSerialNumber = findViewById(R.id.txtSerialNumber)
        generateSerial = findViewById<AppCompatButton>(R.id.random_serial).apply {
            setOnClickListener {
                txtSerialNumber.setText(TagArray.bytesToHex(Foomiibo().generateRandomUID()))
            }
        }
        txtInitDate = findViewById<EditText>(R.id.txtInitDate).apply {
            setOnClickListener {
                val calendar = Calendar.getInstance()
                initializedDate?.let { calendar.time = it }
                val datePickerDialog = DatePickerDialog(
                    this@TagDataEditor,
                    onInitDateSet,
                    calendar[Calendar.YEAR],
                    calendar[Calendar.MONTH],
                    calendar[Calendar.DAY_OF_MONTH]
                )
                datePickerDialog.show()
            }
        }
        txtModifiedDate = findViewById<EditText>(R.id.txtModifiedDate).apply {
            setOnClickListener {
                val calendar = Calendar.getInstance()
                modifiedDate?.let { calendar.time = it }
                val datePickerDialog = DatePickerDialog(
                    this@TagDataEditor,
                    onModifiedDateSet,
                    calendar[Calendar.YEAR],
                    calendar[Calendar.MONTH],
                    calendar[Calendar.DAY_OF_MONTH]
                )
                datePickerDialog.show()
            }
        }
        updateAmiiboView(tagData)
        CoroutineScope(Dispatchers.Main + Job()).launch {
            withContext(Dispatchers.IO) {
                var amiiboManager: AmiiboManager? = null
                try {
                    amiiboManager = AmiiboManager.getAmiiboManager(applicationContext)
                } catch (e: IOException) {
                    Debug.warn(e)
                    Toasty(this@TagDataEditor).Short(getString(R.string.amiibo_info_parse_error))
                } catch (e: JSONException) {
                    Debug.warn(e)
                    Toasty(this@TagDataEditor).Short(getString(R.string.amiibo_info_parse_error))
                } catch (e: ParseException) {
                    Debug.warn(e)
                    Toasty(this@TagDataEditor).Short(getString(R.string.amiibo_info_parse_error))
                }
                this@TagDataEditor.amiiboManager = amiiboManager
                withContext(Dispatchers.Main) { updateAmiiboView(tagData) }
            }
        }
        countryCodeAdapter = CountryCodesAdapter(AmiiboData.countryCodes)
        txtCountryCode = findViewById<Spinner>(R.id.txtCountryCode).apply {
            adapter = countryCodeAdapter
        }
        appIdAdapter = AppIdAdapter(AppId.apps)
        txtAppName = findViewById<Spinner>(R.id.txtAppName).apply {
            adapter = appIdAdapter
            onItemSelectedListener = onAppNameSelected
        }
        txtAppId = findViewById<MaskedEditText>(R.id.txtAppId).apply {
            addTextChangedListener(onAppIdChange)
        }
        txtWriteCounter = findViewById<EditText>(R.id.txtWriteCounter).apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun afterTextChanged(editable: Editable) {
                    try {
                        amiiboData.checkWriteCount(editable.toString().toInt())
                        txtWriteCounter.error = null
                    } catch (e: Exception) {
                        txtWriteCounter.error = getString(
                            R.string.error_min_max,
                            AmiiboData.WRITE_COUNT_MIN_VALUE, AmiiboData.WRITE_COUNT_MAX_VALUE
                        )
                    }
                }
            })
        }
        appDataFormat = findViewById<AppCompatButton>(R.id.format_app_data).apply {
            setOnClickListener {
                amiiboData.appId = 0
                amiiboData.appData = ByteArray(amiiboData.appData.size)
                loadData()
            }
        }
        appDataTransfer = findViewById<AppCompatButton>(R.id.transfer_app_data).apply {
            text = getString(R.string.import_app_data)
            setOnClickListener {
                if (null != AppData.transferData) {
                    transferData()
                    val button = it as AppCompatButton
                    button.text = getString(R.string.export_app_data)
                    button.isEnabled = false
                } else {
                    AppData.apply {
                        transferId = amiiboData.appId
                        transferData = amiiboData.appData
                    }
                    finish()
                }
            }
        }
        loadData()
    }

    private val imageTarget: CustomTarget<Bitmap?> = object : CustomTarget<Bitmap?>() {
        override fun onLoadFailed(errorDrawable: Drawable?) {
            imageAmiibo.setImageResource(0)
            imageAmiibo.isGone = true
        }

        override fun onLoadCleared(placeholder: Drawable?) {
            imageAmiibo.setImageResource(0)
            imageAmiibo.isGone = true
        }

        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
            imageAmiibo.setImageBitmap(resource)
            imageAmiibo.isVisible = true
        }
    }

    private fun updateAmiiboView(tagData: ByteArray?) {
        var tagInfo: String? = null
        var amiiboHexId = ""
        var amiiboName = ""
        var amiiboSeries = ""
        var amiiboType = ""
        var gameSeries = ""
        // String character = "";
        var amiiboImageUrl: String? = null
        if (null == tagData) {
            tagInfo = getString(R.string.no_tag_loaded)
        } else {
            val amiiboId: Long = try {
                Amiibo.dataToId(tagData)
            } catch (e: Exception) {
                Debug.info(e)
                -1
            }
            when (amiiboId) {
                -1L -> { tagInfo = getString(R.string.read_error) }
                0L -> { tagInfo = getString(R.string.blank_tag) }
                else -> {
                    var amiibo: Amiibo? = null
                    amiiboManager?.let {
                        amiibo = it.amiibos[amiiboId]
                        if (null == amiibo) amiibo = Amiibo(it, amiiboId, null, null)
                    }
                    amiibo?.let {
                        amiiboHexId = Amiibo.idToHex(it.id)
                        amiiboImageUrl = it.imageUrl
                        it.name?.let { name -> amiiboName = name }
                        it.amiiboSeries?.let { series -> amiiboSeries = series.name }
                        it.amiiboType?.let { type -> amiiboType = type.name }
                        it.gameSeries?.let { series -> gameSeries = series.name }
                    } ?: amiiboId.let {
                        tagInfo = "ID: " + Amiibo.idToHex(it)
                        amiiboImageUrl = Amiibo.getImageUrl(it)
                    }
                }
            }
        }
        if (null == tagInfo) {
            txtError.isGone = true
        } else {
            setAmiiboInfoText(txtError, tagInfo, false)
        }
        val hasTagInfo = null != tagInfo
        setAmiiboInfoText(txtName, amiiboName, hasTagInfo)
        setAmiiboInfoText(txtTagId, amiiboHexId, hasTagInfo)
        setAmiiboInfoText(txtAmiiboSeries, amiiboSeries, hasTagInfo)
        setAmiiboInfoText(txtAmiiboType, amiiboType, hasTagInfo)
        setAmiiboInfoText(txtGameSeries, gameSeries, hasTagInfo)
        // setAmiiboInfoText(txtCharacter, character, hasTagInfo);
        imageAmiibo.setImageResource(0)
        imageAmiibo.isGone = true
        if (!amiiboImageUrl.isNullOrEmpty()) {
            GlideApp.with(imageAmiibo).clear(imageAmiibo)
            GlideApp.with(imageAmiibo).asBitmap().load(amiiboImageUrl).into(imageTarget)
        }
    }

    private fun setAmiiboInfoText(textView: TextView, text: CharSequence?, hasTagInfo: Boolean) {
        textView.isGone = hasTagInfo
        if (!hasTagInfo) {
             if (!text.isNullOrEmpty()) {
                textView.text = text
                textView.isEnabled = true
            } else {
                textView.setText(R.string.unknown)
                textView.isEnabled = false
            }
        }
    }

    private fun loadData() {
        val isUserDataInitialized = amiiboData.isUserDataInitialized
        initialUserDataInitialized = isUserDataInitialized
        userDataSwitch.isChecked = isUserDataInitialized
        onUserDataSwitchClicked(isUserDataInitialized)
        val isAppDataInitialized = amiiboData.isAppDataInitialized
        initialAppDataInitialized = isAppDataInitialized
        appDataSwitch.isChecked = isAppDataInitialized
        onAppDataSwitchClicked(isAppDataInitialized)
        loadUID()
        loadCountryCode()
        loadInitializedDate()
        loadModifiedDate()
        loadNickname()
        loadMiiName()
        loadMiiAuthor()
        loadWriteCounter()
        loadSerialNumber()
        loadAppId()
    }

    private fun transferData() {
        val backupData = amiiboData
        amiiboData.appId = AppData.transferId
        AppData.transferData?.let { amiiboData.appData = it }
        amiiboData.uID = backupData.uID
        amiiboData.amiiboID = backupData.amiiboID
        if (backupData.isUserDataInitialized) {
            amiiboData.countryCode = backupData.countryCode
            amiiboData.initializedDate = backupData.initializedDate
            amiiboData.modifiedDate = backupData.modifiedDate
            amiiboData.nickname = backupData.nickname
            amiiboData.miiName = backupData.miiAuthor
            amiiboData.miiAuthor = backupData.miiAuthor
            amiiboData.titleID = backupData.titleID
            amiiboData.writeCount = backupData.writeCount
        } else {
            amiiboData.countryCode = 0
            amiiboData.initializedDate = Date()
            amiiboData.modifiedDate = Date()
            amiiboData.nickname = ""
            amiiboData.miiName = ""
            amiiboData.miiAuthor = ""
            amiiboData.titleID = 0
            amiiboData.writeCount = 0
        }
        amiiboData.isUserDataInitialized = true
        amiiboData.isAppDataInitialized = true
        loadData()
        AppData.transferId = 0
        AppData.transferData = null
    }

    private fun onUserDataSwitchClicked(isUserDataInitialized: Boolean) {
        this.isUserDataInitialized = isUserDataInitialized
        updateUserDataEnabled(isUserDataInitialized)
    }

    private fun onAppDataSwitchClicked(isAppDataInitialized: Boolean) {
        this.isAppDataInitialized = isAppDataInitialized
        updateAppDataEnabled(isUserDataInitialized && isAppDataInitialized)
    }

    private fun onSaveClicked() {
        val newAmiiboData: AmiiboData
        try {
            newAmiiboData = AmiiboData(amiiboData.array)
            newAmiiboData.isUserDataInitialized = isUserDataInitialized
            newAmiiboData.isAppDataInitialized = isUserDataInitialized && isAppDataInitialized
        } catch (e: Exception) {
            Debug.warn(e)
            showErrorDialog(R.string.fail_save_data)
            return
        }
        if (isUserDataInitialized) {
            try {
                val countryCode = (txtCountryCode.selectedItem as Map.Entry<*, *>).key
                newAmiiboData.countryCode = countryCode as Int
            } catch (e: Exception) {
                txtCountryCode.requestFocus()
                return
            }
            try {
                initializedDate?.let { newAmiiboData.initializedDate = it }
            } catch (e: Exception) {
                txtInitDate.requestFocus()
                return
            }
            try {
                modifiedDate?.let { newAmiiboData.modifiedDate = it }
            } catch (e: Exception) {
                txtModifiedDate.requestFocus()
                return
            }
            try {
                val nickname = txtNickname.text.toString()
                newAmiiboData.nickname = nickname
            } catch (e: Exception) {
                txtNickname.requestFocus()
                return
            }
            try {
                val miiName = txtMiiName.text.toString()
                newAmiiboData.miiName = miiName
            } catch (e: Exception) {
                txtMiiName.requestFocus()
                return
            }
            try {
                val miiAuthor = txtMiiAuthor.text.toString()
                newAmiiboData.miiAuthor = miiAuthor
            } catch (e: Exception) {
                txtMiiAuthor.requestFocus()
                return
            }
            try {
                val writeCounter = txtWriteCounter.text.toString().toInt()
                newAmiiboData.writeCount = writeCounter
            } catch (e: Exception) {
                txtWriteCounter.requestFocus()
                return
            }
            try {
                val serialNumber = TagArray.hexToByteArray(txtSerialNumber.text.toString())
                newAmiiboData.uID = serialNumber
            } catch (e: Exception) {
                txtSerialNumber.requestFocus()
                return
            }
            try {
                val appId = parseAppId()
                newAmiiboData.appId = appId
            } catch (e: Exception) {
                txtAppId.requestFocus()
                return
            }
            if (appDataSwitch.isChecked && null != appDataZeldaTP) {
                try {
                    onAppDataZeldaTPSaved()?.let { newAmiiboData.appData = it }
                } catch (e: Exception) {
                    Debug.verbose(e)
                    return
                }
            }
            if (appDataSwitch.isChecked && null != appDataMLPaperJam) {
                try {
                    onAppDataMLPaperJamSaved()?.let { newAmiiboData.appData = it }
                } catch (e: Exception) {
                    Debug.verbose(e)
                    return
                }
            }
            if (appDataSwitch.isChecked && null != appDataSplatoon) {
                try {
                    onAppDataSplatoonSaved()?.let { newAmiiboData.appData = it }
                } catch (e: Exception) {
                    Debug.verbose(e)
                    return
                }
            }
            if (appDataSwitch.isChecked && null != appDataSplatoon3) {
                try {
                    onAppDataSplatoon3Saved()?.let { newAmiiboData.appData = it }
                } catch (e: Exception) {
                    Debug.verbose(e)
                    return
                }
            }
            if (appDataSwitch.isChecked && null != appDataSSBU) {
                try {
                    onAppDataSSBUSaved()?.let { newAmiiboData.appData = it }
                    newAmiiboData.miiChecksum
                } catch (e: Exception) {
                    Debug.verbose(e)
                    return
                }
            }
            if (appDataSwitch.isChecked && null != appDataSSB) {
                try {
                    onAppDataSSBSaved()?.let { newAmiiboData.appData = it }
                } catch (e: Exception) {
                    Debug.verbose(e)
                    return
                }
            }
        }
        try {
            keyManager.encrypt(newAmiiboData.array).let {
                setResult(RESULT_OK, Intent(NFCIntent.ACTION_EDIT_COMPLETE)
                    .putExtra(NFCIntent.EXTRA_TAG_DATA, it)
                )
                finish()
            }
        } catch (e: Exception) {
            Debug.warn(e)
            showErrorDialog(R.string.fail_encrypt)
        }
    }

    private fun updateUserDataEnabled(isUserDataInitialized: Boolean) {
        txtCountryCode.isEnabled = isUserDataInitialized
        txtInitDate.isEnabled = isUserDataInitialized
        txtModifiedDate.isEnabled = isUserDataInitialized
        txtNickname.isEnabled = isUserDataInitialized
        txtMiiName.isEnabled = isUserDataInitialized
        txtMiiAuthor.isEnabled = isUserDataInitialized
        txtWriteCounter.isEnabled = isUserDataInitialized
        txtSerialNumber.isEnabled = isUserDataInitialized
        generateSerial.isEnabled = isUserDataInitialized
        txtAppName.isEnabled = isUserDataInitialized
        txtAppId.isEnabled = isUserDataInitialized
        appDataSwitch.isEnabled = isUserDataInitialized
        appDataFormat.isEnabled = isUserDataInitialized
        appDataTransfer.isEnabled = isUserDataInitialized
        updateAppDataEnabled(isUserDataInitialized && isAppDataInitialized)
    }

    private fun updateAppDataEnabled(isAppDataInitialized: Boolean) {
        if (null != appDataZeldaTP) onAppDataZeldaTPChecked(isAppDataInitialized)
        if (null != appDataMLPaperJam) onAppDataMLPaperJamChecked(isAppDataInitialized)
        if (null != appDataSplatoon) onAppDataSplatoonChecked(isAppDataInitialized)
        if (null != appDataSplatoon3) onAppDataSplatoon3Checked(isAppDataInitialized)
        if (null != appDataSSBU) onAppDataSSBUChecked(isAppDataInitialized)
        if (null != appDataSSB) onAppDataSSBChecked(isAppDataInitialized)
    }

    private fun loadUID() {
        txtUID.setText(TagArray.bytesToHex(amiiboData.uID))
    }

    private fun loadCountryCode() {
        val countryCode: Int = if (initialUserDataInitialized) {
            try {
                amiiboData.countryCode
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }
        var index = 0
        countryCodeAdapter?.let {
            for (i in 0 until it.count) {
                val (key) = it.getItem(i)
                if (key == countryCode) {
                    index = i
                    break
                }
            }
        }
        txtCountryCode.setSelection(index)
    }

    private fun loadInitializedDate() {
        initializedDate = if (initialUserDataInitialized) {
            try {
                amiiboData.initializedDate
            } catch (e: Exception) {
                Date()
            }
        } else {
            Date()
        }
        initializedDate?.let { updateInitializedDateView(it) }
    }

    private fun updateInitializedDateView(date: Date) {
        val text: String = try {
            getDateString(date)
        } catch (e: IllegalArgumentException) {
            getString(R.string.invalid)
        }
        txtInitDate.setText(text)
    }

    private val onInitDateSet = OnDateSetListener { _, year, month, day ->
        val c = Calendar.getInstance()
        c[Calendar.YEAR] = year
        c[Calendar.MONTH] = month
        c[Calendar.DAY_OF_MONTH] = day
        initializedDate = c.time
        updateInitializedDateView(c.time)
    }

    private fun loadModifiedDate() {
        modifiedDate = if (initialUserDataInitialized) {
            try {
                amiiboData.modifiedDate
            } catch (e: Exception) {
                Date()
            }
        } else {
            Date()
        }
        modifiedDate?.let { updateModifiedDateView(it) }
    }

    private fun updateModifiedDateView(date: Date) {
        val text: String = try {
            getDateString(date)
        } catch (e: IllegalArgumentException) {
            getString(R.string.invalid)
        }
        txtModifiedDate.setText(text)
    }

    private val onModifiedDateSet = OnDateSetListener { _, year, month, day ->
        val c = Calendar.getInstance()
        c[Calendar.YEAR] = year
        c[Calendar.MONTH] = month
        c[Calendar.DAY_OF_MONTH] = day
        modifiedDate = c.time
        updateModifiedDateView(c.time)
    }

    private fun loadNickname() {
        val nickname: String = if (initialUserDataInitialized) {
            try {
                amiiboData.nickname.trim { it <= ' ' }
            } catch (e: UnsupportedEncodingException) {
                ""
            }
        } else {
            ""
        }
        txtNickname.setText(nickname)
    }

    private fun loadMiiName() {
        val miiName: String = if (initialUserDataInitialized) {
            try {
                amiiboData.miiName.trim { it <= ' ' }
            } catch (e: UnsupportedEncodingException) {
                ""
            }
        } else {
            ""
        }
        txtMiiName.setText(miiName)
    }

    private fun loadMiiAuthor() {
        val miiAuthor: String = if (initialUserDataInitialized) {
            try {
                amiiboData.miiAuthor.trim { it <= ' ' }
            } catch (e: UnsupportedEncodingException) {
                ""
            }
        } else {
            ""
        }
        txtMiiAuthor.setText(miiAuthor)
    }

    private fun loadAppId() {
        appId = if (initialUserDataInitialized) amiiboData.appId else null
        updateAppIdView(appId)
        updateAppNameView()
        updateAppDataView(appId)
    }

    private fun updateAppIdView(appId: Int?) {
        txtAppId.setText(appId?.let { String.format("%08X", it) } ?: "")
    }

    @Throws(IOException::class, NumberFormatException::class)
    private fun parseAppId(): Int {
        var text = txtAppId.unMaskedText
        return if (null != text) {
            text = text.trim { it <= ' ' }
            if (text.length != 8) {
                throw IOException(getString(R.string.error_length))
            }
            try {
                text.toLong(16).toInt()
            } catch (e: NumberFormatException) {
                throw NumberFormatException(getString(R.string.error_input))
            }
        } else {
            throw IOException(getString(R.string.invalid_app_data))
        }
    }

    private val onAppNameSelected: AdapterView.OnItemSelectedListener =
        object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
            if (ignoreAppNameSelected) {
                ignoreAppNameSelected = false
                return
            }
            adapterView.getItemAtPosition(i)?.let {
                appId = (it as Map.Entry<*, *>).key as Int?
            }
            updateAppIdView(appId)
            updateAppDataView(appId)
        }

        override fun onNothingSelected(adapterView: AdapterView<*>?) {}
    }

    private fun updateAppNameView() {
        var index = 0
        appIdAdapter?.let {
            for (i in 0 until it.count) {
                if (it.getItem(i).key == appId) {
                    index = i
                    break
                }
            }
        }
        if (txtAppName.selectedItemPosition != index) {
            ignoreAppNameSelected = true
            txtAppName.setSelection(index)
        }
    }

    private val onAppIdChange: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
        override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
        override fun afterTextChanged(editable: Editable) {
            try {
                appId = parseAppId()
                txtAppId.error = null
            } catch (e: Exception) {
                appId = null
                txtAppId.error = e.message
            }
            updateAppNameView()
            updateAppDataView(appId)
        }
    }

    private fun updateAppDataView(appId: Int?) {
        appDataViewChibiRobo.isGone = true
        appDataChibiRobo = null
        appDataViewZeldaTP.isGone = true
        appDataZeldaTP = null
        appDataViewMHStories.isGone = true
        appDataMHStories = null
        appDataViewMLPaperJam.isGone = true
        appDataMLPaperJam = null
        appDataViewMLSuperstarSaga.isGone = true
        appDataMLSuperstarSaga = null
        appDataViewMSSuperstars.isGone = true
        appDataMSSuperstars = null
        appDataViewMarioTennis.isGone = true
        appDataMarioTennis = null
        appDataViewPikmin.isGone = true
        appDataPikmin = null
        appDataViewSplatoon.isGone = true
        appDataSplatoon = null
        appDataViewSplatoon3.isGone = true
        appDataSplatoon3 = null
        appDataViewSSB.isGone = true
        appDataSSB = null
        appDataViewSSBU.isGone = true
        appDataSSBU = null
        appId?.let {
            when (it) {
                AppId.ChibiRobo -> {
                    appDataViewChibiRobo.isVisible = true
                    enableAppDataChibiRobo(amiiboData.appData)
                }
                AppId.ZeldaTP -> {
                    appDataViewZeldaTP.isVisible = true
                    enableAppDataZeldaTP(amiiboData.appData)
                }
                AppId.MHStories -> {
                    appDataViewMHStories.isVisible = true
                    enableAppDataMHStories(amiiboData.appData)
                }
                AppId.MLSuperstarSaga -> {
                    appDataViewMLSuperstarSaga.isVisible = true
                    enableAppDataMLSuperstarSaga(amiiboData.appData)
                }
                AppId.MSSuperstars -> {
                    appDataViewMSSuperstars.isVisible = true
                    enableAppDataMSSuperstars(amiiboData.appData)
                }
                AppId.MarioTennis -> {
                    appDataViewMarioTennis.isVisible = true
                    enableAppDataMarioTennis(amiiboData.appData)
                }
                AppId.Pikmin -> {
                    appDataViewPikmin.isVisible = true
                    enableAppDataPikmin(amiiboData.appData)
                }
                AppId.MLPaperJam -> {
                    appDataViewMLPaperJam.isVisible = true
                    enableAppDataMLPaperJam(amiiboData.appData)
                }
                AppId.Splatoon -> {
                    appDataViewSplatoon.isVisible = true
                    enableAppDataSplatoon(amiiboData.appData)
                }
                AppId.Splatoon3 -> {
                    appDataViewSplatoon3.isVisible = true
                    enableAppDataSplatoon3(amiiboData.appData)
                }
                AppId.SSB -> {
                    appDataViewSSB.isVisible = true
                    enableAppDataSSB(amiiboData.appData)
                }
                AppId.SSBU -> {
                    appDataViewSSBU.isVisible = true
                    enableAppDataSSBU(amiiboData.appData)
                }
            }
        }
    }

    private fun loadWriteCounter() {
        val writeCounter: Int = if (initialUserDataInitialized) {
            amiiboData.writeCount
        } else {
            0
        }
        txtWriteCounter.setText(writeCounter.toString())
    }

    private fun loadSerialNumber() {
        txtSerialNumber.tag = txtSerialNumber.keyListener
        txtSerialNumber.keyListener = null
        val value = amiiboData.uID
        txtSerialNumber.setText(TagArray.bytesToHex(value))
    }

    private class CountryCodesAdapter(data: HashMap<Int, String>) : BaseAdapter(), Filterable {
        var data: ArrayList<Map.Entry<Int, String>>
        override fun getCount(): Int {
            return data.size
        }

        override fun getItem(i: Int): Map.Entry<Int, String> {
            return data[i]
        }

        override fun getItemId(i: Int): Long {
            return data[i].key.toLong()
        }

        override fun getDropDownView(position: Int, view: View?, parent: ViewGroup): View? {
            var viewItem = view
            if (null == viewItem) {
                viewItem = LayoutInflater.from(parent.context)
                    .inflate(android.R.layout.simple_dropdown_item_1line, parent, false)
            }
            viewItem?.findViewById<TextView>(android.R.id.text1)?.text = getItem(position).value
            return viewItem
        }

        override fun getView(position: Int, view: View?, parent: ViewGroup): View {
            return view ?: LayoutInflater.from(parent.context).inflate(
                R.layout.spinner_text, parent, false
            ).apply {
                (this as TextView).text = getItem(position).value
            }
        }

        override fun getFilter(): Filter {
            return filter
        }

        private var filter: Filter = object : Filter() {
            override fun performFiltering(charSequence: CharSequence): FilterResults {
                val filterResults = FilterResults()
                filterResults.values = data
                filterResults.count = data.size
                return filterResults
            }

            override fun publishResults(charSequence: CharSequence?, filterResults: FilterResults) {}
        }

        init {
            this.data = ArrayList(data.entries)
            if (Version.isNougat) {
                Collections.sort(this.data, java.util.Map.Entry.comparingByKey())
            } else {
                this.data.sortWith { (key): Map.Entry<Int, String>, (key1): Map.Entry<Int, String> ->
                    key.compareTo(key1)
                }
            }
        }
    }

    private class AppIdAdapter(data: HashMap<Int, String>) : BaseAdapter() {
        var data: ArrayList<Map.Entry<Int, String>>

        init {
            this.data = ArrayList(data.entries)
            if (Version.isNougat) {
                Collections.sort(this.data, java.util.Map.Entry.comparingByKey())
            } else {
                this.data.sortWith { (key): Map.Entry<Int, String>, (key1): Map.Entry<Int, String> ->
                    key.compareTo(key1)
                }
            }
        }

        override fun getCount(): Int {
            return data.size
        }

        override fun getItem(i: Int): Map.Entry<Int, String> {
            return data[i]
        }

        override fun getItemId(i: Int): Long {
            return data[i].key.toLong()
        }

        override fun getDropDownView(position: Int, view: View?, parent: ViewGroup): View? {
            return view ?: LayoutInflater.from(parent.context).inflate(
                android.R.layout.simple_dropdown_item_1line, parent, false
            ).apply {
                findViewById<TextView>(android.R.id.text1)?.text = getItem(position).value
            }
        }

        override fun getView(position: Int, view: View?, parent: ViewGroup): View {
            return view ?: LayoutInflater.from(parent.context).inflate(
                R.layout.spinner_text, parent, false
            ).apply {
                (this as TextView).text = getItem(position).value
            }
        }
    }

    private fun setListForSpinner(control: Spinner?, list: Int) {
        control?.adapter = ArrayAdapter.createFromResource(
            this, list, R.layout.spinner_text
        ).apply {
            setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
        }
    }

    fun onHeartsUpdate(hearts: Int) {
        txtHearts2?.let { spinner ->
            txtHearts1?.let {
                try {
                    spinner.isEnabled = hearts < 20
                    if (!spinner.isEnabled) spinner.setSelection(0)
                    try {
                        appDataZeldaTP?.checkHearts(hearts * 4)
                        it.error = null
                    } catch (e: Exception) {
                        it.error = getString(R.string.error_min_max, 0, 20)
                    }
                } catch (e: NumberFormatException) {
                    it.error = getString(R.string.error_min_max, 0, 20)
                    spinner.isEnabled = it.isEnabled == true
                }
            }
        }
    }

    private fun setEffectValue(spinner: Spinner, value: Int) {
        var spinValue = value
        if (spinValue == 0xFF) spinValue = 0 else spinValue++
        if (spinValue > spinner.adapter.count) spinValue = 0
        spinner.setSelection(spinValue)
    }

    private fun getEffectValue(spinner: Spinner?): Int {
        return spinner?.let {
            var value = it.selectedItemPosition
            if (value == 0) value = 0xFF else value--
            value
        } ?: 0xFF
    }

    private fun enableAppDataChibiRobo(appData: ByteArray) {
        try {
            appDataChibiRobo = AppDataChibiRobo(appData)
        } catch (e: Exception) {
            appDataViewChibiRobo.isGone = true
            return
        }
        onAppDataChibiRoboChecked(isAppDataInitialized)
    }

    private fun enableAppDataZeldaTP(appData: ByteArray) {
        try {
            appDataZeldaTP = AppDataZeldaTP(appData)
        } catch (e: Exception) {
            appDataViewZeldaTP.isGone = true
            return
        }
        var level = 40
        var hearts: Int = AppDataZeldaTP.HEARTS_MAX_VALUE
        if (initialAppDataInitialized) {
            appDataZeldaTP?.let {
                level = try {
                    it.level
                } catch (e: Exception) { 40 }
                hearts = try {
                    it.hearts
                } catch (e: Exception) { AppDataZeldaTP.HEARTS_MAX_VALUE }
            }
        }
        txtHearts1 = findViewById<EditText>(R.id.txtHearts1).apply {
            setText((hearts / 4).toString())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                    onHeartsUpdate(charSequence.toString().toInt())
                }

                override fun afterTextChanged(editable: Editable) {}
            })
        }
        txtHearts2 = findViewById<Spinner>(R.id.txtHearts2).also {
            setListForSpinner(it, R.array.editor_tp_hearts)
        }. apply {
            setSelection(hearts % 4)
            isEnabled = hearts / 4 < 20
        }
        txtLevelZeldaTP = findViewById<EditText>(R.id.txtLevelZeldaTP).apply {
            setText(level.toString())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                    error = try {
                        try {
                            appDataZeldaTP?.checkLevel(charSequence.toString().toInt())
                            null
                        } catch (e: Exception) {
                            getString(R.string.error_min_max, 0, 40)
                        }
                    } catch (e: NumberFormatException) {
                        getString(R.string.error_min_max, 0, 40)
                    }
                }

                override fun afterTextChanged(editable: Editable) {}
            })
        }
        onAppDataZeldaTPChecked(isAppDataInitialized)
    }

    private fun enableAppDataMHStories(appData: ByteArray) {
        try {
            appDataMHStories = AppDataMHStories(appData)
        } catch (e: Exception) {
            appDataViewMHStories.isGone = true
            return
        }
        onAppDataMHStoriesChecked(isAppDataInitialized)
    }

    private fun enableAppDataMLPaperJam(appData: ByteArray) {
        try {
            appDataMLPaperJam = AppDataMLPaperJam(appData)
        } catch (e: Exception) {
            appDataViewMLPaperJam.isGone = true
            return
        }
        buttonUnlock = findViewById<AppCompatButton>(R.id.unlock_sparkle_cards).apply {
            setOnClickListener {
                appDataMLPaperJam?.let { appData ->
                    appData.unlockSparkleCards()
                    it.isEnabled = !appData.checkSparkleCards()
                }
            }
        }
        onAppDataMLPaperJamChecked(isAppDataInitialized)
    }

    private fun enableAppDataMLSuperstarSaga(appData: ByteArray) {
        try {
            appDataMLSuperstarSaga = AppDataMLSuperstarSaga(appData)
        } catch (e: Exception) {
            appDataViewMLSuperstarSaga.isGone = true
            return
        }
        onAppDataMLSuperstarSagaChecked(isAppDataInitialized)
    }

    private fun enableAppDataMSSuperstars(appData: ByteArray) {
        try {
            appDataMSSuperstars = AppDataMSSuperstars(appData)
        } catch (e: Exception) {
            appDataViewMSSuperstars.isGone = true
            return
        }
        onAppDataMSSuperstarsChecked(isAppDataInitialized)
    }

    private fun enableAppDataMarioTennis(appData: ByteArray) {
        try {
            appDataMarioTennis = AppDataMarioTennis(appData)
        } catch (e: Exception) {
            appDataViewMarioTennis.isGone = true
            return
        }
        onAppDataMarioTennisChecked(isAppDataInitialized)
    }

    private fun enableAppDataPikmin(appData: ByteArray) {
        try {
            appDataPikmin = AppDataPikmin(appData)
        } catch (e: Exception) {
            appDataViewPikmin.isGone = true
            return
        }
        onAppDataPikminChecked(isAppDataInitialized)
    }

    private fun enableAppDataSplatoon(appData: ByteArray) {
        try {
            appDataSplatoon = AppDataSplatoon(appData)
        } catch (e: Exception) {
            appDataViewSplatoon.isGone = true
            return
        }
        buttonInject = findViewById<AppCompatButton>(R.id.inject_game_data).apply {
            setOnClickListener {
                appDataSplatoon?.let { appData ->
                    appData.saveData
                    it.isEnabled = !appData.hasUnlockData()
                }
            }
        }
        onAppDataSplatoonChecked(isAppDataInitialized)
    }

    private fun enableAppDataSplatoon3(appData: ByteArray) {
        try {
            appDataSplatoon3 = AppDataSplatoon3(appData)
        } catch (e: Exception) {
            appDataViewSplatoon3.isGone = true
            return
        }
        buttonInject3 = findViewById<AppCompatButton>(R.id.inject_game_data_3).apply {
            setOnClickListener {
                appDataSplatoon3?.let { appData ->
                    appData.saveData
                    it.isEnabled = !appData.hasUnlockData()
                }
            }
        }
        onAppDataSplatoon3Checked(isAppDataInitialized)
    }

    private fun enableAppDataSSB(appData: ByteArray) {
        try {
            appDataSSB = AppDataSSB(appData)
        } catch (e: Exception) {
            appDataViewSSB.isGone = true
            return
        }
        var appearance: Int = AppDataSSB.APPEARANCE_MIN_VALUE
        var level: Int = AppDataSSB.LEVEL_MAX_VALUE
        var statAttack: Int = AppDataSSB.STATS_MAX_VALUE
        var statDefense: Int = AppDataSSB.STATS_MAX_VALUE
        var statSpeed: Int = AppDataSSB.STATS_MAX_VALUE
        var specialNeutral: Int = AppDataSSB.SPECIAL_MIN_VALUE
        var specialSide: Int = AppDataSSB.SPECIAL_MIN_VALUE
        var specialUp: Int = AppDataSSB.SPECIAL_MIN_VALUE
        var specialDown: Int = AppDataSSB.SPECIAL_MIN_VALUE
        var bonusEffect1: Int = AppDataSSB.BONUS_MAX_VALUE
        var bonusEffect2: Int = AppDataSSB.BONUS_MAX_VALUE
        var bonusEffect3: Int = AppDataSSB.BONUS_MAX_VALUE
        if (initialAppDataInitialized) {
            appDataSSB?.let {
                appearance = try {
                    it.appearence
                } catch (e: Exception) { AppDataSSB.APPEARANCE_MIN_VALUE }
                level = try {
                    it.level
                } catch (e: Exception) { AppDataSSB.LEVEL_MAX_VALUE }
                statAttack = try {
                    it.statAttack
                } catch (e: Exception) { AppDataSSB.STATS_MAX_VALUE }
                statDefense = try {
                    it.statDefense
                } catch (e: Exception) { AppDataSSB.STATS_MAX_VALUE }
                statSpeed = try {
                    it.statSpeed
                } catch (e: Exception) { AppDataSSB.STATS_MAX_VALUE }
                specialNeutral = try {
                    it.specialNeutral
                } catch (e: Exception) { AppDataSSB.SPECIAL_MIN_VALUE }
                specialSide = try {
                    it.specialSide
                } catch (e: Exception) { AppDataSSB.SPECIAL_MIN_VALUE }
                specialUp = try {
                    it.specialUp
                } catch (e: Exception) { AppDataSSB.SPECIAL_MIN_VALUE }
                specialDown = try {
                    it.specialDown
                } catch (e: Exception) { AppDataSSB.SPECIAL_MIN_VALUE }
                bonusEffect1 = try {
                    it.bonusEffect1
                } catch (e: Exception) { AppDataSSB.BONUS_MAX_VALUE }
                bonusEffect2 = try {
                    it.bonusEffect2
                } catch (e: Exception) { AppDataSSB.BONUS_MAX_VALUE }
                bonusEffect3 = try {
                    it.bonusEffect3
                } catch (e: Exception) { AppDataSSB.BONUS_MAX_VALUE }
            }
        } else {
            appearance = AppDataSSB.APPEARANCE_MIN_VALUE
            level = AppDataSSB.LEVEL_MAX_VALUE
            statAttack = AppDataSSB.STATS_MAX_VALUE
            statDefense = AppDataSSB.STATS_MAX_VALUE
            statSpeed = AppDataSSB.STATS_MAX_VALUE
            specialNeutral = AppDataSSB.SPECIAL_MIN_VALUE
            specialSide = AppDataSSB.SPECIAL_MIN_VALUE
            specialUp = AppDataSSB.SPECIAL_MIN_VALUE
            specialDown = AppDataSSB.SPECIAL_MIN_VALUE
            bonusEffect1 = AppDataSSB.BONUS_MAX_VALUE
            bonusEffect2 = AppDataSSB.BONUS_MAX_VALUE
            bonusEffect3 = AppDataSSB.BONUS_MAX_VALUE
        }
        spnAppearance = findViewById<Spinner>(R.id.spnAppearance).also {
            setListForSpinner(it, R.array.ssb_appearance_values)
        }.apply {
            setSelection(appearance)
        }
        txtLevelSSB = findViewById<EditText>(R.id.txtLevelSSB).apply {
            setText(level.toString())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                    error = try {
                        val text = charSequence.toString().toInt()
                        if (text < 1 || text > 50)
                            getString(R.string.error_min_max, 1, 50)
                        else null
                    } catch (e: NumberFormatException) {
                        getString(R.string.error_min_max, 1, 50)
                    }
                }

                override fun afterTextChanged(editable: Editable) {}
            })
        }
        spnSpecialNeutral = findViewById<Spinner>(R.id.spnSpecial1).also {
            setListForSpinner(it, R.array.ssb_specials_values)
        }.apply {
            setSelection(specialNeutral)
        }
        spnSpecialSide = findViewById<Spinner>(R.id.spnSpecial2).also {
            setListForSpinner(it, R.array.ssb_specials_values)
        }.apply {
            setSelection(specialSide)
        }
        spnSpecialUp = findViewById<Spinner>(R.id.spnSpecial3).also {
            setListForSpinner(it, R.array.ssb_specials_values)
        }.apply {
            setSelection(specialUp)
        }
        spnSpecialDown = findViewById<Spinner>(R.id.spnSpecial4).also {
            setListForSpinner(it, R.array.ssb_specials_values)
        }.apply {
            setSelection(specialDown)
        }
        txtStatAttack = findViewById<EditText>(R.id.txtStatAttack).apply {
            setText(statAttack.toString())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                    error = try {
                        val text = charSequence.toString().toInt()
                        try {
                            appDataSSB?.checkStat(text)
                            null
                        } catch (e: Exception) {
                            getString(R.string.error_min_max, -200, 200)
                        }
                    } catch (e: NumberFormatException) {
                        getString(R.string.error_min_max, -200, 200)
                    }
                }

                override fun afterTextChanged(editable: Editable) {}
            })
        }
        txtStatDefense = findViewById<EditText>(R.id.txtStatDefense).apply {
            setText(statDefense.toString())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                    error = try {
                        try {
                            appDataSSB?.checkStat(charSequence.toString().toInt())
                            null
                        } catch (e: Exception) {
                            getString(R.string.error_min_max, -200, 200)
                        }
                    } catch (e: NumberFormatException) {
                        getString(R.string.error_min_max, -200, 200)
                    }
                }

                override fun afterTextChanged(editable: Editable) {}
            })
        }
        txtStatSpeed = findViewById<EditText>(R.id.txtStatSpeed).apply {
            setText(statSpeed.toString())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                    error = try {
                        try {
                            appDataSSB?.checkStat(charSequence.toString().toInt())
                            null
                        } catch (e: Exception) {
                            getString(R.string.error_min_max, -200, 200)
                        }
                    } catch (e: NumberFormatException) {
                        getString(R.string.error_min_max, -200, 200)
                    }
                }

                override fun afterTextChanged(editable: Editable) {}
            })
        }
        spnEffect1 = findViewById<Spinner>(R.id.spnEffect1).also {
            setListForSpinner(it, R.array.ssb_bonus_effects)
            setEffectValue(it, bonusEffect1)
        }
        spnEffect2 = findViewById<Spinner>(R.id.spnEffect2).also {
            setListForSpinner(it, R.array.ssb_bonus_effects)
            setEffectValue(it, bonusEffect2)
        }
        spnEffect3 = findViewById<Spinner>(R.id.spnEffect3).also {
            setListForSpinner(it, R.array.ssb_bonus_effects)
            setEffectValue(it, bonusEffect3)
        }
        onAppDataSSBChecked(isAppDataInitialized)
    }

    @Suppress("unused")
    private fun enableAppDataSSBU(appData: ByteArray) {
        try {
            appDataSSBU = AppDataSSBU(appData)
        } catch (e: Exception) {
            appDataViewSSBU.isGone = true
            return
        }
        var appearance: Int = AppDataSSBU.APPEARANCE_MIN_VALUE
        var level: Int = AppDataSSBU.LEVEL_MIN_VALUE
        var giftCount = 0
        var levelCPU: Int = AppDataSSBU.LEVEL_MIN_VALUE
        var statAttack = 0
        var statDefense = 0
        var statSpeed = 0
        var specialNeutral = 0
        var specialSide = 0
        var specialUp = 0
        var specialDown = 0
        var bonusEffect1: Int = 0xFF
        var bonusEffect2: Int = 0xFF
        var bonusEffect3: Int = 0xFF
        if (initialAppDataInitialized) {
            appDataSSBU?.let {
                appearance = try {
                   it.appearence
                } catch (e: Exception) { AppDataSSBU.APPEARANCE_MIN_VALUE }
                level = try {
                    it.level
                } catch (e: Exception) { AppDataSSBU.LEVEL_MIN_VALUE }
                giftCount = try {
                    it.giftCount
                } catch (e: Exception) { 0 }
                levelCPU = try {
                    it.levelCPU
                } catch (e: Exception) { AppDataSSBU.LEVEL_MIN_VALUE }
                statAttack = try {
                    it.statAttack
                } catch (e: Exception) { 0 }
                statDefense = try {
                    it.statDefense
                } catch (e: Exception) { 0 }
                statSpeed = try {
                    it.statSpeed
                } catch (e: Exception) { 0 }
            }
        }
        spnAppearanceU = findViewById<Spinner>(R.id.spnAppearanceU).also {
            setListForSpinner(it, R.array.ssb_appearance_values)
        }.apply {
            setSelection(appearance)
        }
        txtLevelSSBU = findViewById<EditText>(R.id.txtLevelSSBU).apply {
            setText(level.toString())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                    error = try {
                        val text = charSequence.toString().toInt()
                        if (text < 1 || text > 50)
                            getString(R.string.error_min_max, 1, 50)
                        else null
                    } catch (e: NumberFormatException) {
                        getString(R.string.error_min_max, 1, 50)
                    }
                }

                override fun afterTextChanged(editable: Editable) {}
            })
        }
        txtGiftCount = findViewById<EditText>(R.id.txtGiftCount).apply {
            setText(giftCount.toString())
        }
        txtLevelCPU = findViewById<EditText>(R.id.txtLevelCPU).apply {
            setText(levelCPU.toString())
        }
        txtStatAttackU = findViewById<EditText>(R.id.txtStatAttackU).apply {
            setText(statAttack.toString())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                    error = try {
                        try {
                            appDataSSBU?.checkStat(charSequence.toString().toInt())
                            null
                        } catch (e: Exception) {
                            getString(R.string.error_min_max, -0, 2500)
                        }
                    } catch (e: NumberFormatException) {
                        getString(R.string.error_min_max, 0, 2500)
                    }
                }

                override fun afterTextChanged(editable: Editable) {}
            })
        }
        txtStatDefenseU = findViewById<EditText>(R.id.txtStatDefenseU).apply {
            setText(statDefense.toString())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                    error = try {
                        try {
                            appDataSSBU?.checkStat(charSequence.toString().toInt())
                            null
                        } catch (e: Exception) {
                            getString(R.string.error_min_max, 0, 2500)
                        }
                    } catch (e: NumberFormatException) {
                        getString(R.string.error_min_max, 0, 2500)
                    }
                }

                override fun afterTextChanged(editable: Editable) {}
            })
        }
        txtStatSpeedU = findViewById<EditText>(R.id.txtStatSpeedU).apply {
            setText(statSpeed.toString())
        }
    }

    private fun onAppDataChibiRoboChecked(enabled: Boolean) {

    }

    private fun onAppDataZeldaTPChecked(enabled: Boolean) {
        txtHearts1?.let {
            it.isEnabled = enabled
            onHeartsUpdate(it.text.toString().toInt())
        }
        txtLevelZeldaTP?.isEnabled = enabled
    }

    private fun onAppDataMHStoriesChecked(enabled: Boolean) {

    }

    private fun onAppDataMLPaperJamChecked(enabled: Boolean) {
        appDataMLPaperJam?.let { appData -> buttonUnlock?.let {
            it.isEnabled = enabled && !appData.checkSparkleCards()
        } }
    }

    private fun onAppDataMLSuperstarSagaChecked(enabled: Boolean) {

    }

    private fun onAppDataMSSuperstarsChecked(enabled: Boolean) {

    }

    private fun onAppDataMarioTennisChecked(enabled: Boolean) {

    }

    private fun onAppDataPikminChecked(enabled: Boolean) {

    }

    private fun onAppDataSplatoonChecked(enabled: Boolean) {
        appDataSplatoon?.let { appData -> buttonInject?.let {
            it.isEnabled = enabled && !appData.hasUnlockData()
        } }
    }

    private fun onAppDataSplatoon3Checked(enabled: Boolean) {
        appDataSplatoon3?.let { appData ->buttonInject3?.let {
            it.isEnabled = enabled && !appData.hasUnlockData()
        } }
    }

    private fun onAppDataSSBChecked(enabled: Boolean) {
        spnAppearance?.let {
            it.isEnabled = enabled
            txtLevelSSB?.isEnabled = enabled
            spnSpecialNeutral?.isEnabled = enabled
            spnSpecialSide?.isEnabled = enabled
            spnSpecialUp?.isEnabled = enabled
            spnSpecialDown?.isEnabled = enabled
            txtStatAttack?.isEnabled = enabled
            txtStatDefense?.isEnabled = enabled
            txtStatSpeed?.isEnabled = enabled
            spnEffect1?.isEnabled = enabled
            spnEffect2?.isEnabled = enabled
            spnEffect3?.isEnabled = enabled
        }
    }

    private fun onAppDataSSBUChecked(enabled: Boolean) {
        spnAppearanceU?.let {
            it.isEnabled = enabled
            txtLevelSSBU?.isEnabled = enabled
            // txtGiftCount?.isEnabled = enabled
            txtLevelCPU?.isEnabled = enabled
            txtStatAttackU?.isEnabled = enabled
            txtStatDefenseU?.isEnabled = enabled
        }
    }

    @Throws(NumberFormatException::class)
    private fun onAppDataZeldaTPSaved(): ByteArray? {
        return appDataZeldaTP?.apply {
            this.level = try {
                txtLevelZeldaTP?.text.toString().toInt()
            } catch (e: NumberFormatException) {
                txtLevelZeldaTP?.requestFocus()
                throw e
            }
            try {
                val hearts1 = txtHearts1?.text.toString().toInt()
                txtHearts2?.selectedItemPosition?.let { this.hearts = hearts1 * 4 + it }
            } catch (e: NumberFormatException) {
                txtHearts1?.requestFocus()
                throw e
            }
        }?.array
    }

    private fun onAppDataMLPaperJamSaved(): ByteArray? {
        return appDataMLPaperJam?.array
    }

    private fun onAppDataSplatoonSaved(): ByteArray? {
        return appDataSplatoon?.array
    }

    private fun onAppDataSplatoon3Saved(): ByteArray? {
        return appDataSplatoon3?.array
    }

    @Throws(Exception::class)
    private fun onAppDataSSBSaved(): ByteArray? {
        return appDataSSB?.apply {
            this.appearence = try {
                spnAppearance?.selectedItemPosition ?: appearence
            } catch (e: NumberFormatException) {
                spnAppearance?.requestFocus()
                throw e
            }
            try {
                val level = txtLevelSSB?.text.toString().toInt()
                val oldLevel: Int? = try {
                    this.level
                } catch (e: Exception) { null }

                // level is a granular value, so we don't want to overwrite it halfway through a level
                if (null == oldLevel || level != oldLevel) this.level = level
            } catch (e: NumberFormatException) {
                txtLevelSSB?.requestFocus()
                throw e
            }
            this.specialNeutral = try {
                spnSpecialNeutral?.selectedItemPosition ?: specialNeutral
            } catch (e: NumberFormatException) {
                spnSpecialNeutral?.requestFocus()
                throw e
            }
            this.specialSide = try {
                spnSpecialSide?.selectedItemPosition ?: specialSide
            } catch (e: NumberFormatException) {
                spnSpecialSide?.requestFocus()
                throw e
            }
            this.specialUp = try {
                spnSpecialUp?.selectedItemPosition ?: specialUp
            } catch (e: NumberFormatException) {
                spnSpecialUp?.requestFocus()
                throw e
            }
            this.specialDown = try {
                spnSpecialDown?.selectedItemPosition ?: specialDown
            } catch (e: NumberFormatException) {
                spnSpecialDown?.requestFocus()
                throw e
            }
            this.statAttack = try {
                txtStatAttack?.text.toString().toInt()
            } catch (e: NumberFormatException) {
                txtStatAttack?.requestFocus()
                throw e
            }
            this.statDefense = try {
                txtStatDefense?.text.toString().toInt()
            } catch (e: NumberFormatException) {
                txtStatDefense?.requestFocus()
                throw e
            }
            this.statSpeed = try {
                txtStatSpeed?.text.toString().toInt()
            } catch (e: NumberFormatException) {
                txtStatSpeed?.requestFocus()
                throw e
            }
            this.bonusEffect1 = try {
                getEffectValue(spnEffect1)
            } catch (e: NumberFormatException) {
                spnEffect1?.requestFocus()
                throw e
            }
            this.bonusEffect2 = try {
                getEffectValue(spnEffect2)
            } catch (e: NumberFormatException) {
                spnEffect2?.requestFocus()
                throw e
            }
            this.bonusEffect3 = try {
                getEffectValue(spnEffect3)
            } catch (e: NumberFormatException) {
                spnEffect3?.requestFocus()
                throw e
            }
        }?.array
    }

    @Throws(Exception::class)
    private fun onAppDataSSBUSaved(): ByteArray? {
        return appDataSSBU?.apply {
            this.appearence = try {
                spnAppearanceU?.selectedItemPosition ?: appearence
            } catch (e: NumberFormatException) {
                spnAppearanceU?.requestFocus()
                throw e
            }
            try {
                val level = txtLevelSSBU?.text.toString().toInt()
                val oldLevel: Int? = try {
                    this.level
                } catch (e: Exception) { null }

                // level is a granular value, so we don't want to overwrite it halfway through a level
                if (null == oldLevel || level != oldLevel) this.level = level
            } catch (e: NumberFormatException) {
                txtLevelSSBU?.requestFocus()
                throw e
            }
            this.statAttack = try {
                txtStatAttackU?.text.toString().toInt()
            } catch (e: NumberFormatException) {
                txtStatAttackU?.requestFocus()
                throw e
            }
            this.statDefense = try {
                txtStatDefenseU?.text.toString().toInt()
            } catch (e: NumberFormatException) {
                txtStatDefenseU?.requestFocus()
                throw e
            }
            checksum
        }?.array
    }

    private fun showErrorDialog(msgRes: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.error_caps)
            .setMessage(msgRes)
            .setPositiveButton(R.string.close) { _: DialogInterface?, _: Int -> finish() }
            .show()
        setResult(RESULT_OK, Intent(NFCIntent.ACTION_FIX_BANK_DATA))
    }
}