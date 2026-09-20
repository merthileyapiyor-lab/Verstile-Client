package com.example.autopvp;

import com.example.autopvp.combat.CombatAssistant;
import com.example.autopvp.gui.ClickGuiScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class ClientMod implements ClientModInitializer {
    private boolean wasGuiKeyPressed = false;

    @Override
    public void onInitializeClient() {
        // Tick Event Hook
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getWindow() == null || client.player == null) return;

            long windowHandle = client.getWindow().handle();

            // Check GUI customizable keybind
            boolean keyState = GLFW.glfwGetKey(windowHandle, AutoPvpMod.config.guiKey) == GLFW.GLFW_PRESS;
            if (keyState && !wasGuiKeyPressed && client.screen == null) {
                client.setScreen(new ClickGuiScreen());
            }
            wasGuiKeyPressed = keyState;

            // Trigger Combat Assist Ticks
            CombatAssistant.tick();
        });

        // HUD Render Overlay callback
        HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui || mc.player == null) return;
            if (!AutoPvpMod.config.hudEnabled) return;

            // Drawing parameters
            int y = AutoPvpMod.config.hudY;
            int x = AutoPvpMod.config.hudX;
            int lineHeight = 10;

            // Draw Premium Title
            drawContext.drawString(mc.font, Component.literal("§6§lVerstile HUD"), x, y, 0xFFFFAA00, true);
            y += lineHeight + 2;

            // Target information
            if (CombatAssistant.targetEntity != null) {
                String targetInfo = String.format("Target: §e%s §7(§c%.1f HP§7)", 
                        CombatAssistant.targetEntity.getName().getString(), 
                        CombatAssistant.targetEntity.getHealth());
                drawContext.drawString(mc.font, Component.literal(targetInfo), x, y, 0xFFFFFFFF, true);
            } else {
                drawContext.drawString(mc.font, Component.literal("Target: §8None"), x, y, 0xFFFFFFFF, true);
            }
            y += lineHeight;

            // Active modules list
            drawContext.drawString(mc.font, Component.literal("§7--- Active Modules ---"), x, y, 0xFF888888, true);
            y += lineHeight;

            if (AutoPvpMod.config.crystalPvp) {
                drawContext.drawString(mc.font, Component.literal("§b▶ §fCrystal PvP"), x, y, 0xFFFFFFFF, true);
                y += lineHeight;
            }
            if (AutoPvpMod.config.macePvp) {
                drawContext.drawString(mc.font, Component.literal("§b▶ §fMace PvP"), x, y, 0xFFFFFFFF, true);
                y += lineHeight;
            }
            if (AutoPvpMod.config.swordPvp) {
                drawContext.drawString(mc.font, Component.literal("§b▶ §fSword PvP"), x, y, 0xFFFFFFFF, true);
                y += lineHeight;
            }
            if (AutoPvpMod.config.axePvp) {
                drawContext.drawString(mc.font, Component.literal("§b▶ §fAxe PvP"), x, y, 0xFFFFFFFF, true);
                y += lineHeight;
            }
            if (AutoPvpMod.config.potPvp) {
                drawContext.drawString(mc.font, Component.literal("§b▶ §fPot PvP"), x, y, 0xFFFFFFFF, true);
                y += lineHeight;
            }
            if (AutoPvpMod.config.spearPvp) {
                drawContext.drawString(mc.font, Component.literal("§b▶ §fSpear PvP"), x, y, 0xFFFFFFFF, true);
                y += lineHeight;
            }
            if (AutoPvpMod.config.critChaining) {
                drawContext.drawString(mc.font, Component.literal("§a✔ §eCrit Chaining"), x, y, 0xFFFFFFFF, true);
                y += lineHeight;
            }
            if (AutoPvpMod.config.wTap) {
                drawContext.drawString(mc.font, Component.literal("§a✔ §eW-Tap"), x, y, 0xFFFFFFFF, true);
                y += lineHeight;
            }
            if (AutoPvpMod.config.totemCycling) {
                drawContext.drawString(mc.font, Component.literal("§a✔ §eAuto-Totem"), x, y, 0xFFFFFFFF, true);
                y += lineHeight;
            }
        });
    }
}
