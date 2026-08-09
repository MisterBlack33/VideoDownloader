package de.example.videodownloader.util;

import de.example.videodownloader.models.StreamType;

/**
 * Ermittelt unabhaengig vom erkannten Player, um welchen Streamtyp es sich
 * bei einer gegebenen URL handelt (Direct File, HLS oder DASH).
 */
public final class StreamTypeResolver {

    private StreamTypeResolver() {
    }

    public static StreamType resolve(String url) {
        if (url == null) {
            return StreamType.UNKNOWN;
        }
        String lower = url.toLowerCase();
        // Query-Parameter abschneiden, um die eigentliche Dateiendung zu pruefen
        int queryIndex = lower.indexOf('?');
        String withoutQuery = queryIndex >= 0 ? lower.substring(0, queryIndex) : lower;

        if (withoutQuery.endsWith(".m3u8")) {
            return StreamType.HLS;
        }
        if (withoutQuery.endsWith(".mpd")) {
            return StreamType.DASH;
        }
        if (withoutQuery.endsWith(".mp4") || withoutQuery.endsWith(".webm")
                || withoutQuery.endsWith(".m4v") || withoutQuery.endsWith(".mov")) {
            return StreamType.DIRECT_FILE;
        }
        // Manche Server liefern .m3u8 trotzdem nur ueber Query-Parameter erkennbar
        if (lower.contains(".m3u8")) {
            return StreamType.HLS;
        }
        if (lower.contains(".mpd")) {
            return StreamType.DASH;
        }
        return StreamType.UNKNOWN;
    }
}
