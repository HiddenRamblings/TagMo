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
public class BluetoothLeService extends Service {

    private final Class<?> TAG = BluetoothLeService.class;

    public enum Process {
        IDLING, // Nothing
        GATHER, // getList()
        ACTIVE, // get()
        SELECT, // setActive()
        DELETE, // Delete
        UPLOAD  // Upload
    }

    private Process currentProcess = Process.IDLING;

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

    private ArrayList<Runnable> Callbacks = new ArrayList<Runnable>();

    interface BluetoothGattListener {
        void onServicesDiscovered();
        void onFlaskActiveChanged(JSONObject jsonObject);
        void onFlaskActiveDeleted(JSONObject jsonObject);
        void onFlaskListRetrieved(JSONArray jsonArray);
        void onFlaskActiveLocated(JSONObject jsonObject);
        void onFlaskFilesUploaded();
        void onGattConnectionLost();
    }


    public boolean isJSONValid(String test) {
        try {
            new JSONObject(test);
        } catch (JSONException ex) {
            try {
                new JSONArray(test);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
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

                if (isJSONValid(progress) || progress.endsWith(">") || progress.lastIndexOf("undefined") == 0 || progress.lastIndexOf("\n") == 0) {
                    if (Callbacks.size() > 0) {
                        Debug.Log(TAG, "Callback Called");
                        Callbacks.get(0).run();
                        Callbacks.remove(0);
                    }
                }
                
                if (progress.contains("Uncaught no such element")) {
                    response = new StringBuilder();
                    delayedWriteTagCharacteristic("setTag(\""
                            + nameCompat + "|" + tailCompat + "\")");
                    nameCompat = null;
                    tailCompat = null;
                } else if (progress.startsWith("tag.get()") || progress.startsWith("tag.setTag")) {
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
                        }
                        response = new StringBuilder();
                    }
                } else if (progress.startsWith("tag.getList()")) {
                    if (progress.endsWith(">")) {
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
                if (response.length() <= 0) currentProcess = Process.IDLING;
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
                if (null != listener) listener.onServicesDiscovered();
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                    mBluetoothGatt.requestMtu(517);
//                }
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
                Debug.Log(TAG, "GattWriteService: " + customService.getUuid().toString());
                /*get the read characteristic from the service*/
                mCharacteristicTX = getCharacteristicTX(customService);
            }
        } else {
            mCharacteristicTX = getCharacteristicTX(mCustomService);
        }
        setCharacteristicNotification(mCharacteristicTX, true);
    }

    private int commandQueue = 0;

    private void delayedWriteCharacteristic(byte[] value) {
        List<byte[]> chunks = byteToPortions(value, 20);
        int currentQueue = commandQueue;
        commandQueue += 1 + chunks.size();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            for (int i = 0; i < chunks.size(); i += 1) {
                final byte[] chunk = chunks.get(i);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    mCharacteristicTX.setValue(chunk);
                    mCharacteristicTX.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    mBluetoothGatt.writeCharacteristic(mCharacteristicTX);
                    commandQueue -= 1;
                }, (i + 1) * 30L);
            }
            commandQueue -= 1;
        }, currentQueue * 30L);
    }

    public void delayedWriteTagCharacteristic(String value) {
        if (null == mCharacteristicTX) {
            try {
                setFlaskCharacteristicTX();
            } catch (TagLostException e) {
                e.printStackTrace();
            }
        }

        if (commandQueue == 0) {
            delayedWriteCharacteristic(("tag." + value + "\n").getBytes(CharsetCompat.UTF_8));
            return;
        }

        Callbacks.add(() -> delayedWriteCharacteristic(("tag." + value + "\n").getBytes(CharsetCompat.UTF_8)));
    }

    public void uploadAmiiboFile(byte[] amiiboData, Amiibo amiibo) {
        currentProcess = Process.UPLOAD;
        delayedWriteTagCharacteristic("startTagUpload(" + amiiboData.length + ")");
        List<String> chunks = stringToPortions(Base64.encodeToString(
                amiiboData, Base64.NO_PADDING | Base64.NO_CLOSE | Base64.NO_WRAP
        ), 128);
        for(int i = 0; i < chunks.size(); i+=1) {
            final String chunk = chunks.get(i);
            delayedWriteTagCharacteristic(
                    "tagUploadChunk(\"" + chunk + "\")"
            );
        }
        String flaskTail = Integer.toString(Integer.parseInt(
                TagUtils.amiiboIdToHex(amiibo.getTail())
                        .substring(8, 16), 16), 32);
        String name = amiibo.name.length() > 18
                ? amiibo.name.substring(0, 18) : amiibo.name;
        delayedWriteTagCharacteristic("saveUploadedTag(\""
                + name + "|" + flaskTail + "|0\")");
        delayedWriteTagCharacteristic("uploadsComplete()");
        delayedWriteTagCharacteristic("getList()");
    }

    public void setActiveAmiibo(String name, String tail) {
        nameCompat = name;
        tailCompat = tail;
        currentProcess = Process.SELECT;
        delayedWriteTagCharacteristic("setTag(\"" + name + "|" + tail + "|0\")");
    }

    public void getActiveAmiibo() {
        currentProcess = Process.ACTIVE;
        delayedWriteTagCharacteristic("get()");
    }

    public void getDeviceAmiibo() {
        currentProcess = Process.GATHER;
        delayedWriteTagCharacteristic("getList()");
    }

    // https://stackoverflow.com/a/50022158/461982
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
