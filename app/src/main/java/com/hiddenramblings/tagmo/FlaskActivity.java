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
import androidx.core.content.ContextCompat;

import com.hiddenramblings.tagmo.eightbit.charset.CharsetCompat;
import com.hiddenramblings.tagmo.widget.Toasty;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("MissingPermission")
public class FlaskActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;
    private String flaskAddress;
    private BroadcastReceiver pairingRequest;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flask);

        if (null != getSupportActionBar())
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
                activateBluetooth();
            } else {
                final String[] PERMISSIONS_LOCATION = {
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
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
            new Toasty(FlaskActivity.this).Long(R.string.flask_not_found);
            finish();
        }
    });

    ActivityResultLauncher<Intent> onRequestBluetooth = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        mBluetoothAdapter = getBluetoothAdapter();
        if (mBluetoothAdapter.isEnabled()) {
            selectBluetoothDevice();
        } else {
            new Toasty(FlaskActivity.this).Long(R.string.flask_not_found);
            finish();
        }
    });

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
            mBluetoothAdapter = getBluetoothAdapter();
            if (mBluetoothAdapter.isEnabled())
                selectBluetoothDevice();
            else
                onRequestBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            new Toasty(FlaskActivity.this).Long(R.string.flask_not_found);
            finish();
        }
    });

    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothAdapter mBluetoothAdapter;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1)
            mBluetoothAdapter = ((BluetoothManager)
                    getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
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

        for (BluetoothDevice bt : pairedDevices) {
            if (bt.getName().toLowerCase(Locale.ROOT).startsWith("flask")) {
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
        if (null != flaskAddress) {
            startFlaskService();
        } else {
            IntentFilter filter = new IntentFilter(
                    "android.bluetooth.device.action.PAIRING_REQUEST"
            );
           registerReceiver(pairingRequest, filter);
           if (mBluetoothAdapter.isDiscovering())
               mBluetoothAdapter.cancelDiscovery();
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
        startService(service);
        bindService(service, mServerConn, Context.BIND_AUTO_CREATE);
    }

    public void stopFlaskService() {
        unbindService(mServerConn);
        stopService(new Intent(this, BluetoothLeService.class));
    }

    private void dismissFlaskActivity() {
        try {
            unregisterReceiver(pairingRequest);
        } catch (Exception ignored) { }
        if (null != mBluetoothAdapter) {
            if (mBluetoothAdapter.isDiscovering())
                mBluetoothAdapter.cancelDiscovery();
            if (isServiceRunning())
                stopFlaskService();
        }
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service
                : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (BluetoothLeService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        dismissFlaskActivity();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissFlaskActivity();
    }
}
