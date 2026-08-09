package de.example.videodownloader;

import de.example.videodownloader.core.DownloadManager;
import de.example.videodownloader.core.PlayerManager;
import de.example.videodownloader.core.VideoDownloader;
import de.example.videodownloader.detection.Html5VideoDetector;
import de.example.videodownloader.detection.JWPlayerDetector;
import de.example.videodownloader.detection.PlyrDetector;
import de.example.videodownloader.detection.ShakaPlayerDetector;
import de.example.videodownloader.detection.VideoJsDetector;
import de.example.videodownloader.download.DashDownloadStrategy;
import de.example.videodownloader.download.DirectFileDownloadStrategy;
import de.example.videodownloader.download.HlsDownloadStrategy;
import de.example.videodownloader.exceptions.VideoNotFoundException;
import de.example.videodownloader.ffmpeg.FfmpegService;
import de.example.videodownloader.hls.HlsAnalyzer;
import de.example.videodownloader.hls.HlsVariant;
import de.example.videodownloader.models.DownloadResult;
import de.example.videodownloader.player.Html5VideoHandler;
import de.example.videodownloader.player.JWPlayerHandler;
import de.example.videodownloader.player.PlyrHandler;
import de.example.videodownloader.player.ShakaPlayerHandler;
import de.example.videodownloader.player.VideoJsHandler;
import de.example.videodownloader.util.UrlValidator;
import de.example.videodownloader.web.PageAnalyzer;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Konsoleneingabe, Programmablauf und Ausgabe von Statusinformationen.
 * Main besitzt bewusst KEINE Kenntnisse ueber konkrete Player (siehe Abschnitt 35
 * der Spezifikation) - die gesamte Player-/Stream-spezifische Logik lebt in den
 * jeweiligen Detector-/Handler-/Strategy-Implementierungen.
 */
public final class Main {

    private static final String DEFAULT_FFMPEG_PATH_WINDOWS = "ffmpeg.exe";
    private static final String DEFAULT_FFMPEG_PATH_UNIX = "ffmpeg";
    private static final Path DOWNLOADS_DIR = Path.of("downloads");

    private Main() {
    }

    public static void main(String[] args) {
        boolean debug = containsFlag(args, "--debug");
        String ffmpegPathOverride = extractOption(args, "--ffmpeg");

        Scanner scanner = new Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8);

        printBanner();

        String ffmpegPath = resolveFfmpegPath(ffmpegPathOverride);
        FfmpegService ffmpegService = new FfmpegService(ffmpegPath);

        boolean ffmpegAvailable = ffmpegService.isAvailable();
        System.out.println("FFmpeg: " + (ffmpegAvailable ? "OK" : "NICHT GEFUNDEN"));
        if (!ffmpegAvailable) {
            System.out.println();
            System.out.println("Fehler: FFmpeg wurde nicht gefunden.");
            System.out.println("Pfad geprueft: " + ffmpegPath);
            System.out.println("Bitte FFmpeg installieren oder den Pfad ueber --ffmpeg=<pfad> angeben.");
            return;
        }
        System.out.println();

        VideoDownloader videoDownloader = bootstrap(ffmpegService, debug);

        while (true) {
            System.out.println("URL der Webseite (oder 'exit' zum Beenden):");
            System.out.print("> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                break;
            }
            if (input.isEmpty()) {
                continue;
            }

            Optional<URI> validatedUrl = UrlValidator.validate(input);
            if (validatedUrl.isEmpty()) {
                System.out.println();
                System.out.println("Fehler: Ungueltige URL.");
                System.out.println();
                continue;
            }

            System.out.println();
            runSingleDownload(videoDownloader, validatedUrl.get(), scanner);
            System.out.println();
            System.out.println("Neue URL eingeben oder 'exit' zum Beenden:");
        }

