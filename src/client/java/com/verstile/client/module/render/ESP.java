package com.verstile.client.module.render;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.overlay.ExternalTracerOverlay;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.ModeSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.util.NativeWindowBounds;
import net.minecraft.client.Camera;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class ESP extends Module implements WorldRenderModule {
    public final ModeSetting renderMode = new ModeSetting("Render Mode", "WORLD_3D", "WORLD_3D", "EXTERNAL");
    public final BooleanSetting renderPlayers = new BooleanSetting("Players", true);
    public final BooleanSetting renderMobs = new BooleanSetting("Mobs", false);
    public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", true);
    public final BooleanSetting fill = new BooleanSetting("3D Fill", true);
    public final NumberSetting range = new NumberSetting("Range", 96.0, 16.0, 256.0, 8.0);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", 1.8, 0.8, 4.0, 0.2);
    public final NumberSetting opacity = new NumberSetting("Outline Opacity", 90.0, 20.0, 100.0, 5.0);
    public final NumberSetting fillOpacity = new NumberSetting("Fill Opacity", 15.0, 0.0, 70.0, 2.0);
    public final NumberSetting smoothing = new NumberSetting("External Smoothness", 0.32, 0.05, 1.0, 0.05);
    public final BooleanSetting labels = new BooleanSetting("Labels", false);
    private final ExternalTracerOverlay overlay = new ExternalTracerOverlay();
    private volatile List<LivingEntity> targets = List.of();
    private boolean externalRunning;

    public ESP() {
        super("ESP", "Accurate filled world-space entity boxes with optional external mode", Category.RENDER, 0);
        addSetting(renderMode); addSetting(renderPlayers); addSetting(renderMobs); addSetting(throughWalls);
        addSetting(fill); addSetting(range); addSetting(lineWidth); addSetting(opacity); addSetting(fillOpacity);
        addSetting(smoothing); addSetting(labels);
    }

    @Override public void onEnable() { externalRunning = false; targets = List.of(); }
    @Override public void onDisable() { overlay.stop(); externalRunning = false; targets = List.of(); }

    @Override public void onTick() {
        if (client.player == null || client.level == null) { targets = List.of(); overlay.hide(); return; }
        List<LivingEntity> fresh = new ArrayList<>();
        double maxSquared = range.getValue() * range.getValue();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == client.player || !living.isAlive()) continue;
            boolean player = living instanceof Player;
            if ((player && (!renderPlayers.getValue() || ((Player) living).isSpectator()))
                    || (!player && !renderMobs.getValue()) || client.player.distanceToSqr(living) > maxSquared) continue;
            fresh.add(living);
        }
        targets = List.copyOf(fresh);
        if (renderMode.is("EXTERNAL")) {
            if (!externalRunning) { overlay.start(); externalRunning = true; }
            captureExternal();
        } else if (externalRunning) {
            overlay.stop(); externalRunning = false;
        }
    }

    @Override public void renderWorld() {
        if (!renderMode.is("WORLD_3D")) return;
        int outlineAlpha = (int) Math.round(opacity.getValue() * 2.55);
        int bodyAlpha = (int) Math.round(fillOpacity.getValue() * 2.55);
        for (LivingEntity target : targets) {
            int rgb = target instanceof Player ? 0x35FF78 : 0xFF4058;
            int outlineColor = (outlineAlpha << 24) | rgb;
            int bodyColor = (bodyAlpha << 24) | rgb;
            GizmoStyle style = fill.getValue()
                    ? GizmoStyle.strokeAndFill(outlineColor, lineWidth.getValue().floatValue(), bodyColor)
                    : GizmoStyle.stroke(outlineColor, lineWidth.getValue().floatValue());
            var box = Gizmos.cuboid(target.getBoundingBox().inflate(0.025), style);
            if (throughWalls.getValue()) box.setAlwaysOnTop();
        }
    }

    private void captureExternal() {
        if (client.getWindow() == null || client.gameRenderer == null) { overlay.hide(); return; }
        List<ExternalTracerOverlay.Target> projected = new ArrayList<>();
        for (LivingEntity living : targets) {
            double distance = client.player.distanceTo(living);
            projected.add(new ExternalTracerOverlay.Target(living.getId(), living.getX(), living.getY(), living.getZ(),
                    living.getBbWidth(), living.getBbHeight(), distance, living.getScoreboardName(),
                    living instanceof Player ? 0x35FF78 : 0xFF4058));
        }
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cameraPosition = camera.position();
        NativeWindowBounds.Bounds bounds = NativeWindowBounds.minecraftClientArea(client);
        overlay.update(new ExternalTracerOverlay.Frame(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                cameraPosition.x, cameraPosition.y, cameraPosition.z, camera.yRot(), camera.xRot(),
                client.options.fov().get(), lineWidth.getValue().floatValue(), opacity.getValue().floatValue() / 100.0f,
                smoothing.getValue().floatValue(), labels.getValue(), false, true, false, projected));
    }
}
