/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * Flask Copyright (C) 2022 withgallantry @ BluupLabs
 * ====================================================================
 */

package com.hiddenramblings.tagmo.bluetooth

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import androidx.annotation.RequiresApi
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.bluetooth.GattArray.toDataBytes
import com.hiddenramblings.tagmo.bluetooth.GattArray.toFileBytes
import com.hiddenramblings.tagmo.bluetooth.GattArray.toPortions
import com.hiddenramblings.tagmo.bluetooth.GattArray.toUnicode
import com.hiddenramblings.tagmo.bluetooth.Nordic.isUUID
import com.hiddenramblings.tagmo.bluetooth.Nordic.logTag
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.nfctech.NfcByte
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.hiddenramblings.tagmo.nfctech.TagArray.toHex
import com.hiddenramblings.tagmo.nfctech.TagArray.toPages
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.Objects
import java.util.UUID
import kotlin.random.Random

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
 **/

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("MissingPermission")
class GattService : Service() {
    private var listener: BluetoothGattListener? = null
    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothDeviceAddress: String? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mCharacteristicRX: BluetoothGattCharacteristic? = null
    private var mCharacteristicTX: BluetoothGattCharacteristic? = null

    private var maxTransmissionUnit = 20 // MTU - 3
    private val chunkTimeout = 25L

    private var slotsCount = 0

    private var nameCompat: String? = null
    private var tailCompat: String? = null
    private var wipeDeviceCount = 0
    private val listCount = 10

    private var response = StringBuilder()
    private var rangeIndex = 0

    private var uploadData = byteArrayOf()

    private var puckArray = ArrayList<ByteArray>(slotsCount)
    private var readResponse = byteArrayOf()
    private var tempInfoData = byteArrayOf()

    private var chunkNumber = 0

    @Suppress("unused")
    private enum class PUCK(bytes: Int) {
        TEST(0x00),
        INFO(0x01),
        READ(0x02),
        WRITE(0x03),
        SAVE(0x04),
        FWRITE(0x05),
        MOVE(0xFD),
        UART(0xFE),
        NFC(0xFF);

        // RESTART
        val bytes: Byte

        init { this.bytes = bytes.toByte() }
    }

    private enum class PIXL(bytes: Int) {
        VERSION(0x01),
        DFU(0x02),
        LIST(0x10),
        FORMAT(0x11),
        OPEN(0x12),
        CLOSE(0x13),
        READ(0x14),
        WRITE(0x15),
        READ_DIR(0x16),
        CREATE_DIR(0x17),
        DELETE_DIR(0x18),
        RENAME_DIR(0x19),
        META(0x1A);


        // RESTART
        val bytes: Byte

        init { this.bytes = bytes.toByte() }
    }

    fun setListener(listener: BluetoothGattListener?) {
        this.listener = listener
    }
    private val commandCallbacks = ArrayList<Runnable>()
    private val gattHandler = Handler(Looper.getMainLooper())

    var serviceType = Nordic.DEVICE.GATT

    interface BluetoothGattListener {
        fun onBluupServicesDiscovered()
        fun onBluupActiveChanged(jsonObject: JSONObject?)
        fun onBluupStatusChanged(jsonObject: JSONObject?)
        fun onBluupListRetrieved(jsonArray: JSONArray)
        fun onBluupRangeRetrieved(jsonArray: JSONArray)

        fun onPixlServicesDiscovered()
        fun onPixlConnected(firmware: String)
        fun onPixlActiveChanged(jsonObject: JSONObject?)

        fun onPuckServicesDiscovered()
        fun onPuckActiveChanged(slot: Int)
        fun onPuckDeviceProfile(activeSlot: Int, slotCount: Int)
        fun onPuckListRetrieved(slotData: ArrayList<ByteArray>)
        fun onPuckTagReloaded()

        fun onFilesDownload(tagData: ByteArray)
        fun onProcessFinish()
        fun onConnectionLost()
    }

