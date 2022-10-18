package com.mumumusuc.libjoycon

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.util.Log
import java.lang.reflect.Method

/*
 * frameworks/base/core/java/android/bluetooth/BluetoothHidHost.java
 */

@Suppress("UNCHECKED_CAST")
class BluetoothHidHost(private val mProfile: BluetoothProfile) {
    private val TAG = "MyBluetoothHidHost"
    private val DBG = true
    private val VDBG = false

    companion object {
        const val ACTION_PROTOCOL_MODE_CHANGED = "android.bluetooth.input.profile.action.PROTOCOL_MODE_CHANGED"
        const val ACTION_HANDSHAKE = "android.bluetooth.input.profile.action.HANDSHAKE"
        const val ACTION_REPORT = "android.bluetooth.input.profile.action.REPORT"
        const val ACTION_VIRTUAL_UNPLUG_STATUS = "android.bluetooth.input.profile.action.VIRTUAL_UNPLUG_STATUS"
        const val ACTION_IDLE_TIME_CHANGED = "android.bluetooth.input.profile.action.IDLE_TIME_CHANGED"
        const val INPUT_DISCONNECT_FAILED_NOT_CONNECTED = 5000
        const val INPUT_CONNECT_FAILED_ALREADY_CONNECTED = 5001
        const val INPUT_CONNECT_FAILED_ATTEMPT_FAILED = 5002
        const val INPUT_OPERATION_GENERIC_FAILURE = 5003
        const val INPUT_OPERATION_SUCCESS = 5004
        const val PROTOCOL_REPORT_MODE = 0
        const val PROTOCOL_BOOT_MODE = 1
        const val PROTOCOL_UNSUPPORTED_MODE = 255
        const val REPORT_TYPE_INPUT: Byte = 1
        const val REPORT_TYPE_OUTPUT: Byte = 2
        const val REPORT_TYPE_FEATURE: Byte = 3
        const val VIRTUAL_UNPLUG_STATUS_SUCCESS = 0
        const val VIRTUAL_UNPLUG_STATUS_FAIL = 1
        const val EXTRA_PROTOCOL_MODE = "android.bluetooth.BluetoothHidHost.extra.PROTOCOL_MODE"
        const val EXTRA_REPORT_TYPE = "android.bluetooth.BluetoothHidHost.extra.REPORT_TYPE"
        const val EXTRA_REPORT_ID = "android.bluetooth.BluetoothHidHost.extra.REPORT_ID"
        const val EXTRA_REPORT_BUFFER_SIZE = "android.bluetooth.BluetoothHidHost.extra.REPORT_BUFFER_SIZE"
        const val EXTRA_REPORT = "android.bluetooth.BluetoothHidHost.extra.REPORT"
        const val EXTRA_STATUS = "android.bluetooth.BluetoothHidHost.extra.STATUS"
        const val EXTRA_VIRTUAL_UNPLUG_STATUS = "android.bluetooth.BluetoothHidHost.extra.VIRTUAL_UNPLUG_STATUS"
        const val EXTRA_IDLE_TIME = "android.bluetooth.BluetoothHidHost.extra.IDLE_TIME"
        private val mMethods = HashMap<String, Method>()
    }

