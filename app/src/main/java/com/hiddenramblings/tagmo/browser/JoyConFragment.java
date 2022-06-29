package com.hiddenramblings.tagmo.browser;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.nfc.TagLostException;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.widget.Toasty;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class JoyConFragment extends Fragment {

    private JoyConGattService serviceJoyCon;
    private String addressJoyCon;

    protected ServiceConnection mServerConn = new ServiceConnection() {
        boolean isServiceDiscovered = false;

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            JoyConGattService.LocalBinder localBinder = (JoyConGattService.LocalBinder) binder;
            serviceJoyCon = localBinder.getService();
            if (serviceJoyCon.initialize()) {
                if (serviceJoyCon.connect(addressJoyCon)) {
                    serviceJoyCon.setListener(new JoyConGattService.BluetoothGattListener() {
                        @Override
                        public void onServicesDiscovered() {
                            isServiceDiscovered = true;
                            new Toasty(requireActivity())
                                    .Short("SERVICES");
                            try {
                                serviceJoyCon.setFlaskCharacteristicRX();
                            } catch (TagLostException tle) {
                                new Toasty(requireActivity())
                                        .Short(R.string.flask_invalid);
                            }
                        }

                        @Override
                        public void onGattConnectionLost() {
                            addressJoyCon = null;
                            stopJoyConService();
                        }
                    });
                } else {
                    stopJoyConService();
                    new Toasty(requireActivity()).Short(R.string.flask_invalid);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            disconnectJoyCon();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_joy_con, container, false);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        boolean hasProController = false;
        int[] gamepads = InputDevice.getDeviceIds();
        for (int gamepad: gamepads) {
            Log.d("InputDevice", "ID: " + gamepad + ", Name: "
                    + InputDevice.getDevice(gamepad).getName() + ", Descriptor: "
                    + InputDevice.getDevice(gamepad).getDescriptor());

            if (InputDevice.getDevice(gamepad).getName().equals("Nintendo Switch Pro Controller")) {
                hasProController = true;
                break;
            }
        }

        if (hasProController) {
            for (BluetoothDevice device : getBluetoothAdapter().getBondedDevices()) {
                if (device.getName().equals("Pro Controller")) {
                    addressJoyCon = device.getAddress();
                    Intent service = new Intent(requireContext(), JoyConGattService.class);
                    requireContext().startService(service);
                    requireContext().bindService(service, mServerConn, Context.BIND_AUTO_CREATE);
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothAdapter mBluetoothAdapter;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mBluetoothAdapter = ((BluetoothManager) requireContext()
                    .getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
            if (null != mBluetoothAdapter) {
                if (!mBluetoothAdapter.isEnabled()) mBluetoothAdapter.enable();
                return mBluetoothAdapter;
            }
        } else {
            //noinspection deprecation
            return BluetoothAdapter.getDefaultAdapter();
        }
        return null;
    }

    public void disconnectJoyCon() {
        if (null != serviceJoyCon)
            serviceJoyCon.disconnect();
        else
            stopJoyConService();
    }

    public void stopJoyConService() {
        try {
            requireContext().unbindService(mServerConn);
            requireContext().stopService(new Intent(requireContext(), JoyConGattService.class));
        } catch (IllegalArgumentException ignored) { }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnectJoyCon();
    }
}