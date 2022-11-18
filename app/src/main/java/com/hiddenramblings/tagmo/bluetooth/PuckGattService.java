/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * Copyright (C) 2022 withgallantry @ BluupLabs
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

import androidx.annotation.RequiresApi;

import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.nfctech.NfcByte;

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
public class PuckGattService extends Service {

    private final Class<?> TAG = PuckGattService.class;

    private BluetoothGattListener listener;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    BluetoothGattCharacteristic mCharacteristicRX = null;
    BluetoothGattCharacteristic mCharacteristicTX = null;

    private int maxTransmissionUnit = 23;
    public final static UUID PuckNUS = UUID.fromString("78290001-d52e-473f-a9f4-f03da7c67dd1");
    private final static UUID PuckTX = UUID.fromString("78290002-d52e-473f-a9f4-f03da7c67dd1");
    private final static UUID PuckRX = UUID.fromString("78290003-d52e-473f-a9f4-f03da7c67dd1");

    // Command, Slot, Parameters
    private enum PUCK {
        INFO(0x01),
        READ(0x02),
        WRITE(0x03),
        SAVE(0x04),
        MOVE(0xFD),
        UART(0xFE),
        NFC(0xFF); // RESTART

        private final byte bytes;

        PUCK(int bytes) {
            this.bytes = (byte) bytes;
        }

        public byte getBytes() {
            return this.bytes;
        }
    }

    private int activeSlot = 0;
    private int selectSlot = 0;
    private int slotsCount = 0;

    public void setListener(BluetoothGattListener listener) {
        this.listener = listener;
    }

    private final ArrayList<Runnable> outgoingCallbacks = new ArrayList<>();

    private final Handler puckHandler = new Handler(Looper.getMainLooper());

    public interface BluetoothGattListener {
        void onServicesDiscovered();
        void onPuckActiveChanged(int slot);
        void onPuckListRetrieved(ArrayList<byte[]> slotData, int active);
        void onPuckFilesDownload(byte[] tagData);
        void onPuckProcessFinish();
        void onGattConnectionLost();
    }

    ArrayList<byte[]> puckArray = new ArrayList<>();
    byte[] readResponse = new byte[NfcByte.TAG_FILE_SIZE];

    private void getCharacteristicValue(BluetoothGattCharacteristic characteristic) {
        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            Debug.Verbose(TAG, getLogTag(characteristic.getUuid())
                    + " " + Arrays.toString(data));

            if (characteristic.getUuid().compareTo(PuckRX) == 0) {
                if (outgoingCallbacks.size() > 0) {
                    outgoingCallbacks.get(0).run();
                    outgoingCallbacks.remove(0);
                }
                if (data[0] == PUCK.INFO.getBytes()) {
                    if (data.length == 3) {
                        activeSlot = data[1];
                        slotsCount = data[2];
                        selectSlot = 0;
                        puckArray = new ArrayList<>();
                        sendCommand(new byte[]{PUCK.INFO.getBytes(), (byte) (selectSlot)}, null);
                    } else {
                        byte[] infoResponse = new byte[NfcByte.KEY_FILE_SIZE];
                        System.arraycopy(data, 2, infoResponse, 0, NfcByte.KEY_FILE_SIZE);
                        puckArray.add(infoResponse);
                        selectSlot += 1;
                        if (selectSlot == slotsCount || slotsCount == 0) {
                            if (null != listener)
                                listener.onPuckListRetrieved(puckArray, activeSlot);
                        } else {
                            getSlotSummary(selectSlot);
                        }
                    }
                } else if (data[0] == PUCK.READ.getBytes()) {
                    if (data[2] == 0) {
                        System.arraycopy(data, 4, readResponse, 0, data.length);
                    } else if (data[2] > 62 && data[2] < 126) {
                        System.arraycopy(data, 4, readResponse, 252, data.length);
                    } else {
                        System.arraycopy(data, 4, readResponse, 504, data.length);
                        if (null != listener) listener.onPuckFilesDownload(readResponse);
                        readResponse = new byte[NfcByte.TAG_FILE_SIZE];
                    }
                } else if (data[0] == PUCK.SAVE.getBytes()) {
                    if (null != listener) listener.onPuckProcessFinish();
                    getDeviceAmiibo();
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
            getCharacteristicValue(characteristic);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (null != listener) listener.onServicesDiscovered();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Debug.Verbose(TAG, "onMtuChange complete: " + mtu);
                maxTransmissionUnit = mtu;
            } else {
                Debug.Warn(TAG, "onMtuChange received: " + status);
            }
        }
    };

    public class LocalBinder extends Binder {
        public PuckGattService getService() {
            return PuckGattService.this;
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
                mCustomService.getCharacteristic(PuckRX);
        if (!mBluetoothGatt.readCharacteristic(mReadCharacteristic)) {
            for (BluetoothGattCharacteristic customRead : mCustomService.getCharacteristics()) {
                UUID customUUID = customRead.getUuid();
                /*get the read characteristic from the service*/
                if (customUUID.compareTo(PuckRX) == 0) {
                    Debug.Verbose(TAG, "GattReadCharacteristic: " + customUUID);
                    mReadCharacteristic = mCustomService.getCharacteristic(customUUID);
                    break;
                }
            }
        }
        return mReadCharacteristic;
    }

