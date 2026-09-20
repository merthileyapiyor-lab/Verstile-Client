package com.verstile.client.module.hud;

import com.verstile.client.gui.HudEditorScreen;

public final class HudEditorModule extends HudModule {
    public HudEditorModule() {
        super("HUD Editor", "Drag and arrange enabled HUD elements", 0, 0);
    }

    @Override
    public void onEnable() {
        client.execute(() -> client.setScreen(new HudEditorScreen()));
        setEnabled(false);
    }

    @Override protected void renderContent(net.minecraft.client.gui.GuiGraphics graphics, boolean preview) {}
    @Override public int contentWidth() { return 0; }
    @Override public int contentHeight() { return 0; }
}
