/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * Copyright (C) 2022 withgallantry @ BluupLabs
 * ====================================================================
 */
package com.hiddenramblings.tagmo.bluetooth

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.content.Intent
import android.os.*
import android.util.Base64
import androidx.annotation.RequiresApi
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Version
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * Service for managing connection and data communication with a GATT server
 * hosted on a given Bluetooth LE device based on core/java/android/bluetooth
 *
 * Android Bluetooth Low Energy Status Codes
 *
 * 0	0x00	BLE_HCI_STATUS_CODE_SUCCESS
 * 1	0x01	BLE_HCI_STATUS_CODE_UNKNOWN_BTLE_COMMAND
 * 2	0x02	BLE_HCI_STATUS_CODE_UNKNOWN_CONNECTION_IDENTIFIER
 * 5	0x05	BLE_HCI_AUTHENTICATION_FAILURE
 * 6	0x06	BLE_HCI_STATUS_CODE_PIN_OR_KEY_MISSING
 * 7	0x07	BLE_HCI_MEMORY_CAPACITY_EXCEEDED
 * 8	0x08	BLE_HCI_CONNECTION_TIMEOUT
 * 12	0x0C	BLE_HCI_STATUS_CODE_COMMAND_DISALLOWED
 * 18	0x12	BLE_HCI_STATUS_CODE_INVALID_BTLE_COMMAND_PARAMETERS
 * 19	0x13	BLE_HCI_REMOTE_USER_TERMINATED_CONNECTION
 * 20	0x14	BLE_HCI_REMOTE_DEV_TERMINATION_DUE_TO_LOW_RESOURCES
 * 21	0x15	BLE_HCI_REMOTE_DEV_TERMINATION_DUE_TO_POWER_OFF
 * 22	0x16	BLE_HCI_LOCAL_HOST_TERMINATED_CONNECTION
 * 26	0x1A	BLE_HCI_UNSUPPORTED_REMOTE_FEATURE
 * 30	0x1E	BLE_HCI_STATUS_CODE_INVALID_LMP_PARAMETERS
 * 31	0x1F	BLE_HCI_STATUS_CODE_UNSPECIFIED_ERROR
 * 34	0x22	BLE_HCI_STATUS_CODE_LMP_RESPONSE_TIMEOUT
 * 36	0x24	BLE_HCI_STATUS_CODE_LMP_PDU_NOT_ALLOWED
 * 40	0x28	BLE_HCI_INSTANT_PASSED
 * 41	0x29	BLE_HCI_PAIRING_WITH_UNIT_KEY_UNSUPPORTED
 * 42	0x2A	BLE_HCI_DIFFERENT_TRANSACTION_COLLISION
 * 58	0x3A	BLE_HCI_CONTROLLER_BUSY
 * 59	0x3B	BLE_HCI_CONN_INTERVAL_UNACCEPTABLE
 * 60	0x3C	BLE_HCI_DIRECTED_ADVERTISER_TIMEOUT
 * 61	0x3D	BLE_HCI_CONN_TERMINATED_DUE_TO_MIC_FAILURE
 * 62	0x3E	BLE_HCI_CONN_FAILED_TO_BE_ESTABLISHED
 * 128	0x80	GATT_NO_RESSOURCES
 * 129	0x81	GATT_INTERNAL_ERROR
 * 130	0x82	GATT_WRONG_STATE
 * 131	0x83	GATT_DB_FULL
 * 132	0x84	GATT_BUSY
 * 133	0x85	GATT_ERROR
 * 135	0x87	GATT_ILLEGAL_PARAMETER
 * 137	0x89	GATT_AUTH_FAIL
 * 138	0x8A	GATT_MORE
 * 139	0x8B	GATT_INVALID_CFG
 * 140	0x8C	GATT_SERVICE_STARTED
 * 141	0x8D	GATT_ENCRYPED_NO_MITM
 * 142	0x8E	GATT_NOT_ENCRYPTED
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("MissingPermission")
class BluupGattService : Service() {
    private var listener: BluupBluetoothListener? = null
    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothDeviceAddress: String? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mCharacteristicRX: BluetoothGattCharacteristic? = null
    private var mCharacteristicTX: BluetoothGattCharacteristic? = null
    private var nameCompat: String? = null
    private var tailCompat: String? = null
    private var wipeDeviceCount = 0
    private var maxTransmissionUnit = 23
    private val chunkTimeout = 30L
    fun setListener(listener: BluupBluetoothListener?) {
        this.listener = listener
    }

