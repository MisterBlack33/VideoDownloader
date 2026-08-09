package de.example.videodownloader.hls;

import de.example.videodownloader.exceptions.HlsException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HlsAnalyzerTest {

    private final HlsAnalyzer analyzer = new HlsAnalyzer();
    private final URI manifestUrl = URI.create("https://example.com/video/playlist.m3u8");

    @Test
    void detectsMasterPlaylist() throws HlsException {
        String master = "#EXTM3U\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=854x480\n" +
                "video_480p.m3u8\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720\n" +
                "video_720p.m3u8\n";

        HlsAnalyzer.ManifestAnalysis result = analyzer.analyze(master, manifestUrl);

        assertTrue(result.isMasterPlaylist);
        assertEquals(2, result.variants.size());
    }

    @Test
    void detectsMediaPlaylist() throws HlsException {
        String media = "#EXTM3U\n" +
                "#EXTINF:5.0,\n" +
                "segment001.ts\n" +
                "#EXTINF:5.0,\n" +
                "segment002.ts\n";

        HlsAnalyzer.ManifestAnalysis result = analyzer.analyze(media, manifestUrl);

        assertFalse(result.isMasterPlaylist);
        assertTrue(result.variants.isEmpty());
    }

    @Test
    void resolvesRelativeVariantUrls() throws HlsException {
        String master = "#EXTM3U\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=854x480\n" +
                "video_480p.m3u8\n";

        HlsAnalyzer.ManifestAnalysis result = analyzer.analyze(master, manifestUrl);

        assertEquals("https://example.com/video/video_480p.m3u8", result.variants.get(0).getUrl().toString());
    }

    @Test
    void resolvesAbsoluteVariantUrls() throws HlsException {
        String master = "#EXTM3U\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=854x480\n" +
                "https://cdn.example.com/video_480p.m3u8\n";

        HlsAnalyzer.ManifestAnalysis result = analyzer.analyze(master, manifestUrl);

        assertEquals("https://cdn.example.com/video_480p.m3u8", result.variants.get(0).getUrl().toString());
    }

    @Test
    void selectsMultipleResolutions() throws HlsException {
        String master = "#EXTM3U\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=854x480\n" +
                "video_480p.m3u8\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720\n" +
                "video_720p.m3u8\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080\n" +
                "video_1080p.m3u8\n";

        HlsAnalyzer.ManifestAnalysis result = analyzer.analyze(master, manifestUrl);

        assertEquals(3, result.variants.size());
        HlsVariant best = analyzer.selectHighestQuality(result.variants);
        assertEquals(1920, best.getWidth());
        assertEquals(1080, best.getHeight());
    }

    @Test
    void throwsOnMissingManifestMarker() {
        String invalid = "this is not an hls manifest";
        assertThrows(HlsException.class, () -> analyzer.analyze(invalid, manifestUrl));
    }

    @Test
    void throwsOnEmptyManifest() {
        assertThrows(HlsException.class, () -> analyzer.analyze("", manifestUrl));
        assertThrows(HlsException.class, () -> analyzer.analyze(null, manifestUrl));
    }

    @Test
    void selectHighestQualityReturnsNullForEmptyList() {
        assertNull(analyzer.selectHighestQuality(List.of()));
        assertNull(analyzer.selectHighestQuality(null));
    }

    @Test
    void fallsBackToBandwidthWhenNoResolutionPresent() throws HlsException {
        String master = "#EXTM3U\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=800000\n" +
                "audio_low.m3u8\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=2800000\n" +
                "audio_high.m3u8\n";

        HlsAnalyzer.ManifestAnalysis result = analyzer.analyze(master, manifestUrl);
        HlsVariant best = analyzer.selectHighestQuality(result.variants);

        assertEquals(2800000, best.getBandwidth());
    }
}
