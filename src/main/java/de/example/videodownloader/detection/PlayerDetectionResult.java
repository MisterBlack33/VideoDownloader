package de.example.videodownloader.detection;

import java.util.HashMap;
import java.util.Map;

/** Strukturiertes Ergebnis einer einzelnen Player-Erkennung. */
public class PlayerDetectionResult {

    private final String playerName;
    private final String playerVersion;
    private final int confidence;
    private final Map<String, String> metadata;

    public PlayerDetectionResult(String playerName, String playerVersion, int confidence,
                                  Map<String, String> metadata) {
        this.playerName = playerName;
        this.playerVersion = playerVersion;
        this.confidence = confidence;
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public static PlayerDetectionResult notDetected() {
        return new PlayerDetectionResult(null, null, 0, new HashMap<>());
    }

    public boolean isDetected() {
        return playerName != null && confidence > 0;
    }

    public String getPlayerName() { return playerName; }
    public String getPlayerVersion() { return playerVersion; }
    public int getConfidence() { return confidence; }
    public Map<String, String> getMetadata() { return metadata; }

    public String getMetadata(String key) {
        return metadata.get(key);
    }

    @Override
    public String toString() {
        return "PlayerDetectionResult{" +
                "player='" + playerName + '\'' +
                ", version='" + playerVersion + '\'' +
                ", confidence=" + confidence +
                ", metadata=" + metadata +
                '}';
    }
}
