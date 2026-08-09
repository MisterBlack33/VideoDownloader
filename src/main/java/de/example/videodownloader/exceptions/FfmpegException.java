package de.example.videodownloader.exceptions;

/** Wird geworfen, wenn FFmpeg nicht gefunden wird oder die Verarbeitung fehlschlaegt. */
public class FfmpegException extends Exception {
    public FfmpegException(String message) { super(message); }
    public FfmpegException(String message, Throwable cause) { super(message, cause); }
}
