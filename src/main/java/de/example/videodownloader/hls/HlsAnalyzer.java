package de.example.videodownloader.hls;

import de.example.videodownloader.exceptions.HlsException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Laedt und analysiert HLS-Manifeste (.m3u8): unterscheidet Master- und Media-Playlists,
 * ermittelt verfuegbare Qualitaetsstufen und loest relative URLs auf.
 */
public class HlsAnalyzer {

    private static final Pattern STREAM_INF =
            Pattern.compile("#EXT-X-STREAM-INF:([^\\n\\r]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BANDWIDTH_ATTR =
            Pattern.compile("BANDWIDTH=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESOLUTION_ATTR =
            Pattern.compile("RESOLUTION=(\\d+)x(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODECS_ATTR =
            Pattern.compile("CODECS=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;

    public HlsAnalyzer() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public HlsAnalyzer(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public static class ManifestAnalysis {
        public final boolean isMasterPlaylist;
        public final List<HlsVariant> variants;
        public final URI manifestUrl;

        public ManifestAnalysis(boolean isMasterPlaylist, List<HlsVariant> variants, URI manifestUrl) {
            this.isMasterPlaylist = isMasterPlaylist;
            this.variants = variants;
            this.manifestUrl = manifestUrl;
        }
    }

    /** Laedt das Manifest von der gegebenen URL. */
    public String loadManifest(URI manifestUrl) throws HlsException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(manifestUrl)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new HlsException("HLS-Manifest konnte nicht geladen werden (HTTP " + response.statusCode() + ").");
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new HlsException("HLS-Manifest konnte nicht gelesen werden.", e);
        }
    }

    /** Analysiert den Manifest-Inhalt und unterscheidet Master- vs. Media-Playlist. */
    public ManifestAnalysis analyze(String manifestContent, URI manifestUrl) throws HlsException {
        if (manifestContent == null || !manifestContent.contains("#EXTM3U")) {
            throw new HlsException("Ungueltiges HLS-Manifest (kein #EXTM3U gefunden).");
        }

        boolean isMaster = manifestContent.contains("#EXT-X-STREAM-INF");

        if (!isMaster) {
            // Media Playlist: die uebergebene URL ist bereits die abzuspielende Variante.
            return new ManifestAnalysis(false, new ArrayList<>(), manifestUrl);
        }

        List<HlsVariant> variants = parseMasterPlaylist(manifestContent, manifestUrl);
        if (variants.isEmpty()) {
            throw new HlsException("Master Playlist enthaelt keine gueltigen Varianten.");
        }
        return new ManifestAnalysis(true, variants, manifestUrl);
    }

    private List<HlsVariant> parseMasterPlaylist(String content, URI manifestUrl) {
        List<HlsVariant> variants = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.startsWith("#EXT-X-STREAM-INF:")) {
                continue;
            }
            String attrLine = line.substring("#EXT-X-STREAM-INF:".length());

            // Naechste nicht-leere, nicht-kommentierte Zeile ist die URI der Variante.
            String uriLine = null;
            for (int j = i + 1; j < lines.length; j++) {
                String candidate = lines[j].trim();
                if (candidate.isEmpty() || candidate.startsWith("#")) {
                    continue;
                }
                uriLine = candidate;
                break;
            }
            if (uriLine == null) {
                continue;
            }

            long bandwidth = parseLong(BANDWIDTH_ATTR, attrLine, 0);
            int width = 0;
            int height = 0;
            Matcher resMatcher = RESOLUTION_ATTR.matcher(attrLine);
            if (resMatcher.find()) {
                width = Integer.parseInt(resMatcher.group(1));
                height = Integer.parseInt(resMatcher.group(2));
            }
            String codecs = firstGroupOrNull(CODECS_ATTR, attrLine);

            URI variantUri = resolve(manifestUrl, uriLine);
            if (variantUri != null) {
                variants.add(new HlsVariant(variantUri, width, height, bandwidth, codecs));
            }
        }

        return variants;
    }

    /** Waehlt aus den Varianten die mit der hoechsten Aufloesung (bzw. Bandbreite als Fallback). */
    public HlsVariant selectHighestQuality(List<HlsVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return null;
        }
        return variants.stream()
                .max((a, b) -> {
                    if (a.hasResolution() && b.hasResolution()) {
                        long areaA = (long) a.getWidth() * a.getHeight();
                        long areaB = (long) b.getWidth() * b.getHeight();
                        return Long.compare(areaA, areaB);
                    }
                    return Long.compare(a.getBandwidth(), b.getBandwidth());
                })
                .orElse(null);
    }

    private long parseLong(Pattern pattern, String input, long fallback) {
        Matcher m = pattern.matcher(input);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String firstGroupOrNull(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private URI resolve(URI base, String possiblyRelative) {
        try {
            URI candidate = new URI(possiblyRelative);
            if (candidate.isAbsolute()) {
                return candidate;
            }
            if (base != null) {
                return base.resolve(candidate);
            }
            return candidate;
        } catch (Exception e) {
            return null;
        }
    }
}
