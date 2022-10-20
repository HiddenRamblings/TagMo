/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */

package com.hiddenramblings.tagmo.bluetooth;

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
import com.hiddenramblings.tagmo.eightbit.io.Debug;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server
 * hosted on a given Bluetooth LE device based on core/java/android/bluetooth
 *
 * Android Bluetooth Low Energy Status Codes
 *
 * 0	0x00	BLE_HCI_STATUS_CODE_SUCCESS
 * 1	0x01	BLE_HCI_STATUS_CODE_UNKNOWN_BTLE_COMMAND
 * 2	0x02	BLE_HCI_STATUS_CODE_UNKNOWN_CONNECTION_IDENTIFIER
 * 5	0x05	BLE_HCI_AUTHENTICATION_FAILURE
 * 6	0x06	BLE_HCI_STATUS_CODE_PIN_OR_KEY_MISSING
 * 7	0x07	BLE_HCI_MEMORY_CAPACITY_EXCEEDED
 * 8	0x08	BLE_HCI_CONNECTION_TIMEOUT
 * 12	0x0C	BLE_HCI_STATUS_CODE_COMMAND_DISALLOWED
 * 18	0x12	BLE_HCI_STATUS_CODE_INVALID_BTLE_COMMAND_PARAMETERS
 * 19	0x13	BLE_HCI_REMOTE_USER_TERMINATED_CONNECTION
 * 20	0x14	BLE_HCI_REMOTE_DEV_TERMINATION_DUE_TO_LOW_RESOURCES
 * 21	0x15	BLE_HCI_REMOTE_DEV_TERMINATION_DUE_TO_POWER_OFF
 * 22	0x16	BLE_HCI_LOCAL_HOST_TERMINATED_CONNECTION
 * 26	0x1A	BLE_HCI_UNSUPPORTED_REMOTE_FEATURE
 * 30	0x1E	BLE_HCI_STATUS_CODE_INVALID_LMP_PARAMETERS
 * 31	0x1F	BLE_HCI_STATUS_CODE_UNSPECIFIED_ERROR
 * 34	0x22	BLE_HCI_STATUS_CODE_LMP_RESPONSE_TIMEOUT
 * 36	0x24	BLE_HCI_STATUS_CODE_LMP_PDU_NOT_ALLOWED
 * 40	0x28	BLE_HCI_INSTANT_PASSED
 * 41	0x29	BLE_HCI_PAIRING_WITH_UNIT_KEY_UNSUPPORTED
 * 42	0x2A	BLE_HCI_DIFFERENT_TRANSACTION_COLLISION
 * 58	0x3A	BLE_HCI_CONTROLLER_BUSY
 * 59	0x3B	BLE_HCI_CONN_INTERVAL_UNACCEPTABLE
 * 60	0x3C	BLE_HCI_DIRECTED_ADVERTISER_TIMEOUT
 * 61	0x3D	BLE_HCI_CONN_TERMINATED_DUE_TO_MIC_FAILURE
 * 62	0x3E	BLE_HCI_CONN_FAILED_TO_BE_ESTABLISHED
 * 128	0x80	GATT_NO_RESSOURCES
 * 129	0x81	GATT_INTERNAL_ERROR
 * 130	0x82	GATT_WRONG_STATE
 * 131	0x83	GATT_DB_FULL
 * 132	0x84	GATT_BUSY
 * 133	0x85	GATT_ERROR
 * 135	0x87	GATT_ILLEGAL_PARAMETER
 * 137	0x89	GATT_AUTH_FAIL
 * 138	0x8A	GATT_MORE
 * 139	0x8B	GATT_INVALID_CFG
 * 140	0x8C	GATT_SERVICE_STARTED
 * 141	0x8D	GATT_ENCRYPED_NO_MITM
 * 142	0x8E	GATT_NOT_ENCRYPTED
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

    private int maxTransmissionUnit = 23;
    public final static UUID FlaskNUS = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private final static UUID FlaskTX = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private final static UUID FlaskRX = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    public void setListener(BluetoothGattListener listener) {
        this.listener = listener;
    }

    private final ArrayList<Runnable> outgoingCallbacks = new ArrayList<>();
    private final ArrayList<Runnable> incomingCallbacks = new ArrayList<>();

    private final Handler flaskHandler = new Handler(Looper.getMainLooper());

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
    private int rangeIndex = 0;
    private JSONArray rangeArray;


    private void getCharacteristicValue(BluetoothGattCharacteristic characteristic) {
        String output = characteristic.getStringValue(0x0);
        if (null != output && output.length() > 0) {
            Debug.Verbose(TAG, getLogTag(characteristic.getUuid()) + " " + output);

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
                    if (outgoingCallbacks.size() > 0) {
                        outgoingCallbacks.get(0).run();
                        outgoingCallbacks.remove(0);
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
                                    listener.onFlaskActiveChanged(new JSONObject(getAmiibo));
                                } catch (JSONException e) {
                                    Debug.Warn(e);
                                    if (null != listener)
                                        listener.onFlaskActiveChanged(null);
                                }
                            }
                        } catch (StringIndexOutOfBoundsException ex) {
                            Debug.Warn(ex);
                        }
                        response = new StringBuilder();
                    }
                } else if (progress.startsWith("tag.getList")) {
                    if (progress.endsWith(">") || progress.endsWith("\n")) {
                        String getList = progress.substring(progress.indexOf("["),
                                progress.lastIndexOf("]") + 1);
                        try {
                            String escapedList = getList
                                    .replace("/", "\\/")
                                    .replace("'", "\\'")
                                    .replace("-", "\\-");
                            if (getList.contains("...")) {
                                if (rangeIndex > 0) {
                                    rangeIndex = 0;
                                    escapedList = escapedList.replace(" ...", "");
                                    if (null != listener)
                                        listener.onFlaskListRetrieved(new JSONArray(escapedList));
                                } else {
                                    rangeIndex += 1;
                                    getDeviceAmiiboRange(0);
                                }
                            } else if (rangeIndex > 0) {
                                JSONArray jsonArray = new JSONArray(escapedList);
                                if (jsonArray.length() > 0) {
                                    for (int i = 0; i < jsonArray.length(); i++) {
                                        rangeArray.put(jsonArray.getJSONObject(i));
                                    }
                                    rangeIndex += 1;
                                    getDeviceAmiiboRange(rangeIndex * 10);
                                } else {
                                    rangeIndex = 0;
                                    if (null != listener) listener.onFlaskListRetrieved(rangeArray);
                                    rangeArray = null;
                                }
                            } else {
                                if (null != listener)
                                    listener.onFlaskListRetrieved(new JSONArray(escapedList));
                            }
                        } catch (JSONException e) {
                            Debug.Warn(e);
                        }
                        response = new StringBuilder();
                        if (rangeIndex == 0 && null != listener) listener.onFlaskFilesUploaded();
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
                                Debug.Warn(e);
                            }
                        }
                    }
                    response = new StringBuilder();
                } else if (progress.endsWith(">")) {
                    response = new StringBuilder();
                }
            }
        }
