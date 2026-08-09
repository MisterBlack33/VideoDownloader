package de.example.videodownloader.util;

/** Erzeugt gueltige, dateisystemsichere Dateinamen aus beliebigen Titeltexten. */
public final class FileNameSanitizer {

    private static final String INVALID_CHARS_REGEX = "[<>:\"/\\\\|?*\\x00-\\x1F]";
    private static final int MAX_LENGTH = 150;
    private static final String FALLBACK_NAME = "video";

    private FileNameSanitizer() {
    }

    /**
     * Bereinigt einen beliebigen Titel zu einem gueltigen Dateinamen (ohne Endung).
     */
    public static String sanitize(String rawTitle) {
        if (rawTitle == null) {
            return FALLBACK_NAME;
        }

        String cleaned = rawTitle
                .replaceAll(INVALID_CHARS_REGEX, "")
                .trim()
                .replaceAll("\\s+", " ");

        // Windows erlaubt zudem keine trailing Punkte/Leerzeichen.
        while (cleaned.endsWith(".") || cleaned.endsWith(" ")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        if (cleaned.isEmpty()) {
            return FALLBACK_NAME;
        }

        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH).trim();
        }

        if (cleaned.isEmpty()) {
            return FALLBACK_NAME;
        }

        return cleaned;
    }

    /**
     * Baut aus einem Titel einen vollstaendigen, eindeutigen Dateinamen mit Endung.
     * Existiert die Datei bereits im Zielordner, wird ein Zaehler angehaengt.
     */
    public static String ensureUnique(java.nio.file.Path directory, String baseName, String extension) {
        String candidate = baseName + "." + extension;
        int counter = 1;
        while (java.nio.file.Files.exists(directory.resolve(candidate))) {
            candidate = baseName + " (" + counter + ")." + extension;
            counter++;
        }
        return candidate;
    }
}
