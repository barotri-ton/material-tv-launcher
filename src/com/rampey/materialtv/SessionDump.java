package com.rampey.materialtv;

import android.media.MediaMetadata;
import android.media.session.PlaybackState;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What plays, read from {@code dumpsys media_session}, for TVs where Android never lets a notification listener on.
 * Android 9 ignores notification access on low-RAM devices (ro.config.low_ram=true, e.g. a 1 GB projector), so
 * getActiveSessions() is refused there whatever install.sh does. The DUMP permission (granted over ADB) lets the app
 * read the same dump `adb shell dumpsys media_session` prints. It gives the package, state, position, speed and the
 * metadata description (title, subtitle, description); no art, duration or ids, and no way to send play/pause.
 * Format, as printed on Android 9 (MediaSessionRecord.dump):
 *       package=com.google.android.youtube.tv
 *       active=true
 *       state=PlaybackState {state=3, position=13329, buffered position=0, speed=1.0, updated=5101690, actions=379, ...}
 *       metadata:size=5, description=Title, Channel, null
 */
final class SessionDump {
    static final String PERMISSION = "android.permission.DUMP";

    static final class Session {
        String pkg;
        boolean active;
        MediaMetadata meta;
        PlaybackState state;
    }

    private static final Pattern STATE = Pattern.compile(
            "state=(\\d+), position=(-?\\d+), buffered position=-?\\d+, speed=([-0-9.E]+), updated=(-?\\d+), actions=(-?\\d+)");

    private SessionDump() {
    }

    /** Runs dumpsys (blocking: call off the main thread). Empty when the dump can't be read. */
    static List<Session> read() {
        List<String> lines = new ArrayList<>();
        Process p = null;
        try {
            p = new ProcessBuilder("dumpsys", "media_session").redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                for (String l; (l = r.readLine()) != null; ) lines.add(l.trim());
            }
        } catch (Exception e) {
            return new ArrayList<>();
        } finally {
            if (p != null) p.destroy();
        }
        return parse(lines);
    }

    static List<Session> parse(List<String> lines) {
        List<Session> out = new ArrayList<>();
        Session s = null;
        for (String l : lines) {
            if (l.startsWith("package=")) {
                s = new Session();
                s.pkg = l.substring("package=".length());
                out.add(s);
            } else if (s == null) {
                continue;
            } else if (l.startsWith("active=")) {
                s.active = l.endsWith("true");
            } else if (l.startsWith("state=PlaybackState")) {
                Matcher m = STATE.matcher(l);
                if (m.find()) {
                    try {
                        s.state = new PlaybackState.Builder()
                                .setState(Integer.parseInt(m.group(1)), Long.parseLong(m.group(2)),
                                        Float.parseFloat(m.group(3)), Long.parseLong(m.group(4)))
                                .setActions(Long.parseLong(m.group(5)))
                                .build();
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else if (l.startsWith("metadata:")) {
                s.meta = metadata(l);
            } else if (l.startsWith("Audio playback") || l.startsWith("Sessions Stack")) {
                s = null;   // the next block is not part of this session
            }
        }
        return out;
    }

    /**
     * "metadata:size=5, description=Title, Subtitle, Description" (MediaDescription.toString joins the three with
     * ", " and prints a missing one as "null"). Split from the right: titles carry commas far more often than
     * channel or artist names do.
     */
    private static MediaMetadata metadata(String l) {
        int at = l.indexOf("description=");
        if (at < 0) return null;
        String d = l.substring(at + "description=".length());
        int b = d.lastIndexOf(", ");
        if (b < 0) return null;
        int a = d.lastIndexOf(", ", b - 1);
        if (a < 0) return null;
        String title = d.substring(0, a);
        String subtitle = d.substring(a + 2, b);
        if (title.isEmpty() || title.equals("null")) return null;
        MediaMetadata.Builder m = new MediaMetadata.Builder().putText(MediaMetadata.METADATA_KEY_TITLE, title);
        if (!subtitle.isEmpty() && !subtitle.equals("null")) m.putText(MediaMetadata.METADATA_KEY_ARTIST, subtitle);
        return m.build();
    }
}
