package de.example.videodownloader.exceptions;

/** Wird geworfen, wenn auf der Zielseite kein unterstuetzter Videostream gefunden werden konnte. */
public class VideoNotFoundException extends Exception {
    public VideoNotFoundException(String message) { super(message); }
    public VideoNotFoundException(String message, Throwable cause) { super(message, cause); }
}
