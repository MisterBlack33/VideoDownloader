package de.example.videodownloader.util;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {

    @Test
    void acceptsValidHttpsUrl() {
        assertTrue(UrlValidator.isValid("https://example.com/video"));
    }

    @Test
    void acceptsValidHttpUrl() {
        assertTrue(UrlValidator.isValid("http://example.com/video"));
    }

    @Test
    void rejectsUrlWithoutScheme() {
        assertFalse(UrlValidator.isValid("example.com/video"));
    }

    @Test
    void rejectsNonHttpScheme() {
        assertFalse(UrlValidator.isValid("ftp://example.com/video"));
        assertFalse(UrlValidator.isValid("javascript:alert(1)"));
    }

    @Test
    void rejectsEmptyOrNullInput() {
        assertFalse(UrlValidator.isValid(""));
        assertFalse(UrlValidator.isValid("   "));
        assertFalse(UrlValidator.isValid(null));
    }

    @Test
    void rejectsMalformedUrl() {
        assertFalse(UrlValidator.isValid("https://"));
    }

    @Test
    void acceptsUrlWithQueryParameters() {
        Optional<URI> result = UrlValidator.validate("https://example.com/video?id=123&token=abc");
        assertTrue(result.isPresent());
        assertEquals("example.com", result.get().getHost());
    }

    @Test
    void trimsWhitespaceAroundInput() {
        assertTrue(UrlValidator.isValid("   https://example.com/video   "));
    }
}
