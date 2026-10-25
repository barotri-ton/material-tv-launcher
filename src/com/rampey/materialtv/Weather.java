package com.rampey.materialtv;

import android.os.Handler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Weather from Open-Meteo (no account, no key). The only things that leave the TV are the city name the user typed
 * and, afterwards, that city's coordinates; nothing identifies the TV or the user. Off until a city is set.
 */
final class Weather {
    private Weather() {
    }

    interface Callback<T> {
        /** Called on the UI thread; null when the request failed. */
        void done(T result);
    }

    static final class Place {
        final String name;
        final double lat, lon;

        Place(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "weather");
        t.setDaemon(true);
        return t;
    });

    static void findCity(Handler ui, String query, Callback<Place> cb) {
        IO.execute(() -> {
            Place place = null;
            try {
                String body = get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name="
                        + URLEncoder.encode(query.trim(), "UTF-8"));
                JSONArray results = new JSONObject(body).optJSONArray("results");
                if (results != null && results.length() > 0) {
                    JSONObject r = results.getJSONObject(0);
                    String country = r.optString("country", "");
                    place = new Place(r.getString("name") + (country.isEmpty() ? "" : ", " + country),
                            r.getDouble("latitude"), r.getDouble("longitude"));
                }
            } catch (Exception ignored) {
            }
            Place found = place;
            ui.post(() -> cb.done(found));
        });
    }

    /** "21°C · Partly cloudy" for the place, or null when offline. */
    static void current(Handler ui, double lat, double lon, boolean fahrenheit, Callback<String> cb) {
        IO.execute(() -> {
            String line = null;
            try {
                String body = get("https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code&latitude="
                        + lat + "&longitude=" + lon + "&temperature_unit=" + (fahrenheit ? "fahrenheit" : "celsius"));
                JSONObject cur = new JSONObject(body).getJSONObject("current");
                line = Math.round(cur.getDouble("temperature_2m")) + (fahrenheit ? "°F" : "°C") + " · " + describe(cur.getInt("weather_code"));
            } catch (Exception ignored) {
            }
            String result = line;
            ui.post(() -> cb.done(result));
        });
    }

    /** WMO weather interpretation codes, as Open-Meteo reports them. */
    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }

    private static String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 65536) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }
}
