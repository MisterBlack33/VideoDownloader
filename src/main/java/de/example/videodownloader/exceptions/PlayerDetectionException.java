package de.example.videodownloader.exceptions;

/** Wird geworfen, wenn waehrend der Player-Erkennung ein unerwarteter Fehler auftritt. */
public class PlayerDetectionException extends Exception {
    public PlayerDetectionException(String message) { super(message); }
    public PlayerDetectionException(String message, Throwable cause) { super(message, cause); }
}
