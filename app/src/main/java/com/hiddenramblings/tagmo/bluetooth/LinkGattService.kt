/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */
package com.hiddenramblings.tagmo.bluetooth

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.content.Intent
import android.os.*
import androidx.annotation.RequiresApi
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.nfctech.TagArray
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.math.floor

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("MissingPermission")
class LinkGattService : Service() {
    private var listener: LinkBluetoothListener? = null
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
    private val chunkTimeout = 25L
    fun setListener(listener: LinkBluetoothListener?) {
        this.listener = listener
    }

    private val commandCallbacks = ArrayList<Runnable>()
    private val linkHandler = Handler(Looper.getMainLooper())
    private val listCount = 10

    interface LinkBluetoothListener {
        fun onLinkServicesDiscovered()
        fun onLinkActiveChanged(jsonObject: JSONObject?)
        fun onLinkStatusChanged(jsonObject: JSONObject?)
        fun onLinkListRetrieved(jsonArray: JSONArray)
        fun onLinkRangeRetrieved(jsonArray: JSONArray)
        fun onLinkFilesDownload(dataString: String)
        fun onLinkProcessFinish()
        fun onLinkConnectionLost()
    }

    private var response = StringBuilder()
    private var rangeIndex = 0

    private fun getCharacteristicValue(characteristic: BluetoothGattCharacteristic, output: String?) {
        if (!output.isNullOrEmpty()) {
            Debug.verbose(this.javaClass, "${getLogTag(characteristic.uuid)} $output")
            if (characteristic.uuid.compareTo(LinkRX) == 0) {

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
                listener?.onLinkConnectionLost()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (Version.isLollipop)
                    gatt.requestMtu(512) // Maximum: 517
                else listener?.onLinkServicesDiscovered()
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
            listener?.onLinkServicesDiscovered()
        }
    }

    inner class LocalBinder : Binder() {
        val service: LinkGattService
            get() = this@LinkGattService
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
        try {
            val descriptorTX = characteristic.getDescriptor(
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

    private fun getCharacteristicRX(mCustomService: BluetoothGattService): BluetoothGattCharacteristic {
        var mReadCharacteristic = mCustomService.getCharacteristic(LinkRX)
        if (mBluetoothGatt?.readCharacteristic(mReadCharacteristic) != true) run breaking@{
            mCustomService.characteristics.forEach {
                val customUUID = it.uuid
                /*get the read characteristic from the service*/
                if (customUUID.compareTo(LinkRX) == 0) {
                    Debug.verbose(this.javaClass, "GattReadCharacteristic: $customUUID")
                    mReadCharacteristic = mCustomService.getCharacteristic(customUUID)
                    return@breaking
                }
            }
        }
        return mReadCharacteristic
    }

    @Throws(UnsupportedOperationException::class)
    fun setLinkCharacteristicRX() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw UnsupportedOperationException()
        }
        val mCustomService = mBluetoothGatt!!.getService(LinkNUS)
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
        var mWriteCharacteristic = mCustomService.getCharacteristic(LinkTX)
        if (!mCustomService.characteristics.contains(mWriteCharacteristic)) {
            for (characteristic in mCustomService.characteristics) {
                val customUUID = characteristic.uuid
                if (customUUID.compareTo(LinkTX) == 0) {
                    Debug.verbose(this.javaClass, "GattWriteCharacteristic: $customUUID")
                    mWriteCharacteristic = mCustomService.getCharacteristic(customUUID)
                    break
                }

            }
        }
        return mWriteCharacteristic
    }

    @Throws(UnsupportedOperationException::class)
    fun setLinkCharacteristicTX() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw UnsupportedOperationException()
        }
        val mCustomService = mBluetoothGatt!!.getService(LinkNUS)
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

    private fun delayedWriteCharacteristic(value: ByteArray) {
        val chunks = GattArray.byteToPortions(value, maxTransmissionUnit)
        val commandQueue = commandCallbacks.size + chunks.size
        linkHandler.postDelayed({
            chunks.forEachIndexed { i, chunk ->
                linkHandler.postDelayed({
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
                        listener?.onLinkServicesDiscovered()
                    }
                }, (i + 1) * chunkTimeout)
            }
        }, commandQueue * chunkTimeout)
    }

    private fun queueByteCharacteristic(value: ByteArray, index: Int) {
        if (null == mCharacteristicTX) {
            try {
                setLinkCharacteristicTX()
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

    private fun delayedByteCharacteric(value: ByteArray) {
        queueByteCharacteristic(value, commandCallbacks.size)
    }

    fun uploadAmiiboFile(tagData: ByteArray, amiibo: Amiibo, index: Int, complete: Boolean) {
        delayedByteCharacteric(byteArrayOf(0xA0.toByte(), 0xB0.toByte()))
        delayedByteCharacteric(byteArrayOf(
            0xAC.toByte(), 0xAC.toByte(), 0x00, 0x04, 0x00, 0x00, 0x02, 0x1C
        ))
        delayedByteCharacteric(byteArrayOf(0xAB.toByte(), 0xAB.toByte(), 0x02, 0x1C))

        val parameters: ArrayList<ByteArray> = arrayListOf()
        GattArray.byteToPortions(tagData, 20).forEachIndexed { i, chunk ->
            val iteration = floor(i / 20F) + 1
            val bytes: ByteArray = byteArrayOf(0xDD.toByte(), 0xAA.toByte(), 0x00, 0x14)
            bytes.plus(chunk)
            bytes.plus(0).plus(iteration.toInt().toByte())
            parameters.add(bytes)
        }

        if (complete) {
            parameters.add(byteArrayOf(0xBC.toByte(), 0xBC.toByte()))
            parameters.add(byteArrayOf(0xCC.toByte(), 0xDD.toByte()))
        }
        parameters.forEach {
            commandCallbacks.add(commandCallbacks.size, Runnable {
                delayedByteCharacteric(it)
            })
        }
    }

    val activeAmiibo: Unit
        get() {

        }
    val deviceAmiibo: Unit
        get() {

        }

    private fun getLogTag(uuid: UUID): String {
        return when {
            uuid.compareTo(LinkTX) == 0 -> {
                "LinkTX"
            }
            uuid.compareTo(LinkRX) == 0 -> {
                "LinkRX"
            }
            uuid.compareTo(LinkNUS) == 0 -> {
                "LinkNUS"
            }
            else -> {
                uuid.toString()
            }
        }
    }

    companion object {
        val LinkNUS: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        private val LinkTX = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        private val LinkRX = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    }
}