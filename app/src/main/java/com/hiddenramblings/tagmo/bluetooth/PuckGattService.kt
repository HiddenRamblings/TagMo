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
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.nfctech.NfcByte
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.hiddenramblings.tagmo.nfctech.toFileBytes
import java.lang.Math.floor
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
    private var maxTransmissionUnit = 53
    private val chunkTimeout = 25L

    // Command, Slot, Parameters
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

    private var activeSlot = 0
    private var slotsCount = 32
    fun setListener(listener: BluetoothGattListener?) {
        this.listener = listener
    }
    private val commandCallbacks = ArrayList<Runnable>()
    private val puckHandler = Handler(Looper.getMainLooper())

    interface BluetoothGattListener {
        fun onPuckServicesDiscovered()
        fun onPuckActiveChanged(slot: Int)
        fun onPuckDeviceProfile(slotCount: Int)
        fun onPuckListRetrieved(slotData: ArrayList<ByteArray>, active: Int)
        fun onPuckFilesDownload(tagData: ByteArray)
        fun onPuckProcessFinish()
        fun onPuckConnectionLost()
    }

    private var puckArray = ArrayList<ByteArray>(slotsCount)
    private var readResponse = byteArrayOf()
    private var tempInfoData = byteArrayOf()

    private fun getCharacteristicValue(characteristic: BluetoothGattCharacteristic, data: ByteArray?) {
        if (data?.isNotEmpty() == true) {
            Debug.info(
                this.javaClass, "${Nordic.getLogTag("Puck",
                    characteristic.uuid)} ${TagArray.bytesToHex(data)}"
            )
            if (characteristic.uuid.compareTo(PuckRX) == 0) {
                when {
                    TagArray.bytesToString(data).endsWith("DTM_PUCK_FAST") -> {
                        sendCommand(byteArrayOf(PUCK.INFO.bytes), null)
                    }
                    tempInfoData.isNotEmpty() -> {
                        val sliceData = tempInfoData.plus(data)
                        puckArray.add(sliceData[1].toInt(), sliceData.copyOfRange(2, sliceData.size))
                        tempInfoData = byteArrayOf()
                        if (puckArray.size == slotsCount) {
                            listener?.onPuckListRetrieved(puckArray, activeSlot)
                        } else{
                            val nextSlot = sliceData[1].toInt() + 1
                            sendCommand(byteArrayOf(PUCK.INFO.bytes, nextSlot.toByte()), null)
                        }
                    }
                    data[0] == PUCK.INFO.bytes -> {
                        if (data.size == 3) {
                            activeSlot = data[1].toInt()
                            slotsCount = data[2].toInt()
                            listener?.onPuckDeviceProfile(slotsCount)
                        } else {
                            tempInfoData = data
                        }
                    }
                    data[0] == PUCK.READ.bytes -> {
                        if (data[2].toInt() + (data[3].toInt() * 4) >= 143) {
                            readResponse = readResponse.plus(data.copyOfRange(4, data.size))
                            listener?.onPuckFilesDownload(readResponse)
                            readResponse = byteArrayOf()
                        } else {
                            readResponse = readResponse.plus(data.copyOfRange(4, data.size))
                        }
                    }
                    data[0] == PUCK.WRITE.bytes -> {

                    }
                    data[0] == PUCK.SAVE.bytes -> {
                        sendCommand(byteArrayOf(PUCK.NFC.bytes), null)
                    }
                    data[0] == PUCK.NFC.bytes -> {
                        listener?.onPuckProcessFinish()
                        deviceAmiibo
                    }
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
                mBluetoothGatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                listener?.onPuckConnectionLost()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (Version.isLollipop)
                    gatt.requestMtu(512) // Maximum: 517
                else listener?.onPuckServicesDiscovered()
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
            Debug.info(this.javaClass,
                "${Nordic.getLogTag("Puck", characteristic.uuid)} onCharacteristicWrite $status"
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
                maxTransmissionUnit = mtu - 3
            } else {
                Debug.warn(this.javaClass, "onMtuChange received: $status")
            }
            listener?.onPuckServicesDiscovered()
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
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return
        }
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
        setPuckCharacteristicRX()
    }

    private fun getCharacteristicRX(mCustomService: BluetoothGattService): BluetoothGattCharacteristic {
        var mReadCharacteristic = mCustomService.getCharacteristic(PuckRX)
        if (mBluetoothGatt?.readCharacteristic(mReadCharacteristic) != true) {
            for (characteristic in mCustomService.characteristics) {
                val customUUID = characteristic.uuid
                /*get the read characteristic from the service*/
                if (customUUID.compareTo(PuckRX) == 0) {
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
        var mWriteCharacteristic = mCustomService.getCharacteristic(PuckTX)
        if (!mCustomService.characteristics.contains(mWriteCharacteristic)) {
            for (characteristic in mCustomService.characteristics) {
                val customUUID = characteristic.uuid
                if (customUUID.compareTo(PuckTX) == 0) {
                    Debug.verbose(this.javaClass, "GattWriteCharacteristic: $customUUID")
                    mWriteCharacteristic = mCustomService.getCharacteristic(customUUID)
                    break
                }
            }
        }
        return mWriteCharacteristic
    }

    @Throws(UnsupportedOperationException::class)
    fun setPuckCharacteristicTX() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw UnsupportedOperationException()
        }
        val mCustomService = mBluetoothGatt!!.getService(PuckNUS)
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
        puckHandler.postDelayed({
            var i = 0
            while (i < chunks.size) {
                val chunk = chunks[i]
                if (null == mCharacteristicTX) continue
                puckHandler.postDelayed({
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
        delayedByteCharacteric(data?.let { params.plus(data) } ?: params)
    }

    val deviceDetails: Unit
        get() {
            if (!legacyInterface) {
                sendCommand(byteArrayOf(
                        0x66, 0x61, 0x73, 0x74, 0x4D, 0x6F, 0x64, 0x65, 0x28, 0x29, 0x0A
                ), null)
            }
        }

    val deviceAmiibo: Unit
        get() {
            puckArray = ArrayList<ByteArray>(slotsCount)
            sendCommand(byteArrayOf(PUCK.INFO.bytes, 0.toByte()), null)
        }

    fun uploadSlotAmiibo(tagData: ByteArray, slot: Int) {
        val pages = TagArray.bytesToPages(tagData)
        TagArray.bytesToPages(tagData).forEachIndexed { index, bytes ->
            sendCommand(byteArrayOf(
                    PUCK.WRITE.bytes, slot.toByte(), (index * NfcByte.PAGE_SIZE).toByte(), 0x01
            ), bytes)
        }
        sendCommand(byteArrayOf(PUCK.SAVE.bytes, slot.toByte()), null)
    }

    @Suppress("unused")
    fun downloadSlotData(slot: Int) {
        for (i in 0..35) {
            sendCommand(byteArrayOf(PUCK.READ.bytes, slot.toByte(), (i * 4).toByte(), 0x04), null)
        }
        sendCommand(byteArrayOf(PUCK.READ.bytes, slot.toByte(), 0x8C.toByte(), 0x03), null)

    }

    fun setActiveSlot(slot: Int) {
        sendCommand(byteArrayOf(PUCK.NFC.bytes, slot.toByte()), null)
        activeSlot = slot
        listener?.onPuckActiveChanged(activeSlot)
    }

    companion object {
        private var legacyInterface = false
        val PuckNUS: UUID = if (legacyInterface) Nordic.LegacyNUS else Nordic.NUS
        val PuckTX = if (legacyInterface) Nordic.LegacyTX else Nordic.TX
        val PuckRX = if (legacyInterface) Nordic.LegacyRX else Nordic.RX
    }
}