package videodownloader.detection;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Erkennt den Shaka Player anhand von Skript-Referenzen und typischer Initialisierung. */
public class ShakaPlayerDetector implements PlayerDetector {

    private static final int PRIORITY = 70;

    private static final Pattern SCRIPT_REF =
            Pattern.compile("shaka-player(?:[-.](\\d+\\.\\d+(?:\\.\\d+)?))?(?:\\.compiled)?\\.js",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern JS_INIT =
            Pattern.compile("new\\s+shaka\\.Player\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final Pattern LOAD_CALL = Pattern.compile(
            "\\.load\\s*\\(\\s*[\"']([^\"']+\\.(?:m3u8|mpd)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

    @Override
    public String getName() {
        return "Shaka Player";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean supports(String html, URI pageUrl) {
        if (html == null) return false;
        return SCRIPT_REF.matcher(html).find() || JS_INIT.matcher(html).find();
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
            confidence += 40;
        }
        if (JS_INIT.matcher(html).find()) {
            confidence += 40;
        }

        String streamUrl = firstGroupOrNull(LOAD_CALL, html);
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
