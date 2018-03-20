/**
 * Created by estarcev on 13.03.2018.
 */
package usbserial.cz.com.tpmsreader.Driver;


import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Log;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static android.view.MotionEvent.ACTION_MASK;

public class ProlificSerialDriver implements UsbSerialDriver {
    private final String TAG = ProlificSerialDriver.class.getSimpleName();
    private  UsbDevice mDevice;
    private final UsbSerialPort mPort;

    class ProlificSerialPort extends CommonUsbSerialPort {
        private static final int CONTROL_DTR = 1;
        private static final int CONTROL_RTS = 2;
        private static final int DEVICE_TYPE_0 = 1;
        private static final int DEVICE_TYPE_1 = 2;
        private static final int DEVICE_TYPE_HX = 0;
        private static final int FLUSH_RX_REQUEST = 8;
        private static final int FLUSH_TX_REQUEST = 9;
        private static final int INTERRUPT_ENDPOINT = 129;
        private static final int PROLIFIC_CTRL_OUT_REQTYPE = 33;
        private static final int PROLIFIC_VENDOR_IN_REQTYPE = 192;
        private static final int PROLIFIC_VENDOR_OUT_REQTYPE = 64;
        private static final int PROLIFIC_VENDOR_READ_REQUEST = 1;
        private static final int PROLIFIC_VENDOR_WRITE_REQUEST = 1;
        private static final int READ_ENDPOINT = 131;
        private static final int SET_CONTROL_REQUEST = 34;
        private static final int SET_LINE_REQUEST = 32;
        private static final int STATUS_BUFFER_SIZE = 10;
        private static final int STATUS_BYTE_IDX = 8;
        private static final int STATUS_FLAG_CD = 1;
        private static final int STATUS_FLAG_CTS = 128;
        private static final int STATUS_FLAG_DSR = 2;
        private static final int STATUS_FLAG_RI = 8;
        private static final int USB_READ_TIMEOUT_MILLIS = 1000;
        private static final int USB_RECIP_INTERFACE = 1;
        private static final int USB_WRITE_TIMEOUT_MILLIS = 5000;
        private static final int WRITE_ENDPOINT = 2;
        private int mBaudRate = -1;
        private int mControlLinesValue = DEVICE_TYPE_HX;
        private int mDataBits = -1;
        private int mDeviceType = DEVICE_TYPE_HX;
        private UsbEndpoint mInterruptEndpoint;
        private int mParity = -1;
        private UsbEndpoint mReadEndpoint;
        private IOException mReadStatusException = null;
        private volatile Thread mReadStatusThread = null;
        private final Object mReadStatusThreadLock = new Object();
        private int mStatus = DEVICE_TYPE_HX;
        private int mStopBits = -1;
        boolean mStopReadStatusThread = false;
        private UsbEndpoint mWriteEndpoint;

        class C01551 implements Runnable {
            C01551() {
            }

            public void run() {
                ProlificSerialPort.this.readStatusThreadFunction();
            }
        }

        public ProlificSerialPort(UsbDevice device, int portNumber) {
            super(device, portNumber);
        }

        public UsbSerialDriver getDriver() {
            return ProlificSerialDriver.this;
        }

        private final byte[] inControlTransfer(int requestType, int request, int value, int index, int length) throws IOException {
            byte[] buffer = new byte[length];
            int result = this.mConnection.controlTransfer(requestType, request, value, index, buffer, length, USB_READ_TIMEOUT_MILLIS);
            if (result == length) {
                return buffer;
            }
            Object[] objArr = new Object[WRITE_ENDPOINT];
            objArr[DEVICE_TYPE_HX] = Integer.valueOf(value);
            objArr[USB_RECIP_INTERFACE] = Integer.valueOf(result);
            throw new IOException(String.format("ControlTransfer with value 0x%x failed: %d", objArr));
        }

        private final void outControlTransfer(int requestType, int request, int value, int index, byte[] data) throws IOException {
            int length = data == null ? DEVICE_TYPE_HX : data.length;
            int result = this.mConnection.controlTransfer(requestType, request, value, index, data, length, USB_WRITE_TIMEOUT_MILLIS);
            if (result != length) {
                Object[] objArr = new Object[WRITE_ENDPOINT];
                objArr[DEVICE_TYPE_HX] = Integer.valueOf(value);
                objArr[USB_RECIP_INTERFACE] = Integer.valueOf(result);
                throw new IOException(String.format("ControlTransfer with value 0x%x failed: %d", objArr));
            }
        }

        private final byte[] vendorIn(int value, int index, int length) throws IOException {
            return inControlTransfer(PROLIFIC_VENDOR_IN_REQTYPE, USB_RECIP_INTERFACE, value, index, length);
        }

