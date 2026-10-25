package com.rampey.materialtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The launcher's own choices, kept on the TV: pinned dock apps, calmer motion, apps per row, the app to open at boot. */
final class Prefs {
    static final int MIN_COLUMNS = 4;
    static final int MAX_COLUMNS = 7;
    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    /** Pinned dock packages, in the order they sit in the dock (leftmost first). */
    List<String> pinned() {
        String s = sp.getString("pinned", "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    void setPinned(List<String> packages) {
        sp.edit().putString("pinned", TextUtils.join(",", packages)).apply();
    }

    /** Apps hidden from the dock and the Apps page (they stay installed). */
    Set<String> hidden() {
        String s = sp.getString("hidden", "");
        return s.isEmpty() ? new HashSet<>() : new HashSet<>(Arrays.asList(s.split(",")));
    }

    void setHidden(Set<String> packages) {
        sp.edit().putString("hidden", TextUtils.join(",", packages)).apply();
    }

    /** 0 follows the TV's own 12/24-hour setting, 1 is 12-hour, 2 is 24-hour. */
    int clockMode() {
        return sp.getInt("clock_mode", 0);
    }

    void setClockMode(int mode) {
        sp.edit().putInt("clock_mode", mode).apply();
    }

    String clockPattern(Context c) {
        int mode = clockMode();
        boolean h24 = mode == 2 || (mode == 0 && android.text.format.DateFormat.is24HourFormat(c));
        return h24 ? "HH:mm" : "h:mm";
    }

    /** Screensaver shows random online photos instead of the generated shapes. */
    /** Screensaver photos: 0 off (generated shapes), 1 random online photos, 2 the bundled wallpapers (offline). */
    int photosMode() {
        return sp.contains("photos_mode") ? sp.getInt("photos_mode", 0) : (sp.getBoolean("photos", false) ? 1 : 0);
    }

    void setPhotosMode(int mode) {
        sp.edit().putInt("photos_mode", mode).apply();
    }

    boolean photosOn() {
        return photosMode() != 0;
    }

    /** The bundled photo behind Home (a file name under assets/wallpapers), or null for colors only. */
    String wallpaper() {
        return sp.getString("wallpaper", null);
    }

    void setWallpaper(String file) {
        sp.edit().putString("wallpaper", file).apply();
    }

    // ---- folders (groups of apps on the Apps page) ----

    /** Folder name -> package names, in the order the folders were made. */
    java.util.Map<String, List<String>> folders() {
        java.util.Map<String, List<String>> out = new java.util.LinkedHashMap<>();
        try {
            org.json.JSONObject all = new org.json.JSONObject(foldersRaw());
            java.util.Iterator<String> names = all.keys();
            while (names.hasNext()) {
                String name = names.next();
                org.json.JSONArray arr = all.getJSONArray(name);
                List<String> pkgs = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) pkgs.add(arr.getString(i));
                out.put(name, pkgs);
            }
        } catch (org.json.JSONException ignored) {
        }
        return out;
    }

    String foldersRaw() {
        return sp.getString("folders", "{}");
    }

    /** Empty folders are dropped. */
    void setFolders(java.util.Map<String, List<String>> folders) {
        org.json.JSONObject all = new org.json.JSONObject();
        try {
            for (java.util.Map.Entry<String, List<String>> e : folders.entrySet()) {
                if (!e.getValue().isEmpty()) all.put(e.getKey(), new org.json.JSONArray(e.getValue()));
            }
        } catch (org.json.JSONException ignored) {
        }
        sp.edit().putString("folders", all.toString()).apply();
    }

    /** Fixed color themes next to "follow the focused app" (index 0); seeds are the same kind the banners give. */
    static final String[] COLOR_NAMES = {"Follow app", "Ocean", "Forest", "Sunset", "Rose", "Violet", "Graphite"};
    private static final int[] COLOR_SEEDS = {0, 0xFF1976D2, 0xFF2E7D32, 0xFFEF6C00, 0xFFD81B60, 0xFF6750A4, 0xFF607D8B};

    int colorTheme() {
        return Math.max(0, Math.min(COLOR_NAMES.length - 1, sp.getInt("color_theme", 0)));
    }

    void setColorTheme(int index) {
        sp.edit().putInt("color_theme", index).apply();
    }

    /** The fixed seed color, or 0 when colors follow the focused app. */
    int colorSeed() {
        return COLOR_SEEDS[colorTheme()];
    }

    /** The Continue tab: what apps offer under Watch Next. */
    boolean continueOn() {
        return sp.getBoolean("continue", false);
    }

    void setContinueOn(boolean on) {
        sp.edit().putBoolean("continue", on).apply();
    }

    // ---- recently played (kept on the TV, only while the Continue tab is on) ----

    private static final int MAX_RECENT = 12;

