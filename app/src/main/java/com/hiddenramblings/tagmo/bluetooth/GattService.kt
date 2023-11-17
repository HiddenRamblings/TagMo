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
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import androidx.annotation.RequiresApi
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.bluetooth.GattArray.getCharacteristicByProperty
import com.hiddenramblings.tagmo.bluetooth.GattArray.hasProperty
import com.hiddenramblings.tagmo.bluetooth.GattArray.toPortions
import com.hiddenramblings.tagmo.bluetooth.GattArray.toUnicode
import com.hiddenramblings.tagmo.bluetooth.Nordic.logTag
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.nfctech.NfcByte
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.hiddenramblings.tagmo.nfctech.TagArray.toHex
import com.hiddenramblings.tagmo.nfctech.TagArray.toPages
import com.hiddenramblings.tagmo.nfctech.TagArray.toSignedArray
import com.hiddenramblings.tagmo.nfctech.TagArray.toTagArray
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.Objects
import java.util.UUID

@SuppressLint("MissingPermission")
class GattService : Service() {
    private var listener: BluetoothGattListener? = null
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

    enum class SORTING {
        MANUAL, SEQUENTIAL, AUTO;
    }

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
        fun onPixlUpdateRequired()

        fun onPuckServicesDiscovered()
        fun onPuckActiveChanged(slot: Int)
        fun onPuckDeviceProfile(activeSlot: Int, slotCount: Int)
        fun onPuckListRetrieved(slotData: ArrayList<ByteArray>)
        fun onPuckTagReloaded()