        private final void vendorOut(int value, int index, byte[] data) throws IOException {
            outControlTransfer(PROLIFIC_VENDOR_OUT_REQTYPE, USB_RECIP_INTERFACE, value, index, data);
        }

        private void resetDevice() throws IOException {
            purgeHwBuffers(true, true);
        }

        private final void ctrlOut(int request, int value, int index, byte[] data) throws IOException {
            outControlTransfer(PROLIFIC_CTRL_OUT_REQTYPE, request, value, index, data);
        }

        private void doBlackMagic() throws IOException {
            vendorIn(33924, DEVICE_TYPE_HX, USB_RECIP_INTERFACE);
            vendorOut(1028, DEVICE_TYPE_HX, null);
            vendorIn(33924, DEVICE_TYPE_HX, USB_RECIP_INTERFACE);
            vendorIn(33667, DEVICE_TYPE_HX, USB_RECIP_INTERFACE);
            vendorIn(33924, DEVICE_TYPE_HX, USB_RECIP_INTERFACE);
            vendorOut(1028, USB_RECIP_INTERFACE, null);
            vendorIn(33924, DEVICE_TYPE_HX, USB_RECIP_INTERFACE);
            vendorIn(33667, DEVICE_TYPE_HX, USB_RECIP_INTERFACE);
            vendorOut(DEVICE_TYPE_HX, USB_RECIP_INTERFACE, null);
            vendorOut(USB_RECIP_INTERFACE, DEVICE_TYPE_HX, null);
            vendorOut(WRITE_ENDPOINT, this.mDeviceType == 0 ? 68 : 36, null);
        }

        private void setControlLines(int newControlLinesValue) throws IOException {
            ctrlOut(SET_CONTROL_REQUEST, newControlLinesValue, DEVICE_TYPE_HX, null);
            this.mControlLinesValue = newControlLinesValue;
        }

        private final void readStatusThreadFunction() {
            while (!this.mStopReadStatusThread) {
                try {
                    byte[] buffer = new byte[STATUS_BUFFER_SIZE];
                    int readBytesCount = this.mConnection.bulkTransfer(this.mInterruptEndpoint, buffer, STATUS_BUFFER_SIZE, 500/*TTSPlayThread.DEFAULT_PLAY_START_BUFFER_TIME*/);
                    if (readBytesCount > 0) {
                        if (readBytesCount == STATUS_BUFFER_SIZE) {
                            this.mStatus = buffer[STATUS_FLAG_RI] & ACTION_MASK;
                        } else {
                            Object[] objArr = new Object[WRITE_ENDPOINT];
                            objArr[DEVICE_TYPE_HX] = Integer.valueOf(STATUS_BUFFER_SIZE);
                            objArr[USB_RECIP_INTERFACE] = Integer.valueOf(readBytesCount);
                            throw new IOException(String.format("Invalid CTS / DSR / CD / RI status buffer received, expected %d bytes, but received %d", objArr));
                        }
                    }
                } catch (IOException e) {
                    this.mReadStatusException = e;
                    return;
                }
            }
        }

        private final int getStatus() throws IOException {
            if (this.mReadStatusThread == null && this.mReadStatusException == null) {
                synchronized (this.mReadStatusThreadLock) {
                    if (this.mReadStatusThread == null) {
                        byte[] buffer = new byte[STATUS_BUFFER_SIZE];
                        if (this.mConnection.bulkTransfer(this.mInterruptEndpoint, buffer, STATUS_BUFFER_SIZE, 100) != STATUS_BUFFER_SIZE) {
                            Log.w(ProlificSerialDriver.this.TAG, "Could not read initial CTS / DSR / CD / RI status");
                        } else {
                            this.mStatus = buffer[STATUS_FLAG_RI] & ACTION_MASK;
                        }
                        this.mReadStatusThread = new Thread(new C01551());
                        this.mReadStatusThread.setDaemon(true);
                        this.mReadStatusThread.start();
                    }
                }
            }
            IOException readStatusException = this.mReadStatusException;
            if (this.mReadStatusException == null) {
                return this.mStatus;
            }
            this.mReadStatusException = null;
            throw readStatusException;
        }

        private final boolean testStatusFlag(int flag) throws IOException {
            return (getStatus() & flag) == flag;
        }

