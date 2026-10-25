package com.rampey.materialtv;

import android.content.res.AssetManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** The photos bundled in assets/wallpapers (NASA public domain, Wikimedia Commons CC BY-SA; see CREDITS.md there). */
final class Wallpapers {
    private Wallpapers() {
    }

    static final String DIR = "wallpapers";

    /** File names of the bundled photos, sorted. */
    static List<String> files(AssetManager assets) {
        List<String> out = new ArrayList<>();
        try {
            String[] all = assets.list(DIR);
            if (all != null) for (String f : all) if (f.toLowerCase(Locale.ROOT).endsWith(".jpg")) out.add(f);
        } catch (IOException ignored) {
        }
        Collections.sort(out);
        return out;
    }

    /** "hong_kong_night.jpg" -> "Hong kong night". */
    static String label(String file) {
        String name = file.replaceFirst("\\.[^.]+$", "").replace('_', ' ');
        return name.isEmpty() ? file : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    static String path(String file) {
        return DIR + "/" + file;
    }
}