    init {
        synchronized(mMethods) {
            if (mMethods.isEmpty()) {
                try {
                    val cls = mProfile::class.java
                    mMethods["connect"] = cls.getDeclaredMethod("connect", BluetoothDevice::class.java)
                    mMethods["disconnect"] = cls.getDeclaredMethod("disconnect", BluetoothDevice::class.java)
                    mMethods["getConnectedDevices"] = cls.getDeclaredMethod("getConnectedDevices")
                    mMethods["getDevicesMatchingConnectionStates"] =
                        cls.getDeclaredMethod("getDevicesMatchingConnectionStates", IntArray::class.java)
                    mMethods["getConnectionState"] =
                        cls.getDeclaredMethod("getConnectionState", BluetoothDevice::class.java)
                    mMethods["setPriority"] =
                        cls.getDeclaredMethod("setPriority", BluetoothDevice::class.java, Int::class.java)
                    mMethods["getPriority"] = cls.getDeclaredMethod("getPriority", BluetoothDevice::class.java)
                    mMethods["virtualUnplug"] = cls.getDeclaredMethod("virtualUnplug", BluetoothDevice::class.java)
                    mMethods["getProtocolMode"] = cls.getDeclaredMethod("getProtocolMode", BluetoothDevice::class.java)
                    mMethods["setProtocolMode"] =
                        cls.getDeclaredMethod("setProtocolMode", BluetoothDevice::class.java, Int::class.java)
                    mMethods["getReport"] = cls.getDeclaredMethod(
                        "getReport",
                        BluetoothDevice::class.java,
                        Byte::class.java,
                        Byte::class.java,
                        Int::class.java
                    )
                    mMethods["setReport"] =
                        cls.getDeclaredMethod(
                            "setReport",
                            BluetoothDevice::class.java,
                            Byte::class.java,
                            String::class.java
                        )
                    mMethods["sendData"] =
                        cls.getDeclaredMethod("sendData", BluetoothDevice::class.java, String::class.java)
                    mMethods["getIdleTime"] = cls.getDeclaredMethod("getIdleTime", BluetoothDevice::class.java)
                    mMethods["setIdleTime"] =
                        cls.getDeclaredMethod("setIdleTime", BluetoothDevice::class.java, Byte::class.java)
                } catch (e: NoSuchMethodException) {
                    Log.e(TAG, Log.getStackTraceString(e))
                }
            }
        }
    }

    fun asBluetoothProfile(): BluetoothProfile {
        return mProfile
    }

    fun connect(device: BluetoothDevice): Boolean {
        return mMethods["connect"]!!.invoke(mProfile, device) as Boolean
    }

    fun disconnect(device: BluetoothDevice): Boolean {
        return mMethods["disconnect"]!!.invoke(mProfile, device) as Boolean
    }

    fun getConnectedDevices(): List<BluetoothDevice> {
        return mMethods["getConnectedDevices"]!!.invoke(mProfile) as List<BluetoothDevice>
    }

    fun getDevicesMatchingConnectionStates(states: IntArray): List<BluetoothDevice> {
        return mMethods["getDevicesMatchingConnectionStates"]!!.invoke(mProfile, states) as List<BluetoothDevice>
    }

    fun getConnectionState(device: BluetoothDevice): Int {
        return mMethods["getConnectionState"]!!.invoke(mProfile, device) as Int
    }

    fun setPriority(device: BluetoothDevice, priority: Int): Boolean {
        return mMethods["setPriority"]!!.invoke(mProfile, device, priority) as Boolean
    }

    fun getPriority(device: BluetoothDevice): Int {
        return mMethods["getPriority"]!!.invoke(mProfile, device) as Int
    }

    fun virtualUnplug(device: BluetoothDevice): Boolean {
        return mMethods["virtualUnplug"]!!.invoke(mProfile, device) as Boolean
    }

    fun getProtocolMode(device: BluetoothDevice): Boolean {
        return mMethods["getProtocolMode"]!!.invoke(mProfile, device) as Boolean
    }

    fun setProtocolMode(device: BluetoothDevice, protocolMode: Int): Boolean {
        return mMethods["setProtocolMode"]!!.invoke(mProfile, device, protocolMode) as Boolean
    }

    fun getReport(device: BluetoothDevice, reportType: Byte, reportId: Byte, bufferSize: Int): Boolean {
        return mMethods["getReport"]!!.invoke(mProfile, device, reportType, reportId, bufferSize) as Boolean
    }

    fun setReport(device: BluetoothDevice, reportType: Byte, report: String): Boolean {
        return mMethods["setReport"]!!.invoke(mProfile, device, reportType, report) as Boolean
    }

    fun sendData(device: BluetoothDevice, report: String): Boolean {
        return mMethods["sendData"]!!.invoke(mProfile, device, report) as Boolean
    }

    fun getIdleTime(device: BluetoothDevice): Boolean {
        return mMethods["getIdleTime"]!!.invoke(mProfile, device) as Boolean
    }

    fun setIdleTime(device: BluetoothDevice, idleTime: Byte): Boolean {
        return mMethods["setIdleTime"]!!.invoke(mProfile, device, idleTime) as Boolean
    }

}