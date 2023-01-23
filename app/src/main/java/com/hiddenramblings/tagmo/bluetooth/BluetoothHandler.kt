/*
 * ====================================================================
 * Copyright (c) 2021-2022 AbandonedCart.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * For the purpose of this license, the phrase "TagMo labels" shall
 * be used to refer to the labels "8-bit Dream", "TwistedUmbrella",
 * "TagMo" and "AbandonedCart" and these labels should be considered
 * the equivalent of any usage of the aforementioned phrase.
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All materials mentioning features or use of this software and
 *    redistributions of any form whatsoever must display the following
 *    acknowledgment unless made available by tagged, public "commits":
 *    "This product includes software developed for TagMo by AbandonedCart"
 *
 * 4. The TagMo labels must not be used in any form to endorse or promote
 *    products derived from this software without prior written permission.
 *    For written permission, please contact enderinexiledc@gmail.com
 *
 * 5. Products derived from this software may not be called by the TagMo labels
 *    nor may these labels appear in their names or product information without
 *    prior written permission of AbandonedCart.
 *
 * THIS SOFTWARE IS PROVIDED BY AbandonedCart AND TagMo ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
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
import com.hiddenramblings.tagmo.eightbit.io.Debug.isNewer

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

    init {
        onRequestLocationQ = registry.register(
            "LocationQ",
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions: Map<String, Boolean> ->
            var isLocationAvailable = false
            for ((key, value) in permissions) {
                if (key == Manifest.permission.ACCESS_FINE_LOCATION && value)
                    isLocationAvailable = true
            }
            if (isLocationAvailable) requestBluetooth(context) else listener.onPermissionsFailed()
        }
        onRequestBackgroundQ = registry.register(
            "BackgroundQ",
            ActivityResultContracts.RequestPermission()
        ) { }
        onRequestBluetoothS = registry.register(
            "BluetoothS",
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions: Map<String, Boolean> ->
            var isBluetoothAvailable = false
            for ((_, value) in permissions)
                if (value) isBluetoothAvailable = true
            if (isBluetoothAvailable) {
                val mBluetoothAdapter = getBluetoothAdapter(context)
                if (null != mBluetoothAdapter) {
//                    if (Debug.hasMinimum(Build.VERSION_CODES.Q)) {
//                        onRequestBackgroundQ.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
//                    }
                    listener.onAdapterEnabled(mBluetoothAdapter)
                } else {
                    listener.onAdapterMissing()
                }
            } else {
                listener.onAdapterMissing()
            }
        }
        onRequestBluetooth = registry.register(
            "Bluetooth",
            ActivityResultContracts.StartActivityForResult()
        ) {
            val mBluetoothAdapter = getBluetoothAdapter(context)
            if (null != mBluetoothAdapter) {
//                if (Debug.hasMinimum(Build.VERSION_CODES.Q)) {
//                    onRequestBackgroundQ.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
//                }
                listener.onAdapterEnabled(mBluetoothAdapter)
            } else {
                listener.onAdapterMissing()
            }
        }
        onRequestLocation = registry.register(
            "Location",
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions: Map<String, Boolean> ->
            var isLocationAvailable = false
            for ((_, value) in permissions)
                if (value) isLocationAvailable = true
            if (isLocationAvailable) {
                val mBluetoothAdapter = getBluetoothAdapter(context)
                if (null != mBluetoothAdapter)
                    listener.onAdapterEnabled(mBluetoothAdapter)
                else onRequestBluetooth.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                )
            } else {
                listener.onPermissionsFailed()
            }
        }
        this.listener = listener
    }

    private fun requestBluetooth(context: Context) {
        if (isNewer(Build.VERSION_CODES.S)) {
            onRequestBluetoothS.launch(PERMISSIONS_BLUETOOTH)
        } else {
            val mBluetoothAdapter = getBluetoothAdapter(context)
            if (null != mBluetoothAdapter) {
//                if (Debug.hasMinimum(Build.VERSION_CODES.Q)) {
//                    onRequestBackgroundQ.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
//                }
                listener.onAdapterEnabled(mBluetoothAdapter)
            } else {
                onRequestBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }
    }

    fun requestPermissions(context: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            val mBluetoothAdapter = getBluetoothAdapter(context)
            if (null != mBluetoothAdapter)
                listener.onAdapterEnabled(mBluetoothAdapter)
            else onRequestBluetooth.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            )
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                AlertDialog.Builder(context)
                    .setMessage(R.string.location_disclosure)
                    .setCancelable(false)
                    .setPositiveButton(R.string.accept) { dialog: DialogInterface, _: Int ->
                        dialog.dismiss()
                        if (isNewer(Build.VERSION_CODES.Q)) {
                            onRequestLocationQ.launch(PERMISSIONS_LOCATION)
                        } else {
                            onRequestLocation.launch(PERMISSIONS_LOCATION)
                        }
                    }
                    .setNegativeButton(R.string.deny) { _: DialogInterface?, _: Int ->
                        listener.onPermissionsFailed() }
                    .show()
            } else if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                requestBluetooth(context)
            } else {
                if (BuildConfig.GOOGLE_PLAY) {
                    AlertDialog.Builder(context)
                        .setMessage(R.string.location_disclosure)
                        .setCancelable(false)
                        .setPositiveButton(R.string.accept) { dialog: DialogInterface, _: Int ->
                            dialog.dismiss()
                            if (isNewer(Build.VERSION_CODES.Q))
                                onRequestLocationQ.launch(PERMISSIONS_LOCATION)
                            else onRequestLocation.launch(PERMISSIONS_LOCATION)
                        }
                        .setNegativeButton(R.string.deny) { _: DialogInterface?, _: Int ->
                            listener.onPermissionsFailed() }
                        .show()
                } else {
                    if (isNewer(Build.VERSION_CODES.Q))
                        onRequestLocationQ.launch(PERMISSIONS_LOCATION)
                    else onRequestLocation.launch(PERMISSIONS_LOCATION)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getBluetoothAdapter(context: Context?): BluetoothAdapter? {
        if (null == context) return null
        if (null != mBluetoothAdapter) {
            if (!mBluetoothAdapter!!.isEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    AlertDialog.Builder(context)
                        .setMessage(R.string.tiramisu_bluetooth)
                        .setCancelable(false)
                        .setPositiveButton(R.string.proceed) { dialog: DialogInterface, _: Int ->
                            context.startActivity(
                                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            )
                            dialog.dismiss()
                        }
                        .setNegativeButton(R.string.cancel) { _: DialogInterface?, _: Int ->
                            listener.onAdapterMissing() }
                        .show()
                } else {
                    @Suppress("DEPRECATION")
                    mBluetoothAdapter!!.enable()
                }
            }
            return mBluetoothAdapter
        } else {
            mBluetoothAdapter = if (isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2)) {
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            } else {
                @Suppress("DEPRECATION")
                BluetoothAdapter.getDefaultAdapter()
            }
            if (null != mBluetoothAdapter) {
                if (!mBluetoothAdapter!!.isEnabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        AlertDialog.Builder(context)
                            .setMessage(R.string.tiramisu_bluetooth)
                            .setCancelable(false)
                            .setPositiveButton(R.string.proceed) { dialog: DialogInterface, _: Int ->
                                context.startActivity(
                                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                )
                                dialog.dismiss()
                            }
                            .setNegativeButton(R.string.cancel) { _: DialogInterface?, _: Int ->
                                listener.onAdapterMissing() }
                            .show()
                    } else {
                        @Suppress("DEPRECATION")
                        mBluetoothAdapter!!.enable()
                    }
                }
                return mBluetoothAdapter
            }
        }
        return null
    }

    companion object {
        private val PERMISSIONS_LOCATION = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        @RequiresApi(Build.VERSION_CODES.S)
        private val PERMISSIONS_BLUETOOTH = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    }

    interface BluetoothListener {
        fun onPermissionsFailed()
        fun onAdapterMissing()
        fun onAdapterEnabled(adapter: BluetoothAdapter?)
    }
}