package com.rampey.materialtv;

import android.content.ContentResolver;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.os.Handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What the TV's apps offer under "Continue watching" (Android's Watch Next table), read on the TV only. Needs the
 * READ_TV_LISTINGS permission; without ACCESS_ALL_EPG_DATA the system shows a reader only searchable rows, so an app
 * that publishes nothing (or nothing searchable) simply does not appear.
 */
final class WatchNext {
    private WatchNext() {
    }

    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    private static final int MAX_ITEMS = 20;

    static final class Item {
        String pkg, title, poster, intentUri;
        long position, duration, engaged;
        /** Only for items remembered from Now Playing (no poster, no intent from the app). */
        String subtitle, mediaId, mediaUri;
        boolean recent;
    }

    /**
     * What the Continue tab lists: the apps' own Watch Next items plus what was played on this TV. The same title from the
     * same app is one entry (the app's item wins, with the remembered position if it has none); finished items drop out.
     */
    static List<Item> merge(List<Item> offered, List<Item> played, Set<String> skip) {
        java.util.Map<String, Item> byKey = new java.util.LinkedHashMap<>();
        if (offered != null) for (Item it : offered) byKey.put(key(it), it);
        if (played != null) {
            for (Item it : played) {
                if (skip.contains(it.pkg)) continue;
                Item have = byKey.get(key(it));
                if (have == null) {
                    byKey.put(key(it), it);
                } else {
                    if (have.position <= 0) have.position = it.position;
                    if (have.duration <= 0) have.duration = it.duration;
                    have.engaged = Math.max(have.engaged, it.engaged);
                }
            }
        }
        List<Item> out = new ArrayList<>();
        for (Item it : byKey.values()) {
            if (it.duration > 0 && it.position >= it.duration * 0.95) continue;
            out.add(it);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.engaged, a.engaged));
        return out.size() > MAX_ITEMS ? new ArrayList<>(out.subList(0, MAX_ITEMS)) : out;
    }

    private static String key(Item it) {
        return it.pkg + "|" + it.title.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "watch-next");
        t.setDaemon(true);
        return t;
    });

    /** Newest engagement first; the callback runs on the UI thread with null if the table could not be read. */
    static void load(Handler ui, ContentResolver cr, Set<String> skip, Weather.Callback<List<Item>> cb) {
        IO.execute(() -> {
            List<Item> items = null;
            try (Cursor c = cr.query(TvContract.WatchNextPrograms.CONTENT_URI, new String[]{
                    TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                    TvContract.WatchNextPrograms.COLUMN_TITLE,
                    TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                    TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
                    TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                    TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                    TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                    TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS}, null, null, null)) {
                if (c != null) {
                    items = new ArrayList<>();
                    while (c.moveToNext()) {
                        Item it = new Item();
                        it.pkg = c.getString(0);
                        it.title = c.getString(1);
                        it.poster = c.getString(2);
                        if (it.poster == null || it.poster.isEmpty()) it.poster = c.getString(3);
                        it.intentUri = c.getString(4);
                        it.position = c.getLong(5);
                        it.duration = c.getLong(6);
                        it.engaged = c.getLong(7);
                        if (it.pkg == null || it.title == null || it.title.isEmpty() || skip.contains(it.pkg)) continue;
                        items.add(it);
                    }
                    Collections.sort(items, (a, b) -> Long.compare(b.engaged, a.engaged));
                    if (items.size() > MAX_ITEMS) items = new ArrayList<>(items.subList(0, MAX_ITEMS));
                }
            } catch (RuntimeException ignored) {
                // No permission yet, or the TV has no TV provider.
            }
            List<Item> result = items;
            ui.post(() -> cb.done(result));
        });
    }
}
