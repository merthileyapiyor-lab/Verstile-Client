package com.verstile.client.module.render;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** True world-space storage ESP rendered through Minecraft's anti-aliased gizmo pipeline. */
public class StorageESP extends Module implements WorldRenderModule {
    public final BooleanSetting infiniteRange = new BooleanSetting("Infinite Range", false);
    public final BooleanSetting chests = new BooleanSetting("Chests", true);
    public final BooleanSetting shulkers = new BooleanSetting("Shulkers", true);
    public final BooleanSetting enderChests = new BooleanSetting("Ender Chests", true);
    public final BooleanSetting otherContainers = new BooleanSetting("Others", true);
    public final BooleanSetting fill = new BooleanSetting("3D Fill", true);
    public final BooleanSetting outline = new BooleanSetting("3D Outline", true);
    public final BooleanSetting tracers = new BooleanSetting("Tracers", true);
    public final BooleanSetting tracerGlow = new BooleanSetting("Tracer Glow", true);
    public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", true);
    public final NumberSetting range = new NumberSetting("Range", 128.0, 16.0, 512.0, 8.0);
    public final NumberSetting maxBoxes = new NumberSetting("Max Boxes", 256.0, 8.0, 1024.0, 8.0);
    public final NumberSetting scanInterval = new NumberSetting("Scan Interval", 10.0, 2.0, 60.0, 2.0);
    public final NumberSetting outlineOpacity = new NumberSetting("Outline Opacity", 90.0, 10.0, 100.0, 5.0);
    public final NumberSetting fillOpacity = new NumberSetting("Fill Opacity", 22.0, 0.0, 80.0, 2.0);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", 1.8, 0.5, 5.0, 0.1);
    public final NumberSetting tracerOpacity = new NumberSetting("Tracer Opacity", 82.0, 10.0, 100.0, 2.0);
    public final NumberSetting tracerWidth = new NumberSetting("Tracer Width", 1.6, 0.5, 6.0, 0.1);
    public final NumberSetting chestColor = new NumberSetting("Chest RGB", 0xFFBB00, 0, 0xFFFFFF, 1);
    public final NumberSetting trappedColor = new NumberSetting("Trapped Chest RGB", 0xFF3300, 0, 0xFFFFFF, 1);
    public final NumberSetting shulkerColor = new NumberSetting("Shulker RGB", 0xFF00CC, 0, 0xFFFFFF, 1);
    public final NumberSetting enderColor = new NumberSetting("Ender Chest RGB", 0xCC00FF, 0, 0xFFFFFF, 1);
    public final NumberSetting otherColor = new NumberSetting("Other Storage RGB", 0x00E5FF, 0, 0xFFFFFF, 1);

    private volatile List<CachedStorage> cachedTargets = List.of();
    private int scanCooldown;
    private Object cachedLevel;

    public record CachedStorage(AABB bounds, int color, double distanceSquared) {}

    public StorageESP() {
        super("StorageESP", "Filled world-space 3D container boxes with continuous tracers", Category.RENDER, 0);
        addSetting(infiniteRange);
        addSetting(chests);
        addSetting(shulkers);
        addSetting(enderChests);
        addSetting(otherContainers);
        addSetting(fill);
        addSetting(outline);
        addSetting(tracers);
        addSetting(tracerGlow);
        addSetting(throughWalls);
        addSetting(range);
        addSetting(maxBoxes);
        addSetting(scanInterval);
        addSetting(outlineOpacity);
        addSetting(fillOpacity);
        addSetting(lineWidth);
        addSetting(tracerOpacity);
        addSetting(tracerWidth);
        addSetting(chestColor);
        addSetting(trappedColor);
        addSetting(shulkerColor);
        addSetting(enderColor);
        addSetting(otherColor);
    }

    @Override
    public void onEnable() {
        scanCooldown = 0;
        cachedLevel = null;
    }

    @Override
    public void onDisable() {
        cachedTargets = List.of();
        cachedLevel = null;
    }

    @Override
    public void onTick() {
        if (client.player == null || client.level == null) {
            cachedTargets = List.of();
            cachedLevel = null;
            return;
        }
        if (cachedLevel != client.level) {
            cachedLevel = client.level;
            cachedTargets = List.of();
            scanCooldown = 0;
        }
        if (scanCooldown-- <= 0) {
            scanCooldown = scanInterval.getValue().intValue();
            updateCache(client);
        }
    }