    public void setPuckCharacteristicRX() throws UnsupportedOperationException {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw new UnsupportedOperationException();
        }

        BluetoothGattService mCustomService = mBluetoothGatt.getService(PuckNUS);
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
                mCustomService.getCharacteristic(PuckTX);
        if (!mBluetoothGatt.writeCharacteristic(mWriteCharacteristic)) {
            for (BluetoothGattCharacteristic customWrite : mCustomService.getCharacteristics()) {
                UUID customUUID = customWrite.getUuid();
                /*get the write characteristic from the service*/
                if (customUUID.compareTo(PuckTX) == 0) {
                    Debug.Verbose(TAG, "GattWriteCharacteristic: " + customUUID);
                    mWriteCharacteristic = mCustomService.getCharacteristic(customUUID);
                    break;
                }
            }
        }
        return mWriteCharacteristic;
    }

    public void setPuckCharacteristicTX() throws UnsupportedOperationException {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            throw new UnsupportedOperationException();
        }

        BluetoothGattService mCustomService = mBluetoothGatt.getService(PuckNUS);
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

    private void delayedWriteCharacteristic(byte[] value) {
        List<byte[]> chunks = GattArray.byteToPortions(value, maxTransmissionUnit - 3);
        int commandQueue = outgoingCallbacks.size() + 1 + chunks.size();
        puckHandler.postDelayed(() -> {
            for (int i = 0; i < chunks.size(); i += 1) {
                final byte[] chunk = chunks.get(i);
                puckHandler.postDelayed(() -> {
                    mCharacteristicTX.setValue(chunk);
                    mCharacteristicTX.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    mBluetoothGatt.writeCharacteristic(mCharacteristicTX);
                }, (i + 1) * 30L);
            }
        }, commandQueue * 30L);
    }

    public void queueByteCharacteristic(byte[] value, int index) {
        if (null == mCharacteristicTX) {
            try {
                setPuckCharacteristicTX();
            } catch (UnsupportedOperationException e) {
                Debug.Warn(e);
            }
        }

        outgoingCallbacks.add(index, () -> delayedWriteCharacteristic(value));

        if (outgoingCallbacks.size() == 1) {
            outgoingCallbacks.get(0).run();
            outgoingCallbacks.remove(0);
        }
    }

    public void delayedByteCharacteric(byte[] value) {
        queueByteCharacteristic(value, outgoingCallbacks.size());
    }

    private void sendCommand(byte[] params, byte[] data) {
        if (null != data) {
            byte[] command = new byte[params.length + data.length];
            System.arraycopy(params, 0, command, 0, params.length);
            System.arraycopy(data, 0, command, params.length, data.length);
            delayedByteCharacteric(command);
        } else {
            delayedByteCharacteric(params);
        }
    }

    public void getDeviceAmiibo() {
        sendCommand(new byte[] { PUCK.INFO.getBytes() }, null);
    }

    public void getSlotSummary(int slot) {
        sendCommand(new byte[] { PUCK.INFO.getBytes(), (byte) slot }, null);
    }

    public void uploadSlotAmiibo(byte[] tagData, int slot) {
        for (int i = 0; i < tagData.length % 16; i ++) {
            byte[] data = new byte[16];
            System.arraycopy(tagData, i * 16, data, 0, data.length);
            sendCommand(new byte[] { PUCK.WRITE.getBytes(), (byte) slot, (byte) (i * 4) }, data);
        }
        sendCommand(
                new byte[] { PUCK.SAVE.getBytes(), (byte) slot },
                activeSlot == slot ? new byte[] { PUCK.NFC.getBytes() } : null
        );
    }

    private void downloadSlotData(int slot) {
        sendCommand(new byte[] { PUCK.READ.getBytes(), (byte) slot, 0x00, 0x3F }, null);
        sendCommand(new byte[] { PUCK.READ.getBytes(), (byte) slot, 0x3F, 0x3F }, null);
        sendCommand(new byte[] { PUCK.READ.getBytes(), (byte) slot, 0x7E, 0x11 }, null);
    }

    public void setActiveSlot(int slot) {
        sendCommand(new byte[] { PUCK.NFC.getBytes(), (byte) slot }, null);
        activeSlot = slot;
        if (null != listener) listener.onPuckActiveChanged(activeSlot);
    }

    private String getLogTag(UUID uuid) {
        if (uuid.compareTo(PuckTX) == 0) {
            return "PuckTX";
        } else if (uuid.compareTo(PuckRX) == 0) {
            return "PuckRX";
        } else if (uuid.compareTo(PuckNUS) == 0) {
            return "PuckNUS";
        } else {
            return uuid.toString();
        }
    }
}
