/*
 * ====================================================================
 * Copyright (c) 2021-2023 AbandonedCart.  All rights reserved.
 *
 * https://github.com/AbandonedCart/AbandonedCart/blob/main/LICENSE#L4
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */

package com.hiddenramblings.tagmo.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hiddenramblings.tagmo.BuildConfig
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Version

class BluetoothHandler(
    context: Context, registry: ActivityResultRegistry, listener: BluetoothListener
) {
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private val listener: BluetoothListener
    private var onRequestLocationQ: ActivityResultLauncher<Array<String>>
    private var onRequestBackgroundQ: ActivityResultLauncher<String>
    private var onRequestBluetoothS: ActivityResultLauncher<Array<String>>
    private var onRequestBluetooth: ActivityResultLauncher<Intent>
    private var onRequestLocation: ActivityResultLauncher<Array<String>>
    private var onRequestAdapter: ActivityResultLauncher<Intent>

    init {
        onRequestLocationQ = registry.register(
            "LocationQ", ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions: Map<String, Boolean> ->
            var isLocationAvailable = false
            permissions.entries.forEach {
                if (it.key == Manifest.permission.ACCESS_FINE_LOCATION)
                    isLocationAvailable = it.value
            }
            if (isLocationAvailable) requestBluetooth(context) else listener.onPermissionsFailed()
        }
        onRequestBackgroundQ = registry.register(
            "BackgroundQ", ActivityResultContracts.RequestPermission()
        ) { }
        onRequestBluetoothS = registry.register(
            "BluetoothS", ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions: Map<String, Boolean> ->
            var isBluetoothAvailable = true
            permissions.entries.forEach {
                if (!it.value) isBluetoothAvailable = false
            }
            if (isBluetoothAvailable) {
                val mBluetoothAdapter = getBluetoothAdapter(context)
                mBluetoothAdapter?.let { listener.onAdapterEnabled(it) } ?: listener.onAdapterMissing()
            } else {
                listener.onAdapterMissing()
            }
        }
        onRequestBluetooth = registry.register(
            "Bluetooth", ActivityResultContracts.StartActivityForResult()
        ) {
            val mBluetoothAdapter = getBluetoothAdapter(context)
            mBluetoothAdapter?.let { listener.onAdapterEnabled(it) } ?: listener.onAdapterMissing()
        }
        onRequestLocation = registry.register(
            "Location", ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions: Map<String, Boolean> ->
            var isLocationAvailable = false
            permissions.entries.forEach {
                if (it.key == Manifest.permission.ACCESS_FINE_LOCATION)
                    isLocationAvailable = it.value
            }
            if (isLocationAvailable) {
                val mBluetoothAdapter = getBluetoothAdapter(context)
                mBluetoothAdapter?.let { listener.onAdapterEnabled(it) }
                    ?: onRequestBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                listener.onPermissionsFailed()
            }
        }
        onRequestAdapter = registry.register(
            "Adapter", ActivityResultContracts.StartActivityForResult()
        ) { listener.onAdapterRestricted() }
        this.listener = listener
    }

    private fun requestBluetooth(context: Context) {
        if (Version.isSnowCone) {
            onRequestBluetoothS.launch(PERMISSIONS_BLUETOOTH)
        } else {
            val mBluetoothAdapter = getBluetoothAdapter(context)
            mBluetoothAdapter?.let { listener.onAdapterEnabled(it) }
                ?: onRequestBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    fun requestPermissions(activity: Activity) {
        if (Version.isLowerThan(Build.VERSION_CODES.M)) {
            val mBluetoothAdapter = getBluetoothAdapter(activity)
            mBluetoothAdapter?.let { listener.onAdapterEnabled(it) }
                ?: onRequestBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                AlertDialog.Builder(activity)
                    .setMessage(R.string.location_disclosure)
                    .setCancelable(false)
                    .setPositiveButton(R.string.accept) { dialog: DialogInterface, _: Int ->
                        dialog.dismiss()
                        if (Version.isAndroid10) {
                            onRequestLocationQ.launch(PERMISSIONS_LOCATION)
                        } else {
                            onRequestLocation.launch(PERMISSIONS_LOCATION)
                        }
                    }
                    .setNegativeButton(R.string.deny) { _: DialogInterface?, _: Int ->
                        listener.onPermissionsFailed() }
                    .show()
            } else if (ContextCompat.checkSelfPermission(
                    activity, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                requestBluetooth(activity)
            } else {
                if (BuildConfig.GOOGLE_PLAY) {
                    AlertDialog.Builder(activity)
                        .setMessage(R.string.location_disclosure)
                        .setCancelable(false)
                        .setPositiveButton(R.string.accept) { dialog: DialogInterface, _: Int ->
                            dialog.dismiss()
                            if (Version.isAndroid10)
                                onRequestLocationQ.launch(PERMISSIONS_LOCATION)
                            else onRequestLocation.launch(PERMISSIONS_LOCATION)
                        }
                        .setNegativeButton(R.string.deny) { _: DialogInterface?, _: Int ->
                            listener.onPermissionsFailed() }
                        .show()
                } else {
                    if (Version.isAndroid10)
                        onRequestLocationQ.launch(PERMISSIONS_LOCATION)
                    else onRequestLocation.launch(PERMISSIONS_LOCATION)
                }
            }
        }
    }

    private fun enableBluetoothAdapter(
        context: Context, adapter: BluetoothAdapter
    ) : BluetoothAdapter {
        return adapter.also {
            if (it.isEnabled) return it
            if (Version.isTiramisu) {
                AlertDialog.Builder(context)
                    .setMessage(R.string.tiramisu_bluetooth)
                    .setCancelable(false)
                    .setPositiveButton(R.string.proceed) { dialog: DialogInterface, _: Int ->
                        listener.onAdapterMissing()
                        onRequestAdapter.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel) { _: DialogInterface?, _: Int ->
                        listener.onAdapterMissing()
                    }
                    .show()
            } else {
                try {
                    @Suppress("DEPRECATION")
                    it.enable()
                } catch (se: SecurityException) { listener.onPermissionsFailed() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getBluetoothAdapter(context: Context?): BluetoothAdapter? {
        if (null == context) return null
        return try {
            mBluetoothAdapter?.let {enableBluetoothAdapter(context, it) }
                ?: enableBluetoothAdapter(context, if (Version.isJellyBeanMR2) {
                    (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                } else {
                    @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
                })
        } catch (ex: SecurityException) {
            Debug.warn(ex)
            null
        }
    }

    fun unregisterResultContracts() {
        onRequestLocationQ.unregister()
        onRequestBackgroundQ.unregister()
        onRequestBluetoothS.unregister()
        onRequestBluetooth.unregister()
        onRequestLocation.unregister()
        onRequestAdapter.unregister()
    }

    companion object {
        private val PERMISSIONS_LOCATION = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        @RequiresApi(Build.VERSION_CODES.S)
        private val PERMISSIONS_BLUETOOTH = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }

    interface BluetoothListener {
        fun onPermissionsFailed()
        fun onAdapterMissing()
        fun onAdapterRestricted()
        fun onAdapterEnabled(adapter: BluetoothAdapter?)
    }
}