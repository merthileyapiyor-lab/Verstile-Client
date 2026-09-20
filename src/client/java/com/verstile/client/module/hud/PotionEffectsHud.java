package com.verstile.client.module.hud;

import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.ModeSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Draggable list of active potion/status effects with live duration. */
public final class PotionEffectsHud extends HudModule {
    public final ModeSetting sorting = new ModeSetting("Sorting", "DURATION", "DURATION", "NAME");
    public final BooleanSetting showDuration = new BooleanSetting("Show Duration", true);
    public final BooleanSetting showAmplifier = new BooleanSetting("Show Amplifier", true);
    public final BooleanSetting effectColors = new BooleanSetting("Effect Colors", true);
    public final NumberSetting textColor = new NumberSetting("Text RGB", 0xE6EAF2, 0, 0xFFFFFF, 1);
    private int lastWidth = 118;
    private int lastHeight = 31;

    public PotionEffectsHud() {
        super("Potion Effects", "Active potion effects, levels and remaining duration", 8, 105);
        addSetting(sorting);
        addSetting(showDuration);
        addSetting(showAmplifier);
        addSetting(effectColors);
        addSetting(textColor);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, boolean preview) {
        List<EffectLine> lines = effectLines(preview);
        if (lines.isEmpty()) return;
        int widest = 0;
        for (EffectLine line : lines) widest = Math.max(widest, client.font.width(line.text));
        lastWidth = Math.max(92, widest + 15);
        lastHeight = lines.size() * 12 + 8;
        background(graphics, lastWidth, lastHeight);
        int drawY = 5;
        for (EffectLine line : lines) {
            graphics.fill(5, drawY + 1, 7, drawY + 9, line.color);
            graphics.drawString(client.font, line.text, 10, drawY, line.color, true);
            drawY += 12;
        }
    }

    private List<EffectLine> effectLines(boolean preview) {
        List<MobEffectInstance> effects = client.player == null
                ? new ArrayList<>() : new ArrayList<>(client.player.getActiveEffects());
        Comparator<MobEffectInstance> comparator = sorting.is("NAME")
                ? Comparator.comparing(effect -> effect.getEffect().value().getDisplayName().getString(),
                String.CASE_INSENSITIVE_ORDER)
                : Comparator.comparingInt(MobEffectInstance::getDuration).reversed();
        effects.sort(comparator);
        List<EffectLine> result = new ArrayList<>();
        for (MobEffectInstance effect : effects) {
            StringBuilder text = new StringBuilder(effect.getEffect().value().getDisplayName().getString());
            if (showAmplifier.getValue() && effect.getAmplifier() > 0) {
                text.append(' ').append(effect.getAmplifier() + 1);
            }
            if (showDuration.getValue()) text.append("  ").append(duration(effect));
            int rgb = effectColors.getValue() ? effect.getEffect().value().getColor() : textColor.getValue().intValue();
            result.add(new EffectLine(text.toString(), 0xFF000000 | rgb));
        }
        if (preview && result.isEmpty()) {
            result.add(new EffectLine("Speed II  1:24", 0xFF7CAFC6));
            result.add(new EffectLine("Strength  0:42", 0xFFDF3A3A));
        }
        return result;
    }

    private static String duration(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) return "∞";
        int totalSeconds = Math.max(0, effect.getDuration() / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    @Override public int contentWidth() { return lastWidth; }
    @Override public int contentHeight() { return lastHeight; }

    private record EffectLine(String text, int color) {}
}
