package de.example.videodownloader.core;

import de.example.videodownloader.detection.PlayerDetectionResult;
import de.example.videodownloader.exceptions.HlsException;
import de.example.videodownloader.exceptions.VideoNotFoundException;
import de.example.videodownloader.hls.HlsAnalyzer;
import de.example.videodownloader.hls.HlsVariant;
import de.example.videodownloader.models.DownloadResult;
import de.example.videodownloader.models.StreamType;
import de.example.videodownloader.models.VideoInfo;
import de.example.videodownloader.player.PlayerHandler;
import de.example.videodownloader.util.FileNameSanitizer;
import de.example.videodownloader.web.PageAnalyzer;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Steuert den allgemeinen Downloadprozess:
 * URL -&gt; Webseite -&gt; Player Detection -&gt; Player Handler -&gt; VideoInfo -&gt; Download Strategy -&gt; Datei.
 * Besitzt selbst keine Kenntnis ueber konkrete Player (siehe Abschnitt 35 der Spezifikation).
 */
public class VideoDownloader {

    private final PageAnalyzer pageAnalyzer;
    private final PlayerManager playerManager;
    private final DownloadManager downloadManager;
    private final HlsAnalyzer hlsAnalyzer;
    private final Path outputDirectory;
    private final boolean debug;

    public VideoDownloader(PageAnalyzer pageAnalyzer, PlayerManager playerManager,
                            DownloadManager downloadManager, HlsAnalyzer hlsAnalyzer,
                            Path outputDirectory, boolean debug) {
        this.pageAnalyzer = pageAnalyzer;
        this.playerManager = playerManager;
        this.downloadManager = downloadManager;
        this.hlsAnalyzer = hlsAnalyzer;
        this.outputDirectory = outputDirectory;
        this.debug = debug;
    }

    /** Callback, mit dem der Benutzer bei mehreren verfuegbaren HLS-Qualitaeten eine Auswahl trifft. */
    @FunctionalInterface
    public interface QualityChooser {
        /** @return gewaehlte Variante, oder {@code null} fuer automatische Auswahl der besten Qualitaet. */
        HlsVariant choose(List<HlsVariant> variants);
    }

