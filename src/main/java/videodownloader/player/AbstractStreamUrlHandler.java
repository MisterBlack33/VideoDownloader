package videodownloader.player;
import videodownloader.detection.PlayerDetectionResult;
import videodownloader.models.StreamType;
import videodownloader.util.StreamTypeResolver;
import videodownloader.models.VideoInfo;


import java.net.URI;

/**
 * Basisimplementierung fuer Handler, deren Detector die Stream-URL bereits unter
 * dem Metadaten-Schluessel "streamUrl" bereitstellt. Vermeidet doppelten Code
 * zwischen den einzelnen playerspezifischen Handlern.
 */
public abstract class AbstractStreamUrlHandler implements PlayerHandler {

    private final String playerName;

    protected AbstractStreamUrlHandler(String playerName) {
        this.playerName = playerName;
    }

    @Override
    public String getName() {
        return playerName;
    }

    @Override
    public boolean supports(PlayerDetectionResult detection) {
        return detection != null && detection.isDetected() && playerName.equals(detection.getPlayerName());
    }

    @Override
    public VideoInfo extractVideoInfo(PlayerDetectionResult detection, URI pageUrl) {
        if (!supports(detection)) {
            return null;
        }

        String rawStreamUrl = detection.getMetadata("streamUrl");
        if (rawStreamUrl == null) {
            return null;
        }

        URI resolvedStreamUrl = resolve(pageUrl, rawStreamUrl);
        String mimeTypeHint = detection.getMetadata("streamUrlType");
        StreamType streamType = StreamTypeResolver.resolve(rawStreamUrl, mimeTypeHint);

        VideoInfo info = new VideoInfo();
        info.setPageUrl(pageUrl);
        info.setPlayer(detection.getPlayerName());
        info.setPlayerVersion(detection.getPlayerVersion());
        info.setStreamType(streamType);
        info.setStreamUrl(resolvedStreamUrl);

        return info;
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
