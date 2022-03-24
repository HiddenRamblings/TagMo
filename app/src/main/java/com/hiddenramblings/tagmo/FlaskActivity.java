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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.hiddenramblings.tagmo.eightbit.charset.CharsetCompat;
import com.hiddenramblings.tagmo.widget.Toasty;

import java.util.Locale;
import java.util.Set;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("MissingPermission")
public class FlaskActivity extends AppCompatActivity {

    private BluetoothLeService leService;
    private String flaskAddress;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED) {
                BluetoothAdapter bluetoothAdapter = ((BluetoothManager)
                        getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
                if (!bluetoothAdapter.isEnabled()) bluetoothAdapter.enable();
                selectBluetoothDevice();
            } else {
                onRequestBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
                BluetoothAdapter bluetoothAdapter = ((BluetoothManager)
                        getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
                if (bluetoothAdapter.isEnabled())
                    selectBluetoothDevice();
                else
                    onRequestOldBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            } else {
                onRequestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        } else {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (!mBluetoothAdapter.isEnabled()) mBluetoothAdapter.enable();
            selectBluetoothDevice();
        }
    }

    ActivityResultLauncher<String> onRequestBluetooth = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isEnabled -> {
        BluetoothAdapter bluetoothAdapter = ((BluetoothManager)
                getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (!bluetoothAdapter.isEnabled()) bluetoothAdapter.enable();
        selectBluetoothDevice();
    });

    ActivityResultLauncher<Intent> onRequestOldBluetooth = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        BluetoothAdapter bluetoothAdapter = ((BluetoothManager)
                getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (bluetoothAdapter.isEnabled())
            selectBluetoothDevice();
    });

    ActivityResultLauncher<String> onRequestLocation = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isEnabled -> {
        BluetoothAdapter bluetoothAdapter = ((BluetoothManager)
                getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (bluetoothAdapter.isEnabled())
            selectBluetoothDevice();
        else
            onRequestOldBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
    });

    private void selectBluetoothDevice() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        boolean hasFoundFlask = false;
        for (BluetoothDevice bt : pairedDevices) {
            if (bt.getName().toLowerCase(Locale.ROOT).startsWith("flask")) {
                hasFoundFlask = true;
                flaskAddress = bt.getAddress();
                break;
            }
        }
        if (hasFoundFlask) {
            startFlaskService();
        } else {
            IntentFilter filter = new IntentFilter(
                    "android.bluetooth.device.action.PAIRING_REQUEST");
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
            leService = localBinder.getService();
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
