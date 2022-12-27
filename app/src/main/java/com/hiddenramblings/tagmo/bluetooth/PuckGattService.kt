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
import androidx.annotation.RequiresApi
import com.hiddenramblings.tagmo.bluetooth.GattArray.byteToPortions
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.nfctech.NfcByte
import java.util.*

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("MissingPermission")
class PuckGattService : Service() {
    private var listener: BluetoothGattListener? = null
    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothDeviceAddress: String? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mCharacteristicRX: BluetoothGattCharacteristic? = null
    private var mCharacteristicTX: BluetoothGattCharacteristic? = null
    private var maxTransmissionUnit = 23
    private val chunkTimeout = 30L

    // Command, Slot, Parameters
    @Suppress("UNUSED")
    private enum class PUCK(bytes: Int) {
        INFO(0x01),
        READ(0x02),
        WRITE(0x03),
        SAVE(0x04),
        MOVE(0xFD),
        UART(0xFE),
        NFC(0xFF);

        // RESTART
        val bytes: Byte

        init {
            this.bytes = bytes.toByte()
        }
    }

    private var activeSlot = 0
    private var slotsCount = 32
    fun setListener(listener: BluetoothGattListener?) {
        this.listener = listener
    }

    private val commandCallbacks = ArrayList<Runnable>()
    private val puckHandler = Handler(Looper.getMainLooper())

    interface BluetoothGattListener {
        fun onServicesDiscovered()
        fun onPuckActiveChanged(slot: Int)
        fun onPuckListRetrieved(slotData: ArrayList<ByteArray?>, active: Int)
        fun onPuckFilesDownload(tagData: ByteArray)
        fun onPuckProcessFinish()
        fun onGattConnectionLost()
    }

    private var puckArray = ArrayList<ByteArray?>()
    private var readResponse = ByteArray(NfcByte.TAG_FILE_SIZE)
    private fun getCharacteristicValue(characteristic: BluetoothGattCharacteristic, data: ByteArray?) {
        if (data != null && data.isNotEmpty()) {
            Debug.verbose(
                this.javaClass, getLogTag(characteristic.uuid) + " " + Arrays.toString(data)
            )
            if (characteristic.uuid.compareTo(PuckRX) == 0) {
                if (data[0] == PUCK.INFO.bytes) {
                    if (data.size == 3) {
                        activeSlot = data[1].toInt()
                        slotsCount = data[2].toInt()
                        getDeviceSlots(slotsCount)
                    } else {
                        if (data.size > 2) {
                            puckArray.add(Arrays.copyOfRange(data, 2, data.size))
                        } else {
                            puckArray.add(null)
                        }
                        if (puckArray.size == slotsCount) {
                            listener?.onPuckListRetrieved(
                                puckArray,
                                activeSlot
                            )
                        }
                    }
                } else if (data[0] == PUCK.READ.bytes) {
                    if (data[2].toInt() == 0) {
                        System.arraycopy(data, 4, readResponse, 0, data.size)
                    } else if (data[2] in 63..125) {
                        System.arraycopy(data, 4, readResponse, 252, data.size)
                    } else {
                        System.arraycopy(data, 4, readResponse, 504, data.size)
                        listener?.onPuckFilesDownload(readResponse)
                        readResponse = ByteArray(NfcByte.TAG_FILE_SIZE)
                    }
                } else if (data[0] == PUCK.SAVE.bytes) {
                    listener?.onPuckProcessFinish()
                    deviceAmiibo
                }
                if (commandCallbacks.size > 0) {
                    commandCallbacks[0].run()
                    commandCallbacks.removeAt(0)
                }
            }
        }
    }

