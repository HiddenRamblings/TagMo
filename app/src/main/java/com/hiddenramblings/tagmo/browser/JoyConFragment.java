package com.hiddenramblings.tagmo.browser;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.eightbit.bluetooth.BluetoothHandler;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.widget.Toasty;

import java.io.IOException;
import java.lang.reflect.Constructor;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class JoyConFragment extends DialogFragment implements
        BluetoothHandler.BluetoothListener {

    private BluetoothHandler bluetoothHandler;

    private String addressJoyCon;

    public static BluetoothSocket createL2CAPBluetoothSocket(
            BluetoothDevice device, String address, int psm) {
        return createBluetoothSocket(device, address, psm);
    }

    private static BluetoothSocket createBluetoothSocket(
            BluetoothDevice device, String address, int port) {
        Debug.Verbose(JoyConFragment.class, "Creating socket with " + address + ":" + port);
        /*
        android / bluetooth / BluetoothSocket.java

        static final int TYPE_L2CAP = 3;
        ...
        private BluetoothSocket(int type, int fd, boolean auth, boolean encrypt, String address,
        int port) throws IOException {
            this(type, fd, auth, encrypt, new BluetoothDevice(address), port, null);
        }
        */
        try {
            Constructor<BluetoothSocket> constructor = BluetoothSocket.class.getDeclaredConstructor(
                    int.class, int.class, boolean.class, boolean.class, String.class, int.class
            );
            constructor.setAccessible(true);
            // type, fd, auth, encrypt, address, port
            return constructor.newInstance(3, -1, false, false, address, port);
        } catch (Exception e) {
            try {
                Constructor<BluetoothSocket> constructor = BluetoothSocket.class.getDeclaredConstructor(
                        int.class, int.class, boolean.class, boolean.class,
                        BluetoothDevice.class, String.class, int.class
                );
                constructor.setAccessible(true);
                // type, fd, auth, encrypt, address, port
                return constructor.newInstance(3, -1, false, false, device, address, port);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_joy_con, container, false);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    public void delayedBluetoothEnable() {
        if (null != addressJoyCon) return;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            bluetoothHandler = null != bluetoothHandler ? bluetoothHandler : new BluetoothHandler(
                    requireContext(), requireActivity().getActivityResultRegistry(),
                    JoyConFragment.this
            );
            bluetoothHandler.requestPermissions(requireActivity());
        }, 100);
    }

    @Override
    public void onPermissionsFailed() {
        this.dismiss();
        new Toasty(requireActivity()).Long(R.string.fail_permissions);
    }

    @Override
    public void onAdapterMissing() {
        this.dismiss();
        new Toasty(requireActivity()).Long(R.string.fail_bluetooth_adapter);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onAdapterEnabled(BluetoothAdapter mBluetoothAdapter) {
        boolean hasProController = false;
        int[] gamepads = InputDevice.getDeviceIds();
        for (int gamepad: gamepads) {
            Debug.Verbose(JoyConFragment.class, "ID: " + gamepad + ", Name: "
                    + InputDevice.getDevice(gamepad).getName() + ", Descriptor: "
                    + InputDevice.getDevice(gamepad).getDescriptor());

            if (InputDevice.getDevice(gamepad).getName().equals("Nintendo Switch Pro Controller")) {
                hasProController = true;
                break;
            }
        }

        if (hasProController) {
            for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
                if (device.getName().equals("Pro Controller")) {
                    addressJoyCon = device.getAddress();
                    try {
                        BluetoothSocket mSocket = createL2CAPBluetoothSocket(
                                device, addressJoyCon, 0x11
                        );
                        if (mSocket != null) {
                            if (!mSocket.isConnected()) {
                                mSocket.connect();
                            }
                        }
                    } catch (IOException ex) {
                        JoyConFragment.this.dismiss();
                        new Toasty(requireActivity()).Short(R.string.joy_con_invalid);
                    }
                }
            }
        }
    }

    public static JoyConFragment newInstance() {
        return new JoyConFragment();
    }

    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireActivity());
        dialog.setNegativeButton(R.string.close, (dialogInterface, i) -> dialogInterface.cancel());
        Dialog joyConDialog = dialog.setView(getView()).create();
        joyConDialog.setOnShowListener(dialogInterface -> delayedBluetoothEnable());
        return joyConDialog;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
    }
}
