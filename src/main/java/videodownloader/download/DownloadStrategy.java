package videodownloader.download;
import videodownloader.models.DownloadResult;
import videodownloader.models.VideoInfo;


import java.nio.file.Path;

/**
 * Verantwortlich fuer die eigentliche Verarbeitung eines bestimmten Streamtyps
 * (Direct File, HLS, DASH, ...). Unabhaengig davon, welcher Player das Video
 * bereitgestellt hat.
 */
public interface DownloadStrategy {

    boolean supports(VideoInfo video);

    DownloadResult download(VideoInfo video, Path output);
}
