package videodownloader.download;
import videodownloader.models.DownloadResult;
import videodownloader.exceptions.FfmpegException;
import videodownloader.ffmpeg.FfmpegService;
import videodownloader.hls.HlsAnalyzer;
import videodownloader.exceptions.HlsException;
import videodownloader.hls.HlsVariant;
import videodownloader.models.StreamType;
import videodownloader.models.VideoInfo;


import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Verarbeitet HLS-Streams (.m3u8): laedt und analysiert das Manifest, waehlt bei
 * einer Master Playlist (als Sicherheitsnetz, falls noch keine Variante gewaehlt wurde)
 * automatisch die hoechste Qualitaetsstufe und uebergibt die finale URL an FFmpeg.
 *
 * Die interaktive Qualitaetsauswahl findet regulaer bereits eine Ebene hoeher in
 * {@link videodownloader.core.VideoDownloader} statt; diese Strategie
 * bleibt dadurch eigenstaendig einsetz- und testbar (auch ohne Konsolen-UI).
 */
public class HlsDownloadStrategy implements DownloadStrategy {

    private final HlsAnalyzer hlsAnalyzer;
    private final FfmpegService ffmpegService;
    private final boolean debug;
    private final Consumer<FfmpegService.Progress> progressListener;

    public HlsDownloadStrategy(HlsAnalyzer hlsAnalyzer, FfmpegService ffmpegService, boolean debug,
                                Consumer<FfmpegService.Progress> progressListener) {
        this.hlsAnalyzer = hlsAnalyzer;
        this.ffmpegService = ffmpegService;
        this.debug = debug;
        this.progressListener = progressListener;
    }

    @Override
    public boolean supports(VideoInfo video) {
        return video != null && video.getStreamType() == StreamType.HLS && video.getStreamUrl() != null;
    }

    @Override
    public DownloadResult download(VideoInfo video, Path output) {
        try {
            URI manifestUrl = video.getStreamUrl();
            String manifestContent = hlsAnalyzer.loadManifest(manifestUrl);
            HlsAnalyzer.ManifestAnalysis analysis = hlsAnalyzer.analyze(manifestContent, manifestUrl);

            URI finalStreamUrl;
            if (analysis.isMasterPlaylist) {
                HlsVariant chosen = hlsAnalyzer.selectHighestQuality(analysis.variants);
                if (chosen == null) {
                    return DownloadResult.failure("Keine gueltige HLS-Qualitaetsstufe gefunden.");
                }
                finalStreamUrl = chosen.getUrl();
            } else {
                finalStreamUrl = manifestUrl;
            }

            if (debug) {
                System.out.println("[DEBUG] HLS URL: " + manifestUrl);
                System.out.println("[DEBUG] Selected resolution/stream: " + finalStreamUrl);
            }

            try {
                ffmpegService.remuxToMp4(finalStreamUrl, output, debug, progressListener);
            } catch (FfmpegException copyFailed) {
                // Fallback: Re-Encoding versuchen, falls reines Copy-Remuxing scheitert.
                if (debug) {
                    System.out.println("[DEBUG] Copy-Remuxing fehlgeschlagen, versuche Re-Encoding: "
                            + copyFailed.getMessage());
                }
                Files.deleteIfExists(output);
                ffmpegService.transcodeToMp4(finalStreamUrl, output, debug, progressListener);
            }

            if (!Files.exists(output)) {
                return DownloadResult.failure("FFmpeg hat keine Ausgabedatei erzeugt.");
            }

            long size = Files.size(output);
            return DownloadResult.success(output, size);

        } catch (HlsException e) {
            return DownloadResult.failure(e.getMessage());
        } catch (FfmpegException e) {
            return DownloadResult.failure(e.getMessage());
        } catch (Exception e) {
            return DownloadResult.failure("Unerwarteter Fehler bei der HLS-Verarbeitung: " + e.getMessage());
        }
    }
}
