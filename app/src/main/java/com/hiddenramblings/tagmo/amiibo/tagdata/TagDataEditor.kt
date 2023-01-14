package com.hiddenramblings.tagmo.amiibo.tagdata

import android.app.DatePickerDialog
import android.app.DatePickerDialog.OnDateSetListener
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
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
import com.hiddenramblings.tagmo.amiibo.tagdata.AppData.Companion.appIds
import com.hiddenramblings.tagmo.eightbit.io.Debug
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
    private lateinit var appDataViewZeldaTP: LinearLayout
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
    private lateinit var keyManager: KeyManager
    private var amiiboManager: AmiiboManager? = null
    private lateinit var amiiboData: AmiiboData

    private var initialUserDataInitialized = false
    private var isAppDataInitialized = false
    private var initialAppDataInitialized = false
    private var isUserDataInitialized = false
    private var initializedDate: Date? = null
    private var modifiedDate: Date? = null
    private var appId: Int? = null
    private val appDataChibiRobo: AppDataChibiRobo? = null
    private var txtHearts1: EditText? = null
    private var txtHearts2: Spinner? = null
    private var txtLevelZeldaTP: EditText? = null
    private var appDataZeldaTP: AppDataZeldaTP? = null
    private val appDataMHStories: AppDataMHStories? = null
    private val appDataMLPaperJam: AppDataMLPaperJam? = null
    private val appDataMLSuperstarSaga: AppDataMLSuperstarSaga? = null
    private val appDataMSSuperstars: AppDataMSSuperstars? = null
    private val appDataMarioTennis: AppDataMarioTennis? = null
    private val appDataPikmin: AppDataPikmin? = null
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
    private var appDataSSBU: AppDataSSBU? = null

    private val loadingScope = CoroutineScope(Dispatchers.Main + Job())

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tag_data)
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        keyManager = KeyManager(this)
        if (keyManager.isKeyMissing) {
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
        txtCountryCode = findViewById(R.id.txtCountryCode)
        txtInitDate = findViewById(R.id.txtInitDate)
        txtModifiedDate = findViewById(R.id.txtModifiedDate)
        txtNickname = findViewById(R.id.txtNickname)
        txtMiiName = findViewById(R.id.txtMiiName)
        txtMiiAuthor = findViewById(R.id.txtMiiAuthor)
        txtWriteCounter = findViewById(R.id.txtWriteCounter)
        txtSerialNumber = findViewById(R.id.txtSerialNumber)
        txtAppName = findViewById(R.id.txtAppName)
        txtAppId = findViewById(R.id.txtAppId)
        appDataSwitch = findViewById(R.id.appDataSwitch)
        userDataSwitch = findViewById(R.id.userDataSwitch)
        generateSerial = findViewById(R.id.random_serial)
        appDataViewZeldaTP = findViewById(R.id.appDataZeldaTP)
        appDataViewSplatoon = findViewById(R.id.appDataSplatoon)
        appDataViewSplatoon3 = findViewById(R.id.appDataSplatoon3)
        appDataViewSSB = findViewById(R.id.appDataSSB)
        appDataViewSSBU = findViewById(R.id.appDataSSBU)
        appDataFormat = findViewById(R.id.format_app_data)
        appDataTransfer = findViewById(R.id.transfer_app_data)

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
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setTitle(R.string.edit_props)
        toolbar.inflateMenu(R.menu.save_menu)
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { item: MenuItem ->
            if (item.itemId == R.id.mnu_save) {
                onSaveClicked()
                return@setOnMenuItemClickListener true
            }
            false
        }
        userDataSwitch.setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->
            onUserDataSwitchClicked(checked)
        }
        appDataSwitch.setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->
            onAppDataSwitchClicked(checked)
        }
        findViewById<View>(R.id.random_serial).setOnClickListener {
            txtSerialNumber.setText(TagArray.bytesToHex(Foomiibo().generateRandomUID()))
        }
        findViewById<View>(R.id.txtInitDate).setOnClickListener {
            val c = Calendar.getInstance()
            c.time = initializedDate!!
            val datePickerDialog = DatePickerDialog(
                this@TagDataEditor,
                onInitDateSet,
                c[Calendar.YEAR],
                c[Calendar.MONTH],
                c[Calendar.DAY_OF_MONTH]
            )
            datePickerDialog.show()
        }
        findViewById<View>(R.id.txtModifiedDate).setOnClickListener {
            val c = Calendar.getInstance()
            c.time = modifiedDate!!
            val datePickerDialog = DatePickerDialog(
                this@TagDataEditor,
                onModifiedDateSet,
                c[Calendar.YEAR],
                c[Calendar.MONTH],
                c[Calendar.DAY_OF_MONTH]
            )
            datePickerDialog.show()
        }
        updateAmiiboView(tagData)
        loadingScope.launch {
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
        txtCountryCode.adapter = countryCodeAdapter
        appIdAdapter = AppIdAdapter(appIds)
        txtAppName.adapter = appIdAdapter
        txtAppName.onItemSelectedListener = onAppNameSelected
        txtAppId.addTextChangedListener(onAppIdChange)
        txtWriteCounter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                try {
                    val writeCounter = txtWriteCounter.text.toString().toInt()
                    amiiboData.checkWriteCount(writeCounter)
                    txtWriteCounter.error = null
                } catch (e: Exception) {
                    txtWriteCounter.error = getString(
                        R.string.error_min_max,
                        AmiiboData.WRITE_COUNT_MIN_VALUE, AmiiboData.WRITE_COUNT_MAX_VALUE
                    )
                }
            }
        })
        loadData()
        appDataFormat.setOnClickListener {
            amiiboData.appId = 0
            amiiboData.appData = ByteArray(amiiboData.appData.size)
            loadData()
        }
        appDataTransfer.setOnClickListener {
            Toasty(this).Short(R.string.notice_incomplete)
        }
    }

    private val imageTarget: CustomTarget<Bitmap?> = object : CustomTarget<Bitmap?>() {
        override fun onLoadFailed(errorDrawable: Drawable?) {
            imageAmiibo.isGone = true
        }

        override fun onLoadCleared(placeholder: Drawable?) {}
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
            if (amiiboId == -1L) {
                tagInfo = getString(R.string.read_error)
            } else if (amiiboId == 0L) {
                tagInfo = getString(R.string.blank_tag)
            } else {
                var amiibo: Amiibo? = null
                if (null != amiiboManager) {
                    amiibo = amiiboManager!!.amiibos[amiiboId]
                    if (null == amiibo) amiibo = Amiibo(amiiboManager, amiiboId, null, null)
                }
                if (null != amiibo) {
                    amiiboHexId = Amiibo.idToHex(amiibo.id)
                    amiiboImageUrl = amiibo.imageUrl
                    if (null != amiibo.name) amiiboName = amiibo.name!!
                    if (null != amiibo.amiiboSeries) amiiboSeries = amiibo.amiiboSeries!!.name
                    if (null != amiibo.amiiboType) amiiboType = amiibo.amiiboType!!.name
                    if (null != amiibo.gameSeries) gameSeries = amiibo.gameSeries!!.name
                } else {
                    tagInfo = "ID: " + Amiibo.idToHex(amiiboId)
                    amiiboImageUrl = Amiibo.getImageUrl(amiiboId)
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
        imageAmiibo.isGone = true
        GlideApp.with(imageAmiibo).clear(imageAmiibo)
        if (!amiiboImageUrl.isNullOrEmpty()) {
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
            newAmiiboData = AmiiboData(amiiboData.array())
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
                newAmiiboData.initializedDate = initializedDate!!
            } catch (e: Exception) {
                txtInitDate.requestFocus()
                return
            }
            try {
                newAmiiboData.modifiedDate = modifiedDate!!
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
                    newAmiiboData.appData = onAppDataZeldaTPSaved()
                } catch (e: Exception) {
                    return
                }
            }
            if (appDataSwitch.isChecked && null != appDataSplatoon) {
                try {
                    newAmiiboData.appData = onAppDataSplatoonSaved()
                } catch (e: Exception) {
                    return
                }
            }
            if (appDataSwitch.isChecked && null != appDataSplatoon) {
                try {
                    newAmiiboData.appData = onAppDataSplatoon3Saved()
                } catch (e: Exception) {
                    return
                }
            }
            if (appDataSwitch.isChecked && null != appDataSSBU) {
                try {
                    newAmiiboData.appData = onAppDataSSBUSaved()
                } catch (e: Exception) {
                    return
                }
            }
            if (appDataSwitch.isChecked && null != appDataSSB) {
                try {
                    newAmiiboData.appData = onAppDataSSBSaved()
                } catch (e: Exception) {
                    return
                }
            }
        }
        val tagData: ByteArray = try {
            keyManager.encrypt(newAmiiboData.array())
        } catch (e: Exception) {
            Debug.warn(e)
            showErrorDialog(R.string.fail_encrypt)
            return
        }
        val intent = Intent(NFCIntent.ACTION_EDIT_COMPLETE)
        intent.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData)
        setResult(RESULT_OK, intent)
        finish()
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
        if (null != countryCodeAdapter) {
            for (i in 0 until countryCodeAdapter!!.count) {
                val (key) = countryCodeAdapter!!.getItem(i)
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
        updateInitializedDateView(initializedDate!!)
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
        updateModifiedDateView(modifiedDate!!)
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
        if (null != appId) {
            txtAppId.setText(String.format("%08X", appId))
        } else {
            txtAppId.setText("")
        }
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
            val selectedItem = adapterView.getItemAtPosition(i)
            if (null != selectedItem) {
                appId = (selectedItem as Map.Entry<*, *>).key as Int?
            }
            updateAppIdView(appId)
            updateAppDataView(appId)
        }

        override fun onNothingSelected(adapterView: AdapterView<*>?) {}
    }

    private fun updateAppNameView() {
        var index = 0
        if (null != appIdAdapter) {
            for (i in 0 until appIdAdapter!!.count) {
                if (appIdAdapter!!.getItem(i).key == appId) {
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
        appDataViewZeldaTP.isGone = true
        appDataZeldaTP = null
        appDataViewSplatoon.isGone = true
        appDataSplatoon = null
        appDataViewSplatoon3.isGone = true
        appDataSplatoon3 = null
        appDataViewSSB.isGone = true
        appDataSSB = null
        appDataViewSSBU.isGone = true
        appDataSSBU = null
        if (null != appId) {
            when (appId) {
                AppId_ZeldaTP -> {
                    appDataViewZeldaTP.isVisible = true
                    enableAppDataZeldaTP(amiiboData.appData)
                }
                AppId_Splatoon -> {
                    appDataViewSplatoon.isVisible = true
                    enableAppDataSplatoon(amiiboData.appData)
                }
                AppId_Splatoon3 -> {
                    appDataViewSplatoon3.isVisible = true
                    enableAppDataSplatoon3(amiiboData.appData)
                }
                AppId_SSB -> {
                    appDataViewSSB.isVisible = true
                    enableAppDataSSB(amiiboData.appData)
                }
                AppId_SSBU -> {
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
            var viewItem = view
            if (null == view) {
                viewItem = LayoutInflater.from(parent.context)
                    .inflate(R.layout.spinner_text, parent, false)
            }
            (viewItem as TextView).text = getItem(position).value
            return viewItem
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
            if (Debug.isNewer(Build.VERSION_CODES.N)) {
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
            if (Debug.isNewer(Build.VERSION_CODES.N)) {
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
            var viewItem = view
            if (null == viewItem) {
                viewItem = LayoutInflater.from(parent.context)
                    .inflate(android.R.layout.simple_dropdown_item_1line, parent, false)
            }
            viewItem?.findViewById<TextView>(android.R.id.text1)?.text = getItem(position).value
            return viewItem
        }

        override fun getView(position: Int, view: View?, parent: ViewGroup): View {
            var viewItem = view
            if (null == view) {
                viewItem = LayoutInflater.from(parent.context)
                    .inflate(R.layout.spinner_text, parent, false)
            }
            (viewItem as TextView).text = getItem(position).value
            return viewItem
        }
    }

    private fun setListForSpinners(controls: Array<Spinner?>, list: Int) {
        controls.forEach {
            val adapter = ArrayAdapter.createFromResource(
                this, list, R.layout.spinner_text
            )
            adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
            it!!.adapter = adapter
        }
    }

    fun onHeartsUpdate() {
        try {
            val hearts = txtHearts1!!.text.toString().toInt()
            txtHearts2!!.isEnabled = hearts < 20
            if (!txtHearts2!!.isEnabled) {
                txtHearts2!!.setSelection(0)
            }
            try {
                appDataZeldaTP!!.checkHearts(hearts * 4)
                txtHearts1?.error = null
            } catch (e: Exception) {
                txtHearts1?.error = getString(R.string.error_min_max, 0, 20)
            }
        } catch (e: NumberFormatException) {
            txtHearts1?.error = getString(R.string.error_min_max, 0, 20)
            if (null != txtHearts1)
                txtHearts2?.isEnabled = txtHearts1!!.isEnabled
        }
    }

    private fun setEffectValue(spinner: Spinner?, value: Int) {
        var spinValue = value
        if (spinValue == 0xFF) spinValue = 0 else spinValue++
        if (spinValue > spinner!!.adapter.count) spinValue = 0
        spinner.setSelection(spinValue)
    }

    private fun getEffectValue(spinner: Spinner?): Int {
        var value = spinner!!.selectedItemPosition
        if (value == 0) value = 0xFF else value--
        return value
    }

    private fun enableAppDataZeldaTP(appData: ByteArray) {
        try {
            appDataZeldaTP = AppDataZeldaTP(appData)
        } catch (e: Exception) {
            appDataViewZeldaTP.isGone = true
            return
        }
        txtHearts1 = findViewById(R.id.txtHearts1)
        txtHearts2 = findViewById(R.id.txtHearts2)
        txtLevelZeldaTP = findViewById(R.id.txtLevelZeldaTP)
        setListForSpinners(arrayOf(txtHearts2), R.array.editor_tp_hearts)
        val level: Int
        val hearts: Int
        if (initialAppDataInitialized) {
            level = try {
                appDataZeldaTP!!.level
            } catch (e: Exception) {
                40
            }
            hearts = try {
                appDataZeldaTP!!.hearts
            } catch (e: Exception) {
                AppDataZeldaTP.HEARTS_MAX_VALUE
            }
        } else {
            level = 40
            hearts = AppDataZeldaTP.HEARTS_MAX_VALUE
        }
        txtLevelZeldaTP!!.setText(level.toString())
        txtHearts1!!.setText((hearts / 4).toString())
        txtHearts2!!.setSelection(hearts % 4)
        txtHearts2!!.isEnabled = hearts / 4 < 20
        txtLevelZeldaTP!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                try {
                    val text = txtLevelZeldaTP!!.text.toString().toInt()
                    try {
                        appDataZeldaTP!!.checkLevel(text)
                        txtLevelZeldaTP?.error = null
                    } catch (e: Exception) {
                        txtLevelZeldaTP?.error = getString(R.string.error_min_max, 0, 40)
                    }
                } catch (e: NumberFormatException) {
                    txtLevelZeldaTP?.error = getString(R.string.error_min_max, 0, 40)
                }
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        txtHearts1!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                onHeartsUpdate()
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        onAppDataZeldaTPChecked(isAppDataInitialized)
    }

    private fun enableAppDataSplatoon(appData: ByteArray) {
        try {
            appDataSplatoon = AppDataSplatoon(appData)
        } catch (e: Exception) {
            appDataViewSplatoon.isGone = true
            return
        }
        buttonInject = findViewById(R.id.inject_game_data)
        buttonInject?.setOnClickListener {
            appDataSplatoon!!.injectSaveData()
            it.isEnabled = !appDataSplatoon!!.checkSaveData()
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
        buttonInject3 = findViewById(R.id.inject_game_data_3)
        buttonInject3?.setOnClickListener {
            appDataSplatoon3!!.injectSaveData()
            it.isEnabled = !appDataSplatoon3!!.checkSaveData()
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
        spnAppearance = findViewById(R.id.spnAppearance)
        txtLevelSSB = findViewById(R.id.txtLevelSSB)
        spnSpecialNeutral = findViewById(R.id.spnSpecial1)
        spnSpecialSide = findViewById(R.id.spnSpecial2)
        spnSpecialUp = findViewById(R.id.spnSpecial3)
        spnSpecialDown = findViewById(R.id.spnSpecial4)
        spnEffect1 = findViewById(R.id.spnEffect1)
        spnEffect2 = findViewById(R.id.spnEffect2)
        spnEffect3 = findViewById(R.id.spnEffect3)
        txtStatAttack = findViewById(R.id.txtStatAttack)
        txtStatDefense = findViewById(R.id.txtStatDefense)
        txtStatSpeed = findViewById(R.id.txtStatSpeed)
        setListForSpinners(arrayOf(spnAppearance), R.array.ssb_appearance_values)
        setListForSpinners(
            arrayOf(
                spnSpecialNeutral, spnSpecialSide,
                spnSpecialUp, spnSpecialDown
            ), R.array.ssb_specials_values
        )
        setListForSpinners(
            arrayOf(spnEffect1, spnEffect2, spnEffect3),
            R.array.ssb_bonus_effects
        )
        val appearance: Int
        val level: Int
        val statAttack: Int
        val statDefense: Int
        val statSpeed: Int
        val specialNeutral: Int
        val specialSide: Int
        val specialUp: Int
        val specialDown: Int
        val bonusEffect1: Int
        val bonusEffect2: Int
        val bonusEffect3: Int
        if (initialAppDataInitialized) {
            appearance = try {
                appDataSSB!!.appearence
            } catch (e: Exception) {
                AppDataSSB.APPEARANCE_MIN_VALUE
            }
            level = try {
                appDataSSB!!.level
            } catch (e: Exception) {
                AppDataSSB.LEVEL_MAX_VALUE
            }
            statAttack = try {
                appDataSSB!!.statAttack
            } catch (e: Exception) {
                AppDataSSB.STATS_MAX_VALUE
            }
            statDefense = try {
                appDataSSB!!.statDefense
            } catch (e: Exception) {
                AppDataSSB.STATS_MAX_VALUE
            }
            statSpeed = try {
                appDataSSB!!.statSpeed
            } catch (e: Exception) {
                AppDataSSB.STATS_MAX_VALUE
            }
            specialNeutral = try {
                appDataSSB!!.specialNeutral
            } catch (e: Exception) {
                AppDataSSB.SPECIAL_MIN_VALUE
            }
            specialSide = try {
                appDataSSB!!.specialSide
            } catch (e: Exception) {
                AppDataSSB.SPECIAL_MIN_VALUE
            }
            specialUp = try {
                appDataSSB!!.specialUp
            } catch (e: Exception) {
                AppDataSSB.SPECIAL_MIN_VALUE
            }
            specialDown = try {
                appDataSSB!!.specialDown
            } catch (e: Exception) {
                AppDataSSB.SPECIAL_MIN_VALUE
            }
            bonusEffect1 = try {
                appDataSSB!!.bonusEffect1
            } catch (e: Exception) {
                AppDataSSB.BONUS_MAX_VALUE
            }
            bonusEffect2 = try {
                appDataSSB!!.bonusEffect2
            } catch (e: Exception) {
                AppDataSSB.BONUS_MAX_VALUE
            }
            bonusEffect3 = try {
                appDataSSB!!.bonusEffect3
            } catch (e: Exception) {
                AppDataSSB.BONUS_MAX_VALUE
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
        spnAppearance!!.setSelection(appearance)
        txtLevelSSB!!.setText(level.toString())
        txtStatAttack!!.setText(statAttack.toString())
        txtStatDefense!!.setText(statDefense.toString())
        txtStatSpeed!!.setText(statSpeed.toString())
        spnSpecialNeutral!!.setSelection(specialNeutral)
        spnSpecialSide!!.setSelection(specialSide)
        spnSpecialUp!!.setSelection(specialUp)
        spnSpecialDown!!.setSelection(specialDown)
        setEffectValue(spnEffect1, bonusEffect1)
        setEffectValue(spnEffect2, bonusEffect2)
        setEffectValue(spnEffect3, bonusEffect3)
        txtLevelSSB!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                try {
                    val text = txtLevelSSB!!.text.toString().toInt()
                    if (text < 1 || text > 50) {
                        txtLevelSSB?.error = getString(R.string.error_min_max, 1, 50)
                    } else {
                        txtLevelSSB?.error = null
                    }
                } catch (e: NumberFormatException) {
                    txtLevelSSB?.error = getString(R.string.error_min_max, 1, 50)
                }
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        txtStatAttack!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                try {
                    val text = txtStatAttack!!.text.toString().toInt()
                    try {
                        appDataSSB!!.checkStat(text)
                        txtStatAttack?.error = null
                    } catch (e: Exception) {
                        txtStatAttack?.error = getString(R.string.error_min_max, -200, 200)
                    }
                } catch (e: NumberFormatException) {
                    txtStatAttack?.error = getString(R.string.error_min_max, -200, 200)
                }
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        txtStatDefense!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                try {
                    val text = txtStatDefense!!.text.toString().toInt()
                    try {
                        appDataSSB!!.checkStat(text)
                        txtStatDefense?.error = null
                    } catch (e: Exception) {
                        txtStatDefense?.error = getString(R.string.error_min_max, -200, 200)
                    }
                } catch (e: NumberFormatException) {
                    txtStatDefense?.error = getString(R.string.error_min_max, -200, 200)
                }
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        txtStatSpeed!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                try {
                    val text = txtStatSpeed!!.text.toString().toInt()
                    try {
                        appDataSSB!!.checkStat(text)
                        txtStatSpeed?.error = null
                    } catch (e: Exception) {
                        txtStatSpeed?.error = getString(R.string.error_min_max, -200, 200)
                    }
                } catch (e: NumberFormatException) {
                    txtStatSpeed?.error = getString(R.string.error_min_max, -200, 200)
                }
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        onAppDataSSBChecked(isAppDataInitialized)
    }

    private fun enableAppDataSSBU(appData: ByteArray) {
        try {
            appDataSSBU = AppDataSSBU(appData)
        } catch (e: Exception) {
            appDataViewSSBU.isGone = true
            return
        }
        spnAppearanceU = findViewById(R.id.spnAppearanceU)
        txtLevelSSBU = findViewById(R.id.txtLevelSSBU)
        txtGiftCount = findViewById(R.id.txtGiftCount)
        txtLevelCPU = findViewById(R.id.txtLevelCPU)
        txtStatAttackU = findViewById(R.id.txtStatAttackU)
        txtStatDefenseU = findViewById(R.id.txtStatDefenseU)
        setListForSpinners(arrayOf(spnAppearanceU), R.array.ssb_appearance_values)
        val appearance: Int
        val level: Int
        val giftCount: Int
        val levelCPU: Int
        val statAttack: Int
        val statDefense: Int
        val statSpeed: Int
        val specialNeutral: Int
        val specialSide: Int
        val specialUp: Int
        val specialDown: Int
        val bonusEffect1: Int
        val bonusEffect2: Int
        val bonusEffect3: Int
        if (initialAppDataInitialized) {
            appearance = try {
                appDataSSBU!!.appearence
            } catch (e: Exception) {
                AppDataSSBU.APPEARANCE_MIN_VALUE
            }
            level = try {
                appDataSSBU!!.level
            } catch (e: Exception) {
                AppDataSSBU.LEVEL_MIN_VALUE
            }
            giftCount = try {
                appDataSSBU!!.giftCount
            } catch (e: Exception) {
                0
            }
            levelCPU = try {
                appDataSSBU!!.levelCPU
            } catch (e: Exception) {
                AppDataSSBU.LEVEL_MIN_VALUE
            }
            statAttack = try {
                appDataSSBU!!.statAttack
            } catch (e: Exception) {
                0
            }
            statDefense = try {
                appDataSSBU!!.statDefense
            } catch (e: Exception) {
                0
            }
        } else {
            appearance = AppDataSSBU.APPEARANCE_MIN_VALUE
            level = AppDataSSBU.LEVEL_MIN_VALUE
            giftCount = 0
            levelCPU = AppDataSSBU.LEVEL_MIN_VALUE
            statAttack = 0
            statDefense = 0
            statSpeed = 0
            specialNeutral = 0
            specialSide = 0
            specialUp = 0
            specialDown = 0
            bonusEffect1 = 0xFF
            bonusEffect2 = 0xFF
            bonusEffect3 = 0xFF
        }
        spnAppearanceU!!.setSelection(appearance)
        txtLevelSSBU!!.setText(level.toString())
        txtGiftCount!!.setText(giftCount.toString())
        txtLevelCPU!!.setText(levelCPU.toString())
        txtStatAttackU!!.setText(statAttack.toString())
        txtStatDefenseU!!.setText(statDefense.toString())
        txtLevelSSBU!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                try {
                    val text = txtLevelSSBU!!.text.toString().toInt()
                    if (text < 1 || text > 50) {
                        txtLevelSSBU?.error = getString(R.string.error_min_max, 1, 50)
                    } else {
                        txtLevelSSBU?.error = null
                    }
                } catch (e: NumberFormatException) {
                    txtLevelSSBU?.error = getString(R.string.error_min_max, 1, 50)
                }
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        txtStatAttackU!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                try {
                    val text = txtStatAttackU!!.text.toString().toInt()
                    try {
                        appDataSSBU!!.checkStat(text)
                        txtStatAttackU?.error = null
                    } catch (e: Exception) {
                        txtStatAttackU?.error = getString(R.string.error_min_max, -0, 2500)
                    }
                } catch (e: NumberFormatException) {
                    txtStatAttackU?.error = getString(R.string.error_min_max, 0, 2500)
                }
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        txtStatDefenseU!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                try {
                    val text = txtStatDefenseU!!.text.toString().toInt()
                    try {
                        appDataSSBU!!.checkStat(text)
                        txtStatDefenseU?.error = null
                    } catch (e: Exception) {
                        txtStatDefenseU?.error = getString(R.string.error_min_max, 0, 2500)
                    }
                } catch (e: NumberFormatException) {
                    txtStatDefenseU?.error = getString(R.string.error_min_max, 0, 2500)
                }
            }

            override fun afterTextChanged(editable: Editable) {}
        })
    }

    private fun onAppDataZeldaTPChecked(enabled: Boolean) {
        if (null == txtHearts2) return
        txtHearts1?.isEnabled = enabled
        onHeartsUpdate()
        txtLevelZeldaTP?.isEnabled = enabled
    }

    private fun onAppDataSplatoonChecked(enabled: Boolean) {
        if (null == buttonInject) return
        buttonInject?.isEnabled = enabled && !appDataSplatoon!!.checkSaveData()
    }

    private fun onAppDataSplatoon3Checked(enabled: Boolean) {
        if (null == buttonInject3) return
        buttonInject3?.isEnabled = enabled && !appDataSplatoon3!!.checkSaveData()
    }

    private fun onAppDataSSBChecked(enabled: Boolean) {
        if (null == spnAppearance) return
        spnAppearance?.isEnabled = enabled
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

    private fun onAppDataSSBUChecked(enabled: Boolean) {
        if (null == spnAppearanceU) return
        spnAppearanceU?.isEnabled = enabled
        txtLevelSSBU?.isEnabled = enabled
        // txtGiftCount?.isEnabled = enabled
        // txtLevelCPU?.isEnabled = enabled
        txtStatAttackU?.isEnabled = enabled
        txtStatDefenseU?.isEnabled = enabled
    }

    @Throws(NumberFormatException::class)
    private fun onAppDataZeldaTPSaved(): ByteArray {
        try {
            val level = txtLevelZeldaTP!!.text.toString().toInt()
            appDataZeldaTP!!.level = level
        } catch (e: NumberFormatException) {
            txtLevelZeldaTP?.requestFocus()
            throw e
        }
        try {
            val hearts1 = txtHearts1!!.text.toString().toInt()
            val hearts2 = txtHearts2!!.selectedItemPosition
            appDataZeldaTP!!.hearts = hearts1 * 4 + hearts2
        } catch (e: NumberFormatException) {
            txtHearts1?.requestFocus()
            throw e
        }
        return appDataZeldaTP!!.array()
    }

    private fun onAppDataSplatoonSaved(): ByteArray {
        return appDataSplatoon!!.array()
    }

    private fun onAppDataSplatoon3Saved(): ByteArray {
        return appDataSplatoon3!!.array()
    }

    @Throws(Exception::class)
    private fun onAppDataSSBSaved(): ByteArray {
        try {
            val appearance = spnAppearance!!.selectedItemPosition
            appDataSSB!!.appearence = appearance
        } catch (e: NumberFormatException) {
            spnAppearance?.requestFocus()
            throw e
        }
        try {
            val level = txtLevelSSB!!.text.toString().toInt()
            val oldLevel: Int? = try {
                appDataSSB!!.level
            } catch (e: Exception) {
                null
            }

            // level is a granular value, so we don't want to overwrite it halfway through a level
            if (null == oldLevel || level != oldLevel) {
                appDataSSB!!.level = level
            }
        } catch (e: NumberFormatException) {
            txtLevelSSB?.requestFocus()
            throw e
        }
        try {
            val specialNeutral = spnSpecialNeutral!!.selectedItemPosition
            appDataSSB!!.specialNeutral = specialNeutral
        } catch (e: NumberFormatException) {
            spnSpecialNeutral?.requestFocus()
            throw e
        }
        try {
            val specialSide = spnSpecialSide!!.selectedItemPosition
            appDataSSB!!.specialSide = specialSide
        } catch (e: NumberFormatException) {
            spnSpecialSide?.requestFocus()
            throw e
        }
        try {
            val specialUp = spnSpecialUp!!.selectedItemPosition
            appDataSSB!!.specialUp = specialUp
        } catch (e: NumberFormatException) {
            spnSpecialUp?.requestFocus()
            throw e
        }
        try {
            val specialDown = spnSpecialDown!!.selectedItemPosition
            appDataSSB!!.specialDown = specialDown
        } catch (e: NumberFormatException) {
            spnSpecialDown?.requestFocus()
            throw e
        }
        try {
            val statAttack = txtStatAttack!!.text.toString().toInt()
            appDataSSB!!.statAttack = statAttack
        } catch (e: NumberFormatException) {
            txtStatAttack?.requestFocus()
            throw e
        }
        try {
            val statDefense = txtStatDefense!!.text.toString().toInt()
            appDataSSB!!.statDefense = statDefense
        } catch (e: NumberFormatException) {
            txtStatDefense?.requestFocus()
            throw e
        }
        try {
            val statSpeed = txtStatSpeed!!.text.toString().toInt()
            appDataSSB!!.statSpeed = statSpeed
        } catch (e: NumberFormatException) {
            txtStatSpeed?.requestFocus()
            throw e
        }
        try {
            val bonusEffect1 = getEffectValue(spnEffect1)
            appDataSSB!!.bonusEffect1 = bonusEffect1
        } catch (e: NumberFormatException) {
            spnEffect1?.requestFocus()
            throw e
        }
        try {
            val bonusEffect2 = getEffectValue(spnEffect2)
            appDataSSB!!.bonusEffect2 = bonusEffect2
        } catch (e: NumberFormatException) {
            spnEffect2?.requestFocus()
            throw e
        }
        try {
            val bonusEffect3 = getEffectValue(spnEffect3)
            appDataSSB!!.bonusEffect3 = bonusEffect3
        } catch (e: NumberFormatException) {
            spnEffect3?.requestFocus()
            throw e
        }
        return appDataSSB!!.array()
    }

    @Throws(Exception::class)
    private fun onAppDataSSBUSaved(): ByteArray {
        try {
            val appearance = spnAppearanceU!!.selectedItemPosition
            appDataSSBU!!.appearence = appearance
        } catch (e: NumberFormatException) {
            spnAppearanceU?.requestFocus()
            throw e
        }
        try {
            val level = txtLevelSSBU!!.text.toString().toInt()
            val oldLevel: Int? = try {
                appDataSSBU!!.level
            } catch (e: Exception) {
                null
            }

            // level is a granular value, so we don't want to overwrite it halfway through a level
            if (null == oldLevel || level != oldLevel) {
                appDataSSBU!!.level = level
            }
        } catch (e: NumberFormatException) {
            txtLevelSSBU?.requestFocus()
            throw e
        }
        try {
            val statAttack = txtStatAttackU!!.text.toString().toInt()
            appDataSSBU!!.statAttack = statAttack
        } catch (e: NumberFormatException) {
            txtStatAttackU?.requestFocus()
            throw e
        }
        try {
            val statDefense = txtStatDefenseU!!.text.toString().toInt()
            appDataSSBU!!.statDefense = statDefense
        } catch (e: NumberFormatException) {
            txtStatDefenseU?.requestFocus()
            throw e
        }
        return appDataSSBU!!.withChecksum().array()
    }

    private fun showErrorDialog(msgRes: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.error_caps)
            .setMessage(msgRes)
            .setPositiveButton(R.string.close) { _: DialogInterface?, _: Int -> finish() }
            .show()
        setResult(RESULT_OK, Intent(NFCIntent.ACTION_FIX_BANK_DATA))
    }

    companion object {
        const val AppId_ChibiRobo = 0x00152600
        const val AppId_ZeldaTP = 0x1019C800
        const val AppId_MHStories = 0x0016E100
        const val AppId_MLPaperJam = 0x00132600
        const val AppId_MLSuperstarSaga = 0x00194B00
        const val AppId_MSSuperstars = 0x00188B00
        const val AppId_MarioTennis = 0x10199000
        const val AppId_Pikmin = 0x001A9200
        const val AppId_Splatoon = 0x10162B00
        const val AppId_Splatoon3 = 0x38600500
        const val AppId_SSB = 0x10110E00
        const val AppId_SSBU = 0x34F80200
        const val AppId_Unspecified = 0x00000000
        private fun getDateString(date: Date): String {
            return SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date)
        }
    }
}