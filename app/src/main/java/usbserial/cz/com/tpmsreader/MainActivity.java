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
import android.os.Handler;
import android.os.Message;
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
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import usbserial.cz.com.tpmsreader.Driver.SerialInputOutputManager;
import usbserial.cz.com.tpmsreader.Driver.UsbSerialDriver;
import usbserial.cz.com.tpmsreader.Driver.UsbSerialPort;
import usbserial.cz.com.tpmsreader.Driver.UsbSerialProber;
import usbserial.cz.com.tpmsreader.util.Tools;

import static usbserial.cz.com.tpmsreader.util.Tools.bytesToHexString;
import static usbserial.cz.com.tpmsreader.util.Tools.sum;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener {

    private static final String ACTION_USB_PERMISSION = "usbserial.cz.com.tpmsreader.USB_PERMISSION";
    private Button btScanUsbDevices;
    private Button btClearResults;
    private EditText etScanResults;
    private ListView lvDevices;
    private String TAG = "TPMSReader";
    private UsbManager mUsbManager = null;
    public boolean DEBUG = true;
    private PendingIntent mPermissionIntent = null;
    private BroadcastReceiver mUsbReceiver = new UsbReceiver();
    private List<UsbSerialPort> mEntries = null;
    ArrayList<HashMap<String, String>> listViewItems = new ArrayList<>();
    private SimpleAdapter listViewAdapter;
    private UsbSerialPort sPort;
    private String VERS_INFO = "";
    private static SerialInputOutputManager mSerialIoManager;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final SerialInputOutputManager.Listener mListener = new SerialTPMSListener();
    public static final int P_UNIT = 2;
    public static final int LT_PROGRESS_STATAR = 10;
    private static final int MESSAGE_DATA = 105;
    private static final int MESSAGE_HANDSHAKE_NO = 107;
    private static final int MESSAGE_HANDSHAKE_OK = 106;
    private static final int MESSAGE_USB_CONNECT = 103;
    private static final int MESSAGE_USB_OPEN_FAIL = 101;
    private static final int MESSAGE_USB_OPEN_OK = 102;
    private static final int MESSAGE_VOICE_SPEK = 104;
    private static final int MESSAGE_WARN_HIGH_TIRE_PRESSURE = 110;
    private static final int MESSAGE_WARN_HIGH_TIRE_TEMPERATURE = 111;
    private static final int MESSAGE_WARN_LOW_BATTERY = 112;
    private static final int MESSAGE_WARN_LOW_TIRE_PRESSURE = 109;
    private static final int MESSAGE_WARN_NO_RF_SIGNAL = 113;
    private static final int MESSAGE_WARN_TIRE_LEAK = 108;
    public static Handler mHandlerSeriaTest = null;
    public static Handler mHandler = null;
    private Timer mTimerHandShake = null;
    private TimerTask mTimerTaskHandShake = null;
    int buf_len = 0;
    int buf_temp_len = 0;
    static byte[] buf = new byte[40];
    static byte[] buf_temp = new byte[40];
    static byte[] temp = new byte[40];
    private int data_count = 0;
    boolean data_head_falg = false;
    private static Boolean HandShake = Boolean.valueOf(false);
    private static int HandShakeCount = 0;
    private static int HandShakeTotal = 120;
    private int f533j;
    private static byte time = (byte) 0;
    Context mContext = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mEntries = new ArrayList();

        mPermissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.EXTRA_PERMISSION_GRANTED);
        filter.addAction(ACTION_USB_PERMISSION);
        mContext.registerReceiver(mUsbReceiver, filter);
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

        etScanResults.append("find all usb devices :\n");
        HashMap<String, String> map = new HashMap<>();

        etScanResults.append("find usb devices by filter:\n");

        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
        for (UsbSerialDriver driver : drivers) {
            List<UsbSerialPort> ports = driver.getPorts();

            map = new HashMap<>();
            map.put("Name", "name: " + driver.getDevice().getDeviceName());
            map.put("ID", "ID: " + Integer.toHexString(driver.getDevice().getDeviceId()));
            listViewItems.add(map);
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
                    if (mHandler != null) {
                        mHandler.sendEmptyMessage(MESSAGE_USB_OPEN_FAIL);
                        return;
                    }
                    return;
                }
                try {
                    this.sPort.open(connection);
                    try {
                        this.sPort.setParameters(19200, 8, 1, 0);
                        if (mHandler != null) {
                            mHandler.sendEmptyMessage(MESSAGE_USB_OPEN_OK);
                        }
                        if (this.sPort != null) {
                            try {
                                String versionName = Tools.getVersionName(this.mContext);
                                VERS_INFO = new StringBuilder(String.valueOf(versionName)).append(" ").append(this.sPort.getClass().getSimpleName()).toString();
                            } catch (IOException e2) {
                                e2.printStackTrace();
                                etScanResults.append("Error Tools.getVersionName: " + e2.getMessage());
                            } catch (Exception e) {
                                e.printStackTrace();
                                etScanResults.append("Error Tools.getVersionName: " + e.getMessage());
                            }
                        }
                        onDeviceStateChange();
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
                        if (mHandler != null) {
                            mHandler.sendEmptyMessage(MESSAGE_USB_OPEN_FAIL);
                        }

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

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            etScanResults.append("Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (this.sPort != null) {
            etScanResults.append("Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(this.sPort, this.mListener);
            this.mExecutor.submit(mSerialIoManager);
        }
    }

    public void writeData(byte[] data) {
        if (mSerialIoManager != null) {
            try {
                mSerialIoManager.writeAsync(data);
            } catch (Exception e) {
            }
            if (DEBUG) {
                etScanResults.append( "cz writeAsync " + bytesToHexString(data));
                return;
            }
            return;
        }
        etScanResults.append("cz writeAsync mSerialIoManager =null ");
    }

    private void sendMessage(Handler mHandler, byte[] data) {
        Message msg = Message.obtain();
        msg.arg1 = data.length;
        Bundle bundle = new Bundle();
        bundle.putByteArray("data", data);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    public void registerHandler(Handler h) {
        mHandler = h;
    }

    public void unregisterHandler() {
        if (mHandler != null) {
            mHandler = null;
        }
    }

    private boolean isDataBoolean(byte[] buff, int len) {
        byte sum = buff[0];
        if (len > 20) {
            len = LT_PROGRESS_STATAR;
        }
        if (buff[0] != (byte) 85 || buff[1] != (byte) -86) {
            return false;
        }
        for (int i = 1; i < len - 1; i++) {
            sum = (byte) (buff[i] ^ sum);
        }
        if (sum == buff[len - 1]) {
            return true;
        }
        return false;
    }

    private void startTimerHandShake() {
        if (this.mTimerHandShake == null) {
            this.mTimerHandShake = new Timer();
        }
        if (this.mTimerTaskHandShake == null) {
            this.mTimerTaskHandShake = new HandShakeTimerTask();
        }
    }

    void setTimeNsHandShake(long dalayms) {
        if (this.mTimerHandShake != null && this.mTimerTaskHandShake != null) {
            this.mTimerHandShake.schedule(this.mTimerTaskHandShake, dalayms * 1000, 1000 * dalayms);
        }
    }

    private boolean isDataWarn(byte[] b) {
        if (DEBUG) {
            etScanResults.append( "cz  isDataWarn  " + bytesToHexString(b));
        }
        if (b[0] != (byte) 85 || b[1] != (byte) -86) {
            return false;
        }
        try {
//            int ret = ServerDataP(b);
//            ServerDataT(b);
//            ServerDataWarn(b);
//            int retPH = getWarnHP();
//            int retPL = getWarnLP();
//            int retHT = getWarnHT();
//            HandShakeData(b);
//            if (ret == 0 || retPH == 0 || retPL == 0 || retHT == 0) {
//                return false;
//            }
//            if (ret == 1) {
//                if (left1_TyrePressure > retPH || left1_TyrePressure < retPL || left1_TyreTemperature > retHT || UnitTools.warning_AIR(left1_Byte).booleanValue() || UnitTools.warning_P(left1_Byte).booleanValue() || UnitTools.warning_Signal(left1_Byte).booleanValue()) {
//                    if (left1_TyrePressure > retPH) {
//                        left1_TyrePressure_Low_count = 0;
//                        if (left1_TyrePressure_Hight_count > DEF_WARN_COUNT) {
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u5de6\u524d\u8f6e\u538b\u529b\u8fc7\u9ad8\uff0c\u8bf7\u6ce8\u610f");
//                            left1_TyrePressure_Hight_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_HIGH_TIRE_PRESSURE, "");
//                        }
//                        left1_TyrePressure_Hight_count++;
//                    } else if (left1_TyrePressure < retPL) {
//                        left1_TyrePressure_Hight_count = 0;
//                        if (left1_TyrePressure_Low_count > DEF_WARN_COUNT) {
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u5de6\u524d\u8f6e\u538b\u529b\u8fc7\u4f4e\uff0c\u8bf7\u6ce8\u610f");
//                            left1_TyrePressure_Low_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_LOW_TIRE_PRESSURE, "");
//                        }
//                        left1_TyrePressure_Low_count++;
//                    } else {
//                        left1_TyrePressure_Hight_count = 0;
//                        left1_TyrePressure_Low_count = 0;
//                    }
//                    if (left1_TyreTemperature > retHT) {
//                        if (left1_TyreTemperature_Hight_count > DEF_WARN_COUNT) {
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u5de6\u524d\u8f6e\u6e29\u5ea6\u8fc7\u9ad8\uff0c\u8bf7\u6ce8\u610f");
//                            left1_TyreTemperature_Hight_count = 0;
//                        }
//                        left1_TyreTemperature_Hight_count++;
//                    } else {
//                        left1_TyreTemperature_Hight_count = 0;
//                    }
//                    if (UnitTools.warning_AIR(left1_Byte).booleanValue()) {
//                        sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u5de6\u524d\u8f6e\u6f0f\u6c14\uff0c\u8bf7\u6ce8\u610f");
//                        sendServerMessage(this.serviceHandler, MESSAGE_WARN_TIRE_LEAK, "");
//                    }
//                    if (UnitTools.warning_P(left1_Byte).booleanValue()) {
//                        sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u5de6\u524d\u8f6e\u7535\u6c60\u7535\u538b\u4f4e\uff0c\u8bf7\u6ce8\u610f");
//                        sendServerMessage(this.serviceHandler, MESSAGE_WARN_LOW_BATTERY, "");
//                    }
//                    if (UnitTools.warning_Signal(left1_Byte).booleanValue()) {
//                        if (left1_Warning_Signal_count > DEF_WARN_COUNT + 8) {
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u5de6\u524d\u8f6e\u4fe1\u53f7\u4e22\u5931\uff0c\u8bf7\u6ce8\u610f");
//                            left1_Warning_Signal_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_NO_RF_SIGNAL, "");
//                        }
//                        left1_Warning_Signal_count++;
//                    } else {
//                        left1_Warning_Signal_count = 0;
//                    }
//                } else {
//                    left1_TyrePressure_Hight_count = 0;
//                    left1_TyrePressure_Low_count = 0;
//                    left1_TyreTemperature_Hight_count = 0;
//                    left1_Warning_Signal_count = 0;
//                }
//            }
//            if (ret == P_UNIT) {
//                if (left2_TyrePressure > retPH || left2_TyrePressure < retPL || left2_TyreTemperature > retHT || UnitTools.warning_AIR(left2_Byte).booleanValue() || UnitTools.warning_P(left2_Byte).booleanValue() || UnitTools.warning_Signal(left2_Byte).booleanValue()) {
//                    if (left2_TyrePressure > retPH) {
//                        left2_TyrePressure_Low_count = 0;
//                        if (left2_TyrePressure_Hight_count > DEF_WARN_COUNT) {
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u5de6\u540e\u8f6e\u538b\u529b\u8fc7\u9ad8\uff0c\u8bf7\u6ce8\u610f");
//                            left2_TyrePressure_Hight_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_HIGH_TIRE_PRESSURE, "");
//                        }
//                        left2_TyrePressure_Hight_count++;
//                    } else if (left2_TyrePressure < retPL) {
//                        left2_TyrePressure_Hight_count = 0;
//                        if (left2_TyrePressure_Low_count > DEF_WARN_COUNT) {
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u5de6\u540e\u8f6e\u538b\u529b\u8fc7\u4f4e\uff0c\u8bf7\u6ce8\u610f");
//                            left2_TyrePressure_Low_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_LOW_TIRE_PRESSURE, "");
//                        }
//                        left2_TyrePressure_Low_count++;
//                    } else {
//                        left2_TyrePressure_Hight_count = 0;
//                        left2_TyrePressure_Low_count = 0;
//                    }
//                    if (left2_TyreTemperature > retHT) {
//                        if (left2_TyreTemperature_Hight_count > DEF_WARN_COUNT) {
//                            left2_TyreTemperature_Hight_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u5de6\u540e\u8f6e\u6e29\u5ea6\u8fc7\u9ad8\uff0c\u8bf7\u6ce8\u610f");
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_HIGH_TIRE_TEMPERATURE, "");
//                        }
//                        left2_TyreTemperature_Hight_count++;
//                    } else {
//                        left2_TyreTemperature_Hight_count = 0;
//                    }
//                    if (UnitTools.warning_AIR(left2_Byte).booleanValue()) {
//                        sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u5de6\u540e\u8f6e\u6f0f\u6c14\uff0c\u8bf7\u6ce8\u610f");
//                        sendServerMessage(this.serviceHandler, MESSAGE_WARN_TIRE_LEAK, "");
//                    }
//                    if (UnitTools.warning_P(left2_Byte).booleanValue()) {
//                        sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u5de6\u540e\u8f6e\u7535\u6c60\u7535\u538b\u4f4e\uff0c\u8bf7\u6ce8\u610f");
//                        sendServerMessage(this.serviceHandler, MESSAGE_WARN_LOW_BATTERY, "");
//                    }
//                    if (UnitTools.warning_Signal(left2_Byte).booleanValue()) {
//                        if (left2_Warning_Signal_count > DEF_WARN_COUNT + 8) {
//                            left2_Warning_Signal_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u5de6\u540e\u8f6e\u4fe1\u53f7\u4e22\u5931\uff0c\u8bf7\u6ce8\u610f");
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_NO_RF_SIGNAL, "");
//                        }
//                        left2_Warning_Signal_count++;
//                    } else {
//                        left2_Warning_Signal_count = 0;
//                    }
//                } else {
//                    left2_TyrePressure_Hight_count = 0;
//                    left2_TyrePressure_Low_count = 0;
//                    left2_TyreTemperature_Hight_count = 0;
//                    left2_Warning_Signal_count = 0;
//                }
//            }
//            if (ret == 3) {
//                if (right1_TyrePressure > retPH || right1_TyrePressure < retPL || right1_TyreTemperature > retHT || UnitTools.warning_AIR(right1_Byte).booleanValue() || UnitTools.warning_P(right1_Byte).booleanValue() || UnitTools.warning_Signal(right1_Byte).booleanValue()) {
//                    if (right1_TyrePressure > retPH) {
//                        right1_TyrePressure_Low_count = 0;
//                        if (right1_TyrePressure_Hight_count > DEF_WARN_COUNT) {
//                            right1_TyrePressure_Hight_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u53f3\u524d\u8f6e\u538b\u529b\u8fc7\u9ad8\uff0c\u8bf7\u6ce8\u610f");
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_HIGH_TIRE_PRESSURE, "");
//                        }
//                        right1_TyrePressure_Hight_count++;
//                    } else if (right1_TyrePressure < retPL) {
//                        right1_TyrePressure_Hight_count = 0;
//                        if (right1_TyrePressure_Low_count > DEF_WARN_COUNT) {
//                            right1_TyrePressure_Low_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u53f3\u524d\u8f6e\u538b\u529b\u8fc7\u4f4e\uff0c\u8bf7\u6ce8\u610f");
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_LOW_TIRE_PRESSURE, "");
//                        }
//                        right1_TyrePressure_Low_count++;
//                    } else {
//                        right1_TyrePressure_Hight_count = 0;
//                        right1_TyrePressure_Low_count = 0;
//                    }
//                    if (right1_TyreTemperature > retHT) {
//                        if (right1_TyreTemperature_Hight_count > DEF_WARN_COUNT) {
//                            right1_TyreTemperature_Hight_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u53f3\u524d\u8f6e\u6e29\u5ea6\u8fc7\u9ad8\uff0c\u8bf7\u6ce8\u610f");
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_HIGH_TIRE_TEMPERATURE, "");
//                        }
//                        right1_TyreTemperature_Hight_count++;
//                    } else {
//                        right1_TyreTemperature_Hight_count = 0;
//                    }
//                    if (UnitTools.warning_AIR(right1_Byte).booleanValue()) {
//                        sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u53f3\u524d\u8f6e\u6f0f\u6c14\uff0c\u8bf7\u6ce8\u610f");
//                        sendServerMessage(this.serviceHandler, MESSAGE_WARN_TIRE_LEAK, "");
//                    }
//                    if (UnitTools.warning_P(right1_Byte).booleanValue()) {
//                        sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u53f3\u524d\u8f6e\u7535\u6c60\u7535\u538b\u4f4e\uff0c\u8bf7\u6ce8\u610f");
//                        sendServerMessage(this.serviceHandler, MESSAGE_WARN_LOW_BATTERY, "");
//                    }
//                    if (UnitTools.warning_Signal(right1_Byte).booleanValue()) {
//                        if (right1_Warning_Signal_count > DEF_WARN_COUNT + 8) {
//                            right1_Warning_Signal_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u53f3\u524d\u8f6e\u4fe1\u53f7\u4e22\u5931\uff0c\u8bf7\u6ce8\u610f");
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_NO_RF_SIGNAL, "");
//                        }
//                        right1_Warning_Signal_count++;
//                    } else {
//                        right1_Warning_Signal_count = 0;
//                    }
//                } else {
//                    right1_TyrePressure_Hight_count = 0;
//                    right1_TyrePressure_Low_count = 0;
//                    right1_TyreTemperature_Hight_count = 0;
//                    right1_Warning_Signal_count = 0;
//                }
//            }
//            if (ret == 4) {
//                if (right2_TyrePressure > retPH || right2_TyrePressure < retPL || right2_TyreTemperature > retHT || UnitTools.warning_AIR(right2_Byte).booleanValue() || UnitTools.warning_P(right2_Byte).booleanValue() || UnitTools.warning_Signal(right2_Byte).booleanValue()) {
//                    if (right2_TyrePressure > retPH) {
//                        right2_TyrePressure_Low_count = 0;
//                        if (right2_TyrePressure_Hight_count > DEF_WARN_COUNT) {
//                            right2_TyrePressure_Hight_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u53f3\u540e\u8f6e\u538b\u529b\u8fc7\u9ad8\uff0c\u8bf7\u6ce8\u610f");
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_HIGH_TIRE_PRESSURE, "");
//                        }
//                        right2_TyrePressure_Hight_count++;
//                    } else if (right2_TyrePressure < retPL) {
//                        right2_TyrePressure_Hight_count = 0;
//                        if (right2_TyrePressure_Low_count > DEF_WARN_COUNT) {
//                            right2_TyrePressure_Low_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u53f3\u540e\u8f6e\u538b\u529b\u8fc7\u4f4e\uff0c\u8bf7\u6ce8\u610f");
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_LOW_TIRE_PRESSURE, "");
//                        }
//                        right2_TyrePressure_Low_count++;
//                    } else {
//                        right2_TyrePressure_Hight_count = 0;
//                        right2_TyrePressure_Low_count = 0;
//                    }
//                    if (right2_TyreTemperature > retHT) {
//                        if (right2_TyreTemperature_Hight_count > DEF_WARN_COUNT) {
//                            right2_TyreTemperature_Hight_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u53f3\u540e\u8f6e\u6e29\u5ea6\u8fc7\u9ad8\uff0c\u8bf7\u6ce8\u610f");
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_HIGH_TIRE_TEMPERATURE, "");
//                        }
//                        right2_TyreTemperature_Hight_count++;
//                    } else {
//                        right2_TyreTemperature_Hight_count = 0;
//                    }
//                    if (UnitTools.warning_AIR(right2_Byte).booleanValue()) {
//                        sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u53f3\u540e\u8f6e\u6f0f\u6c14\uff0c\u8bf7\u6ce8\u610f");
//                        sendServerMessage(this.serviceHandler, MESSAGE_WARN_TIRE_LEAK, "");
//                    }
//                    if (UnitTools.warning_P(right2_Byte).booleanValue()) {
//                        sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u53f3\u540e\u8f6e\u7535\u6c60\u7535\u538b\u4f4e\uff0c\u8bf7\u6ce8\u610f");
//                        sendServerMessage(this.serviceHandler, MESSAGE_WARN_LOW_BATTERY, "");
//                    }
//                    if (UnitTools.warning_Signal(right2_Byte).booleanValue()) {
//                        if (right2_Warning_Signal_count > DEF_WARN_COUNT + 8) {
//                            right2_Warning_Signal_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u53f3\u540e\u8f6e\u4fe1\u53f7\u4e22\u5931\uff0c\u8bf7\u6ce8\u610f");
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_NO_RF_SIGNAL, "");
//                        }
//                        right2_Warning_Signal_count++;
//                    } else {
//                        right2_Warning_Signal_count = 0;
//                    }
//                } else {
//                    right2_TyrePressure_Hight_count = 0;
//                    right2_TyrePressure_Low_count = 0;
//                    right2_TyreTemperature_Hight_count = 0;
//                    right2_Warning_Signal_count = 0;
//                }
//            }
//            if (ret == 5 && getBackUpTyreStaus().booleanValue()) {
//                if (backup_TyrePressure > retPH || backup_TyrePressure < retPL || backup_TyreTemperature > retHT || UnitTools.warning_AIR(backup_Byte).booleanValue() || UnitTools.warning_P(backup_Byte).booleanValue() || UnitTools.warning_Signal(backup_Byte).booleanValue()) {
//                    if (backup_TyrePressure > retPH) {
//                        backup_TyrePressure_Low_count = 0;
//                        if (backup_TyrePressure_Hight_count > DEF_WARN_COUNT) {
//                            backup_TyrePressure_Hight_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u540e\u5907\u80ce\u538b\u529b\u8fc7\u9ad8\uff0c\u8bf7\u6ce8\u610f");
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_HIGH_TIRE_PRESSURE, "");
//                        }
//                        backup_TyrePressure_Hight_count++;
//                    } else if (backup_TyrePressure < retPL) {
//                        backup_TyrePressure_Hight_count = 0;
//                        if (backup_TyrePressure_Low_count > DEF_WARN_COUNT) {
//                            backup_TyrePressure_Low_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u540e\u5907\u80ce\u538b\u529b\u8fc7\u4f4e\uff0c\u8bf7\u6ce8\u610f");
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_LOW_TIRE_PRESSURE, "");
//                        }
//                        backup_TyrePressure_Low_count++;
//                    } else {
//                        backup_TyrePressure_Hight_count = 0;
//                        backup_TyrePressure_Low_count = 0;
//                    }
//                    if (backup_TyreTemperature > retHT) {
//                        if (backup_TyreTemperature_Hight_count > DEF_WARN_COUNT) {
//                            backup_TyreTemperature_Hight_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u540e\u5907\u80ce\u6e29\u5ea6\u8fc7\u9ad8\uff0c\u8bf7\u6ce8\u610f");
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_HIGH_TIRE_TEMPERATURE, "");
//                        }
//                        backup_TyreTemperature_Hight_count++;
//                    } else {
//                        backup_TyreTemperature_Hight_count = 0;
//                    }
//                    if (UnitTools.warning_AIR(backup_Byte).booleanValue()) {
//                        sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u540e\u5907\u80ce\u6f0f\u6c14\uff0c\u8bf7\u6ce8\u610f");
//                        sendServerMessage(this.serviceHandler, MESSAGE_WARN_TIRE_LEAK, "");
//                    }
//                    if (UnitTools.warning_P(backup_Byte).booleanValue()) {
//                        sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u540e\u5907\u80ce\u7535\u6c60\u7535\u538b\u4f4e\uff0c\u8bf7\u6ce8\u610f");
//                        sendServerMessage(this.serviceHandler, MESSAGE_WARN_LOW_BATTERY, "");
//                    }
//                    if (UnitTools.warning_Signal(backup_Byte).booleanValue()) {
//                        if (backup_Warning_Signal_count > DEF_WARN_COUNT + 8) {
//                            backup_Warning_Signal_count = 0;
//                            sendServerMessage(this.serviceHandler, MESSAGE_VOICE_SPEK, "\u540e\u5907\u80ce\u4fe1\u53f7\u4e22\u5931\uff0c\u8bf7\u6ce8\u610f");
//                            sendServerMessage(this.serviceHandler, MESSAGE_WARN_NO_RF_SIGNAL, "");
//                        }
//                        backup_Warning_Signal_count++;
//                    } else {
//                        backup_Warning_Signal_count = 0;
//                    }
//                } else {
//                    backup_TyrePressure_Hight_count = 0;
//                    backup_TyrePressure_Low_count = 0;
//                    backup_TyreTemperature_Hight_count = 0;
//                    backup_Warning_Signal_count = 0;
//                }
//            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static byte[] getMergeBytes(byte[] pByteA, int numA, byte[] pByteB, int numB) {
        int i;
        byte[] b = new byte[(numA + numB)];
        for (i = 0; i < numA; i++) {
            b[i] = pByteA[i];
        }
        for (i = 0; i < numB; i++) {
            b[numA + i] = pByteB[i];
        }
        return b;
    }

    private void dealData(byte[] buff) {
        int len = buff.length / LT_PROGRESS_STATAR;
        int i=0;
        if (mHandlerSeriaTest != null) {
            sendMessage(mHandlerSeriaTest, buff);
        }
        if (DEBUG && buff != null) {
            etScanResults.append( "cz1 buff " + bytesToHexString(buff) + " len " + buff.length);
        }
        if (buff == null) {
            etScanResults.append( "cz2 buff null " + bytesToHexString(buff) + " len " + buff.length);
        } else if (buff.length > 3 && buff[0] == (byte) 85 && buff[1] == (byte) -86 && buff.length >= buff[P_UNIT] && isDataBoolean(buff, buff[P_UNIT])) {
            if (mHandler != null) {
                sendMessage(mHandler, buff);
            }
            if (this.mTimerHandShake == null && !HandShake.booleanValue()) {
                startTimerHandShake();
                setTimeNsHandShake(1);
            }
            isDataWarn(buff);
            if (buff.length > buff[P_UNIT]) {
                int buff_len = buff[P_UNIT];
                i = 0;
                while (i < buff.length - buff_len) {
                    if (i + buff_len < buff.length && i < buf_temp.length) {
                        buf_temp[i] = buff[i + buff_len];
                    }
                    i++;
                }
                if (i > 0) {
                    this.buf_temp_len = buff.length - buff_len;
                }
            }
        } else {
            if (buff.length < 20 && buf_temp.length > this.buf_temp_len + buff.length) {
                if (DEBUG) {
                    etScanResults.append( "cz44 buf_temp " + bytesToHexString(buf_temp) + "  buf_temp_len " + this.buf_temp_len);
                }
                temp = getMergeBytes(buf_temp, this.buf_temp_len, buff, buff.length);
                for (i = 0; i < temp.length; i++) {
                    buf_temp[i] = temp[i];
                }
                this.buf_temp_len = temp.length;
                if (DEBUG) {
                    etScanResults.append( "cz44--- buf_temp " + bytesToHexString(buf_temp) + "  buf_temp_len " + this.buf_temp_len + " temp.length " + temp.length);
                }
            }
            if (DEBUG) {
                etScanResults.append( "cz5 buf_temp " + bytesToHexString(buf_temp) + "  buf_temp_len " + this.buf_temp_len);
            }
            i = 0;
            while (i < this.buf_temp_len) {
                if (buf.length - 1 > i && buf_temp.length - 1 > i) {
                    buf[i] = buf_temp[i];
                    buf_temp[i] = (byte) 0;
                }
                i++;
            }
            this.buf_len = this.buf_temp_len;
            if (DEBUG) {
                etScanResults.append( "cz5-- buf " + bytesToHexString(buf) + "  buf_len " + this.buf_len);
            }
            this.data_head_falg = false;
            this.f533j = 0;
            i = 0;
            while (i < this.buf_len) {
                if (i + 1 < buf.length && buf[i] == (byte) 85 && buf[i + 1] == (byte) -86) {
                    this.data_head_falg = true;
                    this.f533j = 0;
                    break;
                }
                i++;
            }
            if (DEBUG) {
                etScanResults.append( "cz556-- " + this.data_head_falg + " buf " + bytesToHexString(buf) + "  i " + i);
            }
            if (this.data_head_falg) {
                this.f533j = 0;
                while (i < this.buf_len) {
                    if (buf.length > i && buf_temp.length > this.f533j) {
                        buf_temp[this.f533j] = buf[i];
                    }
                    this.f533j++;
                    i++;
                }
            }
            if (this.data_head_falg) {
                this.buf_temp_len = this.f533j;
            }
            if (DEBUG) {
                etScanResults.append( "cz--77 " + this.data_head_falg + " buf " + bytesToHexString(buf_temp) + "  buf_temp_len " + this.buf_temp_len);
            }
            if (this.buf_temp_len < buf_temp.length - 10 && this.buf_temp_len > 5 && this.data_head_falg && this.buf_temp_len >= buf_temp[P_UNIT] && isDataBoolean(buf_temp, buf_temp[P_UNIT])) {
                if (DEBUG) {
                    etScanResults.append( "cz6 buf_temp " + bytesToHexString(buf_temp) + "  buf_temp_len " + this.buf_temp_len + "  len " + buf_temp[P_UNIT]);
                }
                byte[] b = new byte[buf_temp[P_UNIT]];
                for (byte y = (byte) 0; y < buf_temp[P_UNIT]; y++) {
                    b[y] = buf_temp[y];
                }
                if (this.buf_temp_len > buf_temp[P_UNIT]) {
                    i = 0;
                    while (i < this.buf_temp_len - buf_temp[P_UNIT]) {
                        if (buf_temp[P_UNIT] + i < buf_temp.length && i < buf_temp.length) {
                            buf_temp[i] = buf_temp[buf_temp[P_UNIT] + i];
                        }
                        i++;
                    }
                    if (i > 0) {
                        this.buf_temp_len = i;
                    }
                } else {
                    for (int ii = 0; ii < buf_temp.length; ii++) {
                        buf_temp[ii] = (byte) 0;
                    }
                    this.buf_temp_len = 0;
                }
                if (this.mTimerHandShake == null && !HandShake.booleanValue()) {
                    startTimerHandShake();
                    setTimeNsHandShake(1);
                }
                if (mHandler != null) {
                    sendMessage(mHandler, b);
                }
                isDataWarn(b);
                if (DEBUG) {
                    etScanResults.append( "cz7 buf_temp " + bytesToHexString(b));
                }
            }
            if (this.buf_temp_len > buf_temp.length - 2) {
                this.buf_temp_len = 0;
            }
            if (this.buf_len > buf.length - 2) {
                this.buf_len = 0;
            }
        }
    }

    class SerialTPMSListener implements SerialInputOutputManager.Listener {
        SerialTPMSListener() {
        }

        public void onRunError(Exception e) {
            if (DEBUG) {
                etScanResults.append("Runner stopped.");
            }
        }

        public void onNewData(byte[] data) {
            if (DEBUG) {
                etScanResults.append("cz onNewData" + bytesToHexString(data));
            }
            dealData(data);
            try {
                byte[] bArr = new byte[6];
                bArr[0] = (byte) 85;
                bArr[1] = (byte) -86;
                bArr[P_UNIT] = (byte) 6;
                bArr[3] = (byte) 25;
                writeData(sum(bArr));
            } catch (Exception e) {
            }
        }
    }

    private void stopTimerHandShake() {
        if (this.mTimerHandShake != null) {
            this.mTimerHandShake.cancel();
            this.mTimerHandShake = null;
        }
        if (this.mTimerTaskHandShake != null) {
            this.mTimerTaskHandShake.cancel();
            this.mTimerTaskHandShake = null;
        }
    }

    class HandShakeTimerTask extends TimerTask {
        HandShakeTimerTask() {
        }

        public void run() {
            if (HandShake.booleanValue()) {
                stopTimerHandShake();
                return;
            }
            if (DEBUG) {
                etScanResults.append( "HandShakeCount " + HandShakeCount + " " + time);
            }
            try {
                byte[] bArr = new byte[6];
                bArr[0] = (byte) 85;
                bArr[1] = (byte) -86;
                bArr[P_UNIT] = (byte) 6;
                bArr[3] = (byte) 90;
                bArr[4] = time;
                writeData(sum(bArr));
            } catch (Exception e) {
            }
            HandShakeCount = HandShakeCount + 1;
            if (HandShakeCount > HandShakeTotal) {
                if (mHandler != null) {
                    mHandler.sendEmptyMessage(MESSAGE_HANDSHAKE_NO);
                }
                HandShakeCount = HandShakeTotal + 1;
                stopTimerHandShake();
            }
        }
    }
}

