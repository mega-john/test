package usbserial.cz.com.tpmsreader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class UsbReceiver extends BroadcastReceiver {

    public void onReceive(Context context, Intent intent) {

//        Toast.makeText(getApplicationContext(), "Inside USB Broadcast", Toast.LENGTH_SHORT).show();
        Toast.makeText(context, "Inside USB Broadcast", Toast.LENGTH_SHORT).show();
    }
}
