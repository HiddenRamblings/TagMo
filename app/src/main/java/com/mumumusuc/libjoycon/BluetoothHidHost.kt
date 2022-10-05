package com.mumumusuc.libjoycon

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.util.Log
import java.lang.reflect.Method

/*
    http://androidxref.com/9.0.0_r3/xref/frameworks/base/core/java/android/bluetooth/BluetoothHidHost.java
*/

class BluetoothHidHost(private val mProfile: BluetoothProfile) {
    private val TAG = "MyBluetoothHidHost"
    private val DBG = true
    private val VDBG = false

    companion object {
        val ACTION_PROTOCOL_MODE_CHANGED = "android.bluetooth.input.profile.action.PROTOCOL_MODE_CHANGED"
        val ACTION_HANDSHAKE = "android.bluetooth.input.profile.action.HANDSHAKE"
        val ACTION_REPORT = "android.bluetooth.input.profile.action.REPORT"
        val ACTION_VIRTUAL_UNPLUG_STATUS = "android.bluetooth.input.profile.action.VIRTUAL_UNPLUG_STATUS"
        val ACTION_IDLE_TIME_CHANGED = "android.bluetooth.input.profile.action.IDLE_TIME_CHANGED"
        val INPUT_DISCONNECT_FAILED_NOT_CONNECTED = 5000
        val INPUT_CONNECT_FAILED_ALREADY_CONNECTED = 5001
        val INPUT_CONNECT_FAILED_ATTEMPT_FAILED = 5002
        val INPUT_OPERATION_GENERIC_FAILURE = 5003
        val INPUT_OPERATION_SUCCESS = 5004
        val PROTOCOL_REPORT_MODE = 0
        val PROTOCOL_BOOT_MODE = 1
        val PROTOCOL_UNSUPPORTED_MODE = 255
        val REPORT_TYPE_INPUT: Byte = 1
        val REPORT_TYPE_OUTPUT: Byte = 2
        val REPORT_TYPE_FEATURE: Byte = 3
        val VIRTUAL_UNPLUG_STATUS_SUCCESS = 0
        val VIRTUAL_UNPLUG_STATUS_FAIL = 1
        val EXTRA_PROTOCOL_MODE = "android.bluetooth.BluetoothHidHost.extra.PROTOCOL_MODE"
        val EXTRA_REPORT_TYPE = "android.bluetooth.BluetoothHidHost.extra.REPORT_TYPE"
        val EXTRA_REPORT_ID = "android.bluetooth.BluetoothHidHost.extra.REPORT_ID"
        val EXTRA_REPORT_BUFFER_SIZE = "android.bluetooth.BluetoothHidHost.extra.REPORT_BUFFER_SIZE"
        val EXTRA_REPORT = "android.bluetooth.BluetoothHidHost.extra.REPORT"
        val EXTRA_STATUS = "android.bluetooth.BluetoothHidHost.extra.STATUS"
        val EXTRA_VIRTUAL_UNPLUG_STATUS = "android.bluetooth.BluetoothHidHost.extra.VIRTUAL_UNPLUG_STATUS"
        val EXTRA_IDLE_TIME = "android.bluetooth.BluetoothHidHost.extra.IDLE_TIME"
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