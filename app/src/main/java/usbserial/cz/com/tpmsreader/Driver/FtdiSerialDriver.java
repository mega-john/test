/**
 * Created by estarcev on 13.03.2018.
 */
package usbserial.cz.com.tpmsreader.Driver;


import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.util.Log;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;

public class FtdiSerialDriver implements UsbSerialDriver {
    private UsbDevice mDevice = null;
    private final UsbSerialPort mPort = (UsbSerialPort) new FtdiSerialPort(this.mDevice, 0);

    private enum DeviceType {
        TYPE_BM,
        TYPE_AM,
        TYPE_2232C,
        TYPE_R,
        TYPE_2232H,
        TYPE_4232H
    }

    public class FtdiSerialPort extends CommonUsbSerialPort {
        private static final boolean ENABLE_ASYNC_READS = false;
        public static final int FTDI_DEVICE_IN_REQTYPE = 192;
        public static final int FTDI_DEVICE_OUT_REQTYPE = 64;
        private static final int MODEM_STATUS_HEADER_LENGTH = 2;
        private static final int SIO_MODEM_CTRL_REQUEST = 1;
        private static final int SIO_RESET_PURGE_RX = 1;
        private static final int SIO_RESET_PURGE_TX = 2;
        private static final int SIO_RESET_REQUEST = 0;
        private static final int SIO_RESET_SIO = 0;
        private static final int SIO_SET_BAUD_RATE_REQUEST = 3;
        private static final int SIO_SET_DATA_REQUEST = 4;
        private static final int SIO_SET_FLOW_CTRL_REQUEST = 2;
        public static final int USB_ENDPOINT_IN = 128;
        public static final int USB_ENDPOINT_OUT = 0;
        public static final int USB_READ_TIMEOUT_MILLIS = 5000;
        public static final int USB_RECIP_DEVICE = 0;
        public static final int USB_RECIP_ENDPOINT = 2;
        public static final int USB_RECIP_INTERFACE = 1;
        public static final int USB_RECIP_OTHER = 3;
        public static final int USB_TYPE_CLASS = 0;
        public static final int USB_TYPE_RESERVED = 0;
        public static final int USB_TYPE_STANDARD = 0;
        public static final int USB_TYPE_VENDOR = 0;
        public static final int USB_WRITE_TIMEOUT_MILLIS = 5000;
        private final String TAG = FtdiSerialDriver.class.getSimpleName();
        private int mInterface = USB_TYPE_VENDOR;
        private int mMaxPacketSize = FTDI_DEVICE_OUT_REQTYPE;
        private DeviceType mType;

        public FtdiSerialPort(UsbDevice device, int portNumber) {
            super(device, portNumber);
        }

        public UsbSerialDriver getDriver() {
            return FtdiSerialDriver.this;
        }

        private final int filterStatusBytes(byte[] src, byte[] dest, int totalBytesRead, int maxPacketSize) {
            int packetsCount = (totalBytesRead / maxPacketSize) + (totalBytesRead % maxPacketSize == 0 ? USB_TYPE_VENDOR : USB_RECIP_INTERFACE);
            for (int packetIdx = USB_TYPE_VENDOR; packetIdx < packetsCount; packetIdx += USB_RECIP_INTERFACE) {
                int count;
                if (packetIdx == packetsCount - 1) {
                    count = (totalBytesRead % maxPacketSize) - 2;
                } else {
                    count = maxPacketSize - 2;
                }
                if (count > 0) {
                    System.arraycopy(src, (packetIdx * maxPacketSize) + USB_RECIP_ENDPOINT, dest, (maxPacketSize - 2) * packetIdx, count);
                }
            }
            return totalBytesRead - (packetsCount * USB_RECIP_ENDPOINT);
        }

        public void reset() throws IOException {
            int result = this.mConnection.controlTransfer(FTDI_DEVICE_OUT_REQTYPE, USB_TYPE_VENDOR, USB_TYPE_VENDOR, USB_TYPE_VENDOR, null, USB_TYPE_VENDOR, USB_WRITE_TIMEOUT_MILLIS);
            if (result != 0) {
                throw new IOException("Reset failed: result=" + result);
            }
            this.mType = DeviceType.TYPE_R;
        }

        public void open(UsbDeviceConnection connection) throws IOException {
            if (this.mConnection != null) {
                throw new IOException("Already open");
            }
            this.mConnection = connection;
            int i = USB_TYPE_VENDOR;
            while (i < this.mDevice.getInterfaceCount()) {
                try {
                    if (connection.claimInterface(this.mDevice.getInterface(i), true)) {
                        Log.d(this.TAG, "claimInterface " + i + " SUCCESS");
                        i += USB_RECIP_INTERFACE;
                    } else {
                        throw new IOException("Error claiming interface " + i);
                    }
                } catch (Throwable th) {
                    if (!ENABLE_ASYNC_READS) {
                        close();
                        this.mConnection = null;
                    }
                }
            }
            reset();
            if (!true) {
                close();
                this.mConnection = null;
            }
        }

