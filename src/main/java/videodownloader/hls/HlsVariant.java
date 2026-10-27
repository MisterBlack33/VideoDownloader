package videodownloader.hls;

import java.net.URI;

/** Eine einzelne Qualitaetsvariante innerhalb einer HLS Master Playlist. */
public class HlsVariant {

    private final URI url;
    private final int width;
    private final int height;
    private final long bandwidth;
    private final String codecs;

    public HlsVariant(URI url, int width, int height, long bandwidth, String codecs) {
        this.url = url;
        this.width = width;
        this.height = height;
        this.bandwidth = bandwidth;
        this.codecs = codecs;
    }

    public URI getUrl() { return url; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getBandwidth() { return bandwidth; }
    public String getCodecs() { return codecs; }

    public boolean hasResolution() { return width > 0 && height > 0; }

    public String describeResolution() {
        return hasResolution() ? width + "x" + height : "unbekannt";
    }

    @Override
    public String toString() {
        return "HlsVariant{" + describeResolution() + ", bandwidth=" + bandwidth + "}";
    }
}
