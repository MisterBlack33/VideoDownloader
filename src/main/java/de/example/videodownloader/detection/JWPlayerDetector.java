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
    private static final Pattern FILE_PROPERTY = Pattern.compile(
            "[\"']?file[\"']?\\s*:\\s*[\"']([^\"']+\\.(?:m3u8|mpd|mp4|webm|m4v|mov)[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);

    // sources: [ { file: "...", type: "..." }, ... ]
    private static final Pattern SOURCE_BLOCK = Pattern.compile(
            "\\{[^{}]*[\"']?file[\"']?\\s*:\\s*[\"']([^\"']+)[\"'][^{}]*\\}",
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

        String streamUrl = extractStreamUrl(html);
        if (streamUrl != null) {
            metadata.put("streamUrl", streamUrl);
            confidence += 20;
        }

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

    /**
     * Sucht nach der ersten plausiblen Stream-URL innerhalb einer jwplayer-Konfiguration:
     * zunaechst innerhalb von "sources"-Bloecken, dann direkt ueber "file"-Eigenschaften.
     */
    private String extractStreamUrl(String html) {
        Matcher sourceMatcher = SOURCE_BLOCK.matcher(html);
        while (sourceMatcher.find()) {
            String candidate = sourceMatcher.group(1);
            if (looksLikeStreamUrl(candidate)) {
                return candidate;
            }
        }

        Matcher fileMatcher = FILE_PROPERTY.matcher(html);
        if (fileMatcher.find()) {
            return fileMatcher.group(1);
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
