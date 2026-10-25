package com.rampey.materialtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