    private fun getCharacteristicValue(characteristic: BluetoothGattCharacteristic, data: ByteArray?) {
        if (data?.isNotEmpty() == true) {
            Debug.warn(this.javaClass, "${characteristic.uuid.logTag} ${data.toHex()}")
            if (characteristic.uuid.isUUID(GattRX)) {
                val hexData = data.toHex()
                when (serviceType) {
                    Nordic.DEVICE.PIXL -> {

                    }

                    Nordic.DEVICE.LOOP -> {
                        when {
                            hexData == "0201000103" -> {
                                chunkNumber -= 1
                                if (chunkNumber == 0) listener?.onProcessFinish()
                            }

                            hexData.contains("416D694C6F6F705F46575F56") -> {
                                listener?.onPixlConnected(decipherFirmware(data))
                            }

                            else -> {

                            }
                        }
                    }

                    Nordic.DEVICE.LINK -> {
                        when {
                            hexData.startsWith("00002013") -> {
                                listener?.onPixlConnected(hexData.substring(8, hexData.length))
                            }
                            hexData == "00001002E346EA49B8A3B2541F1CCAB1F93FCF43" -> {

                            }
                            hexData == byteArrayOf(0xB0.toByte(), 0xA0.toByte()).toHex() -> {
                                delayedByteCharacteric(byteArrayOf(
                                        0xAC.toByte(), 0xAC.toByte(), 0x00.toByte(), 0x04.toByte(),
                                        0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x1C.toByte())
                                )
                            }

                            hexData == byteArrayOf(0xCA.toByte(), 0xCA.toByte()).toHex() -> {
                                delayedByteCharacteric(byteArrayOf(
                                        0xAB.toByte(), 0xAB.toByte(), 0x02.toByte(), 0x1C.toByte()
                                ))
                            }

                            hexData == byteArrayOf(0xBA.toByte(), 0xBA.toByte()).toHex() -> {
                                processLinkUpload()
                            }

                            hexData == byteArrayOf(0xAA.toByte(), 0xDD.toByte()).toHex() -> {

                            }

                            hexData == byteArrayOf(0xCB.toByte(), 0xCB.toByte()).toHex() -> {
                                delayedByteCharacteric(byteArrayOf(0xCC.toByte(), 0xDD.toByte()))
                            }

                            hexData == byteArrayOf(0xDD.toByte(), 0xCC.toByte()).toHex() -> {
                                listener?.onProcessFinish()
                            }

                            else -> {

                            }
                        }
                    }

                    Nordic.DEVICE.PUCK -> {
                        when {
                            TagArray.hexToString(hexData).endsWith("DTM_PUCK_FAST") -> {
                                delayedByteCharacteric(byteArrayOf(PUCK.INFO.bytes))
                            }

                            tempInfoData.isNotEmpty() -> {
                                val sliceData = tempInfoData.plus(data)
                                puckArray.add(sliceData[1].toInt(), sliceData.copyOfRange(2, sliceData.size))
                                tempInfoData = byteArrayOf()
                                if (puckArray.size == slotsCount) {
                                    listener?.onPuckListRetrieved(puckArray)
                                } else {
                                    val nextSlot = sliceData[1].toInt() + 1
                                    delayedByteCharacteric(byteArrayOf(PUCK.INFO.bytes, nextSlot.toByte()))
                                }
                            }

                            data[0] == PUCK.INFO.bytes -> {
                                if (data.size == 3) {
                                    slotsCount = data[2].toInt()
                                    listener?.onPuckDeviceProfile(data[1].toInt(), slotsCount)
                                } else {
                                    tempInfoData = data
                                }
                            }

                            data[0] == PUCK.READ.bytes -> {
                                if (data[2].toInt() + (data[3].toInt() * 4) >= 143) {
                                    readResponse = readResponse.plus(data.copyOfRange(4, data.size))
                                    listener?.onFilesDownload(readResponse)
                                    readResponse = byteArrayOf()
                                } else {
                                    readResponse = readResponse.plus(data.copyOfRange(4, data.size))
                                }
                            }

                            data[0] == PUCK.WRITE.bytes -> {

                            }

                            data[0] == PUCK.FWRITE.bytes -> {
                                processPuckUpload(data[1])
                            }

                            data[0] == PUCK.SAVE.bytes -> {
                                delayedByteCharacteric(byteArrayOf(PUCK.NFC.bytes))
                            }

                            data[0] == PUCK.NFC.bytes -> {
                                listener?.onPuckTagReloaded()
                            }
                        }
                    }
                    else -> {

                    }
                }
                if (commandCallbacks.size > 0) {
                    commandCallbacks[0].run()
                    commandCallbacks.removeAt(0)
                }
            }
        }
    }

