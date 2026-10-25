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

    /** Every choice back to how it came; pinned and hidden apps stay. */
    void resetSettings() {
        SharedPreferences.Editor e = sp.edit();
        for (String key : new String[]{"reduced_motion", "columns", "clock_mode", "start_app"}) e.remove(key);
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