    List<WatchNext.Item> recent() {
        List<WatchNext.Item> out = new ArrayList<>();
        try {
            org.json.JSONArray all = new org.json.JSONArray(sp.getString("recent", "[]"));
            for (int i = 0; i < all.length(); i++) {
                org.json.JSONObject o = all.getJSONObject(i);
                WatchNext.Item it = new WatchNext.Item();
                it.pkg = o.getString("pkg");
                it.title = o.getString("title");
                it.subtitle = o.optString("subtitle", "");
                it.mediaId = o.optString("mediaId", null);
                it.mediaUri = o.optString("mediaUri", null);
                it.position = o.optLong("position");
                it.duration = o.optLong("duration");
                it.engaged = o.optLong("engaged");
                it.recent = true;
                out.add(it);
            }
        } catch (org.json.JSONException ignored) {
        }
        return out;
    }

    /** Newest first; the same app and title replaces its older entry. */
    void addRecent(WatchNext.Item item) {
        List<WatchNext.Item> list = recent();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).pkg.equals(item.pkg) && list.get(i).title.equals(item.title)) list.remove(i);
        }
        list.add(0, item);
        while (list.size() > MAX_RECENT) list.remove(list.size() - 1);
        try {
            org.json.JSONArray all = new org.json.JSONArray();
            for (WatchNext.Item it : list) {
                all.put(new org.json.JSONObject().put("pkg", it.pkg).put("title", it.title).put("subtitle", it.subtitle == null ? "" : it.subtitle)
                        .put("mediaId", it.mediaId).put("mediaUri", it.mediaUri).put("position", it.position)
                        .put("duration", it.duration).put("engaged", it.engaged));
            }
            sp.edit().putString("recent", all.toString()).apply();
        } catch (org.json.JSONException ignored) {
        }
    }

    void clearRecent() {
        sp.edit().remove("recent").apply();
    }

    // ---- weather (off until a city is set) ----

    boolean weatherOn() {
        return sp.contains("weather_lat");
    }

    String weatherCity() {
        return sp.getString("weather_city", "");
    }

    double weatherLat() {
        return Double.longBitsToDouble(sp.getLong("weather_lat", 0));
    }

    double weatherLon() {
        return Double.longBitsToDouble(sp.getLong("weather_lon", 0));
    }

    void setWeatherPlace(String city, double lat, double lon) {
        sp.edit().putString("weather_city", city).putLong("weather_lat", Double.doubleToLongBits(lat))
                .putLong("weather_lon", Double.doubleToLongBits(lon)).remove("weather_text").remove("weather_at").apply();
    }

    void clearWeather() {
        sp.edit().remove("weather_city").remove("weather_lat").remove("weather_lon").remove("weather_text").remove("weather_at").apply();
    }

    boolean fahrenheit() {
        return sp.getBoolean("fahrenheit", false);
    }

    void setFahrenheit(boolean on) {
        sp.edit().putBoolean("fahrenheit", on).remove("weather_text").remove("weather_at").apply();
    }

    /** The last weather line ("21°C · Clear"), kept so the screensaver can show it without going online. */
    String weatherText() {
        return sp.getString("weather_text", "");
    }

    long weatherAt() {
        return sp.getLong("weather_at", 0);
    }

    void setWeatherText(String text) {
        sp.edit().putString("weather_text", text).putLong("weather_at", System.currentTimeMillis()).apply();
    }

    /** Every choice back to how it came; pinned and hidden apps stay. */
    void resetSettings() {
        SharedPreferences.Editor e = sp.edit();
        for (String key : new String[]{"reduced_motion", "columns", "clock_mode", "start_app", "fahrenheit", "photos", "photos_mode", "wallpaper", "continue", "recent", "color_theme", "weather_city", "weather_lat",
                "weather_lon", "weather_text", "weather_at"}) e.remove(key);
        e.apply();
    }

    boolean reducedMotion() {
        return sp.getBoolean("reduced_motion", false);
    }

    void setReducedMotion(boolean on) {
        sp.edit().putBoolean("reduced_motion", on).apply();
    }

    int columns() {
        return Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, sp.getInt("columns", 5)));
    }

    void setColumns(int n) {
        sp.edit().putInt("columns", n).apply();
    }

    /** The app that opens instead of Home the first time Home comes up after the TV starts; null means none. */
    String startApp() {
        return sp.getString("start_app", null);
    }

    void setStartApp(String pkg) {
        sp.edit().putString("start_app", pkg).apply();
    }

    /** True once per boot: the first call after each start of the TV returns true, later calls false. */
    boolean firstHomeSinceBoot(int bootCount) {
        if (sp.getInt("boot_seen", -1) == bootCount) return false;
        sp.edit().putInt("boot_seen", bootCount).apply();
        return true;
    }
}
