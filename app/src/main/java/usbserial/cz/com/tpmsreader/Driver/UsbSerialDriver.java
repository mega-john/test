/**
 * Created by estarcev on 13.03.2018.
 */
package usbserial.cz.com.tpmsreader.Driver;

import android.hardware.usb.UsbDevice;
import java.util.List;

public interface UsbSerialDriver {
    UsbDevice getDevice();

    List<UsbSerialPort> getPorts();
}
