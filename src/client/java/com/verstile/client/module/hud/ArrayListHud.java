package com.verstile.client.module.hud;

import com.verstile.client.module.Module;
import com.verstile.client.module.ModuleManager;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.ModeSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ArrayListHud extends HudModule {
    public final ModeSetting sorting = new ModeSetting("Sorting", "WIDTH", "WIDTH", "NAME");
    public final BooleanSetting suffixes = new BooleanSetting("Module Suffixes", true);
    public final BooleanSetting rightAligned = new BooleanSetting("Right Aligned", false);
    public final NumberSetting color = new NumberSetting("Text RGB", 0xC98BFF, 0, 0xFFFFFF, 1);
    public final NumberSetting animationSpeed = new NumberSetting("Animation Speed", 12.0, 1.0, 30.0, 1.0);
    private volatile int lastWidth = 110;
    private volatile int lastHeight = 20;

    public ArrayListHud() {
        super("ArrayList", "Animated list of enabled modules", 8, 8);
        addSetting(sorting);
        addSetting(suffixes);
        addSetting(rightAligned);
        addSetting(color);
        addSetting(animationSpeed);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, boolean preview) {
        List<String> names = names(preview);
        int width = 0;
        for (String name : names) width = Math.max(width, client.font.width(name));
        lastWidth = Math.max(90, width + 12);
        lastHeight = Math.max(18, names.size() * 11 + 6);
        background(graphics, lastWidth, lastHeight);
        int y = 4;
        int rgb = 0xFF000000 | color.getValue().intValue();
        for (String name : names) {
            int drawX = rightAligned.getValue() ? lastWidth - client.font.width(name) - 6 : 6;
            graphics.drawString(client.font, name, drawX, y, rgb, true);
            y += 11;
        }
    }

    private List<String> names(boolean preview) {
        List<String> result = new ArrayList<>();
        for (Module module : ModuleManager.getInstance().getModules()) {
            if (module instanceof HudModule || !module.isEnabled()) continue;
            String name = module.getName();
            if (suffixes.getValue()) {
                for (var setting : module.getSettings()) {
                    if (setting instanceof ModeSetting mode && !mode.getName().equals("Keybind Mode")) {
                        name += " §7[" + mode.getValue() + "]";
                        break;
                    }
                }
            }
            result.add(name);
        }
        if (preview && result.isEmpty()) result.add("ArrayList Preview");
        Comparator<String> comparator = sorting.is("NAME") ? String.CASE_INSENSITIVE_ORDER
                : Comparator.comparingInt((String value) -> client.font.width(value)).reversed();
        result.sort(comparator);
        return result;
    }

    @Override public int contentWidth() { return lastWidth; }
    @Override public int contentHeight() { return lastHeight; }
}