    private fun getCharacteristicValue(characteristic: BluetoothGattCharacteristic, output: String?) {
        if (!output.isNullOrEmpty()) {
            Debug.warn(this.javaClass, "${characteristic.uuid.logTag} $output")
            if (characteristic.uuid.isUUID(GattRX)) {
                if (output.contains(">tag.")) {
                    response = StringBuilder()
                    response.append(output.split(">".toRegex()).toTypedArray()[1])
                } else if (output.startsWith("tag.")
                        || output.startsWith("{") || response.isNotEmpty()) {
                    response.append(output)
                }
                val formatted =
                        if (response.isNotEmpty()) response.toString().trim { it <= ' ' }.replace(
                                Objects.requireNonNull(System.getProperty("line.separator")).toRegex(), ""
                        ) else ""
                if (isJSONValid(formatted) || formatted.endsWith(">")
                        || formatted.lastIndexOf("undefined") == 0 || formatted.lastIndexOf("\n") == 0
                ) {
                    if (commandCallbacks.size > 0) {
                        commandCallbacks[0].run()
                        commandCallbacks.removeAt(0)
                    }
                }
                when {
                    formatted.startsWith("tag.get()") || formatted.startsWith("tag.setTag") -> {
                        if (formatted.endsWith(">")) {
                            if (formatted.contains("Uncaught no such element")
                                    && null != nameCompat && null != tailCompat
                            ) {
                                response = StringBuilder()
                                fixBluupDetails(nameCompat, tailCompat)
                                nameCompat = null
                                tailCompat = null
                                return
                            }
                            try {
                                val getAmiibo = formatted.substring(
                                        formatted.indexOf("{"),
                                        formatted.lastIndexOf("}") + 1
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
                    }
                    formatted.startsWith("tag.getList") -> {
                        if (formatted.endsWith(">") || formatted.endsWith("\n")) {
                            val getList = formatted.substring(
                                    formatted.indexOf("["),
                                    formatted.lastIndexOf("]") + 1
                            )
                            try {
                                var escapedList = getList
                                        .replace("/", "\\/")
                                        .replace("'", "\\'")
                                        .replace("-", "\\-")
                                when {
                                    getList.contains("...") -> {
                                        if (rangeIndex > 0) {
                                            rangeIndex = 0
                                            escapedList = escapedList.replace(" ...", "")
                                            listener?.onBluupListRetrieved(JSONArray(escapedList))
                                        } else {
                                            rangeIndex += 1
                                            listener?.onBluupListRetrieved(JSONArray())
                                            getDeviceAmiiboRange(0)
                                        }
                                    }
                                    rangeIndex > 0 -> {
                                        val jsonArray = JSONArray(escapedList)
                                        if (jsonArray.length() > 0) {
                                            listener?.onBluupRangeRetrieved(jsonArray)
                                            getDeviceAmiiboRange(rangeIndex * listCount)
                                            rangeIndex += 1
                                        } else {
                                            rangeIndex = 0
                                            activeAmiibo
                                        }
                                    }
                                    else -> {
                                        listener?.onBluupListRetrieved(JSONArray(escapedList))
                                    }
                                }
                            } catch (e: JSONException) {
                                Debug.warn(e)
                            }
                            response = StringBuilder()
                            if (rangeIndex == 0) listener?.onProcessFinish()
                        }
                    }
                    formatted.startsWith("tag.remove") -> {
                        if (formatted.endsWith(">") || formatted.endsWith("\n")) {
                            if (wipeDeviceCount > 0) {
                                wipeDeviceCount -= 1
                                delayedTagCharacteristic("remove(tag.get().name)")
                            } else {
                                listener?.onBluupStatusChanged(null)
                            }
                            response = StringBuilder()
                        }
                    }
                    formatted.startsWith("tag.download") -> {
                        if (formatted.endsWith(">") || formatted.endsWith("\n")) {
                            listener?.let {
                                for (dataString in formatted.split(
                                        "new Uint8Array".toRegex()).toTypedArray()
                                ) {
                                    if (dataString.startsWith("tag.download")
                                            && dataString.endsWith("=")
                                    ) continue
                                    Debug.info(this.javaClass, dataString)
                                    try {
                                        it.onFilesDownload(dataString.substring(
                                                1, dataString.lastIndexOf(")")
                                        ).toByteArray())
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                            response = StringBuilder()
                        }
                    }
                    formatted.startsWith("tag.createBlank()") -> {
                        if (formatted.endsWith(">") || formatted.endsWith("\n")) {
                            response = StringBuilder()
                            listener?.onBluupStatusChanged(null)
                        }
                    }
                    formatted.endsWith("}") -> {
                        if (formatted.startsWith("tag.saveUploadedTag")) {
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
                    }
                    formatted.endsWith(">") -> {
                        response = StringBuilder()
                    }
                }
            }
        }
    }

    fun getCharacteristicValue(characteristic: BluetoothGattCharacteristic) {
        when (serviceType) {
            Nordic.DEVICE.BLUUP, Nordic.DEVICE.FLASK, Nordic.DEVICE.SLIDE -> {
                getCharacteristicValue(characteristic, characteristic.getStringValue(0x0))
            }
            Nordic.DEVICE.PIXL, Nordic.DEVICE.LOOP, Nordic.DEVICE.LINK -> {
                getCharacteristicValue(characteristic, characteristic.value)
            }
            Nordic.DEVICE.PUCK -> {
                getCharacteristicValue(characteristic, characteristic.value)
            }
            else -> {

            }
        }
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mBluetoothGatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                listener?.onConnectionLost()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Debug.warn(this.javaClass, "${serviceType.logTag} onServicesDiscovered $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (Version.isLollipop) {
                    gatt.requestMtu(512) // Maximum: 517
                } else {
                    when (serviceType) {
                        Nordic.DEVICE.BLUUP, Nordic.DEVICE.FLASK, Nordic.DEVICE.SLIDE -> {
                            listener?.onBluupServicesDiscovered()
                        }
                        Nordic.DEVICE.PIXL, Nordic.DEVICE.LOOP, Nordic.DEVICE.LINK -> {
                            listener?.onPixlServicesDiscovered()
                        }
                        Nordic.DEVICE.PUCK -> {
                            listener?.onPuckServicesDiscovered()
                        }
                        else -> {

                        }
                    }
                }
            }
        }

        override fun onCharacteristicRead(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS)
                getCharacteristicValue(characteristic, value)
        }


        @Deprecated("Deprecated in Java", ReplaceWith(""))
        override fun onCharacteristicRead(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) getCharacteristicValue(characteristic)
        }

        override fun onCharacteristicWrite(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            Debug.info(this.javaClass, "${characteristic.uuid.logTag} onCharacteristicWrite $status")
        }

        override fun onCharacteristicChanged(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray
        ) {
            when (serviceType) {
                Nordic.DEVICE.BLUUP, Nordic.DEVICE.FLASK, Nordic.DEVICE.SLIDE -> {
                    getCharacteristicValue(characteristic, value.decodeToString())
                }
                Nordic.DEVICE.PIXL, Nordic.DEVICE.LOOP, Nordic.DEVICE.LINK -> {
                    getCharacteristicValue(characteristic, value)
                }
                Nordic.DEVICE.PUCK -> {
                    getCharacteristicValue(characteristic, value)
                }
                else -> {

                }
            }
        }

        @Deprecated("Deprecated in Java", ReplaceWith(""))
        override fun onCharacteristicChanged(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
        ) {
            getCharacteristicValue(characteristic)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Debug.warn(this.javaClass, "${serviceType.logTag} onMtuChange $mtu $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                maxTransmissionUnit = mtu - 3
            }
            when (serviceType) {
                Nordic.DEVICE.BLUUP, Nordic.DEVICE.FLASK, Nordic.DEVICE.SLIDE -> {
                    listener?.onBluupServicesDiscovered()
                }
                Nordic.DEVICE.PIXL, Nordic.DEVICE.LOOP, Nordic.DEVICE.LINK -> {
                    listener?.onPixlServicesDiscovered()
                }
                Nordic.DEVICE.PUCK -> {
                    listener?.onPuckServicesDiscovered()
                }
                else -> {

                }
            }
        }

        override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {
            super.onReliableWriteCompleted(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS)
                mCharacteristicRX?.let { getCharacteristicValue(it) }
        }

    }

    inner class LocalBinder : Binder() {
        val service: GattService
            get() = this@GattService
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onUnbind(intent: Intent): Boolean {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        if (mBluetoothGatt == null) return super.onUnbind(intent)
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
            if (mBluetoothManager == null) return false
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
        if (mBluetoothAdapter == null || address == null) return false

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

    private fun setResponseDescriptors(characteristic: BluetoothGattCharacteristic) {
        try {
            val descriptorTX = characteristic.getDescriptor(
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
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.
     */
    @Suppress("SameParameterValue")
    private fun setCharacteristicNotification(
            characteristic: BluetoothGattCharacteristic, enabled: Boolean
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

    @Throws(UnsupportedOperationException::class)
    fun setPuckServicesUUID()  {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw UnsupportedOperationException()
        }
        val services = supportedGattServices
        if (services.isNullOrEmpty()) throw UnsupportedOperationException()
        for (customService in services) {
            when (customService.uuid) {
                Nordic.NUS -> {
                    legacyInterface = false
                    break
                }
                Nordic.LegacyNUS -> {
                    legacyInterface = true
                    break
                }
                else -> {
                    continue
                }
            }
        }
        setCharacteristicRX()
    }

    private fun getCharacteristicRX(mCustomService: BluetoothGattService): BluetoothGattCharacteristic {
        var mReadCharacteristic = mCustomService.getCharacteristic(GattRX)
        if (mBluetoothGatt?.readCharacteristic(mReadCharacteristic) != true) run breaking@{
            mCustomService.characteristics.forEach {
                val customUUID = it.uuid
                /*get the read characteristic from the service*/
                if (customUUID.isUUID(GattRX)) {
                    Debug.verbose(this.javaClass, "GattReadCharacteristic: $customUUID")
                    mReadCharacteristic = mCustomService.getCharacteristic(customUUID)
                    return@breaking
                }
            }
        }
        return mReadCharacteristic
    }

    @Throws(UnsupportedOperationException::class)
    fun setCharacteristicRX() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw UnsupportedOperationException()
        }
        val mCustomService = mBluetoothGatt!!.getService(GattNUS)
        if (null == mCustomService) {
            val services = supportedGattServices
            if (services.isNullOrEmpty()) throw UnsupportedOperationException()
            for (service in services) {
                Debug.verbose(this.javaClass, "GattReadService: ${service.uuid}")
                mCharacteristicRX = getCharacteristicRX(service)
                break
            }
        } else {
            mCharacteristicRX = getCharacteristicRX(mCustomService)
        }
        mCharacteristicRX?.let { setCharacteristicNotification(it, true) }
    }

    private fun getCharacteristicTX(mCustomService: BluetoothGattService): BluetoothGattCharacteristic {
        var mWriteCharacteristic = mCustomService.getCharacteristic(GattTX)
        if (!mCustomService.characteristics.contains(mWriteCharacteristic)) {
            for (characteristic in mCustomService.characteristics) {
                val customUUID = characteristic.uuid
                if (customUUID.isUUID(GattTX)) {
                    Debug.verbose(this.javaClass, "GattWriteCharacteristic: $customUUID")
                    mWriteCharacteristic = mCustomService.getCharacteristic(customUUID)
                    break
                }

            }
        }
        return mWriteCharacteristic
    }

    @Throws(UnsupportedOperationException::class)
    fun setCharacteristicTX() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw UnsupportedOperationException()
        }
        val mCustomService = mBluetoothGatt!!.getService(GattNUS)
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
        mCharacteristicTX?.let { setCharacteristicNotification(it, true) }
    }

    private fun reliableWriteCharacteristic(value: ByteArray) {
//        mBluetoothGatt!!.beginReliableWrite()
//        mCharacteristicTX!!.writeType =
//                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
//        if (Version.isTiramisu) {
//            mBluetoothGatt!!.writeCharacteristic(
//                    mCharacteristicTX!!, value,
//                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
//            )
//        } else @Suppress("DEPRECATION") {
//            mCharacteristicTX!!.value = value
//            mBluetoothGatt!!.writeCharacteristic(mCharacteristicTX)
//        }
//        mBluetoothGatt!!.executeReliableWrite()
        delayedByteCharacteric(value)
    }

    private fun delayedWriteCharacteristic(value: ByteArray) {
        val chunks = value.toPortions(maxTransmissionUnit)
        val commandQueue = commandCallbacks.size + chunks.size
        gattHandler.postDelayed({
            var i = 0
            while (i < chunks.size) {
                val chunk = chunks[i]
                if (null == mCharacteristicTX) continue
                gattHandler.postDelayed({
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
                }, (i + 1) * chunkTimeout)
                i += 1
            }
        }, commandQueue * chunkTimeout)
    }

    private fun queueByteCharacteristic(value: ByteArray) {
        if (null == mCharacteristicTX) {
            try {
                setCharacteristicTX()
            } catch (e: UnsupportedOperationException) {
                Debug.warn(e)
            }
        }
        commandCallbacks.add(Runnable { delayedWriteCharacteristic(value) })
        if (commandCallbacks.size == 1) {
            commandCallbacks[0].run()
            commandCallbacks.removeAt(0)
        }
    }

    private fun delayedByteCharacteric(value: ByteArray) {
        queueByteCharacteristic(value)
    }

    private fun delayedWriteCharacteristic(value: String) {
        val chunks = value.toPortions(maxTransmissionUnit)
        val commandQueue = commandCallbacks.size + chunks.size
        gattHandler.postDelayed({
            chunks.forEachIndexed { i, chunk ->
                gattHandler.postDelayed({
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

    private fun queueTagCharacteristic(value: String) {
        if (null == mCharacteristicTX) {
            try {
                setCharacteristicTX()
            } catch (e: UnsupportedOperationException) {
                Debug.warn(e)
            }
        }
        commandCallbacks.add(Runnable { delayedWriteCharacteristic("tag.$value\n") })
        if (commandCallbacks.size == 1) {
            commandCallbacks[0].run()
            commandCallbacks.removeAt(0)
        }
    }

    private fun delayedTagCharacteristic(value: String) {
        queueTagCharacteristic(value)
    }

    private fun promptTagCharacteristic(value: String) {
        if (null == mCharacteristicTX) {
            try {
                setCharacteristicTX()
            } catch (e: UnsupportedOperationException) {
                Debug.warn(e)
            }
        }
        commandCallbacks.add(0, Runnable { delayedWriteCharacteristic("tag.$value\n") })
        if (commandCallbacks.size == 1) {
            commandCallbacks[0].run()
            commandCallbacks.removeAt(0)
        }
    }

    private fun queueScreenCharacteristic(value: String) {
        if (null == mCharacteristicTX) {
            try {
                setCharacteristicTX()
            } catch (e: UnsupportedOperationException) {
                Debug.warn(e)
            }
        }
        commandCallbacks.add(Runnable { delayedWriteCharacteristic("screen.$value\n") })
        if (commandCallbacks.size == 1) {
            commandCallbacks[0].run()
            commandCallbacks.removeAt(0)
        }
    }

    private fun delayedScreenCharacteristic(value: String) {
        queueScreenCharacteristic(value)
    }

    val deviceDetails: Unit
        get() {
            if (legacyInterface) {
                delayedByteCharacteric(byteArrayOf(PUCK.INFO.bytes))
            } else {
                delayedByteCharacteric(byteArrayOf(
                        0x66, 0x61, 0x73, 0x74, 0x4D, 0x6F, 0x64, 0x65, 0x28, 0x29, 0x0A
                ))
            }
        }

    val deviceAmiibo: Unit
        get() {
            when (serviceType) {
                Nordic.DEVICE.BLUUP, Nordic.DEVICE.FLASK, Nordic.DEVICE.SLIDE -> {
                    delayedTagCharacteristic("getList()")
                }
                Nordic.DEVICE.LOOP -> {
                    delayedByteCharacteric(byteArrayOf(
                            0x02, 0x01, 0x89.toByte(), 0x88.toByte(), 0x03
                    ))
                }
                Nordic.DEVICE.LINK -> {
                    delayedByteCharacteric(byteArrayOf(
                            0x00, 0x00, 0x10, 0x02, 0x33,
                            0x53, 0x34, 0xAB.toByte(), 0x1F, 0xE8.toByte(),
                            0xC2.toByte(), 0x6D, 0xE5.toByte(), 0x35, 0x27,
                            0x4B, 0x52, 0xE0.toByte(), 0x1F, 0x26
                    ))
                }
                Nordic.DEVICE.PUCK -> {
                    puckArray = ArrayList(slotsCount)
                    delayedByteCharacteric(byteArrayOf(PUCK.INFO.bytes, 0.toByte()))
                }
                else ->{

                }
            }
        }

    val activeAmiibo: Unit
        get() {
            delayedTagCharacteristic("get()")
        }

    private fun processLoopUpload(tagData: ByteArray): ArrayList<ByteArray> {
        val output = arrayListOf<ByteArray>()
        tagData.toPortions(128).forEachIndexed { index, bytes ->
            val newData = ByteArray(5 + bytes.size + 2)
            newData[0] = 0x02.toByte()
            newData[1] = (bytes.size + 3).toByte()
            newData[2] = 0x87.toByte()
            newData[3] = if (bytes.size < 128) 1 else 0
            newData[4] = index.toByte()
            bytes.copyInto(newData, 5)
            val xorValue = xorByteArray(newData.copyOfRange(1, newData.size - 2))
            newData[newData.size - 2] = xorValue
            newData[newData.size - 1] = 0x03.toByte()
            output.add(newData)
        }
        return output
    }

    private fun processLinkUpload() {
        val chunks = uploadData.toPortions(0x96)
        chunks.forEachIndexed { index, bytes ->
            val size = if (index == chunks.lastIndex) bytes.size else 0x96
            val tempArray = byteArrayOf(
                    0xDD.toByte(), 0xAA.toByte(), 0x00.toByte(), size.toByte()
            ).plus(bytes).plus(byteArrayOf(0x00.toByte(), index.toByte()))
            delayedByteCharacteric(tempArray)
        }
        delayedByteCharacteric(byteArrayOf(0xBC.toByte(), 0xBC.toByte()))
    }

    private fun processPuckUpload(slot: Byte) {
        val parameters: ArrayList<ByteArray> = arrayListOf()
        uploadData.toFileBytes().toPortions(maxTransmissionUnit).forEach {
            parameters.add(it)
        }
        parameters.add(byteArrayOf(PUCK.SAVE.bytes, slot))
        parameters.forEach {
            delayedByteCharacteric(it)
        }
    }

    private fun generateBlankData(): ByteArray {
        val blankData = ByteArray(NfcByte.TAG_FILE_SIZE)

        blankData[0] = 0x04.toByte()
        blankData[1] = Random.nextInt(256).toByte()
        blankData[2] = Random.nextInt(256).toByte()
        blankData[3] = (blankData[0].toInt()
                xor blankData[1].toInt()
                xor blankData[2].toInt()
                xor 0x88).toByte()
        blankData[4] = Random.nextInt(256).toByte()
        blankData[5] = Random.nextInt(256).toByte()
        blankData[6] = Random.nextInt(256).toByte()
        blankData[7] = Random.nextInt(256).toByte()
        blankData[8] = (blankData[4].toInt()
                xor blankData[5].toInt()
                xor blankData[6].toInt()
                xor blankData[7].toInt()).toByte()

        val static0x09 = byteArrayOf(
                0x48, 0x00, 0x00, 0xE1.toByte(), 0x10, 0x3E, 0x00, 0x03, 0x00, 0xFE.toByte()
        )
        static0x09.copyInto(blankData, 9)

        val static0x20B = byteArrayOf(
                0xBD.toByte(), 0x04, 0x00, 0x00, 0xFF.toByte(), 0x00, 0x05
        )
        static0x20B.copyInto(blankData, 0x20B)

        return blankData
    }

    fun uploadAmiiboData(tagData: ByteArray) {
        when (serviceType) {
            Nordic.DEVICE.LOOP -> {
                val uploadData = tagData.toDataBytes()
                uploadData[536] = 0x80.toByte()
                uploadData[537] = 0x80.toByte()
                val parameters = processLoopUpload(uploadData)
                chunkNumber = parameters.size
                parameters.forEach {
                    commandCallbacks.add(Runnable { delayedWriteCharacteristic(it) })
                }
                if (commandCallbacks.size == chunkNumber) {
                    commandCallbacks[0].run()
                    commandCallbacks.removeAt(0)
                }
            }
            Nordic.DEVICE.LINK -> {
                uploadData = tagData.toDataBytes()
                delayedByteCharacteric(byteArrayOf(0xA0.toByte(), 0xB0.toByte()))
            }
            else -> {

            }
        }
    }

    fun uploadPuckAmiibo(tagData: ByteArray, slot: Int) {
        val parameters: ArrayList<ByteArray> = arrayListOf()
        tagData.toFileBytes().toPages().forEachIndexed { index, bytes ->
            bytes?.let {
                parameters.add(byteArrayOf(
                        PUCK.WRITE.bytes, slot.toByte(), (index * NfcByte.PAGE_SIZE).toByte()
                ).plus(it))
            }
        }
        parameters.add(byteArrayOf(PUCK.SAVE.bytes, slot.toByte()))
//        parameters.forEach {
//            delayedByteCharacteric(it)
//        }
        parameters.forEach {
            commandCallbacks.add(Runnable { delayedWriteCharacteristic(it) })
        }
        if (commandCallbacks.size == parameters.size) {
            commandCallbacks[0].run()
            commandCallbacks.removeAt(0)
        }
//        uploadData = tagData.toFileBytes()
//        delayedByteCharacteric(byteArrayOf(PUCK.FWRITE.bytes, slot.toByte()))
    }

    fun uploadAmiiboFile(tagData: ByteArray, amiibo: Amiibo, index: Int, complete: Boolean) {
        val parameters: ArrayList<String> = arrayListOf()
        for (chunk in tagData.toPortions(128)) {
            val byteString = Base64.encodeToString(
                    chunk, Base64.NO_PADDING or Base64.NO_CLOSE or Base64.NO_WRAP
            )
            parameters.add("tagUploadChunk(\"$byteString\")")
        }
        amiibo.name?.let { name ->
            val nameUnicode = name.toUnicode()
            val nameIndexed = if (index > 0) "$index.$nameUnicode" else nameUnicode
            val amiiboName = truncateUnicode(nameIndexed, amiibo.bluupTail.length)
            parameters.add("saveUploadedTag(\"$amiiboName|${amiibo.bluupTail}|0\")")
        }
        if (complete) {
            parameters.add("uploadsComplete()")
            parameters.add("getList()")
        }
        parameters.forEach {
            commandCallbacks.add(Runnable {
                delayedWriteCharacteristic("tag.$it\n")
            })
        }
        delayedTagCharacteristic("startTagUpload(${tagData.size})")
    }

    fun setActiveAmiibo(amiiboName: String?, tail: String?) {
        amiiboName?.let { name ->
            if (name.startsWith("New Tag")) {
                delayedTagCharacteristic("setTag(\"$name|$tail|0\")")
                return
            }
            tail?.let {
                nameCompat = truncateUnicode(name.toUnicode(), it.length)
                tailCompat = it
                delayedTagCharacteristic("setTag(\"$nameCompat|$it|0\")")
            }
        }
    }

    fun setActiveAmiibo(slot: Int) {
        delayedByteCharacteric(byteArrayOf(PUCK.NFC.bytes, slot.toByte()))
        listener?.onPuckActiveChanged(slot)
    }

    fun deleteAmiibo(amiiboName: String?, tail: String?) {
        amiiboName?.let { name ->
            if (name.startsWith("New Tag")) {
                // delayedTagCharacteristic("remove(\"$name||$tail\")")
                delayedTagCharacteristic("remove(\"$name|$tail|0\")")
                return
            }
            tail?.let {
                nameCompat = truncateUnicode(name.toUnicode(), it.length)
                tailCompat = it
                delayedTagCharacteristic("remove(\"$nameCompat|$it|0\")")
            }
        }
    }

    fun downloadAmiiboData(fileName: String?, tail: String?) {
        tail?.let {
            fileName?.let { file ->
                val amiiboName = truncateUnicode(file.toUnicode(), it.length)
                delayedTagCharacteristic("download(\"$amiiboName|$it|0\")")
            }
        }
    }

    @Suppress("unused")
    fun downloadAmiiboData(slot: Int) {
        val parameters: ArrayList<ByteArray> = arrayListOf()
        for (i in 0..34) {
            parameters.add(byteArrayOf(PUCK.READ.bytes, slot.toByte(), (i * 4).toByte(), 0x04))
        }
        parameters.add(byteArrayOf(PUCK.READ.bytes, slot.toByte(), 0x8C.toByte(), 0x03))
        parameters.forEach {
            delayedByteCharacteric(it)
        }
    }

    private fun getDeviceAmiiboRange(index: Int) {
        delayedTagCharacteristic("getList($index,$listCount)") // 5 ... 5
    }

    fun createBlankTag() {
        when (serviceType) {
            Nordic.DEVICE.BLUUP, Nordic.DEVICE.FLASK, Nordic.DEVICE.SLIDE -> {
                delayedTagCharacteristic("createBlank()")
            }
            Nordic.DEVICE.LOOP -> {
                val blankData = generateBlankData()
                val tagData = blankData.toDataBytes()
                tagData[536] = 0x80.toByte()
                tagData[537] = 0x80.toByte()

                val parameters = processLoopUpload(tagData)
                chunkNumber = parameters.size
                parameters.forEach {
                    commandCallbacks.add(Runnable { delayedWriteCharacteristic(it) })
                }
                if (commandCallbacks.size == chunkNumber) {
                    commandCallbacks[0].run()
                    commandCallbacks.removeAt(0)
                }
            }
            Nordic.DEVICE.LINK -> {
                generateBlankData()
            }
            else -> {


            }
        }
    }

    fun clearStorage(count: Int) {
        wipeDeviceCount = count - 1
        delayedTagCharacteristic("remove(tag.get().name)")
    }

    fun setSortingMode(mode: Int) {
        when (serviceType) {
            Nordic.DEVICE.LOOP -> {
                delayedByteCharacteric(byteArrayOf(
                        0x02, 0x02, 0x8a.toByte(), mode.toByte(), (0x88 + mode).toByte(), 0x03
                ))
            }
            Nordic.DEVICE.LINK -> {
                delayedByteCharacteric(byteArrayOf(0x00, 0x00, 0x10, 0x03).plus(
                        when (mode) {
                            1 -> byteArrayOf(
                                    0x2F, 0x25, 0xD8.toByte(), 0x0C,
                                    0x49, 0x42, 0xC0.toByte(), 0xD5.toByte(),
                                    0x0B, 0x0B, 0xC6.toByte(), 0xDF.toByte(),
                                    0xCA.toByte(), 0x60, 0x21, 0xFC.toByte()
                            )
                            2 -> byteArrayOf(
                                    0xE3.toByte(), 0x96.toByte(), 0x51, 0xEC.toByte(),
                                    0x07, 0xE7.toByte(), 0xE5.toByte(), 0x54,
                                    0x37, 0xB6.toByte(), 0x13, 0x8E.toByte(),
                                    0x80.toByte(), 0xC9.toByte(), 0xB3.toByte(), 0x09
                            )
                            else -> byteArrayOf(
                                    0x34, 0x1F, 0x98.toByte(), 0xE8.toByte(),
                                    0x46, 0x19, 0x85.toByte(), 0x75,
                                    0xE3.toByte(), 0xD3.toByte(), 0xE0.toByte(), 0x42,
                                    0x5D, 0x41, 0x89.toByte(), 0x42
                            )
                        }
                ))
            }
            else -> {

            }
        }
    }

    fun resetDevice() {
        when (serviceType) {
            Nordic.DEVICE.BLUUP, Nordic.DEVICE.FLASK, Nordic.DEVICE.SLIDE -> {
                delayedScreenCharacteristic("reset()")
            }
            Nordic.DEVICE.LOOP -> {
                delayedByteCharacteric(byteArrayOf(
                        0x12, 0x0d, 0x00, 0x02, 0x01, 0x8f.toByte(), 0x8e.toByte(), 0x03
                ))
                listener?.onProcessFinish()
            }
            Nordic.DEVICE.LINK -> {
                delayedByteCharacteric(byteArrayOf(
                        0x00, 0x00, 0x10, 0x02, 0xAA.toByte(),
                        0x54, 0x54, 0x2B, 0xD5.toByte(), 0xCC.toByte(),
                        0x22, 0x42, 0x36, 0x7D, 0x6D,
                        0xB2.toByte(), 0x6A, 0xAC.toByte(), 0xA6.toByte(), 0xAC.toByte()
                ))
            }
            else -> {

            }
        }
    }

    fun setFlaskFace(stacked: Boolean) {
        delayedScreenCharacteristic("setFace(" + (if (stacked) 1 else 0) + ")")
    }

    private fun fixBluupDetails(amiiboName: String?, tail: String?) {
        tail?.let {
            amiiboName?.let { amiibo ->
                val fixedName = truncateUnicode(amiibo.toUnicode(), it.length)
                promptTagCharacteristic(
                        "rename(\"$fixedName|$it\",\"$fixedName|$it|0\" )"
                )
                deviceAmiibo
            }
        }
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

    private fun decipherFirmware(data: ByteArray): String {
        return data.sliceArray(3 until data[1].toInt() + 3).toString(Charset.defaultCharset()).also { firmware ->
            Debug.warn(this.javaClass, "${serviceType.logTag} $firmware")
            val version = firmware.substring(0, firmware.lastIndexOf("-")).filter { it.isDigit() }
            Debug.warn(this.javaClass, "${serviceType.logTag} $version")
        }
    }

    private fun truncateUnicode(unicodeName: String, tailSize: Int) : String {
        return unicodeName.run {
            val nameLength = length + tailSize + 3 // |tail|#
            if (nameLength > 28) substring(0, length - (nameLength - 28)) else this
        }
    }

    private fun xorByteArray(byteArray: ByteArray): Byte {
        if (byteArray.isEmpty()) {
            throw IllegalArgumentException("Empty collection can't be reduced.")
        }
        var result = byteArray[0]
        for (i in 1 until byteArray.size) {
            result = (result.toInt() xor byteArray[i].toInt()).toByte()
        }
        return (result.toInt() and 0xFF).toByte()
    }

    companion object {
        private var legacyInterface = false
        val GattNUS: UUID = if (legacyInterface) Nordic.LegacyNUS else Nordic.NUS
        val GattTX: UUID = if (legacyInterface) Nordic.LegacyTX else Nordic.TX
        val GattRX: UUID = if (legacyInterface) Nordic.LegacyRX else Nordic.RX
    }
}