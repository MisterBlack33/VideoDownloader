package de.example.videodownloader.player;

/** Extrahiert Videoinformationen aus einem generischen &lt;video&gt;-Tag ohne spezifischen Player. */
public class Html5VideoHandler extends AbstractStreamUrlHandler {

    public Html5VideoHandler() {
        super("HTML5 Video");
    }
}