    @Override public void renderWorld() {
        if (!isEnabled() || client.player == null || client.level == null || cachedLevel != client.level) return;
        int strokeAlpha = alpha(outlineOpacity.getValue());
        int bodyAlpha = alpha(fillOpacity.getValue());
        float width = lineWidth.getValue().floatValue();
        Vec3 cam = client.gameRenderer.getMainCamera().position();
        Vec3 look = client.getCameraEntity() == null
                ? new Vec3(0.0, 0.0, 1.0)
                : client.getCameraEntity().getViewVector(1.0f);
        // Starting inside the near clipping plane makes a tracer appear as
        // disconnected pixels on some GPUs. Move it slightly into the scene.
        Vec3 tracerStart = cam.add(look.scale(0.32));

        for (CachedStorage target : cachedTargets) {
            int rgb = target.color() & 0x00FFFFFF;
            int stroke = outline.getValue() ? (strokeAlpha << 24) | rgb : 0;
            int body   = fill.getValue()    ? (bodyAlpha << 24) | rgb : 0;

            GizmoStyle style = (outline.getValue() && fill.getValue())
                    ? GizmoStyle.strokeAndFill(stroke, width, body)
                    : outline.getValue() ? GizmoStyle.stroke(stroke, width) : GizmoStyle.fill(body);

            var box = Gizmos.cuboid(target.bounds(), style);
            if (throughWalls.getValue()) box.setAlwaysOnTop();

            if (tracers.getValue()) {
                // Smooth glow efekti: önce kalın yarı-şeffaf arka plan çizgisi,
                // sonra ince renkli çizgi — ExternalTracerOverlay'deki gibi
                Vec3 center = target.bounds().getCenter();
                double distance = Math.sqrt(target.distanceSquared());
                double fade = Math.max(0.22, Math.min(1.0, 1.2 - distance / Math.max(32.0, range.getValue() * 1.15)));
                int tracerAlpha = alpha(tracerOpacity.getValue() * fade);
                int shadowAlpha = Math.max(0, tracerAlpha / 4);
                int shadowColor = (shadowAlpha << 24); // siyah gölge
                int tracerColor = (tracerAlpha << 24) | rgb;
                float tracerCoreWidth = tracerWidth.getValue().floatValue();
                float shadowWidth = tracerCoreWidth + 2.8f;

                if (tracerGlow.getValue()) {
                    var shadow = Gizmos.line(tracerStart, center, shadowColor, shadowWidth);
                    if (throughWalls.getValue()) shadow.setAlwaysOnTop();
                }
                var line = Gizmos.line(tracerStart, center, tracerColor, tracerCoreWidth);
                if (throughWalls.getValue()) line.setAlwaysOnTop();
            }
        }
    }

    private void updateCache(Minecraft mc) {
        boolean unlimited = infiniteRange.getValue();
        double effectiveRange = unlimited ? 512.0 : range.getValue();
        double maxDistanceSquared = effectiveRange * effectiveRange;
        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int renderDistance = mc.options.getEffectiveRenderDistance();
        int chunkRadius = unlimited ? renderDistance : Math.min(renderDistance, (int) Math.ceil(effectiveRange / 16.0));
        List<CachedStorage> fresh = new ArrayList<>();

        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!isTarget(blockEntity)) continue;
                    BlockPos pos = blockEntity.getBlockPos();
                    AABB bounds = boundsFor(blockEntity, pos);
                    if (bounds == null) continue;
                    double distanceSquared = bounds.distanceToSqr(mc.player.getEyePosition());
                    if (!unlimited && distanceSquared > maxDistanceSquared) continue;
                    fresh.add(new CachedStorage(bounds, colorFor(blockEntity), distanceSquared));
                }
            }
        }

        fresh.sort(Comparator.comparingDouble(CachedStorage::distanceSquared));
        cachedTargets = List.copyOf(fresh.subList(0, Math.min(fresh.size(), maxBoxes.getValue().intValue())));
    }

    private AABB boundsFor(BlockEntity entity, BlockPos pos) {
        AABB bounds = new AABB(pos).deflate(0.035);
        if (!(entity instanceof ChestBlockEntity)) return bounds;
        BlockState state = client.level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE)) return bounds;
        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) return bounds;
        BlockPos connected = ChestBlock.getConnectedBlockPos(pos, state);
        // Render one combined box instead of drawing both chest halves.
        if (pos.asLong() > connected.asLong()) return null;
        return bounds.minmax(new AABB(connected).deflate(0.035));
    }

    private boolean isTarget(BlockEntity entity) {
        if (entity instanceof TrappedChestBlockEntity) return chests.getValue();
        if (entity instanceof ChestBlockEntity) return chests.getValue();
        if (entity instanceof ShulkerBoxBlockEntity) return shulkers.getValue();
        if (entity instanceof EnderChestBlockEntity) return enderChests.getValue();
        return otherContainers.getValue() && (entity instanceof BarrelBlockEntity
                || entity instanceof HopperBlockEntity || entity instanceof FurnaceBlockEntity);
    }

    private int colorFor(BlockEntity entity) {
        if (entity instanceof EnderChestBlockEntity) return enderColor.getValue().intValue();
        if (entity instanceof ShulkerBoxBlockEntity) return shulkerColor.getValue().intValue();
        if (entity instanceof TrappedChestBlockEntity) return trappedColor.getValue().intValue();
        if (entity instanceof ChestBlockEntity) return chestColor.getValue().intValue();
        return otherColor.getValue().intValue();
    }

    private static int alpha(double percent) {
        return Math.max(0, Math.min(255, (int) Math.round(percent * 2.55)));
    }
}
