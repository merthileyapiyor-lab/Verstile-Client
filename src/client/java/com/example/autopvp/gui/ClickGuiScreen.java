package com.example.autopvp.gui;

import com.example.autopvp.AutoPvpMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class ClickGuiScreen extends Screen {
    private boolean listeningForKey = false;
    private FlatBlackButton keyBindButton;

    public ClickGuiScreen() {
        super(Component.literal("Verstile"));
    }

    @Override
    protected void init() {
        int bw = 160;
        int bh = 20;
        
        // Start layout coords
        int leftColX = this.width / 2 - 170;
        int rightColX = this.width / 2 + 10;
        int startY = this.height / 2 - 60;

        // --- Column 1: Core PvP Modes ---
        this.addRenderableWidget(new FlatBlackButton(leftColX, startY, bw, bh, getCrystalText(), btn -> {
            AutoPvpMod.config.crystalPvp = !AutoPvpMod.config.crystalPvp;
            btn.setMessage(getCrystalText());
            AutoPvpMod.config.save();
        }));

        this.addRenderableWidget(new FlatBlackButton(leftColX, startY + 25, bw, bh, getMaceText(), btn -> {
            AutoPvpMod.config.macePvp = !AutoPvpMod.config.macePvp;
            btn.setMessage(getMaceText());
            AutoPvpMod.config.save();
        }));

        this.addRenderableWidget(new FlatBlackButton(leftColX, startY + 50, bw, bh, getSwordText(), btn -> {
            AutoPvpMod.config.swordPvp = !AutoPvpMod.config.swordPvp;
            btn.setMessage(getSwordText());
            AutoPvpMod.config.save();
        }));

        this.addRenderableWidget(new FlatBlackButton(leftColX, startY + 75, bw, bh, getAxeText(), btn -> {
            AutoPvpMod.config.axePvp = !AutoPvpMod.config.axePvp;
            btn.setMessage(getAxeText());
            AutoPvpMod.config.save();
        }));

        this.addRenderableWidget(new FlatBlackButton(leftColX, startY + 100, bw, bh, getPotText(), btn -> {
            AutoPvpMod.config.potPvp = !AutoPvpMod.config.potPvp;
            btn.setMessage(getPotText());
            AutoPvpMod.config.save();
        }));

        this.addRenderableWidget(new FlatBlackButton(leftColX, startY + 125, bw, bh, getSpearText(), btn -> {
            AutoPvpMod.config.spearPvp = !AutoPvpMod.config.spearPvp;
            btn.setMessage(getSpearText());
            AutoPvpMod.config.save();
        }));

        // --- Column 2: Mechanics & System ---
        this.addRenderableWidget(new FlatBlackButton(rightColX, startY, bw, bh, getCritChainText(), btn -> {
            AutoPvpMod.config.critChaining = !AutoPvpMod.config.critChaining;
            btn.setMessage(getCritChainText());
            AutoPvpMod.config.save();
        }));

        this.addRenderableWidget(new FlatBlackButton(rightColX, startY + 25, bw, bh, getWTapText(), btn -> {
            AutoPvpMod.config.wTap = !AutoPvpMod.config.wTap;
            btn.setMessage(getWTapText());
            AutoPvpMod.config.save();
        }));

        this.addRenderableWidget(new FlatBlackButton(rightColX, startY + 50, bw, bh, getTotemCycleText(), btn -> {
            AutoPvpMod.config.totemCycling = !AutoPvpMod.config.totemCycling;
            btn.setMessage(getTotemCycleText());
            AutoPvpMod.config.save();
        }));

        // Keybind configuration
        keyBindButton = new FlatBlackButton(rightColX, startY + 75, bw, bh, getKeybindText(), btn -> {
            listeningForKey = true;
            btn.setMessage(Component.literal("Press any key..."));
        });
        this.addRenderableWidget(keyBindButton);

        this.addRenderableWidget(new FlatBlackButton(rightColX, startY + 100, bw, bh, getHudText(), btn -> {
            AutoPvpMod.config.hudEnabled = !AutoPvpMod.config.hudEnabled;
            btn.setMessage(getHudText());
            AutoPvpMod.config.save();
        }));

        // Center bottom close button
        this.addRenderableWidget(new FlatBlackButton(this.width / 2 - 80, startY + 150, 160, bh, Component.literal("Save & Close"), btn -> this.onClose()));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (listeningForKey) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listeningForKey = false;
                keyBindButton.setMessage(getKeybindText());
                return true;
            }
            AutoPvpMod.config.guiKey = keyCode;
            AutoPvpMod.config.save();
            listeningForKey = false;
            keyBindButton.setMessage(getKeybindText());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render sleek flat-black transparent panel background instead of default dirt background
        int panelWidth = 380;
        int panelHeight = 250;
        int px = (this.width - panelWidth) / 2;
        int py = (this.height - panelHeight) / 2;

        // Draw flat dark-gray/black container card with thin clean border
        graphics.fill(px, py, px + panelWidth, py + panelHeight, 0xF2080808); // 95% opacity black card
        graphics.fill(px, py, px + panelWidth, py + 1, 0xFF333333); // Top border line
        graphics.fill(px, py + panelHeight - 1, px + panelWidth, py + panelHeight, 0xFF333333); // Bottom border line
        graphics.fill(px, py, px + 1, py + panelHeight, 0xFF333333); // Left border line
        graphics.fill(px + panelWidth - 1, py, px + panelWidth, py + panelHeight, 0xFF333333); // Right border line

        // Render Title Header text in custom clean font style
        graphics.drawString(this.font, Component.literal("Verstile"), px + 20, py + 18, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("v1.0.0 // Settings saved"), px + 20, py + 29, 0xFF555555, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- Component Text Generators ---
    private Component getCrystalText() {
        return Component.literal("Crystal PvP: " + (AutoPvpMod.config.crystalPvp ? "Active" : "Disabled"));
    }
    private Component getMaceText() {
        return Component.literal("Mace PvP: " + (AutoPvpMod.config.macePvp ? "Active" : "Disabled"));
    }
    private Component getSwordText() {
        return Component.literal("Sword PvP: " + (AutoPvpMod.config.swordPvp ? "Active" : "Disabled"));
    }
    private Component getAxeText() {
        return Component.literal("Axe Swap: " + (AutoPvpMod.config.axePvp ? "Active" : "Disabled"));
    }
    private Component getPotText() {
        return Component.literal("Pot PvP: " + (AutoPvpMod.config.potPvp ? "Active" : "Disabled"));
    }
    private Component getSpearText() {
        return Component.literal("Spear Lunge: " + (AutoPvpMod.config.spearPvp ? "Active" : "Disabled"));
    }
    private Component getCritChainText() {
        return Component.literal("Crit Chaining: " + (AutoPvpMod.config.critChaining ? "Active" : "Disabled"));
    }
    private Component getWTapText() {
        return Component.literal("W-Tap: " + (AutoPvpMod.config.wTap ? "Active" : "Disabled"));
    }
    private Component getTotemCycleText() {
        return Component.literal("Auto-Totem: " + (AutoPvpMod.config.totemCycling ? "Active" : "Disabled"));
    }
    private Component getHudText() {
        return Component.literal("Overlay HUD: " + (AutoPvpMod.config.hudEnabled ? "Visible" : "Hidden"));
    }
    private Component getKeybindText() {
        String keyName = GLFW.glfwGetKeyName(AutoPvpMod.config.guiKey, 0);
        if (keyName == null) {
            if (AutoPvpMod.config.guiKey == GLFW.GLFW_KEY_RIGHT_SHIFT) keyName = "R_SHIFT";
            else keyName = "Key " + AutoPvpMod.config.guiKey;
        } else {
            keyName = keyName.toUpperCase();
        }
        return Component.literal("GUI Bind: [" + keyName + "]");
    }

    // --- Premium Custom Flat Black Button Class ---
    private static class FlatBlackButton extends Button {
        public FlatBlackButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = this.isHoveredOrFocused();
            
            // Premium dark styling colors
            int bgColor = hovered ? 0xFF141414 : 0xFF0A0A0A;
            int borderColor = hovered ? 0xFFFFAA00 : 0xFF222222; // Gold glow border on hover, charcoal otherwise
            
            // Draw clean background block over the default sprite
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            
            // Draw single-pixel sharp border outlines
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, borderColor);
            graphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, borderColor);
            graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, borderColor);
            graphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, borderColor);

            // Draw centered clean label text
            int textColor = hovered ? 0xFFFFAA00 : 0xFF888888;
            graphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, textColor);
        }
    }
}
