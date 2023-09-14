/*
 * Copyright (C) 2016-2020 Microchip Technology Inc. and its subsidiaries.  You may use this software and any
 * derivatives exclusively with Microchip products.
 *
 * THIS SOFTWARE IS SUPPLIED BY MICROCHIP "AS IS".  NO WARRANTIES, WHETHER EXPRESS, IMPLIED OR STATUTORY, APPLY TO THIS
 * SOFTWARE, INCLUDING ANY IMPLIED WARRANTIES OF NON-INFRINGEMENT, MERCHANTABILITY, AND FITNESS FOR A PARTICULAR
 * PURPOSE, OR ITS INTERACTION WITH MICROCHIP PRODUCTS, COMBINATION WITH ANY OTHER PRODUCTS, OR USE IN ANY APPLICATION.
 *
 * IN NO EVENT WILL MICROCHIP BE LIABLE FOR ANY INDIRECT, SPECIAL, PUNITIVE, INCIDENTAL OR CONSEQUENTIAL LOSS, DAMAGE,
 * COST OR EXPENSE OF ANY KIND WHATSOEVER RELATED TO THE SOFTWARE, HOWEVER CAUSED, EVEN IF MICROCHIP HAS BEEN ADVISED OF
 * THE POSSIBILITY OR THE DAMAGES ARE FORESEEABLE.  TO THE FULLEST EXTENT ALLOWED BY LAW, MICROCHIP'S TOTAL LIABILITY ON
 * ALL CLAIMS IN ANY WAY RELATED TO THIS SOFTWARE WILL NOT EXCEED THE AMOUNT OF FEES, IF ANY, THAT YOU HAVE PAID
 * DIRECTLY TO MICROCHIP FOR THIS SOFTWARE.
 *
 * MICROCHIP PROVIDES THIS SOFTWARE CONDITIONALLY UPON YOUR ACCEPTANCE OF THESE TERMS.
 */

package com.microchip.mu_ble1;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Activity for scanning and displaying available Bluetooth LE devices
 */
public class BleScanActivity extends AppCompatActivity {
	private final static String TAG = BleScanActivity.class.getSimpleName();                        //Activity name for logging messages on the ADB

    public static final String EXTRA_SCAN_ADDRESS = "BLE_SCAN_DEVICE_ADDRESS";                      //Identifier for Bluetooth device address attached to Intent that returns a result
    public static final String EXTRA_SCAN_NAME = "BLE_SCAN_DEVICE_NAME";                            //Identifier for Bluetooth device name attached to Intent that returns a result
    private static final int REQ_CODE_ENABLE_BT = 1;                                                //Code to identify activity that enables Bluetooth
    private static final UUID EXAMPLE_SERVICE_UUID = UUID.fromString("24e84f52-d20c-4387-85a6-2cea19259c7d"); //Advertised service UUID for scan filter
    private static final long SCAN_TIME = 20000;                                                    //Length of time in milliseconds to scan for BLE devices

    private ProgressBar progressBar;                                                                //Progress bar (indeterminate circular) to show that activity is busy connecting to BLE device
    private BluetoothAdapter btAdapter;                                                             //BluetoothAdapter represents the Bluetooth radio in the phone
    private BluetoothLeScanner bleScanner;                                                          //BluetoothLeScanner handles the scanning for BLE devices that are advertising
    private Handler stopScanHandler;                                                                //Handler to stop the scan after a time delay
    private DeviceListAdapter deviceListAdapter;                                                    //ArrayAdapter to manage the ListView showing the devices found during the scan
    private boolean areScanning;                                                                    //Indicator that a scan is in progress
    private TextView deviceListText;                                                                //Text to indicate whether devices have been found by the scan

    /******************************************************************************************************************
     * Methods for handling life cycle events of the activity.
     */