        public void open(UsbDeviceConnection connection) throws IOException {
            if (this.mConnection != null) {
                throw new IOException("Already open");
            }
            UsbInterface usbInterface = this.mDevice.getInterface(DEVICE_TYPE_HX);
            if (connection.claimInterface(usbInterface, true)) {
                this.mConnection = connection;
                for (int i = DEVICE_TYPE_HX; i < usbInterface.getEndpointCount(); i += USB_RECIP_INTERFACE) {
                    UsbEndpoint currentEndpoint = usbInterface.getEndpoint(i);
                    switch (currentEndpoint.getAddress()) {
                        case WRITE_ENDPOINT /*2*/:
                            try {
                                this.mWriteEndpoint = currentEndpoint;
                                break;
                            } catch (Exception e2) {
                                Log.e(ProlificSerialDriver.this.TAG, "An unexpected exception occured while trying to detect PL2303 subtype", e2);
                                break;
//                            } catch (NoSuchMethodException e) {
//                                Log.w(ProlificSerialDriver.this.TAG, "Method UsbDeviceConnection.getRawDescriptors, required for PL2303 subtype detection, not available! Assuming that it is a HX device");
//                                this.mDeviceType = DEVICE_TYPE_HX;
//                                break;
                            } catch (Throwable th) {
                                if (!false) {
                                    this.mConnection = null;
                                    connection.releaseInterface(usbInterface);
                                }
                            }
                        case INTERRUPT_ENDPOINT /*129*/:
                            this.mInterruptEndpoint = currentEndpoint;
                            break;
                        case READ_ENDPOINT /*131*/:
                            this.mReadEndpoint = currentEndpoint;
                            break;
                        default:
                            break;
                    }
                }
                boolean bb= false;
                try {
                    bb = ((byte[]) this.mConnection.getClass().getMethod("getRawDescriptors", new Class[DEVICE_TYPE_HX]).invoke(this.mConnection, new Object[DEVICE_TYPE_HX]))[7] == (byte) 64;
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                }
                if (this.mDevice.getDeviceClass() == WRITE_ENDPOINT) {
                    this.mDeviceType = USB_RECIP_INTERFACE;
                } else if (bb){
                    this.mDeviceType = DEVICE_TYPE_HX;
                } else if (this.mDevice.getDeviceClass() == 0 || this.mDevice.getDeviceClass() == ACTION_MASK) {
                    this.mDeviceType = WRITE_ENDPOINT;
                } else {
                    Log.w(ProlificSerialDriver.this.TAG, "Could not detect PL2303 subtype, Assuming that it is a HX device");
                    this.mDeviceType = DEVICE_TYPE_HX;
                }
                setControlLines(this.mControlLinesValue);
                resetDevice();
                doBlackMagic();
                if (!true) {
                    this.mConnection = null;
                    connection.releaseInterface(usbInterface);
                    return;
                }
                return;
            }
            throw new IOException("Error claiming Prolific interface 0");
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void close() throws IOException {
            if (this.mConnection == null) {
                throw new IOException("Already closed");
            }
            try {
                this.mStopReadStatusThread = true;
                synchronized (this.mReadStatusThreadLock) {
                    if (this.mReadStatusThread != null) {
                        try {
                            this.mReadStatusThread.join();
                        } catch (Exception e) {
                            Log.w(ProlificSerialDriver.this.TAG, "An error occured while waiting for status read thread", e);
                        }
                    }
                }
                resetDevice();
            } finally {
                try {
                    this.mConnection.releaseInterface(this.mDevice.getInterface(DEVICE_TYPE_HX));
                } finally {
                    this.mConnection = null;
                }
            }
        }

        public int read(byte[] dest, int timeoutMillis) throws IOException {
            synchronized (this.mReadBufferLock) {
                int numBytesRead = this.mConnection.bulkTransfer(this.mReadEndpoint, this.mReadBuffer, Math.min(dest.length, this.mReadBuffer.length), timeoutMillis);
                if (numBytesRead < 0) {
                    return DEVICE_TYPE_HX;
                }
                System.arraycopy(this.mReadBuffer, DEVICE_TYPE_HX, dest, DEVICE_TYPE_HX, numBytesRead);
                return numBytesRead;
            }
        }

        public int write(byte[] src, int timeoutMillis) throws IOException {
            int offset = DEVICE_TYPE_HX;
            int amtWritten=0;
            int writeLength=0;
            while (offset < src.length) {
                synchronized (this.mWriteBufferLock) {
                    byte[] writeBuffer;
                    writeLength = Math.min(src.length - offset, this.mWriteBuffer.length);
                    if (offset == 0) {
                        writeBuffer = src;
                    } else {
                        System.arraycopy(src, offset, this.mWriteBuffer, DEVICE_TYPE_HX, writeLength);
                        writeBuffer = this.mWriteBuffer;
                    }
                    amtWritten = this.mConnection.bulkTransfer(this.mWriteEndpoint, writeBuffer, writeLength, timeoutMillis);
                }
                if (amtWritten <= 0) {
                    throw new IOException("Error writing " + writeLength + " bytes at offset " + offset + " length=" + src.length);
                }
                offset += amtWritten;
            }
            return offset;
        }

        public void setParameters(int baudRate, int dataBits, int stopBits, int parity) throws IOException {
            if (this.mBaudRate != baudRate || this.mDataBits != dataBits || this.mStopBits != stopBits || this.mParity != parity) {
                byte[] lineRequestData = new byte[7];
                lineRequestData[DEVICE_TYPE_HX] = (byte) (baudRate & ACTION_MASK);
                lineRequestData[USB_RECIP_INTERFACE] = (byte) ((baudRate >> STATUS_FLAG_RI) & ACTION_MASK);
                lineRequestData[WRITE_ENDPOINT] = (byte) ((baudRate >> 16) & ACTION_MASK);
                lineRequestData[3] = (byte) ((baudRate >> 24) & ACTION_MASK);
                switch (stopBits) {
                    case USB_RECIP_INTERFACE /*1*/:
                        lineRequestData[4] = (byte) 0;
                        break;
                    case WRITE_ENDPOINT /*2*/:
                        lineRequestData[4] = (byte) 2;
                        break;
//                    case DownloadTask.PAUSED /*3*/:
//                        lineRequestData[4] = (byte) 1;
//                        break;
                    default:
                        throw new IllegalArgumentException("Unknown stopBits value: " + stopBits);
                }
                switch (parity) {
                    case DEVICE_TYPE_HX /*0*/:
                        lineRequestData[5] = (byte) 0;
                        break;
                    case USB_RECIP_INTERFACE /*1*/:
                        lineRequestData[5] = (byte) 1;
                        break;
                    case WRITE_ENDPOINT /*2*/:
                        lineRequestData[5] = (byte) 2;
                        break;
//                    case DownloadTask.PAUSED /*3*/:
//                        lineRequestData[5] = (byte) 3;
//                        break;
//                    case DownloadTask.DELETED /*4*/:
//                        lineRequestData[5] = (byte) 4;
//                        break;
                    default:
                        throw new IllegalArgumentException("Unknown parity value: " + parity);
                }
                lineRequestData[6] = (byte) dataBits;
                ctrlOut(SET_LINE_REQUEST, DEVICE_TYPE_HX, DEVICE_TYPE_HX, lineRequestData);
                resetDevice();
                this.mBaudRate = baudRate;
                this.mDataBits = dataBits;
                this.mStopBits = stopBits;
                this.mParity = parity;
            }
        }

        public boolean getCD() throws IOException {
            return testStatusFlag(USB_RECIP_INTERFACE);
        }

        public boolean getCTS() throws IOException {
            return testStatusFlag(STATUS_FLAG_CTS);
        }

        public boolean getDSR() throws IOException {
            return testStatusFlag(WRITE_ENDPOINT);
        }

        public boolean getDTR() throws IOException {
            return (this.mControlLinesValue & USB_RECIP_INTERFACE) == USB_RECIP_INTERFACE;
        }

        public void setDTR(boolean value) throws IOException {
            int newControlLinesValue;
            if (value) {
                newControlLinesValue = this.mControlLinesValue | USB_RECIP_INTERFACE;
            } else {
                newControlLinesValue = this.mControlLinesValue & -2;
            }
            setControlLines(newControlLinesValue);
        }

        public boolean getRI() throws IOException {
            return testStatusFlag(STATUS_FLAG_RI);
        }

        public boolean getRTS() throws IOException {
            return (this.mControlLinesValue & WRITE_ENDPOINT) == WRITE_ENDPOINT;
        }

        public void setRTS(boolean value) throws IOException {
            int newControlLinesValue;
            if (value) {
                newControlLinesValue = this.mControlLinesValue | WRITE_ENDPOINT;
            } else {
                newControlLinesValue = this.mControlLinesValue & -3;
            }
            setControlLines(newControlLinesValue);
        }

        public boolean purgeHwBuffers(boolean purgeReadBuffers, boolean purgeWriteBuffers) throws IOException {
            if (purgeReadBuffers) {
                vendorOut(STATUS_FLAG_RI, DEVICE_TYPE_HX, null);
            }
            if (purgeWriteBuffers) {
                vendorOut(FLUSH_TX_REQUEST, DEVICE_TYPE_HX, null);
            }
            if (purgeReadBuffers || purgeWriteBuffers) {
                return true;
            }
            return false;
        }
    }

    public ProlificSerialDriver(UsbDevice device) {
        this.mDevice = device;
        this.mPort = new ProlificSerialPort(this.mDevice, 0);
    }

    public List<UsbSerialPort> getPorts() {
        return Collections.singletonList(this.mPort);
    }

    public UsbDevice getDevice() {
        return this.mDevice;
    }

    public static Map<Integer, int[]> getSupportedDevices() {
        Map<Integer, int[]> supportedDevices = new LinkedHashMap();
        supportedDevices.put(Integer.valueOf(UsbId.VENDOR_PROLIFIC), new int[]{8963});
        return supportedDevices;
    }
}
