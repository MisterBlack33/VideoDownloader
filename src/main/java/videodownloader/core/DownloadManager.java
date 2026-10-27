package videodownloader.core;
import videodownloader.models.DownloadResult;
import videodownloader.download.DownloadStrategy;
import videodownloader.models.VideoInfo;


import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Verantwortlich fuer die Auswahl der passenden {@link DownloadStrategy} anhand
 * des erkannten Streamtyps sowie den Start des Downloads.
 */
public class DownloadManager {

    private final List<DownloadStrategy> strategies = new ArrayList<>();

    public void registerStrategy(DownloadStrategy strategy) {
        strategies.add(strategy);
    }

    public DownloadStrategy findStrategy(VideoInfo video) {
        for (DownloadStrategy strategy : strategies) {
            if (strategy.supports(video)) {
                return strategy;
            }
        }
        return null;
    }

    public DownloadResult download(VideoInfo video, Path output) {
        DownloadStrategy strategy = findStrategy(video);
        if (strategy == null) {
            return DownloadResult.failure("Kein unterstuetzter Videostream gefunden.");
        }
        return strategy.download(video, output);
    }

    public List<DownloadStrategy> getStrategies() {
        return strategies;
    }
}
