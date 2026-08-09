package de.example.videodownloader.ffmpeg;

import de.example.videodownloader.exceptions.FfmpegException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kapselt den Aufruf von FFmpeg: Verfuegbarkeitspruefung, Start des Prozesses,
 * Uebergabe der Stream-URL sowie Verarbeitung von Fortschritt und Exit-Code.
 */
public class FfmpegService {

    private final String ffmpegPath;

    private static final Pattern TIME_PATTERN = Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})");
    private static final Pattern SPEED_PATTERN = Pattern.compile("speed=\\s*([\\d.]+)x");
    private static final Pattern SIZE_PATTERN = Pattern.compile("size=\\s*(\\d+)kB");

    public FfmpegService(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    /** Prueft, ob der konfigurierte FFmpeg-Pfad ausfuehrbar ist. */
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            // Ausgabe verwerfen, aber lesen, damit der Prozess nicht blockiert
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {
                    // ignorieren
                }
            }
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /** Beschreibt einen Fortschrittspunkt waehrend der FFmpeg-Verarbeitung. */
    public static class Progress {
        public final long timeSeconds;
        public final double speed;
        public final long sizeKb;

        public Progress(long timeSeconds, double speed, long sizeKb) {
            this.timeSeconds = timeSeconds;
            this.speed = speed;
            this.sizeKb = sizeKb;
        }
    }

    /**
     * Remuxt/kopiert einen Stream (z. B. eine HLS- oder DASH-URL) verlustfrei nach MP4.
     *
     * @param streamUrl       finale Manifest-/Stream-URL
     * @param outputFile      Zielpfad der MP4-Datei
     * @param debug           ob der tatsaechlich verwendete FFmpeg-Befehl geloggt werden soll
     * @param progressHandler wird bei jedem erkannten Fortschrittspunkt aufgerufen (darf null sein)
     */
    public void remuxToMp4(URI streamUrl, Path outputFile, boolean debug, Consumer<Progress> progressHandler)
            throws FfmpegException {
        runFfmpeg(streamUrl, outputFile, true, debug, progressHandler);
    }

    /**
     * Wie {@link #remuxToMp4}, aber mit erzwungenem Re-Encoding, falls ein reines
     * Copy-Remuxing durch den Server/Container nicht moeglich ist.
     */
    public void transcodeToMp4(URI streamUrl, Path outputFile, boolean debug, Consumer<Progress> progressHandler)
            throws FfmpegException {
        runFfmpeg(streamUrl, outputFile, false, debug, progressHandler);
    }

    private void runFfmpeg(URI streamUrl, Path outputFile, boolean useCopy, boolean debug,
                            Consumer<Progress> progressHandler) throws FfmpegException {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-i");
        command.add(streamUrl.toString());
        if (useCopy) {
            command.add("-c");
            command.add("copy");
        } else {
            command.add("-c:v");
            command.add("libx264");
            command.add("-c:a");
            command.add("aac");
        }
        command.add("-bsf:a");
        command.add("aac_adtstoasc");
        command.add(outputFile.toString());

        if (debug) {
            System.out.println("[DEBUG] FFmpeg command:");
            System.out.println("[DEBUG] " + String.join(" ", command));
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (debug) {
                        System.out.println("[FFmpeg] " + line);
                    }
                    if (progressHandler != null) {
                        Progress p = parseProgressLine(line);
                        if (p != null) {
                            progressHandler.accept(p);
                        }
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new FfmpegException("FFmpeg konnte den Stream nicht verarbeiten (Exit-Code " + exitCode + ").");
            }
        } catch (IOException e) {
            throw new FfmpegException("FFmpeg konnte nicht gestartet werden.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FfmpegException("FFmpeg-Verarbeitung wurde unterbrochen.", e);
        }
    }

    private Progress parseProgressLine(String line) {
        Matcher timeMatcher = TIME_PATTERN.matcher(line);
        if (!timeMatcher.find()) {
            return null;
        }
        long hours = Long.parseLong(timeMatcher.group(1));
        long minutes = Long.parseLong(timeMatcher.group(2));
        long seconds = Long.parseLong(timeMatcher.group(3));
        long totalSeconds = hours * 3600 + minutes * 60 + seconds;

        double speed = 0.0;
        Matcher speedMatcher = SPEED_PATTERN.matcher(line);
        if (speedMatcher.find()) {
            speed = Double.parseDouble(speedMatcher.group(1));
        }

        long sizeKb = 0;
        Matcher sizeMatcher = SIZE_PATTERN.matcher(line);
        if (sizeMatcher.find()) {
            sizeKb = Long.parseLong(sizeMatcher.group(1));
        }

        return new Progress(totalSeconds, speed, sizeKb);
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }
}