    private val commandCallbacks = ArrayList<Runnable>()
    private val bluupHandler = Handler(Looper.getMainLooper())
    private val listCount = 10

    interface BluupBluetoothListener {
        fun onBluupServicesDiscovered()
        fun onBluupActiveChanged(jsonObject: JSONObject?)
        fun onBluupStatusChanged(jsonObject: JSONObject?)
        fun onBluupListRetrieved(jsonArray: JSONArray)
        fun onBluupRangeRetrieved(jsonArray: JSONArray)
        fun onBluupFilesDownload(dataString: String)
        fun onBluupProcessFinish()
        fun onBluupConnectionLost()
    }

    private var response = StringBuilder()
    private var rangeIndex = 0
    private fun getCharacteristicValue(characteristic: BluetoothGattCharacteristic, output: String?) {
        if (!output.isNullOrEmpty()) {
            Debug.verbose(this.javaClass, "${getLogTag(characteristic.uuid)} $output")
            if (characteristic.uuid.compareTo(BluupRX) == 0) {
                if (output.contains(">tag.")) {
                    response = StringBuilder()
                    response.append(output.split(">".toRegex()).toTypedArray()[1])
                } else if (output.startsWith("tag.")
                    || output.startsWith("{") || response.isNotEmpty()) {
                    response.append(output)
                }
                val progress =
                    if (response.isNotEmpty()) response.toString().trim { it <= ' ' }.replace(
                        Objects.requireNonNull(System.getProperty("line.separator")).toRegex(), ""
                    ) else ""
                if (isJSONValid(progress) || progress.endsWith(">")
                    || progress.lastIndexOf("undefined") == 0 || progress.lastIndexOf("\n") == 0
                ) {
                    if (commandCallbacks.size > 0) {
                        commandCallbacks[0].run()
                        commandCallbacks.removeAt(0)
                    }
                }
                if (progress.startsWith("tag.get()") || progress.startsWith("tag.setTag")) {
                    if (progress.endsWith(">")) {
                        if (progress.contains("Uncaught no such element")
                            && null != nameCompat && null != tailCompat
                        ) {
                            response = StringBuilder()
                            fixSlotDetails(nameCompat, tailCompat)
                            nameCompat = null
                            tailCompat = null
                            return
                        }
                        try {
                            val getAmiibo = progress.substring(
                                progress.indexOf("{"),
                                progress.lastIndexOf("}") + 1
                            )
                            try {
                                listener?.onBluupActiveChanged(JSONObject(getAmiibo))
                            } catch (e: JSONException) {
                                Debug.warn(e)
                                listener?.onBluupActiveChanged(null)
                            }
                        } catch (ex: StringIndexOutOfBoundsException) {
                            Debug.warn(ex)
                            listener?.onBluupActiveChanged(null)
                        }
                        response = StringBuilder()
                        nameCompat = null
                        tailCompat = null
                    }
                } else if (progress.startsWith("tag.getList")) {
                    if (progress.endsWith(">") || progress.endsWith("\n")) {
                        val getList = progress.substring(
                            progress.indexOf("["),
                            progress.lastIndexOf("]") + 1
                        )
                        try {
                            var escapedList = getList
                                .replace("/", "\\/")
                                .replace("'", "\\'")
                                .replace("-", "\\-")
                            if (getList.contains("...")) {
                                if (rangeIndex > 0) {
                                    rangeIndex = 0
                                    escapedList = escapedList.replace(" ...", "")
                                    listener?.onBluupListRetrieved(JSONArray(escapedList))
                                } else {
                                    rangeIndex += 1
                                    listener?.onBluupListRetrieved(JSONArray())
                                    getDeviceAmiiboRange(0)
                                }
                            } else if (rangeIndex > 0) {
                                val jsonArray = JSONArray(escapedList)
                                if (jsonArray.length() > 0) {
                                    listener?.onBluupRangeRetrieved(jsonArray)
                                    getDeviceAmiiboRange(rangeIndex * listCount)
                                    rangeIndex += 1
                                } else {
                                    rangeIndex = 0
                                    activeAmiibo
                                }
                            } else {
                                listener?.onBluupListRetrieved(JSONArray(escapedList))
                            }
                        } catch (e: JSONException) {
                            Debug.warn(e)
                        }
                        response = StringBuilder()
                        if (rangeIndex == 0) listener?.onBluupProcessFinish()
                    }
                } else if (progress.startsWith("tag.remove")) {
                    if (progress.endsWith(">") || progress.endsWith("\n")) {
                        if (wipeDeviceCount > 0) {
                            wipeDeviceCount -= 1
                            delayedTagCharacteristic("remove(tag.get().name)")
                        } else {
                            listener?.onBluupStatusChanged(null)
                        }
                        response = StringBuilder()
                    }
                } else if (progress.startsWith("tag.download")) {
                    if (progress.endsWith(">") || progress.endsWith("\n")) {
                        listener?.let {
                            for (dataString in progress.split(
                                "new Uint8Array".toRegex()).toTypedArray()
                            ) {
                                if (dataString.startsWith("tag.download")
                                    && dataString.endsWith("=")
                                ) continue
                                it.onBluupFilesDownload(dataString.substring(
                                    1, dataString.lastIndexOf(">") - 2
                                ))
                            }
                        }
                    }
                } else if (progress.startsWith("tag.createBlank()")) {
                    if (progress.endsWith(">") || progress.endsWith("\n")) {
                        response = StringBuilder()
                        listener?.onBluupStatusChanged(null)
                    }
                } else if (progress.endsWith("}")) {
                    if (progress.startsWith("tag.saveUploadedTag")) {
                        response = StringBuilder()
                    } else {
                        listener?.let {
                            try {
                                val jsonObject = JSONObject(response.toString())
                                val event = jsonObject.getString("event")
                                if (event == "button") it.onBluupActiveChanged(jsonObject)
                                if (event == "delete") it.onBluupStatusChanged(jsonObject)
                            } catch (e: JSONException) {
                                if (e.message?.contains("tag.setTag") == true)
                                    activeAmiibo
                                else Debug.warn(e)
                            }
                        }
                    }
                    response = StringBuilder()
                } else if (progress.endsWith(">")) {
                    response = StringBuilder()
                }
            }
        }
    }