        System.out.println("Programm beendet.");
    }

    private static void runSingleDownload(VideoDownloader videoDownloader, URI url, Scanner scanner) {
        try {
            DownloadResult result = videoDownloader.process(
                    url,
                    Main::printStatus,
                    variants -> askUserForQuality(variants, scanner)
            );

            System.out.println();
            if (result.isSuccess()) {
                System.out.println("Download abgeschlossen.");
                System.out.println();
                System.out.println("Datei:");
                System.out.println(result.getOutputFile());
                System.out.println();
                System.out.println("Groesse:");
                System.out.println(formatBytes(result.getFileSizeBytes()));
            } else {
                System.out.println("Fehler: " + result.getErrorMessage());
            }
        } catch (VideoNotFoundException e) {
            System.out.println();
            System.out.println("Fehler: " + e.getMessage());
        } catch (Exception e) {
            System.out.println();
            System.out.println("Fehler: Unerwarteter Fehler.");
            if (System.getProperty("videodownloader.debug") != null) {
                e.printStackTrace();
            }
        }
    }

    private static HlsVariant askUserForQuality(List<HlsVariant> variants, Scanner scanner) {
        System.out.println();
        System.out.println("Verfuegbare Qualitaeten:");
        System.out.println();
        System.out.println("[0] Automatisch");
        for (int i = 0; i < variants.size(); i++) {
            System.out.println("[" + (i + 1) + "] " + variants.get(i).describeResolution());
        }
        System.out.println();
        System.out.print("Auswahl: ");

        if (!scanner.hasNextLine()) {
            return null;
        }
        String choice = scanner.nextLine().trim();
        int index;
        try {
            index = Integer.parseInt(choice);
        } catch (NumberFormatException e) {
            return null;
        }
        if (index <= 0 || index > variants.size()) {
            return null; // automatisch
        }
        HlsVariant chosen = variants.get(index - 1);
        System.out.println();
        System.out.println("Ausgewaehlt:");
        System.out.println(chosen.describeResolution());
        return chosen;
    }

    private static void printStatus(String message) {
        System.out.println(message);
    }

    private static VideoDownloader bootstrap(FfmpegService ffmpegService, boolean debug) {
        PlayerManager playerManager = new PlayerManager();

        // Neue Player werden ausschliesslich hier registriert - keine sonstige Klasse
        // muss dafuer angepasst werden (siehe Abschnitt 34 der Spezifikation).
        playerManager.registerDetector(new JWPlayerDetector());
        playerManager.registerDetector(new VideoJsDetector());
        playerManager.registerDetector(new PlyrDetector());
        playerManager.registerDetector(new ShakaPlayerDetector());
        playerManager.registerDetector(new Html5VideoDetector());

        playerManager.registerHandler(new JWPlayerHandler());
        playerManager.registerHandler(new VideoJsHandler());
        playerManager.registerHandler(new PlyrHandler());
        playerManager.registerHandler(new ShakaPlayerHandler());
        playerManager.registerHandler(new Html5VideoHandler());

        DownloadManager downloadManager = new DownloadManager();
        HlsAnalyzer hlsAnalyzer = new HlsAnalyzer();

        downloadManager.registerStrategy(new DirectFileDownloadStrategy());
        downloadManager.registerStrategy(
                new HlsDownloadStrategy(hlsAnalyzer, ffmpegService, debug, Main::printProgress));
        downloadManager.registerStrategy(
                new DashDownloadStrategy(ffmpegService, debug, Main::printProgress));

        PageAnalyzer pageAnalyzer = new PageAnalyzer();

        return new VideoDownloader(pageAnalyzer, playerManager, downloadManager, hlsAnalyzer, DOWNLOADS_DIR, debug);
    }

    private static void printProgress(FfmpegService.Progress progress) {
        String time = formatDuration(progress.timeSeconds);
        System.out.print("\rDownload laeuft...  Zeit: " + time
                + "   Geschwindigkeit: " + progress.speed + "x"
                + "   Groesse: " + (progress.sizeKb / 1024) + " MB   ");
    }

    private static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    private static void printBanner() {
        System.out.println("=================================");
        System.out.println("       HLS VIDEO DOWNLOADER");
        System.out.println("=================================");
        System.out.println();
    }

    private static String resolveFfmpegPath(String override) {
        if (override != null && !override.isBlank()) {
            return override;
        }
        String envPath = System.getenv("FFMPEG_PATH");
        if (envPath != null && !envPath.isBlank()) {
            return envPath;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? DEFAULT_FFMPEG_PATH_WINDOWS : DEFAULT_FFMPEG_PATH_UNIX;
    }

    private static boolean containsFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equalsIgnoreCase(flag)) {
                return true;
            }
        }
        return false;
    }

    private static String extractOption(String[] args, String key) {
        String prefix = key + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return null;
    }
}
