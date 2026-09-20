package com.verstile.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.function.Consumer;

public final class CrashReportScreen extends Screen {
    private final Screen parent;
    private final Path report;
    private final Consumer<Path> sendAction;
    private final Consumer<Path> dismissAction;
    private boolean handled;

    public CrashReportScreen(Screen parent, Path report, Consumer<Path> sendAction, Consumer<Path> dismissAction) {
        super(Component.literal("Verstile crash report"));
        this.parent = parent; this.report = report; this.sendAction = sendAction; this.dismissAction = dismissAction;
    }

    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Send Report"), button -> finish(true))
                .bounds(width / 2 - 155, height / 2 + 42, 145, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Don't Send"), button -> finish(false))
                .bounds(width / 2 + 10, height / 2 + 42, 145, 22).build());
    }

    private void finish(boolean send) {
        if (handled) return;
        handled = true;
        if (send) sendAction.accept(report); else dismissAction.accept(report);
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override public void onClose() { finish(false); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xEC0D0D14);
        graphics.drawCenteredString(font, "A crash report from the previous run was found", width / 2,
                height / 2 - 38, 0xFFFFFFFF);
        graphics.drawCenteredString(font, report.getFileName().toString(), width / 2,
                height / 2 - 17, 0xFFC98BFF);
        graphics.drawCenteredString(font, "Send the crash report and latest.log to the Verstile updater?", width / 2,
                height / 2 + 5, 0xFFAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
