package de.example.videodownloader.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileNameSanitizerTest {

    @Test
    void keepsValidTitleUnchanged() {
        assertEquals("Meine interessante Dokumentation", FileNameSanitizer.sanitize("Meine interessante Dokumentation"));
    }

    @Test
    void removesInvalidWindowsCharacters() {
        String result = FileNameSanitizer.sanitize("Titel: <Test> \"Video\" | /Pfad\\ ?*");
        assertFalse(result.contains("<"));
        assertFalse(result.contains(">"));
        assertFalse(result.contains(":"));
        assertFalse(result.contains("\""));
        assertFalse(result.contains("/"));
        assertFalse(result.contains("\\"));
        assertFalse(result.contains("|"));
        assertFalse(result.contains("?"));
        assertFalse(result.contains("*"));
    }

    @Test
    void fallsBackOnEmptyTitle() {
        assertEquals("video", FileNameSanitizer.sanitize(""));
        assertEquals("video", FileNameSanitizer.sanitize("   "));
    }

    @Test
    void fallsBackOnNullTitle() {
        assertEquals("video", FileNameSanitizer.sanitize(null));
    }

    @Test
    void truncatesVeryLongTitle() {
        String longTitle = "a".repeat(500);
        String result = FileNameSanitizer.sanitize(longTitle);
        assertTrue(result.length() <= 150);
        assertFalse(result.isEmpty());
    }

    @Test
    void collapsesWhitespace() {
        assertEquals("Ein Titel", FileNameSanitizer.sanitize("Ein     Titel"));
    }

    @Test
    void ensureUniqueAppendsCounterOnCollision(@TempDir Path tempDir) throws Exception {
        Files.createFile(tempDir.resolve("video.mp4"));
        String unique = FileNameSanitizer.ensureUnique(tempDir, "video", "mp4");
        assertEquals("video (1).mp4", unique);
    }

    @Test
    void ensureUniqueReturnsPlainNameWhenNoCollision(@TempDir Path tempDir) {
        String unique = FileNameSanitizer.ensureUnique(tempDir, "video", "mp4");
        assertEquals("video.mp4", unique);
    }
}
