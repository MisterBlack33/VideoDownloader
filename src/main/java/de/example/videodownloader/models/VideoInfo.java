package de.example.videodownloader.models;

import de.example.videodownloader.hls.HlsVariant;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Einheitliche, playerunabhaengige Beschreibung eines gefundenen Videos.
 * Wird von jedem {@code PlayerHandler} erzeugt und an die {@code DownloadStrategy}
 * uebergeben.
 */
public class VideoInfo {

    private String title;
    private URI pageUrl;

    private String player;
    private String playerVersion;

    private StreamType streamType;

    private URI streamUrl;

    private List<HlsVariant> variants = new ArrayList<>();

    public VideoInfo() {
    }

    public VideoInfo(String title, URI pageUrl, String player, String playerVersion,
                      StreamType streamType, URI streamUrl) {
        this.title = title;
        this.pageUrl = pageUrl;
        this.player = player;
        this.playerVersion = playerVersion;
        this.streamType = streamType;
        this.streamUrl = streamUrl;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public URI getPageUrl() { return pageUrl; }
    public void setPageUrl(URI pageUrl) { this.pageUrl = pageUrl; }

    public String getPlayer() { return player; }
    public void setPlayer(String player) { this.player = player; }

    public String getPlayerVersion() { return playerVersion; }
    public void setPlayerVersion(String playerVersion) { this.playerVersion = playerVersion; }

    public StreamType getStreamType() { return streamType; }
    public void setStreamType(StreamType streamType) { this.streamType = streamType; }

    public URI getStreamUrl() { return streamUrl; }
    public void setStreamUrl(URI streamUrl) { this.streamUrl = streamUrl; }

    public List<HlsVariant> getVariants() { return variants; }
    public void setVariants(List<HlsVariant> variants) {
        this.variants = variants != null ? variants : new ArrayList<>();
    }

    public List<HlsVariant> getSortedVariantsByResolution() {
        List<HlsVariant> sorted = new ArrayList<>(variants);
        sorted.sort(Collections.reverseOrder(
                (a, b) -> Long.compare((long) a.getWidth() * a.getHeight(), (long) b.getWidth() * b.getHeight())));
        return sorted;
    }

    @Override
    public String toString() {
        return "VideoInfo{" +
                "title='" + title + '\'' +
                ", player='" + player + " " + playerVersion + '\'' +
                ", streamType=" + streamType +
                ", streamUrl=" + streamUrl +
                ", variants=" + variants.size() +
                '}';
    }
}
