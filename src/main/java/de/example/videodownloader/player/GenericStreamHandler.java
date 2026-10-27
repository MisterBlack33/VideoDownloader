package de.example.videodownloader.player;

import de.example.videodownloader.detection.GenericManifestDetector;

/** Handler fuer playerunabhaengig gefundene Manifest-URLs. */
public class GenericStreamHandler extends AbstractStreamUrlHandler {

    public GenericStreamHandler() {
        super(GenericManifestDetector.NAME);
    }
}