package com.verstile.client.module.hud;

import com.verstile.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

public final class CoordinatesHud extends HudModule {
    public final NumberSetting color = new NumberSetting("Text RGB", 0xFFFFFF, 0, 0xFFFFFF, 1);

    public CoordinatesHud() {
        super("Coordinates", "Current player block coordinates", 8, 100);
        addSetting(color);
    }

    private String text() {
        if (client.player == null) return "X 0  Y 0  Z 0";
        return "X " + client.player.getBlockX() + "  Y " + client.player.getBlockY() + "  Z " + client.player.getBlockZ();
    }

    @Override protected void renderContent(GuiGraphics graphics, boolean preview) {
        background(graphics, contentWidth(), contentHeight());
        graphics.drawString(client.font, text(), 6, 6, 0xFF000000 | color.getValue().intValue(), true);
    }
    @Override public int contentWidth() { return Math.max(105, client.font.width(text()) + 12); }
    @Override public int contentHeight() { return 21; }
}
