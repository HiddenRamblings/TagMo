package com.hiddenramblings.tagmo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.nfc.TagLostException;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.hiddenramblings.tagmo.eightbit.charset.CharsetCompat;
import com.hiddenramblings.tagmo.widget.Toasty;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("MissingPermission")
public class FlaskFragment extends Fragment {

    private boolean isPermissionPrompt = false;
    private View fragmentView;
    private BluetoothAdapter mBluetoothAdapter;
    private String flaskAddress;
    private BroadcastReceiver pairingRequest;
    @RequiresApi(api = Build.VERSION_CODES.Q)
    ActivityResultLauncher<String[]> onRequestLocationQ = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> { boolean isLocationAvailable = true;
        for (Map.Entry<String,Boolean> entry : permissions.entrySet()) {
            if (entry.getKey().equals(Manifest.permission.ACCESS_FINE_LOCATION)
                    && !entry.getValue()) isLocationAvailable = false;
        }
        if (isLocationAvailable) {
            activateBluetooth();
        } else {
            new Toasty(requireActivity()).Long(R.string.flask_permissions);
            ((BrowserActivity) requireActivity()).showBrowserPage();
        }
    });
    private BluetoothLeService flaskService;
    ActivityResultLauncher<String[]> onRequestBluetoothS = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> { boolean isBluetoothAvailable = true;
        for (Map.Entry<String,Boolean> entry : permissions.entrySet()) {
            if (!entry.getValue()) isBluetoothAvailable = false;
        }
        if (isBluetoothAvailable) {
            mBluetoothAdapter = getBluetoothAdapter();
            if (!mBluetoothAdapter.isEnabled()) mBluetoothAdapter.enable();
            selectBluetoothDevice();
        } else {
            new Toasty(requireActivity()).Long(R.string.flask_bluetooth);
            ((BrowserActivity) requireActivity()).showBrowserPage();
        }
    });
    ActivityResultLauncher<Intent> onRequestBluetooth = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        mBluetoothAdapter = getBluetoothAdapter();
        if (mBluetoothAdapter.isEnabled()) {
            selectBluetoothDevice();
        } else {
            new Toasty(requireActivity()).Long(R.string.flask_bluetooth);
            ((BrowserActivity) requireActivity()).showBrowserPage();
        }
    });
    ActivityResultLauncher<String[]> onRequestLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> { boolean isLocationAvailable = true;
        for (Map.Entry<String,Boolean> entry : permissions.entrySet()) {
            if (!entry.getValue()) isLocationAvailable = false;
        }
        if (isLocationAvailable) {
            mBluetoothAdapter = getBluetoothAdapter();
            if (mBluetoothAdapter.isEnabled())
                selectBluetoothDevice();
            else
                onRequestBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            new Toasty(requireActivity()).Long(R.string.flask_permissions);
            ((BrowserActivity) requireActivity()).showBrowserPage();
        }
    });
    protected ServiceConnection mServerConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d("Flask", "onServiceConnected");
            BluetoothLeService.LocalBinder localBinder = (BluetoothLeService.LocalBinder) binder;
            flaskService = localBinder.getService();
            if (flaskService.initialize()) {
                if (flaskService.connect(flaskAddress)) {
                    try {
                        flaskService.readCustomCharacteristic();
                    } catch (TagLostException tle) {
                        stopFlaskService();
                        new Toasty(requireActivity()).Long(R.string.flask_invalid);
                    }
                } else {
                    stopFlaskService();
                    new Toasty(requireActivity()).Long(R.string.flask_invalid);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("Flask", "onServiceDisconnected");
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_flask, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        fragmentView = view;
        verifyPermissions();
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (!isPermissionPrompt) verifyPermissions();
    }

    private void verifyPermissions() {
        isPermissionPrompt = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
                activateBluetooth();
            } else {
                final String[] PERMISSIONS_LOCATION = {
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                };
                onRequestLocationQ.launch(PERMISSIONS_LOCATION);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final String[] PERMISSIONS_LOCATION = {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
            onRequestLocation.launch(PERMISSIONS_LOCATION);
        } else {
            mBluetoothAdapter = getBluetoothAdapter();
            if (!mBluetoothAdapter.isEnabled()) mBluetoothAdapter.enable();
            selectBluetoothDevice();
        }
    }

    private void activateBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            final String[] PERMISSIONS_BLUETOOTH = {
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            };
            onRequestBluetoothS.launch(PERMISSIONS_BLUETOOTH);
        } else {
            mBluetoothAdapter = getBluetoothAdapter();
            if (mBluetoothAdapter.isEnabled())
                selectBluetoothDevice();
            else
                onRequestBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }
    }

    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothAdapter mBluetoothAdapter;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1)
            mBluetoothAdapter = ((BluetoothManager)
                    requireContext().getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        else
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter;
    }

    private void selectBluetoothDevice() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        pairingRequest = new BroadcastReceiver() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("android.bluetooth.device.action.PAIRING_REQUEST")) {
                    try {
                        BluetoothDevice device = intent
                                .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        device.setPin((String.valueOf(intent.getIntExtra(
                                "android.bluetooth.device.extra.PAIRING_KEY", 0
                        ))).getBytes(CharsetCompat.UTF_8));
                        device.setPairingConfirmation(true);
                        if (device.getName().toLowerCase(Locale.ROOT).startsWith("flask")) {
                            flaskAddress = device.getAddress();
                            dismissFlaskDiscovery();
                            startFlaskService();
                        } else {
                            View bonded = getLayoutInflater().inflate(R.layout.bluetooth_device, null);
                            bonded.setOnClickListener(view1 -> {
                                flaskAddress = device.getAddress();
                                dismissFlaskDiscovery();
                                startFlaskService();
                            });
                            ((TextView) bonded.findViewById(R.id.bluetooth_text)).setText(device.getName());
                            ((LinearLayout) fragmentView.findViewById(R.id.bluetooth_devices)).addView(bonded);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        new Toasty(requireActivity()).Long(R.string.flask_failed);
                    }
                }
            }
        };
        for (BluetoothDevice bt : pairedDevices) {
            if (bt.getName().toLowerCase(Locale.ROOT).startsWith("flask")) {
                flaskAddress = bt.getAddress();
                break;
            } else {
                View bonded = getLayoutInflater().inflate(R.layout.bluetooth_device, null);
                bonded.setOnClickListener(view1 -> {
                    flaskAddress = bt.getAddress();
                    dismissFlaskDiscovery();
                    startFlaskService();
                });
                ((TextView) bonded.findViewById(R.id.bluetooth_text)).setText(bt.getName());
                ((LinearLayout) fragmentView.findViewById(R.id.bluetooth_devices)).addView(bonded);
            }
        }
        if (null != flaskAddress) {
            startFlaskService();
        } else {
            View device = getLayoutInflater().inflate(R.layout.bluetooth_device, null);
            device.setOnClickListener(view1 -> {
                IntentFilter filter = new IntentFilter(
                        "android.bluetooth.device.action.PAIRING_REQUEST"
                );
                requireActivity().registerReceiver(pairingRequest, filter);
                if (mBluetoothAdapter.isDiscovering())
                    mBluetoothAdapter.cancelDiscovery();
                mBluetoothAdapter.startDiscovery();
            });
            ((TextView) device.findViewById(R.id.bluetooth_text)).setText(R.string.bluetooth_pair);
            ((LinearLayout) fragmentView.findViewById(R.id.bluetooth_devices)).addView(device, 0);
        }
    }

    public void startFlaskService() {
        Intent service = new Intent(requireActivity(), BluetoothLeService.class);
        requireActivity().startService(service);
        requireActivity().bindService(service, mServerConn, Context.BIND_AUTO_CREATE);
    }

    public void stopFlaskService() {
        flaskService.disconnect();
        requireActivity().unbindService(mServerConn);
        requireActivity().stopService(new Intent(requireActivity(), BluetoothLeService.class));
    }

    private void dismissFlaskDiscovery() {
        try {
            requireActivity().unregisterReceiver(pairingRequest);
        } catch (Exception ignored) { }
        if (null != mBluetoothAdapter) {
            if (mBluetoothAdapter.isDiscovering())
                mBluetoothAdapter.cancelDiscovery();
            if (isServiceRunning())
                stopFlaskService();
        }
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service
                : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (BluetoothLeService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isPermissionPrompt = false;
        dismissFlaskDiscovery();
    }
}