        public void close() throws IOException {
            if (this.mConnection == null) {
                throw new IOException("Already closed");
            }
            try {
                this.mConnection.close();
            } finally {
                this.mConnection = null;
            }
        }

        public int read(byte[] dest, int timeoutMillis) throws IOException {
            int filterStatusBytes;
            UsbEndpoint endpoint = this.mDevice.getInterface(USB_TYPE_VENDOR).getEndpoint(USB_TYPE_VENDOR);
            synchronized (this.mReadBufferLock) {
                int totalBytesRead = this.mConnection.bulkTransfer(endpoint, this.mReadBuffer, Math.min(dest.length, this.mReadBuffer.length), timeoutMillis);
                if (totalBytesRead < USB_RECIP_ENDPOINT) {
                    throw new IOException("Expected at least 2 bytes");
                }
                filterStatusBytes = filterStatusBytes(this.mReadBuffer, dest, totalBytesRead, endpoint.getMaxPacketSize());
            }
            return filterStatusBytes;
        }

        public int write(byte[] src, int timeoutMillis) throws IOException {
            UsbEndpoint endpoint = this.mDevice.getInterface(USB_TYPE_VENDOR).getEndpoint(USB_RECIP_INTERFACE);
            int offset = USB_TYPE_VENDOR;
            int amtWritten=0;
            int writeLength=0;
            while (offset < src.length) {
                synchronized (this.mWriteBufferLock) {
                    byte[] writeBuffer;
                    writeLength = Math.min(src.length - offset, this.mWriteBuffer.length);
                    if (offset == 0) {
                        writeBuffer = src;
                    } else {
                        System.arraycopy(src, offset, this.mWriteBuffer, USB_TYPE_VENDOR, writeLength);
                        writeBuffer = this.mWriteBuffer;
                    }
                    amtWritten = this.mConnection.bulkTransfer(endpoint, writeBuffer, writeLength, timeoutMillis);
                }
                if (amtWritten <= 0) {
                    throw new IOException("Error writing " + writeLength + " bytes at offset " + offset + " length=" + src.length);
                }
                offset += amtWritten;
            }
            return offset;
        }

        private int setBaudRate(int baudRate) throws IOException {
            long[] vals = convertBaudrate(baudRate);
            long actualBaudrate = vals[USB_TYPE_VENDOR];
            long index = vals[USB_RECIP_INTERFACE];
            int result = this.mConnection.controlTransfer(FTDI_DEVICE_OUT_REQTYPE, USB_RECIP_OTHER, (int) vals[USB_RECIP_ENDPOINT], (int) index, null, USB_TYPE_VENDOR, USB_WRITE_TIMEOUT_MILLIS);
            if (result == 0) {
                return (int) actualBaudrate;
            }
            throw new IOException("Setting baudrate failed: result=" + result);
        }

        public void setParameters(int baudRate, int dataBits, int stopBits, int parity) throws IOException {
            setBaudRate(baudRate);
            int config = dataBits;
            switch (parity) {
                case USB_TYPE_VENDOR /*0*/:
                    config |= USB_TYPE_VENDOR;
                    break;
                case USB_RECIP_INTERFACE /*1*/:
                    config |= ACTION_NEXT_AT_MOVEMENT_GRANULARITY;
                    break;
                case USB_RECIP_ENDPOINT /*2*/:
                    config |= ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;
                    break;
                case USB_RECIP_OTHER /*3*/:
                    config |= 768;
                    break;
                case SIO_SET_DATA_REQUEST /*4*/:
                    config |= ACTION_NEXT_HTML_ELEMENT;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown parity value: " + parity);
            }
            switch (stopBits) {
                case USB_RECIP_INTERFACE /*1*/:
                    config |= USB_TYPE_VENDOR;
                    break;
                case USB_RECIP_ENDPOINT /*2*/:
                    config |= ACTION_SCROLL_FORWARD;
                    break;
                case USB_RECIP_OTHER /*3*/:
                    config |= ACTION_PREVIOUS_HTML_ELEMENT;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown stopBits value: " + stopBits);
            }
            int result = this.mConnection.controlTransfer(FTDI_DEVICE_OUT_REQTYPE, SIO_SET_DATA_REQUEST, config, USB_TYPE_VENDOR, null, USB_TYPE_VENDOR, USB_WRITE_TIMEOUT_MILLIS);
            if (result != 0) {
                throw new IOException("Setting parameters failed: result=" + result);
            }
        }

