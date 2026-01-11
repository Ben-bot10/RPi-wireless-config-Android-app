#!/usr/bin/env python3

import os
import subprocess
import time
import re
from bluetooth import *

# Path to wpa_supplicant
wpa_supplicant_conf = "/etc/wpa_supplicant/wpa_supplicant.conf"

def get_ip_address():
    """Gets the current IP address reliably using hostname -I"""
    try:
        # hostname -I returns all IP addresses separated by space
        output = subprocess.check_output(['hostname', '-I']).decode('utf-8').strip()
        if output:
            return output.split(' ')[0] # Return the first IP found
        else:
            return "<Not Set>"
    except Exception:
        return "<Not Set>"

def scan_wifi():
    """Scans for available Wi-Fi networks using iwlist"""
    cmd = "sudo iwlist wlan0 scan"
    try:
        result = subprocess.check_output(cmd, shell=True).decode('utf-8')
        # Regex to find ESSIDs
        ssids = re.findall(r'ESSID:"([^"]+)"', result)
        # Remove duplicates and empty strings
        unique_ssids = list(set(filter(None, ssids)))
        
        wifi_info = 'Found ssid:\n'
        for ssid in unique_ssids:
            wifi_info += ssid + "\n"
        wifi_info += "!"
        return wifi_info
    except Exception as e:
        print(f"Error scanning wifi: {e}")
        return "Error scanning!"

def wifi_connect(ssid, psk):
    """Writes config and reloads wpa_supplicant"""
    print(f"Attempting to connect to {ssid}...")
    
    config_lines = [
        'country=IN',
        'ctrl_interface=DIR=/var/run/wpa_supplicant GROUP=netdev',
        'update_config=1',
        '\n',
        'network={',
        f'    ssid="{ssid}"',
        f'    psk="{psk}"',
        '}'
    ]

    # Write the file directly (script must be run as sudo)
    try:
        with open(wpa_supplicant_conf, 'w') as f:
            f.write('\n'.join(config_lines))
    except PermissionError:
        print("Error: Script must be run with sudo to edit Wi-Fi config.")
        return "Permission Error"

    # Reload configuration using wpa_cli (standard for RPi/DietPi)
    # This avoids taking the interface completely down/up which can hang
    os.system('sudo wpa_cli -i wlan0 reconfigure')
    
    print("Configuration reloaded. Waiting for connection...")
    time.sleep(10) # Wait for DHCP to assign IP

    return get_ip_address()

def handle_client(client_sock):
    # 1. Send discovered SSIDs
    print("Scanning for networks...")
    client_sock.send(scan_wifi().encode('utf-8'))
    print("Waiting for SSID selection...")

    # 2. Receive SSID
    try:
        data = client_sock.recv(1024)
        ssid = data.decode('utf-8').strip()
    except:
        return

    if not ssid:
        return

    print(f"SSID Received: {ssid}")

    # 3. Request Password
    client_sock.send("waiting-psk!".encode('utf-8'))
    print("Waiting for PSK...")

    # 4. Receive Password
    try:
        data = client_sock.recv(1024)
        psk = data.decode('utf-8').strip()
    except:
        return
    
    if not psk:
        return

    print("PSK received.")

    # 5. Connect and get IP
    ip_address = wifi_connect(ssid, psk)

    print(f"New IP address: {ip_address}")
    client_sock.send(f"ip-address:{ip_address}!".encode('utf-8'))
    return

# --- Main Execution ---

try:
    print("Starting Bluetooth Wi-Fi Config Server...")
    print("Ensure you are running this as ROOT (sudo).")

    while True:
        server_sock = BluetoothSocket(RFCOMM)
        server_sock.bind(("", PORT_ANY))
        server_sock.listen(1)

        port = server_sock.getsockname()[1]

        uuid = "815425a5-bfac-47bf-9321-c5ff980b5e11"

        advertise_service(server_sock, "RPi Wifi config",
                          service_id=uuid,
                          service_classes=[uuid, SERIAL_PORT_CLASS],
                          profiles=[SERIAL_PORT_PROFILE])

        print(f"Waiting for connection on RFCOMM channel {port}")

        client_sock, client_info = server_sock.accept()
        print(f"Accepted connection from {client_info}")

        handle_client(client_sock)

        client_sock.close()
        server_sock.close()

        print('Finished configuration cycle. Restarting listener...\n')

except (KeyboardInterrupt, SystemExit):
    print('\nExiting...')
