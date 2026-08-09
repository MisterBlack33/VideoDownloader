package de.example.videodownloader.core;

import de.example.videodownloader.detection.PlayerDetectionResult;
import de.example.videodownloader.detection.PlayerDetector;
import de.example.videodownloader.player.PlayerHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Zentrale, generische Komponente zur Auswahl des passenden {@link PlayerDetector}
 * und {@link PlayerHandler}. {@code Main} und {@code VideoDownloader} benoetigen
 * dadurch keine Kenntnis ueber konkrete Player.
 */
public class PlayerManager {

    private final List<PlayerDetector> detectors = new ArrayList<>();
    private final List<PlayerHandler> handlers = new ArrayList<>();

    public void registerDetector(PlayerDetector detector) {
        detectors.add(detector);
    }

    public void registerHandler(PlayerHandler handler) {
        handlers.add(handler);
    }

    /**
     * Fuehrt alle registrierten Detectoren aus und liefert das Ergebnis mit dem
     * hoechsten (Prioritaet, Confidence)-Wert. Detectoren, die nichts erkennen,
     * werden ignoriert.
     */
    public PlayerDetectionResult detect(String html, URI pageUrl) {
        List<PlayerDetector> sorted = new ArrayList<>(detectors);
        sorted.sort(Comparator.comparingInt(PlayerDetector::getPriority).reversed());

        PlayerDetectionResult best = null;
        int bestScore = -1;

        for (PlayerDetector detector : sorted) {
            if (!detector.supports(html, pageUrl)) {
                continue;
            }
            PlayerDetectionResult result = detector.detect(html, pageUrl);
            if (result == null || !result.isDetected()) {
                continue;
            }
            // Score kombiniert Detector-Prioritaet (dominant) und Confidence (Tiebreaker).
            int score = detector.getPriority() * 1000 + result.getConfidence();
            if (score > bestScore) {
                bestScore = score;
                best = result;
            }
        }

        return best != null ? best : PlayerDetectionResult.notDetected();
    }

    public PlayerHandler findHandler(PlayerDetectionResult detection) {
        if (detection == null || !detection.isDetected()) {
            return null;
        }
        for (PlayerHandler handler : handlers) {
            if (handler.supports(detection)) {
                return handler;
            }
        }
        return null;
    }

    public List<PlayerDetector> getDetectors() {
        return detectors;
    }

    public List<PlayerHandler> getHandlers() {
        return handlers;
    }
}
