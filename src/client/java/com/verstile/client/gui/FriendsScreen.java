package com.verstile.client.gui;

import com.verstile.client.friend.FriendManager;
import com.verstile.client.friend.AutoTeammateManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Click an online player to add/remove them from the persistent friend list. */
public final class FriendsScreen extends Screen {
    private final Screen parent;
    public FriendsScreen(Screen parent) { super(Component.literal("Friends")); this.parent = parent; }

    @Override protected void init() {
        int left = width / 2 - 180;
        int y = height / 2 - 105;
        List<String> names = new ArrayList<>(FriendManager.list());
        if (minecraft != null && minecraft.level != null) {
            for (Player player : minecraft.level.players()) {
                if (player != minecraft.player && !names.contains(player.getScoreboardName())) names.add(player.getScoreboardName());
            }
        }
        names.sort(Comparator.naturalOrder());
        for (int i = 0; i < Math.min(14, names.size()); i++) {
            String name = names.get(i);
            boolean friend = FriendManager.isFriend(name);
            boolean automatic = FriendManager.isAutoFriend(name) && !FriendManager.isManualFriend(name);
            int x = left + (i % 2) * 185;
            int rowY = y + (i / 2) * 27;
            addRenderableWidget(Button.builder(Component.literal((automatic ? "⚡ " : friend ? "✓ " : "+ ") + name), button -> {
                FriendManager.toggle(name); clearWidgets(); init();
            }).bounds(x, rowY, 175, 22).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Auto Teammate: "
                        + (AutoTeammateManager.isEnabled() ? "ON" : "OFF")), button -> {
                    AutoTeammateManager.toggle(); clearWidgets(); init();
                }).bounds(width / 2 - 85, height / 2 + 78, 170, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(width / 2 - 55, height / 2 + 112, 110, 22).build());
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x60000000);
        int left = width / 2 - 200, top = height / 2 - 145;
        roundRect(graphics, left - 4, top - 4, 408, 294, 13, 0x50000000);
        roundRect(graphics, left, top, 400, 286, 10, 0xF00D0F14);
        roundRect(graphics, left, top, 400, 34, 10, 0xF8151820);
        roundRect(graphics, left + 2, top + 1, 396, 2, 1, 0xFF9C4DFF);
        graphics.drawCenteredString(font, "FRIENDS", width / 2, height / 2 - 132, 0xFFC98BFF);
        graphics.drawCenteredString(font, "Online players and saved friends — click to toggle", width / 2,
                height / 2 - 118, 0xFFAAAAAA);
        graphics.drawCenteredString(font, "BEDWARS", width / 2, height / 2 + 102, 0xFF858B9A);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static void roundRect(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        graphics.fill(x + r, y, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + width, y + height - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            graphics.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
        }
    }
}
