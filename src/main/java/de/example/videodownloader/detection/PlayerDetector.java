package de.example.videodownloader.detection;

import java.net.URI;

/**
 * Gemeinsames Interface fuer alle Player-Erkenner.
 * Ein Detector stellt ausschliesslich fest, OB und WELCHER Player vorhanden ist -
 * er fuehrt keinen Download durch.
 */
public interface PlayerDetector {

    /** Anzeigename des erkannten Players, z. B. "JW Player". */
    String getName();

    /**
     * Prioritaet dieses Detectors. Ein hoeherer Wert wird bei gleichzeitigem
     * Ansprechen mehrerer Detectoren bevorzugt (spezifische Player vor generischem HTML5).
     */
    int getPriority();

    /** Schnelle Vorabpruefung, ob dieser Detector ueberhaupt zustaendig sein koennte. */
    boolean supports(String html, URI pageUrl);

    /** Fuehrt die eigentliche Erkennung durch und liefert ein strukturiertes Ergebnis. */
    PlayerDetectionResult detect(String html, URI pageUrl);
}
