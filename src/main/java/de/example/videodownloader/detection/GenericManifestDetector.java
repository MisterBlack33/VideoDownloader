package de.example.videodownloader.detection;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Playerunabhaengiger Fallback: findet beliebige .m3u8/.mpd-URLs im HTML/JS.
 * Prioritaet liegt zwischen spezifischen Playern und dem HTML5-Fallback.
 */
public class GenericManifestDetector implements PlayerDetector {

    public static final String NAME = "Generic Stream";
    private static final int PRIORITY = 20;

    private static final Pattern MANIFEST_URL = Pattern.compile(
            "[\"'(]([^\"'\\s<>()\\\\]+\\.(?:m3u8|mpd)(?:\\?[^\"'\\s<>()\\\\]*)?)[\"')]",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean supports(String html, URI pageUrl) {
        return html != null && findManifest(html) != null;
    }

    @Override
    public PlayerDetectionResult detect(String html, URI pageUrl) {
        if (html == null) {
            return PlayerDetectionResult.notDetected();
        }
        String url = findManifest(html);
        if (url == null) {
            return PlayerDetectionResult.notDetected();
        }
        Map<String, String> metadata = new HashMap<>();
        metadata.put("streamUrl", url);
        return new PlayerDetectionResult(NAME, null, 50, metadata);
    }

    private String findManifest(String html) {
        // JSON-Escapes und HTML-Entities normalisieren
        String normalized = html.replace("\\/", "/").replace("&amp;", "&");
        Matcher m = MANIFEST_URL.matcher(normalized);
        return m.find() ? m.group(1) : null;
    }
}