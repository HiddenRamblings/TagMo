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
import com.hiddenramblings.tagmo.nfctech.TagArray
import org.json.JSONObject
import java.util.*

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("MissingPermission")
class PixlGattService : Service() {
    private var listener: PixlBluetoothListener? = null
    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothDeviceAddress: String? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mCharacteristicRX: BluetoothGattCharacteristic? = null
    private var mCharacteristicTX: BluetoothGattCharacteristic? = null
    private var maxTransmissionUnit = 23
    private val chunkTimeout = 25L
    fun setListener(listener: PixlBluetoothListener?) {
        this.listener = listener
    }

    private val commandCallbacks = ArrayList<Runnable>()
    private val pixlHandler = Handler(Looper.getMainLooper())

    var serviceType = Nordic.DEVICE.PIXL

    interface PixlBluetoothListener {
        fun onPixlServicesDiscovered()
        fun onPixlActiveChanged(jsonObject: JSONObject?)
        fun onPixlStatusChanged(jsonObject: JSONObject?)
        fun onPixlDataReceived(result: String?)
        fun onPixlFilesDownload(dataString: String)
        fun onPixlProcessFinish()
        fun onPixlConnectionLost()
    }

    private var response = StringBuilder()
    private var rangeIndex = 0

    private fun getCharacteristicValue(characteristic: BluetoothGattCharacteristic, data: ByteArray?) {
        if (data?.isNotEmpty() == true) {
            Debug.info(
                    this.javaClass, "${Nordic.getLogTag("Pixl",
                    characteristic.uuid)} ${TagArray.bytesToHex(data)}"
            )
            if (characteristic.uuid.compareTo(Nordic.RX) == 0) {
                listener?.onPixlDataReceived(Arrays.toString(data))
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
                listener?.onPixlConnectionLost()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (Version.isLollipop)
                    gatt.requestMtu(512) // Maximum: 517
                else listener?.onPixlServicesDiscovered()
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
            Debug.info(
                this.javaClass, Nordic.getLogTag("Puck",
                    characteristic.uuid) + " onCharacteristicWrite " + status
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
            listener?.onPixlServicesDiscovered()
        }
    }

    inner class LocalBinder : Binder() {
        val service: PixlGattService
            get() = this@PixlGattService
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
        var mReadCharacteristic = mCustomService.getCharacteristic(Nordic.RX)
        if (mBluetoothGatt?.readCharacteristic(mReadCharacteristic) != true) run breaking@{
            mCustomService.characteristics.forEach {
                val customUUID = it.uuid
                /*get the read characteristic from the service*/
                if (customUUID.compareTo(Nordic.RX) == 0) {
                    Debug.verbose(this.javaClass, "GattReadCharacteristic: $customUUID")
                    mReadCharacteristic = mCustomService.getCharacteristic(customUUID)
                    return@breaking
                }
            }
        }
        return mReadCharacteristic
    }

