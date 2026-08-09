package de.example.videodownloader.core;

import de.example.videodownloader.detection.*;
import de.example.videodownloader.player.Html5VideoHandler;
import de.example.videodownloader.player.JWPlayerHandler;
import de.example.videodownloader.player.PlayerHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class PlayerManagerTest {

    private PlayerManager playerManager;
    private final URI pageUrl = URI.create("https://example.com/video");

    @BeforeEach
    void setUp() {
        playerManager = new PlayerManager();
        playerManager.registerDetector(new JWPlayerDetector());
        playerManager.registerDetector(new VideoJsDetector());
        playerManager.registerDetector(new PlyrDetector());
        playerManager.registerDetector(new ShakaPlayerDetector());
        playerManager.registerDetector(new Html5VideoDetector());

        playerManager.registerHandler(new JWPlayerHandler());
        playerManager.registerHandler(new Html5VideoHandler());
    }

    @Test
    void detectsJwPlayerWhenPresent() {
        String html = "<script>jwplayer(\"p\").setup({file:\"v.m3u8\"});</script>Powered by JW Player 8.26.0";

        PlayerDetectionResult result = playerManager.detect(html, pageUrl);

        assertTrue(result.isDetected());
        assertEquals("JW Player", result.getPlayerName());
    }

    @Test
    void fallsBackToHtml5WhenNoSpecificPlayerDetected() {
        String html = "<video src=\"plain.mp4\"></video>";

        PlayerDetectionResult result = playerManager.detect(html, pageUrl);

        assertTrue(result.isDetected());
        assertEquals("HTML5 Video", result.getPlayerName());
    }

    @Test
    void returnsNotDetectedWhenNothingMatches() {
        String html = "<html><body>Kein Video</body></html>";

        PlayerDetectionResult result = playerManager.detect(html, pageUrl);

        assertFalse(result.isDetected());
    }

    @Test
    void prefersSpecificDetectorOverMultipleSimultaneousMatches() {
        // Sowohl JW Player als auch ein generisches <video>-Tag sind gleichzeitig vorhanden.
        String html = "<video src=\"fallback.mp4\"></video>" +
                "<script>jwplayer(\"p\").setup({file:\"v.m3u8\"});</script>Powered by JW Player 8.26.0";

        PlayerDetectionResult result = playerManager.detect(html, pageUrl);

        assertEquals("JW Player", result.getPlayerName());
    }

    @Test
    void findsMatchingHandlerForDetectedPlayer() {
        String html = "<script>jwplayer(\"p\").setup({file:\"v.m3u8\"});</script>Powered by JW Player 8.26.0";
        PlayerDetectionResult result = playerManager.detect(html, pageUrl);

        PlayerHandler handler = playerManager.findHandler(result);

        assertNotNull(handler);
        assertEquals("JW Player", handler.getName());
    }

    @Test
    void returnsNullHandlerWhenNoDetectionMatches() {
        assertNull(playerManager.findHandler(PlayerDetectionResult.notDetected()));
        assertNull(playerManager.findHandler(null));
    }
}
