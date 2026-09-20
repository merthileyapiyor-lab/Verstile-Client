package com.verstile.client.module.hud;

import com.verstile.client.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Draggable live latency/server HUD. */
public final class PingHud extends HudModule {
    public final BooleanSetting showServer = new BooleanSetting("Show Server", true);
    private int ping = -1;

    public PingHud() {
        super("Ping HUD", "Live server latency and address", 8, 145);
        addSetting(showServer);
    }

    @Override
    public void onTick() {
        ping = -1;
        if (client.player != null && client.getConnection() != null) {
            PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
            if (info != null) ping = info.getLatency();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, boolean preview) {
        String latency = ping < 0 ? (preview ? "Ping: 42 ms" : "Ping: --") : "Ping: " + ping + " ms";
        int width = contentWidth();
        int height = contentHeight();
        background(graphics, width, height);
        graphics.drawString(client.font, latency, 7, 6, ping >= 0 && ping < 100 ? 0xFF6BFF8A : 0xFFFFC857, true);
        if (showServer.getValue()) {
            var server = client.getCurrentServer();
            String address = server == null ? (preview ? "example.net" : "Singleplayer") : server.ip;
            if (address.length() > 22) address = address.substring(0, 22) + "…";
            graphics.drawString(client.font, address, 7, 19, 0xFFB7BDCA, false);
        }
    }

    @Override public int contentWidth() { return 150; }
    @Override public int contentHeight() { return showServer.getValue() ? 34 : 22; }
}
