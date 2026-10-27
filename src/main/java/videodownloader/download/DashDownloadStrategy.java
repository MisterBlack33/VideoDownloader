package videodownloader.download;
import videodownloader.models.DownloadResult;
import videodownloader.exceptions.FfmpegException;
import videodownloader.ffmpeg.FfmpegService;
import videodownloader.models.StreamType;
import videodownloader.models.VideoInfo;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Verarbeitet DASH-Streams (.mpd) ueber FFmpeg. Die vollstaendige Variantenauswahl
 * innerhalb des MPD-Manifests ist fuer die erste Version noch nicht implementiert;
 * FFmpeg uebernimmt aktuell die komplette Verarbeitung der Manifest-URL direkt.
 * Die Architektur (eigene Strategie-Implementierung) erlaubt eine spaetere Erweiterung,
 * ohne bestehende Klassen aendern zu muessen.
 */
public class DashDownloadStrategy implements DownloadStrategy {

    private final FfmpegService ffmpegService;
    private final boolean debug;
    private final Consumer<FfmpegService.Progress> progressListener;

    public DashDownloadStrategy(FfmpegService ffmpegService, boolean debug,
                                 Consumer<FfmpegService.Progress> progressListener) {
        this.ffmpegService = ffmpegService;
        this.debug = debug;
        this.progressListener = progressListener;
    }

    @Override
    public boolean supports(VideoInfo video) {
        return video != null && video.getStreamType() == StreamType.DASH && video.getStreamUrl() != null;
    }

    @Override
    public DownloadResult download(VideoInfo video, Path output) {
        try {
            if (debug) {
                System.out.println("[DEBUG] DASH-Manifest wird direkt an FFmpeg uebergeben: " + video.getStreamUrl());
            }
            ffmpegService.remuxToMp4(video.getStreamUrl(), output, debug, progressListener);

            if (!Files.exists(output)) {
                return DownloadResult.failure("FFmpeg hat keine Ausgabedatei erzeugt.");
            }
            long size = Files.size(output);
            return DownloadResult.success(output, size);
        } catch (FfmpegException e) {
            return DownloadResult.failure(e.getMessage());
        } catch (Exception e) {
            return DownloadResult.failure("Unerwarteter Fehler bei der DASH-Verarbeitung: " + e.getMessage());
        }
    }
}
