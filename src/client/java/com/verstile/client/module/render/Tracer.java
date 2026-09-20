package com.verstile.client.module.render;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.overlay.ExternalTracerOverlay;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.util.NativeWindowBounds;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class Tracer extends Module {
    public final NumberSetting range = new NumberSetting("Range", 96.0, 16.0, 256.0, 8.0);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", 1.8, 0.8, 4.0, 0.2);
    public final NumberSetting opacity = new NumberSetting("Opacity", 82.0, 20.0, 100.0, 5.0);
    public final NumberSetting smoothing = new NumberSetting("Smoothness", 0.28, 0.05, 1.0, 0.05);
    public final BooleanSetting labels = new BooleanSetting("Labels", true);
    public final BooleanSetting fromCrosshair = new BooleanSetting("From Crosshair", false);
    public final BooleanSetting boxes3d = new BooleanSetting("3D Boxes", true);

    private final ExternalTracerOverlay overlay = new ExternalTracerOverlay();

    public Tracer() {
        super("Tracer", "Smooth player tracers in a separate click-through overlay", Category.RENDER, 0);
        addSetting(range);
        addSetting(lineWidth);
        addSetting(opacity);
        addSetting(smoothing);
        addSetting(labels);
        addSetting(fromCrosshair);
        addSetting(boxes3d);
    }

    @Override
    public void onEnable() {
        overlay.start();
    }

    @Override
    public void onDisable() {
        overlay.stop();
    }

    @Override
    public void onTick() {
        captureFrame();
    }

    public void captureFrame() {
        if (client.player == null || client.level == null || client.getWindow() == null) {
            overlay.hide();
            return;
        }

        double maxRange = range.getValue();
        double maxRangeSquared = maxRange * maxRange;
        List<ExternalTracerOverlay.Target> targets = new ArrayList<>();
        for (Player player : client.level.players()) {
            if (player == client.player || !player.isAlive() || player.isSpectator()) continue;
            double distanceSquared = client.player.distanceToSqr(player);
            if (distanceSquared > maxRangeSquared) continue;
            targets.add(new ExternalTracerOverlay.Target(
                    player.getId(),
                    player.getX(), player.getY(), player.getZ(), player.getBbWidth(), player.getBbHeight(),
                    Math.sqrt(distanceSquared), player.getScoreboardName(), -1));
        }

        NativeWindowBounds.Bounds bounds = NativeWindowBounds.minecraftClientArea(client);

        overlay.update(new ExternalTracerOverlay.Frame(
                bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                client.player.getX(), client.player.getEyeY(), client.player.getZ(),
                client.player.getYRot(), client.player.getXRot(), client.options.fov().get(),
                lineWidth.getValue().floatValue(), opacity.getValue().floatValue() / 100.0f,
                smoothing.getValue().floatValue(), labels.getValue(), fromCrosshair.getValue(),
                boxes3d.getValue(), true, targets));
    }
}
