package de.example.videodownloader.detection;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class VideoJsDetectorTest {

    private final VideoJsDetector detector = new VideoJsDetector();
    private final URI pageUrl = URI.create("https://example.com/video");

    @Test
    void detectsViaClassAttribute() {
        String html = "<video-js class=\"video-js vjs-default-skin\"></video-js>";

        assertTrue(detector.supports(html, pageUrl));
        assertTrue(detector.detect(html, pageUrl).isDetected());
    }

    @Test
    void detectsViaScriptReferenceAndExtractsVersion() {
        String html = "<script src=\"https://cdn.example.com/video.js-7.20.3.js\"></script>";

        PlayerDetectionResult result = detector.detect(html, pageUrl);

        assertTrue(result.isDetected());
        assertEquals("7.20.3", result.getPlayerVersion());
    }

    @Test
    void detectsViaInitCall() {
        String html = "<script>videojs('my-player', {});</script>";

        assertTrue(detector.detect(html, pageUrl).isDetected());
    }

    @Test
    void doesNotDetectUnrelatedHtml() {
        String html = "<html><body>Nichts hier</body></html>";

        assertFalse(detector.supports(html, pageUrl));
    }

    @Test
    void hasLowerPriorityThanJwPlayer() {
        JWPlayerDetector jw = new JWPlayerDetector();
        assertTrue(detector.getPriority() < jw.getPriority());
    }
}
