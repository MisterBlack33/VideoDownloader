package videodownloader.web;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verantwortlich fuer das Laden einer Webseite und das Bereitstellen des HTML-Inhalts
 * fuer die Player-Detectoren. Enthaelt selbst keine player-spezifische Logik.
 */
public class PageAnalyzer {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final HttpClient httpClient;

    public PageAnalyzer() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public PageAnalyzer(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /** Ergebnis eines Seitenabrufs: HTML-Inhalt, finale URL (nach Redirects) und Statuscode. */
    public static class FetchResult {
        public final String html;
        public final URI finalUrl;
        public final int statusCode;

        public FetchResult(String html, URI finalUrl, int statusCode) {
            this.html = html;
            this.finalUrl = finalUrl;
            this.statusCode = statusCode;
        }
    }

    public FetchResult fetch(URI url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(url)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String html = new String(response.body(), StandardCharsets.UTF_8);
        URI finalUrl = response.uri();

        return new FetchResult(html, finalUrl, response.statusCode());
    }

    /** Extrahiert den Inhalt des &lt;title&gt;-Tags, falls vorhanden. */
    public String extractHtmlTitle(String html) {
        if (html == null) {
            return null;
        }
        Matcher m = TITLE_PATTERN.matcher(html);
        if (m.find()) {
            String raw = m.group(1);
            // Entities grob dekodieren und Whitespace normalisieren
            String decoded = raw
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&apos;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">");
            return decoded.replaceAll("\\s+", " ").trim();
        }
        return null;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }
}