    public DownloadResult process(URI pageUrl,
                                   Consumer<String> statusListener,
                                   QualityChooser qualityChooser) throws VideoNotFoundException {

        statusListener.accept("[1/6] Lade Webseite...");
        PageAnalyzer.FetchResult fetchResult;
        try {
            fetchResult = pageAnalyzer.fetch(pageUrl);
        } catch (IOException | InterruptedException e) {
            return DownloadResult.failure("Webseite konnte nicht geladen werden.");
        }

        if (debug) {
            System.out.println("[DEBUG] Page URL: " + pageUrl);
            System.out.println("[DEBUG] HTTP Status: " + fetchResult.statusCode);
            System.out.println("[DEBUG] Final URL: " + fetchResult.finalUrl);
        }

        if (fetchResult.statusCode >= 400) {
            return DownloadResult.failure("Webseite konnte nicht geladen werden.\nHTTP Status: " + fetchResult.statusCode);
        }
        statusListener.accept("OK");

        URI finalUrl = fetchResult.finalUrl != null ? fetchResult.finalUrl : pageUrl;

        statusListener.accept("[2/6] Erkenne Videoplayer...");
        PlayerDetectionResult detection = playerManager.detect(fetchResult.html, finalUrl);
        if (debug) {
            System.out.println("[DEBUG] Detected player: " + detection.getPlayerName());
            System.out.println("[DEBUG] Player version: " + detection.getPlayerVersion());
        }
        if (!detection.isDetected()) {
            throw new VideoNotFoundException("Kein unterstuetzter Videostream gefunden.");
        }
        statusListener.accept(detection.getPlayerName()
                + (detection.getPlayerVersion() != null ? " " + detection.getPlayerVersion() : "")
                + " erkannt.");

        PlayerHandler handler = playerManager.findHandler(detection);
        if (handler == null) {
            throw new VideoNotFoundException("Kein unterstuetzter Videostream gefunden.");
        }

        statusListener.accept("[3/6] Analysiere Player-Konfiguration...");
        VideoInfo videoInfo = handler.extractVideoInfo(detection, finalUrl);
        if (videoInfo == null || videoInfo.getStreamUrl() == null
                || videoInfo.getStreamType() == null || videoInfo.getStreamType() == StreamType.UNKNOWN) {
            if (debug) {
                System.out.println("[DEBUG] Extraktion fehlgeschlagen. Erkannte Metadaten:");
                System.out.println("[DEBUG] " + detection.getMetadata());
                if (videoInfo != null) {
                    System.out.println("[DEBUG] Ermittelte streamUrl: " + videoInfo.getStreamUrl());
                    System.out.println("[DEBUG] Ermittelter streamType: " + videoInfo.getStreamType());
                }
            }
            throw new VideoNotFoundException("Kein unterstuetzter Videostream gefunden.");
        }
        if (debug) {
            System.out.println("[DEBUG] Stream type: " + videoInfo.getStreamType());
        }
        statusListener.accept(describeStreamType(videoInfo.getStreamType()) + "-Stream erkannt.");

        String htmlTitle = pageAnalyzer.extractHtmlTitle(fetchResult.html);
        videoInfo.setTitle(htmlTitle);

        if (videoInfo.getStreamType() == StreamType.HLS) {
            statusListener.accept("[4/6] Analysiere HLS-Manifest...");
            if (debug) {
                System.out.println("[DEBUG] HLS URL: " + videoInfo.getStreamUrl());
            }
            try {
                String manifest = hlsAnalyzer.loadManifest(videoInfo.getStreamUrl());
                HlsAnalyzer.ManifestAnalysis analysis = hlsAnalyzer.analyze(manifest, videoInfo.getStreamUrl());
                if (analysis.isMasterPlaylist) {
                    videoInfo.setVariants(analysis.variants);
                    HlsVariant chosen = qualityChooser != null ? qualityChooser.choose(analysis.variants) : null;
                    if (chosen == null) {
                        chosen = hlsAnalyzer.selectHighestQuality(analysis.variants);
                    }
                    if (chosen == null) {
                        return DownloadResult.failure("Keine gueltige HLS-Qualitaetsstufe gefunden.");
                    }
                    videoInfo.setStreamUrl(chosen.getUrl());
                    if (debug) {
                        System.out.println("[DEBUG] Selected resolution: " + chosen.describeResolution());
                    }
                }
            } catch (HlsException e) {
                return DownloadResult.failure(e.getMessage());
            }
        } else {
            statusListener.accept("[4/6] Analysiere Stream...");
        }

        String fileName = determineFileName(videoInfo);
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException e) {
            return DownloadResult.failure("Ausgabeordner konnte nicht erstellt werden: " + e.getMessage());
        }
        String uniqueName = FileNameSanitizer.ensureUnique(outputDirectory, fileName, "mp4");
        Path outputFile = outputDirectory.resolve(uniqueName);

        String strategyDescription = describeStreamType(videoInfo.getStreamType())
                + (videoInfo.getStreamType() == StreamType.DIRECT_FILE ? " -> Direkter Download" : " -> FFmpeg");
        statusListener.accept("[5/6] Starte Download...");
        statusListener.accept("Download Strategy: " + strategyDescription);
        if (debug) {
            System.out.println("[DEBUG] Download strategy: " + strategyDescription);
        }

        statusListener.accept("[6/6] Download laeuft...");
        return downloadManager.download(videoInfo, outputFile);
    }

    private String determineFileName(VideoInfo videoInfo) {
        if (videoInfo.getTitle() != null && !videoInfo.getTitle().isBlank()) {
            return FileNameSanitizer.sanitize(videoInfo.getTitle());
        }
        if (videoInfo.getStreamUrl() != null) {
            String path = videoInfo.getStreamUrl().getPath();
            if (path != null) {
                String lastSegment = path.substring(path.lastIndexOf('/') + 1);
                lastSegment = lastSegment.replaceAll("\\.(m3u8|mpd|mp4|webm|m4v|mov)$", "");
                if (!lastSegment.isBlank()) {
                    return FileNameSanitizer.sanitize(lastSegment);
                }
            }
        }
        return "video";
    }

    private String describeStreamType(StreamType type) {
        switch (type) {
            case HLS: return "HLS";
            case DASH: return "DASH";
            case DIRECT_FILE: return "Direkte Videodatei";
            default: return "Unbekannt";
        }
    }
}