    fun getCharacteristicValue(characteristic: BluetoothGattCharacteristic) {
        @Suppress("DEPRECATION")
        getCharacteristicValue(characteristic, characteristic.getStringValue(0x0))
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mBluetoothGatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                listener?.onBluupConnectionLost()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (Version.isLollipop)
                    gatt.requestMtu(512) // Maximum: 517
                else listener?.onBluupServicesDiscovered()
            } else {
                Debug.warn(this.javaClass, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS)
                getCharacteristicValue(characteristic, value.decodeToString())
        }

        @Deprecated("Deprecated in Java", ReplaceWith(
            "onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int)"
        ))
        override fun onCharacteristicRead(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) getCharacteristicValue(characteristic)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            Debug.verbose(
                this.javaClass, getLogTag(characteristic.uuid) + " onCharacteristicWrite " + status
            )
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray
        ) {
            getCharacteristicValue(characteristic, value.decodeToString())
        }

        @Deprecated("Deprecated in Java", ReplaceWith(
            "onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray)"
        ))
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
        ) {
            getCharacteristicValue(characteristic)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Debug.verbose(this.javaClass, "onMtuChange complete: $mtu")
                maxTransmissionUnit = mtu - 3
            } else {
                Debug.warn(this.javaClass, "onMtuChange received: $status")
            }
            listener?.onBluupServicesDiscovered()
        }
    }

    inner class LocalBinder : Binder() {
        val service: BluupGattService
            get() = this@BluupGattService
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onUnbind(intent: Intent): Boolean {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        if (mBluetoothGatt == null) {
            return super.onUnbind(intent)
        }
        mBluetoothGatt!!.close()
        mBluetoothGatt = null
        return super.onUnbind(intent)
    }

    private val mBinder: IBinder = LocalBinder()

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    fun initialize(): Boolean {
        // For API level 18 and above, get a reference to BluetoothAdapter through BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            if (mBluetoothManager == null) {
                return false
            }
        }
        mBluetoothAdapter = mBluetoothManager?.adapter
        return mBluetoothAdapter != null
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * `BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)`
     * callback.
     */
    fun connect(address: String?): Boolean {
        if (mBluetoothAdapter == null || address == null) {
            return false
        }

        // Previously connected device.  Try to reconnect.
        if (address == mBluetoothDeviceAddress) mBluetoothGatt?.let { return it.connect() }
        val device = mBluetoothAdapter?.getRemoteDevice(address) ?: return false
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback)
        mBluetoothDeviceAddress = address
        return true
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * `BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)`
     * callback.
     */
    fun disconnect() {
        if (mBluetoothAdapter == null) return
        mBluetoothGatt?.disconnect()
    }

    private fun setResponseDescriptors(characteristic: BluetoothGattCharacteristic?) {
        try {
            val descriptorTX = characteristic!!.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )
            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Version.isTiramisu) {
                mBluetoothGatt?.writeDescriptor(descriptorTX, value)
            } else @Suppress("DEPRECATION") {
                descriptorTX.value = value
                mBluetoothGatt?.writeDescriptor(descriptorTX)
            }
        } catch (ignored: Exception) { }
        try {
            val descriptorTX = characteristic!!.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )
            val value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            if (Version.isTiramisu) {
                mBluetoothGatt?.writeDescriptor(descriptorTX, value)
            } else @Suppress("DEPRECATION") {
                descriptorTX.value = value
                mBluetoothGatt?.writeDescriptor(descriptorTX)
            }
        } catch (ignored: Exception) { }
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.
     */
    @Suppress("SameParameterValue")
    private fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic?, enabled: Boolean
    ) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) return
        mBluetoothGatt?.setCharacteristicNotification(characteristic, enabled)
        setResponseDescriptors(characteristic)
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after `BluetoothGatt#discoverServices()` completes successfully.
     *
     * @return A `List` of supported services.
     */
    private val supportedGattServices: List<BluetoothGattService>?
        get() = mBluetoothGatt?.services

    private fun getCharacteristicRX(mCustomService: BluetoothGattService): BluetoothGattCharacteristic {
        var mReadCharacteristic = mCustomService.getCharacteristic(BluupRX)
        if (mBluetoothGatt?.readCharacteristic(mReadCharacteristic) != true) run breaking@{
            mCustomService.characteristics.forEach {
                val customUUID = it.uuid
                /*get the read characteristic from the service*/
                if (customUUID.compareTo(BluupRX) == 0) {
                    Debug.verbose(this.javaClass, "GattReadCharacteristic: $customUUID")
                    mReadCharacteristic = mCustomService.getCharacteristic(customUUID)
                    return@breaking
                }
            }
        }
        return mReadCharacteristic
    }

    @Throws(UnsupportedOperationException::class)
    fun setBluupCharacteristicRX() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw UnsupportedOperationException()
        }
        val mCustomService = mBluetoothGatt!!.getService(BluupNUS)
        if (null == mCustomService) {
            val services = supportedGattServices
            if (services.isNullOrEmpty()) throw UnsupportedOperationException()
            run breaking@{
                services.forEach {
                    Debug.verbose(this.javaClass, "GattReadService: ${it.uuid}")
                    mCharacteristicRX = getCharacteristicRX(it)
                    return@breaking
                }
            }
        } else {
            mCharacteristicRX = getCharacteristicRX(mCustomService)
        }
        setCharacteristicNotification(mCharacteristicRX, true)
    }

    private fun getCharacteristicTX(mCustomService: BluetoothGattService): BluetoothGattCharacteristic {
        var mWriteCharacteristic = mCustomService.getCharacteristic(BluupTX)
        if (!mCustomService.characteristics.contains(mWriteCharacteristic)) {
            run breaking@{
                mCustomService.characteristics.forEach {
                    val customUUID = it.uuid
                    if (customUUID.compareTo(BluupTX) == 0) {
                        Debug.verbose(this.javaClass, "GattWriteCharacteristic: $customUUID")
                        mWriteCharacteristic = mCustomService.getCharacteristic(customUUID)
                        return@breaking
                    }
                }
            }
        }
        return mWriteCharacteristic
    }

    @Throws(UnsupportedOperationException::class)
    fun setBluupCharacteristicTX() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw UnsupportedOperationException()
        }
        val mCustomService = mBluetoothGatt!!.getService(BluupNUS)
        if (null == mCustomService) {
            val services = supportedGattServices
            if (services.isNullOrEmpty()) throw UnsupportedOperationException()
            for (customService in services) {
                Debug.verbose(this.javaClass, "GattWriteService: ${customService.uuid}")
                mCharacteristicTX = getCharacteristicTX(customService)
            }
        } else {
            mCharacteristicTX = getCharacteristicTX(mCustomService)
        }
        setCharacteristicNotification(mCharacteristicTX, true)
    }

    private fun delayedWriteCharacteristic(value: String) {
        val chunks = GattArray.stringToPortions(value, maxTransmissionUnit)
        val commandQueue = commandCallbacks.size + chunks.size
        bluupHandler.postDelayed({
            chunks.forEachIndexed { i, chunk ->
                bluupHandler.postDelayed({
                    try {
                        mCharacteristicTX!!.writeType =
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        if (Version.isTiramisu) {
                            mBluetoothGatt!!.writeCharacteristic(
                                mCharacteristicTX!!, chunk.encodeToByteArray(),
                                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            )
                        } else @Suppress("DEPRECATION") {
                            mCharacteristicTX!!.value = chunk.encodeToByteArray()
                            mBluetoothGatt!!.writeCharacteristic(mCharacteristicTX)
                        }
                    } catch (ex: NullPointerException) {
                        listener?.onBluupServicesDiscovered()
                    }
                }, (i + 1) * chunkTimeout)
            }
        }, commandQueue * chunkTimeout)
    }

    private fun delayedWriteCharacteristic(value: ByteArray) {
        val chunks = GattArray.byteToPortions(value, maxTransmissionUnit)
        val commandQueue = commandCallbacks.size + chunks.size
        bluupHandler.postDelayed({
            chunks.forEachIndexed { i, chunk ->
                bluupHandler.postDelayed({
                    try {
                        mCharacteristicTX!!.writeType =
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        if (Version.isTiramisu) {
                            mBluetoothGatt!!.writeCharacteristic(
                                mCharacteristicTX!!, chunk,
                                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            )
                        } else @Suppress("DEPRECATION") {
                            mCharacteristicTX!!.value = chunk
                            mBluetoothGatt!!.writeCharacteristic(mCharacteristicTX)
                        }
                    } catch (ex: NullPointerException) {
                        listener?.onBluupServicesDiscovered()
                    }
                }, (i + 1) * chunkTimeout)
            }
        }, commandQueue * chunkTimeout)
    }

    private fun queueTagCharacteristic(value: String, index: Int) {
        if (null == mCharacteristicTX) {
            try {
                setBluupCharacteristicTX()
            } catch (e: UnsupportedOperationException) {
                Debug.warn(e)
            }
        }
        commandCallbacks.add(index, Runnable { delayedWriteCharacteristic("tag.$value\n") })
        if (commandCallbacks.size == 1) {
            commandCallbacks[0].run()
            commandCallbacks.removeAt(0)
        }
    }

    private fun queueByteCharacteristic(value: ByteArray, index: Int) {
        if (null == mCharacteristicTX) {
            try {
                setBluupCharacteristicTX()
            } catch (e: UnsupportedOperationException) {
                Debug.warn(e)
            }
        }
        commandCallbacks.add(index, Runnable { delayedWriteCharacteristic(value) })
        if (commandCallbacks.size == 1) {
            commandCallbacks[0].run()
            commandCallbacks.removeAt(0)
        }
    }

    private fun delayedTagCharacteristic(value: String) {
        queueTagCharacteristic(value, commandCallbacks.size)
    }

    private fun promptTagCharacteristic(value: String) {
        queueTagCharacteristic(value, 0)
    }

    private fun delayedByteCharacteric(value: ByteArray) {
        queueByteCharacteristic(value, commandCallbacks.size)
    }

    private fun queueScreenCharacteristic(value: String, index: Int) {
        if (null == mCharacteristicTX) {
            try {
                setBluupCharacteristicTX()
            } catch (e: UnsupportedOperationException) {
                Debug.warn(e)
            }
        }
        commandCallbacks.add(index, Runnable { delayedWriteCharacteristic("screen.$value\n") })
        if (commandCallbacks.size == 1) {
            commandCallbacks[0].run()
            commandCallbacks.removeAt(0)
        }
    }

    private fun delayedScreenCharacteristic(value: String) {
        queueScreenCharacteristic(value, commandCallbacks.size)
    }

    private fun truncateUnicode(unicodeName: String, tailSize: Int) : String {
        return unicodeName.run {
            val nameLength = length + tailSize + 3 // |tail|#
            if (nameLength > 28) substring(0, length - (nameLength - 28)) else this
        }
    }

    private fun fixSlotDetails(amiiboName: String?, tail: String?) {
        tail?.let {
            amiiboName?.let { amiibo ->
                val fixedName = truncateUnicode(GattArray.stringToUnicode(amiibo), it.length)
                promptTagCharacteristic(
                    "rename(\"$fixedName|$it\",\"$fixedName|$it|0\" )"
                )
                deviceAmiibo
            }
        }
    }

    fun uploadAmiiboFile(tagData: ByteArray, amiibo: Amiibo, index: Int, complete: Boolean) {
        delayedTagCharacteristic("startTagUpload(${tagData.size})")
        val parameters: ArrayList<String> = arrayListOf()
        for (chunk in GattArray.byteToPortions(tagData, 128)) {
            val byteString = Base64.encodeToString(
                chunk, Base64.NO_PADDING or Base64.NO_CLOSE or Base64.NO_WRAP
            )
            parameters.add("tagUploadChunk(\"$byteString\")")
        }
        amiibo.name?.let { name ->
            val nameUnicode = GattArray.stringToUnicode(name)
            val nameIndexed = if (index > 0) "$index.$nameUnicode" else nameUnicode
            val amiiboName = truncateUnicode(nameIndexed, amiibo.bluupTail.length)
            parameters.add("saveUploadedTag(\"$amiiboName|${amiibo.bluupTail}|0\")")
        }
        if (complete) {
            parameters.add("uploadsComplete()")
            parameters.add("getList()")
        }
        parameters.forEach {
            commandCallbacks.add(commandCallbacks.size, Runnable {
                delayedWriteCharacteristic("tag.$it\n")
            })
        }
    }

    fun setActiveAmiibo(amiiboName: String?, tail: String?) {
        amiiboName?.let { name ->
            if (name.startsWith("New Tag")) {
                delayedTagCharacteristic("setTag(\"$name|$tail|0\")")
                return
            }
            tail?.let {
                nameCompat = truncateUnicode(GattArray.stringToUnicode(name), it.length)
                tailCompat = it
                delayedTagCharacteristic("setTag(\"$nameCompat|$it|0\")")
            }
        }
    }

    fun deleteAmiibo(amiiboName: String?, tail: String?) {
        amiiboName?.let { name ->
            if (name.startsWith("New Tag")) {
                // delayedTagCharacteristic("remove(\"$name||$tail\")")
                delayedTagCharacteristic("remove(\"$name|$tail|0\")")
                return
            }
            tail?.let {
                nameCompat = truncateUnicode(GattArray.stringToUnicode(it), it.length)
                tailCompat = it
                delayedTagCharacteristic("remove(\"$nameCompat|$it|0\")")
            }
        }
    }

    fun downloadAmiibo(fileName: String?, tail: String?) {
        tail?.let {
            fileName?.let { file ->
                val amiiboName = truncateUnicode(GattArray.stringToUnicode(file), it.length)
                delayedTagCharacteristic("download(\"$amiiboName|$it|0\")")
            }
        }
    }

    val activeAmiibo: Unit
        get() {
            delayedTagCharacteristic("get()")
        }
     val deviceAmiibo: Unit
        get() {
            delayedTagCharacteristic("getList()")
        }

    private fun getDeviceAmiiboRange(index: Int) {
        delayedTagCharacteristic("getList($index,$listCount)") // 5 ... 5
    }

    fun createBlankTag() {
        delayedTagCharacteristic("createBlank()")
    }

    fun clearStorage(count: Int) {
        wipeDeviceCount = count - 1
        delayedTagCharacteristic("remove(tag.get().name)")
    }

    fun setFlaskFace(stacked: Boolean) {
        delayedScreenCharacteristic("setFace(" + (if (stacked) 1 else 0) + ")")
    }

    private fun isJSONValid(test: String): Boolean {
        if (test.startsWith("tag.") && test.endsWith(")")) return false
        try {
            JSONObject(test)
        } catch (ex: JSONException) {
            try {
                JSONArray(test)
            } catch (jex: JSONException) {
                return false
            }
        }
        return true
    }

    private fun getLogTag(uuid: UUID): String {
        return if (uuid.compareTo(BluupTX) == 0) {
            "BluupTX"
        } else if (uuid.compareTo(BluupRX) == 0) {
            "BluupRX"
        } else if (uuid.compareTo(BluupNUS) == 0) {
            "BluupNUS"
        } else {
            uuid.toString()
        }
    }

    companion object {
        val BluupNUS: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        private val BluupTX = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        private val BluupRX = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    }
}