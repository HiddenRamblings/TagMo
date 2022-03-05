package com.hiddenramblings.tagmo;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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

public class FlaskActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED) {
                BluetoothAdapter bluetoothAdapter = ((BluetoothManager)
                        getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
                if (!bluetoothAdapter.isEnabled()) {
                    bluetoothAdapter.enable();
                }
                startFlaskService();
            } else {
                onRequestBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
                BluetoothAdapter bluetoothAdapter = ((BluetoothManager)
                        getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
                if (bluetoothAdapter.isEnabled()) {
                    startFlaskService();
                } else {
                    onRequestOldBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
                }
            } else {
                onRequestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        } else {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (!mBluetoothAdapter.isEnabled()){
                mBluetoothAdapter.enable();
            }
            startFlaskService();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    ActivityResultLauncher<String> onRequestBluetooth = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isEnabled -> {
        BluetoothAdapter bluetoothAdapter = ((BluetoothManager)
                getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        }
        startFlaskService();
    });

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    ActivityResultLauncher<Intent> onRequestOldBluetooth = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        BluetoothAdapter bluetoothAdapter = ((BluetoothManager)
                getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (bluetoothAdapter.isEnabled()) {
            startFlaskService();
        }
    });

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    ActivityResultLauncher<String> onRequestLocation = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isEnabled -> {
        BluetoothAdapter bluetoothAdapter = ((BluetoothManager)
                getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (bluetoothAdapter.isEnabled()) {
            startFlaskService();
        } else {
            onRequestOldBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }
    });

    protected ServiceConnection mServerConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d("Flask", "onServiceConnected");
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
        stopFlaskService();
    }
}
