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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static android.view.MotionEvent.ACTION_MASK;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT;

public class Cp21xxSerialDriver implements UsbSerialDriver {
    private static final String TAG = Cp21xxSerialDriver.class.getSimpleName();
    private  UsbDevice mDevice = null;
    private final UsbSerialPort mPort = new Cp21xxSerialPort(this.mDevice, 0);

    public class Cp21xxSerialPort extends CommonUsbSerialPort {
        private static final int BAUD_RATE_GEN_FREQ = 3686400;
        private static final int CONTROL_WRITE_DTR = 256;
        private static final int CONTROL_WRITE_RTS = 512;
        private static final int DEFAULT_BAUD_RATE = 9600;
        private static final int FLUSH_READ_CODE = 10;
        private static final int FLUSH_WRITE_CODE = 5;
        private static final int MCR_ALL = 3;
        private static final int MCR_DTR = 1;
        private static final int MCR_RTS = 2;
        private static final int REQTYPE_HOST_TO_DEVICE = 65;
        private static final int SILABSER_FLUSH_REQUEST_CODE = 18;
        private static final int SILABSER_IFC_ENABLE_REQUEST_CODE = 0;
        private static final int SILABSER_SET_BAUDDIV_REQUEST_CODE = 1;
        private static final int SILABSER_SET_BAUDRATE = 30;
        private static final int SILABSER_SET_LINE_CTL_REQUEST_CODE = 3;
        private static final int SILABSER_SET_MHS_REQUEST_CODE = 7;
        private static final int UART_DISABLE = 0;
        private static final int UART_ENABLE = 1;
        private static final int USB_WRITE_TIMEOUT_MILLIS = 5000;
        private UsbEndpoint mReadEndpoint;
        private UsbEndpoint mWriteEndpoint;

        public /* bridge */ /* synthetic */ int getPortNumber() {
            return super.getPortNumber();
        }

        public /* bridge */ /* synthetic */ String getSerial() {
            return super.getSerial();
        }

        public /* bridge */ /* synthetic */ String toString() {
            return super.toString();
        }

        public Cp21xxSerialPort(UsbDevice device, int portNumber) {
            super(device, portNumber);
        }

        public UsbSerialDriver getDriver() {
            return Cp21xxSerialDriver.this;
        }

        private int setConfigSingle(int request, int value) {
            return this.mConnection.controlTransfer(REQTYPE_HOST_TO_DEVICE, request, value, UART_DISABLE, null, UART_DISABLE, USB_WRITE_TIMEOUT_MILLIS);
        }

