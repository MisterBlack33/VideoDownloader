package videodownloader.detection;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Erkennt Video.js anhand von Skript-Referenzen und typischen DOM-/JS-Mustern. */
public class VideoJsDetector implements PlayerDetector {

    private static final int PRIORITY = 90;

    private static final Pattern SCRIPT_REF =
            Pattern.compile("video\\.?js(?:[-.](\\d+\\.\\d+(?:\\.\\d+)?))?\\.js", Pattern.CASE_INSENSITIVE);

    private static final Pattern CLASS_ATTR =
            Pattern.compile("class\\s*=\\s*[\"'][^\"']*video-js[^\"']*[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern JS_INIT =
            Pattern.compile("videojs\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final Pattern SOURCE_SRC = Pattern.compile(
            "src\\s*:\\s*[\"']([^\"']+\\.(?:m3u8|mpd|mp4|webm|m4v|mov)[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String getName() {
        return "Video.js";
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
            confidence += 30;
        }
        if (JS_INIT.matcher(html).find()) {
            confidence += 30;
        }

        String streamUrl = firstGroupOrNull(SOURCE_SRC, html);
        if (streamUrl != null) {
            metadata.put("streamUrl", streamUrl);
            confidence += 10;
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
