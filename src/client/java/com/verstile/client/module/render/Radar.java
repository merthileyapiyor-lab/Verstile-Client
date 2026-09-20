package com.verstile.client.module.render;

import com.verstile.client.module.hud.HudModule;
import com.verstile.client.overlay.ExternalRadarOverlay;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import com.verstile.client.util.NativeWindowBounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public final class Radar extends HudModule {
    public final NumberSetting range = new NumberSetting("Range", 50.0, 20.0, 150.0, 5.0);
    public final NumberSetting size = new NumberSetting("Size", 140.0, 70.0, 260.0, 5.0);
    public final NumberSetting opacity = new NumberSetting("Opacity", 82.0, 20.0, 100.0, 5.0);
    public final NumberSetting alignmentSeconds = new NumberSetting("Position Check Seconds", 5.0, 1.0, 15.0, 1.0);
    public final BooleanSetting showNames = new BooleanSetting("Show Names", false);
    public final BooleanSetting captureProtection = new BooleanSetting("Capture Protection", true);

    private final ExternalRadarOverlay overlay = new ExternalRadarOverlay();

    public Radar() {
        super("Radar", "External transparent radar aligned to the Minecraft client area", 18, 42);
        addSetting(range);
        addSetting(size);
        addSetting(opacity);
        addSetting(alignmentSeconds);
        addSetting(showNames);
        addSetting(captureProtection);
    }

    @Override public void onEnable() { overlay.start(); }
    @Override public void onDisable() { overlay.stop(); }
    @Override public void onTick() { captureFrame(); }

    public void captureFrame() {
        if (client.player == null || client.level == null || client.getWindow() == null) {
            overlay.hide();
            return;
        }

        double radarRange = range.getValue();
        double rangeSquared = radarRange * radarRange;
        List<ExternalRadarOverlay.Target> targets = new ArrayList<>();
        for (Player player : client.level.players()) {
            if (player == client.player || !player.isAlive() || player.isSpectator()) continue;
            double dx = player.getX() - client.player.getX();
            double dz = player.getZ() - client.player.getZ();
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared > rangeSquared) continue;
            targets.add(new ExternalRadarOverlay.Target(dx, dz, Math.sqrt(distanceSquared), player.getScoreboardName()));
        }

        NativeWindowBounds.Bounds bounds = NativeWindowBounds.minecraftClientArea(client);
        overlay.update(new ExternalRadarOverlay.Frame(
                bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                x.getValue().intValue(), y.getValue().intValue(), scaledWidth(), radarRange,
                opacity.getValue().floatValue() / 100.0f, client.player.getYRot(), showNames.getValue(),
                captureProtection.getValue(), alignmentSeconds.getValue().intValue(), targets));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, boolean preview) {
        // The live radar stays in its capture-protected native window. Minecraft
        // only draws this lightweight representation while arranging the HUD.
        if (!preview) return;
        int diameter = size.getValue().intValue();
        roundRect(graphics, 0, 0, diameter, diameter, diameter / 2, 0xD90A0D14);
        int center = diameter / 2;
        graphics.fill(center, 6, center + 1, diameter - 6, 0x6637FFD0);
        graphics.fill(6, center, diameter - 6, center + 1, 0x6637FFD0);
        graphics.renderOutline(3, 3, diameter - 6, diameter - 6, 0xFF37FFD0);
        graphics.fill(center - 2, center - 2, center + 3, center + 3, 0xFF55FF67);
        graphics.fill(center + diameter / 5, center - diameter / 6,
                center + diameter / 5 + 5, center - diameter / 6 + 5, 0xFFFF4058);
        graphics.drawCenteredString(client.font, "RADAR", center, 9, 0xFFFFFFFF);
    }

    @Override public int contentWidth() { return size.getValue().intValue(); }
    @Override public int contentHeight() { return size.getValue().intValue(); }
}
