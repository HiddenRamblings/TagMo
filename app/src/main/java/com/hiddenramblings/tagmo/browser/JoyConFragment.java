package com.hiddenramblings.tagmo.browser;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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

import com.hiddenramblings.tagmo.JoyCon;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.eightbit.bluetooth.BluetoothHandler;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.widget.Toasty;
import com.mumumusuc.libjoycon.BluetoothHelper;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class JoyConFragment extends DialogFragment implements
        BluetoothHandler.BluetoothListener {

    private BluetoothHandler bluetoothHandler;

    private String addressJoyCon;

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

    private byte getPlayer(int player) {
        return (byte) ((0x1 << (player)) - 1);
    }

    private byte getFlash(int flash) {
        return (byte) ((0x1 << (flash)) - 1);
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
                    BluetoothHelper bluetoothHelper = new BluetoothHelper();
                    bluetoothHelper.register(requireContext(), (name, address, state) -> {

                    });
                    bluetoothHelper.connectL2cap(device);
                    JoyCon proController = new JoyCon(bluetoothHelper, device);
                    proController.enableRumble(true);
                    proController.setPlayer(getPlayer(1), getFlash(4));
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
