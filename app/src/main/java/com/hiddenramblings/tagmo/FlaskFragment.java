package com.hiddenramblings.tagmo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.nfc.TagLostException;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.widget.Toasty;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("MissingPermission")
public class FlaskFragment extends Fragment {

    private boolean hasCheckedPermissions = false;
    private ViewGroup fragmentView;
    private LinearLayout deviceList;
    private ProgressBar progressBar;
    private Snackbar statusBar;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothAdapter.LeScanCallback scanCallback;
    private BluetoothLeService flaskService;
    private String flaskAddress;

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

    ActivityResultLauncher<String[]> onRequestBluetoothS = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> { boolean isBluetoothAvailable = true;
        for (Map.Entry<String,Boolean> entry : permissions.entrySet()) {
            if (!entry.getValue()) isBluetoothAvailable = false;
        }
        if (isBluetoothAvailable) {
            mBluetoothAdapter = getBluetoothAdapter();
            if (null != mBluetoothAdapter) {
                selectBluetoothDevice();
            } else {
                new Toasty(requireActivity()).Long(R.string.flask_bluetooth);
                ((BrowserActivity) requireActivity()).showBrowserPage();
            }
        } else {
            new Toasty(requireActivity()).Long(R.string.flask_bluetooth);
            ((BrowserActivity) requireActivity()).showBrowserPage();
        }
    });
    ActivityResultLauncher<Intent> onRequestBluetooth = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        mBluetoothAdapter = getBluetoothAdapter();
        if (null != mBluetoothAdapter) {
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
            if (null != mBluetoothAdapter)
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
            Log.d("FlaskService", "onServiceConnected");
            BluetoothLeService.LocalBinder localBinder = (BluetoothLeService.LocalBinder) binder;
            flaskService = localBinder.getService();
            if (flaskService.initialize()) {
                if (flaskService.connect(flaskAddress)) {
                    flaskService.setListener(new BluetoothLeService.BluetoothGattListener() {
                        boolean isServiceDiscovered = false;
                        @Override
                        public void onServicesDiscovered() {
                            isServiceDiscovered = true;
                            try {
                                flaskService.readCustomCharacteristic();
                                dismissConnectionNotice();
                                new Toasty(requireActivity()).Short(R.string.flask_connected);
                            } catch (TagLostException tle) {
                                stopFlaskService();
                                new Toasty(requireActivity()).Short(R.string.flask_invalid);
                            }
                        }

                        @Override
                        public void onServicesDisconnect() {
                            if (!isServiceDiscovered) {
                                stopFlaskService();
                                new Toasty(requireActivity()).Short(R.string.flask_missing);
                            }
                        }
                    });
                } else {
                    stopFlaskService();
                    new Toasty(requireActivity()).Short(R.string.flask_invalid);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("FlaskService", "onServiceDisconnected");
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_flask, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        fragmentView = (ViewGroup) view;
        deviceList = view.findViewById(R.id.bluetooth_devices);
        progressBar = view.findViewById(R.id.scanner_progress);
    }

    private void verifyPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
                activateBluetooth();
            } else {
                final String[] PERMISSIONS_LOCATION = {
                        Manifest.permission.ACCESS_FINE_LOCATION,
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
            if (null != mBluetoothAdapter)
                selectBluetoothDevice();
            else
                onRequestBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
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
            if (null != mBluetoothAdapter)
                selectBluetoothDevice();
            else
                onRequestBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }
    }

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

    private void scanBluetoothServices() {
        progressBar.setVisibility(View.VISIBLE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
            ParcelUuid FlaskUUID = new ParcelUuid(BluetoothLeService.FlaskNUS);
            ScanFilter filter = new ScanFilter.Builder().setServiceUuid(FlaskUUID).build();
            ScanSettings settings = new ScanSettings.Builder().build();
            ScanCallback callback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    flaskAddress = result.getDevice().getAddress();
                    dismissFlaskDiscovery();
                    showConnectionNotice();
                    startFlaskService();
                }
            };
            scanner.startScan(Collections.singletonList(filter), settings, callback);
        } else {
            scanCallback = (bluetoothDevice, i, bytes) -> {
                flaskAddress = bluetoothDevice.getAddress();
                dismissFlaskDiscovery();
                showConnectionNotice();
                startFlaskService();
            };
            mBluetoothAdapter.startLeScan(new UUID[]{ BluetoothLeService.FlaskNUS }, scanCallback);
        }
    }

    ActivityResultLauncher<Intent> onRequestPairing = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> scanBluetoothServices());

    private void selectBluetoothDevice() {
        deviceList.removeAllViews();

        for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
            if (device.getName().toLowerCase(Locale.ROOT).startsWith("flask")) {
                new Toasty(requireActivity()).Long(R.string.flask_paired);
                dismissFlaskDiscovery();
                try {
                    onRequestPairing.launch(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                } catch (ActivityNotFoundException anf) {
                    scanBluetoothServices();
                }
            }
        }
        scanBluetoothServices();
    }

    private void showConnectionNotice() {
        statusBar = new IconifiedSnackbar(requireActivity(), fragmentView).buildSnackbar(
                R.string.flask_located, R.drawable.ic_bluup_flask_24dp, Snackbar.LENGTH_INDEFINITE
        );
        statusBar.show();
    }

    private void dismissConnectionNotice() {
        if (null != statusBar && statusBar.isShown()) statusBar.dismiss();
    }

    public void startFlaskService() {
        Intent service = new Intent(requireActivity(), BluetoothLeService.class);
        requireActivity().startService(service);
        requireActivity().bindService(service, mServerConn, Context.BIND_AUTO_CREATE);
    }

    public void stopFlaskService() {
        dismissConnectionNotice();
        flaskService.disconnect();
        requireActivity().unbindService(mServerConn);
        requireActivity().stopService(new Intent(requireActivity(), BluetoothLeService.class));
    }

    private void dismissFlaskDiscovery() {
        if (null != mBluetoothAdapter) {
            progressBar.setVisibility(View.INVISIBLE);
            if (null != scanCallback)
                mBluetoothAdapter.stopLeScan(scanCallback);
        }
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager)
                requireContext().getSystemService(Context.ACTIVITY_SERVICE);
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
        dismissFlaskDiscovery();
        if (isServiceRunning())
            stopFlaskService();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!hasCheckedPermissions) {
            hasCheckedPermissions = true;
            verifyPermissions();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        dismissFlaskDiscovery();
    }
}
