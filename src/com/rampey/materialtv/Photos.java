package com.rampey.materialtv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
}