//        if (incomingCallbacks.size() > 0) {
//            incomingCallbacks.get(0).run();
//            incomingCallbacks.remove(0);
//        }
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
                if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP))
                    mBluetoothGatt.requestMtu(512); // Maximum: 517
                else if (null != listener) listener.onServicesDiscovered();
            } else {
                Debug.Warn(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
//                incomingCallbacks.add(incomingCallbacks.size(), () ->
//                        getCharacteristicValue(characteristic));
//
//                if (incomingCallbacks.size() == 1) {
//                    incomingCallbacks.get(0).run();
//                    incomingCallbacks.remove(0);
//                }
                 getCharacteristicValue(characteristic);
            }
        }

        @Override
        public void onCharacteristicWrite(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status
        ) {
            Debug.Verbose(TAG, getLogTag(characteristic.getUuid())
                    + " onCharacteristicWrite " + status);
        }

        @Override
        public void onCharacteristicChanged(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic
        ) {
//            incomingCallbacks.add(incomingCallbacks.size(), () -> getCharacteristicValue(characteristic));
//
//            if (incomingCallbacks.size() == 1) {
//                incomingCallbacks.get(0).run();
//                incomingCallbacks.remove(0);
//            }
             getCharacteristicValue(characteristic);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (null != listener) listener.onServicesDiscovered();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Debug.Verbose(TAG, "onMtuChange complete: " + mtu);
                maxTransmissionUnit = mtu - 3;
            } else {
                Debug.Warn(TAG, "onMtuChange received: " + status);
            }
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
                    Debug.Verbose(TAG, "GattReadCharacteristic: " + customUUID);
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
                Debug.Verbose(TAG, "GattReadService: " + customService.getUuid().toString());
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
                    Debug.Verbose(TAG, "GattWriteCharacteristic: " + customUUID);
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
                Debug.Verbose(TAG, "GattWriteService: " + customService.getUuid().toString());
                /*get the read characteristic from the service*/
                mCharacteristicTX = getCharacteristicTX(customService);
            }
        } else {
            mCharacteristicTX = getCharacteristicTX(mCustomService);
        }
        setCharacteristicNotification(mCharacteristicTX, true);
    }

    @SuppressWarnings("unused")
    private void delayedWriteCharacteristic(byte[] value) {
        List<byte[]> chunks = GattArray.byteToPortions(value, maxTransmissionUnit);
        int commandQueue = outgoingCallbacks.size() + 1 + chunks.size();
        flaskHandler.postDelayed(() -> {
            for (int i = 0; i < chunks.size(); i += 1) {
                final byte[] chunk = chunks.get(i);
                flaskHandler.postDelayed(() -> {
                    mCharacteristicTX.setValue(chunk);
                    mCharacteristicTX.setWriteType(
                            // BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    );
                    try {
                        mBluetoothGatt.writeCharacteristic(mCharacteristicTX);
                    } catch (NullPointerException ex) {
                        if (null != listener) listener.onServicesDiscovered();
                    }
                }, (i + 1) * 30L);
            }
        }, commandQueue * 30L);
    }

    private void delayedWriteCharacteristic(String value) {
        List<String> chunks = GattArray.stringToPortions(value, maxTransmissionUnit);
        int commandQueue = outgoingCallbacks.size() + 1 + chunks.size();
        flaskHandler.postDelayed(() -> {
            for (int i = 0; i < chunks.size(); i += 1) {
                final String chunk = chunks.get(i);
                flaskHandler.postDelayed(() -> {
                    mCharacteristicTX.setValue(chunk);
                    mCharacteristicTX.setWriteType(
                            // BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    );
                    try {
                        mBluetoothGatt.writeCharacteristic(mCharacteristicTX);
                    } catch (NullPointerException ex) {
                        if (null != listener) listener.onServicesDiscovered();
                    }
                }, (i + 1) * 30L);
            }
        }, commandQueue * 30L);
    }

    public void queueTagCharacteristic(String value, int index) {
        if (null == mCharacteristicTX) {
            try {
                setFlaskCharacteristicTX();
            } catch (UnsupportedOperationException e) {
                Debug.Warn(e);
            }
        }

        outgoingCallbacks.add(index, () -> delayedWriteCharacteristic(("tag." + value + "\n")));

        if (outgoingCallbacks.size() == 1) {
            outgoingCallbacks.get(0).run();
            outgoingCallbacks.remove(0);
        }
    }

    public void delayedTagCharacteristic(String value) {
        queueTagCharacteristic(value, outgoingCallbacks.size());
    }

    public void promptTagCharacteristic(String value) {
        queueTagCharacteristic(value, 0);
    }
    
    public void uploadAmiiboFile(byte[] amiiboData, Amiibo amiibo) {
        delayedTagCharacteristic("startTagUpload(" + amiiboData.length + ")");
        List<String> chunks = GattArray.stringToPortions(Base64.encodeToString(
                amiiboData, Base64.NO_PADDING | Base64.NO_CLOSE | Base64.NO_WRAP
        ), 128);
        for (int i = 0; i < chunks.size(); i+=1) {
            final String chunk = chunks.get(i);
            delayedTagCharacteristic(
                    "tagUploadChunk(\"" + chunk + "\")"
            );
        }
        String flaskTail = Integer.toString(Integer.parseInt(
                Amiibo.idToHex(amiibo.id).substring(8, 16), 16
        ), 36);
        int reserved = flaskTail.length() + 3; // |tail|#
        String nameUnicode = GattArray.stringToUnicode(amiibo.name);
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
            String nameUnicode = GattArray.stringToUnicode(name);
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
        String nameUnicode = GattArray.stringToUnicode(name);
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
            String nameUnicode = GattArray.stringToUnicode(name);
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
        String nameUnicode = GattArray.stringToUnicode(name);
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

    public void getDeviceAmiiboRange(int index) {
        delayedTagCharacteristic("getList(" + index + ",10)"); // 5 ... 5
    }

    public void createBlankTag() {
        delayedTagCharacteristic("createBlank()");
    }

    public void setFlaskFace(boolean stacked) {
        delayedTagCharacteristic("screen.setFace(" + (stacked ? 1 : 0) + ")");
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
