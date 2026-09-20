package com.verstile.client.friend;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Case-insensitive, profile-independent friend list. */
public final class FriendManager {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("verstile").resolve("friends.txt");
    private static final Set<String> FRIENDS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private static final Set<String> AUTO_FRIENDS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private static boolean loaded;

    private FriendManager() {}

    public static synchronized List<String> list() {
        load();
        Set<String> combined = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        combined.addAll(FRIENDS); combined.addAll(AUTO_FRIENDS);
        return List.copyOf(combined);
    }
    public static synchronized boolean isManualFriend(String name) { load(); return name != null && FRIENDS.contains(name.trim()); }
    public static synchronized boolean isAutoFriend(String name) { return name != null && AUTO_FRIENDS.contains(name.trim()); }
    public static boolean isFriend(Player player) { return player != null && isFriend(player.getScoreboardName()); }
    public static synchronized boolean isFriend(String name) {
        load();
        return name != null && (FRIENDS.contains(name.trim()) || AUTO_FRIENDS.contains(name.trim()));
    }

    public static synchronized boolean add(String name) {
        load();
        String clean = sanitize(name);
        if (clean == null || !FRIENDS.add(clean)) return false;
        save(); return true;
    }

    public static synchronized boolean remove(String name) {
        load();
        if (name == null || !FRIENDS.remove(name.trim())) return false;
        save(); return true;
    }

    public static synchronized boolean toggle(String name) {
        return isManualFriend(name) ? (remove(name) && false) : add(name);
    }

    public static synchronized void replaceAutoFriends(Iterable<String> names) {
        AUTO_FRIENDS.clear();
        if (names == null) return;
        for (String name : names) {
            String clean = sanitize(name);
            if (clean != null && !FRIENDS.contains(clean)) AUTO_FRIENDS.add(clean);
        }
    }

    public static synchronized void clearAutoFriends() { AUTO_FRIENDS.clear(); }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.isRegularFile(FILE)) {
                for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                    String clean = sanitize(line);
                    if (clean != null) FRIENDS.add(clean);
                }
            }
        } catch (Exception error) {
            System.err.println("[Verstile] Could not load friends: " + error.getMessage());
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.write(FILE, FRIENDS, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception error) {
            System.err.println("[Verstile] Could not save friends: " + error.getMessage());
        }
    }

    private static String sanitize(String name) {
        if (name == null) return null;
        String clean = name.trim();
        return clean.matches("[A-Za-z0-9_]{1,16}") ? clean : null;
    }
}
