package com.rpiwc.app;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import java.util.List;

public class DeviceAdapter extends ArrayAdapter<BluetoothDevice> {
    private final LayoutInflater inflater;

    public DeviceAdapter(Context context, List<BluetoothDevice> devices) {
        super(context, android.R.layout.simple_spinner_item, devices);
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        inflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        TextView textView = view.findViewById(android.R.id.text1);
        BluetoothDevice device = getItem(position);
        if (device != null) {
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                textView.setText("Permission needed");
                return view;
            }
            String name = device.getName() != null ? device.getName() : "Unknown Device";
            textView.setText(name);
        }
        return view;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        View view = super.getDropDownView(position, convertView, parent);
        TextView textView = view.findViewById(android.R.id.text1);
        BluetoothDevice device = getItem(position);
        if (device != null) {
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                textView.setText("Permission needed");
                return view;
            }
            String name = device.getName() != null ? device.getName() : "Unknown Device";
            String address = device.getAddress();
            textView.setText(name + "\n" + address);
        }
        return view;
    }
}