        public void open(UsbDeviceConnection connection) throws IOException {
            if (this.mConnection != null) {
                throw new IOException("Already opened.");
            }
            this.mConnection = connection;
            int i = UART_DISABLE;
            while (i < this.mDevice.getInterfaceCount()) {
                try {
                    if (this.mConnection.claimInterface(this.mDevice.getInterface(i), true)) {
                        Log.d(Cp21xxSerialDriver.TAG, "claimInterface " + i + " SUCCESS");
                    } else {
                        Log.d(Cp21xxSerialDriver.TAG, "claimInterface " + i + " FAIL");
                    }
                    i += UART_ENABLE;
                } catch (Throwable th) {
                    if (!false) {
                        try {
                            close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
            UsbInterface dataIface = this.mDevice.getInterface(this.mDevice.getInterfaceCount() - 1);
            for (i = UART_DISABLE; i < dataIface.getEndpointCount(); i += UART_ENABLE) {
                UsbEndpoint ep = dataIface.getEndpoint(i);
                if (ep.getType() == MCR_RTS) {
                    if (ep.getDirection() == FtdiSerialDriver.FtdiSerialPort.USB_ENDPOINT_IN) {
                        this.mReadEndpoint = ep;
                    } else {
                        this.mWriteEndpoint = ep;
                    }
                }
            }
            setConfigSingle(UART_DISABLE, UART_ENABLE);
            setConfigSingle(SILABSER_SET_MHS_REQUEST_CODE, 771);
            setConfigSingle(UART_ENABLE, 384);
            if (!true) {
                try {
                    close();
                } catch (IOException e2) {
                }
            }
        }

        public void close() throws IOException {
            if (this.mConnection == null) {
                throw new IOException("Already closed");
            }
            try {
                setConfigSingle(UART_DISABLE, UART_DISABLE);
                this.mConnection.close();
            } finally {
                this.mConnection = null;
            }
        }

        public int read(byte[] dest, int timeoutMillis) throws IOException {
            synchronized (this.mReadBufferLock) {
                int numBytesRead = this.mConnection.bulkTransfer(this.mReadEndpoint, this.mReadBuffer, Math.min(dest.length, this.mReadBuffer.length), timeoutMillis);
                if (numBytesRead < 0) {
                    return UART_DISABLE;
                }
                System.arraycopy(this.mReadBuffer, UART_DISABLE, dest, UART_DISABLE, numBytesRead);
                return numBytesRead;
            }
        }

        public int write(byte[] src, int timeoutMillis) throws IOException {
            int offset = UART_DISABLE;
            int amtWritten=0;
            int writeLength=0;
            while (offset < src.length) {
                synchronized (this.mWriteBufferLock) {
                    byte[] writeBuffer;
                    writeLength = Math.min(src.length - offset, this.mWriteBuffer.length);
                    if (offset == 0) {
                        writeBuffer = src;
                    } else {
                        System.arraycopy(src, offset, this.mWriteBuffer, UART_DISABLE, writeLength);
                        writeBuffer = this.mWriteBuffer;
                    }
                    amtWritten = this.mConnection.bulkTransfer(this.mWriteEndpoint, writeBuffer, writeLength, timeoutMillis);
                }
                if (amtWritten <= 0) {
                    throw new IOException("Error writing " + writeLength + " bytes at offset " + offset + " length=" + src.length);
                }
                Log.d(Cp21xxSerialDriver.TAG, "Wrote amt=" + amtWritten + " attempted=" + writeLength);
                offset += amtWritten;
            }
            return offset;
        }

        private void setBaudRate(int baudRate) throws IOException {
            if (this.mConnection.controlTransfer(REQTYPE_HOST_TO_DEVICE, SILABSER_SET_BAUDRATE, UART_DISABLE, UART_DISABLE, new byte[]{(byte) (baudRate & ACTION_MASK), (byte) ((baudRate >> 8) & ACTION_MASK), (byte) ((baudRate >> 16) & ACTION_MASK), (byte) ((baudRate >> 24) & ACTION_MASK)}, 4, USB_WRITE_TIMEOUT_MILLIS) < 0) {
                throw new IOException("Error setting baud rate.");
            }
        }

        public void setParameters(int baudRate, int dataBits, int stopBits, int parity) throws IOException {
            int configDataBits;
            setBaudRate(baudRate);
            switch (dataBits) {
                case FLUSH_WRITE_CODE /*5*/:
                    configDataBits = UART_DISABLE | 1280;
                    break;
                case 6: //C0159a.CRASHTYPE_COCOS2DX_LUA /*6*/:
                    configDataBits = UART_DISABLE | 1536;
                    break;
                case SILABSER_SET_MHS_REQUEST_CODE /*7*/:
                    configDataBits = UART_DISABLE | 1792;
                    break;
                case UsbSerialPort.FLOWCONTROL_XONXOFF_OUT /*8*/:
                    configDataBits = UART_DISABLE | ACTION_PREVIOUS_HTML_ELEMENT;
                    break;
                default:
                    configDataBits = UART_DISABLE | ACTION_PREVIOUS_HTML_ELEMENT;
                    break;
            }
            switch (parity) {
                case UART_ENABLE /*1*/:
                    configDataBits |= 16;
                    break;
                case MCR_RTS /*2*/:
                    configDataBits |= 32;
                    break;
            }
            switch (stopBits) {
                case UART_ENABLE /*1*/:
                    configDataBits |= UART_DISABLE;
                    break;
                case MCR_RTS /*2*/:
                    configDataBits |= MCR_RTS;
                    break;
            }
            setConfigSingle(SILABSER_SET_LINE_CTL_REQUEST_CODE, configDataBits);
        }

        public boolean getCD() throws IOException {
            return false;
        }

        public boolean getCTS() throws IOException {
            return false;
        }

        public boolean getDSR() throws IOException {
            return false;
        }

        public boolean getDTR() throws IOException {
            return true;
        }

        public void setDTR(boolean value) throws IOException {
        }

        public boolean getRI() throws IOException {
            return false;
        }

        public boolean getRTS() throws IOException {
            return true;
        }

        public void setRTS(boolean value) throws IOException {
        }

        public boolean purgeHwBuffers(boolean purgeReadBuffers, boolean purgeWriteBuffers) throws IOException {
            int i;
            int i2 = UART_DISABLE;
            if (purgeReadBuffers) {
                i = FLUSH_READ_CODE;
            } else {
                i = UART_DISABLE;
            }
            if (purgeWriteBuffers) {
                i2 = FLUSH_WRITE_CODE;
            }
            int value = i | i2;
            if (value != 0) {
                setConfigSingle(SILABSER_FLUSH_REQUEST_CODE, value);
            }
            return true;
        }
    }

    public Cp21xxSerialDriver(UsbDevice device) {
        this.mDevice = device;
    }

    public UsbDevice getDevice() {
        return this.mDevice;
    }

    public List<UsbSerialPort> getPorts() {
        return Collections.singletonList(this.mPort);
    }

    public static Map<Integer, int[]> getSupportedDevices() {
        Map<Integer, int[]> supportedDevices = new LinkedHashMap();
        supportedDevices.put(Integer.valueOf(UsbId.VENDOR_SILABS), new int[]{UsbId.SILABS_CP2102, UsbId.SILABS_CP2105, UsbId.SILABS_CP2108, UsbId.SILABS_CP2110});
        return supportedDevices;
    }
}
