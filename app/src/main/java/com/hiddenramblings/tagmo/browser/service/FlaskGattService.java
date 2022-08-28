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
 *
 * Bluup Labs and its members are exempt from the above license requirements.
 */

package com.hiddenramblings.tagmo.browser.service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;

import androidx.annotation.RequiresApi;

import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.eightbit.charset.CharsetCompat;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.nfctech.TagUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("MissingPermission")
public class FlaskGattService extends Service {

    private final Class<?> TAG = FlaskGattService.class;

    private BluetoothGattListener listener;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    BluetoothGattCharacteristic mCharacteristicRX = null;
    BluetoothGattCharacteristic mCharacteristicTX = null;

    private String nameCompat = null;
    private String tailCompat = null;

    public final static UUID FlaskNUS = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private final static UUID FlaskTX = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private final static UUID FlaskRX = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    public void setListener(BluetoothGattListener listener) {
        this.listener = listener;
    }

    private final ArrayList<Runnable> Callbacks = new ArrayList<>();

    public interface BluetoothGattListener {
        void onServicesDiscovered();
        void onFlaskActiveChanged(JSONObject jsonObject);
        void onFlaskStatusChanged(JSONObject jsonObject);
        void onFlaskListRetrieved(JSONArray jsonArray);
        void onFlaskFilesDownload(String dataString);
        void onFlaskFilesUploaded();
        void onGattConnectionLost();
    }

    StringBuilder response = new StringBuilder();

    private void getCharacteristicValue(BluetoothGattCharacteristic characteristic) {
        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            String output = new String(data);
            Debug.Log(TAG, getLogTag(characteristic.getUuid()) + " " + output);

            if (characteristic.getUuid().compareTo(FlaskRX) == 0) {
                if (output.contains(">tag.")) {
                    response = new StringBuilder();
                    response.append(output.split(">")[1]);
                } else if (output.startsWith("tag.") || output.startsWith("{") || response.length() > 0) {
                    response.append(output);
                }
                String progress = response.length() > 0 ? response.toString().trim().replaceAll(
                        Objects.requireNonNull(System.getProperty("line.separator")), ""
                ) : "";

                if (isJSONValid(progress) || progress.endsWith(">")
                        || progress.lastIndexOf("undefined") == 0
                        || progress.lastIndexOf("\n") == 0) {
                    if (Callbacks.size() > 0) {
                        Callbacks.get(0).run();
                        Callbacks.remove(0);
                    }
                }

                if (progress.startsWith("tag.get()") || progress.startsWith("tag.setTag")) {
                    if (progress.endsWith(">")) {
                        if (progress.contains("Uncaught no such element")
                                && null != nameCompat && null != tailCompat) {
                            response = new StringBuilder();
                            fixAmiiboName(nameCompat, tailCompat);
                            nameCompat = null;
                            tailCompat = null;
                            return;
                        }
                        try {
                            String getAmiibo = progress.substring(progress.indexOf("{"),
                                    progress.lastIndexOf("}") + 1);
                            if (null != listener) {
                                try {
                                    JSONObject jsonObject = new JSONObject(getAmiibo);
                                    listener.onFlaskActiveChanged(jsonObject);
                                } catch (JSONException e) {
                                    Debug.Log(e);
                                    if (null != listener)
                                        listener.onFlaskActiveChanged(null);
                                }
                            }
                        } catch (StringIndexOutOfBoundsException ex) {
                            Debug.Log(ex);
                        }
                        response = new StringBuilder();
                    }
                } else if (progress.startsWith("tag.getList()")) {
                    if (progress.endsWith(">") || progress.endsWith("\n")) {
                        String getList = progress.substring(progress.indexOf("["),
                                progress.lastIndexOf("]") + 1);
                        try {
                            String escapedList = getList.replace("'", "\\'")
                                    .replace("-", "\\-");
                            JSONArray jsonArray = new JSONArray(escapedList);
                            if (null != listener) listener.onFlaskListRetrieved(jsonArray);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        response = new StringBuilder();
                        if (null != listener) listener.onFlaskFilesUploaded();
                    }
                } else if (progress.startsWith("tag.remove")) {
                    if (progress.endsWith(">") || progress.endsWith("\n")) {
                        if (null != listener) listener.onFlaskStatusChanged(null);
                        response = new StringBuilder();
                    }
                } else if (progress.startsWith("tag.download")) {
                    if (progress.endsWith(">") || progress.endsWith("\n")) {
                        String[] getData = progress.split("new Uint8Array");
                        if (null != listener) {
                            for (String dataString : getData) {
                                if (dataString.startsWith("tag.download")
                                        && dataString.endsWith("=")) continue;
                                dataString = dataString.substring(1, dataString
                                        .lastIndexOf(">") - 2);
                                listener.onFlaskFilesDownload(dataString);
                            }
                        }
                    }
                } else if (progress.startsWith("tag.createBlank()")) {
                    if (progress.endsWith(">") || progress.endsWith("\n")) {
                        response = new StringBuilder();
                        if (null != listener) listener.onFlaskStatusChanged(null);
                    }
                } else if (progress.endsWith("}")) {
                    if (null != listener) {
                        try {
                            JSONObject jsonObject = new JSONObject(response.toString());
                            String event = jsonObject.getString("event");
                            if (event.equals("button"))
                                listener.onFlaskActiveChanged(jsonObject);
                            if (event.equals("delete"))
                                listener.onFlaskStatusChanged(jsonObject);
                        } catch (JSONException e) {
                            if (null != e.getMessage() && e.getMessage().contains("tag.setTag")) {
                                getActiveAmiibo();
                            } else {
                                e.printStackTrace();
                            }
                        }
                    }
                    response = new StringBuilder();
                } else if (progress.endsWith(">")) {
                    response = new StringBuilder();
                }
            }
        }
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (null != listener) listener.onGattConnectionLost();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    mBluetoothGatt.requestMtu(512); // Maximum: 517
                else if (null != listener) listener.onServicesDiscovered();
            } else {
                Debug.Log(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                getCharacteristicValue(characteristic);
            }
        }

