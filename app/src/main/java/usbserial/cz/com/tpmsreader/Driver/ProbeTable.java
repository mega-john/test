/**
 * Created by estarcev on 13.03.2018.
 */
package usbserial.cz.com.tpmsreader.Driver;


import android.util.Pair;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class ProbeTable {
    private final Map<Pair<Integer, Integer>, Class<? extends UsbSerialDriver>> mProbeTable = new LinkedHashMap();

    public ProbeTable addProduct(int vendorId, int productId, Class<? extends UsbSerialDriver> driverClass) {
        this.mProbeTable.put(Pair.create(Integer.valueOf(vendorId), Integer.valueOf(productId)), driverClass);
        return this;
    }

    ProbeTable addDriver(Class<? extends UsbSerialDriver> driverClass) {
        try {
            try {
                for (Object entry : ((Map) driverClass.getMethod("getSupportedDevices", new Class[0]).invoke(null, new Object[0])).entrySet()) {
                    int vendorId = ((Integer) ((Entry<Integer, int[]>)entry).getKey()).intValue();
                    for (int productId : (int[]) ((Entry<Integer, int[]>)entry).getValue()) {
                        addProduct(vendorId, productId, driverClass);
                    }
                }
                return this;
            } catch (IllegalArgumentException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e2) {
                throw new RuntimeException(e2);
            } catch (InvocationTargetException e3) {
                throw new RuntimeException(e3);
            }
        } catch (SecurityException e4) {
            throw new RuntimeException(e4);
        } catch (NoSuchMethodException e5) {
            throw new RuntimeException(e5);
        }
    }

    public Class<? extends UsbSerialDriver> findDriver(int vendorId, int productId) {
        return (Class) this.mProbeTable.get(Pair.create(Integer.valueOf(vendorId), Integer.valueOf(productId)));
    }
}

