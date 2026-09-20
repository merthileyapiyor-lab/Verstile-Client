package com.verstile.client.update;

import com.verstile.client.util.ClientVersion;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Stores which release notes have already been acknowledged on this profile. */
public final class ChangelogManager {
    private static final Path LAST_SEEN_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("verstile").resolve("last-seen-version.txt");
    private static final String CHANGELOG_RESOURCE = "/assets/autopvp/changelog.txt";

    private ChangelogManager() {}

    public static boolean shouldShow() {
        String current = ClientVersion.current();
        if ("unknown".equalsIgnoreCase(current)) return false;

        try {
            return !Files.isRegularFile(LAST_SEEN_FILE)
                    || !current.equals(Files.readString(LAST_SEEN_FILE, StandardCharsets.UTF_8).trim());
        } catch (Exception error) {
            System.err.println("[Verstile] Could not read changelog state: " + error.getMessage());
            return true;
        }
    }

    public static List<String> lines() {
        try (InputStream stream = ChangelogManager.class.getResourceAsStream(CHANGELOG_RESOURCE)) {
            if (stream == null) return List.of("Performance, compatibility and module improvements.");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                List<String> result = reader.lines().toList();
                return result.isEmpty()
                        ? List.of("Performance, compatibility and module improvements.")
                        : result;
            }
        } catch (Exception error) {
            System.err.println("[Verstile] Could not load changelog: " + error.getMessage());
            return List.of("Performance, compatibility and module improvements.");
        }
    }

    public static void markSeen() {
        try {
            Files.createDirectories(LAST_SEEN_FILE.getParent());
            Files.writeString(LAST_SEEN_FILE, ClientVersion.current(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception error) {
            System.err.println("[Verstile] Could not save changelog state: " + error.getMessage());
        }
    }
}