    @Throws(UnsupportedOperationException::class)
    fun setPixlCharacteristicRX() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw UnsupportedOperationException()
        }
        val mCustomService = mBluetoothGatt!!.getService(Nordic.NUS)
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
        var mWriteCharacteristic = mCustomService.getCharacteristic(Nordic.TX)
        if (!mCustomService.characteristics.contains(mWriteCharacteristic)) {
            for (characteristic in mCustomService.characteristics) {
                val customUUID = characteristic.uuid
                if (customUUID.compareTo(Nordic.TX) == 0) {
                    Debug.verbose(this.javaClass, "GattWriteCharacteristic: $customUUID")
                    mWriteCharacteristic = mCustomService.getCharacteristic(customUUID)
                    break
                }

            }
        }
        return mWriteCharacteristic
    }

    @Throws(UnsupportedOperationException::class)
    fun setPixlCharacteristicTX() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw UnsupportedOperationException()
        }
        val mCustomService = mBluetoothGatt!!.getService(Nordic.NUS)
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
        pixlHandler.postDelayed({
            var i = 0
            while (i < chunks.size) {
                val chunk = chunks[i]
                if (null == mCharacteristicTX) continue
                pixlHandler.postDelayed({
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
                setPixlCharacteristicTX()
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

    private fun queueByteCharacteristic(value: ByteArray, index: Int) {
        if (null == mCharacteristicTX) {
            try {
                setPixlCharacteristicTX()
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
        queueByteCharacteristic(value)
    }

    val deviceAmiibo: Unit
        get() {
            when (serviceType) {
                Nordic.DEVICE.LOOP -> {
                    delayedByteCharacteric(byteArrayOf(
                            0x02, 0x01, 0x89.toByte(), 0x88.toByte(), 0x03
                    ))
                }
                Nordic.DEVICE.LINK -> {
                    delayedByteCharacteric(byteArrayOf(
                            0x00, 0x00, 0x10, 0x02,
                            0x33, 0x53, 0x34, 0xAB.toByte(),
                            0x1F, 0xE8.toByte(), 0xC2.toByte(), 0x6D,
                            0xE5.toByte(), 0x35, 0x27, 0x4B,
                            0x52, 0xE0.toByte(), 0x1F, 0x26
                    ))
                }
                else ->{

                }
            }
        }

    val activeAmiibo: Unit
        get() {

        }

    private fun xorByteArray(byteArray: ByteArray): Byte {
        if (byteArray.isEmpty()) {
            throw IllegalArgumentException("Empty collection can't be reduced.")
        }
        var result = byteArray[0]
        for (i in 1 until byteArray.size) {
            result = (result.toInt() xor byteArray[i].toInt()).toByte()
        }
        return (result.toInt() and 0xff).toByte()
    }
    private fun processLoopUpload(input: ByteArray): List<ByteArray> {
        val output = mutableListOf<ByteArray>()
        var start = 0
        while (start < input.size) {
            val chunkSize = 128.coerceAtMost(input.size - start)
            val chunk = input.sliceArray(start until start + chunkSize)
            val newData = ByteArray(5 + chunk.size + 2)
            newData[0] = 0x02.toByte()
            newData[1] = (chunk.size + 3).toByte()
            newData[2] = 0x87.toByte()
            newData[3] = if (chunk.size < 128) 1 else 0
            newData[4] = output.size.toByte()
            chunk.copyInto(newData, 5)
            val xorValue = xorByteArray(newData.sliceArray(1 until newData.size - 2))
            newData[newData.size - 2] = xorValue
            newData[newData.size - 1] = 0x03.toByte()
            output.add(newData)
            start += chunkSize
        }
        return output
    }

    private fun processLinkUpload(inputArray: ByteArray): List<ByteArray> {
        val writeCommands = mutableListOf<ByteArray>()

        // Ensure the working array is exactly 540 bytes
        val workingArray = ByteArray(540)
        inputArray.copyInto(workingArray, startIndex = 0, endIndex = 540)

        // Add initial byte arrays to the output
        writeCommands.add(byteArrayOf(0xA0.toByte(), 0xB0.toByte()))
        writeCommands.add(byteArrayOf(
                0xAC.toByte(), 0xAC.toByte(), 0x00.toByte(), 0x04.toByte(),
                0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x1C.toByte())
        )
        writeCommands.add(byteArrayOf(0xAB.toByte(), 0xAB.toByte(), 0x02.toByte(), 0x1C.toByte()))

        // Loop through the input array and slice 20 bytes at a time
        for (i in workingArray.indices step 20) {
            val slice = workingArray.sliceArray(i until i + 20)
            val iteration = (i / 20) + 1

            // Create temporary ByteArray with required values
            val tempArray = byteArrayOf(
                    0xDD.toByte(), 0xAA.toByte(), 0x00.toByte(), 0x14.toByte(),
                    *slice,
                    0x00.toByte(),
                    iteration.toByte()
            )

            // Add temporary array to the output
            writeCommands.add(tempArray)
        }

        // Add final byte arrays to the output
        writeCommands.add(byteArrayOf(0xBC.toByte(), 0xBC.toByte()))
        writeCommands.add(byteArrayOf(0xCC.toByte(), 0xDD.toByte()))

        return writeCommands
    }

    fun uploadAmiiboData(tagData: ByteArray) {
        when (serviceType) {
            Nordic.DEVICE.LOOP -> {
                tagData[536] = 0x80.toByte()
                tagData[537] = 0x80.toByte()
                processLoopUpload(tagData).forEach {
                    commandCallbacks.add(Runnable {
                        delayedByteCharacteric(it)
                    })
                }
            }
            Nordic.DEVICE.LINK -> {
                processLinkUpload(tagData).forEach {
                    commandCallbacks.add(Runnable {
                        delayedByteCharacteric(it)
                    })
                }
            }
            else -> {

            }
        }
    }
}