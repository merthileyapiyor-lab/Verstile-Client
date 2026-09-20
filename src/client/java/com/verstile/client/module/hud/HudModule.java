package com.verstile.client.module.hud;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

public abstract class HudModule extends Module {
    public final NumberSetting x;
    public final NumberSetting y;
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 2.0, 0.05);
    public final NumberSetting backgroundOpacity = new NumberSetting("Background Opacity", 55.0, 0.0, 100.0, 5.0);
    public final BooleanSetting roundedBackground = new BooleanSetting("Rounded Background", true);

    protected HudModule(String name, String description, int defaultX, int defaultY) {
        super(name, description, Category.HUD, 0);
        x = new NumberSetting("Position X", defaultX, 0.0, 3840.0, 1.0);
        y = new NumberSetting("Position Y", defaultY, 0.0, 2160.0, 1.0);
        addSetting(x);
        addSetting(y);
        addSetting(scale);
        addSetting(backgroundOpacity);
        addSetting(roundedBackground);
    }

    public final void renderHud(GuiGraphics graphics, boolean preview) {
        if (!preview && !isEnabled()) return;
        float factor = scale.getValue().floatValue();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x.getValue().floatValue(), y.getValue().floatValue());
        graphics.pose().scale(factor, factor);
        renderContent(graphics, preview);
        graphics.pose().popMatrix();
    }

    protected void background(GuiGraphics graphics, int width, int height) {
        int alpha = Math.max(0, Math.min(255, (int) Math.round(backgroundOpacity.getValue() * 2.55)));
        int color = (alpha << 24) | 0x0B0D12;
        if (roundedBackground.getValue()) roundRect(graphics, 0, 0, width, height, 5, color);
        else graphics.fill(0, 0, width, height, color);
    }

    public int scaledWidth() {
        return (int) Math.ceil(contentWidth() * scale.getValue());
    }

    public int scaledHeight() {
        return (int) Math.ceil(contentHeight() * scale.getValue());
    }

    public abstract int contentWidth();
    public abstract int contentHeight();
    protected abstract void renderContent(GuiGraphics graphics, boolean preview);

    protected static void roundRect(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        graphics.fill(x + r, y, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + width, y + height - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            double exactInset = r - Math.sqrt(Math.max(0.0, r * r - dy * dy));
            int inset = (int) Math.ceil(exactInset);
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            graphics.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
            int edge = inset - 1;
            if (edge >= 0) {
                int smoothColor = scaleAlpha(color, Math.max(0.0, Math.min(1.0, inset - exactInset)));
                graphics.fill(x + edge, y + row, x + edge + 1, y + row + 1, smoothColor);
                graphics.fill(x + width - edge - 1, y + row, x + width - edge, y + row + 1, smoothColor);
                graphics.fill(x + edge, y + height - row - 1, x + edge + 1, y + height - row, smoothColor);
                graphics.fill(x + width - edge - 1, y + height - row - 1,
                        x + width - edge, y + height - row, smoothColor);
            }
        }
    }

    private static int scaleAlpha(int color, double coverage) {
        int alpha = (color >>> 24) & 0xFF;
        int smoothAlpha = Math.max(0, Math.min(255, (int) Math.round(alpha * coverage)));
        return (color & 0x00FFFFFF) | (smoothAlpha << 24);
    }
}
