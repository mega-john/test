package usbserial.cz.com.tpmsreader;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import usbserial.cz.com.tpmsreader.Driver.UsbSerialDriver;
import usbserial.cz.com.tpmsreader.Driver.UsbSerialPort;
import usbserial.cz.com.tpmsreader.Driver.UsbSerialProber;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener {

    private static final String ACTION_USB_PERMISSION = "usbserial.cz.com.tpmsreader.USB_PERMISSION";
    private Button btScanUsbDevices;
    private Button btClearResults;
    private EditText etScanResults;
    private ListView lvDevices;
    private String TAG = "TPMSReader";
    private UsbManager mUsbManager = null;
    private boolean DEBUG = true;
    private PendingIntent mPermissionIntent = null;
    private BroadcastReceiver mUsbReceiver = new UsbReceiver();
    private List<UsbSerialPort> mEntries = null;
    ArrayList<HashMap<String, String>> listViewItems = new ArrayList<>();
    private SimpleAdapter listViewAdapter;
    private UsbSerialPort sPort;
    private String VERS_INFO = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mEntries = new ArrayList();

        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.EXTRA_PERMISSION_GRANTED);
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
        initView();
        etScanResults.clearFocus();
    }

    private void initView() {
        btScanUsbDevices = (Button) findViewById(R.id.btScanUsbDevices);
        btScanUsbDevices.setOnClickListener(this);
        btClearResults = (Button) findViewById(R.id.btClearResults);
        btClearResults.setOnClickListener(this);
        etScanResults = (EditText) findViewById(R.id.etScanResults);
//        etScanResults.setInputType(InputType.TYPE_NULL);
        etScanResults.setKeyListener(null);
        lvDevices = (ListView) findViewById(R.id.lvDevices);
        lvDevices.setOnItemClickListener(this);
        listViewAdapter = new SimpleAdapter(this,
                listViewItems,
                android.R.layout.simple_list_item_2,
                new String[]{"Name", "ID"},
                new int[]{android.R.id.text1, android.R.id.text2});
        lvDevices.setAdapter(listViewAdapter);
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mEntries == null || mEntries.size() <= 0) {
            etScanResults.append("usb devices list empty!!!");
            return;
        }
        UsbSerialPort port = mEntries.get(position);
        if (port != null) {
            showConsoleActivity(port);
        } else {
            etScanResults.append("selected UsbSerialPort is null!!!");
        }
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.btScanUsbDevices:
                StartScanUsbDevices();
                return;

            case R.id.btClearResults:
                ClearScanResults();
                return;

            default:
                return;
        }
    }


    private void ClearScanResults() {
        etScanResults.setText("");
        etScanResults.clearFocus();
        listViewItems.clear();
        listViewAdapter.notifyDataSetChanged();
    }

    private void StartScanUsbDevices() {
//        Log.d(TAG, "Refreshing device list ...");
        etScanResults.append("Refreshing device list ..." + "\r\n");
        try {
            SystemClock.sleep(1000);
        } catch (Exception e) {
            etScanResults.append(e.getMessage() + "\r\n");
        }
        if (mUsbManager == null) {
            mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        }

        mEntries.clear();

//        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
//        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        etScanResults.append("find all usb devices :\n");
        HashMap<String, String> map = new HashMap<>();

//        while (deviceIterator.hasNext()) {
//            UsbDevice device = deviceIterator.next();
////            etScanResults.append("name: " + device.getDeviceName() + ", " + "ID: " + device.getDeviceId());
//            etScanResults.append("--------------------------------------------------\n");
//            etScanResults.append("DeviceName: " + device.getDeviceName() + "\n");
//            etScanResults.append("DeviceId: " + Integer.toHexString(device.getDeviceId()) + "\n");
//            etScanResults.append("DeviceProtocol: " + Integer.toHexString(device.getDeviceProtocol()) + "\n");
//            etScanResults.append("ProductId: " + Integer.toHexString(device.getProductId()) + "\n");
//            etScanResults.append("VendorId: " + Integer.toHexString(device.getVendorId()) + "\n");
//            map = new HashMap<>();
//            map.put("Name", "name: " + device.getDeviceName());
//            map.put("ID", "ID: " + Integer.toHexString(device.getDeviceId()));
//            listViewItems.add(map);
//        }
//        listViewAdapter.notifyDataSetChanged();
//
//        etScanResults.append("\n");
        etScanResults.append("find usb devices by filter:\n");

        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
        for (UsbSerialDriver driver : drivers) {
            List<UsbSerialPort> ports = driver.getPorts();
//            if (DEBUG) {
//                String str;
//                String access$0 = TAG;
//                String str2 = "found item: + %s: %s port%s";
//                Object[] objArr = new Object[3];
//                objArr[0] = driver;
//                objArr[1] = Integer.valueOf(ports.size());
//                objArr[2] = ports.size() == 1 ? "" : "s";
//                str = String.format(str2, objArr);
//                etScanResults.append(str + "\r\n");

            map = new HashMap<>();
            map.put("Name", "name: " + driver.getDevice().getDeviceName());
            map.put("ID", "ID: " + Integer.toHexString(driver.getDevice().getDeviceId()));
            listViewItems.add(map);
//            }
            mEntries.addAll(ports);
        }
        listViewAdapter.notifyDataSetChanged();

        etScanResults.append("--------------------------------------------------\n");
        etScanResults.append("scan result " + drivers.size() + "\r\n");
    }

    public void onDestroy() {
        super.onDestroy();

        if (mUsbReceiver != null) {
            unregisterReceiver(mUsbReceiver);
        }
    }

    private void showConsoleActivity(UsbSerialPort Port) {
        UsbInterface mInterface = null;
        this.sPort = Port;
        if (this.sPort != null) {
            UsbSerialDriver driver = this.sPort.getDriver();
            if (driver == null) {
                etScanResults.append("this.sPort.getDriver() returns null!");
                return;
            }
            UsbDevice device = driver.getDevice();
            if (device == null) {
                etScanResults.append("driver.getDevice() returns null!");
                return;
            }
            if (0 < device.getInterfaceCount()) {
                mInterface = this.sPort.getDriver().getDevice().getInterface(0);
            }
            if (mInterface == null) {
                etScanResults.append("USB device NO  Interface\n");
            } else if (this.mUsbManager.hasPermission(device)) {
                UsbDeviceConnection connection = this.mUsbManager.openDevice(device);
                if (connection == null) {
                    try {
                        if (this.sPort != null) {
                            this.sPort.close();
                        }
                    } catch (IOException e) {
                        etScanResults.append("Error close port: " + e.getMessage() + "\n");
                    }
                    this.sPort = null;
                    if (DEBUG) {
                        etScanResults.append("Error openDevice:  connection " + connection);
                    }
//                    if (mHandler != null) {
//                        mHandler.sendEmptyMessage(MESSAGE_USB_OPEN_FAIL);
//                        return;
//                    }
                    return;
                }
                try {
                    this.sPort.open(connection);
                    try {
                        this.sPort.setParameters(19200, 8, 1, 0);
//                        if (mHandler != null) {
//                            mHandler.sendEmptyMessage(MESSAGE_USB_OPEN_OK);
//                        }
                        if (this.sPort != null) {
//                            try {
//                                String versionName = Tools.getVersionName(this.mContext);
//                                VERS_INFO = new StringBuilder(String.valueOf(versionName)).append(" ").append(this.sPort.getClass().getSimpleName()).toString();
//                            } catch (IOException e2) {
//                                e2.printStackTrace();
//                                Log.e(TAG, "Error Tools.getVersionName: " + e2.getMessage(), e2);
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                                Log.e(TAG, "Error Tools.getVersionName: " + e.getMessage(), e);
//                            }
                        }
//                        onDeviceStateChange();
                    } catch (Exception e22) {
                        if (DEBUG) {
                            etScanResults.append("Error setting up device: " + e22.getMessage());
                        }
                        try {
                            if (this.sPort != null) {
                                this.sPort.close();
                            }
                        } catch (Exception e3) {
                        }
                        this.sPort = null;
//                        if (mHandler != null) {
//                            mHandler.sendEmptyMessage(MESSAGE_USB_OPEN_FAIL);
//                        }
                    }
                } catch (Exception e23) {
                    if (DEBUG) {
                        etScanResults.append("cz open device: " + e23.getMessage());
                    }
                }
            } else {
                if (DEBUG) {
                    etScanResults.append("permission denied for device ");
                }
                this.mUsbManager.requestPermission(this.sPort.getDriver().getDevice(), mPermissionIntent);
            }
        }
    }
}

