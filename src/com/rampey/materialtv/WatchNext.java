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
