package videodownloader.core;
import videodownloader.models.DownloadResult;
import videodownloader.download.DownloadStrategy;
import videodownloader.models.StreamType;
import videodownloader.models.VideoInfo;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DownloadManagerTest {

    private DownloadManager downloadManager;

    /** Einfache Test-Strategie, die nur fuer einen bestimmten StreamType zustaendig ist. */
    private static class FakeStrategy implements DownloadStrategy {
        private final StreamType supportedType;
        private final String label;

        FakeStrategy(StreamType supportedType, String label) {
            this.supportedType = supportedType;
            this.label = label;
        }

        @Override
        public boolean supports(VideoInfo video) {
            return video != null && video.getStreamType() == supportedType;
        }

        @Override
        public DownloadResult download(VideoInfo video, Path output) {
            return DownloadResult.success(output, 1234L);
        }

        String getLabel() {
            return label;
        }
    }

    private FakeStrategy hlsStrategy;
    private FakeStrategy dashStrategy;
    private FakeStrategy directStrategy;

    @BeforeEach
    void setUp() {
        downloadManager = new DownloadManager();
        hlsStrategy = new FakeStrategy(StreamType.HLS, "hls");
        dashStrategy = new FakeStrategy(StreamType.DASH, "dash");
        directStrategy = new FakeStrategy(StreamType.DIRECT_FILE, "direct");

        downloadManager.registerStrategy(directStrategy);
        downloadManager.registerStrategy(hlsStrategy);
        downloadManager.registerStrategy(dashStrategy);
    }

    private VideoInfo videoOfType(StreamType type) {
        VideoInfo info = new VideoInfo();
        info.setStreamType(type);
        info.setStreamUrl(URI.create("https://example.com/video"));
        return info;
    }

    @Test
    void selectsHlsStrategyForHlsStream() {
        DownloadStrategy strategy = downloadManager.findStrategy(videoOfType(StreamType.HLS));
        assertSame(hlsStrategy, strategy);
    }

    @Test
    void selectsDashStrategyForDashStream() {
        DownloadStrategy strategy = downloadManager.findStrategy(videoOfType(StreamType.DASH));
        assertSame(dashStrategy, strategy);
    }

    @Test
    void selectsDirectFileStrategyForDirectStream() {
        DownloadStrategy strategy = downloadManager.findStrategy(videoOfType(StreamType.DIRECT_FILE));
        assertSame(directStrategy, strategy);
    }

    @Test
    void returnsNullWhenNoStrategyMatches() {
        DownloadStrategy strategy = downloadManager.findStrategy(videoOfType(StreamType.UNKNOWN));
        assertNull(strategy);
    }

    @Test
    void downloadFailsGracefullyWhenNoStrategyMatches() {
        DownloadResult result = downloadManager.download(videoOfType(StreamType.UNKNOWN), Path.of("out.mp4"));
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void downloadDelegatesToMatchingStrategy() {
        DownloadResult result = downloadManager.download(videoOfType(StreamType.HLS), Path.of("out.mp4"));
        assertTrue(result.isSuccess());
    }
}
