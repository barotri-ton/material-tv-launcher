package com.rampey.materialtv;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.net.Uri;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Random landscape photos for the screensaver, from picsum.photos (free photos from Unsplash, no account or key).
 * The request carries no identifier: just a size. Only used while the screensaver runs with photos turned on.
 */
final class Photos {
    private Photos() {
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "photos");
        t.setDaemon(true);
        return t;
    });
    private static final int MAX_BYTES = 6 * 1024 * 1024;

    /** One 1280x720 photo, decoded; the callback runs on the UI thread with null when it could not be fetched. */
    static void fetch(Handler ui, Weather.Callback<Bitmap> cb) {
        IO.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL("https://picsum.photos/1280/720").openConnection();
                c.setUseCaches(false);
                c.setConnectTimeout(8000);
                c.setReadTimeout(15000);
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0 && out.size() < MAX_BYTES) out.write(buf, 0, n);
                    byte[] bytes = out.toByteArray();
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
            Bitmap result = bitmap;
            ui.post(() -> cb.done(result));
        });
    }

    /**
     * A poster for the Continue watching row: from the app's own image provider (content://, android.resource://) or,
     * for an https address, from the address the app gave. Decoded down to about maxWidth pixels.
     */
    static void image(Handler ui, ContentResolver cr, String address, int maxWidth, Weather.Callback<Bitmap> cb) {
        IO.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection c = null;
            try {
                byte[] bytes;
                Uri uri = Uri.parse(address);
                InputStream in;
                if ("https".equals(uri.getScheme())) {
                    c = (HttpURLConnection) new URL(address).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(15000);
                    in = c.getInputStream();
                } else if ("content".equals(uri.getScheme()) || "android.resource".equals(uri.getScheme())) {
                    in = cr.openInputStream(uri);
                } else {
                    in = null;
                }
                if (in != null) {
                    try (InputStream stream = in) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = stream.read(buf)) > 0 && out.size() < MAX_BYTES) out.write(buf, 0, n);
                        bytes = out.toByteArray();
                    }
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = Math.max(1, bounds.outWidth / Math.max(1, maxWidth));
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
            Bitmap result = bitmap;
            ui.post(() -> cb.done(result));
        });
    }
}
