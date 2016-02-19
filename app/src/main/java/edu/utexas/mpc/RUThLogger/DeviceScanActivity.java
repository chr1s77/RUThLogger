package edu.utexas.mpc.RUThLogger;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.List;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
@TargetApi(Build.VERSION_CODES.M)
public class DeviceScanActivity extends ListActivity {
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothLeAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private final String TAG = "RUThLogger";
    private BufferedWriter mBufferedWriter = null;
    private boolean mScanning;
    private Handler mHandler;

    private static final int REQUEST_ENABLE_BT = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setTitle(R.string.title_devices);
        get_log_num();
        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth LE scanner.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE); //qua era context.BLUETOOTH_SERVICE
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return;
            }
        }

        mBluetoothLeAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothLeAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return;
        }

        mBluetoothLeScanner = mBluetoothLeAdapter.getBluetoothLeScanner();
        if (mBluetoothLeScanner == null) {
            Log.e(TAG, "Unable to obtain a mBluetoothLeScanner.");
            return;
        }


        // Checks if Bluetooth is supported on the device.
        if (mBluetoothLeScanner == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothLeAdapter.isEnabled()) {
            if (!mBluetoothLeAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
        scanLeDevice(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
                ScanSettings mScanSettings = new ScanSettings.Builder()
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .build();
                mScanning = true;
                mBluetoothLeScanner.startScan(null, mScanSettings, mLeScanCallback);
            }
            else {
                mScanning = true;
                mBluetoothLeScanner.startScan(mLeScanCallback);
            }
        } else {
            mScanning = false;
            mBluetoothLeScanner.flushPendingScanResults(mLeScanCallback);
            mBluetoothLeScanner.stopScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BLEDeviceEntry> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BLEDeviceEntry>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BLEDeviceEntry device) {
            mLeDevices.add(device);
        }

        public BLEDeviceEntry getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                viewHolder.deviceTimestamp = (TextView) view.findViewById(R.id.device_timestamp);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BLEDeviceEntry deviceEntry = mLeDevices.get(i);
            final String deviceName = deviceEntry.device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(deviceEntry.device.getAddress());
            viewHolder.deviceTimestamp.setText(deviceEntry.timestamp);
            return view;
        }
    }


    private int get_log_num(){
        Log.i(TAG, "Initializing log file.");
        File root = new File("/sdcard/BTLELogs");
        TimeZone tz = TimeZone.getTimeZone("Europe/Rome");

        Calendar rightNow = Calendar.getInstance(tz);
        String dirName = root.getAbsolutePath() + "/BTLE_log_data/"+rightNow.get(Calendar.DAY_OF_MONTH)+"_"+ (rightNow.get(Calendar.MONTH) + 1) +"_"+ rightNow.get(Calendar.YEAR) +"/";
        File newFile = null;
        try{
            newFile = new File(dirName);
            newFile.mkdirs();
            Log.i(TAG, "Directory \""+ dirName + "\" created.");

        }
        catch(Exception e)
        {
            Log.w(TAG, "Exception creating folder");
            return -1;
        }

        Log.i(TAG, "\n ************ WRITING ************\n");

        if (newFile.canWrite()){

            String file_name_log = "log_"+rightNow.get(Calendar.DAY_OF_YEAR)+"_"+rightNow.get(Calendar.HOUR_OF_DAY)+"."+rightNow.get(Calendar.MINUTE)+"."+rightNow.get(Calendar.SECOND)+".txt";

            File mFile = new File(dirName,file_name_log);

            Log.i(TAG, "******************DIRECTORY: " + dirName + "****************");
            Log.i(TAG, "******************FILE: " + file_name_log + "****************");


            try {
                FileWriter mFileWriter = new FileWriter(mFile);
                mBufferedWriter = new BufferedWriter(mFileWriter);
                Log.i(TAG, "Log file \""+ file_name_log + "\"created!");

            } catch (IOException e) {
                e.printStackTrace();
                Log.w(TAG, "IOException in creating file");
            }
        }else{
            Log.w(TAG, "Can't write to file: " + newFile.getAbsolutePath());
            return -1;
        }

        return 1;
    }



    // Device scan callback.

    private ScanCallback mLeScanCallback =
            new ScanCallback() {

                @Override
                public void onBatchScanResults(List<ScanResult> results){
                    Log.i(TAG, "Number of scan results: " + results.size());
                }

                @Override
                public void onScanResult(int callbackType, final ScanResult result){
                    Log.i(TAG, "Callback type: " + callbackType);
                    final String timestamp = new SimpleDateFormat("yyyy MM dd HH mm ss").format(new Date());
                    if (mBufferedWriter != null) {
                        long nowMillis = SystemClock.uptimeMillis();
                        String manufString = "";

                        byte[] manufacterData = result.getScanRecord().getManufacturerSpecificData(0x000D);//TI Specific id
                        if(manufacterData != null) {
                            for (int i = 0; i < manufacterData.length; i++) {
                                manufString = manufString + String.format("%02X", manufacterData[i]);
                            }
                        }


                        try {
                            String logLine =  ""+ timestamp +
                                    " " + nowMillis +
                                    " " + result.getDevice().getAddress() +
                                    " " + result.getDevice().getName() +
                                    " ADV data" +
                                    " " + manufString +
                                    "\n" ;
                            //TODO: AGGIUNGERE RSSI
                            mBufferedWriter.write(logLine);
                            mBufferedWriter.flush();

                        } catch (IOException e) {
                            Log.w(TAG, "Exception thrown while writing data to file.");
                        }
                    }


                    Log.i(TAG, "******************RESULT****************");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            BLEDeviceEntry newEntry = new BLEDeviceEntry();
                            newEntry.device = result.getDevice();
                            newEntry.timestamp = timestamp;
                            mLeDeviceListAdapter.addDevice(newEntry);
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        TextView deviceTimestamp;
    }
}

class BLEDeviceEntry {

    public BluetoothDevice device;
    public String timestamp;

}