package com.bokaliai.terminal;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.UUID;

/** Non-secret connection profiles for user-owned devices and services. */
public final class DeviceHubProfile {
    public static final String ANDROID_ADB = "ANDROID_ADB";
    public static final String SSH = "SSH";
    public static final String HOME_ASSISTANT = "HOME_ASSISTANT";
    public static final String WEB = "WEB";
    public static final String ROUTER_SSH = "ROUTER_SSH";

    public final String id;
    public final String name;
    public final String platform;
    public final String host;
    public final int port;
    public final String username;

    public DeviceHubProfile(String id, String name, String platform, String host, int port, String username) {
        this.id = id;
        this.name = name;
        this.platform = platform;
        this.host = host;
        this.port = port;
        this.username = username;
    }

    public static ArrayList<DeviceHubProfile> load(Context context) {
        ArrayList<DeviceHubProfile> result = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences("bo_device_hub", Context.MODE_PRIVATE);
        String raw = prefs.getString("profiles", "[]");
        try {
            JSONArray items = new JSONArray(raw);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                String name = item.optString("name", "");
                String platform = item.optString("platform", "");
                String host = item.optString("host", "");
                int port = item.optInt("port", 0);
                String username = item.optString("username", "");
                if (!id.isEmpty() && !name.isEmpty() && !host.isEmpty()
                    && isSupported(platform) && port > 0 && port <= 65535) {
                    result.add(new DeviceHubProfile(id, name, platform, host, port, username));
                }
            }
        } catch (Exception ignored) { }
        return result;
    }

    public static boolean isSupported(String platform) {
        return ANDROID_ADB.equals(platform) || SSH.equals(platform)
            || HOME_ASSISTANT.equals(platform) || WEB.equals(platform)
            || ROUTER_SSH.equals(platform);
    }

    public static String save(Context context, String name, String platform,
                              String host, int port, String username) {
        ArrayList<DeviceHubProfile> profiles = load(context);
        String id = UUID.randomUUID().toString();
        profiles.add(new DeviceHubProfile(id, name, platform, host, port, username));
        persist(context, profiles);
        return id;
    }

    public static void delete(Context context, String id) {
        ArrayList<DeviceHubProfile> profiles = load(context);
        for (int i = profiles.size() - 1; i >= 0; i--) {
            if (profiles.get(i).id.equals(id)) profiles.remove(i);
        }
        persist(context, profiles);
    }

    private static void persist(Context context, ArrayList<DeviceHubProfile> profiles) {
        JSONArray items = new JSONArray();
        try {
            for (DeviceHubProfile profile : profiles) {
                JSONObject item = new JSONObject();
                item.put("id", profile.id);
                item.put("name", profile.name);
                item.put("platform", profile.platform);
                item.put("host", profile.host);
                item.put("port", profile.port);
                item.put("username", profile.username);
                items.put(item);
            }
            context.getSharedPreferences("bo_device_hub", Context.MODE_PRIVATE)
                .edit().putString("profiles", items.toString()).apply();
        } catch (Exception ignored) { }
    }
}
