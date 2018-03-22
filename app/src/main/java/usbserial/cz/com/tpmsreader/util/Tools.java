package usbserial.cz.com.tpmsreader.util;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Process;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.widget.Toast;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Created by estarcev on 22.03.2018.
 */


public class Tools {
    private static boolean flag = true;
    static Toast toast = null;
    static final String usbHeartbeatServer = "com.cz.usbserial.activity.HeartbeatServer";
    static final String usbService = "com.cz.usbserial.activity.TpmsServer";

    public static boolean isUSBService(Context context) {
        for (ActivityManager.RunningServiceInfo info : ((ActivityManager) context.getSystemService("activity")).getRunningServices(30)) {
            if (usbService.equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUSBHeartbeatServer(Context context) {
        for (ActivityManager.RunningServiceInfo info : ((ActivityManager) context.getSystemService("activity")).getRunningServices(30)) {
            if (usbHeartbeatServer.equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isServiceWork(Context mContext, String serviceName) {
        boolean isWork = false;
        List<ActivityManager.RunningServiceInfo> myList = ((ActivityManager) mContext.getSystemService("activity")).getRunningServices(40);
        if (myList.size() <= 0) {
            return false;
        }
        for (int i = 0; i < myList.size(); i++) {
            if (((ActivityManager.RunningServiceInfo) myList.get(i)).service.getClassName().toString().equals(serviceName)) {
                isWork = true;
                break;
            }
        }
        return isWork;
    }

    public static void Toast(Context context, String text) {
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(context, text, 0);
        toast.show();
    }

    public static void Log(String str) {
        if (flag) {
            Log.v("TPMS", str);
        }
    }

    public static byte[] dealData(byte[] buff) {
        int len = buff.length / 10;
        int i = 0;
        while (i < len) {
            if (buff[i * 10] == (byte) 85 && buff[(i * 10) + 1] == (byte) -86) {
                byte[] b = new byte[buff[(i * 10) + 2]];
                for (int y = 0; y < 10; y++) {
                    b[y] = buff[(i * 10) + y];
                }
                if (isDataBoolean2(b)) {
                    return buff;
                }
            }
            i++;
        }
        return null;
    }

    private static boolean isDataBoolean2(byte[] buff) {
        byte sum = buff[0];
        for (int i = 1; i < buff.length - 1; i++) {
            sum = (byte) (buff[i] ^ sum);
        }
        if (sum == buff[buff.length - 1]) {
            return true;
        }
        return false;
    }

    public static boolean isWarn2(byte[] data, int[] i) {
        if (data.length == 10) {
            isDataBoolean(data);
        }
        return false;
    }

    private static boolean isDataBoolean(byte[] buff) {
        byte sum = buff[0];
        for (int i = 1; i < buff.length - 2; i++) {
            sum = (byte) (buff[i] ^ sum);
        }
        if (sum == buff[buff.length - 1]) {
            return true;
        }
        return false;
    }

    public static boolean checkData(byte[] data) {
        byte sum = data[data.length - 1];
        byte dat = data[0];
        for (int i = 1; i < data.length - 2; i++) {
            dat = (byte) (data[i] ^ dat);
        }
        if (dat == sum) {
            return true;
        }
        return false;
    }

    public static boolean isHP(byte b, int i) {
        if (((int) (((double) Integer.valueOf(Integer.toBinaryString(b & MotionEventCompat.ACTION_MASK), 2).intValue()) * 3.44d)) > (i * 10) + 250) {
            return true;
        }
        return false;
    }

    public static boolean isLP(byte b, int i) {
        if (((int) (((double) Integer.valueOf(Integer.toBinaryString(b & MotionEventCompat.ACTION_MASK), 2).intValue()) * 3.44d)) < (i * 10) + 180) {
            return true;
        }
        return false;
    }

    public static boolean isHT(int b, int i) {
        return b > i;
    }

    public static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (byte b : src) {
            String hv = Integer.toHexString(b & MotionEventCompat.ACTION_MASK);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    public static String byteToHexString(byte b) {
        String stmp = Integer.toHexString(b & MotionEventCompat.ACTION_MASK);
        if (stmp.length() == 1) {
            stmp = "0" + stmp;
        }
        return stmp.toUpperCase();
    }

    public static double getType_T(String str) {
        return (double) (Integer.parseInt(str, 16) - 50);
    }

    public static byte[] sum(byte[] s) {
        byte[] buff = s;
        byte checkdata = (byte) 0;
        for (int i = 0; i < buff.length - 1; i++) {
            checkdata = (byte) (buff[i] ^ checkdata);
        }
        buff[buff.length - 1] = checkdata;
        return buff;
    }

    public static String byteToHexString(byte[] b) {
        String stmp = "";
        StringBuilder sb = new StringBuilder("");
        for (byte c : b) {
            String str;
            stmp = Integer.toHexString(c & MotionEventCompat.ACTION_MASK);
            if (stmp.length() == 1) {
                str = "0" + stmp;
            } else {
                str = stmp;
            }
            sb.append(str);
            sb.append(" ");
        }
        return sb.toString().toUpperCase().trim();
    }

    public static byte[] requestData(byte[] data, int length) {
        byte[] buff = new byte[length];
        for (int i = 0; i < buff.length; i++) {
            buff[i] = data[i];
        }
        Log.d("REQUEST", byteToHexString(buff));
        return buff;
    }

    public static byte[] sendPaassword(byte b) {
        byte[] bArr = new byte[6];
        bArr[0] = (byte) 85;
        bArr[1] = (byte) -86;
        bArr[2] = (byte) 6;
        bArr[3] = (byte) 90;
        bArr[4] = (byte) 17;
        return sum(bArr);
    }

    public static boolean isPassword(byte b1, byte b2) {
        if (b2 != (byte) -1 && ((byte) ((((((((b1 ^ 32) ^ 21) ^ 16) ^ 1) ^ 2) ^ 3) ^ 4) ^ 5)) == b2) {
            return false;
        }
        return true;
    }

    public static byte isPasswordByte(byte[] buff) {
        byte b = (byte) -1;
        int i = 0;
        while (i < buff.length) {
            try {
                if (buff[i] == (byte) 85 && buff[i + 1] == (byte) -86 && buff[i + 2] == (byte) 6 && buff[i + 3] == (byte) -91) {
                    b = buff[i + 4];
                    break;
                }
                i++;
            } catch (Exception e) {
            }
        }
        return b;
    }

    public static String getCurProcessName(Context context) {
        int pid = Process.myPid();
        for (ActivityManager.RunningAppProcessInfo appProcess : ((ActivityManager) context.getSystemService("activity")).getRunningAppProcesses()) {
            if (appProcess.pid == pid) {
                return appProcess.processName;
            }
        }
        return null;
    }

    public static double div(double v1, double v2, int scale) {
        if (scale >= 0) {
            return new BigDecimal(Double.toString(v1)).divide(new BigDecimal(Double.toString(v2)), scale, 4).doubleValue();
        }
        throw new IllegalArgumentException("The scale must be a positive integer or zero");
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

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService("connectivity");
        if (connectivity != null) {
            NetworkInfo info = connectivity.getActiveNetworkInfo();
            if (info != null && info.isConnected() && info.getState() == NetworkInfo.State.CONNECTED) {
                return true;
            }
        }
        return false;
    }

    public static String getVersionName(Context context) throws Exception {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
    }

    public static String getAppBuildTime(Context context) {
        String result = "";
        try {
            ZipFile zf = new ZipFile(context.getPackageManager().getApplicationInfo(context.getPackageName(), 0).sourceDir);
            long time = zf.getEntry("META-INF/MANIFEST.MF").getTime();
            SimpleDateFormat formatter = (SimpleDateFormat) SimpleDateFormat.getInstance();
            formatter.applyPattern("yyyy/MM/dd HH:mm");
            result = formatter.format(new Date(time));
            zf.close();
            return result;
        } catch (Exception e) {
            return result;
        }
    }
}
