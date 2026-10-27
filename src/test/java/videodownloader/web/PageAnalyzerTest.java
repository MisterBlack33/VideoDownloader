package videodownloader.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testet nur die offline-faehigen Teile von {@link PageAnalyzer} (HTML-Titel-Extraktion).
 * Das eigentliche Laden einer Webseite (fetch) erfordert Netzwerkzugriff und wird
 * hier bewusst nicht getestet, um die Tests deterministisch und schnell zu halten.
 */
class PageAnalyzerTest {

    private final PageAnalyzer pageAnalyzer = new PageAnalyzer();

    @Test
    void extractsSimpleTitle() {
        String html = "<html><head><title>Meine interessante Dokumentation</title></head><body></body></html>";
        assertEquals("Meine interessante Dokumentation", pageAnalyzer.extractHtmlTitle(html));
    }

    @Test
    void decodesCommonHtmlEntitiesInTitle() {
        String html = "<title>Katzen &amp; Hunde &quot;Doku&quot;</title>";
        assertEquals("Katzen & Hunde \"Doku\"", pageAnalyzer.extractHtmlTitle(html));
    }

    @Test
    void collapsesWhitespaceInTitle() {
        String html = "<title>\n   Mehrzeiliger\n   Titel   \n</title>";
        assertEquals("Mehrzeiliger Titel", pageAnalyzer.extractHtmlTitle(html));
    }

    @Test
    void returnsNullWhenNoTitlePresent() {
        String html = "<html><body>Kein Titel</body></html>";
        assertNull(pageAnalyzer.extractHtmlTitle(html));
    }

    @Test
    void returnsNullForNullHtml() {
        assertNull(pageAnalyzer.extractHtmlTitle(null));
    }

    @Test
    void isCaseInsensitiveForTitleTag() {
        String html = "<TITLE>Grossgeschriebener Titel</TITLE>";
        assertEquals("Grossgeschriebener Titel", pageAnalyzer.extractHtmlTitle(html));
    }
}
