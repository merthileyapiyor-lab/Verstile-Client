package com.verstile.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Release notes displayed once after launching a newly installed version. */
public final class ChangelogScreen extends Screen {
    private final Screen parent;
    private final String version;
    private final List<String> changelog;
    private final Runnable seenAction;
    private boolean closed;

    public ChangelogScreen(Screen parent, String version, List<String> changelog, Runnable seenAction) {
        super(Component.literal("Verstile changelog"));
        this.parent = parent;
        this.version = version;
        this.changelog = List.copyOf(changelog);
        this.seenAction = seenAction;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Continue"), button -> closeAndRemember())
                .bounds(width / 2 - 70, Math.min(height - 34, height / 2 + 92), 140, 22)
                .build());
    }

    @Override
    public void onClose() {
        closeAndRemember();
    }

    private void closeAndRemember() {
        if (closed) return;
        closed = true;
        seenAction.run();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xEC0D0D14);

        int panelWidth = Math.min(520, width - 36);
        int panelHeight = Math.min(250, height - 44);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF31B1824);
        graphics.fill(left, top, left + 3, top + panelHeight, 0xFF9C4DFF);
        graphics.drawCenteredString(font, "What's new in Verstile v" + version, width / 2, top + 18,
                0xFFFFFFFF);

        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (String entry : changelog) {
            if (entry.isBlank()) {
                wrapped.add(FormattedCharSequence.EMPTY);
            } else {
                wrapped.addAll(font.split(Component.literal("• " + entry), panelWidth - 44));
            }
        }

        int y = top + 48;
        int maxY = Math.min(top + panelHeight - 48, height / 2 + 78);
        for (FormattedCharSequence line : wrapped) {
            if (y > maxY) break;
            graphics.drawString(font, line, left + 22, y, 0xFFD4D0DD, false);
            y += 13;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
