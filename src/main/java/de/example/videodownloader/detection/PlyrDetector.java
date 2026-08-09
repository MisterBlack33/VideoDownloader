package de.example.videodownloader.detection;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Erkennt den Plyr-Player anhand von Skript-Referenzen und typischen DOM-Klassen. */
public class PlyrDetector implements PlayerDetector {

    private static final int PRIORITY = 80;

    private static final Pattern SCRIPT_REF =
            Pattern.compile("plyr(?:[-.](\\d+\\.\\d+(?:\\.\\d+)?))?\\.(?:js|min\\.js)", Pattern.CASE_INSENSITIVE);

    private static final Pattern CLASS_ATTR =
            Pattern.compile("class\\s*=\\s*[\"'][^\"']*\\bplyr\\b[^\"']*[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern JS_INIT =
            Pattern.compile("new\\s+Plyr\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final Pattern DATA_SOURCE = Pattern.compile(
            "src\\s*[:=]\\s*[\"']([^\"']+\\.(?:m3u8|mpd|mp4|webm|m4v|mov)[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String getName() {
        return "Plyr";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean supports(String html, URI pageUrl) {
        if (html == null) return false;
        return SCRIPT_REF.matcher(html).find()
                || CLASS_ATTR.matcher(html).find()
                || JS_INIT.matcher(html).find();
    }

    @Override
    public PlayerDetectionResult detect(String html, URI pageUrl) {
        if (html == null || !supports(html, pageUrl)) {
            return PlayerDetectionResult.notDetected();
        }

        int confidence = 0;
        Map<String, String> metadata = new HashMap<>();

        String version = firstGroupOrNull(SCRIPT_REF, html);
        if (version != null) {
            metadata.put("version", version);
            confidence += 30;
        }
        if (CLASS_ATTR.matcher(html).find()) {
            confidence += 25;
        }
        if (JS_INIT.matcher(html).find()) {
            confidence += 25;
        }

        String streamUrl = firstGroupOrNull(DATA_SOURCE, html);
        if (streamUrl != null) {
            metadata.put("streamUrl", streamUrl);
            confidence += 20;
        }

        confidence = Math.min(confidence, 100);
        if (confidence <= 0) {
            return PlayerDetectionResult.notDetected();
        }

        return new PlayerDetectionResult(getName(), version, confidence, metadata);
    }

    private String firstGroupOrNull(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        if (m.find() && m.groupCount() >= 1) {
            return m.group(1);
        }
        return null;
    }
}