        private long[] convertBaudrate(int baudrate) {
            long index;
            int divisor = 24000000 / baudrate;
            int bestDivisor = USB_TYPE_VENDOR;
            int bestBaud = USB_TYPE_VENDOR;
            int bestBaudDiff = USB_TYPE_VENDOR;
            int[] fracCode = new int[8];
            fracCode[USB_RECIP_INTERFACE] = USB_RECIP_OTHER;
            fracCode[USB_RECIP_ENDPOINT] = USB_RECIP_ENDPOINT;
            fracCode[USB_RECIP_OTHER] = SIO_SET_DATA_REQUEST;
            fracCode[SIO_SET_DATA_REQUEST] = USB_RECIP_INTERFACE;
            fracCode[5] = 5;
            fracCode[6] = 6;
            fracCode[7] = 7;
            for (int i = USB_TYPE_VENDOR; i < USB_RECIP_ENDPOINT; i += USB_RECIP_INTERFACE) {
                int baudDiff;
                int tryDivisor = divisor + i;
                if (tryDivisor <= 8) {
                    tryDivisor = 8;
                } else {
                    if (this.mType != DeviceType.TYPE_AM && tryDivisor < 12) {
                        tryDivisor = 12;
                    } else if (divisor < 16) {
                        tryDivisor = 16;
                    } else {
                        if (this.mType != DeviceType.TYPE_AM && tryDivisor > 131071) {
                            tryDivisor = 131071;
                        }
                    }
                }
                int baudEstimate = (24000000 + (tryDivisor / USB_RECIP_ENDPOINT)) / tryDivisor;
                if (baudEstimate < baudrate) {
                    baudDiff = baudrate - baudEstimate;
                } else {
                    baudDiff = baudEstimate - baudrate;
                }
                if (i == 0 || baudDiff < bestBaudDiff) {
                    bestDivisor = tryDivisor;
                    bestBaud = baudEstimate;
                    bestBaudDiff = baudDiff;
                    if (baudDiff == 0) {
                        break;
                    }
                }
            }
            long encodedDivisor = (long) ((bestDivisor >> USB_RECIP_OTHER) | (fracCode[bestDivisor & 7] << 14));
            if (encodedDivisor == 1) {
                encodedDivisor = 0;
            } else if (encodedDivisor == 16385) {
                encodedDivisor = 1;
            }
            long value = encodedDivisor & 65535;
            if (this.mType == DeviceType.TYPE_2232C || this.mType == DeviceType.TYPE_2232H || this.mType == DeviceType.TYPE_4232H) {
                index = (((encodedDivisor >> 8) & 65535) & 65280) | 0;
            } else {
                index = (encodedDivisor >> 16) & 65535;
            }
            long[] jArr = new long[USB_RECIP_OTHER];
            jArr[USB_TYPE_VENDOR] = (long) bestBaud;
            jArr[USB_RECIP_INTERFACE] = index;
            jArr[USB_RECIP_ENDPOINT] = value;
            return jArr;
        }

        public boolean getCD() throws IOException {
            return ENABLE_ASYNC_READS;
        }

        public boolean getCTS() throws IOException {
            return ENABLE_ASYNC_READS;
        }

        public boolean getDSR() throws IOException {
            return ENABLE_ASYNC_READS;
        }

        public boolean getDTR() throws IOException {
            return ENABLE_ASYNC_READS;
        }

        public void setDTR(boolean value) throws IOException {
        }

        public boolean getRI() throws IOException {
            return ENABLE_ASYNC_READS;
        }

        public boolean getRTS() throws IOException {
            return ENABLE_ASYNC_READS;
        }

        public void setRTS(boolean value) throws IOException {
        }

        public boolean purgeHwBuffers(boolean purgeReadBuffers, boolean purgeWriteBuffers) throws IOException {
            int result;
            if (purgeReadBuffers) {
                result = this.mConnection.controlTransfer(FTDI_DEVICE_OUT_REQTYPE, USB_TYPE_VENDOR, USB_RECIP_INTERFACE, USB_TYPE_VENDOR, null, USB_TYPE_VENDOR, USB_WRITE_TIMEOUT_MILLIS);
                if (result != 0) {
                    throw new IOException("Flushing RX failed: result=" + result);
                }
            }
            if (purgeWriteBuffers) {
                result = this.mConnection.controlTransfer(FTDI_DEVICE_OUT_REQTYPE, USB_TYPE_VENDOR, USB_RECIP_ENDPOINT, USB_TYPE_VENDOR, null, USB_TYPE_VENDOR, USB_WRITE_TIMEOUT_MILLIS);
                if (result != 0) {
                    throw new IOException("Flushing RX failed: result=" + result);
                }
            }
            return true;
        }
    }

    public FtdiSerialDriver(UsbDevice device) {
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
        supportedDevices.put(Integer.valueOf(UsbId.VENDOR_FTDI), new int[]{UsbId.FTDI_FT232R, UsbId.FTDI_FT231X});
        return supportedDevices;
    }
}
