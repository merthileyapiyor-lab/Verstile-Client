package com.verstile.client.gui;

import com.verstile.client.module.Module;
import com.verstile.client.module.ModuleManager;
import com.verstile.client.module.hud.HudEditorModule;
import com.verstile.client.module.hud.HudModule;
import com.verstile.client.profile.AutoConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class HudEditorScreen extends Screen {
    private HudModule dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    public HudEditorScreen() {
        super(Component.literal("Verstile HUD Editor"));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        dragging = null;
        AutoConfig.save();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x78080A0F);
        graphics.drawCenteredString(font, "HUD EDITOR", width / 2, 12, 0xFFC98BFF);
        graphics.drawCenteredString(font, "Drag elements • ESC to save and close", width / 2, 26, 0xFFB2B6C2);
        for (Module module : ModuleManager.getInstance().getModules()) {
            if (!(module instanceof HudModule hud) || hud instanceof HudEditorModule) continue;
            hud.renderHud(graphics, true);
            int x = hud.x.getValue().intValue();
            int y = hud.y.getValue().intValue();
            int w = Math.max(8, hud.scaledWidth());
            int h = Math.max(8, hud.scaledHeight());
            int color = contains(hud, mouseX, mouseY) ? 0xFFC98BFF : 0x887A8090;
            graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, color);
            graphics.drawString(font, hud.getName(), x + 3, y - 10, color, true);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        for (Module module : ModuleManager.getInstance().getModules().reversed()) {
            if (module instanceof HudModule hud && !(hud instanceof HudEditorModule)
                    && contains(hud, event.x(), event.y())) {
                dragging = hud;
                dragOffsetX = event.x() - hud.x.getValue();
                dragOffsetY = event.y() - hud.y.getValue();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging != null && event.button() == 0) {
            double newX = Math.max(0.0, Math.min(width - dragging.scaledWidth(), event.x() - dragOffsetX));
            double newY = Math.max(0.0, Math.min(height - dragging.scaledHeight(), event.y() - dragOffsetY));
            dragging.x.setValueClamped(newX);
            dragging.y.setValueClamped(newY);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && dragging != null) {
            dragging = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    private boolean contains(HudModule hud, double mouseX, double mouseY) {
        int x = hud.x.getValue().intValue();
        int y = hud.y.getValue().intValue();
        return mouseX >= x && mouseX <= x + Math.max(8, hud.scaledWidth())
                && mouseY >= y && mouseY <= y + Math.max(8, hud.scaledHeight());
    }
}
