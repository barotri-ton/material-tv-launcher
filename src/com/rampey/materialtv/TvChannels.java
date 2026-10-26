package com.rampey.materialtv;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.os.Handler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The TV channels the apps on this TV publish to Android TV's own channel row: each preview program carries the name,
 * the logo address the app gave and the intent that opens that channel in the app. Read on the TV only, with
 * READ_TV_LISTINGS; the addresses are opened only while the row is on (see Privacy).
 */
final class TvChannels {
    private TvChannels() {
    }

    /** A row under the app dock, not a directory: the apps' first channels, in the order they set. */
    private static final int MAX = 30;

    static final class Channel {
        String title;
        String logo;
        String intent;
        String pkg;
        int weight;

        /** Unique per channel, and unlike a package name so colors and the hero do not confuse it with an app. */
        String key() {
            return "tv:" + pkg + "|" + title;
        }
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tv-channels");
        t.setDaemon(true);
        return t;
    });

    /**
     * The row as last read, kept on the TV so Home shows it before the table is read again. The apps' own logo
     * addresses and intents stay here; nothing is sent anywhere to build this.
     */
    static List<Channel> cached(String json) {
        List<Channel> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JSONArray all = new JSONArray(json);
            for (int i = 0; i < all.length() && out.size() < MAX; i++) {
                JSONObject o = all.getJSONObject(i);
                Channel ch = new Channel();
                ch.title = o.optString("title", "");
                ch.logo = o.optString("logo", null);
                ch.intent = o.optString("intent", null);
                ch.pkg = o.optString("pkg", "");
                ch.weight = o.optInt("weight", 0);
                if (!ch.title.isEmpty()) out.add(ch);
            }
        } catch (org.json.JSONException ignored) {
        }
        return out;
    }

    static String json(List<Channel> channels) {
        JSONArray all = new JSONArray();
        try {
            for (Channel ch : channels) {
                all.put(new JSONObject().put("title", ch.title).put("logo", ch.logo == null ? "" : ch.logo)
                        .put("intent", ch.intent == null ? "" : ch.intent).put("pkg", ch.pkg).put("weight", ch.weight));
            }
        } catch (org.json.JSONException ignored) {
        }
        return all.toString();
    }

    /** What the row shows; a rebuild is needed only when this changes. */
    static String key(List<Channel> channels) {
        StringBuilder out = new StringBuilder();
        for (Channel ch : channels) out.append('|').append(ch.key());
        return out.toString();
    }

    /** The intent the app published for this channel, or null when it cannot be read. */
    static Intent intent(Channel ch) {
        if (ch.intent == null || ch.intent.isEmpty()) return null;
        try {
            return Intent.parseUri(ch.intent, Intent.URI_INTENT_SCHEME);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The browsable preview programs of the TV's apps, heaviest first. The callback runs on the UI thread with null
     * when the table could not be read (no READ_TV_LISTINGS, or no TV provider).
     */
    static void load(Handler ui, ContentResolver cr, String self, Weather.Callback<List<Channel>> cb) {
        IO.execute(() -> {
            List<Channel> channels = null;
            try (Cursor c = cr.query(TvContract.PreviewPrograms.CONTENT_URI, new String[]{
                    TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
                    TvContract.PreviewPrograms.COLUMN_TITLE,
                    TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
                    TvContract.PreviewPrograms.COLUMN_INTENT_URI,
                    TvContract.PreviewPrograms.COLUMN_WEIGHT,
                    TvContract.PreviewPrograms.COLUMN_BROWSABLE}, null, null, null)) {
                if (c != null) {
                    channels = new ArrayList<>();
                    Set<String> seen = new HashSet<>();
                    while (c.moveToNext()) {
                        Channel ch = new Channel();
                        ch.pkg = c.getString(0);
                        ch.title = c.getString(1);
                        ch.logo = c.isNull(2) ? null : c.getString(2);
                        ch.intent = c.isNull(3) ? null : c.getString(3);
                        ch.weight = c.isNull(4) ? 0 : c.getInt(4);
                        boolean browsable = !c.isNull(5) && c.getInt(5) == 1;
                        // A program without an intent cannot be opened, and Android's own recommendation service is
                        // not a channel app (its rows are not something we can launch).
                        if (!browsable || ch.title == null || ch.title.isEmpty() || intent(ch) == null) continue;
                        if (ch.pkg == null || ch.pkg.equals(self) || ch.pkg.endsWith(".tvrecommendations")) continue;
                        if (!seen.add(ch.pkg + "|" + ch.title.toLowerCase(Locale.ROOT))) continue;
                        channels.add(ch);
                    }
                    Collections.sort(channels, (a, b) -> Integer.compare(b.weight, a.weight));
                    if (channels.size() > MAX) channels = new ArrayList<>(channels.subList(0, MAX));
                }
            } catch (Exception ignored) {
            }
            List<Channel> result = channels;
            ui.post(() -> cb.done(result));
        });
    }
}