    // ----------------------------------------------------------------------------------------------------------------
    // Activity created
    // Gets access to the Bluetooth radio and sets up the display
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);                                                         //Call superclass (AppCompatActivity) onCreate method
        setContentView(R.layout.scan_list_screen);                                                  //Show the screen
        Toolbar myToolbar = findViewById(R.id.toolbar);                                             //Get a reference to the Toolbar at the top of the screen
        setSupportActionBar(myToolbar);                                                             //Treat the toolbar as an Action bar (used for app name, menu, navigation, etc.)
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);                                      //Show the back navigation arrow on the Action bar
        progressBar = findViewById(R.id.toolbar_progress_bar);                                      //Get a reference to the progress bar
        progressBar.setIndeterminate(true);                                                         //Make the progress bar indeterminate (circular)
        deviceListText = findViewById(R.id.deviceListText);                                         //Text to indicate devices found or not found
        ListView deviceListView = findViewById(R.id.deviceListView);                                //ListView to show all the devices found during the scan
        deviceListView.setOnItemClickListener(deviceListClickListener);                             //Click listener for when the user selects an item in the ListView
        deviceListAdapter = new DeviceListAdapter(this, R.layout.scan_list_item);           //Create new ArrayAdapter to hold a list of BLE devices found during the scan
        deviceListView.setAdapter(deviceListAdapter);                                               //Bind our ArrayAdapter the new list adapter in our ListActivity
        stopScanHandler = new Handler(Looper.getMainLooper());                                      //Create a handler for a delayed runnable that will stop the scan after a time delay
        try {
            btAdapter = BluetoothAdapter.getDefaultAdapter();                                       //Get a reference to the BluetoothAdapter
            if (btAdapter == null) {                                                                //Unlikely that there is no Bluetooth radio but best to check anyway
                Log.e(TAG, "Unable to obtain a BluetoothAdapter");
                finish();                                                                           //End the activity, can do nothing without a BluetoothAdapter
            }
            bleScanner = btAdapter.getBluetoothLeScanner();                                         //Get a BluetoothLeScanner so we can scan for BLE devices
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity resumed after it was paused
    // Initializes ArrayAdapter for list of devices and starts a new scan
    @Override
    protected void onResume() {
        super.onResume();                                                                           //Call superclass (AppCompatActivity) onResume method
        try {
            if (!btAdapter.isEnabled()) {                                                           //Check that Bluetooth is still enabled
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_CODE_ENABLE_BT); //Invoke the Intent to start the activity to turn on Bluetooth
                Log.d(TAG, "Requesting user to enable Bluetooth radio");
            } else {
                startScan();                                                                        //Always start a scan when resuming from a pause
            }
        }
        catch (Exception e) {
                Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity paused
    // Stop any scan in progress
    @Override
    protected void onPause() {
        super.onPause();                                                                            //Call superclass (AppCompatActivity) onPause method
        stopScanHandler.removeCallbacks(stopScanRunnable);                                          //Stop the scan timeout handler from calling the runnable to stop the scan
        stopScan();                                                                                 //Stop any scan in progress
    }

    /******************************************************************************************************************
     * Methods for handling menu creation and operation.
     */

    // ----------------------------------------------------------------------------------------------------------------
    // Options menu is different depending on whether scanning or not
    // Only show Scan menu option if not scanning
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.ble_scan_menu, menu);                                      //Show the menu
        if (areScanning) {                                                                          //Are scanning
            menu.findItem(R.id.menu_scan).setVisible(false);                                        //so do not show Scan menu option
        }
        else {                                                                                      //Are not scanning
            menu.findItem(R.id.menu_scan).setVisible(true);                                         //so show Scan menu option
        }
        return true;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Menu item selected
    // Start scanning for BLE devices
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {                                                                 //See which menu item was selected
	        case R.id.menu_scan: {                                                                  //Option to Scan selected
                startScan();                                                                        //Start a scan
                break;
            }
            case android.R.id.home: {                                                               //User pressed the back arrow next to the app name on the ActionBar
                onBackPressed();                                                                    //Treat it as if the back button was pressed
                break;
            }
            default: {                                                                              //Unexpected menu item selected
                Log.w(TAG, "Invalid menu item selected");
                return false;
            }
        }
        return true;
    }

    /******************************************************************************************************************
     * Callback methods for handling Activity result events.
     */

    // ----------------------------------------------------------------------------------------------------------------
    // Callback for Activity that returns a result
    // In case we started the Activity to turn on the Bluetooth radio
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQ_CODE_ENABLE_BT && resultCode != Activity.RESULT_OK) {                //User was requested to enable Bluetooth but did not
            onBackPressed();                                                                        //User chose not to enable Bluetooth so go back to calling activity
        }
    }

    /******************************************************************************************************************
     * Methods for starting and stopping scans
     */

    // ----------------------------------------------------------------------------------------------------------------
    // Starts a scan
    private void startScan() {
        try {
            if (!areScanning) {                                                                     //Only start scanning if not already scanning
                if (btAdapter.isEnabled() && bleScanner != null) {                                  //Check that Bluetooth is enabled
                    areScanning = true;                                                             //Indicate that we are scanning - used for menu context and to avoid starting scan twice
                    deviceListText.setText(R.string.no_devices_found);                                     //Show "No devices found" until scan returns a result
                    deviceListAdapter.clear();                                                      //Clear list of BLE devices found
                    deviceListAdapter.notifyDataSetChanged();                                       //Update the display to clear previous devices from the screen
                    progressBar.setVisibility(ProgressBar.VISIBLE);                                 //Show the circular progress bar
                    invalidateOptionsMenu();                                                        //The options menu needs to be refreshed
                    List<ScanFilter> scanFilterList = new ArrayList<>();                            //Create a new ScanFilter list
                    scanFilterList.add(new ScanFilter.Builder().setDeviceName("AVR-BLE_1B19").build()); //Add a device name to the filter list
                    scanFilterList.add(new ScanFilter.Builder().setDeviceName("PIC-BLE_519E").build()); //Add a device name to the filter list
                    scanFilterList.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(EXAMPLE_SERVICE_UUID)).build()); //Add a service UUID to the filter list
                    final int MCHP_MFR_ID = 0x00cd; //TODO Change this to your own ID               //Microchip's Bluetooth SIG assigned manufacturer ID
                    scanFilterList.add(new ScanFilter.Builder().setManufacturerData(MCHP_MFR_ID, new byte[]{1, 2, 3, 4}).build()); //Add manufacturer specific data to the filter list
                    ScanSettings scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(); //Set the scan mode to low latency
                    //bleScanner.startScan(scanFilterList, scanSettings, bleScanCallback);            //Start scanning with ScanFilter and provide a callback for scan results
                    bleScanner.startScan(bleScanCallback);                                          //Start a scan with no filtering and provide a callback for scan results
                    stopScanHandler.postDelayed(stopScanRunnable, SCAN_TIME);                       //Create delayed runnable that will stop the scan when it runs after SCAN_TIME milliseconds
                }
                else {                                                                              //Radio needs to be enabled
                    startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_CODE_ENABLE_BT); //Invoke the Intent to start the activity that will return a result based on user input
                    Log.d(TAG, "Requesting user to enable Bluetooth radio");
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Stop scan for BLE devices
    private void stopScan() {
        try {
            if (areScanning) {                                                                      //See if still scanning
                bleScanner.stopScan(bleScanCallback);                                               //Stop scanning
                areScanning = false;                                                                //Indicate that we are not scanning
                progressBar.setVisibility(ProgressBar.INVISIBLE);                                   //Hide circular progress bar
                invalidateOptionsMenu();                                                            //The options menu needs to be refreshed
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Runnable used by the stopScanHandler to stop the scan after a delay of SCAN_TIME milliseconds
    private Runnable stopScanRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();                                                                             //Stop the scan
        }
    };

    // ----------------------------------------------------------------------------------------------------------------
    // Scan callback for API 21 (Lollipop, Android 5.0) or later. BluetoothLeScanner calls this method when a new device is discovered during a scan.
    // The callback is only called for devices with advertising packets meeting the scan filter parameters.
    private ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            try {
                BluetoothDevice device = result.getDevice();                                        //Get the device found by the scan
                deviceListAdapter.addDevice(device);                                                //Add the new BleDevice object to our list adapter that displays a list on the screen
                deviceListAdapter.notifyDataSetChanged();                                           //Refresh the list on the screen
                deviceListText.setText(R.string.devices_found);                                           //Show "Devices found:" because we have found a device
                Log.i(TAG, "ScanResult: Addr - " + device.getAddress() + ", Name - " + device.getName());
            }
            catch (Exception e) {
                Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan Failed: Error Code: " + errorCode);
        }
    };

    /******************************************************************************************************************
     * Methods for handling the ListAdapter that shows the scanned items.
     */

    // ----------------------------------------------------------------------------------------------------------------
    // Adapter for holding devices found through scanning
    private static class DeviceListAdapter extends ArrayAdapter<BluetoothDevice> {

        private ArrayList<BluetoothDevice> btDevices;                                               //An ArrayList to hold the BluetoothDevice objects in the list
        private int layoutResourceId;
        private Context context;

        public DeviceListAdapter(Context context, int layoutResourceId) {                           //Constructor for the DeviceListAdapter
            super(context, layoutResourceId);
            this.layoutResourceId = layoutResourceId;
            this.context = context;
            btDevices = new ArrayList<>();                                                          //Create the list to hold BleDevice objects
        }

        public void addDevice(BluetoothDevice device) {                                             //Add a new device to the list
            if(!btDevices.contains(device)) {                                                       //See if device is not already in the list
                btDevices.add(device);                                                              //Add the device to the list
            }
        }

        public void clear() {                                                                       //Clear the list of devices
            btDevices.clear();
        }

        @Override
        public int getCount() {                                                                     //Get the number of devices in the list
            return btDevices.size();
        }

        @Override
        public BluetoothDevice getItem(int i) {                                                      //Get a device from the list based on its position
            return btDevices.get(i);
        }

        @Override
        public long getItemId(int i) {                                                              //Get device ID which is just its position in the list
            return i;
        }

        //Called by the Android OS to show each item in the view. View items that scroll off the screen are reused.
        @Override
        public View getView(int position, View convertView, ViewGroup parentView) {
            if (convertView == null) {                                                              //Only inflate a new layout if not recycling a view
                LayoutInflater inflater = ((Activity) context).getLayoutInflater();                 //Get the layout inflater for this activity
                convertView = inflater.inflate(layoutResourceId, parentView, false);    //Inflate a new view containing the device information
            }
            BluetoothDevice device = btDevices.get(position);                                       //Get device item based on the position
            TextView textViewAddress = convertView.findViewById(R.id.device_address);               //Get the TextView for the address
            textViewAddress.setText(device.getAddress());                                           //Set the text to the name of the device
            TextView textViewName = convertView.findViewById(R.id.device_name);                     //Get the TextView for the name
            textViewName.setText(device.getName());                                                 //Set the text to the name of the device
            return convertView;
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Device has been selected in the list adapter
    // Return name and address of BLE device to the BleMainActivity that started this activity
    private AdapterView.OnItemClickListener deviceListClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            final BluetoothDevice device = deviceListAdapter.getItem(i);		                    //Get the device from the list adapter
            stopScanHandler.removeCallbacks(stopScanRunnable);                                      //Stop the scan timeout handler from calling the runnable to stop the scan
            stopScan();                                                                             //Stop a scan that might still be running
            final Intent intent = new Intent();                                                     //Create Intent to return information to the BleMainActivity that started this activity
            if (device != null) {                                                                   //Check that a valid device was received
                intent.putExtra(EXTRA_SCAN_NAME, device.getName());                                 //Add BLE device name to the Intent
                intent.putExtra(EXTRA_SCAN_ADDRESS, device.getAddress());                           //Add BLE device address to the Intent
                setResult(Activity.RESULT_OK, intent);                                              //Set the Intent to return a result to the calling activity with the selected BLE name and address
            }
            else {
                setResult(Activity.RESULT_CANCELED, intent);                                        //Something went wrong so indicate cancelled
            }
            finish();                                                                               //End this activity and send result Intent back to caller
        }
    };
}
