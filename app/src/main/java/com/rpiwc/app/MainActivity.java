package com.rpiwc.app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    BluetoothSocket mmSocket;

    Spinner devicesSpinner;
    Button refreshDevicesButton;
    EditText ssidTextView;
    EditText pskTextView;
    Button startButton;
    TextView messageTextView;

    final UUID uuid = UUID.fromString("815425a5-bfac-47bf-9321-c5ff980b5e11");
    final byte delimiter = 33;

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    refreshDevices();
                } else {
                    Toast.makeText(this, "Bluetooth not enabled.", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String[]> requestMultiplePermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allGranted = true;
                for (Boolean isGranted : permissions.values()) {
                    if (!isGranted) {
                        allGranted = false;
                        break;
                    }
                }

                if (allGranted) {
                    refreshDevices();
                } else {
                    Toast.makeText(this, "Bluetooth permissions are required.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ssidTextView = findViewById(R.id.ssid_text);
        pskTextView = findViewById(R.id.psk_text);
        messageTextView = findViewById(R.id.messages_text);
        devicesSpinner = findViewById(R.id.devices_spinner);
        refreshDevicesButton = findViewById(R.id.refresh_devices_button);
        startButton = findViewById(R.id.start_button);

        refreshDevicesButton.setOnClickListener(v -> requestPermissionsAndRefreshDevices());
        startButton.setOnClickListener(v -> {
            String ssid = ssidTextView.getText().toString();
            String psk = pskTextView.getText().toString();

            BluetoothDevice device = (BluetoothDevice) devicesSpinner.getSelectedItem();
            if (device != null) {
                new Thread(new workerThread(ssid, psk, device)).start();
            } else {
                Toast.makeText(this, "No device selected.", Toast.LENGTH_SHORT).show();
            }
        });

        requestPermissionsAndRefreshDevices();
    }

    private void requestPermissionsAndRefreshDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] requiredPermissions = new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN};
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requestMultiplePermissionsLauncher.launch(requiredPermissions);
            } else {
                refreshDevices();
            }
        } else {
            refreshDevices();
        }
    }

    private void refreshDevices() {
        DeviceAdapter adapter_devices = new DeviceAdapter(this, new ArrayList<>());
        devicesSpinner.setAdapter(adapter_devices);

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // This should not happen if permissions are requested correctly
                return;
            }
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBluetoothLauncher.launch(enableBluetooth);
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // This check was incorrect. It should check for BLUETOOTH_CONNECT for getBondedDevices.
                return;
            }
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if (!pairedDevices.isEmpty()) {
                for (BluetoothDevice device : pairedDevices) {
                    adapter_devices.add(device);
                }
            }
        }
    }

    final class workerThread implements Runnable {
        private final String ssid;
        private final String psk;
        private final BluetoothDevice device;

        public workerThread(String ssid, String psk, BluetoothDevice device) {
            this.ssid = ssid;
            this.psk = psk;
            this.device = device;
        }

        public void run() {
            clearOutput();
            writeOutput("Starting config update.");
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                writeOutput("Error: Bluetooth connect permission not granted.");
                return;
            }

            String deviceName = device.getName();
            if (deviceName == null) {
                deviceName = "Unknown Device";
            }
            writeOutput("Device: " + deviceName + " - " + device.getAddress());

            try {
                mmSocket = device.createRfcommSocketToServiceRecord(uuid);
                if (!mmSocket.isConnected()) {
                    mmSocket.connect();
                    Thread.sleep(1000);
                }

                writeOutput("Connected.");

                OutputStream mmOutputStream = mmSocket.getOutputStream();
                final InputStream mmInputStream = mmSocket.getInputStream();

                waitForResponse(mmInputStream);

                writeOutput("Sending SSID.");

                mmOutputStream.write(ssid.getBytes());
                mmOutputStream.flush();
                waitForResponse(mmInputStream);

                writeOutput("Sending PSK.");

                mmOutputStream.write(psk.getBytes());
                mmOutputStream.flush();
                waitForResponse(mmInputStream);

                mmSocket.close();

                writeOutput("Success.");

            } catch (Exception e) {
                Log.e(TAG, "Error in worker thread", e);
                writeOutput("Failed: " + e.getMessage());
            }

            writeOutput("Done.");
        }
    }

    private void writeOutput(final String text) {
        runOnUiThread(() -> messageTextView.append("\n" + text));
    }

    private void clearOutput() {
        runOnUiThread(() -> messageTextView.setText(""));
    }

    private void waitForResponse(InputStream mmInputStream) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(64);
        while (true) {
            int b = mmInputStream.read(); // This is a blocking call
            if (b == -1) {
                // End of stream has been reached unexpectedly.
                throw new IOException("Bluetooth connection closed.");
            }

            if (b == delimiter) {
                final String data = line.toString(StandardCharsets.US_ASCII.name());
                writeOutput("Received:" + data);
                return;
            }
            line.write(b);
        }
    }
}
