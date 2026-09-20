package com.verstile.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Choice shown after cancelling a normal (non-forced) update countdown. */
public final class UpdateChoiceScreen extends Screen {
    private final Runnable skipAction;
    private final Runnable installNextLaunchAction;
    private final String version;

    public UpdateChoiceScreen(String version, Runnable skipAction, Runnable installNextLaunchAction) {
        super(Component.literal("Update cancelled"));
        this.skipAction = skipAction;
        this.installNextLaunchAction = installNextLaunchAction;
        this.version = version;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Skip this version"), button -> skipAction.run())
                .bounds(width / 2 - 105, height / 2 + 16, 210, 22)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Install at next launch"),
                        button -> installNextLaunchAction.run())
                .bounds(width / 2 - 105, height / 2 + 44, 210, 22)
                .build());
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
        graphics.drawCenteredString(font, "Choose what to do with Verstile " + version, width / 2,
                height / 2 - 24, 0xFFFFFFFF);
        graphics.drawCenteredString(font, "You can skip it or install it after closing Minecraft.", width / 2,
                height / 2 - 5, 0xFFB8B8C8);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