        fun onFilesDownload(tagData: ByteArray)
        fun onProcessFinish(showMenu: Boolean)
        fun onConnectionLost()
    }

    private val emptyAdapater: Boolean get() { return mBluetoothAdapter == null }

    private fun getCharacteristicValue(characteristic: BluetoothGattCharacteristic, data: ByteArray?) {
        if (data?.isNotEmpty() == true) {
            Debug.info(Companion::class.java, "${characteristic.uuid.logTag} ${data.toHex()}")
            if (characteristic.hasProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)) {
                val hexData = data.toHex()
                when (serviceType) {
                    Nordic.DEVICE.PIXL -> {

                    }

                    Nordic.DEVICE.LOOP -> {
                        when {
                            hexData == "0201000103" -> {
                                chunkNumber -= 1
                                if (chunkNumber == 0) listener?.onProcessFinish(true)
                            }

                            hexData.contains("416D694C6F6F705F46575F56") -> {
                                listener?.onPixlConnected(decipherFirmware(data))
                            }

                            else -> { }
                        }
                    }

                    Nordic.DEVICE.LINK -> {
                        if (hexData.startsWith("00002013")) {
                            listener?.onPixlConnected(hexData.substring(8, hexData.length))
                        } else {
                            when (hexData) {
                                "00001002E346EA49B8A3B2541F1CCAB1F93FCF43" -> { }
                                "000010029AA82CA5A943CBA3304A195EA40E4691" -> { }
                                "00001002D8F1889E45DA37E7205C1BCF497B28FD" -> { }
                                "00001002CE9BD933FB8C34A4776E2CDA19DE1091" -> { }
                                "00001002034068D444930159EB6E21A4202E061C" -> { }
                                "000010029C4D9FC65E4AA40A1DA07BCAD5661703" -> {
                                    listener?.onProcessFinish(true)
                                }
                                else -> { }
                            }
                        }
                    }

                    Nordic.DEVICE.PUCK -> {
                        when {
                            TagArray.hexToString(hexData).endsWith("DTM_PUCK_FAST") -> {
                                queueByteCharacteristic(byteArrayOf(PUCK.INFO.bytes))
                            }

                            tempInfoData.isNotEmpty() -> {
                                val sliceData = tempInfoData.plus(data)
                                puckArray.add(sliceData[1].toInt(), sliceData.copyOfRange(2, sliceData.size))
                                tempInfoData = byteArrayOf()
                                if (puckArray.size == slotsCount) {
                                    listener?.onPuckListRetrieved(puckArray)
                                } else {
                                    val nextSlot = sliceData[1].toInt() + 1
                                    queueByteCharacteristic(byteArrayOf(PUCK.INFO.bytes, nextSlot.toByte()))
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
                                queueByteCharacteristic(byteArrayOf(PUCK.NFC.bytes))
                            }

                            data[0] == PUCK.NFC.bytes -> {
                                listener?.onPuckTagReloaded()
                            }
                        }
                    }
                    else -> { }
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
            Debug.info(Companion::class.java, "${characteristic.uuid.logTag} $output")
            if (characteristic.hasProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)) {
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
                            if (rangeIndex == 0) listener?.onProcessFinish(false)
                        }
                    }
                    formatted.startsWith("tag.remove") -> {
                        if (formatted.endsWith(">") || formatted.endsWith("\n")) {
                            if (wipeDeviceCount > 0) {
                                wipeDeviceCount -= 1
                                queueTagCharacteristic("remove(tag.get().name)")
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
                                    Debug.info(Companion::class.java, dataString)
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

    @Suppress("deprecation")
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
            else -> { }
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
            Debug.verbose(Companion::class.java, "${serviceType.logTag} onServicesDiscovered $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (Version.isLollipop) {
                    gatt.requestMtu(247) // Nordic
                    return
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
                    else -> { }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Debug.verbose(Companion::class.java, "${serviceType.logTag} onMtuChange $mtu $status")
            if (status == BluetoothGatt.GATT_SUCCESS)
                maxTransmissionUnit = mtu - 3
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
                else -> { }
            }
        }

        override fun onCharacteristicRead(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS)
                getCharacteristicValue(characteristic, value)
        }
        
        @Deprecated("Deprecated in Java", ReplaceWith("if (status == BluetoothGatt.GATT_SUCCESS) getCharacteristicValue(characteristic)", "android.bluetooth.BluetoothGatt"))
        override fun onCharacteristicRead(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS)
                getCharacteristicValue(characteristic)
        }

        override fun onCharacteristicWrite(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            Debug.verbose(Companion::class.java, "${characteristic.uuid.logTag} onCharacteristicWrite $status")
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
                else -> { }
            }
        }
        
        @Deprecated("Deprecated in Java", ReplaceWith("getCharacteristicValue(characteristic)"))
        override fun onCharacteristicChanged(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
        ) {
            getCharacteristicValue(characteristic)
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
        mBluetoothGatt?.close() ?: return super.onUnbind(intent)
        mBluetoothGatt = null
        return super.onUnbind(intent)
    }

    private val mBinder: IBinder = LocalBinder()

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
        mBluetoothAdapter = with (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager) { adapter }
        if (emptyAdapater || address == null) return false

        // Previously connected device.  Try to reconnect.
        mBluetoothGatt?.let {
            if (address == mBluetoothDeviceAddress && it.connect()) return true
        }
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
        if (emptyAdapater) return
        mBluetoothGatt?.disconnect()
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
        if (emptyAdapater) return
        characteristic?.let {
            mBluetoothGatt?.setCharacteristicNotification(it, enabled)
            try {
                val descriptor = it.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                )
                val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (Version.isTiramisu) {
                    mBluetoothGatt?.writeDescriptor(descriptor, value)
                } else @Suppress("deprecation") {
                    descriptor.value = value
                    mBluetoothGatt?.writeDescriptor(descriptor)
                }
            } catch (ignored: Exception) { }
        }
    }

    @Throws(IllegalAccessException::class, UnsupportedOperationException::class)
    fun setPuckServicesUUID()  {
        if (emptyAdapater || mBluetoothGatt == null)
            throw IllegalAccessException(getString(R.string.fail_bluetooth_adapter))
        legacyInterface = mBluetoothGatt!!.services.any { service ->
            service.characteristics.any { it.uuid == Nordic.LegacyNUS }
        }
        setCharacteristicRX()
    }

    fun setOmllboServicesUUID()  {
        if (emptyAdapater || mBluetoothGatt == null)
            throw IllegalAccessException(getString(R.string.fail_bluetooth_adapter))
        omllboInterface = mBluetoothGatt!!.services.any { service ->
            service.characteristics.any { it.uuid == Nordic.OmllboNUS }
        }
        setCharacteristicRX()
    }

    @Throws(IllegalAccessException::class, UnsupportedOperationException::class)
    fun setCharacteristicRX() {
        if (emptyAdapater)
            throw IllegalAccessException(getString(R.string.fail_bluetooth_adapter))
        mBluetoothGatt?.let { gatt ->
            mCharacteristicRX = gatt.getService(GattNUS).getCharacteristic(GattRX)
                    ?: gatt.getCharacteristicByProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)
                    ?: throw UnsupportedOperationException(getString(R.string.characteristic_null))
        } ?: throw IllegalAccessException(getString(R.string.fail_bluetooth_adapter))
        setCharacteristicNotification(mCharacteristicRX, true)
    }

    @Throws(IllegalAccessException::class, UnsupportedOperationException::class)
    fun setCharacteristicTX() {
        if (mBluetoothAdapter == null)
            throw IllegalAccessException(getString(R.string.fail_bluetooth_adapter))
        mBluetoothGatt?.let { gatt ->
            mCharacteristicTX = gatt.getService(GattNUS).getCharacteristic(GattTX)
                    ?: gatt.getCharacteristicByProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
                    ?: throw UnsupportedOperationException(getString(R.string.characteristic_null))
        } ?: throw IllegalAccessException(getString(R.string.fail_bluetooth_adapter))
        setCharacteristicNotification(mCharacteristicTX, true)

    }

    private fun reliableWriteCharacteristic(value: ByteArray) {
        /*
        mBluetoothGatt!!.beginReliableWrite()
        mCharacteristicTX!!.writeType =
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (Version.isTiramisu) {
            mBluetoothGatt!!.writeCharacteristic(
                    mCharacteristicTX!!, value,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else @Suppress("deprecation") {
            mCharacteristicTX!!.value = value
            mBluetoothGatt!!.writeCharacteristic(mCharacteristicTX)
        }
        mBluetoothGatt!!.executeReliableWrite()
        */
        queueByteCharacteristic(value)
    }

    private fun delayedWriteCharacteristic(value: ByteArray) {
        val chunks = value.toPortions(maxTransmissionUnit)
        val commandQueue = commandCallbacks.size + chunks.size
        gattHandler.postDelayed({
            for (i in 0 until chunks.size) {
                val chunk = chunks[i]
                if (null == mCharacteristicTX) break
                gattHandler.postDelayed({
                    mCharacteristicTX?.let {
                        it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        if (Version.isTiramisu) {
                            mBluetoothGatt!!.writeCharacteristic(
                                    it, chunk, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            )
                        } else @Suppress("deprecation") {
                            it.value = chunk
                            mBluetoothGatt!!.writeCharacteristic(it)
                        }
                    }
                }, (i + 1) * chunkTimeout)
            }
        }, commandQueue * chunkTimeout)
    }

    private fun delayedWriteCharacteristic(value: String) {
        delayedWriteCharacteristic(value.encodeToByteArray())
    }

    private fun queueByteCharacteristic(value: ByteArray) {
        if (null == mCharacteristicTX) {
            try {
                setCharacteristicTX()
            } catch (e: Exception) {
                Debug.warn(e)
            }
        }
        commandCallbacks.add(Runnable { delayedWriteCharacteristic(value) })
        if (commandCallbacks.size == 1) {
            commandCallbacks[0].run()
            commandCallbacks.removeAt(0)
        }
    }

    private fun queueTagCharacteristic(value: String) {
        if (null == mCharacteristicTX) {
            try {
                setCharacteristicTX()
            } catch (e: Exception) {
                Debug.warn(e)
            }
        }
        commandCallbacks.add(Runnable { delayedWriteCharacteristic("tag.$value\n") })
        if (commandCallbacks.size == 1) {
            commandCallbacks[0].run()
            commandCallbacks.removeAt(0)
        }
    }

    private fun promptTagCharacteristic(value: String) {
        if (null == mCharacteristicTX) {
            try {
                setCharacteristicTX()
            } catch (e: Exception) {
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
            } catch (e: Exception) {
                Debug.warn(e)
            }
        }
        commandCallbacks.add(Runnable { delayedWriteCharacteristic("screen.$value\n") })
        if (commandCallbacks.size == 1) {
            commandCallbacks[0].run()
            commandCallbacks.removeAt(0)
        }
    }

    val deviceDetails: Unit
        get() {
            if (legacyInterface) {
                queueByteCharacteristic(byteArrayOf(PUCK.INFO.bytes))
            } else {
                queueByteCharacteristic(byteArrayOf(
                        0x66, 0x61, 0x73, 0x74, 0x4D, 0x6F, 0x64, 0x65, 0x28, 0x29, 0x0A
                ))
            }
        }

    val deviceAmiibo: Unit
        get() {
            when (serviceType) {
                Nordic.DEVICE.BLUUP, Nordic.DEVICE.FLASK, Nordic.DEVICE.SLIDE -> {
                    queueTagCharacteristic("getList()")
                }
                Nordic.DEVICE.LOOP -> {
                    queueByteCharacteristic(byteArrayOf(
                            0x02, 0x01, 0x89.toByte(), 0x88.toByte(), 0x03
                    ))
                }
                Nordic.DEVICE.LINK -> {
                    queueByteCharacteristic(byteArrayOf(
                            0x00, 0x00, 0x10, 0x02, 0x33,
                            0x53, 0x34, 0xAB.toByte(), 0x1F, 0xE8.toByte(),
                            0xC2.toByte(), 0x6D, 0xE5.toByte(), 0x35, 0x27,
                            0x4B, 0x52, 0xE0.toByte(), 0x1F, 0x26
                    ))
                }
                Nordic.DEVICE.PUCK -> {
                    puckArray = ArrayList(slotsCount)
                    queueByteCharacteristic(byteArrayOf(PUCK.INFO.bytes, 0.toByte()))
                }
                else ->{

                }
            }
        }

    val activeAmiibo: Unit
        get() {
            queueTagCharacteristic("get()")
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

    private fun processPuckUpload(slot: Byte) {
        val parameters: ArrayList<ByteArray> = arrayListOf()
        uploadData.toSignedArray().toPortions(maxTransmissionUnit).forEach {
            parameters.add(it)
        }
        parameters.add(byteArrayOf(PUCK.SAVE.bytes, slot))
        parameters.forEach {
            commandCallbacks.add(Runnable { delayedWriteCharacteristic(it) })
        }
        if (commandCallbacks.size == parameters.size) {
            commandCallbacks[0].run()
            commandCallbacks.removeAt(0)
        }
    }

    fun uploadAmiiboData(tagData: ByteArray) {
        val byteData = tagData.toTagArray()
        when (serviceType) {
            Nordic.DEVICE.LOOP -> {
                byteData[536] = 0x80.toByte()
                byteData[537] = 0x80.toByte()
                val parameters = processLoopUpload(byteData)
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
                val parameters: ArrayList<ByteArray> = arrayListOf()
                parameters.add(byteArrayOf(0xA0.toByte(), 0xB0.toByte()))
                parameters.add(byteArrayOf(
                        0xAC.toByte(), 0xAC.toByte(), 0x00.toByte(), 0x04.toByte(),
                        0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x1C.toByte())
                )
                parameters.add(byteArrayOf(
                        0xAB.toByte(), 0xAB.toByte(), 0x02.toByte(), 0x1C.toByte()
                ))
                val chunks = byteData.toPortions(0x96)
                chunks.forEachIndexed { index, bytes ->
                    val size = if (index == chunks.lastIndex) bytes.size else 0x96
                    val tempArray = byteArrayOf(
                            0xDD.toByte(), 0xAA.toByte(), 0x00.toByte(), size.toByte()
                    ).plus(bytes).plus(byteArrayOf(0x00.toByte(), index.toByte()))
                    parameters.add(tempArray)
                }
                parameters.add(byteArrayOf(0xBC.toByte(), 0xBC.toByte()))
                parameters.add(byteArrayOf(0xCC.toByte(), 0xDD.toByte()))
                parameters.forEach {
                    commandCallbacks.add(Runnable { delayedWriteCharacteristic(it) })
                }
                if (commandCallbacks.size == parameters.size) {
                    commandCallbacks[0].run()
                    commandCallbacks.removeAt(0)
                }
            }
            else -> { }
        }
    }

    fun uploadPuckAmiibo(tagData: ByteArray, slot: Int) {
        val parameters: ArrayList<ByteArray> = arrayListOf()
        tagData.toSignedArray().toPages().forEachIndexed { index, bytes ->
            bytes?.let {
                parameters.add(byteArrayOf(
                        PUCK.WRITE.bytes, slot.toByte(), (index * NfcByte.PAGE_SIZE).toByte()
                ).plus(it))
            }
        }
        parameters.add(byteArrayOf(PUCK.SAVE.bytes, slot.toByte()))
        parameters.forEach {
            commandCallbacks.add(Runnable { delayedWriteCharacteristic(it) })
        }
        if (commandCallbacks.size == parameters.size) {
            commandCallbacks[0].run()
            commandCallbacks.removeAt(0)
        }
//        uploadData = tagData.toFileBytes()
//        queueByteCharacteristic(byteArrayOf(PUCK.FWRITE.bytes, slot.toByte()))
    }

    fun uploadAmiiboFile(tagData: ByteArray, amiibo: Amiibo, index: Int, complete: Boolean) {
        val parameters: ArrayList<String> = arrayListOf()
        parameters.add("startTagUpload(${tagData.size})")
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
        if (commandCallbacks.size == parameters.size) {
            commandCallbacks[0].run()
            commandCallbacks.removeAt(0)
        }
    }

    fun setActiveAmiibo(amiiboName: String?, tail: String?) {
        amiiboName?.let { name ->
            if (name.startsWith("New Tag")) {
                queueTagCharacteristic("setTag(\"$name|$tail|0\")")
                return
            }
            tail?.let {
                nameCompat = truncateUnicode(name.toUnicode(), it.length)
                tailCompat = it
                queueTagCharacteristic("setTag(\"$nameCompat|$it|0\")")
            }
        }
    }

    fun setActiveAmiibo(slot: Int) {
        queueByteCharacteristic(byteArrayOf(PUCK.NFC.bytes, slot.toByte()))
        listener?.onPuckActiveChanged(slot)
    }

    fun deleteAmiibo(amiiboName: String?, tail: String?) {
        amiiboName?.let { name ->
            if (name.startsWith("New Tag")) {
                queueTagCharacteristic("remove(\"$name|$tail|0\")")
                return
            }
            tail?.let {
                nameCompat = truncateUnicode(name.toUnicode(), it.length)
                tailCompat = it
                queueTagCharacteristic("remove(\"$nameCompat|$it|0\")")
            }
        }
    }

    fun downloadAmiiboData(fileName: String?, tail: String?) {
        tail?.let {
            fileName?.let { file ->
                val amiiboName = truncateUnicode(file.toUnicode(), it.length)
                queueTagCharacteristic("download(\"$amiiboName|$it|0\")")
            }
        }
    }

    @Suppress("unused")
    fun downloadAmiiboData(slot: Int) {
        val parameters: ArrayList<ByteArray> = arrayListOf()
        for (i in 0..35) {
            parameters.add(byteArrayOf(PUCK.READ.bytes, slot.toByte(), (i * 4).toByte(), 0x04))
        }
        parameters.add(byteArrayOf(PUCK.READ.bytes, slot.toByte(), 0x8C.toByte(), 0x03))
        parameters.forEach {
            queueByteCharacteristic(it)
        }
    }

    fun getAmiiboDetails() {
        queueTagCharacteristic("getData()")
    }

    private fun getDeviceAmiiboRange(index: Int) {
        queueTagCharacteristic("getList($index,$listCount)") // 5 ... 5
    }

    fun createBlankTag() {
        when (serviceType) {
            Nordic.DEVICE.BLUUP, Nordic.DEVICE.FLASK, Nordic.DEVICE.SLIDE -> {
                queueTagCharacteristic("createBlank()")
            }
            Nordic.DEVICE.LOOP, Nordic.DEVICE.LINK -> {
                uploadAmiiboData(GattArray.generateBlank())
            }
            else -> { }
        }
    }

    fun clearStorage(count: Int) {
        wipeDeviceCount = count - 1
        queueTagCharacteristic("remove(tag.get().name)")
    }

    fun setSortingMode(mode: Int) {
        when (serviceType) {
            Nordic.DEVICE.LOOP -> {
                queueByteCharacteristic(byteArrayOf(
                        0x02, 0x02, 0x8a.toByte(), mode.toByte(), (0x88 + mode).toByte(), 0x03
                ))
            }
            Nordic.DEVICE.LINK -> {
                queueByteCharacteristic(byteArrayOf(0x00, 0x00, 0x10, 0x03).plus(
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
            else -> { }
        }
    }

    fun resetDevice() {
        when (serviceType) {
            Nordic.DEVICE.BLUUP, Nordic.DEVICE.FLASK, Nordic.DEVICE.SLIDE -> {
                queueScreenCharacteristic("reset()")
            }
            Nordic.DEVICE.LOOP -> {
                queueByteCharacteristic(byteArrayOf(
                        0x12, 0x0d, 0x00, 0x02, 0x01, 0x8f.toByte(), 0x8e.toByte(), 0x03
                ))
                listener?.onProcessFinish(true)
            }
            Nordic.DEVICE.LINK -> {
                queueByteCharacteristic(byteArrayOf(
                        0x00, 0x00, 0x10, 0x02, 0xAA.toByte(),
                        0x54, 0x54, 0x2B, 0xD5.toByte(), 0xCC.toByte(),
                        0x22, 0x42, 0x36, 0x7D, 0x6D,
                        0xB2.toByte(), 0x6A, 0xAC.toByte(), 0xA6.toByte(), 0xAC.toByte()
                ))
                queueByteCharacteristic(byteArrayOf(0xA2.toByte(), 0xB2.toByte()))
                listener?.onProcessFinish(true)
            }
            else -> { }
        }
    }

    fun setFlaskFace(stacked: Boolean) {
        queueScreenCharacteristic("setFace(" + (if (stacked) 1 else 0) + ")")
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
        return data.sliceArray(3 until data[1].toInt() + 3)
                .toString(Charset.defaultCharset()).also { firmware ->
            if (firmware.split("-")[0].filter { it.isDigit() }.toInt() < 103)
                listener?.onPixlUpdateRequired()
        }
    }

    private fun truncateUnicode(unicodeName: String, tailSize: Int) : String {
        return unicodeName.run {
            val nameLength = length + tailSize + 3 // |tail|#
            if (nameLength > 28) substring(0, length - (nameLength - 28)) else this
        }
    }

    private fun xorByteArray(byteArray: ByteArray): Byte {
        if (byteArray.isEmpty()) throw IllegalArgumentException(getString(R.string.xor_invalid))
        var result = byteArray[0]
        for (i in 1 until byteArray.size) {
            result = (result.toInt() xor byteArray[i].toInt()).toByte()
        }
        return (result.toInt() and 0xFF).toByte()
    }

    companion object {
        private var legacyInterface = false
        private var omllboInterface = false
        val GattNUS: UUID = when {
            legacyInterface -> Nordic.LegacyNUS
            omllboInterface -> Nordic.OmllboNUS
            else -> Nordic.NUS
        }
        val GattTX: UUID = when {
            legacyInterface -> Nordic.LegacyTX
            omllboInterface -> Nordic.OmllboTX
            else -> Nordic.TX
        }
        val GattRX: UUID = when {
            legacyInterface -> Nordic.LegacyRX
            omllboInterface -> Nordic.OmllboRX
            else -> Nordic.RX
        }
    }
}