    fun getCharacteristicValue(characteristic: BluetoothGattCharacteristic) {
        @Suppress("DEPRECATION")
        getCharacteristicValue(characteristic, characteristic.value)
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mBluetoothGatt!!.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                listener?.onGattConnectionLost()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) mBluetoothGatt!!.requestMtu(512) // Maximum: 517
                else listener?.onServicesDiscovered()
            } else {
                Debug.warn(this.javaClass, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS)
                getCharacteristicValue(characteristic, value)
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
            getCharacteristicValue(characteristic, value)
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
                maxTransmissionUnit = mtu
            } else {
                Debug.warn(this.javaClass, "onMtuChange received: $status")
            }
            listener?.onServicesDiscovered()
        }
    }

    inner class LocalBinder : Binder() {
        val service: PuckGattService
            get() = this@PuckGattService
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
        mBluetoothGatt?.close()
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
        mBluetoothAdapter = mBluetoothManager!!.adapter
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
        if (address == mBluetoothDeviceAddress && mBluetoothGatt != null) {
            return mBluetoothGatt!!.connect()
        }
        val device = mBluetoothAdapter!!.getRemoteDevice(address) ?: return false
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
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return
        }
        mBluetoothGatt!!.disconnect()
    }

    private fun setResponseDescriptors(characteristic: BluetoothGattCharacteristic?) {
        try {
            val descriptorTX = characteristic!!.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )
            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Debug.isNewer(Build.VERSION_CODES.TIRAMISU)) {
                mBluetoothGatt!!.writeDescriptor(descriptorTX, value)
            } else @Suppress("DEPRECATION") {
                descriptorTX.value = value
                mBluetoothGatt!!.writeDescriptor(descriptorTX)
            }
        } catch (ignored: Exception) {
        }
        try {
            val descriptorTX = characteristic!!.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )
            val value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            if (Debug.isNewer(Build.VERSION_CODES.TIRAMISU)) {
                mBluetoothGatt!!.writeDescriptor(descriptorTX, value)
            } else @Suppress("DEPRECATION") {
                descriptorTX.value = value
                mBluetoothGatt!!.writeDescriptor(descriptorTX)
            }
        } catch (ignored: Exception) {
        }
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
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return
        }
        mBluetoothGatt!!.setCharacteristicNotification(characteristic, enabled)
        setResponseDescriptors(characteristic)
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after `BluetoothGatt#discoverServices()` completes successfully.
     *
     * @return A `List` of supported services.
     */
    private val supportedGattServices: List<BluetoothGattService>?
        get() = if (mBluetoothGatt == null) null else mBluetoothGatt!!.services

    private fun getCharacteristicRX(mCustomService: BluetoothGattService): BluetoothGattCharacteristic {
        var mReadCharacteristic = mCustomService.getCharacteristic(PuckRX)
        if (!mBluetoothGatt!!.readCharacteristic(mReadCharacteristic)) {
            for (customRead in mCustomService.characteristics) {
                val customUUID = customRead.uuid
                /*get the read characteristic from the service*/if (customUUID.compareTo(
                        PuckRX
                    ) == 0
                ) {
                    Debug.verbose(this.javaClass, "GattReadCharacteristic: $customUUID")
                    mReadCharacteristic = mCustomService.getCharacteristic(customUUID)
                    break
                }
            }
        }
        return mReadCharacteristic
    }

    @Throws(UnsupportedOperationException::class)
    fun setPuckCharacteristicRX() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw UnsupportedOperationException()
        }
        val mCustomService = mBluetoothGatt!!.getService(PuckNUS)
        /*check if the service is available on the device*/if (null == mCustomService) {
            val services = supportedGattServices
            if (null == services || services.isEmpty()) {
                throw UnsupportedOperationException()
            }
            for (customService in services) {
                Debug.verbose(this.javaClass, "GattReadService: " + customService.uuid.toString())
                /*get the read characteristic from the service*/mCharacteristicRX =
                    getCharacteristicRX(customService)
                break
            }
        } else {
            mCharacteristicRX = getCharacteristicRX(mCustomService)
        }
        setCharacteristicNotification(mCharacteristicRX, true)
    }

    private fun getCharacteristicTX(mCustomService: BluetoothGattService): BluetoothGattCharacteristic {
        var mWriteCharacteristic = mCustomService.getCharacteristic(PuckTX)
        // if (!mBluetoothGatt!!.writeCharacteristic(mWriteCharacteristic)) {
            for (customWrite in mCustomService.characteristics) {
                val customUUID = customWrite.uuid
                /*get the write characteristic from the service*/
                if (customUUID.compareTo(PuckTX) == 0) {
                    Debug.verbose(this.javaClass, "GattWriteCharacteristic: $customUUID")
                    mWriteCharacteristic = mCustomService.getCharacteristic(customUUID)
                    break
                }
            }
        // }
        return mWriteCharacteristic
    }

    @Throws(UnsupportedOperationException::class)
    fun setPuckCharacteristicTX() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw UnsupportedOperationException()
        }
        val mCustomService = mBluetoothGatt!!.getService(PuckNUS)
        /*check if the service is available on the device*/if (null == mCustomService) {
            val services = supportedGattServices
            if (null == services || services.isEmpty()) {
                throw UnsupportedOperationException()
            }
            for (customService in services) {
                Debug.verbose(this.javaClass, "GattWriteService: " + customService.uuid.toString())
                /*get the read characteristic from the service*/mCharacteristicTX =
                    getCharacteristicTX(customService)
            }
        } else {
            mCharacteristicTX = getCharacteristicTX(mCustomService)
        }
        setCharacteristicNotification(mCharacteristicTX, true)
    }

    private fun delayedWriteCharacteristic(value: ByteArray) {
        val chunks = byteToPortions(
            value, maxTransmissionUnit - 3
        )
        val commandQueue = commandCallbacks.size + 1 + chunks.size
        puckHandler.postDelayed({
            var i = 0
            while (i < chunks.size) {
                val chunk = chunks[i]
                puckHandler.postDelayed({
                    if (Debug.isNewer(Build.VERSION_CODES.TIRAMISU)) {
                        mBluetoothGatt!!.writeCharacteristic(
                            mCharacteristicTX!!, chunk,
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        )
                    } else @Suppress("DEPRECATION") {
                        mCharacteristicTX!!.value = chunk
                        mCharacteristicTX!!.writeType =
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        mBluetoothGatt!!.writeCharacteristic(mCharacteristicTX)
                    }
                }, (i + 1) * chunkTimeout)
                i += 1
            }
        }, commandQueue * chunkTimeout)
    }

    private fun queueByteCharacteristic(value: ByteArray, index: Int) {
        if (null == mCharacteristicTX) {
            try {
                setPuckCharacteristicTX()
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

    private fun sendCommand(params: ByteArray, data: ByteArray?) {
        if (null != data) {
            val command = ByteArray(params.size + data.size)
            System.arraycopy(params, 0, command, 0, params.size)
            System.arraycopy(data, 0, command, params.size, data.size)
            delayedByteCharacteric(command)
        } else {
            delayedByteCharacteric(params)
        }
    }

    // sendCommand(new byte[] { PUCK.INFO.bytes }, null);
    val deviceAmiibo: Unit
        get() {
            puckArray = ArrayList()
            // sendCommand(new byte[] { PUCK.INFO.bytes }, null);
            getDeviceSlots(slotsCount)
        }

    private fun getSlotSummary(slot: Int) {
        sendCommand(byteArrayOf(PUCK.INFO.bytes, slot.toByte()), null)
    }

    private fun getDeviceSlots(total: Int) {
        for (i in 0 until total) {
            getSlotSummary(i)
        }
    }

    fun uploadSlotAmiibo(tagData: ByteArray, slot: Int) {
        for (i in 0 until tagData.size % 16) {
            sendCommand(byteArrayOf(
                PUCK.WRITE.bytes, slot.toByte(), (i * 4).toByte()
            ), tagData.copyOfRange(i * 16, 16))
        }
        sendCommand(
            byteArrayOf(PUCK.SAVE.bytes, slot.toByte()),
            if (activeSlot == slot) byteArrayOf(PUCK.NFC.bytes) else null
        )
    }

    @Suppress("UNUSED")
    fun downloadSlotData(slot: Int) {
        sendCommand(byteArrayOf(PUCK.READ.bytes, slot.toByte(), 0x00, 0x3F), null)
        sendCommand(byteArrayOf(PUCK.READ.bytes, slot.toByte(), 0x3F, 0x3F), null)
        sendCommand(byteArrayOf(PUCK.READ.bytes, slot.toByte(), 0x7E, 0x11), null)
    }

    fun setActiveSlot(slot: Int) {
        sendCommand(byteArrayOf(PUCK.NFC.bytes, slot.toByte()), null)
        activeSlot = slot
        listener?.onPuckActiveChanged(activeSlot)
    }

    private fun getLogTag(uuid: UUID): String {
        return if (uuid.compareTo(PuckTX) == 0) {
            "PuckTX"
        } else if (uuid.compareTo(PuckRX) == 0) {
            "PuckRX"
        } else if (uuid.compareTo(PuckNUS) == 0) {
            "PuckNUS"
        } else {
            uuid.toString()
        }
    }

    companion object {
        val PuckNUS: UUID = UUID.fromString("78290001-d52e-473f-a9f4-f03da7c67dd1")
        private val PuckTX = UUID.fromString("78290002-d52e-473f-a9f4-f03da7c67dd1")
        private val PuckRX = UUID.fromString("78290003-d52e-473f-a9f4-f03da7c67dd1")
    }
}