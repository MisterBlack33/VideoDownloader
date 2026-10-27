package de.example.videodownloader.detection;

import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class GenericManifestDetectorTest {

    private final GenericManifestDetector detector = new GenericManifestDetector();
    private final URI pageUrl = URI.create("https://example.com/video");

    @Test
    void findsPlainManifestUrl() {
        String html = "<script>var src = \"https://cdn.example.com/v/master.m3u8?t=1\";</script>";
        assertEquals("https://cdn.example.com/v/master.m3u8?t=1",
                detector.detect(html, pageUrl).getMetadata("streamUrl"));
    }

    @Test
    void findsEscapedJsonUrl() {
        String html = "{\"url\":\"https:\\/\\/cdn.example.com\\/v\\/manifest.mpd\"}";
        assertEquals("https://cdn.example.com/v/manifest.mpd",
                detector.detect(html, pageUrl).getMetadata("streamUrl"));
    }

    @Test
    void ignoresHtmlWithoutManifest() {
        assertFalse(detector.supports("<html>nichts</html>", pageUrl));
    }
}