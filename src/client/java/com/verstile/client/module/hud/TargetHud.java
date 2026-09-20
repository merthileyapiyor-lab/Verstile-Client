package com.verstile.client.module.hud;

import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class TargetHud extends HudModule {
    public final NumberSetting range = new NumberSetting("Target Range", 10.0, 3.0, 32.0, 0.5);
    public final NumberSetting accent = new NumberSetting("Accent RGB", 0xB95CFF, 0, 0xFFFFFF, 1);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", true);
    public final BooleanSetting showArmor = new BooleanSetting("Show Armor", true);
    private Player target;
    private float displayedHealth;

    public TargetHud() {
        super("Target HUD", "Name, health, armor and distance for the closest target", 220, 170);
        addSetting(range);
        addSetting(accent);
        addSetting(showDistance);
        addSetting(showArmor);
    }

    @Override
    public void onTick() {
        if (client.player == null || client.level == null) {
            target = null;
            return;
        }
        Player nearest = null;
        double nearestDistance = range.getValue();
        for (Player candidate : client.level.players()) {
            if (candidate == client.player || !candidate.isAlive() || candidate.isSpectator()) continue;
            double distance = client.player.distanceTo(candidate);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        if (nearest != target) displayedHealth = nearest == null ? 0.0f : nearest.getHealth();
        target = nearest;
        if (target != null) displayedHealth += (target.getHealth() - displayedHealth) * 0.22f;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, boolean preview) {
        Player shown = target;
        String name = shown == null ? (preview ? "Target Preview" : "No target") : shown.getScoreboardName();
        float health = shown == null ? 16.0f : displayedHealth;
        float maxHealth = shown == null ? 20.0f : Math.max(1.0f, shown.getMaxHealth());
        background(graphics, contentWidth(), contentHeight());
        graphics.drawString(client.font, name, 9, 8, 0xFFFFFFFF, true);
        String details = Math.round(health * 10.0f) / 10.0f + " HP";
        if (shown != null && showDistance.getValue() && client.player != null) {
            details += "  " + String.format("%.1fm", client.player.distanceTo(shown));
        }
        if (showArmor.getValue()) details += "  Armor " + (shown == null ? 12 : shown.getArmorValue());
        graphics.drawString(client.font, details, 9, 22, 0xFFB7BDCA, false);
        graphics.fill(9, 38, 161, 43, 0xFF20242D);
        int filled = (int) Math.round(152.0 * Mth.clamp(health / maxHealth, 0.0f, 1.0f));
        graphics.fill(9, 38, 9 + filled, 43, 0xFF000000 | accent.getValue().intValue());
    }

    @Override public int contentWidth() { return 170; }
    @Override public int contentHeight() { return 52; }
}
