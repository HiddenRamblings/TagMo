package com.hiddenramblings.tagmo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.hiddenramblings.tagmo.eightbit.charset.CharsetCompat;
import com.hiddenramblings.tagmo.widget.Toasty;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("MissingPermission")
public class FlaskActivity extends AppCompatActivity {

    private String flaskAddress;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flask);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            onRequestLocationQ.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final String[] PERMISSIONS_LOCATION = {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
            onRequestLocation.launch(PERMISSIONS_LOCATION);
        } else {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (!mBluetoothAdapter.isEnabled()) mBluetoothAdapter.enable();
            selectBluetoothDevice();
        }
    }

    ActivityResultLauncher<String[]> onRequestBluetoothS = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> { boolean isBluetoothAvailable = true;
        for (Map.Entry<String,Boolean> entry : permissions.entrySet()) {
            if (!entry.getValue()) isBluetoothAvailable = false;
        }
        if (isBluetoothAvailable) {
            BluetoothAdapter bluetoothAdapter = ((BluetoothManager)
                    getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
            if (!bluetoothAdapter.isEnabled()) bluetoothAdapter.enable();
            selectBluetoothDevice();
        } else {
            new Toasty(FlaskActivity.this).Long(R.string.flask_not_found);
            finish();
        }
    });

    ActivityResultLauncher<Intent> onRequestBluetooth = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        BluetoothAdapter bluetoothAdapter = ((BluetoothManager)
                getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (bluetoothAdapter.isEnabled()) {
            selectBluetoothDevice();
        } else {
            new Toasty(FlaskActivity.this).Long(R.string.flask_not_found);
            finish();
        }
    });

    @RequiresApi(api = Build.VERSION_CODES.Q)
    ActivityResultLauncher<String> onRequestLocationQ = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
        if (isGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                final String[] PERMISSIONS_BLUETOOTH = {
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                };
                onRequestBluetoothS.launch(PERMISSIONS_BLUETOOTH);
            } else {
                BluetoothAdapter bluetoothAdapter = ((BluetoothManager)
                        getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
                if (bluetoothAdapter.isEnabled())
                    selectBluetoothDevice();
                else
                    onRequestBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            }
        } else {
            new Toasty(FlaskActivity.this).Long(R.string.flask_not_found);
            finish();
        }
    });

    ActivityResultLauncher<String[]> onRequestLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> { boolean isLocationAvailable = true;
        for (Map.Entry<String,Boolean> entry : permissions.entrySet()) {
            if (!entry.getValue()) isLocationAvailable = false;
        }
        if (isLocationAvailable) {
            BluetoothAdapter bluetoothAdapter = ((BluetoothManager)
                    getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
            if (bluetoothAdapter.isEnabled())
                selectBluetoothDevice();
            else
                onRequestBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            new Toasty(FlaskActivity.this).Long(R.string.flask_not_found);
            finish();
        }
    });

    private void selectBluetoothDevice() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        BroadcastReceiver pairingRequest = new BroadcastReceiver() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("android.bluetooth.device.action.PAIRING_REQUEST")) {
                    try {
                        BluetoothDevice device = intent
                                .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (!device.getName().toLowerCase(Locale.ROOT).startsWith("flask")) {
                            unregisterReceiver(this);
                            if (mBluetoothAdapter.isDiscovering())
                                mBluetoothAdapter.cancelDiscovery();
                            new Toasty(FlaskActivity.this).Long(R.string.flask_not_found);
                            finish();
                        }
                        device.setPin((String.valueOf(intent.getIntExtra(
                                "android.bluetooth.device.extra.PAIRING_KEY", 0
                        ))).getBytes(CharsetCompat.UTF_8));
                        device.setPairingConfirmation(true);
                        flaskAddress = device.getAddress();
                        unregisterReceiver(this);
                        if (mBluetoothAdapter.isDiscovering())
                            mBluetoothAdapter.cancelDiscovery();
                        startFlaskService();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        unregisterReceiver(this);
                        if (mBluetoothAdapter.isDiscovering())
                            mBluetoothAdapter.cancelDiscovery();
                        new Toasty(FlaskActivity.this).Long(R.string.flask_not_found);
                        finish();
                    }
                }
            }
        };

        boolean hasFoundFlask = false;
        for (BluetoothDevice bt : pairedDevices) {
            if (bt.getName().toLowerCase(Locale.ROOT).startsWith("flask")) {
                hasFoundFlask = true;
                flaskAddress = bt.getAddress();
                break;
            } else {
                View view = getLayoutInflater().inflate(R.layout.bluetooth_device, null);
                view.setOnClickListener(view1 -> {
                    try {
                        unregisterReceiver(pairingRequest);
                    } catch (Exception ignored) { }
                    if (mBluetoothAdapter.isDiscovering())
                        mBluetoothAdapter.cancelDiscovery();
                    flaskAddress = bt.getAddress();
                    startFlaskService();
                });
                ((TextView) view.findViewById(R.id.bluetooth_text)).setText(bt.getName());
                ((LinearLayout) findViewById(R.id.bluetooth_devices)).addView(view);
            }
        }
        if (hasFoundFlask) {
            startFlaskService();
        } else {
            IntentFilter filter = new IntentFilter(
                    "android.bluetooth.device.action.PAIRING_REQUEST"
            );
           registerReceiver(pairingRequest, filter);
           if (mBluetoothAdapter.isDiscovering()) mBluetoothAdapter.cancelDiscovery();
           mBluetoothAdapter.startDiscovery();
        }
    }

    protected ServiceConnection mServerConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d("Flask", "onServiceConnected");
            BluetoothLeService.LocalBinder localBinder = (BluetoothLeService.LocalBinder) binder;
            BluetoothLeService leService = localBinder.getService();
            if (leService.initialize()) leService.connect(flaskAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("Flask", "onServiceDisconnected");
        }
    };

    public void startFlaskService() {
        Intent service = new Intent(this, BluetoothLeService.class);
        bindService(service, mServerConn, Context.BIND_AUTO_CREATE);
        startService(service);
    }

    public void stopFlaskService() {
        stopService(new Intent(this, BluetoothLeService.class));
        unbindService(mServerConn);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter.isDiscovering()) mBluetoothAdapter.cancelDiscovery();
        stopFlaskService();
    }
}
