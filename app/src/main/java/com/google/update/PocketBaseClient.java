package com.google.update;
import android.util.Log;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
public class PocketBaseClient {
    private static final String BASE_URL = "http://10.34.191.214:8090";
    private final OkHttpClient client;
    private String adminToken;
    public PocketBaseClient() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }
    public void login(String email, String password, Callback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("identity", email);
            json.put("password", password);
            Request request = new Request.Builder()
                .url(BASE_URL + "/api/admins/auth-with-password")
                .post(RequestBody.create(json.toString(), MediaType.parse("application/json")))
                .build();
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            JSONObject result = new JSONObject(response.body().string());
                            adminToken = result.getString("token");
                            callback.onSuccess(adminToken);
                        } catch (Exception e) { callback.onError(e.getMessage()); }
                    } else { callback.onError("Login failed: " + response.code()); }
                }
                @Override public void onFailure(Call call, IOException e) { callback.onError(e.getMessage()); }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }
    public void registerDevice(String deviceId, String deviceName, String token, Callback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("device_id", deviceId);
            json.put("device_name", deviceName);
            Request request = new Request.Builder()
                .url(BASE_URL + "/api/collections/devices/records")
                .post(RequestBody.create(json.toString(), MediaType.parse("application/json")))
                .header("Authorization", token)
                .build();
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Device registered");
                    else callback.onError("Failed: " + response.code());
                }
                @Override public void onFailure(Call call, IOException e) { callback.onError(e.getMessage()); }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }
    public void sendCommand(String deviceId, String command, String token, Callback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("device_id", deviceId);
            json.put("command", command);
            json.put("status", "pending");
            Request request = new Request.Builder()
                .url(BASE_URL + "/api/collections/commands/records")
                .post(RequestBody.create(json.toString(), MediaType.parse("application/json")))
                .header("Authorization", token)
                .build();
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Command sent");
                    else callback.onError("Failed: " + response.code());
                }
                @Override public void onFailure(Call call, IOException e) { callback.onError(e.getMessage()); }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }
    public interface Callback { void onSuccess(String result); void onError(String error); }
}