        @Override
        public void onCharacteristicWrite(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status
        ) {
            Debug.Log(TAG, getLogTag(characteristic.getUuid())
                    + " onCharacteristicWrite " + status);
        }

        @Override
        public void onCharacteristicChanged(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic
        ) {
            getCharacteristicValue(characteristic);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Debug.Log(TAG, "onMtuChange complete: " + mtu);
            } else {
                Debug.Log(TAG, "onMtuChange received: " + status);
            }
            if (null != listener) listener.onServicesDiscovered();
        }
    };

    public class LocalBinder extends Binder {
        public FlaskGattService getService() {
            return FlaskGattService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        if (mBluetoothGatt == null) {
            return super.onUnbind(intent);
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        return mBluetoothAdapter != null;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            return mBluetoothGatt.connect();
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        mBluetoothDeviceAddress = address;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.disconnect();
    }

    private void setResponseDescriptors(BluetoothGattCharacteristic characteristic) {
        try {
            BluetoothGattDescriptor descriptorTX = characteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            );
            descriptorTX.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptorTX);
        } catch (Exception ignored) {
        }
        try {
            BluetoothGattDescriptor descriptorTX = characteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            );
            descriptorTX.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptorTX);
        } catch (Exception ignored) {
        }
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(
            BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        setResponseDescriptors(characteristic);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    public BluetoothGattCharacteristic getCharacteristicRX(BluetoothGattService mCustomService) {
        BluetoothGattCharacteristic mReadCharacteristic =
                mCustomService.getCharacteristic(FlaskRX);
        if (!mBluetoothGatt.readCharacteristic(mReadCharacteristic)) {
            for (BluetoothGattCharacteristic customRead : mCustomService.getCharacteristics()) {
                UUID customUUID = customRead.getUuid();
                /*get the read characteristic from the service*/
                if (customUUID.compareTo(FlaskRX) == 0) {
                    Debug.Log(TAG, "GattReadCharacteristic: " + customUUID);
                    mReadCharacteristic = mCustomService.getCharacteristic(customUUID);
                    break;
                }
            }
        }
        return mReadCharacteristic;
    }

    public void setFlaskCharacteristicRX() throws UnsupportedOperationException {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw new UnsupportedOperationException();
        }

        BluetoothGattService mCustomService = mBluetoothGatt.getService(FlaskNUS);
        /*check if the service is available on the device*/
        if (null == mCustomService) {
            List<BluetoothGattService> services = getSupportedGattServices();
            if (null == services || services.isEmpty()) {
                throw new UnsupportedOperationException();
            }

            for (BluetoothGattService customService : services) {
                Debug.Log(TAG, "GattReadService: " + customService.getUuid().toString());
                /*get the read characteristic from the service*/
                mCharacteristicRX = getCharacteristicRX(customService);
                break;
            }
        } else {
            mCharacteristicRX = getCharacteristicRX(mCustomService);
        }
        setCharacteristicNotification(mCharacteristicRX, true);
    }

    public BluetoothGattCharacteristic getCharacteristicTX(BluetoothGattService mCustomService) {
        BluetoothGattCharacteristic mWriteCharacteristic =
                mCustomService.getCharacteristic(FlaskTX);
        if (!mBluetoothGatt.writeCharacteristic(mWriteCharacteristic)) {
            for (BluetoothGattCharacteristic customWrite : mCustomService.getCharacteristics()) {
                UUID customUUID = customWrite.getUuid();
                /*get the write characteristic from the service*/
                if (customUUID.compareTo(FlaskTX) == 0) {
                    Debug.Log(TAG, "GattWriteCharacteristic: " + customUUID);
                    mWriteCharacteristic = mCustomService.getCharacteristic(customUUID);
                    break;
                }
            }
        }
        return mWriteCharacteristic;
    }

    public void setFlaskCharacteristicTX() throws UnsupportedOperationException {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw new UnsupportedOperationException();
        }

        BluetoothGattService mCustomService = mBluetoothGatt.getService(FlaskNUS);
        /*check if the service is available on the device*/
        if (null == mCustomService) {
            List<BluetoothGattService> services = getSupportedGattServices();
            if (null == services || services.isEmpty()) {
                throw new UnsupportedOperationException();
            }

            for (BluetoothGattService customService : services) {
                Debug.Log(TAG, "GattWriteService: " + customService.getUuid().toString());
                /*get the read characteristic from the service*/
                mCharacteristicTX = getCharacteristicTX(customService);
            }
        } else {
            mCharacteristicTX = getCharacteristicTX(mCustomService);
        }
        setCharacteristicNotification(mCharacteristicTX, true);
    }

    private void delayedWriteCharacteristic(byte[] value) {
        List<byte[]> chunks = byteToPortions(value, 20);
        int commandQueue = Callbacks.size() + 1 + chunks.size();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            for (int i = 0; i < chunks.size(); i += 1) {
                final byte[] chunk = chunks.get(i);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    mCharacteristicTX.setValue(chunk);
                    mCharacteristicTX.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    mBluetoothGatt.writeCharacteristic(mCharacteristicTX);
                }, (i + 1) * 30L);
            }
        }, commandQueue * 30L);
    }

    public void queueTagCharacteristic(String value, int index) {
        if (null == mCharacteristicTX) {
            try {
                setFlaskCharacteristicTX();
            } catch (UnsupportedOperationException e) {
                e.printStackTrace();
            }
        }

        Callbacks.add(index, () -> delayedWriteCharacteristic(
                ("tag." + value + "\n").getBytes(CharsetCompat.UTF_8)
        ));

        if (Callbacks.size() == 1) {
            Callbacks.get(0).run();
        }
    }

    public void delayedTagCharacteristic(String value) {
        queueTagCharacteristic(value, Callbacks.size());
    }

    public void promptTagCharacteristic(String value) {
        queueTagCharacteristic(value, 0);
    }
    
    public void uploadAmiiboFile(byte[] amiiboData, Amiibo amiibo) {
        delayedTagCharacteristic("startTagUpload(" + amiiboData.length + ")");
        List<String> chunks = stringToPortions(Base64.encodeToString(
                amiiboData, Base64.NO_PADDING | Base64.NO_CLOSE | Base64.NO_WRAP
        ), 128);
        for (int i = 0; i < chunks.size(); i+=1) {
            final String chunk = chunks.get(i);
            delayedTagCharacteristic(
                    "tagUploadChunk(\"" + chunk + "\")"
            );
        }
        String flaskTail = Integer.toString(Integer.parseInt(TagUtils
                .amiiboIdToHex(amiibo.id).substring(8, 16), 16), 36);
        int reserved = flaskTail.length() + 3; // |tail|#
        String nameUnicode = stringToUnicode(amiibo.name);
        String amiiboName = nameUnicode.length() + reserved > 28
                ? nameUnicode.substring(0, nameUnicode.length()
                - ((nameUnicode.length() + reserved) - 28))
                : nameUnicode;
        delayedTagCharacteristic("saveUploadedTag(\""
                + amiiboName + "|" + flaskTail + "|0\")");
    }

    public void uploadFilesComplete() {
        delayedTagCharacteristic("uploadsComplete()");
        delayedTagCharacteristic("getList()");
    }

    public void setActiveAmiibo(String name, String tail) {
        if (name.equals("New Tag ")) {
            delayedTagCharacteristic("setTag(\"" + name + "||" + tail + "\")");
        } else {
            int reserved = tail.length() + 3; // |tail|#
            String nameUnicode = stringToUnicode(name);
            nameCompat = nameUnicode.length() + reserved > 28
                    ? nameUnicode.substring(0, nameUnicode.length()
                    - ((nameUnicode.length() + reserved) - 28))
                    : nameUnicode;
            tailCompat = tail;
            delayedTagCharacteristic("setTag(\"" + nameCompat + "|" + tailCompat + "|0\")");
        }
    }

    public void fixAmiiboName(String name, String tail) {
        int reserved = tail.length() + 3; // |tail|#
        String nameUnicode = stringToUnicode(name);
        String amiiboName = nameUnicode.length() + reserved > 28
                ? nameUnicode.substring(0, nameUnicode.length()
                - ((nameUnicode.length() + reserved) - 28))
                : nameUnicode;
        promptTagCharacteristic("rename(\"" + amiiboName + "|" + tail
                + "\",\"" + amiiboName + "|" + tail + "|0\" )");
        getDeviceAmiibo();
    }

    public void deleteAmiibo(String name, String tail) {
        if (name.equals("New Tag ")) {
            delayedTagCharacteristic("remove(\"" + name + "||" + tail + "\")");
        } else {
            int reserved = tail.length() + 3; // |tail|#
            String nameUnicode = stringToUnicode(name);
            nameCompat = nameUnicode.length() + reserved > 28
                    ? nameUnicode.substring(0, nameUnicode.length()
                    - ((nameUnicode.length() + reserved) - 28))
                    : nameUnicode;
            tailCompat = tail;
            delayedTagCharacteristic("remove(\"" + nameCompat + "|" + tailCompat + "|0\")");
        }
    }

    public void downloadAmiibo(String name, String tail) {
        int reserved = tail.length() + 3; // |tail|#
        String nameUnicode = stringToUnicode(name);
        String amiiboName = nameUnicode.length() + reserved > 28
                ? nameUnicode.substring(0, nameUnicode.length()
                - ((nameUnicode.length() + reserved) - 28))
                : nameUnicode;
        delayedTagCharacteristic("download(\"" + amiiboName + "|" + tail + "|0\")");
    }

    public void getActiveAmiibo() {
        delayedTagCharacteristic("get()");
    }

    public void getDeviceAmiibo() {
        delayedTagCharacteristic("getList()");
    }

    public void createBlankTag() {
        delayedTagCharacteristic("createBlank()");
    }

    public static List<byte[]> byteToPortions(byte[] largeByteArray, int sizePerPortion) {
        List<byte[]> byteArrayPortions = new ArrayList<>();
        int offset = 0;
        while (offset < largeByteArray.length) {
            byte[] portion = Arrays.copyOfRange(largeByteArray, offset, offset + sizePerPortion);
            offset += sizePerPortion;
            byteArrayPortions.add(portion);
        }
        return byteArrayPortions;
    }

    public static List<String> stringToPortions(String largeString, int sizePerPortion) {
        List<String> stringPortions = new ArrayList<>();
        int size = largeString.length();
        if (size <= sizePerPortion) {
            stringPortions.add(largeString);
        } else {
            int index = 0;
            while (index < size) {
                stringPortions.add(largeString.substring(index,
                        Math.min(index + sizePerPortion, largeString.length())));
                index += sizePerPortion;
            }
        }
        return stringPortions;
    }

    public static String stringToUnicode(String s) {
        StringBuilder sb = new StringBuilder(s.length() * 3);
        for (char c : s.toCharArray()) {
            if (c < 256) {
                sb.append(c);
            } else {
                String strHex = Integer.toHexString(c);
                sb.append("\\u").append(strHex);
            }
        }
        return sb.toString();
    }

    public boolean isJSONValid(String test) {
        if (test.startsWith("tag.") && test.endsWith(")")) return false;
        try {
            new JSONObject(test);
        } catch (JSONException ex) {
            try {
                new JSONArray(test);
            } catch (JSONException jex) {
                return false;
            }
        }
        return true;
    }

    private String getLogTag(UUID uuid) {
        if (uuid.compareTo(FlaskTX) == 0) {
            return "FlaskTX";
        } else if (uuid.compareTo(FlaskRX) == 0) {
            return "FlaskRX";
        } else if (uuid.compareTo(FlaskNUS) == 0) {
            return "FlaskNUS";
        } else {
            return uuid.toString();
        }
    }
}
