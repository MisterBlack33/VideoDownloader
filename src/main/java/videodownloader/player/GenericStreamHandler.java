package videodownloader.player;
import videodownloader.detection.GenericManifestDetector;


/** Handler fuer playerunabhaengig gefundene Manifest-URLs. */
public class GenericStreamHandler extends AbstractStreamUrlHandler {

    public GenericStreamHandler() {
        super(GenericManifestDetector.NAME);
    }
}