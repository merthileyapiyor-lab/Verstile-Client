package com.verstile.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class UpdateRestartScreen extends Screen {
    private final long deadlineMillis;
    private final boolean forced;
    private final Runnable cancelAction;
    private final String version;

    public UpdateRestartScreen(int seconds, boolean forced, String version, Runnable cancelAction) {
        super(Component.literal("Restart for new version"));
        this.deadlineMillis = System.currentTimeMillis() + seconds * 1000L;
        this.forced = forced;
        this.version = version;
        this.cancelAction = cancelAction;
    }

    @Override
    protected void init() {
        if (!forced) {
            addRenderableWidget(Button.builder(Component.literal("Click here to cancel"), button -> cancelAction.run())
                    .bounds(width / 2 - 85, height / 2 + 34, 170, 22)
                    .build());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE8101016);
        int remaining = Math.max(0, (int) Math.ceil((deadlineMillis - System.currentTimeMillis()) / 1000.0));
        String title = forced ? "Forced update - restart required" : "Restart for new version";
        String countdown = "Installing Verstile " + version + " - " + remaining + "s";
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 12, 0xFFFFFFFF);
        graphics.drawCenteredString(font, countdown, width / 2, height / 2 + 10, 0xFF9C4DFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
