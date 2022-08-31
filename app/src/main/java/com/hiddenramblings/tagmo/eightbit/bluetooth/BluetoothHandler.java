/* ====================================================================
 * Copyright (c) 2012-2022 AbandonedCart.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * For the purpose of this license, the phrase "TagMo labels" shall
 * be used to refer to the labels "8-Bit Dream", "TwistedUmbrella",
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

package com.hiddenramblings.tagmo.eightbit.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResultRegistry;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;

import java.util.Map;

public class BluetoothHandler {

    private BluetoothAdapter mBluetoothAdapter;
    private final BluetoothListener listener;

    ActivityResultLauncher<String[]> onRequestLocationQ;
    ActivityResultLauncher<String> onRequestBackgroundQ;
    ActivityResultLauncher<String[]> onRequestBluetoothS;
    ActivityResultLauncher<Intent> onRequestBluetooth;
    ActivityResultLauncher<String[]> onRequestLocation;

    public BluetoothHandler(Context context, ActivityResultRegistry registry, BluetoothListener listener) {
        onRequestLocationQ = registry.register("LocationQ",
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> { boolean isLocationAvailable = false;
            for (Map.Entry<String,Boolean> entry : permissions.entrySet()) {
                if (entry.getKey().equals(Manifest.permission.ACCESS_FINE_LOCATION)
                        && entry.getValue()) isLocationAvailable = true;
            }
            if (isLocationAvailable) {
                requestBluetooth(context);
            } else {
                listener.onPermissionsFailed();
            }
        });

        onRequestBackgroundQ = registry.register("BackgroundQ",
                new ActivityResultContracts.RequestPermission(), permission -> {});
        
        onRequestBluetoothS = registry.register("BluetoothS",
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> { boolean isBluetoothAvailable = false;
            for (Map.Entry<String,Boolean> entry : permissions.entrySet()) {
                if (entry.getValue()) isBluetoothAvailable = true;
            }
            if (isBluetoothAvailable) {
                BluetoothAdapter mBluetoothAdapter = getBluetoothAdapter(context);
                if (null != mBluetoothAdapter) {
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                        onRequestBackgroundQ.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
//                    }
                    listener.onAdapterEnabled(mBluetoothAdapter);
                } else {
                    listener.onAdapterMissing();
                }
            } else {
                listener.onAdapterMissing();
            }
        });
        
        onRequestBluetooth = registry.register("Bluetooth",
                new ActivityResultContracts.StartActivityForResult(), result -> {
            BluetoothAdapter mBluetoothAdapter = getBluetoothAdapter(context);
            if (null != mBluetoothAdapter) {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                    onRequestBackgroundQ.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
//                }
                listener.onAdapterEnabled(mBluetoothAdapter);
            } else {
                listener.onAdapterMissing();
            }
        });
        
        onRequestLocation = registry.register("Location", 
                new ActivityResultContracts.RequestMultiplePermissions(), 
                permissions -> { boolean isLocationAvailable = false;
            for (Map.Entry<String,Boolean> entry : permissions.entrySet()) {
                if (entry.getValue()) isLocationAvailable = true;
            }
            if (isLocationAvailable) {
                BluetoothAdapter mBluetoothAdapter = getBluetoothAdapter(context);
                if (null != mBluetoothAdapter)
                    listener.onAdapterEnabled(mBluetoothAdapter);
                else
                    onRequestBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            } else {
                listener.onPermissionsFailed();
            }
        });

        this.listener = listener;
    }

    private void requestBluetooth(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            final String[] PERMISSIONS_BLUETOOTH = {
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            };
            onRequestBluetoothS.launch(PERMISSIONS_BLUETOOTH);
        } else {
            BluetoothAdapter mBluetoothAdapter = getBluetoothAdapter(context);
            if (null != mBluetoothAdapter) {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                    onRequestBackgroundQ.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
//                }
                listener.onAdapterEnabled(mBluetoothAdapter);
            } else {
                onRequestBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            }
        }
    }

    public void requestPermissions(Activity context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            BluetoothAdapter mBluetoothAdapter = getBluetoothAdapter(context);
            if (null != mBluetoothAdapter)
                listener.onAdapterEnabled(mBluetoothAdapter);
            else
                onRequestBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
            )) {
                new AlertDialog.Builder(context)
                        .setMessage(R.string.location_disclosure)
                        .setCancelable(false)
                        .setPositiveButton(R.string.accept, (dialog, which) -> {
                            dialog.dismiss();
                            final String[] PERMISSIONS_LOCATION = {
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                            };
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                onRequestLocationQ.launch(PERMISSIONS_LOCATION);
                            } else {
                                onRequestLocation.launch(PERMISSIONS_LOCATION);
                            }
                        }).setNegativeButton(R.string.deny, (dialog, which) ->
                                listener.onPermissionsFailed()).show();
            } else if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
                requestBluetooth(context);
            } else {
                if (TagMo.isGooglePlay()) {
                    new AlertDialog.Builder(context)
                            .setMessage(R.string.location_disclosure)
                            .setCancelable(false)
                            .setPositiveButton(R.string.accept, (dialog, which) -> {
                                dialog.dismiss();
                                final String[] PERMISSIONS_LOCATION = {
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                };
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    onRequestLocationQ.launch(PERMISSIONS_LOCATION);
                                } else {
                                    onRequestLocation.launch(PERMISSIONS_LOCATION);
                                }
                            }).setNegativeButton(R.string.deny, (dialog, which) ->
                                    listener.onPermissionsFailed()).show();
                } else {
                    final String[] PERMISSIONS_LOCATION = {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    };
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        onRequestLocationQ.launch(PERMISSIONS_LOCATION);
                    } else {
                        onRequestLocation.launch(PERMISSIONS_LOCATION);
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    public BluetoothAdapter getBluetoothAdapter(Context context) {
        if (null == context) return null;
        if (null != mBluetoothAdapter) {
            if (!mBluetoothAdapter.isEnabled()) mBluetoothAdapter.enable();
            return mBluetoothAdapter;
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mBluetoothAdapter = ((BluetoothManager) context.getSystemService(
                        Context.BLUETOOTH_SERVICE
                )).getAdapter();
            } else {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            if (null != mBluetoothAdapter) {
                if (!mBluetoothAdapter.isEnabled()) mBluetoothAdapter.enable();
                return mBluetoothAdapter;
            }
        }
        return null;
    }

    public interface BluetoothListener {
        void onPermissionsFailed();
        void onAdapterMissing();
        void onAdapterEnabled(BluetoothAdapter adapter);
    }
}
