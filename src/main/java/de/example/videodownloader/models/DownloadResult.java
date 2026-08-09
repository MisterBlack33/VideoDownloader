package de.example.videodownloader.models;

import java.nio.file.Path;

/** Ergebnis eines Downloadvorgangs. */
public class DownloadResult {

    private final boolean success;
    private final Path outputFile;
    private final long fileSizeBytes;
    private final String errorMessage;

    private DownloadResult(boolean success, Path outputFile, long fileSizeBytes, String errorMessage) {
        this.success = success;
        this.outputFile = outputFile;
        this.fileSizeBytes = fileSizeBytes;
        this.errorMessage = errorMessage;
    }

    public static DownloadResult success(Path outputFile, long fileSizeBytes) {
        return new DownloadResult(true, outputFile, fileSizeBytes, null);
    }

    public static DownloadResult failure(String errorMessage) {
        return new DownloadResult(false, null, 0, errorMessage);
    }

    public boolean isSuccess() { return success; }
    public Path getOutputFile() { return outputFile; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public String getErrorMessage() { return errorMessage; }
}
