package com.verstile.client.friend;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** BedWars spawn-radius teammate capture. Automatic entries only live for the current match. */
public final class AutoTeammateManager {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("verstile")
            .resolve("auto-teammate.txt");
    private static final double TEAM_RADIUS = 10.0;
    private static final double MATCH_TELEPORT_DISTANCE = 32.0;
    private static boolean loaded;
    private static boolean enabled;
    private static Level knownLevel;
    private static UUID knownPlayer;
    private static Vec3 lastPosition;
    private static int captureDelay;

    private AutoTeammateManager() {}

    public static synchronized boolean isEnabled() { load(); return enabled; }

    public static synchronized void setEnabled(boolean value) {
        load(); enabled = value;
        if (!enabled) FriendManager.clearAutoFriends();
        resetCapture(); save();
    }

    public static synchronized void toggle() { setEnabled(!isEnabled()); }

    public static synchronized void tick(Minecraft client) {
        load();
        if (!enabled || client.player == null || client.level == null) {
            knownLevel = null; knownPlayer = null; lastPosition = null; captureDelay = 0;
            if (!enabled) FriendManager.clearAutoFriends();
            return;
        }

        Vec3 current = client.player.position();
        boolean newLife = knownLevel != client.level || !client.player.getUUID().equals(knownPlayer);
        boolean matchTeleport = lastPosition != null && current.distanceTo(lastPosition) >= MATCH_TELEPORT_DISTANCE;
        if (newLife || matchTeleport) {
            FriendManager.clearAutoFriends();
            knownLevel = client.level;
            knownPlayer = client.player.getUUID();
            captureDelay = 40; // allow nearby teammates to load after the match teleport
        }
        lastPosition = current;

        if (captureDelay > 0 && --captureDelay == 0) captureNearby(client);
    }

    private static void captureNearby(Minecraft client) {
        if (!hasBedNearby(client)) {
            FriendManager.clearAutoFriends();
            return;
        }
        List<String> nearby = new ArrayList<>();
        double radiusSquared = TEAM_RADIUS * TEAM_RADIUS;
        for (Player player : client.level.players()) {
            if (player == client.player || player.isSpectator()) continue;
            if (player.distanceToSqr(client.player) > radiusSquared) continue;
            // BedWars servers normally expose teams through the scoreboard.
            // When that data exists, distance alone must never turn a nearby
            // enemy into a friend. The radius fallback is for servers that do
            // not publish team metadata during the spawn countdown.
            if (client.player.getTeam() != null && !client.player.isAlliedTo(player)) continue;
            nearby.add(player.getScoreboardName());
        }
        FriendManager.replaceAutoFriends(nearby);
    }

    private static boolean hasBedNearby(Minecraft client) {
        var center = client.player.blockPosition();
        int horizontal = 30;
        int vertical = 10;
        for (int y = -vertical; y <= vertical; y++) {
            for (int x = -horizontal; x <= horizontal; x++) {
                for (int z = -horizontal; z <= horizontal; z++) {
                    if (x * x + z * z > horizontal * horizontal) continue;
                    if (client.level.getBlockState(center.offset(x, y, z)).getBlock() instanceof BedBlock) return true;
                }
            }
        }
        return false;
    }

    private static void resetCapture() {
        knownLevel = null; knownPlayer = null; lastPosition = null; captureDelay = 0;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try { enabled = Files.isRegularFile(FILE) && Boolean.parseBoolean(Files.readString(FILE).trim()); }
        catch (Exception error) { System.err.println("[Verstile] Could not load Auto Teammate: " + error.getMessage()); }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, Boolean.toString(enabled), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception error) { System.err.println("[Verstile] Could not save Auto Teammate: " + error.getMessage()); }
    }
}
