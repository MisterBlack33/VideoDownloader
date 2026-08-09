package de.example.videodownloader.detection;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generischer Fallback-Detector fuer einfache &lt;video&gt;-Tags ohne erkennbaren
 * spezifischen Player. Hat bewusst die niedrigste Prioritaet, damit spezifische
 * Player-Detectoren immer bevorzugt werden.
 */
public class Html5VideoDetector implements PlayerDetector {

    private static final int PRIORITY = 10;

    private static final Pattern VIDEO_TAG =
            Pattern.compile("<video\\b[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern DIRECT_SRC_ATTR = Pattern.compile(
            "<video\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+\\.(?:mp4|webm|m4v|mov|m3u8|mpd)[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern SOURCE_TAG = Pattern.compile(
            "<source\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+\\.(?:mp4|webm|m4v|mov|m3u8|mpd)[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public String getName() {
        return "HTML5 Video";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean supports(String html, URI pageUrl) {
        return html != null && VIDEO_TAG.matcher(html).find();
    }

    @Override
    public PlayerDetectionResult detect(String html, URI pageUrl) {
        if (html == null || !supports(html, pageUrl)) {
            return PlayerDetectionResult.notDetected();
        }

        Map<String, String> metadata = new HashMap<>();
        int confidence = 40; // <video>-Tag vorhanden, aber kein spezifischer Player

        String streamUrl = firstGroupOrNull(DIRECT_SRC_ATTR, html);
        if (streamUrl == null) {
            streamUrl = firstGroupOrNull(SOURCE_TAG, html);
        }
        if (streamUrl != null) {
            metadata.put("streamUrl", streamUrl);
            confidence += 20;
        }

        return new PlayerDetectionResult(getName(), null, Math.min(confidence, 100), metadata);
    }

    private String firstGroupOrNull(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
