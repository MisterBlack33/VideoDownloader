package videodownloader.models;

/** Erkannter Streamtyp, unabhaengig vom verwendeten Player. */
public enum StreamType {
    DIRECT_FILE,
    HLS,
    DASH,
    UNKNOWN
}
