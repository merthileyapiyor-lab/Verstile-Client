package com.verstile.client.update;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;

/** Detects an unhandled Minecraft crash report from the previous run. */
public final class CrashReportManager {
    private static final Path STATE = FabricLoader.getInstance().getConfigDir().resolve("verstile")
            .resolve("last-crash-prompt.txt");
    private static final Path SESSION = FabricLoader.getInstance().getConfigDir().resolve("verstile")
            .resolve("last-session-start.txt");
    private static long previousSessionStart = Long.MAX_VALUE;

    private CrashReportManager() {}

    public static void beginSession() {
        long now = System.currentTimeMillis();
        try {
            Files.createDirectories(SESSION.getParent());
            previousSessionStart = Files.isRegularFile(SESSION)
                    ? Long.parseLong(Files.readString(SESSION, StandardCharsets.US_ASCII).trim()) : now;
            Files.writeString(SESSION, Long.toString(now), StandardCharsets.US_ASCII, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception error) {
            previousSessionStart = now;
            System.err.println("[Verstile] Could not initialize crash session tracking: " + error.getMessage());
        }
    }

    public static Path pendingReport() {
        Path directory = FabricLoader.getInstance().getGameDir().resolve("crash-reports");
        if (!Files.isDirectory(directory)) return null;
        try (var files = Files.list(directory)) {
            Path latest = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("-client.txt"))
                    .filter(path -> modified(path) >= previousSessionStart)
                    .max(Comparator.comparingLong(CrashReportManager::modified)).orElse(null);
            if (latest == null) return null;
            String identity = identity(latest);
            String handled = Files.isRegularFile(STATE) ? Files.readString(STATE, StandardCharsets.UTF_8).trim() : "";
            return identity.equals(handled) ? null : latest;
        } catch (Exception error) {
            System.err.println("[Verstile] Could not inspect crash reports: " + error.getMessage());
            return null;
        }
    }

    public static void markHandled(Path report) {
        if (report == null) return;
        try {
            Files.createDirectories(STATE.getParent());
            Files.writeString(STATE, identity(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception error) {
            System.err.println("[Verstile] Could not save crash prompt state: " + error.getMessage());
        }
    }

    private static String identity(Path path) { return path.getFileName() + ":" + modified(path); }
    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (Exception ignored) { return 0L; }
    }
}
