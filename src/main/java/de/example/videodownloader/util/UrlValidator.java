package de.example.videodownloader.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/** Prueft und normalisiert vom Benutzer eingegebene URLs. */
public final class UrlValidator {

    private UrlValidator() {
    }

    /**
     * Validiert eine vom Benutzer eingegebene URL.
     *
     * @param input die rohe Benutzereingabe
     * @return die geparste {@link URI}, sofern sie gueltig ist und mit http/https beginnt
     */
    public static Optional<URI> validate(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return Optional.empty();
            }
            scheme = scheme.toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return Optional.empty();
            }
            if (uri.getHost() == null || uri.getHost().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(uri);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    public static boolean isValid(String input) {
        return validate(input).isPresent();
    }
}
