package videodownloader.detection;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class JWPlayerDetectorTest {

    private final JWPlayerDetector detector = new JWPlayerDetector();
    private final URI pageUrl = URI.create("https://example.com/video");

    @Test
    void detectsJwPlayerWithFileProperty() {
        String html = "<script>jwplayer(\"player\").setup({file: \"https://example.com/video.m3u8\"});</script>";

        assertTrue(detector.supports(html, pageUrl));
        PlayerDetectionResult result = detector.detect(html, pageUrl);

        assertTrue(result.isDetected());
        assertEquals("JW Player", result.getPlayerName());
        assertEquals("https://example.com/video.m3u8", result.getMetadata("streamUrl"));
    }

    @Test
    void detectsJwPlayerWithSourcesArray() {
        String html = "<script>jwplayer(\"player\").setup({" +
                "sources: [{ file: \"https://example.com/video.m3u8\", type: \"application/x-mpegURL\" }]" +
                "});</script>";

        PlayerDetectionResult result = detector.detect(html, pageUrl);

        assertTrue(result.isDetected());
        assertTrue(result.getMetadata("streamUrl").contains(".m3u8"));
    }

    @Test
    void extractsVersionFromPoweredByText() {
        String html = "<script>jwplayer(\"p\").setup({file:\"v.m3u8\"});</script>Powered by JW Player 8.26.0";

        PlayerDetectionResult result = detector.detect(html, pageUrl);

        assertEquals("8.26.0", result.getPlayerVersion());
    }

    @Test
    void doesNotDetectWhenJwplayerAbsent() {
        String html = "<html><body>Kein Player hier</body></html>";

        assertFalse(detector.supports(html, pageUrl));
        assertFalse(detector.detect(html, pageUrl).isDetected());
    }

    @Test
    void hasHighPriorityOverGenericDetectors() {
        assertTrue(detector.getPriority() > 50);
    }

    @Test
    void returnsNotDetectedForNullHtml() {
        assertFalse(detector.detect(null, pageUrl).isDetected());
    }
}
