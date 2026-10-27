# HLS Video Downloader

Ein modulares Java-Konsolenprogramm, das von einer eingegebenen Webseiten-URL
das eingebettete Video erkennt und herunterlädt. Erste konkrete Implementierung:
**JW Player 8.x** mit **HLS** (`.m3u8`) über **FFmpeg**.

> ⚠️ Das Programm umgeht **keine** DRM-, Login-, Bezahlschranken- oder sonstigen
> Zugriffsschutzmaßnahmen. Es verarbeitet ausschließlich öffentlich zugängliche
> HTTP/HTTPS/HLS-Streams, deren Abruf und Speicherung rechtmäßig zulässig ist.

## Voraussetzungen

- Java 17 oder neuer
- Maven 3.8+
- [FFmpeg](https://ffmpeg.org/) (im `PATH` oder Pfad manuell angeben)

## Build

```bash
mvn clean package
```

Erzeugt `target/VideoDownloader.jar`.

> Hinweis: Der Build benötigt Zugriff auf Maven Central, um Plugins (und für
> `mvn test` die JUnit-5-Testabhängigkeit) herunterzuladen. In stark
> eingeschränkten Offline-/Sandbox-Umgebungen kann stattdessen direkt mit
> `javac` kompiliert werden (siehe unten) – die Kernanwendung selbst hat
> **keine** externen Laufzeit-Abhängigkeiten.

## Start

```bash
java -jar target/VideoDownloader.jar
```

Mit explizitem FFmpeg-Pfad:

```bash
java -jar target/VideoDownloader.jar --ffmpeg="C:\Program Files\ffmpeg\bin\ffmpeg.exe"
```

Debug-Modus (zusätzliche `[DEBUG]`-Ausgaben):

```bash
java -jar target/VideoDownloader.jar --debug
```

Der FFmpeg-Pfad kann alternativ über die Umgebungsvariable `FFMPEG_PATH`
gesetzt werden. Ohne Angabe wird `ffmpeg` (Unix) bzw. `ffmpeg.exe` (Windows)
im `PATH` gesucht.

## Kompilieren ohne Maven (rein mit dem JDK)

Die Anwendung selbst verwendet ausschließlich die Java-Standardbibliothek
(`java.net.http.HttpClient`, Regex-basierte HTML-Analyse statt jsoup), daher
funktioniert auch ein einfacher `javac`-Build ohne jegliche Abhängigkeiten:

```bash
mkdir out
javac -encoding UTF-8 -d out $(find src/main/java -name "*.java")
java -cp out de.example.videodownloader.Main
```

## Verwendung

```text
=================================
       HLS VIDEO DOWNLOADER
=================================

FFmpeg: OK

URL der Webseite (oder 'exit' zum Beenden):
> https://example.com/video

[1/6] Lade Webseite...
OK
[2/6] Erkenne Videoplayer...
JW Player 8.26.0 erkannt.
[3/6] Analysiere Player-Konfiguration...
HLS-Stream erkannt.
[4/6] Analysiere HLS-Manifest...

Verfuegbare Qualitaeten:

[0] Automatisch
[1] 854x480
[2] 1280x720
[3] 1920x1080

Auswahl: 0

Ausgewaehlt:
1920x1080

[5/6] Starte Download...
Download Strategy: HLS -> FFmpeg
[6/6] Download laeuft...

Download abgeschlossen.

Datei:
downloads/example-video.mp4
```

Downloads landen standardmäßig im Ordner `downloads/` (wird automatisch
erstellt). Nach jedem Download kann direkt eine weitere URL eingegeben
werden; `exit` beendet das Programm.

## Architektur

Die Anwendung folgt strikt dem Prinzip **Detect → Analyze → Select → Download**
und trennt Player-Erkennung, Stream-Erkennung und Download vollständig
voneinander, sodass neue Player oder Streamingformate ergänzt werden können,
ohne bestehenden Code zu ändern:

```text
src/main/java/de/example/videodownloader/
├── Main.java                  # Konsolen-UI, kennt keine konkreten Player
├── core/
│   ├── VideoDownloader.java   # Orchestriert den gesamten Ablauf
│   ├── PlayerManager.java     # Wählt Detector/Handler (priorisiert)
│   └── DownloadManager.java   # Wählt passende DownloadStrategy
├── detection/                 # PlayerDetector-Implementierungen
│   ├── JWPlayerDetector.java  # Priorität 100
│   ├── VideoJsDetector.java   # Priorität 90
│   ├── PlyrDetector.java      # Priorität 80
│   ├── ShakaPlayerDetector.java # Priorität 70
│   └── Html5VideoDetector.java  # Priorität 10 (generischer Fallback)
├── player/                    # PlayerHandler-Implementierungen
├── download/                  # DownloadStrategy-Implementierungen
│   ├── DirectFileDownloadStrategy.java  # MP4/WebM direkt per HttpClient
│   ├── HlsDownloadStrategy.java         # .m3u8 über FFmpeg
│   └── DashDownloadStrategy.java        # .mpd über FFmpeg (Grundgerüst)
├── hls/
│   ├── HlsAnalyzer.java       # Master-/Media-Playlist-Parsing
│   └── HlsVariant.java
├── ffmpeg/
│   └── FfmpegService.java     # Start, Fortschritt, Exit-Code
├── models/                    # VideoInfo, DownloadResult, StreamType
├── util/                      # UrlValidator, FileNameSanitizer, ...
├── web/
│   └── PageAnalyzer.java      # HTTP-Abruf + Titel-Extraktion
└── exceptions/
```

### Einen neuen Player ergänzen

Ein zusätzlicher Player benötigt in der Regel nur zwei neue Klassen:

```java
public class NewPlayerDetector implements PlayerDetector { ... }
public class NewPlayerHandler implements PlayerHandler { ... }
```

und deren Registrierung in `Main.bootstrap()`. `Main`, `VideoDownloader`,
`HlsAnalyzer` und `FfmpegService` müssen dafür **nicht** verändert werden.

## Tests

Unit-Tests liegen unter `src/test/java` (JUnit 5) und decken u. a. ab:

- `UrlValidatorTest`, `FileNameSanitizerTest`
- `HlsAnalyzerTest` (Master-/Media-Playlist, relative/absolute URLs, mehrere
  Auflösungen, ungültige Manifeste)
- `JWPlayerDetectorTest`, `VideoJsDetectorTest`
- `PlayerManagerTest` (Priorisierung bei mehreren gleichzeitigen Treffern)
- `DownloadManagerTest` (Auswahl der passenden Strategie je Streamtyp)
- `PageAnalyzerTest` (Titel-Extraktion, offline-fähig)

Ausführen mit:

```bash
mvn test
```

## Grenzen der ersten Version

- DASH (`DashDownloadStrategy`) übergibt das `.mpd`-Manifest aktuell direkt an
  FFmpeg, ohne eigene Varianten-/Qualitätsauswahl innerhalb des Manifests –
  die Architektur erlaubt das spätere Nachrüsten, ohne bestehenden Code zu
  ändern.
- HTML-Analyse erfolgt gezielt über reguläre Ausdrücke statt eines vollen
  DOM-Parsers (bewusst, um ohne externe Abhängigkeiten auszukommen).
