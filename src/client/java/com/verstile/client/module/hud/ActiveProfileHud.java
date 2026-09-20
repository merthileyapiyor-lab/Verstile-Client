package com.verstile.client.module.hud;

import com.verstile.client.profile.ProfileManager;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

public final class ActiveProfileHud extends HudModule {
    public final NumberSetting color = new NumberSetting("Text RGB", 0xC98BFF, 0, 0xFFFFFF, 1);
    public ActiveProfileHud() {
        super("Active Profile", "Displays the currently loaded profile", 8, 128);
        addSetting(color);
    }
    private String text() { return "Profile: " + ProfileManager.activeProfile(); }
    @Override protected void renderContent(GuiGraphics graphics, boolean preview) {
        background(graphics, contentWidth(), contentHeight());
        graphics.drawString(client.font, text(), 6, 6, 0xFF000000 | color.getValue().intValue(), true);
    }
    @Override public int contentWidth() { return Math.max(95, client.font.width(text()) + 12); }
    @Override public int contentHeight() { return 21; }
}
