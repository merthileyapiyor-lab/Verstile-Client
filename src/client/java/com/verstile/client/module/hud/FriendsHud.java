package com.verstile.client.module.hud;

import com.verstile.client.friend.FriendManager;
import com.verstile.client.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FriendsHud extends HudModule {
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", true);
    public final BooleanSetting automaticMarker = new BooleanSetting("Automatic Marker", true);
    private final List<String> visible = new ArrayList<>();
    private int lastWidth = 110;

    public FriendsHud() {
        super("Friends HUD", "Online friends and automatic BedWars teammates", 8, 180);
        addSetting(showDistance); addSetting(automaticMarker);
    }

    @Override public void onTick() {
        visible.clear();
        if (client.level == null || client.player == null) return;
        client.level.players().stream().filter(player -> player != client.player && FriendManager.isFriend(player))
                .sorted(Comparator.comparingDouble(client.player::distanceTo)).forEach(player -> visible.add(label(player)));
    }

    private String label(Player player) {
        String marker = automaticMarker.getValue() && FriendManager.isAutoFriend(player.getScoreboardName()) ? "⚡ " : "";
        return marker + player.getScoreboardName() + (showDistance.getValue()
                ? " §7" + Math.round(client.player.distanceTo(player)) + "m" : "");
    }

    @Override protected void renderContent(GuiGraphics graphics, boolean preview) {
        List<String> rows = visible.isEmpty() && preview ? List.of("⚡ Teammate 8m", "Friend 21m") : List.copyOf(visible);
        lastWidth = 110;
        for (String row : rows) lastWidth = Math.max(lastWidth, client.font.width(row) + 12);
        background(graphics, lastWidth, contentHeight());
        graphics.drawString(client.font, "FRIENDS", 6, 5, 0xFFC98BFF, true);
        int y = 18;
        for (String row : rows) { graphics.drawString(client.font, row, 6, y, 0xFFFFFFFF, false); y += 11; }
    }

    @Override public int contentWidth() { return lastWidth; }
    @Override public int contentHeight() { return Math.max(24, 22 + visible.size() * 11); }
}
