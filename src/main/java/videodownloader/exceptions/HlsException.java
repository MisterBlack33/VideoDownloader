package videodownloader.exceptions;

/** Wird geworfen, wenn ein HLS-Manifest nicht geladen oder nicht interpretiert werden kann. */
public class HlsException extends Exception {
    public HlsException(String message) { super(message); }
    public HlsException(String message, Throwable cause) { super(message, cause); }
}
