package de.example.videodownloader.ffmpeg;

import de.example.videodownloader.exceptions.FfmpegException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kapselt den Aufruf von FFmpeg: Verfuegbarkeitspruefung, Start des Prozesses,
 * Uebergabe der Stream-URL sowie Verarbeitung von Fortschritt und Exit-Code.
 */
public class FfmpegService {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final Pattern TIME_PATTERN = Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})");
    private static final Pattern DURATION_PATTERN =
            Pattern.compile("Duration:\\s*(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})");
    private static final Pattern SPEED_PATTERN = Pattern.compile("speed=\\s*([\\d.]+)x");
    private static final Pattern SIZE_PATTERN = Pattern.compile("size=\\s*(\\d+)kB");

    private final String ffmpegPath;
    private volatile String referer;

    public FfmpegService(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    /** Prueft, ob der konfigurierte FFmpeg-Pfad ausfuehrbar ist. */
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {
                    // ignorieren
                }
            }
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /** Beschreibt einen Fortschrittspunkt waehrend der FFmpeg-Verarbeitung. */
    public static class Progress {
        public final long timeSeconds;
        public final long totalSeconds; // 0 = unbekannt
        public final double speed;
        public final long sizeKb;

        public Progress(long timeSeconds, long totalSeconds, double speed, long sizeKb) {
            this.timeSeconds = timeSeconds;
            this.totalSeconds = totalSeconds;
            this.speed = speed;
            this.sizeKb = sizeKb;
        }

        /** @return 0-100, oder -1 wenn die Gesamtdauer unbekannt ist */
        public int percent() {
            if (totalSeconds <= 0) return -1;
            return (int) Math.min(100, timeSeconds * 100 / totalSeconds);
        }
    }

    /** Remuxt/kopiert einen Stream verlustfrei nach MP4. */
    public void remuxToMp4(URI streamUrl, Path outputFile, boolean debug, Consumer<Progress> progressHandler)
            throws FfmpegException {
        runFfmpeg(streamUrl, outputFile, true, debug, progressHandler);
    }

    /** Wie {@link #remuxToMp4}, aber mit erzwungenem Re-Encoding. */
    public void transcodeToMp4(URI streamUrl, Path outputFile, boolean debug, Consumer<Progress> progressHandler)
            throws FfmpegException {
        runFfmpeg(streamUrl, outputFile, false, debug, progressHandler);
    }

    private void runFfmpeg(URI streamUrl, Path outputFile, boolean useCopy, boolean debug,
                           Consumer<Progress> progressHandler) throws FfmpegException {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-user_agent");
        command.add(USER_AGENT);
        if (referer != null) {
            command.add("-headers");
            command.add("Referer: " + referer + "\r\n");
        }
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
        command.add(outputFile.toString());

        if (debug) {
            System.out.println("[DEBUG] FFmpeg command:");
            System.out.println("[DEBUG] " + String.join(" ", command));
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            Deque<String> tail = new ArrayDeque<>();
            long totalSeconds = 0;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (debug) {
                        System.out.println("[FFmpeg] " + line);
                    }
                    if (!line.isBlank() && !line.contains("time=")) {
                        tail.addLast(line);
                        if (tail.size() > 4) tail.removeFirst();
                    }
                    if (totalSeconds == 0) {
                        Matcher d = DURATION_PATTERN.matcher(line);
                        if (d.find()) {
                            totalSeconds = Long.parseLong(d.group(1)) * 3600
                                    + Long.parseLong(d.group(2)) * 60
                                    + Long.parseLong(d.group(3));
                        }
                    }
                    if (progressHandler != null) {
                        Progress p = parseProgressLine(line, totalSeconds);
                        if (p != null) {
                            progressHandler.accept(p);
                        }
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new FfmpegException("FFmpeg konnte den Stream nicht verarbeiten (Exit-Code "
                        + exitCode + ").\n" + String.join("\n", tail));
            }
        } catch (IOException e) {
            throw new FfmpegException("FFmpeg konnte nicht gestartet werden.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FfmpegException("FFmpeg-Verarbeitung wurde unterbrochen.", e);
        }
    }

    private Progress parseProgressLine(String line, long totalSeconds) {
        Matcher timeMatcher = TIME_PATTERN.matcher(line);
        if (!timeMatcher.find()) {
            return null;
        }
        long currentSeconds = Long.parseLong(timeMatcher.group(1)) * 3600
                + Long.parseLong(timeMatcher.group(2)) * 60
                + Long.parseLong(timeMatcher.group(3));

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

        return new Progress(currentSeconds, totalSeconds, speed, sizeKb);
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }
}