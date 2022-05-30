package com.hiddenramblings.tagmo;

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
import android.nfc.TagLostException;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.hiddenramblings.tagmo.eightbit.charset.CharsetCompat;
import com.hiddenramblings.tagmo.eightbit.io.Debug;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("MissingPermission")
public class BluetoothLeService extends Service {

    private BluetoothGattListener listener;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    BluetoothGattCharacteristic mCharacteristicRX = null;
    BluetoothGattCharacteristic mCharacteristicTX = null;

    public final static UUID FlaskNUS = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private final static UUID FlaskTX = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private final static UUID FlaskRX = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    public void setListener(BluetoothGattListener listener) {
        this.listener = listener;
    }
    interface BluetoothGattListener {
        void onServicesDiscovered();
        void onFlaskActiveChanged(JSONObject jsonObject);
        void onFlaskActiveDeleted(JSONObject jsonObject);
        void onFlaskListRetrieved(JSONArray jsonArray);
        void onFlaskActiveLocated(JSONObject jsonObject);
    }

    StringBuilder response = new StringBuilder();
    private void getCharacteristicValue(BluetoothGattCharacteristic characteristic) {
        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            String output = new String(data);
            Log.d(getLogTag(characteristic.getUuid()), output);

            if (characteristic.getUuid().compareTo(FlaskRX) == 0) {
                if (output.startsWith("tag.") || output.startsWith("{") || response.length() > 0) {
                    response.append(output);
                }
                String progress = response.length() > 0 ? response.toString().trim() : "";
                if (progress.startsWith("tag.get()")) {
                    if (progress.endsWith(">")) {
                        String getAmiibo = progress.substring(progress.indexOf("{"),
                                progress.lastIndexOf("}") + 1);
                        if (null != listener) {
                            try {
                                JSONObject jsonObject = new JSONObject(getAmiibo);
                                listener.onFlaskActiveLocated(jsonObject);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            response = new StringBuilder();
                        }
                    }
                } else if (progress.startsWith("tag.getList()")) {
                    if (progress.endsWith(">")) {
                        String getList = progress.substring(progress.indexOf("["),
                                progress.lastIndexOf("]") + 1);
                        try {
                            JSONArray jsonArray = new JSONArray(getList);
                            if (null != listener) listener.onFlaskListRetrieved(jsonArray);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        response = new StringBuilder();
                    }
                } else if (progress.endsWith("}")) {
                    if (null != listener) {
                        try {
                            JSONObject jsonObject = new JSONObject(response.toString());
                            String event = jsonObject.getString("event");
                            if (event.equals("button"))
                                listener.onFlaskActiveChanged(jsonObject);
                            if (event.equals("delete"))
                                listener.onFlaskActiveDeleted(jsonObject);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
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

            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (null != listener) listener.onServicesDiscovered();
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                    mBluetoothGatt.requestMtu(517);
//                }
            } else {
                Debug.Log(BluetoothLeService.class,
                        "onServicesDiscovered received: " + status);
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
            Log.d(getLogTag(characteristic.getUuid()), "onCharacteristicWrite " + status);
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                getCharacteristicValue(characteristic);
//            }
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
                Debug.Log(BluetoothLeService.class,
                        "onMtuChange complete: " + mtu);
            } else {
                Debug.Log(BluetoothLeService.class,
                        "onMtuChange received: " + status);
            }
        }
    };

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
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
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
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

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    private void setResponseDescriptors(BluetoothGattCharacteristic characteristic) {
        try {
            BluetoothGattDescriptor descriptorTX = characteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            );
            descriptorTX.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptorTX);
        } catch (Exception ignored) { }
        try {
            BluetoothGattDescriptor descriptorTX = characteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            );
            descriptorTX.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptorTX);
        } catch (Exception ignored) { }
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
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
                    Debug.Log(BluetoothLeService.class,
                            "GattReadCharacteristic: " + customUUID);
                    mReadCharacteristic = mCustomService.getCharacteristic(customUUID);
                    break;
                }
            }
        }
        return mReadCharacteristic;
    }

    public void setFlaskCharacteristicRX() throws TagLostException {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw new TagLostException();
        }

        BluetoothGattService mCustomService = mBluetoothGatt.getService(FlaskNUS);
        /*check if the service is available on the device*/
        if (null == mCustomService) {
            List<BluetoothGattService> services = getSupportedGattServices();
            if (null == services || services.isEmpty()) {
                throw new TagLostException();
            }

            for (BluetoothGattService customService : services) {
                Debug.Log(BluetoothLeService.class,
                        "GattReadService: " + customService.getUuid().toString());
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
                    Debug.Log(BluetoothLeService.class,
                            "GattWriteCharacteristic: " + customUUID);
                    mWriteCharacteristic = mCustomService.getCharacteristic(customUUID);
                    break;
                }
            }
        }
        return mWriteCharacteristic;
    }

    public void setFlaskCharacteristicTX() throws TagLostException {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw new TagLostException();
        }

        BluetoothGattService mCustomService = mBluetoothGatt.getService(FlaskNUS);
        /*check if the service is available on the device*/
        if (null == mCustomService) {
            List<BluetoothGattService> services = getSupportedGattServices();
            if (null == services || services.isEmpty()) {
                throw new TagLostException();
            }

            for (BluetoothGattService customService : services) {
                Debug.Log(BluetoothLeService.class,
                        "GattWriteService: " + customService.getUuid().toString());
                /*get the read characteristic from the service*/
                mCharacteristicTX = getCharacteristicTX(customService);
            }
        } else {
            mCharacteristicTX = getCharacteristicTX(mCustomService);
        }
        setCharacteristicNotification(mCharacteristicTX, true);
    }

    // https://stackoverflow.com/a/50022158/461982
    // this method splits your byte array into small portions
    // and returns a list with those portions
    public static List<byte[]> byteToPortions(byte[] largeByteArray) {
        // create a list to keep the portions
        List<byte[]> byteArrayPortions = new ArrayList<>();

        int sizePerPortion = 20;
        int offset = 0;

        // split the array
        while(offset < largeByteArray.length) {
            // into 5 mb portions
            byte[] portion = Arrays.copyOfRange(largeByteArray, offset, offset + sizePerPortion);
            // update the offset to increment the copied area
            offset += sizePerPortion;
            // add the byte array portions to the list
            byteArrayPortions.add(portion);
        }
        // return portions
        return byteArrayPortions;
    }

    public void delayedWriteCharacteristic(String value) {
        if (null == mCharacteristicTX) {
            try {
                setFlaskCharacteristicTX();
            } catch (TagLostException e) {
                e.printStackTrace();
            }
        }

        String command = value + "\n";
        List<byte[]> chunks = byteToPortions(command.getBytes(CharsetCompat.UTF_8));
        for (int i = 0; i < chunks.size(); i += 1) {
            final byte[] chunk = chunks.get(i);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d(getLogTag(mCharacteristicTX.getUuid()), new String(chunk));
                mCharacteristicTX.setValue(chunk);
                mCharacteristicTX.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                mBluetoothGatt.writeCharacteristic(mCharacteristicTX);
            }, (i + 1) * 50L);
        }
//        for (int i = 0; i < command.length(); i += 20) {
//            String chunk = command.substring(i, i + 20);
//            new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                Log.d(getLogTag(mCharacteristicTX.getUuid()), chunk);
//                mCharacteristicTX.setValue(chunk.getBytes(CharsetCompat.UTF_8));
//                mBluetoothGatt.writeCharacteristic(mCharacteristicTX);
//            }, i + 50);
//        }
    }

    private String getLogTag(UUID uuid) {
        if (uuid.compareTo(FlaskTX) == 0) {
            return "FlaskTX";
        } else if (uuid.compareTo(FlaskRX) == 0) {
            return "FlaskRX";
        } else {
            return "FlaskNUS";
        }
    }
}
