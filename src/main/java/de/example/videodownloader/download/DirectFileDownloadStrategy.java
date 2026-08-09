package de.example.videodownloader.download;

import de.example.videodownloader.models.DownloadResult;
import de.example.videodownloader.models.StreamType;
import de.example.videodownloader.models.VideoInfo;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Laedt direkt referenzierte Videodateien (z. B. .mp4) ueber den Java-HTTP-Client herunter. */
public class DirectFileDownloadStrategy implements DownloadStrategy {

    private final HttpClient httpClient;

    public DirectFileDownloadStrategy() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public boolean supports(VideoInfo video) {
        return video != null && video.getStreamType() == StreamType.DIRECT_FILE && video.getStreamUrl() != null;
    }

    @Override
    public DownloadResult download(VideoInfo video, Path output) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(video.getStreamUrl())
                    .timeout(Duration.ofMinutes(30))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(output));

            if (response.statusCode() >= 400) {
                Files.deleteIfExists(output);
                return DownloadResult.failure("Datei konnte nicht heruntergeladen werden (HTTP "
                        + response.statusCode() + ").");
            }

            long size = Files.size(output);
            return DownloadResult.success(output, size);
        } catch (IOException | InterruptedException e) {
            return DownloadResult.failure("Fehler beim direkten Dateidownload: " + e.getMessage());
        }
    }
}
