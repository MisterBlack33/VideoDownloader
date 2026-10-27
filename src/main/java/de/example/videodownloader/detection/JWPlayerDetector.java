package de.example.videodownloader.detection;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Erkennt JW Player (insbesondere Version 8.x) anhand typischer HTML-/JavaScript-Muster,
 * nicht nur anhand von sichtbarem Text wie "Powered by JW Player".
 */
public class JWPlayerDetector implements PlayerDetector {

    private static final int PRIORITY = 100;

    // "jwplayer(" Aufruf, z. B. jwplayer("player").setup({...})
    private static final Pattern JW_SETUP_CALL =
            Pattern.compile("jwplayer\\s*\\(", Pattern.CASE_INSENSITIVE);

    // Version aus Dateinamen, z. B. jwplayer-8.26.0.js oder jwplayer.js?v=8.26.0
    private static final Pattern VERSION_FROM_SCRIPT =
            Pattern.compile("jwplayer[-/]?(\\d+\\.\\d+(?:\\.\\d+)?)\\.js", Pattern.CASE_INSENSITIVE);

    // Version aus sichtbarem Text: "Powered by JW Player 8.26.0"
    private static final Pattern VERSION_FROM_TEXT =
            Pattern.compile("Powered by JW Player\\s+(\\d+\\.\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);

    // Version aus internem JS-Objekt, z. B. jwplayer.version = "8.26.0"
    private static final Pattern VERSION_FROM_JS_OBJECT =
            Pattern.compile("jwplayer\\.version\\s*=\\s*[\"']([\\d.]+)[\"']", Pattern.CASE_INSENSITIVE);

    // file: "https://.../video.m3u8" innerhalb einer setup()-Konfiguration
    private static final Pattern FILE_PROPERTY_WITH_EXTENSION = Pattern.compile(
            "[\"']?file[\"']?\\s*:\\s*[\"']([^\"']+\\.(?:m3u8|mpd|mp4|webm|m4v|mov)[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);

    // Fallback: file-Eigenschaft OHNE erkennbare Dateiendung, z. B. signierte/generierte
    // URLs wie "getVideo.php?id=123". Der Streamtyp wird in diesem Fall ueber das
    // begleitende "type"-Attribut ermittelt (siehe StreamTypeResolver.resolveByMimeType).
    private static final Pattern ANY_FILE_PROPERTY = Pattern.compile(
            "[\"']?file[\"']?\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    // sources: [ { file: "...", type: "..." }, ... ] - Block wird als Ganzes erfasst,
    // damit file UND type gemeinsam ausgewertet werden koennen.
    private static final Pattern SOURCE_BLOCK = Pattern.compile(
            "\\{([^{}]*[\"']?file[\"']?\\s*:\\s*[\"']([^\"']+)[\"'][^{}]*)\\}",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TYPE_PROPERTY = Pattern.compile(
            "[\"']?type[\"']?\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern PROVIDER_TEXT =
            Pattern.compile("Provider\\s+(hlsjs|html5|flash|shaka)", Pattern.CASE_INSENSITIVE);

    private static final Pattern STREAM_TYPE_TEXT =
            Pattern.compile("Stream\\s*Type\\s+(hls|dash|mp4)", Pattern.CASE_INSENSITIVE);

    @Override
    public String getName() {
        return "JW Player";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean supports(String html, URI pageUrl) {
        if (html == null) {
            return false;
        }
        return JW_SETUP_CALL.matcher(html).find()
                || html.toLowerCase().contains("jwplayer")
                || html.toLowerCase().contains("jwplatform");
    }

    @Override
    public PlayerDetectionResult detect(String html, URI pageUrl) {
        if (html == null || !supports(html, pageUrl)) {
            return PlayerDetectionResult.notDetected();
        }

        int confidence = 0;
        Map<String, String> metadata = new HashMap<>();

        boolean hasSetupCall = JW_SETUP_CALL.matcher(html).find();
        if (hasSetupCall) {
            confidence += 60;
        }

        String version = extractVersion(html);
        if (version != null) {
            metadata.put("version", version);
            confidence += 20;
        }

        String provider = firstGroupOrNull(PROVIDER_TEXT, html);
        if (provider != null) {
            metadata.put("provider", provider);
        }

        String streamTypeHint = firstGroupOrNull(STREAM_TYPE_TEXT, html);
        if (streamTypeHint != null) {
            metadata.put("streamTypeHint", streamTypeHint);
        }

        StreamCandidate streamCandidate = extractStreamUrl(html);
        if (streamCandidate != null) {
            metadata.put("streamUrl", streamCandidate.url);
            if (streamCandidate.mimeType != null) {
                metadata.put("streamUrlType", streamCandidate.mimeType);
            }
            confidence += 20;
        }
        String streamUrl = streamCandidate != null ? streamCandidate.url : null;

        if (!hasSetupCall && streamUrl == null && version == null) {
            // Nur der Text "jwplayer" kam vor, aber keine echte Konfiguration erkennbar.
            confidence = 10;
        }

        confidence = Math.min(confidence, 100);

        if (confidence <= 0) {
            return PlayerDetectionResult.notDetected();
        }

        return new PlayerDetectionResult(getName(), version, confidence, metadata);
    }

    private String extractVersion(String html) {
        String v = firstGroupOrNull(VERSION_FROM_JS_OBJECT, html);
        if (v != null) return v;
        v = firstGroupOrNull(VERSION_FROM_TEXT, html);
        if (v != null) return v;
        v = firstGroupOrNull(VERSION_FROM_SCRIPT, html);
        return v;
    }

    /** Ergebnis der Stream-URL-Suche: URL plus (falls vorhanden) begleitender MIME-Type. */
    private static class StreamCandidate {
        final String url;
        final String mimeType;

        StreamCandidate(String url, String mimeType) {
            this.url = url;
            this.mimeType = mimeType;
        }
    }

    /**
     * Sucht nach der ersten plausiblen Stream-URL innerhalb einer jwplayer-Konfiguration.
     * Reihenfolge:
     * 1. "sources"-Bloecke mit erkennbarer Dateiendung (file + optional type)
     * 2. direkte "file"-Eigenschaft mit erkennbarer Dateiendung
     * 3. "sources"-Bloecke OHNE erkennbare Dateiendung, aber mit auswertbarem "type"
     *    (z. B. signierte/generierte URLs) - der Streamtyp wird dann ueber den MIME-Type
     *    ermittelt (siehe StreamTypeResolver)
     * 4. direkte "file"-Eigenschaft ohne erkennbare Dateiendung, als letzter Fallback
     */
    private StreamCandidate extractStreamUrl(String html) {
        Matcher sourceMatcher = SOURCE_BLOCK.matcher(html);
        StreamCandidate fallbackWithoutExtension = null;

        while (sourceMatcher.find()) {
            String block = sourceMatcher.group(1);
            String candidateUrl = sourceMatcher.group(2);
            String type = firstGroupOrNull(TYPE_PROPERTY, block);

            if (looksLikeStreamUrl(candidateUrl)) {
                return new StreamCandidate(candidateUrl, type);
            }
            if (fallbackWithoutExtension == null && type != null) {
                fallbackWithoutExtension = new StreamCandidate(candidateUrl, type);
            }
        }

        Matcher fileMatcher = FILE_PROPERTY_WITH_EXTENSION.matcher(html);
        if (fileMatcher.find()) {
            return new StreamCandidate(fileMatcher.group(1), null);
        }

        if (fallbackWithoutExtension != null) {
            return fallbackWithoutExtension;
        }

        Matcher anyFileMatcher = ANY_FILE_PROPERTY.matcher(html);
        if (anyFileMatcher.find()) {
            return new StreamCandidate(anyFileMatcher.group(1), null);
        }

        return null;
    }

    private boolean looksLikeStreamUrl(String candidate) {
        if (candidate == null) return false;
        String lower = candidate.toLowerCase();
        return lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains(".mp4")
                || lower.contains(".webm") || lower.contains(".m4v") || lower.contains(".mov");
    }

    private String firstGroupOrNull(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
