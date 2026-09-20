package com.verstile.client.module.render;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Chunk-safe spawner ESP tuned for large servers such as DonutSMP. */
public final class SpawnerESP extends Module implements WorldRenderModule {
    public final NumberSetting range = new NumberSetting("Range", 192.0, 16.0, 512.0, 8.0);
    public final NumberSetting maxSpawners = new NumberSetting("Max Spawners", 256.0, 8.0, 1024.0, 8.0);
    public final NumberSetting scanInterval = new NumberSetting("Scan Interval", 12.0, 2.0, 100.0, 2.0);
    public final NumberSetting color = new NumberSetting("Spawner RGB", 0xFF3B30, 0, 0xFFFFFF, 1);
    public final NumberSetting fillOpacity = new NumberSetting("Fill Opacity", 24.0, 0.0, 80.0, 2.0);
    public final NumberSetting outlineOpacity = new NumberSetting("Outline Opacity", 96.0, 10.0, 100.0, 2.0);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", 2.1, 0.5, 5.0, 0.1);
    public final BooleanSetting tracers = new BooleanSetting("Tracers", true);
    public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", true);

    private record Target(AABB box, int rgb, double distanceSquared) {}
    private volatile List<Target> targets = List.of();
    private Object knownLevel;
    private int cooldown;

    public SpawnerESP() {
        super("SpawnerESP", "DonutSMP-ready loaded-chunk spawner boxes and smooth tracers", Category.RENDER, 0);
        addSetting(range); addSetting(maxSpawners); addSetting(scanInterval);
        addSetting(color); addSetting(fillOpacity);
        addSetting(outlineOpacity); addSetting(lineWidth); addSetting(tracers);
        addSetting(throughWalls);
    }

    @Override public void onEnable() { cooldown = 0; knownLevel = null; }
    @Override public void onDisable() { targets = List.of(); knownLevel = null; }

    @Override public void onTick() {
        if (client.player == null || client.level == null) {
            targets = List.of(); knownLevel = null; return;
        }
        if (knownLevel != client.level) {
            knownLevel = client.level; targets = List.of(); cooldown = 0;
        }
        if (cooldown-- <= 0) {
            cooldown = scanInterval.getValue().intValue();
            scanLoadedChunks();
        }
    }

    private void scanLoadedChunks() {
        double maxRange = range.getValue();
        double maxDistanceSquared = maxRange * maxRange;
        int centerX = client.player.getBlockX() >> 4;
        int centerZ = client.player.getBlockZ() >> 4;
        int radius = Math.min(client.options.getEffectiveRenderDistance(), (int) Math.ceil(maxRange / 16.0));
        List<Target> found = new ArrayList<>();
        Vec3 eye = client.player.getEyePosition();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                LevelChunk chunk = client.level.getChunkSource().getChunk(x, z, false);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof SpawnerBlockEntity)) continue;
                    BlockPos pos = entity.getBlockPos();
                    AABB box = new AABB(pos).inflate(0.045);
                    double distanceSquared = box.distanceToSqr(eye);
                    if (distanceSquared > maxDistanceSquared) continue;
                    found.add(new Target(box, color.getValue().intValue(), distanceSquared));
                }
            }
        }
        found.sort(Comparator.comparingDouble(Target::distanceSquared));
        targets = List.copyOf(found.subList(0, Math.min(found.size(), maxSpawners.getValue().intValue())));
        if (client.player != null) client.player.displayClientMessage(
                Component.literal("§aFound (" + targets.size() + ") spawners"), true);
    }

    @Override public void renderWorld() {
        if (client.player == null || client.level == null || knownLevel != client.level) return;
        int fillA = alpha(fillOpacity.getValue());
        int strokeA = alpha(outlineOpacity.getValue());
        float width = lineWidth.getValue().floatValue();
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        Vec3 look = client.getCameraEntity() == null ? new Vec3(0, 0, 1) : client.getCameraEntity().getViewVector(1.0f);
        Vec3 start = camera.add(look.scale(0.32));

        for (Target target : targets) {
            int rgb = target.rgb() & 0xFFFFFF;
            var box = Gizmos.cuboid(target.box(), GizmoStyle.strokeAndFill((strokeA << 24) | rgb, width, (fillA << 24) | rgb));
            if (throughWalls.getValue()) box.setAlwaysOnTop();
            if (tracers.getValue()) {
                int tracerA = Math.max(45, (int) (strokeA * Math.max(0.28, 1.0 - Math.sqrt(target.distanceSquared()) / (range.getValue() * 1.25))));
                var glow = Gizmos.line(start, target.box().getCenter(), (tracerA / 4) << 24, width + 2.3f);
                var line = Gizmos.line(start, target.box().getCenter(), (tracerA << 24) | rgb, Math.max(0.8f, width * 0.8f));
                if (throughWalls.getValue()) { glow.setAlwaysOnTop(); line.setAlwaysOnTop(); }
            }
        }
    }

    private static int alpha(double percent) {
        return Math.max(0, Math.min(255, (int) Math.round(percent * 2.55)));
    }
}
