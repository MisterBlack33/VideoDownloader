package de.example.videodownloader.player;

import de.example.videodownloader.detection.PlayerDetectionResult;
import de.example.videodownloader.models.VideoInfo;

import java.net.URI;

/**
 * Interpretiert das Ergebnis eines {@code PlayerDetector} und extrahiert daraus
 * einheitliche, playerunabhaengige Video-Informationen ({@link VideoInfo}).
 */
public interface PlayerHandler {

    String getName();

    boolean supports(PlayerDetectionResult detection);

    /**
     * @param detection Ergebnis der Player-Erkennung
     * @param pageUrl   URL der urspruenglichen Webseite (fuer relative URL-Aufloesung)
     * @return extrahierte Videoinformationen, niemals {@code null} bei erfolgreicher Erkennung
     */
    VideoInfo extractVideoInfo(PlayerDetectionResult detection, URI pageUrl);
}
